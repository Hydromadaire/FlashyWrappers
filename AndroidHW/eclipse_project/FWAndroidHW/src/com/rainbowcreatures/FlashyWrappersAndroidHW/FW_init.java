package com.rainbowcreatures.FlashyWrappersAndroidHW;

import android.opengl.EGL14;
import android.opengl.GLES20;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class FW_init implements FREFunction {

	private FlashyWrappersWrapper _encoder = null;
	
	public FW_init(FlashyWrappersWrapper encoder) {
		_encoder = encoder;
	}
	
	@Override
	public FREObject call(FREContext arg0, FREObject[] arg1) {
		// TODO Auto-generated method stub
		try {
			int width = arg1[0].getAsInt();
			int height = arg1[1].getAsInt();
			int fps = arg1[2].getAsInt();
			int bitrate = arg1[3].getAsInt();		
			int stage_fps = arg1[4].getAsInt();
			int audio_sample_rate = arg1[6].getAsInt();
			int audio_number_channels = arg1[7].getAsInt();
			int realtime = arg1[8].getAsInt();
			int audio = arg1[9].getAsInt();
						
			// set recording for AIR
			_encoder.setProfile(FlashyWrappersWrapper.PROFILE_AIR);
			// start all components
			_encoder.GLESComposer.start(width, height, fps, bitrate, stage_fps, arg1[5].getAsString(), audio_sample_rate, audio_number_channels, realtime, audio, EGL14.eglGetCurrentContext());
			// only capture using OpenGL in realtime mode
			if (realtime == 1) {
				_encoder.GLESRecorder.start();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}
