package com.rainbowcreatures.FlashyWrappersAndroidHW;

import android.os.Handler;
import android.text.format.Time;
import android.view.Choreographer.FrameCallback;
import android.view.Choreographer;

// native frame callback for automatic recording without the need to call capture() from within AS3

public class FlashyWrappersFrameCallback implements FrameCallback {
	FlashyWrappersWindowsRecorder _encoder = null;
	long millisOld = 0;
	private final Choreographer mChoreographer;
	private boolean mShouldStop = false;
	private double lastScreenshot = 0;
	
	public FlashyWrappersFrameCallback(FlashyWrappersWindowsRecorder encoder, Choreographer ch) {
		_encoder = encoder;
		mChoreographer = ch;
	}
	
	@Override
	public void doFrame(long arg0) {
		// TODO Auto-generated method stub
		if (mShouldStop) return;
		try {
			// take screenshot each second
//			if (System.currentTimeMillis() - lastScreenshot > 1000) {
//				lastScreenshot = System.currentTimeMillis();
			_encoder.captureWindowsFrame(100);				
//			}
			// capture frame & render back 
			//_encoder.captureFullscreenNative();			
			mChoreographer.postFrameCallback(this);			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public void start() {
		mShouldStop = false;
	}
	public void stop() {
		mShouldStop = true;
	}
}
