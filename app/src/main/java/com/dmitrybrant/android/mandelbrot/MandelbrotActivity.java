package com.dmitrybrant.android.mandelbrot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class MandelbrotActivity extends Activity {
	public static final String PREFS_NAME = "MandelbrotActivityPrefs";
	
	static{
		System.loadLibrary("mandelnative_jni");
	}
	
	MandelbrotCanvas mandelbrotView;
	FrameLayout topLayout;
	View iterationsContainer;
	public TextView txtInfo, txtIterations;
	Button btnOptions, btnSave, btnReset;
	Button btnJulia, btnAbout, btnColorScheme;
	SeekBar seekBarIterations;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.main);
        topLayout = (FrameLayout)findViewById(R.id.topLayout);
        
        txtInfo = (TextView)findViewById(R.id.txtInfo);
        txtInfo.setVisibility(View.INVISIBLE);
        
        txtIterations = (TextView)findViewById(R.id.txtIterations);
        
        btnJulia = (Button)findViewById(R.id.btnJulia);
        btnJulia.getBackground().setAlpha(128);
        btnJulia.setOnClickListener(new OnClickListener(){
			public void onClick(View arg0) {
	        	mandelbrotView.juliaMode = !mandelbrotView.juliaMode;
	        	btnJulia.setText(mandelbrotView.juliaMode ? "Mandelbrot mode" : "Julia mode");
	        	mandelbrotView.RenderMandelbrot();
			}
        });
        
        btnAbout = (Button)findViewById(R.id.btnAbout);
        btnAbout.getBackground().setAlpha(128);
        btnAbout.setVisibility(View.INVISIBLE);
        btnAbout.setOnClickListener(new OnClickListener(){
			public void onClick(View arg0) {
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
			}
        });
        
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
        
        btnSave = (Button)findViewById(R.id.btnSave);
        btnSave.getBackground().setAlpha(128);
        btnSave.setVisibility(View.INVISIBLE);
        btnSave.setOnClickListener(new OnClickListener(){
			public void onClick(View arg0) {
				try{
					String path = Environment.getExternalStorageDirectory().getAbsolutePath();
					SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
					path += "/" + f.format(new Date()) + ".png";
					mandelbrotView.SavePicture(path);
					Toast.makeText(MandelbrotActivity.this, "Picture saved as: " + path, Toast.LENGTH_SHORT).show();
				}catch(Exception ex){
					Toast.makeText(MandelbrotActivity.this, "Error saving file: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
				}
			}
        });
        
        btnReset = (Button)findViewById(R.id.btnReset);
        btnReset.getBackground().setAlpha(128);
        btnReset.setVisibility(View.INVISIBLE);
        btnReset.setOnClickListener(new OnClickListener(){
			public void onClick(View arg0) {
				mandelbrotView.Reset();
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

}


