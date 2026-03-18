package com.invisiblesushi.hanzilens.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.invisiblesushi.hanzilens.R
import com.invisiblesushi.hanzilens.ui.navigation.Routes
import com.invisiblesushi.hanzilens.ui.theme.AppColors
import com.invisiblesushi.hanzilens.ui.viewmodel.CameraViewModel
import kotlin.math.roundToLong

@Composable
fun SettingsScreen(navController: NavController, viewModel: CameraViewModel) {
    val ocrThrottleMs    by viewModel.ocrThrottleMs.collectAsState()
    val pinyinAutoSize   by viewModel.pinyinAutoSize.collectAsState()
    val showDebug        by viewModel.showDebug.collectAsState()
    val showOcrDebugText by viewModel.showOcrDebugText.collectAsState()
    val keepScreenOn     by viewModel.keepScreenOn.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SettingsSectionLabel(stringResource(R.string.settings_section_display))

        // Keep screen on toggle
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_keep_screen_on), color = Color.White) },
            supportingContent = {
                Text(
                    text  = if (keepScreenOn) stringResource(R.string.settings_keep_screen_on_on)
                            else               stringResource(R.string.settings_keep_screen_on_off),
                    color    = AppColors.Dim,
                    fontSize = 12.sp
                )
            },
            leadingContent = {
                Icon(Icons.Default.LightMode, contentDescription = null,
                    tint = if (keepScreenOn) AppColors.Green else AppColors.Dim)
            },
            trailingContent = {
                Switch(
                    checked         = keepScreenOn,
                    onCheckedChange = { viewModel.setKeepScreenOn(it) },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor   = Color.Black,
                        checkedTrackColor   = AppColors.Green,
                        uncheckedThumbColor = AppColors.Dim,
                        uncheckedTrackColor = AppColors.DividerStrong
                    )
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        HorizontalDivider(color = AppColors.Divider)

        // Debug overlay toggle
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_debug_overlay), color = Color.White) },
            supportingContent = {
                Text(
                    text  = if (showDebug) stringResource(R.string.settings_debug_overlay_on)
                            else           stringResource(R.string.settings_debug_overlay_off),
                    color    = AppColors.Dim,
                    fontSize = 12.sp
                )
            },
            leadingContent = {
                Icon(Icons.Default.BugReport, contentDescription = null,
                    tint = if (showDebug) AppColors.Green else AppColors.Dim)
            },
            trailingContent = {
                Switch(
                    checked         = showDebug,
                    onCheckedChange = { viewModel.setShowDebug(it) },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor   = Color.Black,
                        checkedTrackColor   = AppColors.Green,
                        uncheckedThumbColor = AppColors.Dim,
                        uncheckedTrackColor = AppColors.DividerStrong
                    )
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        // OCR debug text toggle
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_debug_ocr_text), color = Color.White) },
            supportingContent = {
                Text(
                    text  = if (showOcrDebugText) stringResource(R.string.settings_debug_ocr_text_on)
                            else                   stringResource(R.string.settings_debug_ocr_text_off),
                    color    = AppColors.Dim,
                    fontSize = 12.sp
                )
            },
            leadingContent = {
                Icon(Icons.Default.TextFields, contentDescription = null,
                    tint = if (showOcrDebugText) AppColors.Green else AppColors.Dim)
            },
            trailingContent = {
                Switch(
                    checked         = showOcrDebugText,
                    onCheckedChange = { viewModel.setShowOcrDebugText(it) },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor   = Color.Black,
                        checkedTrackColor   = AppColors.Green,
                        uncheckedThumbColor = AppColors.Dim,
                        uncheckedTrackColor = AppColors.DividerStrong
                    )
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        HorizontalDivider(color = AppColors.Divider)
        SettingsSectionLabel(stringResource(R.string.settings_section_ocr))

        // OCR speed slider
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_detection_speed), color = Color.White) },
            supportingContent = {
                Column {
                    Text(
                        text  = "Every ${"%.1f".format(ocrThrottleMs / 1000f)} s",
                        color    = AppColors.Green,
                        fontSize = 12.sp
                    )
                    Slider(
                        value         = ocrThrottleMs.toFloat(),
                        onValueChange = { viewModel.setOcrThrottleMs(it.roundToLong()) },
                        valueRange    = 250f..5000f,
                        steps         = 18,
                        colors        = SliderDefaults.colors(
                            thumbColor         = AppColors.Green,
                            activeTrackColor   = AppColors.Green,
                            inactiveTrackColor = AppColors.DividerStrong
                        )
                    )
                }
            },
            leadingContent = {
                Icon(Icons.Default.Speed, contentDescription = null, tint = AppColors.Dim)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        // Pinyin auto-size toggle
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_pinyin_autosize), color = Color.White) },
            supportingContent = {
                Text(
                    text  = if (pinyinAutoSize) stringResource(R.string.settings_pinyin_autosize_on)
                            else                stringResource(R.string.settings_pinyin_autosize_off),
                    color    = AppColors.Dim,
                    fontSize = 12.sp
                )
            },
            leadingContent = {
                Icon(Icons.Default.FormatSize, contentDescription = null, tint = AppColors.Dim)
            },
            trailingContent = {
                Switch(
                    checked         = pinyinAutoSize,
                    onCheckedChange = { viewModel.setPinyinAutoSize(it) },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor      = Color.Black,
                        checkedTrackColor      = AppColors.Green,
                        uncheckedThumbColor    = AppColors.Dim,
                        uncheckedTrackColor    = AppColors.DividerStrong
                    )
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        HorizontalDivider(color = AppColors.Divider)
        SettingsSectionLabel(stringResource(R.string.settings_section_app))

        // About
        ListItem(
            headlineContent   = { Text(stringResource(R.string.nav_about), color = Color.White) },
            supportingContent = { Text(stringResource(R.string.nav_about_subtitle), color = AppColors.Dim) },
            leadingContent    = {
                Icon(Icons.Default.Info, contentDescription = null, tint = AppColors.Dim)
            },
            trailingContent   = {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF555555))
            },
            colors   = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { navController.navigate(Routes.ABOUT) }
        )

        HorizontalDivider(color = AppColors.Divider)
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text       = text.uppercase(),
        color      = AppColors.Green,
        fontSize   = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier   = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}
