package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.Normalizer
import java.util.Locale

/**
 * Migração silenciosa dos códigos do catálogo para os códigos da tabela oficial
 * recebida em 01/09/2026.
 *
 * Regra:
 * - quando a tabela informa EAN/código de leitura, ele passa a ser o código do produto;
 * - quando não informa EAN, é usado o código interno 25....
 *
 * A linha 4028 / 2004055 foi propositalmente ignorada porque está riscada na tabela.
 */
object CatalogCodeMigration {
    private const val TAG = "CatalogCodeMigration"
    private const val MIGRATION_ID = "ean_table_2026_09_01_v1"

    data class MigrationResult(
        val applied: Boolean,
        val migrated: Int = 0,
        val alreadyCorrect: Int = 0,
        val notFound: Int = 0,
        val message: String? = null,
    )

    internal data class Rule(
        val internalCode: String,
        val targetCode: String,
        val description: String,
    )

    private val rules = listOf(
        Rule("2025662", "20222", "AGUA MIN SANTA MARIA PREMIUM 20L"),
        Rule("2000622", "7898049880072", "AGUA MIN SANTA MARIA S/GAS GAL 10L"),
        Rule("2000623", "7898049880119", "AGUA MIN SANTA MARIA S/GAS GAL 20L"),
        Rule("2000637", "7898005514881", "AGUA MIN STER BOM S/GAS GAL 10L"),
        Rule("2000642", "7898005516205", "AGUA MIN STER BOM VASILHAME 10L"),
        Rule("2000643", "45105", "AGUA MIN STER BOM VASILHAME 20L"),
        Rule("254584", "254584", "CAMARAO INT CONG CANANOR AG KG"),
        Rule("2000290", "964", "CESTA BASICA FAB PROPRIA N01"),
        Rule("2000291", "965", "CESTA BASICA FAB PROPRIA N02"),
        Rule("2000292", "597", "CESTA BASICA FAB PROPRIA N03"),
        Rule("2014350", "14178", "CESTA BASICA FAB PROPRIA PRODUMAR"),
        Rule("2000293", "692", "CESTA BASICA FAB PROPRIA SIMAS"),
        Rule("2014600", "845", "CESTA NATALINA AMETISTA FAB PROPRIA"),
        Rule("2014601", "847", "CESTA NATALINA CRISTAL FAB PROPRIA"),
        Rule("2017795", "20163", "CESTA NATALINA CRISTAL SF FAB PROPRIA"),
        Rule("2014602", "848", "CESTA NATALINA ESMERALDA FAB PROPRIA"),
        Rule("2014603", "846", "CESTA NATALINA OPALA FAB PROPRIA"),
        Rule("2014604", "849", "CESTA NATALINA PEROLA FAB PROPRIA"),
        Rule("2015192", "20095", "CESTA NATALINA QUARTZO 2 FAB PROPRIA"),
        Rule("2014745", "850", "CESTA NATALINA QUARTZO FAB PROPRIA"),
        Rule("2017738", "20164", "CESTA NATALINA QUARTZO SF FAB PROPRIA"),
        Rule("2014746", "851", "CESTA NATALINA RUBI FAB PROPRIA"),
        Rule("2014747", "863", "CESTA NATALINA SAFIRA FAB PROPRIA"),
        Rule("2014748", "852", "CESTA NATALINA TOPAZIO FAB PROPRIA"),
        Rule("2014749", "853", "CESTA NATALINA TURMALINA FAB PROPRIA"),
        Rule("2017739", "20162", "CESTA NATALINA TURMALINA SF FAB PROPRIA"),
        Rule("2014413", "9051", "COLETE PROMOTOR TACTEL AZ UN G"),
        Rule("2004053", "10783", "GARRAFAO PVC INDAIA 20L VASILHAME UN"),
        Rule("2004054", "1529", "GARRAFAO PVC SANTA MARIA 10L UN"),
        Rule("2004056", "17080", "GARRAFAO PVC STER BOM 10L UN"),
        Rule("2004057", "7898005514829", "GARRAFAO PVC STER BOM 20L UN"),
        Rule("255373", "255373", "PAO ESPECIAL FAB PROPRIA KG"),
        Rule("255474", "255474", "PAO FERM NAT AZEITONA KG"),
        Rule("255476", "255476", "PAO FERM NAT CALABRESA KG"),
        Rule("255473", "255473", "PAO FERM NAT GORGONZOLA E NOZES KG"),
        Rule("255475", "255475", "PAO FERM NAT TOMATE SECO KG"),
        Rule("250090", "250090", "PAO FRANCES FAB PROPRIA KG"),
        Rule("254469", "254469", "PAO ITALIANO FAB PROPRIA KG"),
        Rule("255345", "255345", "PAO RUSTICO FAB PROPRIA KG"),
        Rule("253291", "253291", "SALG FOLHADO FAB PROPRIA FRANGO UN"),
        Rule("253292", "253292", "SALG FOLHADO FAB PROPRIA PRES/QUEI UN"),
        Rule("253301", "253301", "SALG FOLHADO FAB PROPRIA QUEIJO UN"),
        Rule("253302", "253302", "SALG FOLHADO FAB PROPRIA SALSICHA UN"),
        Rule("254135", "254135", "TAXA DE ENTREGA INTERMUNICIPAL"),
    )

    internal fun rulesForTest(): List<Rule> = rules

    suspend fun applySilently(): MigrationResult {
        if (!FirebaseService.isFirebaseConfigured()) {
            return MigrationResult(false, message = "Firebase não configurado.")
        }
        if (!hasManagementAccess()) {
            return MigrationResult(false, message = "Sessão sem permissão de Mestre/Admin.")
        }

        val firestore = FirebaseFirestore.getInstance()
        val markerRef = firestore.collection("config").document(MIGRATION_ID)

        return try {
            val marker = markerRef.get().await()
            if (marker.getBoolean("done") == true) {
                return MigrationResult(
                    applied = false,
                    migrated = marker.getLong("migrated")?.toInt() ?: 0,
                    alreadyCorrect = marker.getLong("alreadyCorrect")?.toInt() ?: 0,
                    notFound = marker.getLong("notFound")?.toInt() ?: 0,
                    message = "Migração já aplicada."
                )
            }

            // Cria uma cópia de segurança antes de qualquer troca de documento/código.
            val safetySnapshot = FirebaseService.createCatalogSnapshot("pre_$MIGRATION_ID")
            if (safetySnapshot == null) {
                return MigrationResult(false, message = "Não foi possível criar o backup de segurança do catálogo.")
            }

            val snapshot = firestore.collection("products").get().await()
            val documents = snapshot.documents
            val byCode = documents.associateBy { document ->
                document.getString("code")?.trim().orEmpty().ifBlank { document.id }
            }
            val byName = documents.groupBy { document ->
                canonicalName(document.getString("name").orEmpty())
            }

            var migrated = 0
            var alreadyCorrect = 0
            var notFound = 0

            rules.forEach { rule ->
                val targetAlreadyExists = byCode[rule.targetCode]
                if (targetAlreadyExists != null) {
                    alreadyCorrect += 1
                    return@forEach
                }

                val source = byCode[rule.internalCode]
                    ?: byName[canonicalName(rule.description)]?.singleOrNull()

                if (source == null) {
                    notFound += 1
                    return@forEach
                }

                if (source.id == rule.targetCode || source.getString("code")?.trim() == rule.targetCode) {
                    alreadyCorrect += 1
                    return@forEach
                }

                val changed = firestore.runTransaction { transaction ->
                    val sourceRef = source.reference
                    val targetRef = firestore.collection("products").document(rule.targetCode)
                    val latestSource = transaction.get(sourceRef)
                    val latestTarget = transaction.get(targetRef)

                    if (!latestSource.exists() || latestTarget.exists()) {
                        false
                    } else {
                        val migratedData = HashMap<String, Any?>(latestSource.data.orEmpty()).apply {
                            this["code"] = rule.targetCode
                            this["updatedAt"] = FieldValue.serverTimestamp()
                        }
                        transaction.set(targetRef, migratedData)
                        transaction.delete(sourceRef)
                        true
                    }
                }.await()

                if (changed) migrated += 1 else alreadyCorrect += 1
            }

            markerRef.set(
                mapOf(
                    "done" to true,
                    "migrationId" to MIGRATION_ID,
                    "migrated" to migrated,
                    "alreadyCorrect" to alreadyCorrect,
                    "notFound" to notFound,
                    "appliedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            Log.i(
                TAG,
                "Migração concluída: migrated=$migrated alreadyCorrect=$alreadyCorrect notFound=$notFound"
            )
            MigrationResult(
                applied = true,
                migrated = migrated,
                alreadyCorrect = alreadyCorrect,
                notFound = notFound,
            )
        } catch (error: Exception) {
            Log.e(TAG, "Falha na migração de códigos", error)
            MigrationResult(false, message = error.message)
        }
    }

    private fun hasManagementAccess(): Boolean {
        val email = FirebaseAuth.getInstance().currentUser?.email?.lowercase(Locale.ROOT)
        return email == "admin@nrdlojas.com" || email == "mestre@nrdlojas.com"
    }

    private fun canonicalName(value: String): String = Normalizer
        .normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .replace(Regex("[^a-z0-9]+"), "")
}
