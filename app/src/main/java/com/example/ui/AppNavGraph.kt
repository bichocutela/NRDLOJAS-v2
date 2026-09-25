package com.example.ui

import android.util.Log
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.example.ui.theme.LocalGlassSoftStyle
import com.example.ui.theme.LocalExpressiveStyle
import com.example.ui.theme.glassSoftShadow
import com.example.ui.theme.expressiveShadow

private const val ADMIN_LOGIN_TIMEOUT_MS = 45_000L
private const val ADMIN_LOGIN_TAG = "AdminLogin"

@Composable
fun AppNavGraph(
    viewModel: MainViewModel,
    openAboutFromNotification: Boolean = false,
    openPromotionsFromNotification: Boolean = false,
    productCodeFromNotification: String? = null,
    sharedOrderPdfUri: String? = null,
    sharedOrderPdfRequestKey: Long = 0L,
    onSharedOrderPdfConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val nossaGenteApi = remember { com.example.data.NossaGenteApi(context) }
    val nossaGenteCredentialStore = remember { com.example.data.NossaGenteCredentialStore(context.applicationContext) }
    var profileEnabled by remember { mutableStateOf(nossaGenteCredentialStore.isProfileEnabled()) }
    var promotionsEnabled by remember { mutableStateOf(nossaGenteCredentialStore.isPromotionsEnabled()) }
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    val initialRole = remember(firebaseAuth) { managementRoleForEmail(firebaseAuth.currentUser?.email) }
    var isLoggedIn by remember { mutableStateOf(initialRole != null) }
    var userRole by remember { mutableStateOf(initialRole ?: "user") }
    val glassSoftStyle = LocalGlassSoftStyle.current
    val expressiveStyle = LocalExpressiveStyle.current
    val screenProfile = rememberNrdScreenProfile()
    val expressiveDrawerShape = RoundedCornerShape(
        topEnd = if (screenProfile.compact) 28.dp else 36.dp,
        bottomEnd = if (screenProfile.compact) 28.dp else 36.dp
    )
    val glassDrawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)

    DisposableEffect(firebaseAuth) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val authenticatedRole = managementRoleForEmail(auth.currentUser?.email)
            if (authenticatedRole != null) {
                isLoggedIn = true
                userRole = authenticatedRole
            } else {
                isLoggedIn = false
                userRole = "user"
            }
            scope.launch {
                com.example.util.FcmTopicSubscription.reconcileMasterUpdates(
                    isMaster = authenticatedRole == "mestre"
                )
            }
        }
        firebaseAuth.addAuthStateListener(listener)
        onDispose { firebaseAuth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(openAboutFromNotification) {
        if (openAboutFromNotification) {
            navController.navigate("about") { launchSingleTop = true }
        }
    }

    LaunchedEffect(openPromotionsFromNotification) {
        if (openPromotionsFromNotification) {
            navController.navigate(
                if (nossaGenteApi.hasSession() && promotionsEnabled) "promotions" else "promotions_login"
            ) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(sharedOrderPdfUri, sharedOrderPdfRequestKey, isLoggedIn, userRole) {
        if (sharedOrderPdfUri != null && isLoggedIn && userRole == "mestre") {
            navController.navigate("acp_consultation") {
                launchSingleTop = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .widthIn(max = if (screenProfile.compact) 312.dp else 352.dp)
                    .glassSoftShadow(glassDrawerShape)
                    .expressiveShadow(expressiveDrawerShape, 10.dp),
                drawerShape = when {
                    glassSoftStyle.enabled -> glassDrawerShape
                    expressiveStyle.enabled -> expressiveDrawerShape
                    else -> DrawerDefaults.shape
                },
                drawerContainerColor = when {
                    glassSoftStyle.enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
                    expressiveStyle.enabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                    else -> DrawerDefaults.modalContainerColor
                }
            ) {
                LoginDrawerContent(
                    viewModel = viewModel,
                    isLoggedIn = isLoggedIn,
                    userRole = userRole,
                    showMyProfile = nossaGenteApi.hasSession() && profileEnabled,
                    onLoginSuccess = { role ->
                        isLoggedIn = true
                        userRole = role
                        scope.launch { drawerState.close() }
                        when (role) {
                            "mestre" -> navController.navigate("mestre")
                            "admin" -> navController.navigate("admin")
                            "teste" -> navController.navigate("search")
                        }
                    },
                    onLogout = {
                        scope.launch { com.example.util.FcmTopicSubscription.reconcileMasterUpdates(isMaster = false) }
                        firebaseAuth.signOut()
                        isLoggedIn = false
                        userRole = "user"
                        scope.launch { drawerState.close() }
                        navController.navigate("search") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onGoToPromotions = {
                        scope.launch { drawerState.close() }
                        navController.navigate(if (nossaGenteApi.hasSession() && promotionsEnabled) "promotions" else "promotions_login")
                    },
                    onGoToMyPoint = {
                        scope.launch { drawerState.close() }
                        navController.navigate("my_profile") { launchSingleTop = true }
                    },
                    onGoToSettings = { scope.launch { drawerState.close() }; navController.navigate("settings") },
                    onGoToAcp = { scope.launch { drawerState.close() }; navController.navigate("acp_consultation") { launchSingleTop = true } },
                    onGoToAdmin = {
                        scope.launch { drawerState.close() }
                        navController.navigate(if (userRole == "mestre") "mestre" else "admin")
                    },
                    onGoToAbout = { scope.launch { drawerState.close() }; navController.navigate("about") },
                    onGoToDynamicTab = { tabId -> scope.launch { drawerState.close() }; navController.navigate("dynamic_tab/$tabId") }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = if (glassSoftStyle.enabled || expressiveStyle.enabled) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.background
            }
        ) { innerPadding ->
            NavHost(navController = navController, startDestination = "search", modifier = Modifier.padding(innerPadding)) {
                composable("dynamic_tab/{tabId}") { backStackEntry ->
                    val tabId = backStackEntry.arguments?.getString("tabId")?.toIntOrNull()
                    val dynamicTabs by viewModel.dynamicTabs.collectAsState()
                    val tab = dynamicTabs.find {
                        it.id == tabId && com.example.data.DynamicPageCodec.isVisible(it)
                    }
                    if (tab != null) {
                        DynamicTabScreen(tab = tab, onNavigateBack = { navController.popBackStack() })
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Este conteúdo não está disponível no momento.")
                        }
                    }
                }
                composable("search") {
                    SearchScreen(
                        viewModel = viewModel,
                        notificationProductCode = productCodeFromNotification,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        canQuickEditBanner = isLoggedIn && userRole == "mestre",
                        onQuickEditBanner = { themeKey ->
                            navController.navigate("mestre/banner/$themeKey") { launchSingleTop = true }
                        }
                    )
                }
                composable("admin") {
                    ProtectedManagementRoute(isLoggedIn, userRole, setOf("admin", "mestre"), { navController.navigateToSearch() }) {
                        AdminScreen(viewModel, onNavigateBack = { navController.popBackStack() })
                    }
                }
                composable("mestre") {
                    ProtectedManagementRoute(isLoggedIn, userRole, setOf("mestre"), { navController.navigateToSearch() }) {
                        MestreScreen(viewModel, { navController.navigate("admin") }, { navController.navigate("manage_tabs") }, { navController.navigate("manage_products") }, { navController.popBackStack() })
                    }
                }
                composable("mestre/banner/{themeKey}") { backStackEntry ->
                    val themeKey = backStackEntry.arguments?.getString("themeKey")
                    ProtectedManagementRoute(isLoggedIn, userRole, setOf("mestre"), { navController.navigateToSearch() }) {
                        MestreScreen(
                            viewModel = viewModel,
                            onNavigateToAdmin = { navController.navigate("admin") },
                            onNavigateToManageTabs = { navController.navigate("manage_tabs") },
                            onNavigateToManageProducts = { navController.navigate("manage_products") },
                            onNavigateBack = { navController.popBackStack() },
                            quickEditThemeKey = themeKey
                        )
                    }
                }
                composable("manage_tabs") {
                    ProtectedManagementRoute(isLoggedIn, userRole, setOf("mestre"), { navController.navigateToSearch() }) {
                        ManageTabsScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
                    }
                }
                composable("manage_products") {
                    ProtectedManagementRoute(isLoggedIn, userRole, setOf("mestre"), { navController.navigateToSearch() }) {
                        ManageProductsScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
                    }
                }
                composable("promotions_login") {
                    PromotionsLoginScreen(
                        api = nossaGenteApi,
                        onLoginSuccess = {
                            profileEnabled = nossaGenteCredentialStore.isProfileEnabled()
                            promotionsEnabled = true
                            nossaGenteCredentialStore.setPromotionsEnabled(true)
                            navController.navigate("promotions") { popUpTo("promotions_login") { inclusive = true }; launchSingleTop = true }
                        },
                        onNavigateBack = { navController.popBackStack() },
                        reuseExistingSession = promotionsEnabled,
                    )
                }
                composable("promotions") {
                    PromotionsScreen(
                        api = nossaGenteApi,
                        onNavigateBack = { navController.popBackStack() },
                        onRequireLogin = { navController.navigate("promotions_login") { popUpTo("promotions") { inclusive = true } } },
                        onLogout = {
                            promotionsEnabled = false
                            nossaGenteCredentialStore.setPromotionsEnabled(false)
                            if (!profileEnabled) nossaGenteApi.logout()
                            navController.navigate("promotions_login") { popUpTo("promotions") { inclusive = true }; launchSingleTop = true }
                        },
                        showReactivateProfile = !profileEnabled && nossaGenteApi.hasSession(),
                        onReactivateProfile = {
                            profileEnabled = true
                            nossaGenteCredentialStore.setProfileEnabled(true)
                        }
                    )
                }
                composable("my_point_login") {
                    PromotionsLoginScreen(
                        api = nossaGenteApi,
                        onLoginSuccess = {
                            profileEnabled = true
                            nossaGenteCredentialStore.setProfileEnabled(true)
                            navController.navigate("my_profile") { popUpTo("my_point_login") { inclusive = true }; launchSingleTop = true }
                        },
                        onNavigateBack = { navController.popBackStack() },
                        reuseExistingSession = false,
                        title = "Acesso ao Meu Perfil"
                    )
                }
                composable("my_profile") {
                    MyPointScreen(
                        api = nossaGenteApi,
                        onNavigateBack = { navController.popBackStack() },
                        onSignOut = {
                            profileEnabled = false
                            nossaGenteCredentialStore.setProfileEnabled(false)
                            if (!promotionsEnabled) nossaGenteApi.logout()
                            navController.navigate("search") { popUpTo("my_profile") { inclusive = true } }
                        }
                    )
                }
                composable("my_point") { LaunchedEffect(Unit) { navController.navigate("my_profile") { popUpTo("my_point") { inclusive = true } } } }
                composable("settings") { SettingsScreen(viewModel, onNavigateBack = { navController.popBackStack() }) }
                composable("acp_consultation") {
                    AcpConsultationScreen(
                        canConfigure = isLoggedIn && userRole in setOf("admin", "mestre"),
                        onNavigateBack = { navController.popBackStack() },
                        externalPdfUri = if (isLoggedIn && userRole == "mestre") sharedOrderPdfUri else null,
                        externalPdfRequestKey = sharedOrderPdfRequestKey,
                        onExternalPdfConsumed = onSharedOrderPdfConsumed
                    )
                }
                composable("about") { AboutScreen(onNavigateBack = { navController.popBackStack() }) }
            }
        }
    }
}

internal fun managementRoleForEmail(email: String?): String? = when (email?.trim()?.lowercase()) {
    "admin@nrdlojas.com" -> "admin"
    "mestre@nrdlojas.com" -> "mestre"
    else -> null
}

@Composable
private fun ProtectedManagementRoute(isLoggedIn: Boolean, userRole: String, allowedRoles: Set<String>, onDenied: () -> Unit, content: @Composable () -> Unit) {
    if (isLoggedIn && userRole in allowedRoles) content() else LaunchedEffect(isLoggedIn, userRole) { onDenied() }
}

private fun androidx.navigation.NavHostController.navigateToSearch() {
    navigate("search") { popUpTo(graph.findStartDestination().id) { inclusive = false }; launchSingleTop = true }
}

@Composable
fun LoginDrawerContent(
    viewModel: MainViewModel,
    isLoggedIn: Boolean,
    userRole: String,
    showMyProfile: Boolean,
    onLoginSuccess: (String) -> Unit,
    onLogout: () -> Unit,
    onGoToAdmin: () -> Unit,
    onGoToPromotions: () -> Unit,
    onGoToMyPoint: () -> Unit,
    onGoToSettings: () -> Unit,
    onGoToAbout: () -> Unit,
    onGoToDynamicTab: (Int) -> Unit,
    onGoToAcp: () -> Unit = {}
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loginStatus by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var loginExpanded by remember { mutableStateOf(false) }
    val activeCategoryNames by viewModel.activeCategoryNames.collectAsState()
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    com.example.util.UpdateAvailabilityState.initialize(context)
    val drawerUpdateAvailable by com.example.util.UpdateAvailabilityState.available.collectAsState()

    LaunchedEffect(isLoggedIn, userRole) {
        com.example.util.UpdateAvailabilityState.clearIfCurrent(context)
        if (isLoggedIn && userRole == "mestre") {
            com.example.util.UpdateAvailabilityState.refresh(context)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showMyProfile) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Meu Perfil", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onGoToMyPoint, modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("Meu Perfil") }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))
        }
        if (!isLoggedIn) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { loginExpanded = !loginExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Login", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Icon(
                        imageVector = if (loginExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (loginExpanded) "Recolher login" else "Expandir login",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (loginExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Usuário") }, enabled = !isLoading, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), enabled = !isLoading, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                onClick = {
                    if (isLoading) return@Button
                    val inputUser = username.trim().lowercase()
                    if ((inputUser == "admin" || inputUser == "mestre") && password == "nrdlojas") {
                        val email = if (inputUser == "admin") "admin@nrdlojas.com" else "mestre@nrdlojas.com"
                        val passwordSnapshot = password
                        isLoading = true
                        loginStatus = null
                        scope.launch {
                            try {
                                val auth = FirebaseAuth.getInstance()
                                val currentRole = managementRoleForEmail(auth.currentUser?.email)
                                val authenticatedRole = if (currentRole == inputUser) {
                                    currentRole
                                } else {
                                    val result = withTimeout(ADMIN_LOGIN_TIMEOUT_MS) {
                                        auth.signInWithEmailAndPassword(email, passwordSnapshot).await()
                                    }
                                    managementRoleForEmail(result.user?.email)
                                }

                                if (authenticatedRole == inputUser) {
                                    Log.d(ADMIN_LOGIN_TAG, "Sessão Firebase administrativa validada com sucesso")
                                    password = ""
                                    loginStatus = null
                                    onLoginSuccess(authenticatedRole)
                                } else {
                                    auth.signOut()
                                    loginStatus = "Usuário sem acesso administrativo"
                                }
                            } catch (_: TimeoutCancellationException) {
                                loginStatus = "A autenticação demorou demais. Verifique sua conexão e tente novamente."
                            } catch (_: FirebaseNetworkException) {
                                loginStatus = "Sem conexão com o serviço de login. Verifique sua internet e tente novamente."
                            } catch (_: FirebaseTooManyRequestsException) {
                                loginStatus = "Muitas tentativas seguidas. Aguarde um instante e tente novamente."
                            } catch (error: Exception) {
                                Log.e(ADMIN_LOGIN_TAG, "Falha ao autenticar sessão administrativa", error)
                                loginStatus = "Não foi possível autenticar o acesso administrativo. Tente novamente."
                            } finally {
                                isLoading = false
                            }
                        }
                    } else {
                        isLoading = false
                        loginStatus = "Usuário ou senha incorretos"
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(if (isLoading) "Autenticando..." else "Entrar") }
                if (loginStatus != null) { Spacer(modifier = Modifier.height(8.dp)); Text(loginStatus!!, color = MaterialTheme.colorScheme.error) }
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(if (userRole == "mestre" || userRole == "admin") "Administrador" else "Usuário", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            if (userRole == "mestre" || userRole == "admin") {
                Button(onClick = onGoToAdmin, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(if (userRole == "mestre") "Acessar Painel Mestre" else "Acessar Painel Administrativo") }
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedButton(onClick = { loginStatus = null; onLogout() }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Sair") }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(10.dp))

        val dynamicTabs by viewModel.dynamicTabs.collectAsState()
        val supportedDynamicTabs = dynamicTabs.filter { tab ->
            val supported = tab.type == "text" ||
                tab.type == "image" ||
                com.example.data.DynamicPageCodec.isPage(tab.content)
            supported && com.example.data.DynamicPageCodec.isVisible(tab)
        }
        if (supportedDynamicTabs.isNotEmpty()) {
            Text("Páginas e Cursos", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(4.dp))
            supportedDynamicTabs.sortedWith(compareBy<com.example.data.DynamicTab> { it.displayOrder }.thenBy { it.id }).forEach { tab ->
                TextButton(onClick = { onGoToDynamicTab(tab.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)) { Text(tab.title) }
            }
            Spacer(modifier = Modifier.height(8.dp)); HorizontalDivider(); Spacer(modifier = Modifier.height(8.dp))
        }

        Text("Categorias", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(6.dp))
        activeCategoryNames.forEach { categoryName ->
            CategoryItem(categoryName, viewModel, expandedCategory == categoryName) { expandedCategory = if (expandedCategory == categoryName) null else categoryName }
        }

        Spacer(modifier = Modifier.height(12.dp)); HorizontalDivider(); Spacer(modifier = Modifier.height(10.dp))
        Button(onClick = onGoToAcp, modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("Consultar Preços") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onGoToPromotions, modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("Promoções") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onGoToSettings, modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("Configurações") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onGoToAbout, modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("Sobre") }
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Versão instalada: v${com.example.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (drawerUpdateAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onGoToAbout,
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Text("Existe Atualização", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryItem(category: String, viewModel: MainViewModel, isExpanded: Boolean, onExpandToggle: () -> Unit) {
    val productsFlow = remember(category) { viewModel.getProductsByCategory(category) }
    val products by if (isExpanded) productsFlow.collectAsState(initial = emptyList()) else remember { mutableStateOf(emptyList()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onExpandToggle, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(category, style = MaterialTheme.typography.titleSmall)
                Icon(if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = if (isExpanded) "Recolher" else "Expandir", modifier = Modifier.size(22.dp))
            }
        }
        if (isExpanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 4.dp)) {
                if (products.isEmpty()) Text("Carregando...", style = MaterialTheme.typography.bodySmall)
                else products.forEach { product ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(product.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(product.code, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
