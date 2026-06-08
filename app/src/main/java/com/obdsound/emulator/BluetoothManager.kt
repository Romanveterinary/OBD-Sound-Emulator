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
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    var isConnected = false

    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String): Boolean {
        val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        
        return try {
            socket = device?.createRfcommSocketToServiceRecord(MY_UUID)
            socket?.connect()
            
            inputStream = socket?.inputStream
            outputStream = socket?.outputStream
            
            isConnected = true
            Log.d("BluetoothManager", "Підключено до: $deviceAddress")
            true
        } catch (e: IOException) {
            Log.e("BluetoothManager", "Помилка підключення: ${e.message}")
            disconnect()
            false
        }
    }

    fun sendCommand(command: String): String {
        val out = outputStream ?: return ""
        val `in` = inputStream ?: return ""
        
        return try {
            // Команди OBD2 обов'язково повинні закінчуватися символом повернення каретки \r
            out.write((command + "\r").toByteArray())
            out.flush()
            
            val buffer = ByteArray(1024)
            var bytes: Int
            val responseBuilder = StringBuilder()
            
            // ELM327 сигналізує про завершення відповіді символом ">"
            while (true) {
                bytes = `in`.read(buffer)
                val chunk = String(buffer, 0, bytes)
                responseBuilder.append(chunk)
                if (chunk.contains(">")) {
                    break
                }
            }
            responseBuilder.toString().trim()
        } catch (e: IOException) {
            Log.e("BluetoothManager", "Помилка передачі даних: ${e.message}")
            isConnected = false
            ""
        }
    }

    fun disconnect() {
        isConnected = false
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothManager", "Помилка закриття сокета: ${e.message}")
        }
    }
}