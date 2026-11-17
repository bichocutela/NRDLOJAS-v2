package com.example.data.promotions

import kotlinx.coroutines.delay

/** Observe only the scan requested by a button click; never starts a background polling job. */
internal suspend fun awaitMailScan(
    requestId: String,
    readStatus: suspend () -> MailImportRepository.ScanStatus,
    pause: suspend () -> Unit = { delay(3_000) },
    maxChecks: Int = 45
) {
    repeat(maxChecks) { attempt ->
        val status = readStatus()
        if (status.finishedId == requestId) {
            check(!status.failed) { "Não foi possível concluir a busca no Gmail. Toque em Buscar para tentar novamente." }
            return
        }
        check(status.requestId == null || status.requestId == requestId) {
            "Outra busca foi solicitada. Toque em Buscar para acompanhar a solicitação atual."
        }
        if (attempt + 1 < maxChecks) pause()
    }
    error("A busca ainda não terminou. Toque em Buscar para continuar ou em Atualizar fila para conferir os documentos já recebidos.")
}
