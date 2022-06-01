package com.gojungparkjo.routetracker.model

import android.Manifest
import android.app.Dialog
import android.content.Context

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gojungparkjo.routetracker.MainActivity
import com.gojungparkjo.routetracker.databinding.FeedbackdialogBinding
import java.util.*
import kotlin.system.exitProcess

class FeedBackDialog(private val context : AppCompatActivity) {

    private lateinit var binding : FeedbackdialogBinding
    private val dlg = Dialog(context)
    private lateinit var mRecognizer: SpeechRecognizer
    private lateinit var recognitionListener: RecognitionListener//부모 액티비티의 context 가 들어감
    private lateinit var intent: Intent

    fun show(content: MainActivity) {
        binding = FeedbackdialogBinding.inflate(context.layoutInflater)

        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)   //타이틀바 제거
        dlg.setContentView(binding.root)     //다이얼로그에 사용할 xml 파일을 불러옴
        dlg.setCancelable(false)    //다이얼로그의 바깥 화면을 눌렀을 때 다이얼로그가 닫히지 않도록 함
    // 권한 체크
        if(ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO)== PackageManager.PERMISSION_DENIED
        ){
            ActivityCompat.requestPermissions(
                context,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                0
            )
        }
        //ok 버튼 동작
        binding.yesBtn.setOnClickListener {
            dlg.dismiss()
            ActivityCompat.finishAffinity(context)
            exitProcess(0)
        }

        binding.mic.setOnClickListener {
            intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            val language = "ko-KR"
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            setListener()
            mRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            mRecognizer.setRecognitionListener(recognitionListener)
            mRecognizer.startListening(intent)
        }
//
//        binding.mic.setOnClickListener {
//
//            dlg.dismiss()
//        }

        dlg.show()
    }
    private fun setListener() {
        recognitionListener = object: RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(context, "음성인식을 시작합니다.", Toast.LENGTH_SHORT).show()
            }

            override fun onBeginningOfSpeech() {

            }

            override fun onRmsChanged(rmsdB: Float) {

            }

            override fun onBufferReceived(buffer: ByteArray?) {

            }

            override fun onEndOfSpeech() {

            }

            override fun onError(error: Int) {
                var message: String

                when (error) {
                    SpeechRecognizer.ERROR_AUDIO ->
                        message = "오디오 에러"
                    SpeechRecognizer.ERROR_CLIENT ->
                        message = "클라이언트 에러"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        message = "퍼미션 없음"
                    SpeechRecognizer.ERROR_NETWORK ->
                        message = "네트워크 에러"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        message = "네트워크 타임아웃"
                    SpeechRecognizer.ERROR_NO_MATCH ->
                        message = "찾을 수 없음"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        message = "RECOGNIZER가 바쁨"
                    SpeechRecognizer.ERROR_SERVER ->
                        message = "서버가 이상함"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        message = "말하는 시간초과"
                    else ->
                        message = "알 수 없는 오류"
                }
                Toast.makeText(context, "에러 발생 $message", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                var matches: ArrayList<String> = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) as ArrayList<String>

                for (i in 0 until matches.size) {
//                    tvResult.text = matches[i]
                    binding.yesBtn.text = matches[i]
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {

            }

            override fun onEvent(eventType: Int, params: Bundle?) {

            }

        }
    }
}
