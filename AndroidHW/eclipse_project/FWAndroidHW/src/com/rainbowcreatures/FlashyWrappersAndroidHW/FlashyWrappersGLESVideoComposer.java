package com.rainbowcreatures.FlashyWrappersAndroidHW;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Bitmap.Config;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Choreographer;
import android.view.Surface;
import android.view.View;

import com.adobe.fre.FREContext;
import com.rainbowcreatures.FlashyWrappersAndroidHW.FlashyWrappersWindowsRecorder.ViewWithTexture;

// saves video using GLES 

public class FlashyWrappersGLESVideoComposer {

	// apps context
	private FlashyWrappersWrapper _wrapper = null;
	public boolean cacheDone = false;
	
	// general FW wide bool, if something goes wrong this should be set to false and all the 
	// methods should return just afer calling to prevent the app being captured from crashing
	public boolean allowCapture = true;
	EGLContext sharedContext = null;
	public String _videoFilePath = "";
	public volatile String state = "";

	public boolean behaviorLateInit = true;
	public boolean behaviorFBO0AfterInit = false;
	public boolean behaviorAutocapture = false;
	public boolean behaviorTextureInit = false;
	public boolean behaviorTextureDepthAndStencil = true;

	private volatile boolean cacheLocked = false;
	public ComposerRunnable composerRunnable = null;	
		
	// this is locked until the cache is filled
	private volatile boolean renderFrame = false;
	
	private static final String TAG = "[FlashyWrappers] ";

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
	
	public static final int CAPTURERECT_MODE_CALCULATE = 0;
	public static final int CAPTURERECT_MODE_VISUAL = 1;

	public boolean hasGLESContext = false;
	
	// video dimensions, in pixels
	public int mWidth = -1;
	public int mHeight = -1;
	
	// current display dimensions, in pixels
	public int displayWidth = -1;
	public int displayHeight = -1;
	
	// GL viewport dimensions
	public int viewportWidth = -1;
	public int viewportHeight = -1;
	
	// capture region 
	private int captureRectX = 0;
	private int captureRectY = 0;
	private int captureRectW = 0;
	private int captureRectH = 0;
	
	// the dimensions of the coordinate system we are capturing from
	private int captureStageW = 0;
	private int captureStageH = 0;
	
	private int captureRectColor = 0;
	private int captureRectMode = 0;
	
	// bit rate, in bits per second
	private int mBitRate = -1;
	private int mAudioSampleRate = 44100;
	private int mAudioNumberChannels = 1;
	
	private boolean realtime = false;
	private boolean audio = false;

	private boolean keyframe = false;
	private boolean forceKeyframe = false;

	private int videoFramesSent = 0;
	private volatile int videoFramesRendered = 0;
	
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

	private int nativeBitrate = 0;
	private int nativeFps = 0;
	private int frameIndex = 0;
	
	// what version will out context be? if shared context is enabled, then this is set automatically
	public static int GLESversion = 2;
	
	// the FBO used to clone textures into texture cache
	private int GLESCacheFBO = -1;
	private int GLESOldFBO = 0;
	
	// the texture cache
	private ArrayList<CachedTexture> textureCache = new ArrayList<CachedTexture>();	
	
	FlashyWrappersTexture bufferCacheTexture = null;
	ByteBuffer bufferCache = null;
	
	public static final int SAMPLES_PER_FRAME = 1024; // AAC
	public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
	public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

	private AudioRecord audioRecord;
	boolean firstFrameReady = false;

	// FW logo 
	byte[] logo = null;

	// threading locks
	private static Object muxerLock = new Object();
	private static Object videoLock = new Object();
	private static Object genericLock = new Object();
	
	/**
	 * This is the static lock preventing multiple threads calling into this threads
	 * OpenGL context at once, which could mess everything up. All threads calling 
	 * to this context must synchronize on this lock.
	 */
	public static final Object GLESLock = new Object(); 
	
	private Thread videoEncoderThread = null;
	private Thread audioEncoderThread = null;
	public Thread composerThread = null;

	// reference to the texture in the texture backed FBO
	int depthBuffer[] = new int[1];
	int stencilBuffer[] = new int[1];	
	int textureW = 0;
	int textureH = 0;
	float textureU = 1;
	float textureV = 1;
	float scaleFactor = 1;

	int PTSMode = PTS_AUTO;
	int framedropMode = FRAMEDROP_AUTO;

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
	
	// intermediate audio buffer, we are collecting into this buffer at first to not potentially
	// lag microphone recording or other stuff, and only fill muxer on separate thead whenever possible
	LinkedBlockingQueue<FlashyWrappersAudioPacket> audioBuffer = new LinkedBlockingQueue<FlashyWrappersAudioPacket>();
	// this remembers how many packets we've added in the audio buffer queue to avoid 
	// calling the pretty slow isEmpty() on the queue
	private volatile long audioBufferPackets = 0;
	
	public String profile = "AIR";
	
	float a = 0;

	public Choreographer ch = null;
	
	private EGLDisplay mSavedEglDisplay;
	private EGLSurface mSavedEglDrawSurface;
	private EGLSurface mSavedEglReadSurface;
	private EGLContext mSavedEglContext;
	
	
	/**
	 * This is comparator for sorting the cache textures by their Z property
	 * @author Pavel
	 *
	 */
	public class TextureComparator implements Comparator<CachedTexture> {
	    @Override
	    public int compare(CachedTexture o1, CachedTexture o2) {
	    	int res = 0;
	        if (o1.getCachedTexture().Z > o2.getCachedTexture().Z) res = 1;
	        if (o1.getCachedTexture().Z < o2.getCachedTexture().Z) res = -1;
	        return res;	        
	    }
	}
	
	/**
	 * This is the cached texture class 
	 * 
	 * @author Pavel
	 *
	 */
	class CachedTexture {
		// the original texture
		private FlashyWrappersTexture sourceTexture = null;
		// the cached texture
		private FlashyWrappersTexture cachedTexture = null;
		
		FlashyWrappersGLESVideoComposer composer = null;
		
		private boolean locked = false;
				
		public FlashyWrappersTexture getSourceTexture() {
			return sourceTexture;
		}
		
		public FlashyWrappersTexture getCachedTexture() {
			return cachedTexture;
		}
		
		// the constructor also creates the cache texture with the same dimensions as the original texture
		public CachedTexture(FlashyWrappersTexture t, FlashyWrappersGLESVideoComposer c) throws Exception {
			composer = c;
			sourceTexture = t;			
			FWLog.i("Creating cache texture...");
			cachedTexture = _wrapper.createTexture(sourceTexture.getRequestedWidth(), sourceTexture.getRequestedHeight(), false, FlashyWrappersTexture.NORMAL);
			cachedTexture.Z = sourceTexture.Z;
			cachedTexture.x = sourceTexture.x;
			cachedTexture.y = sourceTexture.y;
			if (captureRectW == 0 || captureRectH == 0) {			
				cachedTexture.updateScreenDimensions(viewportWidth, viewportHeight);
			}
			cachedTexture.updateVertices();
		}
		
		public boolean isLocked() {
			return locked;
		}
		
		public void dispose() {
			cachedTexture.dispose();
		}
		
		public void unlock() {
			locked = false;
		}
				
		// this will cache the original texture
		// we assume that startCaching was called! And that we've got the FBO for caching bound.
		public void cache() throws Exception {
			// the cache is locked, it should be unlocked after rendering
			// set viewport size, ie scale the video if we're not capturing a rectangle
			if (captureRectW == 0 || captureRectH == 0) {
				//GLES20.glViewport(0, 0, viewportWidth, viewportHeight);
			} else {
				// if we are capturing a rectangle, we never scale the viewport.
				int captureX = 0;
				int captureY = 0;				
				// visual mode used for inputting coordinates
				if (captureRectMode == FlashyWrappersGLESVideoComposer.CAPTURERECT_MODE_VISUAL) {
					captureX = -(captureRectX) / 2;
					captureY = -((displayHeight - (captureRectY + captureRectH)) / 2);
				} else {
					// trying to calculate it
					captureX = -(int)(((float)captureRectX / (float)captureStageW) * (float)(displayWidth / 2));
					captureY = -(int)((float)((captureStageH - (captureRectY + captureRectH)) / (float)captureStageH) * (float)(displayHeight / 2));
					if (FWLog.VERBOSE) FWLog.i("Capture rect X,Y:" + captureRectX + ", " + captureRectY + " Stage: " + captureStageW + " x " + captureStageH + " captureX,Y:" + captureX + ", " + captureY + " display:" + displayWidth + " x " + displayHeight);
				}				
				GLES20.glViewport(captureX, captureY, displayWidth, displayHeight);
			}
			
			locked = true;
			// we will attach the cached texture to the FBO
			if (FWLog.VERBOSE) FWLog.i("Caching texture...");
			GlUtil.checkGlError("cache0");			
			GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, cachedTexture.getGLESid(), 0);
			GlUtil.checkGlError("cache1");			
			// we render the source texture to the FBO, this will make it copied into the cachedTexture
			GLES20.glClearColor(0, 0, 0, 0);
			GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
			GlUtil.checkGlError("cache2");												
			sourceTexture.render();
			// we detach the cached texture from the FBO
			GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, 0, 0);
			GlUtil.checkGlError("cache3");												
			if (FWLog.VERBOSE) FWLog.i("Done caching texure...");
		}
		
		public void render() throws Exception {			
			cachedTexture.render();
			unlock();			
		}
	}
	
	// this caches a texture
	// TODO we must BLOCK until the texture is safely cached...otherwise we might end up 
	// with garbage inside the texture cache.
	
	
	/**
	 * Start sending texture batch to composer thread
	 * 
	 */
	public void startCacheBatch() {		
	}
	
	
	/**
	 * Finish sending the texture batch to composer thread
	 * 
	 */
	public void finishCacheBatch() {
	}
	
	/**
	 * Cache a texture, use only when sending batch of textures
	 * 
	 * @param t
	 * @throws Exception
	 */
	public void cacheTextureBatch(FlashyWrappersTexture t) throws Exception {
	}

	
	/**
	 * Cache a single texture
	 * 
	 * @param t
	 * @throws Exception
	 */
	public synchronized void cacheTexture(FlashyWrappersTexture t) throws Exception {
		// lock composer from accepting other textures to cache		
			if (FWLog.VERBOSE) FWLog.i("Posting cache texture as runnable...");
							
				final FlashyWrappersTexture tF = t;
				final FlashyWrappersGLESVideoComposer thisF = this;
				
				// TODO figure out how to do this in DOFRAME
				
				final Runnable task = new Runnable() {
					@Override
					public void run() {
						synchronized(GLESLock) {
							try {
								if (FWLog.VERBOSE) FWLog.i("The caching runnable was started...");
								CachedTexture ct = thisF.isTextureCached(tF);						
								// if it was already in cache, just update 
								if (ct != null) {
									thisF.startCaching();
									ct.cache();
									thisF.finishCaching();
								} else {				
									// otherwise create a new one
									thisF.startCaching();
									CachedTexture newCache = new CachedTexture(tF, thisF);
									textureCache.add(newCache);
									// re-sort the textures
									Collections.sort(textureCache, new TextureComparator());
									newCache.cache();
									thisF.finishCaching();
								}
								if (FWLog.VERBOSE) FWLog.i("The caching runnable was finished, notifyAll texture " + tF.getGLESid() + "...");								
								GLESLock.notifyAll();
							} catch (Exception e) {
								cacheDone = true;
								GLESLock.notifyAll();
								e.printStackTrace();
								Thread.currentThread().interrupt();								
							}
						}
					}
			};
			composerRunnable.handler.post(task);			
			// blocking until done so it can't be called multiple times(for now)
			synchronized(GLESLock) {
				while (!cacheDone) {
					if (FWLog.VERBOSE) FWLog.i("Waiting for caching to finish...");
					GLESLock.wait();
				}
			}
			cacheDone = false;

			Message msg = composerRunnable.handler.obtainMessage();
			msg.obj = "DOFRAME";
			composerRunnable.handler.sendMessage(msg);
//			FWLog.i("Done!");
	}
	
	/**
	 * Set the rectangle we want to capture from
	 * 
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 */
	public void setCaptureRectangle(int x, int y, int w, int h, int color, int mode) {
		captureRectX = x;
		captureRectY = y;
		captureRectW = w;
		captureRectH = h;
		captureRectColor = color;
		captureRectMode = mode;
	}
	
	public void setCaptureStage(int w, int h) {
		captureStageW = w;
		captureStageH = h;
	}

	public void setCaptureRectColor(int color) {
		captureRectColor = color;
	}

	public int getCaptureRectColor() {
		return captureRectColor;
	}
	
	// blit FW logo into ByteArray picture
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
	
	/**
	 * Add video frame for pixels, mostly for AIR only if someone wants to add MovieClip 
	 * 
	 * @param bb
	 * @return
	 * @throws Exception
	 */
	public boolean addVideoFrame(ByteBuffer bb) throws Exception {
		if (!allowCapture) {
			return false;
		}
		if (cacheLocked) {
			FWLog.i("Frame caching skipped");
			return false;
		}				
		cacheLocked = true;
		
		try {
						
			// extract the bytes
			byte[] bytes = new byte[(int) bb.capacity()];
			bb.get(bytes);						                            
			bb.position(0);

			// logo if set only in DEMO version, where it has some length...
			if (logo != null && logo.length > 0) {
				blitLogo(bytes, mWidth, 85, 60);
			}

			// update the buffer cache - this will be uploaded to texture only
			// when theres time because its pretty slow
			bufferCache.position(0);
			bufferCache.put(bytes);
			bufferCache.position(0);
			bytes = null;	
				
			// the requested to render must be sent to the composer thread 
			// beacuse thats where the GLES context is alive
			// TODO figure out how to do this in DOFRAME				
			final Runnable task = new Runnable() {
				@Override
				public void run() {
					synchronized(GLESLock) {
						try {
							tryRenderFrame();								
							cacheLocked = false;
						} catch (Exception e) {
							cacheLocked = false;
							e.printStackTrace();
							Thread.currentThread().interrupt();								
						}
					}
				}
			};			
			composerRunnable.handler.post(task);			
			Message msg = composerRunnable.handler.obtainMessage();
			msg.obj = "DOFRAME";
			composerRunnable.handler.sendMessage(msg);			
		} catch (Exception e) {
			AIRErrorHandler.handle(e);    		
		}
		return true;
	}

	/**
	 * Cache a single texture, however this version skips the caching if the cacher is busy
	 * instead of using the synchronized version which waits.
	 * 
	 * @param t
	 * @throws Exception
	 */
	public void cacheTextureSkipFrames(FlashyWrappersTexture t) throws Exception {
		// lock composer from accepting other textures to cache
			if (cacheLocked) {
				FWLog.i("Frame caching skipped");
				return;
			}
			cacheLocked = true;
			
			if (FWLog.VERBOSE) FWLog.i("Posting cache texture as runnable...");
							
				final FlashyWrappersTexture tF = t;
				final FlashyWrappersGLESVideoComposer thisF = this;

				// TODO figure out how to do this in DOFRAME
				
				final Runnable task = new Runnable() {
					@Override
					public void run() {
						synchronized(GLESLock) {
							try {
								if (FWLog.VERBOSE) FWLog.i("The caching runnable was started...");
								CachedTexture ct = thisF.isTextureCached(tF);						
								// if it was already in cache, just update 
								if (ct != null) {
									thisF.startCaching();
									ct.cache();
									thisF.finishCaching();
								} else {				
									// otherwise create a new one
									thisF.startCaching();
									CachedTexture newCache = new CachedTexture(tF, thisF);
									textureCache.add(newCache);
									// re-sort the textures
									Collections.sort(textureCache, new TextureComparator());
									newCache.cache();
									thisF.finishCaching();
								}
								if (FWLog.VERBOSE) FWLog.i("The caching runnable was finished, notifyAll texture " + tF.getGLESid() + "...");								
								cacheLocked = false;								
							} catch (Exception e) {
								e.printStackTrace();
								Thread.currentThread().interrupt();
								cacheLocked = false;																
							}
						}
					}
			};
			composerRunnable.handler.post(task);			
			// blocking until done so it can't be called multiple times(for now)
						
			Message msg = composerRunnable.handler.obtainMessage();
			msg.obj = "DOFRAME";
			composerRunnable.handler.sendMessage(msg);
//			FWLog.i("Done!");
	}


	private CachedTexture isTextureCached(FlashyWrappersTexture t) throws Exception {
		for (int a = 0; a < textureCache.size(); a++) {
			// match, this texture was already cached, return it		
			if (textureCache.get(a).getSourceTexture().getGLESid() == t.getGLESid()) {
//				FWLog.i("Texture cache found, id of texture " + t.getGLESid());
				return textureCache.get(a);
			}
		}		
		// was not cached
		return null;		
	}
	
	
	/**
	 * Is called by FW Wrapper when it wants to dispose a texture. The source texture
	 * can be disposed externally (for example window will be destroyed), so we need 
	 * to be informed about that to remove the corresponding texture cache.
	 * 
	 * @param t
	 */
	public void onTextureDispose(FlashyWrappersTexture t) {
        for (Iterator<CachedTexture> iterator = textureCache.iterator(); iterator.hasNext(); ) {
        	CachedTexture ct = iterator.next();
        	if (t.getGLESid() == ct.sourceTexture.getGLESid()) {
        		FWLog.i("Removing cached texture " + ct.getCachedTexture().getGLESid() + " with source ----> " + ct.sourceTexture.getGLESid() + "...");
        		ct.dispose();            		
        		iterator.remove();
        	}
        }		
		// re-sort the textures
		Collections.sort(textureCache, new TextureComparator());
	}
	
	// start the caching, binds the cache FBO - separated into different command
	// so we dont have to bind buffer FBO for each texture render
	private void startCaching() throws Exception {
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLESCacheFBO);
		GlUtil.checkGlError("startCaching");					
	}
	
	// finish caching, binds the default FBO 
	private void finishCaching() throws Exception {
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLESOldFBO);
		GlUtil.checkGlError("finishCaching");					
		
		// just for now, TODO, render the composers frame
		tryRenderFrame();
		GlUtil.checkGlError("tryRenderFrame");					

		cacheDone = true;
	}

	// create the FBO used for saving cached textures
	// we will attach the cache texture which will equal to the original texture in dimensions
	// then we will render the original texture into this FBO - this will effectively clone the
	// texture. 	
	private void setupCacheFBO() throws Exception {
		FWLog.i("setupCacheFBO called...");
		int params[] = new int[1];
		GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, params, 0);		
		GLESOldFBO = params[0];
		FWLog.i("Remembering the old FBO " + GLESOldFBO + " ...");
		GLES20.glGenFramebuffers(1, params, 0);		
		GlUtil.checkGlError("glGenFramebuffers");
		GLESCacheFBO = params[0];    
		FWLog.i("Cache FBO is " + GLESCacheFBO);
	}	


	/**
	 * This is where the recorder thread is running all the time
	 * 
	 * @author Pavel
	 *
	 */
	class ComposerRunnable implements Runnable {
		
		public Handler handler;
		public FlashyWrappersGLESVideoComposer _composer = null;			
		
		@Override
		public void run() {
			Looper.prepare();
			
			// we don't use callback for messages..for now at least. It would be actually
			// useful to get messages from the main thread, to start / stop recording
			FWLog.i("Starting composerRunnable...");
			handler = new Handler() {				
				public void handleMessage(Message msg) {
						String message = (String)msg.obj;
							
						// DO FRAME
						// --------
						if (message.equals("DOFRAME")) {
							if (state == "started") {
								try {
									// TODO allow the composer to record video all the time
									// separate out the composing from recording video
									// record all the time, but compose only when allowed									
									_composer.recordFrame();
								} catch (Exception e) {
									// don't try other captures
									allowCapture = false;
									// interrupt in case anything goes wrong
									e.printStackTrace();
									Looper.myLooper().quitSafely();
								}
							}								
						}
						
						// START RECORDING
						// ---------------
						
						if (message.equals("START")) {														
							try { 
								synchronized (genericLock) {
									FWLog.i("Composer thread handler: Got START message!");
									
									// prepare the encoder & create the EGL context on this thread
									prepareEncoder(_composer._videoFilePath, _composer.sharedContext);									

									// start video
									if (videoEncoderThread == null) {
										VideoEncoderRunnable runnable = new VideoEncoderRunnable();
										runnable._encoder = _composer;
										videoEncoderThread = new Thread(runnable);        	
									}

									videoEncoderThread.start();
									FWLog.i("Started video encoding thread");

									// start audio
									if (audio) {
										if (audioEncoderThread == null) {
											AudioEncoderRunnable runnable = new AudioEncoderRunnable();
											runnable._encoder = _composer;
											audioEncoderThread = new Thread(runnable);
										}
										audioEncoderThread.start();        	
										FWLog.i("Started audio encoding thread");
									}

									// ANY GLES INITIALISATIONS ONLY AFTER THIS POINT
									// ----------------------------------------------
									// make the composer GL context current
									
									mInputSurface.makeCurrent();
									hasGLESContext = true;
									if (!realtime) {
										// texture for sending direct pixels to it
										FWLog.i("Creating bufferCache texture...");
										bufferCacheTexture = _wrapper.createTexture(mWidth,  mHeight, false, FlashyWrappersTexture.AIR_BYTEARRAY);
									}

									// now we can create the programs, they will live in the composer context but are also shared with the GLESRecorder context
									
									_wrapper.programRenderTexture = GlUtil.createProgram("#version 100\nattribute vec4 Position;attribute vec4 SourceColor;varying vec4 DestinationColor; attribute vec2 TexCoordIn; varying vec2 TexCoordOut; void main(void) {DestinationColor = SourceColor;gl_Position = Position;TexCoordOut = TexCoordIn;}",
											"varying lowp vec4 DestinationColor; varying lowp vec2 TexCoordOut; uniform sampler2D Texture; void main(void) {gl_FragColor = texture2D(Texture, TexCoordOut);}");								
									
									GLES20.glBindAttribLocation(_wrapper.programRenderTexture, 0, "Position");
									GLES20.glBindAttribLocation(_wrapper.programRenderTexture, 1, "SourceColor");
									GLES20.glBindAttribLocation(_wrapper.programRenderTexture, 2, "TexCoordIn");
									
									GlUtil.linkProgram(_wrapper.programRenderTexture);
									// for now do not use this, enable for window recording
									//_wrapper.programRenderExternalTexture = GlUtil.createProgram("#version 100\nattribute vec4 Position;attribute vec4 SourceColor;varying vec4 DestinationColor; attribute vec2 TexCoordIn; varying vec2 TexCoordOut; void main(void) {DestinationColor = SourceColor;gl_Position = Position;TexCoordOut = TexCoordIn;}",									
//										"#extension GL_OES_EGL_image_external : require;\n precision lowp float;varying vec2 TexCoordOut;\nuniform samplerExternalOES Texture;\nvoid main() {\ngl_FragColor = texture2D( Texture, TexCoordOut );\n}");
//									GLES20.glBindAttribLocation(_wrapper.programRenderExternalTexture, 0, "Position");
									//GLES20.glBindAttribLocation(_wrapper.programRenderExternalTexture, 1, "SourceColor");
									//GLES20.glBindAttribLocation(_wrapper.programRenderExternalTexture, 2, "TexCoordIn");

									//GlUtil.linkProgram(_wrapper.programRenderExternalTexture);
									
									_wrapper.programARGB = GlUtil.createProgram("#version 100\nattribute vec4 Position;attribute vec4 SourceColor;varying vec4 DestinationColor; attribute vec2 TexCoordIn; varying vec2 TexCoordOut; void main(void) {DestinationColor= SourceColor; gl_Position = Position;TexCoordOut = TexCoordIn;}",
												"varying lowp vec4 DestinationColor; varying lowp vec2 TexCoordOut; uniform sampler2D Texture; void main(void) {gl_FragColor = texture2D(Texture, TexCoordOut).gbar;}");

									GLES20.glBindAttribLocation(_wrapper.programARGB, 0, "Position");
									GLES20.glBindAttribLocation(_wrapper.programARGB, 1, "SourceColor");
									GLES20.glBindAttribLocation(_wrapper.programARGB, 2, "TexCoordIn");

									GlUtil.linkProgram(_wrapper.programARGB);
									// create the thread for recording & creating the GLES context

									setupCacheFBO();
									
									
									// we can start recording video now(ie composing cached textures and posting frames)
									state = "started";	
									genericLock.notify();
								}
							} catch (Exception e) {
								allowCapture = false;								
								synchronized (genericLock) {
									genericLock.notify();
								}
								e.printStackTrace();
								Looper.myLooper().quitSafely();
							}					
						}
						
						// FINISH RECORDING
						// ----------------
						
						if (message.equals("STOP")) {
							try {
								synchronized (genericLock) {
									state = "";
									FWLog.i("Finishing...");						
									// finish of the threads(trying everything because unsure how to stop threads 100%, ehm)
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
										FWLog.i("Joining audio thread...");
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
										// TODO dispatch encoded event
										if (FlashyWrappers.currentAIRContext != null) {
											FlashyWrappers.currentAIRContext.dispatchStatusEventAsync("encoded", "");
										}
									}
									
									FWLog.i("Removing cache FBO...");
									
									int params[] = new int[1];
									params[0] = GLESCacheFBO;
									GLES20.glDeleteFramebuffers(1, params, 0);
									
									FWLog.i("Removing cached textures...");

									// remove all cached texture - this should be done from the same thread
									// as they were created 
							        for (Iterator<CachedTexture> iterator = textureCache.iterator(); iterator.hasNext(); ) {
							        	CachedTexture ct = iterator.next();
						        		FWLog.i("Removing cached texture " + ct.getCachedTexture().getGLESid() + " with source ----> " + ct.sourceTexture.getGLESid() + "...");
						        		ct.dispose();            		
						        		iterator.remove();
							        }			
							        
									FWLog.i("Deleting programs...");

									// delete programs
									GLES20.glDeleteProgram(_wrapper.programRenderTexture);
									GLES20.glDeleteProgram(_wrapper.programRenderExternalTexture);		
									GLES20.glDeleteProgram(_wrapper.programARGB);										        
							        
									if (bufferCacheTexture != null) _wrapper.disposeTexture(bufferCacheTexture);
							        								
									FWLog.i("Interrupting composer thread...");
									// this will go into exception and call notify
									genericLock.notify();
									Looper.myLooper().quitSafely();
								}
							} catch (Exception e) {
								FWLog.i("In composer exception code...");
								synchronized (genericLock) {
									FWLog.i("Sending composer notify...");
									genericLock.notify();
								}
								e.printStackTrace();
								Looper.myLooper().quitSafely();
							}					
						}
				}				
			};
			FWLog.i("Starting composerRunnable loop...");
			Looper.loop();			
			FWLog.i("Finishing composerRunnable loop...");
		}
	}
	
	/**
	 * This is the video encoder thread
	 * 
	 * @author Pavel
	 *
	 */
	class VideoEncoderRunnable implements Runnable {
		public FlashyWrappersGLESVideoComposer _encoder;        	
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

	
	/**
	 * This is the audio encoder thread
	 * @author Pavel
	 *
	 */
	class AudioEncoderRunnable implements Runnable {
		public FlashyWrappersGLESVideoComposer _encoder;        	
		@Override
		public void run() {
				if (FWLog.VERBOSE) FWLog.i("Entering audio encoding thread runnable");
				// TODO Auto-generated method stub
				if (nativeMicrophoneRecording) {
					setupAudioRecord();
					FWLog.i("Before recording, microphone state is " + audioRecord.getState() + ", recording state is: " + audioRecord.getRecordingState() );					
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
				// perform a drain to make sure we have free buffers for sending the final
				// stop data
				try {
					_encoder.drainAudioEncoder(false);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if (nativeMicrophoneRecording) {
					FWLog.i("Stopping audio recorder...");					
					sendMicrophoneAudioToEncoder(true);
					FWLog.i("Before stop, microphone state is " + audioRecord.getState() + ", recording state is: " + audioRecord.getRecordingState() );					
					audioRecord.stop();
					FWLog.i("After stop, microphone state is " + audioRecord.getState() + ", recording state is: " + audioRecord.getRecordingState() );										
					audioRecord.release();
				} else {
					FWLog.i("Sending stop to encoder");
					sendAudioToEncoder(null, true, 0);
				}
				FWLog.i("Exiting audio thread runnable...");				
				return;
		}        	
	}

	
	/**
	 * Constructor
	 * @param wrapper
	 */
	public FlashyWrappersGLESVideoComposer() {
		_wrapper = FlashyWrappersWrapper.instance();
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

	/**
	 * Do we want to record microphone as well?
	 * @param b true to record microphone
	 */
	public void setNativeMicrophoneRecording(Boolean b) {
		nativeMicrophoneRecording = b;
		if (nativeMicrophoneRecording) {
			FWLog.i("Native microphone recording is ON");
		} else {
			FWLog.i("Native microphone recording is OFF");
		}
	}

	/**
	 * Set the PTS calculation mode, can be realtime or non-realtime
	 * @param mode PTS_AUTO, PTS_REALTIME, PTS_MONO
	 */
	public void setPTSMode(int mode) {
		PTSMode = mode;
		FWLog.i("Forcing PTS mode " + PTSMode);    	
	}

	/**
	 * Set the framedrop mode, can drop frames or not drop, or auto
	 * @param mode FRAMEDROP_AUTO, FRAMEDROP_OFF or FRAMEDROP_ON
	 */
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
				// non-realtime encoding, compute timestamp based on the sample length
				long pts = 0;
				if ((realtime && PTSMode == PTS_AUTO) || PTSMode == PTS_REALTIME) {																		
					//						pts = getJitterFreePTS(ap_pts, inputLength / 4);
					pts = ap_pts;
				}

				if (!endOfStream) {
					mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, inputLength, pts, 0);
				} else {
					FWLog.i("queue input buffer with END_OF_STREAM flag");
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

	// send audio from microphone straight to encoder, TODO should be probably joined with sendAudioToEncoder, similar
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
	
	// this renders the composed texture to screen
	// we only need to render before swapBuffers so we're using renderFrame flag
	// which tells us its time to render.
	
	private void tryRenderFrame() throws Exception {
		// render texture cache to OpenGL context at video fps intervals.
		// it would be useless to render all the time if the video is lower fps anyway...
		if (renderFrame) {			
			renderFrame = false;
			GLES20.glClearColor(0, 0, 0, 0);
			GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);			
			if (bufferCache != null && bufferCache.capacity() > 0) {
				GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bufferCacheTexture.getGLESid());
				GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bufferCacheTexture.getRequestedWidth(), bufferCacheTexture.getRequestedHeight(), GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bufferCache);
				GlUtil.checkGlError("glTexSubImage2D");
				GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
				bufferCacheTexture.render();
			}						
			// render all textures in cache into video					
			if (textureCache.size() > 0) {
				for (int i = 0; i < textureCache.size(); i++) {
					textureCache.get(i).render();
				}
			}		
			videoFramesRendered++;
		}
	}
	
	/**
	 * Add audio frame to audio buffer, in the natively accepted shorts. 
	 * 
	 * @param shortsBytes the PCM with audio, shorts are expected
	 * @throws Exception
	 */
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
	
	/**
	 * This is for FW AIR, sets the logo for trial version. 
	 * 
	 * @param bb the logo image, in ARGB format
	 */
	public void setLogo(ByteBuffer bb) {
		logo = null;
		byte[] bytes = new byte[(int) bb.capacity()];
		bb.get(bytes);						                            
		bb.position(0);
		logo = bytes.clone();
		bytes = null;
	}


	/**
	 * Add audio frame to audio buffer, in the floats typical for AIR
	 * @param bb
	 * @throws Exception
	 */
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
 
	/**
	 * Compose a frame from GLES / Windows textures and render them to video
	 * 
	 * @throws Exception
	 */
	public void recordFrame() throws Exception {
		//FWLog.i("Frames rendered: " + videoFramesRendered);
		if (!allowCapture) return;		
		try {
												
			// DEMO, end after 30 secs
			if (logo != null && logo.length > 0) {
				if ((float)videoFramesSent / (float)nativeFps > 30) { 
					throw new Exception("No more than 30 seconds recording allowed in free mode. Visit http://www.flashywrappers.com to buy an upgrade.");
				}	
			}

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

			if (FWLog.VERBOSE) FWLog.i("stepAccum " + stepAccum + " stepTarget " + stepTarget + " step " + step);

			//  Save video frame when its time (based on fps)
			if (stepAccum >= stepTarget) {
								
				renderFrame = true;
				if (FWLog.VERBOSE) FWLog.i("its time now!");

				stepTarget += step;

				// do not record when nothing was rendered yet
				if (videoFramesRendered == 0) return;

				videoFramesSent++;

				// only if the video encoder was drained in the background thread 
				if (drainedVideo) {
					
					// TODO switching to another context -> this might be in secondary thread instead later on
					// only do this if we're sharing context on this thread					
					//if (sharedContext) mInputSurface.makeCurrent();

										
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
					
					synchronized (GLESLock) {
						// 	this sends new input to media encoder so we know its not drained at this point
						mInputSurface.swapBuffers();
					}
					
					// restore previous context if we're in shared context on this thread 
//					if (sharedContext) restoreRenderState();
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
			if (FWLog.VERBOSE) FWLog.i("composeFrame took " + ((System.nanoTime() - time) / 1000000) + " ms");
		} catch (Exception e) {
			AIRErrorHandler.handle(e);    		
		}		
	}

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

	/**
	 * Init the native recording
	 */

	public void start(int width, int height, int fps, int bitrate, int _stage_fps, String videoFilePath, int audio_sample_rate, int audio_number_channels, int _realtime, int _audio, EGLContext _sharedContext) throws Exception {
		
		FWLog.i("Starting the composer thread...");
				
		if (composerThread == null) {
			composerRunnable = new ComposerRunnable();
			composerRunnable._composer = this;
			composerThread = new Thread(composerRunnable);			
		}		
		composerThread.start();
		
		FWLog.i("Composer thread started...");
		
		try {
			// check for Android version support (TODO move it to new method called isSupported to be able to return this cleanly as well)
			if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) { 
				// only for gingerbread and newer versions
				FWLog.i("Attempting to run on Android < 4.3!");
				throw new Exception("Sorry, but Android < 4.3 is not supported for the video recording functionality.");
			}			
			_videoFilePath = videoFilePath;			
			sharedContext = _sharedContext;
			videoFramesSent = 0;
			videoFramesRendered = 0;
			startWhen = 0;
			firstFrameReady = false;
			mEosSpinCount = 0;
			audio_startPTS = 0;
			audio_totalSamplesNum = 0;
			mAudioNumberChannels = audio_number_channels;
			mAudioSampleRate = audio_sample_rate;
			audioNonrealtimePts = 0;
			videoNonrealtimePts = 0;
			step = 0;
			delta = 0;
			millisOld = 0;
			stepAccum = 0;
			stepTarget = 0;
			lastEncodedAudioTimeStamp = 0;
			mNumTracksAdded = 0;
			keyframe = false;
			forceKeyframe = false;
			nativeFps = fps;
			nativeBitrate = bitrate;
			
			FWLog.i("Video capture rect: " + captureRectW + " x " + captureRectH);
			
			// if capture rectangle wasn't specified the dimensions of the video can be specified
			if (captureRectW == 0 && captureRectH == 0) {
				mWidth = width;
				mHeight = height;
			} else {
				// otherwise the dimensions must be equal to the capture rect we are capturing
				mWidth = captureRectW;
				mHeight = captureRectH;
			}
			
			mBitRate = nativeBitrate;
			frameIndex = 0;
			cacheLocked = false;
			renderFrame = false;
			
			int[] viewport = new int[4];
			// Initialize OpenGL, first get the viewport size         	               
			GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0);
			int stageW = viewport[2];
			int stageH = viewport[3];

			// set audio on / off
			audio = false;
			if (_audio == 1) audio = true;

			// set realtime on / off
			realtime = false;
			if (_realtime == 1) realtime = true;

			if (realtime) {
				// use stage dimensions only if dimensions are not specified
				if (mWidth <= 0 || mHeight <= 0) {
					mWidth = stageW;
					mHeight = stageH;
				}
				
				displayWidth = stageW;
				displayHeight = stageH;
				
				// if capture rectangle wasn't specified then allow scaling the video 
				if (captureRectW == 0 && captureRectH == 0) {
					viewportWidth = mWidth;
					viewportHeight = mHeight;
				} else {
					// otherwise it must always be unscaled 
					viewportWidth = stageW;
					viewportHeight = stageH;					
				}
			} else {
				// allocate buffer for those pixels
				bufferCache = ByteBuffer.allocate(width * height * 4);				
				mWidth = width;
				mHeight = height;
				displayWidth = mWidth;
				displayHeight = mHeight;
				viewportWidth = mWidth;
				viewportHeight = mHeight;				
			}

			
			// set number of tracks (if no audio then only 1)
			numTracks = 2;
			if (!audio) {
				numTracks = 1;
			}

			// if stage fps is set and we're in realtime mode we compute the step value
			if ((realtime && framedropMode == FRAMEDROP_AUTO) || framedropMode == FRAMEDROP_ON) {
				step = 1000 / (float)fps;
			}
						
			FWLog.i("frame step is " + step);			
			FWLog.i("mWidth:" + mWidth + " mHeight:" + mHeight + "mBitRate:" + mBitRate);			
			
			// are we in OpenGL context already? If yes then share it
			// TODO figure this out
			if (sharedContext != null) {
				String version = GLES20.glGetString(GLES20.GL_VERSION);
				String renderer = GLES20.glGetString(GLES20.GL_RENDERER);				
				GLESversion = getMajorGLVersionFromString(version);
				FWLog.i("Shared context requested, finding out which version it is...");
				FWLog.i("GLES version: " + version);
				FWLog.i("Parsed GLES version (hopefully matches string above): " + GLESversion);
				FWLog.i("GLES renderer: " + renderer);
			}		

			isVideoEncoding = true;        
			isAudioEncoding = true;
			
			int params[] = new int[1];
			GLES20.glGetIntegerv(GLES20.GL_MAX_VERTEX_ATTRIBS, params, 0);		
			FWLog.i("GL_MAX_VERTEX_ATTRIBS: " + params[0]);

			
			FWLog.i("Sending START message to composer thread...");
			FWLog.i("composerRunnable:" + composerRunnable);
			FWLog.i("handler:" + composerRunnable.handler);
			Message msg = composerRunnable.handler.obtainMessage();
			msg.obj = "START";
			
			composerRunnable.handler.sendMessage(msg);
			
			// force waiting for the state to change to get ready
			synchronized(genericLock) {
				while (state == "") {
					FWLog.i("Waiting for composer to start...");
					genericLock.wait();
				}
			}
			
		} catch (Exception e) {
			FWLog.i("Initialization was not OK, capturing will be disabled");
			allowCapture = false;
			AIRErrorHandler.handle(e);    		
		}
	}

	/**
	 * Update the display dimensions with this when app changes dimension
	 * 
	 * @param w
	 * @param h
	 */
	public void updateDisplayDimensions(int w, int h) {
		displayWidth = w;
		displayHeight = h;
		FWLog.i("Updating display dimensions to " + displayWidth + " x " + displayHeight);
	}
	
	/**
	 * Finish up the native encoding  
	 */

	public void stop() throws Exception {
		if (!allowCapture) {
			FWLog.i("Capturing not allowed, stop will not be called...");
			return;
		}
		try {						
			FWLog.i("Sending stop message to composer thread...");
			Message msg = composerRunnable.handler.obtainMessage();
			msg.obj = "STOP";
			composerRunnable.handler.sendMessage(msg);
			
			// wait for finishing...
			synchronized(genericLock) {
				while (state != "") {				
					FWLog.i("Waiting for composer to stop...");
					genericLock.wait();					
				}
			}
			
			FWLog.i("Composer stopped...");
			
			composerThread.join();
			composerThread = null;

			if (bufferCache != null) {
				bufferCache.clear();
				bufferCache = null;
			}
			
//			FWLog.i("Sending video to server...");
//			_wrapper.POSTClient.send(_videoFilePath);			
			
			FWLog.i("All done in composer!");
			
			// reset capture rectangle before new encoding
			/*captureRectX = 0;
			captureRectY = 0;
			captureRectW = 0;
			captureRectH = 0;*/
			
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
	
	public String fixAndroidEncoderQuirks() {
		FWLog.i("Listing available codecs...");
		int count = MediaCodecList.getCodecCount();
		for (int a = 0; a < count; a++) {
			String name = MediaCodecList.getCodecInfoAt(a).getName();
			FWLog.i(name);
			// handle quirks based on codecs
			if (name.equals("OMX.MTK.VIDEO.ENCODER.AVC")/* || name.equals("OMX.Intel.VideoEncoder.AVC")*/) {
				FWLog.i("Detected Mediatek encoder, resolution should be 1280 x 720.");
				return "quirk_mtk";
			}
		}	
		return "";
	}

	/**
	 * Configures encoder and muxer state, and prepares the input Surface.
	 */
	private void prepareEncoder(String outputPath, EGLContext _sharedContext) throws Exception {    		
		FWLog.i("setting up video encoder");		
		String quirk = fixAndroidEncoderQuirks();
		if (quirk.equals("quirk_mtk")) {
			captureRectW = 0;
			captureRectH = 0;		
		}
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
		FWLog.i("Started video encoding thread");

		// we can use for input and wrap it with a class that handles the EGL work.
		mVideoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
		mVideoEncoder.configure(VideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mInputSurface = new CodecInputSurface(mVideoEncoder.createInputSurface(), _sharedContext);
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
		int loops = 0;
		while (true) {
			if (loops > 100) {
				FWLog.i("too many loops in audio drain: " + loops);
			}
			loops++;
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
		public CodecInputSurface(Surface surface, EGLContext _sharedContext) throws Exception {
			if (surface == null) {
				throw new NullPointerException();
			}
			mSurface = surface;
			eglSetup(_sharedContext);
		}

		/**
		 * Prepares EGL.  We want a GLES 2.0 context and a surface that supports recording.
		 */
		private void eglSetup(EGLContext _sharedContext) throws Exception {
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
				EGLContext context = null;
				if (_sharedContext != null) context = _sharedContext; else context = EGL14.EGL_NO_CONTEXT;
				mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], context, attrib_list, 0);
			} else {
				if (GLESversion == 3) {
					int[] attrib_list = {
							EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
							EGL14.EGL_NONE
					};
					EGLContext context = null;
					if (_sharedContext != null) context = _sharedContext; else context = EGL14.EGL_NO_CONTEXT;
					mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], context, attrib_list, 0);					
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
