package com.dmitrybrant.android.mandelbrot

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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.documentfile.provider.DocumentFile
import com.dmitrybrant.android.mandelbrot.ColorScheme.getColorSchemes
import com.dmitrybrant.android.mandelbrot.ColorScheme.getShiftedScheme
import com.dmitrybrant.android.mandelbrot.ColorScheme.initColorSchemes
import com.dmitrybrant.android.mandelbrot.GradientUtil.getCubicGradient
import com.dmitrybrant.android.mandelbrot.MandelbrotViewBase.OnCoordinatesChanged
import com.dmitrybrant.android.mandelbrot.MandelbrotViewBase.OnPointSelected
import com.dmitrybrant.android.mandelbrot.databinding.MainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

class MandelbrotActivity : AppCompatActivity() {
    private lateinit var binding: MainBinding
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
        binding = MainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initColorSchemes()
        binding.mainToolbar.background = getCubicGradient(ContextCompat
                .getColor(this, R.color.toolbar_gradient), Gravity.TOP)
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

        // Note: onPointSelected and onCoordinatesChanged removed for now
        // The new GL view handles touch internally for navigation
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.topLayout) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            binding.mainToolbar.updatePadding(top = statusBarInsets.top)
            binding.settingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = navBarInsets.bottom }

            WindowInsetsCompat.CONSUMED
        }

        // Initialize GL Mandelbrot view with stored parameters
        binding.mandelbrotView.navigateToLocation(
            viewModel.xCenter.toString(),
            viewModel.yCenter.toString(), 
            viewModel.xExtent.toString()
        )

        updateColorScheme()

        binding.juliaView.reset()
        // Note: Julia view integration needs to be updated for GL view
        // binding.juliaView.setJuliaCoords(binding.mandelbrotView.xCenter, binding.mandelbrotView.yCenter)
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
        // Note: GL view doesn't expose coordinates in the same way
        // We'll need to add methods to get current state if needed
        viewModel.save()
    }

    override fun onDestroy() {
        binding.juliaView.terminateThreads()
        // Note: GL view manages its own resources
        MandelbrotCalculator.releaseParameters(1)
        MandelbrotCalculator.releaseBitmap(1) 
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.settingsContainer.isVisible) {
            toggleSettings()
            return
        } else if (viewModel.juliaEnabled) {
            toggleJulia()
            return
        }
        super.onBackPressed()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // GL view handles its own touch events
        return super.onTouchEvent(event)
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
                if (binding.settingsContainer.isVisible) {
                    // Update info display with current GL view state
                    binding.txtInfo.text = binding.mandelbrotView.getCurrentInfo()
                }
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
                binding.mandelbrotView.requestRender()
                binding.juliaView.render()
                return true
            }
            R.id.menu_reset -> {
                binding.mandelbrotView.resetView()
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
            // Note: Coordinate display needs to be updated for GL view
            binding.txtInfo.text = binding.mandelbrotView.getCurrentInfo()
        }
    }

    private fun updateIterationBar() {
        binding.seekBarIterations.progress = sqrt(viewModel.numIterations.toDouble()).toInt()
    }

    private fun updateJulia() {
        if (binding.juliaView.animation != null && !binding.juliaView.animation.hasEnded()) {
            return
        }
        // Note: Crosshairs functionality needs to be updated for GL view
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
        // Note: Color scheme functionality needs to be implemented in GL renderer
        binding.juliaView.setColorScheme(getShiftedScheme(getColorSchemes()[viewModel.currentColorScheme],
                getColorSchemes()[viewModel.currentColorScheme].size / 2))
    }

    private fun updateIterations(iterations: Int) {
        viewModel.numIterations = iterations
        // Note: Iterations update needs to be implemented in GL renderer
        binding.mandelbrotView.requestRender()
        binding.juliaView.numIterations = viewModel.numIterations
        binding.juliaView.render()
    }

    private fun updatePower(power: Int) {
        viewModel.power = power
        // Note: Power setting needs to be implemented in GL renderer
        binding.mandelbrotView.requestRender()
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

            // Note: Save functionality needs to be implemented for GL renderer
            Toast.makeText(this, "Save image not yet implemented for GL renderer", Toast.LENGTH_LONG).show()
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
            // Note: Save functionality needs to be implemented for GL renderer
            Toast.makeText(this, "Save image not yet implemented for GL renderer", Toast.LENGTH_LONG).show()
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
