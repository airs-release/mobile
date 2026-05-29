package com.airs.android.provisioning.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.airs.android.provisioning.domain.WifiConfigPayload
import com.airs.android.provisioning.model.AirsBleNode
import com.airs.android.provisioning.model.ProvisioningStatus
import com.airs.android.provisioning.model.ProvisioningStatusType
import com.airs.android.provisioning.model.WifiCredentials
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.channels.awaitClose
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidBleProvisioningRepository(
    private val context: Context // Android BLE API 접근에 필요한 application context
) : BleProvisioningRepository { // 실제 Android 휴대폰에서 BLE provisioning을 수행하는 repository
    private var bluetoothGatt: BluetoothGatt? = null // 현재 연결된 노드의 GATT 연결 객체
    private var wifiConfigCharacteristic: BluetoothGattCharacteristic? = null // SSID/PW JSON을 write할 characteristic
    private var statusCharacteristic: BluetoothGattCharacteristic? = null // 노드 Wi-Fi 진행 상태를 notify 받을 characteristic
    private var connectedFakeNode: AirsBleNode? = null // 태블릿 UI 테스트용 가짜 노드 연결 상태
    private var connectContinuation: CancellableContinuation<Unit>? = null // GATT 연결 완료를 기다리는 coroutine
    private var descriptorWriteContinuation: CancellableContinuation<Unit>? = null // notify 설정 완료를 기다리는 coroutine
    private var wifiWriteContinuation: CancellableContinuation<Unit>? = null // Wi-Fi payload write 완료를 기다리는 coroutine
    private var statusEvents: SendChannel<ProvisioningStatus>? = null // BLE notify를 ViewModel Flow로 전달하는 channel

    private val gattCallback = object : BluetoothGattCallback() { // Android BLE callback을 coroutine/Flow 세계로 이어주는 callback
        // BLE 연결 상태가 바뀔 때 호출
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failPendingOperations(IllegalStateException("BLE 연결 상태 변경 실패: status=$status")) // Android BLE stack이 실패를 보고한 경우
                disconnect() // 실패한 GATT 연결 정리
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (!gatt.discoverServices()) { // 연결 직후 AIRS service/characteristic 목록 검색 시작
                        failPendingOperations(IllegalStateException("BLE service discovery를 시작하지 못했습니다."))
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    failPendingOperations(IllegalStateException("BLE 연결이 끊어졌습니다.")) // 연결 중이던 작업에 실패를 전달
                    statusEvents?.close(IllegalStateException("BLE 연결이 끊어졌습니다.")) // 진행 중 Flow도 종료
                }
            }
        }

        // service discovery가 끝났을 때 호출
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                resumeConnect(Result.failure(IllegalStateException("BLE service discovery 실패: status=$status"))) // service discovery 실패
                return
            }

            val result = runCatching { cacheAirsCharacteristics(gatt) } // AIRS service와 characteristic을 찾아 저장
            resumeConnect(result)
        }

        // notify 활성화를 위한 CCCD write 결과를 받을 때 호출
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val result = if (status == BluetoothGatt.GATT_SUCCESS) {
                Result.success(Unit) // notify 설정 성공
            } else {
                Result.failure(IllegalStateException("Status notify 설정 실패: status=$status")) // notify 설정 실패
            }
            resumeDescriptorWrite(result)
        }

        // Wi-Fi Config characteristic write 결과를 받을 때 호출
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val result = if (status == BluetoothGatt.GATT_SUCCESS) {
                Result.success(Unit) // payload write 성공
            } else {
                Result.failure(IllegalStateException("Wi-Fi config write 실패: status=$status")) // payload write 실패
            }
            resumeWifiWrite(result)
        }

        // Android 12 이하 방식의 characteristic notify callback
        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Android 13, kept for older devices")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleStatusBytes(characteristic.value) // characteristic 안에 들어온 bytes를 상태 문자열로 해석
        }

        // Android 13 이상 방식의 characteristic notify callback
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleStatusBytes(value) // callback이 직접 넘겨준 bytes를 상태 문자열로 해석
        }
    }

    // 주변 AIRS BLE 노드를 실제 Android BLE scan으로 찾음
    @SuppressLint("MissingPermission")
    override fun scanForNodes(): Flow<List<AirsBleNode>> = callbackFlow {
        val adapter = requireReadyAdapter() // 권한/Bluetooth ON 여부를 먼저 확인
        val scanner = adapter.bluetoothLeScanner ?: throw IllegalStateException("BLE scanner를 사용할 수 없습니다.") // BLE scan 객체 확보
        val foundNodes = linkedMapOf<String, AirsBleNode>() // 같은 노드를 중복 표시하지 않기 위한 address 기준 map
        val callback = object : ScanCallback() {
            // BLE scan 결과가 하나씩 들어올 때 호출
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val node = result.toAirsNode() ?: return // AIRS service/name 규칙에 맞지 않으면 무시
                foundNodes[node.id] = node // 같은 address의 최신 RSSI로 갱신
                trySend(foundNodes.values.sortedByDescending { it.rssi }.toList()) // 신호가 강한 노드가 위에 보이도록 정렬해서 전달
            }

            // BLE scan 자체가 실패했을 때 호출
            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan 실패: errorCode=$errorCode")) // ViewModel의 catch로 실패 전달
            }
        }

        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback) // 우선 전체 scan 후 앱에서 AIRS 노드만 필터링
        val timeoutJob = launch {
            delay(AirsBleConstants.SCAN_TIMEOUT_MILLIS) // 너무 오래 scan하지 않도록 제한
            if (foundNodes.isEmpty()) {
                if (AirsBleConstants.ADD_FAKE_NODE_WHEN_SCAN_EMPTY) {
                    trySend(listOf(createFakeNode())) // 노드가 없을 때 태블릿 UI 검증용 가짜 노드를 표시
                    close() // 가짜 노드를 내보낸 뒤 scan 종료
                } else {
                    close(IllegalStateException("AIRS 노드를 찾지 못했습니다. 노드가 설정 모드인지 확인해 주세요.")) // 발견된 노드가 없으면 실패로 종료
                }
            } else {
                close() // 노드를 하나 이상 찾았다면 현재 목록을 유지한 채 scan 종료
            }
        }

        awaitClose {
            timeoutJob.cancel() // Flow가 닫히면 timeout coroutine 정리
            scanner.stopScan(callback) // BLE scan 중지
        }
    }

    // 사용자가 선택한 노드에 GATT 연결을 맺고 AIRS characteristic을 찾음
    @SuppressLint("MissingPermission")
    override suspend fun connect(node: AirsBleNode) {
        if (node.isFakeNode()) {
            disconnect() // 실제 BLE 연결이 남아 있으면 먼저 정리
            delay(400) // 실제 연결 화면이 너무 빠르게 지나가지 않도록 짧게 대기
            connectedFakeNode = node // 이후 provisionWifi에서 가짜 노드임을 확인할 수 있게 저장
            return
        }

        val adapter = requireReadyAdapter() // 권한/Bluetooth ON 여부 확인
        disconnect() // 이전 연결이 남아 있으면 먼저 정리
        val device = runCatching { adapter.getRemoteDevice(node.id) }
            .getOrElse { throw IllegalArgumentException("BLE address가 올바르지 않습니다: ${node.id}") } // scan에서 받은 address로 remote device 확보

        suspendCancellableCoroutine { continuation ->
            connectContinuation = continuation // service discovery 완료까지 coroutine을 대기시킴
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE) // BLE transport로 GATT 연결 시작
            if (bluetoothGatt == null) {
                connectContinuation = null // 연결 시작 자체가 실패하면 continuation 정리
                continuation.resumeWithException(IllegalStateException("GATT 연결을 시작하지 못했습니다."))
            }
            continuation.invokeOnCancellation { disconnect() } // 연결 대기 중 취소되면 GATT 정리
        }
    }

    // 연결된 노드에 Wi-Fi 정보를 write하고 status notify를 Flow로 전달
    @SuppressLint("MissingPermission")
    override fun provisionWifi(
        node: AirsBleNode, // 연결된 AIRS 노드
        credentials: WifiCredentials // 사용자가 입력한 SSID/PW
    ): Flow<ProvisioningStatus> {
        if (node.isFakeNode()) {
            return fakeProvisionWifi(node, credentials) // 가짜 노드는 실제 BLE write 없이 UI 흐름만 검증
        }

        return callbackFlow {
            val gatt = bluetoothGatt ?: throw IllegalStateException("연결된 BLE 노드가 없습니다.") // connect()가 먼저 성공해야 함
            val wifiCharacteristic = wifiConfigCharacteristic ?: throw IllegalStateException("Wi-Fi Config characteristic을 찾지 못했습니다.") // write 대상 확인
            val statusCharacteristic = statusCharacteristic ?: throw IllegalStateException("Provision Status characteristic을 찾지 못했습니다.") // notify 대상 확인
            val events = this // callback에서 notify 상태를 보내기 위한 현재 Flow channel
            statusEvents = events // BLE notify callback이 이 channel로 상태를 보냄

            try {
                enableStatusNotifications(gatt, statusCharacteristic) // write 전에 notify를 먼저 켜서 상태를 놓치지 않게 함
                writeWifiPayload(gatt, wifiCharacteristic, WifiConfigPayload.build(credentials)) // SSID/PW JSON payload를 characteristic에 write
            } catch (error: Throwable) {
                statusEvents = null // 실패 시 notify channel 연결 해제
                close(error) // ViewModel의 catch로 실패 전달
                return@callbackFlow
            }

            awaitClose {
                if (statusEvents === events) {
                    statusEvents = null // 이 Flow가 닫힐 때 현재 notify channel만 정리
                }
            }
        }
    }

    // 현재 GATT 연결과 대기 중 작업을 정리
    @SuppressLint("MissingPermission")
    override fun disconnect() {
        failPendingOperations(IllegalStateException("BLE 연결을 정리했습니다.")) // 대기 중인 coroutine이 있으면 종료
        connectedFakeNode = null // 가짜 노드 연결 상태도 정리
        bluetoothGatt?.disconnect() // Android BLE 연결 해제 요청
        bluetoothGatt?.close() // GATT 자원 반납
        bluetoothGatt = null // 연결 객체 제거
        wifiConfigCharacteristic = null // 캐시된 characteristic 제거
        statusCharacteristic = null // 캐시된 characteristic 제거
        statusEvents = null // notify channel 제거
    }

    // AIRS service와 required characteristics를 찾아 field에 저장
    private fun cacheAirsCharacteristics(gatt: BluetoothGatt) {
        val service = gatt.getService(AirsBleConstants.PROVISIONING_SERVICE_UUID)
            ?: throw IllegalStateException("AIRS Provisioning Service를 찾지 못했습니다.") // firmware UUID 불일치 가능성
        val wifiCharacteristic = service.getCharacteristic(AirsBleConstants.WIFI_CONFIG_CHARACTERISTIC_UUID)
            ?: throw IllegalStateException("Wi-Fi Config characteristic을 찾지 못했습니다.") // write characteristic 누락
        val statusCharacteristic = service.getCharacteristic(AirsBleConstants.PROVISION_STATUS_CHARACTERISTIC_UUID)
            ?: throw IllegalStateException("Provision Status characteristic을 찾지 못했습니다.") // notify characteristic 누락

        val canWrite = wifiCharacteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 // WRITE WITH RESPONSE 가능 여부
        val canNotify = statusCharacteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 // NOTIFY 가능 여부
        if (!canWrite) throw IllegalStateException("Wi-Fi Config characteristic에 WRITE property가 없습니다.") // firmware characteristic property 문제
        if (!canNotify) throw IllegalStateException("Provision Status characteristic에 NOTIFY property가 없습니다.") // firmware characteristic property 문제

        this.wifiConfigCharacteristic = wifiCharacteristic // 이후 provisionWifi에서 재사용
        this.statusCharacteristic = statusCharacteristic // 이후 provisionWifi에서 재사용
    }

    // Status characteristic notify를 활성화
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun enableStatusNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            throw IllegalStateException("Status characteristic notify 활성화를 시작하지 못했습니다.") // Android API 호출 실패
        }
        val descriptor = characteristic.getDescriptor(AirsBleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
            ?: throw IllegalStateException("Status characteristic CCCD를 찾지 못했습니다.") // notify를 켤 descriptor 누락

        suspendCancellableCoroutine { continuation ->
            descriptorWriteContinuation = continuation // descriptor write 완료 callback까지 대기
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE // notify 활성화 값 설정
            if (!gatt.writeDescriptor(descriptor)) {
                descriptorWriteContinuation = null // 시작 실패 시 대기 상태 제거
                continuation.resumeWithException(IllegalStateException("Status characteristic CCCD write를 시작하지 못했습니다."))
            }
            continuation.invokeOnCancellation {
                if (descriptorWriteContinuation === continuation) {
                    descriptorWriteContinuation = null // 취소된 continuation은 callback에서 재사용하지 않음
                }
            }
        }
    }

    // Wi-Fi 설정 JSON payload를 Wi-Fi Config characteristic에 write
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun writeWifiPayload(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, payload: String) {
        suspendCancellableCoroutine { continuation ->
            wifiWriteContinuation = continuation // characteristic write 완료 callback까지 대기
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT // write with response 사용
            characteristic.value = payload.toByteArray(StandardCharsets.UTF_8) // JSON 문자열을 UTF-8 bytes로 변환
            if (!gatt.writeCharacteristic(characteristic)) {
                wifiWriteContinuation = null // 시작 실패 시 대기 상태 제거
                continuation.resumeWithException(IllegalStateException("Wi-Fi config write를 시작하지 못했습니다."))
            }
            continuation.invokeOnCancellation {
                if (wifiWriteContinuation === continuation) {
                    wifiWriteContinuation = null // 취소된 continuation은 callback에서 재사용하지 않음
                }
            }
        }
    }

    // Android 권한, Bluetooth adapter, Bluetooth ON 상태를 공통으로 확인
    private fun requireReadyAdapter(): BluetoothAdapter {
        if (!BleRuntimePermissions.hasAll(context)) {
            throw SecurityException("Bluetooth 권한이 없습니다. 앱 권한을 허용한 뒤 다시 시도해 주세요.") // runtime permission 미승인
        }
        val manager = context.getSystemService(BluetoothManager::class.java)
            ?: throw IllegalStateException("BluetoothManager를 사용할 수 없습니다.") // 기기에 Bluetooth stack이 없는 경우
        val adapter = manager.adapter ?: throw IllegalStateException("Bluetooth adapter를 사용할 수 없습니다.") // BLE 미지원 가능성
        if (!adapter.isEnabled) {
            throw IllegalStateException("휴대폰 Bluetooth를 켠 뒤 다시 시도해 주세요.") // 사용자 설정에서 Bluetooth가 꺼진 경우
        }
        return adapter
    }

    // BLE scan result가 AIRS 노드인지 판별하고 화면 모델로 변환
    @SuppressLint("MissingPermission")
    private fun ScanResult.toAirsNode(): AirsBleNode? {
        val serviceUuids = scanRecord?.serviceUuids.orEmpty() // advertising packet에 포함된 service UUID 목록
        val hasAirsService = serviceUuids.any { it == ParcelUuid(AirsBleConstants.PROVISIONING_SERVICE_UUID) } // AIRS service UUID 포함 여부
        val scanRecordName = scanRecord?.deviceName // advertising packet 안의 local name
        val deviceName = runCatching { device.name }.getOrNull() // Android가 알고 있는 device name
        val displayName = scanRecordName ?: deviceName ?: "AIRS node ${device.address.takeLast(5)}" // 이름이 없으면 address 일부로 표시
        val hasAirsName = displayName.startsWith(AirsBleConstants.NODE_NAME_PREFIX) // 이름 prefix fallback 판별

        if (!hasAirsService && !hasAirsName) return null // service UUID나 이름 규칙 둘 중 하나도 맞지 않으면 AIRS 노드가 아님

        return AirsBleNode(
            id = device.address, // 실제 BLE 연결에 사용할 MAC address
            displayName = displayName, // 화면에 보여줄 노드 이름
            rssi = rssi, // scan result의 신호 세기
            firmwareVersion = "unknown" // Device Info read는 다음 단계에서 보강할 값
        )
    }

    // 태블릿 UI 검증용 가짜 노드 모델 생성
    private fun createFakeNode(): AirsBleNode {
        return AirsBleNode(
            id = AirsBleConstants.FAKE_NODE_ID, // 실제 BLE address가 아닌 테스트 전용 ID
            displayName = AirsBleConstants.FAKE_NODE_NAME, // 화면에 표시할 가짜 노드 이름
            rssi = -1, // 실제 신호 세기가 아님을 구분하기 위한 값
            firmwareVersion = "fake-ui-test" // 실제 firmware가 아닌 UI 테스트용 버전
        )
    }

    // 현재 노드가 태블릿 UI 테스트용 가짜 노드인지 확인
    private fun AirsBleNode.isFakeNode(): Boolean {
        return id == AirsBleConstants.FAKE_NODE_ID // 실제 BLE address와 구분되는 테스트 ID인지 검사
    }

    // 가짜 노드를 대상으로 Wi-Fi provisioning UI 흐름을 검증
    private fun fakeProvisionWifi(
        node: AirsBleNode, // 태블릿 UI 테스트용 가짜 노드
        credentials: WifiCredentials // UI에서 선택/입력한 Wi-Fi 정보
    ): Flow<ProvisioningStatus> = kotlinx.coroutines.flow.flow {
        if (connectedFakeNode?.id != node.id) {
            throw IllegalStateException("가짜 노드 연결 상태가 아닙니다. 노드를 다시 선택해 주세요.") // 정상 흐름에서는 connect가 먼저 호출되어야 함
        }

        val preview = WifiConfigPayload.buildSafePreview(credentials) // 화면 검증용으로 비밀번호를 가린 payload
        emit(ProvisioningStatus(ProvisioningStatusType.Received, "테스트 모드: 실제 BLE write 없이 아래 payload를 확인합니다. $preview")) // 전송될 값 확인 상태
        delay(700) // 진행 상태 확인을 위한 짧은 대기
        emit(ProvisioningStatus(ProvisioningStatusType.WifiConnecting, "테스트 모드: 실제 노드 Wi-Fi 연결은 수행하지 않습니다.")) // 실제 노드가 없음을 명확히 표시
        delay(900) // 성공/실패 화면 전환 확인을 위한 대기

        if (credentials.ssid.contains("fail", ignoreCase = true)) {
            emit(ProvisioningStatus(ProvisioningStatusType.WifiFailed, "테스트 모드 실패: SSID에 fail이 포함되어 있습니다.")) // 실패 화면 테스트
            return@flow
        }

        emit(ProvisioningStatus(ProvisioningStatusType.WifiConnected, "테스트 모드 성공: 실제 노드 대신 UI 흐름만 완료했습니다.")) // 성공 화면 테스트
    }

    // notify로 받은 bytes를 provisioning status로 변환하고 Flow로 전달
    private fun handleStatusBytes(value: ByteArray) {
        val rawStatus = value.toString(StandardCharsets.UTF_8).trim() // firmware가 보낸 상태 문자열
        if (rawStatus.isBlank()) return // 빈 notify는 무시

        val status = rawStatus.toProvisioningStatus() // 문자열을 앱 상태 모델로 변환
        statusEvents?.trySend(status) // ViewModel collect 블록으로 상태 전달
        if (status.type == ProvisioningStatusType.WifiConnected || status.type == ProvisioningStatusType.WifiFailed) {
            statusEvents?.close() // 성공/실패는 terminal 상태이므로 Flow 종료
        }
    }

    // firmware 상태 문자열을 앱 enum으로 매핑
    private fun String.toProvisioningStatus(): ProvisioningStatus {
        val normalized = lowercase() // 대소문자 차이를 무시하기 위한 정규화
        val type = when {
            "wifi_connected" in normalized -> ProvisioningStatusType.WifiConnected // Wi-Fi 연결 성공 notify
            "wifi_failed" in normalized -> ProvisioningStatusType.WifiFailed // Wi-Fi 연결 실패 notify
            "wifi_connecting" in normalized -> ProvisioningStatusType.WifiConnecting // Wi-Fi 연결 시도 중 notify
            "received" in normalized -> ProvisioningStatusType.Received // payload 수신 notify
            else -> ProvisioningStatusType.Unknown // 약속하지 않은 상태 문자열
        }
        return ProvisioningStatus(type = type, message = "노드 상태: $this") // 원문 상태를 debugging용 메시지로 보존
    }

    // 연결 대기 coroutine을 성공/실패로 종료
    private fun resumeConnect(result: Result<Unit>) {
        val continuation = connectContinuation ?: return // 대기 중인 연결 작업이 없으면 무시
        connectContinuation = null // 같은 continuation을 두 번 resume하지 않게 제거
        result.resumeContinuation(continuation) // Result에 따라 resume 또는 exception 전달
    }

    // descriptor write 대기 coroutine을 성공/실패로 종료
    private fun resumeDescriptorWrite(result: Result<Unit>) {
        val continuation = descriptorWriteContinuation ?: return // 대기 중인 descriptor write가 없으면 무시
        descriptorWriteContinuation = null // 중복 resume 방지
        result.resumeContinuation(continuation) // Result에 따라 resume 또는 exception 전달
    }

    // Wi-Fi payload write 대기 coroutine을 성공/실패로 종료
    private fun resumeWifiWrite(result: Result<Unit>) {
        val continuation = wifiWriteContinuation ?: return // 대기 중인 characteristic write가 없으면 무시
        wifiWriteContinuation = null // 중복 resume 방지
        result.resumeContinuation(continuation) // Result에 따라 resume 또는 exception 전달
    }

    // 연결/descriptor/write 작업 중인 coroutine이 있으면 모두 실패로 종료
    private fun failPendingOperations(error: Throwable) {
        resumeConnect(Result.failure(error)) // 연결 대기 중이면 실패 처리
        resumeDescriptorWrite(Result.failure(error)) // notify 설정 대기 중이면 실패 처리
        resumeWifiWrite(Result.failure(error)) // Wi-Fi write 대기 중이면 실패 처리
    }
}

private fun Result<Unit>.resumeContinuation(continuation: CancellableContinuation<Unit>) { // Result를 CancellableContinuation에 적용하는 helper
    if (!continuation.isActive) return // 이미 취소/완료된 coroutine이면 아무것도 하지 않음
    fold(
        onSuccess = { continuation.resume(Unit) }, // 성공이면 정상 재개
        onFailure = { continuation.resumeWithException(it) } // 실패이면 예외로 재개
    )
}
