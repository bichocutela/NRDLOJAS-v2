package com.example.util

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import com.example.MainActivity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.R
import org.json.JSONObject

/**
 * Camada opcional para acompanhar downloads reais de atualização na Super Island.
 *
 * Usa Focus em Xiaomi e Live Updates em Android 16+ compatível com outras marcas.
 * O DownloadManager e as notificações normais continuam sendo a fonte principal.
 */
object XiaomiSuperIslandUpdateNotifier {
    private const val CHANNEL_ID = "xiaomi_super_island_updates_v2"
    private const val NOTIFICATION_ID = 93040
    private var capabilityCheckedAt = -30_000L
    private var focusAllowed = false
    private var lastProgress = -1
    private var lastVersion = ""
    private var lastAppearance = ""
    private var brandBitmap: Bitmap? = null
    private var lastPostedAt = -5_000L
    private const val BUSINESS = "nrd_app_update"

    @Synchronized
    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        if (!NotificationAppearance.enabled(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val manufacturer = (Build.MANUFACTURER + " " + Build.BRAND).lowercase(java.util.Locale.ROOT)
        if (listOf("xiaomi", "redmi", "poco").none { it in manufacturer }) { focusAllowed = false; return Build.VERSION.SDK_INT >= 36 }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val now = SystemClock.elapsedRealtime()
        if (now - capabilityCheckedAt < 30_000L) return focusAllowed || Build.VERSION.SDK_INT >= 36
        capabilityCheckedAt = now
        val protocol = runCatching {
            Settings.System.getInt(
                context.contentResolver,
                "notification_focus_protocol",
                0
            )
        }.getOrDefault(0)

        if (protocol < 3) { focusAllowed = false; return Build.VERSION.SDK_INT >= 36 }

        focusAllowed = runCatching {
            val extras = Bundle().apply {
                putString("package", context.packageName)
            }
            context.contentResolver.call(
                Uri.parse("content://miui.statusbar.notification.public"),
                "canShowFocus",
                null,
                extras
            )?.getBoolean("canShowFocus", false) ?: false
        }.getOrDefault(false)
        return focusAllowed || Build.VERSION.SDK_INT >= 36
    }

    /** Xiaomi-only Focus permission. Do not promote one-off product alerts as Android Live Updates. */
    @Synchronized
    internal fun canShowProductFocus(context: Context): Boolean {
        return isAvailable(context) && focusAllowed
    }

    @Synchronized
    fun showDownload(context: Context, versionTag: String, progress: Int) {
        if (!isAvailable(context)) return
        val safeProgress = progress.coerceIn(0, 100)
        val now = SystemClock.elapsedRealtime()
        val appearance = NotificationAppearance.palette(context).key
        if (lastVersion == versionTag && lastProgress >= 0 && appearance == lastAppearance) {
            if (!XiaomiProgressPolicy.shouldPost(lastProgress, safeProgress, now - lastPostedAt)) return
        }
        lastVersion = versionTag; lastProgress = safeProgress; lastPostedAt = now; lastAppearance = appearance
        post(
            context = context,
            title = "NRD · baixando $versionTag",
            content = "Baixando atualização…",
            detail = "$safeProgress%",
            progress = safeProgress,
            ongoing = true
        )
    }

    @Synchronized
    fun showReady(context: Context, versionTag: String) {
        lastProgress = -1; lastVersion = ""
        if (!isAvailable(context)) return
        post(
            context = context,
            title = "NRD · atualização pronta",
            content = "$versionTag pronto para instalar",
            detail = "Concluído",
            progress = 100,
            ongoing = false
        )
    }

    @Synchronized
    fun showFailed(context: Context) {
        lastProgress = -1; lastVersion = ""
        if (!isAvailable(context)) return
        post(
            context = context,
            title = "NRD · falha no download",
            content = "Não foi possível baixar a atualização",
            detail = "Falhou",
            progress = null,
            ongoing = false
        )
    }

    @Synchronized
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        lastProgress = -1; lastVersion = ""; lastPostedAt = -5_000L
    }

    private fun post(
        context: Context,
        title: String,
        content: String,
        detail: String,
        progress: Int?,
        ongoing: Boolean
    ) {
        createChannel(context)

        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val logo = brandImage(context)
        val statusIcon = if (ongoing) R.drawable.ic_notification_app_update else if (progress == 100) R.drawable.ic_notification_document_done else R.drawable.ic_notification_document_attention
        val pics = Bundle().apply {
            putParcelable(
                "miui.focus.pic_imageText",
                Icon.createWithBitmap(logo)
            )
            putParcelable("miui.focus.pic_status", Icon.createWithResource(context, statusIcon))
        }

        val islandJson = JSONObject().apply {
            put("param_v2", JSONObject().apply {
                put("protocol", 1)
                put("business", BUSINESS)
                put("islandFirstFloat", false)
                put("enableFloat", false)
                put("updatable", true)
                put("reopen", "close")
                put("filterWhenNoPermission", false)
                put("ticker", if (progress == null) "NRD" else "NRD $progress%")
                put("tickerPic", "miui.focus.pic_imageText")
                put("aodTitle", content)
                put("param_island", JSONObject().apply {
                    put("islandProperty", 1)
                    put("islandOrder", false)
                    put("dismissIsland", false)
                    put("islandTimeout", if (ongoing) 300 else 8)
                    put("bigIslandArea", JSONObject().apply {
                        put("imageTextInfoLeft", JSONObject().apply {
                            put("type", 1)
                            put("picInfo", JSONObject().apply {
                                put("type", 1)
                                put("pic", "miui.focus.pic_imageText")
                            })
                            put("textInfo", JSONObject().apply {
                                put("title", detail)
                                put("content", content)
                                put("showHighlightColor", false)
                                put("narrowFont", true)
                            })
                        })
                        put("picInfo", JSONObject().apply {
                            put("type", 1)
                            put("pic", "miui.focus.pic_status")
                        })
                    })
                    put("smallIslandArea", JSONObject().apply {
                        put("picInfo", JSONObject().apply {
                            put("type", 1)
                            put("pic", "miui.focus.pic_imageText")
                        })
                    })
                    put("shareData", JSONObject().apply {
                        put("title", title)
                        put("content", content)
                        put("shareContent", "NRD Códigos")
                        put("pic", "miui.focus.pic_imageText")
                    })
                })
                put("bgInfo", JSONObject().put("type", 1).put("colorBg", if (dark) "#171A21" else "#FAFBFF"))
                put("baseInfo", JSONObject().apply {
                    put("title", title)
                    put("content", content)
                    put("colorTitle", "#25324A")
                    put("colorTitleDark", "#F3F5FC")
                    put("colorContent", "#43516A")
                    put("colorContentDark", "#CCD3E1")
                    put("type", 2)
                })
                put("hintInfo", JSONObject().apply {
                    put("type", 1)
                    put("title", detail)
                    if (!ongoing && progress == 100) put("actionInfo", JSONObject().put("action", "miui.focus.action_install"))
                })
            })
        }
        NotificationAppearance.applyFocus(context, islandJson.getJSONObject("param_v2"), pics)

        val target = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_ABOUT, true)
            .putExtra(MainActivity.EXTRA_INSTALL_READY_UPDATE, !ongoing && progress == 100)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, target, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val extras = Bundle().apply {
            if (!ongoing && progress == 100 && focusAllowed) {
                putBundle("miui.focus.actions", Bundle().apply {
                    putParcelable("miui.focus.action_install", android.app.Notification.Action.Builder(Icon.createWithResource(context, statusIcon), "Instalar", pending).build())
                })
            }
            if (focusAllowed) {
                putString("miui.focus.param", islandJson.toString())
                putBundle("miui.focus.pics", pics)
            }
            if (Build.VERSION.SDK_INT >= 36) putBoolean("android.requestPromotedOngoing", ongoing)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(statusIcon)
            .setLargeIcon(logo)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pending)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .addExtras(extras)

        if (!ongoing && progress == 100) builder.addAction(statusIcon, "Instalar", pending)

        if (progress != null) {
            builder.setProgress(100, progress.coerceIn(0, 100), false)
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    /** Keep the original logo lettering and aspect ratio, with a small cached notification bitmap. */
    @Synchronized
    internal fun brandImage(context: Context): Bitmap {
        brandBitmap?.let { return it }
        val decoded = BitmapFactory.decodeResource(context.resources, R.drawable.nrd_logo_multicolor,
            BitmapFactory.Options().apply { inSampleSize = 8; inScaled = false }) ?: return Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val height = 128f * decoded.height / decoded.width
        Canvas(bitmap).drawBitmap(decoded, null, RectF(0f, (128f - height) / 2f, 128f, (128f + height) / 2f), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        decoded.recycle()
        brandBitmap = bitmap
        return bitmap
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Download de atualização",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progresso de atualização do NRD na interface nativa dos aparelhos compatíveis."
        }
        manager.createNotificationChannel(channel)
    }
}
