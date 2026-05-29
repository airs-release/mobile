package com.airs.android.provisioning.data

import com.airs.android.provisioning.model.ProvisioningStatusType
import com.airs.android.provisioning.model.WifiCredentials
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockBleProvisioningRepositoryTest { // Mock BLE repository가 앱 흐름 검증에 필요한 상태를 내보내는지 확인
    private val repository = MockBleProvisioningRepository() // 테스트 대상 mock repository

    @Test
    // scan 결과로 태블릿 테스트용 가짜 노드 하나가 표시되는지 확인
    fun scanForNodes_emitsDiscoveredAirsNodes() = runTest {
        val emissions = repository.scanForNodes().toList() // flow가 내보낸 모든 노드 목록을 수집

        assertEquals(1, emissions.size) // mock scan은 태블릿 테스트용 결과를 한 번 내보냄
        assertEquals("AIRS-FAKE-0001", emissions.first().first().displayName) // 가짜 노드 하나가 표시되어야 함
        assertTrue(emissions.first().first().firmwareVersion.startsWith("mock")) // 실제 firmware가 아닌 mock임을 구분할 수 있어야 함
    }

    @Test
    // 정상 SSID를 입력하면 Wi-Fi 연결 성공 상태까지 도달하는지 확인
    fun provisionWifi_withValidSsid_emitsWifiConnected() = runTest {
        val node = repository.scanForNodes().toList().last().first() // mock scan 결과에서 테스트용 노드 선택
        val credentials = WifiCredentials(ssid = "AIRS-LAB", password = "password") // 성공 시나리오용 Wi-Fi 정보

        val statuses = repository.provisionWifi(node, credentials).toList() // Wi-Fi 전달 상태들을 모두 수집

        assertEquals(ProvisioningStatusType.Received, statuses[0].type) // 첫 상태는 payload 수신이어야 함
        assertEquals(ProvisioningStatusType.WifiConnecting, statuses[1].type) // 두 번째 상태는 Wi-Fi 연결 시도여야 함
        assertEquals(ProvisioningStatusType.WifiConnected, statuses[2].type) // 마지막 상태는 Wi-Fi 연결 성공이어야 함
        assertEquals(3, statuses.size) // MQTT publish는 이 mock Wi-Fi provisioning 성공 기준에 포함하지 않음
    }

    @Test
    // fail이 포함된 SSID를 입력하면 실패 상태를 내보내는지 확인
    fun provisionWifi_withFailSsid_emitsWifiFailed() = runTest {
        val node = repository.scanForNodes().toList().last().first() // mock scan 결과에서 테스트용 노드 선택
        val credentials = WifiCredentials(ssid = "AIRS-fail-test", password = "password") // 실패 시나리오용 Wi-Fi 정보

        val statuses = repository.provisionWifi(node, credentials).toList() // Wi-Fi 전달 상태들을 모두 수집

        assertEquals(ProvisioningStatusType.Received, statuses[0].type) // 실패하더라도 payload 수신 상태는 먼저 와야 함
        assertEquals(ProvisioningStatusType.WifiConnecting, statuses[1].type) // Wi-Fi 연결 시도 상태가 와야 함
        assertEquals(ProvisioningStatusType.WifiFailed, statuses[2].type) // 마지막 상태는 Wi-Fi 실패여야 함
        assertEquals(3, statuses.size) // 실패 후에는 추가 성공 상태를 보내지 않아야 함
    }
}
