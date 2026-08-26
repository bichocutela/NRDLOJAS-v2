package com.example.util

import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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

            val channelCodeChanged = NotificationChannel("product_code_changed", "Código alterado", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notificações quando o código de um produto é alterado"
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
            
            // Legacy channel just in case
            val legacy = NotificationChannel(CHANNEL_ID, "Atualizações de Produtos", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(legacy)
        }
    }


    private fun smallIconForType(type: String): Int = when (type) {
        "NEW_PRODUCT" -> R.drawable.ic_notification_product_added
        "CODE_CHANGED" -> R.drawable.ic_notification_code_changed
        "SUGGESTION_FIXED" -> R.drawable.ic_notification_suggestion_fixed
        else -> R.drawable.ic_notification_default
    }

    private fun largeIconForNotification(context: Context) =
        BitmapFactory.decodeResource(context.resources, R.drawable.icon_multicolor_original)

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
        "CODE_CHANGED" -> "product_code_changed"
        "SUGGESTION_FIXED" -> "suggestion_fixed"
        "APP_UPDATE" -> "app_update"
        "PROMOTION_UPDATED" -> "promotion_updates"
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

    fun showNotification(context: Context, type: String, title: String, body: String) {
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (type == "APP_UPDATE") {
            builder.setContentIntent(appUpdatePendingIntent(context))
        }

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

}
