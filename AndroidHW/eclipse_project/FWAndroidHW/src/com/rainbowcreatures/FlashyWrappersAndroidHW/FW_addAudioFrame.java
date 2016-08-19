package com.rainbowcreatures.FlashyWrappersAndroidHW;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import com.adobe.fre.FREByteArray;
import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class FW_addAudioFrame implements FREFunction {

	private FlashyWrappersWrapper _encoder = null;
	
	public FW_addAudioFrame(FlashyWrappersWrapper encoder) {
		_encoder = encoder;
	}
	
	@Override
	public FREObject call(FREContext arg0, FREObject[] arg1) {
		// TODO Auto-generated method stub
				
		try {
			FREByteArray ba = (FREByteArray) arg1[0];
			ba.acquire();
			ByteBuffer bb = ba.getBytes();
			byte[] bytes = new byte[(int) ba.getLength()];
			bb.get(bytes);			
			FloatBuffer bb2 = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
			ba.release();			
			_encoder.GLESComposer.addAudioFrame(bb2);
		}
		catch (Exception e) {
			arg0.dispatchStatusEventAsync("error", e.getMessage());
		}
		
		return null;
	}

}
