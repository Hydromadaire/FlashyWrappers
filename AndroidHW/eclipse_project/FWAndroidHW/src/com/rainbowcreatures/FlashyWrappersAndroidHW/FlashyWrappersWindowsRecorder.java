package com.rainbowcreatures.FlashyWrappersAndroidHW;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Environment;
import android.util.Log;
import android.view.Choreographer;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

// records Windows 

public class FlashyWrappersWindowsRecorder {
	
	FlashyWrappersWrapper _wrapper = null;
	ArrayList<ViewWithTexture> viewsWithTextures = new ArrayList<ViewWithTexture>();
	private Choreographer ch;
	long nextCapture = 0;
	private FlashyWrappersFrameCallback frameCallback;
	private volatile boolean taskDone = false;
	private volatile boolean updatingRootViews = false;
	public volatile String state = "";
	
	// we do not want to capture this
	private View gameSurfaceView = null;
	
	class ViewWithTexture {
		
		public View view = null;
		private FlashyWrappersTexture texture = null;
		public boolean valid = false;
		public String viewName = "";
		public SurfaceTexture surfaceTexture = null;
		public Surface surface = null;
		
		void ViewWithTexture() {
			
		}
		
		public FlashyWrappersTexture getTexture() {
			return texture;
		}
		
		public void dispose() {
			// release surface, surfacetexture and FW texture connected to this surface
			surfaceTexture.release();
			surface.release();
			_wrapper.disposeTexture(texture);
		}
		
		/**
		 * Assign GLES texture, this automatically creates both surface and surfaceTexture
		 * for rendering the view
		 * 
		 * @param t
		 */
		
		public void setGLESTexture(FlashyWrappersTexture t) {
			texture = t;			
			surfaceTexture = new SurfaceTexture(t.getGLESid());
			surfaceTexture.setDefaultBufferSize(t.getWidth(), t.getHeight());
			surface = new Surface(surfaceTexture);
		}
	}
	
	// save window layout, start tree observers monitoring all views every second or something
	// (also, swap to FW's context, setup textures for saving window(s), then create surface(s) with SurfaceTexture(s), using GLES texture(s)).
	public FlashyWrappersWindowsRecorder() {
		_wrapper = FlashyWrappersWrapper.instance();
	}
	
	
	/**
	 * This will start the windows recorder, it needs Choreographer
	 * with thread containing Looper to call the frame callback 
	 * 
	 * @param ch
	 */
	public void start(Choreographer cho) throws Exception {
		// update views once
		if (FlashyWrappersWrapper.instance().GLESComposer.state != "started") throw new Exception("You need to start GLESComposer before starting WindowsRecorder! The recorder needs the composer to be able to target the recording somewhere.");
		updateRootViews();
		ch = cho;
		frameCallback = new FlashyWrappersFrameCallback(this, ch);		
		ch.postFrameCallback(frameCallback);
		state = "started";
	}
	
	/**
	 * Stop the windows recorder - most notably removes the choreographer callback
	 * 
	 */
	public void stop() {		
		ch = null;
		frameCallback.stop();
        for (Iterator<ViewWithTexture> iterator = viewsWithTextures.iterator(); iterator.hasNext(); ) {
        	ViewWithTexture vt = iterator.next();
       		FWLog.i("Removing " + vt.viewName + "...");
       		iterator.remove();
       		vt.dispose();            		       		
        }
		state = "";
	}
	
	private ViewWithTexture isViewWithTexture(String viewName) throws Exception {
		for (int a = 0; a < viewsWithTextures.size(); a++) {
			// texture for this view already exists, just return that
			if (viewsWithTextures.get(a).viewName.equals(viewName)) {
				return viewsWithTextures.get(a);
			}
		}		
		return null;		
	}
	
	private ViewWithTexture createViewWithTexture(View v, String viewName) throws Exception {
		ViewWithTexture vt = new ViewWithTexture();
		int width = 0;
		int height = 0;
		width = v.getWidth();
		height = v.getHeight();
		if (width == 0 || height == 0) {
			FWLog.i("View returned 0x0 dimensions, will try display dimensions.");
			width = _wrapper.GLESComposer.displayWidth;
			height = _wrapper.GLESComposer.displayHeight;
		}
		FlashyWrappersTexture texture = _wrapper.createTexture(width, height, false, FlashyWrappersTexture.EXTERNAL);
		vt.viewName = viewName;
		vt.view = v;
		int[] loc = new int[2];
		v.getLocationOnScreen(loc);		
		texture.setXY(loc[0], loc[1]);
		vt.setGLESTexture(texture);
		vt.valid = true;
		viewsWithTextures.add(vt);
		return vt;
	}
	
	private void invalidateViewsWithTextures() {
		for (int a = 0; a < viewsWithTextures.size(); a++) {
			viewsWithTextures.get(a).valid = false;
		}
	}
	
	/**
	 * Find the root views of all app windows
	 * 
	 */
	public void updateRootViews() {
		updatingRootViews = true;		
		try {
			Class wmgClass = Class.forName("android.view.WindowManagerGlobal");                        
			Object wmgInstnace = wmgClass.getMethod("getInstance").invoke(null, (Object[])null);
			Method getViewRootNames = wmgClass.getMethod("getViewRootNames"); 
			Method getRootView = wmgClass.getMethod("getRootView", String.class);
			String[] rootViewNames = (String[])getViewRootNames.invoke(wmgInstnace, (Object[])null);			
			invalidateViewsWithTextures();
			if (FWLog.VERBOSE) FWLog.i("Updating windows...");
			int viewId = 1;
            for (String viewName : rootViewNames) {
            	// just an ugly hack for now to not capture the bottom view
            	// which is GLES view with game, which would result in capturing
            	// a completely black texture over the whole game, making the game yeah, invisible, whichkindofdefeatsthewholepurposeofthis...
            	//if (viewId > 0) {            	
            		final View rootView = (View)getRootView.invoke(wmgInstnace, viewName);            		
            		final String viewNameF = viewName;
            		final int viewIdF = viewId;
            		ViewWithTexture vt = null;            		
            		if ((vt = isViewWithTexture(viewName)) != null) {
            			vt.valid = true;
            			FWLog.i(viewName + "(" + viewId + ") exists, validating...");
            		} else {
            			FWLog.i(viewName + "(" + viewId + ") doesn't exist, creating texture...");
            			final Runnable task = new Runnable() {
            				@Override
            				public void run() {
            					FWLog.i("Runnable for creating ViewWithTexture started...");
            					synchronized(FlashyWrappersGLESVideoComposer.GLESLock) {
            						FWLog.i("Runnable for creating ViewWithTexture got into synchronized code...");
            						try {
            							ViewWithTexture vt = createViewWithTexture(rootView, viewNameF);
            							if (vt != null) {
            								vt.getTexture().Z = viewIdF;
            							}
            							taskDone = true;
            							FlashyWrappersGLESVideoComposer.GLESLock.notifyAll();
            						} catch (Exception e) {
									// 	TODO Auto-generated catch block
            							taskDone = true;
            							FlashyWrappersGLESVideoComposer.GLESLock.notifyAll();
            							e.printStackTrace();
            						}
            					}
            				}
            			};  
            			_wrapper.GLESComposer.composerRunnable.handler.post(task);
            			synchronized(FlashyWrappersGLESVideoComposer.GLESLock) {
            				while (!taskDone) {
        						FWLog.i("Waiting for creating texture to finish...");
        						FlashyWrappersGLESVideoComposer.GLESLock.wait();            				
            				}
            			}
            			taskDone = false;
            		}
            	//}
    			viewId++;
            }            
            if (FWLog.VERBOSE) FWLog.i("Wiping all invalid window/texture pairs...");
            // wipe all invalid view/texture pairs            
            for (Iterator<ViewWithTexture> iterator = viewsWithTextures.iterator(); iterator.hasNext(); ) {
            	ViewWithTexture vt = iterator.next();
            	if(!vt.valid){
            		FWLog.i("Removing " + vt.viewName + "...");
            		iterator.remove();
            		vt.dispose();            		            		
            	}
            }
            updatingRootViews = false;            
        } catch (Exception e) {
            // Several error may come out with file handling or OOM
            e.printStackTrace();
        }
	}
		
	/**
	 * capture all windows into their respective GLES textures
	 * this is called from choreographer callback automatically so shouldn't be really 
	 * called manually.
	 * 
	 * @param intervalMs the interval of the capture, keep it at something like 1000ms for testing
	 * @throws Exception
	 */
	
	public void captureWindowsFrame(int intervalMs) throws Exception {
		// do not mess with this in case we're still didn't update root views fully
		// this might happen in another thread so we might be in the middle of update
		// while capturing a frame
		if (updatingRootViews) return;
		
		// capture in interval		
		if (System.currentTimeMillis() >= nextCapture) {
			nextCapture = System.currentTimeMillis() + intervalMs;			
			// TODO ugly, this should update only when there are new windows added / removed(but its imposible or hard to find out on Android)
//			updateRootViews();			
			if (FWLog.VERBOSE) FWLog.i("Capturing windows...");
			//_wrapper.GLESComposer.startCacheBatch();

			// sync on texture delete lock
			synchronized (_wrapper.textureDeleteLock) {		

				for (int a = 0; a < viewsWithTextures.size(); a++) {
					// get root view
					FWLog.i("Capturing window " + a + "(" + viewsWithTextures.get(a).viewName + ")");
					final ViewWithTexture vt = viewsWithTextures.get(a);
					View v1 = vt.view;

					// draw the whole root view again, this should end up in Surface->SurfaceTexture->OpenGL texture
					// and be rendered by GLESVideoComposer
					final Canvas surfaceCanvas = vt.surface.lockCanvas(null);				
					// for window 0 ignore all surface views
					if (a == 0) {
						// clear canvas first
						/*Paint paint = new Paint();
					paint.setXfermode(new PorterDuffXfermode(Mode.CLEAR));
					surfaceCanvas.drawPaint(paint);
					paint.setXfermode(new PorterDuffXfermode(Mode.SRC));*/
						Paint myPaint = new Paint();
						myPaint.setColor(Color.argb(128, 255, 0, 0));
						//myPaint.setStrokeWidth(10);
						//myPaint.setXfermode(new PorterDuffXfermode(Mode.CLEAR));
						myPaint.setXfermode(new PorterDuffXfermode(Mode.CLEAR));					
						surfaceCanvas.drawRect(0, 0, surfaceCanvas.getWidth(), surfaceCanvas.getHeight(), myPaint);
						myPaint.setXfermode(new PorterDuffXfermode(Mode.SRC));
						// TODO draw bottom to top, now its probably drawing in wrong direction
						ViewViewer.drawWithoutSurfaceView(v1, surfaceCanvas);
					} else {
						v1.draw(surfaceCanvas);
					}
					vt.surface.unlockCanvasAndPost( surfaceCanvas );

					// as usual, when posting to existing OpenGL context, we must
					// sync on the global GLES lock to not mess with any other threads
					// accessing the context at the same time. 
					final Runnable task = new Runnable() {
						@Override
						public void run() {
							synchronized(FlashyWrappersGLESVideoComposer.GLESLock) {
								vt.surfaceTexture.updateTexImage();
								taskDone = true;
								FlashyWrappersGLESVideoComposer.GLESLock.notifyAll();
							}
						}
					};

					// post to GLES composer OpenGL thread
					_wrapper.GLESComposer.composerRunnable.handler.post(task);				

					synchronized(FlashyWrappersGLESVideoComposer.GLESLock) {
						while (!taskDone) {
							FWLog.i("(WindowsRecorder) Waiting for caching to finish...");
							FlashyWrappersGLESVideoComposer.GLESLock.wait();
						}
					}

					taskDone = false;

					_wrapper.GLESComposer.cacheTexture(vt.getTexture());				
				}
			}
			//_wrapper.GLESComposer.finishCacheBatch();
		}
	}	
}
