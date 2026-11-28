package ru.lisurgut.oalab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomePortraitBoundsTest {
    @Test fun keepsAnInsetSilhouetteWithPadding() {
        val mask = IntArray(100 * 140) { index ->
            if (index % 100 in 24..75 && index / 100 in 10..128) -1 else 0
        }
        assertEquals(HomePortraitBounds(22, 8, 78, 131), visiblePortrait(mask, 100, 140))
    }

    @Test fun rejectsATorsoCutOffAtTheBottom() {
        val mask = IntArray(100 * 140) { index ->
            if (index % 100 in 24..75 && index / 100 in 10..139) -1 else 0
        }
        assertNull(visiblePortrait(mask, 100, 140))
    }

    @Test fun rejectsAClippedSide() {
        val mask = IntArray(100 * 140) { index ->
            if (index % 100 in 0..75 && index / 100 in 10..128) -1 else 0
        }
        assertNull(visiblePortrait(mask, 100, 140))
    }
}
