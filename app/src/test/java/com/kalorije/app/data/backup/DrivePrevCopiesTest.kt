package com.kalorije.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DrivePrevCopiesTest {
    private val main = "kalorije_backup.json"

    @Test
    fun `ime prethodne kopije nosi vreme i ne poklapa se sa glavnim fajlom`() {
        val name = DrivePrevCopies.prevCopyName(main, 1_700_000_000_000)

        assertEquals("kalorije_backup_prev_1700000000000.json", name)
        assertEquals(1_700_000_000_000, DrivePrevCopies.prevCopyTimestamp(main, name))
        assertNull(DrivePrevCopies.prevCopyTimestamp(main, main))
        assertNull(DrivePrevCopies.prevCopyTimestamp(main, "kalorije_backup_prev_abc.json"))
    }

    @Test
    fun `cuva se pet najnovijih kopija a glavni fajl se nikad ne brise`() {
        val names = (1..8).map { DrivePrevCopies.prevCopyName(main, it * 1000L) } + main

        val toPrune = DrivePrevCopies.namesToPrune(main, names)

        assertEquals(
            listOf(3000L, 2000L, 1000L).map { DrivePrevCopies.prevCopyName(main, it) },
            toPrune,
        )
        assertFalse(toPrune.contains(main))
    }
}
