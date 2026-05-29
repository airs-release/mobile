package com.airs.android.provisioning.domain

import com.airs.android.provisioning.model.WifiCredentials

object WifiConfigPayload { // Wi-Fi 설정 payload 문자열을 만드는 싱글턴 helper
    // 실제 노드에 write할 SSID/PW JSON 문자열을 생성
    fun build(credentials: WifiCredentials): String {
        return """{"ssid":"${credentials.ssid.escapeJson()}","password":"${credentials.password.escapeJson()}"}""" // SSID/PW를 JSON 문자열로 조립
    }

    // 화면 표시용 payload preview를 생성
    fun buildSafePreview(credentials: WifiCredentials): String {
        val maskedPassword = "*".repeat(credentials.password.length.coerceAtMost(8)) // 실제 비밀번호 대신 최대 8개의 별표만 사용
        return """{"ssid":"${credentials.ssid.escapeJson()}","password":"$maskedPassword"}""" // 비밀번호 원문이 노출되지 않는 preview JSON 생성
    }
}

// JSON 문자열 안에서 깨질 수 있는 문자를 안전한 escape 형태로 변환
private fun String.escapeJson(): String {
    return buildString { // StringBuilder처럼 결과 문자열을 누적 생성
        for (character in this@escapeJson) { // 원본 문자열의 문자를 하나씩 검사
            when (character) { // 문자 종류에 따라 JSON escape 규칙 적용
                '\\' -> append("\\\\") // 역슬래시는 JSON에서 두 번 적어야 함
                '"' -> append("\\\"") // 큰따옴표는 JSON 문자열 종료로 오해되지 않게 escape
                '\n' -> append("\\n") // 줄바꿈 문자를 JSON escape 문자열로 변환
                '\r' -> append("\\r") // carriage return 문자를 JSON escape 문자열로 변환
                '\t' -> append("\\t") // tab 문자를 JSON escape 문자열로 변환
                else -> append(character) // 특별 처리 대상이 아니면 그대로 추가
            }
        }
    }
}
