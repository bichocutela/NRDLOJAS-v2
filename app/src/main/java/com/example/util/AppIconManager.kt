package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object AppIconManager {
    private const val TAG = "AppIconManager"
    private const val DEFAULT_ICON = "multicolor"

    private val aliases = linkedMapOf(
        "multicolor" to "com.example.MainActivityMulticolor",
        "red" to "com.example.MainActivityRed",
        "green" to "com.example.MainActivityGreen",
        "blue" to "com.example.MainActivityBlue",
        "orange" to "com.example.MainActivityOrange",
        "gold" to "com.example.MainActivityGold"
    )

    /**
     * Mantém exatamente um activity-alias de launcher habilitado.
     * Os aliases são todos desabilitados antes de o alvo ser habilitado para
     * eliminar estados antigos que poderiam manter vários ícones no launcher.
     */
    fun applyIcon(context: Context, iconName: String): Boolean {
        val packageManager = context.packageManager
        val selectedIcon = iconName.takeIf { it in aliases } ?: DEFAULT_ICON
        val targetAlias = aliases.getValue(selectedIcon)

        return try {
            aliases.values.forEach { aliasName ->
                packageManager.setComponentEnabledSetting(
                    ComponentName(context.packageName, aliasName),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, targetAlias),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            verifyIconState(context, targetAlias)
        } catch (exception: Exception) {
            Log.e(TAG, "Não foi possível aplicar o ícone $selectedIcon", exception)
            false
        }
    }

    /**
     * Considera o estado padrão apenas para Multicolorido, pois ele é o único
     * alias declarado como habilitado no manifesto de uma instalação nova.
     */
    private fun verifyIconState(context: Context, expectedAlias: String): Boolean {
        val packageManager = context.packageManager
        val enabledAliases = aliases.values.filter { aliasName ->
            val state = packageManager.getComponentEnabledSetting(
                ComponentName(context.packageName, aliasName)
            )
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
                    aliasName == aliases.getValue(DEFAULT_ICON))
        }

        return enabledAliases.size == 1 && enabledAliases.singleOrNull() == expectedAlias
    }

    /**
     * Restaura o estado consistente na abertura do aplicativo. Valores salvos
     * inválidos ou ausentes voltam para o ícone Multicolorido.
     */
    fun ensureValidIconState(context: Context, savedIcon: String?) {
        val selectedIcon = savedIcon?.takeIf { it in aliases } ?: DEFAULT_ICON
        val expectedAlias = aliases.getValue(selectedIcon)

        if (!verifyIconState(context, expectedAlias)) {
            Log.w(TAG, "Estado de aliases inválido. Restaurando $selectedIcon")
            applyIcon(context, selectedIcon)
        }
    }
}
