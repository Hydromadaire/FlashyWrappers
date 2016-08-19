/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * Flash FFmpeg encoder wrapper for the common cpp files. You could create similar wrappers for any other platforms and build FFmpeg based encoders for them, the C++ code in "common" is crossplatform.
 *
 */

#include <AS3/AS3.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <iostream>
#include "../common/FW_ffmpeg_encode.h"
#include "../common/FW_ffmpeg_decode.h"
#include "../common/FW_exception.h"

extern "C" {

FW_ffmpeg_encode* encoderInstance = NULL;
FW_ffmpeg_decode* decoderInstance = NULL;

// Base 64 encoding
static char encoding_table[] = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
                                'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
                                'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
                                'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
                                'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
                                'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
                                'w', 'x', 'y', 'z', '0', '1', '2', '3',
                                '4', '5', '6', '7', '8', '9', '+', '/'};
static int mod_table[] = {0, 2, 1};

// Take C++ Exception and throw it in AS3 through AS3 exception
void handleException(MyException &e) {
	// Convert C++ Exception into AS3 (dispatch status event)
	AS3_DeclareVar(myString, String);
	AS3_CopyCStringToVar(myString, (const char*)e.what(), strlen((const char*)e.what()));		
	inline_as3(
	    "throw new Error('[FlashyWrappers error] ' + myString);"
	);
}

void ffmpeg_init() __attribute__((used,
          annotate("as3sig:public function ffmpeg_init(container:String='mp4', codecVideo:String='libx264', codecAudio:String='libmp3lame', w:int = 200, h:int = 200, fps:int = 20, speed:int = 8, bitrate:int = 300000, quality:int = 23, as3_keyframe_frequency:int = 10, as3_audio_sample_rate:int = 44100, as3_audio_channels:int = 2, as3_audio_bit_rate:int = 64000, intermediate_buffer_length:int = 20, realtime:int = 1, audio:int = 0):int"),
          annotate("as3package:com.fw_ffmpeg")));
void ffmpeg_init() {

	encoderInstance = new FW_ffmpeg_encode();
	decoderInstance = new FW_ffmpeg_decode();

	int sca_w = 0;
	int sca_h = 0;
	int sca_bitrate = 0;
	int sca_quality = 0;
	int sca_fps = 0;
   	int sca_speed = 0;
	int sca_keyframe_interval = 0;
	int sca_intermediate_buffer_length = 0;
   	int sca_realtime = 0;
   	int sca_audio = 0;

	unsigned char *str_container = NULL;
	unsigned char *str_codec_video = NULL;
	unsigned char *str_codec_audio = NULL;
	int audio_bit_rate, audio_channels, audio_sample_rate;

	// TODO: Missing free of those 3?	
	AS3_MallocString(str_container, container);
	AS3_MallocString(str_codec_video, codecVideo);
	AS3_MallocString(str_codec_audio, codecAudio);

	AS3_GetScalarFromVar(sca_fps, fps); 
	AS3_GetScalarFromVar(sca_w, w);
	AS3_GetScalarFromVar(sca_h, h); 
	AS3_GetScalarFromVar(sca_speed, speed); 
	AS3_GetScalarFromVar(sca_bitrate, bitrate);
	AS3_GetScalarFromVar(sca_quality, quality);
	AS3_GetScalarFromVar(sca_keyframe_interval, as3_keyframe_frequency);
	AS3_GetScalarFromVar(audio_sample_rate, as3_audio_sample_rate);
	AS3_GetScalarFromVar(audio_channels, as3_audio_channels);
	AS3_GetScalarFromVar(audio_bit_rate, as3_audio_bit_rate);
	AS3_GetScalarFromVar(sca_intermediate_buffer_length, intermediate_buffer_length);
	AS3_GetScalarFromVar(sca_realtime, realtime);
	AS3_GetScalarFromVar(sca_audio, audio); 

	try {
		int res = encoderInstance->ffmpeg_init(str_container, str_codec_video, str_codec_audio, sca_w, sca_h, sca_fps, sca_speed, sca_bitrate, sca_quality, sca_keyframe_interval, audio_sample_rate, audio_channels, audio_bit_rate, sca_intermediate_buffer_length, sca_realtime, sca_audio);
		AS3_DeclareVar(asresult, int);
		AS3_CopyScalarToVar(asresult, res);	
		AS3_ReturnAS3Var(asresult);	
	} catch (MyException &e) { 
		handleException(e);
	}
}

void ffmpeg_canFinish() __attribute__((used,
          annotate("as3sig:public function ffmpeg_canFinish():Boolean"),
          annotate("as3package:com.fw_ffmpeg")));
void ffmpeg_canFinish() {
	bool res = encoderInstance->canFinish();
	AS3_DeclareVar(asRes, Boolean);
	AS3_CopyScalarToVar(asRes, res);
	AS3_ReturnAS3Var(asRes);		
}

void ffmpeg_initFinished() __attribute__((used,
          annotate("as3sig:public function ffmpeg_initFinished():Boolean"),
          annotate("as3package:com.fw_ffmpeg")));
void ffmpeg_initFinished() {
	bool res = encoderInstance->initThreadFinished();
	AS3_DeclareVar(asRes, Boolean);
	AS3_CopyScalarToVar(asRes, res);
	AS3_ReturnAS3Var(asRes);		
}

void ffmpeg_encodeit() __attribute__((used,
		annotate("as3sig:public function ffmpeg_encodeit():ByteArray"),
		annotate("as3import:flash.utils.ByteArray"),
		annotate("as3package:com.fw_ffmpeg")));
void ffmpeg_encodeit() {


	try {

	// flush any remaining packets into the stream now
	encoderInstance->flush();

	// finish before returning the final stream
	encoderInstance->ffmpeg_finish();    

	// get the stream
	std::stringstream *return_buffer = encoderInstance->ffmpeg_getStream();

	// flush stream
	return_buffer->flush();

 	// find out stream size
	return_buffer->seekp(0, std::ios_base::end);
	size_t output_buffer_size = return_buffer->tellp();
	return_buffer->seekp(0, std::ios_base::beg);

 	// allocate memory in domain buffer so we can prepare it for Flash

	inline_as3(
	    "var outDataPtr:int = CModule.malloc(%0);\n"
	    : : "r"(output_buffer_size)
	);

	// copy output buffer into output domain memory

	char *sca_outData = NULL;
	AS3_GetScalarFromVar(sca_outData, outDataPtr);

	// fill return buffer
	return_buffer->read((char*)sca_outData, output_buffer_size);    

	// fill the decoder buffer with the last video (it might play replay later on)
	// must rewind the return buffer first
//	decoderInstance->init(sca_outData, output_buffer_size);

	// copy the domain memory buffer into ByteArray which we return
	inline_as3(
	"var asresult:ByteArray = new ByteArray();\n"
                    "CModule.readBytes(%0, %1, asresult);\n"
                    : : "r"(sca_outData), "r"(output_buffer_size)
 	);      

	// reset the memory and other stuff 
	encoderInstance->ffmpeg_reset();
	
	// free the instance, it gets initialized in init again
	free(encoderInstance);
	encoderInstance = NULL;	

	// return buffer
	AS3_ReturnAS3Var(asresult);

	} catch (MyException &e) {
		handleException(e);
	}
}

void decoder_doFrame() __attribute__((used,
          annotate("as3sig:public function decoder_doFrame():Number"),
          annotate("as3package:com.fw_ffmpeg")));
void decoder_doFrame() {
	long res = decoderInstance->doFrame();	
	// all frames decoded
	if (res == 0) {
		free(decoderInstance);
		decoderInstance = NULL;
	}
	AS3_DeclareVar(asRes, Number);
	AS3_CopyScalarToVar(asRes, res);
	AS3_ReturnAS3Var(asRes);		
}


void decoder_flushVideoStream() __attribute__((used,
		annotate("as3sig:public function decoder_flushVideoStream(ba:ByteArray)"),
		annotate("as3import:flash.utils.ByteArray"),
		annotate("as3package:com.fw_ffmpeg")));
void decoder_flushVideoStream() {

	try {

	// get the video stream from decoder
	std::stringstream *return_buffer = decoderInstance->getVideoStream();

	// flush stream
	return_buffer->flush();

 	// find out stream size
	return_buffer->seekp(0, std::ios_base::end);
	size_t output_buffer_size = return_buffer->tellp();
	return_buffer->seekp(0, std::ios_base::beg);

 	// allocate memory in domain buffer so we can prepare it for Flash
	inline_as3(
	    "var outDataPtr:int = CModule.malloc(%0);\n"
	    : : "r"(output_buffer_size)
	);

	// copy output buffer into output domain memory
	char *sca_outData = NULL;
	AS3_GetScalarFromVar(sca_outData, outDataPtr);

	// fill return buffer
	return_buffer->read((char*)sca_outData, output_buffer_size);    

	// copy the domain memory buffer into ByteArray which we supplied
	inline_as3(
                    "CModule.readBytes(%0, %1, ba);\n"
                    : : "r"(sca_outData), "r"(output_buffer_size)
 	);      
	// free the temp data which should now be in ByteArray
	free(sca_outData);

	// empty the videoStream
	return_buffer->str("");

	} catch (MyException &e) {
		handleException(e);
	}
}

void decoder_flushAudioStream() __attribute__((used,
		annotate("as3sig:public function decoder_flushAudioStream(ba:ByteArray)"),
		annotate("as3import:flash.utils.ByteArray"),
		annotate("as3package:com.fw_ffmpeg")));
void decoder_flushAudioStream() {

	try {

	// get the audio stream from decoder
	std::stringstream *return_buffer = decoderInstance->getAudioStream();

	// flush stream
	return_buffer->flush();

 	// find out stream size
	return_buffer->seekp(0, std::ios_base::end);
	size_t output_buffer_size = return_buffer->tellp();
	return_buffer->seekp(0, std::ios_base::beg);

 	// allocate memory in domain buffer so we can prepare it for Flash
	inline_as3(
	    "var outDataPtr:int = CModule.malloc(%0);\n"
	    : : "r"(output_buffer_size)
	);

	// copy output buffer into output domain memory
	char *sca_outData = NULL;
	AS3_GetScalarFromVar(sca_outData, outDataPtr);

	// fill return buffer
	return_buffer->read((char*)sca_outData, output_buffer_size);    

	// copy the domain memory buffer into ByteArray which we supplied
	inline_as3(
                    "CModule.readBytes(%0, %1, ba);\n"
                    : : "r"(sca_outData), "r"(output_buffer_size)
 	);      
	// free the temp data which should now be in ByteArray
	free(sca_outData);

	// empty the audioStream
	return_buffer->str("");

	} catch (MyException &e) {
		handleException(e);
	}
}


void decoder_getAudioStreamLength() __attribute__((used,
          annotate("as3sig:public function decoder_getAudioStreamLength():Number"),
          annotate("as3package:com.fw_ffmpeg")));
void decoder_getAudioStreamLength() {
	try {

	// get the audio stream from decoder
	std::stringstream *return_buffer = decoderInstance->getAudioStream();

	// flush stream
	return_buffer->flush();

 	// find out stream size	
	return_buffer->seekp(0, std::ios_base::end);
	size_t output_buffer_size = return_buffer->tellp();
	return_buffer->seekp(0, std::ios_base::beg);

	AS3_DeclareVar(asRes, Number);
	AS3_CopyScalarToVar(asRes, output_buffer_size);
	AS3_ReturnAS3Var(asRes);		

	} catch (MyException &e) {
		handleException(e);
	}	
}



char *base64_encode(const unsigned char *data,
                    size_t input_length,
                    size_t *output_length) {

    *output_length = 4 * ((input_length + 2) / 3);

    char *encoded_data = (char*)malloc(*output_length);
    if (encoded_data == NULL) return NULL;

    for (unsigned int i = 0, j = 0; i < input_length;) {

        uint32_t octet_a = i < input_length ? (unsigned char)data[i++] : 0;
        uint32_t octet_b = i < input_length ? (unsigned char)data[i++] : 0;
        uint32_t octet_c = i < input_length ? (unsigned char)data[i++] : 0;

        uint32_t triple = (octet_a << 0x10) + (octet_b << 0x08) + octet_c;

        encoded_data[j++] = encoding_table[(triple >> 3 * 6) & 0x3F];
        encoded_data[j++] = encoding_table[(triple >> 2 * 6) & 0x3F];
        encoded_data[j++] = encoding_table[(triple >> 1 * 6) & 0x3F];
        encoded_data[j++] = encoding_table[(triple >> 0 * 6) & 0x3F];
    }

    for (int i = 0; i < mod_table[input_length % 3]; i++)
        encoded_data[*output_length - 1 - i] = '=';

    return encoded_data;
}


void ffmpeg_encodeitBase64() __attribute__((used,
		annotate("as3sig:public function ffmpeg_encodeitBase64():ByteArray"),
		annotate("as3import:flash.utils.ByteArray"),
		annotate("as3package:com.fw_ffmpeg")));
void ffmpeg_encodeitBase64() {


	try {

	// flush any remaining packets into the stream now
	encoderInstance->flush();

	// finish before returning the final stream
	encoderInstance->ffmpeg_finish();    

	// get the stream
	std::stringstream *return_buffer = encoderInstance->ffmpeg_getStream();

	// flush stream
	return_buffer->flush();

 	// find out stream size
	return_buffer->seekp(0, std::ios_base::end);
	size_t base64_buffer_size = return_buffer->tellp();
	size_t output_buffer_size = 0;
	return_buffer->seekp(0, std::ios_base::beg);

	char *base64in = NULL;
	// fill the return buffer
	return_buffer->read((char*)base64in, base64_buffer_size);

	// fill the decoder buffer with the last video(it might play replay later on)
	decoderInstance->init(base64in, base64_buffer_size);

	char *base64out = base64_encode((const unsigned char*)base64in, base64_buffer_size, &output_buffer_size);

 	// allocate memory in domain buffer so we can prepare it for Flash

	inline_as3(
	    "var outDataPtr:int = CModule.malloc(%0);\n"
	    : : "r"(output_buffer_size)
	);

	// copy output buffer into output domain memory

	char *sca_outData = NULL;
	AS3_GetScalarFromVar(sca_outData, outDataPtr);

	// copy base64 video to output buffer
	memcpy(sca_outData, base64out, output_buffer_size);

	// fill the ByteArray with base64 and return
	inline_as3(
	"var asresult:ByteArray = new ByteArray();\n"
                    "CModule.readBytes(%0, %1, asresult);\n"
                    : : "r"(sca_outData), "r"(output_buffer_size)
 	);      

	// reset the memory and other stuff 
	encoderInstance->ffmpeg_reset();
	
	// free the instance, it gets initialized in init again
	free(encoderInstance);
	free(base64out);
	free(base64in);
	free(sca_outData);
	encoderInstance = NULL;	

	// return buffer
	AS3_ReturnAS3Var(asresult);

	} catch (MyException &e) {
		handleException(e);
	}
}

void ffmpeg_addVideoData() __attribute__((used,
          annotate("as3sig:public function ffmpeg_addVideoData(data:ByteArray):int"),
          annotate("as3package:com.fw_ffmpeg"),
          annotate("as3import:flash.utils.ByteArray"),
          annotate("as3import:flash.display.BitmapData")));
void ffmpeg_addVideoData()
{
 	inline_as3(
 		"var dataPtr:int = CModule.malloc(data.length);\n"
 		"CModule.writeBytes(dataPtr, data.length, data);\n"
 		"var dataSize:int = data.length;\n"            
 		: :
 	);

	// TODO: Directly write to memory - it switches around A,R,G,B in the final frame why?	
/* 	inline_as3(
 		"var dataPtr:int = CModule.malloc(data.rect.width * data.rect.height * 4);\n"
		"CModule.ram.position = dataPtr;\n"
 		"data.copyPixelsToByteArray(data.rect, CModule.ram);\n"
 		"var dataSize:int = (data.rect.width * data.rect.height) * 4;\n"            
 		: :
 	);*/

 	unsigned char* sca_data = 0;
 	unsigned long sca_data_size = 0;
 	
 	AS3_GetScalarFromVar(sca_data, dataPtr);
 	AS3_GetScalarFromVar(sca_data_size, dataSize);

	try {
		// return whenever video data was really added or not
		int res = encoderInstance->ffmpeg_addVideoData(sca_data, sca_data_size);
		AS3_DeclareVar(asRes, Number);
		AS3_CopyScalarToVar(asRes, res);
		AS3_ReturnAS3Var(asRes);				
	} catch (MyException &e) {
		handleException(e);
	}
//	free(sca_data);	
}


void ffmpeg_addAudioData() __attribute__((used,
          annotate("as3sig:public function ffmpeg_addAudioData(data:ByteArray)"),
          annotate("as3package:com.fw_ffmpeg"),
          annotate("as3import:flash.utils.ByteArray")));
void ffmpeg_addAudioData()
{
 	inline_as3(
 		"var dataPtr:int = CModule.malloc(data.length);\n"
 		"CModule.writeBytes(dataPtr, data.length, data);\n"
 		"var dataSize:int = data.length;\n"            
 		: :
 	);
 	
 	unsigned char* sca_data = 0;
 	unsigned long sca_data_size = 0;
 	
 	AS3_GetScalarFromVar(sca_data, dataPtr);
 	AS3_GetScalarFromVar(sca_data_size, dataSize);

	try { 	
		encoderInstance->ffmpeg_addAudioData(sca_data, sca_data_size);
	} catch (MyException &e) {
		handleException(e);
	}
	free(sca_data);	
}

void ffmpeg_setFrames() __attribute__((used,
          annotate("as3sig:public function ffmpeg_setFrames(frames:int):void"),
          annotate("as3package:com.fw_ffmpeg")));
void ffmpeg_setFrames() {
	int sca_frames = 0;
	AS3_GetScalarFromVar(sca_frames, frames);
	try {
		encoderInstance->ffmpeg_setFrames(sca_frames);
	}  catch (MyException &e) {
		handleException(e);
	}
}

void fw_setAudioRealtime() __attribute__((used,
          annotate("as3sig:public function fw_setAudioRealtime(realtime:int):void"),
          annotate("as3package:com.fw_ffmpeg")));
void fw_setAudioRealtime() {
	int sca_realtime = 0;
	AS3_GetScalarFromVar(sca_realtime, realtime);
	try {
		if (sca_realtime) {
			encoderInstance->setAudioRealtime(true);
		} else {
			encoderInstance->setAudioRealtime(false);
		}
	}  catch (MyException &e) {
		handleException(e);
	}
}


void configureRAMAdjuster() __attribute__((used,
          annotate("as3sig:public function configureRAMAdjuster(RAMadjusterFPSDivider:Number = 2, RAMadjusterTolerance:Number = 1.5):void"),
          annotate("as3package:com.fw_ffmpeg")));
void configureRAMAdjuster() {
	double sca_RAMadjusterFPSDivider = 0;
	double sca_RAMadjusterTolerance = 0;
	AS3_GetScalarFromVar(sca_RAMadjusterFPSDivider, RAMadjusterFPSDivider);
	AS3_GetScalarFromVar(sca_RAMadjusterTolerance, RAMadjusterTolerance);
	try {
		encoderInstance->configureRAMAdjuster(sca_RAMadjusterFPSDivider, sca_RAMadjusterTolerance);
	}  catch (MyException &e) {
		handleException(e);
	}
}

void fw_setPTSMode() __attribute__((used,
          annotate("as3sig:public function fw_setPTSMode(PTSmode:int):void"),
          annotate("as3package:com.fw_ffmpeg")));
void fw_setPTSMode() {
	int sca_mode = 0;
	AS3_GetScalarFromVar(sca_mode, PTSmode);
	try {
		encoderInstance->setPTSMode(sca_mode);
	}  catch (MyException &e) {
		handleException(e);
	}
}

void fw_setFramedropMode() __attribute__((used,
          annotate("as3sig:public function fw_setFramedropMode(framedropMode:int):void"),
          annotate("as3package:com.fw_ffmpeg")));
void fw_setFramedropMode() {
	int sca_mode = 0;
	AS3_GetScalarFromVar(sca_mode, framedropMode);
	try {
		encoderInstance->setFramedropMode(sca_mode);
	}  catch (MyException &e) {
		handleException(e);
	}
}

void fw_setLogging() __attribute__((used,
          annotate("as3sig:public function fw_setLogging(logging:Boolean, verbose:Boolean):void"),
          annotate("as3package:com.fw_ffmpeg")));
void fw_setLogging() {
	bool sca_logging = false;
	bool sca_verbose = false;

	AS3_GetScalarFromVar(sca_logging, logging);
	AS3_GetScalarFromVar(sca_verbose, verbose);
	try {
		encoderInstance->setLogging(sca_logging, sca_verbose);
	}  catch (MyException &e) {
		handleException(e);
	}
}


void decoder_getVideoWidth() __attribute__((used,
          annotate("as3sig:public function decoder_getVideoWidth():Number"),
          annotate("as3package:com.fw_ffmpeg")));
void decoder_getVideoWidth() {
	long res = decoderInstance->getVideoWidth();
	AS3_DeclareVar(asRes, Number);
	AS3_CopyScalarToVar(asRes, res);
	AS3_ReturnAS3Var(asRes);		
}

void decoder_getVideoHeight() __attribute__((used,
          annotate("as3sig:public function decoder_getVideoHeight():Number"),
          annotate("as3package:com.fw_ffmpeg")));
void decoder_getVideoHeight() {
	long res = decoderInstance->getVideoHeight();
	AS3_DeclareVar(asRes, Number);
	AS3_CopyScalarToVar(asRes, res);
	AS3_ReturnAS3Var(asRes);		
}

void decoder_getEOF() __attribute__((used,
          annotate("as3sig:public function decoder_getEOF():Boolean"),
          annotate("as3package:com.fw_ffmpeg")));
void decoder_getEOF() {
	long res = decoderInstance->eof;
	AS3_DeclareVar(asRes, Number);
	AS3_CopyScalarToVar(asRes, res);
	AS3_ReturnAS3Var(asRes);		
}

void decoder_bufferFrames() __attribute__((used,
          annotate("as3sig:public function decoder_bufferFrames(frameCount:int = 0):void"),
          annotate("as3package:com.fw_ffmpeg")));
void decoder_bufferFrames() {
	uint32_t sca_frameCount = 0;
	AS3_GetScalarFromVar(sca_frameCount, frameCount); 
	decoderInstance->runBufferFrames(sca_frameCount);
}

void decoder_getVideoFramesDecoded() __attribute__((used,
          annotate("as3sig:public function decoder_getVideoFramesDecoded():Number"),
          annotate("as3package:com.fw_ffmpeg")));
void decoder_getVideoFramesDecoded() {
	long res = decoderInstance->getVideoFramesCount();
	AS3_DeclareVar(asRes, Number);
	AS3_CopyScalarToVar(asRes, res);
	AS3_ReturnAS3Var(asRes);		
}

void ffmpeg_getVideoFramesSent() __attribute__((used,
          annotate("as3sig:public function ffmpeg_getVideoFramesSent():Number"),
          annotate("as3package:com.fw_ffmpeg")));
void ffmpeg_getVideoFramesSent() {
	long res = encoderInstance->ffmpeg_getVideoFramesSent();
	AS3_DeclareVar(asRes, Number);
	AS3_CopyScalarToVar(asRes, res);
	AS3_ReturnAS3Var(asRes);		
}

int main()
{
    AS3_GoAsync();
}

}