package com.gojungparkjo.routetracker.model.pedestrianroad


import com.google.gson.annotations.SerializedName

data class PropertiesX(
    @SerializedName("H_DATE")
    val hDATE: Any?,
    @SerializedName("H_TYPE")
    val hTYPE: Any?,
    @SerializedName("H_WORK")
    val hWORK: Any?,
    @SerializedName("ID")
    val iD: Any?,
    @SerializedName("MGRNU")
    val mGRNU: String,
    @SerializedName("RD_PP_CDE")
    val rDPPCDE: Any?,
    @SerializedName("U_DATE")
    val uDATE: Any?,
    @SerializedName("UFID")
    val uFID: Any?,
    @SerializedName("U_ID")
    val uID: Any?,
    @SerializedName("U_TYPE")
    val uTYPE: Any?,
    @SerializedName("U_USERID")
    val uUSERID: Any?,
    @SerializedName("VIEW_CDE")
    val vIEWCDE: String?,
    @SerializedName("WID")
    val wID: Any?,
    @SerializedName("YN_CDE")
    val yNCDE: Any?
)