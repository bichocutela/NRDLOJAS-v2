package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.R

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
            
            // Legacy channel just in case
            val legacy = NotificationChannel(CHANNEL_ID, "Atualizações de Produtos", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(legacy)
        }
    }


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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    
    fun showNotification(context: Context, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        
        val targetChannelId = when (title) {
            "Produto adicionado" -> "product_added"
            "Código alterado" -> "product_code_changed"
            else -> CHANNEL_ID
        }

        val builder = NotificationCompat.Builder(context, targetChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

}
