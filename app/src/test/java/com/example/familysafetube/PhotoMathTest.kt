package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoMathTest {
    @Test fun rotation_fromExif() {
        assertEquals(0, PhotoMath.rotationDegrees(1)); assertEquals(90, PhotoMath.rotationDegrees(6))
        assertEquals(180, PhotoMath.rotationDegrees(3)); assertEquals(270, PhotoMath.rotationDegrees(8))
        assertEquals(0, PhotoMath.rotationDegrees(0)); assertEquals(0, PhotoMath.rotationDegrees(99))
    }

    @Test fun sampleSize_staysAtOrAboveTarget() {
        assertEquals(1, PhotoMath.sampleSize(1000, 800, 1280))      // already small
        assertEquals(1, PhotoMath.sampleSize(2000, 1500, 1280))     // halving would drop below the target
        assertEquals(2, PhotoMath.sampleSize(4000, 3000, 1280))
        assertEquals(4, PhotoMath.sampleSize(8000, 6000, 1280))
        assertEquals(1, PhotoMath.sampleSize(0, 0, 1280)); assertEquals(1, PhotoMath.sampleSize(4000, 3000, 0))
    }

    @Test fun sampleSize_usesTheShorterSide() {
        assertEquals(1, PhotoMath.sampleSize(8000, 1500, 1280))     // a panorama must not be over-reduced
    }

    @Test fun scaledSize_capsTheLongestSide() {
        assertEquals(1280 to 960, PhotoMath.scaledSize(4000, 3000, 1280))
        assertEquals(960 to 1280, PhotoMath.scaledSize(3000, 4000, 1280))
        assertEquals(800 to 600, PhotoMath.scaledSize(800, 600, 1280))     // never enlarges
        assertEquals(1280 to 1, PhotoMath.scaledSize(100000, 10, 1280))    // never collapses to zero
    }
}
