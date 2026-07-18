package com.example.gpstest.domain.model

/** Android GNSS 导航电文的一帧原始数据。 */
data class NavigationMessageFrame(
    val constellation: Constellation,
    val svid: Int,
    val type: Int,
    val status: Int,
    val messageId: Int,
    val submessageId: Int,
    val data: ByteArray,
    val timestampMs: Long,
) {
    val hexData: String
        get() = data.joinToString(separator = " ") { byte -> "%02X".format(byte.toInt() and 0xFF) }
}
