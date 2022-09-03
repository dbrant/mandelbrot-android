package com.dmitrybrant.android.mandelbrot

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.DialogInterface
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.documentfile.provider.DocumentFile
import com.dmitrybrant.android.mandelbrot.ColorScheme.getColorSchemes
import com.dmitrybrant.android.mandelbrot.ColorScheme.getShiftedScheme
import com.dmitrybrant.android.mandelbrot.ColorScheme.initColorSchemes
import com.dmitrybrant.android.mandelbrot.GradientUtil.getCubicGradient
import com.dmitrybrant.android.mandelbrot.MandelbrotViewBase.OnCoordinatesChanged
import com.dmitrybrant.android.mandelbrot.MandelbrotViewBase.OnPointSelected
import com.dmitrybrant.android.mandelbrot.databinding.MainBinding
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

class MandelbrotActivity : AppCompatActivity() {
    private lateinit var binding: MainBinding
    private val viewModel: MandelbrotActivityViewModel by viewModels()

    private val isSettingsVisible: Boolean
        get() = binding.settingsContainer.visibility == View.VISIBLE

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK && it.data?.data != null) {
            val treeUri = it.data?.data!!
            DocumentFile.fromTreeUri(this, treeUri)?.let { dir ->
                grantUriPermission(packageName, treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                saveImage(dir)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            beginChooseFolder()
        } else {
            Toast.makeText(this, R.string.picture_save_permissions, Toast.LENGTH_SHORT).show()
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        binding = MainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initColorSchemes()
        ViewCompat.setBackground(binding.mainToolbar, getCubicGradient(ContextCompat
                .getColor(this, R.color.toolbar_gradient), Gravity.TOP))
        setSupportActionBar(binding.mainToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        binding.settingsContainer.visibility = View.GONE
        binding.seekBarIterations.max = sqrt(MandelbrotViewBase.MAX_ITERATIONS.toDouble()).toInt() + 1
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

        binding.mandelbrotView.onPointSelected = object : OnPointSelected {
            override fun pointSelected(x: Double, y: Double) {
                binding.juliaView.terminateThreads()
                binding.juliaView.setJuliaCoords(binding.mandelbrotView.xCenter, binding.mandelbrotView.yCenter)
                binding.juliaView.render()
            }
        }
        binding.mandelbrotView.onCoordinatesChanged = coordinatesChangedListener

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topLayout)) { _, insets ->
            var params = binding.settingsContainer.layoutParams as FrameLayout.LayoutParams
            params.topMargin = insets.systemWindowInsetTop
            params.bottomMargin = insets.systemWindowInsetBottom
            params.leftMargin = insets.systemWindowInsetLeft
            params.rightMargin = insets.systemWindowInsetRight
            params = binding.mainToolbar.layoutParams as FrameLayout.LayoutParams
            params.topMargin = insets.systemWindowInsetTop
            params.bottomMargin = insets.systemWindowInsetBottom
            params.leftMargin = insets.systemWindowInsetLeft
            params.rightMargin = insets.systemWindowInsetRight
            insets.consumeSystemWindowInsets()
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
        MandelNative.releaseParameters(0)
        MandelNative.releaseBitmap(0)
        MandelNative.releaseParameters(1)
        MandelNative.releaseBitmap(1)
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

    override fun onBackPressed() {
        if (isSettingsVisible) {
            toggleSettings()
            return
        } else if (viewModel.juliaEnabled) {
            toggleJulia()
            return
        }
        super.onBackPressed()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return binding.mandelbrotView.onTouchEvent(event)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
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

    private val coordinatesChangedListener: OnCoordinatesChanged = object : OnCoordinatesChanged {
        override fun newCoordinates(xmin: Double, xmax: Double, ymin: Double, ymax: Double) {
            if (binding.txtInfo.visibility != View.VISIBLE) {
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
            binding.juliaView.visibility = View.VISIBLE
            binding.juliaView.render()
        } else {
            binding.juliaView.visibility = View.GONE
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
        if (binding.settingsContainer.visibility != View.VISIBLE) {
            binding.settingsContainer.visibility = View.VISIBLE
        } else {
            binding.settingsContainer.visibility = View.GONE
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            try {
                openDocumentLauncher.launch(intent)
                Toast.makeText(applicationContext, R.string.folder_picker_instruction, Toast.LENGTH_LONG).show()
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
                saveImageOld()
            }
        } else {
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
        val alertDialog = AlertDialog.Builder(this).create()
        alertDialog.setTitle(getString(R.string.about))
        alertDialog.setMessage(getString(R.string.str_about))
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok), null as DialogInterface.OnClickListener?)
        alertDialog.show()
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

    companion object {
        init {
            System.loadLibrary("mandelnative_jni")
        }
    }
}
