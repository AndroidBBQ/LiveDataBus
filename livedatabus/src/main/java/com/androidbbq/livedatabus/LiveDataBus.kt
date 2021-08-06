package com.androidbbq.livedatabus

import java.util.concurrent.ConcurrentHashMap

object LiveDataBus {
    private val mBusMap: ConcurrentHashMap<Any, LiveBusData<Any>> by lazy {
        ConcurrentHashMap()
    }

    fun <T : Any> with(key: String): LiveBusData<T> {
        var liveBusData = mBusMap.get(key)
        if (liveBusData != null) return liveBusData as LiveBusData<T>
        synchronized(mBusMap) {
            liveBusData = mBusMap.get(key)
            if (liveBusData == null) {
                liveBusData = LiveBusData()
                mBusMap.put(key, liveBusData as LiveBusData)
            }
        }
        return liveBusData as LiveBusData<T>
    }

}