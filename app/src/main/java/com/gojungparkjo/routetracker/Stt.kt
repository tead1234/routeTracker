package com.gojungparkjo.routetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class Stt(val context: Context, val packageName: String) {

    var intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
    }

    var onReadyForSpeech: ((Bundle?) -> Unit)? = null
    var onResults: ((Bundle?) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    lateinit var speechRecognizer: SpeechRecognizer

    val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(p0: Bundle?) {
            onReadyForSpeech?.invoke(p0)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(p0: Float) {}

        override fun onBufferReceived(p0: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO ->
                    "오디오 에러"
                SpeechRecognizer.ERROR_CLIENT ->
                    "클라이언트 에러"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "퍼미션 에러"
                SpeechRecognizer.ERROR_NETWORK ->
                    "네트워크 에러"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "네트워크 타임아웃"
                SpeechRecognizer.ERROR_NO_MATCH ->
                    "소리가 들리지 않았거나 적절한 변경 단어를 찾지 못함"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    "RECOGNIZER_BUSY 에러"
                SpeechRecognizer.ERROR_SERVER ->
                    "서버 에러"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "말하는 시간 초과"
                else ->
                    "알 수 없는 오류"
            }
            onError?.invoke(message)
        }

        override fun onResults(p0: Bundle?) {
            onResults?.invoke(p0)
        }

        override fun onPartialResults(p0: Bundle?) {}

        override fun onEvent(p0: Int, p1: Bundle?) {}
    }

    fun startSpeech() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer.setRecognitionListener(recognitionListener)
        speechRecognizer.startListening(intent)
    }
}