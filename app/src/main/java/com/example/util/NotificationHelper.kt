package com.example.util

import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.R
import com.example.MainActivity

import android.util.Log
import android.widget.Toast

object NotificationHelper {
    private const val CHANNEL_ID = "new_products_channel"
    private const val NOTIFICATION_ID = 1001

    private var currentToast: Toast? = null

    fun showToast(context: Context, message: String, length: Int = Toast.LENGTH_SHORT) {
        currentToast?.cancel()
        currentToast = Toast.makeText(context, message, length)
        currentToast?.show()
    }

    
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val channelAdded = NotificationChannel("product_added", "Produto adicionado", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notificações quando novos produtos são adicionados"
            }
            notificationManager.createNotificationChannel(channelAdded)

            val channelCodeChanged = NotificationChannel("product_code_changed", "Produto alterado", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notificações quando produtos ou seus códigos são alterados"
            }
            notificationManager.createNotificationChannel(channelCodeChanged)

            val channelSuggestionFixed = NotificationChannel("suggestion_fixed", "Sugestão corrigida", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notificações quando uma sugestão do usuário é corrigida"
            }
            notificationManager.createNotificationChannel(channelSuggestionFixed)

            val channelAppUpdate = NotificationChannel("app_update", "Atualizações do aplicativo", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notificações quando uma nova versão do aplicativo está disponível"
            }
            notificationManager.createNotificationChannel(channelAppUpdate)

            val channelPromotionUpdates = NotificationChannel("promotion_updates", "Ofertas da loja favorita", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notificações de novas ofertas da loja favorita"
            }
            notificationManager.createNotificationChannel(channelPromotionUpdates)

            val channelBenefitUpdates = NotificationChannel("benefit_updates", "Atualizações do convênio", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notificações de liberação e compras realizadas no convênio"
            }
            notificationManager.createNotificationChannel(channelBenefitUpdates)

            val channelHoursUpdates = NotificationChannel("hours_updates", "Banco de horas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notificações de alterações no banco de horas"
            }
            notificationManager.createNotificationChannel(channelHoursUpdates)
            notificationManager.createNotificationChannel(NotificationChannel("personal_clock_reminders", "Lembretes do meu ponto", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Batidas de ponto e registros pendentes da sua jornada manual"
            })
            notificationManager.createNotificationChannel(NotificationChannel("point_updates", "Atualizações de ponto", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notificações de novos dados no espelho de ponto"
            })

            notificationManager.createNotificationChannel(NotificationChannel("schedule_reminders", "Minhas folgas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisos das folgas cadastradas manualmente no perfil ou no ponto"
            })

            val channelInstallations = NotificationChannel("installation_updates", "Instalações e atualizações", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisos exclusivos do Mestre quando alguém instala ou atualiza o NRD V2"
            }
            notificationManager.createNotificationChannel(channelInstallations)
            notificationManager.createNotificationChannel(NotificationChannel("mail_documents", "Novos documentos por e-mail", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Avisos exclusivos do Mestre quando relatórios chegam ao NRD" })
            
            notificationManager.createNotificationChannel(NotificationChannel("encartes", "Novos encartes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisos de novos encartes de Natal e região"
            })
            // Legacy channel just in case
            notificationManager.createNotificationChannel(NotificationChannel("training_updates", "Novos treinamentos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisos de novos treinamentos da Academia NRD"
            })
            val legacy = NotificationChannel(CHANNEL_ID, "Atualizações de Produtos", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(legacy)
        }
    }


    private fun smallIconForType(type: String): Int = ProfileNotificationRouting.iconRes(type)

    private fun largeIconForNotification(context: Context) =
        XiaomiSuperIslandUpdateNotifier.brandImage(context)

    fun showProductEventNotification(context: Context, type: String, productName: String, oldName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val title = if (type == "NEW_PRODUCT") "Novo produto adicionado" else "Produto atualizado"
        val text = when (type) {
            "NEW_PRODUCT" -> "$productName foi adicionado ao aplicativo."
            "CODE_CHANGED" -> "O código de $productName foi atualizado."
            "NAME_CHANGED" -> "$oldName agora aparece como $productName."
            "INFO_CHANGED" -> "As informações de $productName foram atualizadas."
            else -> "$productName foi atualizado."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIconForType(type))
            .setLargeIcon(largeIconForNotification(context))
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    
    fun channelIdForType(type: String): String? = when (type) {
        "NEW_PRODUCT" -> "product_added"
        "CODE_CHANGED", "INFO_CHANGED", "NAME_CHANGED" -> "product_code_changed"
        "SUGGESTION_FIXED" -> "suggestion_fixed"
        "APP_UPDATE" -> "app_update"
        "PROMOTION_UPDATED" -> "promotion_updates"
        "NEW_TRAINING" -> "training_updates"
        "NEW_ENCARTE" -> "encartes"
        "BENEFIT_RELEASED", "BENEFIT_PURCHASE" -> "benefit_updates"
        "HOURS_UPDATED" -> "hours_updates"
        "POINT_UPDATED" -> "point_updates"
        "CLOCK_REMINDER", "CLOCK_PENDING" -> "personal_clock_reminders"
        "TODAY_OFF", "TOMORROW_OFF", "SCHEDULE_INSERTED", "SCHEDULE_CHANGED", "CLOCK_TODAY_OFF", "CLOCK_TOMORROW_OFF" -> "schedule_reminders"
        "NEW_INSTALLATION" -> "installation_updates"
        "NEW_MAIL_DOCUMENTS" -> "mail_documents"
        else -> null
    }

    fun showUpdateNotification(context: Context, versionTag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("NotificationHelper", "Notificação de atualização ignorada: permissão ausente")
            return
        }

        val pendingIntent = appUpdatePendingIntent(context, versionTag)

        val builder = NotificationCompat.Builder(context, "app_update")
            .setSmallIcon(R.drawable.ic_notification_default)
            .setLargeIcon(largeIconForNotification(context))
            .setContentTitle("Atualização disponível")
            .setContentText("A versão $versionTag está disponível. Toque para baixar agora.")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(2001, builder.build())
    }

    private fun appUpdatePendingIntent(context: Context, versionTag: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_ABOUT, true)
            if (!versionTag.isNullOrBlank()) putExtra(MainActivity.EXTRA_UPDATE_TAG, versionTag)
        }
        return PendingIntent.getActivity(
            context,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun promotionsPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_PROMOTIONS, true)
            putExtra("type", "PROMOTION_UPDATED")
        }
        return PendingIntent.getActivity(
            context,
            3001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun productPendingIntent(context: Context, type: String, productCode: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = android.net.Uri.parse("nrd://product/" + android.net.Uri.encode(productCode) + "/" + type)
            putExtra(MainActivity.EXTRA_OPEN_PRODUCT_CODE, productCode)
            putExtra("type", type)
        }
        return PendingIntent.getActivity(
            context,
            (type + productCode).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun profileNotificationPendingIntent(context: Context, type: String, section: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_PROFILE_SECTION, section)
            putExtra("type", type)
        }
        return PendingIntent.getActivity(
            context,
            (type + section).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }


    fun showNotification(
        context: Context,
        type: String,
        title: String,
        body: String,
        productCode: String? = null,
        notificationTag: String? = null,
        productBarcode: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w("NotificationHelper", "Notificação local ignorada: permissão POST_NOTIFICATIONS ausente; type=$type")
                return
            }
        }

        val targetChannelId = channelIdForType(type)
        if (targetChannelId == null) {
            Log.w("NotificationHelper", "Notificação local ignorada: type inválido ($type)")
            return
        }

        Log.d("NotificationHelper", "Exibindo notificação local: type=$type, canal=$targetChannelId")
        val builder = NotificationCompat.Builder(context, targetChannelId)
            .setSmallIcon(smallIconForType(type))
            .setLargeIcon(largeIconForNotification(context))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (notificationTag != null) builder.setOnlyAlertOnce(true)

        when (type) {
            "NEW_PRODUCT", "CODE_CHANGED", "INFO_CHANGED", "NAME_CHANGED" -> {
                val content = ProductNotificationContent.from(body, productCode, productBarcode)
                val pending = content.code.takeIf { it.isNotBlank() }?.let { productPendingIntent(context, type, it) }
                ProductDynamicNotification.decorate(context, builder, type, title, content, pending)
            }
            "NEW_TRAINING" -> builder.setContentIntent(PendingIntent.getActivity(context, 3003,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("type", "NEW_TRAINING")
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            "NEW_ENCARTE" -> builder.setContentIntent(PendingIntent.getActivity(context, 3002,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("type", "NEW_ENCARTE")
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            "NEW_MAIL_DOCUMENTS" -> builder.setContentIntent(PendingIntent.getActivity(context, 3004,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("type", "NEW_MAIL_DOCUMENTS")
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            "APP_UPDATE" -> builder.setContentIntent(appUpdatePendingIntent(context))
            "PROMOTION_UPDATED" -> builder.setContentIntent(promotionsPendingIntent(context))
            else -> ProfileNotificationRouting.profileSection(type)?.let { section ->
                builder.setContentIntent(profileNotificationPendingIntent(context, type, section))
            }
        }

        with(NotificationManagerCompat.from(context)) {
            if (notificationTag != null) notify(notificationTag, 0, builder.build())
            else notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

}
