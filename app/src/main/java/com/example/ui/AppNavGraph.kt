package com.example.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AppNavGraph(viewModel: MainViewModel, openAboutFromNotification: Boolean = false) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val nossaGenteApi = remember { com.example.data.NossaGenteApi(context) }
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    val initialRole = remember(firebaseAuth) { managementRoleForEmail(firebaseAuth.currentUser?.email) }
    var isLoggedIn by remember { mutableStateOf(initialRole != null) }
    var userRole by remember { mutableStateOf(initialRole ?: "user") }
    val drawerAppTheme by viewModel.userPreferences.appTheme.collectAsState(initial = "multicolor")
    val isGlassDrawer = drawerAppTheme.trim().lowercase() == "glass"

    DisposableEffect(firebaseAuth) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val authenticatedRole = managementRoleForEmail(auth.currentUser?.email)
            isLoggedIn = authenticatedRole != null
            userRole = authenticatedRole ?: "user"
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
            navController.navigate("about") {
                launchSingleTop = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = if (isGlassDrawer) Color.Transparent else MaterialTheme.colorScheme.surface
            ) {
                LoginDrawerContent(
                    viewModel = viewModel,
                    isLoggedIn = isLoggedIn,
                    userRole = userRole,
                    onLoginSuccess = { role ->
                        isLoggedIn = true
                        userRole = role
                        scope.launch { drawerState.close() }
                        if (role == "mestre") {
                            navController.navigate("mestre")
                        } else if (role == "admin") {
                            navController.navigate("admin")
                        } else if (role == "teste") {
                            navController.navigate("search")
                        }
                    },
                    onLogout = {
                        scope.launch {
                            com.example.util.FcmTopicSubscription.reconcileMasterUpdates(isMaster = false)
                        }
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
                        navController.navigate(if (nossaGenteApi.hasSession()) "promotions" else "promotions_login")
                    },
                    onGoToSettings = { scope.launch { drawerState.close() }; navController.navigate("settings") },
                    onGoToAdmin = {
                        scope.launch { drawerState.close() }
                        if (userRole == "mestre") {
                            navController.navigate("mestre")
                        } else {
                            navController.navigate("admin")
                        }
                    },
                    onGoToAbout = { scope.launch { drawerState.close() }; navController.navigate("about") },
                    onGoToDynamicTab = { tabId ->
                        scope.launch { drawerState.close() }
                        navController.navigate("dynamic_tab/$tabId")
                    }
                )
            }
        }
    ) {
        Scaffold(
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "search",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("dynamic_tab/{tabId}") { backStackEntry ->
                    val tabId = backStackEntry.arguments?.getString("tabId")?.toIntOrNull()
                    val dynamicTabs by viewModel.dynamicTabs.collectAsState()
                    val tab = dynamicTabs.find { it.id == tabId }
                    if (tab != null) {
                        DynamicTabScreen(tab = tab, onNavigateBack = { navController.popBackStack() })
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Aba não encontrada.")
                        }
                    }
                }
                composable("search") {
                    SearchScreen(viewModel, onOpenDrawer = { scope.launch { drawerState.open() } })
                }
                composable("assistant") {
                    AssistantScreen(viewModel)
                }
                composable("admin") {
                    ProtectedManagementRoute(
                        isLoggedIn = isLoggedIn,
                        userRole = userRole,
                        allowedRoles = setOf("admin", "mestre"),
                        onDenied = { navController.navigateToSearch() }
                    ) {
                        AdminScreen(viewModel, onNavigateBack = {
                            navController.popBackStack()
                        })
                    }
                }
                composable("mestre") {
                    ProtectedManagementRoute(
                        isLoggedIn = isLoggedIn,
                        userRole = userRole,
                        allowedRoles = setOf("mestre"),
                        onDenied = { navController.navigateToSearch() }
                    ) {
                        MestreScreen(
                            viewModel = viewModel,
                            onNavigateToAdmin = { navController.navigate("admin") },
                            onNavigateToManageTabs = { navController.navigate("manage_tabs") },
                            onNavigateToManageProducts = { navController.navigate("manage_products") },
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
                composable("manage_tabs") {
                    ProtectedManagementRoute(
                        isLoggedIn = isLoggedIn,
                        userRole = userRole,
                        allowedRoles = setOf("mestre"),
                        onDenied = { navController.navigateToSearch() }
                    ) {
                        ManageTabsScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
                    }
                }
                composable("manage_products") {
                    ProtectedManagementRoute(
                        isLoggedIn = isLoggedIn,
                        userRole = userRole,
                        allowedRoles = setOf("mestre"),
                        onDenied = { navController.navigateToSearch() }
                    ) {
                        ManageProductsScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
                    }
                }
                composable("promotions_login") {
                    PromotionsLoginScreen(
                        api = nossaGenteApi,
                        onLoginSuccess = {
                            navController.navigate("promotions") {
                                popUpTo("promotions_login") { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("promotions") {
                    PromotionsScreen(
                        api = nossaGenteApi,
                        onNavigateBack = { navController.popBackStack() },
                        onRequireLogin = {
                            navController.navigate("promotions_login") {
                                popUpTo("promotions") { inclusive = true }
                            }
                        },
                        onLogout = {
                            nossaGenteApi.logout()
                            navController.navigate("promotions_login") {
                                popUpTo("promotions") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable("settings") {
                    SettingsScreen(viewModel, onNavigateBack = {
                        navController.popBackStack()
                    })
                }
                composable("about") {
                    AboutScreen(onNavigateBack = {
                        navController.popBackStack()
                    })
                }
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
private fun ProtectedManagementRoute(
    isLoggedIn: Boolean,
    userRole: String,
    allowedRoles: Set<String>,
    onDenied: () -> Unit,
    content: @Composable () -> Unit
) {
    if (isLoggedIn && userRole in allowedRoles) {
        content()
    } else {
        LaunchedEffect(isLoggedIn, userRole) { onDenied() }
    }
}

private fun androidx.navigation.NavHostController.navigateToSearch() {
    navigate("search") {
        popUpTo(graph.findStartDestination().id) { inclusive = false }
        launchSingleTop = true
    }
}

@Composable
fun LoginDrawerContent(
    viewModel: MainViewModel,
    isLoggedIn: Boolean,
    userRole: String,
    onLoginSuccess: (String) -> Unit,
    onLogout: () -> Unit,
    onGoToAdmin: () -> Unit,
    onGoToPromotions: () -> Unit,
    onGoToSettings: () -> Unit,
    onGoToAbout: () -> Unit,
    onGoToDynamicTab: (Int) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loginStatus by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val categories by viewModel.productsCountByCategory.collectAsState()
    val activeCategoryNames by viewModel.activeCategoryNames.collectAsState()
    val appTheme by viewModel.userPreferences.appTheme.collectAsState(initial = "multicolor")
    val glassAccentColor by viewModel.userPreferences.glassAccentColor.collectAsState(initial = "multicolor")
    val glassTransparency by viewModel.userPreferences.glassTransparency.collectAsState(initial = 0.55f)
    val glassType by viewModel.userPreferences.glassType.collectAsState(initial = "soft")
    val sharedGlassStyle = rememberGlassVisualStyle(viewModel)
    val isMulticolorTheme = appTheme.trim().lowercase() == "multicolor"
    val isGlassTheme = appTheme.trim().lowercase() == "glass"
    val multicolorSessionColors = remember {
        listOf(
            Color(0xFFE5252A), Color(0xFF2E9D44), Color(0xFFF28C18),
            Color(0xFF2474D2), Color(0xFFC99A14)
        ).shuffled()
    }
    val glassSessionColors = remember(glassAccentColor) {
        when (glassAccentColor) {
            "red" -> listOf(Color(0xFFE5252A), Color(0xFFFF8A8D), Color(0xFFFFD5D6))
            "green" -> listOf(Color(0xFF2E9D44), Color(0xFF83D69A), Color(0xFFD7F2DF))
            "orange" -> listOf(Color(0xFFF28C18), Color(0xFFFFBA68), Color(0xFFFFE1BC))
            "blue" -> listOf(Color(0xFF2474D2), Color(0xFF75ACEA), Color(0xFFD4E7FA))
            "gold" -> listOf(Color(0xFFC99A14), Color(0xFFE6C45E), Color(0xFFF6E8B7))
            else -> multicolorSessionColors
        }
    }
    val drawerAccentColors = if (isGlassTheme) glassSessionColors else multicolorSessionColors
    val sessionAccent = remember(multicolorSessionColors) { multicolorSessionColors.first() }
    val glassAccent = remember(glassSessionColors) { glassSessionColors.first() }
    val drawerAccent = if (isGlassTheme) glassAccent else sessionAccent
    val multicolorBrush = remember(drawerAccent) { Brush.linearGradient(listOf(drawerAccent, drawerAccent)) }
    val useGradientDrawer = false
    val isDarkGlass = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val glassSurfaceAlpha = when (glassType) {
        "frosted" -> (0.94f - glassTransparency * 0.48f).coerceIn(0.44f, 0.84f)
        "crystal" -> (0.76f - glassTransparency * 0.38f).coerceIn(0.28f, 0.66f)
        else -> (0.86f - glassTransparency * 0.44f).coerceIn(0.36f, 0.76f)
    }
    val drawerGlassBrush = remember(drawerAccent, isDarkGlass) {
        Brush.verticalGradient(
            if (isDarkGlass) {
                listOf(
                    Color(0xFF0C0F14),
                    drawerAccent.copy(alpha = 0.38f),
                    Color(0xFF11151B)
                )
            } else {
                listOf(
                    drawerAccent.copy(alpha = 0.18f),
                    Color.White.copy(alpha = 0.94f),
                    drawerAccent.copy(alpha = 0.10f)
                )
            }
        )
    }
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isGlassTheme) Modifier
                    .background(sharedGlassStyle.fill.copy(alpha = sharedGlassStyle.alpha), RoundedCornerShape(28.dp))
                    .border(1.dp, sharedGlassStyle.border, RoundedCornerShape(28.dp))
                else Modifier
            )
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isLoggedIn) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Login",
                style = if (useGradientDrawer) {
                    MaterialTheme.typography.headlineMedium.merge(TextStyle(brush = multicolorBrush))
                } else MaterialTheme.typography.headlineMedium,
                color = if (isMulticolorTheme || isGlassTheme) drawerAccent else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuário") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (isLoading) return@Button
                    isLoading = true
                    val inputUser = username.trim().lowercase()
                    scope.launch {
                        try {
                            val email = when (inputUser) {
                                "admin" -> "admin@nrdlojas.com"
                                "mestre" -> "mestre@nrdlojas.com"
                                else -> null
                            }
                            if (email != null && password.isNotBlank()) {
                                val auth = FirebaseAuth.getInstance()
                                auth.signInWithEmailAndPassword(email, password).await()
                                val authenticatedRole = managementRoleForEmail(auth.currentUser?.email)
                                if (authenticatedRole != null) {
                                    password = ""
                                    loginStatus = null
                                    onLoginSuccess(authenticatedRole)
                                } else {
                                    auth.signOut()
                                    loginStatus = "Usuário sem acesso administrativo"
                                }
                            } else {
                                loginStatus = "Usuário ou senha incorretos"
                            }
                        } catch (_: Exception) {
                            loginStatus = "Usuário ou senha incorretos"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLoading) "Autenticando..." else "Entrar")
            }
            if (loginStatus != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = loginStatus!!,
                    color = if (loginStatus?.startsWith("Login") == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        } else {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (userRole == "mestre" || userRole == "admin") "Administrador" else "Usuário",
                style = if (useGradientDrawer) {
                    MaterialTheme.typography.headlineMedium
                } else MaterialTheme.typography.headlineMedium,
                color = if (isMulticolorTheme || isGlassTheme) drawerAccent else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (userRole == "mestre" || userRole == "admin") {
                Button(
                    onClick = onGoToAdmin,
                    modifier = if (useGradientDrawer) {
                        Modifier.fillMaxWidth().background(multicolorBrush, RoundedCornerShape(28.dp))
                    } else Modifier.fillMaxWidth(),
                    colors = if (useGradientDrawer) {
                        ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White)
                    } else ButtonDefaults.buttonColors()
                ) {
                    if (userRole == "mestre") {
                        Text("Acessar Painel Mestre")
                    } else {
                        Text("Acessar Painel Administrativo")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Text(
                    text = "Bem-vindo!",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            OutlinedButton(
                onClick = { 
                    loginStatus = null
                    onLogout() 
                },
                modifier = Modifier.fillMaxWidth(),
                colors = if (isGlassTheme) {
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = sharedGlassStyle.fill.copy(alpha = sharedGlassStyle.alpha),
                        contentColor = sharedGlassStyle.highlight
                    )
                } else ButtonDefaults.outlinedButtonColors()
            ) {
                Text("Sair")
            }
        }
        
        
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        
        val dynamicTabs by viewModel.dynamicTabs.collectAsState()
        val supportedDynamicTabs = dynamicTabs.filter { it.type == "text" || it.type == "image" }
        if (supportedDynamicTabs.isNotEmpty()) {
            Text(
                text = "Abas Adicionais",
                style = if (useGradientDrawer) {
                    MaterialTheme.typography.titleLarge.merge(TextStyle(brush = multicolorBrush))
                } else MaterialTheme.typography.titleLarge,
                color = if (isMulticolorTheme || isGlassTheme) drawerAccent else MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            supportedDynamicTabs.sortedWith(compareBy<com.example.data.DynamicTab> { it.displayOrder }.thenBy { it.id }).forEach { tab ->
                TextButton(
                    onClick = {
                        onGoToDynamicTab(tab.id)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tab.title)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Text(
            text = "Categorias",
            style = if (useGradientDrawer) {
                MaterialTheme.typography.titleLarge.merge(TextStyle(brush = multicolorBrush))
            } else MaterialTheme.typography.titleLarge,
            color = if (isMulticolorTheme || isGlassTheme) drawerAccent else MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        activeCategoryNames.forEach { categoryName ->
            CategoryItem(
                category = categoryName,
                viewModel = viewModel,
                isExpanded = expandedCategory == categoryName,
                accentBrush = if (useGradientDrawer) multicolorBrush else null,
                accentColor = if (useGradientDrawer) {
                    multicolorSessionColors[kotlin.math.abs(categoryName.hashCode()) % multicolorSessionColors.size]
                } else null,
                onExpandToggle = {
                    if (expandedCategory == categoryName) {
                        expandedCategory = null
                    } else {
                        expandedCategory = categoryName
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onGoToPromotions,
            modifier = if (useGradientDrawer) {
                Modifier.fillMaxWidth().background(multicolorBrush, RoundedCornerShape(28.dp))
            } else Modifier.fillMaxWidth(),
            colors = if (useGradientDrawer) {
                ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White)
            } else ButtonDefaults.buttonColors()
        ) {
            Text("Promoções")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onGoToSettings,
            modifier = if (useGradientDrawer) {
                Modifier.fillMaxWidth().background(multicolorBrush, RoundedCornerShape(28.dp))
            } else Modifier.fillMaxWidth(),
            colors = if (useGradientDrawer) {
                ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White)
            } else ButtonDefaults.buttonColors()
        ) {
            Text("Configurações")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onGoToAbout,
            modifier = if (useGradientDrawer) {
                Modifier.fillMaxWidth().background(multicolorBrush, RoundedCornerShape(28.dp))
            } else Modifier.fillMaxWidth(),
            colors = if (useGradientDrawer) {
                ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White)
            } else ButtonDefaults.buttonColors()
        ) {
            Text("Sobre")
        }
    }
}

@Composable
fun CategoryItem(
    category: String,
    viewModel: MainViewModel,
    isExpanded: Boolean,
    accentBrush: Brush? = null,
    accentColor: Color? = null,
    onExpandToggle: () -> Unit
) {
    val allProducts by viewModel.allProducts.collectAsState()
    val products = remember(allProducts, category) {
        allProducts.filter { it.category.equals(category, ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
    }
    val glass = rememberGlassVisualStyle(viewModel)
    val shape = RoundedCornerShape(18.dp)
    val glassFillAlpha = (glass.alpha * 0.68f).coerceIn(0.24f, 0.62f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (glass.enabled) 4.dp else 0.dp)
            .clip(shape)
            .then(
                if (glass.enabled) {
                    Modifier
                        .background(glass.fill.copy(alpha = glassFillAlpha))
                        .border(1.dp, glass.border, shape)
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(onClick = onExpandToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category,
                style = if (!glass.enabled && accentBrush != null) {
                    MaterialTheme.typography.titleMedium.merge(TextStyle(brush = accentBrush))
                } else MaterialTheme.typography.titleMedium,
                color = when {
                    glass.enabled -> glass.highlight
                    accentBrush != null -> Color.Unspecified
                    else -> (accentColor ?: MaterialTheme.colorScheme.primary)
                }
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Recolher" else "Expandir",
                tint = if (glass.enabled) glass.highlight else (accentColor ?: MaterialTheme.colorScheme.primary)
            )
        }

        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                if (products.isEmpty()) {
                    Text(
                        "Nenhum produto nesta categoria.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    products.forEach { product ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = product.code,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (glass.enabled) glass.highlight else MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(
                            color = if (glass.enabled) glass.border.copy(alpha = 0.28f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
