package com.gemma.avatar

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var llm: LlmInference? = null
    private lateinit var log: TextView
    private lateinit var state: TextView
    private lateinit var prompt: EditText
    private lateinit var avatar: Mira3DView
    private lateinit var previewView: PreviewView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var downloadLabel: TextView
    private lateinit var downloadButton: Button
    private lateinit var cameraButton: Button
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var downloadJob: Job? = null
    @Volatile private var activeConnection: HttpURLConnection? = null
    private var faceDetector: FaceDetector? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val modelName = "gemma3-1b-it-int4.task"
    private val modelFile get() = File(filesDir, modelName)
    private val modelUrl = "https://huggingface.co/MiCkSoftware/Gemma3-1B-IT-LiteRT/resolve/main/gemma3-1b-it-int4.task"
    private val expectedSha256 = "e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee"

    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(modelFile).use { output -> input.copyTo(output) }
                } ?: error("ফাইল খোলা যায়নি")
                withContext(Dispatchers.Main) { loadModel() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { state.text = "Model copy error: ${e.message}" }
            }
        }
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else state.text = "ক্যামেরা ব্যবহারের অনুমতি দেওয়া হয়নি"
    }
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceRecognition() else state.text = "মাইক্রোফোনের অনুমতি দেওয়া হয়নি"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()
        faceDetector = FaceDetection.getClient(options)
        if (SpeechRecognizer.isRecognitionAvailable(this)) recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        buildUi()
        if (modelFile.exists() && modelFile.length() > 500_000_000L) loadModel()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 8, 14, 8)
            setBackgroundColor(Color.rgb(9, 14, 29))
        }
        val title = TextView(this).apply {
            text = "✦  MIRA • GEMMA AI"; textSize = 21f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setPadding(0, 8, 0, 2)
        }
        val subtitle = TextView(this).apply {
            text = "YOUR PRIVATE ON-DEVICE AI COMPANION"
            textSize = 10f; gravity = Gravity.CENTER; setTextColor(Color.rgb(121, 190, 224))
            setPadding(0, 0, 0, 6)
        }
        state = TextView(this).apply {
            text = "AI offline model not installed"; textSize = 12f
            gravity = Gravity.CENTER; setTextColor(Color.rgb(115, 225, 185))
        }
        avatar = Mira3DView(this)
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            visibility = View.GONE
        }
        val visualRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(avatar, LinearLayout.LayoutParams(0, 230, 1f))
            addView(previewView, LinearLayout.LayoutParams(112, 150))
            gravity = Gravity.CENTER
        }
        log = TextView(this).apply {
            text = "হ্যালো! আমি মীরা। আমাকে Download Gemma চাপ দিয়ে প্রস্তুত করুন।"
            textSize = 15f; setTextColor(Color.WHITE); setPadding(12, 12, 12, 12)
        }
        val scroll = ScrollView(this).apply {
            addView(log); setBackgroundColor(Color.rgb(24, 34, 53))
        }
        prompt = EditText(this).apply {
            hint = "আমাকে কিছু বলুন…"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            maxLines = 3
        }
        val send = Button(this).apply { text = "Send"; setOnClickListener { sendMessage() } }
        val mic = Button(this).apply { text = "🎙"; setOnClickListener { voiceInput() } }
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(prompt, LinearLayout.LayoutParams(0, -2, 1f)); addView(mic); addView(send)
        }
        downloadButton = Button(this).apply {
            text = "⬇ এক চাপেই Gemma 3 1B ডাউনলোড"
            setOnClickListener { onDownloadClicked() }
        }
        cameraButton = Button(this).apply {
            text = "📷 ক্যামেরা চালু"
            setOnClickListener { toggleCamera() }
        }
        val choose = Button(this).apply {
            text = "আগে থেকে থাকা .task মডেল নির্বাচন"
            setOnClickListener { modelPicker.launch(arrayOf("application/octet-stream", "*/*")) }
        }
        downloadProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0; visibility = View.GONE
        }
        downloadLabel = TextView(this).apply {
            text = "মডেল ডাউনলোডের জন্য প্রস্তুত"; textSize = 11f
            setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
        }
        root.addView(title); root.addView(subtitle); root.addView(state)
        root.addView(visualRow)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(inputRow)
        root.addView(downloadButton)
        root.addView(downloadProgress)
        root.addView(downloadLabel)
        root.addView(cameraButton)
        root.addView(choose)
        setContentView(root)
    }

    private fun onDownloadClicked() {
        if (downloadJob?.isActive == true) {
            downloadJob?.cancel()
            activeConnection?.disconnect()
            downloadLabel.text = "ডাউনলোড থামানো হয়েছে; পরে আবার চালালে চলবে"
            return
        }
        val consent = CheckBox(this).apply {
            text = "আমি Gemma Terms of Use পড়েছি এবং মেনে নিয়ে মডেলটি ব্যবহার করতে সম্মত"
            setTextColor(Color.DKGRAY)
            setPadding(18, 12, 18, 12)
        }
        val message = TextView(this).apply {
            text = "মডেলটি একটি পাবলিক কমিউনিটি মিরর থেকে ডাউনলোড হবে (প্রায় ৫৫৫ MB)। Hugging Face Token লাগবে না। এটি Google-এর Gemma মডেল; ব্যবহারের আগে Gemma Terms মানতে হবে। ডাউনলোড শেষে SHA-256 যাচাই করা হবে।"
            setTextColor(Color.DKGRAY); setPadding(20, 12, 20, 8)
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(message); addView(consent)
        }
        AlertDialog.Builder(this)
            .setTitle("Gemma মডেল ডাউনলোড")
            .setView(panel)
            .setPositiveButton("Terms মেনে ডাউনলোড") { _, _ ->
                if (!consent.isChecked) {
                    state.text = "ডাউনলোডের আগে Gemma Terms-এ সম্মতি দিন"
                    return@setPositiveButton
                }
                startModelDownload()
            }
            .setNeutralButton("Gemma Terms পড়ুন") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.google.dev/gemma/terms")))
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun startModelDownload() {
        if (downloadJob?.isActive == true) return
        downloadButton.text = "ডাউনলোড থামান"
        downloadProgress.visibility = View.VISIBLE
        downloadJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                downloadModel()
                withContext(Dispatchers.Main) {
                    downloadLabel.text = "ডাউনলোড সম্পূর্ণ • SHA-256 যাচাই হয়েছে"
                    state.text = "Gemma প্রস্তুত হচ্ছে…"
                    downloadButton.text = "Gemma আবার ডাউনলোড"
                    loadModel()
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    downloadLabel.text = "ডাউনলোড থেমেছে; আবার চাপলে Resume হবে"
                    downloadButton.text = "ডাউনলোড আবার শুরু করুন"
                }
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    downloadLabel.text = "ডাউনলোড ব্যর্থ: ${e.message}"
                    state.text = "Download error"
                    downloadButton.text = "আবার চেষ্টা করুন"
                }
            } finally {
                activeConnection = null
            }
        }
    }

    private suspend fun downloadModel() {
        val partial = File(filesDir, "$modelName.part")
        val existing = if (partial.exists()) partial.length() else 0L
        val connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "GemmaAIAvatar/2.0 Android")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        activeConnection = connection
        try {
            val code = connection.responseCode
            if (code !in listOf(200, 206)) error("HTTP $code; ইন্টারনেট বা মডেল সার্ভার পরীক্ষা করুন")
            val append = code == 206 && existing > 0
            if (!append && partial.exists()) partial.delete()
            val total = if (append) existing + connection.contentLengthLong else connection.contentLengthLong
            val type = connection.contentType.orEmpty()
            if (type.contains("text/html", true)) error("মডেল ফাইলের বদলে ওয়েব পেজ এসেছে")
            var downloaded = if (append) existing else 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        downloaded += n
                        val percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
                        withContext(Dispatchers.Main) {
                            downloadProgress.progress = percent
                            downloadLabel.text = "Gemma ডাউনলোড: $percent% • ${downloaded / 1048576} MB"
                        }
                    }
                }
            }
            if (partial.length() < 500_000_000L) error("ফাইল অসম্পূর্ণ; আবার Download চাপুন")
            val digest = MessageDigest.getInstance("SHA-256")
            partial.inputStream().buffered().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!hash.equals(expectedSha256, true)) error("SHA-256 মিলেনি; নিরাপত্তার জন্য মডেল বাতিল করা হয়েছে")
            if (modelFile.exists()) modelFile.delete()
            if (!partial.renameTo(modelFile)) error("মডেল ফাইল সংরক্ষণ করা যায়নি")
            withContext(Dispatchers.Main) { downloadProgress.progress = 100 }
        } finally {
            connection.disconnect()
            activeConnection = null
        }
    }

    private fun loadModel() {
        if (!modelFile.exists() || modelFile.length() < 500_000_000L) {
            state.text = "মডেল ডাউনলোড করা হয়নি"; return
        }
        state.text = "Gemma মডেল লোড হচ্ছে…"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(512)
                    .build()
                val instance = LlmInference.createFromOptions(this@MainActivity, options)
                withContext(Dispatchers.Main) {
                    llm?.close()
                    llm = instance
                    state.text = "Gemma 3 1B প্রস্তুত • Offline"
                    log.append("\n\nমীরা এখন অফলাইনে কথা বলতে প্রস্তুত।")
                    downloadButton.text = "Gemma মডেল প্রস্তুত"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state.text = "মডেল লোড হয়নি: ${e.message} • ফোনের RAM/সাপোর্ট পরীক্ষা করুন"
                }
            }
        }
    }

    private fun sendMessage() {
        val question = prompt.text.toString().trim()
        if (question.isEmpty()) return
        prompt.setText("")
        log.append("\n\nআপনি: $question\nমীরা: ")
        val active = llm
        if (active == null) {
            log.append("আগে Gemma মডেল ডাউনলোড করুন।")
            return
        }
        state.text = "মীরা ভাবছে…"
        lifecycleScope.launch {
            try {
                val answer = withContext(Dispatchers.IO) {
                    active.generateResponse("তুমি মীরা, বন্ধুসুলভ বাংলা ভাষাভাষী ভার্চুয়াল AI সঙ্গী। স্বাভাবিক, সংক্ষিপ্ত ও সহানুভূতিশীলভাবে উত্তর দাও। ব্যবহারকারীর আবেগকে সম্মান করো।\\nব্যবহারকারী: $question")
                }
                log.append(answer)
                setExpression(answer)
                state.text = "Ready • Offline"
                speak(answer)
            } catch (e: Exception) {
                log.append("\n[AI error: ${e.message}]")
                state.text = "AI inference error"
            }
        }
    }

    private fun setExpression(text: String) {
        val lower = text.lowercase()
        avatar.expression = when {
            listOf("দুঃখ", "কষ্ট", "খারাপ লাগ", "সমবেদনা", "sorry", "sad").any { lower.contains(it) } -> Mira3DView.Expression.SAD
            listOf("হা হা", "মজার", "খুশি", "দারুণ", "চমৎকার", "আনন্দ", "হাসি", "great", "happy").any { lower.contains(it) } -> Mira3DView.Expression.HAPPY
            listOf("অবাক", "সত্যি?", "বাহ!", "wow").any { lower.contains(it) } -> Mira3DView.Expression.SURPRISED
            else -> Mira3DView.Expression.NEUTRAL
        }
    }

    private fun voiceInput() {
        if (recognizer == null) { state.text = "Speech recognition পাওয়া যায়নি"; return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO); return
        }
        startVoiceRecognition()
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { state.text = "শুনছি…" }
            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (words != null) { prompt.setText(words); sendMessage() }
                state.text = "কথা বুঝেছি"
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
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mira_reply")
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("bn", "BD"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                state.text = "ফোনে বাংলা TTS voice নেই; TTS settings থেকে বাংলা voice ইনস্টল করুন"
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { runOnUiThread { avatar.talking = true } }
                override fun onDone(utteranceId: String?) { runOnUiThread { avatar.talking = false } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { runOnUiThread { avatar.talking = false } }
            })
        }
    }

    private fun toggleCamera() {
        if (previewView.visibility == View.VISIBLE) {
            ProcessCameraProvider.getInstance(this).get().unbindAll()
            previewView.visibility = View.GONE
            cameraButton.text = "📷 ক্যামেরা চালু"
            avatar.userSmiling = false
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        } else startCamera()
    }

    private fun startCamera() {
        previewView.visibility = View.VISIBLE
        cameraButton.text = "ক্যামেরা বন্ধ করুন"
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { proxy ->
                    val mediaImage = proxy.image
                    if (mediaImage == null) {
                        proxy.close()
                    } else {
                        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                        faceDetector?.process(image)
                            ?.addOnSuccessListener { faces ->
                                val face = faces.firstOrNull()
                                val smile = face?.smilingProbability ?: 0f
                                runOnUiThread {
                                    avatar.userSmiling = smile > 0.65f
                                    if (face != null) state.text = if (smile > 0.65f) "আপনাকে হাসিখুশি দেখাচ্ছে 😊" else "ক্যামেরায় আপনাকে দেখছি"
                                }
                            }
                            ?.addOnCompleteListener { proxy.close() }
                            ?: proxy.close()
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            } catch (e: Exception) {
                state.text = "ক্যামেরা চালু হয়নি: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onPause() {
        avatar.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::avatar.isInitialized) avatar.onResume()
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        activeConnection?.disconnect()
        recognizer?.destroy()
        faceDetector?.close()
        cameraExecutor.shutdown()
        tts?.stop(); tts?.shutdown()
        llm?.close()
        super.onDestroy()
    }
}
