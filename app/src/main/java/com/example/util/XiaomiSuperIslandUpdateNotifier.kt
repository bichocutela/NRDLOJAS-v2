package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
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
 * Em aparelhos sem HyperOS/Super Island/permissão Focus, não faz nada.
 * O DownloadManager e as notificações normais continuam sendo a fonte principal.
 */
object XiaomiSuperIslandUpdateNotifier {
    private const val CHANNEL_ID = "xiaomi_super_island_updates_v1"
    private const val NOTIFICATION_ID = 93040
    private const val BUSINESS = "nrd_app_update"

    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val protocol = runCatching {
            Settings.System.getInt(
                context.contentResolver,
                "notification_focus_protocol",
                0
            )
        }.getOrDefault(0)

        if (protocol < 3) return false

        return runCatching {
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
    }

    fun showDownload(context: Context, versionTag: String, progress: Int) {
        if (!isAvailable(context)) return
        val safeProgress = progress.coerceIn(0, 100)
        post(
            context = context,
            title = "NRD · baixando $versionTag",
            content = "Baixando atualização… $safeProgress%",
            detail = "$safeProgress%",
            progress = safeProgress,
            ongoing = safeProgress < 100
        )
    }

    fun showReady(context: Context, versionTag: String) {
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

    fun showFailed(context: Context) {
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

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
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

        val pics = Bundle().apply {
            putParcelable(
                "miui.focus.pic_imageText",
                Icon.createWithResource(context, R.drawable.ic_notification_default)
            )
        }

        val islandJson = JSONObject().apply {
            put("param_v2", JSONObject().apply {
                put("protocol", 1)
                put("business", BUSINESS)
                put("islandFirstFloat", true)
                put("enableFloat", true)
                put("updatable", true)
                put("reopen", "reopen")
                put("filterWhenNoPermission", false)
                put("ticker", if (progress == null) "NRD" else "NRD $progress%")
                put("aodTitle", content)
                put("param_island", JSONObject().apply {
                    put("islandProperty", 1)
                    put("islandOrder", true)
                    put("dismissIsland", false)
                    put("islandTimeout", 300)
                    put("highlightColor", "#1976D2")
                    put("bigIslandArea", JSONObject().apply {
                        put("imageTextInfoLeft", JSONObject().apply {
                            put("type", 1)
                            put("picInfo", JSONObject().apply {
                                put("type", 1)
                                put("pic", "miui.focus.pic_imageText")
                            })
                            put("miui.focus.paramtextInfo", JSONObject().apply {
                                put("frontTitle", "NRD")
                                put("title", detail)
                                put("content", content)
                                put("useHighLight", true)
                            })
                        })
                        put("picInfo", JSONObject().apply {
                            put("type", 1)
                            put("pic", "miui.focus.pic_imageText")
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
                put("baseInfo", JSONObject().apply {
                    put("title", title)
                    put("content", content)
                    put("colorTitle", "#1976D2")
                    put("type", 2)
                })
                put("hintInfo", JSONObject().apply {
                    put("type", 1)
                    put("title", detail)
                })
            })
        }.toString()

        val extras = Bundle().apply {
            putString("miui.focus.param", islandJson)
            putBundle("miui.focus.pics", pics)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_default)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$detail · $content"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .addExtras(extras)

        if (progress != null) {
            builder.setProgress(100, progress.coerceIn(0, 100), false)
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Atualizações na Super Island",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Progresso de atualização do NRD em aparelhos Xiaomi compatíveis."
        }
        manager.createNotificationChannel(channel)
    }
}
