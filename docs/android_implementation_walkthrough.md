# AIRS Android 구현 Walkthrough

작성일: 2026-05-28

이 문서는 AIRS Wi-Fi provisioning Android 구현을 학습용으로 설명한다.
단순히 "무엇을 만들었다"가 아니라, "왜 만들었는지", "AIRS 전체 흐름에서 어디에 들어가는지", "Kotlin 코드를 어떻게 읽어야 하는지"를 정리한다.

## 1. 구현 전에 무엇을 하고자 했는가

현재 AIRS의 가장 중요한 앱 구현 목표는 센서 노드에 Wi-Fi 정보를 전달하는 것이다.
센서 노드는 Wi-Fi에 연결되어야 MQTT publish를 할 수 있는데, 매번 breadboard나 PC에 직접 연결해서 Wi-Fi를 설정하는 방식은 불편하다.

따라서 앱이 해야 할 일은 아래와 같다.

```text
사용자
-> Android 앱 실행
-> 주변 AIRS 노드 감지
-> 노드 선택
-> Wi-Fi SSID/PW 입력
-> BLE로 노드에 Wi-Fi 정보 전달
-> 노드가 Wi-Fi 접속
-> 노드가 MQTT publish
```

구현 기준은 Android 휴대폰이다.
현재 Android 휴대폰이 없으면 Samsung 태블릿을 임시 테스트 장비로 사용할 수 있지만, 앱 내부 흐름과 실제 실행 경로는 휴대폰 기준으로 설계한다.
실제 AIRS 노드는 아직 없으므로 앱 내부 흐름은 mock으로 검증하고, 실제 실행 경로에는 Native Android BLE 구현을 붙여 휴대폰 검증을 준비한다.

이번 구현의 목적은 다음이다.

1. 실제 AIRS 노드 없이도 앱 화면 흐름을 먼저 만든다.
2. 실제 BLE 구현을 붙일 자리를 미리 정한다.
3. ViewModel과 UI가 실제 BLE API에 직접 의존하지 않도록 한다.
4. Wi-Fi payload 생성 로직을 먼저 검증한다.
5. Kotlin/Compose/MVVM 구조를 공부할 수 있는 기준 코드를 만든다.

## 2. 무엇을 위해 추가했는가

이번에 추가한 핵심은 `mock-first architecture`이다.

```text
지금:
ProvisioningScreen
-> ProvisioningViewModel
-> BleProvisioningRepository
-> MockBleProvisioningRepository

나중:
ProvisioningScreen
-> ProvisioningViewModel
-> BleProvisioningRepository
-> AndroidBleProvisioningRepository
-> BluetoothLeScanner / BluetoothGatt
```

즉, 화면과 ViewModel은 "BLE가 진짜인지 mock인지"를 몰라도 된다.
이 구조를 만든 이유는 mock 테스트와 Android 휴대폰의 실제 BLE 실행 경로를 같은 화면/ViewModel 위에서 돌리기 위해서이다.

## 3. AIRS 프로젝트에서 실제 동작 예시

현재 mock 앱에서 사용자가 보는 흐름은 실제 제품 흐름을 흉내 낸 것이다.

### 3.1 성공 예시

```text
1. 테스트에서 mock 노드 검색을 시작한다.
2. 앱이 AIRS-SETUP-0001 노드를 발견한 것처럼 표시한다.
3. 사용자가 AIRS-SETUP-0001을 선택한다.
4. 앱이 mock 연결 중 상태를 보여준다.
5. 사용자가 SSID = AIRS-LAB, PW = password 를 입력한다.
6. 앱이 mock repository에 Wi-Fi 정보를 전달한다.
7. mock repository가 아래 상태를 순서대로 보낸다.
   - received
   - wifi_connecting
   - wifi_connected
8. 앱은 성공 화면을 보여준다.
```

### 3.2 실패 예시

```text
1. 사용자가 SSID에 fail을 포함해서 입력한다.
   예: AIRS-fail-test
2. mock repository가 의도적으로 wifi_failed 상태를 보낸다.
3. 앱은 실패 화면을 보여준다.
4. 사용자는 다시 검색하거나 Wi-Fi 입력 화면으로 돌아갈 수 있다.
```

이 실패 예시는 실제 노드가 없을 때도 실패 UI와 복구 흐름을 확인하기 위한 장치이다.

## 4. 전체 코드 흐름

```text
MainActivity
-> ProvisioningRoute
-> ProvisioningScreen
-> 사용자의 버튼 클릭
-> ProvisioningViewModel 함수 호출
-> BleProvisioningRepository 인터페이스 호출
-> MockBleProvisioningRepository가 fake 결과 emit
-> ViewModel의 uiState 변경
-> Compose가 uiState를 보고 화면 재구성
```

핵심은 `uiState`이다.
Compose는 화면을 직접 수정하는 방식이 아니라, 상태가 바뀌면 화면을 다시 그리는 방식으로 동작한다.

Java Swing이나 Android XML 방식에 익숙하면 아래처럼 생각하면 된다.

```text
예전 방식:
button.setText(...)
textView.setVisibility(...)
adapter.notifyDataSetChanged()

Compose 방식:
uiState = uiState.copy(step = ProvisioningStep.Success)
-> Compose가 Success 화면을 다시 그림
```

## 5. 추가/수정한 파일의 역할

| 파일 | 역할 | 왜 필요한가 |
|---|---|---|
| `MainActivity.kt` | 앱 시작점 | Android가 처음 실행하는 Activity이며 Compose 화면을 붙인다. |
| `ProvisioningModels.kt` | 상태/데이터 모델 | 노드, Wi-Fi 입력값, 화면 단계, 진행 상태를 타입으로 고정한다. |
| `BleProvisioningRepository.kt` | BLE 기능 계약 | 실제 BLE와 mock BLE를 같은 방식으로 호출하기 위한 인터페이스이다. |
| `MockBleProvisioningRepository.kt` | 가짜 BLE 구현 | 실제 AIRS 노드 없이도 앱 흐름을 테스트한다. |
| `AndroidBleProvisioningRepository.kt` | 실제 BLE 구현 | Android 휴대폰에서 scan, GATT connect, Wi-Fi write, status notify를 수행한다. |
| `BleRuntimePermissions.kt` | 권한 helper | Android 버전에 맞는 Bluetooth runtime permission 목록과 승인 여부를 계산한다. |
| `BlePermissionScreen.kt` | 권한 화면 | 실제 BLE scan 전에 사용자에게 Bluetooth 권한을 요청한다. |
| `WifiConfigPayload.kt` | Wi-Fi JSON 생성 | 노드에 전달할 SSID/PW payload를 만든다. |
| `ProvisioningViewModel.kt` | 상태 관리 | 화면 이벤트를 받아 scan/connect/provisioning 흐름을 제어한다. |
| `ProvisioningScreen.kt` | Compose UI | 현재 상태에 맞춰 화면을 그린다. |
| `WifiConfigPayloadTest.kt` | 단위 테스트 | payload 생성과 비밀번호 마스킹을 검증한다. |

## 6. Kotlin 문법을 먼저 이해하기

### 6.1 `val`과 `var`

```kotlin
val credentials = WifiCredentials(...)
var uiState by mutableStateOf(...)
```

- `val`: 값을 다시 대입할 수 없다. Java의 `final` 변수에 가깝다.
- `var`: 값을 다시 대입할 수 있다.

앱 상태인 `uiState`는 계속 바뀌어야 하므로 `var`이다.
반대로 함수 안에서 한 번 만든 `credentials`는 다시 바꿀 필요가 없으므로 `val`이다.

### 6.2 `data class`

```kotlin
data class WifiCredentials(
    val ssid: String,
    val password: String
)
```

Java로 치면 값을 담는 DTO에 가깝다.
Kotlin의 `data class`는 생성자, `equals`, `hashCode`, `toString`, `copy` 같은 기능을 자동으로 만들어준다.

### 6.3 `enum class`

```kotlin
enum class ProvisioningStep {
    Start,
    Scanning,
    NodeList,
    Connecting,
    WifiInput,
    Provisioning,
    Success,
    Failure
}
```

정해진 값 중 하나만 허용할 때 사용한다.
화면 단계는 임의 문자열보다 enum으로 고정하는 것이 안전하다.

### 6.4 `Flow`

```kotlin
fun scanForNodes(): Flow<List<AirsBleNode>>
```

`Flow`는 시간이 지나면서 여러 값을 순서대로 내보내는 stream이다.
BLE 스캔은 한 번에 끝나는 값이 아니라, 시간이 지나면서 "노드 발견됨", "노드 더 발견됨" 같은 결과가 생긴다.
그래서 `Flow`가 잘 맞는다.

### 6.5 `@Composable`

```kotlin
@Composable
fun ProvisioningScreen(...)
```

Compose 화면 함수라는 뜻이다.
XML layout 파일 대신 Kotlin 함수가 UI가 된다.

## 7. `MainActivity.kt` line-by-line

파일: `android/app/src/main/java/com/airs/android/MainActivity.kt`

| 줄 | 코드 | 설명 |
|---|---|---|
| 1 | `package com.airs.android` | 이 파일이 `com.airs.android` 패키지에 속한다. Java의 package 선언과 같다. |
| 3 | `import android.os.Bundle` | Android Activity 생명주기 함수에서 사용하는 `Bundle`을 가져온다. |
| 4 | `import androidx.activity.ComponentActivity` | Compose Activity의 기반이 되는 AndroidX Activity 클래스이다. |
| 5 | `import androidx.activity.compose.setContent` | Activity에 Compose UI를 붙이기 위한 함수이다. |
| 6 | `import androidx.activity.enableEdgeToEdge` | 상태바/내비게이션바까지 자연스럽게 화면을 확장하는 설정이다. |
| 7 | `import androidx.activity.viewModels` | Activity에서 ViewModel을 생성/보관하기 위한 delegate이다. |
| 8 | `import com.airs.android.provisioning.ui.ProvisioningRoute` | 실제 Wi-Fi provisioning 화면 진입 함수를 가져온다. |
| 9 | `import com.airs.android.provisioning.ui.ProvisioningViewModel` | 화면 상태를 관리하는 ViewModel 클래스를 가져온다. |
| 10 | `import com.airs.android.ui.theme.AIRSTheme` | 앱 전체에 적용할 Compose theme을 가져온다. |
| 12 | `class MainActivity : ComponentActivity()` | `MainActivity`가 `ComponentActivity`를 상속한다. Java의 `extends`와 같다. |
| 13 | `private val provisioningViewModel: ProvisioningViewModel by viewModels()` | Activity 생명주기에 맞춰 ViewModel을 생성한다. `private`라 이 Activity 안에서만 쓴다. |
| 15 | `override fun onCreate(savedInstanceState: Bundle?)` | Activity가 처음 생성될 때 Android가 호출한다. `Bundle?`의 `?`는 null 가능성을 뜻한다. |
| 16 | `super.onCreate(savedInstanceState)` | 부모 클래스의 기본 초기화 로직을 먼저 실행한다. Java에서도 자주 쓰는 패턴이다. |
| 17 | `enableEdgeToEdge()` | 화면을 edge-to-edge 스타일로 설정한다. |
| 18 | `setContent {` | 이 Activity의 화면을 Compose로 정의하기 시작한다. |
| 19 | `AIRSTheme(dynamicColor = false) {` | AIRS 공통 테마를 적용한다. dynamic color는 꺼서 기기별 색 변화가 작게 했다. |
| 20 | `ProvisioningRoute(viewModel = provisioningViewModel)` | 실제 화면을 띄운다. ViewModel을 화면에 넘겨준다. |
| 21-22 | `}` | theme와 setContent 블록을 닫는다. |
| 23-24 | `}` | `onCreate` 함수와 `MainActivity` 클래스를 닫는다. |

## 8. `ProvisioningModels.kt` line-by-line

파일: `android/app/src/main/java/com/airs/android/provisioning/model/ProvisioningModels.kt`

| 줄 | 코드 | 설명 |
|---|---|---|
| 1 | `package ...model` | 데이터 모델 파일임을 패키지로 구분한다. |
| 3 | `data class AirsBleNode(` | BLE로 발견한 AIRS 노드 하나를 표현한다. |
| 4 | `val id: String` | 앱 내부에서 노드를 구분하기 위한 ID이다. mock에서는 임시 ID를 쓴다. |
| 5 | `val displayName: String` | 사용자에게 보여줄 노드 이름이다. 예: `AIRS-SETUP-0001`. |
| 6 | `val rssi: Int` | BLE 신호 세기이다. 값이 0에 가까울수록 보통 신호가 강하다. |
| 7 | `val firmwareVersion: String` | 노드 firmware 버전이다. 실제 노드 연결 후 Device Info에서 읽을 값이다. |
| 8 | `)` | `AirsBleNode` 정의를 끝낸다. |
| 10 | `data class WifiCredentials(` | 사용자가 입력한 Wi-Fi 정보를 묶는 값 객체이다. |
| 11 | `val ssid: String` | Wi-Fi 이름이다. |
| 12 | `val password: String` | Wi-Fi 비밀번호이다. 화면/로그 노출을 조심해야 한다. |
| 15 | `enum class ProvisioningStep` | 앱 화면 흐름의 현재 단계를 나타낸다. |
| 16 | `Start` | 아직 검색을 시작하지 않은 초기 상태이다. |
| 17 | `Scanning` | 주변 노드를 검색 중인 상태이다. |
| 18 | `NodeList` | 검색된 노드 목록을 보여주는 상태이다. |
| 19 | `Connecting` | 선택한 노드에 연결 중인 상태이다. |
| 20 | `WifiInput` | SSID/PW를 입력받는 상태이다. |
| 21 | `Provisioning` | Wi-Fi 정보를 전송하고 결과를 기다리는 상태이다. |
| 22 | `Success` | mock 기준 Wi-Fi 연결이 성공한 상태이다. |
| 23 | `Failure` | 연결 또는 전송 중 실패한 상태이다. |
| 26 | `enum class ProvisioningStatusType(val label: String)` | 상태 종류와 UI 표시 label을 함께 정의한다. |
| 27-30 | `Received` ... `WifiFailed` | 노드가 보내는 진행 상태를 mock으로 표현한다. |
| 34 | `data class ProvisioningStatus(` | 진행 상태 하나를 나타내는 값 객체이다. |
| 35 | `val type: ProvisioningStatusType` | 상태 종류이다. |
| 36 | `val message: String` | 사용자에게 보여줄 상세 메시지이다. |
| 39 | `data class ProvisioningUiState(` | Compose 화면이 읽을 모든 상태를 모은다. |
| 40 | `val step...` | 현재 화면 단계이다. 기본은 `Start`. |
| 41 | `val nodes...` | 검색된 노드 목록이다. 기본은 빈 리스트. |
| 42 | `val selectedNode...` | 사용자가 선택한 노드이다. 아직 없으면 null. |
| 43 | `val ssid...` | 입력 중인 SSID이다. |
| 44 | `val password...` | 입력 중인 비밀번호이다. |
| 45 | `val passwordVisible...` | 비밀번호를 화면에 보이게 할지 여부이다. |
| 46 | `val statuses...` | 노드 진행 상태 목록이다. |
| 47 | `val safePayloadPreview...` | 비밀번호를 마스킹한 payload preview이다. |
| 48 | `val errorMessage...` | 사용자에게 보여줄 오류 메시지이다. |
| 50-51 | `val canSendWifiConfig get() = ssid.isNotBlank()` | SSID가 있을 때만 전송 버튼을 활성화한다. |

## 9. `WifiConfigPayload.kt` line-by-line

파일: `android/app/src/main/java/com/airs/android/provisioning/domain/WifiConfigPayload.kt`

| 줄 | 코드 | 설명 |
|---|---|---|
| 1 | `package ...domain` | payload 생성은 UI도 data도 아닌 domain 성격이라 domain 패키지에 둔다. |
| 3 | `import WifiCredentials` | SSID/PW 객체를 사용하기 위해 import한다. |
| 5 | `object WifiConfigPayload` | 싱글턴 utility 객체이다. Java static helper class처럼 쓴다. |
| 6 | `fun build(credentials...)` | 실제 노드에 보낼 JSON 문자열을 만든다. |
| 7 | `return ...` | SSID/PW를 JSON 형태로 넣는다. 특수문자는 `escapeJson()`으로 처리한다. |
| 10 | `fun buildSafePreview(...)` | 화면에 보여줄 안전한 preview를 만든다. |
| 11 | `val maskedPassword = "*".repeat(...)` | 실제 비밀번호 대신 최대 8개의 `*`만 표시한다. |
| 12 | `return ...` | SSID는 보여주고 password는 마스킹한 JSON preview를 만든다. |
| 16 | `private fun String.escapeJson()` | 문자열을 JSON 안에 넣기 전에 특수문자를 escape한다. |
| 17 | `return buildString` | 문자열을 효율적으로 조립한다. |
| 18 | `for (character in this@escapeJson)` | 원래 문자열의 문자를 하나씩 확인한다. |
| 19 | `when (character)` | C/Java의 switch와 비슷하다. 문자 종류별로 처리한다. |
| 20 | `'\\' -> append("\\\\")` | 역슬래시는 JSON에서 `\\`로 바꾼다. |
| 21 | `'"' -> append("\\\"")` | 큰따옴표는 JSON에서 `\"`로 바꾼다. |
| 22 | `'\n' -> append("\\n")` | 줄바꿈은 `\n`으로 바꾼다. |
| 23 | `'\r' -> append("\\r")` | carriage return을 escape한다. |
| 24 | `'\t' -> append("\\t")` | tab을 escape한다. |
| 25 | `else -> append(character)` | 특별한 문자가 아니면 그대로 넣는다. |

## 10. `BleProvisioningRepository.kt` line-by-line

파일: `android/app/src/main/java/com/airs/android/provisioning/data/BleProvisioningRepository.kt`

| 줄 | 코드 | 설명 |
|---|---|---|
| 1 | `package ...data` | 외부 통신 경계를 data 패키지에 둔다. |
| 3-6 | `import ...` | 노드, 상태, Wi-Fi 입력값, Flow 타입을 가져온다. |
| 8 | `interface BleProvisioningRepository` | BLE provisioning 기능의 계약이다. 실제 구현과 mock 구현이 이 계약을 따른다. |
| 9 | `fun scanForNodes(): Flow<List<AirsBleNode>>` | 노드 검색 결과를 stream으로 반환한다. |
| 11 | `suspend fun connect(node: AirsBleNode)` | 선택한 노드에 연결한다. 시간이 걸리므로 suspend 함수로 둔다. |
| 13-16 | `fun provisionWifi(...): Flow<ProvisioningStatus>` | Wi-Fi 정보를 전달하고 진행 상태를 stream으로 반환한다. |

## 11. `MockBleProvisioningRepository.kt` line-by-line

파일: `android/app/src/main/java/com/airs/android/provisioning/data/MockBleProvisioningRepository.kt`

| 줄 | 코드 | 설명 |
|---|---|---|
| 1 | `package ...data` | repository 구현체이므로 data 패키지에 둔다. |
| 3-9 | `import ...` | 모델, delay, Flow, flow builder를 가져온다. |
| 11 | `class MockBleProvisioningRepository : BleProvisioningRepository` | 인터페이스를 구현하는 mock 클래스이다. |
| 12 | `override fun scanForNodes()` | 실제 BLE scan 대신 가짜 노드 목록을 반환한다. |
| 13 | `delay(600)` | 검색하는 듯한 시간을 만든다. |
| 14-23 | `emit(listOf(AirsBleNode(...)))` | 첫 번째 mock 노드 `AIRS-SETUP-0001`을 발견한 것처럼 내보낸다. |
| 24 | `delay(700)` | 시간이 조금 더 지난 상황을 만든다. |
| 25-40 | `emit(listOf(... two nodes ...))` | 두 번째 mock 노드까지 추가로 발견된 상황을 만든다. |
| 43 | `override suspend fun connect(node...)` | 노드 연결 함수이다. 현재는 실제 연결 없이 기다리기만 한다. |
| 44 | `delay(700)` | 연결 중 화면을 확인할 수 있도록 시간을 둔다. |
| 47-50 | `override fun provisionWifi(...)` | Wi-Fi 정보 전달 mock 흐름을 시작한다. |
| 51 | `emit(Received...)` | 노드가 설정값을 받았다는 상태를 보낸다. |
| 52 | `delay(700)` | 다음 상태로 넘어가기 전 대기한다. |
| 53 | `emit(WifiConnecting...)` | 노드가 Wi-Fi 연결을 시도 중이라는 상태를 보낸다. |
| 54 | `delay(900)` | 연결 시도 시간을 흉내 낸다. |
| 56 | `if (credentials.ssid.contains("fail"...))` | 실패 흐름을 테스트하기 위한 조건이다. |
| 57 | `emit(WifiFailed...)` | 실패 상태를 보낸다. |
| 58 | `return@flow` | 실패했으므로 이후 성공 상태를 보내지 않고 flow를 끝낸다. |
| 61 | `emit(WifiConnected...)` | 성공 케이스에서는 Wi-Fi 연결 성공 상태를 보낸다. |
| 61 | `emit(WifiConnected...)` | Wi-Fi provisioning의 최종 성공 상태를 보낸다. |

## 12. `ProvisioningViewModel.kt` 핵심 line-by-line

파일: `android/app/src/main/java/com/airs/android/provisioning/ui/ProvisioningViewModel.kt`

| 줄 | 코드 | 설명 |
|---|---|---|
| 20-22 | `class ProvisioningViewModel(...): ViewModel()` | 화면 상태를 관리하는 클래스이다. 기본 repository로 mock 구현을 주입한다. |
| 23-24 | `var uiState by mutableStateOf(...) private set` | Compose가 관찰할 상태이다. 외부에서는 읽기만 가능하고 수정은 ViewModel 함수로만 한다. |
| 26 | `private var scanJob: Job? = null` | 진행 중인 scan coroutine을 기억한다. |
| 27 | `private var provisioningJob: Job? = null` | 진행 중인 Wi-Fi provisioning coroutine을 기억한다. |
| 29 | `fun startScan()` | 사용자가 검색 시작 버튼을 눌렀을 때 호출된다. |
| 30 | `scanJob?.cancel()` | 기존 검색이 있으면 취소한다. 중복 검색을 막는다. |
| 31 | `uiState = ProvisioningUiState(step = Scanning)` | 화면을 검색 중 상태로 바꾼다. |
| 32 | `scanJob = viewModelScope.launch` | ViewModel 생명주기에 묶인 coroutine을 시작한다. |
| 33 | `repository.scanForNodes()` | repository에 노드 검색을 요청한다. 현재는 mock 결과가 온다. |
| 34-39 | `.catch { error -> ... }` | scan 중 예외가 나면 실패 상태와 오류 메시지를 넣는다. |
| 40-45 | `.collect { nodes -> ... }` | Flow가 내보내는 노드 목록을 받을 때마다 UI 상태를 갱신한다. |
| 49 | `fun selectNode(node...)` | 사용자가 노드 카드를 눌렀을 때 호출된다. |
| 50 | `scanJob?.cancel()` | 노드를 선택했으므로 더 이상 scan을 계속하지 않는다. |
| 51-55 | `uiState = uiState.copy(...)` | 선택 노드와 연결 중 상태를 반영한다. |
| 56 | `viewModelScope.launch` | 연결 작업을 비동기로 실행한다. |
| 57 | `runCatching { repository.connect(node) }` | 연결 성공/실패를 Result 스타일로 처리한다. |
| 58-60 | `.onSuccess { ... WifiInput }` | 연결 성공 시 Wi-Fi 입력 화면으로 이동한다. |
| 61-66 | `.onFailure { ... Failure }` | 연결 실패 시 실패 화면으로 이동한다. |
| 70-80 | `updateSsid`, `updatePassword`, `togglePasswordVisible` | 입력창 값과 비밀번호 표시 상태를 업데이트한다. |
| 82 | `fun sendWifiConfig()` | 사용자가 Wi-Fi 정보 전송 버튼을 눌렀을 때 호출된다. |
| 83 | `val node = uiState.selectedNode ?: return` | 선택된 노드가 없으면 함수 종료. Kotlin의 Elvis operator `?:` 사용 예시이다. |
| 84-87 | `val credentials = WifiCredentials(...)` | 현재 입력값으로 Wi-Fi 정보 객체를 만든다. |
| 89-92 | `if (credentials.ssid.isBlank())` | SSID가 비어 있으면 오류 메시지를 표시하고 종료한다. |
| 94 | `provisioningJob?.cancel()` | 기존 전송 작업이 있으면 취소한다. |
| 95-100 | `uiState = uiState.copy(step = Provisioning, ...)` | 전송 중 상태로 바꾸고, 안전한 payload preview를 만든다. |
| 102 | `provisioningJob = viewModelScope.launch` | Wi-Fi 전달 흐름을 coroutine으로 시작한다. |
| 103 | `repository.provisionWifi(node, credentials)` | repository에 Wi-Fi 전달을 요청한다. |
| 104-109 | `.catch { error -> ... }` | 전달 중 오류가 생기면 실패 상태로 바꾼다. |
| 110 | `.collect { status -> ... }` | repository가 보내는 진행 상태를 하나씩 받는다. |
| 111-115 | `when (status.type)` | 상태 종류에 따라 다음 화면 단계를 결정한다. |
| 116-119 | `uiState = uiState.copy(...)` | 새 상태를 목록에 추가하고 화면 단계를 갱신한다. |
| 124-128 | `retryFromStart()` | 진행 중 작업을 취소하고 초기 상태로 되돌린다. |
| 130-138 | `backToWifiInput()` | Wi-Fi 입력 화면으로 돌아간다. 실패 후 재입력에 사용한다. |

## 13. `ProvisioningScreen.kt` 동작 단위별 설명

파일: `android/app/src/main/java/com/airs/android/provisioning/ui/ProvisioningScreen.kt`

이 파일은 길기 때문에 같은 패턴의 UI helper까지 한 줄씩 모두 반복 설명하면 오히려 읽기 어렵다.
대신 실제 동작에 영향을 주는 블록을 line block 단위로 설명한다.

| 줄 | 코드 블록 | 설명 |
|---|---|---|
| 1-42 | package/import | Compose UI를 그리기 위한 layout, Material3, 색상, 모델, theme을 가져온다. |
| 44-49 | `private val AppBackground...` | 화면에서 반복 사용할 색상 상수이다. |
| 51-64 | `ProvisioningRoute(viewModel)` | ViewModel의 상태와 함수를 `ProvisioningScreen`에 연결한다. `viewModel::startScan`은 함수 참조이다. |
| 66-77 | `ProvisioningScreen(...)` 파라미터 | 화면은 상태와 callback만 받는다. 그래서 ViewModel 없이 Preview도 가능하다. |
| 78-88 | `Surface`와 `Column` | 전체 배경, 스크롤, 여백, 세로 배치를 정의한다. |
| 89 | `HeaderCard()` | 화면 상단 설명 카드를 표시한다. |
| 90 | `StepSummary(uiState.step)` | 현재 단계 표시 바를 그린다. |
| 92-98 | `uiState.errorMessage?.let` | 오류 메시지가 있을 때만 오류 카드를 보여준다. `?.let`은 null이 아닐 때만 실행된다. |
| 100-135 | `when (uiState.step)` | 현재 단계에 따라 다른 화면 조각을 보여준다. Compose에서 상태 기반 화면 전환의 핵심이다. |
| 101 | `Start -> StartContent` | 초기 시작 화면이다. |
| 102 | `Scanning -> ScanningContent` | mock scan 진행 화면이다. |
| 103 | `NodeList -> NodeListContent` | 발견된 노드 목록을 보여준다. |
| 104 | `Connecting -> ConnectingContent` | mock 연결 중 화면이다. |
| 105-111 | `WifiInput -> WifiInputContent` | SSID/PW 입력 화면이다. 입력 변경 callback을 넘긴다. |
| 113 | `Provisioning -> ProvisioningProgressContent` | Wi-Fi 정보 전송 중 상태 화면이다. |
| 114-123 | `Success -> ResultContent` | 성공 화면이다. 다시 시작/재입력 버튼을 제공한다. |
| 125-134 | `Failure -> ResultContent` | 실패 화면이다. 재시도 흐름을 제공한다. |
| 140-162 | `HeaderCard()` | 현재 구현 목적을 설명하는 상단 카드이다. |
| 164-207 | `StepSummary(step)` | 시작, 검색, 연결, Wi-Fi, 전송, 완료 단계를 막대 형태로 보여준다. |
| 185-186 | `steps.forEach`와 `selected` | 각 단계를 돌면서 현재 단계인지 계산한다. |
| 187-203 | `Box`와 `Text` | 선택된 단계는 파란 배경, 나머지는 회색 배경으로 표시한다. |
| 209-217 | `StartContent` | 검색 시작 버튼이 있는 첫 화면이다. |
| 219-227 | `ScanningContent` | 검색 중 메시지와 progress bar를 보여준다. |
| 229-244 | `NodeListContent` | 노드 목록을 반복하며 `NodeCard`를 만든다. |
| 246-277 | `NodeCard` | 노드 이름, RSSI, firmware version, 연결 버튼을 보여준다. |
| 269-274 | `Button(onClick = { onNodeSelected(node) })` | 버튼을 누르면 선택된 노드를 ViewModel로 보낸다. |
| 279-287 | `ConnectingContent` | 선택한 노드와 연결 중임을 보여준다. |
| 289-349 | `WifiInputContent` | Wi-Fi SSID/PW 입력 화면 전체이다. |
| 315-321 | 첫 번째 `OutlinedTextField` | SSID 입력창이다. |
| 322-339 | 두 번째 `OutlinedTextField` | password 입력창이다. `PasswordVisualTransformation`으로 마스킹한다. |
| 334-337 | `trailingIcon` | 표시/숨김 버튼이다. |
| 340-346 | 전송 버튼 | `uiState.canSendWifiConfig`가 true일 때만 활성화된다. |
| 351-367 | `ProvisioningProgressContent` | payload preview, 상태 목록, progress bar를 보여준다. |
| 358-364 | safe payload preview | 실제 비밀번호가 아닌 마스킹된 preview만 보여준다. |
| 369-399 | `ResultContent` | 성공/실패 결과 화면을 공통으로 만든다. |
| 401-430 | `StatusList` | received, wifi_connecting 같은 상태 기록을 번호와 함께 보여준다. |
| 432-465 | `ActionCard` | 제목, 설명, 버튼이 있는 재사용 카드이다. |
| 467-509 | `InfoCard`, `MessageCard` | 안내/오류/성공 메시지를 같은 모양으로 표시하기 위한 helper이다. |
| 511-537 | `ProvisioningScreenPreview` | Android Studio Preview에서 화면을 미리 보기 위한 mock 상태이다. |

## 14. `WifiConfigPayloadTest.kt` line-by-line

파일: `android/app/src/test/java/com/airs/android/provisioning/domain/WifiConfigPayloadTest.kt`

| 줄 | 코드 | 설명 |
|---|---|---|
| 1 | `package ...domain` | 테스트 대상과 같은 domain 패키지에 둔다. |
| 3 | `import WifiCredentials` | 테스트 입력값을 만들기 위해 가져온다. |
| 4-6 | `import org.junit...` | JUnit assertion과 test annotation을 가져온다. |
| 8 | `class WifiConfigPayloadTest` | payload 생성 로직 테스트 클래스이다. |
| 9-19 | `build_createsWifiConfigJson` | 일반 SSID/PW가 기대 JSON으로 변환되는지 확인한다. |
| 21-31 | `build_escapesJsonSpecialCharacters` | 큰따옴표와 줄바꿈이 JSON에서 깨지지 않게 escape되는지 확인한다. |
| 33-44 | `buildSafePreview_doesNotExposePassword` | preview에 실제 비밀번호가 포함되지 않는지 확인한다. |

## 15. 왜 source code에 모든 설명을 주석으로 넣지 않았는가

소스 코드 안에 모든 line-by-line 설명을 주석으로 넣으면 실제 앱 코드가 너무 장황해진다.
실무에서는 코드는 간결하게 두고, 학습용 설명은 별도 문서에 두는 편이 좋다.

그래서 현재 방식은 아래와 같다.

```text
소스 코드:
실제 동작에 필요한 최소 코드

문서:
목적, 구조, 흐름, line-by-line 학습 설명
```

이렇게 하면 나중에 Android BLE 실제 구현을 추가할 때도 코드 가독성을 유지하면서 학습 기록을 계속 확장할 수 있다.

## 16. 검증 결과

아래 명령으로 단위 테스트와 debug 빌드를 확인했다.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

결과:

```text
BUILD SUCCESSFUL
```
