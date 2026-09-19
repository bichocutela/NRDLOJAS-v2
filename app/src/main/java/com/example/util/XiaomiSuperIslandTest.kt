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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class XiaomiIslandProbe(
    val protocolVersion: Int,
    val islandSupported: Boolean,
    val focusPermission: Boolean,
    val notificationsEnabled: Boolean
) {
    val hyperOsLabel: String
        get() = when (protocolVersion) {
            3 -> "HyperOS 3 · Super Island"
            2 -> "HyperOS 2 · Focus Notification"
            1 -> "HyperOS 1 · Focus Notification"
            else -> "Protocolo Xiaomi não detectado"
        }
}

object XiaomiSuperIslandTest {
    private const val CHANNEL_ID = "xiaomi_super_island_test"
    private const val NOTIFICATION_ID = 93031

    suspend fun probe(context: Context): XiaomiIslandProbe = withContext(Dispatchers.IO) {
        val protocol = runCatching {
            Settings.System.getInt(
                context.contentResolver,
                "notification_focus_protocol",
                0
            )
        }.getOrDefault(0)

        val supported = runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getDeclaredMethod(
                "getBoolean",
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            (method.invoke(null, "persist.sys.feature.island", false) as? Boolean) ?: false
        }.getOrDefault(false)

        val focusAllowed = runCatching {
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

        XiaomiIslandProbe(
            protocolVersion = protocol,
            islandSupported = supported,
            focusPermission = focusAllowed,
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        )
    }

    suspend fun sendTest(context: Context): String {
        val probe = probe(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return "Permissão de notificações do Android está desativada."
        }

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
                put("business", "nrd_master_test")
                put("islandFirstFloat", true)
                put("enableFloat", true)
                put("updatable", true)
                put("filterWhenNoPermission", false)
                put("ticker", "NRD")
                put("aodTitle", "NRD · teste Super Island")
                put("param_island", JSONObject().apply {
                    put("islandProperty", 1)
                    put("islandTimeout", 120)
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
                                put("title", "Teste")
                                put("content", "Super Island")
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
                        put("title", "NRD Códigos")
                        put("content", "Teste da Xiaomi Super Island")
                        put("shareContent", "NRD Códigos")
                        put("pic", "miui.focus.pic_imageText")
                    })
                })
                put("baseInfo", JSONObject().apply {
                    put("title", "NRD Códigos")
                    put("content", "Teste da Xiaomi Super Island")
                    put("colorTitle", "#1976D2")
                    put("type", 2)
                })
                put("hintInfo", JSONObject().apply {
                    put("type", 1)
                    put("title", "Teste Mestre")
                })
            })
        }.toString()

        val extras = Bundle().apply {
            putString("miui.focus.param", islandJson)
            putBundle("miui.focus.pics", pics)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_default)
            .setContentTitle("NRD Códigos")
            .setContentText("Teste da Xiaomi Super Island")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Teste Mestre · Xiaomi Super Island / Focus Notification"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .addExtras(extras)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)

        return when {
            probe.protocolVersion >= 3 && probe.focusPermission ->
                "Teste enviado com parâmetros da Super Island. Veja a ilha e a central de notificações."
            probe.protocolVersion >= 3 ->
                "HyperOS 3 detectado, mas a permissão Focus/Super Island não está liberada para o NRD. A notificação comum foi enviada como fallback."
            probe.protocolVersion in 1..2 ->
                "Focus Notification detectada (protocolo " + probe.protocolVersion + "). A notificação de teste foi enviada."
            else ->
                "Notificação de teste enviada, mas o protocolo Super Island não foi detectado neste aparelho."
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Teste Xiaomi Super Island",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Canal de teste restrito ao Painel Mestre para Xiaomi Super Island."
        }
        manager.createNotificationChannel(channel)
    }
}
