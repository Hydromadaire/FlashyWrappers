package com.rainbowcreatures.FlashyWrappersAndroidHW;

import android.app.Activity;
import android.os.Bundle;

/**
 * Custom FlashyWrappers activity to detect what the app is doing, so we can control 
 * FW in response instead of leeching to the app's activity.
 * 
 * @author Pavel
 *
 */
public class FlashyWrappersActivity extends Activity {	
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {    
    	super.onCreate(savedInstanceState);
    }
    
    @Override
    public void onWindowFocusChanged (boolean hasFocus) {
    	super.onWindowFocusChanged(hasFocus);
    }

    @Override
    protected void onStart() {
    	super.onStart();
    }

    @Override
    protected void onRestart() {
    	super.onRestart();    	
    }

    @Override
    protected void onResume() {
    	super.onResume();
    	
    }

    @Override
    protected void onPause() {
    	super.onPause();
    }

    @Override
    protected void onStop() {
    	super.onStop();
    }

    @Override
    protected void onDestroy() {
    	super.onDestroy();
    }
}
