package com.rainbowcreatures.FlashyWrappersAndroidHW;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;
import com.adobe.fre.FREWrongThreadException;

public class FW_getAndroidVideoPath implements FREFunction {

	@Override
	public FREObject call(FREContext arg0, FREObject[] arg1) {
		// TODO Auto-generated method stub
		String path = FlashyWrappers.currentAIRContext.getActivity().getExternalFilesDir(null).getAbsolutePath() + "/";
				
		FREObject result = null;
		try {
			result = FREObject.newObject(path);
		} catch (FREWrongThreadException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}				
		return result;
	}

}
