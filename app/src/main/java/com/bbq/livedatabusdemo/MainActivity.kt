package com.bbq.livedatabusdemo

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import com.androidbbq.livedatabus.LiveDataBus

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        LiveDataBus.with<String>("haha").observe(this, {
            Log.i(TAG, "LiveDataBus  onCreate: $it")
        })

        LiveDataBus222.with<String>("good").observe(this, {
            Log.i(TAG, "LiveData  onCreate: $it")
        })
    }

    fun jumpTwo(view: View) {
        val intent = Intent(this, TwoActivity::class.java)
        startActivity(intent)
    }
}