package com.rainbowcreatures.FlashyWrappersAndroidHW;

import android.content.ComponentCallbacks2;
import android.content.res.Configuration;

public class FlashyWrappersBackgroundDetect implements ComponentCallbacks2 {

	@Override
	public void onConfigurationChanged(Configuration arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onLowMemory() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTrimMemory(int level) {
		// TODO Auto-generated method stub
		  if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
	            // We're in the Background			  
			  try {
				FlashyWrappersWrapper.instance().stopAll();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			  FlashyWrappersWrapper.instance().wasInBackground = true;
		  }
	}

}
