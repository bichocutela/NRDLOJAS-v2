package com.example.data.training

import org.junit.Assert.*
import org.junit.Test

class TrainingImageUrlTest {
    private val id = "1AbCdEfGhIjKlMnOpQrStUvWxYz"
    @Test fun sharedDriveImageLinksUseTheSameDirectImageResolverInBothDisplays() {
        val expected = "https://lh3.googleusercontent.com/d/$id"
        listOf("https://drive.google.com/file/d/$id/view?usp=sharing", "https://drive.google.com/open?usp=sharing&id=$id",
            "https://drive.google.com/uc?export=view&id=$id", "https://drive.google.com/thumbnail?sz=w800&id=$id", "https://docs.google.com/uc?id=$id")
            .forEach { assertEquals(expected, TrainingImageUrl.normalize(it)) }
    }
    @Test fun resourceKeysAreRetainedForSharedFilesThatRequireThem() {
        assertEquals("https://drive.google.com/thumbnail?id=$id&sz=w1200&resourcekey=0-key", TrainingImageUrl.normalize("https://drive.google.com/file/d/$id/view?resourcekey=0-key&usp=sharing"))
    }
    @Test fun directImageLinksAndUnrelatedHostsAreNotRewritten() {
        val cdn = "https://cdn.example.com/image.webp"
        assertEquals(cdn, TrainingImageUrl.normalize(" $cdn "))
        val spoof = "https://drive.google.com.example.com/file/d/$id/view"
        assertEquals(spoof, TrainingImageUrl.normalize(spoof))
        assertEquals("https://drive.google.com/drive/folders/abc", TrainingImageUrl.normalize("https://drive.google.com/drive/folders/abc"))
    }
}
