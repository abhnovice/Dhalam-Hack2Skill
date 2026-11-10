package com.dhalam.ap

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.JSONObject

data class ReviewPeer(val address: String, val name: String)
data class BluetoothReviewState(
    val peers: List<ReviewPeer> = emptyList(),
    val selectedAddress: String? = null,
    val enteredCode: String = "",
    val sharingCode: String? = null,
    val approvalPeer: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val inventory: RemoteDeviceInventory? = null
)

// ready() checks CONNECT on every operation; revoked access is caught at the Bluetooth boundary.
@SuppressLint("MissingPermission")
class BluetoothReviewViewModel(application: Application) : AndroidViewModel(application) {
    var state by mutableStateOf(BluetoothReviewState())
        private set
    @Volatile private var epoch = 0
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var server: BluetoothServerSocket? = null
    private var task: Job? = null
    private var approval: CompletableDeferred<Boolean>? = null
    private val adapter get() = getApplication<Application>().getSystemService(BluetoothManager::class.java)?.adapter

    fun permitted(): Boolean = Build.VERSION.SDK_INT < 31 ||
        getApplication<Application>().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun ready(): Boolean {
        if (!permitted()) { state = state.copy(error = "Allow Nearby devices to connect."); return false }
        return try {
            when {
                adapter == null -> { state = state.copy(error = "Bluetooth is unavailable on this device."); false }
                adapter?.isEnabled != true -> { state = state.copy(error = "Turn on Bluetooth in Android Settings."); false }
                else -> true
            }
        } catch (_: SecurityException) { state = state.copy(error = "Bluetooth access was revoked."); false }
    }

    fun refreshPeers() {
        if (!ready()) return
        try {
            val peers = adapter!!.bondedDevices.map { ReviewPeer(it.address, (it.name ?: "Paired device").take(80)) }.sortedBy { it.name }
            state = state.copy(peers = peers, error = null,
                message = if (peers.isEmpty()) "Pair a device in Android Settings first." else null)
        } catch (_: SecurityException) { state = state.copy(error = "Bluetooth access was revoked.") }
    }

    fun select(address: String) { state = state.copy(selectedAddress = address, inventory = null, error = null) }
    fun code(value: String) { state = state.copy(enteredCode = value.filter(Char::isDigit).take(6)) }
    fun approve(allowed: Boolean) { approval?.complete(allowed) }

    fun pairingMessage(message: String) { state = state.copy(message = message, error = null) }

    @Synchronized fun stopTransfers(clearCode: Boolean = false) {
        epoch++
        runCatching { socket?.close() }; socket = null
        runCatching { server?.close() }; server = null
        approval?.complete(false); approval = null
        task?.cancel(); task = null
        state = state.copy(busy = false, sharingCode = null,
            enteredCode = if (clearCode) "" else state.enteredCode, approvalPeer = null)
    }

    private suspend fun <T> exchange(channel: BluetoothSocket, milliseconds: Long = 15_000, block: () -> T): T = coroutineScope {
        val deadline = launch { delay(milliseconds); runCatching { channel.close() } }
        try { withContext(Dispatchers.IO) { block() } } finally { deadline.cancel() }
    }

    @Synchronized private fun attach(connection: BluetoothSocket, token: Int) {
        if (epoch != token) { connection.close(); throw CancellationException() }
        socket = connection
    }

    @Synchronized private fun attachServer(listener: BluetoothServerSocket, token: Int) {
        if (epoch != token) { listener.close(); throw CancellationException() }
        server = listener
    }

    fun requestReview() {
        if (state.busy || !ready()) return
        val address = state.selectedAddress ?: return
        val code = state.enteredCode
        if (!code.matches(Regex("[0-9]{6}"))) { state = state.copy(error = "Enter the code shown on the other device."); return }
        stopTransfers()
        val token = epoch
        state = state.copy(busy = true, inventory = null, error = null, message = "Connecting…")
        task = viewModelScope.launch {
            var connection: BluetoothSocket? = null
            try {
                val peer = adapter!!.bondedDevices.firstOrNull { it.address == address } ?: error("Device is no longer paired.")
                val channel = withContext(Dispatchers.IO) {
                    peer.createRfcommSocketToServiceRecord(DeviceReviewProtocol.SERVICE_ID).also { attach(it, token) }
                }
                connection = channel
                exchange(channel, 20_000) { channel.connect() }
                val request = JSONObject().put("version", 1).put("operation", "READ_INVENTORY").put("code", code)
                exchange(channel) { DeviceReviewProtocol.write(channel.outputStream, request.toString().toByteArray()) }
                state = state.copy(message = "Approve sharing on the other device.")
                val response = exchange(channel, 75_000) {
                    JSONObject(DeviceReviewProtocol.read(channel.inputStream).toString(Charsets.UTF_8))
                }
                require(response.optInt("version") == 1) { "Unsupported companion version." }
                check(response.optBoolean("ok")) { "Sharing was denied, or the code expired. Start a new session." }
                val inventory = RemoteDeviceInventory.parse(response.getJSONObject("inventory"))
                if (epoch == token) state = state.copy(inventory = inventory, message = "Read-only snapshot received.", error = null)
            } catch (cancel: CancellationException) { throw cancel }
              catch (_: SecurityException) { if (epoch == token) state = state.copy(error = "Bluetooth access was revoked.", message = null) }
              catch (_: Exception) { if (epoch == token) state = state.copy(error = "Could not read this device. Open Dhalam's companion and verify its code.", message = null) }
            finally {
                runCatching { connection?.close() }
                if (epoch == token) { socket = null; state = state.copy(busy = false, enteredCode = "") }
            }
        }
    }

    fun startSharing() {
        if (state.busy || !ready()) return
        stopTransfers()
        val token = epoch
        val code = DeviceReviewProtocol.newCode()
        val issuedAt = SystemClock.elapsedRealtime()
        state = state.copy(busy = true, sharingCode = code, error = null, message = "Code expires in 2 minutes.")
        task = viewModelScope.launch {
            var listener: BluetoothServerSocket? = null
            var connection: BluetoothSocket? = null
            try {
                val channel = withContext(Dispatchers.IO) {
                    listener = adapter!!.listenUsingRfcommWithServiceRecord("Dhalam read-only review", DeviceReviewProtocol.SERVICE_ID)
                        .also { attachServer(it, token) }
                    listener!!.accept(DeviceReviewProtocol.OFFER_MS.toInt()).also { attach(it, token) }
                }
                connection = channel
                listener?.close()
                require(channel.remoteDevice.bondState == BluetoothDevice.BOND_BONDED)
                val request = exchange(channel) {
                    JSONObject(DeviceReviewProtocol.read(channel.inputStream).toString(Charsets.UTF_8))
                }
                val keys = request.keys().asSequence().toSet()
                require(keys == setOf("version", "operation", "code") &&
                    request.get("version") == 1 && request.get("operation") == "READ_INVENTORY" &&
                    DeviceReviewProtocol.validCode(code, request.getString("code"), issuedAt, SystemClock.elapsedRealtime()))
                val consent = CompletableDeferred<Boolean>()
                approval = consent
                state = state.copy(sharingCode = null, approvalPeer = (channel.remoteDevice.name ?: "Paired device").take(80),
                    message = "Approve one read-only transfer.")
                val allowed = withTimeout(45_000) { consent.await() }
                if (!allowed) {
                    exchange(channel) { DeviceReviewProtocol.write(channel.outputStream,
                        JSONObject().put("version", 1).put("ok", false).toString().toByteArray()) }
                    state = state.copy(message = "Sharing declined.")
                    return@launch
                }
                val inventory = withContext(Dispatchers.IO) { collectRemoteDeviceInventory(getApplication()) }
                val response = JSONObject().put("version", 1).put("ok", true).put("inventory", inventory.json())
                exchange(channel) { DeviceReviewProtocol.write(channel.outputStream, response.toString().toByteArray()) }
                if (epoch == token) state = state.copy(message = "Shared once. Connection closed.")
            } catch (_: TimeoutCancellationException) {
                if (epoch == token) state = state.copy(error = "Approval timed out. Show a new code and approve on the TV.", message = null)
            } catch (cancel: CancellationException) {
                if (epoch == token) state = state.copy(message = "Session ended. Generate a new code.")
                throw cancel
            } catch (error: Exception) {
                android.util.Log.w("DhalamBluetooth", "Sharing failed: ${error.javaClass.simpleName}")
                if (epoch == token) state = state.copy(error = when (error) {
                    is SecurityException -> "Nearby devices permission was revoked. Allow it and show a new code."
                    is IllegalArgumentException -> "Invalid code or report size. Show a new code and retry."
                    else -> "Bluetooth connection failed. Keep both apps open and show a new code."
                }, message = null)
            } finally {
                runCatching { connection?.close() }; runCatching { listener?.close() }
                if (epoch == token) {
                    socket = null; server = null; approval = null
                    state = state.copy(busy = false, sharingCode = null, approvalPeer = null)
                }
            }
        }
    }

    override fun onCleared() { stopTransfers(); super.onCleared() }
}
