package com.gojungparkjo.routetracker.activity.destinationsetting

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import com.gojungparkjo.routetracker.data.TmapPoiRepository
import com.gojungparkjo.routetracker.databinding.ActivityDestinationSettingBinding
import kotlinx.coroutines.*

class DestinationSettingActivity : AppCompatActivity() {

    private val TAG = "DestinationSettingActivity"

    lateinit var binding: ActivityDestinationSettingBinding
    lateinit var adapter: DestinationListAdapter

    private val tmapPoiRepository = TmapPoiRepository()

    private val job = Job()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDestinationSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.poiRecyclerView.layoutManager = LinearLayoutManager(this,LinearLayoutManager.VERTICAL,false)
        binding.poiRecyclerView.adapter = DestinationListAdapter().also { adapter = it }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                Log.d(TAG, "onQueryTextSubmit: ")
                CoroutineScope(Dispatchers.IO + job).launch {
                    val poiList = tmapPoiRepository.getPoiQueryResult(query ?: "")
                    withContext(Dispatchers.Main){
                        adapter.submitList(poiList)
                    }
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                Log.d(TAG, "onQueryTextChange: ")
                return true
            }
        })

    }

}