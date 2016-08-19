package com.rainbowcreatures.FlashyWrappersAndroidHW;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREExtension;
import com.rainbowcreatures.FlashyWrappersAndroidHW.FlashyWrappersContext;

public class FlashyWrappers implements FREExtension {

	public static FREContext currentAIRContext = null;
	
	@Override
	public FREContext createContext(String arg0) {
		// TODO Auto-generated method stub
		currentAIRContext = new FlashyWrappersContext();
		return currentAIRContext;		
	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub

	}

	@Override
	public void initialize() {
		// TODO Auto-generated method stub

	}

}
