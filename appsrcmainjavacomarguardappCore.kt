package com.arguard.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader

// ---------- مدل‌ها ----------
data class Tunnel(val name: String, val config: String)

// ---------- Prefs ----------
object Prefs {
    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("arguard", Context.MODE_PRIVATE)

    var Context.ipv6DnsEnabled: Boolean
        get() = sp(this).getBoolean("ipv6_on", false)
        set(v) { sp(this).edit().putBoolean("ipv6_on", v).apply() }

    var Context.ipv6Dns: String
        get() = sp(this).getString("ipv6_dns", "2606:4700:4700::1111") ?: ""
        set(v) { sp(this).edit().putString("ipv6_dns", v).apply() }

    var Context.lagReduction: Boolean
        get() = sp(this).getBoolean("lag", false)
        set(v) { sp(this).edit().putBoolean("lag", v).apply() }

    var Context.specialOpt: Boolean
        get() = sp(this).getBoolean("opt", false)
        set(v) { sp(this).edit().putBoolean("opt", v).apply() }

    var Context.registryBoost: Boolean
        get() = sp(this).getBoolean("reg", false)
        set(v) { sp(this).edit().putBoolean("reg", v).apply() }
}

// ---------- ذخیره تانل‌ها ----------
object TunnelStore {
    private const val KEY = "tunnels"
    private fun sp(ctx: Context) = ctx.getSharedPreferences("arguard", Context.MODE_PRIVATE)

    fun load(ctx: Context): MutableList<Tunnel> {
        val raw = sp(ctx).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<Tunnel>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(Tunnel(o.optString("name"), o.optString("config")))
        }
        return list
    }

    fun save(ctx: Context, list: List<Tunnel>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("name", it.name); put("config", it.config)
            })
        }
        sp(ctx).edit().putString(KEY, arr.toString()).apply()
    }
}

// ---------- ابزار کانفیگ ----------
object ConfigUtils {

    /** اعمال تنظیمات روی متن خام کانفیگ WireGuard */
    fun applySettings(raw: String, ctx: Context): String {
        val lines = raw.lines()
        val out = mutableListOf<String>()
        var inInterface = false
        var inPeer = false

        val dnsOverride = if (ctx.ipv6DnsEnabled && ctx.ipv6Dns.isNotBlank()) ctx.ipv6Dns else null
        val mtuOverride = when {
            ctx.lagReduction -> "1280"
            ctx.registryBoost -> "1420"
            else -> null
        }
        val keepalive = if (ctx.specialOpt) "25" else null

        var dnsDone = false; var mtuDone = false; var kaDone = false

        for (line in lines) {
            val t = line.trim()
            when {
                t.equals("[Interface]", true) -> { inInterface = true; inPeer = false; out.add(line) }
                t.equals("[Peer]", true) -> {
                    if (inInterface) {
                        if (mtuOverride != null && !mtuDone) { out.add("MTU = $mtuOverride"); mtuDone = true }
                        if (dnsOverride != null && !dnsDone) { out.add("DNS = $dnsOverride"); dnsDone = true }
                    }
                    inInterface = false; inPeer = true; out.add(line)
                }
                inInterface && t.startsWith("DNS", true) -> {
                    if (dnsOverride != null && !dnsDone) { out.add("DNS = $dnsOverride"); dnsDone = true }
                    else out.add(line)
                }
                inInterface && t.startsWith("MTU", true) -> {
                    if (mtuOverride != null && !mtuDone) { out.add("MTU = $mtuOverride"); mtuDone = true }
                    else out.add(line)
                }
                inPeer && t.startsWith("PersistentKeepalive", true) -> {
                    if (keepalive != null && !kaDone) { out.add("PersistentKeepalive = $keepalive"); kaDone = true }
                    else out.add(line)
                }
                else -> out.add(line)
            }
        }
        return out.joinToString("\n")
    }

    /** استخراج هاست و پورت از Endpoint */
    fun endpointOf(raw: String): Pair<String, Int>? {
        val m = Regex("(?m)^\\s*Endpoint\\s*=\\s*([^:\\s]+):(\\d+)").find(raw) ?: return null
        return m.groupValues[1] to (m.groupValues[2].toIntOrNull() ?: return null)
    }
}

// ---------- تست پینگ ----------
object PingTester {
    /** برمی‌گرداند میلی‌ثانیه یا null اگر جواب نداد */
    fun ping(host: String, timeoutSec: Int = 2): Int? {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", "-W", timeoutSec.toString(), host))
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            var time: Int? = null
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val m = Regex("time=([0-9.]+)").find(line!!)
                if (m != null) { time = m.groupValues[1].toFloat().toInt(); break }
            }
            p.waitFor()
            time
        } catch (_: Exception) { null }
    }
}