package com.bbq.livedatabusdemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import com.androidbbq.livedatabus.LiveDataBus

class TwoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_two)

        /*LiveDataBus.with<String>("haha").observe(this, {
            Log.i("MainActivity", " TwoActivity LiveDataBus  onCreate: $it")
        })*/
         LiveDataBus.with<String>("haha").observeStick(this, {
             Log.i("MainActivity", " TwoActivity LiveDataBus  onCreate: $it")
         })
        LiveDataBus222.with<String>("good").observe(this, {
            Log.i("MainActivity", "TwoActivity LiveData  onCreate: $it")
        })
    }

    fun setValue(view: View) {
        LiveDataBus.with<String>("haha").value = "todo"
        LiveDataBus.with<String>("haha").postValue("todo")
        LiveDataBus222.with<String>("good").value = "tiantiat"
    }
}