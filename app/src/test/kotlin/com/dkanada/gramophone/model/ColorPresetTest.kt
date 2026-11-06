package com.dkanada.gramophone.model

import org.junit.Assert.*
import org.junit.Test

class ColorPresetTest {
    @Test
    fun parse_readsAStoredPreset() {
        assertEquals(ColorPreset.TEAL, ColorPreset.parse("TEAL", false))
        assertEquals(ColorPreset.NEUTRAL, ColorPreset.parse("NEUTRAL", false))
        assertEquals(ColorPreset.WALLPAPER, ColorPreset.parse("WALLPAPER", true))
    }

    @Test
    fun parse_fallsBackInsteadOfFailingTheLaunch() {
        assertEquals(ColorPreset.DEFAULT, ColorPreset.parse(null, true))
        assertEquals(ColorPreset.DEFAULT, ColorPreset.parse("REMOVED_SEED", true))
    }

    @Test
    fun parse_storedWallpaperFallsBackWithoutDynamicColor() {
        assertEquals(ColorPreset.DEFAULT, ColorPreset.parse("WALLPAPER", false))
    }

    @Test
    fun available_offersWallpaperOnlyWithDynamicColor() {
        assertTrue(ColorPreset.available(true).contains(ColorPreset.WALLPAPER))
        assertFalse(ColorPreset.available(false).contains(ColorPreset.WALLPAPER))
        assertEquals(ColorPreset.values().size - 1, ColorPreset.available(false).size)
    }

    @Test
    fun overlay_followsTheTheme_withBlackUsingTheDarkOne() {
        val preset = ColorPreset.INDIGO

        assertNotEquals(preset.overlay(Theme.LIGHT), preset.overlay(Theme.DARK))
        assertEquals(preset.overlay(Theme.DARK), preset.overlay(Theme.BLACK))
    }
}
