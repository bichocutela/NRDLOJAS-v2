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
     * Troca o ícone mantendo exatamente um activity-alias de launcher habilitado.
     * Em caso de falha na confirmação, restaura o alias que estava consistente
     * antes da tentativa — ou Multicolorido quando não houver estado recuperável.
     */
    fun applyIcon(context: Context, iconName: String): Boolean {
        val selectedIcon = normalizeIcon(iconName)
        val selectedAlias = aliases.getValue(selectedIcon)
        val recoveryAlias = currentSingleEnabledAlias(context) ?: aliases.getValue(DEFAULT_ICON)

        return try {
            setOnlyAliasEnabled(context, selectedAlias)
            if (verifyIconState(context, selectedAlias)) {
                true
            } else {
                Log.e(TAG, "A troca para $selectedIcon não produziu um único alias habilitado.")
                restoreSingleAlias(context, recoveryAlias)
                false
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Não foi possível aplicar o ícone $selectedIcon", exception)
            restoreSingleAlias(context, recoveryAlias)
            false
        }
    }

    /**
     * Reconcilia o estado na abertura do aplicativo. Retorna a chave válida que
     * pode ser persistida; retorna null se não foi possível garantir um único alias.
     */
    fun ensureValidIconState(context: Context, savedIcon: String?): String? {
        val selectedIcon = normalizeIcon(savedIcon)
        val expectedAlias = aliases.getValue(selectedIcon)

        if (verifyIconState(context, expectedAlias)) {
            return selectedIcon
        }

        Log.w(TAG, "Estado de aliases inválido. Restaurando $selectedIcon")
        return if (applyIcon(context, selectedIcon)) selectedIcon else null
    }

    private fun normalizeIcon(iconName: String?): String =
        iconName?.takeIf { it in aliases } ?: DEFAULT_ICON

    private fun setOnlyAliasEnabled(context: Context, enabledAlias: String) {
        val packageManager = context.packageManager

        aliases.values.forEach { aliasName ->
            packageManager.setComponentEnabledSetting(
                componentName(context, aliasName),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        packageManager.setComponentEnabledSetting(
            componentName(context, enabledAlias),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun restoreSingleAlias(context: Context, preferredAlias: String) {
        runCatching {
            setOnlyAliasEnabled(context, preferredAlias)
        }.onFailure { exception ->
            Log.e(TAG, "Não foi possível restaurar um único alias de launcher", exception)
        }
    }

    private fun currentSingleEnabledAlias(context: Context): String? =
        enabledAliases(context).singleOrNull()

    private fun verifyIconState(context: Context, expectedAlias: String): Boolean {
        val enabledAliases = enabledAliases(context)
        return enabledAliases.size == 1 && enabledAliases.single() == expectedAlias
    }

    private fun enabledAliases(context: Context): List<String> {
        val packageManager = context.packageManager
        val defaultAlias = aliases.getValue(DEFAULT_ICON)

        return aliases.values.filter { aliasName ->
            when (packageManager.getComponentEnabledSetting(componentName(context, aliasName))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> aliasName == defaultAlias
                else -> false
            }
        }
    }

    private fun componentName(context: Context, aliasName: String) =
        ComponentName(context.packageName, aliasName)
}
