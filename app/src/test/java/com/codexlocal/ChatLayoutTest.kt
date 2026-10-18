package com.codexlocal

import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.codexlocal.databinding.ActivityMainBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "mdpi")
class ChatLayoutTest {
    private fun screen(): ActivityMainBinding {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_CodexLocal)
        return ActivityMainBinding.inflate(LayoutInflater.from(context)).also(ChatWindowInsets::install)
    }

    private fun layout(b: ActivityMainBinding, width: Int, height: Int, keyboard: Int = 0,
                       left: Int = 0, right: Int = 0) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 24))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(left, 0, right, 0))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboard))
            .setVisible(WindowInsetsCompat.Type.ime(), keyboard > 0)
            .build()
        ViewCompat.dispatchApplyWindowInsets(b.root, insets)
        b.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        b.root.layout(0, 0, width, height)
    }

    private fun bounds(b: ActivityMainBinding, view: View): Rect {
        val rect = Rect(0, 0, view.width, view.height)
        b.root.offsetDescendantRectToMyCoords(view, rect)
        return rect
    }

    @Test fun inputAndSendStayAboveKeyboardOnCompactPhone() {
        val b = screen()
        b.input.setText("Première ligne\nDeuxième ligne\nTroisième ligne\nQuatrième ligne")
        layout(b, 360, 640, keyboard = 280)
        for (view in listOf(b.input, b.send)) {
            val rect = bounds(b, view)
            assertTrue("Champ et envoi au-dessus du clavier", rect.bottom <= 360)
            assertTrue("Champ entièrement dans l'écran", rect.top >= 24 && rect.left >= 0 && rect.right <= 360)
            assertTrue("Cible utilisable", rect.height() >= 48)
        }
        assertEquals(View.GONE, b.status.visibility)
        assertTrue(b.messages.height > 0)
    }

    @Test fun tabletContentIsCenteredAndWidthIsLimited() {
        val b = screen()
        layout(b, 1200, 1000)
        val rect = bounds(b, b.chatContent)
        assertEquals(840, rect.width())
        assertEquals(1200, rect.left + rect.right)
        assertEquals(976, bounds(b, b.send).bottom + 8)
    }

    @Test fun landscapeCutoutAndKeyboardDoNotHideComposer() {
        RuntimeEnvironment.setQualifiers("land-mdpi")
        val b = screen()
        b.input.setText("1\n2\n3\n4\n5\n6")
        layout(b, 800, 360, keyboard = 160, left = 40, right = 24)
        val rect = bounds(b, b.input)
        assertTrue(rect.left >= 40 && rect.right <= 776)
        assertTrue(rect.top >= 24 && rect.bottom <= 200)
        assertEquals(2, b.input.maxLines)
        assertTrue(b.messages.height > 0)
    }

    @Test fun repeatedInsetsAndRotationDoNotAccumulatePadding() {
        val b = screen()
        repeat(3) { layout(b, 360, 640, keyboard = 280) }
        assertEquals(280, b.mainContent.paddingBottom)
        assertEquals(24, b.tiroir.paddingBottom)
        layout(b, 800, 360, left = 40)
        assertEquals(24, b.mainContent.paddingBottom)
        assertEquals(40, b.mainContent.paddingLeft)
        assertEquals(View.VISIBLE, b.status.visibility)
        assertEquals(6, b.input.maxLines)
        assertTrue(bounds(b, b.input).bottom <= 336)
    }
}
