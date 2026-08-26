package com.example.localcall

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.net.wifi.WifiManager
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.*

class MainActivity : AppCompatActivity() {

    private lateinit var tvMyIp: TextView
    private lateinit var btnDiscover: Button
    private lateinit var btnAccept: Button
    private lateinit var lvDevices: ListView
    private lateinit var tvStatus: TextView

    private val BROADCAST_PORT = 50001
    private val VOICE_PORT = 50002
    private val deviceList = mutableListOf<DeviceInfo>()
    private lateinit var deviceAdapter: ArrayAdapter<String>

    private var voiceSocket: Socket? = null
    private var isCalling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvMyIp = findViewById(R.id.tvMyIp)
        btnDiscover = findViewById(R.id.btnDiscover)
        btnAccept = findViewById(R.id.btnAccept)
        lvDevices = findViewById(R.id.lvDevices)
        tvStatus = findViewById(R.id.tvStatus)

        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        lvDevices.adapter = deviceAdapter

        requestPermissions()
        tvMyIp.text = "My IP: ${getLocalIp()}"

        btnDiscover.setOnClickListener {
            deviceList.clear()
            deviceAdapter.clear()
            startDiscovery()        }

        btnAccept.setOnClickListener {
            startAccepting()
        }

        lvDevices.setOnItemClickListener { _, _, position, _ ->
            val device = deviceList[position]
            makeCall(device.ip)
        }

        val filter = IntentFilter("INCOMING_CALL")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(incomingCallReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(incomingCallReceiver, filter)
        }
    }

    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.POST_NOTIFICATIONS
        )
        ActivityCompat.requestPermissions(this, perms, 100)
    }

    private fun getLocalIp(): String {
        return try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            String.format("%d.%d.%d.%d", ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
        } catch (e: Exception) { "Unknown" }
    }

    private fun startDiscovery() {
        tvStatus.text = "Status: ရှာဖွေနေသည်..."
        Thread {
            try {
                val socket = DatagramSocket(BROADCAST_PORT)
                socket.broadcast = true
                val buf = ByteArray(1024)
                val packet = DatagramPacket(buf, buf.size)
                socket.soTimeout = 5000
                while (true) {
                    try {
                        socket.receive(packet)                        val data = String(packet.data, 0, packet.length)
                        val parts = data.split("|")
                        if (parts.size == 2) {
                            val name = parts[0]
                            val ip = parts[1]
                            val info = DeviceInfo(name, ip)
                            if (!deviceList.contains(info)) {
                                deviceList.add(info)
                                runOnUiThread {
                                    deviceAdapter.add("$name ($ip)")
                                    tvStatus.text = "Status: ${deviceList.size} ဖုန်း တွေ့ပြီ"
                                }
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        break
                    }
                }
                socket.close()
                runOnUiThread { tvStatus.text = "Status: ရှာဖွေမှု ပြီးဆုံးပါပြီ" }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun startAccepting() {
        tvStatus.text = "Status: ခေါ်ဆိုမှုများကို စောင့်ဆိုင်းနေသည်..."
        val intent = Intent(this, IncomingCallService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        Thread {
            try {
                val socket = DatagramSocket(BROADCAST_PORT)
                socket.broadcast = true
                val buf = ByteArray(1024)
                val packet = DatagramPacket(buf, buf.size)
                while (true) {
                    socket.receive(packet)
                    val data = String(packet.data, 0, packet.length)
                    val parts = data.split("|")
                    if (parts.size == 2) {
                        val callerName = parts[0]
                        val callerIp = parts[1]
                        runOnUiThread {
                            showIncomingCallDialog(callerName, callerIp)                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun makeCall(targetIp: String) {
        tvStatus.text = "Status: $targetIp သို့ ခေါ်ဆိုနေသည်..."
        Thread {
            try {
                val myName = Build.MODEL
                val myIp = getLocalIp()
                val msg = "$myName|$myIp"
                val socket = DatagramSocket()
                val packet = DatagramPacket(
                    msg.toByteArray(),
                    msg.length,
                    InetAddress.getByName(targetIp),
                    BROADCAST_PORT
                )
                socket.send(packet)
                socket.close()

                voiceSocket = Socket(InetAddress.getByName(targetIp), VOICE_PORT)
                isCalling = true
                runOnUiThread { tvStatus.text = "Status: ချိတ်ဆက်ပြီးပါပြီ - စကားပြောနေသည်" }
                startVoiceStream(voiceSocket!!)
            } catch (e: Exception) {
                runOnUiThread { tvStatus.text = "Status: ချိတ်ဆက်မှု မအောင်မြင်ပါ" }
            }
        }.start()
    }

    private val incomingCallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val callerName = intent?.getStringExtra("caller_name") ?: "Unknown"
            val callerIp = intent?.getStringExtra("caller_ip") ?: ""
            showIncomingCallDialog(callerName, callerIp)
        }
    }

    private fun showIncomingCallDialog(callerName: String, callerIp: String) {
        AlertDialog.Builder(this)
            .setTitle("📞 ဖုန်းဝင်လာပါတယ်")
            .setMessage("$callerName မှ ခေါ်ဆိုနေပါတယ်")
            .setCancelable(false)
            .setPositiveButton("လက်ခံမယ်") { _, _ ->                acceptCall(callerIp)
            }
            .setNegativeButton("ပယ်ချမယ်") { dialog, _ ->
                dialog.dismiss()
                tvStatus.text = "Status: ငြင်းပယ်လိုက်သည်"
            }
            .show()
    }

    private fun acceptCall(callerIp: String) {
        Thread {
            try {
                voiceSocket = Socket(InetAddress.getByName(callerIp), VOICE_PORT)
                isCalling = true
                runOnUiThread { tvStatus.text = "Status: ချိတ်ဆက်ပြီး - စကားပြောနေသည်" }
                startVoiceStream(voiceSocket!!)
            } catch (e: Exception) {
                runOnUiThread { tvStatus.text = "Status: ချိတ်ဆက်မှု မအောင်မြင်ပါ" }
            }
        }.start()
    }

    private fun startVoiceStream(socket: Socket) {
        val sampleRate = 44100
        val channelIn = android.media.AudioFormat.CHANNEL_IN_MONO
        val channelOut = android.media.AudioFormat.CHANNEL_OUT_MONO
        val format = android.media.AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelIn, format)

        val recorder = android.media.AudioRecord(
            android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate, channelIn, format, bufferSize
        )
        val player = android.media.AudioTrack(
            android.media.AudioManager.STREAM_VOICE_CALL,
            sampleRate, channelOut, format, bufferSize,
            android.media.AudioTrack.MODE_STREAM
        )

        recorder.startRecording()
        player.play()

        Thread {
            val buf = ByteArray(bufferSize)
            val out = DataOutputStream(socket.getOutputStream())
            while (isCalling) {
                val read = recorder.read(buf, 0, buf.size)
                if (read > 0) {
                    out.writeInt(read)
                    out.write(buf, 0, read)                }
            }
        }.start()

        Thread {
            val buf = ByteArray(bufferSize)
            val inp = DataInputStream(socket.getInputStream())
            while (isCalling) {
                try {
                    val len = inp.readInt()
                    inp.readFully(buf, 0, len)
                    player.write(buf, 0, len)
                } catch (e: Exception) {
                    isCalling = false
                    break
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        isCalling = false
        voiceSocket?.close()
        try { unregisterReceiver(incomingCallReceiver) } catch (_: Exception) {}
    }
}

data class DeviceInfo(val name: String, val ip: String)
