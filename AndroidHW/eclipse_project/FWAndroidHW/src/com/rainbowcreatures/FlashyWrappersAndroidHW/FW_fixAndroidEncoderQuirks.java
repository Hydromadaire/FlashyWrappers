package com.rainbowcreatures.FlashyWrappersAndroidHW;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;
import com.adobe.fre.FREWrongThreadException;

public class FW_fixAndroidEncoderQuirks implements FREFunction {

	private FlashyWrappersWrapper _encoder = null;
	
	public FW_fixAndroidEncoderQuirks(FlashyWrappersWrapper encoder) {
		_encoder = encoder;
	}

	@Override
	public FREObject call(FREContext arg0, FREObject[] arg1) {
		// TODO Auto-generated method stub
		String quirk = _encoder.GLESComposer.fixAndroidEncoderQuirks();
		FREObject result = null;
		try {
			result = FREObject.newObject(quirk);
		} catch (FREWrongThreadException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		return result;
	}

}
