package com.bbq.livedatabusdemo

import androidx.lifecycle.MutableLiveData

object LiveDataBus222 {

    private val mMapAll = HashMap<String, Any>()
    fun <T> with(key: String): MutableLiveData<T> {
        if (!mMapAll.containsKey(key)) {
            mMapAll[key] = MutableLiveData<T>()
        }
        return (mMapAll[key] as MutableLiveData<T>)
    }

}