package com.obdsound.emulator

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothManager(private val bluetoothAdapter: BluetoothAdapter?) {

    private var socket: BluetoothSocket? = null
    var inputStream: InputStream? = null
    var outputStream: OutputStream? = null

    // Стандартний UUID для профілю SPP (Serial Port Profile)
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String): Boolean {
        val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        
        return try {
            socket = device?.createRfcommSocketToServiceRecord(MY_UUID)
            socket?.connect()
            
            inputStream = socket?.inputStream
            outputStream = socket?.outputStream
            
            Log.d("BluetoothManager", "Підключено до: $deviceAddress")
            true
        } catch (e: IOException) {
            Log.e("BluetoothManager", "Помилка підключення: ${e.message}")
            disconnect()
            false
        }
    }

    fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothManager", "Помилка закриття сокета: ${e.message}")
        }
    }
}