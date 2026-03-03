package com.sosh.remote

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import java.io.IOException
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private var decoderIp: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newCachedThreadPool()

    private val keyMap = mapOf(
        "POWER" to "116",  "MUTE"  to "113",
        "HOME"  to "125",  "GUIDE" to "362",
        "VOD"   to "393",  "UP"    to "103",
        "DOWN"  to "108",  "LEFT"  to "105",
        "RIGHT" to "106",  "OK"    to "28",
        "BACK"  to "158",  "MENU"  to "139",
        "VOL_UP"    to "115", "VOL_DOWN"  to "114",
        "CH_UP"     to "402", "CH_DOWN"   to "403",
        "PLAY_PAUSE" to "164", "STOP"     to "128",
        "REW"   to "168",  "FFW"   to "159",
        "REC"   to "167",  "PREV"  to "165",
        "NEXT"  to "163",  "RED"   to "398",
        "GREEN" to "399",  "YELLOW" to "400",
        "BLUE"  to "401",  "TEXT"  to "388",
        "0" to "512", "1" to "513", "2" to "514",
        "3" to "515", "4" to "516", "5" to "517",
        "6" to "518", "7" to "519", "8" to "520", "9" to "521"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupButtons()
    }

    private fun setupButtons() {
        findViewById<View>(R.id.btn_connect).setOnClickListener { connectManual() }
        findViewById<View>(R.id.btn_scan).setOnClickListener { startScan() }
        mapOf(
            R.id.btn_power to "POWER",    R.id.btn_mute    to "MUTE",
            R.id.btn_home  to "HOME",     R.id.btn_guide   to "GUIDE",
            R.id.btn_vod   to "VOD",      R.id.btn_up      to "UP",
            R.id.btn_down  to "DOWN",     R.id.btn_left    to "LEFT",
            R.id.btn_right to "RIGHT",    R.id.btn_ok      to "OK",
            R.id.btn_back  to "BACK",     R.id.btn_menu_btn to "MENU",
            R.id.btn_vol_up   to "VOL_UP",   R.id.btn_vol_down to "VOL_DOWN",
            R.id.btn_ch_up    to "CH_UP",    R.id.btn_ch_down  to "CH_DOWN",
            R.id.btn_prev  to "PREV",     R.id.btn_rew     to "REW",
            R.id.btn_play  to "PLAY_PAUSE", R.id.btn_ffw   to "FFW",
            R.id.btn_next  to "NEXT",     R.id.btn_stop    to "STOP",
            R.id.btn_rec   to "REC",      R.id.btn_red     to "RED",
            R.id.btn_green to "GREEN",    R.id.btn_yellow  to "YELLOW",
            R.id.btn_blue  to "BLUE",     R.id.btn_text    to "TEXT",
            R.id.btn_0 to "0", R.id.btn_1 to "1", R.id.btn_2 to "2",
            R.id.btn_3 to "3", R.id.btn_4 to "4", R.id.btn_5 to "5",
            R.id.btn_6 to "6", R.id.btn_7 to "7", R.id.btn_8 to "8",
            R.id.btn_9 to "9"
        ).forEach { (id, cmd) ->
            findViewById<View>(id)?.setOnClickListener { sendCommand(cmd) }
        }
    }

    private fun connectManual() {
        val ip = findViewById<EditText>(R.id.ip_input).text.toString().trim()
        if (ip.isEmpty()) { toast("Entrez une adresse IP"); return }
        connectToIp(ip)
    }

    private fun connectToIp(ip: String) {
        setStatus("connecting", "Connexion en cours…")
        client.newCall(
            Request.Builder().url("http://$ip:8080/remoteControl/cmd?operation=10").build()
        ).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                decoderIp = ip
                response.close()
                mainHandler.post {
                    setStatus("connected", "✅ Connecté — $ip")
                    toast("Décodeur connecté !")
                }
            }
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    setStatus("error", "❌ Impossible de joindre $ip")
                    toast("Connexion échouée")
                }
            }
        })
    }

    private fun sendCommand(cmd: String) {
        val ip = decoderIp ?: run { toast("Connectez-vous d'abord"); return }
        val key = keyMap[cmd] ?: return
        val url = "http://$ip:8080/remoteControl/cmd?operation=01&key=$key&mode=0"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { toast("❌ ${e.message}") }
            }
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    private fun startScan() {
        val scanPanel   = findViewById<ScrollView>(R.id.scan_panel)
        val scanResults = findViewById<LinearLayout>(R.id.scan_results)
        val scanStatus  = findViewById<TextView>(R.id.scan_status)
        val scanProg    = findViewById<ProgressBar>(R.id.scan_progress)
        val scanProgTxt = findViewById<TextView>(R.id.scan_progress_text)

        scanPanel.visibility = View.VISIBLE
        scanResults.removeAllViews()
        scanProg.progress = 0

        executor.execute {
            val subnet = detectSubnet()
            mainHandler.post { scanStatus.text = "🔍 Scan de $subnet.1–254…" }

            val found = mutableListOf<String>()
            var done = 0
            val pool = Executors.newFixedThreadPool(50)

            (1..254).map { host ->
                pool.submit {
                    val ip = "$subnet.$host"
                    try {
                        val fastClient = OkHttpClient.Builder()
                            .connectTimeout(800, TimeUnit.MILLISECONDS)
                            .readTimeout(800, TimeUnit.MILLISECONDS)
                            .build()
                        val resp = fastClient.newCall(
                            Request.Builder()
                                .url("http://$ip:8080/remoteControl/cmd?operation=10")
                                .build()
                        ).execute()
                        if (resp.isSuccessful) {
                            synchronized(found) { found.add(ip) }
                            mainHandler.post { addFoundDevice(ip, scanResults) }
                        }
                        resp.close()
                    } catch (e: Exception) { }
                    val d = synchronized(this) { ++done }
                    mainHandler.post {
                        scanProg.progress = d
                        scanProgTxt.text = "$d/254"
                    }
                }
            }.forEach { it.get() }
            pool.shutdown()

            mainHandler.post {
                scanStatus.text = if (found.isEmpty())
                    "😕 Aucun décodeur trouvé"
                else "✅ ${found.size} décodeur(s) trouvé(s) !"
            }
        }
    }

    private fun addFoundDevice(ip: String, container: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 14, 16, 14)
            setBackgroundColor(0xFF1C1C28.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 4, 0, 4) }
        }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(TextView(this).apply {
            text = "📺 Décodeur Sosh"
            setTextColor(0xFFE8531A.toInt())
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        info.addView(TextView(this).apply {
            text = ip
            setTextColor(0xFF888899.toInt())
            textSize = 12f
        })
        row.addView(info)
        row.addView(Button(this).apply {
            text = "Utiliser"
            setBackgroundColor(0xFFE8531A.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                findViewById<EditText>(R.id.ip_input).setText(ip)
                connectToIp(ip)
            }
        })
        container.addView(row)
    }

    private fun detectSubnet(): String {
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                if (ni.isLoopback || !ni.isUp) return@forEach
                ni.interfaceAddresses.forEach { ia ->
                    val ip = ia.address.hostAddress ?: return@forEach
                    if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
                        val p = ip.split(".")
                        if (p.size == 4) return "${p[0]}.${p[1]}.${p[2]}"
                    }
                }
            }
        } catch (e: Exception) { }
        return "192.168.1"
    }

    private fun setStatus(state: String, msg: String) {
        findViewById<TextView>(R.id.status_text).text = msg
        findViewById<View>(R.id.status_dot).setBackgroundResource(
            when (state) {
                "connected" -> R.drawable.dot_green
                "error"     -> R.drawable.dot_red
                else        -> R.drawable.dot_orange
            }
        )
    }

    private fun toast(msg: String) =
        mainHandler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }
}
