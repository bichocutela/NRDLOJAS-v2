package com.example

import android.app.Application
import android.util.Log
import com.example.data.CatalogCodeMigration
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

class MyApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var catalogCodeMigrationStarted = false

    override fun onCreate() {
        super.onCreate()
        CrashReporter.setup(this)
        try {
            com.example.data.FirebaseService.initialize(this)
            Log.d("MyApplication", "Firebase initialized manually")
            scheduleCatalogCodeMigration()
        } catch (e: Exception) {
            Log.e("MyApplication", "Firebase initialization failed", e)
        }
        try {
            com.example.util.UpdateNotificationWorker.schedule(this)
            Log.d("MyApplication", "Periodic update check scheduled")
        } catch (e: IllegalStateException) {
            Log.w("MyApplication", "Periodic update check not scheduled in this process", e)
        }
        try {
            com.example.util.PromotionNotificationWorker.schedule(this)
            Log.d("MyApplication", "Favorite store promotion check scheduled")
        } catch (e: IllegalStateException) {
            Log.w("MyApplication", "Favorite store promotion check not scheduled in this process", e)
        }
        Log.d("MyApplication", "Application started and CrashReporter setup")
    }

    /**
     * Aguarda uma sessão administrativa real do Firebase e aplica a tabela de
     * códigos silenciosamente. Usuários comuns nunca tentam escrever no catálogo.
     */
    private fun scheduleCatalogCodeMigration() {
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val email = auth.currentUser?.email?.lowercase(Locale.ROOT)
            val isManager = email == "admin@nrdlojas.com" || email == "mestre@nrdlojas.com"
            if (!isManager || catalogCodeMigrationStarted) return@addAuthStateListener

            catalogCodeMigrationStarted = true
            applicationScope.launch {
                val result = CatalogCodeMigration.applySilently()
                if (result.applied) {
                    Log.i(
                        "MyApplication",
                        "Catalog code migration applied: migrated=${result.migrated}, " +
                            "alreadyCorrect=${result.alreadyCorrect}, notFound=${result.notFound}"
                    )
                } else {
                    Log.d("MyApplication", "Catalog code migration skipped: ${result.message.orEmpty()}")
                }
            }
        }
    }
}
