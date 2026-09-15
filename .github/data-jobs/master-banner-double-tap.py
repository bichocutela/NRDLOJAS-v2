from pathlib import Path


def replace_once(path, old, new, label):
    p = Path(path)
    text = p.read_text(encoding='utf-8')
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: esperado 1 trecho, encontrado {count}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')

# SearchScreen: gesto somente quando autorizado pelo AppNavGraph.
path = 'app/src/main/java/com/example/ui/SearchScreen.kt'
replace_once(path,
'''import androidx.compose.foundation.clickable\n''',
'''import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.combinedClickable\n''',
'import combinedClickable')
replace_once(path,
'''fun SearchScreen(viewModel: MainViewModel, onOpenDrawer: () -> Unit = {}) {\n''',
'''fun SearchScreen(\n    viewModel: MainViewModel,\n    onOpenDrawer: () -> Unit = {},\n    canQuickEditBanner: Boolean = false,\n    onQuickEditBanner: (String) -> Unit = {}\n) {\n''',
'assinatura SearchScreen')
replace_once(path,
'''                    modifier = Modifier.fillMaxSize()\n                )\n\n                IconButton(\n''',
'''                    modifier = Modifier\n                        .fillMaxSize()\n                        .then(\n                            if (canQuickEditBanner && activeThemeBackground != null) {\n                                Modifier.combinedClickable(\n                                    onClick = {},\n                                    onDoubleClick = {\n                                        onQuickEditBanner(if (isGlassTheme) "glass" else normalizedTheme)\n                                    }\n                                )\n                            } else {\n                                Modifier\n                            }\n                        )\n                )\n\n                IconButton(\n''',
'gesto no banner')

# BannerPreviewEditor: botão simples reutilizando o editor real.
path = 'app/src/main/java/com/example/ui/BannerPreviewEditor.kt'
replace_once(path,
'''import androidx.compose.material.icons.filled.ExpandMore\n''',
'''import androidx.compose.material.icons.filled.ExpandMore\nimport androidx.compose.material.icons.filled.Edit\n''',
'import Edit')
replace_once(path,
'''    isSaving: Boolean,\n    onDismiss: () -> Unit,\n    onSave: (ThemeBackground, BannerMaskSettings) -> Unit\n''',
'''    isSaving: Boolean,\n    onDismiss: () -> Unit,\n    onEditBackground: (() -> Unit)? = null,\n    onSave: (ThemeBackground, BannerMaskSettings) -> Unit\n''',
'assinatura BannerPreviewEditor')
replace_once(path,
'''                    }\n                }\n\n                Button(\n''',
'''                    }\n\n                    if (onEditBackground != null) {\n                        OutlinedButton(\n                            onClick = onEditBackground,\n                            modifier = Modifier.fillMaxWidth(),\n                            enabled = !isSaving\n                        ) {\n                            Icon(Icons.Default.Edit, contentDescription = null)\n                            Spacer(modifier = Modifier.width(6.dp))\n                            Text("Editar fundo")\n                        }\n                    }\n                }\n\n                Button(\n''',
'botão Editar fundo')

# MestreScreen: entrada direta na prévia do fundo ativo e callback para o editor já existente.
path = 'app/src/main/java/com/example/ui/MestreScreen.kt'
replace_once(path,
'''    onNavigateToManageProducts: () -> Unit,\n    onNavigateBack: () -> Unit\n) {\n    var pageStack by rememberSaveable { mutableStateOf(arrayListOf(MestrePanelPage.DASHBOARD.name)) }\n''',
'''    onNavigateToManageProducts: () -> Unit,\n    onNavigateBack: () -> Unit,\n    quickEditThemeKey: String? = null\n) {\n    var pageStack by rememberSaveable(quickEditThemeKey) {\n        mutableStateOf(\n            if (quickEditThemeKey.isNullOrBlank()) arrayListOf(MestrePanelPage.DASHBOARD.name)\n            else arrayListOf(MestrePanelPage.APPEARANCE_SETTINGS.name)\n        )\n    }\n''',
'assinatura e página inicial MestreScreen')
replace_once(path,
'''    var backgroundToPreview by remember { mutableStateOf<Pair<String, ThemeBackground>?>(null) }\n''',
'''    var backgroundToPreview by remember { mutableStateOf<Pair<String, ThemeBackground>?>(null) }\n    var quickPreviewOpened by remember(quickEditThemeKey) { mutableStateOf(false) }\n''',
'estado quick preview')
replace_once(path,
'''    fun pickerDateToIsoDate(millis: Long?): String? = millis?.let {\n''',
'''    LaunchedEffect(quickEditThemeKey, appearanceSettings.themeBackgrounds) {\n        val themeKey = quickEditThemeKey?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect\n        if (!quickPreviewOpened) {\n            appearanceSettings.activeBackgroundFor(themeKey)?.let { activeBackground ->\n                backgroundToPreview = themeKey to activeBackground\n                quickPreviewOpened = true\n            }\n        }\n    }\n\n    fun pickerDateToIsoDate(millis: Long?): String? = millis?.let {\n''',
'abertura automática da prévia')
replace_once(path,
'''          onDismiss = {\n              if (!isSavingAppearanceSettings) backgroundToPreview = null\n          },\n          onSave = { updatedBackground, maskSettings ->\n''',
'''          onDismiss = {\n              if (!isSavingAppearanceSettings) backgroundToPreview = null\n          },\n          onEditBackground = if (themeKey != CONSULTATION_BACKGROUND_KEY && previewOfferKey == null) {\n              {\n                  backgroundToPreview = null\n                  openBackgroundEditor(themeKey, background)\n              }\n          } else null,\n          onSave = { updatedBackground, maskSettings ->\n''',
'callback editor real')

# AppNavGraph: somente Mestre recebe o gesto e a rota protegida.
path = 'app/src/main/java/com/example/ui/AppNavGraph.kt'
replace_once(path,
'''                composable("search") { SearchScreen(viewModel, onOpenDrawer = { scope.launch { drawerState.open() } }) }\n''',
'''                composable("search") {\n                    SearchScreen(\n                        viewModel = viewModel,\n                        onOpenDrawer = { scope.launch { drawerState.open() } },\n                        canQuickEditBanner = isLoggedIn && userRole == "mestre",\n                        onQuickEditBanner = { themeKey ->\n                            navController.navigate("mestre/banner/$themeKey") { launchSingleTop = true }\n                        }\n                    )\n                }\n''',
'rota SearchScreen')
replace_once(path,
'''                composable("manage_tabs") {\n''',
'''                composable("mestre/banner/{themeKey}") { backStackEntry ->\n                    val themeKey = backStackEntry.arguments?.getString("themeKey")\n                    ProtectedManagementRoute(isLoggedIn, userRole, setOf("mestre"), { navController.navigateToSearch() }) {\n                        MestreScreen(\n                            viewModel = viewModel,\n                            onNavigateToAdmin = { navController.navigate("admin") },\n                            onNavigateToManageTabs = { navController.navigate("manage_tabs") },\n                            onNavigateToManageProducts = { navController.navigate("manage_products") },\n                            onNavigateBack = { navController.popBackStack() },\n                            quickEditThemeKey = themeKey\n                        )\n                    }\n                }\n                composable("manage_tabs") {\n''',
'rota Mestre banner')

print('Patch aplicado com sucesso.')
