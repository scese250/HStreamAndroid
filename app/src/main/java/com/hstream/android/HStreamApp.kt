package com.hstream.android

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class HStreamApp : Application() {

    companion object {
        var isUnlocked = false
    }

    override fun onCreate() {
        super.onCreate()

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // App entró en primer plano (Foreground)
                val prefs = getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE)
                val isPrivacyLocked = prefs.getBoolean("privacy_lock", false)

                if (isPrivacyLocked && !isUnlocked) {
                    val intent = Intent(this@HStreamApp, LockActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                // App fue a segundo plano (Background)
                isUnlocked = false
            }
        })
    }
}
