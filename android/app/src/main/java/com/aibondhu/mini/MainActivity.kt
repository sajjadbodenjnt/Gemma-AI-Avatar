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
        root.addView(label("AIBondhu", 27f, 0xFFF4F8FF.toInt()))
        root.addView(label("বাংলা অফলাইন AI · Qwen 0.5B Bengali LoRA", 13f, 0xFF55D6FF.toInt()))
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
        root.addView(avatar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)))
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
        status.text = "অ্যাপের সঙ্গে থাকা বাংলা AI মডেল কপি হচ্ছে…"
        worker.execute {
            try {
                val target = File(filesDir, "aibondhu-q4_k_m.gguf")
                if (!target.exists() || target.length() < 100_000_000L) {
                    assets.open("aibondhu-q4_k_m.gguf").use { source ->
                        target.outputStream().use { output -> source.copyTo(output) }
                    }
                }
                val ok = nativeLoadModel(target.absolutePath)
                runOnUiThread {
                    modelReady = ok
                    status.text = if (ok) "বাংলা অফলাইন AI মডেল প্রস্তুত ✓" else "মডেল লোড হয়নি। মডেল ফাইল নির্বাচন করে চেষ্টা করুন।"
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "মডেল লোড ত্রুটি: ${e.message} — GGUF ফাইল নির্বাচন করুন।" }
            } catch (e: UnsatisfiedLinkError) {
                runOnUiThread { status.text = "Native AI ইঞ্জিন চালু হয়নি: ${e.message}" }
            }
        }
    }

    private fun importAndLoad(uri: Uri) {
        status.text = "নির্বাচিত GGUF মডেল কপি ও লোড হচ্ছে…"
        worker.execute {
            try {
                val target = File(filesDir, "aibondhu-q4_k_m.gguf")
                contentResolver.openInputStream(uri).use { source ->
                    requireNotNull(source) { "ফাইল পড়া যায়নি" }.copyTo(target.outputStream())
                }
                val ok = nativeLoadModel(target.absolutePath)
                runOnUiThread {
                    modelReady = ok
                    status.text = if (ok) "অফলাইন মডেল প্রস্তুত ✓" else "মডেল লোড ব্যর্থ"
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "ত্রুটি: ${e.message}" }
            }
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
