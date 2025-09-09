package com.dmitrybrant.android.mandelbrot

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.dmitrybrant.android.mandelbrot.databinding.MainBinding
import com.dmitrybrant.android.mandelbrot.simple.SimpleMandelbrotActivity

class MainActivity : AppCompatActivity() {
    private lateinit var binding: MainBinding

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.mainToolbar)
        supportActionBar?.title = getString(R.string.app_name)

        ViewCompat.setOnApplyWindowInsetsListener(binding.topLayout) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.mainToolbar.updatePadding(top = statusBarInsets.top)
            WindowInsetsCompat.CONSUMED
        }

        binding.buttonStartSimple.setOnClickListener {
            startActivity(Intent(this, SimpleMandelbrotActivity::class.java) )
        }
        binding.buttonStartExperimental.setOnClickListener {
            startActivity(Intent(this, MandelbrotActivity::class.java) )
        }
    }
}
