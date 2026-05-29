package com.airs.android.provisioning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.airs.android.provisioning.model.AirsBleNode
import com.airs.android.provisioning.model.ProvisioningStep
import com.airs.android.provisioning.model.ProvisioningStatus
import com.airs.android.provisioning.model.ProvisioningUiState
import com.airs.android.provisioning.model.WifiNetwork
import com.airs.android.ui.theme.AIRSTheme

private val AirsBlue = Color(0xFF0B7BFF)
private val AirsGreen = Color(0xFF24C875)
private val AirsInk = Color(0xFF0B132B)
private val AirsMuted = Color(0xFF52637A)
private val AirsBg = Color(0xFFF4F8FB)
private val AirsCard = Color.White
private val AirsDanger = Color(0xFFEA4D5A)
private val AirsGradient = Brush.horizontalGradient(listOf(AirsBlue, AirsGreen))

@Composable
fun ProvisioningRoute(viewModel: ProvisioningViewModel) {
    ProvisioningScreen(
        uiState = viewModel.uiState,
        onStartScan = viewModel::startScan,
        onNodeSelected = viewModel::selectNode,
        onWifiNetworkSelected = viewModel::selectWifiNetwork,
        onPasswordChanged = viewModel::updatePassword,
        onTogglePassword = viewModel::togglePasswordVisible,
        onBluetoothSuccessConfirmed = viewModel::continueToWifiInput,
        onSendWifiConfig = viewModel::sendWifiConfig,
        onRetry = viewModel::retryFromStart,
        onBackToWifiInput = viewModel::backToWifiInput
    )
}

@Composable
fun ProvisioningScreen(
    uiState: ProvisioningUiState,
    onStartScan: () -> Unit,
    onNodeSelected: (AirsBleNode) -> Unit,
    onWifiNetworkSelected: (WifiNetwork) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onBluetoothSuccessConfirmed: () -> Unit,
    onSendWifiConfig: () -> Unit,
    onRetry: () -> Unit,
    onBackToWifiInput: () -> Unit
) {
    AirsScreenShell(uiState.step) {
        when (uiState.step) {
            ProvisioningStep.Start -> StartContent(onStartScan)
            ProvisioningStep.Scanning -> ScanningContent()
            ProvisioningStep.NodeList -> NodeListContent(uiState, onNodeSelected)
            ProvisioningStep.Connecting -> ConnectingContent(uiState.selectedNode)
            ProvisioningStep.BluetoothConnected -> BluetoothConnectedContent(uiState.selectedNode, onBluetoothSuccessConfirmed)
            ProvisioningStep.WifiInput -> WifiInputContent(
                uiState = uiState,
                onWifiNetworkSelected = onWifiNetworkSelected,
                onPasswordChanged = onPasswordChanged,
                onTogglePassword = onTogglePassword,
                onSendWifiConfig = onSendWifiConfig
            )

            ProvisioningStep.Provisioning -> ProvisioningContent(uiState)
            ProvisioningStep.Success -> ResultContent(
                title = "Wi-Fi 설정 완료",
                body = "노드가 Wi-Fi 연결 성공 상태를 알려왔습니다.",
                success = true,
                statuses = uiState.statuses,
                onPrimary = onRetry,
                primaryText = "처음부터 다시",
                onSecondary = onBackToWifiInput,
                secondaryText = "Wi-Fi 다시 입력"
            )

            ProvisioningStep.Failure -> ResultContent(
                title = "설정 실패",
                body = uiState.errorMessage ?: "노드 검색, BLE 연결, Wi-Fi 정보 전송, 또는 노드 Wi-Fi 연결 중 문제가 발생했습니다.",
                success = false,
                statuses = uiState.statuses,
                onPrimary = onRetry,
                primaryText = "다시 검색",
                onSecondary = onBackToWifiInput,
                secondaryText = "Wi-Fi 다시 입력"
            )
        }
    }
}

@Composable
private fun AirsScreenShell(
    step: ProvisioningStep,
    content: ColumnScopeBody
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AirsBg)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderCard()
        StepBar(step)
        content()
    }
}

private typealias ColumnScopeBody = @Composable ColumnScope.() -> Unit

@Composable
private fun HeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AirsCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("AIRS Wi-Fi Provisioning", color = AirsInk, fontWeight = FontWeight.ExtraBold)
            Text(
                "Android 휴대폰에서 실제 BLE로 AIRS 노드를 검색하고 Wi-Fi 정보를 전달합니다.",
                color = AirsMuted
            )
        }
    }
}

@Composable
private fun StepBar(step: ProvisioningStep) {
    val items = listOf(
        ProvisioningStep.Start to "시작",
        ProvisioningStep.Scanning to "검색",
        ProvisioningStep.Connecting to "연결",
        ProvisioningStep.WifiInput to "Wi-Fi",
        ProvisioningStep.Provisioning to "전송",
        ProvisioningStep.Success to "완료"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AirsCard, RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (itemStep, label) ->
            val active = stepGroup(step) == itemStep
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (active) AirsBlue else Color(0xFFEAF0F7),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (active) Color.White else AirsMuted,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StartContent(onStartScan: () -> Unit) {
    StatusCard(
        title = "준비 완료",
        body = "노드가 설정 모드로 BLE advertising 중인지 확인한 뒤 검색을 시작하세요.",
        success = null
    )
    GradientButton(
        text = "AIRS 노드 검색 시작",
        onClick = onStartScan,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ScanningContent() {
    StatusCard(
        title = "AIRS 노드 검색 중",
        body = "주변 BLE 신호에서 AIRS provisioning service 또는 이름 규칙에 맞는 노드를 찾고 있습니다.",
        success = null,
        loading = true
    )
}

@Composable
private fun NodeListContent(
    uiState: ProvisioningUiState,
    onNodeSelected: (AirsBleNode) -> Unit
) {
    SectionCard(title = "검색된 AIRS 노드") {
        if (uiState.nodes.isEmpty()) {
            Text("아직 발견된 노드가 없습니다.", color = AirsMuted)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.nodes.forEach { node ->
                    NodeRow(node = node, onClick = { onNodeSelected(node) })
                }
            }
        }
    }
}

@Composable
private fun ConnectingContent(node: AirsBleNode?) {
    StatusCard(
        title = "Bluetooth 연결 중",
        body = "${node?.displayName ?: "선택한 AIRS 노드"}에 BLE GATT 연결을 시도하고 있습니다.",
        success = null,
        loading = true
    )
}

@Composable
private fun BluetoothConnectedContent(
    node: AirsBleNode?,
    onConfirm: () -> Unit
) {
    StatusCard(
        title = "Bluetooth 연결 완료",
        body = "${node?.displayName ?: "선택한 AIRS 노드"}에 Wi-Fi 정보를 전달할 준비가 되었습니다.",
        success = true
    )
    GradientButton(
        text = "Wi-Fi 정보 입력으로 이동",
        onClick = onConfirm,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun WifiInputContent(
    uiState: ProvisioningUiState,
    onWifiNetworkSelected: (WifiNetwork) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSendWifiConfig: () -> Unit
) {
    var passwordTarget by remember { mutableStateOf<WifiNetwork?>(null) }
    var dialogPassword by remember { mutableStateOf("") }

    passwordTarget?.let { network ->
        AlertDialog(
            onDismissRequest = {
                passwordTarget = null
                dialogPassword = ""
            },
            title = { Text("${network.ssid} 비밀번호") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Android는 저장된 Wi-Fi 비밀번호를 앱에 제공하지 않으므로 직접 입력해야 합니다.")
                    OutlinedTextField(
                        value = dialogPassword,
                        onValueChange = { dialogPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = dialogPassword.isNotBlank(),
                    onClick = {
                        onWifiNetworkSelected(network)
                        onPasswordChanged(dialogPassword)
                        passwordTarget = null
                        dialogPassword = ""
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        passwordTarget = null
                        dialogPassword = ""
                    }
                ) {
                    Text("취소")
                }
            }
        )
    }

    SectionCard(title = "Wi-Fi 정보 입력") {
        Text("Wi-Fi 목록에서 SSID를 선택하면 비밀번호 입력 창이 열립니다.", color = AirsMuted)
        if (uiState.ssid.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            PayloadPreview(uiState.safePayloadPreview)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (uiState.passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(onClick = onTogglePassword) {
                        Text(if (uiState.passwordVisible) "숨김" else "표시")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(modifier = Modifier.height(10.dp))
            GradientButton(
                text = "Wi-Fi 정보 전송",
                onClick = onSendWifiConfig,
                enabled = uiState.canSendWifiConfig,
                modifier = Modifier.fillMaxWidth()
            )
        }
        uiState.errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(message, color = AirsDanger, fontWeight = FontWeight.Bold)
        }
    }

    SectionCard(title = "기기에서 감지한 Wi-Fi") {
        when {
            uiState.isLoadingWifiNetworks -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Wi-Fi 목록을 읽고 있습니다.", color = AirsMuted)
                }
            }

            uiState.wifiNetworks.isEmpty() -> {
                Text(uiState.wifiNetworkMessage ?: "감지된 Wi-Fi가 없습니다.", color = AirsMuted)
            }

            else -> {
                uiState.wifiNetworkMessage?.let {
                    Text(it, color = AirsMuted)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.wifiNetworks.forEach { network ->
                        WifiNetworkRow(
                            network = network,
                            selected = network.ssid == uiState.ssid,
                            onClick = {
                                dialogPassword = ""
                                passwordTarget = network
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProvisioningContent(uiState: ProvisioningUiState) {
    StatusCard(
        title = "Wi-Fi 정보 전송 중",
        body = uiState.statuses.lastOrNull()?.message ?: "BLE characteristic에 Wi-Fi payload를 write하고 노드 상태 notify를 기다립니다.",
        success = null,
        loading = true
    )
    StatusList(uiState.statuses)
}

@Composable
private fun ResultContent(
    title: String,
    body: String,
    success: Boolean,
    statuses: List<ProvisioningStatus>,
    onPrimary: () -> Unit,
    primaryText: String,
    onSecondary: () -> Unit,
    secondaryText: String
) {
    StatusCard(title = title, body = body, success = success)
    StatusList(statuses)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GradientButton(
            text = primaryText,
            onClick = onPrimary,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = onSecondary,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(secondaryText)
        }
    }
}

@Composable
private fun NodeRow(node: AirsBleNode, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FBFE)),
        border = BorderStroke(1.dp, Color(0xFFE1E8F2)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(node.displayName, color = AirsInk, fontWeight = FontWeight.ExtraBold)
                Text("신호 강도: ${signalLabel(node.rssi)} · ${node.rssi} dBm", color = AirsMuted)
            }
            OutlinedButton(onClick = onClick) {
                Text("연결")
            }
        }
    }
}

@Composable
private fun WifiNetworkRow(
    network: WifiNetwork,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container = if (selected) Color(0xFFEAF7F0) else Color(0xFF514B62)
    val content = if (selected) AirsInk else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val prefix = if (network.isConnected) "현재 연결됨 / " else ""
        Text(
            text = "$prefix${network.ssid}${network.rssi?.let { " / ${it}dBm" } ?: ""}${if (selected) " / 선택됨" else ""}",
            color = content,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PayloadPreview(preview: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F9FF)),
        border = BorderStroke(1.dp, Color(0xFFD6E8FF)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("노드에 전달될 값", color = AirsInk, fontWeight = FontWeight.Bold)
            Text(preview, color = AirsMuted)
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    success: Boolean?,
    loading: Boolean = false
) {
    val accent = when (success) {
        true -> AirsGreen
        false -> AirsDanger
        null -> AirsBlue
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AirsCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(44.dp)
                    .background(accent, RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = AirsInk, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(body, color = AirsMuted)
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.width(26.dp), color = accent)
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: ColumnScopeBody
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AirsCard),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = AirsInk, fontWeight = FontWeight.ExtraBold)
            content()
        }
    }
}

@Composable
private fun StatusList(statuses: List<ProvisioningStatus>) {
    if (statuses.isEmpty()) return
    SectionCard(title = "노드 상태") {
        statuses.forEachIndexed { index, status ->
            Text(
                "${index + 1}. ${status.type.label}",
                color = AirsInk,
                fontWeight = FontWeight.Bold
            )
            Text(status.message, color = AirsMuted)
            if (index != statuses.lastIndex) Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color(0xFFE8EDF4)
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (enabled) AirsGradient else Brush.horizontalGradient(listOf(Color(0xFFE8EDF4), Color(0xFFE8EDF4)))),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = if (enabled) Color.White else Color(0xFF9AA6B5), fontWeight = FontWeight.Bold)
        }
    }
}

private fun stepGroup(step: ProvisioningStep): ProvisioningStep {
    return when (step) {
        ProvisioningStep.Start -> ProvisioningStep.Start
        ProvisioningStep.Scanning, ProvisioningStep.NodeList -> ProvisioningStep.Scanning
        ProvisioningStep.Connecting, ProvisioningStep.BluetoothConnected -> ProvisioningStep.Connecting
        ProvisioningStep.WifiInput -> ProvisioningStep.WifiInput
        ProvisioningStep.Provisioning -> ProvisioningStep.Provisioning
        ProvisioningStep.Success, ProvisioningStep.Failure -> ProvisioningStep.Success
    }
}

private fun signalLabel(rssi: Int): String {
    return when {
        rssi >= -55 -> "강함"
        rssi >= -70 -> "보통"
        else -> "약함"
    }
}

@Preview(showBackground = true)
@Composable
private fun ProvisioningScreenPreview() {
    AIRSTheme(dynamicColor = false) {
        ProvisioningScreen(
            uiState = ProvisioningUiState(
                step = ProvisioningStep.WifiInput,
                selectedNode = AirsBleNode("mock-node-0001", "AIRS-FAKE-0001", -48, "mock-0.1.0"),
                wifiNetworks = listOf(
                    WifiNetwork("AIRS-LAB", -42, true),
                    WifiNetwork("AIRS-GUEST", -68, false)
                ),
                ssid = "AIRS-LAB",
                password = "password",
                safePayloadPreview = """{"ssid":"AIRS-LAB","password":"********"}"""
            ),
            onStartScan = {},
            onNodeSelected = {},
            onWifiNetworkSelected = {},
            onPasswordChanged = {},
            onTogglePassword = {},
            onBluetoothSuccessConfirmed = {},
            onSendWifiConfig = {},
            onRetry = {},
            onBackToWifiInput = {}
        )
    }
}
