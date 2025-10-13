package com.dmitrybrant.android.mandelbrot

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.dmitrybrant.android.mandelbrot.databinding.MainBinding
import com.dmitrybrant.android.mandelbrot.simple.SimpleMandelbrotActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private lateinit var binding: MainBinding

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.mainToolbar)
        supportActionBar?.title = getString(R.string.app_name)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val newStatusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val newNavBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val newCaptionBarInsets = insets.getInsets(WindowInsetsCompat.Type.captionBar())
            val newSystemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.mainToolbarContainer.updatePadding(top = max(max(max(newStatusBarInsets.top, newCaptionBarInsets.top), newSystemBarInsets.top), newNavBarInsets.top))
            insets
        }

        binding.buttonStartSimple.setOnClickListener {
            startActivity(Intent(this, SimpleMandelbrotActivity::class.java) )
        }
        binding.buttonStartExperimental.setOnClickListener {
            startActivity(Intent(this, MandelbrotActivity::class.java) )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_about -> {
                showAboutDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about)
            .setMessage(R.string.str_about)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
