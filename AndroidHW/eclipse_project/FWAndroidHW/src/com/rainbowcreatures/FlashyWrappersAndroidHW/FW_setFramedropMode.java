package com.rainbowcreatures.FlashyWrappersAndroidHW;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class FW_setFramedropMode implements FREFunction {

	private FlashyWrappersWrapper _encoder = null;
	
	public FW_setFramedropMode(FlashyWrappersWrapper encoder) {
		_encoder = encoder;
	}
	
	@Override
	public FREObject call(FREContext arg0, FREObject[] arg1) {
		try {
			int mode = arg1[0].getAsInt();
			_encoder.GLESComposer.setFramedropMode(mode);
		} catch (Exception e) {
			arg0.dispatchStatusEventAsync("error", e.getMessage());
		}
		return null;
	}
}
