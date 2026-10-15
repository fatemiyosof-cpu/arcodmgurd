package com.arguard.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0000"))
            setPadding(48, 120, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(TextView(this).apply {
            text = "AR Guard"
            setTextColor(Color.parseColor("#E53935"))
            textSize = 34f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "نسخه 1.0"
            setTextColor(Color.parseColor("#888888"))
            textSize = 13f
            setPadding(0, 8, 0, 40)
        })

        root.addView(TextView(this).apply {
            text = "این برنامه توسط چنل ARCODM ساخته شده.\n\n" +
                   "اگر می‌خواهید یک وایرگارد بدون پینگ مناسبِ بازی داشته باشید، " +
                   "به تلگرام ما بیایید:\n\n@ar8codm"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setLineSpacing(6f, 1f)
        })

        root.addView(TextView(this).apply {
            text = "https://t.me/ar8codm"
            setTextColor(Color.parseColor("#4FC3F7"))
            textSize = 16f
            setPadding(0, 40, 0, 0)
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/ar8codm")))
            }
        })

        setContentView(root)
    }
}