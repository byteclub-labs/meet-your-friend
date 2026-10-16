package com.byteclub.meetyourfriend.share

import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsAppLauncherTest {
    @Test
    fun buildsEncodedWhatsappUrl() {
        assertEquals(
            "https://wa.me/971501234567?text=Hi%20Priya%2C%20great%21",
            WhatsAppLauncher.buildUrl("+971501234567", "Hi Priya, great!"),
        )
    }
}
