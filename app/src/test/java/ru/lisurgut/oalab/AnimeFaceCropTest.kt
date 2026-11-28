package ru.lisurgut.oalab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeFaceCropTest {
    @Test fun detectedFaceFitsInsideSquareNearImageEdge() {
        val face = FaceBox(2, 9, 51, 60)
        val crop = squareAroundFace(200, 350, face)
        assertEquals(0, crop.x)
        assertEquals(0, crop.y)
        assertTrue(crop.x + crop.side >= face.x + face.width)
        assertTrue(crop.y + crop.side >= face.y + face.height)
    }

    @Test fun fallbackKeepsTheUpperPartOfATallPortrait() {
        val crop = squareAroundFace(200, 360, null)
        assertEquals(164, crop.side)
        assertEquals(18, crop.x)
        assertTrue(crop.y + crop.side / 2 < 180)
    }
}
