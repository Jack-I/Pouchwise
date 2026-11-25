package app.pouchwise.ui.screens

import app.pouchwise.R
import app.pouchwise.model.Release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseLabelTest {
    @Test
    fun theListNamesASlowOrFastPouchAndLeavesANormalOneUnsaid() {
        assertEquals(R.string.slow_release, releaseLabel(Release.SLOW))
        assertEquals(R.string.fast_release, releaseLabel(Release.FAST))
        assertNull(releaseLabel(Release.NORMAL))
    }
}
