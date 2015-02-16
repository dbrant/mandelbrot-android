package com.dmitrybrant.android.mandelbrot;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class MandelbrotActivity extends ActionBarActivity {
    public static final String PREFS_NAME = "MandelbrotActivityPrefs";

    static {
        System.loadLibrary("mandelnative_jni");
    }

    private MandelbrotView mandelbrotView;
    private JuliaView juliaView;
    private boolean juliaEnabled = false;
    private int currentColorScheme = 0;

    private View settingsContainer;
    public TextView txtInfo, txtIterations;
    private SeekBar seekBarIterations;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main);

        ColorScheme.initColorSchemes();

        final Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("");
        forceOverflowMenuIcon(this);

        txtInfo = (TextView)findViewById(R.id.txtInfo);
        txtInfo.setVisibility(View.INVISIBLE);
        txtIterations = (TextView)findViewById(R.id.txtIterations);

        settingsContainer = findViewById(R.id.settings_container);
        settingsContainer.setVisibility(View.GONE);

        seekBarIterations = (SeekBar)findViewById(R.id.seekBarIterations);
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

        juliaView = (JuliaView)findViewById(R.id.julia_view);
        mandelbrotView = (MandelbrotView)findViewById(R.id.mandelbrot_view);
        mandelbrotView.setOnPointSelected(new MandelbrotViewBase.OnPointSelected() {
            @Override
            public void pointSelected(double x, double y) {
                juliaView.terminateThreads();
                juliaView.setJuliaCoords(mandelbrotView.getXCenter(), mandelbrotView.getYCenter());
                juliaView.render();
            }
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
        juliaView.post(new Runnable() {
            @Override
            public void run() {
                final int widthOffset = 32;
                int width = getWindow().getDecorView().getWidth();
                int height = getWindow().getDecorView().getHeight();
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) juliaView.getLayoutParams();
                if (width > height) {
                    params.gravity = Gravity.START;
                    params.height = FrameLayout.LayoutParams.MATCH_PARENT;
                    params.width = width / 2 - (int)(widthOffset * getResources().getDisplayMetrics().density);
                } else {
                    params.gravity = Gravity.BOTTOM;
                    params.width = FrameLayout.LayoutParams.MATCH_PARENT;
                    params.height = height / 2 - (int)(widthOffset * getResources().getDisplayMetrics().density);
                }
                juliaView.setLayoutParams(params);
                updateJulia();
            }
        });

        updateIterationBar();
    }

    /**
     * Helper function to force the Activity to show the three-dot overflow icon in its ActionBar.
     * @param activity Activity whose overflow icon will be forced.
     */
    private static void forceOverflowMenuIcon(Activity activity) {
        try {
            ViewConfiguration config = ViewConfiguration.get(activity);
            // Note: this field doesn't exist in API <11, so those users will need to tap the physical menu button,
            // unless we figure out another solution.
            // This field also doesn't exist in 4.4, where the overflow icon is always shown:
            // https://android.googlesource.com/platform/frameworks/base.git/+/ea04f3cfc6e245fb415fd352ed0048cd940a46fe%5E!/
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if (menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception ex) {
            // multiple exceptions may be thrown above, but it's not super critical if it fails.
        }
    }

    private void updateIterationBar() {
        seekBarIterations.setProgress((int)Math.sqrt(mandelbrotView.getNumIterations()));
    }

    private void updateJulia() {
        mandelbrotView.setCrosshairsEnabled(juliaEnabled);
        mandelbrotView.invalidate();
        juliaView.setVisibility(juliaEnabled ? View.VISIBLE : View.GONE);
        if (juliaEnabled) {
            juliaView.render();
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

    @Override
    protected void onStop(){
        super.onStop();
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("xcenter", Double.toString(mandelbrotView.getXCenter()));
        editor.putString("ycenter", Double.toString(mandelbrotView.getYCenter()));
        editor.putString("xextent", Double.toString(mandelbrotView.getXExtent()));
        editor.putInt("iterations", mandelbrotView.getNumIterations());
        editor.putInt("colorscheme", currentColorScheme);
        editor.putBoolean("juliaEnabled", juliaEnabled);
        editor.commit();
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
        if (keyCode == KeyEvent.KEYCODE_BACK){
            finish();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN){
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
                if (settingsContainer.getVisibility() != View.VISIBLE) {
                    settingsContainer.setVisibility(View.VISIBLE);
                } else {
                    settingsContainer.setVisibility(View.GONE);
                }
                return true;
            case R.id.menu_julia_mode:
                juliaEnabled = !juliaEnabled;
                updateJulia();
                return true;
            case R.id.menu_save_image:
                try{
                    String path = Environment.getExternalStorageDirectory().getAbsolutePath();
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
                    path += "/" + f.format(new Date()) + ".png";
                    mandelbrotView.SavePicture(path);
                    Toast.makeText(MandelbrotActivity.this, "Picture saved as: " + path, Toast.LENGTH_SHORT).show();
                }catch(Exception ex){
                    Toast.makeText(MandelbrotActivity.this, "Error saving file: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                }
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
                LayoutInflater inflater = (LayoutInflater)MandelbrotActivity.this.getSystemService(LAYOUT_INFLATER_SERVICE);
                View layout = inflater.inflate(R.layout.about, null);
                TextView txtAbout = (TextView)layout.findViewById(R.id.txtAbout);
                txtAbout.setText(MandelbrotActivity.this.getString(R.string.str_about));
                AlertDialog alertDialog = new AlertDialog.Builder(MandelbrotActivity.this).create();
                alertDialog.setTitle("About...");
                alertDialog.setView(layout);
                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                alertDialog.show();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

}
