package com.example.gpstest.domain.ephemeris

import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.NavigationMessageFrame

/** 将同一 GPS 卫星的有效 LNAV 子帧 1/2/3 组装为可传播的广播星历。 */
class GpsLnavEphemerisStore {
    private val frames = mutableMapOf<Int, MutableMap<Int, ByteArray>>()

    fun add(frame: NavigationMessageFrame): GpsBroadcastEphemeris? {
        if (frame.constellation != Constellation.GPS ||
            frame.type != GPS_L1_CA_TYPE ||
            frame.status !in VALID_STATUSES ||
            frame.submessageId !in 1..3 ||
            frame.data.size != GPS_LNAV_SIZE_BYTES
        ) {
            return null
        }
        val bySubframe = frames.getOrPut(frame.svid) { mutableMapOf() }
        bySubframe[frame.submessageId] = frame.data.copyOf()
        val subframe1 = bySubframe[1] ?: return null
        val subframe2 = bySubframe[2] ?: return null
        val subframe3 = bySubframe[3] ?: return null
        return GpsLnavEphemerisParser.parse(frame.svid, subframe1, subframe2, subframe3)
    }

    fun clear() = frames.clear()

    private companion object {
        const val GPS_L1_CA_TYPE = 0x0101
        const val GPS_LNAV_SIZE_BYTES = 40
        val VALID_STATUSES = setOf(1, 2)
    }
}
