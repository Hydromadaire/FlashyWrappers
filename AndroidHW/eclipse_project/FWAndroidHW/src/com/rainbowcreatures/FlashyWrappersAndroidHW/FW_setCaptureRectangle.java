package com.rainbowcreatures.FlashyWrappersAndroidHW;

import java.nio.ByteBuffer;

import com.adobe.fre.FREByteArray;
import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class FW_setCaptureRectangle implements FREFunction {

	private FlashyWrappersWrapper _encoder = null;
	
	public FW_setCaptureRectangle(FlashyWrappersWrapper encoder) {
		_encoder = encoder;
	}
	
	@Override
	public FREObject call(FREContext arg0, FREObject[] arg1) {
		// TODO Auto-generated method stub
			FREObject result = null;
			try {
					int x = arg1[0].getAsInt();
					int y = arg1[1].getAsInt();
					int w = arg1[2].getAsInt();
					int h = arg1[3].getAsInt();
					int color = arg1[4].getAsInt();					
					int mode = arg1[5].getAsInt();
					if (mode == FlashyWrappersGLESVideoComposer.CAPTURERECT_MODE_CALCULATE) {
						_encoder.GLESComposer.setCaptureRectangle(x, y, w, h, color, mode);
					} else {
						_encoder.GLESComposer.setCaptureRectColor(color);
					}
			}
			catch (Exception e) {
				arg0.dispatchStatusEventAsync("error", e.getMessage());
			}
			return result;
	}

}
