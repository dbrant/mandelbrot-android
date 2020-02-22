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
import kotlinx.android.synthetic.main.main.*
import com.dmitrybrant.android.mandelbrot.ColorScheme.getColorSchemes
import com.dmitrybrant.android.mandelbrot.ColorScheme.getShiftedScheme
import com.dmitrybrant.android.mandelbrot.ColorScheme.initColorSchemes
import com.dmitrybrant.android.mandelbrot.GradientUtil.getCubicGradient
import com.dmitrybrant.android.mandelbrot.MandelbrotViewBase.OnCoordinatesChanged
import com.dmitrybrant.android.mandelbrot.MandelbrotViewBase.OnPointSelected
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

class MandelbrotActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "MandelbrotActivityPrefs"
        private const val WRITE_PERMISSION_REQUEST = 50
        private const val OPEN_DOCUMENT_REQUEST = 101

        init {
            System.loadLibrary("mandelnative_jni")
        }
    }

    private var juliaEnabled = false
    private var currentColorScheme = 0

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.main)

        initColorSchemes()
        ViewCompat.setBackground(mainToolbar, getCubicGradient(ContextCompat
                .getColor(this, R.color.toolbar_gradient), Gravity.TOP))
        setSupportActionBar(mainToolbar)
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.title = ""
        }

        settingsContainer.visibility = View.GONE
        seekBarIterations.max = sqrt(MandelbrotViewBase.MAX_ITERATIONS.toDouble()).toInt() + 1
        seekBarIterations.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) {
                    return
                }
                updateIterations(progress * progress)
            }

            override fun onStartTrackingTouch(arg0: SeekBar) {}
            override fun onStopTrackingTouch(arg0: SeekBar) {}
        })

        mandelbrotView.setOnPointSelected(object : OnPointSelected {
            override fun pointSelected(x: Double, y: Double) {
                juliaView.terminateThreads()
                juliaView.setJuliaCoords(mandelbrotView.xCenter, mandelbrotView.yCenter)
                juliaView.render()
            }
        })
        mandelbrotView.setOnCoordinatesChanged(coordinatesChangedListener)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topLayout)) { _: View?, insets: WindowInsetsCompat ->
            var params = settingsContainer.layoutParams as FrameLayout.LayoutParams
            params.topMargin = insets.systemWindowInsetTop
            params.bottomMargin = insets.systemWindowInsetBottom
            params.leftMargin = insets.systemWindowInsetLeft
            params.rightMargin = insets.systemWindowInsetRight
            params = mainToolbar.layoutParams as FrameLayout.LayoutParams
            params.topMargin = insets.systemWindowInsetTop
            params.bottomMargin = insets.systemWindowInsetBottom
            params.leftMargin = insets.systemWindowInsetLeft
            params.rightMargin = insets.systemWindowInsetRight
            insets.consumeSystemWindowInsets()
        }

        //restore settings...
        mandelbrotView.reset()
        val settings = getSharedPreferences(PREFS_NAME, 0)
        mandelbrotView.xCenter = settings.getString("xcenter", MandelbrotViewBase.DEFAULT_X_CENTER.toString())!!.toDouble()
        mandelbrotView.yCenter = settings.getString("ycenter", MandelbrotViewBase.DEFAULT_Y_CENTER.toString())!!.toDouble()
        mandelbrotView.xExtent = settings.getString("xextent", MandelbrotViewBase.DEFAULT_X_EXTENT.toString())!!.toDouble()
        mandelbrotView.setNumIterations(settings.getInt("iterations", MandelbrotViewBase.DEFAULT_ITERATIONS))
        currentColorScheme = settings.getInt("colorscheme", 0)
        juliaEnabled = settings.getBoolean("juliaEnabled", false)
        updateColorScheme()
        juliaView.reset()
        juliaView.setJuliaCoords(mandelbrotView.xCenter, mandelbrotView.yCenter)
        juliaView.setNumIterations(mandelbrotView.getNumIterations())

        // set the position and gravity of the Julia view, based on screen orientation
        juliaView.post { initJulia() }
        updateIterationBar()
    }

    public override fun onStop() {
        super.onStop()
        val settings = getSharedPreferences(PREFS_NAME, 0)
        val editor = settings.edit()
        editor.putString("xcenter", mandelbrotView.xCenter.toString())
        editor.putString("ycenter", mandelbrotView.yCenter.toString())
        editor.putString("xextent", mandelbrotView.xExtent.toString())
        editor.putInt("iterations", mandelbrotView.getNumIterations())
        editor.putInt("colorscheme", currentColorScheme)
        editor.putBoolean("juliaEnabled", juliaEnabled)
        editor.apply()
    }

    public override fun onDestroy() {
        mandelbrotView.terminateThreads()
        juliaView.terminateThreads()
        mandelnative.ReleaseParameters(0)
        mandelnative.ReleaseBitmap(0)
        mandelnative.ReleaseParameters(1)
        mandelnative.ReleaseBitmap(1)
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        var iterationInc = mandelbrotView!!.getNumIterations() / 16
        if (iterationInc < 1) {
            iterationInc = 1
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            updateIterations(mandelbrotView.getNumIterations() - iterationInc)
            updateIterationBar()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            updateIterations(mandelbrotView.getNumIterations() + iterationInc)
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
        return mandelbrotView.onTouchEvent(event)
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
                mandelbrotView.requestCoordinates()
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
                mandelbrotView.render()
                juliaView.render()
                return true
            }
            R.id.menu_reset -> {
                mandelbrotView.reset()
                juliaView.reset()
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
        val width = mandelbrotView.width
        val height = mandelbrotView.height
        val params = juliaView.layoutParams as FrameLayout.LayoutParams
        if (width > height) {
            params.gravity = Gravity.START
            params.height = FrameLayout.LayoutParams.MATCH_PARENT
            params.width = width / 2 - (widthOffset * resources.displayMetrics.density).toInt()
        } else {
            params.gravity = Gravity.BOTTOM
            params.width = FrameLayout.LayoutParams.MATCH_PARENT
            params.height = height / 2 - (widthOffset * resources.displayMetrics.density).toInt()
        }
        juliaView.layoutParams = params
        updateJulia()
    }

    private val coordinatesChangedListener: OnCoordinatesChanged = object : OnCoordinatesChanged {
        override fun newCoordinates(xmin: Double, xmax: Double, ymin: Double, ymax: Double) {
            if (txtInfo.visibility != View.VISIBLE) {
                return
            }
            txtIterations.text = String.format(Locale.ROOT, "%d", mandelbrotView.getNumIterations())
            if (juliaEnabled) {
                txtInfo.text = String.format(getString(R.string.coordinate_display_julia), xmin,
                        xmax, ymin, ymax, mandelbrotView.xCenter, mandelbrotView.yCenter)
            } else {
                txtInfo.text = String.format(getString(R.string.coordinate_display), xmin, xmax, ymin, ymax)
            }
        }
    }

    private fun updateIterationBar() {
        seekBarIterations.progress = sqrt(mandelbrotView.getNumIterations().toDouble()).toInt()
    }

    private fun updateJulia() {
        if (juliaView.animation != null && !juliaView.animation.hasEnded()) {
            return
        }
        mandelbrotView.setCrosshairsEnabled(juliaEnabled)
        mandelbrotView.invalidate()
        mandelbrotView.requestCoordinates()
        if (juliaEnabled) {
            juliaView.visibility = View.VISIBLE
            juliaView.render()
        } else {
            juliaView.visibility = View.GONE
        }
    }

    private fun updateColorScheme() {
        if (currentColorScheme >= getColorSchemes().size) {
            currentColorScheme = 0
        }
        mandelbrotView.setColorScheme(getColorSchemes()[currentColorScheme])
        juliaView.setColorScheme(getShiftedScheme(getColorSchemes()[currentColorScheme],
                getColorSchemes()[currentColorScheme].size / 2))
    }

    private fun updateIterations(iterations: Int) {
        mandelbrotView.setNumIterations(iterations)
        mandelbrotView.render()
        juliaView.setNumIterations(iterations)
        juliaView.render()
    }

    private val isSettingsVisible: Boolean
        get() = settingsContainer.visibility == View.VISIBLE

    private fun toggleSettings() {
        if (settingsContainer.animation != null && !settingsContainer.animation.hasEnded()) {
            return
        }
        if (settingsContainer.visibility != View.VISIBLE) {
            settingsContainer.visibility = View.VISIBLE
        } else {
            settingsContainer.visibility = View.GONE
        }
    }

    private fun toggleJulia() {
        if (juliaView.animation != null && !juliaView.animation.hasEnded()) {
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
            val fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT)
                    .format(Date()) + ".png"
            val file = dir.createFile("image/png", fileName)
            mandelbrotView.savePicture(contentResolver.openOutputStream(file!!.uri)!!)
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
            mandelbrotView!!.savePicture(path)
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
}