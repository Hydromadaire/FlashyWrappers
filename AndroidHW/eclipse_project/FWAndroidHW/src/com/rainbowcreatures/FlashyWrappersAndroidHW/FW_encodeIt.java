package com.rainbowcreatures.FlashyWrappersAndroidHW;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class FW_encodeIt implements FREFunction {
	private FlashyWrappersWrapper _encoder = null;
	
	public FW_encodeIt(FlashyWrappersWrapper encoder) {
		_encoder = encoder;
	}
	
	@Override
	public FREObject call(FREContext arg0, FREObject[] arg1) {
		// TODO Auto-generated method stub
		try {
			if (_encoder.GLESRecorder.state == "started") {
				_encoder.GLESRecorder.stop();
			}
			if (_encoder.GLESComposer.state == "started") {
				_encoder.GLESComposer.stop();
			}
		}
		catch (Exception e) {
			arg0.dispatchStatusEventAsync("error", e.getMessage());
		}
		return null;
	}

}
