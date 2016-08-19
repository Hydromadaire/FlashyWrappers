package com.rainbowcreatures.FlashyWrappersAndroidHW;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Environment;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class FW_saveToGallery implements FREFunction {

	private FlashyWrappersWrapper _encoder = null;

	public FW_saveToGallery(FlashyWrappersWrapper encoder) {
		_encoder = encoder;
	}

	@Override
	public FREObject call(FREContext arg0, FREObject[] arg1) {
		// TODO Auto-generated method stub
		try {
			String albumName = arg1[1].getAsString();
			if (albumName == "") albumName = "Videos";
			String sourcePath = arg1[2].getAsString();
			String targetPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath() + "/" + arg1[0].getAsString() + "/" + albumName;
		
			File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), arg1[0].getAsString());
			// Create the storage directory if it does not exist
			if (! mediaStorageDir.exists()){
				if (! mediaStorageDir.mkdirs()){
					FWLog.i("failed to create directory");
					throw new Error("failed to create directory");
				}
			}
			
			FileInputStream inStream = new FileInputStream(new File(sourcePath));
			FileOutputStream outStream = new FileOutputStream(new File(targetPath));
			FileChannel inChannel = inStream.getChannel();
			FileChannel outChannel = outStream.getChannel();
			inChannel.transferTo(0, inChannel.size(), outChannel);
			inStream.close();
			outStream.close();			
			// force media scanner for the video to appear in the gallery
			MediaScannerConnection.scanFile(FlashyWrappers.currentAIRContext.getActivity(), new String[] {targetPath}, null, new MediaScannerConnection.OnScanCompletedListener() {
				
				@Override
				public void onScanCompleted(String path, Uri uri) {
					// TODO Auto-generated method stub
					FlashyWrappers.currentAIRContext.dispatchStatusEventAsync("gallery_saved", "");
				}
			});
		} catch (Exception e) {
			arg0.dispatchStatusEventAsync("gallery_failed", e.getMessage());
		}	    		
		return null;
	}

}
