package com.rainbowcreatures.FlashyWrappersAndroidHW;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import android.util.Log;
	
public class FlashyWrappersClient {
	
        URL connectURL;
        String responseString;
        String Game;
        byte[ ] dataToServer;        
        FileInputStream fileInputStream = null;
        Thread clientThread = null;

        /**
         * The POST runnable which uploads the mp4 file on background thread
         * @author Pavel
         *
         */
        class POSTRunnable implements Runnable {
        	@Override
        	public void run() {
        		String iFileName = "video.mp4";
        		String lineEnd = "\r\n";
        		String twoHyphens = "--";
        		String boundary = "*****";
        		String Tag="fSnd";
        		try
        		{
        			FWLog.i("Starting Http File Sending to URL");

        			// Open a HTTP connection to the URL
        			HttpURLConnection conn = (HttpURLConnection)connectURL.openConnection();

        			// Allow Inputs
        			conn.setDoInput(true);

        			// Allow Outputs
        			conn.setDoOutput(true);

        			// Don't use a cached copy.
        			conn.setUseCaches(false);

        			// Use a post method.
        			conn.setRequestMethod("POST");

        			conn.setRequestProperty("Connection", "Keep-Alive");

        			conn.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary);

        			DataOutputStream dos = new DataOutputStream(conn.getOutputStream());

        			dos.writeBytes(twoHyphens + boundary + lineEnd);
        			dos.writeBytes("Content-Disposition: form-data; name=\"game\""+ lineEnd);
        			dos.writeBytes(lineEnd);
        			dos.writeBytes(Game);
        			dos.writeBytes(lineEnd);
        			dos.writeBytes(twoHyphens + boundary + lineEnd);

/*        			dos.writeBytes("Content-Disposition: form-data; name=\"description\""+ lineEnd);
        			dos.writeBytes(lineEnd);
        			dos.writeBytes(Description);
        			dos.writeBytes(lineEnd);
        			dos.writeBytes(twoHyphens + boundary + lineEnd);*/

        			dos.writeBytes("Content-Disposition: form-data; name=\"uploadedfile\";filename=\"" + iFileName +"\"" + lineEnd);
        			dos.writeBytes(lineEnd);

        			FWLog.i("Headers are written");

        			// create a buffer of maximum size
        			int bytesAvailable = fileInputStream.available();

        			int maxBufferSize = 1024;
        			int bufferSize = Math.min(bytesAvailable, maxBufferSize);
        			byte[ ] buffer = new byte[bufferSize];

        			// read file and write it into form...
        			int bytesRead = fileInputStream.read(buffer, 0, bufferSize);

        			while (bytesRead > 0)
        			{
        				dos.write(buffer, 0, bufferSize);
        				bytesAvailable = fileInputStream.available();
        				bufferSize = Math.min(bytesAvailable,maxBufferSize);
        				bytesRead = fileInputStream.read(buffer, 0,bufferSize);
        			}
        			dos.writeBytes(lineEnd);
        			dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

        			// close streams
        			fileInputStream.close();

        			dos.flush();

        			FWLog.i("File sent, response code: " + String.valueOf(conn.getResponseCode()));

        			InputStream is = conn.getInputStream();

        			// retrieve the response from server
        			int ch;
        			StringBuffer b = new StringBuffer();
        			while( ( ch = is.read() ) != -1 ){ b.append( (char)ch ); }
        			String s = b.toString();
        			FWLog.i("Response from server: " + s);
        			dos.close();
        		}
        		catch (MalformedURLException ex)
        		{
        			FWLog.i("URL error: " + ex.getMessage());
        		}

        		catch (IOException ioe)
        		{
        			FWLog.i("IO error: " + ioe.getMessage());
        		}
        	}        
        }

        /**
         * Constructor
         * @param urlString where to post the video
         * @param vTitle
         * @param vDesc
         */
        FlashyWrappersClient(String urlString, String vGame){
        	try {
        		connectURL = new URL(urlString);
        		Game = vGame;
        	} catch (Exception ex) {
        		Log.i("HttpFileUpload", "Wrong URL");
        	}
        }
	
        public void send(String filename) throws Exception {
        	FileInputStream fStream = new FileInputStream(filename);
            fileInputStream = fStream;
            clientThread = new Thread(new POSTRunnable());
            clientThread.start();
        }	
    	
}