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
    private lateinit var tvRpm: TextView
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
        tvRpm = findViewById(R.id.tvRpm)
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
            tvStatus.text = "Статус: Увімкніть Bluetooth!"
            return
        }

        tvStatus.text = "Статус: Пошук OBD2..."

        val pairedDevices = bluetoothAdapter!!.bondedDevices
        val obdDevice = pairedDevices.find { it.name?.contains("OBD", ignoreCase = true) == true }

        if (obdDevice == null) {
            tvStatus.text = "Статус: OBD2 не знайдено в спарених."
            return
        }

        tvStatus.text = "Статус: Підключення до ${obdDevice.name}..."

        thread {
            val isConnected = myBluetoothManager.connectToDevice(obdDevice.address)
            runOnUiThread {
                if (isConnected) {
                    tvStatus.text = "Статус: ПІДКЛЮЧЕНО. Отримання даних..."
                    btnConnect.text = "З'ЄДНАНО"
                    btnConnect.isEnabled = false
                    
                    // Запуск циклу опитування датчиків
                    startDataPolling()
                } else {
                    tvStatus.text = "Статус: Помилка з'єднання сокета."
                }
            }
        }
    }

    private fun startDataPolling() {
        thread {
            // 1. Скидання та базове налаштування ELM327
            myBluetoothManager.sendCommand("ATZ")
            Thread.sleep(500)
            myBluetoothManager.sendCommand("ATE0") // Вимкнути відлуння команд
            Thread.sleep(200)

            // 2. Цикл безперервного запиту RPM
            while (myBluetoothManager.isConnected) {
                val response = myBluetoothManager.sendCommand("01 0C")
                val rpm = parseRpmResponse(response)

                runOnUiThread {
                    if (rpm >= 0) {
                        tvRpm.text = rpm.toString()
                    }
                }
                Thread.sleep(150) // Пауза між запитами, щоб не перевантажувати шину шину K-Line/CAN
            }

            runOnUiThread {
                tvStatus.text = "Статус: Зв'язок розірвано."
                btnConnect.text = "ПІДКЛЮЧИТИ OBD2"
                btnConnect.isEnabled = true
            }
        }
    }

    private fun parseRpmResponse(response: String): Int {
        // Очищаємо відповідь від сміття, пробілів та переносів рядків
        val clean = response.replace(" ", "").replace("\r", "").replace("\n", "")
        
        // Успішна відповідь на PID 01 0C повинна містити маркер "410C"
        val index = clean.indexOf("410C")
        if (index != -1 && clean.length >= index + 8) {
            try {
                // Вирізаємо наступні 4 символи (це два Hex-байти даних: наприклад, 0B20)
                val hexValue = clean.substring(index + 4, index + 8)
                val decimal = hexValue.toInt(16)
                return decimal / 4
            } catch (e: Exception) {
                // Помилка конвертації невалідної шістнадцяткової стрічки
            }
        }
        return -1
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