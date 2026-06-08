package com.obdsound.emulator

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager as SystemBluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var tvStatus: TextView
    private lateinit var btnConnect: Button
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var myBluetoothManager: BluetoothManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            tvStatus.text = "Статус: Дозволи отримано. Натисни ПІДКЛЮЧИТИ."
        } else {
            tvStatus.text = "Статус: ПОМИЛКА. Немає прав."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnConnect = findViewById(R.id.btnConnect)

        val systemBtManager = getSystemService(SystemBluetoothManager::class.java)
        bluetoothAdapter = systemBtManager?.adapter
        myBluetoothManager = BluetoothManager(bluetoothAdapter)

        checkPermissions()

        btnConnect.setOnClickListener {
            connectToOBD()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToOBD() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            tvStatus.text = "Статус: Увімкніть Bluetooth на телефоні!"
            return
        }

        tvStatus.text = "Статус: Пошук OBD2 серед спарених..."

        // Шукаємо серед спарених пристроїв
        val pairedDevices = bluetoothAdapter!!.bondedDevices
        val obdDevice = pairedDevices.find { it.name?.contains("OBD", ignoreCase = true) == true }

        if (obdDevice == null) {
            tvStatus.text = "Статус: OBD2 не знайдено. Зробіть пару в налаштуваннях телефону."
            return
        }

        tvStatus.text = "Статус: Підключення до ${obdDevice.name}..."

        // Підключення по сокету блокує систему, тому запускаємо у фоні
        thread {
            val isConnected = myBluetoothManager.connectToDevice(obdDevice.address)
            runOnUiThread {
                if (isConnected) {
                    tvStatus.text = "Статус: ПІДКЛЮЧЕНО до ${obdDevice.name}!"
                    btnConnect.text = "З'ЄДНАНО"
                    btnConnect.isEnabled = false
                } else {
                    tvStatus.text = "Статус: Помилка підключення. Перевірте запалювання."
                }
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            val connectPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)

            if (scanPermission != PackageManager.PERMISSION_GRANTED || connectPermission != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(
                    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                )
            } else {
                tvStatus.text = "Статус: Bluetooth готовий. Натисни ПІДКЛЮЧИТИ."
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        myBluetoothManager.disconnect()
    }
}