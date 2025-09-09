package com.dmitrybrant.android.mandelbrot

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
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
import com.dmitrybrant.android.mandelbrot.simple.ColorScheme.initColorSchemes
import com.dmitrybrant.android.mandelbrot.GradientUtil.getCubicGradient
import com.dmitrybrant.android.mandelbrot.databinding.MandelGmpBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

class MandelbrotActivity : AppCompatActivity() {
    private lateinit var binding: MandelGmpBinding
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
        binding = MandelGmpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initColorSchemes()
        binding.mainToolbar.background = getCubicGradient(ContextCompat
                .getColor(this, R.color.toolbar_gradient), Gravity.TOP)
        setSupportActionBar(binding.mainToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        binding.settingsContainer.visibility = View.GONE
        binding.seekBarIterations.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) {
                    return
                }
                // TODO
            }

            override fun onStartTrackingTouch(arg0: SeekBar) {}
            override fun onStopTrackingTouch(arg0: SeekBar) {}
        })

        ViewCompat.setOnApplyWindowInsetsListener(binding.topLayout) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            binding.mainToolbar.updatePadding(top = statusBarInsets.top)
            binding.settingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = navBarInsets.bottom }

            WindowInsetsCompat.CONSUMED
        }

        binding.mandelGLView.callback = object : MandelGLView.Callback {
            override fun onUpdateState(centerX: String, centerY: String, radius: String, iterations: Int, colorScale: Float) {
                viewModel.xCenter = centerX
                viewModel.yCenter = centerY
                viewModel.xExtent = radius
                viewModel.numIterations = iterations
                viewModel.colorScale = colorScale
                updateInfo()
            }
        }
        binding.mandelGLView.initState(viewModel.xCenter, viewModel.yCenter, viewModel.xExtent, viewModel.numIterations, viewModel.colorScale)
        updateInfo()
    }

    override fun onStop() {
        super.onStop()
        viewModel.save()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_gmp, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.menu_zoom_out -> {
                binding.mandelGLView.zoomOut(2.0)
                return true
            }
            R.id.menu_settings -> {
                binding.settingsContainer.isVisible = true
                return true
            }
            R.id.menu_save_image -> {
                checkWritePermissionThenSaveImage()
                return true
            }
            R.id.menu_reset -> {
                binding.mandelGLView.reset()
                return true
            }
            R.id.menu_about -> {
                showAboutDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateInfo() {
        binding.txtInfo.text = "Re: " + viewModel.xCenter + "\nIm: " + viewModel.yCenter + "\nRadius: " + viewModel.xExtent
    }

    private fun checkWritePermissionThenSaveImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            beginChooseFolder()
        }
    }

    private fun beginChooseFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        try {
            openDocumentLauncher.launch(intent)
            Toast.makeText(applicationContext, R.string.folder_picker_instruction, Toast.LENGTH_LONG).show()
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
        }
    }

    private fun saveImage(dir: DocumentFile) {
        try {
            val fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date()) + ".png"
            val file = dir.createFile("image/png", fileName)

            // TODO
            notifyContentResolver(file!!.uri.toString())
            Toast.makeText(this, String.format(getString(R.string.picture_save_success), file.uri.path), Toast.LENGTH_LONG).show()
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
