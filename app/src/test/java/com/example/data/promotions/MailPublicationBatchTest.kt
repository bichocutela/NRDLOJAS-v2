package com.example.data.promotions

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MailPublicationBatchTest {
    @Test fun failureKeepsOtherDocumentsPublishingAndIdentifiesRetryItems() = runBlocking {
        val progress=mutableListOf<Int>()
        val attempted=mutableListOf<String>()
        val result=publishMailBatch(listOf("first","bad","last"),{ _,index,total ->
            assertEquals(3,total); progress.add(index)
        }) { item ->
            attempted.add(item)
            if(item=="bad") error("Confira a validade")
        }
        assertEquals(listOf("first","bad","last"),attempted)
        assertEquals(listOf(1,2,3),progress)
        assertEquals(listOf("first","last"),result.published)
        assertEquals(listOf("bad" to "Confira a validade"),result.failures)
    }

    @Test fun leavingTheScreenCancelsTheBatchWithoutStartingMoreDocuments() = runBlocking {
        val attempted=mutableListOf<String>()
        try {
            publishMailBatch(listOf("first","last"),{ _,_,_ -> }) { item ->
                attempted.add(item)
                throw CancellationException("Screen closed")
            }
            fail("Cancellation must propagate")
        } catch(_: CancellationException) { }
        assertEquals(listOf("first"),attempted)
    }
}
