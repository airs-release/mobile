package com.airs.android.provisioning.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.airs.android.R
import com.airs.android.provisioning.model.AirsBleNode
import com.airs.android.provisioning.model.ProvisioningStep
import com.airs.android.provisioning.model.ProvisioningUiState
import com.airs.android.ui.theme.AIRSTheme

private const val DESIGN_WIDTH = 941f
private const val DESIGN_HEIGHT = 1672f
private val AirsBlue = Color(0xFF0875F5)
private val AirsGreen = Color(0xFF28C86F)
private val AirsNavy = Color(0xFF071A33)
private val AirsText = Color(0xFF637089)
private val AirsLine = Color(0xFFE5EAF2)
private val AirsFieldBorder = Color(0xFFD8DEE8)
private val AirsRed = Color(0xFFF04452)

private data class DesignScale(
    val maxWidth: Dp,
    val maxHeight: Dp
) {
    fun width(value: Float): Dp = maxWidth * (value / DESIGN_WIDTH)
    fun height(value: Float): Dp = maxHeight * (value / DESIGN_HEIGHT)
    fun font(value: Float) = (maxHeight.value * (value / DESIGN_HEIGHT)).sp
    fun frame(left: Float, top: Float, width: Float, height: Float): Modifier {
        return Modifier
            .offset(x = this.width(left), y = this.height(top))
            .size(width = this.width(width), height = this.height(height))
    }
}

@Composable
fun ProvisioningRoute(viewModel: ProvisioningViewModel) {
    ProvisioningScreen(
        uiState = viewModel.uiState,
        onStartScan = viewModel::startScan,
        onNodeSelected = viewModel::selectNode,
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
    onPasswordChanged: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onBluetoothSuccessConfirmed: () -> Unit,
    onSendWifiConfig: () -> Unit,
    onRetry: () -> Unit,
    onBackToWifiInput: () -> Unit
) {
    when (uiState.step) {
        ProvisioningStep.Start -> NoNodeScreen(onAddDevice = onStartScan)
        ProvisioningStep.Scanning -> BluetoothListScreen(
            uiState = uiState,
            showScanningHint = true,
            onNodeSelected = onNodeSelected,
            onBack = onRetry
        )

        ProvisioningStep.NodeList -> BluetoothListScreen(
            uiState = uiState,
            showScanningHint = false,
            onNodeSelected = onNodeSelected,
            onBack = onRetry
        )

        ProvisioningStep.Connecting -> StaticImageScreen(R.drawable.ui_06_bluetooth_linking)
        ProvisioningStep.BluetoothConnected -> BluetoothSuccessScreen(
            uiState = uiState,
            onConfirm = onBluetoothSuccessConfirmed
        )
        ProvisioningStep.WifiInput -> WifiSetupScreen(
            uiState = uiState,
            onPasswordChanged = onPasswordChanged,
            onTogglePassword = onTogglePassword,
            onSendWifiConfig = onSendWifiConfig,
            onBack = onRetry
        )

        ProvisioningStep.Provisioning -> WifiProvisioningScreen(uiState = uiState)
        ProvisioningStep.Success -> SetupSuccessScreen(uiState = uiState)
        ProvisioningStep.Failure -> {
            if (uiState.statuses.isNotEmpty() || uiState.ssid.isNotBlank()) {
                SetupFailureScreen(uiState = uiState, onRetry = onBackToWifiInput, onBack = onRetry)
            } else {
                BluetoothFailureScreen(uiState = uiState, onConfirm = onRetry)
            }
        }
    }
}

@Composable
private fun NoNodeScreen(onAddDevice: () -> Unit) {
    ImageScreenFrame(imageRes = R.drawable.ui_04_no_node) {
        Cover(left = 92f, top = 980f, width = 760f, height = 280f)
        GradientButtonFrame(
            left = 104f,
            top = 1002f,
            width = 732f,
            height = 104f,
            text = "+  새 디바이스 추가",
            onClick = onAddDevice
        )
        OutlineButtonFrame(
            left = 104f,
            top = 1135f,
            width = 732f,
            height = 100f,
            text = "▣  디바이스 추가 가이드 보기",
            onClick = {}
        )
    }
}

@Composable
private fun BluetoothListScreen(
    uiState: ProvisioningUiState,
    showScanningHint: Boolean,
    onNodeSelected: (AirsBleNode) -> Unit,
    onBack: () -> Unit
) {
    ImageScreenFrame(imageRes = R.drawable.ui_05_bluetooth_list) {
        BackHotspot(onBack = onBack)
        val fallbackNode = uiState.selectedNode ?: AirsBleNode("pending", "AIRS-AC-24E1", -48, "unknown")
        val nodes = uiState.nodes.ifEmpty { listOf(fallbackNode) }
        Cover(left = 65f, top = 560f, width = 812f, height = 900f)
        NodeListPanel(
            nodes = nodes,
            isScanning = showScanningHint,
            onNodeSelected = onNodeSelected
        )
    }
}

@Composable
private fun BluetoothSuccessScreen(uiState: ProvisioningUiState, onConfirm: () -> Unit) {
    ImageScreenFrame(imageRes = R.drawable.ui_08_bluetooth_success) {
        Cover(left = 210f, top = 875f, width = 520f, height = 75f)
        CenteredFrameText(
            left = 220f,
            top = 884f,
            width = 500f,
            height = 55f,
            text = "${selectedNodeName(uiState)}이(가) 연결되었습니다.",
            color = AirsText,
            fontSize = 24
        )
        Cover(left = 218f, top = 925f, width = 505f, height = 115f)
        GradientButtonFrame(left = 232f, top = 934f, width = 480f, height = 92f, text = "확인", onClick = onConfirm)
    }
}

@Composable
private fun BluetoothFailureScreen(uiState: ProvisioningUiState, onConfirm: () -> Unit) {
    ImageScreenFrame(imageRes = R.drawable.ui_07_bluetooth_fail) {
        if (uiState.selectedNode != null) {
            Cover(left = 300f, top = 795f, width = 340f, height = 45f)
            CenteredFrameText(
                left = 300f,
                top = 795f,
                width = 340f,
                height = 45f,
                text = selectedNodeName(uiState),
                color = AirsText,
                fontSize = 20
            )
        }
        Cover(left = 218f, top = 925f, width = 505f, height = 115f)
        GradientButtonFrame(left = 232f, top = 934f, width = 480f, height = 92f, text = "확인", onClick = onConfirm)
    }
}

@Composable
private fun WifiSetupScreen(
    uiState: ProvisioningUiState,
    onPasswordChanged: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSendWifiConfig: () -> Unit,
    onBack: () -> Unit
) {
    ImageScreenFrame(imageRes = R.drawable.ui_09_wifi_setup) {
        BackHotspot(onBack = onBack)
        Cover(left = 245f, top = 420f, width = 380f, height = 185f)
        SelectedNodeText(uiState)
        Cover(left = 50f, top = 780f, width = 840f, height = 125f)
        ReadOnlyFieldFrame(
            left = 52f,
            top = 790f,
            width = 836f,
            height = 106f,
            text = if (uiState.isLoadingWifiNetworks) "현재 연결된 Wi-Fi 확인 중..." else uiState.ssid,
        )
        Cover(left = 50f, top = 1015f, width = 840f, height = 125f)
        PasswordInput(
            left = 52f,
            top = 1028f,
            width = 720f,
            height = 104f,
            value = uiState.password,
            passwordVisible = uiState.passwordVisible,
            onValueChange = onPasswordChanged
        )
        OutlineButtonFrame(left = 790f, top = 1028f, width = 98f, height = 104f, text = if (uiState.passwordVisible) "숨김" else "표시", onClick = onTogglePassword)
        Cover(left = 42f, top = 1245f, width = 856f, height = 145f)
        GradientButtonFrame(
            left = 52f,
            top = 1260f,
            width = 836f,
            height = 108f,
            text = "연결",
            enabled = uiState.canSendWifiConfig,
            onClick = onSendWifiConfig
        )
    }
}

@Composable
private fun WifiProvisioningScreen(uiState: ProvisioningUiState) {
    ImageScreenFrame(imageRes = R.drawable.ui_09_wifi_setup) {
        Cover(left = 245f, top = 420f, width = 380f, height = 185f)
        SelectedNodeText(uiState)
        Cover(left = 50f, top = 780f, width = 840f, height = 125f)
        ReadOnlyFieldFrame(
            left = 52f,
            top = 790f,
            width = 836f,
            height = 106f,
            text = uiState.ssid,
        )
        Cover(left = 42f, top = 1245f, width = 856f, height = 145f)
        FrameText(
            left = 95f,
            top = 1265f,
            width = 750f,
            height = 90f,
            text = uiState.statuses.lastOrNull()?.message ?: "노드에 Wi-Fi 정보를 전송하고 있습니다.",
            color = Color(0xFF0875F5),
            fontSize = 13,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SetupSuccessScreen(uiState: ProvisioningUiState) {
    ImageScreenFrame(imageRes = R.drawable.ui_10_setup_success) {
        Cover(left = 150f, top = 800f, width = 640f, height = 105f)
        CenteredFrameText(
            left = 160f,
            top = 805f,
            width = 620f,
            height = 95f,
            text = "${selectedNodeName(uiState)}이(가) Wi-Fi 네트워크에 연결되어\n정상적으로 사용할 수 있습니다.",
            color = AirsText,
            fontSize = 22
        )
        Cover(left = 290f, top = 962f, width = 500f, height = 155f)
        ResultNodeSummary(uiState = uiState, isSuccess = true)
        Cover(left = 42f, top = 1185f, width = 856f, height = 235f)
        GradientButtonFrame(left = 56f, top = 1198f, width = 828f, height = 92f, text = "내 노드로 이동", onClick = {})
        OutlineButtonFrame(left = 56f, top = 1312f, width = 828f, height = 92f, text = "노드 상세 보기", onClick = {})
    }
}

@Composable
private fun SetupFailureScreen(uiState: ProvisioningUiState, onRetry: () -> Unit, onBack: () -> Unit) {
    ImageScreenFrame(imageRes = R.drawable.ui_11_setup_failure) {
        Cover(left = 290f, top = 900f, width = 500f, height = 155f)
        ResultNodeSummary(uiState = uiState, isSuccess = false)
        Cover(left = 42f, top = 1135f, width = 856f, height = 235f)
        GradientButtonFrame(left = 56f, top = 1148f, width = 828f, height = 92f, text = "다시 시도", onClick = onRetry)
        OutlineButtonFrame(left = 56f, top = 1265f, width = 828f, height = 92f, text = "이전으로 돌아가기", onClick = onBack)
    }
}

@Composable
private fun StaticImageScreen(@DrawableRes imageRes: Int) {
    ImageScreenFrame(imageRes = imageRes)
}

@Composable
private fun ImageScreenFrame(
    @DrawableRes imageRes: Int,
    overlays: @Composable BoxWithConstraintsScope.() -> Unit = {}
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.TopCenter
    ) {
        val designRatio = DESIGN_WIDTH / DESIGN_HEIGHT
        val screenRatio = maxWidth.value / maxHeight.value
        val frameModifier = if (screenRatio > designRatio) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(designRatio)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(designRatio)
        }

        BoxWithConstraints(modifier = frameModifier) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            overlays()
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.BackHotspot(onBack: () -> Unit) {
    Hotspot(left = 26f, top = 100f, width = 70f, height = 80f, onClick = onBack)
}

@Composable
private fun BoxWithConstraintsScope.Cover(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color = Color.White
) {
    Box(modifier = frameModifier(left, top, width, height).background(color))
}

@Composable
private fun BoxWithConstraintsScope.GradientButtonFrame(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val scale = designScale()
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = scale.frame(left, top, width, height)
            .clip(RoundedCornerShape(scale.height(18f)))
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(AirsBlue, AirsGreen))
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFFDCE4EF), Color(0xFFDCE4EF)))
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = scale.font(32f),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BoxWithConstraintsScope.OutlineButtonFrame(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    text: String,
    onClick: () -> Unit
) {
    val scale = designScale()
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = scale.frame(left, top, width, height)
            .clip(RoundedCornerShape(scale.height(18f)))
            .background(Color.White)
            .border(1.4.dp, AirsBlue, RoundedCornerShape(scale.height(18f)))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = AirsBlue,
            fontSize = scale.font(if (height < 85f) 22f else 30f),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BoxWithConstraintsScope.NodeListPanel(
    nodes: List<AirsBleNode>,
    isScanning: Boolean,
    onNodeSelected: (AirsBleNode) -> Unit
) {
    val scale = designScale()
    Box(
        modifier = scale.frame(72f, 570f, 796f, 880f)
            .clip(RoundedCornerShape(scale.height(18f)))
            .background(Color.White)
            .border(1.dp, AirsLine, RoundedCornerShape(scale.height(18f)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val visibleNodes = if (isScanning) {
                listOf(AirsBleNode("scanning", "AIRS 기기 검색 중", -60, ""))
            } else {
                nodes.take(5)
            }
            visibleNodes.forEachIndexed { index, node ->
                DynamicNodeRow(
                    node = node,
                    isScanning = isScanning,
                    scale = scale,
                    onClick = { if (!isScanning) onNodeSelected(node) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scale.height(176f))
                )
                if (index != visibleNodes.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(AirsLine)
                    )
                }
            }
        }
    }
}

@Composable
private fun DynamicNodeRow(
    node: AirsBleNode,
    isScanning: Boolean,
    scale: DesignScale,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Row(
        modifier = modifier.padding(
            start = scale.width(34f),
            end = scale.width(36f)
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniDeviceThumb(scale = scale)
        Spacer(modifier = Modifier.width(scale.width(42f)))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(scale.height(12f))
        ) {
            Text(
                text = node.displayName,
                color = AirsNavy,
                fontSize = scale.font(31f),
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Text(
                text = if (isScanning) "잠시만 기다려주세요" else "▥  신호 강도: ${signalLabel(node.rssi)}",
                color = AirsText,
                fontSize = scale.font(22f),
                maxLines = 1
            )
        }
        if (isScanning) {
            Box(
                modifier = Modifier
                    .size(scale.width(48f))
                    .clip(CircleShape)
                    .border(4.dp, AirsBlue, CircleShape)
            )
        } else {
            SmallConnectButton(scale = scale, onClick = onClick)
        }
    }
}

@Composable
private fun MiniDeviceThumb(scale: DesignScale) {
    Box(
        modifier = Modifier
            .size(scale.width(112f))
            .clip(RoundedCornerShape(scale.width(20f)))
            .background(Color(0xFFF8FAFD)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ui_device_thumb),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun SmallConnectButton(scale: DesignScale, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(width = scale.width(150f), height = scale.height(82f))
            .clip(RoundedCornerShape(scale.width(11f)))
            .background(Color.White)
            .border(1.3.dp, AirsBlue, RoundedCornerShape(scale.width(11f)))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("연결", color = AirsBlue, fontSize = scale.font(25f), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BoxWithConstraintsScope.SelectedNodeText(uiState: ProvisioningUiState) {
    FrameText(
        left = 258f,
        top = 430f,
        width = 355f,
        height = 56f,
        text = uiState.selectedNode?.displayName ?: "선택한 AIRS 기기",
        color = AirsNavy,
        fontSize = 27,
        fontWeight = FontWeight.ExtraBold
    )
    FrameText(
        left = 258f,
        top = 500f,
        width = 250f,
        height = 42f,
        text = "●  페어링 완료",
        color = AirsGreen,
        fontSize = 17,
        fontWeight = FontWeight.Bold
    )
    FrameText(
        left = 258f,
        top = 560f,
        width = 280f,
        height = 42f,
        text = "▥  신호 강도: ${signalLabel(uiState.selectedNode?.rssi ?: -60)}",
        color = AirsText,
        fontSize = 16
    )
}

@Composable
private fun BoxWithConstraintsScope.ReadOnlyFieldFrame(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    text: String
) {
    val scale = designScale()
    Box(
        modifier = scale.frame(left, top, width, height)
            .clip(RoundedCornerShape(scale.height(13f)))
            .background(Color.White)
            .border(1.dp, AirsFieldBorder, RoundedCornerShape(scale.height(13f)))
            .padding(horizontal = scale.width(26f)),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text.ifBlank { "현재 연결된 Wi-Fi를 확인하지 못했습니다" },
            color = AirsNavy,
            fontSize = scale.font(24f),
            maxLines = 1
        )
    }
}

@Composable
private fun BoxWithConstraintsScope.ResultNodeSummary(
    uiState: ProvisioningUiState,
    isSuccess: Boolean
) {
    val node = uiState.selectedNode
    val statusColor = if (isSuccess) AirsGreen else AirsRed
    FrameText(
        left = 306f,
        top = if (isSuccess) 965f else 904f,
        width = 470f,
        height = 46f,
        text = selectedNodeName(uiState),
        color = AirsNavy,
        fontSize = 32,
        fontWeight = FontWeight.ExtraBold
    )
    StatusPill(
        left = 306f,
        top = if (isSuccess) 1023f else 960f,
        width = if (isSuccess) 132f else 138f,
        height = 42f,
        text = if (isSuccess) "● 연결됨" else "● 연결 실패",
        color = statusColor
    )
    FrameText(
        left = 306f,
        top = if (isSuccess) 1092f else 1035f,
        width = 245f,
        height = 36f,
        text = "⌁  ${uiState.ssid.ifBlank { "Wi-Fi" }}",
        color = AirsText,
        fontSize = 20
    )
    FrameText(
        left = 596f,
        top = if (isSuccess) 1092f else 1035f,
        width = 245f,
        height = 36f,
        text = "▥  신호 강도: ${signalLabel(node?.rssi ?: -60)}",
        color = AirsText,
        fontSize = 20
    )
}

@Composable
private fun BoxWithConstraintsScope.StatusPill(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    text: String,
    color: Color
) {
    val scale = designScale()
    Box(
        modifier = scale.frame(left, top, width, height)
            .clip(RoundedCornerShape(scale.height(18f)))
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = scale.font(18f),
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun BoxWithConstraintsScope.Hotspot(
    frame: Frame,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Hotspot(
        left = frame.left,
        top = frame.top,
        width = frame.width,
        height = frame.height,
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
private fun BoxWithConstraintsScope.Hotspot(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = frameModifier(left, top, width, height)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
    )
}

@Composable
private fun BoxWithConstraintsScope.FrameText(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    text: String,
    color: Color,
    fontSize: Int,
    fontWeight: FontWeight = FontWeight.Normal
) {
    val scale = designScale()
    Box(
        modifier = scale.frame(left, top, width, height),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = color,
            fontSize = scale.font(fontSize.toFloat()),
            fontWeight = fontWeight,
            maxLines = 2
        )
    }
}

@Composable
private fun BoxWithConstraintsScope.CenteredFrameText(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    text: String,
    color: Color,
    fontSize: Int,
    fontWeight: FontWeight = FontWeight.Normal
) {
    val scale = designScale()
    Box(
        modifier = scale.frame(left, top, width, height),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = scale.font(fontSize.toFloat()),
            fontWeight = fontWeight,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun BoxWithConstraintsScope.PasswordInput(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    value: String,
    passwordVisible: Boolean,
    onValueChange: (String) -> Unit
) {
    val scale = designScale()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = scale.frame(left, top, width, height)
            .clip(RoundedCornerShape(scale.height(13f)))
            .background(Color.White)
            .border(1.dp, AirsFieldBorder, RoundedCornerShape(scale.height(13f)))
            .padding(horizontal = scale.width(26f), vertical = scale.height(22f)),
        singleLine = true,
        textStyle = TextStyle(
            color = Color(0xFF071A33),
            fontSize = scale.font(24f)
        ),
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}

private fun BoxWithConstraintsScope.designScale() = DesignScale(maxWidth, maxHeight)

private fun BoxWithConstraintsScope.frameModifier(
    left: Float,
    top: Float,
    width: Float,
    height: Float
): Modifier = designScale().frame(left, top, width, height)

private data class Frame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

private fun signalLabel(rssi: Int): String {
    return when {
        rssi >= -55 -> "강함"
        rssi >= -70 -> "보통"
        else -> "약함"
    }
}

private fun selectedNodeName(uiState: ProvisioningUiState): String {
    return uiState.selectedNode?.displayName ?: "선택한 AIRS 기기"
}

@Preview(showBackground = true)
@Composable
private fun ProvisioningScreenPreview() {
    AIRSTheme(dynamicColor = false) {
        ProvisioningScreen(
            uiState = ProvisioningUiState(
                step = ProvisioningStep.WifiInput,
                selectedNode = AirsBleNode("mock-node-0001", "AIRS-AC-24E1", -48, "mock-0.1.0"),
                ssid = "AIRS_Office_5G"
            ),
            onStartScan = {},
            onNodeSelected = {},
            onPasswordChanged = {},
            onTogglePassword = {},
            onBluetoothSuccessConfirmed = {},
            onSendWifiConfig = {},
            onRetry = {},
            onBackToWifiInput = {}
        )
    }
}
