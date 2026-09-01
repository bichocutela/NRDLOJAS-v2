package com.example

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay


import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity

import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.AppearanceSettings
import com.example.data.NossaGenteApi
import com.example.data.NossaGenteCodeLookupResult
import com.example.data.ProductRepository
import com.example.data.StoreCatalog
import com.example.data.UserPreferences
import com.example.data.dataStore
import com.example.ui.AppNavGraph
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.GlassSoftBackground
import com.example.ui.theme.LocalGlassSoftStyle

import com.example.util.FcmTopicSubscription
import com.example.util.NotificationHelper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    private val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "products.db"
        ).fallbackToDestructiveMigration().build()
    }
    
    private val repository by lazy {
        ProductRepository(db.productDao(), db.dynamicTabDao())
    }
    private val userPreferences by lazy {
        UserPreferences(applicationContext)
    }

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(repository, userPreferences)
    }

    private var openAboutFromNotification by mutableStateOf(false)
    private var openPromotionsFromNotification by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        openAboutFromNotification = shouldOpenAbout(intent)
        openPromotionsFromNotification = shouldOpenPromotions(intent)
        
        com.example.data.FirebaseService.initialize(this)

        lifecycleScope.launch {
            val notificationsEnabled = userPreferences.notificationsEnabled.first()
            FcmTopicSubscription.reconcile(notificationsEnabled)
            FcmTopicSubscription.reconcileMasterUpdates(
                isMaster = FcmTopicSubscription.isMasterAuthenticated(),
                notificationsEnabled = notificationsEnabled
            )
        }
        val crashLog = CrashReporter.getCrashLog(this)
        if (crashLog != null) {
            Thread {
                try {
                    val encoded = java.net.URLEncoder.encode(crashLog, "UTF-8")
                    val url = java.net.URL("http://10.0.2.2:8081/?crash=$encoded")
                    url.openConnection().getInputStream().close()
                } catch (e: Exception) { }
            }.start()
            CrashReporter.clearCrashLog(this)
            setContent {
            
                androidx.compose.material3.MaterialTheme {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                androidx.compose.material3.Text("App crashed previously with error:\n\n$crashLog", color = Color.Red)
                            }
                        }
                    }
                }
            }
            return
        }


        NotificationHelper.createNotificationChannel(this)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.app.ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
        
        enableEdgeToEdge()
        setContent {

            val fontScale by userPreferences.fontScale.collectAsState(initial = 1.0f)
            val largeText by userPreferences.largeText.collectAsState(initial = false)
            val appTheme by userPreferences.appTheme.collectAsState(initial = "multicolor")
            val appearanceMode by userPreferences.appearanceMode.collectAsState(initial = "system")
            val glassAccentColor by userPreferences.glassAccentColor.collectAsState(initial = "multicolor")
            val glassTransparency by userPreferences.glassTransparency.collectAsState(initial = 0.55f)
            val glassType by userPreferences.glassType.collectAsState(initial = "soft")
            val hasLocalThemeChoice by applicationContext.dataStore.data
                .map { preferences -> preferences[UserPreferences.APP_THEME] != null }
                .collectAsState(initial = false)
            val hasLocalAppearanceChoice by applicationContext.dataStore.data
                .map { preferences -> preferences[UserPreferences.APPEARANCE_MODE] != null }
                .collectAsState(initial = false)
            val remoteAppearance by com.example.data.FirebaseService.observeAppearanceSettings()
                .collectAsState(initial = AppearanceSettings())
            val effectiveAppTheme = if (remoteAppearance.globalOverrideEnabled && !hasLocalThemeChoice) {
                remoteAppearance.theme
            } else {
                appTheme
            }
            val effectiveAppearanceMode = if (remoteAppearance.globalOverrideEnabled && !hasLocalAppearanceChoice) {
                remoteAppearance.appearanceMode
            } else {
                appearanceMode
            }
            val latestFirebase by viewModel.latestProduct.collectAsState(null)
            val latestLocal by viewModel.latestProductLocal.collectAsState(null)
            val lastNotifiedCode by userPreferences.lastNotifiedProductCode.collectAsState("___LOADING___")
            val context = androidx.compose.ui.platform.LocalContext.current
            

            val currentDensity = LocalDensity.current
            val customDensity = androidx.compose.ui.unit.Density(
                density = currentDensity.density,
                fontScale = currentDensity.fontScale * fontScale * if (largeText) 1.15f else 1.0f
            )


            CompositionLocalProvider(LocalDensity provides customDensity) {
                val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
                LaunchedEffect(Unit) {
                    viewModel.syncMessage.collect { msg ->
                        snackbarHostState.showSnackbar(msg)
                    }
                }

                // Diagnóstico temporário para validar se o Nossa Gente devolve EAN/GTIN.
                // Executa somente no Mestre e uma única vez para este código/revisão.
                LaunchedEffect(Unit) {
                    if (!FcmTopicSubscription.isMasterAuthenticated()) return@LaunchedEffect
                    val diagnostics = applicationContext.getSharedPreferences(
                        "nrd_nossa_gente_diagnostics",
                        MODE_PRIVATE
                    )
                    val diagnosticKey = "lookup_${TEST_NOSSA_GENTE_CODE}_r1"
                    if (diagnostics.getBoolean(diagnosticKey, false)) return@LaunchedEffect

                    val diagnosticApi = NossaGenteApi(applicationContext)
                    repeat(120) {
                        if (!diagnosticApi.hasSession()) {
                            delay(5_000)
                            return@repeat
                        }

                        when (val lookup = diagnosticApi.lookupPromotionCode(TEST_NOSSA_GENTE_CODE)) {
                            is NossaGenteCodeLookupResult.Found -> {
                                val match = lookup.matches.first()
                                val storeLabel = match.storeCode
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let(StoreCatalog::nameFor)
                                    ?: "loja não informada"
                                val price = match.offerPrice ?: match.regularPrice ?: "preço não informado"
                                val internalCode = match.internalCode.takeIf { it.isNotBlank() } ?: "não informado"
                                val name = match.name.takeIf { it.isNotBlank() } ?: "produto sem nome"
                                val extraMatches = (lookup.matches.size - 1).coerceAtLeast(0)
                                val suffix = if (extraMatches > 0) " (+$extraMatches resultado(s))" else ""
                                diagnostics.edit().putBoolean(diagnosticKey, true).apply()
                                snackbarHostState.showSnackbar(
                                    message = "Nossa Gente encontrou $TEST_NOSSA_GENTE_CODE no campo ${match.matchedField}: $name | cód. interno $internalCode | $storeLabel | $price$suffix",
                                    withDismissAction = true,
                                    duration = androidx.compose.material3.SnackbarDuration.Long
                                )
                                return@LaunchedEffect
                            }
                            is NossaGenteCodeLookupResult.NotFound -> {
                                diagnostics.edit().putBoolean(diagnosticKey, true).apply()
                                snackbarHostState.showSnackbar(
                                    message = "Nossa Gente não retornou o código ${lookup.queriedCode} nos dados de promoções consultados.",
                                    withDismissAction = true,
                                    duration = androidx.compose.material3.SnackbarDuration.Long
                                )
                                return@LaunchedEffect
                            }
                            NossaGenteCodeLookupResult.Unauthorized -> {
                                delay(5_000)
                            }
                            is NossaGenteCodeLookupResult.Error -> {
                                snackbarHostState.showSnackbar(
                                    message = "Teste de código no Nossa Gente: ${lookup.message}",
                                    withDismissAction = true,
                                    duration = androidx.compose.material3.SnackbarDuration.Long
                                )
                                return@LaunchedEffect
                            }
                        }
                    }
                }

                MyApplicationTheme(
                    appTheme = effectiveAppTheme,
                    appearanceMode = effectiveAppearanceMode,
                    glassAccentColor = glassAccentColor,
                    glassTransparency = glassTransparency,
                    glassType = glassType
                ) {

                var showSplash by remember { mutableStateOf(true) }
                
                LaunchedEffect(Unit) {
                    delay(1500)
                    showSplash = false
                }
                
                GlassSoftBackground(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (LocalGlassSoftStyle.current.enabled) Color.Transparent else MaterialTheme.colorScheme.background
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                        AppNavGraph(
                            viewModel = viewModel,
                            openAboutFromNotification = openAboutFromNotification,
                            openPromotionsFromNotification = openPromotionsFromNotification
                        )

                        androidx.compose.material3.SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                        
                        AnimatedVisibility(
                            visible = showSplash,
                            enter = fadeIn(animationSpec = tween(500)),
                            exit = fadeOut(animationSpec = tween(500))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (LocalGlassSoftStyle.current.enabled) Color.Transparent
                                        else Color.White
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                coil.compose.AsyncImage(
                                    model = R.drawable.splash_logo,
                                    contentDescription = "Logo",
                                    modifier = Modifier.size(150.dp)
                                )
                        }
                    }
                }
                }
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (shouldOpenAbout(intent)) {
            openAboutFromNotification = true
        }
        if (shouldOpenPromotions(intent)) {
            openPromotionsFromNotification = true
        }
    }

    private fun shouldOpenAbout(intent: Intent?): Boolean {
        return intent?.getBooleanExtra(EXTRA_OPEN_ABOUT, false) == true ||
            intent?.getStringExtra("type") == "APP_UPDATE"
    }

    private fun shouldOpenPromotions(intent: Intent?): Boolean {
        return intent?.getBooleanExtra(EXTRA_OPEN_PROMOTIONS, false) == true ||
            intent?.getStringExtra("type") == "PROMOTION_UPDATED"
    }

    companion object {
        const val EXTRA_OPEN_ABOUT = "open_about"
        const val EXTRA_OPEN_PROMOTIONS = "open_promotions"
        const val EXTRA_UPDATE_TAG = "update_tag"
        private const val TEST_NOSSA_GENTE_CODE = "7898919411900"
    }
}