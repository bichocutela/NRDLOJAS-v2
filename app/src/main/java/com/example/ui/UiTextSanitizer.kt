package com.example.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

private fun sanitizeUiText(value: String): String {
    var text = value
        .replace("Preço Sistema", "Preço atual", ignoreCase = true)
        .replace("preço Sistema", "preço atual", ignoreCase = true)
        .replace("Categorias Sistema", "Categorias", ignoreCase = true)
        .replace("Categoria Sistema", "Categoria", ignoreCase = true)
        .replace("Diagnóstico Sistema", "Diagnóstico", ignoreCase = true)
        .replace("Acesso Sistema", "Acesso à consulta", ignoreCase = true)
        .replace("acesso Sistema", "acesso à consulta", ignoreCase = true)
        .replace("produto Sistema", "produto", ignoreCase = true)
        .replace("Buscar no Sistema", "Buscar no sistema", ignoreCase = true)
        .replace("no Sistema", "no sistema", ignoreCase = true)
        .replace("do Sistema", "do sistema", ignoreCase = true)
        .replace("à Sistema", "ao sistema", ignoreCase = true)
        .replace("pelo Sistema", "pelo sistema", ignoreCase = true)
        .replace("a Sistema", "o sistema", ignoreCase = true)
        .replace("Valores diferentes: Sistema", "Valores diferentes: preço atual", ignoreCase = true)

    text = Regex("(?i)\\bACP\\b").replace(text, "sistema")
    return text
}

/**
 * Centraliza a apresentação de textos da interface e remove nomes técnicos internos
 * que não devem aparecer para o funcionário.
 */
@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    androidx.compose.material3.Text(
        text = sanitizeUiText(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}
