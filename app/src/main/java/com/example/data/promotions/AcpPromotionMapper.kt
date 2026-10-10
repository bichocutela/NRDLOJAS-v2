package com.example.data.promotions

import com.example.data.Promotion
import com.example.data.PromotionProduct
import com.example.data.acp.*
import java.math.BigDecimal
import java.math.RoundingMode

internal val promotionFamilies = setOf(AcpOfferFamily.DE_POR, AcpOfferFamily.TAKE_PAY, AcpOfferFamily.CLUB)
internal fun AcpOfferFamily.promotionLabel(): String = when (this) {
    AcpOfferFamily.TAKE_PAY -> "Leve e pague"
    AcpOfferFamily.CLUB -> "Clube"
    else -> "De/Por"
}

internal fun acpPromotion(store: String, product: AcpProduct, offer: AcpOffer,
    validity: AcpOfferValidity?, previousCategory: String? = null): Promotion {
    require(offer.family in promotionFamilies)
    val label = offer.family.promotionLabel()
    val code = product.code.ifBlank { product.barcode }
    val metadata = product.detailsJson?.let { org.json.JSONObject(it) }
    val category = PromotionCategory.resolve(product.description, metadata?.optString("nrdCategory"), previousCategory,
        authoritative = metadata?.optString("catalog_category_source") == "official")
    val previous = offer.referencePrice
    val current = offer.price
    val condition = when (offer.family) {
        AcpOfferFamily.TAKE_PAY -> offer.headline
        AcpOfferFamily.CLUB -> "Clube"
        else -> if (previous != null && current != null && previous > current && previous > BigDecimal.ZERO)
            "${previous.subtract(current).multiply(BigDecimal(100)).divide(previous, 0, RoundingMode.HALF_UP)}%" else null
    }
    return Promotion(offerIdentity(store, code, label), label, category, product.imageUrl,
        AcpPromotionsRepository.isoDate(validity?.startDate), AcpPromotionsRepository.isoDate(validity?.endDate),
        listOf(PromotionProduct(code, product.description,
            current?.brl()?.let { if (offer.family == AcpOfferFamily.TAKE_PAY) "$it/un" else it },
            previous?.brl(), condition, store, product.imageUrl, barcode = product.barcode, detailsJson = product.detailsJson)))
}
