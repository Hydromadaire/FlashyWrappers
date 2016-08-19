package com.rainbowcreatures.FlashyWrappersAndroidHW;
/*
 * FlashyWrappers 2.4
 * Copyright 2015 FlashyWrappers.com - Pavel Langweil 
 *
 */

import android.R;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.Choreographer;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.FloatBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;

import android.view.Choreographer.FrameCallback;


import com.adobe.fre.FREContext;

import java.lang.Exception;
import java.lang.reflect.Method;

import android.media.*;


/**
 * Generate an MP4 file using OpenGL ES drawing commands.  Demonstrates the use of MediaMuxer
 * and MediaCodec with Surface input.
 * <p>
 * This uses various features first available in Android "Jellybean" 4.3 (API 18).  There is
 * no equivalent functionality in previous releases.
 * <p>
 * (This was derived from bits and pieces of CTS tests, and is packaged as such, but is not
 * currently part of CTS.)
 */
public class FlashyWrappersEncoder {
	
	// apps context
	private Activity activity;
	private View rootView;
	private int screenshotCountdown = 10;
	
	// general FW wide bool, if something goes wrong this should be set to false and all the 
	// methods should return just afer calling to prevent the app being captured from crashing
	public boolean allowCapture = true;
	
	public boolean behaviorLateInit = true;
	public boolean behaviorFBO0AfterInit = false;
	public boolean behaviorAutocapture = false;
	public boolean behaviorTextureInit = false;
	public boolean behaviorTextureDepthAndStencil = true;
	
	private static final String TAG = "[FlashyWrappers] ";
	private int textureColorFormat = GLES20.GL_RGBA;

	// where to put the output file (note: /sdcard requires WRITE_EXTERNAL_STORAGE permission)
	private String videoOutputPath = "";

	// parameters for the encoder
	private static final String VIDEO_MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
	private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
	
	// maximum size of the audio buffer
	private static final int AUDIO_MAX_INPUT_SIZE = 16384;
	
	private static final int PTS_AUTO = 0;
	private static final int PTS_MONO = 1;
	private static final int PTS_REALTIME = 2;

	private static final int FRAMEDROP_AUTO = 0;
	private static final int FRAMEDROP_OFF = 1;
	private static final int FRAMEDROP_ON = 2;

	// size of a frame, in pixels
	private int mWidth = -1;
	private int mHeight = -1;
	// bit rate, in bits per second
	private int mBitRate = -1;
	private int mAudioSampleRate = 44100;
	private int mAudioNumberChannels = 1;
	private boolean realtime = false;
	private boolean audio = false;

	private boolean keyframe = false;
	private boolean forceKeyframe = false;

	private int videoFramesSent = 0;
	
	// volatile marks this in similar way as if we used getter / setter and synchronized those varss
	private volatile boolean isVideoEncoding = false;
	private volatile boolean isAudioEncoding = false;
		

	private double audioNonrealtimePts = 0;
	private long videoNonrealtimePts = 0;

	private int mNumTracksAdded = 0;
	private int numTracks = 2;
	private long startWhen = 0;
	private long lastEncodedAudioTimeStamp = 0;

	// encoder / muxer state
	private MediaCodec mVideoEncoder;
	private MediaCodec mAudioEncoder;
	private CodecInputSurface mInputSurface;
	private MediaMuxer mMuxer;
	private int mVideoTrackIndex;
	private int mAudioTrackIndex;
	private boolean mMuxerStarted;
	int mEosSpinCount = 0;
    final int MAX_EOS_SPINS = 10;

	private volatile boolean nativeMicrophoneRecording;

	private volatile boolean drainedVideo = false;
	private volatile boolean drainedAudio = false;

	// allocate one of these up front so we don't need to do it every time
	private MediaCodec.BufferInfo mVideoBufferInfo;
	private MediaCodec.BufferInfo mAudioBufferInfo;

	private int nativeTotalFrames = 0;
	private int nativeBitrate = 0;
	private int nativeFps = 0;
	private int frameIndex = 0;
	private static int GLESversion;
	public static final int SAMPLES_PER_FRAME = 1024; // AAC
	public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
	public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

	private AudioRecord audioRecord;
	boolean firstFrameReady = false;

	// threading vars
	private static Object muxerLock = new Object();
	private static Object videoLock = new Object();
	private static Object audioLock = new Object();

	private Thread videoEncoderThread = null;
	private Thread audioEncoderThread = null;

	// texture backed FBO
	int textureCacheFBO = 0;

	// reference to the texture in the texture backed FBO
	int depthBuffer[] = new int[1];
	int stencilBuffer[] = new int[1];	
	int textureCacheRef = 0;
	int stageW = 0;
	int stageH = 0;
	int textureW = 0;
	int textureH = 0;
	float textureU = 1;
	float textureV = 1;
	float scaleFactor = 1;

	int PTSMode = PTS_AUTO;
	int framedropMode = FRAMEDROP_AUTO;

	// AIR's FBO handle
	int vertexBuffer;
	int indexBuffer;
	int _positionSlot;
	int _colorSlot;
	int _texCoordSlot;
	int _textureUniform;
	int programHandle;
	int programFlipHandle;

	int oldFBO = 0;
	int highresFBO = 0;
	int videoFBO = 0;

	// AIR stage fps, -1 if undetermined
	// if stage fps is set, and the desired video fps is also set, in fullscreen mode we'll record only every Nth frame
	// this should work in non-fullscreen modes as well in the future
	int stage_fps = -1;

	// by default record every frame, otherwise this counts down until we actually record a frame
	double step = 0;
	double delta = 0;
	long millisOld = 0;

	// accumulated frame steps
	double stepAccum = 0;

	// the "target" for the step increments, we will save the movie when step is equal or greater than stepTarget (whole number)
	double stepTarget = 0;

	// Java array.
	float[] Vertices = {
			1, -1, 0,  1, 0, 0, 1,  1, 0,
			1, 1, 0,  0, 1, 0, 1,  1, 1,
			-1, 1, 0,  0, 0, 1, 1,  0, 1,
			-1, -1, 0,  0, 0, 0, 1,  0, 0
	};

	byte[] logo = null;
	
	// intermediate audio buffer, we are collecting into this buffer at first to not potentially
	// lag microphone recording or other stuff, and only fill muxer on separate thead whenever possible
	LinkedBlockingQueue<FlashyWrappersAudioPacket> audioBuffer = new LinkedBlockingQueue<FlashyWrappersAudioPacket>();
	// this remembers how many packets we've added in the audio buffer queue to avoid 
	// calling the pretty slow isEmpty() on the queue
	private volatile long audioBufferPackets = 0;
	
	short indices[] = {0, 1, 2,
			2, 3, 0};

	public String profile = "AIR";
	
	float a = 0;

	public Choreographer ch = null;
	
	// Floating-point buffer
	FloatBuffer VerticesBuffer;
	ShortBuffer indicesBuffer;

	private EGLDisplay mSavedEglDisplay;
	private EGLSurface mSavedEglDrawSurface;
	private EGLSurface mSavedEglReadSurface;
	private EGLContext mSavedEglContext;
	private Boolean firstFrameCapture = false;
	
	// Runnable for the video encoder thread
	class VideoEncoderRunnable implements Runnable {
		public FlashyWrappersEncoder _encoder;        	
		@Override 
		public void run() {
			// TODO Auto-generated method stub
			try {
				if (FWLog.VERBOSE) FWLog.i("Entering video encoding thread runnable");
				// wait until we get notification to drain encoder
				while (!Thread.currentThread().isInterrupted()/*isVideoEncoding*/) {
					synchronized(videoLock) {
						if (FWLog.VERBOSE) FWLog.i("VideoLock waiting for notify in video encoder thread...");
						videoLock.wait(5000);
						if (FWLog.VERBOSE) FWLog.i("VideoLock got notify! Lets try to drain the video encoder.");
					}
					// 	we do not want the video and audio encoder threads to mess around with the
					// 	muxer both at the same time, so we synchronize around the muxerLock
					synchronized (muxerLock) {
						// 	this should happen while the current frame is rendered to screen(bad?)
						if (FWLog.VERBOSE) FWLog.i("VideoLock starting drain.");
						if (!Thread.currentThread().isInterrupted()/*isVideoEncoding*/)
							try {
								_encoder.drainVideoEncoder(false);
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						if (FWLog.VERBOSE) FWLog.i("VideoLock finished drain.");
					}
				}
				FWLog.i("Video encoding thread exiting");
				return;
			} catch (InterruptedException e) {                
			
			}
		}        	
	}

	// Runnable for the audio encoder thread
	class AudioEncoderRunnable implements Runnable {
		public FlashyWrappersEncoder _encoder;        	
		@Override
		public void run() {
				if (FWLog.VERBOSE) FWLog.i("Entering audio encoding thread runnable");
				// TODO Auto-generated method stub
				if (nativeMicrophoneRecording) {
					setupAudioRecord();
					if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
						if (FWLog.VERBOSE) FWLog.i("Setting up microphone recording success.");
						audioRecord.startRecording();						
					} else {
						if (FWLog.VERBOSE) FWLog.i("Setting up microphone recording failed!");
					}
				}				
				// wait until we get notification to drain encoder
				while (!Thread.currentThread().isInterrupted()/*isAudioEncoding*/) {					
					if (!firstFrameReady) continue;
/*					if (!nativeMicrophoneRecording) {
						synchronized(audioLock) {
							if (FWLog.VERBOSE) FWLog.i("AudioLock waiting for notify in audio encoder thread...");
							try {
								audioLock.wait(5000);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							if (FWLog.VERBOSE) FWLog.i("AudioLock got notify!");
						}
					}*/
					// we do not want the video and audio encoder threads to mess around with the
					// muxer both at the same time, so we synchronize around the muxerLock
					
					// prevent changing this in the middle
					Boolean audioBufferEmpty = audioBuffer.isEmpty()/*(audioBufferPackets == 0)*/;
					
					// don't drain encoder if audioBuffer is empty or we are recording from microphone
					if ((!audioBufferEmpty || nativeMicrophoneRecording) && !Thread.currentThread().isInterrupted()/*isAudioEncoding*/) {
						synchronized (muxerLock) {						
							try {
								if (!Thread.currentThread().isInterrupted()/*isAudioEncoding*/) _encoder.drainAudioEncoder(false);
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							// TODO maybe encoder wasnt fully drained, or, the audio data form native 
							// encoder is different from AIR, or, the rate of sending which is probably
							// faster here matters. Explore all 3 options and find the reason why it gets
							// frozen when sent from AIR, but not from here.
						}
						// send either audio from microphone or from the audio buffer
					}
					if (nativeMicrophoneRecording) {								
						if (!Thread.currentThread().isInterrupted()/*isAudioEncoding*/) sendMicrophoneAudioToEncoder(false); else {

						}
					} else {
						// end of thread detected? if yes terminate the audio encoding & buffer
						if (Thread.currentThread().isInterrupted()/*!isAudioEncoding*/) {
						//	sendAudioToEncoder(null, false);
							audioBuffer.clear();
							audioBufferPackets = 0;
						} else if (!audioBufferEmpty) {  
							// only process if there was something in the audio buffer (and we know the audio encoder was drained)					
							// start: check out the first packet in the queue
							FlashyWrappersAudioPacket ap = audioBuffer.peek();
							long size = 16384;
							if (ap.dataPointer + size > ap._data.length) {
								size = ap._data.length - ap.dataPointer;
							}
							if (FWLog.VERBOSE) FWLog.i("Sending audio data with offset at " + ap.dataPointer + " bytes (total length " + size + " bytes)");							
							ByteBuffer bbTemp = ByteBuffer.wrap(ap._data, (int)ap.dataPointer, (int)(size)).order(ByteOrder.LITTLE_ENDIAN);
							
							// 	send audio to encoder
							sendAudioToEncoder(bbTemp, false, ap._pts);
							// increment the step for the next read
							ap.dataPointer += size;
							// if we got beyond the buffer then drop it, otherwise we'll continue reading the buffer in the next cycle
							if (ap.dataPointer >= ap._data.length) {
								audioBuffer.poll();
								audioBufferPackets--; 
								if (FWLog.VERBOSE) FWLog.i("length = dataPointer in audioPacket, dropping it");
							}
							bbTemp = null;
						}
					}
				}

				FWLog.i("Stopping audio recorder...");
				
				if (nativeMicrophoneRecording) {					
					sendMicrophoneAudioToEncoder(true);
					audioRecord.stop();
				} else {
					sendAudioToEncoder(null, true, 0);
				}

				FWLog.i("Exiting audio thread runnable...");				
				return;
		}        	
	}
	
	// set the logo image - the logo for Android is embedded as .png inside the AS3 wrapper
	// then internally, it sets the Java logo byte array through this method. It basically
	// sends it as BitmapData (in byte array) so that the addVideoFrame method here can blit
	// it over video in demo version.
	public void setLogo(ByteBuffer bb) {
		logo = null;
		byte[] bytes = new byte[(int) bb.capacity()];
		bb.get(bytes);						                            
		bb.position(0);
		logo = bytes.clone();
		bytes = null;
	}
	
	public void setGLTextureColorFormat(int colorFormat) {
		textureColorFormat = colorFormat;
	}

	private void setupAudioRecord(){
		int min_buffer_size = AudioRecord.getMinBufferSize(mAudioSampleRate, CHANNEL_CONFIG, AUDIO_FORMAT);
		int buffer_size = SAMPLES_PER_FRAME * 10;
		if (buffer_size < min_buffer_size)
			buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;
		FWLog.i("Microphone min buffer size: " + min_buffer_size);
		FWLog.i("Microphone buffer size: " + buffer_size);        
		audioRecord = new AudioRecord(
				MediaRecorder.AudioSource.MIC,       // source
				mAudioSampleRate,                         // sample rate, hz
				CHANNEL_CONFIG,                      // channels
				AUDIO_FORMAT,                        // audio format
				buffer_size);                        // buffer size (bytes)
	}

	public void setNativeMicrophoneRecording(Boolean b) {
		nativeMicrophoneRecording = b;
		if (nativeMicrophoneRecording) {
			FWLog.i("Native microphone recording is ON");
		} else {
			FWLog.i("Native microphone recording is OFF");
		}
	}

	// set number of frames to encode
	public void setFramesNative(int frames) {
		nativeTotalFrames = frames;
	}

	private void renderTexture(boolean useOldFBO, boolean releaseResources) throws Exception {
		
		int oldProgram[] = new int[1];
		int oldArrayBuffer[] = new int[1];
		int oldElementArrayBuffer[] = new int[1];
			
		GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, oldProgram, 0);
		GLES20.glGetIntegerv(GLES20.GL_ARRAY_BUFFER_BINDING, oldArrayBuffer, 0);
		GLES20.glGetIntegerv(GLES20.GL_ELEMENT_ARRAY_BUFFER_BINDING, oldElementArrayBuffer, 0);
		GLES20.glUseProgram(programHandle);
		
		_positionSlot = GLES20.glGetAttribLocation(programHandle, "Position");
		_colorSlot = GLES20.glGetAttribLocation(programHandle, "SourceColor");
		_texCoordSlot = GLES20.glGetAttribLocation(programHandle, "TexCoordIn");
		_textureUniform = GLES20.glGetUniformLocation(programHandle, "Texture");
		GLES20.glEnableVertexAttribArray(_positionSlot);
		GLES20.glEnableVertexAttribArray(_colorSlot);
		GLES20.glEnableVertexAttribArray(_texCoordSlot);                              
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffer);            
		GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, VerticesBuffer.capacity() * 4, VerticesBuffer, GLES20.GL_STATIC_DRAW);            
		GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
		GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer.capacity() * 2, indicesBuffer, GLES20.GL_STATIC_DRAW);            
		GLES20.glVertexAttribPointer(_positionSlot, 3, GLES20.GL_FLOAT, false, 9 * 4, 0);
		GLES20.glVertexAttribPointer(_colorSlot, 4, GLES20.GL_FLOAT, false, 9 * 4, 4 * 3);
		GLES20.glVertexAttribPointer(_texCoordSlot, 2, GLES20.GL_FLOAT, false, 9 * 4, 4 * 7);
		
		GlUtil.checkGlError("renderTexture1a");
		//if (FWLog.VERBOSE) FWLog.i("GLES20 buffer:" + );
		// record so that we always get the right movie fps by grabbing only some frames while running on different fps
		// TODO RECORD HERE

		// now let's draw the FBO texture to screen..
		if (useOldFBO) {
			GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, oldFBO);
		}
		
		GlUtil.checkGlError("renderTexture2");
		GLES20.glClearColor(0, 0, 0, 1);
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);            
		GLES20.glViewport(0, 0, mWidth, mHeight);
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureCacheRef);
		GLES20.glUniform1i(_textureUniform, 0);
		GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, 0);
		GlUtil.checkGlError("renderTexture3");

		// release?
		if (releaseResources) {
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
			GLES20.glDisableVertexAttribArray(_positionSlot);
			GLES20.glDisableVertexAttribArray(_colorSlot);
			GLES20.glDisableVertexAttribArray(_texCoordSlot);
			GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, oldElementArrayBuffer[0]);
			GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, oldArrayBuffer[0]);
			GLES20.glUseProgram(oldProgram[0]);
			GlUtil.checkGlError("renderTexture4");			
		}
	}

	public void setPTSMode(int mode) {
		PTSMode = mode;
		FWLog.i("Forcing PTS mode " + PTSMode);    	
	}

	public void setFramedropMode(int mode) {
		framedropMode = mode;
		if (framedropMode == FRAMEDROP_AUTO) {
			if (realtime) {
				step = 1000 / (float)nativeFps;
			} else {
				step = 0;
			}
		}
		if (framedropMode == FRAMEDROP_OFF) {
			step = 0;
		}
		if (framedropMode == FRAMEDROP_ON) {
			step = 1000 / (float)nativeFps;
		}
		FWLog.i("Forcing framedrop mode " + framedropMode + ", frame step is " + step);
	}

	long audio_startPTS = 0;
	long audio_totalSamplesNum = 0;

	
	/**
	* Ensures that each audio pts differs by a constant amount from the previous one.
	* @param pts presentation timestamp in us
	* @param inputLength the number of samples of the buffer's frame
	* @return
	*/
	
	private long getJitterFreePTS(long bufferPts, long bufferSamplesNum) {
		long correctedPts = 0;
		long bufferDuration = (1000000 * bufferSamplesNum) / (mAudioSampleRate);
		//bufferPts -= bufferDuration; // accounts for the delay of acquiring the audio buffer
		if (audio_totalSamplesNum == 0) {
			// reset
			audio_startPTS = bufferPts;
			audio_totalSamplesNum = 0;
		}
		correctedPts = audio_startPTS +  (1000000 * audio_totalSamplesNum) / (mAudioSampleRate);
		if (bufferPts - correctedPts >= 2 * bufferDuration) {
			// reset
			audio_startPTS = bufferPts;
			audio_totalSamplesNum = 0;
			correctedPts = audio_startPTS;
		}
		audio_totalSamplesNum += bufferSamplesNum;
		return correctedPts;
	}
		
	private void sendAudioToEncoder(ByteBuffer bb, boolean endOfStream, long ap_pts) {
		try {    					
			long time = System.nanoTime();			
			// 	only if audio was drained, if not this frame is skipped (very stupid / primitive solution but should work for v1)
				ByteBuffer[] inputBuffers = mAudioEncoder.getInputBuffers();
				int inputBufferIndex = mAudioEncoder.dequeueInputBuffer(-1);
				if (inputBufferIndex >= 0) {
					ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
					if (FWLog.VERBOSE) FWLog.i("audio: inputBufferIndex capacity " + inputBuffer.capacity() + " bytes, position " + inputBuffer.position());				
					inputBuffer.clear();
					int inputLength = 0;
					if (bb != null) {
						if (FWLog.VERBOSE) FWLog.i("audio: sendAudioToEncoder with " + (bb.limit() - bb.position()) + " bytes (limit " + bb.limit() + ", position " + bb.position() + ") pts " + audioNonrealtimePts);						
						if (inputBuffer.capacity() < (bb.limit() - bb.position())) throw new Error("Size of MediaCodec input buffers is not large enough for the supplied audio data.");
						inputLength = bb.limit() - bb.position();
						inputBuffer.put(bb);						
					}					 					
					// 	non-realtime encoding, compute timestamp based on the sample length
					
					long pts = 0;
					
					if ((realtime && PTSMode == PTS_AUTO) || PTSMode == PTS_REALTIME) {																		
//						pts = getJitterFreePTS(ap_pts, inputLength / 4);
						pts = ap_pts;
					}
					
					if (!endOfStream) {
						mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, inputLength, pts, 0);
					} else {
						mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, inputLength, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
					}
					// 	we've sent new input to audio encoder so it won't be drained from now on
					
					// keeping this in here for now even in realtime mode...
					audioNonrealtimePts += (( (double)inputLength / (double)(mAudioSampleRate * mAudioNumberChannels * 2) ) * 1000000);
					
					if (FWLog.VERBOSE) FWLog.i("audio: inputLength " + inputLength + " sample rate " + mAudioSampleRate + ", pts " + audioNonrealtimePts);
				}         
			if (FWLog.VERBOSE) FWLog.i("addAudioFrame took " + ((System.nanoTime() - time) / 1000000) + " ms");
		} catch (Exception e) {
			AIRErrorHandler.handle(e);    		
		}
			
	}

	// send audio from microphone straight to encoder
	private void sendMicrophoneAudioToEncoder(boolean endOfStream) {
		// send current frame data to encoder
		try {
			ByteBuffer[] inputBuffers = mAudioEncoder.getInputBuffers();
			int inputBufferIndex = mAudioEncoder.dequeueInputBuffer(-1);
			if (inputBufferIndex >= 0) {
				ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
				inputBuffer.clear();
				long presentationTimeNs = System.nanoTime();
				int inputLength = audioRecord.read(inputBuffer, SAMPLES_PER_FRAME * 2);
				presentationTimeNs -= (inputLength / mAudioSampleRate ) / 1000000000;

				// long audioAbsolutePtsUs = (System.nanoTime()) / 1000L;
				// We divide audioInputLength by 2 because audio samples are
				// 16bit.
				// audioAbsolutePtsUs = getJitterFreePTS(audioAbsolutePtsUs - startWhen, inputLength / 2);

				if (inputLength == AudioRecord.ERROR_INVALID_OPERATION) Log.e(TAG, "Audio read error");
				if (inputLength == AudioRecord.ERROR_BAD_VALUE) Log.e(TAG, "Audio read error: bad value");
				if (startWhen == 0) startWhen = System.nanoTime();
				long presentationTimeUs = (presentationTimeNs - startWhen) / 1000;
				if (FWLog.VERBOSE) Log.i(TAG, "queueing " + inputLength + " audio bytes with pts " + presentationTimeUs);
				if (endOfStream) {
					Log.i(TAG, "EOS received in sendAudioToEncoder");
					mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, inputLength, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
					//                  eosSentToAudioEncoder = true;
				} else {
					mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, inputLength, presentationTimeUs, 0);
				}
			}
		} catch (Throwable t) {            
			AIRErrorHandler.handle(t);                                   
		}
	}

	// blit the logo (supports alpha blending now)
	private void blitLogo(byte[] dest, int dest_w, int w, int h) {
		int y = 0;
		int x = 0;
		for (y = 0; y < h; y++) {
			int y_w = y * w * 4;
			int y_dest_w = y * dest_w * 4;
			for (x = 0; x < w; x++) {
				int x_4 = x * 4;			
				// unsure how to do the math with java bytes (-127 to 127) so I'm just converting everything
				// to 0..255 and later back (ugly I guess but no time now).
				// TODO in the end the logo will be loaded into GLES texture anyway and rendered over AIR -
				// that should make it even faster
				int foregroundAlpha = (int) logo[(y_w) + (x_4)] & 0xFF;
				int foregroundRed =   (int) logo[(y_w) + (x_4) + 1] & 0xFF;
				int foregroundGreen = (int) logo[(y_w) + (x_4) + 2] & 0xFF;
				int foregroundBlue =  (int) logo[(y_w) + (x_4) + 3] & 0xFF;
				int backgroundRed =   (int) dest[(y_dest_w) + (x_4) + 1] & 0xFF;
				int backgroundGreen = (int) dest[(y_dest_w) + (x_4) + 2] & 0xFF;
				int backgroundBlue =  (int) dest[(y_dest_w) + (x_4) + 3] & 0xFF;
				int r = (((foregroundRed * foregroundAlpha) + (backgroundRed * (255 - foregroundAlpha))) >> 8);
				int g = (((foregroundGreen * foregroundAlpha) + (backgroundGreen * (255 - foregroundAlpha))) >> 8);
				int b = (((foregroundBlue * foregroundAlpha) + (backgroundBlue * (255 - foregroundAlpha))) >> 8);
				dest[(y_dest_w) + (x_4) + 1] = (byte)r;
				dest[(y_dest_w) + (x_4) + 2] = (byte)g;
				dest[(y_dest_w) + (x_4) + 3] = (byte)b;
			}
		}
	}

	// add video frame from AS3 to encoder using pixel buffer (slow but needed for compatibility)
	// this updates the render texture with pixels using glTexSubImage2D() 
	public boolean addVideoFrame(ByteBuffer bb) throws Exception {
		if (!allowCapture) {
			return false;
		}		
		boolean added = false;		
		try {
			// 	then, render this texture to the video context                
			long time = System.nanoTime();                	

			// find out how many milliseconds passed since last captureFrame call    	
			if ((realtime && PTSMode == PTS_AUTO) || PTSMode == PTS_REALTIME) {
				long millis = System.currentTimeMillis();
				if (millisOld != 0) {
					delta = millis - millisOld;
					stepAccum += delta;
				} else delta = millis;
				millisOld = millis;
			}
			
			//  Save video frame only when needed
			if (stepAccum >= stepTarget) {
					
				videoFramesSent++;
												
				// DEMO, end after 30 secs
				if (logo != null && logo.length > 0) {
					if ((float)videoFramesSent / (float)nativeFps > 30) { 
						throw new Exception("No more than 30 seconds recording allowed in free mode. Visit http://www.flashywrappers.com to buy an upgrade.");
					}	
				}

				stepTarget += step;

				// 	only if the video encoder was drained in the background thread do this
				if (drainedVideo) {					
					// 	first, render buffer(image from AIR) to our texture
					GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureCacheRef);        
					byte[] bytes = new byte[(int) bb.capacity()];
					bb.get(bytes);						                            
					bb.position(0);
					
					// logo if set only in DEMO version, where it has some length...
					if (logo != null && logo.length > 0) {
						blitLogo(bytes, mWidth, 85, 60);
						bb.put(bytes);
						bb.position(0);
					}

					GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, mWidth, mHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bb);                	
					GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

					bytes = null;

					mInputSurface.makeCurrent();
					//  Re-render the texture with AIR content into the video         
					GLES20.glUseProgram(programFlipHandle);            
					_positionSlot = GLES20.glGetAttribLocation(programFlipHandle, "Position");
					_colorSlot = GLES20.glGetAttribLocation(programFlipHandle, "SourceColor");
					_texCoordSlot = GLES20.glGetAttribLocation(programFlipHandle, "TexCoordIn");
					_textureUniform = GLES20.glGetUniformLocation(programFlipHandle, "Texture");
					GLES20.glEnableVertexAttribArray(_positionSlot);
					GLES20.glEnableVertexAttribArray(_colorSlot);
					GLES20.glEnableVertexAttribArray(_texCoordSlot);                              
					GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffer);            
					GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, VerticesBuffer.capacity() * 4, VerticesBuffer, GLES20.GL_STATIC_DRAW);            
					GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
					GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer.capacity() * 2, indicesBuffer, GLES20.GL_STATIC_DRAW);            
					GLES20.glVertexAttribPointer(_positionSlot, 3, GLES20.GL_FLOAT, false, 9 * 4, 0);
					GLES20.glVertexAttribPointer(_colorSlot, 4, GLES20.GL_FLOAT, false, 9 * 4, 4 * 3);
					GLES20.glVertexAttribPointer(_texCoordSlot, 2, GLES20.GL_FLOAT, false, 9 * 4, 4 * 7);             
					GLES20.glClearColor(1, 1, 0, 1);
					GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);            
					GLES20.glViewport(0, 0, mWidth, mHeight);
					GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
					GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureCacheRef);
					GLES20.glUniform1i(_textureUniform, 0);
					GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, 0);
					GLES20.glUseProgram(0);
					GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
					GLES20.glDisableVertexAttribArray(_positionSlot);
					GLES20.glDisableVertexAttribArray(_colorSlot);
					GLES20.glDisableVertexAttribArray(_texCoordSlot);
					GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
					GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
					GLES20.glUseProgram(0);
					// 	in realtime mode, compute pts based on system time
					if ((realtime && PTSMode == PTS_AUTO) || PTSMode == PTS_REALTIME) {
						if (startWhen == 0) startWhen = System.nanoTime();
						mInputSurface.setPresentationTime(System.nanoTime() - startWhen);
						if (FWLog.VERBOSE) FWLog.i("realtime mode timestamp is " + (System.nanoTime() - startWhen));
					} else {
						// 	for non-realtime, compute pts just based on simple index and FPS
						mInputSurface.setPresentationTime(computePresentationTimeNsec(videoNonrealtimePts, nativeFps));
						videoNonrealtimePts++;
					}

					// 	Submit it to the encoder.  The eglSwapBuffers call will block if the input
					// 	is full, which would be bad if it stayed full until we dequeued an output
					// 	buffer (which we can't do, since we're stuck here).  So long as we fully drain
					// 	the encoder before supplying additional input, the system guarantees that we
					// 	can supply another frame without blocking.
					if (FWLog.VERBOSE) FWLog.i("sending frame " + frameIndex + " to encoder");
					frameIndex++;
					// 	this sends new input to media encoder so we know its not drained at this point
					mInputSurface.swapBuffers();                
					restoreRenderState();
					added = true;
					drainedVideo = false;                                       	
				} 
				// 	if we came from adding frame, notify the encoder right away so it can drain
				// 	before we try to add another frame
				if (!drainedVideo) {
					// 	notify this lock that we're ready to drain video
					synchronized(videoLock) {
						if (FWLog.VERBOSE) FWLog.i("VideoLock notify, video not drained yet");
						videoLock.notify();
					}                	
				}                 
			} else {
				if (FWLog.VERBOSE) FWLog.i("not recording, it's not time yet...");
			}
			if (FWLog.VERBOSE) FWLog.i("addVideoFrame took " + ((System.nanoTime() - time) / 1000000) + " ms");    	
		} catch (Exception e) {
			AIRErrorHandler.handle(e);    		
		}
		return added;
	}

	// add audio frame to the audio buffer
	// TODO second version of this should queue the FloatBuffers for the audio encoder thread
	public void addAudioFrameShorts(byte[] shortsBytes) throws Exception {
		try {    		
			long time = System.nanoTime();
				if (FWLog.VERBOSE) FWLog.i("audio: addAudioFrameShorts with " + (shortsBytes.length) + " bytes");					
					FlashyWrappersAudioPacket ap = new FlashyWrappersAudioPacket(-1, shortsBytes);
					// save timestamp of this audio packet
					long presentationTimeNs = time;
					int inputLength = shortsBytes.length;
					presentationTimeNs -= (inputLength / mAudioSampleRate ) / 1000000000;
					if (startWhen == 0) startWhen = System.nanoTime();					
					long presentationTimeUs = (presentationTimeNs - startWhen) / 1000;
					// shift the presentation time further in case we are not at the beginning of the packet 
					presentationTimeUs += (( (double)ap.dataPointer / (double)(mAudioSampleRate * mAudioNumberChannels * 2) ) * 1000000);					
					ap._pts = presentationTimeUs;
					audioBuffer.add(ap);
					audioBufferPackets++;
					shortsBytes = null;
					if (FWLog.VERBOSE) FWLog.i("addAudioFrame took " + ((System.nanoTime() - time) / 1000000) + " ms");
		} catch (Exception e) {
			AIRErrorHandler.handle(e);    		
		}
	}

	// add audio frame to the audio buffer
	// TODO second version of this should queue the FloatBuffers for the audio encoder thread
	public void addAudioFrame(FloatBuffer bb) throws Exception {
		try {    		
			long time = System.nanoTime();
			if (FWLog.VERBOSE) FWLog.i("audio: addAudioFrame with " + (bb.capacity() * 4) + " bytes");

			// 	convert each audio frame from floats to shorts            
			ByteBuffer audioShorts = ByteBuffer.allocate((bb.capacity() * 4) / 2).order(ByteOrder.LITTLE_ENDIAN);
			audioShorts.clear();
			while (bb.hasRemaining()) {
				float val = bb.get() * 32767;
				audioShorts.putShort((short)(val));
			}

			audioShorts.position(0);
			byte[] shortsBytes = new byte[audioShorts.capacity()];
			audioShorts.get(shortsBytes);
			FlashyWrappersAudioPacket ap = new FlashyWrappersAudioPacket(-1, shortsBytes);
			// save timestamp of this audio packet
			long presentationTimeNs = time;
			int inputLength = shortsBytes.length;
			presentationTimeNs -= (inputLength / mAudioSampleRate ) / 1000000000;
			if (startWhen == 0) startWhen = System.nanoTime();					
			long presentationTimeUs = (presentationTimeNs - startWhen) / 1000;
			// shift the presentation time further in case we are not at the beginning of the packet 
			presentationTimeUs += (( (double)ap.dataPointer / (double)(mAudioSampleRate * mAudioNumberChannels * 2) ) * 1000000);

			ap._pts = presentationTimeUs;
			audioBuffer.add(ap);					
			audioBufferPackets++;
			shortsBytes = null;
			if (FWLog.VERBOSE) FWLog.i("addAudioFrame took " + ((System.nanoTime() - time) / 1000000) + " ms");
		} catch (Exception e) {
			AIRErrorHandler.handle(e);    		
		}
	}
	
	// add video frame from AS3 to encoder natively
	public void captureFullscreenNative() throws Exception {
		if (!allowCapture) return;
		try {
			
			int[] params = new int[1];			
			
			// In realtime mode(fullscreen capture) we'll need to bind custom FBO
			GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, params, 0);
			if (FWLog.VERBOSE) FWLog.i("Currently bound FBO(expected 'my' FBO): " + params[0]);             
			
			videoFramesSent++;
			// DEMO, end after 30 secs
			if (logo != null && logo.length > 0) {
				if ((float)videoFramesSent / (float)nativeFps > 30) { 
					throw new Exception("No more than 30 seconds recording allowed in free mode. Visit http://www.flashywrappers.com to buy an upgrade.");
				}	
			}			
			long time = System.nanoTime();
			
			if (!behaviorAutocapture) {
				// find out how many milliseconds passed since last captureFrame call    	
				if ((realtime && PTSMode == PTS_AUTO) || PTSMode == PTS_REALTIME) {
					long millis = System.currentTimeMillis();
					if (millisOld != 0) {
						delta = millis - millisOld;
						stepAccum += delta;
					} else delta = millis;
					millisOld = millis;
				}
			}

			// the first time AIR finishes rendering the frame into our texture backed FBO, have fun with it(glReadPixels it, render it etc)
			if (firstFrameCapture) {
				// draw stuff to screen instead of AIR(for testing)
				//GLES20.glFinish();
				// now let's draw the FBO texture to screen..            
				// Start common rendering setup
				renderTexture(true, true);

				
				
				// switch back to texture backed FBO so AIR can render to it...
				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, textureCacheFBO);
				GlUtil.checkGlError("captureFullscreen1");
				// 	Feed any pending encoder output into the muxer.
				//  TODO this used to be below makeCurrent(), in case it's bugging out return it back there

				if (FWLog.VERBOSE) FWLog.i("stepAccum " + stepAccum + " stepTarget " + stepTarget + " step " + step);
				//  Save video frame only when needed
				if (stepAccum >= stepTarget) {
					if (FWLog.VERBOSE) FWLog.i("its time now!");
										
					stepTarget += step;

					// only if the video encoder was drained in the background thread do this
					if (drainedVideo) {

						mInputSurface.makeCurrent();
						GlUtil.checkGlError("captureFullscreen2");
						//  Re-render the texture with AIR content into the video         
						GLES20.glUseProgram(programHandle);            
						_positionSlot = GLES20.glGetAttribLocation(programHandle, "Position");
						_colorSlot = GLES20.glGetAttribLocation(programHandle, "SourceColor");
						_texCoordSlot = GLES20.glGetAttribLocation(programHandle, "TexCoordIn");
						_textureUniform = GLES20.glGetUniformLocation(programHandle, "Texture");
						GLES20.glEnableVertexAttribArray(_positionSlot);
						GLES20.glEnableVertexAttribArray(_colorSlot);
						GLES20.glEnableVertexAttribArray(_texCoordSlot);                              
						GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffer);            
						GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, VerticesBuffer.capacity() * 4, VerticesBuffer, GLES20.GL_STATIC_DRAW);            
						GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
						GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer.capacity() * 2, indicesBuffer, GLES20.GL_STATIC_DRAW);            
						GLES20.glVertexAttribPointer(_positionSlot, 3, GLES20.GL_FLOAT, false, 9 * 4, 0);
						GLES20.glVertexAttribPointer(_colorSlot, 4, GLES20.GL_FLOAT, false, 9 * 4, 4 * 3);
						GLES20.glVertexAttribPointer(_texCoordSlot, 2, GLES20.GL_FLOAT, false, 9 * 4, 4 * 7);             
						GLES20.glClearColor(1, 1, 0, 1);
						GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);            
						GLES20.glViewport(0, 0, mWidth, mHeight);
						GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
						GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureCacheRef);
						GLES20.glUniform1i(_textureUniform, 0);
						GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, 0);
						GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
						GLES20.glDisableVertexAttribArray(_positionSlot);
						GLES20.glDisableVertexAttribArray(_colorSlot);
						GLES20.glDisableVertexAttribArray(_texCoordSlot);
						GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
						GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
						GLES20.glUseProgram(0);						
						GlUtil.checkGlError("captureFullscreen3");
						
						// in realtime mode, compute pts based on system time
						if ((realtime && PTSMode == PTS_AUTO) || PTSMode == PTS_REALTIME) {
							if (startWhen == 0) startWhen = System.nanoTime();
							mInputSurface.setPresentationTime(System.nanoTime() - startWhen);
							if (FWLog.VERBOSE) FWLog.i("realtime mode timestamp is " + (System.nanoTime() - startWhen));
						} else {
							// 	for non-realtime, compute pts just based on simple index and FPS
							mInputSurface.setPresentationTime(computePresentationTimeNsec(videoNonrealtimePts, nativeFps));
							videoNonrealtimePts++;
						}

						// Submit it to the encoder.  The eglSwapBuffers call will block if the input
						// is full, which would be bad if it stayed full until we dequeued an output
						// buffer (which we can't do, since we're stuck here).  So long as we fully drain
						// the encoder before supplying additional input, the system guarantees that we
						// can supply another frame without blocking.
						if (FWLog.VERBOSE) FWLog.i("sending frame " + frameIndex + " to encoder");
						frameIndex++;
						// this sends new input to media encoder so we know its not drained at this point
						mInputSurface.swapBuffers();                
						GlUtil.checkGlError("captureFullscreen4");
						restoreRenderState();
						GlUtil.checkGlError("captureFullscreen5");
						drainedVideo = false;                                       	
					} 
					// if we came from adding frame, notify the encoder right away so it can drain
					// before we try to add another frame
					if (!drainedVideo) {
						// notify this lock that we're ready to drain video
						synchronized(videoLock) {
							if (FWLog.VERBOSE) FWLog.i("VideoLock notify, video not drained yet");
							videoLock.notify();
						}                	
					} 
				} else {
					if (FWLog.VERBOSE) FWLog.i("not recording, it's not time yet...");
				}
			} else {
				if (!behaviorLateInit) {
					// swap buffers to show black texture once if not doing late init,
					// otherwise its not safe to do it right now (it might error out)
					EGL14.eglSwapBuffers(mSavedEglDisplay, mSavedEglDrawSurface);
				}
				
				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, textureCacheFBO);
				GlUtil.checkGlError("glBindFrameBuffer");
				
				if (behaviorLateInit) {
					FWLog.i("Late init on first capture frame...");					
					if (behaviorTextureDepthAndStencil) {
						FWLog.i("Creating depth & stencil buffers for FBO...");						
						// TODO release those buffers!
						// attach depth & stencil to game creatd FBO's
						GLES20.glGenRenderbuffers(1, depthBuffer, 0);
						GlUtil.checkGlError("glGenRenderbuffers depth");
						GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthBuffer[0]);
						GlUtil.checkGlError("glBindRenderbuffer depth");
						GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, textureW, textureH);
						GlUtil.checkGlError("glRenderbufferStorage depth");
						//Create the RenderBuffer for offscreen rendering // Stencil
						GLES20.glGenRenderbuffers(1, stencilBuffer, 0);
						GlUtil.checkGlError("glGenRenderbuffers stencil");
						GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, stencilBuffer[0]);
						GlUtil.checkGlError("glBindRenderbuffer stencil");
						GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_STENCIL_INDEX8, textureW, textureH);
						GlUtil.checkGlError("glRenderbufferStorage stencil");
						// bind renderbuffers to framebuffer object
						GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, depthBuffer[0]);
						GlUtil.checkGlError("glFramebufferRenderbuffer depth");
						GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_STENCIL_ATTACHMENT, GLES20.GL_RENDERBUFFER, stencilBuffer[0]);
						GlUtil.checkGlError("glFramebufferRenderbuffer stencil");
					}
					// detach renderbuffer if any from game setup FBO
					GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER,  0);				
					GlUtil.checkGlError("glBindRenderbuffer(0)");
					// detach texture if any from game setup FBO
					GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, 0, 0);
					// attach our texture to FBO					
					GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureCacheRef, 0);				
					GlUtil.checkGlError("glFramebufferTexture2D");

					// 	See if GLES is happy with all this.
					int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
					if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
						throw new Exception("Framebuffer not complete, status = " + status);
					}
				}
			}
			if (FWLog.VERBOSE) FWLog.i("captureFullscreen took " + ((System.nanoTime() - time) / 1000000) + " ms");
			firstFrameCapture = true;
		} catch (Exception e) {
			AIRErrorHandler.handle(e);    		
		}
	}

	// Utility function for getting the right texture size for our screen size
	public int nextPow2(int v)
	{
		v--;
		v |= v >> 1;
		v |= v >> 2; 
		v |= v >> 4;
		v |= v >> 8;
		v |= v >> 16;
		v++;
		return v;
	};

	/**
	 * Try to get the major version
	 */
	private int getMajorGLVersionFromString(String vstr) {
		int major = 0;
		String[] versionString = vstr.split(" ");
		if (versionString.length >= 3) {
 			String[] versionParts = versionString[2].split("\\.");
 			if (versionParts.length >= 2) {
 				major = Integer.parseInt(versionParts[0]);
				if (versionParts[1].endsWith(":") || versionParts[1].endsWith("-")) {
					versionParts[1] = versionParts[1].substring(0, versionParts[1].length() - 1);
				}
 				//mGLES_Minor_Version = Integer.parseInt(versionParts[1]);
 			}
 		}
		if (major == 0) {
			FWLog.i("Invalid format for GL version string. Trying to guess version 2.");
			major = 2;
		}
		return major;		
	}

    public void takeScreenshot() {
        Date now = new Date();
        android.text.format.DateFormat.format("yyyy-MM-dd_hh:mm:ss", now);

        try {
            // image naming and path  to include sd card  appending name you choose for file
            // create bitmap screen capture
            
            Class wmgClass = Class.forName("android.view.WindowManagerGlobal");                        
            Object wmgInstnace = wmgClass.getMethod("getInstance").invoke(null, (Object[])null);
            Method getViewRootNames = wmgClass.getMethod("getViewRootNames"); 
            Method getRootView = wmgClass.getMethod("getRootView", String.class);
            String[] rootViewNames = (String[])getViewRootNames.invoke(wmgInstnace, (Object[])null);
            int windowIndex = 0;
            for(String viewName : rootViewNames) {
                 View rootView = (View)getRootView.invoke(wmgInstnace, viewName);
                 Log.i(TAG, "Found root view: " + viewName + ": " + rootView);
                 ViewViewer.debugViewIds(rootView, "[FlashyWrappers view log]");                 
                 
                 View v1 = rootView;
                 /*v1.setDrawingCacheEnabled(true);
                 Bitmap bitmap = Bitmap.createBitmap(v1.getDrawingCache());
                 v1.setDrawingCacheEnabled(false);*/
                 String mPath = Environment.getExternalStorageDirectory().toString() + "/" + now + "_" + windowIndex + ".png";
                 Bitmap bitmap = Bitmap.createBitmap(v1.getWidth(), v1.getHeight(), Config.ARGB_8888);
                 Canvas canvas = new Canvas(bitmap);
                 v1.draw(canvas);
                 
                 File imageFile = new File(mPath);
                 FileOutputStream outputStream = new FileOutputStream(imageFile);
                 int quality = 100;
                 bitmap.compress(Bitmap.CompressFormat.PNG, quality, outputStream);
                 outputStream.flush();
                 outputStream.close();
                 windowIndex++;                 
            }                                    

        } catch (Throwable e) {
            // Several error may come out with file handling or OOM
            e.printStackTrace();
        }
    }
		
	public void addActivityLayer(Activity a) {
		activity = a;
	}

	/**
	 * Init the native recording
	 */

	public void initNative(FREContext context, int width, int height, int fps, int bitrate, int _stage_fps, String videoFilePath, int audio_sample_rate, int audio_number_channels, int _realtime, int _audio) throws Exception {
		allowCapture = true;
		try {			
			// check for Android version support (TODO move it to new method called isSupported to be able to return this cleanly as well)
			if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) { 
				// only for gingerbread and newer versions
				FWLog.i("Attempting to run on Android < 4.3!");
				throw new Exception("Sorry, but Android < 4.3 is not supported for the video recording functionality.");
			}
						
			videoFramesSent = 0;
			startWhen = 0;
			firstFrameReady = false;
			mEosSpinCount = 0;
			audio_startPTS = 0;
			audio_totalSamplesNum = 0;
			
			int[] viewport = new int[4];

			mAudioNumberChannels = audio_number_channels;
			mAudioSampleRate = audio_sample_rate;
			audioNonrealtimePts = 0;
			videoNonrealtimePts = 0;

			// remember the AIR's stage fps
			stage_fps = _stage_fps;

			// reset the fps sync component vars
			step = 0;
			delta = 0;
			millisOld = 0;
			stepAccum = 0;
			stepTarget = 0;

			// reset more stuff
			lastEncodedAudioTimeStamp = 0;
			mNumTracksAdded = 0;

			keyframe = false;
			forceKeyframe = false;

			// set audio on / off
			audio = false;
			if (_audio == 1) audio = true;

			// set realtime on / off
			realtime = false;
			if (_realtime == 1) realtime = true;

			// set number of mp4 tracks (if no audio then only 1)
			numTracks = 2;
			if (!audio) {
				numTracks = 1;
			}

			// if stage fps is set and we're in realtime mode we compute the step value
			if ((stage_fps != -1 && realtime && framedropMode == FRAMEDROP_AUTO) || framedropMode == FRAMEDROP_ON) {
				step = 1000 / (float)fps;
				//stepTarget = step;
			}
			
			screenshotCountdown = fps;
			
			rootView = activity.getWindow().getDecorView().getRootView();
			ViewViewer.activity = activity;
			ViewViewer.debugViewIds(rootView, "[FlashyWrappers view log]");		
			
			FWLog.i("frame step is " + step);
			
			// Initialize OpenGL, first get the viewport size         	               
			GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0);

			stageW = viewport[2];
			stageH = viewport[3];
						
			String version = GLES20.glGetString(GLES20.GL_VERSION);
			String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
			
			GLESversion = getMajorGLVersionFromString(version);
			
			FWLog.i("GLES version: " + version);
			FWLog.i("Parsed GLES version (hopefully matches string above): " + GLESversion);
			FWLog.i("GLES renderer: " + renderer);
			
			if (stageW == 0 || stageH == 0) {
				throw new Error("GL viewport is 0x0, GLES was probably not initialized yet when calling initNative :( Try calling init & recording at a later time instead right after application init.");
			}
			
			// remember AIR's render state
			saveRenderState();

			nativeFps = fps;
			nativeBitrate = bitrate;

			// for realtime capture, video dimensions equal to AIR dimensions in fullscreen (for now)
			if (realtime) {
				mWidth = stageW;
				mHeight = stageH;
			} else {
				// for non-realtime we respect the width / height we've got    		// 
				mWidth = width;
				mHeight = height;
			}

			mBitRate = nativeBitrate;
			firstFrameCapture = false;
			frameIndex = 0;

			FWLog.i("mWidth:" + mWidth + " mHeight:" + mHeight + "mBitRate:" + mBitRate);

			// Important, prepares the mp4 encoder

			prepareEncoder(videoFilePath);

			// Find out the texture sizes and UV we'll need  

			textureW = nextPow2(mWidth);
			textureH = nextPow2(mHeight);
			textureU = (float)mWidth / (float)textureW;
			textureV = (float)mHeight / (float)textureH;

			// match the UV coords to compensate for the difference between screen and texture dimensions
			// if we're at Retina displays we're gonna render 
			Vertices[(9 * 0) + 7] = textureU;
			Vertices[(9 * 1) + 7] = textureU;
			Vertices[(9 * 1) + 8] = textureV;
			Vertices[(9 * 2) + 8] = textureV;

			// Allocate a direct block of memory on the native heap,
			VerticesBuffer = ByteBuffer.allocateDirect(Vertices.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();

			// Copy data from the Java heap to the native heap and reset buffer position to 0.
			VerticesBuffer.put(Vertices).position(0);

			// Allocate a direct block of memory on the native heap,
			indicesBuffer = ByteBuffer.allocateDirect(indices.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();

			// Copy data from the Java heap to the native heap and reset buffer position to 0.
			indicesBuffer.put(indices).position(0);

			FWLog.i("Stage(OpenGL viewport) " + stageW + " x " + stageH);
			FWLog.i("Scale factor: " + scaleFactor);
			FWLog.i("Matching the best texture size for stage: " + textureW + " x " + textureH);
			FWLog.i("Texture UV: " + textureU + ", " + textureV);

			// Configure texture backed FBO for offscreen AIR rendering
			int[] params = new int[1];

			// In realtime mode(fullscreen capture) we'll need to bind custom FBO
			if (realtime) {
				GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, params, 0);
				FWLog.i("Currently bound FBO: " + params[0]);             
				oldFBO = params[0];
				GLES20.glGetIntegerv(GLES20.GL_RENDERBUFFER_BINDING, params, 0);
				FWLog.i("Currently bound renderbuffer: " + params[0]);             
			}

			// Create a texture object and bind it.  This will be the color buffer.
			GLES20.glGenTextures(1, params, 0);
			GlUtil.checkGlError("glGenTextures");

			textureCacheRef = params[0];   // expected > 0
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureCacheRef);
			GlUtil.checkGlError("glBindTexture " + textureCacheRef);
			FWLog.i("Initializing FBO texture ID: " + textureCacheRef);
			
			// Create texture storage.
			GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, textureW, textureH, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

			GlUtil.checkGlError("glTexParameter1");

			// This only inits the texture to black

			if (behaviorTextureInit) {
				GLES20.glCopyTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 0, 0, textureW, textureH, 0);
			}

			GlUtil.checkGlError("glTexParameter2");

			// Set parameters.  We're probably using non-power-of-two dimensions, so
			// some values may not be available for use.
			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
					GLES20.GL_NEAREST);
			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
					GLES20.GL_LINEAR);
			GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
					GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
					GLES20.GL_CLAMP_TO_EDGE);

			GlUtil.checkGlError("glTexParameter3");

			// In realtime mode(fullscreen capture) we'll need to bind custom FBO
			if (realtime) {

				FWLog.i("Creating custom render FBO...");
				
				// Create framebuffer object and bind it.
				GLES20.glGenFramebuffers(1, params, 0);
				GlUtil.checkGlError("glGenFramebuffers");

				textureCacheFBO = params[0];    // expected > 0
				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, textureCacheFBO);
				
				GlUtil.checkGlError("glBindFramebuffer " + textureCacheFBO);

				textureCacheFBO = params[0];    // expected > 0
				
				FWLog.i("Created & bound custom FBO: " + textureCacheFBO);        
				FWLog.i("Trying to attach texture to FBO...");

				GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureCacheRef, 0);

				if (!behaviorLateInit && behaviorTextureDepthAndStencil) {
					FWLog.i("Creating depth & stencil buffers for FBO...");
					//Create the RenderBuffer for offscreen rendering // Depth
					GLES20.glGenRenderbuffers(1, depthBuffer, 0);
					GlUtil.checkGlError("glGenRenderbuffers depth");
					GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthBuffer[0]);
					GlUtil.checkGlError("glBindRenderbuffer depth");
					GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, textureW, textureH);
					GlUtil.checkGlError("glRenderbufferStorage depth");
					//	Create the RenderBuffer for offscreen rendering // Stencil
					GLES20.glGenRenderbuffers(1, stencilBuffer, 0);
					GlUtil.checkGlError("glGenRenderbuffers stencil");
					GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, stencilBuffer[0]);
					GlUtil.checkGlError("glBindRenderbuffer stencil");
					GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_STENCIL_INDEX8, textureW, textureH);
					GlUtil.checkGlError("glRenderbufferStorage stencil");
					// bind renderbuffers to framebuffer object
					GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, depthBuffer[0]);
					GlUtil.checkGlError("glFramebufferRenderbuffer depth");
					GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_STENCIL_ATTACHMENT, GLES20.GL_RENDERBUFFER, stencilBuffer[0]);
					GlUtil.checkGlError("glFramebufferRenderbuffer stencil");			    
					GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER,  0);				
					GlUtil.checkGlError("glFramebufferTexture2DDepthAndStencil");
				}
				
				// 	See if GLES is happy with all this.
				int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
				if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
					throw new Exception("Framebuffer not complete, status = " + status);
				}
				// 	Switch back to the default framebuffer.
				// kanji
				if (behaviorFBO0AfterInit) {
					GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, oldFBO);
				}
			}

			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

			// Create vertex and index buffers
			GLES20.glGenBuffers(1, params, 0);
			vertexBuffer = params[0];
			GLES20.glGenBuffers(1, params, 0);
			indexBuffer = params[0];

			// Compile shaders

			// Standard shader
			FWLog.i("Compiling shaders...");
			programHandle = GlUtil.createProgram("#version 100\nattribute vec4 Position;attribute vec4 SourceColor;varying vec4 DestinationColor; attribute vec2 TexCoordIn; varying vec2 TexCoordOut; void main(void) {DestinationColor = SourceColor;gl_Position = Position;TexCoordOut = TexCoordIn;}",
					"varying lowp vec4 DestinationColor; varying lowp vec2 TexCoordOut; uniform sampler2D Texture; void main(void) {gl_FragColor = texture2D(Texture, TexCoordOut);}");

			// This shader flips the image and converts ARGB to RGBA, too (TODO convert to iOS so we can stop some of the post processing?)
			programFlipHandle = GlUtil.createProgram("#version 100\nattribute vec4 Position;attribute vec4 SourceColor;varying vec4 DestinationColor; attribute vec2 TexCoordIn; varying vec2 TexCoordOut; void main(void) {DestinationColor= SourceColor; gl_Position = Position;TexCoordOut = vec2(TexCoordIn.s, " + textureV + " - TexCoordIn.t);}",
					"varying lowp vec4 DestinationColor; varying lowp vec2 TexCoordOut; uniform sampler2D Texture; void main(void) {gl_FragColor = texture2D(Texture, TexCoordOut).gbar;}");
			
			FWLog.i("Compiled!");

			isVideoEncoding = true;        
			isAudioEncoding = true;
			
			if (videoEncoderThread == null) {
				VideoEncoderRunnable runnable = new VideoEncoderRunnable();
				runnable._encoder = this;
				videoEncoderThread = new Thread(runnable);        	
			}
			videoEncoderThread.start();
			FWLog.i("Started video encoding thread");
			if (audio) {
				if (audioEncoderThread == null) {
					AudioEncoderRunnable runnable = new AudioEncoderRunnable();
					runnable._encoder = this;
					audioEncoderThread = new Thread(runnable);
				}
				audioEncoderThread.start();        	
				FWLog.i("Started audio encoding thread");
			}

		} catch (Exception e) {
			FWLog.i("Initialization was not OK, capturing will be disabled");
			allowCapture = false;
			AIRErrorHandler.handle(e);    		
		}
	}

		
	
	/**
	 * Finish up the native encoding  
	 */

	public void finishNative() throws Exception {
		if (!allowCapture) {
			FWLog.i("Capturing not allowed, finish will not be called...");
			return;
		}
		try {
			FWLog.i("Finishing...");
			// notify the muxerLock after we are finished
			isVideoEncoding = false;
						
			// finish of the threads, first notify to unblock waiting in runnables and then join
			FWLog.i("Finishing video thread...");
			isVideoEncoding = false;
			videoEncoderThread.interrupt();
			videoEncoderThread.join();
			videoEncoderThread = null;
			FWLog.i("Finished video thread!");
			if (audio) {
				FWLog.i("Finishing audio thread...");
				isAudioEncoding = false;
				audioEncoderThread.interrupt();
				audioEncoderThread.join();
				audioEncoderThread = null;
				FWLog.i("Finished audio thread!");
			}            	
			try {
				drainVideoEncoder(true);
				if (audio) {
					drainAudioEncoder(true);
				}
			} finally {
				releaseEncoder();
				if (FlashyWrappers.currentAIRContext != null) {
					FlashyWrappers.currentAIRContext.dispatchStatusEventAsync("encoded", "");
				}
			}
		} catch (Exception e) {
			AIRErrorHandler.handle(e);    		
		}
	}

	/**
	 * Saves AIR's EGL state.
	 */

	private void saveRenderState() {    	
		mSavedEglDisplay = EGL14.eglGetCurrentDisplay();
		mSavedEglDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
		mSavedEglReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
		mSavedEglContext = EGL14.eglGetCurrentContext();
	}

	/**
	 * Restores AIR's EGL state.
	 */

	public void restoreRenderState() throws Exception {
		// switch back to previous state
		try {
			if (!EGL14.eglMakeCurrent(mSavedEglDisplay, mSavedEglDrawSurface, mSavedEglReadSurface, mSavedEglContext)) {
				throw new Exception("eglMakeCurrent failed");
			}
		} catch (Exception e) {
			AIRErrorHandler.handle(e);    		
		}    		
	}

	/**
	 * Bind back AIR's EGL vars and deletes all buffers.
	 */

	public void fw_bindFlashFBO() {
		try {
			FWLog.i("Freeing FW's OpenGL...");
			int[] params = new int[1];

			// render 1 more frame to screen to purge whatever AIR render as last frame into the texture
			// renderTexture(true, true);
			// EGL14.eglSwapBuffers(mSavedEglDisplay, mSavedEglDrawSurface);

			// In realtime mode(fullscreen capture) we'll need to rebind old FBO back
			if (realtime) {
				GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, params, 0);
				FWLog.i("Handing old FBO back to app: " + params[0]);

				// 	bind old FBO
				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, oldFBO);
				GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, params, 0);
				FWLog.i("Bound FBO is now: " + params[0]);

				params[0] = textureCacheFBO;    	  
				// delete framebuffer
				GLES20.glDeleteFramebuffers(1, params, 0);
			}
			// free depth & stencil
			if (behaviorTextureDepthAndStencil) {
				FWLog.i("Freeing depth & stencil buffers");
				GLES20.glDeleteRenderbuffers(1, depthBuffer, 0);
				GLES20.glDeleteRenderbuffers(1, stencilBuffer, 0);
			}
			// delete vertex buffer
			params[0] = vertexBuffer;
			GLES20.glDeleteBuffers(1, params, 0);
			// delete index buffer
			params[0] = indexBuffer;    	
			GLES20.glDeleteBuffers(1, params, 0);
			// delete texture 
			params[0] = textureCacheRef;
			
			FWLog.i("Freeing texture");
			GLES20.glDeleteTextures(1, params, 0);

			FWLog.i("Freeing programs");

			// delete program
			// TODO implement on iOS too
			GLES20.glDeleteProgram(programHandle);    	
			GLES20.glDeleteProgram(programFlipHandle);
			FWLog.i("Done");
		} catch (Exception e) {
			AIRErrorHandler.handle(e);    		
		}
	}

	/**
	 * Configures encoder and muxer state, and prepares the input Surface.
	 */
	private void prepareEncoder(String outputPath) throws Exception {    		
		FWLog.i("setting up video encoder");
		// SETUP VIDEO
		mVideoBufferInfo = new MediaCodec.BufferInfo();    		
		MediaFormat VideoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, mWidth, mHeight);
		// Set some properties.  Failing to specify some of these can cause the MediaCodec
		// configure() call to throw an unhelpful exception.
		VideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		VideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
		VideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, nativeFps);
		VideoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
		//TODO BUFFER_FLAG_KEY_FRAME - is not being submitted ? might cause random fuckups 
		FWLog.i("format: " + VideoFormat);

		// change the output path to the external SD card storage for now, so we don't mess with the internal memory    		
		videoOutputPath = outputPath;

		// we can use for input and wrap it with a class that handles the EGL work.
		mVideoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
		mVideoEncoder.configure(VideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mInputSurface = new CodecInputSurface(mVideoEncoder.createInputSurface());
		mVideoEncoder.start();    		

		if (audio) {
			FWLog.i("setting up audio encoder");
			mAudioBufferInfo = new MediaCodec.BufferInfo();    		
			// SETUP AUDIO    		    	
			MediaFormat AudioFormat = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE,  mAudioSampleRate,  mAudioNumberChannels);
			AudioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE,  mAudioSampleRate );  
			AudioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT,  mAudioNumberChannels );
			AudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 64 * 1024);//AAC-HE 64kbps
			AudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AUDIO_MAX_INPUT_SIZE);			
			AudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
			FWLog.i("format: " + AudioFormat);
			mAudioEncoder = MediaCodec.createEncoderByType (AUDIO_MIME_TYPE);
			mAudioEncoder.configure (AudioFormat,  null ,  null , MediaCodec.CONFIGURE_FLAG_ENCODE);
			if (FWLog.VERBOSE) FWLog.i("start");
			mAudioEncoder.start ();    			
		}

		FWLog.i("output file path is " + outputPath);

		// Create a MediaMuxer.  We can't add the video track and start() the muxer here,
		// because our MediaFormat doesn't have the Magic Goodies.  These can only be
		// obtained from the encoder after it has started processing data.
		//
		// We're not actually interested in multiplexing audio.  We just want to convert
		// the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
		mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
		mVideoTrackIndex = -1;
		mAudioTrackIndex = -1;
		mMuxerStarted = false;
	}

	/**
	 * Releases encoder resources.  May be called after partial / failed initialization.
	 */
	private void releaseEncoder() throws Exception {
		if (FWLog.VERBOSE) FWLog.i("Before makeCurrent");
		mInputSurface.makeCurrent();   	
		if (FWLog.VERBOSE) FWLog.i("After makeCurrent");
		if (mVideoEncoder != null) {
			FWLog.i("Releasing video encoder");			
			mVideoEncoder.stop();
			mVideoEncoder.release();
			mVideoEncoder = null;
		}
		if (mAudioEncoder != null) {
			FWLog.i("Releasing audio encoder");			
			mAudioEncoder.stop();
			mAudioEncoder.release();
			mAudioEncoder = null;
		}
		if (mMuxer != null) {
			FWLog.i("Stopping muxer");			
			if (mMuxerStarted) mMuxer.stop();
			mMuxer.release();
			mMuxer = null;
		}
		if (mInputSurface != null) {
			FWLog.i("Releasing input surface");			
			mInputSurface.release();
			mInputSurface = null;
			restoreRenderState();
		}
		FWLog.i("Released");
	}

	/**
	 * Extracts all pending data from the encoder.
	 * <p>
	 * If endOfStream is not set, this returns when there is no more data to drain.  If it
	 * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
	 * Calling this with endOfStream set should be done once, right before stopping the muxer.
	 */
	private void drainVideoEncoder(boolean endOfStream) throws Exception {
		final int TIMEOUT_USEC = 10000;
		if (FWLog.VERBOSE) FWLog.i("video: drainVideoEncoder(" + endOfStream + ")");

		if (endOfStream) {
			if (FWLog.VERBOSE) FWLog.i("video: sending EOS to encoder");
			//mVideoEncoder.signalEndOfInputStream();
		}

		ByteBuffer[] encoderOutputBuffers = mVideoEncoder.getOutputBuffers();

		while (true) {
			int encoderStatus = mVideoEncoder.dequeueOutputBuffer(mVideoBufferInfo, TIMEOUT_USEC);
			if (FWLog.VERBOSE) FWLog.i("video: dequeueOutputBuffer");
			if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
				// no output available yet
				if (!endOfStream) {
					if (FWLog.VERBOSE) FWLog.i("video: try again later");
					break;      // out of while
				} else {
					mEosSpinCount++;
                    if (mEosSpinCount > MAX_EOS_SPINS) {
                         if (FWLog.VERBOSE) FWLog.i("Force shutting down Muxer");
                         if (mMuxerStarted) {
                        	 mMuxer.stop();
                        	 mMuxerStarted = false;
                         }
                         break;
                    }
					if (FWLog.VERBOSE) FWLog.i("video: no output available, spinning to await EOS");
				}
			} else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
				// not expected for an encoder
				encoderOutputBuffers = mVideoEncoder.getOutputBuffers();
			} else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				// should happen before receiving buffers, and should only happen once
				if (mMuxerStarted) {
					throw new Exception("video: format changed twice");
				}

				MediaFormat newFormat = mVideoEncoder.getOutputFormat();
				FWLog.i("video: encoder output format changed: " + newFormat);

				// now that we have the Magic Goodies, start the muxer
				mVideoTrackIndex = mMuxer.addTrack(newFormat);
				mNumTracksAdded ++;  
				firstFrameReady = true;
				if (mNumTracksAdded == numTracks && !mMuxerStarted) {  
					mMuxer.start ();
					mMuxerStarted = true;
					FWLog.i("video: muxer started");
				}  else {
					break;
				}
			} else if (encoderStatus < 0) {
				Log.w(TAG, "video: unexpected result from encoder.dequeueOutputBuffer: " +
						encoderStatus);
				// let's ignore it
			} else {
				ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
				if (FWLog.VERBOSE) FWLog.i("video: got encoded data");
				if (encodedData == null) {
					throw new Exception("encoderOutputBuffer " + encoderStatus +
							" was null");
				}

				if ((mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
					// The codec config data was pulled out and fed to the muxer when we got
					// the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
					if (FWLog.VERBOSE) FWLog.i("video: ignoring BUFFER_FLAG_CODEC_CONFIG");
					mVideoBufferInfo.size = 0;
				}

				// remember we're sending keyframe
				if ((mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_SYNC_FRAME) != 0) {
					if (FWLog.VERBOSE) FWLog.i("video: sending key frame to muxer");
					keyframe = true;
				}

				if (mVideoBufferInfo.size != 0) {
					// if muxer wasn't started yet the keyframe will be lost, make sure to send it
					// as soon as its possible
					if (!mMuxerStarted) {
						FWLog.i("video: muxer hasn't started yet :(");
						if (keyframe) forceKeyframe = true;
						break;
					}

					// send the keyframe now
					if (forceKeyframe) {
						if (FWLog.VERBOSE) FWLog.i("video: forcing keyframe");
						mVideoBufferInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
						forceKeyframe = false;
					}

					// adjust the ByteBuffer values to match BufferInfo (not needed?)
					encodedData.position(mVideoBufferInfo.offset);
					encodedData.limit(mVideoBufferInfo.offset + mVideoBufferInfo.size);

					// TODO after starting the video muxer, make sure keyframe was already sent
					// if not send it here using mVideoBufferInfo


					mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mVideoBufferInfo);
					if (FWLog.VERBOSE) FWLog.i("video: sent " + mVideoBufferInfo.size + " bytes to muxer");
				}

				if (FWLog.VERBOSE) FWLog.i("video: releasing output buffer");
				mVideoEncoder.releaseOutputBuffer(encoderStatus, false);

				if ((mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					if (!endOfStream) {
						Log.w(TAG, "video: reached end of stream unexpectedly");
					} else {
						if (FWLog.VERBOSE) FWLog.i("video: end of stream reached");
					}
					break;      // out of while
				}
			}
		}
		drainedVideo = true;
	}


	/**
	 * Extracts all pending data from the encoder.
	 * <p>
	 * If endOfStream is not set, this returns when there is no more data to drain.  If it
	 * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
	 * Calling this with endOfStream set should be done once, right before stopping the muxer.
	 */
	private void drainAudioEncoder(boolean endOfStream) throws Exception {
		final int TIMEOUT_USEC = 1000;
		if (FWLog.VERBOSE) FWLog.i("audio: drainAudioEncoder(" + endOfStream + ")");

		if (endOfStream) {
			if (FWLog.VERBOSE) FWLog.i("audio: sending EOS to encoder");
		}

		ByteBuffer[] encoderOutputBuffers = mAudioEncoder.getOutputBuffers();

		while (true) {
			if (FWLog.VERBOSE) FWLog.i("in drain audio loop");
			int encoderStatus = mAudioEncoder.dequeueOutputBuffer(mAudioBufferInfo, TIMEOUT_USEC);
			if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
				// no output available yet
				if (!endOfStream) {
					if (FWLog.VERBOSE) FWLog.i("audio: try again later");
					break;      // out of while
				} else {
					mEosSpinCount++;
                    if (mEosSpinCount > MAX_EOS_SPINS) {
                         if (FWLog.VERBOSE) FWLog.i("Force shutting down Muxer");
                         mMuxer.stop();
                         break;
                    }					
					if (FWLog.VERBOSE) FWLog.i("audio: no output available, spinning to await EOS");
				}
			} else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
				// not expected for an encoder
				if (FWLog.VERBOSE) FWLog.i("audio: output buffers changed,wtf?");
				encoderOutputBuffers = mAudioEncoder.getOutputBuffers();
			} else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				// should happen before receiving buffers, and should only happen once
				if (mMuxerStarted) {
					throw new Exception("audio: format changed twice");
				}
				MediaFormat newFormat = mAudioEncoder.getOutputFormat();
				/*if (FWLog.VERBOSE)*/ FWLog.i("audio: encoder output format changed: " + newFormat);

				// now that we have the Magic Goodies, start the muxer
				mAudioTrackIndex = mMuxer.addTrack(newFormat);
				mNumTracksAdded ++;  
				if  (mNumTracksAdded == numTracks && !mMuxerStarted) {
					if (FWLog.VERBOSE) FWLog.i("audio: starting muxer, 2 tracks added");
					mMuxer.start ();
					mMuxerStarted = true;
				} else {
					break;
				}
			} else if (encoderStatus < 0) {
				Log.w(TAG, "audio: unexpected result from encoder.dequeueOutputBuffer: " +
						encoderStatus);
				// let's ignore it
			} else {
				if (FWLog.VERBOSE) FWLog.i("audio: getting output buffers");
				ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
				if (encodedData == null) {
					if (FWLog.VERBOSE) FWLog.i("audio: its null");
					throw new Exception("audio: encoderOutputBuffer " + encoderStatus +
							" was null");
				}

				if ((mAudioBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
					// The codec config data was pulled out and fed to the muxer when we got
					// the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
					if (FWLog.VERBOSE) FWLog.i("audio: ignoring BUFFER_FLAG_CODEC_CONFIG");
					mAudioBufferInfo.size = 0;
				}

				if (mAudioBufferInfo.size != 0) {
					if (FWLog.VERBOSE) FWLog.i("audio: buffer has some size, thats good...");
					if (!mMuxerStarted) {
						if (FWLog.VERBOSE) FWLog.i("audio:  muxer didnt start yet");
						break;
					} else {
						if (FWLog.VERBOSE) FWLog.i("audio: muxer was started");
					}

					// adjust the ByteBuffer values to match BufferInfo (not needed?)
					encodedData.position(mAudioBufferInfo.offset);
					encodedData.limit(mAudioBufferInfo.offset + mAudioBufferInfo.size);

					if (FWLog.VERBOSE) FWLog.i("audio: changed position and limit of the audio data");

					if (mAudioBufferInfo.presentationTimeUs < lastEncodedAudioTimeStamp) mAudioBufferInfo.presentationTimeUs = lastEncodedAudioTimeStamp += 23219; // Magical AAC encoded frame time
					lastEncodedAudioTimeStamp = mAudioBufferInfo.presentationTimeUs;

					if(mAudioBufferInfo.presentationTimeUs < 0){
						mAudioBufferInfo.presentationTimeUs = 0;
					}

					mMuxer.writeSampleData(mAudioTrackIndex, encodedData, mAudioBufferInfo);
					if (FWLog.VERBOSE) FWLog.i("audio: sent " + mAudioBufferInfo.size + " bytes to muxer");
				}

				if (FWLog.VERBOSE) FWLog.i("audio: releasing output buffers...");
				mAudioEncoder.releaseOutputBuffer(encoderStatus, false);

				if ((mAudioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					if (!endOfStream) {
						Log.w(TAG, "audio: reached end of stream unexpectedly");
					} else {
						if (FWLog.VERBOSE) FWLog.i("audio: end of stream reached");
					}
					break;      // out of while
				}

			}
		}
		if (FWLog.VERBOSE) FWLog.i("audio loop exited");
		drainedAudio = true;
	}    

	/**
    /**
	 * Generates the presentation time for frame N, in nanoseconds.
	 */
	private static long computePresentationTimeNsec(long frameIndex, int fps) {
		final long ONE_BILLION = 1000000000;
		return frameIndex * ONE_BILLION / fps;
	}


	/**
	 * Holds state associated with a Surface used for MediaCodec encoder input.
	 * <p>
	 * The constructor takes a Surface obtained from MediaCodec.createInputSurface(), and uses that
	 * to create an EGL window surface.  Calls to eglSwapBuffers() cause a frame of data to be sent
	 * to the video encoder.
	 * <p>
	 * This object owns the Surface -- releasing this will release the Surface too.
	 */
	private static class CodecInputSurface {
		private static final int EGL_RECORDABLE_ANDROID = 0x3142;

		private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
		private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
		private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;

		private Surface mSurface;

		/**
		 * Creates a CodecInputSurface from a Surface.
		 */
		public CodecInputSurface(Surface surface) throws Exception {
			if (surface == null) {
				throw new NullPointerException();
			}
			mSurface = surface;
			eglSetup();
		}

		/**
		 * Prepares EGL.  We want a GLES 2.0 context and a surface that supports recording.
		 */
		private void eglSetup() throws Exception {
			mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
			if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
				throw new Exception("unable to get EGL14 display");
			}
			int[] version = new int[2];
			if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
				throw new Exception("unable to initialize EGL14");
			}

			// Configure EGL for recording and OpenGL ES 2.0.
			int[] attribList = {
					EGL14.EGL_RED_SIZE, 8,
					EGL14.EGL_GREEN_SIZE, 8,
					EGL14.EGL_BLUE_SIZE, 8,
					EGL14.EGL_ALPHA_SIZE, 8,
					EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
					EGL_RECORDABLE_ANDROID, 1,
					EGL14.EGL_NONE
			};
			EGLConfig[] configs = new EGLConfig[1];
			int[] numConfigs = new int[1];
			EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
					numConfigs, 0);
			checkEglError("eglCreateContext RGB888+recordable ES2");

			// Configure context for OpenGL ES 2.0.
			if (GLESversion == 2) {
				int[] attrib_list = {
						EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
						EGL14.EGL_NONE
				};
				mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.eglGetCurrentContext(),
					attrib_list, 0);
			} else {
				if (GLESversion == 3) {
					int[] attrib_list = {
							EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
							EGL14.EGL_NONE
					};
					mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.eglGetCurrentContext(),
						attrib_list, 0);					
				} else {
					throw new Exception("Unsupported GLES version!");
				}
			}
			
			checkEglError("eglCreateContext");

			// Create a window surface, and attach it to the Surface we received.
			int[] surfaceAttribs = {
					EGL14.EGL_NONE
			};
			mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0], mSurface,
					surfaceAttribs, 0);
			checkEglError("eglCreateWindowSurface");
		}

		/**
		 * Discards all resources held by this class, notably the EGL context.  Also releases the
		 * Surface that was passed to our constructor.
		 */
		public void release() {

			FWLog.i("Releasing recording surface");

			if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
				EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
						EGL14.EGL_NO_CONTEXT);
				EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
				EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
				//EGL14.eglReleaseThread();
				//EGL14.eglTerminate(mEGLDisplay);
			}

			mSurface.release();
			mEGLDisplay = EGL14.EGL_NO_DISPLAY;
			mEGLContext = EGL14.EGL_NO_CONTEXT;
			mEGLSurface = EGL14.EGL_NO_SURFACE;
			mSurface = null;

			FWLog.i("Released");
		}

		/**
		 * Makes our EGL context and surface current.
		 */
		public void makeCurrent() throws Exception {
			EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
			checkEglError("eglMakeCurrent");
		}

		/**
		 * Calls eglSwapBuffers.  Use this to "publish" the current frame.
		 */
		public boolean swapBuffers() throws Exception {
			boolean result = EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
			checkEglError("eglSwapBuffers");
			return result;
		}

		/**
		 * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
		 */
		public void setPresentationTime(long nsecs) throws Exception {
			EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs);
			checkEglError("eglPresentationTimeANDROID");
		}

		/**
		 * Checks for EGL errors.  Throws an exception if one is found.
		 */
		private void checkEglError(String msg) throws Exception {
			int error;
			if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
				throw new Exception(msg + ": EGL error: 0x" + Integer.toHexString(error));
			}
		}      
	}
}