package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductRepositorySearchTest {
    private fun product(
        code: String,
        name: String,
        category: String = "Mercearia",
        searchCount: Int = 0
    ) = Product(
        code = code,
        name = name,
        searchName = ProductStandards.searchNameFrom(name),
        category = category,
        searchCount = searchCount
    )

    @Test
    fun `pao encontra Pao e variantes de acento retornam os mesmos resultados`() {
        val products = listOf(
            product("1", "Pão Francês"),
            product("2", "Pao de Açúcar"),
            product("3", "Leite")
        )

        val pao = rankProductsByRelevance(products, "pao").map { it.code }
        val uppercase = rankProductsByRelevance(products, "PÃO").map { it.code }

        assertEquals(listOf("2", "1"), pao)
        assertEquals(pao, uppercase)
    }

    @Test
    fun `consulta de duas palavras prioriza nome que começa com a consulta`() {
        val products = listOf(
            product("1", "Pão Francês"),
            product("2", "Biscoito sabor Pão Francês"),
            product("3", "Pão de Forma Frances")
        )

        assertEquals(
            listOf("1", "2", "3"),
            rankProductsByRelevance(products, "pao frances").map { it.code }
        )
    }

    @Test
    fun `frances encontra Pao Frances por inicio de palavra`() {
        val products = listOf(
            product("1", "Pão Francês"),
            product("2", "Biscoito Integral")
        )

        assertEquals(listOf("1"), rankProductsByRelevance(products, "frances").map { it.code })
    }

    @Test
    fun `typo fica abaixo de correspondencia textual real`() {
        val products = listOf(
            product("1", "Poa Teste"),
            product("2", "Pão"),
            product("3", "Pato")
        )

        assertEquals(listOf("1", "2"), rankProductsByRelevance(products, "poa").map { it.code })
    }

    @Test
    fun `codigo exato fica acima de correspondencias textuais`() {
        val products = listOf(
            product("789123", "Arroz"),
            product("123", "Produto 789 especial"),
            product("789", "Feijão")
        )

        assertEquals(listOf("789", "789123", "123"), rankProductsByRelevance(products, "789").map { it.code })
    }

    @Test
    fun `mesma relevancia usa ordem alfabetica ignorando acentos e caixa`() {
        val products = listOf(
            product("1", "Pão Integral", searchCount = 100),
            product("2", "Pao Doce", searchCount = 0),
            product("3", "PÃO Caseiro", searchCount = 999)
        )

        assertEquals(listOf("3", "2", "1"), rankProductsByRelevance(products, "pao").map { it.code })
    }

    @Test
    fun `categoria correspondente permanece depois de correspondencia textual`() {
        val products = listOf(
            product("1", "Café", category = "Cafeteria"),
            product("2", "Bolo", category = "Cafeteria")
        )

        assertEquals(listOf("1", "2"), rankProductsByRelevance(products, "cafe").map { it.code })
    }
}
