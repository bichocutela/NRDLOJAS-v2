package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.NossaGenteApi
import com.example.data.NossaGenteLoginResult
import com.example.data.NossaGentePromotionsResult
import com.example.data.Promotion
import com.example.data.PromotionProduct
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromotionsLoginScreen(
    api: NossaGenteApi,
    onLoginSuccess: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var matricula by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Acesso às promoções") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("Entre com o mesmo acesso do Nossa Gente", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Use sua matrícula e sua senha do Nossa Gente. A senha é usada somente nesta autenticação e não é salva no aparelho.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = matricula,
                onValueChange = { value -> matricula = value.take(20) },
                label = { Text("Matrícula") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (isLoading) return@Button
                    error = null
                    isLoading = true
                    scope.launch {
                        when (val result = api.login(matricula, password)) {
                            NossaGenteLoginResult.Success -> {
                                password = ""
                                onLoginSuccess()
                            }
                            is NossaGenteLoginResult.Error -> error = result.message
                        }
                        isLoading = false
                    }
                },
                enabled = !isLoading && matricula.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Entrar")
                }
            }
            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromotionsScreen(
    api: NossaGenteApi,
    onNavigateBack: () -> Unit,
    onRequireLogin: () -> Unit
) {
    var promotions by remember { mutableStateOf<List<Promotion>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadPromotions() {
        scope.launch {
            isLoading = true
            error = null
            when (val result = api.fetchPromotions()) {
                is NossaGentePromotionsResult.Success -> promotions = result.promotions
                NossaGentePromotionsResult.Unauthorized -> onRequireLogin()
                is NossaGentePromotionsResult.Error -> error = result.message
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (!api.hasSession()) {
            onRequireLogin()
        } else {
            loadPromotions()
        }
    }

    LaunchedEffect(api) {
        while (true) {
            delay(60_000)
            if (!api.hasSession()) break
            loadPromotions()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Promoções") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = ::loadPromotions, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar promoções")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            error != null -> Column(
                modifier = Modifier.padding(innerPadding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = ::loadPromotions) { Text("Tentar novamente") }
            }
            promotions.isEmpty() -> Column(
                modifier = Modifier.padding(innerPadding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Nenhuma promoção disponível no momento.")
                Spacer(Modifier.height(8.dp))
                Text("Toque em atualizar para consultar novamente.", style = MaterialTheme.typography.bodyMedium)
            }
            else -> LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Ofertas disponíveis",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        "Atualização automática a cada minuto enquanto esta tela estiver aberta.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(promotions, key = { it.id }) { promotion ->
                    PromotionCard(promotion)
                }
            }
        }
    }
}

@Composable
private fun PromotionCard(promotion: Promotion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!promotion.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = promotion.imageUrl,
                    contentDescription = promotion.title,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalOffer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(promotion.title, style = MaterialTheme.typography.titleLarge)
            }
            if (promotion.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(promotion.description, style = MaterialTheme.typography.bodyMedium)
            }
            if (!promotion.validFrom.isNullOrBlank() || !promotion.validTo.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Validade: ${promotion.validFrom.orEmpty()}${if (!promotion.validTo.isNullOrBlank()) " até ${promotion.validTo}" else ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (promotion.products.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Produtos em oferta", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                promotion.products.forEach { product ->
                    PromotionProductRow(product)
                }
            }
        }
    }
}

@Composable
private fun PromotionProductRow(product: PromotionProduct) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(product.name.ifBlank { product.code }, modifier = Modifier.weight(1f))
            val offer = product.offerPrice
            if (!offer.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(offer, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            }
        }
        if (product.code.isNotBlank()) {
            Text("Código: ${product.code}", style = MaterialTheme.typography.bodySmall)
        }
        val details = listOfNotNull(
            product.regularPrice?.takeIf { it.isNotBlank() }?.let { "De $it" },
            product.discount?.takeIf { it.isNotBlank() }?.let { "Desconto $it" }
        )
        if (details.isNotEmpty()) {
            Text(details.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
        }
    }
}
