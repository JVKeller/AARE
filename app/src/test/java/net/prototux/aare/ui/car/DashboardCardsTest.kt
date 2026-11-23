package net.prototux.aare.ui.car

import net.prototux.aare.ui.car.DashCard.CALENDAR
import net.prototux.aare.ui.car.DashCard.MAP
import net.prototux.aare.ui.car.DashCard.MEDIA
import net.prototux.aare.ui.car.DashCard.NOTIFICATION
import net.prototux.aare.ui.car.DashCard.WEATHER
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardCardsTest {
    @Test
    fun parseDropsUnknownAndRepeatedIds() {
        assertEquals(listOf(MEDIA, MAP), DashCard.parse(listOf("media", "gone", "map", "media")))
    }

    @Test
    fun slotsFollowTheOrderAndSkipCardsWithNothingToShow() {
        // No calendar event: the latest notification takes its slot, as before.
        assertEquals(listOf(MAP, MEDIA, NOTIFICATION), dashboardSlots(DashCard.DEFAULT, 3) { it != CALENDAR })
        assertEquals(listOf(MAP, MEDIA, CALENDAR), dashboardSlots(DashCard.DEFAULT, 3) { true })
    }

    @Test
    fun slotsStopAtTheScreensRoom() {
        assertEquals(listOf(MEDIA, MAP), dashboardSlots(listOf(MEDIA, MAP, WEATHER), 2) { true })
    }

    @Test
    fun theMapStandsInWhenNothingIsLeft() {
        assertEquals(listOf(MAP), dashboardSlots(listOf(CALENDAR), 3) { false })
    }

    @Test
    fun showingAddsAtTheEndAndHidingRemoves() {
        assertEquals(listOf(MAP, MEDIA, WEATHER), listOf(MAP, MEDIA).withCard(WEATHER, true))
        assertEquals(listOf(MAP, MEDIA), listOf(MAP, MEDIA).withCard(MEDIA, true))
        assertEquals(listOf(MEDIA), listOf(MAP, MEDIA).withCard(MAP, false))
    }

    @Test
    fun theLastCardCantBeHidden() {
        assertEquals(listOf(MAP), listOf(MAP).withCard(MAP, false))
    }

    @Test
    fun movingSwapsAndClamps() {
        assertEquals(listOf(MEDIA, MAP, CALENDAR), listOf(MAP, MEDIA, CALENDAR).moved(1, -1))
        assertEquals(listOf(MAP, MEDIA, CALENDAR), listOf(MAP, MEDIA, CALENDAR).moved(0, -1))
        assertEquals(listOf(MEDIA, CALENDAR, MAP), listOf(MAP, MEDIA, CALENDAR).moved(0, 5))
    }

    @Test
    fun wideScreensWithRoomGetThreeColumns() {
        // 1920x720 and 2560x720 at 160 dpi, less the side rail (~90 dp)
        assertEquals(DashLayout.THREE_COLUMNS, dashboardLayout(1830f, 700f, 12f))
        assertEquals(DashLayout.THREE_COLUMNS, dashboardLayout(2470f, 700f, 12f))
    }

    @Test
    fun wideButCrampedScreensStackTheSideCards() {
        // Wide enough in ratio, but the third card would be a sliver: big card, then 2 over 3.
        assertEquals(DashLayout.BIG_AND_STACKED, dashboardLayout(1000f, 460f, 12f))
        assertEquals(DashLayout.BIG_AND_STACKED, dashboardLayout(1200f, 560f, 12f))
    }

    @Test
    fun ordinaryScreensKeepTheirLayouts() {
        assertEquals(DashLayout.BIG_AND_STACKED, dashboardLayout(1100f, 620f, 12f)) // 1280x720 class
        assertEquals(DashLayout.NARROW, dashboardLayout(500f, 400f, 12f))
        assertEquals(DashLayout.PORTRAIT, dashboardLayout(720f, 1200f, 12f))
    }
}
