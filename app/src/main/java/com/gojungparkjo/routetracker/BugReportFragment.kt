package com.gojungparkjo.routetracker

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.gojungparkjo.routetracker.databinding.FragmentBugReportBinding
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.model.ServerTimestamps
import com.google.firebase.ktx.Firebase
import com.naver.maps.geometry.LatLng

class BugReportFragment : DialogFragment() {

    private val TAG = "BugReportFragment"

    lateinit var binding: FragmentBugReportBinding
    var position: LatLng? = null

    val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        position = arguments?.get("position") as? LatLng
        Log.d(TAG, "onCreate: $position")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBugReportBinding.inflate(inflater)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.setCanceledOnTouchOutside(false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        position?.let {
            binding.locationTextView.text = "위도 ${it.latitude}\n경도 ${it.longitude}"
        }
        binding.sendButton.setOnClickListener { uploadBugReport() }
        binding.cancelButton.setOnClickListener { dismiss() }
    }

    fun uploadBugReport(){
        binding.editText.text
        val data = hashMapOf<String,Any?>("content" to binding.editText.text.toString(),"location" to position.toString(),"time" to FieldValue.serverTimestamp())
        db.collection("bugReport").document().set(data).addOnCompleteListener {
            if(it.isSuccessful){
                Toast.makeText(requireActivity(),"서버에 저장되었습니다.",Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }

    }

}