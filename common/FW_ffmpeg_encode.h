/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * This file was based on muxing.c source by Fabrice Bellard.
 * It used to be the FFmpeg common code for all platforms, but in time only remained used by Flash (FlasCC / Crossbridge). You can still use this 
 * if you'd like to build FFmpeg based Windows / OS X or whatever platform encoder.
 *
 */

#ifndef FW_FFMPEG_ENCODE_H
#define FW_FFMPEG_ENCODE_H

// Fixes for Windows ANE

#if defined(WIN32) || defined(ANDROID)
#ifndef INT64_C
  #define INT64_C(c) (c ## LL)
   #define UINT64_C(c) (c ## ULL)
#endif
#endif

#if defined(WIN32)
#define snprintf _snprintf
#endif

#include <vector>
#include <sstream>
#include <fstream>
#include <boost/atomic.hpp>

#ifdef __cplusplus

extern "C"{

#endif 

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <iostream>
#include <math.h>
#include <vector>

#include <libavutil/opt.h>
#include <libavutil/mathematics.h>
#include <libavutil/time.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>

// Windows specific
#ifdef WIN32

#include <process.h>
#include <windows.h>
#undef av_err2str
#define av_err2str(errnum) \
av_make_error_string(reinterpret_cast<char*>(_alloca(AV_ERROR_MAX_STRING_SIZE)),\
AV_ERROR_MAX_STRING_SIZE, errnum)

#else
// iOS, Android, Mac have pthread
#include <pthread.h>
#endif
#ifdef __APPLE__
#include <pthread.h>
#endif

#include "FW_ffmpeg_IO.h"

#ifdef __cplusplus

}

#endif

#define STREAM_PIX_FMT    AV_PIX_FMT_YUV420P /* default pix_fmt */

#include "logo.h"

class FW_ffmpeg_encode {

private:

	// threading
	#ifdef WIN32
	HANDLE hThread;
	HANDLE hInitThread;
	#else
	pthread_t hThread;
	pthread_t hInitThread;
	#endif
  

	int sws_flags;

	size_t output_buffer_size;

	std::stringstream return_buffer;

	/* this used to be in main */
	SwsContext *sws_ctx;
	const char *filename;
	AVOutputFormat *fmt;
	AVFormatContext *oc;
	AVIOContext *pb;
	AVStream *audio_st, *video_st;
	AVCodec *audio_codec, *video_codec;
	double audio_pts, video_pts;
	int ret;
	int global_width;
	int global_fps;

	// by default record every frame, otherwise this counts down until we actually record a frame
	double step;
	double delta;
	double millisOld;

	// accumulated frame steps
	double stepAccum;
	// the "target" for the step increments, we will save the movie when step is equal or greater than stepTarget (whole number)
	double stepTarget;

	// record audio - if empty audio codec is specify don't record audio
	bool recordAudio;
	int realtime;
	int audio;
	// time of start recording
	double startRecording;
	double oldPts;
	double oldAudioPts;

	bool adjustedThisFlush;
	bool logging;
	bool verbose;

	// internal buffer for ffmpeg's IO 
	unsigned char* buffer;

	// those are for the RAM adjusting
	double RAMadjusterFPSDivider;
	double RAMadjusterTolerance;
	int RAMadjusterCount;

	// intermediate buffers to be filled from Flash
	std::vector<unsigned char*> videoDataVector_intermediate;
	std::vector<double> videoDataVector_timeStamps_intermediate;
	std::vector<double> videoDataVector_timeStamps;

	unsigned char* audioData_intermediate;
	std::vector<double> audioData_timeStamps_intermediate;
	std::vector<double> audioData_timeStamps;
	std::vector<double> audioData_timeStampsBytes_intermediate;
	std::vector<double> audioData_timeStampsBytes;

	unsigned long audioDataSizeTotal_intermediate;
	unsigned long audioFramesTotal_intermediate;
	unsigned long videoFramesTotal_intermediate;

	// buffers for encoder to be copied from intermediate buffers
	std::vector<unsigned char*> videoDataVector;
	unsigned char* audioData;

	unsigned long audioDataSizeTotal;
	uint32_t audio_frame_size;
	double audioFramesTotal;
	unsigned long audioFramesSent;
	unsigned long audioFramesWritten;
	unsigned long videoFramesSent;
	unsigned long videoFramesPrepared;
	unsigned long videoFramesWritten;
	unsigned long videoFramesTotal;

	// compute audio pts with realtime method or not
	bool audioRealtime;

	// how many raw frames to buffer at least before encoder thread can start. If set to 0, no threading is done
	unsigned long intermediate_buffer_length;

	AVFrame *frame;
	AVPicture src_picture, dst_picture;
	int frame_count;

	// automatic mode - 0
	#define PTS_AUTO 0
	#define PTS_MONO 1
	#define PTS_REALTIME 2

	#define FRAMEDROP_AUTO 0
	#define FRAMEDROP_OFF 1
	#define FRAMEDROP_ON 2

	unsigned int PTSmode;
	unsigned int framedropMode;

	/* Add an output video stream */
	AVStream *add_video_stream(
		AVFormatContext *oc,
		AVCodec **codec,
		enum AVCodecID codec_id,
		int width = 0,
		int height = 0,
		int bit_rate = 300000,
		int fps = 25,
		int speed = 8,
		int keyframe_interval = 12,
		int quality = 23
	);

	/* Add an output audio stream. */
	AVStream *add_audio_stream(
		AVFormatContext *oc,
		AVCodec **codec,
			enum AVCodecID codec_id,
		int sample_rate = 16000,
		int channels = 1,
		int bit_rate = 64000,
		int fps = 25
	);

	#ifdef WIN32
	static void runencoderthread(void *param);
	static void runInitThread(void *param);
	#else
	static void *runencoderthread(void *param);
	static void *runInitThread(void *param);
	#endif
	
	void open_audio(AVFormatContext *oc, AVCodec *codec, AVStream *st);

	void write_audio_frame(AVFormatContext *oc, const uint8_t* audioBytes, int audio_offset, AVStream *st);

	void close_audio(AVFormatContext *oc, AVStream *st);

	void open_video(AVFormatContext *oc, AVCodec *codec, AVStream *st);

	void write_video_frame(AVFormatContext *oc, unsigned char *imageBytes, AVStream *st);

	char* allocString(char const *msg);

	size_t getStringLength(char const *msg);

	void close_video(AVFormatContext *oc, AVStream *st);

	void encode_it();

	/* Flush intermediate buffers to get ready for encoder thread, returns true if there were some frames flushed or false if there weren't */
	bool flush_intermediate_frames();

	/* Try to flush frames and start a new encoding thread */
	bool try_flushAndThread();

	int check_sample_fmt(AVCodec *codec, enum AVSampleFormat sample_fmt);

	double audio_sync_opts;

public:
        unsigned char* str_container;
        unsigned char* str_codec_video;
        unsigned char* str_codec_audio;
        int sca_w;
        int sca_h;
        int sca_fps;
        int sca_speed;
        int sca_bitrate;
        int sca_quality;
        int sca_keyframe_frequency;
        int audio_sample_rate;
        int audio_channels;
        int audio_bit_rate;
        unsigned long _intermediate_buffer_length;
        int _realtime;
        int _audio;

	boost::atomic_bool threadEncodingRunning;
	boost::atomic_bool threadDataAvailable;
	boost::atomic_bool initFinished;

 	/* this indicates whenever the actual output of the encoder started */
	bool encodingStarted;
	bool threadRunning;	

	int encodingStartedFrame;

	/* blit the ARGB demo logo into the top left corner of ARGBframe */
	void blitLogo(unsigned char *dest, int dest_w, int w, int h); 

	/* Constructor */
	FW_ffmpeg_encode();

	void flush();

	void setLogging(bool logging, bool verbose);

	/* Can we finish the encoding (are threads over and no intermediate frames to encode?) This is to not lock Flash if we used waiting for threads native calls or pthread_join for workers */
	bool canFinish();

	void setPTSMode(uint32_t mode);
	
	void setFramedropMode(uint32_t mode);

	void ffmpeg_setFrames(int f);

	void setAudioRealtime(bool b);

	int finishInit();

	/* Returns whenever the initialization thread finished or not */
	bool initThreadFinished();

	long ffmpeg_getVideoFramesSent();

	/* Init codecs */
	int ffmpeg_init(unsigned char* str_container, unsigned char* str_codec_video, unsigned char* str_codec_audio, int sca_w, int sca_h, int sca_fps, int sca_speed, int sca_bitrate, int sca_quality, int sca_keyframe_frequency, int audio_sample_rate, int audio_channels, int audio_bit_rate, unsigned long _intermediate_buffer_length = 20, int _realtime = 1, int _audio = 0);

	/* Finish up */
	void ffmpeg_finish();

	/* Get the movie stream */
	std::stringstream *ffmpeg_getStream();

	/* Reset for another session */
	void ffmpeg_reset();

	/* Add video data - returns 1 if frame was added, 0 if it was skipped */
	int ffmpeg_addVideoData(unsigned char *sca_data, int sca_data_size);
	
	/* Add audio data */
	void ffmpeg_addAudioData(unsigned char *sca_data, int sca_data_size);

	/* Configure the RAM adjuster  - only for Flash */
	void configureRAMAdjuster(double RAMadjusterFPSDivider, double RAMadjusterTolerance);
	
	void AS3_trace(char *str, ...);
};

#endif
