package com.airs.android

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.airs.android.provisioning.data.AndroidBleProvisioningRepository
import com.airs.android.provisioning.data.AndroidWifiNetworkRepository
import com.airs.android.provisioning.data.BleRuntimePermissions
import com.airs.android.provisioning.ui.BlePermissionScreen
import com.airs.android.provisioning.ui.ProvisioningRoute
import com.airs.android.provisioning.ui.ProvisioningViewModel
import com.airs.android.ui.theme.AIRSTheme

class MainActivity : ComponentActivity() { // MainActivity가 Android 앱의 시작 화면
    private var blePermissionsGranted by mutableStateOf(false) // Bluetooth runtime permission 승인 여부를 Compose가 볼 수 있게 저장
    private val blePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        blePermissionsGranted = BleRuntimePermissions.hasAll(this) // 사용자가 권한 창에 응답한 뒤 현재 승인 상태를 다시 계산
    }
    private val provisioningViewModel: ProvisioningViewModel by viewModels {
        object : ViewModelProvider.Factory { // 실제 Android BLE repository를 ViewModel에 넣기 위한 factory
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProvisioningViewModel(
                    bleRepository = AndroidBleProvisioningRepository(applicationContext), // 실제 BLE scan을 먼저 수행하고, 결과가 비었을 때만 테스트용 가짜 노드를 보여줌
                    wifiNetworkRepository = AndroidWifiNetworkRepository(applicationContext) // Android 기기의 Wi-Fi SSID 목록을 읽는 구현 사용
                ) as T
            }
        }
    }

    // 앱 화면이 처음 만들어질 때 실행되는 함수
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // 부모 Activity의 기본 초기화 로직을 먼저 실행
        blePermissionsGranted = BleRuntimePermissions.hasAll(this) // 이미 권한이 있으면 바로 provisioning 화면을 보여줄 수 있게 함
        enableEdgeToEdge() // 상태바와 내비게이션바 영역까지 자연스럽게 화면을 확장
        setContent { // XML layout 대신 Compose UI를 Activity 화면으로 설정
            AIRSTheme(dynamicColor = false) { // AIRS 공통 테마를 적용하고 기기별 동적 색상 변화는 끔
                if (blePermissionsGranted) {
                    ProvisioningRoute(viewModel = provisioningViewModel) // 권한이 있으면 실제 BLE Wi-Fi provisioning 화면 표시
                } else {
                    BlePermissionScreen(onRequestPermissions = ::requestBlePermissions) // 권한이 없으면 권한 요청 화면 표시
                }
            }
        }
        if (!blePermissionsGranted) {
            requestBlePermissions() // 첫 실행 시 바로 Android 권한 승인 창을 띄움
        }
    }

    // Android 버전에 필요한 Bluetooth 권한 요청
    private fun requestBlePermissions() {
        blePermissionLauncher.launch(BleRuntimePermissions.requiredPermissions()) // Activity Result API로 권한 요청 실행
    }
}
