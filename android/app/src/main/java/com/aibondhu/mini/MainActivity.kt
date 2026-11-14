package com.aibondhu.mini

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 24)
            setBackgroundColor(0xFF07111F.toInt())
        }
        fun label(text: String, size: Float, color: Int) = TextView(this).apply {
            this.text=text; textSize=size; setTextColor(color); setPadding(0,12,0,12)
        }
        root.addView(label("AIBondhu Mini",26f,0xFFF4F8FF.toInt()))
        root.addView(label("বাংলা অফলাইন AI + 3D মানব সহকারী",15f,0xFF55D6FF.toInt()))
        val avatar = TextView(this).apply {
            text="3D HUMAN AVATAR\n\nএখানে Rigged GLB/GLTF মানব মডেল যুক্ত হবে।\nএটি এখনো 3D renderer নয়।"
            textSize=18f; gravity=android.view.Gravity.CENTER
            setTextColor(0xFFF4F8FF.toInt()); setBackgroundColor(0xFF10233A.toInt())
            setPadding(20,48,20,48)
        }
        root.addView(avatar, LinearLayout.LayoutParams(-1,0,1f))
        val status=label("মডেল স্ট্যাটাস: GGUF + llama.cpp adapter যুক্ত করা বাকি",13f,0xFF55D6FF.toInt())
        root.addView(status)
        val transcript=label("AIBondhu: প্রথমে প্রশিক্ষণ workflow চালিয়ে LoRA adapter তৈরি করুন।",15f,0xFFF4F8FF.toInt())
        root.addView(transcript)
        val input=EditText(this).apply { hint="বাংলায় লিখুন"; setTextColor(-1); setHintTextColor(0xFF9DB0C6.toInt()) }
        root.addView(input)
        val send=Button(this).apply { text="পাঠান" }
        root.addView(send)
        send.setOnClickListener {
            val q=input.text.toString().trim()
            if(q.isNotEmpty()) {
                transcript.text="আপনি: $q\n\nAIBondhu: এখনো স্থানীয় GGUF inference যুক্ত হয়নি। এই starter কোনো ভুয়া AI উত্তর দেয় না।"
                input.text.clear()
            }
        }
        setContentView(root)
    }
}
