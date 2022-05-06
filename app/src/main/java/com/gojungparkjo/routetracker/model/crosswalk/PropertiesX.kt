package com.gojungparkjo.routetracker.model.crosswalk


import com.google.gson.annotations.SerializedName

data class PropertiesX(
    @SerializedName("A004_KND_CDE")
    val a004KNDCDE: String?,
    @SerializedName("AW_SN_LENX_CDE")
    val aWSNLENXCDE: String?,
    @SerializedName("AW_SN_QUA")
    val aWSNQUA: Int?,
    @SerializedName("CAE_YMD")
    val cAEYMD: Any?,
    @SerializedName("CRS_MGRNU")
    val cRSMGRNU: String?,
    @SerializedName("CSS_CDE")
    val cSSCDE: Any?,
    @SerializedName("CTK_MGRNU")
    val cTKMGRNU: String?,
    @SerializedName("DONG_CDE")
    val dONGCDE: String?,
    @SerializedName("ESB_YMD")
    val eSBYMD: Any?,
    @SerializedName("EVE_CDE")
    val eVECDE: String?,
    @SerializedName("FRM_CDE")
    val fRMCDE: String?,
    @SerializedName("GU_CDE")
    val gUCDE: String?,
    @SerializedName("HISID")
    val hISID: Int?,
    @SerializedName("HOL")
    val hOL: Int?,
    @SerializedName("JIBUN")
    val jIBUN: String?,
    @SerializedName("MGRNU")
    val mGRNU: String,
    @SerializedName("MNG_AGEN")
    val mNGAGEN: Any?,
    @SerializedName("NW_PE_CDE")
    val nWPECDE: String?,
    @SerializedName("OD_PE_CDE")
    val oDPECDE: String?,
    @SerializedName("RN_CDE")
    val rNCDE: Any?,
    @SerializedName("ROD_GBN_CDE")
    val rODGBNCDE: String?,
    @SerializedName("SIXID")
    val sIXID: Any?,
    @SerializedName("STAT_CDE")
    val sTATCDE: String?,
    @SerializedName("TFC_BSS_CDE")
    val tFCBSSCDE: String?,
    @SerializedName("VEL")
    val vEL: Int?,
    @SerializedName("VIEW_CDE")
    val vIEWCDE: String?,
    @SerializedName("WORK_CDE")
    val wORKCDE: String?
)