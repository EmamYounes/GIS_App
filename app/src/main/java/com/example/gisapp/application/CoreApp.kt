package com.egabi.digitalsharjah_services.application

import android.app.Application
import com.example.gisapp.SSLPinningHelper

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize SSL pinning
        val sslPinningHelper = SSLPinningHelper()
        sslPinningHelper.pinCertificate(applicationContext)
    }
}
