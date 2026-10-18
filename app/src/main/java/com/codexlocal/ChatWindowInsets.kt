package com.codexlocal

import android.content.res.Configuration
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.codexlocal.databinding.ActivityMainBinding

/** DrawerLayout positions its children without respecting its own padding. */
internal object ChatWindowInsets {
    fun install(binding: ActivityMainBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.mainContent.setPadding(
                safe.left, safe.top, safe.right, maxOf(safe.bottom, keyboard.bottom)
            )
            // Keep the drawer clear of system bars, independently of the keyboard.
            binding.tiroir.setPadding(safe.left, safe.top, safe.right, safe.bottom)

            val typing = insets.isVisible(WindowInsetsCompat.Type.ime())
            binding.status.isVisible = !typing
            binding.input.maxLines = if (!typing) 6 else if (
                binding.root.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            ) 2 else 3
            // This screen handles insets once; descendants must not add them again.
            WindowInsetsCompat.CONSUMED
        }
        binding.root.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: android.view.View) {
                ViewCompat.requestApplyInsets(view)
            }
            override fun onViewDetachedFromWindow(view: android.view.View) = Unit
        })
        ViewCompat.requestApplyInsets(binding.root)
    }
}
