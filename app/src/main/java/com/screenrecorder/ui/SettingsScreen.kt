package com.screenrecorder.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.screenrecorder.manager.RecordingMode
import com.screenrecorder.manager.RecordingPreferences
import com.screenrecorder.manager.ThemeMode
import com.screenrecorder.model.RecordingState

@Composable
fun SettingsScreen(
    recordingState: RecordingState? = null,
    onBackClick: () -> Unit
) {
    val isRecording = recordingState == RecordingState.RECORDING
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onBackClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("\u2190", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            SectionHeader(
                title = "Appearance",
                subtitle = "Theme and visual preferences"
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppearanceSection()

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(
                title = "Floating Controls",
                subtitle = "Overlay appearance and behavior"
            )
            Spacer(modifier = Modifier.height(8.dp))
            FloatingControlsSection(isRecording = isRecording)

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(
                title = "Tutorial Features",
                subtitle = "Enhance tutorial and guide videos"
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsCard {
                ShowTouchesSetting()
                SettingsDivider()
                ComingSoonSetting(
                    icon = "\uD83D\uDD0D",
                    title = "Auto Zoom",
                    description = "Smart zoom on tapped areas"
                )
                SettingsDivider()
                ComingSoonSetting(
                    icon = "\uD83D\uDD04",
                    title = "Swipe Trail",
                    description = "Visual trail for swipe gestures"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(
                title = "About",
                subtitle = "App information and support"
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsCard {
                InfoSetting(icon = "\u2139\uFE0F", title = "App Version", value = "1.0.0")
                SettingsDivider()
                ActionSetting(icon = "\uD83D\uDEE1\uFE0F", title = "Privacy Policy", subtitle = "Read our privacy policy")
                SettingsDivider()
                ActionSetting(icon = "\uD83D\uDCDC", title = "Terms of Service", subtitle = "View terms and conditions")
                SettingsDivider()
                ActionSetting(icon = "\u2709\uFE0F", title = "Send Feedback", subtitle = "Share your thoughts with us")
                SettingsDivider()
                ActionSetting(icon = "\u2B50", title = "Rate App", subtitle = "Love this app? Leave a review!")
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun AppearanceSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var themeMode by remember {
        mutableStateOf(RecordingPreferences.getThemeMode(context))
    }

    SettingsCard {
        ThemeOption(
            icon = "\uD83C\uDF0D",
            title = "Follow System",
            description = "Uses your device theme",
            selected = themeMode == ThemeMode.SYSTEM,
            onClick = {
                themeMode = ThemeMode.SYSTEM
                RecordingPreferences.setThemeMode(context, ThemeMode.SYSTEM)
            }
        )
        SettingsDivider()
        ThemeOption(
            icon = "\u2600\uFE0F",
            title = "Light",
            description = "Bright, light background",
            selected = themeMode == ThemeMode.LIGHT,
            onClick = {
                themeMode = ThemeMode.LIGHT
                RecordingPreferences.setThemeMode(context, ThemeMode.LIGHT)
            }
        )
        SettingsDivider()
        ThemeOption(
            icon = "\uD83C\uDF19",
            title = "Dark",
            description = "Deep, dark background",
            selected = themeMode == ThemeMode.DARK,
            onClick = {
                themeMode = ThemeMode.DARK
                RecordingPreferences.setThemeMode(context, ThemeMode.DARK)
            }
        )
    }
}

@Composable
private fun ThemeOption(
    icon: String,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Text(
                    text = "\u2713",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FloatingControlsSection(isRecording: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var recordingMode by remember {
        mutableStateOf(RecordingPreferences.getRecordingMode(context))
    }
    var autoCollapse by remember {
        mutableStateOf(RecordingPreferences.isAutoCollapseEnabled(context))
    }
    var snapToEdge by remember {
        mutableStateOf(RecordingPreferences.isSnapToEdgeEnabled(context))
    }
    var opacity by remember {
        mutableFloatStateOf(RecordingPreferences.getOverlayOpacity(context).toFloat())
    }
    var collapseDelayExpanded by remember { mutableStateOf(false) }
    var delayMs by remember {
        mutableIntStateOf(RecordingPreferences.getAutoCollapseDelayMs(context).toInt())
    }

    val showControls = recordingMode == RecordingMode.OVERLAY
    val disabled = isRecording

    if (disabled) {
        Text(
            text = "Overlay controls are disabled while recording",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
        )
    }
    SettingsCard(modifier = if (disabled) Modifier.alpha(0.5f) else Modifier) {
        SwitchSetting(
            icon = "\uD83C\uDF1F",
            title = "Show Floating Controls",
            description = if (showControls) "Visible on screen and in recordings"
            else "Use notification controls only",
            checked = showControls,
            enabled = !disabled,
            onCheckedChange = { enabled ->
                recordingMode = if (enabled) RecordingMode.OVERLAY else RecordingMode.CLEAN
                RecordingPreferences.setRecordingMode(context, recordingMode)
            }
        )

        AnimatedVisibility(
            visible = showControls,
            enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))
        ) {
            Column {
                SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "\uD83D\uDCA7", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Overlay Opacity",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${opacity.toInt()}%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    onValueChangeFinished = {
                        RecordingPreferences.setOverlayOpacity(context, opacity.toInt())
                    },
                    enabled = !disabled,
                    valueRange = 30f..100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline
                    )
                )
                SettingsDivider()
                SwitchSetting(
                    icon = "\uD83D\uDD04",
                    title = "Auto Collapse",
                    description = "Collapse controls after inactivity",
                    checked = autoCollapse,
                    enabled = !disabled,
                    onCheckedChange = {
                        autoCollapse = it
                        RecordingPreferences.setAutoCollapseEnabled(context, it)
                    }
                )
                AnimatedVisibility(visible = autoCollapse && !disabled) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !disabled) { collapseDelayExpanded = !collapseDelayExpanded }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "\u23F0", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto Collapse Delay",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${"%.1f".format(delayMs / 1000f)}s",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Slider(
                            value = delayMs.toFloat(),
                            onValueChange = { delayMs = it.toInt() },
                            onValueChangeFinished = {
                                RecordingPreferences.setAutoCollapseDelayMs(context, delayMs.toLong())
                            },
                            enabled = !disabled,
                            valueRange = 1000f..10000f,
                            steps = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("1s", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("10s", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                SettingsDivider()
                SwitchSetting(
                    icon = "\uD83D\uDCCD",
                    title = "Snap to Edge",
                    description = "Auto-snap overlay to screen edge",
                    checked = snapToEdge,
                    enabled = !disabled,
                    onCheckedChange = {
                        snapToEdge = it
                        RecordingPreferences.setSnapToEdgeEnabled(context, it)
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(250))
                .padding(vertical = 4.dp)
        ) { content() }
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outline)
    )
}

@Composable
private fun SwitchSetting(
    icon: String,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier else Modifier.alpha(0.5f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange ?: {},
            enabled = enabled && onCheckedChange != null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
private fun ShowTouchesSetting() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var enabled by remember {
        mutableStateOf(
            try {
                Settings.System.getInt(context.contentResolver, "show_touches", 0) == 1
            } catch (e: Exception) {
                false
            }
        )
    }
    var showDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = try {
                    Settings.System.getInt(context.contentResolver, "show_touches", 0) == 1
                } catch (e: Exception) {
                    false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Enable Show Touches") },
            text = {
                Text(
                    buildString {
                        append("Show Touches is a built-in Android feature that displays a small circle wherever you tap. To enable it, follow these steps:\n\n")
                        append("1. Open the Settings app on your phone\n")
                        append("2. Scroll to About Phone → find Build Number → tap it 7 times → you'll see 'You are now a developer!'\n")
                        append("3. Go back — Developer Options will now appear in Settings\n")
                        append("4. Open Developer Options → scroll down to the Input section\n")
                        append("5. Tap Show taps to turn it ON\n")
                        append("6. Return to this app — the toggle will update automatically")
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                }) {
                    Text("Open About Phone")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Got It")
                }
            }
        )
    }

    SwitchSetting(
        icon = "\uD83D\uDCA1",
        title = "Show Touches",
        description = if (enabled) "Android native tap indicators visible on screen"
        else "Show touch indicators using system setting",
        checked = enabled,
        onCheckedChange = { value ->
            if (value && !enabled) {
                showDialog = true
            }
        }
    )
}

@Composable
private fun TapColorPicker(
    selectedColor: String,
    onColorSelected: (String) -> Unit
) {
    val colors = listOf(
        "#FF0000" to Color(0xFFFF0000),
        "#0000FF" to Color(0xFF0000FF),
        "#00FF00" to Color(0xFF00FF00),
        "#FFFFFFFF" to Color(0xFFFFFFFF),
        "#800080" to Color(0xFF800080),
        "#FFC0CB" to Color(0xFFFFC0CB)
    )

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = "Color",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            colors.forEach { (hex, color) ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (hex == selectedColor) Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            ) else Modifier
                        )
                        .then(
                            if (hex == "#FFFFFFFF") Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                CircleShape
                            ) else Modifier
                        )
                        .clickable { onColorSelected(hex) },
                    contentAlignment = Alignment.Center
                ) {
                    if (hex == selectedColor) {
                        Text(
                            text = "\u2713",
                            fontSize = 14.sp,
                            color = if (hex == "#FFFFFFFF" || hex == "#FFC0CB") Color.Black
                            else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TapShapeSelector(
    selectedShape: String,
    onShapeSelected: (String) -> Unit
) {
    val shapes = listOf("circle", "square", "ripple")
    val shapeLabels = mapOf("circle" to "Circle", "square" to "Square", "ripple" to "Ripple")

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = "Shape",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            shapes.forEach { shape ->
                val isSelected = shape == selectedShape
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onShapeSelected(shape) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shapeLabels[shape] ?: shape,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun TapSizeSlider(
    currentSize: Int,
    onSizeChanged: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Size",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${currentSize}dp",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = currentSize.toFloat(),
            onValueChange = { onSizeChanged(it.toInt()) },
            valueRange = 10f..40f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("10", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("40", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TapIndicatorPreview(
    color: String,
    shape: String,
    sizeDp: Int
) {
    val indicatorColor = parseHexColor(color)
    val previewSize = (sizeDp * 1.5f).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Preview",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(previewSize)) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                val radius = size.minDimension / 2
                val fillAlpha = 102
                val strokeAlpha = 180

                when (shape) {
                    "square" -> {
                        val cornerRadius = radius / 4
                        drawRoundRect(
                            color = indicatorColor.copy(alpha = fillAlpha / 255f),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                centerX - radius,
                                centerY - radius
                            ),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
                        )
                        drawRoundRect(
                            color = indicatorColor.copy(alpha = strokeAlpha / 255f),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                centerX - radius,
                                centerY - radius
                            ),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
                            style = Stroke(width = 2f * density)
                        )
                    }
                    "ripple" -> {
                        drawCircle(
                            color = indicatorColor.copy(alpha = (fillAlpha * 0.6f).toInt() / 255f),
                            radius = radius * 1f,
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                        )
                        drawCircle(
                            color = indicatorColor.copy(alpha = (strokeAlpha * 0.4f).toInt() / 255f),
                            radius = radius * 1.3f,
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                            style = Stroke(width = 2f * density)
                        )
                        drawCircle(
                            color = indicatorColor.copy(alpha = (strokeAlpha * 0.2f).toInt() / 255f),
                            radius = radius * 1.6f,
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                            style = Stroke(width = 1.5f * density)
                        )
                    }
                    else -> {
                        drawCircle(
                            color = indicatorColor.copy(alpha = fillAlpha / 255f),
                            radius = radius,
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                        )
                        drawCircle(
                            color = indicatorColor.copy(alpha = strokeAlpha / 255f),
                            radius = radius,
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                            style = Stroke(width = 2f * density)
                        )
                    }
                }
            }
        }
    }
}

private fun parseHexColor(hex: String): Color {
    return try {
        val hexStr = hex.removePrefix("#")
        val argb = hexStr.toLong(16)
        Color(argb.toInt())
    } catch (e: Exception) {
        Color.White
    }
}

@Composable
private fun ComingSoonSetting(icon: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.5f)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Soon",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun InfoSetting(icon: String, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(text = value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionSetting(icon: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(text = "\u203A", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
