package com.atala.app

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmokeTest {
    @Test
    fun appStarts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(context)
    }
}
