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
    fun `pao retorna somente nomes que contem pao ignorando acentos e caixa`() {
        val products = listOf(
            product("1", "Pão Italiano"),
            product("2", "Pão de Chocolate"),
            product("3", "Minhoca de Pão"),
            product("4", "Rolo de Bolo com Pão"),
            product("5", "Pão Tomate Seco"),
            product("6", "Abacaxi"),
            product("7", "Cará"),
            product("8", "Coco"),
            product("9", "Arroz"),
            product("10", "Pato"),
            product("11", "Poa"),
            product("12", "Café", category = "Padaria")
        )

        assertEquals(
            listOf("2", "1", "5", "3", "4"),
            rankProductsByRelevance(products, "pao").map { it.code }
        )
        assertEquals(
            listOf("2", "1", "5", "3", "4"),
            rankProductsByRelevance(products, "PÃO").map { it.code }
        )
    }

    @Test
    fun `pao encontra a palavra em qualquer posicao do nome`() {
        val products = listOf(
            product("1", "Minhoca de Pão"),
            product("2", "Rolo de Bolo com Pão"),
            product("3", "Pão Tomate Seco"),
            product("4", "Bolo de Chocolate")
        )

        assertEquals(
            listOf("3", "1", "2"),
            rankProductsByRelevance(products, "pao").map { it.code }
        )
    }

    @Test
    fun `consulta de varias palavras exige todos os termos no nome`() {
        val products = listOf(
            product("1", "Pão Francês"),
            product("2", "Biscoito sabor Pão Francês"),
            product("3", "Pão de Forma Frances"),
            product("4", "Pão de Forma")
        )

        assertEquals(
            listOf("1", "2", "3"),
            rankProductsByRelevance(products, "pao frances").map { it.code }
        )
    }

    @Test
    fun `frances encontra nome que contem a palavra ignorando acento`() {
        val products = listOf(
            product("1", "Pão Francês"),
            product("2", "Biscoito Integral")
        )

        assertEquals(listOf("1"), rankProductsByRelevance(products, "frances").map { it.code })
    }

    @Test
    fun `nao existe fuzzy matching para poa ou outros nomes parecidos`() {
        val products = listOf(
            product("1", "Pão"),
            product("2", "Poa"),
            product("3", "Pato"),
            product("4", "Abacaxi")
        )

        assertEquals(emptyList<String>(), rankProductsByRelevance(products, "poa").map { it.code })
    }

    @Test
    fun `codigo exato continua acima de resultados textuais`() {
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
    fun `categoria nao inclui produto cujo nome nao corresponde`() {
        val products = listOf(
            product("1", "Café", category = "Cafeteria"),
            product("2", "Bolo", category = "Cafeteria")
        )

        assertEquals(listOf("1"), rankProductsByRelevance(products, "cafe").map { it.code })
    }
}
