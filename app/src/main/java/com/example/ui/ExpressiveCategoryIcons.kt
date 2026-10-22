package com.example.ui

import com.example.R

internal fun expressiveCategoryIconRes(category: String): Int = when (category.lowercase()) {
    "açougue", "acougue" -> R.drawable.ic_expressive_meat
    "cafeteria" -> R.drawable.ic_expressive_cafe
    "frios" -> R.drawable.ic_expressive_cheese
    "hortifruti" -> R.drawable.ic_expressive_carrot
    "mercearia" -> R.drawable.ic_expressive_basket
    "padaria" -> R.drawable.ic_expressive_bread
    else -> R.drawable.ic_expressive_basket
}
