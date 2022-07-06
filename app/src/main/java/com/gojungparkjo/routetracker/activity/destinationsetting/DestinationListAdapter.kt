package com.gojungparkjo.routetracker.activity.destinationsetting

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gojungparkjo.routetracker.databinding.ItemPoiBinding
import com.gojungparkjo.routetracker.model.tmappoi.Poi

class DestinationListAdapter: ListAdapter<Poi, DestinationListAdapter.PoiViewHolder>(diffUtil) {

    private val TAG = "DestinationListAdapter"


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PoiViewHolder=
        PoiViewHolder(ItemPoiBinding.inflate(LayoutInflater.from(parent.context),parent,false))

    override fun onBindViewHolder(holder: PoiViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    inner class PoiViewHolder(private val binding: ItemPoiBinding) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(poi:Poi){
            binding.nameTextView.text = poi.name
            binding.addressTextView.text = "${poi.upperAddrName?:""} ${poi.middleAddrName?:""} ${poi.lowerAddrName?:""} ${poi.detailAddrname?:""}"
            binding.root.setOnClickListener {
                Log.d(TAG, "bind: ${poi.frontLat}")
            }
        }

    }

    companion object{
        val diffUtil = object :DiffUtil.ItemCallback<Poi>(){
            override fun areItemsTheSame(oldItem: Poi, newItem: Poi) =
                oldItem.id == newItem.id


            override fun areContentsTheSame(oldItem: Poi, newItem: Poi)=
                oldItem == newItem
        }
    }
}