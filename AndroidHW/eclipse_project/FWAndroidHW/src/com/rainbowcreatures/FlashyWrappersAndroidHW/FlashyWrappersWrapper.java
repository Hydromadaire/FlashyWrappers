package com.rainbowcreatures.FlashyWrappersAndroidHW;

import android.app.Application;
import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.rainbowcreatures.FlashyWrappersAndroidHW.FlashyWrappersWindowsRecorder.ViewWithTexture;

// 

public class FlashyWrappersWrapper {
	
	
	public FlashyWrappersGLESRecorder GLESRecorder = null;
	public FlashyWrappersGLESVideoComposer GLESComposer = null;
	public FlashyWrappersWindowsRecorder WindowsRecorder = null;
	public FlashyWrappersClient POSTClient = null;

	public static String PROFILE_AIR = "profileAIR";
	public static String PROFILE_KANJI = "profileKanji";
	private static String _profile = "";
	
	static Handler wrapperThreadHandler = null;
	
	private static boolean startedAll = false;
	public boolean wasInBackground = false;
	private int numberStarts = 0;
	
	// GLES handles
	// Those will be available for the GLESComposer as well as GLESRecorder, because they will share
	// contexts if GLESRecorder is used.
	
	// textures FW uses (composer directly doesn't render those, it uses its own texture cache ArrayList)
	public ArrayList<FlashyWrappersTexture> textures = new ArrayList<FlashyWrappersTexture>();
	private ArrayList<FlashyWrappersTexture> dieTextures = new ArrayList<FlashyWrappersTexture>();
	
	// programs for common use 
	public int programRenderTexture = -1;
	public int programRenderExternalTexture = -1;
	public int programARGB = -1;
	public Object textureDeleteLock = new Object();
	volatile boolean addingDone = false;

//	private Thread detectGLESThread = null;
	private static FlashyWrappersWrapper instance = null;
	
	private static Handler startThreadHandler = null;
	private static Thread startThread = null;
	private static EGLContext startGLESContext = null;
	private static Context startAppContext = null;
	private static int _width = 0;
	private static int _height = 0;
	private static int _fps = 0;
	private static int _bitrate = 0;
	
/*	class DetectGLESRunnable implements Runnable {
		public FlashyWrappersGLESVideoComposer _composer = null;
		FlashyWrappersWrapper _wrapper = null;
		
		void DetectGLESRunnable(FlashyWrappersWrapper wrapper) {
			_wrapper = wrapper;
		}
		
		@Override
		public void run() {	
			
			boolean contextFound = false;
			while (!contextFound) {
								
				wrapperThreadHandler.post(new Runnable() {
					@Override
					public void run() {
						contextFound = true;
					}
				});
				
				FWLog.i("Looking for GLES context...");				
			}
			
			FWLog.i("Found GLES context, compiling shaders");
			// detect EGL context
			
			try {
				programRenderTexture = GlUtil.createProgram("#version 100\nattribute vec4 Position;attribute vec4 SourceColor;varying vec4 DestinationColor; attribute vec2 TexCoordIn; varying vec2 TexCoordOut; void main(void) {DestinationColor = SourceColor;gl_Position = Position;TexCoordOut = TexCoordIn;}",
						"varying lowp vec4 DestinationColor; varying lowp vec2 TexCoordOut; uniform sampler2D Texture; void main(void) {gl_FragColor = texture2D(Texture, TexCoordOut);}");
				programRenderExternalTexture = GlUtil.createProgram("#version 100\nattribute vec4 Position;attribute vec4 SourceColor;varying vec4 DestinationColor; attribute vec2 TexCoordIn; varying vec2 TexCoordOut; void main(void) {DestinationColor = SourceColor;gl_Position = Position;TexCoordOut = TexCoordIn;}",
					"#extension GL_OES_EGL_image_external : require;\n precision lowp float;varying vec2 TexCoordOut;\nuniform samplerExternalOES Texture;\nvoid main() {\ngl_FragColor = texture2D( Texture, TexCoordOut );\n}");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}							
			FWLog.i("Shaders compiled.");			
		}
	}*/

	static {
		instance();
	}
	
	public static FlashyWrappersWrapper instance() {
		if (instance == null) {
			try {							
				instance = new FlashyWrappersWrapper();
				instance.GLESRecorder = new FlashyWrappersGLESRecorder();
				instance.GLESComposer = new FlashyWrappersGLESVideoComposer();
				instance.WindowsRecorder = new FlashyWrappersWindowsRecorder();
				instance.POSTClient = new FlashyWrappersClient("http://flashywrappers.com/server/gateway/index.php", "1");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} 
		return instance;		
	}

	
	private FlashyWrappersWrapper() {}
		
	/**
	 * Create texture, add it to the textures ArrayList and return it
	 * This always tries to execute on the GLESComposer thread, so GLESComposer
	 * must be running and have GLES context available (ie be started).
	 * 
	 * @param width width of texture, will be converted to the nearest POT
	 * @param height height of texture, will be converted to the nearest POT
	 * @param initToBlack whenever we want to initialize the texture to black
	 * @return returns @FlashyWrappersTexture
	 * @throws Exception
	 */
	public FlashyWrappersTexture createTexture(int width, int height, boolean initToBlack, int type) throws Exception {
		if (GLESComposer.hasGLESContext) {

			final int widthF = width;
			final int heightF = height;
			final boolean initToBlackF = initToBlack;
			final int typeF = type;
			
			FlashyWrappersTexture newTexture2 = new FlashyWrappersTexture(widthF, heightF, initToBlackF, typeF);
			textures.add(newTexture2);
			return newTexture2;

/*		// TODO this is better than posting runnable and then waiting for it, obviously
			ExecutorService executor = Executors.newSingleThreadExecutor();
			synchronized (FlashyWrappersGLESVideoComposer.GLESLock) {				
				Future<FlashyWrappersTexture> result = executor.submit(new Callable<FlashyWrappersTexture>() {
					public FlashyWrappersTexture call() throws Exception {
						FlashyWrappersTexture newTexture = new FlashyWrappersTexture(widthF, heightF, initToBlackF, typeF);
						return newTexture;				        
					}
				});
				try {
					FlashyWrappersTexture newTexture = result.get();
					textures.add(newTexture);
					return newTexture;
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}
			}*/
		} else {
			throw new Exception("You cannot create texture until the composer creates GLES context");
		}
	}
	
	public FlashyWrappersTexture getTexture(int id) throws Exception {
		if (id >= 0 && id < textures.size()) {
			return textures.get(id);
		} else {
			throw new Exception("Texture index out of bounds");
		}
	}
	public String getProfile() {
		return _profile;
	}
	
	public void setProfile(String profile) throws Exception {
		if (profile == PROFILE_AIR || 
			profile == PROFILE_KANJI) {
			applyProfile(profile);
			_profile = profile;
		} else {
			throw new Exception("Profile " + profile + " not recognized!");
		}
	}
	
	/**
	 * Shortcut method for starting all components at once instead of micromanaging them.
	 * Use stopAll to stop recording. Also, if you want to record from GLES start this from 
	 * the target GLES thread right after making the GLES context current - it will feed FBO
	 * (recording FBO) to your app by binding it. You should work with it as you would with FBO 0.
	 * 
	 * @param width width of the app and also the recorded video
	 * @param height height of the app and also the recorded video
	 * @param fps fps of the video
	 * @param bitrate bitrate (quality) of the video
	 * @param appContext the application context, where FW can access Choreographer
	 * @param gameGLESContext when starting from apps GLES thread, use EGL14.eglGetCurrentContext()
	 * @throws Exception
	 */
	public void startAll(int width, int height, int fps, int bitrate, Context appContext, EGLContext gameGLESContext) throws Exception {
		// are we coming really from background?
		if (!wasInBackground && numberStarts > 0) {
			// if not then we never stopped so we don't want to do startAll again
			return;
		} else {
			// yes we were in background so do a full restart
			wasInBackground = false;
		}
		if (startThread == null) {
			startThread = Thread.currentThread();
			startGLESContext = gameGLESContext;
			startAppContext = appContext;
			_width = width;
			_height = height;
			_fps = fps;
			_bitrate = bitrate;
			appContext.registerComponentCallbacks(new FlashyWrappersBackgroundDetect());			
		}
		FWLog.LOGTOFILE = false;
		FWLog.VERBOSE = false;
		FWLog.i("About to start FW");		
		instance.GLESComposer.start(width, height, fps, bitrate, fps, appContext.getExternalFilesDir(null).getAbsolutePath() + "/video_merged.mp4", 0, 0, 1, 0, gameGLESContext);
		instance.GLESRecorder.start();		
		// Get a handler that can be used to post to the main thread
		Handler mainHandler = new Handler(appContext.getMainLooper());
		Runnable myRunnable = new Runnable() {
		    @Override 
		    public void run() {
		    	try {
		    		// we need choreographer so we're posting to app context
		    		instance.WindowsRecorder.start(Choreographer.getInstance());
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		    } // This is your code
		};
		mainHandler.post(myRunnable);				
		startedAll = true;
		numberStarts++;
	}
	
	
	/**
	 * Resumes previously stopped recording. Useful when going out of pause. 
	 * 
	 * @throws Exception
	 */
	public void resumeAll() throws Exception {
	}
	
	/**
	 * Shortcut for stopping everything at once, use startAll to start everything.
	 * 
	 * @throws Exception
	 */
	public void stopAll() throws Exception {
		if (startedAll) {
			instance.WindowsRecorder.stop();                    			
			instance.GLESComposer.stop();                    			
			instance.GLESRecorder.stop();
		} else {
			throw new Exception("Calling stopAll but startAll wasn't called before that.");
		}
		startedAll = false;				
	}
	
	
	/**
	 * Use this just before swapBuffers - this tells GLESRecorder to record FBO AND to render
	 * the recorded FBO to screen instead of your app (because your app isn't using FBO 0 you 
	 * wouldn't see anything on the screen, just in the video).
	 * 
	 */
	public void recordGLES() throws Exception {
		if (startedAll) {
			instance.GLESRecorder.renderFBOTextureToScreen();
		}
	}
	
	/**
	 * This is a shortcut for updating the list of windows, it should be called from onWindowFocusChanged
	 * from the apps activity.
	 * 
	 */
	public void updateWindows() {
		if (instance.WindowsRecorder.state == "started") {
	    	instance.WindowsRecorder.updateRootViews();
	    }		
	}
	
	private void applyProfile(String profile) throws Exception {
		if (profile == PROFILE_AIR) {
//			instance.behaviorAutocapture = false;
			instance.GLESRecorder.behaviorFBO0AfterInit = true;
			instance.GLESRecorder.behaviorLateInit = false;
			instance.GLESRecorder.behaviorDepthAndStencil = false;
			instance.GLESRecorder.behaviorTextureInit = false;
			
			// DO NOT use this skipping thing, that was based on wrong assumptions
			// basically, we are posting runnable and then instantly going "bye" from 
			// the method, but we didn't realize that this runnable has reference to 
			// the texture from main thread.
			
			// once the runnable would start messing with that texture it could be also
			// messed with from the main thread, and THAT would cause an explosion. 
			// so its really best,when posting to GLES thread, to always wait for finishing
			// it and then exit from the method. Unless we're 100% sure that we are not referencing
			// something from main thread which shouldn't be touched otherwise. Then we can
			// probably use this skipping technique(post and exit, and on next reentry exit again
			// if the post wasn't finished yet).
			
			instance.GLESRecorder.behaviorSkipFramesWhenCaching = false; 
			FWLog.i("Setting capturing profile for AIR");
			return;
		}
		if (profile == PROFILE_KANJI) {
			instance.GLESRecorder.behaviorFBO0AfterInit = false;
			instance.GLESRecorder.behaviorLateInit = true;
			instance.GLESRecorder.behaviorDepthAndStencil = true;
			instance.GLESRecorder.behaviorTextureInit = false;
			FWLog.i("Setting capturing profile for Kanji");
			return;
		}
		FWLog.i("WARNING, invalid profile set, default one will be used!");
	}
	
	private void disposeTextures() {
		for (int i = 0; i < textures.size(); i++) {
			// TODO let everyone know the texture is gone, in thread-safe way
			final FlashyWrappersTexture tF = textures.get(i);
			// make sure to perform the disposal of texture in the correct GLES context
			/*ExecutorService executor = Executors.newSingleThreadExecutor();
			synchronized (FlashyWrappersGLESVideoComposer.GLESLock) {				
				Future<Boolean> result = executor.submit(new Callable<Boolean>() {
					public Boolean call() throws Exception {
						tF.dispose();
						return true;				        
					}
				});
			}*/
			tF.dispose();
		}
		textures.clear();
	}

	/**
	 * This will free texture from the render list. It actually uses GLES texture id underneath
	 * to find the texture.
	 * 
	 * @param t
	 */
	public void disposeTexture(FlashyWrappersTexture t) {
		synchronized (textureDeleteLock) {		
			for (Iterator<FlashyWrappersTexture> iterator = textures.iterator(); iterator.hasNext(); ) {
				FlashyWrappersTexture targetTexture = iterator.next();
				FWLog.i("disposeTexture " + t + " vs " + targetTexture);
				if(targetTexture.getGLESid() == t.getGLESid()) {
					FWLog.i("Removing texture " + t.getGLESid() + "...");
					t._shouldDie = true;
					instance.GLESComposer.onTextureDispose(t);
					//dieTextures.add(t);
					// TODO synchronize with WindowsRecorder capturing so we're not interrupting it
					// when disposing texture	
					final FlashyWrappersTexture tF = t;
					/*ExecutorService executor = Executors.newSingleThreadExecutor();
					synchronized (FlashyWrappersGLESVideoComposer.GLESLock) {				
						Future<Boolean> result = executor.submit(new Callable<Boolean>() {
							public Boolean call() throws Exception {
								tF.dispose();
								return true;				        
							}
						});
					}*/
					tF.dispose();
					iterator.remove();
					FWLog.i("Done");
					return;
				}
			}
		}
        FWLog.i("Warning! Texture " + t.getGLESid() + " couldn't be freed because GLES id wasn't found in any of the textures. Was it removed already?");
	}

	public void dispose() throws Exception {
		// stop all components if needed
		if (instance.GLESRecorder.state == "started") {
			instance.GLESRecorder.stop();
		}
		instance.GLESRecorder = null;
		
		if (instance.GLESComposer.state == "started") {
			instance.GLESComposer.stop();
		}
		
		instance.GLESComposer = null;
		
		if (instance.WindowsRecorder.state == "started") {
			instance.WindowsRecorder.stop();				
		}
		
		instance.WindowsRecorder = null;
		
		// dispose our textures
		disposeTextures();
	}
}
