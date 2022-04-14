package com.gojungparkjo.routetracker.model

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class TTS_Module {
    @SuppressLint("StaticFieldLeak")
    companion object : TextToSpeech.OnInitListener {
        private const val TTS_DATA_CHECK_CODE = 7200
        private var ctx : Activity? = null
        private var TTS: TextToSpeech? = null
        private var text: String = ""
        private var locale: Locale = Locale.KOREA
        private var level: Float = PITCH.NOMAL.level
        private var speed: Float = SPEED.s1_0X.speed

        fun toSpeech(ctx: Activity, text: String ,
                     locale: Locale? = Locale.KOREA,
                     level: Float = PITCH.NOMAL.level,
                     speed: Float = SPEED.s1_0X.speed){
            this.text = text
            this.ctx = ctx
            if (locale != null) this.locale = locale
            if (level != null) this.level = level
            if (speed != null) this.speed = speed

            if(TTS == null) {
                val intent = Intent()
                intent.action = TextToSpeech.Engine.ACTION_CHECK_TTS_DATA
                ctx.startActivityForResult(intent, TTS_DATA_CHECK_CODE)
            }else{
                speeach()
            }
        }

        private fun speeach(){

            setLanguage(this.locale)
            setPitch(this.level)
            setSpeechRate(this.speed)
            TTS.speak(text,)
            // 블루투스 연결시 패이드인으로 앞에 소리가 살짝 끊김을 해소 하기 위해
            // 무은 MP3 를 재생 후 TTS 작동. (MP3는 0.5~1초 정도가 적당해 보임)
//            val resID: Int = ctx!!.resources.getIdentifier(
//                "no_sound", "raw", ctx!!.packageName)
//            val player: MediaPlayer = MediaPlayer.create(ctx, resID)
//            player.setOnCompletionListener {
//                Log.d("B-Rain", "TTS 시작")
//                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
//                    TTS!!.speak("", TextToSpeech.QUEUE_FLUSH, null, null)
//                    TTS!!.speak(text, TextToSpeech.QUEUE_ADD, null, null)
//                } else {
//                    TTS!!.speak("", TextToSpeech.QUEUE_FLUSH, null)
//                    TTS!!.speak(text, TextToSpeech.QUEUE_ADD, null)
//                }
//            }
//            player.start()
//            Log.d("B-Rain", "무음 MP3 시작")
        }

        override fun onInit(status: Int) {
            if (status == TextToSpeech.SUCCESS) {
                if (TTS!!.isLanguageAvailable(Locale.KOREA) >=
                    TextToSpeech.LANG_AVAILABLE) {
                    TTS!!.language = Locale.KOREA

                    speeach()
                }
            }
        }

        /** 음성 언어 */
        fun setLanguage(locale: Locale? = Locale.KOREA) {
            if (TTS != null) TTS!!.language = locale
        }

        /** 음성 속도 */
        fun setSpeechRate(speed: Float = SPEED.s1_0X.speed) {
            if (TTS != null) TTS!!.setSpeechRate(speed)
        }

        /** 음성 톤 */
        fun setPitch(level: Float = PITCH.NOMAL.level) {
            if (TTS != null) TTS!!.setPitch(level)
        }

        fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode === TTS_DATA_CHECK_CODE) {
                if (resultCode === TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                    if(TTS == null) {
                        TTS = TextToSpeech(ctx, this)
                    }else{
                        speeach()
                    }

                } else {
                    val installIntent = Intent()
                    installIntent.action =
                        TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
                    ctx!!.startActivity(installIntent)
                }
            }
        }

        fun onPause() {
            if (TTS != null) {
                if (TTS!!.isSpeaking) {
                    TTS!!.stop()
                }
            }
        }

        fun onDestroy() {
            if (TTS != null) {
                if(TTS!!.isSpeaking) {
                    TTS!!.stop();
                }
                TTS!!.shutdown();
            }
        }
    }
}

private fun TextToSpeech?.speak(text: String) {

}

enum class PITCH {
    ROW {
        override fun index() = 1
        override val level: Float
            get() = 0.1F
    },
    NOMAL {
        override fun index() = 2
        override val level: Float
            get() = 1.0F
    },
    HIGH {
        override fun index() = 3
        override val level: Float
            get() = 2.0F
    };

    abstract fun index() : Int
    abstract val level : Float
}

enum class SPEED {
    ZERO {
        override fun index() = 1
        override val speed: Float
            get() = 0.1F
    },
    s1_0X {
        override fun index() = 2
        override val speed: Float
            get() = 1.0F
    },
    s1_5X {
        override fun index() = 3
        override val speed: Float
            get() = 1.5F
    },
    s2_0X {
        override fun index() = 4
        override val speed: Float
            get() = 2.0F
    };

    abstract fun index() : Int
    abstract val speed : Float
}