package com.wayy.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationFlowTest {

    @Ignore("E2E navigation requires live GPS and routing services")
    @Test
    fun navigationFlow_smoke() {
        // TODO: Implement full navigation flow with location simulation.
    }
}
