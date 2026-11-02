package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindDownTest {
    @Test fun warnsOnceAtFiveThenOnceAtOne() {
        val warned = mutableSetOf<Int>()
        assertNull(WindDown.due(12f, warned))
        assertNull(WindDown.due(5.1f, warned))
        assertEquals(5, WindDown.due(4.9f, warned))
        assertNull(WindDown.due(3f, warned))        // no repeat
        assertNull(WindDown.due(1.2f, warned))
        assertEquals(1, WindDown.due(0.9f, warned))
        assertNull(WindDown.due(0.1f, warned))
    }

    @Test fun startingLowWarnsOnlyOnce() {
        val warned = mutableSetOf<Int>()
        assertEquals(5, WindDown.due(3f, warned))   // 3 min left at session start: one warning
        assertNull(WindDown.due(2f, warned))
        assertEquals(1, WindDown.due(1f, warned))
    }

    @Test fun startingVeryLowWarnsOnceWithMostUrgent() {
        val warned = mutableSetOf<Int>()
        assertEquals(1, WindDown.due(0.5f, warned))
        assertNull(WindDown.due(0.4f, warned))
    }

    @Test fun exactlyOnTheThresholdCounts() {
        val warned = mutableSetOf<Int>()
        assertEquals(5, WindDown.due(5f, warned))
    }
}
