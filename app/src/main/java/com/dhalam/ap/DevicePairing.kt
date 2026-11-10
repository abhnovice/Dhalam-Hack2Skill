package com.dhalam.ap

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** System-owned device chooser; no location or unrestricted discovery permission. */
@Suppress("DEPRECATION")
@Composable
internal fun rememberDevicePairing(
    onReady: () -> Unit,
    onMessage: (String) -> Unit,
    onSettingsFallback: () -> Unit,
    systemUi: MutableState<Boolean>
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestReady by rememberUpdatedState(onReady)
    val latestMessage by rememberUpdatedState(onMessage)
    val manager = remember(context) { context.getSystemService(CompanionDeviceManager::class.java) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            systemUi.value = false
            latestMessage("Pairing cancelled. Try again when ready.")
        } else {
            val device = result.data?.getParcelableExtra<BluetoothDevice>(CompanionDeviceManager.EXTRA_DEVICE)
            if (device == null) {
                systemUi.value = false
                latestMessage("Pairing did not return a device. Use Bluetooth settings.")
            } else scope.launch {
                try {
                    // Caller requested CONNECT before opening the chooser; still catch revocation.
                    @Suppress("MissingPermission")
                    val paired = if (device.bondState == BluetoothDevice.BOND_BONDED) true else {
                        @Suppress("MissingPermission")
                        val started = device.createBond()
                        started && withTimeoutOrNull(60_000) {
                            @Suppress("MissingPermission")
                            while (device.bondState != BluetoothDevice.BOND_BONDED) delay(500)
                            true
                        } == true
                    }
                    if (paired) latestReady() else latestMessage("Pairing failed or timed out. Try Bluetooth settings.")
                } catch (_: SecurityException) {
                    latestMessage("Allow Nearby devices, then try pairing again.")
                } finally { systemUi.value = false }
            }
        }
    }
    return {
        if (manager == null || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            onSettingsFallback()
        } else {
            systemUi.value = true
            try {
                val request = AssociationRequest.Builder()
                    .addDeviceFilter(BluetoothDeviceFilter.Builder().build()).setSingleDevice(false).build()
                manager.associate(request, object : CompanionDeviceManager.Callback() {
                    override fun onDeviceFound(chooserLauncher: IntentSender) {
                        launcher.launch(IntentSenderRequest.Builder(chooserLauncher).build())
                    }
                    override fun onFailure(error: CharSequence?) {
                        systemUi.value = false
                        latestMessage("Pairing chooser unavailable. Use Bluetooth settings.")
                    }
                }, null)
            } catch (_: Exception) {
                systemUi.value = false
                onSettingsFallback()
            }
        }
    }
}
