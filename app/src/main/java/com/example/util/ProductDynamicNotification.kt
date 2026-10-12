package com.example.util

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.json.JSONObject

object ProductDynamicNotification {
    fun decorate(context: Context, builder: NotificationCompat.Builder, type: String, title: String,
                 content: ProductNotificationContent, pending: PendingIntent?) {
        builder.setContentText(content.detail).setStyle(NotificationCompat.BigTextStyle().bigText(content.detail))
        pending?.let { builder.setContentIntent(it).addAction(ProfileNotificationRouting.iconRes(type), "Ir pro app", it) }
        val barcode = barcode(content.barcode)
        if (barcode != null) builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(barcode).setSummaryText(content.detail))
        builder.setColor(NotificationAppearance.palette(context).accent)
        if (pending == null || !XiaomiSuperIslandUpdateNotifier.canShowProductFocus(context)) return
        val logo = XiaomiSuperIslandUpdateNotifier.brandImage(context)
        val pictures = Bundle().apply {
            putParcelable("miui.focus.pic_product", Icon.createWithBitmap(logo))
            if (barcode != null) putParcelable("miui.focus.pic_barcode", Icon.createWithBitmap(barcode))
        }
        val parameter = JSONObject().apply {
            put("protocol", 1)
            put("business", "nrd_product_event")
            put("islandFirstFloat", false)
            put("enableFloat", false)
            put("updatable", false)
            put("reopen", "close")
            put("filterWhenNoPermission", false)
            put("ticker", if (type == "NEW_PRODUCT") "Novo" else "Alterado")
            put("tickerPic", "miui.focus.pic_product")
            put("aodTitle", title)
            put("param_island", JSONObject().apply {
                put("islandProperty", 1)
                put("islandOrder", false)
                put("dismissIsland", false)
                put("islandTimeout", 8)
                put("smallIslandArea", JSONObject().put("picInfo", JSONObject().put("type", 1).put("pic", "miui.focus.pic_product")))
                put("bigIslandArea", JSONObject().put("imageTextInfoLeft", JSONObject().apply {
                    put("type", 1)
                    put("picInfo", JSONObject().put("type", 1).put("pic", "miui.focus.pic_product"))
                    put("textInfo", JSONObject().put("title", if (type == "NEW_PRODUCT") "Novo produto" else "Produto alterado").put("content", content.description))
                }))
                put("shareData", JSONObject().put("title", title).put("content", content.detail).put("pic", "miui.focus.pic_product"))
            })
            put("baseInfo", JSONObject().put("type", 2).put("title", content.description).put("content", "Código: ${content.code}").put("extraTitle", title))
            put("hintInfo", JSONObject().apply {
                put("type", 2)
                put("title", content.barcode)
                put("content", "Código de barras")
                if (barcode != null) put("picContent", "miui.focus.pic_barcode")
                put("actionInfo", JSONObject().put("action", "miui.focus.action_product"))
            })
        }
        NotificationAppearance.applyFocus(context, parameter, pictures)
        builder.addExtras(Bundle().apply {
            putString("miui.focus.param", JSONObject().put("param_v2", parameter).toString())
            putBundle("miui.focus.pics", pictures)
            putBundle("miui.focus.actions", Bundle().apply {
                putParcelable("miui.focus.action_product", Notification.Action.Builder(
                    Icon.createWithResource(context, ProfileNotificationRouting.iconRes(type)), "Ir pro app", pending).build())
            })
        })
        // Product events are dismissible alerts, not fake ongoing activities.
    }

    private fun barcode(value: String): Bitmap? = runCatching {
        if (value.isBlank() || value.length > 60 || value.any { it.code !in 32..126 }) return@runCatching null
        val raw = MultiFormatWriter().encode(value, BarcodeFormat.CODE_128, 1, 96)
        val quiet = 16
        val scale = (768 / (raw.width + quiet * 2)).coerceAtLeast(1)
        val width = (raw.width + quiet * 2) * scale
        val pixels = IntArray(width * 128) { Color.WHITE }
        for (x in 0 until raw.width) if (raw[x, 0]) {
            for (dx in 0 until scale) for (y in 16 until 112) pixels[y * width + (x + quiet) * scale + dx] = Color.BLACK
        }
        Bitmap.createBitmap(pixels, width, 128, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}
