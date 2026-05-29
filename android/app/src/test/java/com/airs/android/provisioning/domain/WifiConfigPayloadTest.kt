package com.airs.android.provisioning.domain

import com.airs.android.provisioning.model.WifiCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WifiConfigPayloadTest { // Wi-Fi payload 생성 로직을 검증하는 단위 테스트
    @Test
    // 일반 SSID/PW가 노드에 보낼 JSON 문자열로 만들어지는지 확인
    fun build_createsWifiConfigJson() {
        val credentials = WifiCredentials(
            ssid = "AIRS-LAB", // 테스트용 Wi-Fi 이름
            password = "secret-password" // 테스트용 Wi-Fi 비밀번호
        )

        val payload = WifiConfigPayload.build(credentials) // 실제 전송 payload 생성

        assertEquals("""{"ssid":"AIRS-LAB","password":"secret-password"}""", payload) // 기대한 JSON과 실제 결과 비교
    }

    @Test
    // JSON에서 문제가 될 수 있는 큰따옴표와 줄바꿈이 escape되는지 확인
    fun build_escapesJsonSpecialCharacters() {
        val credentials = WifiCredentials(
            ssid = """AIRS "Lab"""", // 큰따옴표가 들어간 SSID
            password = "line\nbreak" // 줄바꿈이 들어간 비밀번호
        )

        val payload = WifiConfigPayload.build(credentials) // 특수문자가 포함된 payload 생성

        assertEquals("""{"ssid":"AIRS \"Lab\"","password":"line\nbreak"}""", payload) // escape된 JSON 문자열인지 확인
    }

    @Test
    // 화면 preview에 실제 비밀번호가 노출되지 않는지 확인
    fun buildSafePreview_doesNotExposePassword() {
        val credentials = WifiCredentials(
            ssid = "AIRS-LAB", // preview에 표시해도 되는 SSID
            password = "secret-password" // preview에 노출되면 안 되는 비밀번호
        )

        val preview = WifiConfigPayload.buildSafePreview(credentials) // 화면 표시용 안전 preview 생성

        assertFalse(preview.contains("secret-password")) // preview에 실제 비밀번호가 포함되지 않아야 함
        assertEquals("""{"ssid":"AIRS-LAB","password":"********"}""", preview) // 비밀번호가 별표로 마스킹되어야 함
    }
}
