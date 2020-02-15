package com.dmitrybrant.android.mandelbrot;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.core.view.ViewCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class MandelbrotActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "MandelbrotActivityPrefs";
    private static final int WRITE_PERMISSION_REQUEST = 50;
    private static final int OPEN_DOCUMENT_REQUEST = 101;

    static {
        System.loadLibrary("mandelnative_jni");
    }

    private MandelbrotView mandelbrotView;
    private JuliaView juliaView;
    private boolean juliaEnabled;
    private int currentColorScheme;

    private View settingsContainer;
    private TextView txtInfo;
    private TextView txtIterations;
    private SeekBar seekBarIterations;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main);

        ColorScheme.initColorSchemes();

        final Toolbar toolbar = findViewById(R.id.main_toolbar);
        ViewCompat.setBackground(toolbar, GradientUtil.getCubicGradient(ContextCompat
                .getColor(this, R.color.toolbar_gradient), Gravity.TOP));
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        txtInfo = findViewById(R.id.txtInfo);
        txtIterations = findViewById(R.id.txtIterations);
        settingsContainer = findViewById(R.id.settings_container);
        settingsContainer.setVisibility(View.GONE);

        seekBarIterations = findViewById(R.id.seekBarIterations);
        seekBarIterations.setMax((int)Math.sqrt(MandelbrotViewBase.MAX_ITERATIONS) + 1);
        seekBarIterations.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                updateIterations(progress * progress);
            }
            public void onStartTrackingTouch(SeekBar arg0) {
            }
            public void onStopTrackingTouch(SeekBar arg0) {
            }
        });

        juliaView = findViewById(R.id.julia_view);
        mandelbrotView = findViewById(R.id.mandelbrot_view);
        mandelbrotView.setOnPointSelected((x, y) -> {
            juliaView.terminateThreads();
            juliaView.setJuliaCoords(mandelbrotView.getXCenter(), mandelbrotView.getYCenter());
            juliaView.render();
        });
        mandelbrotView.setOnCoordinatesChanged(coordinatesChangedListener);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topLayout), (v, insets) -> {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) settingsContainer.getLayoutParams();
            params.topMargin = insets.getSystemWindowInsetTop();
            params.bottomMargin = insets.getSystemWindowInsetBottom();
            params.leftMargin = insets.getSystemWindowInsetLeft();
            params.rightMargin = insets.getSystemWindowInsetRight();

            params = (FrameLayout.LayoutParams) toolbar.getLayoutParams();
            params.topMargin = insets.getSystemWindowInsetTop();
            params.bottomMargin = insets.getSystemWindowInsetBottom();
            params.leftMargin = insets.getSystemWindowInsetLeft();
            params.rightMargin = insets.getSystemWindowInsetRight();
            return insets.consumeSystemWindowInsets();
        });

        //restore settings...
        mandelbrotView.reset();
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        mandelbrotView.setXCenter(Double.parseDouble(settings.getString("xcenter", Double.toString(MandelbrotViewBase.DEFAULT_X_CENTER))));
        mandelbrotView.setYCenter(Double.parseDouble(settings.getString("ycenter", Double.toString(MandelbrotViewBase.DEFAULT_Y_CENTER))));
        mandelbrotView.setXExtent(Double.parseDouble(settings.getString("xextent", Double.toString(MandelbrotViewBase.DEFAULT_X_EXTENT))));
        mandelbrotView.setNumIterations(settings.getInt("iterations", MandelbrotViewBase.DEFAULT_ITERATIONS));
        currentColorScheme = settings.getInt("colorscheme", 0);
        juliaEnabled = settings.getBoolean("juliaEnabled", false);
        updateColorScheme();

        juliaView.reset();
        juliaView.setJuliaCoords(mandelbrotView.getXCenter(), mandelbrotView.getYCenter());
        juliaView.setNumIterations(mandelbrotView.getNumIterations());

        // set the position and gravity of the Julia view, based on screen orientation
        juliaView.post(this::initJulia);

        updateIterationBar();
    }

    @Override
    public void onStop(){
        super.onStop();
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("xcenter", Double.toString(mandelbrotView.getXCenter()));
        editor.putString("ycenter", Double.toString(mandelbrotView.getYCenter()));
        editor.putString("xextent", Double.toString(mandelbrotView.getXExtent()));
        editor.putInt("iterations", mandelbrotView.getNumIterations());
        editor.putInt("colorscheme", currentColorScheme);
        editor.putBoolean("juliaEnabled", juliaEnabled);
        editor.apply();
    }
    
    @Override
    public void onDestroy(){
        mandelbrotView.terminateThreads();
        juliaView.terminateThreads();
        mandelnative.ReleaseParameters(0);
        mandelnative.ReleaseBitmap(0);
        mandelnative.ReleaseParameters(1);
        mandelnative.ReleaseBitmap(1);
        super.onDestroy();
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int iterationInc = mandelbrotView.getNumIterations() / 16;
        if (iterationInc < 1) {
            iterationInc = 1;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN){
            updateIterations(mandelbrotView.getNumIterations() - iterationInc);
            updateIterationBar();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP){
            updateIterations(mandelbrotView.getNumIterations() + iterationInc);
            updateIterationBar();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (isSettingsVisible()) {
            toggleSettings();
            return;
        } else if (juliaEnabled) {
            toggleJulia();
            return;
        }
        super.onBackPressed();
    }

    public boolean onTouchEvent(MotionEvent event){
        return mandelbrotView.onTouchEvent(event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.menu_settings:
                toggleSettings();
                mandelbrotView.requestCoordinates();
                return true;
            case R.id.menu_julia_mode:
                toggleJulia();
                return true;
            case R.id.menu_save_image:
                checkWritePermissionThenSaveImage();
                return true;
            case R.id.menu_color_scheme:
                currentColorScheme++;
                updateColorScheme();
                mandelbrotView.render();
                juliaView.render();
                return true;
            case R.id.menu_reset:
                mandelbrotView.reset();
                juliaView.reset();
                return true;
            case R.id.menu_about:
                showAboutDialog();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == WRITE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                beginChooseFolder();
            } else {
                Toast.makeText(this, R.string.picture_save_permissions, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == OPEN_DOCUMENT_REQUEST && resultCode == RESULT_OK && resultData.getData() != null) {
            Uri treeUri = resultData.getData();
            DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);
            if (pickedDir == null) {
                return;
            }
            grantUriPermission(getPackageName(), treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            saveImage(pickedDir);
        }
    }

    private void checkWritePermissionThenSaveImage() {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE }, WRITE_PERMISSION_REQUEST);
        } else {
            beginChooseFolder();
        }
    }

    private void initJulia() {
        final int widthOffset = 24;
        int width = mandelbrotView.getWidth();
        int height = mandelbrotView.getHeight();
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) juliaView.getLayoutParams();
        if (width > height) {
            params.gravity = Gravity.START;
            params.height = FrameLayout.LayoutParams.MATCH_PARENT;
            params.width = width / 2 - (int) (widthOffset * getResources().getDisplayMetrics().density);
        } else {
            params.gravity = Gravity.BOTTOM;
            params.width = FrameLayout.LayoutParams.MATCH_PARENT;
            params.height = height / 2 - (int) (widthOffset * getResources().getDisplayMetrics().density);
        }
        juliaView.setLayoutParams(params);
        updateJulia();
    }

    private MandelbrotViewBase.OnCoordinatesChanged coordinatesChangedListener = new MandelbrotViewBase.OnCoordinatesChanged() {
        @Override
        public void newCoordinates(double xmin, double xmax, double ymin, double ymax) {
            if (txtInfo.getVisibility() != View.VISIBLE) {
                return;
            }
            txtIterations.setText(String.format(Locale.ROOT, "%d", mandelbrotView.getNumIterations()));
            if (juliaEnabled) {
                txtInfo.setText(String.format(getString(R.string.coordinate_display_julia), xmin,
                        xmax, ymin, ymax, mandelbrotView.getXCenter(), mandelbrotView.getYCenter()));
            } else {
                txtInfo.setText(String.format(getString(R.string.coordinate_display), xmin, xmax, ymin, ymax));
            }
        }
    };

    private void updateIterationBar() {
        seekBarIterations.setProgress((int) Math.sqrt(mandelbrotView.getNumIterations()));
    }

    private void updateJulia() {
        if (juliaView.getAnimation() != null && !juliaView.getAnimation().hasEnded()) {
            return;
        }
        mandelbrotView.setCrosshairsEnabled(juliaEnabled);
        mandelbrotView.invalidate();
        mandelbrotView.requestCoordinates();
        if (juliaEnabled) {
            juliaView.setVisibility(View.VISIBLE);
            juliaView.render();
        } else {
            juliaView.setVisibility(View.GONE);
        }
    }

    private void updateColorScheme() {
        if (currentColorScheme >= ColorScheme.getColorSchemes().size()) {
            currentColorScheme = 0;
        }
        mandelbrotView.setColorScheme(ColorScheme.getColorSchemes().get(currentColorScheme));
        juliaView.setColorScheme(ColorScheme.getShiftedScheme(ColorScheme.getColorSchemes().get(currentColorScheme),
                ColorScheme.getColorSchemes().get(currentColorScheme).length / 2));
    }

    private void updateIterations(int iterations) {
        mandelbrotView.setNumIterations(iterations);
        mandelbrotView.render();
        juliaView.setNumIterations(iterations);
        juliaView.render();
    }

    private boolean isSettingsVisible() {
        return settingsContainer.getVisibility() == View.VISIBLE;
    }

    private void toggleSettings() {
        if (settingsContainer.getAnimation() != null && !settingsContainer.getAnimation().hasEnded()) {
            return;
        }
        if (settingsContainer.getVisibility() != View.VISIBLE) {
            settingsContainer.setVisibility(View.VISIBLE);
        } else {
            settingsContainer.setVisibility(View.GONE);
        }
    }

    private void toggleJulia() {
        if (juliaView.getAnimation() != null && !juliaView.getAnimation().hasEnded()) {
            return;
        }
        juliaEnabled = !juliaEnabled;
        updateJulia();
    }

    private void beginChooseFolder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            try {
                startActivityForResult(intent, OPEN_DOCUMENT_REQUEST);
                Toast.makeText(getApplicationContext(), R.string.folder_picker_instruction, Toast.LENGTH_LONG).show();
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
                saveImageOld();
            }
        } else {
            saveImageOld();
        }
    }

    private void saveImage(@NonNull DocumentFile dir) {
        try {
            String fileName = (new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT))
                    .format(new Date()) + ".png";

            DocumentFile file = dir.createFile("image/png", fileName);
            mandelbrotView.savePicture(getContentResolver().openOutputStream(file.getUri()));
            notifyContentResolver(file.getUri().toString());
            Toast.makeText(MandelbrotActivity.this, String.format(getString(R.string.picture_save_success), file.getUri().getPath()), Toast.LENGTH_LONG).show();
        } catch(Exception ex) {
            ex.printStackTrace();
            Toast.makeText(MandelbrotActivity.this, String.format(getString(R.string.picture_save_error), ex.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void saveImageOld() {
        try {
            String path = Environment.getExternalStorageDirectory().getAbsolutePath();
            File picsFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            if (picsFile != null) {
                path = picsFile.getAbsolutePath();
            }
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
            path += "/" + f.format(new Date()) + ".png";
            mandelbrotView.savePicture(path);
            notifyContentResolver(path);
            Toast.makeText(MandelbrotActivity.this, String.format(getString(R.string.picture_save_success), path), Toast.LENGTH_LONG).show();
        } catch(Exception ex) {
            ex.printStackTrace();
            Toast.makeText(MandelbrotActivity.this, String.format(getString(R.string.picture_save_error), ex.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void showAboutDialog() {
        AlertDialog alertDialog = new AlertDialog.Builder(MandelbrotActivity.this).create();
        alertDialog.setTitle(getString(R.string.about));
        alertDialog.setMessage(getString(R.string.str_about));
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok), (DialogInterface.OnClickListener) null);
        alertDialog.show();
    }

    private void notifyContentResolver(@NonNull String path) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DATA, path);
            Uri contentUri = MediaStore.Images.Media.INTERNAL_CONTENT_URI;
            getContentResolver().insert(contentUri, values);
        } catch (Exception e) {
            //
        }
    }
}
