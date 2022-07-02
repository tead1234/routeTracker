package com.gojungparkjo.routetracker.model.tmappoi


import com.google.gson.annotations.SerializedName

data class NewAddressList(
    @SerializedName("newAddress")
    val newAddress: List<NewAddres>?
)