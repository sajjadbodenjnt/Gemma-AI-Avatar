package com.aibondhu.mini

import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream
import android.provider.OpenableColumns
import java.io.RandomAccessFile

class MainActivity : AppCompatActivity() {
    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeGenerate(prompt: String): String
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var chat: TextView
    private lateinit var input: EditText
    private lateinit var avatar: WebView
    private var modelReady = false
    private var avatarReady = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    @Volatile private var speakingText = ""

    private val speechController = AvatarSpeechController { viseme, weight ->
        runOnUiThread {
            if (avatarReady) avatar.evaluateJavascript(
                "window.aibondhuSetViseme && window.aibondhuSetViseme('$viseme',$weight)", null
            )
        }
    }

    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importAndLoad(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 14)
            setBackgroundColor(0xFF07111F.toInt())
        }
        fun label(value: String, size: Float, color: Int) = TextView(this).apply {
            text = value; textSize = size; setTextColor(color); setPadding(0, 7, 0, 7)
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val logo = ImageView(this).apply { setImageResource(R.drawable.aibondhu_logo); contentDescription = "AIBondhu logo"; scaleType = ImageView.ScaleType.FIT_CENTER }
        brand.addView(logo, LinearLayout.LayoutParams(dp(52), dp(52)))
        val brandText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brandText.addView(label("AIBondhu", 27f, 0xFFF4F8FF.toInt()))
        brandText.addView(label("বাংলা অফলাইন AI · Qwen Bengali LoRA", 13f, 0xFF55D6FF.toInt()))
        brand.addView(brandText)
        root.addView(brand)
        avatar = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val uri = request.url
                    if (uri.scheme == "https" && uri.host == "appassets.androidplatform.net" &&
                        uri.path?.startsWith("/assets/") == true) {
                        val assetPath = uri.path!!.removePrefix("/assets/")
                        val mime = when {
                            assetPath.endsWith(".html", true) -> "text/html"
                            assetPath.endsWith(".js", true) -> "application/javascript"
                            assetPath.endsWith(".glb", true) -> "model/gltf-binary"
                            else -> "application/octet-stream"
                        }
                        return try { WebResourceResponse(mime, if (mime.startsWith("text/") || mime.contains("javascript")) "UTF-8" else null, assets.open(assetPath)) }
                        catch (_: Exception) { WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), "Missing asset".byteInputStream()) }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
                override fun onPageFinished(view: WebView, url: String) {
                    avatarReady = true
                    speechController.neutral()
                }
            }
        }
        root.addView(avatar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330)))
        status = label("অফলাইন মডেল লোড হচ্ছে…", 12f, 0xFF55D6FF.toInt())
        root.addView(status)
        chat = label("AIBondhu: স্বাগতম! মডেল প্রস্তুত হলে বাংলায় কথা বলতে পারবেন।", 15f, 0xFFF4F8FF.toInt())
        root.addView(ScrollView(this).apply { addView(chat) }, LinearLayout.LayoutParams(-1, 0, 1f))
        input = EditText(this).apply { hint = "বাংলায় প্রশ্ন লিখুন"; setTextColor(-1); setHintTextColor(0xFF9DB0C6.toInt()) }
        root.addView(input)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val send = Button(this).apply { text = "পাঠান" }
        val pick = Button(this).apply { text = "মডেল নির্বাচন" }
        controls.addView(send, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(pick, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(controls)
        setContentView(root)
        avatar.loadUrl("https://appassets.androidplatform.net/assets/avatar.html")
        initializeBengaliVoice()
        send.setOnClickListener { sendMessage() }
        pick.setOnClickListener { modelPicker.launch(arrayOf("*/*")) }
        loadBundledModel()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun initializeBengaliVoice() {
        tts = TextToSpeech(this) { result ->
            if (result != TextToSpeech.SUCCESS) {
                runOnUiThread { status.text = "এই ফোনে Text-to-Speech চালু করা যায়নি।" }
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { speechController.neutral() }
                override fun onDone(id: String?) { speechController.neutral() }
                override fun onError(id: String?) { speechController.neutral() }
                override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                    speechController.onTextRange(speakingText, start, end)
                }
            })
            val voice: Voice? = engine.voices?.filter {
                it.locale.language == "bn" && !it.isNetworkConnectionRequired
            }?.maxByOrNull { it.quality }
            if (voice != null) {
                engine.voice = voice
                ttsReady = true
                runOnUiThread { status.text = "অফলাইন বাংলা Voice প্রস্তুত ✓" }
            } else {
                ttsReady = false
                runOnUiThread { status.text = "এই ফোনে Offline বাংলা Voice নেই। Android Text-to-Speech সেটিংসে বাংলা offline voice ইনস্টল করুন।" }
            }
        }
    }

    private fun speakAnswer(answer: String) {
        if (ttsReady && answer.isNotBlank()) {
            speakingText = answer
            tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "aibondhu-answer")
        }
    }

    private fun loadBundledModel() {
        status.text = "অফলাইন AI মডেল খোঁজা হচ্ছে…"
        worker.execute {
            try {
                val target = File(filesDir, "aibondhu-q4_k_m.gguf")
                if (hasGgufMagic(target)) {
                    val ok = nativeLoadModel(target.absolutePath)
                    runOnUiThread { modelReady = ok; status.text = if (ok) "বাংলা অফলাইন AI মডেল প্রস্তুত ✓" else "GGUF মডেল লোড ব্যর্থ — সঠিক মডেল ফাইল নির্বাচন করুন।" }
                } else {
                    runOnUiThread { status.text = "মডেল ফাইল নির্বাচন করুন: GGUF অথবা AIBondhu model ZIP" }
                }
            } catch (e: UnsatisfiedLinkError) { runOnUiThread { status.text = "Native AI ইঞ্জিন চালু হয়নি: ${e.message}" } }
            catch (e: Exception) { runOnUiThread { status.text = "মডেল পরীক্ষা ব্যর্থ: ${e.message}" } }
        }
    }

    private fun hasGgufMagic(file: File): Boolean {
        if (!file.isFile || file.length() < 100_000_000L) return false
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4); raf.readFully(magic)
            return magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))
        }
    }

    private fun selectedName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: "selected-model"
        }
        return uri.lastPathSegment ?: "selected-model"
    }

    private fun importAndLoad(uri: Uri) {
        status.text = "মডেল ফাইল যাচাই ও প্রস্তুত হচ্ছে…"
        worker.execute {
            try {
                val target = File(filesDir, "aibondhu-q4_k_m.gguf")
                val part = File(filesDir, "aibondhu-q4_k_m.gguf.part")
                val name = selectedName(uri).lowercase()
                contentResolver.openInputStream(uri).use { raw ->
                    requireNotNull(raw) { "নির্বাচিত ফাইল পড়া যায়নি" }.use { stream ->
                        if (name.endsWith(".zip")) {
                            ZipInputStream(stream).use { zip ->
                                var entry = zip.nextEntry; var found = false
                                while (entry != null) {
                                    if (!entry.isDirectory && entry.name.lowercase().endsWith(".gguf")) {
                                        part.outputStream().use { output -> zip.copyTo(output) }; found = true; break
                                    }
                                    zip.closeEntry(); entry = zip.nextEntry
                                }
                                require(found) { "ZIP-এর ভিতরে .gguf মডেল পাওয়া যায়নি" }
                            }
                        } else { part.outputStream().use { output -> stream.copyTo(output) } }
                    }
                }
                require(hasGgufMagic(part)) { "এটি সঠিক GGUF মডেল নয় অথবা ফাইল অসম্পূর্ণ। GitHub থেকে aibondhu-q4_k_m.gguf Export করা ফাইল দিন।" }
                if (target.exists()) target.delete()
                check(part.renameTo(target)) { "মডেল ফাইল সংরক্ষণ করা যায়নি" }
                runOnUiThread { status.text = "GGUF মডেল যাচাই হয়েছে; AI ইঞ্জিন চালু হচ্ছে…" }
                val ok = nativeLoadModel(target.absolutePath)
                runOnUiThread { modelReady = ok; status.text = if (ok) "বাংলা অফলাইন AI মডেল প্রস্তুত ✓" else "GGUF সঠিক, কিন্তু llama.cpp লোড করতে পারেনি। Logcat পরীক্ষা দরকার।" }
            } catch (e: UnsatisfiedLinkError) { runOnUiThread { status.text = "Native AI ইঞ্জিন চালু হয়নি: ${e.message}" } }
            catch (e: Exception) { runOnUiThread { modelReady = false; status.text = "মডেল লোড ব্যর্থ: ${e.message}" } }
        }
    }
    private fun sendMessage() {
        val question = input.text.toString().trim()
        if (question.isEmpty()) return
        if (!modelReady) { status.text = "AI মডেল এখনো প্রস্তুত নয়।"; return }
        input.text.clear()
        chat.text = "আপনি: $question\n\nAIBondhu ভাবছে…"
        worker.execute {
            val answer = try { nativeGenerate(question) } catch (e: Exception) { "ত্রুটি: ${e.message}" }
            runOnUiThread {
                chat.text = "আপনি: $question\n\nAIBondhu: $answer"
                speakAnswer(answer)
            }
        }
    }

    override fun onDestroy() {
        speechController.neutral()
        tts?.stop()
        tts?.shutdown()
        avatar.destroy()
        worker.shutdown()
        super.onDestroy()
    }

    companion object { init { System.loadLibrary("aibondhu-jni") } }
}
