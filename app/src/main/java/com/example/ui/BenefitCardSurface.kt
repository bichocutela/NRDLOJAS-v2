package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R

@Composable
internal fun BenefitCardSurface(
    backgroundUrl: String,
    themeKey: String,
    name: String,
    limit: String,
    spent: String,
    balance: String,
    period: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val fallback = when (themeKey) {
        "red" -> listOf(Color(0xFF8F0008), Color(0xFFE21D28), Color(0xFF65100C))
        "green" -> listOf(Color(0xFF005538), Color(0xFF16A26B), Color(0xFF073C32))
        "blue" -> listOf(Color(0xFF003B72), Color(0xFF1689D4), Color(0xFF102A56))
        "gold" -> listOf(Color(0xFF674300), Color(0xFFD49A14), Color(0xFF432E12))
        "orange" -> listOf(Color(0xFF8D3100), Color(0xFFE96B16), Color(0xFF5A220D))
        else -> listOf(Color(0xFF55207E), Color(0xFF187FA3), Color(0xFFB73568))
    }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().aspectRatio(1.586f).clip(shape)
                .background(Brush.linearGradient(fallback))
        ) {
            val scale = (maxWidth.value / 380f).coerceIn(0.78f, 1.15f)
            if (backgroundUrl.isNotBlank()) {
                AsyncImage(
                    model = backgroundUrl,
                    contentDescription = "Fundo do cartão-convênio",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (themeKey == "red") {
                Image(
                    painter = painterResource(R.drawable.benefit_card_red),
                    contentDescription = "Fundo vermelho do cartão-convênio",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = "CONVÊNIO",
                color = Color(0xFFFFE5A8),
                fontSize = (16f * scale).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (1.2f * scale).sp,
                modifier = Modifier.align(Alignment.TopStart)
                    .offset(x = maxWidth * 0.075f, y = maxHeight * 0.075f)
            )
            Canvas(
                modifier = Modifier.align(Alignment.TopStart)
                    .offset(x = maxWidth * 0.075f, y = maxHeight * 0.245f)
                    .size(width = (52f * scale).dp, height = (37f * scale).dp)
            ) {
                val radius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx())
                drawRoundRect(
                    brush = Brush.linearGradient(listOf(Color(0xFFFFE59B), Color(0xFFCA8B20), Color(0xFFFFF0B9))),
                    cornerRadius = radius
                )
                val stroke = 1.dp.toPx()
                val line = Color(0xFF80551C).copy(alpha = 0.7f)
                drawLine(line, androidx.compose.ui.geometry.Offset(size.width * 0.34f, 0f), androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height), stroke)
                drawLine(line, androidx.compose.ui.geometry.Offset(size.width * 0.67f, 0f), androidx.compose.ui.geometry.Offset(size.width * 0.67f, size.height), stroke)
                drawLine(line, androidx.compose.ui.geometry.Offset(0f, size.height * 0.34f), androidx.compose.ui.geometry.Offset(size.width, size.height * 0.34f), stroke)
                drawLine(line, androidx.compose.ui.geometry.Offset(0f, size.height * 0.67f), androidx.compose.ui.geometry.Offset(size.width, size.height * 0.67f), stroke)
                drawRoundRect(color = Color.White.copy(alpha = 0.3f), cornerRadius = radius, style = Stroke(width = stroke))
            }

            Column(
                modifier = Modifier.align(Alignment.TopStart)
                    .offset(x = maxWidth * 0.075f, y = maxHeight * 0.555f)
                    .fillMaxWidth(0.62f),
                verticalArrangement = Arrangement.spacedBy(5.dp * scale)
            ) {
                Text(
                    text = name.ifBlank { "USUÁRIO" }.uppercase(),
                    color = Color.White,
                    fontSize = (15f * scale).sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (1.1f * scale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp * scale)
                ) {
                    BenefitCardValue("LIMITE", limit, Modifier.weight(1.08f), scale)
                    BenefitCardValue("GASTO", spent, Modifier.weight(1.08f), scale)
                    BenefitCardValue("SALDO", balance, Modifier.weight(0.92f), scale)
                }
                Text(
                    text = "PERÍODO  " + period.ifBlank { "—" },
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = (9.5f * scale).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BenefitCardValue(label: String, value: String, modifier: Modifier, scale: Float) {
    Column(modifier = modifier) {
        Text(label, color = Color.White.copy(alpha = 0.88f), fontSize = (8f * scale).sp,
            fontWeight = FontWeight.Medium, maxLines = 1)
        Text(value.ifBlank { "—" }, color = Color.White, fontSize = (11f * scale).sp,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
