package com.rainbowcreatures.FlashyWrappersAndroidHW;

import java.util.Map;
import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import java.util.HashMap;

public class FlashyWrappersContext extends FREContext {

	FlashyWrappersEncoder encoder;
	FlashyWrappersWrapper wrapper;
	
	@Override
	public void dispose() {
		// TODO Auto-generated method stub

	}

	@Override
	public Map<String, FREFunction> getFunctions() {
		// TODO Auto-generated method stub
		
		encoder = new FlashyWrappersEncoder();
		Map<String, FREFunction> functionMap = new HashMap<String, FREFunction>();		
		
		try {
			wrapper = FlashyWrappersWrapper.instance();
			functionMap.put("fw_ffmpeg_init", new FW_init(wrapper));		
			functionMap.put("fw_ffmpeg_free", new FW_encodeIt(wrapper));		
			functionMap.put("fw_setLogo", new FW_setLogo(wrapper));
			functionMap.put("fw_captureFrame", new FW_captureFullscreen(wrapper));		
			functionMap.put("fw_ffmpeg_addAudioFrame", new FW_addAudioFrame(wrapper));
			functionMap.put("fw_ffmpeg_addAudioFrameShorts", new FW_addAudioFrameShorts(wrapper));
			functionMap.put("fw_ffmpeg_addVideoFrame", new FW_addVideoFrame(wrapper));		
			functionMap.put("fw_setLogging", new FW_setLogging());
			functionMap.put("fw_getAndroidVideoPath", new FW_getAndroidVideoPath());
			functionMap.put("fw_setFramedropMode", new FW_setFramedropMode(wrapper));
			functionMap.put("fw_setPTSMode", new FW_setPTSMode(wrapper));
			functionMap.put("fw_setNativeMicrophoneRecording", new FW_setNativeMicrophoneRecording(wrapper));
			functionMap.put("fw_saveToGallery", new FW_saveToGallery(wrapper));
			functionMap.put("fw_setCaptureRectangle", new FW_setCaptureRectangle(wrapper));			
			functionMap.put("fw_setCaptureStage", new FW_setCaptureStage(wrapper));
			functionMap.put("fw_measureRectStart", new FW_measureRectStart(wrapper));			
			functionMap.put("fw_fixAndroidEncoderQuirks", new FW_fixAndroidEncoderQuirks(wrapper));
			FWLog.start();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return functionMap;		
	}
}
