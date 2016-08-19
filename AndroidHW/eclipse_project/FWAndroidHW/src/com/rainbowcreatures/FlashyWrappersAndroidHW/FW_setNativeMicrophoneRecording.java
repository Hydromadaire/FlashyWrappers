package com.rainbowcreatures.FlashyWrappersAndroidHW;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class FW_setNativeMicrophoneRecording implements FREFunction {

private FlashyWrappersWrapper _encoder = null;
	
	public FW_setNativeMicrophoneRecording(FlashyWrappersWrapper encoder) {
		_encoder = encoder;
	}
	
	@Override
	public FREObject call(FREContext arg0, FREObject[] arg1) {
		try {
			Boolean b = arg1[0].getAsBool();			
			_encoder.GLESComposer.setNativeMicrophoneRecording(b);
		} catch (Exception e) {
			arg0.dispatchStatusEventAsync("error", e.getMessage());
		}
		// TODO Auto-generated method stub
		return null;
	}

}
