package com.dmitrybrant.android.mandelbrot;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
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
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MandelbrotActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "MandelbrotActivityPrefs";

    static {
        System.loadLibrary("mandelnative_jni");
    }

    private MandelbrotView mandelbrotView;
    private JuliaView juliaView;
    private boolean juliaEnabled = false;
    private int currentColorScheme = 0;

    private View settingsContainer;
    private TextView txtInfo;
    private TextView txtIterations;
    private SeekBar seekBarIterations;
    private MandelbrotViewBase.OnCoordinatesChanged coordinatesChangedListener = new MandelbrotViewBase.OnCoordinatesChanged() {
        @Override
        public void newCoordinates(double xmin, double xmax, double ymin, double ymax) {
            if (txtInfo.getVisibility() != View.VISIBLE) {
                return;
            }
            txtIterations.setText(Integer.toString(mandelbrotView.getNumIterations()));
            StringBuilder sb = new StringBuilder(512);
            sb.append("Real: ");
            sb.append(xmin);
            sb.append(" to ");
            sb.append(xmax);
            sb.append("\nImag: ");
            sb.append(ymin);
            sb.append(" to ");
            sb.append(ymax);
            if (juliaEnabled) {
                sb.append("\nJulia: ");
                sb.append(mandelbrotView.getXCenter());
                sb.append(", ");
                sb.append(mandelbrotView.getYCenter());
            }
            txtInfo.setText(sb.toString());
        }
    };

    /**
     * Helper function to force the Activity to show the three-dot overflow icon in its ActionBar.
     *
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

        txtInfo = (TextView) findViewById(R.id.txtInfo);
        txtIterations = (TextView) findViewById(R.id.txtIterations);
        settingsContainer = findViewById(R.id.settings_container);
        settingsContainer.setVisibility(View.GONE);

        seekBarIterations = (SeekBar) findViewById(R.id.seekBarIterations);
        seekBarIterations.setMax((int) Math.sqrt(MandelbrotViewBase.MAX_ITERATIONS) + 1);
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

        juliaView = (JuliaView) findViewById(R.id.julia_view);
        mandelbrotView = (MandelbrotView) findViewById(R.id.mandelbrot_view);
        mandelbrotView.setOnPointSelected(new MandelbrotViewBase.OnPointSelected() {
            @Override
            public void pointSelected(double x, double y) {
                juliaView.terminateThreads();
                juliaView.setJuliaCoords(mandelbrotView.getXCenter(), mandelbrotView.getYCenter());
                juliaView.render();
            }
        });
        mandelbrotView.setOnCoordinatesChanged(coordinatesChangedListener);

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
        });

        updateIterationBar();
    }

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
            Animation anim = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom);
            anim.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    juliaView.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            juliaView.startAnimation(anim);
            juliaView.render();
        } else {
            Animation anim = AnimationUtils.loadAnimation(this, R.anim.slide_out_bottom);
            anim.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    juliaView.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            juliaView.startAnimation(anim);
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
    protected void onStop() {
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
    public void onDestroy() {
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
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            updateIterations(mandelbrotView.getNumIterations() - iterationInc);
            updateIterationBar();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            updateIterations(mandelbrotView.getNumIterations() + iterationInc);
            updateIterationBar();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public boolean onTouchEvent(MotionEvent event) {
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
                String path = Environment.getExternalStorageDirectory().getAbsolutePath();
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
                path += "/" + f.format(new Date()) + ".png";
                mandelbrotView.savePicture(path);
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
                LayoutInflater inflater = (LayoutInflater) MandelbrotActivity.this.getSystemService(LAYOUT_INFLATER_SERVICE);
                View layout = inflater.inflate(R.layout.about, null);
                TextView txtAbout = (TextView) layout.findViewById(R.id.txtAbout);
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

    private boolean isSettingsVisible() {
        return settingsContainer.getVisibility() == View.VISIBLE;
    }

    private void toggleSettings() {
        if (settingsContainer.getAnimation() != null && !settingsContainer.getAnimation().hasEnded()) {
            return;
        }
        if (settingsContainer.getVisibility() != View.VISIBLE) {
            settingsContainer.setVisibility(View.VISIBLE);
            Animation anim = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom);
            anim.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    settingsContainer.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            settingsContainer.startAnimation(anim);
        } else {
            Animation anim = AnimationUtils.loadAnimation(this, R.anim.slide_out_bottom);
            anim.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    settingsContainer.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            settingsContainer.startAnimation(anim);
        }
    }

    private void toggleJulia() {
        if (juliaView.getAnimation() != null && !juliaView.getAnimation().hasEnded()) {
            return;
        }
        juliaEnabled = !juliaEnabled;
        updateJulia();
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

}
