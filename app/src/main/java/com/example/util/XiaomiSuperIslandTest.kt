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
import com.example.data.XiaomiIslandScenarioStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        if (!canPostNotifications(context)) {
            return "Permissão de notificações do Android está desativada."
        }

        createChannel(context)
        postIslandNotification(
            context = context,
            title = "NRD Códigos",
            content = "Teste da Xiaomi Super Island",
            detail = "Teste Mestre",
            progress = null,
            ongoing = false
        )

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

    /**
     * Atualiza a MESMA notificação várias vezes para testar o comportamento em tempo real.
     * Isso ajuda a separar três coisas: notificação Android comum, Focus Notification e
     * atualização da Super Island. O teste é local e fica restrito ao Painel Mestre.
     */
    suspend fun runProgressTest(context: Context): String {
        if (!canPostNotifications(context)) {
            return "Permissão de notificações do Android está desativada."
        }

        createChannel(context)

        for (progress in 0..100 step 10) {
            val finished = progress >= 100
            postIslandNotification(
                context = context,
                title = if (finished) "NRD · concluído" else "NRD · sincronizando",
                content = if (finished) "Sincronização concluída" else "Carregando… " + progress + "%",
                detail = if (finished) "100%" else progress.toString() + "%",
                progress = progress,
                ongoing = !finished
            )
            if (!finished) delay(1500)
        }

        delay(2500)
        postIslandNotification(
            context = context,
            title = "NRD Códigos",
            content = "Teste de carregamento concluído",
            detail = "Concluído",
            progress = null,
            ongoing = false
        )

        return "Teste concluído. A mesma notificação foi atualizada de 0% a 100% em tempo real."
    }

    /**
     * Simula uma atualização real do NRD. O roteiro vem do Firestore sempre que
     * o teste começa, então textos, percentuais e tempos podem ser alterados
     * remotamente sem gerar outro APK.
     */
    suspend fun runFakeAppUpdateTest(context: Context): String {
        if (!canPostNotifications(context)) {
            return "Permissão de notificações do Android está desativada."
        }

        createChannel(context)
        val scenario = XiaomiIslandScenarioStore.loadOrSeedDefault()

        if (!scenario.enabled) {
            return "O cenário remoto da Super Island está desativado."
        }

        scenario.phases.forEach { phase ->
            postIslandNotification(
                context = context,
                title = phase.title,
                content = phase.content,
                detail = phase.detail,
                progress = phase.progress,
                ongoing = phase.progress < 100
            )
            delay(phase.waitMillis)
        }

        val source = if (scenario.remote) "nuvem" else "padrão local"
        return "Teste fake concluído com o cenário \"" + scenario.name + "\" v" + scenario.version +
            " (" + source + ")."
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun postIslandNotification(
        context: Context,
        title: String,
        content: String,
        detail: String,
        progress: Int?,
        ongoing: Boolean
    ) {
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
                put("ticker", if (progress == null) "NRD" else "NRD " + progress + "%")
                put("aodTitle", content)
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
                    // Mantém exatamente a estrutura que já apareceu corretamente no aparelho.
                    // O progresso é enviado pelo texto/baseInfo e pela barra Android, sem inventar
                    // um template de ilha pequena que o SystemUI possa rejeitar.
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail + " · " + content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setAutoCancel(!ongoing)
            .addExtras(extras)

        if (progress != null) {
            builder.setProgress(100, progress.coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, false)
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
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
