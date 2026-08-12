package com.example.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.draw.alpha
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AppNavGraph(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE) }
    var isLoggedIn by remember { mutableStateOf(sharedPref.getBoolean("is_logged_in", false)) }
    var userRole by remember { mutableStateOf(sharedPref.getString("user_role", "admin") ?: "admin") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                LoginDrawerContent(
                    viewModel = viewModel,
                    isLoggedIn = isLoggedIn,
                    userRole = userRole,
                    onLoginSuccess = { role ->
                        sharedPref.edit()
                            .putBoolean("is_logged_in", true)
                            .putString("user_role", role)
                            .apply()
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
                        sharedPref.edit().putBoolean("is_logged_in", false).apply()
                        isLoggedIn = false
                        scope.launch { drawerState.close() }
                        navController.navigate("search") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
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
                    AdminScreen(viewModel, onNavigateBack = {
                        navController.popBackStack()
                    })
                }
                composable("mestre") {
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
                composable("manage_tabs") {
                    ManageTabsScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
                }
                composable("manage_products") {
                    ManageProductsScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
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

@Composable
fun LoginDrawerContent(
    viewModel: MainViewModel,
    isLoggedIn: Boolean,
    userRole: String,
    onLoginSuccess: (String) -> Unit,
    onLogout: () -> Unit,
    onGoToAdmin: () -> Unit,
    onGoToSettings: () -> Unit,
    onGoToAbout: () -> Unit,
    onGoToDynamicTab: (Int) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loginStatus by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val categories by viewModel.productsCountByCategory.collectAsState()
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
                            if ((inputUser == "admin" || inputUser == "mestre") && password == "nrdlojas") {
                                val email = if (inputUser == "admin") "admin@nrdlojas.com" else "mestre@nrdlojas.com"
                                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                                auth.signInWithEmailAndPassword(email, password).await()
                                onLoginSuccess(inputUser)
                            } else {
                                loginStatus = "Usuário ou senha incorretos"
                            }
                        } catch (e: Exception) {
                            loginStatus = "Erro ao autenticar: ${e.localizedMessage}"
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
        
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        
        val dynamicTabs by viewModel.dynamicTabs.collectAsState()
        if (dynamicTabs.isNotEmpty()) {
            Text(
                text = "Abas Adicionais",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            dynamicTabs.sortedBy { it.displayOrder }.forEach { tab ->
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
        
        val allCategoriesList = remember(categories) {
            val defaultCategories = listOf("Hortifruti", "Açougue", "Padaria", "Frios", "Bebidas", "Mercearia", "Limpeza", "Higiene", "Laticínios", "Congelados", "Bazar", "Pet Shop")
            val dbCategories = categories.map { it.category }
            (dbCategories + defaultCategories).distinct().sorted()
        }

        allCategoriesList.forEach { categoryName ->
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

