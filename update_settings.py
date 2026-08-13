import re

with open('app/src/main/java/com/example/ui/SettingsScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add changeAppIcon at the end of the file
change_icon_func = """
fun changeAppIcon(context: Context, iconName: String) {
    val pm = context.packageManager
    val packageName = context.packageName

    val aliases = mapOf(
        "multicolor" to "$packageName.MainActivityMulticolor",
        "red" to "$packageName.MainActivityRed",
        "green" to "$packageName.MainActivityGreen",
        "blue" to "$packageName.MainActivityBlue",
        "orange" to "$packageName.MainActivityOrange",
        "gold" to "$packageName.MainActivityGold"
    )

    val targetAlias = aliases[iconName] ?: aliases["multicolor"]!!

    // Enable the new one first
    pm.setComponentEnabledSetting(
        android.content.ComponentName(packageName, targetAlias),
        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        android.content.pm.PackageManager.DONT_KILL_APP
    )

    // Disable the others
    aliases.values.forEach { alias ->
        if (alias != targetAlias) {
            pm.setComponentEnabledSetting(
                android.content.ComponentName(packageName, alias),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        }
    }
}
"""

content += change_icon_func

# Add appIcon state and appTheme state
# Find val appTheme = ...
theme_pattern = r'val appTheme = viewModel\.userPreferences\.appTheme\.collectAsState\(initial = "multicolor"\)\.value'
if 'val appTheme =' not in content:
    theme_pattern = r'val uppercaseBold by viewModel\.userPreferences\.uppercaseBold\.collectAsState\(initial = false\)'
    theme_replacement = '''val uppercaseBold by viewModel.userPreferences.uppercaseBold.collectAsState(initial = false)
    val appTheme by viewModel.userPreferences.appTheme.collectAsState(initial = "multicolor")
    val appIcon by viewModel.userPreferences.appIcon.collectAsState(initial = "multicolor")'''
    content = re.sub(theme_pattern, theme_replacement, content)

# Inject the App Icon section before App Theme section
theme_section = r'Text\("Tema do Aplicativo", style = MaterialTheme\.typography\.titleMedium, color = getDynamicThemeColor\(1, appTheme, MaterialTheme\.colorScheme\.primary, MaterialTheme\.colorScheme\.onPrimary\)\.first\)'

app_icon_section = '''Text("Ícone do aplicativo", style = MaterialTheme.typography.titleMedium, color = getDynamicThemeColor(1, appTheme, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary).first)
            Text("Escolha como o NRD Códigos aparecerá na tela inicial do seu celular.", style = MaterialTheme.typography.bodySmall)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val iconOptions = listOf(
                "multicolor" to Pair("Multicolorido", com.example.R.drawable.icon_multicolor),
                "red" to Pair("Vermelho", com.example.R.drawable.icon_red),
                "green" to Pair("Verde", com.example.R.drawable.icon_green),
                "blue" to Pair("Azul", com.example.R.drawable.icon_blue),
                "orange" to Pair("Laranja", com.example.R.drawable.icon_orange),
                "gold" to Pair("Dourado", com.example.R.drawable.icon_gold)
            )
            
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().height(260.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(iconOptions.size) { index ->
                    val (iconKey, pair) = iconOptions[index]
                    val (iconLabel, iconResId) = pair
                    val isSelected = appIcon == iconKey
                    
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .androidx.compose.foundation.clickable {
                                coroutineScope.launch {
                                    viewModel.userPreferences.setAppIcon(iconKey)
                                }
                                changeAppIcon(context, iconKey)
                                android.widget.Toast.makeText(context, "Ícone alterado. A tela inicial pode levar alguns segundos para atualizar.", android.widget.Toast.LENGTH_LONG).show()
                            },
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = iconResId),
                                contentDescription = iconLabel,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(iconLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            '''

content = content.replace('Text("Tema do Aplicativo"', app_icon_section + 'Text("Tema do Aplicativo"')

with open('app/src/main/java/com/example/ui/SettingsScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Settings updated")
