package com.dhalam.ap

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun reviewActivity(context: Context): ComponentActivity? {
    var current = context
    repeat(12) {
        val candidate = current
        if (candidate is ComponentActivity) return candidate
        current = (candidate as? ContextWrapper)?.baseContext ?: return null
    }
    return null
}

@Composable
internal fun BluetoothReviewPanel(
    companionOnly: Boolean = false,
    onReport: suspend (RemoteDeviceInventory) -> String = { error("Sign in to Dhalam to use Gemini.") }
) {
    val context = LocalContext.current
    val activity = remember(context) { reviewActivity(context) }
    if (activity == null) { Text(trText("Device review is unavailable here.")); return }
    val model = remember(activity) { ViewModelProvider(activity)[BluetoothReviewViewModel::class.java] }
    val state = model.state
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val systemUi = remember { mutableStateOf(false) }
    DisposableEffect(model, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !activity.isChangingConfigurations && !systemUi.value && model.state.busy) model.stopTransfers()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (!activity.isChangingConfigurations) model.stopTransfers()
        }
    }
    var permissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var rationale by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var report by rememberSaveable(state.inventory?.observedAt) { mutableStateOf<String?>(null) }
    var reportBusy by remember { mutableStateOf(false) }
    var reportError by remember { mutableStateOf<String?>(null) }
    val latestInventory by rememberUpdatedState(state.inventory)
    val scope = rememberCoroutineScope()
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        systemUi.value = false
        val action = permissionAction
        permissionAction = null
        if (granted) { denied = false; action?.invoke() } else denied = true
    }
    val prefs = remember { context.getSharedPreferences("dhalam_device_review", Context.MODE_PRIVATE) }
    fun perform(action: () -> Unit) {
        if (model.permitted()) { action(); return }
        permissionAction = action
        if (Build.VERSION.SDK_INT >= 31) {
            when {
                activity.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT) -> rationale = true
                prefs.getBoolean("requested_connect", false) -> denied = true
                else -> {
                    prefs.edit().putBoolean("requested_connect", true).apply()
                    systemUi.value = true
                    request.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
        }
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        systemUi.value = false
        if (model.permitted()) {
            model.refreshPeers()
            model.pairingMessage("Back in Dhalam. Show a fresh code on the sharing device.")
        }
    }
    fun openBluetoothSettings() {
        model.stopTransfers()
        systemUi.value = true
        val intents = listOf(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        val opened = intents.any { intent -> runCatching { settings.launch(intent); true }.getOrDefault(false) }
        if (!opened) {
            systemUi.value = false
            model.pairingMessage("Bluetooth settings are unavailable on this device.")
        } else model.pairingMessage("Pair your devices, then press Back to return to Dhalam.")
    }
    val pairDevice = rememberDevicePairing(
        onReady = {
            model.refreshPeers()
            if (companionOnly) model.startSharing()
            else model.pairingMessage("Paired. Select the device and enter its Dhalam code.")
        },
        onMessage = model::pairingMessage,
        onSettingsFallback = { openBluetoothSettings() },
        systemUi = systemUi
    )
    if (rationale) AlertDialog(onDismissRequest = { rationale = false; permissionAction = null },
        title = { Text(trText("Nearby devices")) },
        text = { Text(trText("Connect to a paired device for one read-only review.")) },
        confirmButton = { TextButton(onClick = { rationale = false; systemUi.value = true; request.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text(trText("Continue")) } },
        dismissButton = { TextButton(onClick = { rationale = false; permissionAction = null }) { Text(trText("Cancel")) } })
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(trText("Pair a companion")) },
        text = { Text(trText("Install Dhalam on both devices. Pair them in Android Bluetooth settings. On the TV, open Dhalam device review and show a code. Enter it on this phone, then approve on the TV. Bluetooth Classic is required; some TVs do not support this connection. Headsets and controllers cannot share app inventories.")) },
        confirmButton = { TextButton(onClick = { help = false }) { Text(trText("Close")) } })
    if (consent) AlertDialog(onDismissRequest = { consent = false },
        title = { Text(trText("Summarize with Gemini?")) },
        text = { Text(trText("Send selected remote app names and permission states to Google Gemini? The pairing code and Bluetooth address stay on device.")) },
        confirmButton = { TextButton(onClick = {
            consent = false
            val inventory = state.inventory ?: return@TextButton
            reportBusy = true; reportError = null
            scope.launch {
                try {
                    val result = onReport(inventory)
                    if (latestInventory === inventory) report = result
                } catch (cancel: CancellationException) { throw cancel }
                  catch (_: java.io.IOException) { reportError = "Could not reach Gemini. The local report is still available." }
                  catch (error: Exception) { reportError = error.message ?: "Summary unavailable." }
                finally { reportBusy = false }
            }
        }) { Text(trText("Summarize")) } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text(trText("Not now")) } })
    Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(trText(if (companionOnly) "Share this device" else "Bluetooth device review"), style = MaterialTheme.typography.headlineSmall)
            Text(trText("Pair in Android Settings. Dhalam is required on both devices."), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { help = true }) { Text(trText("Setup")) }
                TextButton(onClick = { perform {
                    model.stopTransfers()
                    pairDevice()
                } }, enabled = !state.busy) { Text(trText("Pair a device")) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { openBluetoothSettings() }) { Text(trText("Bluetooth settings")) }
            }
            if (denied) {
                Text(trText("Nearby devices permission is needed."), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null))) }) { Text(trText("App settings")) }
            }
            if (companionOnly) {
                Text(trText("Pair a device, then show a code. Settings: press Back to return."), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { perform { model.startSharing() } }, enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 56.dp)) { Text(trText("Show pairing code")) }
                state.sharingCode?.let {
                    Text(it, style = MaterialTheme.typography.displayLarge, fontFamily = FontFamily.Monospace)
                }
                state.approvalPeer?.let { peer ->
                    Text(trText("Share app and permission names with ") + peer + "?", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { model.approve(false) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(trText("Decline")) }
                        Button(onClick = { model.approve(true) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(trText("Share once")) }
                    }
                }
            } else {
                Button(onClick = { perform { model.refreshPeers() } }, enabled = !state.busy) { Text(trText("Paired devices")) }
                state.peers.forEach { peer ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !state.busy) { model.select(peer.address) },
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.selectedAddress == peer.address,
                            onClick = { model.select(peer.address) }, enabled = !state.busy)
                        Text(peer.name + " · " + peer.address.takeLast(5), Modifier.weight(1f))
                    }
                }
                if (state.selectedAddress != null) {
                    OutlinedTextField(state.enteredCode, model::code, label = { Text(trText("Code on other device")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { perform { model.requestReview() } },
                        enabled = !state.busy && state.enteredCode.length == 6) { Text(trText("Read device")) }
                }
                TextButton(onClick = { context.startActivity(Intent(context, DeviceReviewCompanionActivity::class.java)) }) {
                    Text(trText("Share this device instead"))
                }
            }
            state.message?.let { Text(trText(it), style = MaterialTheme.typography.bodyMedium) }
            state.error?.let { Text(trText(it), color = MaterialTheme.colorScheme.error) }
            if (state.busy) TextButton(onClick = { model.stopTransfers(clearCode = true) }) { Text(trText("Cancel")) }
            state.inventory?.let { inventory ->
                RemoteInventoryView(inventory)
                if (reportBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                report?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                reportError?.let { Text(trText(it), color = MaterialTheme.colorScheme.error) }
                Button(onClick = { consent = true }, enabled = !reportBusy && !state.busy) {
                    Text(trText("Gemini brief"))
                }
            }
        }
    }
}

@Composable
internal fun RemoteInventoryView(inventory: RemoteDeviceInventory) {
    var count by rememberSaveable(inventory.observedAt) { mutableIntStateOf(12) }
    var selected by remember { mutableStateOf<RemoteApp?>(null) }
    selected?.let { app ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(app.label) },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    if (app.totalRequestedPermissions > app.permissions.size) Text("Showing ${app.permissions.size} of ${app.totalRequestedPermissions} permissions.")
                    if (app.permissions.isEmpty()) Text(trText("No requested permissions reported."))
                    app.permissions.forEach {
                        Text(it.name.substringAfterLast('.').replace('_', ' '), style = MaterialTheme.typography.labelLarge)
                        Text(trText(when (it.status) { "granted" -> "Granted"; "not_granted" -> "Not granted"; else -> "Unknown" }))
                    }
                }
            }, confirmButton = { TextButton(onClick = { selected = null }) { Text(trText("Close")) } })
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(inventory.deviceName, style = MaterialTheme.typography.titleLarge)
        Text("Android ${inventory.androidVersion} · ${inventory.apps.size}/${inventory.totalVisibleApps} visible apps",
            style = MaterialTheme.typography.bodySmall)
        Text(trText("Reported by the companion. Granted access does not mean use."), style = MaterialTheme.typography.bodySmall)
        inventory.apps.take(count).forEach { app ->
            OutlinedCard(onClick = { selected = app }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    Text("${app.permissions.count { it.status == "granted" }} granted · ${app.permissions.size}/${app.totalRequestedPermissions} permissions shown",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (count < inventory.apps.size) TextButton(onClick = { count += 12 }) { Text(trText("Show more apps")) }
    }
}
