package com.rainbowcreatures.FlashyWrappersAndroidHW;

import java.io.IOException;
import android.util.Log;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class FWLog {
	
	public static boolean VERBOSE = false;
	public static boolean LOGTOFILE = true;
	private static String logPath = "";
	
	public static void start() {		
		if (LOGTOFILE) {
			logPath = FlashyWrappers.currentAIRContext.getActivity().getExternalFilesDir(null).getAbsolutePath();
			i("Writing logfile to " + logPath);
			try {
				File file = new File(logPath + "/FWlog.txt");    			
				FileOutputStream stream;
				stream = new FileOutputStream(file);
				stream.close();
			} catch (IOException e) {
	    			Log.e("[FlashyWrappers]", "Log file write failed: " + e.toString());
			}				
			i("FW Log started");
			i("API level: " + android.os.Build.VERSION.SDK_INT);      // API Level
			i("Device: " + android.os.Build.DEVICE);        		    // Device
			i("Model: " + android.os.Build.MODEL);            		// Model 
			i("Product: " + android.os.Build.PRODUCT);
		} else {
			i("Logs will be writing only to device log, logfile is disabled");
		}
	}
	
    public static void i(String log) {
    	android.text.format.DateFormat df = new android.text.format.DateFormat();
    	String currentTime = (String) df.format("yyyy-MM-dd hh:mm:ss", new java.util.Date());    	    	
    	Log.i("[FlashyWrappers]", log);
    	if (LOGTOFILE) {
    		try {    		    			
    			File file = new File(logPath + "/FWlog.txt");    			
    			FileOutputStream stream = new FileOutputStream(file, true);
    			try {
    				String line = "[FlashyWrappers][" + currentTime + "]" + log + System.getProperty("line.separator");
    				stream.write(line.getBytes());
    			} finally {
    				stream.close();
    			}    		
    		}
    		catch (IOException e) {
    			Log.e("[FlashyWrappers]", "Log file write failed: " + e.toString());
    		}
    	}
    }
}
