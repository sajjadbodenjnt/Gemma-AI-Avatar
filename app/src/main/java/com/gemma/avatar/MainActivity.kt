package com.gemma.avatar

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.coroutineContext

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var engine: Engine? = null
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null
    private lateinit var log: TextView
    private lateinit var state: TextView
    private lateinit var prompt: EditText
    private lateinit var avatar: AvatarView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var downloadLabel: TextView
    private lateinit var downloadButton: Button
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var downloadJob: Job? = null
    @Volatile private var activeConnection: HttpURLConnection? = null

    private val modelName = "gemma3-1b-it-int4.litertlm"
    private val modelFile get() = File(filesDir, modelName)
    private val modelUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm"
    private var hfToken: String? = null

    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    File(filesDir, modelName).outputStream().use(input::copyTo)
                } ?: error("ফাইল খোলা যায়নি")
                withContext(Dispatchers.Main) { loadModel() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { state.text = "Model copy error: ${e.message}" }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hfToken = TokenVault.read(this)
        tts = TextToSpeech(this, this)
        buildUi()
        if (SpeechRecognizer.isRecognitionAvailable(this)) recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        if (modelFile.exists() && modelFile.length() > 100_000_000L) loadModel()
        updateDownloadButton()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.rgb(9, 15, 29))
        }
        val title = TextView(this).apply {
            text = "✦  GEMMA AI AVATAR"; textSize = 21f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setPadding(0, 8, 0, 3)
        }
        val subtitle = TextView(this).apply {
            text = "YOUR PRIVATE • ON-DEVICE AI COMPANION"
            textSize = 10f; gravity = Gravity.CENTER; setTextColor(Color.rgb(121, 190, 224))
            setPadding(0, 0, 0, 8)
        }
        state = TextView(this).apply {
            text = "OFFLINE AI • Model not installed"
            textSize = 12f; gravity = Gravity.CENTER; setTextColor(Color.rgb(115, 225, 185))
        }
        avatar = AvatarView(this)
        log = TextView(this).apply {
            text = "স্বাগতম! আমি Gemma AI Avatar.\nপ্রথমে Hugging Face থেকে Gemma 3 1B মডেল ডাউনলোড করুন।"
            textSize = 15f; setTextColor(Color.WHITE); setPadding(12, 12, 12, 12)
        }
        val scroll = ScrollView(this).apply {
            addView(log); setBackgroundColor(Color.rgb(24, 34, 53))
        }
        prompt = EditText(this).apply {
            hint = "এখানে লিখুন…"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            maxLines = 3
        }
        val send = Button(this).apply { text = "Send"; setOnClickListener { sendMessage() } }
        val mic = Button(this).apply { text = "🎙"; setOnClickListener { voiceInput() } }
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(prompt, LinearLayout.LayoutParams(0, -2, 1f)); addView(mic); addView(send)
        }
        downloadButton = Button(this).apply {
            text = "⬇ Download Gemma 3 1B (~584 MB)"
            setOnClickListener { onDownloadClicked() }
        }
        val tokenButton = Button(this).apply {
            text = "Hugging Face Access / Change Token"
            setOnClickListener { showTokenDialog() }
        }
        val choose = Button(this).apply {
            text = "Choose Existing .litertlm Model"
            setOnClickListener { modelPicker.launch(arrayOf("application/octet-stream", "*/*")) }
        }
        downloadProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0; visibility = View.GONE
        }
        downloadLabel = TextView(this).apply {
            text = "Model download: ready"; textSize = 11f
            setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(state)
        root.addView(avatar, LinearLayout.LayoutParams(-1, 190))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(inputRow)
        root.addView(downloadButton)
        root.addView(downloadProgress)
        root.addView(downloadLabel)
        root.addView(tokenButton)
        root.addView(choose)
        setContentView(root)
    }

    private fun updateDownloadButton() {
        downloadButton.text = if (modelFile.exists() && modelFile.length() > 100_000_000L)
            "↻ Download Gemma 3 1B Again" else "⬇ Download Gemma 3 1B (~584 MB)"
    }

    private fun onDownloadClicked() {
        if (downloadJob?.isActive == true) {
            downloadJob?.cancel()
            activeConnection?.disconnect()
            downloadLabel.text = "Download cancelled. Partial file kept for resume."
            state.text = "Download paused"
            return
        }
        val token = hfToken
        if (token.isNullOrBlank()) showTokenDialog() else startModelDownload(token)
    }

    private fun showTokenDialog() {
        val field = EditText(this).apply {
            hint = "hf_… Read access token"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            hfToken?.let { setText(it) }
        }
        val message = TextView(this).apply {
            text = "প্রথমে Hugging Face-এ লগইন করে Gemma license গ্রহণ করুন এবং Read access token তৈরি করুন। Token এই ডিভাইসে Android Keystore দিয়ে encrypted থাকবে।"
            setPadding(36, 8, 36, 8); setTextColor(Color.DKGRAY)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(message); addView(field)
        }
        AlertDialog.Builder(this)
            .setTitle("Hugging Face access")
            .setView(box)
            .setPositiveButton("Save & Download") { _, _ ->
                val token = field.text.toString().trim()
                if (!token.startsWith("hf_") || token.length < 20) {
                    state.text = "সঠিক Hugging Face read token দিন"
                } else {
                    hfToken = token
                    TokenVault.save(this, token)
                    startModelDownload(token)
                }
            }
            .setNeutralButton("Open Gemma License") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/litert-community/Gemma3-1B-IT")))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startModelDownload(token: String) {
        if (downloadJob?.isActive == true) return
        downloadButton.text = "Cancel Download"
        downloadProgress.visibility = View.VISIBLE
        downloadProgress.progress = 0
        state.text = "Connecting to Hugging Face…"
        downloadJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                downloadModel(token)
                withContext(Dispatchers.Main) {
                    downloadLabel.text = "Download complete • checking model…"
                    state.text = "Model downloaded • initializing"
                    updateDownloadButton()
                    loadModel()
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    downloadLabel.text = "Download paused. Press Download to resume."
                    state.text = "Download paused"
                    updateDownloadButton()
                }
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    downloadLabel.text = "Download failed: ${e.message}"
                    state.text = if (e.message?.contains("401") == true || e.message?.contains("403") == true)
                        "Access denied • accept Gemma license / check token" else "Download error"
                    updateDownloadButton()
                }
            } finally {
                activeConnection = null
            }
        }
    }

    private suspend fun downloadModel(token: String) {
        val partial = File(filesDir, "$modelName.part")
        val existing = if (partial.exists()) partial.length() else 0L
        val connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "GemmaAIAvatar/1.1 Android")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        activeConnection = connection
        try {
            val code = connection.responseCode
            if (code == 401 || code == 403) error("HTTP $code: Hugging Face access denied. Accept Gemma terms and use a read token.")
            if (code !in listOf(200, 206)) error("HTTP $code from Hugging Face")
            val append = code == 206 && existing > 0
            if (!append && partial.exists()) partial.delete()
            val total = if (append) existing + connection.contentLengthLong else connection.contentLengthLong
            val type = connection.contentType.orEmpty()
            if (type.contains("text/html", ignoreCase = true)) error("Hugging Face returned a web page, not model data. Check access token.")
            var downloaded = if (append) existing else 0L
            connection.inputStream.use { input ->
                partial.outputStream(append).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        downloaded += n
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                            withContext(Dispatchers.Main) {
                                downloadProgress.progress = percent
                                downloadLabel.text = "Downloading Gemma 3 1B • $percent% • ${downloaded / (1024 * 1024)} MB / ${total / (1024 * 1024)} MB"
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                downloadLabel.text = "Downloaded ${downloaded / (1024 * 1024)} MB"
                            }
                        }
                    }
                }
            }
            if (partial.length() < 100_000_000L) error("Downloaded file is too small; incomplete or unauthorized response.")
            if (modelFile.exists()) modelFile.delete()
            if (!partial.renameTo(modelFile)) error("Could not finalize model file")
            withContext(Dispatchers.Main) { downloadProgress.progress = 100 }
        } finally {
            connection.disconnect()
            activeConnection = null
        }
    }

    private fun loadModel() {
        val file = modelFile
        if (!file.exists() || file.length() < 100_000_000L) {
            state.text = "Model missing or incomplete"; return
        }
        state.text = "Loading Gemma model…"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val newEngine = Engine(EngineConfig(modelPath = file.absolutePath))
                newEngine.initialize()
                val newConversation = newEngine.createConversation()
                withContext(Dispatchers.Main) {
                    conversation?.close(); engine?.close()
                    engine = newEngine; conversation = newConversation
                    state.text = "Gemma 3 1B ready • Offline"
                    log.append("\n\n[Gemma প্রস্তুত। এখন ইন্টারনেট ছাড়াই চ্যাট করতে পারবেন।]")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { state.text = "Model load failed: ${e.message}" }
            }
        }
    }

    private fun sendMessage() {
        val question = prompt.text.toString().trim()
        if (question.isEmpty()) return
        prompt.setText("")
        log.append("\n\nYou: $question\nGemma: ")
        val active = conversation
        if (active == null) {
            log.append("আগে Gemma 3 1B মডেল ডাউনলোড বা নির্বাচন করুন।")
            return
        }
        state.text = "Thinking…"
        lifecycleScope.launch {
            try {
                val answer = withContext(Dispatchers.IO) {
                    val output = StringBuilder()
                    active.sendMessageAsync(question).collect { output.append(it.toString()) }
                    output.toString()
                }
                log.append(answer)
                state.text = "Ready • Offline"
                speak(answer)
            } catch (e: Exception) {
                log.append("\n[Inference error: ${e.message}]")
                state.text = "Inference error"
            }
        }
    }

    private fun voiceInput() {
        if (recognizer == null) { state.text = "Speech recognition unavailable"; return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 9); return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
        }
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { state.text = "Listening…" }
            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (words != null) prompt.setText(words)
                state.text = "Voice captured"
            }
            override fun onError(error: Int) { state.text = "Voice error: $error" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer?.startListening(intent)
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        avatar.talking = true
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gemma_reply")
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            tts?.language = Locale("bn", "BD")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { runOnUiThread { avatar.talking = true } }
                override fun onDone(utteranceId: String?) { runOnUiThread { avatar.talking = false } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { runOnUiThread { avatar.talking = false } }
            })
        }
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        activeConnection?.disconnect()
        recognizer?.destroy()
        tts?.stop(); tts?.shutdown()
        conversation?.close(); engine?.close()
        super.onDestroy()
    }
}

private object TokenVault {
    private const val alias = "gemma_hf_token_aes"
    private const val prefs = "secure_hf"
    private const val keyName = "token"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    fun save(context: android.content.Context, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        context.getSharedPreferences(prefs, 0).edit().putString(keyName, encoded).apply()
    }

    fun read(context: android.content.Context): String? {
        return try {
            val encoded = context.getSharedPreferences(prefs, 0).getString(keyName, null) ?: return null
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }
}

class AvatarView(context: android.content.Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var talking = false
        set(value) { field = value; invalidate() }
    private var frame = 0f
    private val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 300; repeatCount = android.animation.ValueAnimator.INFINITE
        addUpdateListener { frame = if (talking) it.animatedValue as Float else 0f; invalidate() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); animator.start() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        p.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
            Color.rgb(28, 76, 119), Color.rgb(82, 43, 113), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 24f, 24f, p)
        p.shader = null
        p.color = Color.rgb(235, 190, 154); canvas.drawCircle(cx, cy, 70f, p)
        p.color = Color.rgb(48, 31, 29); canvas.drawOval(cx-73, cy-82, cx+73, cy-18, p)
        p.color = Color.WHITE; canvas.drawCircle(cx-24, cy-4, 8f, p); canvas.drawCircle(cx+24, cy-4, 8f, p)
        p.color = Color.DKGRAY; canvas.drawCircle(cx-24, cy-4, 3.5f, p); canvas.drawCircle(cx+24, cy-4, 3.5f, p)
        p.color = Color.rgb(145, 48, 63)
        canvas.drawOval(cx-14, cy+29, cx+14, cy+34+frame*14, p)
        p.color = Color.WHITE; p.textSize = 11f; p.textAlign = Paint.Align.CENTER
        canvas.drawText("AI AVATAR", cx, height-10f, p)
    }
}
