package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PaginationTest {

    @Test
    fun emptyListUsesFirstEmptyPage() {
        assertEquals(
            PaginationWindow(pageIndex = 0, pageCount = 1, fromIndex = 0, toIndex = 0),
            calculatePaginationWindow(totalItems = 0, requestedPage = 4, pageSize = 10)
        )
    }

    @Test
    fun lastPageContainsOnlyRemainingItems() {
        assertEquals(
            PaginationWindow(pageIndex = 2, pageCount = 3, fromIndex = 20, toIndex = 23),
            calculatePaginationWindow(totalItems = 23, requestedPage = 2, pageSize = 10)
        )
    }

    @Test
    fun pageIsClampedWhenFilteredListShrinks() {
        assertEquals(
            PaginationWindow(pageIndex = 0, pageCount = 1, fromIndex = 0, toIndex = 4),
            calculatePaginationWindow(totalItems = 4, requestedPage = 5, pageSize = 10)
        )
    }
}
