package com.example.data.promotions

import kotlinx.coroutines.CancellationException

internal data class MailPublicationBatchResult<T>(
    val published: List<T>,
    val failures: List<Pair<T, String>>
)

/** A failed document stays retryable without preventing other stores from publishing. */
internal suspend fun <T> publishMailBatch(
    documents: List<T>,
    onProgress: (T, Int, Int) -> Unit,
    publish: suspend (T) -> Unit
): MailPublicationBatchResult<T> {
    val published=mutableListOf<T>()
    val failures=mutableListOf<Pair<T,String>>()
    documents.forEachIndexed { index, document ->
        onProgress(document,index+1,documents.size)
        try {
            publish(document)
            published.add(document)
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(failure: Exception) {
            failures.add(document to (failure.message ?: "Não foi possível publicar. Confira a conexão e tente novamente."))
        }
    }
    return MailPublicationBatchResult(published,failures)
}
