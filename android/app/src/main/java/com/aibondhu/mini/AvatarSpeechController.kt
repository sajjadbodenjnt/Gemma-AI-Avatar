package com.aibondhu.mini

class AvatarSpeechController(private val onFrame: (String, Float) -> Unit) {
    fun neutral() = onFrame("REST", 0f)
    fun onTextRange(text: CharSequence?, start: Int, end: Int) {
        if (text == null || start < 0 || end <= start || start >= text.length) { neutral(); return }
        val s = text.subSequence(start, end.coerceAtMost(text.length)).toString()
        val v = when {
            s.any { it in "পফবভম" } -> "CLOSED"
            s.any { it in "সশষজঝচছ" } -> "TEETH"
            s.any { it in "ওঔউঊ" || it in "োৌুূ" } -> "ROUND"
            s.any { it in "আঅআইঐ" || it in "ািীৈ" } -> "OPEN"
            s.any { it in "এঈ" || it == 'ে' } -> "WIDE"
            s.isBlank() || s.all { it.isWhitespace() || it in "।,!?;:." } -> "REST"
            else -> "NARROW"
        }
        onFrame(v, if (v == "REST") 0f else 0.82f)
    }
}