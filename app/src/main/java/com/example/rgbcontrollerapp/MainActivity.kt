package com.example.rgbcontrollerapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope                   // **added**
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.util.*

class MainActivity : ComponentActivity() {

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var pairedDevices: Set<BluetoothDevice>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Runtime permission launcher unchanged
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

        getPairedDevices()

        setContent {
            RGBControllerUI(
                onSend    = { r, g, b -> sendRGBToBluetooth(r, g, b) },
                onConnect = { name      -> connectToBluetoothDevice(name) }
            )
        }
    }

    private fun getPairedDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED
        ) {
            pairedDevices = bluetoothAdapter?.bondedDevices
        } else {
            Toast.makeText(this, "Bluetooth permission not granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToBluetoothDevice(deviceName: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Bluetooth permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        // **use lifecycleScope rather than GlobalScope**
        lifecycleScope.launch(Dispatchers.IO) {
            val device = pairedDevices?.find { it.name == deviceName }
            if (device == null) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Device not found", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            try {
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                // **insecure socket often works more reliably with HC-06**
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothAdapter?.cancelDiscovery()
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Connected to $deviceName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun sendRGBToBluetooth(r: Int, g: Int, b: Int) {
        val command = "R${r}G${g}B${b}\n"
        // **use lifecycleScope here too**
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                outputStream?.write(command.toByteArray())
                outputStream?.flush()       // **ensure it's sent immediately**
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
@Composable
fun RGBControllerUI(onSend: (Int, Int, Int) -> Unit, onConnect: (String) -> Unit) {
    var selectedColor by remember { mutableStateOf(Color(0xFF000000)) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var selectedDeviceName by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White,
                            0.25f to Color.White,
                            0.8f to selectedColor,
                            1.0f to selectedColor
                        )
                    )
                )
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("RGB LED Controller", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { showDeviceDialog = true }) {
                Text("Select Bluetooth Device")
            }

            // Show paired devices list in a dialog
            if (showDeviceDialog) {
                // Fetch real paired devices from the BluetoothAdapter
                val pairedDevices = remember {
                    BluetoothAdapter.getDefaultAdapter()
                        ?.bondedDevices
                        ?.map { it.name ?: it.address }
                        ?: emptyList()
                }

                PairedDeviceDialog(
                    devices = pairedDevices,
                    onSelect = { deviceName ->
                        onConnect(deviceName)
                        selectedDeviceName = deviceName
                        showDeviceDialog = false
                    },
                    onDismiss = { showDeviceDialog = false }
                )
            }


            Spacer(modifier = Modifier.height(16.dp))

            HsvColorPickerCircle(selectedColor) { color ->
                selectedColor = color

                // Send RGB values to Bluetooth whenever the color changes
                val r = (color.red * 255).toInt()
                val g = (color.green * 255).toInt()
                val b = (color.blue * 255).toInt()
                onSend(r, g, b)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun PairedDeviceDialog(devices: List<String>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a Bluetooth Device") },
        text = {
            Column {
                devices.forEach { device ->
                    TextButton(onClick = { onSelect(device) }) {
                        Text(device)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun HsvColorPickerCircle(
    selectedColor: Color,
    onColorChanged: (Color) -> Unit
) {
    val controller = rememberColorPickerController()

    HsvColorPicker(
        modifier = Modifier
            .size(300.dp)
            .padding(16.dp),
        controller = controller,
        initialColor = selectedColor,
        onColorChanged = { colorEnvelope ->
            val color = colorEnvelope.color
            onColorChanged(color)
        }
    )
}
