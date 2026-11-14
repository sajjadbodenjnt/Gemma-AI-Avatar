package com.aibondhu.mini

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeGenerate(prompt: String): String
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var chat: TextView
    private lateinit var input: EditText
    private var modelReady = false

    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importAndLoad(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 20)
            setBackgroundColor(0xFF07111F.toInt())
        }
        fun text(value: String, size: Float, color: Int) = TextView(this).apply {
            this.text = value; textSize = size; setTextColor(color); setPadding(0, 10, 0, 10)
        }
        root.addView(text("AIBondhu", 28f, 0xFFF4F8FF.toInt()))
        root.addView(text("বাংলা অফলাইন AI সহকারী · Qwen 0.5B + Bengali LoRA", 14f, 0xFF55D6FF.toInt()))
        val pick = Button(this).apply { text = "GGUF মডেল নির্বাচন করুন" }
        root.addView(pick)
        status = text("মডেল লোড হয়নি। প্রথমে aibondhu-q4_k_m.gguf ফাইল নির্বাচন করুন।", 13f, 0xFF55D6FF.toInt())
        root.addView(status)
        chat = text("AIBondhu: স্বাগতম! মডেল লোড হলে অফলাইনে কথা বলতে পারবেন।", 16f, 0xFFF4F8FF.toInt())
        root.addView(ScrollView(this).apply { addView(chat) }, LinearLayout.LayoutParams(-1, 0, 1f))
        input = EditText(this).apply {
            hint = "বাংলায় প্রশ্ন লিখুন"; setTextColor(-1); setHintTextColor(0xFF9DB0C6.toInt())
        }
        root.addView(input)
        val send = Button(this).apply { text = "প্রশ্ন পাঠান" }
        root.addView(send)
        pick.setOnClickListener { modelPicker.launch(arrayOf("*/*")) }
        send.setOnClickListener {
            val q = input.text.toString().trim()
            if (q.isNotEmpty() && modelReady) {
                input.text.clear()
                chat.text = "আপনি: $q\n\nAIBondhu ভাবছে…"
                worker.execute {
                    val answer = try { nativeGenerate(q) } catch (e: Exception) { "ত্রুটি: ${e.message}" }
                    runOnUiThread { chat.text = "আপনি: $q\n\nAIBondhu: $answer" }
                }
            } else if (!modelReady) status.text = "আগে GGUF মডেল লোড করুন।"
        }
        setContentView(root)
    }

    private fun importAndLoad(uri: Uri) {
        status.text = "মডেল কপি ও লোড হচ্ছে…"
        worker.execute {
            try {
                val target = File(filesDir, "aibondhu-q4_k_m.gguf")
                contentResolver.openInputStream(uri).use { source ->
                    requireNotNull(source) { "ফাইল পড়া যায়নি" }.copyTo(target.outputStream())
                }
                val ok = nativeLoadModel(target.absolutePath)
                runOnUiThread {
                    modelReady = ok
                    status.text = if (ok) "অফলাইন মডেল প্রস্তুত ✓" else "মডেল লোড ব্যর্থ। সঠিক GGUF ফাইল দিন।"
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "মডেল লোড ত্রুটি: ${e.message}" }
            }
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        init { System.loadLibrary("aibondhu-jni") }
    }
}
