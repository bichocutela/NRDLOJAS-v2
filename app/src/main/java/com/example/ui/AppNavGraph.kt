package com.example.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
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
            ModalDrawerSheet {
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
    val multicolorBrush = remember(drawerAccentColors) { Brush.horizontalGradient(drawerAccentColors) }
    val useGradientDrawer = isMulticolorTheme || isGlassTheme
    val glassSurfaceAlpha = when (glassType) {
        "frosted" -> (0.94f - glassTransparency * 0.48f).coerceIn(0.44f, 0.84f)
        "crystal" -> (0.76f - glassTransparency * 0.38f).coerceIn(0.28f, 0.66f)
        else -> (0.86f - glassTransparency * 0.44f).coerceIn(0.36f, 0.76f)
    }
    val drawerGlassBrush = remember(drawerAccentColors) {
        Brush.verticalGradient(listOf(Color.White) + drawerAccentColors.map { it.copy(alpha = 0.16f) } + listOf(Color.White))
    }
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isGlassTheme) Modifier
                    .background(drawerGlassBrush, RoundedCornerShape(28.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(28.dp))
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
                color = if (useGradientDrawer) Color.Unspecified else MaterialTheme.colorScheme.primary
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
                    MaterialTheme.typography.headlineMedium.merge(TextStyle(brush = multicolorBrush))
                } else MaterialTheme.typography.headlineMedium,
                color = if (useGradientDrawer) Color.Unspecified else MaterialTheme.colorScheme.primary
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
                    ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = glassSurfaceAlpha))
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
                color = if (useGradientDrawer) Color.Unspecified else MaterialTheme.colorScheme.primary,
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
            color = if (useGradientDrawer) Color.Unspecified else MaterialTheme.colorScheme.primary,
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
    val productsFlow = remember(category) { viewModel.getProductsByCategory(category) }
    val products by if (isExpanded) productsFlow.collectAsState(initial = emptyList()) else remember { mutableStateOf(emptyList()) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = onExpandToggle,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category,
                    style = if (accentBrush != null) {
                        MaterialTheme.typography.titleMedium.merge(TextStyle(brush = accentBrush))
                    } else MaterialTheme.typography.titleMedium,
                    color = if (accentBrush != null) Color.Unspecified else LocalContentColor.current
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Recolher" else "Expandir",
                    tint = accentColor ?: LocalContentColor.current
                )
            }
        }
        
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                if (products.isEmpty()) {
                    Text("Carregando...", style = MaterialTheme.typography.bodyMedium)
                } else {
                    products.forEach { product ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = product.code,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(modifier = Modifier.alpha(0.5f))
                    }
                }
            }
        }
    }
}
