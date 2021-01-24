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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    private var juliaEnabled = false
    private var currentColorScheme = 0

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        binding = MainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initColorSchemes()
        ViewCompat.setBackground(binding.mainToolbar, getCubicGradient(ContextCompat
                .getColor(this, R.color.toolbar_gradient), Gravity.TOP))
        setSupportActionBar(binding.mainToolbar)
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.title = ""
        }

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

        binding. mandelbrotView.setOnPointSelected(object : OnPointSelected {
            override fun pointSelected(x: Double, y: Double) {
                binding.juliaView.terminateThreads()
                binding.juliaView.setJuliaCoords(binding.mandelbrotView.xCenter, binding.mandelbrotView.yCenter)
                binding.juliaView.render()
            }
        })
        binding.mandelbrotView.setOnCoordinatesChanged(coordinatesChangedListener)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topLayout)) { _: View?, insets: WindowInsetsCompat ->
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

        //restore settings...
        binding.mandelbrotView.reset()
        val settings = getSharedPreferences(PREFS_NAME, 0)
        binding.mandelbrotView.xCenter = settings.getString("xcenter", MandelbrotViewBase.DEFAULT_X_CENTER.toString())!!.toDouble()
        binding.mandelbrotView.yCenter = settings.getString("ycenter", MandelbrotViewBase.DEFAULT_Y_CENTER.toString())!!.toDouble()
        binding.mandelbrotView.xExtent = settings.getString("xextent", MandelbrotViewBase.DEFAULT_X_EXTENT.toString())!!.toDouble()
        binding.mandelbrotView.numIterations = settings.getInt("iterations", MandelbrotViewBase.DEFAULT_ITERATIONS)
        binding.mandelbrotView.power = settings.getInt("power", MandelbrotViewBase.DEFAULT_POWER)
        currentColorScheme = settings.getInt("colorscheme", 0)
        juliaEnabled = settings.getBoolean("juliaEnabled", false)

        updateColorScheme()

        binding.juliaView.reset()
        binding.juliaView.setJuliaCoords(binding.mandelbrotView.xCenter, binding.mandelbrotView.yCenter)
        binding.juliaView.numIterations = binding.mandelbrotView.numIterations
        binding.juliaView.power = binding.mandelbrotView.power

        // set the position and gravity of the Julia view, based on screen orientation
        binding.juliaView.post { initJulia() }

        when (binding.mandelbrotView.power) {
            2 -> binding.buttonPower2.isChecked = true
            3 -> binding.buttonPower3.isChecked = true
            4 -> binding.buttonPower4.isChecked = true
        }

        binding.buttonPower2.setOnClickListener { updatePower(2) }
        binding.buttonPower3.setOnClickListener { updatePower(3) }
        binding.buttonPower4.setOnClickListener { updatePower(4) }

        updateIterationBar()
    }

    public override fun onStop() {
        super.onStop()
        val settings = getSharedPreferences(PREFS_NAME, 0)
        val editor = settings.edit()
        editor.putString("xcenter", binding.mandelbrotView.xCenter.toString())
        editor.putString("ycenter", binding.mandelbrotView.yCenter.toString())
        editor.putString("xextent", binding.mandelbrotView.xExtent.toString())
        editor.putInt("iterations", binding.mandelbrotView.numIterations)
        editor.putInt("power", binding.mandelbrotView.power)
        editor.putInt("colorscheme", currentColorScheme)
        editor.putBoolean("juliaEnabled", juliaEnabled)
        editor.apply()
    }

    public override fun onDestroy() {
        binding.mandelbrotView.terminateThreads()
        binding.juliaView.terminateThreads()
        MandelNative.releaseParameters(0)
        MandelNative.releaseBitmap(0)
        MandelNative.releaseParameters(1)
        MandelNative.releaseBitmap(1)
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        var iterationInc = binding.mandelbrotView.numIterations / 16
        if (iterationInc < 1) {
            iterationInc = 1
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            updateIterations(binding.mandelbrotView.numIterations - iterationInc)
            updateIterationBar()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            updateIterations(binding.mandelbrotView.numIterations + iterationInc)
            updateIterationBar()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        if (isSettingsVisible) {
            toggleSettings()
            return
        } else if (juliaEnabled) {
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
                currentColorScheme++
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
            else -> {
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == WRITE_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                beginChooseFolder()
            } else {
                Toast.makeText(this, R.string.picture_save_permissions, Toast.LENGTH_SHORT).show()
            }
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == OPEN_DOCUMENT_REQUEST && resultCode == Activity.RESULT_OK && resultData!!.data != null) {
            val treeUri = resultData.data
            val pickedDir = DocumentFile.fromTreeUri(this, treeUri!!) ?: return
            grantUriPermission(packageName, treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            saveImage(pickedDir)
        }
    }

    private fun checkWritePermissionThenSaveImage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), WRITE_PERMISSION_REQUEST)
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
            binding.txtIterations.text = String.format(Locale.ROOT, "%d", binding.mandelbrotView.numIterations)
            if (juliaEnabled) {
                binding.txtInfo.text = String.format(getString(R.string.coordinate_display_julia), xmin,
                        xmax, ymin, ymax, binding.mandelbrotView.xCenter, binding.mandelbrotView.yCenter)
            } else {
                binding.txtInfo.text = String.format(getString(R.string.coordinate_display), xmin, xmax, ymin, ymax)
            }
        }
    }

    private fun updateIterationBar() {
        binding.seekBarIterations.progress = sqrt(binding.mandelbrotView.numIterations.toDouble()).toInt()
    }

    private fun updateJulia() {
        if (binding.juliaView.animation != null && !binding.juliaView.animation.hasEnded()) {
            return
        }
        binding.mandelbrotView.showCrosshairs = juliaEnabled
        binding.mandelbrotView.invalidate()
        binding.mandelbrotView.requestCoordinates()
        if (juliaEnabled) {
            binding.juliaView.visibility = View.VISIBLE
            binding.juliaView.render()
        } else {
            binding.juliaView.visibility = View.GONE
        }
    }

    private fun updateColorScheme() {
        if (currentColorScheme >= getColorSchemes().size) {
            currentColorScheme = 0
        }
        binding.mandelbrotView.setColorScheme(getColorSchemes()[currentColorScheme])
        binding.juliaView.setColorScheme(getShiftedScheme(getColorSchemes()[currentColorScheme],
                getColorSchemes()[currentColorScheme].size / 2))
    }

    private fun updateIterations(iterations: Int) {
        binding.mandelbrotView.numIterations = iterations
        binding.mandelbrotView.render()
        binding.juliaView.numIterations = iterations
        binding.juliaView.render()
    }

    private fun updatePower(power: Int) {
        binding.mandelbrotView.power = power
        binding.mandelbrotView.render()
        binding.juliaView.power = power
        binding.juliaView.render()
    }

    private val isSettingsVisible: Boolean
        get() = binding.settingsContainer.visibility == View.VISIBLE

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
        juliaEnabled = !juliaEnabled
        updateJulia()
    }

    private fun beginChooseFolder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            try {
                startActivityForResult(intent, OPEN_DOCUMENT_REQUEST)
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
            Toast.makeText(this@MandelbrotActivity, String.format(getString(R.string.picture_save_success), file.uri.path), Toast.LENGTH_LONG).show()
        } catch (ex: Exception) {
            ex.printStackTrace()
            Toast.makeText(this@MandelbrotActivity, String.format(getString(R.string.picture_save_error), ex.message), Toast.LENGTH_LONG).show()
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
            Toast.makeText(this@MandelbrotActivity, String.format(getString(R.string.picture_save_success), path), Toast.LENGTH_LONG).show()
        } catch (ex: Exception) {
            ex.printStackTrace()
            Toast.makeText(this@MandelbrotActivity, String.format(getString(R.string.picture_save_error), ex.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun showAboutDialog() {
        val alertDialog = AlertDialog.Builder(this@MandelbrotActivity).create()
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
        }
    }

    companion object {
        const val PREFS_NAME = "MandelbrotActivityPrefs"
        private const val WRITE_PERMISSION_REQUEST = 50
        private const val OPEN_DOCUMENT_REQUEST = 101

        init {
            System.loadLibrary("mandelnative_jni")
        }
    }
}
