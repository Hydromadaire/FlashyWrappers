/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * iOS / OS X port of FlashyWrappers. This was planned to be refactored similar to Windows where the encoder is in separate class, but never managed to get to it - feel free!
 * Whats really missing is the ability to record audio from AIR (in general recording audio was an issue) there might be dead code here which attempts that.
 * Also, iOS ReplayKit framework support was almost ready but then because AIR SDK didn't support the latest iOS SDK it couldn't be compiled so its untested. 
 *
 */

#ifndef encoder_iOS_ANE_ANEHelloWorld_h
#define encoder_iOS_ANE_ANEHelloWorld_h

#import "FlashRuntimeExtensions.h"

#if TARGET_OS_IPHONE
#include <OpenGLES/ES2/gl.h>
#include <OpenGLES/ES2/glext.h>
#else
#include <OpenGL/gl.h>
#include <OpenGL/glext.h>
#include "Foundation/Foundation.h"
#endif

extern "C" {
    
    __attribute__((visibility("default"))) void ExtInitializer(void** extDataToSet, FREContextInitializer* ctxInitializerToSet, FREContextFinalizer* ctxFinalizerToSet);  
    
    __attribute__((visibility("default"))) void ExtFinalizer(void* extData);
    
    
    void ContextFinalizer(FREContext ctx);
    
    void ContextInitializer(void *extData, const uint8_t* ctxType, FREContext ctx, uint32_t* numFunctions, const FRENamedFunction** functions);
     
    void MyPixelBufferReleaseCallback(void *releaseRefCon, const void *baseAddress);
    
    void blitLogo(unsigned char *dest, int dest_w, int w, int h);
    
    void encode_it();
    
    void addAudioFrame(unsigned char *data, long audio_len, double timestamp);
    
    bool try_flushAndThread();
    
    void compileShaders();
    
    bool flush_intermediate_frames();
    
    void *runencoderthread(void *param);
    
    GLuint compileShader(const char* shaderStringUTF8, GLenum shaderType);
    
    bool canFinish();
    
    void appendPixelBuffer(bool glFinishBool);
    
    int nextPow2(int v);
    
    void throwError(NSString *text, NSError *error);
    
    void FWLog(NSString *str, ...);
    
// start, create the instance class
FREObject fw_ffmpeg_create(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

// start, free the instance class
FREObject fw_ffmpeg_free(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

FREObject fw_ffmpeg_init(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

FREObject fw_ffmpeg_addVideoFrame(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    
FREObject fw_ffmpeg_addAudioFrame(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    
FREObject fw_ffmpeg_setFrames(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

FREObject fw_ffmpeg_getStream(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

FREObject fw_ffmpeg_getStreamSize(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

FREObject fw_ffmpeg_canFinish(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    
FREObject fw_ffmpeg_getVideoFramesSent(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);   
    
FREObject fw_saveToCameraRoll(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);  

FREObject fw_captureFrame(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);  
    
FREObject fw_bindFlashFBO(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    
FREObject fw_addAudioMix(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    
FREObject fw_stopAudioMix(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    
FREObject fw_finish(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

FREObject fw_getExportProgress(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    
FREObject fw_exportCancel(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    
FREObject fw_setLogging(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);    
    
FREObject fw_captureFullscreenGPU(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    
FREObject fw_setPTSMode(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    
FREObject fw_setFramedropMode(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

FREObject fw_setHighresRecording(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    
FREObject fw_setCaptureRectangle(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    
#if TARGET_OS_IPHONE
    FREObject fw_ReplayKitAvailable(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    FREObject fw_ReplayKitStart(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    FREObject fw_ReplayKitIsRecording(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    FREObject fw_ReplayKitStop(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    FREObject fw_ReplayKitDiscard(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
    FREObject fw_ReplayKitPreview(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
#endif
}

FREObject fw_processByteArrayAudio(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
#endif
