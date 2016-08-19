package com.rainbowcreatures.FlashyWrappersAndroidHW;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

/**
 * This record GLES, so it *assumes* there's a valid GLES context available!
 * 
 * @author Pavel
 *
 */
public class FlashyWrappersGLESRecorder {

	private int width = 0;
	private int height = 0;
	private boolean firstFrame = true;
	private boolean allowRecord = true;
	public volatile String state = "";
	
	// texture backed FBO
	private int GLESFBOid = -1;
	private int GLESOldFBOid = -1;	
	int GLESdepthBuffer[] = new int[1];
	int GLESstencilBuffer[] = new int[1];	

	public boolean behaviorLateInit = true;
	public boolean behaviorFBO0AfterInit = false;
	public boolean behaviorDepthAndStencil = true;
	public boolean behaviorTextureInit = false;
	public boolean behaviorSkipFramesWhenCaching = false;
	
	FlashyWrappersWrapper _wrapper = null;
	FlashyWrappersTexture mTexture = null;
	
	int[] params = new int[1];

	public FlashyWrappersGLESRecorder() {
		_wrapper = FlashyWrappersWrapper.instance();				
	}
	
	/**
	 * Measure testing rectangle to get the coordinates measurements from source coordinate system
	 * to GL coordinate system
	 * 
	 * The start basically does what GL recorder does normally but in a "lite" version. It binds FBO,
	 * attaches texture to it
	 * 
	 * @throws Exception
	 */
	public void measureRectStart() throws Exception {
		int[] viewport = new int[4];
		int color = _wrapper.GLESComposer.getCaptureRectColor();
		int targetA = (color & 0xFF000000) >> 24;
		int targetR = (color & 0x00FF0000) >> 16;
		int targetG = (color & 0x0000FF00) >> 8;
		int targetB = (color & 0x000000FF);
		FWLog.i("Target A " + targetA + " R " + targetR + " G " + targetG + " B " + targetB);
		// Configure texture backed FBO for offscreen AIR rendering
		FWLog.i("GLESRecorder starting measuring...");
		GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0);
		width = viewport[2];
		height = viewport[3];

		if (width == 0 || height == 0) throw new Exception("GLESRecorder issue, viewport dimensions are 0. Make sure you're calling createAndBindFBO() from valid GLES context!");
		FWLog.i("Currently, viewport is ( " + viewport[2] + " x " + viewport[3] + ")");             

	    byte pixelsBuffer[] = new byte[width * height * 4];
	    ByteBuffer ib = ByteBuffer.wrap(pixelsBuffer);
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, ib);	    
        
	    int oldR = 0;
	    int oldG = 0;
	    int oldB = 0;
	    int startX = -1;
	    int startY = -1;
	    int endX = -1;
	    int endY = -1;
	    int x = 0;
	    int y = 0;
	    for (int a = 0; a < (width * height * 4); a += 4) {
	    		int R = pixelsBuffer[a] & 0xFF;
	    		int G = pixelsBuffer[a + 1] & 0xFF;
	    		int B = pixelsBuffer[a + 2] & 0xFF;
	    		// start of measurement
	    		if (endX == -1 && endY == -1 && R == targetR && G == targetG && B == targetB) {
	    			endX = x;
	    			endY = height - 1 - y;
	    			FWLog.i("Pixel found at " + x + ", " + y);
	    		} else {
	    			if (startX == -1 && startY == -1 && R == targetR && G == targetG && B == targetB) {
	    				if (Math.abs(x - endX) > 5 && Math.abs((height - 1 - y) - endY) > 5) {
		    				startX = x;
		    				startY = height - 1 - y;	
		    				FWLog.i("Pixel found at " + x + ", " + y);
	    				} else {
	    					FWLog.i("Pixel found at " + x + ", " + y + ", but it seemed too close so ignoring it.");
	    				}
	    			}
	    		}
	    		x++;
	    		if (x >= width) {
	    			x = 0;
	    			y++;
	    		}
	    }
	    
	    FWLog.i("Rectangle measured: " + startX + ", " + startY + " - " + endX + ", " + endY);
	    int captureWidth = (Math.round((float)(endX - startX) / 4)) * 4;
	    int captureHeight = (Math.round((float)(endY - startY) / 4)) * 4;
	    FWLog.i("Rectangle modified to multiply of 4: " + captureWidth + " x " + captureHeight);	    
	    _wrapper.GLESComposer.setCaptureRectangle(startX, startY, captureWidth, captureHeight, color, FlashyWrappersGLESVideoComposer.CAPTURERECT_MODE_VISUAL);
	}

	/**
	 * Measure testing rectangle to get the coordinates measurements from source coordinate system
	 * to GL coordinate system
	 * @throws Exception
	 */
	public void measureRectFinish() throws Exception {
		
			
	}

	/**
	 * Create and bind custom FBO, this should be called after there's OpenGL 
	 * context available and it has given width / height but before anything else.
	 * Ideally, your app should think that the FBO we will bind here is the 
	 * default Android's FBO. You should never bind FBO 0 afterwards. Instead remember
	 * the "default" FBO at the app's init and bind that. So when you're not recording, 
	 * the default FBO ID will be 0, when recording, the default FBO ID might be 7002.
	 * 
	 */
	public void start() throws Exception {
		if (FlashyWrappersWrapper.instance().GLESComposer.state != "started") throw new Exception("You need to start GLESComposer before starting GLESRecorder! The recorder needs the composer to be able to target the recording somewhere.");		
		allowRecord = true;
		try {
			firstFrame = true;
			int[] viewport = new int[4];

			// Configure texture backed FBO for offscreen AIR rendering
			FWLog.i("GLESRecorder initializing...");

			GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0);
			width = viewport[2];
			height = viewport[3];

			if (width == 0 || height == 0) throw new Exception("GLESRecorder issue, viewport dimensions are 0. Make sure you're calling createAndBindFBO() from valid GLES context!");
			FWLog.i("Currently, viewport is ( " + viewport[2] + " x " + viewport[3] + ")");             

			GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, params, 0);
			FWLog.i("[GLESRecorder] Currently bound FBO: " + params[0]);    

			// gipsy method of preventing reinit because we didn't stop properly before
			// right now, if stop isn't called from game thread it really won't work properly
			// so we assume nothing was deinitialized
			if (state != "stopped" || _wrapper.getProfile() == FlashyWrappersWrapper.PROFILE_AIR) {
				FWLog.i("[GLESRecorder] Creating custom FBO...");
				GLESOldFBOid = params[0];
				GLES20.glGenFramebuffers(1, params, 0);
				GlUtil.checkGlError("glGenFramebuffers");
				GLESFBOid = params[0];    // expected > 0
				GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLESFBOid);		
				GlUtil.checkGlError("glBindFramebuffer " + GLESFBOid);
				FWLog.i("[GLESRecorder] Created & bound custom FBO: " + GLESFBOid);
				FWLog.i("[GLESRecorder] Creating texture...");

				// create texture GLES recorder will record into
				mTexture = _wrapper.createTexture(width,  height,  behaviorTextureInit, FlashyWrappersTexture.NORMAL);
				mTexture.Z = 0;
				
				FWLog.i("Trying to attach texture to FBO...");

				if (!behaviorLateInit) {			
					setupFBO();
				}

				// 	Switch back to the default framebuffer?
				if (behaviorFBO0AfterInit) {
					GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLESOldFBOid);
					GlUtil.checkGlError("glBindFramebuffer");				
				}
			}
			state = "started";
			
		} catch (Exception e) {
			FWLog.i("GLESRecorder initialization was not OK, recording will be disabled");
			FWLog.i("Trying to recover the display by binding old FBO...");
			GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLESOldFBOid);
			allowRecord = false;
			AIRErrorHandler.handle(e);    		
		}
	}
	
	/**
	 * Setups the recorder texture backed FBO. Add depth and stencil buffers if needed. 
	 * 
	 * @throws Exception
	 */
	private void setupFBO() throws Exception {
		FWLog.i("setupFBO called...");		

		// TODO remember the old render buffer, depth & stencil buffer
		// and re-attach them on STOP.
		
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLESFBOid);
				
		if (behaviorDepthAndStencil) {
			FWLog.i("Creating depth & stencil buffers for FBO...");
			//Create the RenderBuffer for offscreen rendering // Depth
			GLES20.glGenRenderbuffers(1, GLESdepthBuffer, 0);
			GlUtil.checkGlError("glGenRenderbuffers depth");
			GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, GLESdepthBuffer[0]);
			GlUtil.checkGlError("glBindRenderbuffer depth");
			GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, mTexture.getWidth(), mTexture.getHeight());
			GlUtil.checkGlError("glRenderbufferStorage depth");
			//	Create the RenderBuffer for offscreen rendering // Stencil
			GLES20.glGenRenderbuffers(1, GLESstencilBuffer, 0);
			GlUtil.checkGlError("glGenRenderbuffers stencil");
			GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, GLESstencilBuffer[0]);
			GlUtil.checkGlError("glBindRenderbuffer stencil");
			GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_STENCIL_INDEX8, mTexture.getWidth(), mTexture.getHeight());
			GlUtil.checkGlError("glRenderbufferStorage stencil");
			// bind renderbuffers to framebuffer object
			GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, GLESdepthBuffer[0]);
			GlUtil.checkGlError("glFramebufferRenderbuffer depth");
			GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_STENCIL_ATTACHMENT, GLES20.GL_RENDERBUFFER, GLESstencilBuffer[0]);
			GlUtil.checkGlError("glFramebufferRenderbuffer stencil");			    
		}
		
		// detach renderbuffer if any from FBO
		GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER,  0);						
		// detach texture if any from game setup FBO
//		GLES11Ext.glFra
		GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, 0, 0);
		// attach the texture to our FBO			
		//GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0, 0);				
		//GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTexture.getGLESid(), 0);
		GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mTexture.getGLESid(), 0);
		// 	See if GLES is happy with all this.
		int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
		if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
			throw new Exception("Framebuffer not complete, status = " + status);
		}
	}	
	
	/**
	 * Renders our texture which is backing custom FBO to screen.
	 * This should be called just before the app calls swapBuffers
	 * @throws Exception
	 */
	public void renderFBOTextureToScreen() throws Exception {
		if (!allowRecord) return;			
		if (firstFrame) {
			if (behaviorLateInit) {			
				setupFBO();
			}
		}  
		
		// switch to ANDROID's default FBO (that should be in the old FBO id)
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLESOldFBOid);
		GLES20.glClearColor(0, 0, 0, 1);
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);            
		GLES20.glViewport(0, 0, width, height);
		mTexture.render();
				
		// switch back to texture backed FBO so app can render into it on next pass
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLESFBOid);			
		firstFrame = false;					
		
		_wrapper.GLESComposer.cacheTexture(mTexture);
	}
	
	/**
	 * This really doesn't "stop" anything because GLES recorder is only
	 * recording when feeding frames to it, but, it will detach itself 
	 * the custom FBO, free its resources (FBO, texture) and bind back
	 * the "old" FBO.
	 * 
	 */
	
	// TODO there is a problem if we fed the FBO to the game and the game
	// thinks its Android FBO. If we bind back FBO 0 it will still try to operate
	// on the FBO we fed it originally. In that case we shouldn't bind FBO 0 
	// back possibly, but keep the custom FBO attached(we might just bind back
	// any texture and depth & stencil buffers that we detached previously on late init)
	
	public void stop() throws Exception {
		// free depth & stencil
		if (behaviorDepthAndStencil) {
			FWLog.i("[GLESRecorder]Freeing depth & stencil buffers...");
			GLES20.glDeleteRenderbuffers(1, GLESdepthBuffer, 0);
			GLES20.glDeleteRenderbuffers(1, GLESstencilBuffer, 0);
			GlUtil.checkGlError("glDeleteRenderBuffers");
		}
		// delete custom FBO		
		FWLog.i("[GLESRecorder] Freeing custom FBO " + GLESFBOid + " ...");			
		params[0] = GLESFBOid;    	  
		GLES20.glDeleteFramebuffers(1, params, 0);
		GlUtil.checkGlError("glDeleteFramebuffers");
		
		// delete texture
		_wrapper.disposeTexture(mTexture);
		
		// bind old FBO
		FWLog.i("[GLESRecorder] Binding back old FBO " + GLESOldFBOid);
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLESOldFBOid);
		GlUtil.checkGlError("glBindFramebuffer");
		
		state = "stopped";
	}			
}
