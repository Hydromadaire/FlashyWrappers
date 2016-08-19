/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * Windows FFmpeg / MediaCodec encoder
 *
 */

#pragma once
#ifndef WINDOWSENCODER_H
#define WINDOWSENCOER_H

#define PTS_AUTO 0
#define PTS_MONO 1
#define PTS_REALTIME 2
#define FRAMEDROP_AUTO 0
#define FRAMEDROP_OFF 1

#include "AudioSample.h"
#include "FlashRuntimeExtensions.h"
#include "stdafx.h"
#include "stdlib.h"
#include <vector>
#include <queue>
#include <Windows.h>
#include <mfapi.h>
#include <mfidl.h>
#include <Mfreadwrite.h>
#include <mferror.h>
#include <sstream>
#include "Shlwapi.h"
#include "Wmcodecdsp.h"
#include <propvarutil.h>
#include "Codecapi.h"
#include <VersionHelpers.h>

class WindowsEncoder {

private:

	std::queue<FWAudioSample*> audioSamples;

	IMFSinkWriter *pSinkWriter = NULL;
	DWORD audioStream;
	DWORD videoStream;
	bool canInitSink = false;
	bool MediaFoundationInit = false;
	IMFByteStream* pwinAPI_byteStream = NULL;
	IStream* winAPI_buffer;

	// by default record every frame, otherwise this counts down until we actually record a frame
	double step;
	double delta;
	double millisOld;
	LONGLONG rtStartVideo = 0;
	LONGLONG rtStartAudio = 0;

	// accumulated frame steps
	double stepAccum;
	// the "target" for the step increments, we will save the movie when step is equal or greater than stepTarget (whole number)
	double stepTarget;

	// Format constants
	UINT32 foundation_width = 640;
	UINT32 foundation_height = 480;
	UINT32 foundation_fps = 20;
	UINT32 foundation_bitRate = 800000;
	UINT64 video_frame_duration = 0;
	UINT64 audio_frame_duration = 0;
	uint32_t foundation_intermediate_buffer_length = 0;
	uint32_t foundation_realtime = 0;
	uint32_t foundation_audio = 0;
	uint32_t foundation_videoFramesSent = 0;
	uint32_t PTSMode = PTS_AUTO;
	uint32_t framedropMode = FRAMEDROP_AUTO;

	double foundation_startRecording = 0;
	bool ARGBConversion = false;
	bool logging = true;
	bool verbose = false;

	const UINT32 VIDEO_WIDTH = 640;
	const UINT32 VIDEO_HEIGHT = 480;
	const UINT32 VIDEO_FPS = 30;
	const UINT64 VIDEO_FRAME_DURATION = 10 * 1000 * 1000 / VIDEO_FPS;
	const UINT32 VIDEO_BIT_RATE = 800000;
	const GUID   VIDEO_ENCODING_FORMAT = MFVideoFormat_H264;
	const GUID   VIDEO_INPUT_FORMAT = MFVideoFormat_ARGB32;
	const UINT32 VIDEO_PELS = VIDEO_WIDTH * VIDEO_HEIGHT;
	const UINT32 VIDEO_FRAME_COUNT = 20 * VIDEO_FPS;

	// for conversions C++ wide strings to/from AS3 UTF-8 strings
	std::string utf8_encode(const std::wstring &wstr);

	std::wstring utf8_decode(const std::string &str);

	void logHRFail(HRESULT hr, char *msg);

	void blitLogo(unsigned char *dest, int dest_w, int w, int h);

	void reverseBytes(unsigned char *start, int size);

public:

	uint32_t foundation_audio_bit_rate = 0;
	uint32_t foundation_audio_channels = 0;
	uint32_t foundation_audio_sample_rate = 0;

	short * floatToPCM(float *data, uint32_t length);

	void FWlog(char *msg, ...);

	HRESULT InitializeSinkWriter(IMFSinkWriter **ppWriter, DWORD *pVideoStreamIndex, DWORD *pAudioStreamIndex);
	HRESULT WriteFrameVideo(IMFSinkWriter *pWriter, BYTE *FlashFrame, DWORD streamIndex, const LONGLONG& rtStartVideo);
	HRESULT WriteFrameAudio(IMFSinkWriter *pWriter, BYTE *FlashFrame, uint32_t frameLength, uint32_t frameDuration, DWORD streamIndex, const LONGLONG& rtStartAudio);
	bool initMF();
	void shutdownMF();
	void setLogging(int sca_logging, int sca_verbose);
	int addVideoData(unsigned char *data, uint32_t length);
	int addAudioData(unsigned char *data, uint32_t length);
	unsigned char* getStream(QWORD *output_buffer_sizePtr);
	uint32_t getStreamSize();
	void setPTSMode(int mode);
	void setFramedropMode(int mode);
	void init(uint32_t foundation_width, uint32_t foundation_height, uint32_t foundation_fps, uint32_t foundation_bitRate,
		uint32_t foundation_audio_sample_rate, uint32_t foundation_audio_channels, uint32_t foundation_audio_bit_rate,
		uint32_t foundation_intermediate_buffer_length, uint32_t foundation_realtime, uint32_t foundation_audio);
	uint32_t getVideoFramesSent();
};

template <class T> void SafeRelease(T **ppT)
{
	if (*ppT)
	{
		(*ppT)->Release();
		*ppT = NULL;
	}
};

#endif
