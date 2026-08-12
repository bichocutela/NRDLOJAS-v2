import re
content = open("app/src/main/java/com/example/util/NotificationHelper.kt").read()

content = content.replace('val name = "Novos Produtos"', 'val name = "Atualizações de Produtos"')

show_notif_method = """
    fun showNotification(context: Context, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}"""
content = content.replace("}\n}", "}\n" + show_notif_method)
open("app/src/main/java/com/example/util/NotificationHelper.kt", "w").write(content)
