package com.tyshi00.lightpods.ui

import android.bluetooth.BluetoothAdapter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

object BluetoothControl {
    val canControl: Boolean = false

    @Suppress("DEPRECATION")
    fun isEnabled(): Boolean? = runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled }.getOrNull()

    fun setEnabled(enabled: Boolean): Boolean = canControl && enabled
}

class BluetoothScreen(sealedActivity: SealedLightActivity) : SimpleLightScreen<Unit>(sealedActivity) {
    private val enabled = MutableStateFlow(BluetoothControl.isEnabled())
    private val notice = MutableStateFlow(false)

    override fun willShow() {
        enabled.value = BluetoothControl.isEnabled()
    }

    private fun toggle() {
        val current = enabled.value ?: return
        if (BluetoothControl.canControl && BluetoothControl.setEnabled(!current)) {
            enabled.value = !current
        } else {
            notice.value = true
        }
    }

    @Composable
    override fun Content() {
        val state by enabled.collectAsState()
        val showNotice by notice.collectAsState()

        LaunchedEffect(Unit) {
            while (true) {
                delay(2_000)
                enabled.value = BluetoothControl.isEnabled()
            }
        }

        PodScaffold(
            title = "Bluetooth",
            onBack = { goBack() },
            startPadding = OPTION_START_UNITS,
            topGap = OPTION_TOP_GAP_UNITS,
        ) {
            LightScrollView(modifier = Modifier.weight(1f).fillMaxWidth()) {
                OptionRow(
                    title = "Bluetooth",
                    subtitle = when (state) {
                        true -> "On"
                        false -> "Off"
                        null -> "Unavailable"
                    },
                    icon = toggleIcon(state == true),
                    onClick = ::toggle,
                )
                if (showNotice) {
                    PodText(
                        text = "Bluetooth can only be changed in LightOS settings for now.",
                        size = 20f,
                        lineHeight = 1.3f,
                        modifier = Modifier.padding(start = 2.95f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp()),
                    )
                }
            }
        }
    }
}
