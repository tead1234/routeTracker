package com.gojungparkjo.routetracker.model.tmappoi


import com.google.gson.annotations.SerializedName

data class Poi(
    @SerializedName("collectionType")
    val collectionType: String?,
    @SerializedName("dataKind")
    val dataKind: String?,
    @SerializedName("desc")
    val desc: String?,
    @SerializedName("detailAddrname")
    val detailAddrname: String?,
    @SerializedName("detailBizName")
    val detailBizName: String?,
    @SerializedName("detailInfoFlag")
    val detailInfoFlag: String?,
    @SerializedName("evChargers")
    val evChargers: EvChargers?,
    @SerializedName("firstBuildNo")
    val firstBuildNo: String?,
    @SerializedName("firstNo")
    val firstNo: String?,
    @SerializedName("frontLat")
    val frontLat: String?,
    @SerializedName("frontLon")
    val frontLon: String?,
    @SerializedName("groupSubLists")
    val groupSubLists: GroupSubLists?,
    @SerializedName("id")
    val id: String?,
    @SerializedName("lowerAddrName")
    val lowerAddrName: String?,
    @SerializedName("lowerBizName")
    val lowerBizName: String?,
    @SerializedName("middleAddrName")
    val middleAddrName: String?,
    @SerializedName("middleBizName")
    val middleBizName: String?,
    @SerializedName("mlClass")
    val mlClass: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("navSeq")
    val navSeq: String?,
    @SerializedName("newAddressList")
    val newAddressList: NewAddressList?,
    @SerializedName("noorLat")
    val noorLat: String?,
    @SerializedName("noorLon")
    val noorLon: String?,
    @SerializedName("parkFlag")
    val parkFlag: String?,
    @SerializedName("pkey")
    val pkey: String?,
    @SerializedName("radius")
    val radius: String?,
    @SerializedName("roadName")
    val roadName: String?,
    @SerializedName("rpFlag")
    val rpFlag: String?,
    @SerializedName("secondBuildNo")
    val secondBuildNo: String?,
    @SerializedName("secondNo")
    val secondNo: String?,
    @SerializedName("upperAddrName")
    val upperAddrName: String?,
    @SerializedName("upperBizName")
    val upperBizName: String?,
    @SerializedName("zipCode")
    val zipCode: String?
){
    constructor(id:String?,name:String?,addr:String?,frontLat: String?,frontLon: String?,noorLat: String?,noorLon: String?):this(null,null,null,null,null,null,null,null,null,frontLat,frontLon,null,id,null,null,null,null,null,name,null,null,noorLat,noorLon,null,null,null,null,null,null,null,addr,null,null)
}