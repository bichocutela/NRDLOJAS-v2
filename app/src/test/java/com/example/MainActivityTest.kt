package com.example

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class MainActivityTest {

    @Test
    fun testActivityStarts() {
        try {
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            System.out.println("Activity started successfully!")
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
