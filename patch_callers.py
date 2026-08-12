import sys
with open("app/src/main/java/com/example/ui/SearchScreen.kt", "r") as f:
    content = f.read()

# 1. Search Results
target1 = """                items(searchResults, key = { it.code }) { product ->
                    ProductCard(product, viewModel)
                }"""
replacement1 = """                itemsIndexed(searchResults, key = { _, it -> it.code }) { index, product ->
                    ProductCard(product, viewModel, index, appTheme)
                }"""
content = content.replace(target1, replacement1)

# 2. Most Used
target2 = """                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(mostUsed, key = { it.code }) { product ->
                                MiniProductCard(product, viewModel)
                            }
                        }"""
replacement2 = """                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(mostUsed, key = { _, it -> it.code }) { index, product ->
                                MiniProductCard(product, viewModel, index, appTheme)
                            }
                        }"""
content = content.replace(target2, replacement2)

# 3. History
target3 = """                            history.take(5).forEach { product ->
                                HistoryItem(product, viewModel)
                            }"""
replacement3 = """                            history.take(5).forEachIndexed { index, product ->
                                HistoryItem(product, viewModel, index, appTheme)
                            }"""
content = content.replace(target3, replacement3)

# 4. Favorites
target4 = """                            favorites.forEach { product ->
                                ProductCard(product, viewModel)
                            }"""
replacement4 = """                            favorites.forEachIndexed { index, product ->
                                ProductCard(product, viewModel, index, appTheme)
                            }"""
content = content.replace(target4, replacement4)

with open("app/src/main/java/com/example/ui/SearchScreen.kt", "w") as f:
    f.write(content)
print("Success")
