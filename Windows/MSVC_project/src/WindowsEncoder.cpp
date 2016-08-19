/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * Windows FFmpeg / MediaCodec encoder
 *
 */

#include "WindowsEncoder.h"
#include "logo.h"
#include <shlobj.h>
#include <comdef.h>
#include "FW_exception.h"

#pragma comment(lib, "mfreadwrite")
#pragma comment(lib, "mfplat")
#pragma comment(lib, "mfuuid")
#pragma comment(lib, "shlwapi")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "Strmiids.lib")

/* Private methods */

// for conversions C++ wide strings to/from AS3 UTF-8 strings
std::string WindowsEncoder::utf8_encode(const std::wstring &wstr) {
	if (wstr.empty()) return std::string();
	int size_needed = WideCharToMultiByte(CP_UTF8, 0, &wstr[0], (int)wstr.size(), NULL, 0, NULL, NULL);
	std::string strTo(size_needed, 0);
	WideCharToMultiByte(CP_UTF8, 0, &wstr[0], (int)wstr.size(), &strTo[0], size_needed, NULL, NULL);
	return strTo;
}

// for conversions C++ wide strings to/from AS3 UTF-8 strings
std::wstring WindowsEncoder::utf8_decode(const std::string &str) {
	if (str.empty()) return std::wstring();
	int size_needed = MultiByteToWideChar(CP_UTF8, 0, &str[0], (int)str.size(), NULL, 0);
	std::wstring wstrTo(size_needed, 0);
	MultiByteToWideChar(CP_UTF8, 0, &str[0], (int)str.size(), &wstrTo[0], size_needed);
	return wstrTo;
}

void WindowsEncoder::FWlog(char *msg, ...) {
	if (logging) {
		std::wstring path;
		std::string logString;
		logString += "[FlashyWrappers]" + (std::string(msg)) + "\n";
		wchar_t my_documents[MAX_PATH];
		HRESULT result = SHGetFolderPath(NULL, CSIDL_PERSONAL, NULL, SHGFP_TYPE_CURRENT, my_documents);
		path += my_documents;
		path += L"\\FW_log.txt";
		if (result == S_OK) {
			FILE *fp = NULL;
			fp = _wfopen(path.c_str(), L"a");
			if (!fp) fp = _wfopen(path.c_str(), L"w");
			va_list argptr;
			const char *logStringChar = logString.c_str();
			va_start(argptr, msg);
			vfprintf(fp, logStringChar, argptr);
			va_end(argptr);
			fclose(fp);
		}
	}
}

void WindowsEncoder::logHRFail(HRESULT hr, char *msg) {
	if (FAILED(hr)) {
		_com_error err(hr);
		LPCTSTR errMsg = err.ErrorMessage();
		FWlog(msg);
		FWlog((char*)std::string(("Error details: ") + utf8_encode(errMsg)).c_str());
	}
}

void WindowsEncoder::blitLogo(unsigned char *dest, int dest_w, int w, int h) {
	int y = 0;
	int x = 0;
	for (y = 0; y < h; y++) {
		long y_w = y * w * 4;
		long y_dest_w = y * dest_w * 4;
		for (x = 0; x < w; x++) {
			long x_4 = x * 4;
			//				memcpy(dest + (y * dest_w * 4), logo + (y * w * 4), w * 4);
			unsigned char foregroundAlpha = logo[(y_w)+(x_4)];
			unsigned char foregroundRed = logo[(y_w)+(x_4)+1];
			unsigned char foregroundGreen = logo[(y_w)+(x_4)+2];
			unsigned char foregroundBlue = logo[(y_w)+(x_4)+3];
			unsigned char backgroundRed = dest[(y_dest_w)+(x_4)+1];
			unsigned char backgroundGreen = dest[(y_dest_w)+(x_4)+2];
			unsigned char backgroundBlue = dest[(y_dest_w)+(x_4)+3];
			unsigned char r = ((foregroundRed * foregroundAlpha) + (backgroundRed * (255 - foregroundAlpha))) >> 8;
			unsigned char g = ((foregroundGreen * foregroundAlpha) + (backgroundGreen * (255 - foregroundAlpha))) >> 8;
			unsigned char b = ((foregroundBlue * foregroundAlpha) + (backgroundBlue * (255 - foregroundAlpha))) >> 8;
			dest[(y_dest_w)+(x_4)+1] = r;
			dest[(y_dest_w)+(x_4)+2] = g;
			dest[(y_dest_w)+(x_4)+3] = b;
		}
	}
}

short * WindowsEncoder::floatToPCM(float *data, uint32_t length) {
	short *dataConverted = (short*)malloc(length / 2);
	for (long a = 0; a < length / 4; a++) {
		dataConverted[a] = (short)(data[a] * 32767);
	}
	return dataConverted;
}

void WindowsEncoder::reverseBytes(unsigned char *start, int size) {
	unsigned char *lo = start;
	unsigned char *hi = start + size - 1;
	unsigned char swap;
	while (lo < hi) {
		swap = *lo;
		*lo++ = *hi;
		*hi-- = swap;
	}
}

HRESULT WindowsEncoder::InitializeSinkWriter(IMFSinkWriter **ppWriter, DWORD *pVideoStreamIndex, DWORD *pAudioStreamIndex) {
	*ppWriter = NULL;
	*pVideoStreamIndex = NULL;
	if (pAudioStreamIndex != NULL) {
		*pAudioStreamIndex = NULL;
	}

	IMFSinkWriter   *pSinkWriter = NULL;
	IMFMediaType    *pVideoTypeOut = NULL;
	IMFMediaType    *pVideoTypeIn = NULL;
	IMFMediaType    *pAudioTypeOut = NULL;
	IMFMediaType    *pAudioTypeIn = NULL;

	IMFAttributes	*pMP4attribs = NULL;
	DWORD           videoStreamIndex;
	DWORD           audioStreamIndex;

	HRESULT hr;

	// attach the stringstream on IMFByteStream
	// TODO: Release winAPI_byteStream!
	winAPI_buffer = SHCreateMemStream(NULL, NULL);

	MFCreateMFByteStreamOnStream(winAPI_buffer, &pwinAPI_byteStream);

	hr = MFCreateAttributes(&pMP4attribs, 0);

	hr = pMP4attribs->SetGUID(MF_TRANSCODE_CONTAINERTYPE, MFTranscodeContainerType_MPEG4);

	hr = MFCreateSinkWriterFromURL(/*L"c:\\Users\\Pavel\\Documents\\output.mp4"*/NULL, pwinAPI_byteStream, pMP4attribs, &pSinkWriter);
	logHRFail(hr, "MFCreateSinkWriterFromURL");

	// only if audio is enabled
	if (foundation_audio) {
		// Set the output audio media type
		if (SUCCEEDED(hr)) {
			hr = MFCreateMediaType(&pAudioTypeOut);
			logHRFail(hr, "MFCreateMediaType");
		}

		if (SUCCEEDED(hr)) {
			hr = pAudioTypeOut->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Audio);
			logHRFail(hr, "MF_MT_MAJOR_TYPE");
		}

		if (SUCCEEDED(hr)) {
			hr = pAudioTypeOut->SetGUID(MF_MT_SUBTYPE, MFAudioFormat_AAC);
			logHRFail(hr, "MF_MT_SUBTYPE");
		}

		if (SUCCEEDED(hr)) {
			hr = pAudioTypeOut->SetUINT32(MF_MT_AUDIO_BITS_PER_SAMPLE, 16);
			logHRFail(hr, "MF_MT_AUDIO_BITS_PER_SAMPLE");
		}

		if (SUCCEEDED(hr)) {
			hr = pAudioTypeOut->SetUINT32(MF_MT_AUDIO_SAMPLES_PER_SECOND, foundation_audio_sample_rate);
			logHRFail(hr, "MF_MT_AUDIO_SAMPLES_PER_SECOND");
		}

		if (SUCCEEDED(hr)) {
			hr = pAudioTypeOut->SetUINT32(MF_MT_AUDIO_NUM_CHANNELS, foundation_audio_channels);
			logHRFail(hr, "MF_MT_AUDIO_NUM_CHANNELS");
		}

		if (SUCCEEDED(hr)) {
			hr = pAudioTypeOut->SetUINT32(MF_MT_AUDIO_AVG_BYTES_PER_SECOND, 20000);
			logHRFail(hr, "MF_MT_FAUDIO_AVG_BYTES_PER_SECOND");
		}

		if (SUCCEEDED(hr))
		{
			hr = pSinkWriter->AddStream(pAudioTypeOut, &audioStreamIndex);
			logHRFail(hr, "AddStream");
		}
	}

	hr = MFCreateMediaType(&pVideoTypeOut);

	if (SUCCEEDED(hr))
	{
		hr = pVideoTypeOut->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
		logHRFail(hr, "MF_MT_MAJOR_TYPE");
	}
	if (SUCCEEDED(hr))
	{
		hr = pVideoTypeOut->SetGUID(MF_MT_SUBTYPE, VIDEO_ENCODING_FORMAT);
		logHRFail(hr, "MF_MT_SUBTYPE");
	}
	if (SUCCEEDED(hr))
	{
		hr = pVideoTypeOut->SetUINT32(MF_MT_AVG_BITRATE, foundation_bitRate);
		logHRFail(hr, "MF_MT_AVG_BITRATE");
	}
	if (SUCCEEDED(hr))
	{
		hr = pVideoTypeOut->SetUINT32(MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive);
		logHRFail(hr, "MF_MT_INTERLACE_MODE");
	}
	if (SUCCEEDED(hr))
	{
		hr = MFSetAttributeSize(pVideoTypeOut, MF_MT_FRAME_SIZE, foundation_width, foundation_height);
		logHRFail(hr, "MF_MT_FRAME_SIZE");
	}
	if (SUCCEEDED(hr))
	{
		hr = MFSetAttributeRatio(pVideoTypeOut, MF_MT_FRAME_RATE, foundation_fps, 1);
		logHRFail(hr, "MF_MT_FRAME_RATE");
	}
	if (SUCCEEDED(hr))
	{
		hr = MFSetAttributeRatio(pVideoTypeOut, MF_MT_PIXEL_ASPECT_RATIO, 1, 1);
		logHRFail(hr, "MF_MT_PIXEL_ASPECT_RATIO");
	}
	if (SUCCEEDED(hr))
	{
		hr = pSinkWriter->AddStream(pVideoTypeOut, &videoStreamIndex);
		logHRFail(hr, "AddStream");
	}


	// only if audio is enabled
	if (foundation_audio) {
		// Set the input audio type	
		if (SUCCEEDED(hr)) {
			hr = MFCreateMediaType(&pAudioTypeIn);
			logHRFail(hr, "MFCreateMediaType");
		}

		if (SUCCEEDED(hr)) {
			hr = pAudioTypeIn->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Audio);
			logHRFail(hr, "MF_MT_MAJOR_TYPE");
		}

		if (SUCCEEDED(hr)) {
			hr = pAudioTypeIn->SetGUID(MF_MT_SUBTYPE, MFAudioFormat_PCM);
			logHRFail(hr, "MF_MT_SUBTYPE");
		}

		if (SUCCEEDED(hr)) {
			hr = pAudioTypeIn->SetUINT32(MF_MT_AUDIO_BITS_PER_SAMPLE, 16);
			logHRFail(hr, "MF_MT_AUDIO_BITS_PER_SAMPLE");
		}

		if (SUCCEEDED(hr)) {
			hr = pAudioTypeIn->SetUINT32(MF_MT_AUDIO_SAMPLES_PER_SECOND, foundation_audio_sample_rate);
			logHRFail(hr, "MF_MT_AUDIO_SAMPLES_PER_SECOND");
		}

		if (SUCCEEDED(hr)) {
			hr = pAudioTypeIn->SetUINT32(MF_MT_AUDIO_NUM_CHANNELS, foundation_audio_channels);
			logHRFail(hr, "MF_MT_AUDIO_NUM_CHANNELS");
		}
		if (SUCCEEDED(hr))
		{
			hr = pSinkWriter->SetInputMediaType(audioStreamIndex, pAudioTypeIn, NULL);
			logHRFail(hr, "setInputMediaType (audio)");
		}
	}


	// Set the input video type.
	if (SUCCEEDED(hr))
	{
		hr = MFCreateMediaType(&pVideoTypeIn);
		logHRFail(hr, "MFCreateMediaType");
	}
	if (SUCCEEDED(hr))
	{
		hr = pVideoTypeIn->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
		logHRFail(hr, "MF_MT_MAJOR_TYPE");
	}
	if (SUCCEEDED(hr))
	{
		// W8 works with ARGB32
		if (IsWindows8OrGreater()) {
			hr = pVideoTypeIn->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_ARGB32);
		}
		else {
			// For W7 use RGB32, ARGB32 doesn't work
			if (IsWindows7OrGreater) {
				hr = pVideoTypeIn->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_RGB32);
				ARGBConversion = true;
			}
		}
		logHRFail(hr, "MF_MT_SUBTYPE");
	}
	if (SUCCEEDED(hr))
	{
		hr = pVideoTypeIn->SetUINT32(MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive);
		logHRFail(hr, "MF_MT_INTERLACE_MODE");
	}
	if (SUCCEEDED(hr))
	{
		hr = MFSetAttributeSize(pVideoTypeIn, MF_MT_FRAME_SIZE, foundation_width, foundation_height);
		logHRFail(hr, "MF_MT_FRAME_SIZE");
	}
	if (SUCCEEDED(hr))
	{
		hr = MFSetAttributeRatio(pVideoTypeIn, MF_MT_FRAME_RATE, foundation_fps, 1);
		logHRFail(hr, "MF_MT_FRAME_RATE");
	}
	if (SUCCEEDED(hr))
	{
		hr = MFSetAttributeRatio(pVideoTypeIn, MF_MT_PIXEL_ASPECT_RATIO, 1, 1);
		logHRFail(hr, "MF_MT_PIXEL_ASPECT_RATIO");
	}
	if (SUCCEEDED(hr))
	{
		hr = pSinkWriter->SetInputMediaType(videoStreamIndex, pVideoTypeIn, NULL);
		logHRFail(hr, "SetInputMediaType (video)");
	}

	// Tell the sink writer to start accepting data.
	if (SUCCEEDED(hr))
	{
		hr = pSinkWriter->BeginWriting();
		logHRFail(hr, "BeginWriting");
	}

	// Return the pointer to the caller.
	if (SUCCEEDED(hr))
	{
		*ppWriter = pSinkWriter;
		(*ppWriter)->AddRef();

		// only if audio is enabled
		if (foundation_audio) {
			*pAudioStreamIndex = audioStreamIndex;
		}
		*pVideoStreamIndex = videoStreamIndex;
	}

	video_frame_duration = 10 * 1000 * 1000 / (foundation_fps);

	//	hr = MFSetAttributeRatio(pVideoTypeIn, MF_MT_FRAME_RATE, 10, 1);
	//	hr = MFSetAttributeRatio(pVideoTypeOut, MF_MT_FRAME_RATE, 10, 1);

	SafeRelease(&pSinkWriter);
	SafeRelease(&pVideoTypeOut);
	SafeRelease(&pVideoTypeIn);

	// only if audio is enabled
	if (foundation_audio) {
		SafeRelease(&pAudioTypeOut);
		SafeRelease(&pAudioTypeIn);
	}
	return hr;
}

HRESULT WindowsEncoder::WriteFrameVideo(IMFSinkWriter *pWriter, BYTE *FlashFrame, DWORD streamIndex, const LONGLONG& rtStartVideo) {
	IMFSample *pSample = NULL;
	IMFMediaBuffer *pBuffer = NULL;
	HRESULT hr;
	LONG cbWidth;
	DWORD cbBuffer;
	BYTE *pData = NULL;

	cbWidth = 4 * foundation_width;
	cbBuffer = cbWidth * foundation_height;

	// Create a new memory buffer.
	hr = MFCreateMemoryBuffer(cbBuffer, &pBuffer);
	// Lock the buffer and copy the video frame to the buffer.

	if (SUCCEEDED(hr))
	{
		hr = pBuffer->Lock(&pData, NULL, NULL);
	}
	else {
		logHRFail(hr, "MFCreateMemoryBuffer");
	}

	// orient the frame to fit the output properly..reverse and flip horizontal (probably can be optimized)
	reverseBytes((unsigned char*)FlashFrame, cbBuffer);
	uint32_t tmp;
	uint32_t *FlashFrameInt = (uint32_t*)FlashFrame;
	for (int y = 0; y < foundation_height; y++)
		for (int x = 0; x < foundation_width / 2; x++)
		{
			tmp = FlashFrameInt[y * foundation_width + x];
			FlashFrameInt[y * foundation_width + x] = FlashFrameInt[(y * foundation_width) + (foundation_width - 1 - x)];
			FlashFrameInt[(y * foundation_width) + (foundation_width - 1 - x)] = tmp;
		}


	if (SUCCEEDED(hr))
	{
		hr = MFCopyImage(
			pData,                      // Destination buffer.
			cbWidth,                    // Destination stride.
			(BYTE*)FlashFrame,    // First row in source image.
			cbWidth,                    // Source stride.
			cbWidth,                    // Image width in bytes.
			foundation_height                // Image height in pixels.
			);
	}
	else {
		logHRFail(hr, "pBuffer->Lock");
	}

	// free the raw frame now!
	free(FlashFrame);

	if (pBuffer)
	{
		pBuffer->Unlock();
	}

	// Set the data length of the buffer.
	if (SUCCEEDED(hr))
	{
		hr = pBuffer->SetCurrentLength(cbBuffer);
	}
	else {
		logHRFail(hr, "pBuffer->Unlock");
	}

	// Create a media sample and add the buffer to the sample.
	if (SUCCEEDED(hr))
	{
		hr = MFCreateSample(&pSample);
	}
	else {
		logHRFail(hr, "pBuffer->SetCurrentLength");
	}

	if (SUCCEEDED(hr))
	{
		hr = pSample->AddBuffer(pBuffer);
	}
	else {
		logHRFail(hr, "MFCreateSample");
	}

	// Set the time stamp and the duration.
	if (SUCCEEDED(hr))
	{
			hr = pSample->SetSampleTime(rtStartVideo);
	}
	else {
		logHRFail(hr, "pSample->AddBuffer");
	}

	// Set the sample duration
	if (SUCCEEDED(hr))
	{
		hr = pSample->SetSampleDuration(video_frame_duration);
	}
	else {
		logHRFail(hr, "pSample->SetSampleTime");
	}

	//	pSample->SetUINT32(MFSampleExtension_Discontinuity, FALSE);

	// Send the sample to the Sink Writer.
	if (SUCCEEDED(hr))
	{
		hr = pWriter->WriteSample(streamIndex, pSample);
		if (FAILED(hr)) {
			FWlog("Failed to write video sample %d!", hr);
		}
	}
	else {
		logHRFail(hr, "pSample->SetSampleDuration");
	}
	SafeRelease(&pSample);
	SafeRelease(&pBuffer);
	return hr;
}

HRESULT WindowsEncoder::WriteFrameAudio(IMFSinkWriter *pWriter, BYTE *FlashFrame, uint32_t frameLength, uint32_t frameDuration, DWORD streamIndex, const LONGLONG& rtStartAudio) {
	IMFSample *pSample = NULL;
	IMFMediaBuffer *pBuffer = NULL;

	BYTE *pData = NULL;

	// Create a new memory buffer.
	HRESULT hr = MFCreateMemoryBuffer(frameLength, &pBuffer);

	// Lock the buffer and copy the audio frame to the buffer.
	if (SUCCEEDED(hr))
	{
		hr = pBuffer->Lock(&pData, NULL, NULL);
	}

	if (SUCCEEDED(hr))
	{
		memcpy(pData, FlashFrame, frameLength);
	}

	if (pBuffer)
	{
		pBuffer->Unlock();
	}

	// Set the data length of the buffer.
	if (SUCCEEDED(hr))
	{
		hr = pBuffer->SetCurrentLength(frameLength);
	}
	// Create a media sample and add the buffer to the sample.
	if (SUCCEEDED(hr))
	{
		hr = MFCreateSample(&pSample);
	}
	if (SUCCEEDED(hr))
	{
		hr = pSample->AddBuffer(pBuffer);
	}

	// Set the time stamp and the duration.
	if (SUCCEEDED(hr))
	{
		if ((foundation_realtime && PTSMode == PTS_AUTO) || PTSMode == PTS_REALTIME) {
			double now = GetTickCount();
			if (foundation_startRecording == 0) {
				foundation_startRecording = now;
			}
			hr = pSample->SetSampleTime((now - foundation_startRecording) * 1000 * 10);
		}
		else {
			hr = pSample->SetSampleTime(rtStartAudio);
		}
	}
	if (SUCCEEDED(hr))
	{
		hr = pSample->SetSampleDuration(frameDuration);
	}

	pSample->SetUINT32(MFSampleExtension_Discontinuity, FALSE);

	// Send the sample to the Sink Writer.
	if (SUCCEEDED(hr))
	{
		hr = pWriter->WriteSample(streamIndex, pSample);
	}

	SafeRelease(&pSample);
	SafeRelease(&pBuffer);
	return hr;
}

bool WindowsEncoder::initMF() {
	FWlog("Before CoInitializeEx");
	HRESULT hr = CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
	FWlog("After CoInitializeEx");
	if (SUCCEEDED(hr)) {
		canInitSink = true;
		FWlog("Before MFStartup");
		hr = MFStartup(MF_VERSION);
		FWlog("After MFStartup");
		if (SUCCEEDED(hr)) {
			MediaFoundationInit = true;
			FWlog("All went fine, ExtInitializer end");
		}
		else {
			FWlog("FAIL!");
			throw new MyException("MediaFoundation init failed");
		}
	}
	return true;
}

void WindowsEncoder::shutdownMF() {
	FWlog("ExtFinalizer start");
	if (MediaFoundationInit) {
		FWlog("MFShutdown");
		MFShutdown();
	}
	if (canInitSink) {
		FWlog("CoUnitialize");
		CoUninitialize();
	}
	FWlog("ExtFinalizer end");
}

void WindowsEncoder::setLogging(int sca_logging, int sca_verbose) {
	if (sca_logging) {
		logging = true;
	}
	else {
		logging = false;
	}
	if (sca_verbose) {
		verbose = true;
	}
	else {
		verbose = false;
	}
}

int WindowsEncoder::addVideoData(unsigned char *data, uint32_t length) {
	double millis;

	if (foundation_realtime) {
		millis = GetTickCount();
		if (millisOld != 0) {
			delta = millis - millisOld;
			stepAccum += delta;
		}
		else delta = millis;
		millisOld = millis;
	}

	/*	        FILE *fp = fopen("c:\\Users\\Pavel\\Documents\\FWlog","wa");
	fprintf(fp, "millis %f millisOld %f delta %f stepTarget %f stepAccum %f step %f", millis, millisOld, delta, stepTarget, stepAccum, step);
	fclose(fp);*/

	// only add frames when needed, in non-realtime all of those vars are 0(just adding zeroes) so this condition is always true
	int ret = 0;
	if (stepAccum >= stepTarget) {

#ifdef DEMO	
		if ((float)foundation_videoFramesSent / (float)foundation_fps > 30) {
			throw MyException("No more than 30 seconds recording allowed in free mode. Visit http://www.flashywrappers.com to buy an upgrade.");
		}
#endif

		stepTarget += step;
		if (verbose) FWlog("Adding video frame");
#ifdef DEMO
		blitLogo(data, foundation_width, 85, 60);
#endif				
		double ptsVideo = 0;
		// Send frames to the sink writer.		
		if ((foundation_realtime && PTSMode == PTS_AUTO) || PTSMode == PTS_REALTIME) {
			double now = GetTickCount();
			if (foundation_startRecording == 0) {
				foundation_startRecording = now;
			}
			ptsVideo = ((now - foundation_startRecording) * 1000 * 10);
		} else {
			ptsVideo = rtStartVideo;
		}
		HRESULT hr = WriteFrameVideo(pSinkWriter, (BYTE*)data, videoStream, ptsVideo);
		if (FAILED(hr))
		{
			FWlog("Failed adding video frame using SinkWriter");
			throw new MyException("Failed adding frame using SinkWriter");
		}
		foundation_videoFramesSent++;
		rtStartVideo += video_frame_duration;
		ret = 1;

		double ptsAudio = 0;
		FWAudioSample *as = NULL;
		// check out if some audio samples are available
		if (audioSamples.size() > 0) {
			// if yes, get the pts of the firs tone
			as = audioSamples.front();
			ptsAudio = (as->pts + as->getPartLength(as->dataPointer));
			if (verbose) FWlog("Audio sample available, pts video %f, pts audio %f", ptsVideo, ptsAudio);
		}

		// now, while we've got audio samples and their pts is less than the pts of the current video frame,
		// feed them to MediaFoundation. This should ensure we'll fill the video frames with audio completely.
		while (audioSamples.size() > 0 && ptsAudio < ptsVideo) {
			// calculate how many bytes we need to send to fill up the video frame with audio completely
			long durationNeeded = ptsVideo - ptsAudio;

			LONGLONG first = ((LONGLONG)durationNeeded * (LONGLONG)foundation_audio_sample_rate);
			long size = (long)ceilf((((double)first / (double)5000000) * (double)foundation_audio_channels));
			//long size = 8192;

				// if we'll be reaching over the end of this audio sample, then take only part of it
				if (as->dataPointer + size > as->length) {
					size = as->length - as->dataPointer;
				}

				// log
				if (verbose) FWlog("Sending audio data with offset at %lu bytes (total length %lu bytes), pts %f, size %lu", as->dataPointer, as->length, as->pts + as->getPartLength(as->dataPointer), size);

				if (size != 0) {

					// 	send audio to encoder
					HRESULT hr = WriteFrameAudio(pSinkWriter, (BYTE*)as->data + as->dataPointer, size, as->getPartLength(size), audioStream, as->pts + as->getPartLength(as->dataPointer));

					if (FAILED(hr))
					{
						FWlog("Failed adding audio frame using SinkWriter");
						throw new MyException("Failed adding frame using SinkWriter");
					}

				}

				// increment the step for the next read
				as->dataPointer += size;
				ptsAudio = (as->pts + as->getPartLength(as->dataPointer));

				// if we got beyond the sample buffer then drop it, otherwise we'll continue reading the buffer in the next cycle
				if (as->dataPointer >= as->length) {
					// cause destructor to delete internal data of the audio sample and then also delete the sample from the queue of samples
					delete(as);
					// drop the sample buffer from the queue
					audioSamples.pop();
					// log it
					if (verbose) FWlog("length = dataPointer in audioPacket, dropping it");
					// get a new sample from the front of the buffer if possible, otherwise 
					// do nothing (the while condition will be satisfied in that case and the loop should break)
					if (audioSamples.size() > 0) {
						as = audioSamples.front();
					}
				}
				
		}
	}
	else {
		// IMPORTANT: TO PREVENT MEMORY LEAKS, raw frame must be killed here because WriteFrameVideo never gets executed when skipping frames
		free(data);
	}
	return ret;
}

int WindowsEncoder::addAudioData(unsigned char *data, uint32_t length) {
	if (verbose) FWlog("Adding audio frame");
	// add audio using MediaFoundation
	// only if audio is enabled
	if (foundation_audio) {
		// compute pts based on realtime / non-realtime mode
		double ptsAudio = 0;
		if ((foundation_realtime && PTSMode == PTS_AUTO) || PTSMode == PTS_REALTIME) {
			double now = GetTickCount();
			if (foundation_startRecording == 0) {
				foundation_startRecording = now;
			}
			double ptsAudio = ((now - foundation_startRecording) * 1000 * 10);
		} else {
			ptsAudio = rtStartAudio;
		}

		audioSamples.push(new FWAudioSample(this, (float*)data, length, ptsAudio));
		FWAudioSample *as = audioSamples.front();
		rtStartAudio += as->duration;

		/*uint32_t nAudioSamplesPerChannel = ((length / foundation_audio_channels) * 8) / 16;
		LONGLONG duration = (nAudioSamplesPerChannel * (LONGLONG)10000000) / foundation_audio_sample_rate;
		HRESULT hr = WriteFrameAudio(pSinkWriter, (BYTE*)PCMaudio, length, duration, audioStream, rtStartAudio);

		if (FAILED(hr))
		{
			FWlog("Failed adding audio frame using SinkWriter");
			throw new MyException("Failed adding frame using SinkWriter");
		}
		rtStartAudio += duration;*/
	} else {
		free(data);
		throw new MyException("The audio recording is off yet you're trying to add audio data to the encoder. Please make sure you know what you're doing with the audio.");		
	}
	// btw.: no free(PCMaudio) here, it should get freed in writeFrameAudio
	return NULL;
}

unsigned char* WindowsEncoder::getStream(QWORD *output_buffer_sizePtr) {
	pwinAPI_byteStream->GetLength(output_buffer_sizePtr);
	QWORD output_buffer_size = *output_buffer_sizePtr;

	// create temp buffer storage of char* type which we fill from return_buffer stringstream
	unsigned char *tmp = (unsigned char*)malloc(output_buffer_size);
	ULONG readBytes = 0;
	pwinAPI_byteStream->SetCurrentPosition(0);
	pwinAPI_byteStream->Flush();
	pwinAPI_byteStream->Read(tmp, output_buffer_size, &readBytes);

	// finally release the in memory stream(IStream) holding the video
	SafeRelease(&pwinAPI_byteStream);
	return tmp;
}

uint32_t WindowsEncoder::getStreamSize() {
	HRESULT hr = pSinkWriter->Finalize();

	while (audioSamples.size() > 0) {
		FWAudioSample *as = audioSamples.front();
		delete(as);		
		audioSamples.pop();
	}

	if (FAILED(hr)) {
		throw new MyException("Failed finishing SinkWriter");
	}

	QWORD output_buffer_size = 0;

	pwinAPI_byteStream->Flush();
	pwinAPI_byteStream->GetLength(&output_buffer_size);

	SafeRelease(&pSinkWriter);
	return output_buffer_size;
}

void WindowsEncoder::setPTSMode(int mode) {
	PTSMode = mode;
}

void WindowsEncoder::setFramedropMode(int mode) {
	framedropMode = mode;
	if (mode == FRAMEDROP_AUTO) {
		if (foundation_realtime) {
			step = 1000 / (float)foundation_fps;
		}
		else {
			step = 0;
		}
	}
	if (mode == FRAMEDROP_OFF) {
		step = 0;
	}
}

void WindowsEncoder::init(uint32_t _foundation_width, uint32_t _foundation_height, uint32_t _foundation_fps, uint32_t _foundation_bitRate,
						  uint32_t _foundation_audio_sample_rate, uint32_t _foundation_audio_channels, uint32_t _foundation_audio_bit_rate,
						  uint32_t _foundation_intermediate_buffer_length, uint32_t _foundation_realtime, uint32_t _foundation_audio) {

	// just in case...clear audio samples
	while (audioSamples.size() > 0) {
		FWAudioSample *as = audioSamples.front();
		delete(as);
		audioSamples.pop();
	}

	foundation_width = _foundation_width;
	foundation_height = _foundation_height;
	foundation_fps = _foundation_fps;
	foundation_bitRate = _foundation_bitRate;
	foundation_audio_sample_rate = _foundation_audio_sample_rate;
	foundation_audio_channels = _foundation_audio_channels;
	foundation_audio_bit_rate = _foundation_audio_bit_rate;
	foundation_intermediate_buffer_length = _foundation_intermediate_buffer_length;
	foundation_realtime = _foundation_realtime;
	foundation_audio = _foundation_audio;
	
	if (foundation_audio) {
		if (foundation_audio_sample_rate != 44100 && foundation_audio_sample_rate != 48000) {
			FWlog("AAC sample rate error");
			throw new MyException("Windows AAC encoder supports only sample rates of 44100 and 48000");
		}

		if (foundation_audio_channels < 1 || foundation_audio_channels > 2) {
			FWlog("AAC audio channels error");
			throw new MyException("Windows AAC encoder supports 1 or 2 audio channels");
		}
	}

	if (!MediaFoundationInit) {
		FWlog("MF couldn't be initialized");
		throw new MyException("MediaFounation couldn't be initialized");
	}
	FWlog("MF initialized");
	if (canInitSink) {
		if (foundation_audio) {
			HRESULT hr = InitializeSinkWriter(&pSinkWriter, &videoStream, &audioStream);
			if (FAILED(hr))
			{
				FWlog("SinkWriter couldn't be initialized");
				throw new MyException("Couldn't initialize SinkWriter");
			}
		}
		else {
			HRESULT hr = InitializeSinkWriter(&pSinkWriter, &videoStream, NULL);
			if (FAILED(hr))
			{
				FWlog("SinkWriter couldn't be initialized");
				throw new MyException("Couldn't initialize SinkWriter");
			}
		}
		if (pSinkWriter == NULL) {
			FWlog("SinkWriter is NULL!");
			throw new MyException("SinkWriter is NULL");
		}

	}
	else {
		throw new MyException("Couldn't initialize COM");
	}
	FWlog("SinkWriter initialized");
	// reset the fps sync component vars
	step = 0;
	delta = 0;
	millisOld = 0;
	stepAccum = 0;
	stepTarget = 0;
	rtStartVideo = 0;
	rtStartAudio = 0;
	foundation_videoFramesSent = 0;
	foundation_startRecording = 0;
	// if stage fps is set then we compute the step value
	if (foundation_realtime && framedropMode == FRAMEDROP_AUTO) {
		step = 1000 / (float)foundation_fps;
	}
}

uint32_t WindowsEncoder::getVideoFramesSent() {
	return foundation_videoFramesSent;
}
