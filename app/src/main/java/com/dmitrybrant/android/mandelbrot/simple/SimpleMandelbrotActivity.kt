package com.dmitrybrant.android.mandelbrot.simple

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.*
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.documentfile.provider.DocumentFile
import com.dmitrybrant.android.mandelbrot.simple.ColorScheme.getColorSchemes
import com.dmitrybrant.android.mandelbrot.simple.ColorScheme.getShiftedScheme
import com.dmitrybrant.android.mandelbrot.simple.ColorScheme.initColorSchemes
import com.dmitrybrant.android.mandelbrot.R
import com.dmitrybrant.android.mandelbrot.databinding.MandelSimpleBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt
import androidx.core.view.isVisible

class SimpleMandelbrotActivity : AppCompatActivity() {
    private lateinit var binding: MandelSimpleBinding
    private val viewModel: MandelbrotActivityViewModel by viewModels()

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK && it.data?.data != null) {
            val treeUri = it.data?.data!!
            DocumentFile.fromTreeUri(this, treeUri)?.let { dir ->
                grantUriPermission(packageName, treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                saveImage(dir)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            beginChooseFolder()
        } else {
            Toast.makeText(this, R.string.picture_save_permissions, Toast.LENGTH_SHORT).show()
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MandelSimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initColorSchemes()
        setSupportActionBar(binding.mainToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        binding.settingsContainer.isVisible = false
        binding.seekBarIterations.max = sqrt(MandelbrotCalculator.MAX_ITERATIONS.toDouble()).toInt() + 1
        binding.seekBarIterations.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) {
                    return
                }
                updateIterations(progress * progress)
            }

            override fun onStartTrackingTouch(arg0: SeekBar) {}
            override fun onStopTrackingTouch(arg0: SeekBar) {}
        })

        binding.mandelbrotView.onPointSelected = object : MandelbrotViewBase.OnPointSelected {
            override fun pointSelected(x: Double, y: Double) {
                binding.juliaView.terminateThreads()
                binding.juliaView.setJuliaCoords(binding.mandelbrotView.xCenter, binding.mandelbrotView.yCenter)
                binding.juliaView.render()
            }
        }
        binding.mandelbrotView.onCoordinatesChanged = coordinatesChangedListener

        ViewCompat.setOnApplyWindowInsetsListener(binding.topLayout) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            binding.mainToolbar.updatePadding(top = statusBarInsets.top)
            binding.settingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = navBarInsets.bottom }

            WindowInsetsCompat.CONSUMED
        }

        binding.mandelbrotView.reset()
        binding.mandelbrotView.xCenter = viewModel.xCenter
        binding.mandelbrotView.yCenter = viewModel.yCenter
        binding.mandelbrotView.xExtent = viewModel.xExtent
        binding.mandelbrotView.numIterations = viewModel.numIterations
        binding.mandelbrotView.power = viewModel.power

        updateColorScheme()

        binding.juliaView.reset()
        binding.juliaView.setJuliaCoords(binding.mandelbrotView.xCenter, binding.mandelbrotView.yCenter)
        binding.juliaView.numIterations = viewModel.numIterations
        binding.juliaView.power = viewModel.power

        // set the position and gravity of the Julia view, based on screen orientation
        binding.juliaView.post { initJulia() }

        when (viewModel.power) {
            2 -> binding.buttonPower2.isChecked = true
            3 -> binding.buttonPower3.isChecked = true
            4 -> binding.buttonPower4.isChecked = true
        }

        binding.buttonPower2.setOnClickListener { updatePower(2) }
        binding.buttonPower3.setOnClickListener { updatePower(3) }
        binding.buttonPower4.setOnClickListener { updatePower(4) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.settingsContainer.isVisible) {
                    toggleSettings()
                } else if (viewModel.juliaEnabled) {
                    toggleJulia()
                } else {
                    finish()
                }
            }
        })

        updateIterationBar()
    }

    override fun onStop() {
        super.onStop()
        viewModel.xCenter = binding.mandelbrotView.xCenter
        viewModel.yCenter = binding.mandelbrotView.yCenter
        viewModel.xExtent = binding.mandelbrotView.xExtent
        viewModel.save()
    }

    override fun onDestroy() {
        binding.mandelbrotView.terminateThreads()
        binding.juliaView.terminateThreads()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        var iterationInc = viewModel.numIterations / 16
        if (iterationInc < 1) {
            iterationInc = 1
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            updateIterations(viewModel.numIterations - iterationInc)
            updateIterationBar()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            updateIterations(viewModel.numIterations + iterationInc)
            updateIterationBar()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return binding.mandelbrotView.onTouchEvent(event)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_simple, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
            R.id.menu_settings -> {
                toggleSettings()
                binding.mandelbrotView.requestCoordinates()
                return true
            }
            R.id.menu_julia_mode -> {
                toggleJulia()
                return true
            }
            R.id.menu_save_image -> {
                checkWritePermissionThenSaveImage()
                return true
            }
            R.id.menu_color_scheme -> {
                viewModel.currentColorScheme++
                updateColorScheme()
                binding.mandelbrotView.render()
                binding.juliaView.render()
                return true
            }
            R.id.menu_reset -> {
                binding.mandelbrotView.reset()
                binding.juliaView.reset()
                return true
            }
            R.id.menu_about -> {
                showAboutDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun checkWritePermissionThenSaveImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            beginChooseFolder()
        }
    }

    private fun initJulia() {
        val widthOffset = 24
        val width = binding.mandelbrotView.width
        val height = binding.mandelbrotView.height
        val params = binding.juliaView.layoutParams as FrameLayout.LayoutParams
        if (width > height) {
            params.gravity = Gravity.START
            params.height = FrameLayout.LayoutParams.MATCH_PARENT
            params.width = width / 2 - (widthOffset * resources.displayMetrics.density).toInt()
        } else {
            params.gravity = Gravity.BOTTOM
            params.width = FrameLayout.LayoutParams.MATCH_PARENT
            params.height = height / 2 - (widthOffset * resources.displayMetrics.density).toInt()
        }
        binding.juliaView.layoutParams = params
        updateJulia()
    }

    private val coordinatesChangedListener: MandelbrotViewBase.OnCoordinatesChanged = object : MandelbrotViewBase.OnCoordinatesChanged {
        override fun newCoordinates(xmin: Double, xmax: Double, ymin: Double, ymax: Double) {
            if (!binding.txtInfo.isVisible) {
                return
            }
            binding.txtIterations.text = String.format(Locale.ROOT, "%d", viewModel.numIterations)
            if (viewModel.juliaEnabled) {
                binding.txtInfo.text = String.format(getString(R.string.coordinate_display_julia), xmin,
                        xmax, ymin, ymax, binding.mandelbrotView.xCenter, binding.mandelbrotView.yCenter)
            } else {
                binding.txtInfo.text = String.format(getString(R.string.coordinate_display), xmin, xmax, ymin, ymax)
            }
        }
    }

    private fun updateIterationBar() {
        binding.seekBarIterations.progress = sqrt(viewModel.numIterations.toDouble()).toInt()
    }

    private fun updateJulia() {
        if (binding.juliaView.animation != null && !binding.juliaView.animation.hasEnded()) {
            return
        }
        binding.mandelbrotView.showCrosshairs = viewModel.juliaEnabled
        binding.mandelbrotView.invalidate()
        binding.mandelbrotView.requestCoordinates()
        if (viewModel.juliaEnabled) {
            binding.juliaView.isVisible = true
            binding.juliaView.render()
        } else {
            binding.juliaView.isVisible = false
        }
    }

    private fun updateColorScheme() {
        if (viewModel.currentColorScheme >= getColorSchemes().size) {
            viewModel.currentColorScheme = 0
        }
        binding.mandelbrotView.setColorScheme(getColorSchemes()[viewModel.currentColorScheme])
        binding.juliaView.setColorScheme(getShiftedScheme(getColorSchemes()[viewModel.currentColorScheme],
                getColorSchemes()[viewModel.currentColorScheme].size / 2))
    }

    private fun updateIterations(iterations: Int) {
        viewModel.numIterations = iterations
        binding.mandelbrotView.numIterations = viewModel.numIterations
        binding.mandelbrotView.render()
        binding.juliaView.numIterations = viewModel.numIterations
        binding.juliaView.render()
    }

    private fun updatePower(power: Int) {
        viewModel.power = power
        binding.mandelbrotView.power = viewModel.power
        binding.mandelbrotView.render()
        binding.juliaView.power = viewModel.power
        binding.juliaView.render()
    }

    private fun toggleSettings() {
        if (binding.settingsContainer.animation != null && !binding.settingsContainer.animation.hasEnded()) {
            return
        }
        if (!binding.settingsContainer.isVisible) {
            binding.settingsContainer.isVisible = true
        } else {
            binding.settingsContainer.isVisible = false
        }
    }

    private fun toggleJulia() {
        if (binding.juliaView.animation != null && !binding.juliaView.animation.hasEnded()) {
            return
        }
        viewModel.juliaEnabled = !viewModel.juliaEnabled
        updateJulia()
    }

    private fun beginChooseFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        try {
            openDocumentLauncher.launch(intent)
            Toast.makeText(applicationContext, R.string.folder_picker_instruction, Toast.LENGTH_LONG).show()
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
            saveImageOld()
        }
    }

    private fun saveImage(dir: DocumentFile) {
        try {
            val fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".png"
            val file = dir.createFile("image/png", fileName)

            binding.mandelbrotView.savePicture(contentResolver.openOutputStream(file!!.uri)!!)
            notifyContentResolver(file.uri.toString())
            Toast.makeText(this, String.format(getString(R.string.picture_save_success), file.uri.path), Toast.LENGTH_LONG).show()
        } catch (ex: Exception) {
            ex.printStackTrace()
            Toast.makeText(this, String.format(getString(R.string.picture_save_error), ex.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun saveImageOld() {
        try {
            var path = Environment.getExternalStorageDirectory().absolutePath
            val picsFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (picsFile != null) {
                path = picsFile.absolutePath
            }
            val f = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
            path += "/" + f.format(Date()) + ".png"
            binding.mandelbrotView.savePicture(path)
            notifyContentResolver(path)
            Toast.makeText(this, String.format(getString(R.string.picture_save_success), path), Toast.LENGTH_LONG).show()
        } catch (ex: Exception) {
            ex.printStackTrace()
            Toast.makeText(this, String.format(getString(R.string.picture_save_error), ex.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about)
                .setMessage(R.string.str_about)
                .setPositiveButton(android.R.string.ok, null)
                .show()
    }

    private fun notifyContentResolver(path: String) {
        try {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.DATA, path)
            val contentUri = MediaStore.Images.Media.INTERNAL_CONTENT_URI
            contentResolver.insert(contentUri, values)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}
