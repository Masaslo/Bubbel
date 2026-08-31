package com.example.bubbel

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class FaceGeometryTest {
    @Test
    fun stressedFaceFrownsAndRelaxedFaceSmiles() {
        val stressed = faceGeometry(relaxed = 0f)
        val relaxed = faceGeometry(relaxed = 1f)

        assertTrue(stressed.eyeBend < 0f)
        assertTrue(stressed.mouthBend < 0f)
        assertTrue(relaxed.eyeBend > 0f)
        assertTrue(relaxed.mouthBend > 0f)
    }

    @Test
    fun activationFadesEmojiColorAndRevealsBubbleRadially() {
        val halfway = bubbleAnimationVisuals(progress = 0.5f)

        assertEquals(0.5f, halfway.emojiColorProgress, 0.001f)
        assertEquals(180f, halfway.bubbleSweepDegrees, 0.001f)
    }

    @Test
    fun everySoundStartsOutsideTheVisibleScreen() {
        assertTrue(soundStartingPoints().all { (x, y) ->
            x !in 0f..1f || y !in 0f..1f
        })
    }

    @Test
    fun soundMaskGrowsFromEmojiToBubbleBoundary() {
        assertEquals(100f, soundMaskRadius(100f, 140f, 0f), 0.001f)
        assertEquals(120f, soundMaskRadius(100f, 140f, 0.5f), 0.001f)
        assertEquals(140f, soundMaskRadius(100f, 140f, 1f), 0.001f)
    }
}
