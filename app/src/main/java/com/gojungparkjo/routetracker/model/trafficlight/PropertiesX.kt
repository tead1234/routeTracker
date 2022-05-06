package com.gojungparkjo.routetracker.model.trafficlight


import com.google.gson.annotations.SerializedName

data class PropertiesX(
    @SerializedName("A057_KND_CDE")
    val a057KNDCDE: String?,
    @SerializedName("A062_MGRNU")
    val a062MGRNU: String?,
    @SerializedName("A062_POS")
    val a062POS: Any?,
    @SerializedName("ASN_DRN")
    val aSNDRN: Double?,
    @SerializedName("ASN_LENX_CDE")
    val aSNLENXCDE: String?,
    @SerializedName("ASN_MGRNU")
    val aSNMGRNU: String?,
    @SerializedName("BSNLP_KND_CDE")
    val bSNLPKNDCDE: Any?,
    @SerializedName("BSNLP_QUA")
    val bSNLPQUA: Int?,
    @SerializedName("CAE_YMD")
    val cAEYMD: String?,
    @SerializedName("CTK_MGRNU")
    val cTKMGRNU: String?,
    @SerializedName("ESB_YMD")
    val eSBYMD: String?,
    @SerializedName("EVE_CDE")
    val eVECDE: String?,
    @SerializedName("FB_CDE")
    val fBCDE: Any?,
    @SerializedName("FRM_CDE")
    val fRMCDE: String?,
    @SerializedName("HISID")
    val hISID: Int?,
    @SerializedName("MGRNU")
    val mGRNU: String,
    @SerializedName("MK_CPY")
    val mKCPY: Any?,
    @SerializedName("MNG_AGEN")
    val mNGAGEN: Any?,
    @SerializedName("SIXID")
    val sIXID: String?,
    @SerializedName("SNLP_KND_CDE")
    val sNLPKNDCDE: String?,
    @SerializedName("SNLP_QUA")
    val sNLPQUA: Int?,
    @SerializedName("SP_RN_CDE")
    val sPRNCDE: String?,
    @SerializedName("STAT_CDE")
    val sTATCDE: String?,
    @SerializedName("VIEW_CDE")
    val vIEWCDE: String?,
    @SerializedName("WORK_CDE")
    val wORKCDE: String?,
    @SerializedName("XCE")
    val xCE: Double?,
    @SerializedName("YCE")
    val yCE: Double?
)