/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * Windows FFmpeg / MediaCodec encoder
 *
 */

//# pragma comment (lib, "avformat.lib")
#include "fw_ffmpeg_encoder_WinANE.h"
#ifndef USE_MEDIACODEC
#include "FW_ffmpeg_encode.h"
#include "FW_ffmpeg_decode.h"
#else
#include "WindowsEncoder.h"
#endif
#include "FW_exception.h"

// Encoder class instance
#ifndef USE_MEDIACODEC
FW_ffmpeg_encode *encoderInstance = NULL;	
FW_ffmpeg_decode *decoderInstance = NULL;
#else
WindowsEncoder *encoderInstance = NULL;
#endif
FREContext currentContext;

#include <signal.h>

extern "C" {

// handle abort coming from FFmpeg
void handleAbort(int signal_number)
{
	FREDispatchStatusEventAsync(currentContext, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)("Abort signal catched, terminating. Please contact us at http://www.rainbowcreatures.com"));	
}

// initializer
void ContextInitializer(void* extData, const uint8_t* ctxType, FREContext ctx, uint32_t* numFunctions, const FRENamedFunction** functions)  
{  

  currentContext = ctx;

  *numFunctions = 26;

  FRENamedFunction* func = (FRENamedFunction*)malloc(sizeof(FRENamedFunction)*(*numFunctions));  
  signal(SIGABRT, &handleAbort);
      
  func[0].name = (const uint8_t*)"fw_ffmpeg_create";  
  func[0].functionData = NULL;  
  func[0].function = &fw_ffmpeg_create;  
 
  func[1].name = (const uint8_t*)"fw_ffmpeg_free";  
  func[1].functionData = NULL;  
  func[1].function = &fw_ffmpeg_free;  

  func[2].name = (const uint8_t*)"fw_ffmpeg_addVideoFrame";  
  func[2].functionData = NULL;  
  func[2].function = &fw_ffmpeg_addVideoFrame;  

  func[3].name = (const uint8_t*)"fw_ffmpeg_addAudioFrame";  
  func[3].functionData = NULL;  
  func[3].function = &fw_ffmpeg_addAudioFrame;  

  func[4].name = (const uint8_t*)"fw_ffmpeg_init";  
  func[4].functionData = NULL;  
  func[4].function = &fw_ffmpeg_init;  

  func[5].name = (const uint8_t*)"fw_ffmpeg_getStreamSize";  
  func[5].functionData = NULL;  
  func[5].function = &fw_ffmpeg_getStreamSize;    

  func[6].name = (const uint8_t*)"fw_ffmpeg_getStream";  
  func[6].functionData = NULL;  
  func[6].function = &fw_ffmpeg_getStream;    

  func[7].name = (const uint8_t*)"fw_ffmpeg_setFrames";
  func[7].functionData = NULL;
  func[7].function = &fw_ffmpeg_setFrames;

  func[8].name = (const uint8_t*)"fw_ffmpeg_canFinish";
  func[8].functionData = NULL;
  func[8].function = &fw_ffmpeg_canFinish;

  func[9].name = (const uint8_t*)"fw_ffmpeg_getVideoFramesSent";
  func[9].functionData = NULL;
  func[9].function = &fw_ffmpeg_getVideoFramesSent;

  func[10].name = (const uint8_t*)"fw_processByteArrayAudio";
  func[10].functionData = NULL;
  func[10].function = &fw_processByteArrayAudio;

  func[11].name = (const uint8_t*)"decoder_doFrame";
  func[11].functionData = NULL;
  func[11].function = &decoder_doFrame;

  func[12].name = (const uint8_t*)"decoder_flushVideoStream";
  func[12].functionData = NULL;
  func[12].function = &decoder_flushVideoStream;

  func[13].name = (const uint8_t*)"decoder_flushAudioStream";
  func[13].functionData = NULL;
  func[13].function = &decoder_flushAudioStream;

  func[14].name = (const uint8_t*)"decoder_getVideoFramesDecoded";
  func[14].functionData = NULL;
  func[14].function = &decoder_getVideoFramesDecoded;

  func[15].name = (const uint8_t*)"decoder_getVideoWidth";
  func[15].functionData = NULL;
  func[15].function = &decoder_getVideoWidth;

  func[16].name = (const uint8_t*)"decoder_getVideoHeight";
  func[16].functionData = NULL;
  func[16].function = &decoder_getVideoHeight;

  func[17].name = (const uint8_t*)"decoder_bufferFrames";
  func[17].functionData = NULL;
  func[17].function = &decoder_bufferFrames;

  func[18].name = (const uint8_t*)"decoder_getEOF";
  func[18].functionData = NULL;
  func[18].function = &decoder_getEOF;

  func[19].name = (const uint8_t*)"decoder_create";
  func[19].functionData = NULL;
  func[19].function = &decoder_create;

  func[20].name = (const uint8_t*)"decoder_free";
  func[20].functionData = NULL;
  func[20].function = &decoder_free;

  func[21].name = (const uint8_t*)"decoder_getAudioStreamLength";
  func[21].functionData = NULL;
  func[21].function = &decoder_getAudioStreamLength;

  func[22].name = (const uint8_t*)"fw_setPTSMode";
  func[22].functionData = NULL;
  func[22].function = &fw_setPTSMode;

  func[23].name = (const uint8_t*)"fw_setFramedropMode";
  func[23].functionData = NULL;
  func[23].function = &fw_setPTSMode;

  func[24].name = (const uint8_t*)"fw_setLogging";
  func[24].functionData = NULL;
  func[24].function = &fw_setLogging;

  func[25].name = (const uint8_t*)"winAPI_saveStream";
  func[25].functionData = NULL;
  func[25].function = &winAPI_saveStream;

 *functions = func;  
}  
  
void ContextFinalizer(FREContext ctx)   
{  
  return;  
}  

void ExtInitializer(void** extData, FREContextInitializer* ctxInitializer, FREContextFinalizer* ctxFinalizer)   
{  
	*ctxInitializer = &ContextInitializer;  
	*ctxFinalizer   = &ContextFinalizer;    
#ifdef USE_MEDIACODEC
	encoderInstance = new WindowsEncoder();
	encoderInstance->initMF();
#endif
}   
  
void ExtFinalizer(void* extData)   
{  
#ifdef USE_MEDIACODEC
	encoderInstance->shutdownMF();
	if (encoderInstance != NULL) delete(encoderInstance);
	encoderInstance = NULL;
#endif
	return;  
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
		for (uint32_t a = 0; a < length / 4; a++) {
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
#ifndef USE_MEDIACODEC

		uint32_t sca_frames = 0;
		FREGetObjectAsUint32(argv[0], &sca_frames);
		encoderInstance->ffmpeg_setFrames(sca_frames);
#endif
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
		FREGetObjectAsBool(argv[0], &sca_logging);
		uint32_t sca_verbose = 0;
		FREGetObjectAsBool(argv[1], &sca_verbose);
#ifdef USE_MEDIACODEC
		
		encoderInstance->setLogging(sca_logging, sca_verbose);
#else
		bool logging;
		bool verbose;
		if (sca_logging) {
			logging = true;
		} else {
			logging = false;
		}
		if (sca_verbose) {
			verbose = true;
		} else {
			verbose = false;
		}
		encoderInstance->setLogging(logging, verbose);
#endif
		return NULL;
	}
	catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}
}

// get how many frames we've sent to the encoder
FREObject fw_ffmpeg_getVideoFramesSent(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
	FREObject asResult;
#ifndef USE_MEDIACODEC
	uint32_t res = encoderInstance->ffmpeg_getVideoFramesSent();
#else
	uint32_t res = encoderInstance->getVideoFramesSent();
#endif
	FRENewObjectFromUint32(res, &asResult);
	return asResult;
}

// create the instance class
FREObject fw_ffmpeg_create(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
#ifndef USE_MEDIACODEC
	if (encoderInstance != NULL) delete(encoderInstance);
	encoderInstance = new FW_ffmpeg_encode();
#else
//	if (encoderInstance != NULL) delete(encoderInstance);
//	encoderInstance = new WindowsEncoder();
#endif
	return NULL;
}

// free the instance class
FREObject fw_ffmpeg_free(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {		
	//if (encoderInstance != NULL) delete(encoderInstance);
	//encoderInstance = NULL;
	return NULL;
}

// free decoder class
FREObject decoder_create(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
#ifndef USE_MEDIACODEC
	decoderInstance = new FW_ffmpeg_decode();
#endif
	return NULL;
}

// free decoder class
FREObject decoder_free(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
#ifndef USE_MEDIACODEC

	delete(decoderInstance);
	decoderInstance = NULL;
#endif
	return NULL;
}


// init
FREObject fw_ffmpeg_init(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {

	try {

#ifndef USE_MEDIACODEC
	uint32_t sca_w = 0;
	uint32_t sca_h = 0;
	uint32_t sca_bitrate = 0;
	uint32_t sca_quality = 0;
	uint32_t sca_fps = 0;
   	uint32_t sca_speed = 0;
	uint32_t sca_keyframe_interval = 0;
	uint32_t audio = 0;
	const uint8_t *str_container = NULL;
	const uint8_t *str_codec_video = NULL;
	const uint8_t *str_codec_audio = NULL;
	uint32_t realtime, audio_bit_rate, audio_channels, audio_sample_rate, intermediate_buffer_length;

	uint32_t len;

	FREGetObjectAsUTF8(argv[0], &len, &str_container);
	char str_container2[50];
	memcpy(str_container2, str_container, len);
	str_container2[len] = 0;
	FREGetObjectAsUTF8(argv[1], &len, &str_codec_video);
	char str_codec_video2[50];
	memcpy(str_codec_video2, str_codec_video, len);
	str_codec_video2[len] = 0;
	FREGetObjectAsUTF8(argv[2], &len, &str_codec_audio);
	char str_codec_audio2[50];
	memcpy(str_codec_audio2, str_codec_audio, len);
	str_codec_audio2[len] = 0;
	FREGetObjectAsUint32(argv[3], &sca_w);
	FREGetObjectAsUint32(argv[4], &sca_h);
	FREGetObjectAsUint32(argv[5], &sca_fps);
	FREGetObjectAsUint32(argv[6], &sca_speed);

	FREGetObjectAsUint32(argv[7], &sca_bitrate);
	FREGetObjectAsUint32(argv[8], &sca_quality);
	FREGetObjectAsUint32(argv[9], &sca_keyframe_interval);
	FREGetObjectAsUint32(argv[10], &audio_sample_rate);
	FREGetObjectAsUint32(argv[11], &audio_channels);
	FREGetObjectAsUint32(argv[12], &audio_bit_rate);
	FREGetObjectAsUint32(argv[13], &intermediate_buffer_length);
	FREGetObjectAsUint32(argv[14], &realtime);
	FREGetObjectAsUint32(argv[15], &audio);

	int res = encoderInstance->ffmpeg_init((uint8_t*)str_container2, (uint8_t*)str_codec_video2, (uint8_t*)str_codec_audio2, sca_w, sca_h, sca_fps, sca_speed, sca_bitrate, sca_quality, sca_keyframe_interval, audio_sample_rate, audio_channels, audio_bit_rate, intermediate_buffer_length, realtime, audio);
	FREObject asResult;
	FRENewObjectFromInt32(res, &asResult);
	return asResult;
#else 
	FREGetObjectAsUint32(argv[3], &foundation_width);
	FREGetObjectAsUint32(argv[4], &foundation_height);
	FREGetObjectAsUint32(argv[5], &foundation_fps);
	FREGetObjectAsUint32(argv[7], &foundation_bitRate);
	FREGetObjectAsUint32(argv[10], &foundation_audio_sample_rate);
	FREGetObjectAsUint32(argv[11], &foundation_audio_channels);
	FREGetObjectAsUint32(argv[12], &foundation_audio_bit_rate);
	FREGetObjectAsUint32(argv[13], &foundation_intermediate_buffer_length);
	FREGetObjectAsUint32(argv[14], &foundation_realtime);
	FREGetObjectAsUint32(argv[15], &foundation_audio);
	encoderInstance->init(foundation_width, foundation_height, foundation_fps, foundation_bitRate, foundation_audio_sample_rate, foundation_audio_channels,
						  foundation_audio_bit_rate, foundation_intermediate_buffer_length, foundation_realtime, foundation_audio);
	int res = 1;
	FREObject asResult;
	FRENewObjectFromInt32(res, &asResult);
	return asResult;

#endif
	} catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}

}

// feed frame into encoder
FREObject fw_ffmpeg_addVideoFrame(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
	try {
		FREByteArray bmd;
		FREAcquireByteArray(argv[0], &bmd);
		unsigned char *data = (unsigned char*)malloc(bmd.length * sizeof(unsigned char));
		uint32_t length = bmd.length;
		memcpy(data, bmd.bytes, bmd.length);
		FREReleaseByteArray(argv[0]);

		FREObject result;

#ifndef USE_MEDIACODEC
		int ret = encoderInstance->ffmpeg_addVideoData((unsigned char*)data, length);	
#else
		int ret = encoderInstance->addVideoData((unsigned char*)data, length);
#endif
		FRENewObjectFromInt32(ret, &result);
		return result;	
	} catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}
}

FREObject fw_ffmpeg_canFinish(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
#ifndef USE_MEDIACODEC
	bool res = encoderInstance->canFinish();
#else
	bool res = true;
#endif
	FREObject asResult;
	FRENewObjectFromBool(res, &asResult);
	return asResult;
}

// override pts mode
FREObject fw_setPTSMode(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
	try {
		uint32_t sca_mode = 0;
		FREGetObjectAsUint32(argv[0], &sca_mode);
		encoderInstance->setPTSMode(sca_mode);
	} catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}
}

// override framedrop mode
FREObject fw_setFramedropMode(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
	try {
		uint32_t sca_mode = 0;
		FREGetObjectAsUint32(argv[0], &sca_mode);
		encoderInstance->setFramedropMode(sca_mode);
	}
	catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}
}

// feed frame into encoder
FREObject fw_ffmpeg_addAudioFrame(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {

	try {
	
	FREByteArray bmd;
	FREAcquireByteArray(argv[0], &bmd);
	unsigned char *data = (unsigned char*)malloc(bmd.length * sizeof(unsigned char));
	uint32_t length = bmd.length;
	memcpy(data, bmd.bytes, bmd.length);
	FREReleaseByteArray(argv[0]);

	#ifndef USE_MEDIACODEC
	encoderInstance->ffmpeg_addAudioData((unsigned char*)data, length);
	#else
	encoderInstance->addAudioData((unsigned char*)data, length);
	#endif
	free(data);

	return NULL;

	} catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}

}

FREObject decoder_flushVideoStream(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
	try {
#ifndef USE_MEDIACODEC

	FREByteArray byteArray;

	// get the video stream from decoder
	std::stringstream *return_buffer = decoderInstance->getVideoStream();

	// flush stream
	return_buffer->flush();

 	// find out stream size
	return_buffer->seekp(0, std::ios_base::end);
	size_t output_buffer_size = return_buffer->tellp();
	return_buffer->seekp(0, std::ios_base::beg);

	// copy output buffer into output domain memory
	char *sca_outData = (char*)malloc(output_buffer_size);

	// TODO: read directly into byteArray, saves one memcpy
	// fill return buffer
	return_buffer->read((char*)sca_outData, output_buffer_size);    
	
	// copy the outDataPtr from AS3 to C++ sca_outData
	FREAcquireByteArray(argv[0], &byteArray);
	memcpy(byteArray.bytes, sca_outData, output_buffer_size);
	FREReleaseByteArray(argv[0]);

	// free the temp data which should now be in ByteArray
	free(sca_outData);

	// empty the videoStream
	return_buffer->str("");
#endif
	return NULL;

	} catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}
}


FREObject decoder_getAudioStreamLength(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
	try {
#ifndef USE_MEDIACODEC

	FREObject result;
	FREByteArray byteArray;

	// get the audio stream from decoder
	std::stringstream *return_buffer = decoderInstance->getAudioStream();

	// flush stream
	return_buffer->flush();

 	// find out stream size
	
	return_buffer->seekp(0, std::ios_base::end);
	size_t output_buffer_size = return_buffer->tellp();
	return_buffer->seekp(0, std::ios_base::beg);

	FRENewObjectFromDouble(output_buffer_size, &result);    
	return result;  
#endif
	} catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}
	
}

FREObject decoder_flushAudioStream(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
	try {
#ifndef USE_MEDIACODEC

	FREObject result;

	FREByteArray byteArray;

	// get the video stream from decoder
	std::stringstream *return_buffer = decoderInstance->getAudioStream();

	// flush stream
	return_buffer->flush();

 	// find out stream size
	
	return_buffer->seekp(0, std::ios_base::end);
	size_t output_buffer_size = return_buffer->tellp();
	return_buffer->seekp(0, std::ios_base::beg);


	// copy output buffer into output domain memory
	char *sca_outData = (char*)malloc(output_buffer_size);

	// fill return buffer
	return_buffer->read((char*)sca_outData, output_buffer_size);    
	
	// copy the outDataPtr from AS3 to C++ sca_outData and set its length as well
	FREAcquireByteArray(argv[0], &byteArray);
	memcpy(byteArray.bytes, sca_outData, output_buffer_size);
	FREReleaseByteArray(argv[0]);

	// free the temp data which should now be in ByteArray
	free(sca_outData);   

	// empty the audioStream
	return_buffer->str("");

	FRENewObjectFromDouble(output_buffer_size, &result);    
	return result;  
#endif

	} catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}
}

FREObject decoder_getVideoFramesDecoded(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
	FREObject result;
#ifndef USE_MEDIACODEC
	long res = decoderInstance->getVideoFramesCount();
	FRENewObjectFromDouble(res, &result);    
	return result;  
#else
	return NULL;
#endif
}

FREObject decoder_getVideoWidth(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
	FREObject result;
#ifndef USE_MEDIACODEC
	long res = decoderInstance->getVideoWidth();
	FRENewObjectFromDouble(res, &result);    
	return result;  
#else
	return NULL;
#endif
}

FREObject decoder_getVideoHeight(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
	FREObject result;
#ifndef USE_MEDIACODEC
	long res = decoderInstance->getVideoHeight();
	FRENewObjectFromDouble(res, &result);    
	return result;  
#else
	return NULL;
#endif
}

FREObject decoder_getEOF(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
	FREObject result;
#ifndef USE_MEDIACODEC
	bool res = decoderInstance->eof;
	FRENewObjectFromBool(res, &result);    
	return result;  
#else
	return NULL;
#endif
}

FREObject decoder_bufferFrames(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
	uint32_t frameCount = 0;
#ifndef USE_MEDIACODEC
	FREGetObjectAsUint32(argv[0], &frameCount);
	decoderInstance->runBufferFrames(frameCount);
	return NULL;
#else
	return NULL;
#endif

}

FREObject decoder_doFrame(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
	FREObject result;
#ifndef USE_MEDIACODEC
	long res = decoderInstance->doFrame();	
	// if BGRA frame is available, write it to the bitmapData we supply and force Flash to rerender
	if (res == 2) {
		FREBitmapData2 bmp;
		FREAcquireBitmapData2(argv[0], &bmp);
		// copy frame data to bitmap
		memcpy((unsigned char*)bmp.bits32, (unsigned char*)decoderInstance->frameBGRA->data[0], bmp.width * bmp.height * 4);
		FREInvalidateBitmapDataRect(argv[0], 0, 0, bmp.width, bmp.height);
		FREReleaseBitmapData(argv[0]);
	}
	FRENewObjectFromDouble(res, &result);    
	return result;  
#else
	return NULL;
#endif
}

FREObject fw_ffmpeg_getStream(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {

	try {

	// get the byteArray from AS3 argument
	FREByteArray byteArray;

#ifndef USE_MEDIACODEC

	// get the C++ stream with the resulting GIF
	std::stringstream *return_buffer = encoderInstance->ffmpeg_getStream();

	// flush any remaining bytes
	return_buffer->flush();

	// find out stream size
	return_buffer->seekp(0, std::ios_base::end);
	size_t output_buffer_size = return_buffer->tellp();
	return_buffer->seekp(0, std::ios_base::beg);

	// create temp buffer storage of char* type which we fill from return_buffer stringstream
	char *tmp = (char*)malloc(output_buffer_size);

	// fill the byteArray with stringstream return_buffer (we found out the size earlier above - the byteArray MUST be initialized with the same size in AS3 - we can get that size with getStreamSize)
	return_buffer->read(tmp, output_buffer_size);

#else	
	QWORD output_buffer_size = 0;
	// create temp buffer storage of char* type which we fill from return_buffer stringstream
	unsigned char *tmp = encoderInstance->getStream(&output_buffer_size);
#endif
	
	// copy the outDataPtr from AS3 to C++ sca_outData
	FREAcquireByteArray(argv[0], &byteArray);
	memcpy(byteArray.bytes, tmp, output_buffer_size);
	FREReleaseByteArray(argv[0]);

	// fill the decoder buffer with the last video (it might play replay later on)
//	decoderInstance->init(tmp, output_buffer_size);	  

	// we don't need tmp now, init copied it into its own buffer
	free(tmp);

#ifndef USE_MEDIACODEC
	// erase the out stream from memory now when it was used to fill a buffer
	encoderInstance->ffmpeg_reset();
#else
#endif
	return NULL;

	} catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}
}

FREObject winAPI_saveStream(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
	try {
		uint32_t len = 0;
		const uint8_t *str_container = NULL;
		FREGetObjectAsUTF8(argv[0], &len, &str_container);
		char str_container2[2048];
		memcpy(str_container2, str_container, len);
		str_container2[len] = 0;
		encoderInstance->getStreamSize();
		QWORD output_buffer_size = 0;		
		unsigned char *tmp = encoderInstance->getStream(&output_buffer_size);
		FILE* file = fopen(str_container2, "wb");
		fwrite(tmp, output_buffer_size, 1, file);
		fflush(file);
		free(tmp);
	} catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}
}

FREObject fw_ffmpeg_getStreamSize(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])   
{  
	try {

	FREObject result;  

#ifndef USE_MEDIACODEC
	// flush any remaining packets into the stream now
	encoderInstance->flush();

	// finish before we get the final stream
	encoderInstance->ffmpeg_finish();

	// get the C++ stream with the resulting GIF
	std::stringstream *return_buffer = encoderInstance->ffmpeg_getStream();

	// flush any remaining bytes
	return_buffer->flush();

	// find out stream size
	return_buffer->seekp(0, std::ios_base::end);
	size_t output_buffer_size = return_buffer->tellp();
	return_buffer->seekp(0, std::ios_base::beg);

#else	
	QWORD output_buffer_size = encoderInstance->getStreamSize();
#endif

	FRENewObjectFromDouble(output_buffer_size, &result);  
  
	return result;  

	} catch (MyException &e) {
		FREDispatchStatusEventAsync(ctx, (const uint8_t*)(std::string("error").c_str()), (const uint8_t*)e.what());
		return NULL;
	}

}  

}
