package com.kadvii.vault

import android.app.Application
import com.kadvii.vault.di.AppContainer
import com.kadvii.vault.di.DefaultAppContainer

class VaultApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
