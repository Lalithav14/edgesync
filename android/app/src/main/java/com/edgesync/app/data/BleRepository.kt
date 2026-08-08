package com.edgesync.app.data

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*
import kotlin.random.Random

/**
 * Handles talking to the EdgeSync ESP32 over BLE.
 *
 * IMPORTANT: until you've flashed + wired the ESP32, call startSimulated()
 * instead of startBleScan(). This lets you build and test the whole app +
 * cloud pipeline today, then flip to real BLE once the hardware is ready.
 * Both paths emit the exact same SensorReading type, so nothing else in
 * the app (ViewModel, UI, networking) needs to change when you switch.
 */
class BleRepository(private val context: Context) {

    companion object {
        private const val DEVICE_NAME = "EdgeSync-ESP32"
        private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    }

    // Small in-memory ring buffer of recent readings — bounded so it can't
    // grow unbounded if the app sits open a long time (backpressure story).
    private val recentReadings = ArrayDeque<SensorReading>(50)
    fun getRecentReadings(): List<SensorReading> = recentReadings.toList()

    private fun record(reading: SensorReading) {
        if (recentReadings.size >= 50) recentReadings.removeFirst()
        recentReadings.addLast(reading)
    }

    /** Simulated stream — no hardware required. Use this until BLE is wired up. */
    fun startSimulated(deviceId: String = "sim-device-1"): Flow<SensorReading> = callbackFlow {
        var base = 24.0
        val job = GlobalScope.launch {
            while (true) {
                base += Random.nextDouble(-1.0, 1.0)
                if (Random.nextInt(0, 40) == 0) base += 8.0 // occasional spike
                base = base.coerceIn(15.0, 45.0)
                val reading = SensorReading(deviceId, base, System.currentTimeMillis())
                record(reading)
                trySend(reading)
                delay(3000)
            }
        }
        awaitClose { job.cancel() }
    }

    /** Real BLE scan + connect + notify. Requires BLUETOOTH_SCAN/CONNECT permissions granted. */
    @SuppressLint("MissingPermission")
    fun startBleScan(deviceId: String = "esp32-1"): Flow<SensorReading> = callbackFlow {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        val scanner = adapter.bluetoothLeScanner

        var gatt: BluetoothGatt? = null

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val characteristic = g.getService(SERVICE_UUID)
                    ?.getCharacteristic(CHARACTERISTIC_UUID) ?: return
                g.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.descriptors.firstOrNull()
                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                descriptor?.let { g.writeDescriptor(it) }
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                val json = String(characteristic.value)
                parseReading(json, deviceId)?.let {
                    record(it)
                    trySend(it)
                }
            }
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.device.name == DEVICE_NAME) {
                    scanner.stopScan(this)
                    gatt = result.device.connectGatt(context, false, gattCallback)
                }
            }
        }

        scanner.startScan(scanCallback)
        awaitClose {
            scanner.stopScan(scanCallback)
            gatt?.close()
        }
    }

    private fun parseReading(json: String, deviceId: String): SensorReading? = try {
        val obj = JSONObject(json)
        SensorReading(deviceId, obj.getDouble("temp"), System.currentTimeMillis())
    } catch (e: Exception) {
        null
    }
}
