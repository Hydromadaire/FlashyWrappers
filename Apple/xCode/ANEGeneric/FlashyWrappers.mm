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

#include <vector>
#include <sstream>
#include <fstream>
#include <boost/atomic.hpp>

#ifdef __cplusplus

extern "C" {
    
#endif 
    
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <iostream>
#include <math.h>
#include <vector>
#include <sys/time.h>

}

#include "FlashyWrappers.h"
//#include "FW_ffmpeg_encode.h"
#include "FW_exception.h"
#include "logo.h"
#include "CoreMedia/CoreMedia.h"
#include "CoreVideo/CoreVideo.h"
#include "AVFoundation/AVFoundation.h"
#if TARGET_OS_IPHONE
//#include "TheAmazingAudioEngine.h"
#include "UIKit/UIKit.h"
#include "AssetsLibrary/AssetsLibrary.h"
#include "Photos/Photos.h"
#else
#include "Appkit/Appkit.h"
#include "Foundation/Foundation.h"
#endif
#include "Accelerate/Accelerate.h"
#include "AudioToolbox/AudioToolbox.h"
#include "FWAudioMix.h"
//#include "AIRReplayKit.h"

#include <cstdlib>

// Encoder class instance
// We're not using the common ffmpeg instance because we're using AVFoundation. In the future 
// some of the stuff should be moved into iOS specific encoderInstance class instead of being a mess inside one .m file
//FW_ffmpeg_encode *encoderInstance = NULL;	

//id<AEAudioReceiver> receiver;

AudioUnit ioUnitInstance;
BOOL remoteRecording = false;

// intermediate buffers to be filled from Flash
std::vector<unsigned char*> videoDataVector_intermediate;
unsigned long videoFramesTotal_intermediate = 0;

// list of audio files we might want to mix in with the video in the end
std::vector<FWAudioMix*> audioMixFiles;

// buffers for encoder to be copied from intermediate buffers
std::vector<unsigned char*> videoDataVector;

boost::atomic_bool threadEncodingRunning;
boost::atomic_bool threadDataAvailable;
bool threadRunning = false;

// capturing in the accelerated "fullscreen" mode? If yes then we will grab directly from OpenGL ES
bool OpenGLCapture = false;
bool forceOpenGLResolution = false;
bool OpenGLCaptureRectangle = false;

int OpenGLCaptureRectX = 0;
int OpenGLCaptureRectY = 0;
int OpenGLCaptureAIRStageWidth = 0;
int OpenGLCaptureAIRStageHeight = 0;

// logging
bool logging = true;
bool verbose = false;

#define PTS_AUTO 0
#define PTS_MONO 1
#define PTS_REALTIME 2

#define FRAMEDROP_AUTO 0
#define FRAMEDROP_OFF 1
#define FRAMEDROP_ON 2

// pts and framedropping modes
uint32_t PTSMode = PTS_AUTO;
uint32_t framedropMode = FRAMEDROP_AUTO;

uint32_t videoFramesSent = 0;
uint32_t videoFramesWritten = 0;
uint32_t videoFramesTotal = 0;
uint32_t intermediate_buffer_length = 0;

// are we reording audio to WAV file (so far keeping it in, the new method seems to have some issues)
uint32_t recordToWAV = 0;

// AIR stage fps, -1 if undetermined
// if stage fps is set, and the desired video fps is also set, in fullscreen mode we'll record only every Nth frame
// this should work in non-fullscreen modes as well in the future
int32_t stage_fps = -1;

// by default record every frame, otherwise this counts down until we actually record a frame
double step = 0;
double delta = 0;
double millisOld = 0;

// accumulated frame steps
double stepAccum = 0;
// the "target" for the step increments, we will save the movie when step is equal or greater than stepTarget (whole number)
double stepTarget = 0;

// MT stuff
double stepMT = 0;
double deltaMT = 0;
double millisOldMT = 0;
double stepAccumMT = 0;
double stepTargetMT = 0;


pthread_t hThread = NULL;

AVAssetWriter *videoWriter;
AVAssetWriterInput* videoWriterInput;
AVAssetWriterInput* audioWriterInput;
AVAssetWriterInputPixelBufferAdaptor *adaptor;
AVAssetExportSession* assetExport = NULL;

// pixel buffer pool will be fed with frames later on
CVPixelBufferRef pixel_buffer = NULL;
CVPixelBufferRef pixel_bufferHighres = NULL;
CVPixelBufferPoolRef pixel_bufferPoolHighres = NULL;

CMFormatDescriptionRef audioFormat = NULL;
AudioStreamBasicDescription asbd = {0};
int32_t foundation_sca_w = 0;
int32_t foundation_sca_h = 0;
uint32_t foundation_fast = 1;
uint32_t foundation_fps = 20;
uint32_t foundation_quality = 2;
uint32_t foundation_bitrate = 96000;
uint32_t foundation_keyframe_interval = 20;
uint32_t foundation_bytesPerRow = 0;
uint32_t foundation_sampleRate = 44100;
uint32_t foundation_noChannels = 2;
uint32_t foundation_audioBitrate = 64000;
uint32_t realtime = 1;
uint32_t forceDimensions = 0;
uint32_t audio = 1;
uint32_t audioCapture = 0;

// time when recording starts in seconds
double recordingStart = 0;
// time when the first video frame is being written, in seconds
double firstFrameTime = 0;

ExtAudioFileRef extAudioFileRef;

// Amazing Audio Engine
#if TARGET_OS_IPHONE
//AEAudioController *audioController = nil;
#endif

#if TARGET_OS_IPHONE
NSString *foundation_native_quality = AVAssetExportPresetHighestQuality;
#else 
NSString *foundation_native_quality = AVAssetExportPresetPassthrough;
#endif
NSError *AVerror = nil;

CVPixelBufferRef buffer = NULL;
int foundation_frameCount = 0;
int foundation_audioFrameCount = -1;

// are timed based PTS enabled for realtime mode?
bool timeBasedPts = true;

// these are variables for the accelerated f directly from AIR (and also for rendering the texture quad for AIR...)
// AIR app views
#if TARGET_OS_IPHONE
UIViewController *rootViewController;
UIView *subview;
#else
NSViewController *rootViewController;
NSView *subview;
#endif

// The FBO texture handle
#if TARGET_OS_IPHONE
CVOpenGLESTextureRef textureRef;
CVOpenGLESTextureRef textureRefHighres;
CVOpenGLESTextureCacheRef textureCacheRef;
CVOpenGLESTextureCacheRef textureCacheHighresRef;
#else 
CVOpenGLTextureRef textureRef;
CVOpenGLTextureRef textureRefHighres;
CVOpenGLTextureCacheRef textureCacheRef;
CVOpenGLTextureCacheRef textureCacheHighresRef;
#endif
GLuint textureCacheFBO;
GLuint textureHighResFBO;
GLuint _positionSlot;
GLuint _colorSlot;
GLuint _texCoordSlot;
GLuint _textureUniform;
GLuint programHandle;

GLuint vertexBuffer;
GLuint indexBuffer;

// AIR's FBO handle
GLuint oldFBO = 0;
GLuint highresFBO = 0;
GLuint videoFBO = 0;

// AIR's render buffer
GLuint AIRRenderBuffer = 0;
GLuint AIRDepthBuffer = -1;
GLuint AIRStencilBuffer = -1;

bool AIRRendered = false;


// AIR stage dimensions and backing texture dimensions
uint16_t stageW = 0;
uint16_t stageH = 0;
uint16_t textureW = 0;
uint16_t textureH = 0;
float_t textureU = 1;
float_t textureV = 1;
float_t scaleFactor = 1;

// Data for our quad rendering the texture with AIR framebuffer
typedef struct {
    float Position[3];
    float Color[4];
    float TexCoord[2];
} Vertex;

Vertex Vertices[] = {
    {{1, -1, 0}, {1, 0, 0, 1}, {1, 0}},
    {{1, 1, 0}, {0, 1, 0, 1}, {1, 1}},
    {{-1, 1, 0}, {0, 0, 1, 1}, {0, 1}},
    {{-1, -1, 0}, {0, 0, 0, 1}, {0, 0}}
};

Vertex VerticesHighRes[] = {
    {{1, -1, 0}, {1, 0, 0, 1}, {1, 0}},
    {{1, 1, 0}, {0, 1, 0, 1}, {1, 1}},
    {{-1, 1, 0}, {0, 0, 1, 1}, {0, 1}},
    {{-1, -1, 0}, {0, 0, 0, 1}, {0, 0}}
};

const GLubyte indices[] = {0, 1, 2,
    2, 3, 0};

// Compile a GLSL shader, utility function
GLuint compileShader(const char* shaderStringUTF8, GLenum shaderType) {
    GLuint shaderHandle = glCreateShader(shaderType);
    int shaderStringLength = strlen(shaderStringUTF8);
    glShaderSource(shaderHandle, 1, &shaderStringUTF8, &shaderStringLength);
    glCompileShader(shaderHandle);
    GLint compileSuccess;
    glGetShaderiv(shaderHandle, GL_COMPILE_STATUS, &compileSuccess);
    if (compileSuccess == GL_FALSE) {
        GLchar messages[256];
        glGetShaderInfoLog(shaderHandle, sizeof(messages), 0, &messages[0]);
        NSString *messageString = [NSString stringWithUTF8String:messages];
        FWLog(@"%@", messageString);
        exit(1);
    }
    return shaderHandle;
}

// Utility function for getting the right texture size for our screen size
int nextPow2(int v)
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

// Logging functions
void FWLog(NSString *str, ...) {
    if (logging) {
        va_list args, args_copy;
        va_start(args, str);
        va_copy(args_copy, args);
        va_end(args);
        NSString *logString = [[NSString alloc] initWithFormat:str arguments:args_copy];
        va_end(args_copy);
        NSLog(@"[FlashyWrappers] %@", logString);
        [logString release];
    }
}

// Compile the inline vertex and fragment shaders
void compileShaders() {
    GLuint vertexShader = compileShader("attribute vec4 Position;attribute vec4 SourceColor;varying vec4 DestinationColor; attribute vec2 TexCoordIn; varying vec2 TexCoordOut; void main(void) {DestinationColor = SourceColor;gl_Position = Position;TexCoordOut = TexCoordIn;}", GL_VERTEX_SHADER);
    //GLuint fragmentShader = [compileShader("varying lowp vec4 DestinationColor;void main(void) {gl_FragColor = DestinationColor;}", GL_FRAGMENT_SHADER);
    
    GLuint fragmentShader = compileShader("varying lowp vec4 DestinationColor; varying lowp vec2 TexCoordOut; uniform sampler2D Texture; void main(void) {gl_FragColor = texture2D(Texture, TexCoordOut);}", GL_FRAGMENT_SHADER);
    programHandle = glCreateProgram();
    glAttachShader(programHandle, vertexShader);
    glAttachShader(programHandle, fragmentShader);
    glLinkProgram(programHandle);
    
    GLint linkSuccess;
    glGetProgramiv(programHandle, GL_LINK_STATUS, &linkSuccess);
    if (linkSuccess == GL_FALSE) {
        GLchar messages[256];
        glGetProgramInfoLog(programHandle, sizeof(messages), 0, &messages[0]);
        NSString *messageString = [NSString stringWithUTF8String:messages];
        FWLog(messageString);
        exit(1);        
    }
    
}

// blit the logo (supports alpha blending now)
// flipped version forcaptureFullscreen
void blitLogoFlipped(unsigned char *dest, int dest_w, int w, int h) {
    int y = 0;
    int x = 0;
    for (y = 0; y < h; y++) {
        long y_w = (h - 1 - y) * w * 4;
        long y_dest_w = (y + (foundation_sca_h - h)) * dest_w * 4;
        for (x = 0; x < w; x++) {
            long x_4 = x * 4;
            //				memcpy(dest + (y * dest_w * 4), logo + (y * w * 4), w * 4);
            unsigned char foregroundAlpha = logo[(y_w) + (x_4)];
            unsigned char foregroundRed =   logo[(y_w) + (x_4) + 1];
            unsigned char foregroundGreen = logo[(y_w) + (x_4) + 2];
            unsigned char foregroundBlue =  logo[(y_w) + (x_4) + 3];
            unsigned char backgroundRed =   dest[(y_dest_w) + (x_4) + 2];
            unsigned char backgroundGreen = dest[(y_dest_w) + (x_4) + 1];
            unsigned char backgroundBlue =  dest[(y_dest_w) + (x_4)];
            unsigned char r = ((foregroundRed * foregroundAlpha) + (backgroundRed * (255 - foregroundAlpha))) >> 8;
            unsigned char g = ((foregroundGreen * foregroundAlpha) + (backgroundGreen * (255 - foregroundAlpha))) >> 8;
            unsigned char b = ((foregroundBlue * foregroundAlpha) + (backgroundBlue * (255 - foregroundAlpha))) >> 8;
            dest[(y_dest_w) + (x_4)] = b;
            dest[(y_dest_w) + (x_4) + 1] = g;
            dest[(y_dest_w) + (x_4) + 2] = r;
        }
    }
}

// normal version for captureMC
void blitLogo(unsigned char *dest, int dest_w, int w, int h) {
    int y = 0;
    int x = 0;
    for (y = 0; y < h; y++) {
        long y_w = y * w * 4;
        long y_dest_w = y * dest_w * 4;
        for (x = 0; x < w; x++) {
            long x_4 = x * 4;
            //				memcpy(dest + (y * dest_w * 4), logo + (y * w * 4), w * 4);
            unsigned char foregroundAlpha = logo[(y_w) + (x_4)];
            unsigned char foregroundRed =   logo[(y_w) + (x_4) + 1];
            unsigned char foregroundGreen = logo[(y_w) + (x_4) + 2];
            unsigned char foregroundBlue =  logo[(y_w) + (x_4) + 3];
            unsigned char backgroundRed =   dest[(y_dest_w) + (x_4) + 2];
            unsigned char backgroundGreen = dest[(y_dest_w) + (x_4) + 1];
            unsigned char backgroundBlue =  dest[(y_dest_w) + (x_4)];
            unsigned char r = ((foregroundRed * foregroundAlpha) + (backgroundRed * (255 - foregroundAlpha))) >> 8;
            unsigned char g = ((foregroundGreen * foregroundAlpha) + (backgroundGreen * (255 - foregroundAlpha))) >> 8;
            unsigned char b = ((foregroundBlue * foregroundAlpha) + (backgroundBlue * (255 - foregroundAlpha))) >> 8;
            dest[(y_dest_w) + (x_4)] = b;
            dest[(y_dest_w) + (x_4) + 1] = g;
            dest[(y_dest_w) + (x_4) + 2] = r;
        }
    }
}


void ContextInitializer(void *extData, const uint8_t* ctxType, FREContext ctx, uint32_t* numFunctions, const FRENamedFunction** functions) {
    FWLog(@"Entering ContextInitializer()");
    #if TARGET_OS_IPHONE && 1 == 0
    *numFunctions = 28;
#else
    *numFunctions = 23;
#endif
    FRENamedFunction* func = (FRENamedFunction*)malloc(sizeof(FRENamedFunction)*(*numFunctions));
    
    func[0].name = (const uint8_t*)"fw_ffmpeg_create";  
    func[0].functionData = NULL;  
    func[0].function = &fw_ffmpeg_create;  
    
    func[1].name = (const uint8_t*)"fw_ffmpeg_free";  
    func[1].functionData = NULL;  
    func[1].function = &fw_ffmpeg_free;  
    
    func[2].name = (const uint8_t*)"fw_ffmpeg_addVideoFrame";  
    func[2].functionData = NULL;  
    func[2].function = &fw_ffmpeg_addVideoFrame;  
    
    func[3].name = (const uint8_t*)"fw_ffmpeg_init";  
    func[3].functionData = NULL;  
    func[3].function = &fw_ffmpeg_init;  
    
    func[4].name = (const uint8_t*)"fw_ffmpeg_getStream";  
    func[4].functionData = NULL;  
    func[4].function = &fw_ffmpeg_getStream;    
    
    func[5].name = (const uint8_t*)"fw_ffmpeg_setFrames";
    func[5].functionData = NULL;
    func[5].function = &fw_ffmpeg_setFrames;
    
    func[6].name = (const uint8_t*)"fw_ffmpeg_canFinish";
    func[6].functionData = NULL;
    func[6].function = &fw_ffmpeg_canFinish;
    
    func[7].name = (const uint8_t*)"fw_ffmpeg_getVideoFramesSent";
    func[7].functionData = NULL;
    func[7].function = &fw_ffmpeg_getVideoFramesSent;    

    func[8].name = (const uint8_t*)"fw_processByteArrayAudio";  
    func[8].functionData = NULL;  
    func[8].function = &fw_processByteArrayAudio;

    func[9].name = (const uint8_t*)"fw_saveToCameraRoll";  
    func[9].functionData = NULL;  
    func[9].function = &fw_saveToCameraRoll;
    
    func[10].name = (const uint8_t*)"fw_bindFlashFBO";
    func[10].functionData = NULL;
    func[10].function = &fw_bindFlashFBO;
    
    func[11].name = (const uint8_t*)"fw_captureFrame";
    func[11].functionData = NULL;
    func[11].function = &fw_captureFrame;
    
    func[12].name = (const uint8_t*)"fw_ffmpeg_addAudioFrame";  
    func[12].functionData = NULL;  
    func[12].function = &fw_ffmpeg_addAudioFrame;
    
    func[13].name = (const uint8_t*)"fw_addAudioMix";
    func[13].functionData = NULL;
    func[13].function = &fw_addAudioMix;
    
    func[14].name = (const uint8_t*)"fw_stopAudioMix";
    func[14].functionData = NULL;
    func[14].function = &fw_stopAudioMix;

    func[15].name = (const uint8_t*)"fw_finish";
    func[15].functionData = NULL;
    func[15].function = &fw_finish;
    
    func[16].name = (const uint8_t*)"fw_getExportProgress";
    func[16].functionData = NULL;
    func[16].function = &fw_getExportProgress;
    
    func[17].name = (const uint8_t*)"fw_exportCancel";
    func[17].functionData = NULL;
    func[17].function = &fw_exportCancel;
    
    func[18].name = (const uint8_t*)"fw_setLogging";
    func[18].functionData = NULL;
    func[18].function = &fw_setLogging;
    
    func[19].name = (const uint8_t*)"fw_setPTSMode";
    func[19].functionData = NULL;
    func[19].function = &fw_setPTSMode;
    
    func[20].name = (const uint8_t*)"fw_setFramedropMode";
    func[20].functionData = NULL;
    func[20].function = &fw_setFramedropMode;
 
    func[21].name = (const uint8_t*)"fw_setHighresRecording";
    func[21].functionData = NULL;
    func[21].function = &fw_setHighresRecording;
    
    func[22].name = (const uint8_t*)"fw_setCaptureRectangle";
    func[22].functionData = NULL;
    func[22].function = &fw_setCaptureRectangle;

#if TARGET_OS_IPHONE && 1 == 0
    func[22].name = (const uint8_t*)"fw_ReplayKitAvailable";
    func[22].functionData = NULL;
    func[22].function = &fw_ReplayKitAvailable;

    func[23].name = (const uint8_t*)"fw_ReplayKitStart";
    func[23].functionData = NULL;
    func[23].function = &fw_ReplayKitStart;

    func[24].name = (const uint8_t*)"fw_ReplayKitStop";
    func[24].functionData = NULL;
    func[24].function = &fw_ReplayKitStop;
  
    func[25].name = (const uint8_t*)"fw_ReplayKitIsRecording";
    func[25].functionData = NULL;
    func[25].function = &fw_ReplayKitIsRecording;

    func[26].name = (const uint8_t*)"fw_ReplayKitDiscard";
    func[26].functionData = NULL;
    func[26].function = &fw_ReplayKitDiscard;

    func[27].name = (const uint8_t*)"fw_ReplayKitPreview";
    func[27].functionData = NULL;
    func[27].function = &fw_ReplayKitPreview;
#endif
    *functions = func;
    
    FWLog(@"FlashyWrappers 2.4 ANE");
    FWLog(@"Exiting ContextInitializer()");
}

void ContextFinalizer(FREContext ctx) {
    FWLog(@"Entering ContextFinalizer()");
    FWLog(@"Exiting ContextFinalizer()");
    return;
}

void ExtInitializer(void **extDataToSet, FREContextInitializer* ctxInitializerToSet, FREContextFinalizer* ctxFinalizerToSet) {
    FWLog(@"Entering ExtInitializer()");
    *extDataToSet = NULL;
    *ctxInitializerToSet = &ContextInitializer;
    *ctxFinalizerToSet = &ContextFinalizer;
    FWLog(@"Creating audio controller for Amazing Audio Engine");
 /*   audioController = [[AEAudioController alloc] initWithAudioDescription:ABAudioStreamBasicDescriptionMake(ABAudioStreamBasicDescriptionSampleTypeFloat32, TRUE, 2, 44100) inputEnabled:NO];
    NSError * error = nil;
    BOOL result = [audioController start:&error];
    if (!result) {
        throwError(@"Error when initializing Amazing Audio Engine", error);
    } else {
        FWLog(@"Amazing Audio Engine started.");
    }*/

    FWLog(@"Exiting ExtInitializer()");
}

void ExtFinalizer(void *extData) {
//    [audioController release];
    FWLog(@"Entering ExtFinalizer()");
    FWLog(@"Exiting ExtFinalizer()");
    return;
}

// this logs error to device and also throws error into AIR
// logs description and failure reason
void throwError(NSString *text, NSError *error) {
    NSString *finalText;
    if (error != NULL) {
        finalText = [NSString stringWithFormat:@"%@: %@ (%@)", text, [error localizedDescription], [error localizedFailureReason]];
    } else {
        finalText = [NSString stringWithFormat:@"%@:", text];
    }
    FWLog(@"[error] %@", finalText);
    throw MyException(std::string([finalText UTF8String]));
}

// throw error from inside completion block, dispatches the event to Flash and doesn't throw an actual C++ error which would crash the Flash app as result (because it can't be catched on the outside scope)
void throwErrorFromBlock(NSString *text, NSError *error, FREContext ctx) {
    NSString *finalText = [NSString stringWithFormat:@"%@: %@ (%@)", text, [error localizedDescription], [error localizedFailureReason]];
    FWLog(@"[error] %@", finalText);
    FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)[finalText UTF8String]);
}

// ReplayKit implementation
// ========================

#if TARGET_OS_IPHONE && 1 == 0

FREObject fw_ReplayKitAvailable(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    try {
        bool res = false;
        if ([AIRReplayKit hasInstance]) {
            res = [[AIRReplayKit Instance] screenRecordingAvailable] == YES;
        }
        int resInt = 0;
        if (res) resInt = 1;
        FREObject asResult;
        FRENewObjectFromInt32(resInt, &asResult);
        return asResult;
    } catch (MyException &e) {
        FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
        return NULL;
    }
}

FREObject fw_ReplayKitStart(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
        try {
            int enableMicrophone = 0;
            FREGetObjectAsInt32(argv[0], &enableMicrophone);
            bool enableMic = enableMicrophone ? YES : NO;
            int result = 0;
            if ([[AIRReplayKit Instance] startRecoring:enableMic] == YES) result = 1;
            FREObject asResult;
            FRENewObjectFromInt32(result, &asResult);
        } catch (MyException &e) {
            FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
            return NULL;
        }
    }
    
FREObject fw_ReplayKitIsRecording(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    try {
        int result = 0;
        FREObject asResult;
        if (![AIRReplayKit hasInstance]) {
            result = -1;
        } else {
            if ([[AIRReplayKit Instance] recording] == YES) result = 1;
        }
        FRENewObjectFromInt32(result, &asResult);
        return asResult;
    } catch (MyException &e) {
        FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
        return NULL;
    }
}

FREObject fw_ReplayKitStop(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    
    try {
        int result = 0;
        if (![AIRReplayKit hasInstance]) {
            result = -1;
        } else {
            if ([[AIRReplayKit Instance] stopRecording] == YES) result = 1;
        }
        FREObject asResult;
        FRENewObjectFromInt32(result, &asResult);
        return asResult;
    } catch (MyException &e) {
        FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
        return NULL;
    }
}

FREObject fw_ReplayKitDiscard(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    
    try {
        int result = 0;
        if (![AIRReplayKit hasInstance]) {
            result -1;
        } else {
            [[AIRReplayKit Instance] discard];
            result = 1;
        }
        FREObject asResult;
        FRENewObjectFromInt32(result, &asResult);
        return asResult;
    } catch (MyException &e) {
        FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
        return NULL;
    }
}

FREObject fw_ReplayKitPreview(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    try {
        int result = 0;
        if (![AIRReplayKit hasInstance]) {
            result = -1;
        } else {
            result = [[AIRReplayKit Instance] preview] == YES;
        }
        FREObject asResult;
        FRENewObjectFromInt32(result, &asResult);
        return asResult;
    } catch (MyException &e) {
        FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
        return NULL;
    }
}

#endif

FREObject fw_setCaptureRectangle(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    try {
        FREGetObjectAsInt32(argv[0], &OpenGLCaptureRectX);
        FREGetObjectAsInt32(argv[1], &OpenGLCaptureRectY);
        FREGetObjectAsInt32(argv[2], &OpenGLCaptureAIRStageWidth);
        FREGetObjectAsInt32(argv[3], &OpenGLCaptureAIRStageHeight);
        OpenGLCaptureRectangle = true;
        return NULL;
    } catch (MyException &e) {
        FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
        return NULL;
    }
}

// get the export progress when the AVAssetExport is working
FREObject fw_getExportProgress(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    float progress = 0;
    if (assetExport != NULL) {
        AVAssetExportSessionStatus status = [assetExport status];
        if (status == AVAssetExportSessionStatusExporting) {
            progress = [assetExport progress];
        } else if (status == AVAssetExportSessionStatusCompleted) {
            progress = 1;
        }
        FWLog([NSString stringWithFormat:@"assetExport progress %ld %f", (long)status, progress]);
    }
    FREObject asResult;
    FRENewObjectFromDouble((double)progress, &asResult);
    return asResult;
}

// convert the audio from Flash for WAV file (from floats to shorts), for iOS only really where it mixes WAV and MP4 tracks and we quickly need to do the conversion(in AS3 it is really slow)
FREObject fw_processByteArrayAudio(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
	try {
		FREByteArray bmd;
		FREAcquireByteArray(argv[0], &bmd);
		float *data = (float*)malloc(bmd.length * sizeof(unsigned char));
		uint32_t length = bmd.length;
		memcpy(data, bmd.bytes, bmd.length);
		FREReleaseByteArray(argv[0]);

		short *dataConverted = (short*)malloc(length / 2);

		for (long a = 0; a < length / 4; a++) {
			dataConverted[a] = (short)(data[a] * 32767);
		}
        
		FREByteArray outBmd;
        
		// copy the outDataPtr from AS3 to C++ sca_outData
		FREAcquireByteArray(argv[1], &outBmd);
        
		memcpy(outBmd.bytes, dataConverted, length / 2);
        
		FREReleaseByteArray(argv[1]);
        
		free(data);
		free(dataConverted);
		data = NULL;
		dataConverted = NULL;
		return NULL;
	}
	catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}
}

// create the instance class
FREObject fw_ffmpeg_setFrames(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    try {
        return NULL;
    } catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}    
}

// create the instance class
FREObject fw_setLogging(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    try {
        uint32_t sca_logging = 0;
        uint32_t sca_verbose = 0;
        FREGetObjectAsBool(argv[0], &sca_logging);
        FREGetObjectAsBool(argv[1], &sca_verbose);
        if (sca_logging) logging = true; else logging = false;
        if (sca_verbose) verbose = true; else verbose = false;
        return NULL;
    } catch (MyException &e) {
        FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
        return NULL;
    }    
}

void setFramedropMode(uint32_t mode) {
    framedropMode = mode;
    if (mode == FRAMEDROP_AUTO) {
        if (verbose) FWLog(@"Framedrop mode set to AUTO");
        if (realtime) {
            step = 1000 / (float)foundation_fps;
        } else {
            step = 0;
        }
    }
    if (mode == FRAMEDROP_OFF) {
        if (verbose) FWLog(@"Framedrop mode set to OFF");
        step = 0;
    }
    if (mode == FRAMEDROP_ON) {
        if (verbose) FWLog(@"Framedrop mode set to ON");
        step = 1000 / (float)foundation_fps;
    }
}

// create the instance class
FREObject fw_setHighresRecording(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    try {
  //      FREGetObjectAsUint32(argv[0], &highresRecording);
        return NULL;
    } catch (MyException &e) {
        FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
        return NULL;
    }
}

// create the instance class
FREObject fw_setPTSMode(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    try {
        FREGetObjectAsUint32(argv[0], &PTSMode);
        return NULL;
    } catch (MyException &e) {
        FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
        return NULL;
    }
}

// create the instance class
FREObject fw_setFramedropMode(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    try {
        FREGetObjectAsUint32(argv[0], &framedropMode);
        setFramedropMode(framedropMode);
        return NULL;
    } catch (MyException &e) {
        FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
        return NULL;
    }
}

// get how many frames we've sent to the encoder
FREObject fw_ffmpeg_getVideoFramesSent(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    FREObject asResult;
    uint32_t res = foundation_frameCount;
    FRENewObjectFromUint32(res, &asResult);
    return asResult;
}

// create the instance class
FREObject fw_ffmpeg_create(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    return NULL;
}

// free the instance class
FREObject fw_ffmpeg_free(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {		
    return NULL;
}

// add audio file to mix and "start recording" (virtually)
FREObject fw_addAudioMix(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    try {
        uint32_t len;
        const uint8_t *filename = NULL;
        FREGetObjectAsUTF8(argv[0], &len, &filename);
        char *str_filename = (char*)malloc(500);
        memcpy(str_filename, filename, len);
        str_filename[len] = 0;
        /*for (int i = 0; i < audioMixFiles.size(); i++) {
            if (!strcmp(audioMixFiles[i], str_filename)) {
                throwError( [NSString stringWithFormat:@"The audio mix with name %@ was already added, pick unique filenames for your mixes.", [NSString stringWithUTF8String:str_filename]], NULL);
            }
        }*/
        FWAudioMix *audioMix = new FWAudioMix();
        audioMix->str_filename = str_filename;
        // timestamp of the function call
        double tsd = CACurrentMediaTime();
        // duration of the audio..becuse iOS for some fucked up reason messes up duration of my test mp3
        double dur = 0;
        FREGetObjectAsDouble(argv[1], &dur);
        audioMix->audioMixDuration = dur;
        audioMix->audioTimeStamp = tsd - recordingStart;
        audioMix->active = true;
        audioMixFiles.push_back(audioMix);
    } catch (MyException &e) {
        FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
        return NULL;
    }
    return NULL;
}

// "stop" audio mix - in reality this just changes the duration of the audio mix based on the time this is called
// we substract the starting time of the mix from current time to get the duration. It can't be longer than the duration
// already stored though! That's the duration of our original track and we can't go over that.

FREObject fw_stopAudioMix(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    try {
        uint32_t len;
        const uint8_t *filename = NULL;
        FREGetObjectAsUTF8(argv[0], &len, &filename);
        char *str_filename = (char*)malloc(500);
        memcpy(str_filename, filename, len);
        str_filename[len] = 0;
        // look for the audio mix by name
        for (int i = 0; i < audioMixFiles.size(); i++) {
            // found it, adjust its timestamp
            if (!strcmp(audioMixFiles[i]->str_filename, str_filename) && audioMixFiles[i]->active) {
                audioMixFiles[i]->active = false;
                double tsd = CACurrentMediaTime();
                // current time minus recording time(our current timestamp) minus the start timestamp => duration
                double durationNew = (tsd - recordingStart) - audioMixFiles[i]->audioTimeStamp;
                // if the new duration is less than the original one then save it
                if (durationNew < audioMixFiles[i]->audioMixDuration) {
                    audioMixFiles[i]->audioMixDuration = durationNew;
                    FWLog(@"fw_stopAudioMix called - adjusting duration of the audiomix %@(%d) from %f to %f", [NSString stringWithUTF8String:str_filename], i, audioMixFiles[i]->audioMixDuration, durationNew);
                } else {
                    throwError( [NSString stringWithFormat:@"You're stopping the audio mix %@(%d) too late. You're beyond the length(%f) of the audio mix when stopping.", [NSString stringWithUTF8String:str_filename], i,  audioMixFiles[i]->audioMixDuration], NULL);
                }
                return NULL;
            }
        }
        throwError( [NSString stringWithFormat:@"The audio mix with name %@ was not found so it can't be stopped.", [NSString stringWithUTF8String:str_filename]], NULL);
    } catch (MyException &e) {
         FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
         return NULL;
    }
    return NULL;
}


// flush intermediate data to the target buffer (for encoder)
bool flush_intermediate_frames() {
    bool flushed = false;
    if (videoDataVector_intermediate.size() > 0) {
        flushed = true;
    }
    
    // flush intermediate video data
    for (int a = 0; a < videoDataVector_intermediate.size(); a++) {
        videoDataVector.push_back(videoDataVector_intermediate[a]);
    }
    
    // reset the intermediate video data, we don't want to free the frames inside the vector, those will be freed during the encoding
    videoFramesTotal_intermediate = 0;
    videoDataVector_intermediate.clear();
    
    // all flushed, thread can start eating the data :P
    if (flushed) threadDataAvailable = true;
    return flushed;
}

void *runencoderthread(void *param) {
    while (threadRunning) {
        if (threadDataAvailable) {
            threadEncodingRunning = true;
            threadDataAvailable = false;
            encode_it();
            threadEncodingRunning = false;
        }
    }
    return NULL;
}

bool canFinish() {
    if (threadEncodingRunning || threadDataAvailable) return false; else {
        if (try_flushAndThread()) {
            return false;
        } else {
            threadRunning = false;
            hThread = NULL;
            return true;
        }
    }
    return false;
}

bool try_flushAndThread() {
    if (flush_intermediate_frames()) {
        // create the encoder thread if not created
        if (hThread == NULL) {
            int val = pthread_create(&hThread, NULL, runencoderthread, NULL);
            struct sched_param param;
            param.sched_priority = sched_get_priority_min(SCHED_FIFO);
            pthread_setschedparam(hThread, SCHED_FIFO, &param);
            if (val != 0) {
                throwError(@"Couldn't create encoder thread", NULL);
            }
        }
        return true;
    }
    else {
        FWLog(@"flush_intermediate_frames false");
    }
    return false;
}

// internal add audio frame
// this is used both by addAudioFrame from AIR and also the capture method of Amazing Audio Engine too add captured audio
// directly into video & mux it

void addAudioFrame(unsigned char *data, long audio_len, double timestamp_double) {
    CMTime timestamp;
    timestamp = CMTimeMake(timestamp_double, foundation_sampleRate);
    CMSampleBufferRef sample;
    CMBlockBufferRef buffer1;
    CMBlockBufferRef buffer2;
    
    // TODO: free data???
    
    int status = CMBlockBufferCreateWithMemoryBlock(kCFAllocatorDefault, data, audio_len, kCFAllocatorDefault, NULL, 0, audio_len, kCMBlockBufferAssureMemoryNowFlag, &buffer1);
    if (status != kCMBlockBufferNoErr) {
        throwError([NSString stringWithFormat:@"Couldn't create BlockBuffer1, code %i", status], NULL);
    }
    
    status = CMBlockBufferCreateContiguous(kCFAllocatorDefault, buffer1, kCFAllocatorDefault, NULL, 0, audio_len, kCMBlockBufferAssureMemoryNowFlag | kCMBlockBufferAlwaysCopyDataFlag, &buffer2);
    
    if (status != noErr) {
        CFRelease(buffer1);
        throwError([NSString stringWithFormat:@"Couldn't create BlockBuffer2, code %i", status], NULL);
    }
    
    status = CMAudioSampleBufferCreateWithPacketDescriptions(kCFAllocatorDefault, buffer2, true, 0, NULL, audioFormat, audio_len / (foundation_noChannels * 4), timestamp, NULL, &sample);
    
    if (status != noErr) {
        CFRelease(buffer1);
        CFRelease(buffer2);
        throwError([NSString stringWithFormat:@"Couldn't create SampleBuffer for audio, code %i", status], NULL);
    }
    
    BOOL append_ok = NO;
    
    if (audioWriterInput.readyForMoreMediaData) {
        append_ok = [audioWriterInput appendSampleBuffer:sample];
        if (verbose) FWLog(@"adding audio data %ld bytes timestamp %f", audio_len, timestamp_double);
        if(append_ok) {
            foundation_audioFrameCount += audio_len / (foundation_noChannels * 4);
        } else {
            FWLog(@"writing of audio sample failed");
        }
    }
    else {
        FWLog(@"(audio)adaptor not ready");
    }
    CFRelease(sample);
    CFRelease(buffer1);
    CFRelease(buffer2);
}

// add audio frame using AVFoundation (new experimental feature)
// feed frame into encoder - for the "classicaL" encoding...
FREObject fw_ffmpeg_addAudioFrame(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    
    try {
        if (!audio) {
            throwError(@"Calling addAudioFrame but AUDIO_OFF was specified in start - enable mono, stereo or microphone", NULL);
        }
        FREByteArray bmd;
        FREAcquireByteArray(argv[0], &bmd);
        unsigned char *data = (unsigned char*)malloc(bmd.length * sizeof(unsigned char));
        memcpy(data, bmd.bytes, bmd.length);
        long audio_len = bmd.length;
        FREReleaseByteArray(argv[0]);
        
        double timestamp;
        if ((!realtime && PTSMode == PTS_AUTO) || PTSMode == PTS_MONO) {
            timestamp = foundation_audioFrameCount;
        } else {
            if (firstFrameTime == 0) {
                firstFrameTime = CACurrentMediaTime();
            }
            // time stamp of the audio is the current time for simplicity
            double currentTime = CACurrentMediaTime() - firstFrameTime;
            // timestamp of audio minus the audio length, only for microphone
            // TODO this is just an assumption that when channels = 1, this is microphone
            // for that we assume delay before the microphone audio got in here so we substract the length of the audio samples
            // it took to record the audio.
            // but for "prepared" audio with 2 channels we don't want to substract its length
            if (foundation_noChannels == 1) {
                timestamp = ((currentTime - ((audio_len / (foundation_noChannels * 4)) / foundation_sampleRate)   ) * foundation_sampleRate);
            } else {
                timestamp = currentTime;
            }
        }
        // call internal audio frame
        addAudioFrame(data, audio_len, timestamp);
    } catch (MyException &e) {
            FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
            return NULL;
    }
    return NULL;
}

void appendPixelBuffer(bool glFinishBool) {
    videoFramesSent++;
    // attemp to add the frame into the file
    BOOL append_ok = NO;
    int j = 0;
    // this while runs on a different thread in MT MODE so it shouldn't block the main thread
    while (!append_ok)
    {
        if (adaptor.assetWriterInput.readyForMoreMediaData)
        {
            if (verbose) FWLog(@"encode_it before appendPixelBuffer");
            CMTime frameTime;
            if ((!realtime && PTSMode == PTS_AUTO) || PTSMode == PTS_MONO) {
                frameTime = CMTimeMake((int32_t)foundation_frameCount,(int32_t) foundation_fps);
            } else {
                if (firstFrameTime == 0) {
                    firstFrameTime = CACurrentMediaTime();
                }
                // time stamp of the audio is the current time for simplicity
                double currentTime = CACurrentMediaTime() - firstFrameTime;
                frameTime = CMTimeMake(currentTime * foundation_fps * 1000000, foundation_fps * 1000000);
            }
            // blit logo onl when we're ready to append a new frame
#ifdef DEMO
            unsigned char *buf = (unsigned char*)CVPixelBufferGetBaseAddress(pixel_buffer);
            if (OpenGLCapture) {
                glFinish();
                blitLogoFlipped(buf, foundation_sca_w, 85, 60);
            } else {
                blitLogo(buf, foundation_sca_w, 85, 60);
            }
#else
            if (glFinishBool && OpenGLCapture) glFinish();
#endif
            append_ok = [adaptor appendPixelBuffer:pixel_buffer withPresentationTime:frameTime];
            if (videoWriter.status == AVAssetWriterStatusFailed) {
                //throwError(@"Writing of video frame failed", videoWriter.error);
                // we don't really want to crash horribly here, finish at least most of the video if possible? just log
                FWLog(@"] videoWriter.status: AVAssetWriterStatusFailed, error: %@", videoWriter.error);
            }
            if (append_ok) {
                if (verbose) FWLog(@"frame added with time %f!", frameTime.value);
                foundation_frameCount++;
                // if the frame was actually appended release the pixel buffer object
            }
            if(/*buffer &&*/ append_ok) {
                //CVBufferRelease(buffer);
            }
            // I'm not sure why this is here but it slows down encoding like HELL
            if (!foundation_fast) {
                [NSThread sleepForTimeInterval:0.05];
            }
        }
        else
        {
            FWLog(@"adaptor not ready");
            //[NSThread sleepForTimeInterval:0.1];
            // Should fix iPad2 issues according to Stack Overflow
            NSDate *maxDate = [NSDate dateWithTimeIntervalSinceNow:0.1];
            [[NSRunLoop currentRunLoop] runUntilDate:maxDate];
        }
        j++;
    }
    CVPixelBufferUnlockBaseAddress(pixel_buffer, 0);
    if (!append_ok) {
        //if (buffer) CVBufferRelease(buffer);
        // only warn about this, don't error out
        FWLog(@"Error appending frame");
    } 
}

// encode one frame from buffer
// ----------------------------
// this will fill the pixel buffer the traditional way, form a buffer received from AIR

void encode_it() {
    for (;;)
    {
        if (videoFramesSent >= videoDataVector.size()) break;

/*CFTimeInterval millis = CFAbsoluteTimeGetCurrent() * 1000;
        if (millisOldMT != 0) {
            deltaMT = millis - millisOldMT;
            stepAccumMT += deltaMT;
        } else deltaMT = millis;
        millisOldMT = millis;
        if (stepAccumMT >= stepTargetMT) {
            stepTargetMT += stepMT;*/
            
            // generate CVPixelBuffer from AS3 ByteArray image(ARGB) - old code without pixel pool
            /*CVReturn status = CVPixelBufferCreateWithBytes(kCFAllocatorDefault, foundation_sca_w, foundation_sca_h, kCVPixelFormatType_32ARGB, videoDataVector.at(videoFramesSent), 4 * foundation_sca_w, &MyPixelBufferReleaseCallback, videoDataVector.at(videoFramesSent), NULL, &buffer);
             videoFramesSent++;
             if (status != kCVReturnSuccess || buffer == NULL) {
             throw MyException("couldn't generate CVPixelBuffer");
             }*/
        
             
            // copy the data straight to pixel buffer with ARGB->BGRA conversion
            CVPixelBufferLockBaseAddress(pixel_buffer, 0);
        
            vImage_Buffer src;
            src.height = foundation_sca_h;
            src.width = foundation_sca_w;
            src.rowBytes = foundation_bytesPerRow;
            src.data = videoDataVector.at(videoFramesSent);
    
            vImage_Buffer dest;
            dest.height = foundation_sca_h;
            dest.width = foundation_sca_w;
            dest.rowBytes = foundation_bytesPerRow;
            dest.data = CVPixelBufferGetBaseAddress(pixel_buffer);
 
            // from ARGB to BGRA
            const uint8_t map[4] = {3,2,1,0};
        
            vImagePermuteChannels_ARGB8888(&src, &dest, map, kvImageNoFlags);

            // this is old code for CVPixelBuffer
            //      memcpy(CVPixelBufferGetBaseAddress(pixel_buffer), videoDataVector.at(videoFramesSent), foundation_sca_w * foundation_sca_h * 4);
        
            // free the frame, it's in the pixel buffer now
            free(videoDataVector.at(videoFramesSent));
        
            // this contains a while loop, but its fine because encode_it is running on another thread
            appendPixelBuffer(false);
     //   }
    }
}

// dead code right now, for version before pixel buffer pool
/*void MyPixelBufferReleaseCallback(void *releaseRefCon, const void *baseAddress){
    if (releaseRefCon != NULL) {
        NSLog(@"Releasing buffer");
        free(releaseRefCon);
        releaseRefCon = NULL;
        baseAddress = NULL;
        NSLog(@"Release over");
    } 
}*/

void printASBD(AudioStreamBasicDescription asbd) {
    char formatIDString[5];
    UInt32 formatID = CFSwapInt32HostToBig (asbd.mFormatID);
    bcopy(&formatID, formatIDString, 4);
    formatIDString[4] = '\0';
    NSLog (@"  Sample Rate:             %10.0f", asbd.mSampleRate);
    NSLog (@"  Format ID:               %10s", formatIDString);
    NSLog (@"  Format Flags:            %10lX", asbd.mFormatFlags);
    NSLog (@"  Bytes per Packet:        %10lu", asbd.mBytesPerPacket);
    NSLog (@"  Frames per Packet:       %10lu", asbd.mFramesPerPacket);
    NSLog (@"  Bytes per Frame:         %10lu", asbd.mBytesPerFrame);
    NSLog (@"  Channels per Frame:      %10lu", asbd.mChannelsPerFrame);
    NSLog (@"  Bits per Channel:        %10lu", asbd.mBitsPerChannel);
}

// init
FREObject fw_ffmpeg_init(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    FWLog(@"Init AVFoundation start");
    const uint8_t *filename = NULL;
    uint32_t len;
    threadEncodingRunning = false;
    threadDataAvailable = false;
    threadRunning = true;
    AIRRendered = false;
    OpenGLCaptureRectX = 0;
    OpenGLCaptureRectY = 0;
    OpenGLCaptureAIRStageWidth = 0;
    OpenGLCaptureAIRStageHeight = 0;
    // Measure the root view dimensions
#if TARGET_OS_IPHONE
    rootViewController = (UIViewController*)[[[UIApplication sharedApplication] keyWindow] rootViewController];
    UIView *rootView = [rootViewController view];
    NSArray *subviews = [[rootViewController view] subviews];
    
#else 
    //rootViewController = (NSViewController*)[[[NSApplication sharedApplication] keyWindow] su];
    NSView *rootView = [[[NSApplication sharedApplication] keyWindow] contentView];
    NSArray *subviews = [rootView subviews];
#endif
    
    
    try {
        FREGetObjectAsUTF8(argv[0], &len, &filename);
        char str_filename[500];
        memcpy(str_filename, filename, len);
        str_filename[len] = 0;
        FREGetObjectAsInt32(argv[1], &foundation_sca_w);
        FREGetObjectAsInt32(argv[2], &foundation_sca_h);
        
        // Set fast mode with some experimental stuff that I'm not sure how it would work
        FREGetObjectAsUint32(argv[3], &foundation_fast);
        FREGetObjectAsUint32(argv[4], &foundation_fps);
        FREGetObjectAsUint32(argv[5], &foundation_quality);    
        FREGetObjectAsUint32(argv[6], &foundation_bitrate);
        FREGetObjectAsUint32(argv[7], &foundation_keyframe_interval);
        FREGetObjectAsUint32(argv[8], &intermediate_buffer_length);
        FREGetObjectAsInt32(argv[9], &stage_fps);
        FREGetObjectAsUint32(argv[10], &recordToWAV);
        FREGetObjectAsUint32(argv[11], &foundation_sampleRate);
        FREGetObjectAsUint32(argv[12], &foundation_noChannels);
        FREGetObjectAsUint32(argv[13], &foundation_audioBitrate);
        FREGetObjectAsUint32(argv[14], &realtime);
        FREGetObjectAsUint32(argv[15], &audio);
        FREGetObjectAsUint32(argv[16], &audioCapture);
        
        // reset the fps sync component vars
        step = 0;
        delta = 0;
        millisOld = 0;
        stepAccum = 0;
        stepTarget = 0;
        
        stepMT = 0;
        deltaMT = 0;
        millisOldMT = 0;
        stepAccumMT = 0;
        stepTargetMT = 0;
        
        // reset the audio framecount
        foundation_audioFrameCount = 0;
        // reset the first frame time
        firstFrameTime = 0;
        OpenGLCapture = false;
        
    
#if TARGET_OS_IPHONE
     /*   if (audioCapture) {
            FWLog(@"Creating audio controller for Amazing Audio Engine");
            audioController = [[AEAudioController alloc] initWithAudioDescription:ABAudioStreamBasicDescriptionMake(ABAudioStreamBasicDescriptionSampleTypeFloat32, TRUE, 2, 44100) inputEnabled:NO];
            NSError * error = nil;
            BOOL result = [audioController start:&error];
            if (!result) {
                throwError(@"Error when initializing Amazing Audio Engine", error);
            } else {
                FWLog(@"Amazing Audio Engine started.");
            }
        }*/
        
        CAEAGLLayer* _eaglLayer;
        FWLog(@"Root view %@", rootView);
        if ([subviews count] > 0) {
            for (UIView *subview in subviews) {
                FWLog(@"Subview %@", subview);
            }
            _eaglLayer = [subviews objectAtIndex:0];
            
            // find our whenever we are at Retina or better display
            scaleFactor = rootView.contentScaleFactor;
            
            stageW = _eaglLayer.frame.size.width * scaleFactor;
            stageH = _eaglLayer.frame.size.height * scaleFactor;
            textureW = nextPow2(stageW);
            textureH = nextPow2(stageH);
            textureU = (float_t)stageW / (float_t)textureW;
            textureV = (float_t)stageH / (float_t)textureH;
            
            forceOpenGLResolution = false;
            
            // only on iPhone are we allowing OpenGL capturing for now
            if (realtime) {
                // we don't want huge 2K or 4K movies so we divide the dimensions for the actual video
                if (foundation_sca_w <= 0 || foundation_sca_h <= 0) {
                    foundation_sca_w = stageW / scaleFactor;
                    foundation_sca_h = stageH / scaleFactor;
                } else {
                    forceOpenGLResolution = true;
                }
                OpenGLCapture = true;
            } else {
                if (foundation_sca_w <= 0 || foundation_sca_h <= 0) {
                    foundation_sca_w = stageW / scaleFactor;
                    foundation_sca_h = stageH / scaleFactor;
                    FWLog(@"Dimensions are 0 in non-realtime mode, guessing dimensions...");
                }
            }
            
            FWLog(@"Scale factor: %f", scaleFactor);
            FWLog(@"Matching the best texture size for stage: %d x %d", textureW, textureH);
            //FWLog(@"Texture UV: %f, %f", textureU, textureV);
        }
        
#else
        CAOpenGLLayer* _eaglLayer;
        FWLog(@"Root view %@", rootView);
        if (1 == 1) {
            
            _eaglLayer = (CAOpenGLLayer*)rootView;
            
            // OS X always 1
            scaleFactor = 1.0f;
            stageW = _eaglLayer.frame.size.width * scaleFactor;
            stageH = _eaglLayer.frame.size.height * scaleFactor;
            if (stageW > 0 && stageH > 0) {
                textureW = nextPow2(stageW);
                textureH = nextPow2(stageH);
                textureU = (float_t)stageW / (float_t)textureW;
                textureV = (float_t)stageH / (float_t)textureH;
                FWLog(@"Scale factor: %f", scaleFactor);
                FWLog(@"Matching the best texture size for stage: %d x %d", textureW, textureH);
                //FWLog(@"Texture UV: %f, %f", textureU, textureV);
            } else {
                // if for some reason OpenGL is screwed up, do not use OpenGL fullscreen capture
                stageW = foundation_sca_w;
                stageH = foundation_sca_h;
                FWLog(@"Note: EAGL layer dimensions are zero, OpenGL was probably not initialized correctly -> switching OpenGL capture off");
            }
            
            // match the UV coords to compensate for the difference between screen and texture dimensions
            // if we're at Retina displays we're gonna render
            /*VerticesHighRes[0].TexCoord[0] = textureU;
             VerticesHighRes[1].TexCoord[0] = textureU;
             VerticesHighRes[1].TexCoord[1] = textureV;
             VerticesHighRes[2].TexCoord[1] = textureV;*/

        }
        
#endif
        
        // if stage fps is set then we compute the step value
        if ((stage_fps != -1 && realtime && framedropMode == FRAMEDROP_AUTO) || framedropMode == FRAMEDROP_ON) {
            step = 1000 / (float)foundation_fps;
        }
        
        // in MT mode for captureMC, supply the frames to the encoder at rate ~equal to 30fps so it doesn't explode
        stepMT = 1000 / 20;
        stepTargetMT = stepMT;
    
        foundation_bytesPerRow = foundation_sca_w * 4;
        
#if TARGET_OS_IPHONE
        if (foundation_quality == 0) {
            foundation_native_quality = AVAssetExportPresetLowQuality;
        }
        if (foundation_quality == 1) {
            foundation_native_quality = AVAssetExportPresetMediumQuality;        
        }
        if (foundation_quality == 2) {
            foundation_native_quality = AVAssetExportPresetHighestQuality;        
        }
        if (foundation_quality == 3) {
            foundation_native_quality = AVAssetExportPresetPassthrough;
        }
#else 
        // deal with this on Mac OS X separately...
#endif
        
        if (!recordToWAV && audio) {
            // Create audio src format description
            asbd.mFormatID = kAudioFormatLinearPCM;
            asbd.mSampleRate = (float)foundation_sampleRate;
            asbd.mFormatFlags = kAudioFormatFlagIsFloat | kAudioFormatFlagIsAlignedHigh;
            asbd.mBitsPerChannel = 4 * 8;
            asbd.mChannelsPerFrame = foundation_noChannels;
            asbd.mBytesPerFrame = asbd.mChannelsPerFrame * 4;
            asbd.mFramesPerPacket = 1;
            asbd.mBytesPerPacket = asbd.mFramesPerPacket * asbd.mBytesPerFrame;
            CMAudioFormatDescriptionCreate(kCFAllocatorDefault, &asbd, 0, NULL, 0, NULL, NULL, &audioFormat);   
        }
        
        // Convert path from C string to NSString
        NSString *path = [NSString stringWithUTF8String:(char*)filename];    
        
        
        videoWriter = [[AVAssetWriter alloc] initWithURL:[NSURL fileURLWithPath:path] fileType:AVFileTypeQuickTimeMovie error:&AVerror];    
        
        
        NSDictionary *videoSettings = [NSDictionary dictionaryWithObjectsAndKeys:
                                       AVVideoCodecH264, AVVideoCodecKey,
                                       [NSDictionary dictionaryWithObjectsAndKeys:
                                        [NSNumber numberWithInt:foundation_bitrate], AVVideoAverageBitRateKey,
#if TARGET_OS_IPHONE
                                        AVVideoProfileLevelH264BaselineAutoLevel, AVVideoProfileLevelKey,
#endif
                                        [NSNumber numberWithInt: foundation_keyframe_interval], AVVideoMaxKeyFrameIntervalKey, nil], AVVideoCompressionPropertiesKey,
                                       [NSNumber numberWithInt:foundation_sca_w], AVVideoWidthKey,
                                       [NSNumber numberWithInt:foundation_sca_h], AVVideoHeightKey,
                                       nil];
        
        videoWriterInput = [[AVAssetWriterInput
                             assetWriterInputWithMediaType:AVMediaTypeVideo
                             outputSettings:videoSettings] retain];
        
        if (!recordToWAV && audio) {
            AudioChannelLayout channelLayout;
            memset(&channelLayout, 0, sizeof(AudioChannelLayout));
            if (foundation_noChannels == 1) channelLayout.mChannelLayoutTag = kAudioChannelLayoutTag_Mono;
            if (foundation_noChannels == 2) channelLayout.mChannelLayoutTag = kAudioChannelLayoutTag_Stereo;
        
            NSDictionary *audioSettings = [NSDictionary dictionaryWithObjectsAndKeys:
                                           [NSNumber numberWithInt:kAudioFormatMPEG4AAC], AVFormatIDKey,
                                       [NSNumber numberWithFloat:(float)foundation_sampleRate], AVSampleRateKey,
                                       [NSNumber numberWithInt:foundation_noChannels], AVNumberOfChannelsKey,
                                       [NSData dataWithBytes:&channelLayout length:sizeof(AudioChannelLayout)], AVChannelLayoutKey,
                                       [NSNumber numberWithInt:foundation_audioBitrate], AVEncoderBitRateKey,
                                        nil];
        
            audioWriterInput = [[AVAssetWriterInput assetWriterInputWithMediaType:AVMediaTypeAudio outputSettings:audioSettings] retain];
        }
        
        NSDictionary *adaptorSettings = [NSDictionary
                                         dictionaryWithObjectsAndKeys:
                                         [NSNumber numberWithInt:kCVPixelFormatType_32BGRA],
                                         kCVPixelBufferPixelFormatTypeKey, 
                                         [NSNumber numberWithInt:foundation_sca_w/*1334*/], kCVPixelBufferWidthKey,
                                         [NSNumber numberWithInt:foundation_sca_h/*750*/], kCVPixelBufferHeightKey,
                                         [NSDictionary dictionary], (id)kCVPixelBufferIOSurfacePropertiesKey,
                                         nil];
        adaptor = [AVAssetWriterInputPixelBufferAdaptor
                   assetWriterInputPixelBufferAdaptorWithAssetWriterInput:videoWriterInput
                   sourcePixelBufferAttributes:adaptorSettings];
        
        [adaptor retain];
        [videoWriter retain];
        videoWriterInput.expectsMediaDataInRealTime = YES;
        if (!recordToWAV && audio) {
            audioWriterInput.expectsMediaDataInRealTime = YES;
        }
        
        [videoWriter addInput:videoWriterInput];
        if (!recordToWAV && audio) {
            [videoWriter addInput:audioWriterInput];
        }
        
        //Start a session:
        [videoWriter startWriting];
        [videoWriter startSessionAtSourceTime:kCMTimeZero];
        
        // Create reusable pixel buffer
        CVReturn status = CVPixelBufferPoolCreatePixelBuffer (NULL, [adaptor pixelBufferPool], &pixel_buffer);
        
        if ((pixel_buffer == NULL) || (status != kCVReturnSuccess)) {
            throwError([NSString stringWithFormat:@"Primary pixel buffer creation failed, status code %i", status], NULL);
        } else {
           // CVPixelBufferLockBaseAddress(pixel_buffer, 0);
        }
        
        // DON'T do any additional setup when not capturing via OpenGL
        if (!OpenGLCapture) {
            // note the recording start in seconds
            recordingStart = CACurrentMediaTime();
            return NULL;
        }

        // Create pixel buffer 2 for highres texture
        // (otherwise AIR doesn't want to render to normal texture for some reason in highres)
        if (scaleFactor > 1 || forceOpenGLResolution) {
            NSMutableDictionary *pixelBufferPoolAttr;
            pixelBufferPoolAttr = [NSMutableDictionary dictionary];
            
            [pixelBufferPoolAttr setObject:[NSNumber numberWithInt:kCVPixelFormatType_32BGRA] forKey:(NSString*)kCVPixelBufferPixelFormatTypeKey];
            [pixelBufferPoolAttr setObject:[NSNumber numberWithInt:stageW] forKey:(NSString*)kCVPixelBufferWidthKey];
            [pixelBufferPoolAttr setObject:[NSNumber numberWithInt:stageH] forKey:(NSString*)kCVPixelBufferHeightKey];
            [pixelBufferPoolAttr setObject:[NSDictionary dictionary] forKey:(id)kCVPixelBufferIOSurfacePropertiesKey];
            // Create it for the high res texture as well otherwise in GPU mode AIR refuses to render into normal texture...
            CVPixelBufferPoolCreate(kCFAllocatorDefault, NULL, (CFDictionaryRef) pixelBufferPoolAttr, &pixel_bufferPoolHighres);
            status = CVPixelBufferPoolCreatePixelBuffer (NULL, pixel_bufferPoolHighres, &pixel_bufferHighres);
            
            if ((pixel_bufferHighres == NULL) || (status != kCVReturnSuccess)) {
                throwError([NSString stringWithFormat:@"Secondary pixel buffer creation failed, status code %i", status], NULL);
            } else {
                // CVPixelBufferLockBaseAddress(pixel_buffer, 0);
            }
        }

        // Create texture backed FBO where we can capture AIR's output
        FWLog(@"Setting up texture cache...");
        // Configure texture cache
#if TARGET_OS_IPHONE
#else
        // for now don't do OpenGL setup on OS X, can't test this reliably yet
        return NULL;
        NSOpenGLContext *obj = [NSOpenGLContext currentContext];
        if (obj == NULL) {
            FWLog(@"CGLCurrentContext is NULL");
        } else {
            FWLog(@"CGLCurrentContext is not null");
        }
        
#endif
        // Configure texture backed FBO for offscreen AIR rendering
        GLint params = 0;
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, &params);
        FWLog(@"Bound AIR FBO: %d", params);
        GLint params2 = 0;
        glGetIntegerv(GL_MAX_TEXTURE_SIZE, &params2);
        FWLog(@"Max texture size: %d", params2);
        // on first frame remember the old FBO bindings
        oldFBO = params;
        
#if TARGET_OS_IPHONE
        CVReturn err = CVOpenGLESTextureCacheCreate(kCFAllocatorDefault, NULL, [EAGLContext currentContext], NULL, &textureCacheRef);
#else
        CVReturn err = CVOpenGLTextureCacheCreate(kCFAllocatorDefault, NULL, *(CGLContextObj*)[[NSOpenGLContext currentContext] CGLContextObj], CGLGetPixelFormat(*(CGLContextObj*)[[NSOpenGLContext currentContext] CGLContextObj]), NULL, &textureCacheRef);
#endif
        if (err) {
            FWLog(@"Error! %d", err);
            throwError([NSString stringWithFormat:@"CVOpenGLESTextureCacheCreate failed, status code %i", err], NULL);
        }
    
        if (scaleFactor > 1 || forceOpenGLResolution) {
            // Configure texture cache for high res texture
#if TARGET_OS_IPHONE
            err = CVOpenGLESTextureCacheCreate(kCFAllocatorDefault, NULL, [EAGLContext currentContext], NULL, &textureCacheHighresRef);
#else
            err = CVOpenGLTextureCacheCreate(kCFAllocatorDefault, NULL, *(CGLContextObj*)[[NSOpenGLContext currentContext] CGLContextObj], CGLGetPixelFormat(*(CGLContextObj*)[[NSOpenGLContext currentContext] CGLContextObj]), NULL, &textureCacheHighresRef);
#endif
            if (err) {
                FWLog(@"Error! %d", err);
                throwError([NSString stringWithFormat:@"CVOpenGLESTextureCacheCreate highres failed, status code %i", err], NULL);
            }
        }
       
        
        glGetIntegerv(GL_RENDERBUFFER_BINDING, &params);
        FWLog(@"Bound AIR renderbuffer: %d", params);
        // remember AIR's renderbuffer
        AIRRenderBuffer = params;
        
        FWLog(@"Saving old AIR renderbuffer:%d", AIRRenderBuffer);
        
        
        // For Retina+ displays, we need to create actual "highres" FBO where AIR will render with its own texture backing
        // THEN we will render this to screen and to our texture backed FBO 
            
        // With lowres displays, AIR renders straight to our texture backed FBO
        
        if (scaleFactor > 1 || forceOpenGLResolution) {
            FWLog(@"Setting up secondary texture cache...");
            /*glGenTextures(1, &textureHighResFBO);
            glBindTexture(GL_TEXTURE_2D, textureHighResFBO);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, textureW, textureH, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
            glBindTexture(GL_TEXTURE_2D, 0);*/
#if TARGET_OS_IPHONE
            status = CVOpenGLESTextureCacheCreateTextureFromImage (kCFAllocatorDefault, textureCacheHighresRef, pixel_bufferHighres,
                                                                   NULL, // texture attributes
                                                                   GL_TEXTURE_2D,
                                                                   GL_RGBA, // opengl format
                                                                   (int)(stageW),
                                                                   (int)(stageH),
                                                                   GL_BGRA, // native iOS format
                                                                   GL_UNSIGNED_BYTE,
                                                                   0,
                                                                   &textureRefHighres);
#else
            status = CVOpenGLTextureCacheCreateTextureFromImage (kCFAllocatorDefault, textureCacheHighresRef, pixel_bufferHighres, NULL, &textureRefHighres);
#endif
            if (status != kCVReturnSuccess) {
                FWLog(@"CVOpenGLESTextureCacheCreateTextureFromImage highres failed, status code %i", status);
                throwError([NSString stringWithFormat:@"CVOpenGLESTextureCacheCreateTextureFromImage highres failed, status code %i", status], NULL);
            }
#if TARGET_OS_IPHONE
            textureHighResFBO = CVOpenGLESTextureGetName(textureRefHighres);
#else
            textureHighResFBO = CVOpenGLTextureGetName(textureRefHighres);
#endif
            FWLog(@"Name of the highres texture created from texture cache: %d", textureHighResFBO);
            glBindTexture(GL_TEXTURE_2D, textureHighResFBO);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glBindTexture(GL_TEXTURE_2D, 0);
            FWLog(@"Done!");
        }
            // The texture cache for rendering video is downscaled by scaleFactor, it needs to match the pixel buffer size
        
        
#if TARGET_OS_IPHONE
        if (!forceOpenGLResolution) {
            FWLog(@"Setting up primary texture backed FBO: %d x %d", (int)(stageW / scaleFactor), (int)(stageH / scaleFactor));
            status = CVOpenGLESTextureCacheCreateTextureFromImage (kCFAllocatorDefault, textureCacheRef, pixel_buffer,
                                                                   NULL, // texture attributes
                                                                   GL_TEXTURE_2D,
                                                                   GL_RGBA, // opengl format
                                                                   (int)(stageW / scaleFactor),
                                                                   (int)(stageH / scaleFactor),
                                                                   GL_BGRA, // native iOS format
                                                                   GL_UNSIGNED_BYTE,
                                                                   0,
                                                                   &textureRef);
        } else {
            FWLog(@"Setting up primary texture backed FBO: %d x %d", foundation_sca_w, foundation_sca_h);
            status = CVOpenGLESTextureCacheCreateTextureFromImage (kCFAllocatorDefault, textureCacheRef, pixel_buffer,
                                                                   NULL, // texture attributes
                                                                   GL_TEXTURE_2D,
                                                                   GL_RGBA, // opengl format
                                                                   (int)(foundation_sca_w),
                                                                   (int)(foundation_sca_h),
                                                                   GL_BGRA, // native iOS format
                                                                   GL_UNSIGNED_BYTE,
                                                                   0,
                                                                   &textureRef);
        }
#else
            status = CVOpenGLTextureCacheCreateTextureFromImage (kCFAllocatorDefault, textureCacheRef, pixel_buffer, NULL, &textureRef);
#endif
            if (status != kCVReturnSuccess) {
                FWLog(@"CVOpenGLESTextureCacheCreateTextureFromImage failed, status code %i", status);
                throwError([NSString stringWithFormat:@"CVOpenGLESTextureCacheCreateTextureFromImage failed, status code %i", status], NULL);
            }
            
#if TARGET_OS_IPHONE
            textureCacheFBO = CVOpenGLESTextureGetName(textureRef);
#else
            textureCacheFBO = CVOpenGLTextureGetName(textureRef);
#endif
            FWLog(@"Name of the texture created from texture cache: %d", textureCacheFBO);
            glBindTexture(GL_TEXTURE_2D, textureCacheFBO);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            // detach the texture now
            glBindTexture(GL_TEXTURE_2D, 0);
        
        // detach render buffer from the current AIR FBO
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, 0);
        // attach texture to the current AIR FBO instead
        if (scaleFactor > 1 || forceOpenGLResolution) {
            FWLog(@"Binding secondary texture cache to AIR's FBO...");
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureHighResFBO, 0);
        } else {
             FWLog(@"Binding primary texture cache to AIR's FBO...");
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureCacheFBO, 0);
        }
       
        // check if all was fine
        GLuint statusRes = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (statusRes != GL_FRAMEBUFFER_COMPLETE) {
            throwError([NSString stringWithFormat:@"Error while creating the texture backed FBO %d", statusRes], NULL);
        } else FWLog(@"Setup successfully!");
        
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, &params);
        FWLog(@"Bound FBO: %d", params);

        glGenBuffers(1, &vertexBuffer);
        glGenBuffers(1, &indexBuffer);
        
        FWLog(@"Compiling shaders...");
        
        compileShaders();
        
        FWLog(@"Compiled!");
#if TARGET_OS_IPHONE
        // if capturing audio, start the block here
/*        if (audioCapture) {
            receiver = [AEBlockAudioReceiver audioReceiverWithBlock:^(void *source, const AudioTimeStamp *time, UInt32 frames, AudioBufferList *audio) {
                
                CMSampleBufferRef sample = NULL;
                
                CMSampleTimingInfo timing = { CMTimeMake(1, 44100), kCMTimeZero, kCMTimeInvalid };

                // TODO deliver timing info (audio stamp) using createwithpacketdescriptions thing
                OSStatus error = CMSampleBufferCreate(kCFAllocatorDefault, NULL, false, NULL, NULL, audioFormat, frames, 1, &timing, 0, NULL, &sample);
                if (error) {
                    NSLog(@"Error when capturing audio %d", (int)error);
                }
                error = CMSampleBufferSetDataBufferFromAudioBufferList(sample, kCFAllocatorDefault, kCFAllocatorDefault, 0, audio);
                
                if (error) {
                    NSLog(@"Error when capturing audio %d", (int)error);
                }
                NSLog(@"Got audio capture, time %f, frames %d, audio buffers %d", time->mSampleTime, frames, audio->mBuffers[0]);
                BOOL append_ok = NO;
                if (audioWriterInput.readyForMoreMediaData) {
                    append_ok = [audioWriterInput appendSampleBuffer:sample];
                }
                else {
                    FWLog(@"(audio)adaptor not ready");
                }

            }];
           [audioController addOutputReceiver: receiver];
        }*/
#endif
        // note the recording start in seconds
        recordingStart = CACurrentMediaTime();

        return NULL;
    } catch (MyException &e) {
        FWLog(@"Init AVFoundation fail");
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}
 
}

float coordToGLX(float coord) {
    return (((float)coord / (float)(OpenGLCaptureAIRStageWidth)) * 2) - 1;
}

float coordToGLY(float coord) {
    return (((float)coord / (float)(OpenGLCaptureAIRStageHeight)) * 2) - 1;
}
// feed the content of AIR stage directly into encoder
FREObject fw_captureFrame(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    try {
#if TARGET_OS_IPHONE
    EAGLContext *currentContext = [EAGLContext currentContext];
#else
    NSOpenGLContext *currentContext = [NSOpenGLContext currentContext];
#endif
    uint32_t glFinishBool = 0;
    FREGetObjectAsBool(argv[0], &glFinishBool);
    
    // find out how many milliseconds passed since last captureFrame call
    if (realtime || PTSMode == PTS_REALTIME) {
    	CFTimeInterval millis = CFAbsoluteTimeGetCurrent() * 1000;
    	if (millisOld != 0) {
        	delta = millis - millisOld;
        	stepAccum += delta;
    	} else delta = millis;
    	millisOld = millis;
        if (verbose) FWLog(@"Delta:%f millisOld:%f millis:%f acc:%f", delta, millisOld, millis, stepAccum);
   }
    
    
//    GLint params;
//    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &params);
//    NSLog(@"Bound FB before bind texture:%d", params);

    // the first time AIR finishes rendering the frame into our texture backed FBO, have fun with it(glReadPixels it, render it etc)
    if (AIRRendered) {
        
        if (verbose) FWLog(@"AIR should have rendered, work with texture");
        //  We can glReadPixels from the AIR screen if we wanted and return that as bitmap(even in wrong color format:)
        
        if (verbose) FWLog(@"Present texture to screen");
        
        CVPixelBufferLockBaseAddress(pixel_buffer, 0);
        
        // Start common rendering setup
        glUseProgram(programHandle);
        
        _positionSlot = glGetAttribLocation(programHandle, "Position");
        _colorSlot = glGetAttribLocation(programHandle, "SourceColor");
        _texCoordSlot = glGetAttribLocation(programHandle, "TexCoordIn");
        _textureUniform = glGetUniformLocation(programHandle, "Texture");
        glEnableVertexAttribArray(_positionSlot);
        glEnableVertexAttribArray(_colorSlot);
        glEnableVertexAttribArray(_texCoordSlot);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        
        // if capturing rectangle modify the vertices so the texture is offset to -x, -y, ie the top left corner
        // of the capture target should be at 0,0 in the video now
        if (OpenGLCaptureAIRStageHeight > 0 && OpenGLCaptureAIRStageWidth > 0) {
            // the x, y must be a relation of the AIR display object coordinates to AIR stage dimensions
            // relating the coords to the apps width height would be a mistake becaus that is different,
            // AIR requests something "rounded off" and nice to the actual AIR stage width and then scales the
            // AIR app to fit the iOS app dimensions.
            
            /*Vertex Vertices[] = {
                {{1, -1, 0}, {1, 0, 0, 1}, {1, 0}},
                {{1, 1, 0}, {0, 1, 0, 1}, {1, 1}},
                {{-1, 1, 0}, {0, 0, 1, 1}, {0, 1}},
                {{-1, -1, 0}, {0, 0, 0, 1}, {0, 0}}
            };*/
            
            float xPos = coordToGLX(-OpenGLCaptureRectX);
            float yPos = coordToGLY(-(OpenGLCaptureAIRStageHeight - (OpenGLCaptureRectY + foundation_sca_h)));
            float xWidth = xPos + 2;
            float yHeight = yPos + 2;
            
            
            
            Vertices[0].Position[0] = xWidth;
            Vertices[0].Position[1] = yPos;
            Vertices[1].Position[0] = xWidth;
            Vertices[1].Position[1] = yHeight;
            Vertices[2].Position[0] = xPos;
            Vertices[2].Position[1] = yHeight;
            Vertices[3].Position[0] = xPos;
            Vertices[3].Position[1] = yPos;
            if (verbose) FWLog(@"capture rect %f %f %f %f, captureRectX %d, captureRectY %d stageWidth %d stageHeight %d", xPos, yPos, xWidth, yHeight, OpenGLCaptureRectX, OpenGLCaptureRectY, OpenGLCaptureAIRStageWidth, OpenGLCaptureAIRStageHeight);

        }
        glBufferData(GL_ARRAY_BUFFER, sizeof(Vertices), Vertices, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(indices), indices, GL_STATIC_DRAW);
        glVertexAttribPointer(_positionSlot, 3, GL_FLOAT, GL_FALSE, sizeof(Vertex), 0);
        glVertexAttribPointer(_colorSlot, 4, GL_FLOAT, GL_FALSE, sizeof(Vertex), (GLvoid*)(sizeof(float)*3));
        glVertexAttribPointer(_texCoordSlot, 2, GL_FLOAT, GL_FALSE, sizeof(Vertex), (GLvoid*)(sizeof(float) * 7));
        
        // rendering to secondary texture cache in case of highres or forced resolution.
        if (scaleFactor > 1 || forceOpenGLResolution) {
            // detach highres texture from FBO
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
            // attach texture cache to FBO
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureCacheFBO, 0);
            
            GLint params;
            
            // remember old depth & stencil attachments if present so we can bind them back later on
            // if there are any issues furhter down the road, this might need to be checked every frame
            // which would suck but there might not be other way...hopefully the depth & stencil names
            // stay the same the first frame as in the all other frames, otherwise this won't work.
            if (AIRDepthBuffer == -1) {
                glGetFramebufferAttachmentParameteriv(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, &params);
            
                FWLog(@"Bound AIR depth attachment: %d", params);
                AIRDepthBuffer = params;
            
                glGetFramebufferAttachmentParameteriv(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, &params);
            
                FWLog(@"Bound AIR stencil attachment: %d", params);
                AIRStencilBuffer = params;
            }
            
            if (verbose) FWLog(@"Detach depth & stencil (if present)");
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, 0);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, 0);

            // render the texture to the primary texture cache(ie the video)
            glClearColor(1.0, 0.0, 0.0, 1.0);
            glClear(GL_COLOR_BUFFER_BIT);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, textureHighResFBO);
            glUniform1i(_textureUniform, 0);
            // scale it to match the video dimensions
            if (!forceOpenGLResolution) {
                // automatic video resolution
                glViewport(0, 0, stageW / scaleFactor, stageH / scaleFactor);
            } else {
                // forced video resoluton
                // when capturing rectangle, render the full viewport, non-scaled down
                if (OpenGLCaptureAIRStageWidth > 0 && OpenGLCaptureAIRStageHeight > 0) {
                    glViewport(0, 0, OpenGLCaptureAIRStageWidth, OpenGLCaptureAIRStageHeight);
                } else {
                    glViewport(0, 0, foundation_sca_w, foundation_sca_h);
                }
            }
            glDrawElements(GL_TRIANGLES, sizeof(indices) / sizeof(indices[0]), GL_UNSIGNED_BYTE, 0);
            glBindTexture(GL_TEXTURE_2D, 0);
            
            // detach texture cache from FBO
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
            // attach highres texture to FBO
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureHighResFBO, 0);
            if (verbose) FWLog(@"Reattach depth & stencil (if present)");
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, AIRDepthBuffer);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, AIRStencilBuffer);
        }
        
        // if we were capturing a rectangle then reset the vertices back to avoid shifting the texture which
        // will be rendered to screen now (we shifed it before when rendering to video)
        if (OpenGLCaptureAIRStageWidth > 0 && OpenGLCaptureAIRStageHeight > 0) {
            Vertices[0].Position[0] = 1;
            Vertices[0].Position[1] = -1;
            Vertices[1].Position[0] = 1;
            Vertices[1].Position[1] = 1;
            Vertices[2].Position[0] = -1;
            Vertices[2].Position[1] = 1;
            Vertices[3].Position[0] = -1;
            Vertices[3].Position[1] = -1;
            glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
            glBufferData(GL_ARRAY_BUFFER, sizeof(Vertices), Vertices, GL_STATIC_DRAW);
        }

        // record so that we always get the right movie fps by grabbing only some frames while running on different fps
        if (stepAccum >= stepTarget) {
          
#ifdef DEMO
            if ((float)videoFramesSent / (float)foundation_fps > 30) {
                throw MyException("No more than 30 seconds recording allowed in free mode. Visit http://www.flashywrappers.com to buy an upgrade.");
            }
#endif
            stepTarget += step;
            if (verbose) FWLog(@"Stepaccum %f >= stepTarget %f, next target is %f", stepAccum, (stepTarget - step), stepTarget);
            
            // this will render out the texture cache to our video
            // TODO, this should go out and instead a function copying the pixel buffer content into our buffer queue
            // in MT mode should be present.
            appendPixelBuffer((bool)glFinishBool);
        } else {
            // render only logo if we're not encoding so that we always get the FW logo on screen (otherwise it would flicker)
            // because what we "render" inside pixel_buffer magically appears back in the texture and thus on screen..coz of texture cache magic
#ifdef DEMO
            unsigned char *buf = (unsigned char*)CVPixelBufferGetBaseAddress(pixel_buffer);
            blitLogoFlipped(buf, foundation_sca_w, 85, 60); 
#endif
            CVPixelBufferUnlockBaseAddress(pixel_buffer, 0);
        }
        
        // now let's draw the FBO texture to screen, we need to detach texture and attach renderbuffer instead..
       
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, AIRRenderBuffer);
        
        // there, now we should be able to render to screen
        
//        glBindFramebuffer(GL_FRAMEBUFFER, oldFBO);
        glClearColor(1.0, 0.0, 0.0, 1.0);
        glClear(GL_COLOR_BUFFER_BIT);
                       
        glViewport(0, 0, stageW, stageH);
        glActiveTexture(GL_TEXTURE0);
        // using only primary texture cache
        if (scaleFactor == 1 && !forceOpenGLResolution) {
            glBindTexture(GL_TEXTURE_2D, textureCacheFBO);
            glUniform1i(_textureUniform, 0);
        } else {
            // using secondary texture cache for rendering
            glBindTexture(GL_TEXTURE_2D, textureHighResFBO);
            glUniform1i(_textureUniform, 0);
        }
        
        glDrawElements(GL_TRIANGLES, sizeof(indices) / sizeof(indices[0]), GL_UNSIGNED_BYTE, 0);
        
        // present to screen
#if TARGET_OS_IPHONE
        [currentContext presentRenderbuffer:GL_RENDERBUFFER];
#else
        [currentContext flushBuffer];
#endif
        
        glBindTexture(GL_TEXTURE_2D, 0);
        glDisableVertexAttribArray(_positionSlot);
        glDisableVertexAttribArray(_colorSlot);
        glDisableVertexAttribArray(_texCoordSlot);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glUseProgram(0);
        
        // before getting back to AIR's render loop, detach the renderbuffer and reattach primary / secondary texture cache again
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, 0);
        if (scaleFactor > 1 || forceOpenGLResolution) {
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,  GL_TEXTURE_2D, textureHighResFBO, 0);
        } else {
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureCacheFBO, 0);
        }
       
        //glGetIntegerv(GL_FRAMEBUFFER_BINDING, &params);
        //NSLog(@"Bound FB after bind texture:%d", params);
        
        // it appears there needs to be at least one Renderbuffer after AIR calls its own to make the switching of render targets 
        // "stick" into AIR. Since we present the buffer for rendering our texture to quad earlier in the code, we can comment this "extra" presentRenderbuffer out
       // [currentContext presentRenderbuffer:GL_RENDERBUFFER];
        
    } else {
        
       // glGetIntegerv(GL_FRAMEBUFFER_BINDING, &params);
       // NSLog(@"Bound FB after bind texture:%d", params);
#if TARGET_OS_IPHONE
#else
        [currentContext flushBuffer];
#endif
    }
    AIRRendered = true;
    if (verbose) FWLog(@"glGetError() state on leaving captureFullscreen: %d", glGetError());
    return nil;
    } catch (MyException &e) {
        FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
        return NULL;
    }
}

// Bind the AIR FBO back after we're done with capturing it
FREObject fw_bindFlashFBO(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    try {
        // only if we initialized the FBO's etc. for fullscreen capture
        if (OpenGLCapture) {
            GLint params;
            //glGetIntegerv(GL_FRAMEBUFFER_BINDING, &params);
            //NSLog(@"Bound FB before bind Flash FBO:%d", params);
            //glBindFramebuffer(GL_FRAMEBUFFER, oldFBO);
            FWLog(@"Detaching texture from FBO");
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
            FWLog(@"Reattaching AIR's renderbuffer to FBO");
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, AIRRenderBuffer);
            glGetIntegerv(GL_FRAMEBUFFER_BINDING, &params);
            FWLog(@"Bound FB after bind Flash FBO:%d", params);
            FWLog(@"Freeing FlashyWrappers OpenGL buffers and textures...");
            glDeleteBuffers(1, &vertexBuffer);
            glDeleteBuffers(1, &indexBuffer);
            if (scaleFactor > 1 || forceOpenGLResolution) {
                glDeleteTextures(1, &textureHighResFBO);
                glDeleteFramebuffers(1, &highresFBO);
            }
            glDeleteFramebuffers(1, &videoFBO);
        }
        return nil;
    } catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}  
}

// feed frame into encoder - for the "classicaL" encoding...
FREObject fw_ffmpeg_addVideoFrame(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    try {
        
	if (realtime || PTSMode == PTS_REALTIME) {
	        CFTimeInterval millis = CFAbsoluteTimeGetCurrent() * 1000;
	        if (millisOld != 0) {
	            delta = millis - millisOld;
	            stepAccum += delta;
	        } else delta = millis;
	        millisOld = millis;
            if (verbose) FWLog(@"Delta:%f millisOld:%f millis:%f acc:%f", delta, millisOld, millis, stepAccum);
	}

// TODO - should have universal switch for all ANE's, for switching framesync
        if (verbose) FWLog(@"Trying to add video frame");
        if (stepAccum >= stepTarget) {
            
#ifdef DEMO
            if ((float)videoFramesSent / (float)foundation_fps > 30) {
                throw MyException("No more than 30 seconds recording allowed in free mode. Visit http://www.flashywrappers.com to buy an upgrade.");
            }
#endif
            stepTarget += step;
            FREByteArray bmd;
            FREAcquireByteArray(argv[0], &bmd);
            unsigned char *data = (unsigned char*)malloc(bmd.length * sizeof(unsigned char));
            memcpy(data, bmd.bytes, bmd.length);
            FREReleaseByteArray(argv[0]);

            /* THREADING */
            if (intermediate_buffer_length > 0) {
                videoDataVector_intermediate.push_back(data);
                if (verbose) FWLog(@"Pushing frame to stack...");
                // if encoder thread is NOT eating data right now give it more data
                if (videoDataVector_intermediate.size() > intermediate_buffer_length && !threadEncodingRunning && !threadDataAvailable) {
                    try_flushAndThread();
                }
            } else {
                if (verbose) FWLog(@"Encoding frame...");
                /* NO THREADING */
                videoDataVector.push_back(data);
                encode_it();
            }
	    // return 1 to indicate the frame as added
	    FREObject result;
	    int ret = 1;
	    FRENewObjectFromInt32(ret, &result);    
 	    return result;  
        }
    if (verbose) FWLog(@"Not added, stepAccum %d, stepTarget %d", stepAccum, stepTarget);

	// return 0 to indicate the frame was skipped
	FREObject result;
	int ret = 0;
	FRENewObjectFromInt32(ret, &result);    
 	return result;  
    } catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
    }       
}

#if TARGET_OS_IPHONE
unsigned char *getPixelsFromUIImage(UIImage *image) {
    CGImageRef imageRef = [image CGImage];
    NSUInteger width = CGImageGetWidth(imageRef);
    NSUInteger height = CGImageGetHeight(imageRef);
    CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
    unsigned char *rawData = (unsigned char*) calloc(height * width * 4, sizeof(unsigned char));
    NSUInteger bytesPerPixel = 4;
    NSUInteger bytesPerRow = bytesPerPixel * width;
    NSUInteger bitsPerComponent = 8;
    CGContextRef context = CGBitmapContextCreate(rawData, width, height, bitsPerComponent, bytesPerRow, colorSpace, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Big);
    CGColorSpaceRelease(colorSpace);
    CGContextDrawImage(context, CGRectMake(0, 0, width, height), imageRef);
    CGContextRelease(context);
    return rawData;
}
#endif

// In threaded mode, asking if the encoding thread has finished and we can return the encoded movie(result)
FREObject fw_ffmpeg_canFinish(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    bool res = canFinish();
    FREObject asResult;
    FRENewObjectFromBool(res, &asResult);
    return asResult;
}    

// iOS only for now, save specified video to Camera Roll
FREObject fw_saveToCameraRoll(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    try {
#if TARGET_OS_IPHONE
        const uint8_t *merged_filename = NULL;
        uint32_t len = 0;    
        FREGetObjectAsUTF8(argv[0], &len, &merged_filename);
        char str_merged_filename[500];
        memcpy(str_merged_filename, merged_filename, len);
        str_merged_filename[len] = 0;
    
        const uint8_t *name  = NULL;
        FREGetObjectAsUTF8(argv[1], &len, &name);
        char str_albumName[500];
        memcpy(str_albumName, name, len);
        str_albumName[len] = 0;

        NSString *merged_path = [NSString stringWithUTF8String:(char*)merged_filename];
        NSString *albumName = [NSString stringWithUTF8String:(char*)str_albumName];
        
        NSURL *filePathURL = [NSURL fileURLWithPath:merged_path isDirectory:NO];
        
        FWLog(@"saving video %@ to camera roll", merged_path);
        
        if (UIVideoAtPathIsCompatibleWithSavedPhotosAlbum([filePathURL relativePath])) {                           
            //UISaveVideoAtPathToSavedPhotosAlbum([filePathURL relativePath], nil, nil, nil);
            //FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("encoded").c_str()), (const uint8_t*)"");
            
            if (albumName.length <= 0) {
            ALAssetsLibrary* library = [[[ALAssetsLibrary alloc] init] autorelease];
            
            [library writeVideoAtPathToSavedPhotosAlbum:filePathURL
                                        completionBlock:^(NSURL *assetURL, NSError *error) {
                                            if (error != nil) {
                                                // this happens usually when the permission for cameraroll is not set
                                                // so throw just a nice cameraroll fail event instead of error so the app
                                                // can handle it
                                                NSString *finalText = [NSString stringWithFormat:@"%@ (%@)", [error localizedDescription], [error localizedFailureReason]];
                                                FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("gallery_failed").c_str()), (const uint8_t*)[finalText UTF8String]);
                                            } else {
                                                FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("gallery_saved").c_str()), (const uint8_t*)"");
                                            }
                                            
                                        }];
            } else {
                    FWLog(@"adding video to album %@", albumName);
                    Class PHPhotoLibrary_class = NSClassFromString(@"PHPhotoLibrary");
                    if (PHPhotoLibrary_class) {
                        
                        [[PHPhotoLibrary sharedPhotoLibrary] performChanges:^{
                            
                            
                            PHFetchOptions *fetchOptions = [PHFetchOptions new];
                            fetchOptions.predicate = [NSPredicate predicateWithFormat:@"title == %@", albumName];
                            
                            NSString *albumID;
                            
                            PHFetchResult *fetchResult = [PHAssetCollection fetchAssetCollectionsWithType:PHAssetCollectionTypeAlbum subtype:PHAssetCollectionSubtypeAlbumRegular options:fetchOptions];
                            
                            PHAssetCollectionChangeRequest *chr;
                            
                            if (!fetchResult || fetchResult.count == 0) {
                                FWLog(@"Album not found, creating a new one");
                                chr = [PHAssetCollectionChangeRequest creationRequestForAssetCollectionWithTitle:albumName];
                            } else {
                                FWLog(@"Album found, using existing");
                                PHAssetCollection *existing = fetchResult.firstObject;
                                chr = [PHAssetCollectionChangeRequest changeRequestForAssetCollection:existing];
                                
                            }
                            albumID = chr.placeholderForCreatedAssetCollection.localIdentifier;
                            FWLog(@"Using album ID %@", albumID);
                            
                            PHAssetChangeRequest *createAssetRequest;
                            createAssetRequest = [PHAssetChangeRequest creationRequestForAssetFromVideoAtFileURL:filePathURL];
                            [chr addAssets:@[createAssetRequest.placeholderForCreatedAsset]];
                            
                        } completionHandler:^(BOOL success, NSError *error) {
                            if (!success) {
                                FWLog(@"Error creating album: %@", error);
                                 NSString *finalText = [NSString stringWithFormat:@"%@ (%@)", [error localizedDescription], [error localizedFailureReason]];
                                 FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("gallery_failed").c_str()), (const uint8_t*)[finalText UTF8String]);
                            } else {
                                FWLog(@"Created album & placed video into it");
                                 FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("gallery_saved").c_str()), (const uint8_t*)"");
                            }
                        }];
                    } else {
                        FWLog(@"Saving to album not supported on iOS < 8");
                    }
            }
        } else {
            throwError(@"Camera roll save failed (corrupted video or wrong resolution, in general width should be multiples of 16)", NULL);
        }
#endif
    } catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}
    return NULL;
}

// Cancel exporting the video with AVAssetExportSession
FREObject fw_exportCancel(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    if (assetExport != NULL) {
        [assetExport cancelExport];
        assetExport = NULL;
    }
    return NULL;
}

// Finish writing the video and clean up
FREObject fw_finish(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    try {
        //CVPixelBufferUnlockBaseAddress(pixel_buffer, 0);
        FWLog(@"Finishing all encoders & cleaning up...");
        CVPixelBufferRelease(pixel_buffer);
        if (OpenGLCapture) {
            if (scaleFactor > 1 || forceOpenGLResolution) {
                CVPixelBufferRelease(pixel_bufferHighres);
                CVPixelBufferPoolRelease(pixel_bufferPoolHighres);
            }
        }
        highresFBO = 0;
        videoFBO = 0;
    
        // clear this..
        foundation_frameCount = 0;
        videoFramesSent = 0;
        videoDataVector.clear();
        videoFramesTotal_intermediate = 0;
        videoDataVector_intermediate.clear();
    
        // stop audio capture
 
        /*if (audioCapture) {
            [audioController removeOutputReceiver:receiver];
        }*/
        [videoWriterInput markAsFinished];
        if (!recordToWAV && audio) {
            [audioWriterInput markAsFinished];
        }
        [videoWriter finishWriting];
        [adaptor release];
        [videoWriter release];
        [videoWriterInput release];
        if (!recordToWAV && audio) {
            [audioWriterInput release];
        }
    } catch (MyException &e) {
        FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
        return NULL;
    }
    return NULL;
}

// Merge tracks

// this really merges all the tracks into one mp4, creating video_merged.mp4...in AS3 ANE wrapper we should read the file
// into ByteArray to simulate the common way this function works.

FREObject fw_ffmpeg_getStream(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    FWLog(@"merging tracks into mp4...");
    
    const uint8_t *video_filename = NULL;
    const uint8_t *audio_filename = NULL;
    const uint8_t *merged_filename = NULL;    
    uint32_t len;
    
    try {
        
        FWLog(@"encodeIt getting paths");
        FREGetObjectAsUTF8(argv[0], &len, &video_filename);
        char str_video_filename[500];
        memcpy(str_video_filename, video_filename, len);
        str_video_filename[len] = 0;
        
        FREGetObjectAsUTF8(argv[1], &len, &audio_filename);
        char str_audio_filename[500];
        memcpy(str_audio_filename, audio_filename, len);
        str_audio_filename[len] = 0;
        
        FREGetObjectAsUTF8(argv[2], &len, &merged_filename);
        char str_merged_filename[500];
        memcpy(str_merged_filename, merged_filename, len);
        str_merged_filename[len] = 0;
       
        
        FWLog(@"encodeIt paths to NSStrings");
       
        
        // Convert path from C string to NSString
        NSString *video_path = [NSString stringWithUTF8String:(char*)video_filename];
        NSString *audio_path = NULL;
        
        FWLog(@"encodeIt paths to NSStrings 1");

        // mix in wav recording?
        if (recordToWAV) {
            audio_path = [NSString stringWithUTF8String:(char*)audio_filename];
            FWLog(@"Reading audio from %@", audio_path);
        }
        
        // list of additional files to mix
        std::vector<NSString*> audioMixFilesNSString;
        
        // mix in additional files?
        if (audioMixFiles.size() > 0) {
            for (int a = 0; a < audioMixFiles.size(); a++) {
                char *audioFile = audioMixFiles[a]->str_filename;
                audioMixFilesNSString.push_back([NSString stringWithUTF8String:(char*)audioFile]);
            }
        }
        
        if (verbose) FWLog(@"encodeIt paths to NSStrings 2");

        NSString *merged_path = [NSString stringWithUTF8String:(char*)merged_filename];
        
        // Microphone recording path (TODO: this is temporary, in the final version the recording in FWSoundMixer will go straight to the mixed buffer, not to separate file)
        
         if (verbose) FWLog(@"encodeIt paths to NSStrings 3");

        NSArray *pathComponents = [NSArray arrayWithObjects:[NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) lastObject], @"Mic.m4a", nil];
        FWLog(@"encodeIt paths to NSStrings 4");

        NSDictionary *micOptions = [NSDictionary dictionaryWithObject:[NSNumber numberWithBool:YES] forKey:AVURLAssetPreferPreciseDurationAndTimingKey];
        
         if (verbose) FWLog(@"encodeIt paths to NSStrings 5");

        AVURLAsset * micAsset = [AVURLAsset URLAssetWithURL:[NSURL fileURLWithPathComponents:pathComponents] options:micOptions];
        
         if (verbose) FWLog(@"encodeIt paths to NSStrings 6");

        BOOL micFileExists = [[NSFileManager defaultManager] fileExistsAtPath:[[NSURL fileURLWithPathComponents:pathComponents] path]];
        
        // Merge audio and video tracks into the final video & audio track
        
         if (verbose) FWLog(@"encodeIt paths to NSStrings 7");

        NSError * error = nil;
        
        AVMutableComposition * composition = [AVMutableComposition composition];
        
        // add video
        AVURLAsset * videoAsset = [AVURLAsset URLAssetWithURL:[NSURL fileURLWithPath:video_path] options:nil];
        
         if (verbose)   FWLog(@"encodeIt paths to NSStrings 8.5");
        NSArray<AVAssetTrack*>* tracks = [videoAsset tracks];
      
           if (verbose)  FWLog(@"encodeIt paths to NSStrings 9");
        if (tracks.count == 0) {
            throw MyException("Tracks count = 0, did you add video frames? If yes this might be a bug.");
        }
        AVAssetTrack * videoAssetTrack = [[videoAsset tracksWithMediaType:AVMediaTypeVideo] objectAtIndex:0];
        
         if (verbose) FWLog(@"encodeIt paths to NSStrings 9");

        
        AVMutableCompositionTrack *compositionVideoTrack = [composition addMutableTrackWithMediaType:AVMediaTypeVideo 
                                                                                    preferredTrackID: kCMPersistentTrackID_Invalid];
        FWLog(@"encodeIt adding video from intermediate MP4, track duration: %f", CMTimeGetSeconds(videoAssetTrack.timeRange.duration));
        [compositionVideoTrack insertTimeRange:videoAssetTrack.timeRange ofTrack:videoAssetTrack atTime:kCMTimeZero
                                         error:&error]; 
        
        
        
        if (error) {
            throwError(@"Insertion of video during final composition failed", error);
        }
        
        // keep the orientation of the original video(in fullscreen its flipped) - shit, works only on iDevice
       // [compositionVideoTrack setPreferredTransform:videoAssetTrack.preferredTransform];
        
       // NSLog(@"encodeIt adding audio track"); 
        
        // add audio
        CMTime audioStartTime = kCMTimeZero;
        
        if (recordToWAV) {
            NSDictionary *audioOptions = [NSDictionary dictionaryWithObject:[NSNumber numberWithBool:YES] forKey:AVURLAssetPreferPreciseDurationAndTimingKey];
            AVURLAsset * audioAsset = [AVURLAsset URLAssetWithURL:[NSURL fileURLWithPath:audio_path] options:audioOptions];
        
            AVAssetTrack * audioAssetTrack = [[audioAsset tracksWithMediaType:AVMediaTypeAudio] objectAtIndex:0];
            AVMutableCompositionTrack *compositionAudioTrack = [composition addMutableTrackWithMediaType:AVMediaTypeAudio 
                                                                                    preferredTrackID: kCMPersistentTrackID_Invalid];
            // clip audio if its longer than video
            
            CMTime duration = audioAsset.duration;
            float audioDurationSec = CMTimeGetSeconds(duration);
            float videoDurationSec = CMTimeGetSeconds(videoAssetTrack.timeRange.duration);
            
            FWLog(@"encodeIt adding audio from intermediate WAV, track duration, track start assumed 0: %f", audioDurationSec);
            
            // if audio length of the WAV is greater than the video length, then clip the audio
            if (audioDurationSec > videoDurationSec) {
                duration.value = videoDurationSec * duration.timescale;
                FWLog(@"audio > video, cutting audio track length to %f", CMTimeGetSeconds(duration));
            }
            
            // insert the WAV audio
            [compositionAudioTrack insertTimeRange:CMTimeRangeMake(kCMTimeZero, duration) ofTrack:audioAssetTrack atTime:audioStartTime error:&error];
        } else {
            // in this variant I'm taking the audio track out of mp4 because we saved audio inside mp4 with AVFoundation
            if ([[videoAsset tracksWithMediaType:AVMediaTypeAudio] count] > 0) {
               
                AVAssetTrack *audioAssetTrack = [[videoAsset tracksWithMediaType:AVMediaTypeAudio] objectAtIndex:0];
                AVMutableCompositionTrack *compositionAudioTrack = [composition addMutableTrackWithMediaType:AVMediaTypeAudio 
                                                                                        preferredTrackID:kCMPersistentTrackID_Invalid];
                // clip audio if its longer than video
                CMTime duration = audioAssetTrack.timeRange.duration;
                float audioDurationSec = CMTimeGetSeconds(duration);
                float videoDurationSec = CMTimeGetSeconds(videoAssetTrack.timeRange.duration);
                
                 FWLog(@"encodeIt adding audio from intermediate MP4, track duration %f, track start in the MP4: %f", audioDurationSec, CMTimeGetSeconds(audioAssetTrack.timeRange.start));
                
                // if audio length of the WAV is greater than the video length, then clip the audio
                if (audioDurationSec > videoDurationSec) {
                    duration.value = videoDurationSec * duration.timescale;
                    FWLog(@"audio > video, cutting audio track length to %f", CMTimeGetSeconds(duration));
                }
                
                // insert the MP4 audio which we recorded "live"
                [compositionAudioTrack insertTimeRange:CMTimeRangeMake(kCMTimeZero, duration) ofTrack:audioAssetTrack atTime:audioStartTime error:&error];

            }
        }
        
        if (error) {
            throwError(@"Insertion of audio during final composition failed", error);
        }
        
        // mix in additional audio files
        if (audioMixFilesNSString.size() > 0) {
            for (int a = 0; a < audioMixFilesNSString.size(); a++) {
                NSString *audioFile = audioMixFilesNSString[a];
                FWLog(@"Mixing in additional file %@", audioFile);
                NSDictionary *audioOptions = [NSDictionary dictionaryWithObject:[NSNumber numberWithBool:YES] forKey:AVURLAssetPreferPreciseDurationAndTimingKey];
                AVURLAsset * audioAsset = [AVURLAsset URLAssetWithURL:[NSURL fileURLWithPath:audioFile] options:audioOptions];
                
                AVAssetTrack * audioAssetTrack = [[audioAsset tracksWithMediaType:AVMediaTypeAudio] objectAtIndex:0];
                AVMutableCompositionTrack *compositionAudioTrack = [composition addMutableTrackWithMediaType:AVMediaTypeAudio
                                                                                            preferredTrackID: kCMPersistentTrackID_Invalid];
                // clip audio if its longer than video
                CMTime duration = audioAsset.duration;
                duration.value = audioMixFiles[a]->audioMixDuration * duration.timescale;
                
                // set start position: add the timestamp in seconds scaled by the asset timescale
                CMTime startPosition = CMTimeMake(audioMixFiles[a]->audioTimeStamp * audioAsset.duration.timescale, audioAsset.duration.timescale);
                float videoDurationSec = CMTimeGetSeconds(videoAssetTrack.timeRange.duration);
                float audioStartSec = audioMixFiles[a]->audioTimeStamp;
                float audioDurationSec = audioMixFiles[a]->audioMixDuration;
                
                // compare if this audio starting somewhere later overflows the length of video.
                if (audioStartSec + audioDurationSec > videoDurationSec) {
                    // if audio track start plus its position is greater than video length then cut it
                    // VL - S
                    duration.value = (videoDurationSec - audioStartSec) * duration.timescale;
                }
                FWLog(@"Audio audioStartSec:%f audioDurationSec:%f startPosition(atTime): %f duration: %f videoDuration: %f, intermediate MP4 duration: %f", audioStartSec, audioDurationSec, CMTimeGetSeconds(startPosition), CMTimeGetSeconds(duration), CMTimeGetSeconds(videoAssetTrack.timeRange.duration), CMTimeGetSeconds(videoAsset.duration));
                [compositionAudioTrack insertTimeRange:CMTimeRangeMake(kCMTimeZero, duration) ofTrack:audioAssetTrack atTime:startPosition error:&error];
            }
        }
        
        if (error) {
            throwError(@"Insertion of additional audio during final composition failed", error);
        }
        
        if (micFileExists) {
            FWLog(@"Found microphone file, mixing in");
            AVAssetTrack * micAssetTrack = [[micAsset tracksWithMediaType:AVMediaTypeAudio] objectAtIndex:0];
            AVMutableCompositionTrack *compositionAudioTrack = [composition addMutableTrackWithMediaType:AVMediaTypeAudio 
                                                                                    preferredTrackID: kCMPersistentTrackID_Invalid];
            
            // clip audio if its longer than video
            
            CMTime duration = micAsset.duration;
            float audioDurationSec = CMTimeGetSeconds(duration);
            float videoDurationSec = CMTimeGetSeconds(videoAssetTrack.timeRange.duration);
            
            FWLog(@"encodeIt adding native microphone from m4a, track start assumed 0: %f", audioDurationSec);
            
            // if audio length of the WAV is greater than the video length, then clip the audio
            if (audioDurationSec > videoDurationSec) {
                duration.value = videoDurationSec * duration.timescale;
                FWLog(@"audio > video, cutting microphone track length to %f", CMTimeGetSeconds(duration));
            }
            
            [compositionAudioTrack insertTimeRange:CMTimeRangeMake(kCMTimeZero, duration) ofTrack:micAssetTrack atTime:audioStartTime error:&error];
            if (error) {
                throwError(@"Insertion of mic during final composition failed", error);
            }
        } else {
            FWLog(@"Native mic recording not found, skipping");
        }
        
        FWLog(@"encodeIt preparing assetExport");
        
        // do the REAL video flip here, actually works
        AVMutableVideoCompositionInstruction *instruction = [AVMutableVideoCompositionInstruction videoCompositionInstruction];
        AVMutableVideoCompositionLayerInstruction *layerInstruction = [AVMutableVideoCompositionLayerInstruction videoCompositionLayerInstructionWithAssetTrack:videoAssetTrack];
  
        AVMutableVideoComposition *videoComposition = [AVMutableVideoComposition videoComposition];
        
        // if we recorded fullscreen it means we need to flip upside down, because the source is OpenGL texture (flipped!)
        if (OpenGLCapture) {
            CGAffineTransform flipTransform = CGAffineTransformMake(1.0, 0.0, 0, -1.0, 0.0, foundation_sca_h);
            [layerInstruction setTransform:flipTransform atTime:kCMTimeZero];
            videoComposition.frameDuration = CMTimeMake(1, foundation_fps);
/*#if not TARGET_OS_IPHONE
            videoComposition.renderScale = 1.0;
#endif*/
            videoComposition.renderSize = CGSizeMake(foundation_sca_w, foundation_sca_h);
            instruction.layerInstructions = [NSArray arrayWithObject: layerInstruction];
            // important, before this was videoAsset.duration, which included audio as well. In case audio was shifted
            // beyond vieo at the end this caused the assetExport choking up as it had no video to process at the end
            instruction.timeRange = CMTimeRangeMake(kCMTimeZero, videoAssetTrack.timeRange.duration);
            videoComposition.instructions = [NSArray arrayWithObject: instruction];
            FWLog(@"Flipping instructions for the video of length %f set", CMTimeGetSeconds(videoAssetTrack.timeRange.duration));
        }
        
        assetExport = [[[AVAssetExportSession alloc] initWithAsset:composition presetName:foundation_native_quality] autorelease];
        
        // again, if fullscreen we need to assign the composition to actually flip 
        if (OpenGLCapture) {
           assetExport.videoComposition = videoComposition;
        }
        assetExport.outputFileType = AVFileTypeMPEG4;// @"com.apple.quicktime-movie";
        assetExport.outputURL = [NSURL fileURLWithPath:merged_path];
        
        // clear the audio mix file instances
        for (int a = 0; a < audioMixFiles.size(); a++) {
            free(audioMixFiles[a]);
        }
        
        audioMixFiles.clear();
        
        FWLog(@"encodeIt doing export");
        
        [assetExport exportAsynchronouslyWithCompletionHandler:
         ^(void ) {
             switch (assetExport.status) 
             {
                 case AVAssetExportSessionStatusCompleted: {
                     // export complete
                     FWLog(@"Export Complete");
                     FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("encoded").c_str()), (const uint8_t*)"");
                 }
                     break;
                 case AVAssetExportSessionStatusFailed:
                     throwErrorFromBlock(@"Exporting of video failed", assetExport.error, ctx);
                     break;
                 case AVAssetExportSessionStatusCancelled:
                     FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("encoding_cancel").c_str()), (const uint8_t*)"");
                    break;
             }
             assetExport = NULL;
         }];    
        
    } catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}
    FWLog(@"encodeIt AVFoundation end");
    return NULL;
}

