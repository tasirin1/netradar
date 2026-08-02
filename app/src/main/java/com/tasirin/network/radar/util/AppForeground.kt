package com.tasirin.network.radar.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/** Lacak apakah app sedang terlihat (foreground) — dipakai untuk notifikasi hasil. */
object AppForeground {
    @Volatile
    var isForeground: Boolean = true
        private set

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { isForeground = true }
            override fun onStop(owner: LifecycleOwner) { isForeground = false }
        })
    }
}
