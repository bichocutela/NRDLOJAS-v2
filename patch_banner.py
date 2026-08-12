import sys
with open("app/src/main/java/com/example/ui/SearchScreen.kt", "r") as f:
    content = f.read()

# Add imports
if "import androidx.compose.ui.graphics.Brush" not in content:
    content = content.replace("import androidx.compose.ui.graphics.Color", "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.Brush")

# 1. Update ThemeBanner
target_banner = """    AsyncImage(
        model = imageUrl,
        contentDescription = "Banner do tema $normalizedTheme",
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentScale = ContentScale.FillWidth
    )"""
replacement_banner = """    AsyncImage(
        model = imageUrl,
        contentDescription = "Banner do tema $normalizedTheme",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.FillWidth
    )"""
content = content.replace(target_banner, replacement_banner)

# 2. Update Title
target_title = """                Text(
                    text = "NRD Códigos Correlatos",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )"""

replacement_title = """                if (normalizedTheme == "multicolor") {
                    val gradientBrush = androidx.compose.runtime.remember {
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFE62325), // Vermelho
                                Color(0xFFFF9800), // Laranja
                                Color(0xFFD4AF37), // Dourado
                                Color(0xFF388E3C), // Verde
                                Color(0xFF1976D2)  // Azul
                            )
                        )
                    }
                    Text(
                        text = "NRD Códigos Correlatos",
                        style = MaterialTheme.typography.titleMedium.copy(brush = gradientBrush),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    )
                } else {
                    Text(
                        text = "NRD Códigos Correlatos",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    )
                }"""
content = content.replace(target_title, replacement_title)

with open("app/src/main/java/com/example/ui/SearchScreen.kt", "w") as f:
    f.write(content)
print("Success")
