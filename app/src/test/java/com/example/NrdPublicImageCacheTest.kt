package com.example

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NrdPublicImageCacheTest {
    @Test fun onlyPublicVersionedImagesAreLongCached() {
        val root="https://kkayksyzksexoarpfxyj.supabase.co/storage/v1/object/public/nrdlojas-images/"
        assertTrue(NrdPublicImageCache.canKeepForWeek((root+"theme_backgrounds/red/ff5141b7-a333-4ba7-b415-7a93f7903b09.jpg").toHttpUrl()))
        assertTrue(NrdPublicImageCache.canKeepForWeek((root+"dynamic-pages/pwa/1789853479406_ffab7e85-8dad-46ec-b76e-d1e2fbe8115b.png").toHttpUrl()))
        assertFalse(NrdPublicImageCache.canKeepForWeek((root+"config/appearance-settings.json").toHttpUrl()))
        assertFalse(NrdPublicImageCache.canKeepForWeek((root+"dynamic-pages/1788811946090_a2c03ccf-e732-44bb-935c-75238dc107bc.mp4").toHttpUrl()))
        assertFalse(NrdPublicImageCache.canKeepForWeek((root+"products/7898906922105_1788115501300.jpg").toHttpUrl()))
        assertFalse(NrdPublicImageCache.canKeepForWeek("https://drive.google.com/uc?export=download&id=abc".toHttpUrl()))
        assertFalse(NrdPublicImageCache.canKeepForWeek("https://example.com/storage/v1/object/public/nrdlojas-images/theme_backgrounds/red/ff5141b7-a333-4ba7-b415-7a93f7903b09.jpg".toHttpUrl()))
        assertFalse(NrdPublicImageCache.canKeepForWeek("https://kkayksyzksexoarpfxyj.supabase.co/storage/v1/object/sign/nrdlojas-images/theme_backgrounds/red/ff5141b7-a333-4ba7-b415-7a93f7903b09.jpg".toHttpUrl()))
    }
}
