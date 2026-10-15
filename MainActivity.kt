package com.arguard.app

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.StringReader

class MainActivity : AppCompatActivity() {

    private lateinit var backend: Backend
    private lateinit var listContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var connectBtn: Button

    private val tunnels = mutableListOf<Tunnel>()
    private var selectedIndex = -1
    private var activeTunnel: Tunnel? = null

    private var pendingConfig: String? = null

    private val vpnPermLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            pendingConfig?.let { doConnect(it) }
        } else {
            toast("دسترسی VPN رد شد")
        }
        pendingConfig = null
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            addTunnelFromText(text, "Imported")
        } catch (e: Exception) { toast("خطا: ${e.message}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backend = GoBackend(this)
        tunnels.addAll(TunnelStore.load(this))
        buildUI()
        refreshList()
    }

    // ------- UI -------
    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0000"))
            setPadding(36, 60, 36, 36)
        }

        val title = TextView(this).apply {
            text = "AR Guard"
            setTextColor(Color.parseColor("#E53935"))
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        root.addView(title)

        val sub = TextView(this).apply {
            text = "Secure WireGuard Client"
            setTextColor(Color.parseColor("#888888"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 24)
        }
        root.addView(sub)

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(smallBtn("📁 فایل") { filePicker.launch("*/*") }, weight())
        btnRow.addView(space(12))
        btnRow.addView(smallBtn("📋 پیست") { pasteFromClipboard() }, weight())
        root.addView(btnRow)

        val btnRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 24)
        }
        btnRow2.addView(smallBtn("⚙️ تنظیمات") {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }, weight())
        btnRow2.addView(space(12))
        btnRow2.addView(smallBtn("ℹ️ درباره ما") {
            startActivity(Intent(this@MainActivity, AboutActivity::class.java))
        }, weight())
        root.addView(btnRow2)

        statusText = TextView(this).apply {
            text = "آماده"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 12)
        }
        root.addView(statusText)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(listContainer)
        root.addView(scroll)

        connectBtn = Button(this).apply {
            text = "اتصال"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#B71C1C"))
            textSize = 18f
            setPadding(0, 28, 0, 28)
            setOnClickListener { onConnectClicked() }
        }
        root.addView(connectBtn)

        setContentView(root)
    }

    private fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    private fun space(w: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(w, 1)
    }
    private fun smallBtn(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#2A0A0A"))
        textSize = 14f
        setOnClickListener { onClick() }
    }

    private fun refreshList() {
        listContainer.removeAllViews()
        if (tunnels.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "هنوز کانفیگی اضافه نشده.\nاز دکمه‌های بالا استفاده کن."
                setTextColor(Color.parseColor("#666666"))
                gravity = Gravity.CENTER
                setPadding(0, 60, 0, 0)
            })
            return
        }
        tunnels.forEachIndexed { i, t ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(28, 20, 28, 20)
                setBackgroundColor(
                    if (i == selectedIndex) Color.parseColor("#3A0A0A")
                    else Color.parseColor("#1A0505")
                )
                setOnClickListener { selectedIndex = i; refreshList() }
            }
            val nameTv = TextView(this).apply {
                text = t.name
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            }
            val infoTv = TextView(this).apply {
                val ep = ConfigUtils.endpointOf(t.config)
                text = ep?.let { "→ ${it.first}:${it.second}" } ?: "بدون Endpoint"
                setTextColor(Color.parseColor("#999999"))
                textSize = 12f
            }
            val pingTv = TextView(this).apply {
                text = "پینگ: در حال تست..."
                setTextColor(Color.parseColor("#FFAA00"))
                textSize = 12f
            }
            card.addView(nameTv); card.addView(infoTv); card.addView(pingTv)

            val del = TextView(this).apply {
                text = "حذف"
                setTextColor(Color.parseColor("#FF5252"))
                textSize = 12f
                setPadding(0, 8, 0, 0)
                setOnClickListener {
                    tunnels.removeAt(i)
                    if (selectedIndex == i) selectedIndex = -1
                    TunnelStore.save(this@MainActivity, tunnels)
                    refreshList()
                }
            }
            card.addView(del)

            listContainer.addView(card)
            listContainer.addView(space(16))

            // تست پینگ در پس‌زمینه
            val ep = ConfigUtils.endpointOf(t.config)
            if (ep != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    val ms = PingTester.ping(ep.first)
                    withContext(Dispatchers.Main) {
                        if (ms != null) {
                            pingTv.text = "پینگ: ${ms}ms ✅ (برای بازی مناسب)"
                            pingTv.setTextColor(Color.parseColor("#4CAF50"))
                        } else {
                            pingTv.text = "پینگ: بدون پاسخ ❌ (برای بازی مناسب نیست)"
                            pingTv.setTextColor(Color.parseColor("#E53935"))
                        }
                    }
                }
            }
        }
    }

    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) { toast("کلیپ‌بورد خالیه"); return }
        if (!text.contains("[Interface]", true) || !text.contains("[Peer]", true)) {
            toast("متن معتبر WireGuard نیست"); return
        }
        addTunnelFromText(text, "Pasted Config")
    }

    private fun addTunnelFromText(text: String, name: String) {
        val n = if (tunnels.any { it.name == name }) "$name ${tunnels.size + 1}" else name
        tunnels.add(Tunnel(n, text.trim()))
        TunnelStore.save(this, tunnels)
        refreshList()
        toast("اضافه شد: $n")
    }

    private fun onConnectClicked() {
        if (activeTunnel != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    backend.setState(activeTunnel!!, Tunnel.State.DOWN, null)
                    withContext(Dispatchers.Main) {
                        activeTunnel = null
                        connectBtn.text = "اتصال"
                        statusText.text = "قطع شد"
                    }
                } catch (e: Exception) { withContext(Dispatchers.Main) { toast("خطا: ${e.message}") } }
            }
            return
        }
        if (selectedIndex < 0 || selectedIndex >= tunnels.size) { toast("اول یک کانفیگ انتخاب کن"); return }
        val cfg = ConfigUtils.applySettings(tunnels[selectedIndex].config, this)
        val prep = VpnService.prepare(this)
        if (prep != null) { pendingConfig = cfg; vpnPermLauncher.launch(prep) }
        else doConnect(cfg)
    }

    private fun doConnect(cfg: String) {
        statusText.text = "در حال اتصال..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val parsed = Config.parse(BufferedReader(StringReader(cfg)))
                val t = object : Tunnel {
                    override fun getName() = "arguard"
                    override fun onStateChange(newState: Tunnel.State) {}
                }
                backend.setState(t, Tunnel.State.UP, parsed)
                withContext(Dispatchers.Main) {
                    activeTunnel = t
                    connectBtn.text = "قطع اتصال"
                    statusText.text = "متصل ✅"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "خطا در اتصال"
                    toast("خطا: ${e.message}")
                }
            }
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}