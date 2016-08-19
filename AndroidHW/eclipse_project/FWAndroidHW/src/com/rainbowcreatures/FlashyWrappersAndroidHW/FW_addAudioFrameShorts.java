package com.rainbowcreatures.FlashyWrappersAndroidHW;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import com.adobe.fre.FREByteArray;
import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class FW_addAudioFrameShorts implements FREFunction {

	private FlashyWrappersWrapper _encoder = null;

	public FW_addAudioFrameShorts(FlashyWrappersWrapper encoder) {
		_encoder = encoder;
	}

	@Override
	public FREObject call(FREContext arg0, FREObject[] arg1) {
		// TODO Auto-generated method stub		
		try {
			FREByteArray ba = (FREByteArray) arg1[0];
			ba.acquire();
			ByteBuffer bb = ba.getBytes().order(ByteOrder.LITTLE_ENDIAN);
			byte[] bytes = new byte[(int) ba.getLength()];
			bb.get(bytes);						
			ba.release();			
			_encoder.GLESComposer.addAudioFrameShorts(bytes);
		}
		catch (Exception e) {
			arg0.dispatchStatusEventAsync("error", e.getMessage());
		}		
		return null;
	}

}
