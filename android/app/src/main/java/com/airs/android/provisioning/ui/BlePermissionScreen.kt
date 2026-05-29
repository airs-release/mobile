package com.airs.android.provisioning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// 실제 BLE scan/connect와 Wi-Fi 목록 확인을 시작하기 전에 필요한 권한을 요청하는 화면
@Composable
fun BlePermissionScreen(onRequestPermissions: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(), // 화면 전체 크기 사용
        color = Color(0xFFF5F8FB) // provisioning 화면과 같은 배경색
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize() // 가운데 배치를 위해 전체 크기 사용
                .padding(24.dp), // 화면 바깥 여백
            verticalArrangement = Arrangement.Center, // 세로 가운데 정렬
            horizontalAlignment = Alignment.CenterHorizontally // 가로 가운데 정렬
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White), // 안내 카드 배경색
                shape = RoundedCornerShape(8.dp) // 카드 모서리 둥글기
            ) {
                Column(
                    modifier = Modifier.padding(22.dp), // 카드 내부 여백
                    verticalArrangement = Arrangement.spacedBy(12.dp) // 텍스트와 버튼 사이 간격
                ) {
                    Text(
                        text = "Bluetooth/Wi-Fi 권한 필요", // 권한 화면 제목
                        style = MaterialTheme.typography.headlineSmall, // 제목 스타일
                        fontWeight = FontWeight.Bold // 제목 강조
                    )
                    Text(
                        text = "AIRS 노드 검색에는 BLE 권한이, 주변 Wi-Fi 목록 확인에는 Wi-Fi/위치 권한이 필요합니다.", // 왜 권한이 필요한지 설명
                        style = MaterialTheme.typography.bodyMedium, // 본문 스타일
                        color = Color(0xFF405168) // 본문 색상
                    )
                    Button(onClick = onRequestPermissions) { // 버튼을 누르면 Activity의 permission launcher 실행
                        Text("필요 권한 허용") // 버튼 문구
                    }
                }
            }
        }
    }
}
