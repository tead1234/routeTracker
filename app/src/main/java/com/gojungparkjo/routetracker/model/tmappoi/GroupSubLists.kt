package com.gojungparkjo.routetracker.model.tmappoi


import com.google.gson.annotations.SerializedName

data class GroupSubLists(
    @SerializedName("groupSub")
    val groupSub: List<GroupSub>?
)