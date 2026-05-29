# AIRS Android Wi-Fi Provisioning

작성일: 2026-05-26

이 문서는 AIRS Android 앱에서 "노드 - 휴대폰 앱 - Wi-Fi 연결" 흐름을 구현하기 위한 기준 문서이다.
현재 목표는 전체 앱 기능이 아니라, BLE를 통해 AIRS 노드에 Wi-Fi 접속 정보를 전달하는 첫 구현 범위를 고정하는 것이다.

## 1. 고정 기술 스택

```text
AIRS Android stack:
- Language: Kotlin
- UI: Jetpack Compose
- Architecture: MVVM + Repository
- BLE: Native Android BLE API wrapper
- First target: Wi-Fi provisioning flow only
```

선택 이유:

- BLE 스캔, 연결, GATT 통신, Android 런타임 권한은 OS 의존성이 강하므로 Native Android가 가장 단순하다.
- Kotlin은 현재 Android 개발의 기본 언어로 보기 적합하다.
- Jetpack Compose는 연결 상태, 스캔 결과, 전송 상태처럼 자주 바뀌는 UI를 상태 기반으로 표현하기 좋다.
- MVVM + Repository는 첫 구현에 필요한 분리만 제공한다. 처음부터 과한 계층 구조를 만들지 않는다.

## 2. AIRS에서 Wi-Fi Provisioning이 필요한 이유

AIRS 센서 노드는 Wi-Fi에 연결되어야 MQTT publish가 가능하다.
하지만 매번 노드를 breadboard나 PC에 직접 연결해서 Wi-Fi 정보를 주입하는 방식은 불편하다.

따라서 Android 앱은 초기 설정 도구 역할을 한다.

```text
사용자
-> Android 앱 실행
-> 주변 AIRS 노드 감지
-> 노드 선택
-> Wi-Fi SSID/PW 입력
-> BLE로 노드에 Wi-Fi 정보 전달
-> 노드가 직접 Wi-Fi 연결
-> 노드가 MQTT publish 시작
```

이때 휴대폰은 Wi-Fi 중계기가 아니다.
휴대폰 앱은 노드에 Wi-Fi 접속 정보를 전달하는 provisioning app이다.

## 3. BLE 역할 구분

```text
Android 앱 = BLE Central / GATT Client
AIRS 노드 = BLE Peripheral / GATT Server
```

- AIRS 노드는 "설정 가능한 기기"임을 BLE advertising으로 주변에 알린다.
- Android 앱은 주변 BLE advertising을 스캔한다.
- 앱은 AIRS 전용 Service UUID 또는 이름 규칙으로 AIRS 노드만 필터링한다.
- 사용자가 노드를 선택하면 앱이 GATT 연결을 맺는다.
- 연결 후 앱은 Wi-Fi 설정 characteristic에 SSID/PW를 write한다.
- 노드는 Wi-Fi 접속 결과를 status characteristic으로 notify한다.

## 4. AIRS 노드 감지 흐름

| 단계 | AIRS에서 일어나는 일 | 앱 쪽 언어/툴 | 노드 쪽 언어/툴 |
|---|---|---|---|
| 1. 노드 설정 모드 진입 | 노드가 아직 Wi-Fi에 연결되지 않았거나 사용자가 설정 모드로 전환한다. | 없음 | 펌웨어 코드. ESP32 기준 C/C++ 또는 Arduino/ESP-IDF |
| 2. BLE 광고 시작 | 노드가 AIRS 전용 Service UUID와 이름을 advertising packet에 포함한다. | 없음 | 노드 BLE peripheral 코드 |
| 3. 앱 권한 요청 | 앱이 Bluetooth scan/connect 권한을 사용자에게 요청한다. | Kotlin, Android Manifest, runtime permission | 없음 |
| 4. 앱 BLE 스캔 | 앱이 주변 BLE 기기를 스캔한다. | Kotlin, `BluetoothLeScanner.startScan()` | 노드는 계속 advertising |
| 5. AIRS 노드 필터링 | 앱이 AIRS Service UUID를 우선 기준으로 노드를 추린다. 이름 prefix는 보조 기준으로 사용한다. | Kotlin, `ScanFilter`, `ScanCallback` | advertising packet에 Service UUID/name 포함 |
| 6. 기기 목록 표시 | 발견된 노드를 UI에 표시한다. | Jetpack Compose, ViewModel state | 없음 |
| 7. 사용자가 노드 선택 | 사용자가 연결할 AIRS 노드를 선택한다. | Compose Navigation, ViewModel event | 없음 |
| 8. GATT 연결 | 앱이 선택한 BLE 기기에 연결한다. | Kotlin, `BluetoothGatt` | 노드는 GATT server 역할 |
| 9. 서비스 확인 | 앱이 AIRS provisioning service와 characteristic 존재 여부를 확인한다. | Kotlin, GATT service discovery | 노드 firmware에 service/characteristic 구현 |
| 10. Wi-Fi 정보 전달 | 앱이 SSID/PW payload를 Wi-Fi config characteristic에 write한다. | Kotlin BLE write with response | 노드가 payload 수신 후 Wi-Fi 연결 시도 |
| 11. 결과 수신 | 앱이 노드의 Wi-Fi 연결 성공/실패 상태를 받는다. | Kotlin notification callback | 노드가 status characteristic notify |

## 5. 현재 고정한 BLE 계약 초안

이 값들은 앱과 노드 firmware가 같이 사용해야 하는 초기 계약이다.
구현 중 문제가 생기면 이 문서를 먼저 수정한 뒤 앱/노드 코드를 맞춘다.

### 5.1 Advertising

선택:

```text
Local name: AIRS-SETUP-XXXX
Primary filter: AIRS Provisioning Service UUID
Fallback filter: Local name starts with "AIRS-SETUP-"
```

예시:

```text
AIRS-SETUP-0001
AIRS-SETUP-A3F7
```

후보와 선택 이유:

| 후보 | 장점 | 단점 | 판단 |
|---|---|---|---|
| 이름만 사용 | 사람이 보기 쉽고 구현이 쉽다. | BLE 이름은 누락되거나 잘릴 수 있고, 같은 이름 충돌이 가능하다. | 단독 기준으로는 사용하지 않는다. |
| MAC address 사용 | 기기 식별이 명확해 보인다. | Android에서 주소가 랜덤화될 수 있고 사용자에게 의미가 약하다. | 사용하지 않는다. |
| Service UUID 사용 | AIRS 기기만 안정적으로 필터링하기 좋다. | firmware와 앱이 UUID를 반드시 공유해야 한다. | 기본 기준으로 사용한다. |
| Manufacturer data 사용 | 제품군/기기 정보를 더 구조적으로 넣을 수 있다. | 첫 구현에는 불필요하게 복잡하다. | 이후 필요 시 추가한다. |

### 5.2 UUID

선택:

```text
AIRS Provisioning Service UUID:
2b340f85-c8c9-4f85-ac71-4b9702e2bcf9

Device Info Characteristic UUID:
88568c03-f498-4c72-9b89-afbaf87578c0

Wi-Fi Config Characteristic UUID:
60ed54cf-eda0-42f6-b7cd-28b88ef8b518

Provision Status Characteristic UUID:
b77420e0-566e-4746-acf0-23d88d634ce7
```

후보와 선택 이유:

| 후보 | 장점 | 단점 | 판단 |
|---|---|---|---|
| Bluetooth 표준 UUID 재사용 | 익숙하고 문서가 많다. | Wi-Fi provisioning용 AIRS 전용 의미를 담기 어렵다. | 사용하지 않는다. |
| 임의 128-bit UUID | AIRS 전용 서비스/특성을 명확히 구분할 수 있다. | 앱과 노드가 값을 정확히 공유해야 한다. | 선택한다. |
| 짧은 16-bit custom UUID | 짧고 보기 쉽다. | 공식 할당 영역과 혼동될 수 있다. | 사용하지 않는다. |

### 5.3 Characteristics

| Characteristic | Property | 용도 |
|---|---|---|
| Device Info | READ | nodeId, firmwareVersion, provisioningVersion 확인 |
| Wi-Fi Config | WRITE WITH RESPONSE | SSID/PW 전달 |
| Provision Status | READ, NOTIFY | Wi-Fi 연결 진행 상태 및 결과 전달 |

선택 이유:

- Device Info는 연결한 기기가 AIRS 노드인지 최종 확인하는 용도이다.
- Wi-Fi Config는 민감한 값을 전달하므로 write with response로 성공/실패를 확인한다.
- Provision Status는 앱이 계속 polling하지 않도록 notify를 사용한다.

### 5.4 Wi-Fi Config Payload

선택:

```json
{
  "ssid": "wifi-name",
  "password": "wifi-password"
}
```

전송 규칙:

- UTF-8 JSON으로 전송한다.
- 앱은 GATT 연결 후 MTU 185 이상을 요청한다.
- 첫 구현에서는 단일 write를 기준으로 한다.
- 단일 write가 실제 기기에서 불안정하면 chunk protocol을 추가한다.
- 앱과 노드 모두 SSID/PW를 로그로 남기지 않는다.

후보와 선택 이유:

| 후보 | 장점 | 단점 | 판단 |
|---|---|---|---|
| JSON | 사람이 읽기 쉽고 앱/펌웨어 디버깅이 쉽다. | binary보다 길다. | 첫 구현에 선택한다. |
| `ssid=...&password=...` | 간단하고 짧다. | escaping 규칙을 따로 정해야 한다. | 보류한다. |
| Binary/TLV | 가장 작고 견고하다. | 배경지식이 없는 팀원이 이해하기 어렵고 구현 비용이 높다. | 첫 구현에서는 사용하지 않는다. |

### 5.5 Provision Status Payload

선택:

```json
{
  "state": "wifi_connected",
  "message": "connected"
}
```

초기 상태 값:

| state | 의미 |
|---|---|
| `received` | 노드가 Wi-Fi config payload를 수신했다. |
| `wifi_connecting` | 노드가 Wi-Fi 접속을 시도 중이다. |
| `wifi_connected` | 노드가 Wi-Fi에 연결되었다. |
| `wifi_failed` | 노드가 Wi-Fi 연결에 실패했다. |

후보와 선택 이유:

| 후보 | 장점 | 단점 | 판단 |
|---|---|---|---|
| 앱이 주기적으로 READ | 구현이 직관적이다. | 불필요한 요청이 반복된다. | 사용하지 않는다. |
| 노드가 NOTIFY | 상태 변화가 생길 때만 앱에 알려준다. | notification 구독 처리가 필요하다. | 선택한다. |

## 6. Backend API와의 경계

첫 구현에서는 Wi-Fi provisioning 자체를 backend API와 분리한다.

```text
앱 -> BLE -> 노드 -> Wi-Fi 연결
```

MQTT publish는 Wi-Fi 연결 이후 노드 firmware/backend 쪽에서 확인할 후속 검증이며,
앱의 Wi-Fi provisioning 성공 기준에는 포함하지 않는다.

Backend는 이후 단계에서 아래 기능에 연결한다.

- 로그인한 사용자의 노드 목록 조회
- provisioning 성공 후 nodeId를 사용자 계정에 등록 또는 claim
- 노드 상태 조회
- 센서 데이터 조회

후보와 선택 이유:

| 후보 | 장점 | 단점 | 판단 |
|---|---|---|---|
| backend 등록을 먼저 구현 | 사용자-노드 관계를 처음부터 관리할 수 있다. | BLE/Wi-Fi 핵심 검증 전에 범위가 커진다. | 첫 구현에서는 제외한다. |
| BLE provisioning만 먼저 구현 | 가장 중요한 하드웨어 연결 문제를 빠르게 검증할 수 있다. | 사용자 계정 연동은 나중에 붙여야 한다. | 선택한다. |
| BLE와 backend를 동시에 구현 | 최종 제품 흐름에 가깝다. | 디버깅 지점이 많아진다. | 보류한다. |

## 7. MVP 보안 기준

첫 구현에서 고정할 기준:

- 노드는 설정 모드일 때만 BLE advertising을 한다.
- 설정 모드는 전원 인가 직후 또는 버튼 입력 후 제한된 시간만 열어둔다.
- 앱은 사용자가 명시적으로 선택한 노드에만 Wi-Fi 정보를 전송한다.
- 앱과 노드는 SSID/PW를 로그로 남기지 않는다.
- BLE 연결 실패, write 실패, Wi-Fi 실패는 UI에서 구분해서 표시한다.

후보와 선택 이유:

| 후보 | 장점 | 단점 | 판단 |
|---|---|---|---|
| 보안 없이 항상 advertising | 구현이 가장 쉽다. | 주변 사람이 노드를 볼 수 있고 오작동 위험이 크다. | 사용하지 않는다. |
| OS bonding/pairing 강제 | BLE 링크 보안이 좋아진다. | Android/기기별 UI와 firmware 설정 복잡도가 올라간다. | 첫 구현 이후 검토한다. |
| 앱 계층 암호화/claim code | 제품 수준 보안에 가깝다. | 초기 구현 범위를 크게 늘린다. | 후속 단계로 둔다. |
| 제한된 설정 모드 + 로그 금지 | 첫 구현 범위에서 위험을 줄일 수 있다. | 완전한 보안은 아니다. | MVP 기준으로 선택한다. |

## 8. Android 화면 흐름 초안

첫 구현 화면은 기능 검증에 필요한 최소 흐름으로 둔다.

```text
ProvisionStartScreen
-> BlePermissionScreen
-> NodeScanScreen
-> NodeConnectingScreen
-> WifiInputScreen
-> ProvisioningProgressScreen
-> ProvisioningResultScreen
```

각 화면의 책임:

| 화면 | 책임 |
|---|---|
| ProvisionStartScreen | Wi-Fi provisioning 시작 |
| BlePermissionScreen | BLE 권한 요청 및 Bluetooth 활성 여부 안내 |
| NodeScanScreen | 주변 AIRS 노드 스캔 및 목록 표시 |
| NodeConnectingScreen | 선택한 노드와 GATT 연결 |
| WifiInputScreen | SSID/PW 입력 |
| ProvisioningProgressScreen | Wi-Fi 정보 전송 및 노드 상태 수신 |
| ProvisioningResultScreen | 성공/실패 결과 표시 및 재시도 |

## 9. 현재 개발 제약

제품 구현 기준은 Android 휴대폰이다.
현재 Android 휴대폰이 없을 때 Samsung 태블릿을 임시 테스트 장비로 사용할 수 있지만, 앱 구조와 문서 기준은 휴대폰으로 유지한다.
따라서 앱 실행 경로에는 실제 BLE 구현을 연결하고, AIRS 노드가 없을 때는 mock 테스트로 화면 흐름과 상태 모델을 검증한다.

현재 기본 전략:

```text
Android 휴대폰 기준 구현
-> 현재는 Samsung 태블릿으로 앱 설치와 BLE 권한 흐름을 임시 확인 가능
-> AIRS 노드 확보 후 Wi-Fi 연결 및 MQTT publish end-to-end 검증
```

## 10. Android 휴대폰 필요 여부 구분

| 작업 | Android 휴대폰 필요 여부 | 노드 필요 여부 | 현재 진행 가능 여부 | 검증 방법 |
|---|---|---|---|---|
| Android 프로젝트 생성 | 필요 없음 | 필요 없음 | 가능 | Gradle sync, `:app:assembleDebug` |
| Compose 화면 구현 | 필요 없음 | 필요 없음 | 가능 | Compose Preview, Emulator |
| 화면 navigation 구현 | 필요 없음 | 필요 없음 | 가능 | Emulator, preview state |
| provisioning 상태 모델 정의 | 필요 없음 | 필요 없음 | 가능 | unit test, 코드 리뷰 |
| Wi-Fi SSID/PW 입력 검증 | 필요 없음 | 필요 없음 | 가능 | unit test, Emulator |
| Wi-Fi config JSON payload 생성 | 필요 없음 | 필요 없음 | 가능 | unit test |
| Mock BLE scan/connect/write 흐름 | 필요 없음 | 필요 없음 | 가능 | fake repository, Emulator |
| BLE 권한 UI 흐름 | 필요 | 필요 없음 | 가능 | Android 휴대폰에서 권한 요청 확인. 현재는 Samsung 태블릿으로 임시 확인 가능 |
| 실제 BLE scan 구현 | 필요 | AIRS 노드 또는 BLE peripheral 필요 | 가능 | Android 휴대폰에서 scan 실행. 현재는 Samsung 태블릿으로 임시 확인 가능 |
| 실제 GATT connect/service discovery | 필요 | 필요 | 노드 확보 후 가능 | Android 휴대폰 + AIRS 노드 |
| 실제 Wi-Fi config write | 필요 | 필요 | 노드 확보 후 가능 | Android 휴대폰 + AIRS 노드 |
| provision status notify 수신 | 필요 | 필요 | 노드 확보 후 가능 | Android 휴대폰 + AIRS 노드 |
| 노드 Wi-Fi 연결 확인 | 필요 | 필요 | 보류 | 노드 serial log, 앱 status |
| MQTT publish end-to-end 확인 | 필요 | 필요 | 보류 | broker/backend에서 publish 확인 |

## 11. 노드 없이 진행한 구현 순서

1. Android 프로젝트 기본 빌드 확인
   - verify: `./gradlew :app:assembleDebug`
2. provisioning flow 패키지 구조 작성
   - verify: 앱 빌드 성공
3. `BleProvisioningRepository` 인터페이스 작성
   - verify: 실제 BLE 구현 없이 화면이 repository 계약에만 의존
4. `MockBleProvisioningRepository` 작성
   - verify: 가짜 노드 발견, 연결 성공, 전송 성공/실패 시나리오 재현
5. provisioning 화면 state 작성
   - verify: scan/connect/input/progress/result 상태가 한 곳에서 관리됨
6. Compose 화면 작성
   - verify: Emulator 또는 Preview로 화면 확인
7. Wi-Fi config payload 생성 로직 작성
   - verify: unit test로 JSON 형식 확인
8. Android BLE 권한 화면 작성
   - verify: 권한 안내 UI와 분기 확인
9. Native Android BLE Repository 작성
   - verify: 실제 Android BLE API로 scan/connect/write/notify 코드가 빌드됨

### 2026-05-28 구현 상태

- Android 프로젝트 생성 완료
  - package/applicationId: `com.airs.android`
  - minSdk: `26`
  - UI: Jetpack Compose
- mock-first 구조 추가
  - `BleProvisioningRepository`
  - `MockBleProvisioningRepository`
  - `ProvisioningViewModel`
  - `ProvisioningScreen`
  - `WifiConfigPayload`
- Android 휴대폰 전제로 실제 BLE 구현 추가
  - `BleRuntimePermissions`
  - `AndroidBleProvisioningRepository`
  - `BlePermissionScreen`
- 설명 문서 추가
  - `mobile/docs/android_architecture_guide.html`
  - `mobile/docs/android_implementation_walkthrough.md`
- 현재 앱 실행 경로는 실제 BLE repository를 사용한다.
- 단위 테스트는 mock repository로 앱 내부 상태 전환을 계속 검증한다.
- 검증 완료
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug`

## 12. Android 휴대폰에서 진행할 검증 순서

1. Android 휴대폰 개발자 옵션과 USB debugging 켜기
2. Android Studio에서 휴대폰이 실행 대상으로 표시되는지 확인
3. 앱 실행 후 Bluetooth 권한 요청 동작 확인
4. Bluetooth가 꺼진 상태에서 오류 메시지 확인
5. AIRS 노드가 없을 때 scan timeout 실패 메시지 확인
6. AIRS 노드 확보 후 Service UUID/name 기준 scan filter 검증
7. GATT connect/service discovery 검증
8. Wi-Fi config characteristic write 검증
9. provision status notification 구독 검증
10. 실패 케이스별 UI 분기 확인

현재 Android 휴대폰이 없으면 1-5번은 Samsung 태블릿으로 임시 확인할 수 있다.

## 13. AIRS 노드 확보 후 진행할 구현 순서

1. 노드가 `AIRS-SETUP-XXXX` 이름과 AIRS Service UUID로 advertising하는지 확인
2. 앱에서 노드가 scan list에 표시되는지 확인
3. 앱에서 노드와 GATT 연결되는지 확인
4. Device Info characteristic READ 확인
5. Wi-Fi Config characteristic WRITE 확인
6. Provision Status characteristic NOTIFY 확인
7. 노드가 Wi-Fi에 연결되는지 확인
8. 노드가 MQTT publish를 수행하는지 확인

## 14. 아직 확정 전인 항목

아래 항목은 앱 단독으로 확정할 수 없고, 노드 firmware 구현과 같이 맞춰야 한다.

- 노드 hardware가 ESP32인지, Raspberry Pi 계열인지
- 노드 firmware 개발 방식: Arduino, ESP-IDF, Python 등
- 노드가 BLE와 Wi-Fi를 동시에 안정적으로 처리할 수 있는지
- 노드의 MQTT publish 성공 여부를 BLE status로 앱에 알려줄지
- provisioning 성공 후 backend에 node claim을 붙일 정확한 API 흐름
