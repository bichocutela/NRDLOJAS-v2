package com.example.ui

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.example.ui.theme.LocalGlassSoftStyle
import com.example.ui.theme.glassSoftShadow

private const val ADMIN_LOGIN_TIMEOUT_MS = 15_000L

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
    val glassSoftStyle = LocalGlassSoftStyle.current
    val glassDrawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)

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
                modifier = Modifier.glassSoftShadow(glassDrawerShape),
                drawerShape = if (glassSoftStyle.enabled) glassDrawerShape else DrawerDefaults.shape,
                drawerContainerColor = if (glassSoftStyle.enabled) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    DrawerDefaults.modalContainerColor
                }
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
            containerColor = if (glassSoftStyle.enabled) Color.Transparent else MaterialTheme.colorScheme.background
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
    var expandedCategory by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isLoggedIn) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Login",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuário") },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                visualTransformation = PasswordVisualTransformation(),
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (isLoading) return@Button
                    isLoading = true
                    loginStatus = null
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
                                val authResult = withTimeout(ADMIN_LOGIN_TIMEOUT_MS) {
                                    auth.signInWithEmailAndPassword(email, password).await()
                                }
                                val authenticatedRole = managementRoleForEmail(authResult.user?.email)
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
                        } catch (_: TimeoutCancellationException) {
                            loginStatus = "A autenticação demorou demais. Verifique sua conexão e tente novamente."
                        } catch (_: FirebaseNetworkException) {
                            loginStatus = "Sem conexão com o serviço de login. Verifique sua internet e tente novamente."
                        } catch (_: FirebaseTooManyRequestsException) {
                            loginStatus = "Muitas tentativas seguidas. Aguarde um instante e tente novamente."
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
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (userRole == "mestre" || userRole == "admin") {
                Button(
                    onClick = onGoToAdmin,
                    modifier = Modifier.fillMaxWidth()
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
                modifier = Modifier.fillMaxWidth()
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
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
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
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        activeCategoryNames.forEach { categoryName ->
            CategoryItem(
                category = categoryName,
                viewModel = viewModel,
                isExpanded = expandedCategory == categoryName,
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Promoções")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onGoToSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Configurações")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onGoToAbout,
            modifier = Modifier.fillMaxWidth()
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
                Text(text = category, style = MaterialTheme.typography.titleMedium)
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Recolher" else "Expandir"
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
