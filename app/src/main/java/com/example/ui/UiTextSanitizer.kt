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
        .replace("Preço ACP", "Preço atual", ignoreCase = true)
        .replace("preço ACP", "preço atual", ignoreCase = true)
        .replace("Categorias ACP", "Categorias", ignoreCase = true)
        .replace("Categoria ACP", "Categoria", ignoreCase = true)
        .replace("Diagnóstico ACP", "Diagnóstico", ignoreCase = true)
        .replace("Acesso ACP", "Acesso à consulta", ignoreCase = true)
        .replace("acesso ACP", "acesso à consulta", ignoreCase = true)
        .replace("produto ACP", "produto", ignoreCase = true)
        .replace("Buscar na ACP", "Buscar no sistema", ignoreCase = true)
        .replace("na ACP", "no sistema", ignoreCase = true)
        .replace("da ACP", "do sistema", ignoreCase = true)
        .replace("à ACP", "ao sistema", ignoreCase = true)
        .replace("pela ACP", "pelo sistema", ignoreCase = true)
        .replace("a ACP", "o sistema", ignoreCase = true)
        .replace("Valores diferentes: ACP", "Valores diferentes: preço atual", ignoreCase = true)

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
