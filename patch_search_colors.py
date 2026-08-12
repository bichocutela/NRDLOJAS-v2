import sys
with open("app/src/main/java/com/example/ui/SearchScreen.kt", "r") as f:
    content = f.read()

if "import com.example.ui.theme.getDynamicThemeColor" not in content:
    content = content.replace("import androidx.compose.ui.Modifier", "import androidx.compose.ui.Modifier\nimport com.example.ui.theme.getDynamicThemeColor")

# Add appTheme to CategorySection
content = content.replace('fun CategorySection(viewModel: MainViewModel) {', 'fun CategorySection(viewModel: MainViewModel, appTheme: String) {')
content = content.replace('CategorySection(viewModel)', 'CategorySection(viewModel, appTheme)')

# Inside CategorySection, change items to itemsIndexed
target_category = """        items(categories) { (category, colors) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.first)
                    .clickable { viewModel.updateSearchQuery(category) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.second
                )
            }
        }"""
replacement_category = """        itemsIndexed(categories) { index, (category, colors) ->
            val dynamicColors = getDynamicThemeColor(index, appTheme, colors.first, colors.second)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(dynamicColors.first)
                    .clickable { viewModel.updateSearchQuery(category) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = dynamicColors.second
                )
            }
        }"""
content = content.replace(target_category, replacement_category)

with open("app/src/main/java/com/example/ui/SearchScreen.kt", "w") as f:
    f.write(content)
print("Success")
