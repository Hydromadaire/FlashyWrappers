/*
	FLASHYWRAPPERS (C) 2013+ Pavel Langweil
*/
#include "FlashRuntimeExtensions.h"
#include "stdlib.h"

#ifdef USE_MEDIACODEC
// Format constants
uint32_t foundation_width = 640;
uint32_t foundation_height = 480;
uint32_t foundation_fps = 20;
uint32_t foundation_bitRate = 800000;
uint64_t video_frame_duration = 0;
uint64_t audio_frame_duration = 0;
uint32_t foundation_intermediate_buffer_length = 0;
uint32_t foundation_realtime = 0;
uint32_t foundation_audio = 0;
uint32_t foundation_videoFramesSent = 0;
uint32_t foundation_audio_bit_rate = 0;
uint32_t foundation_audio_channels = 0;
uint32_t foundation_audio_sample_rate = 0;
#endif
extern "C"  
{  
  __declspec(dllexport) void ExtInitializer(void** extDataToSet, FREContextInitializer* ctxInitializerToSet, FREContextFinalizer* ctxFinalizerToSet);  
  
  __declspec(dllexport) void ExtFinalizer(void* extData);  
  
// start, create the instance class
  __declspec(dllexport) FREObject fw_ffmpeg_create(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

// start, free the instance class
  __declspec(dllexport) FREObject fw_ffmpeg_free(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

// init
  __declspec(dllexport) FREObject fw_ffmpeg_init(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

// add video frame
  __declspec(dllexport) FREObject fw_ffmpeg_addVideoFrame(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

// add audio frame
  __declspec(dllexport) FREObject fw_ffmpeg_addAudioFrame(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

// set number of video frames to encode(to be deprecated, I think we don't need it in the end)
  __declspec(dllexport) FREObject fw_ffmpeg_setFrames(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

// get the video stream
  __declspec(dllexport) FREObject fw_ffmpeg_getStream(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

// get the video stream size to know how big the AS3 bytearray should be
  __declspec(dllexport) FREObject fw_ffmpeg_getStreamSize(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

// get the video frames processed count
  __declspec(dllexport) FREObject fw_ffmpeg_getVideoFramesSent(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

// start sink writer
  __declspec(dllexport) FREObject winAPI_startSinkWriter(FREContext ctx, void *funcData, uint32_t argc, FREObject argv[]);

// stop sink writer
  __declspec(dllexport) FREObject winAPI_addVideoFrame(FREContext ctx, void *funcData, uint32_t argc, FREObject argv[]);

  // start sink writer
  __declspec(dllexport) FREObject winAPI_finishSinkWriter(FREContext ctx, void *funcData, uint32_t argc, FREObject argv[]);

  // save video to disk (for large videos), to avoid having to convert to ByteArray and running out of memory
  __declspec(dllexport) FREObject winAPI_saveStream(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);


// can we finish with the encoding or is the thread still running, or do we need to flush some last frames?
  __declspec(dllexport) FREObject fw_ffmpeg_canFinish(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

  __declspec(dllexport) FREObject fw_setPTSMode(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
  __declspec(dllexport) FREObject fw_setFramedropMode(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
  __declspec(dllexport) FREObject fw_setLogging(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

// convert ByteArray from floats to shorts
  __declspec(dllexport) FREObject fw_processByteArrayAudio(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

  __declspec(dllexport) FREObject decoder_flushAudioStream(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
  __declspec(dllexport) FREObject decoder_flushVideoStream(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
  __declspec(dllexport) FREObject decoder_doFrame(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
  __declspec(dllexport) FREObject decoder_getVideoFramesDecoded(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
  __declspec(dllexport) FREObject decoder_getVideoWidth(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
  __declspec(dllexport) FREObject decoder_getVideoHeight(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
  __declspec(dllexport) FREObject decoder_getEOF(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
  __declspec(dllexport) FREObject decoder_bufferFrames(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
  __declspec(dllexport) FREObject decoder_create(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
  __declspec(dllexport) FREObject decoder_free(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);
  __declspec(dllexport) FREObject decoder_getAudioStreamLength(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]);

  void setFramedropMode(uint32_t mode);

}  