package com.example.bubbel.ui.theme

import org.junit.Assert.assertEquals
import androidx.compose.ui.graphics.toArgb
import org.junit.Test

class BubbelColorTest {
    @Test
    fun approvedPaletteUsesTheSpecifiedColours() {
        assertEquals(0xFFFFEE8C.toInt(), PastelYellow.toArgb())
        assertEquals(0xFFFFB58C.toInt(), SunsetRed.toArgb())
        assertEquals(0xFFFFC88C.toInt(), SunsetOrange.toArgb())
        assertEquals(0xFFFFDB8C.toInt(), LightOrange.toArgb())
        assertEquals(0xFFFDFF8C.toInt(), CreamYellow.toArgb())
        assertEquals(0xFFEAFF8C.toInt(), BananaGreen.toArgb())
        assertEquals(0xFFD6FF8C.toInt(), LimeGreen.toArgb())
        assertEquals(0xFFC3FF8C.toInt(), LightGreen.toArgb())
        assertEquals(0xFF8F8770.toInt(), DarkGrey.toArgb())
        assertEquals(0xFF584B00.toInt(), DarkBrown.toArgb())
        assertEquals(0xFF8C9DFF.toInt(), SoftBlue.toArgb())
    }
}
