/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * Experimantal: This was never used in production, but managed to get it almost working. The aim was to create ogg/ogv decoder for Flash/AIR.
 *
 */

#ifndef FW_FFMPEG_DECODE_H
#define FW_FFMPEG_DECODE_H

#if defined(__MINGW32__)
#define _alloca __builtin_alloca
#endif

#if !defined(PRId64 )
#define PRId64 "I64d"
#endif

#ifndef INT64_C
  #define INT64_C(c) (c ## LL)
   #define UINT64_C(c) (c ## ULL)
#endif

#if defined(WIN32)
#define snprintf _snprintf
#endif

#include "FW_exception.h"
#include "FW_ffmpeg_IO.h"

#include <vector>
#include <sstream>
#include <boost/atomic.hpp>

extern "C"{

#include <libavutil/avutil.h>
#include <libavutil/imgutils.h>
#include <libavutil/samplefmt.h>
#include <libavutil/timestamp.h>
#include <libavformat/avformat.h>

}

// Windows specific
#if defined(WIN32) || defined(__MINGW32__)
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


using namespace std;

class AudioFrame {
	public:
		int size;
		unsigned char *data;
};

class FW_ffmpeg_decode {

private:

	// threading
	#ifdef WIN32
	HANDLE hThread;
	#else
	pthread_t hThread;
	#endif

	FILE *log;
	size_t output_buffer_size;

	std::stringstream videoFrames;
	std::stringstream audioFrames;

	const char *filename;
	AVOutputFormat *fmt;
	AVFormatContext *oc;
	AVIOContext *pb;

	AVFormatContext *fmt_ctx;
	AVCodecContext *video_dec_ctx, *audio_dec_ctx;
	AVStream *video_stream, *audio_stream;
	const char *src_filename;
	const char *video_dst_filename;
	const char *audio_dst_filename;
	FILE *video_dst_file;
	FILE *audio_dst_file;

	AVPicture src_picture, dst_picture;

	uint8_t **audio_dst_data;
	int       audio_dst_linesize;
	int audio_dst_bufsize;

	int video_stream_idx, audio_stream_idx;
	AVFrame *frame;
	AVPacket pkt;

	int video_frame_count;
	int audio_frame_count;
	
	int video_width;
	int video_height;
	
	// for BGRA conversion
	SwsContext *sws_ctx;

	// internal buffer for ffmpeg's IO 
	unsigned char* buffer;

	// how many frames in advance are we buffering
	int decodeBufferCount;

public:
	boost::atomic_bool eof;
	boost::atomic_bool flush;
	boost::atomic_bool threadDecodingRunning;

	AVFrame *frameBGRA;
	std::stringstream return_buffer;

	int getVideoWidth();
	int getVideoHeight();
	int getVideoFrameSize();	
	void resethThread();

	#ifdef WIN32
	static void rundecoderthread(void *param);
	#else
	static void *rundecoderthread(void *param);
	#endif

	int decode_packet(int *got_frame, int cached);

	int open_codec_context(int *stream_idx,
                              AVFormatContext *fmt_ctx, enum AVMediaType type);

	int get_format_from_sample_fmt(const char **fmt,
                                      enum AVSampleFormat sample_fmt);

	int init (char *videoBuffer, size_t videoBufferLength);

	std::stringstream *getVideoBuffer();

	// try to decode 1 frame
	int doFrame();

	void finish();

	// how many frames were decoded?
	int getVideoFramesCount();

	// get the decoded video stream
	std::stringstream *getVideoStream();

	// get the decoded audio stream
	std::stringstream *getAudioStream();

	// run buffer frames
	void runBufferFrames(int count);

	// prepare decoder for next round
	void reset();

	// buffer X number of frames
	int bufferFrames();

	~FW_ffmpeg_decode();
};

#endif