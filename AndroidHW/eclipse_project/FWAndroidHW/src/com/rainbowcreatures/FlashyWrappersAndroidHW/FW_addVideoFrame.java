package com.rainbowcreatures.FlashyWrappersAndroidHW;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import com.adobe.fre.FREByteArray;
import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class FW_addVideoFrame implements FREFunction {

	private FlashyWrappersWrapper _encoder = null;
	
	public FW_addVideoFrame(FlashyWrappersWrapper encoder) {
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
			boolean res = _encoder.GLESComposer.addVideoFrame(bb);
			result = FREObject.newObject(res);
		}
		catch (Exception e) {
			arg0.dispatchStatusEventAsync("error", e.getMessage());
		}
		return result;
	}

}
