package com.rainbowcreatures.FlashyWrappersAndroidHW;

import java.nio.ByteBuffer;

import com.adobe.fre.FREByteArray;
import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class FW_setLogo implements FREFunction {

	private FlashyWrappersWrapper _encoder = null;
	
	public FW_setLogo(FlashyWrappersWrapper encoder) {
		_encoder = encoder;
	}

	@Override
	public FREObject call(FREContext arg0, FREObject[] arg1) {
		// TODO Auto-generated method stub
		FREObject result = null;
		try {
				FREByteArray ba = (FREByteArray) arg1[0];
				ba.acquire();
				ByteBuffer bb = ba.getBytes();
				ba.release();			
				_encoder.GLESComposer.setLogo(bb);				
		}
		catch (Exception e) {
			arg0.dispatchStatusEventAsync("error", e.getMessage());
		}
		return result;
	}

}
