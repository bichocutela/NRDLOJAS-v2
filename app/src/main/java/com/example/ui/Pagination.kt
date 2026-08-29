package com.example.ui

internal data class PaginationWindow(
    val pageIndex: Int,
    val pageCount: Int,
    val fromIndex: Int,
    val toIndex: Int
)

internal fun calculatePaginationWindow(
    totalItems: Int,
    requestedPage: Int,
    pageSize: Int
): PaginationWindow {
    require(pageSize > 0) { "pageSize deve ser maior que zero" }
    val safeTotal = totalItems.coerceAtLeast(0)
    val pageCount = if (safeTotal == 0) 1 else ((safeTotal - 1) / pageSize) + 1
    val pageIndex = requestedPage.coerceIn(0, pageCount - 1)
    val fromIndex = (pageIndex * pageSize).coerceAtMost(safeTotal)
    val toIndex = (fromIndex + pageSize).coerceAtMost(safeTotal)
    return PaginationWindow(pageIndex, pageCount, fromIndex, toIndex)
}
