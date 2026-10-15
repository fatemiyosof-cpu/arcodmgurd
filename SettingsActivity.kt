package com.arguard.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private val sp by lazy { getSharedPreferences("arguard", Context.MODE_PRIVATE) }

    private fun getB(k: String, d: Boolean = false) = sp.getBoolean(k, d)
    private fun setB(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    private fun getS(k: String, d: String = "") = sp.getString(k, d) ?: d
    private fun setS(k: String, v: String) = sp.edit().putString(k, v).apply()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0000"))
            setPadding(48, 80, 48, 48)
        }

        fun header(t: String) = TextView(this).apply {
            text = t; setTextColor(Color.parseColor("#E53935"))
            textSize = 26f; setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 30)
        }
        fun section(t: String) = TextView(this).apply {
            text = t; setTextColor(Color.parseColor("#888888"))
            textSize = 12f; setPadding(0, 24, 0, 8)
        }
        fun row(label: String, initial: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 16)
            }
            r.addView(TextView(this).apply {
                text = label; setTextColor(Color.WHITE); textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            r.addView(Switch(this).apply {
                isChecked = initial
                setOnCheckedChangeListener { _, b -> onChange(b) }
            })
            return r
        }

        root.addView(header("تنظیمات"))
        root.addView(section("بهینه‌سازی شبکه"))
        root.addView(row("ریجستری بیشتر (MTU = 1420)", getB("reg")) { setB("reg", it) })
        root.addView(row("کاهش لگ (MTU = 1280)", getB("lag")) { setB("lag", it) })
        root.addView(row("بهینه‌سازی مخصوص (Keepalive 25)", getB("opt")) { setB("opt", it) })

        root.addView(section("DNS سفارشی IPv6"))
        root.addView(row("فعال‌سازی DNS IPv6 روی تانل", getB("ipv6_on")) { setB("ipv6_on", it) })

        val dnsInput = EditText(this).apply {
            setText(getS("ipv6_dns", "2606:4700:4700::1111"))
            setTextColor(Color.WHITE)
            setHint("مثال: 2606:4700:4700::1111")
            setHintTextColor(Color.parseColor("#666666"))
            inputType = InputType.TYPE_CLASS_TEXT
            setBackgroundColor(Color.parseColor("#1A0505"))
            setPadding(24, 24, 24, 24)
        }
        root.addView(dnsInput)

        root.addView(TextView(this).apply {
            text = "این DNS در بلوک [Interface] روی همه کانفیگ‌ها اعمال می‌شود."
            setTextColor(Color.parseColor("#666666")); textSize = 11f
            setPadding(0, 8, 0, 24)
        })

        root.addView(Button(this).apply {
            text = "ذخیره"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#B71C1C"))
            setOnClickListener {
                setS("ipv6_dns", dnsInput.text.toString().trim())
                Toast.makeText(this@SettingsActivity, "ذخیره شد", Toast.LENGTH_SHORT).show()
                finish()
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }
}