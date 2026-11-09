package com.gemma.avatar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var engine: Engine? = null
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null
    private lateinit var log: TextView
    private lateinit var state: TextView
    private lateinit var prompt: EditText
    private lateinit var avatar: AvatarView
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private val modelName = "gemma.litertlm"

    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    File(filesDir, modelName).outputStream().use(input::copyTo)
                }
                withContext(Dispatchers.Main) { loadModel() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { state.text = "Model copy error: ${e.message}" }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildUi()
        if (SpeechRecognizer.isRecognitionAvailable(this)) recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        if (File(filesDir, modelName).exists()) loadModel()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.rgb(12, 18, 32))
        }
        val title = TextView(this).apply {
            text = "GEMMA AI AVATAR"; textSize = 21f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setPadding(0, 8, 0, 8)
        }
        state = TextView(this).apply {
            text = "OFFLINE AI • Select a compatible model"
            textSize = 12f; gravity = Gravity.CENTER; setTextColor(Color.rgb(115, 225, 185))
        }
        avatar = AvatarView(this)
        log = TextView(this).apply {
            text = "স্বাগতম! আমি Gemma AI Avatar.\nশুরু করতে আপনার .litertlm মডেল নির্বাচন করুন।"
            textSize = 15f; setTextColor(Color.WHITE); setPadding(12, 12, 12, 12)
        }
        val scroll = ScrollView(this).apply {
            addView(log); setBackgroundColor(Color.rgb(26, 35, 53))
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
        val choose = Button(this).apply {
            text = "Choose Offline Gemma Model"
            setOnClickListener { modelPicker.launch(arrayOf("*/*")) }
        }
        root.addView(title)
        root.addView(state)
        root.addView(avatar, LinearLayout.LayoutParams(-1, 230))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(inputRow)
        root.addView(choose)
        setContentView(root)
    }

    private fun loadModel() {
        val file = File(filesDir, modelName)
        if (!file.exists() || file.length() < 1024) {
            state.text = "Model missing or invalid"; return
        }
        state.text = "Loading model…"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val newEngine = Engine(EngineConfig(modelPath = file.absolutePath))
                newEngine.initialize()
                val newConversation = newEngine.createConversation()
                withContext(Dispatchers.Main) {
                    conversation?.close(); engine?.close()
                    engine = newEngine; conversation = newConversation
                    state.text = "Gemma ready • Offline"
                    log.append("\n\n[মডেল প্রস্তুত। এখন অফলাইনে চ্যাট করুন।]")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { state.text = "Load failed: ${e.message}" }
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
            log.append("আগে Choose Offline Gemma Model থেকে একটি LiteRT-LM model নির্বাচন করুন।")
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
        if (statusCode == TextToSpeech.SUCCESS) tts?.language = Locale("bn", "BD")
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop(); tts?.shutdown()
        conversation?.close(); engine?.close()
        super.onDestroy()
    }
}

class AvatarView(context: android.content.Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var talking = false
    private var frame = 0f
    private val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 300; repeatCount = android.animation.ValueAnimator.INFINITE
        addUpdateListener { frame = if (talking) it.animatedValue as Float else 0f; invalidate() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); animator.start() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        p.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
            Color.rgb(34, 73, 110), Color.rgb(84, 49, 108), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 24f, 24f, p)
        p.shader = null
        p.color = Color.rgb(235, 190, 154); canvas.drawCircle(cx, cy, 73f, p)
        p.color = Color.rgb(48, 31, 29); canvas.drawOval(cx-75, cy-85, cx+75, cy-20, p)
        p.color = Color.WHITE; canvas.drawCircle(cx-25, cy-4, 8f, p); canvas.drawCircle(cx+25, cy-4, 8f, p)
        p.color = Color.DKGRAY; canvas.drawCircle(cx-25, cy-4, 3.5f, p); canvas.drawCircle(cx+25, cy-4, 3.5f, p)
        p.color = Color.rgb(145, 48, 63)
        canvas.drawOval(cx-15, cy+32, cx+15, cy+37+frame*15, p)
        p.color = Color.WHITE; p.textSize = 12f; p.textAlign = Paint.Align.CENTER
        canvas.drawText("AI AVATAR", cx, height-12f, p)
    }
}
