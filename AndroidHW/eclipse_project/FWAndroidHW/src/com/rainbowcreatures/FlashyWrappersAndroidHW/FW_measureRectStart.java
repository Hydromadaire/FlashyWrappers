package com.rainbowcreatures.FlashyWrappersAndroidHW;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class FW_measureRectStart implements FREFunction {

private FlashyWrappersWrapper _encoder = null;
	
	public FW_measureRectStart(FlashyWrappersWrapper encoder) {
		_encoder = encoder;
	}

	@Override
	public FREObject call(FREContext arg0, FREObject[] arg1) {
		// TODO Auto-generated method stub
		FREObject result = null;
		try {
			_encoder.GLESRecorder.measureRectStart();				
		}
		catch (Exception e) {
			arg0.dispatchStatusEventAsync("error", e.getMessage());
		}
		return result;
	}


}
