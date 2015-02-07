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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
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

	MandelbrotCanvas mandelbrotView;
	FrameLayout topLayout;
	View iterationsContainer;
	public TextView txtInfo, txtIterations;
	Button btnOptions;
	Button btnColorScheme;
	SeekBar seekBarIterations;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main);
        topLayout = (FrameLayout)findViewById(R.id.topLayout);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("");
        forceOverflowMenuIcon(this);

        txtInfo = (TextView)findViewById(R.id.txtInfo);
        txtInfo.setVisibility(View.INVISIBLE);
        txtIterations = (TextView)findViewById(R.id.txtIterations);

        btnColorScheme = (Button)findViewById(R.id.btnColorScheme);
        btnColorScheme.getBackground().setAlpha(128);
        btnColorScheme.setVisibility(View.INVISIBLE);
        btnColorScheme.setOnClickListener(new OnClickListener(){
			public void onClick(View arg0) {
	        	mandelbrotView.currentColorScheme++;
	        	mandelbrotView.setColorScheme();
	        	mandelbrotView.RenderMandelbrot();
			}
        });

        btnOptions = (Button)findViewById(R.id.btnOptions);
        btnOptions.getBackground().setAlpha(128);
        btnOptions.setOnClickListener(new OnClickListener(){
			public void onClick(View arg0) {
	        	if(txtInfo.getVisibility() != View.VISIBLE){
	        		btnOptions.setText("Hide");
	        		txtInfo.setVisibility(View.VISIBLE);
	        		btnReset.setVisibility(View.VISIBLE);
	        		btnAbout.setVisibility(View.VISIBLE);
	        		btnColorScheme.setVisibility(View.VISIBLE);
	        		btnSave.setVisibility(View.VISIBLE);
	        		iterationsContainer.setVisibility(View.VISIBLE);
	        		
	        	}else{
	        		btnOptions.setText("Options");
	        		txtInfo.setVisibility(View.INVISIBLE);
	        		btnReset.setVisibility(View.INVISIBLE);
	        		btnAbout.setVisibility(View.INVISIBLE);
	        		btnColorScheme.setVisibility(View.INVISIBLE);
	        		btnSave.setVisibility(View.INVISIBLE);
	        		iterationsContainer.setVisibility(View.GONE);

	        	}
			}
        });

        iterationsContainer = findViewById(R.id.iterationsContainer);
        iterationsContainer.setVisibility(View.GONE);
        seekBarIterations = (SeekBar)findViewById(R.id.seekBarIterations);
        seekBarIterations.setMax((int)Math.sqrt(MandelbrotCanvas.MAX_ITERATIONS) + 1);
        seekBarIterations.setOnSeekBarChangeListener(new OnSeekBarChangeListener(){
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if(!fromUser)
					return;
				mandelbrotView.numIterations = progress * progress;
				if(mandelbrotView.numIterations < MandelbrotCanvas.MIN_ITERATIONS)
					mandelbrotView.numIterations = MandelbrotCanvas.MIN_ITERATIONS;
				if(mandelbrotView.numIterations > MandelbrotCanvas.MAX_ITERATIONS)
					mandelbrotView.numIterations = MandelbrotCanvas.MAX_ITERATIONS;
				mandelbrotView.RenderMandelbrot();
			}
			public void onStartTrackingTouch(SeekBar arg0) {
			}
			public void onStopTrackingTouch(SeekBar arg0) {
			}
        });
        
        mandelbrotView = new MandelbrotCanvas(this);
        topLayout.addView(mandelbrotView, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        
        //restore settings...
        try{
        	SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        	mandelbrotView.xcenter = Double.parseDouble(settings.getString("xcenter", "-0.5"));
        	mandelbrotView.ycenter = Double.parseDouble(settings.getString("ycenter", "0.0"));
        	mandelbrotView.xextent = Double.parseDouble(settings.getString("xextent", "3.0"));
        	mandelbrotView.numIterations = settings.getInt("iterations", 128);
        	mandelbrotView.currentColorScheme = settings.getInt("colorscheme", 0);
        	
        }catch(Exception ex){ }
        
        seekBarIterations.setProgress((int)Math.sqrt(mandelbrotView.numIterations));
        
	}

    /**
     * Helper function to force the Activity to show the three-dot overflow icon in its ActionBar.
     * @param activity Activity whose overflow icon will be forced.
     */
    private static void forceOverflowMenuIcon(Activity activity) {
        try {
            ViewConfiguration config = ViewConfiguration.get(activity);
            // Note: this field doesn't exist in 2.3, so those users will need to tap the physical menu button,
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
    protected void onStop(){
    	super.onStop();
    	SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
    	SharedPreferences.Editor editor = settings.edit();
    	editor.putString("xcenter", Double.toString(mandelbrotView.xcenter));
    	editor.putString("ycenter", Double.toString(mandelbrotView.ycenter));
    	editor.putString("xextent", Double.toString(mandelbrotView.xextent));
    	editor.putInt("iterations", mandelbrotView.numIterations);
    	editor.putInt("colorscheme", mandelbrotView.currentColorScheme);
    	editor.commit();
    }
    
	@Override
	public void onDestroy(){
		mandelbrotView.TerminateThread();
		
		mandelnative.ReleaseParameters();
		mandelnative.ReleaseBitmap();
		super.onDestroy();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		int iterationInc = mandelbrotView.numIterations / 16;
		if(iterationInc < 1)
			iterationInc = 1;
		if (keyCode == KeyEvent.KEYCODE_BACK){
			finish();
			return true;
		}
		else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN){
			mandelbrotView.numIterations -= iterationInc;
			if(mandelbrotView.numIterations < MandelbrotCanvas.MIN_ITERATIONS)
				mandelbrotView.numIterations = MandelbrotCanvas.MIN_ITERATIONS;
			seekBarIterations.setProgress((int)Math.sqrt(mandelbrotView.numIterations));
			mandelbrotView.RenderMandelbrot();
			return true;
		}
		else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP){
			mandelbrotView.numIterations += iterationInc;
			if(mandelbrotView.numIterations > MandelbrotCanvas.MAX_ITERATIONS)
				mandelbrotView.numIterations = MandelbrotCanvas.MAX_ITERATIONS;
			seekBarIterations.setProgress((int)Math.sqrt(mandelbrotView.numIterations));
			mandelbrotView.RenderMandelbrot();
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
            case R.id.menu_julia_mode:
                mandelbrotView.juliaMode = !mandelbrotView.juliaMode;
                mandelbrotView.RenderMandelbrot();
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
            case R.id.menu_reset:
                mandelbrotView.Reset();
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
                    } });
                alertDialog.show();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

}


