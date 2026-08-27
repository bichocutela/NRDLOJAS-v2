package com.example.data

/**
 * Categoria administrável pelo Painel Mestre.
 * O id permanece estável mesmo quando o nome exibido é alterado.
 */
data class CategoryDefinition(
    val id: String,
    val name: String,
    val displayOrder: Int,
    val isActive: Boolean = true
) {
    companion object {
        val defaults = ProductStandards.officialCategories.mapIndexed { index, name ->
            CategoryDefinition(
                id = ProductStandards.categoryId(name),
                name = name,
                displayOrder = index
            )
        }
    }
}
