package com.rainbowcreatures.FlashyWrappersAndroidHW;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.view.Display;

/**
 * This is FW texture class used for either being attached to FBO and rendered into, 
 * or it is rendered back to screen as the game loses rendering.
 * 
 * Also it can be used for WindowsRecorder to render into it.
 * 
 * @author Pavel
 *
 */
public class FlashyWrappersTexture {	

	/**
	 * Normal OpenGL texture with standard shader
	 */
	public static int NORMAL = 0;
	
	/**
	 * GL_TEXTURE_EXTERNAL_OES, this one is used for capturing software Views into OpenGL, uses specialized shader.
	 */
	public static int EXTERNAL = 1;
	
	/**
	 * Indicates the texture will contain ByteArray from AIR in ARGB format(and upside down), uses specialized shader.
	 */
	public static int AIR_BYTEARRAY = 2;

	int screenWidth = 0;
	int screenHeight = 0;	
	int requestedWidth = 0;
	int requestedHeight = 0;
	int width = 0;
	int height = 0;
	float U = 0;
	float V = 0;	
	float x = 0;
	float y = 0;
	int GLESid = -1;
	int GLESvertexBuffer = -1;
	int GLESindexBuffer = -1;
	int GLESpositionSlot = -1;
	int GLEScolorSlot = -1;
	int GLEStexCoordSlot = -1;
	int GLEStextureUniform = -1;
	public String rootView = "";
	private int _type = 0;
	public volatile boolean _shouldDie = false;
	
	// ordering of the texture in the display list
	public int Z = 0;

	int[] params = new int[1];

	short indices[] = {0, 1, 2,
			2, 3, 0};

	float[] Vertices = {
			1, -1, 0,  1, 0, 0, 1,  1, 0,
			1, 1, 0,  0, 1, 0, 1,  1, 1,
			-1, 1, 0,  0, 0, 1, 1,  0, 1,
			-1, -1, 0,  0, 0, 0, 1,  0, 0
	};
	
	// Floating-point buffer
	FloatBuffer VerticesBuffer;
	ShortBuffer indicesBuffer;
	
	FlashyWrappersWrapper _wrapper = null;

	/**
	 * @param wrapper the FW wrapper reference
	 * @param w width of the texture, might be non-POT, will be converted to POT
	 * @param h height of the texture, might be non-POT, will be converted to POT
	 * @param initTextureToBlack init texture to black?
	 * @param type is the type of texture, use one of NORMAL, EXTERNAL, AIR_BYTEARRAY
	 * @throws Exception
	 */
	public FlashyWrappersTexture(int w, int h, boolean initTextureToBlack, int type) throws Exception {
		_type = type;
		_wrapper = FlashyWrappersWrapper.instance();
		requestedWidth = w;
		requestedHeight = h;
		width = nextPow2(requestedWidth);
		height = nextPow2(requestedHeight);
		
		// Setup memory for vertices and indices of this texture
		
		// Allocate a direct block of memory on the native heap,
		VerticesBuffer = ByteBuffer.allocateDirect(Vertices.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();

		// Copy data from the Java heap to the native heap and reset buffer position to 0.
		VerticesBuffer.put(Vertices).position(0);
		
		updateScreenDimensions(_wrapper.GLESComposer.displayWidth, _wrapper.GLESComposer.displayHeight);
		updateVertices();		
		
		// Allocate a direct block of memory on the native heap,
		indicesBuffer = ByteBuffer.allocateDirect(indices.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();

		// Copy data from the Java heap to the native heap and reset buffer position to 0.
		indicesBuffer.put(indices).position(0);

		// setup GLES stuff for this texture

		// Create vertex and index buffers
		GLES20.glGenBuffers(1, params, 0);
		GLESvertexBuffer = params[0];
		GLES20.glGenBuffers(1, params, 0);
		GLESindexBuffer = params[0];
		
		// finish the GLES texture setup
		// Create a texture object and bind it.  This will be the color buffer.
		FWLog.i("Generating GLES texture, requested dimensions " + requestedWidth + "x" + requestedHeight + " actual dimensions (" + width + " x " + height + "), U: " + U + " V: " + V);

		GLES20.glGenTextures(1, params, 0);
		GlUtil.checkGlError("glGenTextures");
		setGLESid(params[0]);   // expected > 0
		
		if (_type != EXTERNAL) {
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, GLESid);
		} else {
			GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLESid);			
		}
		GlUtil.checkGlError("glBindTexture " + GLESid);
		
		// Create texture storage.
		GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
		GlUtil.checkGlError("glTexParameter1");

/*		if (initTextureToBlack) {
			// TODO unsure if this should also be only TEXTURE_2D but since we're not using it now leaving it alone...
			GLES20.glCopyTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 0, 0, width, height, 0);
			GlUtil.checkGlError("glTexParameter2");
		}*/
		
		ByteBuffer bb = ByteBuffer.allocate(width * height * 4);
		
		// clear texture
		if (_type != EXTERNAL) {		
			byte[] pixels = new byte[width * height * 4];
			for (int a = 0; a < width * height; a += 4) {
				pixels[a] = 0;
				pixels[a + 1] = 0;
				pixels[a + 2] = 0;
				pixels[a + 3] = 0;
			}	
			bb.position(0);
			bb.put(pixels);
			bb.position(0);
			GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bb);
			pixels = null;
		}

		if (_type != EXTERNAL) {
			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
			GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
		} else {
			GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
			GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
		}
		GlUtil.checkGlError("glTexParameter3");		
		FWLog.i("Successfully created GLES texture " + GLESid);
	}
	
	/**
	 * Set position of texture
	 * 
	 * @param x
	 * @param y
	 */
	public void setXY(float newX, float newY) {
		this.x = newX;
		this.y = newY;
		FWLog.i("setXY: " + x + ", " + y);
	}
	
	public void updateScreenDimensions(int w, int h) {
		screenWidth = w;
		screenHeight = h;
	}
	
	/**
	 * Updates vertices and UV of this texture based on its X,Y position
	 * and texture type (the UV's might be flipped upside down).
	 */
	public void updateVertices() {

		U = (float)requestedWidth / (float)width;
		V = (float)requestedHeight / (float)height;

/*		1, -1, 0,  1, 0, 0, 1,  1, 0,
		1, 1, 0,  0, 1, 0, 1,  1, 1,
		-1, 1, 0,  0, 0, 1, 1,  0, 1,
		-1, -1, 0,  0, 0, 0, 1,  0, 0*/

		if (_type == NORMAL) {
			// modify the vertices to match the U / V 
			Vertices[(9 * 0) + 7] = U;
			Vertices[(9 * 1) + 7] = U;
			Vertices[(9 * 1) + 8] = V;
			Vertices[(9 * 2) + 8] = V;
		} else {
			// external texture needs to be flipped so we just change UV coords(hopefully)
			Vertices[(9 * 0) + 7] = U;
			Vertices[(9 * 0) + 8] = V;
			Vertices[(9 * 1) + 7] = U;
			Vertices[(9 * 1) + 8] = 0;
			Vertices[(9 * 2) + 7] = 0;			
			Vertices[(9 * 2) + 8] = 0;
			Vertices[(9 * 3) + 7] = 0;
			Vertices[(9 * 3) + 8] = V;
		}
						
		float xPos = (((float)this.x / (float)(screenWidth)) * 2) - 1;
		float yPos = ( ( ((float)this.y / (float)(screenHeight))) * 2) - 1;		
		float textureWidth = (((float)(requestedWidth + this.x) / (float)(screenWidth)) * 2) - 1;
		float textureHeight = ( ( ((float)(requestedHeight + this.y) / (float)(screenHeight))) * 2) - 1;
		
		Vertices[(9 * 0) + 0] = textureWidth;
		Vertices[(9 * 0) + 1] = yPos;
		Vertices[(9 * 1) + 0] = textureWidth;
		Vertices[(9 * 1) + 1] = textureHeight;		
		Vertices[(9 * 2) + 0] = xPos;
		Vertices[(9 * 2) + 1] = textureHeight;
		Vertices[(9 * 3) + 0] = xPos;
		Vertices[(9 * 3) + 1] = yPos;
		
		// Copy data from the Java heap to the native heap and reset buffer position to 0.
		VerticesBuffer.clear();		
		VerticesBuffer.put(Vertices).position(0);
		FWLog.i("Texture coords X1: " + xPos + " Y1: " + yPos + " X2: " + textureWidth + "Y2: " + textureHeight + " screen " + screenWidth + " x " + screenHeight);		
	}
	
	
	/**
	 * Get texture type
	 * 
	 * @return returns NORMAL, EXTERNAL or AIR_BYTEARRAY
	 */
	public int getType() {
		return _type;
	}
	
			
	/**
	 * Render the texture to current GLES context
	 * 
	 * @throws Exception
	 */
	public void render() throws Exception {
		if (GLESid > -1) {
			GlUtil.checkGlError("render texture 0");
			int oldProgram[] = new int[1];
			int oldArrayBuffer[] = new int[1];
			int oldElementArrayBuffer[] = new int[1];				
			GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, oldProgram, 0);
			GLES20.glGetIntegerv(GLES20.GL_ARRAY_BUFFER_BINDING, oldArrayBuffer, 0);
			GLES20.glGetIntegerv(GLES20.GL_ELEMENT_ARRAY_BUFFER_BINDING, oldElementArrayBuffer, 0);
			if (_type == NORMAL) { 
				GLES20.glUseProgram(_wrapper.programRenderTexture);
			} else {
				if (_type == EXTERNAL) {
					GLES20.glUseProgram(_wrapper.programRenderExternalTexture);
				} else {
					if (_type == AIR_BYTEARRAY) {
						GLES20.glUseProgram(_wrapper.programARGB);
					}
				}
			}
			GlUtil.checkGlError("render texture 1");
			/*GLESpositionSlot = GLES20.glGetAttribLocation(_wrapper.programRenderTexture, "Position");
			GLEScolorSlot = GLES20.glGetAttribLocation(_wrapper.programRenderTexture, "SourceColor");
			GLEStexCoordSlot = GLES20.glGetAttribLocation(_wrapper.programRenderTexture, "TexCoordIn");*/
			GLESpositionSlot = 0;
			GLEScolorSlot = 1;
			GLEStexCoordSlot = 2;
			GLEStextureUniform = GLES20.glGetUniformLocation(_wrapper.programRenderTexture, "Texture");
			
			if (FWLog.VERBOSE) FWLog.i("(2)GLES Position: " + GLESpositionSlot + ", Color: " + GLEScolorSlot + " TexCoord: " + GLEStexCoordSlot + " TexUniform: " + GLEStextureUniform);			
			
			GlUtil.checkGlError("render texture 1b");			
			GLES20.glEnableVertexAttribArray(GLESpositionSlot);
			GLES20.glEnableVertexAttribArray(GLEScolorSlot);
			GLES20.glEnableVertexAttribArray(GLEStexCoordSlot);
			GlUtil.checkGlError("render texture 1c");						
			GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, GLESvertexBuffer);            
			GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, VerticesBuffer.capacity() * 4, VerticesBuffer, GLES20.GL_STATIC_DRAW);            
			GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, GLESindexBuffer);
			GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer.capacity() * 2, indicesBuffer, GLES20.GL_STATIC_DRAW);
			GlUtil.checkGlError("render texture 1d");									
			GLES20.glVertexAttribPointer(GLESpositionSlot, 3, GLES20.GL_FLOAT, false, 9 * 4, 0);
			GLES20.glVertexAttribPointer(GLEScolorSlot, 4, GLES20.GL_FLOAT, false, 9 * 4, 4 * 3);
			GLES20.glVertexAttribPointer(GLEStexCoordSlot, 2, GLES20.GL_FLOAT, false, 9 * 4, 4 * 7);
			GlUtil.checkGlError("render texture 1e");												
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			GlUtil.checkGlError("render texture 2");
			if (_type != EXTERNAL) {
				GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, GLESid);
			} else {				
				GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLESid);
			}
			GlUtil.checkGlError("render texture 3");
			
			GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
			GLES20.glEnable(GLES20.GL_BLEND);
			
			GLES20.glUniform1i(GLEStextureUniform, 0);
			GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, 0);
			if (_type != EXTERNAL) {
				GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
			} else {				
				GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
			}
			GlUtil.checkGlError("render texture 4");
			GLES20.glDisableVertexAttribArray(GLESpositionSlot);
			GLES20.glDisableVertexAttribArray(GLEScolorSlot);
			GLES20.glDisableVertexAttribArray(GLEStexCoordSlot);
			GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, oldElementArrayBuffer[0]);
			GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, oldArrayBuffer[0]);
			GLES20.glUseProgram(oldProgram[0]);						
			GlUtil.checkGlError("render texture 5");
		}
	}
	
	
	
	private int nextPow2(int v)
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

	private void setGLESid(int id) {
		GLESid = id;
	}
	
	/**
	 * Get the GLES id of the texture for the current context
	 * 
	 * @return
	 */
	public int getGLESid() {
		return GLESid;
	}
	
	public int getWidth() {
		return width;
	}
	
	public int getHeight() {
		return height;
	}

	public int getRequestedWidth() {
		return requestedWidth;
	}
	
	public int getRequestedHeight() {
		return requestedHeight;
	}

	/**
	 * This shouldn't be called directly, but through FlashyWrappersWrapper.disposeTexture()
	 * There is additional layer of thread safe disposing the texture done through FW disposeTexture.
	 * 
	 */
	public void dispose() {
		FWLog.i("Freeing texture");		
		// delete vertex buffer
		params[0] = GLESvertexBuffer;
		GLES20.glDeleteBuffers(1, params, 0);
		// delete index buffer
		params[0] = GLESindexBuffer;    	
		GLES20.glDeleteBuffers(1, params, 0);
		// delete texture 
		params[0] = GLESid;		
		GLES20.glDeleteTextures(1, params, 0);		
	}	
}
