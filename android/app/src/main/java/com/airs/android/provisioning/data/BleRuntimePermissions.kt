package com.airs.android.provisioning.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object BleRuntimePermissions { // Android 버전에 따라 필요한 BLE runtime permission을 계산하는 helper
    // 현재 기기 Android 버전에 필요한 권한 목록을 반환
    fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>() // Android 버전에 맞는 runtime permission을 누적
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 이상은 Bluetooth 권한이 분리됨
            permissions += Manifest.permission.BLUETOOTH_SCAN // 주변 BLE 기기 scan 권한
            permissions += Manifest.permission.BLUETOOTH_CONNECT // BLE 기기 연결/GATT 사용 권한
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION // Android 11 이하는 BLE scan에 위치 권한이 필요
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13 이상은 nearby Wi-Fi 권한 그룹이 추가됨
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES // Wi-Fi 주변 기기/API 접근 권한
        }
        permissions += Manifest.permission.ACCESS_FINE_LOCATION // Wi-Fi scan 결과를 얻기 위한 위치 권한

        return permissions.distinct().toTypedArray() // 중복 권한 제거 후 배열로 반환
    }

    // 필요한 권한이 모두 승인되어 있는지 확인
    fun hasAll(context: Context): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED // 권한별 승인 여부 확인
        }
    }
}
