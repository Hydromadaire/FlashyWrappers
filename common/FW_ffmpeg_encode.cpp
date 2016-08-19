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

#define X264_API_IMPORTS

#if defined(__MINGW32__)
#define _alloca __builtin_alloca
#endif

#if defined(__AVM2) 	
#include <AS3/AS3.h>
#endif

#include "FW_ffmpeg_encode.h"
#include "FW_exception.h"

#ifndef _MSC_VER
#include <sys/time.h>
#endif

using namespace std;

	// blit the logo (supports alpha blending now)
	void FW_ffmpeg_encode::blitLogo(unsigned char *dest, int dest_w, int w, int h) {
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
				unsigned char backgroundRed =   dest[(y_dest_w) + (x_4) + 1];
				unsigned char backgroundGreen = dest[(y_dest_w) + (x_4) + 2];
				unsigned char backgroundBlue =  dest[(y_dest_w) + (x_4) + 3];
				unsigned char r = ((foregroundRed * foregroundAlpha) + (backgroundRed * (255 - foregroundAlpha))) >> 8;
				unsigned char g = ((foregroundGreen * foregroundAlpha) + (backgroundGreen * (255 - foregroundAlpha))) >> 8;
				unsigned char b = ((foregroundBlue * foregroundAlpha) + (backgroundBlue * (255 - foregroundAlpha))) >> 8;
				dest[(y_dest_w) + (x_4) + 1] = r;
				dest[(y_dest_w) + (x_4) + 2] = g;
				dest[(y_dest_w) + (x_4) + 3] = b;
			}
		}
	}
	
	void FW_ffmpeg_encode::setLogging(bool logging, bool verbose) {
		this->logging = logging;
		this->verbose = verbose;
	}

	AVStream *FW_ffmpeg_encode::add_video_stream(
		AVFormatContext *oc,
		AVCodec **codec,
		enum AVCodecID codec_id,
		int width,
		int height,
		int bit_rate,
		int fps,
		int speed,
		int keyframe_interval,
		int quality
	)
	{
		audio_sync_opts = 0;
	    AVCodecContext *c;
	    AVStream *st;

	    /* find the encoder */
	    *codec = avcodec_find_encoder(codec_id);
	    if (!(*codec)) {
		std::string codec_str = std::string (avcodec_get_name(codec_id));
		throw MyException("Could not find video encoder for " + codec_str);
	    }

	    st = avformat_new_stream(oc, *codec);
	    if (!st) {
		throw MyException("Could not allocate video stream");
	    }
	    st->id = oc->nb_streams-1;
	    c = st->codec;

	    switch ((*codec)->type) {
			case AVMEDIA_TYPE_VIDEO:
				c->codec_id = codec_id;			
				c->bit_rate = bit_rate;
				/* Resolution must be a multiple of two. */
				c->width    = width;
				c->height   = height;
				global_width = width;
				/* timebase: This is the fundamental unit of time (in seconds) in terms
				* of which frame timestamps are represented. For fixed-fps content,
				* timebase should be 1/framerate and timestamp increments should be
				* identical to 1. */
				c->time_base.den = fps;
				c->time_base.num = 1;
				c->gop_size      = keyframe_interval; /* emit one intra frame every twelve frames at most */
				c->pix_fmt       = STREAM_PIX_FMT;
				c->max_b_frames = 0;
				c->has_b_frames = 0;				
				c->delay = 0;
				c->global_quality = quality;



		/*        if (c->codec_id == AV_CODEC_ID_MPEG2VIDEO) {
					c->max_b_frames = 2;
				}*/
				if (c->codec_id == AV_CODEC_ID_MPEG1VIDEO) {
					/* Needed to avoid using macroblocks in which some coeffs overflow.
					* This does not happen with normal video, it just happens here as
					* the motion of the chroma plane does not match the luma plane. */
					c->mb_decision = 2;
				}
	
			if (c->codec_id == AV_CODEC_ID_H264) {
				av_opt_set(c->priv_data, "movflags", "faststart", 0);
				if (speed == 0) {
					av_opt_set(c->priv_data, "preset", "veryslow", 0);
				}
				if (speed == 1) {
					av_opt_set(c->priv_data, "preset", "slower", 0);
				}
				if (speed == 2) {
					av_opt_set(c->priv_data, "preset", "slow", 0);
				}
				if (speed == 3) {
					av_opt_set(c->priv_data, "preset", "medium", 0);
				}
				if (speed == 4) {
					av_opt_set(c->priv_data, "preset", "fast", 0);
				}
				if (speed == 5) {
					av_opt_set(c->priv_data, "preset", "faster", 0);
				}
				if (speed == 6) {
					av_opt_set(c->priv_data, "preset", "veryfast", 0);
				}
				if (speed == 7) {
					av_opt_set(c->priv_data, "preset", "superfast", 0);
				}
				if (speed == 8) {
					av_opt_set(c->priv_data, "preset", "ultrafast", 0);
				}
				std::stringstream ss;
					ss << quality;
				av_opt_set(c->priv_data, "crf", (ss.str()).c_str(), 0);
				av_opt_set(c->priv_data, "profile", "baseline", AV_OPT_SEARCH_CHILDREN);				
			}

			break;

			default:
			break;
	    }
	
	    /* Some formats want stream headers to be separate. */
	    if (oc->oformat->flags & AVFMT_GLOBALHEADER)
	        c->flags |= CODEC_FLAG_GLOBAL_HEADER;
	
	    return st;
	}

	/* check that a given sample format is supported by the encoder */
	int FW_ffmpeg_encode::check_sample_fmt(AVCodec *codec, enum AVSampleFormat sample_fmt)
	{
		const enum AVSampleFormat *p = codec->sample_fmts;
		while (*p != AV_SAMPLE_FMT_NONE) {
			if (*p == sample_fmt)
				return 1;
			p++;
		}
		return 0;
	}


	/* Add an output stream. */
	AVStream *FW_ffmpeg_encode::add_audio_stream(
		AVFormatContext *oc,
		AVCodec **codec,
			enum AVCodecID codec_id,
		int sample_rate,
		int channels,
		int bit_rate,
		int fps
	)
	{
	    AVCodecContext *c;
	    AVStream *st;
	
	    /* find the encoder */
	    *codec = avcodec_find_encoder(codec_id);
	    if (!(*codec)) {
		std::string codec_str = std::string (avcodec_get_name(codec_id));
		throw MyException("Could not find video encoder for " + codec_str);
	    }
	
	    st = avformat_new_stream(oc, *codec);
	    if (!st) {
		std::string codec_str = std::string (avcodec_get_name(codec_id));
		throw MyException("Could not allocate audio stream");
	    }
	    st->id = oc->nb_streams-1;
	    c = st->codec;
	
	    switch ((*codec)->type) {
		case AVMEDIA_TYPE_AUDIO:
			st->id = 1;
			c->sample_fmt  = AV_SAMPLE_FMT_FLTP;
			if (!check_sample_fmt(audio_codec, c->sample_fmt)) {
				std::string sample_fmt_str = std::string (av_get_sample_fmt_name(c->sample_fmt));
				throw MyException("Could not find video encoder for " + sample_fmt_str);
			}
/*			c->time_base.den = sample_rate;
			c->time_base.num = 1;*/
			c->sample_rate = sample_rate;
			c->channels    = channels;
			c->bit_rate    = bit_rate;
			c->channel_layout = av_get_default_channel_layout(c->channels);
			c->delay = 0;
			// frame size 0 means unlimited, so we can internally set it to 1024
		break;
			
		default:
			break;
	    }
	
	    /* Some formats want stream headers to be separate. */
	    if (oc->oformat->flags & AVFMT_GLOBALHEADER)
	        c->flags |= CODEC_FLAG_GLOBAL_HEADER;
		
	    return st;
	}

	void FW_ffmpeg_encode::setAudioRealtime(bool b) {
		audioRealtime = b;
	}


	void FW_ffmpeg_encode::open_audio(AVFormatContext *oc, AVCodec *codec, AVStream *st)
	{
	    AVCodecContext *c;
	    int ret;

	    c = st->codec;
	    c->strict_std_compliance = -2;

	    /* open it */
	    ret = avcodec_open2(c, codec, NULL);
	    if (ret < 0) {
		std::string error_str = std::string (av_err2str(ret));
		throw MyException("Could not open audio codec: " + error_str);
	    }
	}

	void FW_ffmpeg_encode::write_audio_frame(AVFormatContext *oc, const uint8_t* audioBytes, int audioOffset, AVStream *st)
	{
		if (logging && verbose) AS3_trace("Encoding audio frame...");

		AVCodecContext *c;
		AVPacket pkt = { 0 }; // data and size must be 0;
#if LIBAVCODEC_VERSION_INT >= AV_VERSION_INT(55, 45, 101)
		AVFrame *audio_frame = avcodec_alloc_frame();
#else
		AVFrame *audio_frame = avcodec_alloc_frame();
#endif
		int got_packet, ret;

		av_init_packet(&pkt);
		pkt.data = NULL;
		pkt.size = 0;
		c = st->codec;

		if (verbose && logging) AS3_trace("Audio frame size: %d", c->frame_size);

		audio_frame->nb_samples = c->frame_size;

		uint8_t* output = NULL;
		if (c->channels == 1)
		{
			output = (uint8_t*)audioBytes;
		}
		else if (c->channels == 2)
		{
			// deinterleave samples
			output = (uint8_t*)malloc(audio_frame->nb_samples * 4 * 2);
			for (int i = 0; i < (audio_frame->nb_samples); i++)
			{
				uint8_t* src;
				uint8_t* dst;
				dst = (uint8_t *)output + (i * 4);
				src = (uint8_t *)audioBytes + (i * 2 * 4);
				memcpy(dst, src, 4);
				dst = (uint8_t *)output + ((audio_frame->nb_samples * 4)) + (i * 4);
				src = (uint8_t *)audioBytes + (i * 2 * 4) + 4;
				memcpy(dst, src, 4);
			}
		}

		ret = avcodec_fill_audio_frame(
			audio_frame,
			c->channels,
			c->sample_fmt,
			(uint8_t *)output,
			c->frame_size * av_get_bytes_per_sample(c->sample_fmt) * c->channels,
			1
			);
		if (ret < 0) {
			std::string error_str = std::string (av_err2str(ret));
			throw MyException("Error filling audio frame: " + error_str);
		}

		// in case the mode is not realtime then just add monotonic audio frame pts
		// we can also override to have this always on by audioRealtime false
		if (((!realtime && PTSmode == PTS_AUTO) || PTSmode == PTS_MONO) || !audioRealtime) {
			if (audio_frame->pts == AV_NOPTS_VALUE) audio_frame->pts = audio_sync_opts;
			audio_sync_opts = av_rescale_q(audio_frame->pts + audio_frame->nb_samples, audio_st->codec->time_base, audio_st->time_base);
			audio_frame->pts = audio_sync_opts;
			if (logging && verbose) AS3_trace("Audio PTS is non-realtime, value is %d", audio_frame->pts);
		}

		// in case of realtime try to use audio pts based on time
		// this needs audioRealtime set to true
		if (((realtime && PTSmode == PTS_AUTO) || PTSmode == PTS_REALTIME) && audioRealtime) {
			if (logging && verbose) AS3_trace("Audio PTS is realtime");

			double now = 0;
			int ptsIndex = 0;

			// iterate over all timestamps bytes marks
			for (int i = 0; i < audioData_timeStampsBytes.size(); i++) {
				// if this bytemark is still less or equal the current audio offset, remember that mark
				if (audioData_timeStampsBytes[i] <= audioOffset) {
					ptsIndex = i;
				}
			}

			if (logging && verbose) AS3_trace("Audio PTS, ptsIndex found: %d", ptsIndex);

			double closestPts = audioData_timeStamps.at(ptsIndex);
			if (logging && verbose) AS3_trace("Audio PTS, closestPts: %f", closestPts);

			double closestPtsBytemark = audioData_timeStampsBytes.at(ptsIndex);
			if (logging && verbose) AS3_trace("Audio PTS, closestPtsBytemark: %f", closestPtsBytemark);

			// calculate the difference in bytes between the closest previous pts mark and the current audio data offset(also in bytes)
			double diff = audioOffset - closestPtsBytemark;			

			// now convert this difference to samples
			double diffSamples = diff / ((float)av_get_bytes_per_sample(c->sample_fmt) * audio_st->codec->channels);

			// now convert this difference in samples to ms
			double diffPts = (diffSamples / c->sample_rate) * 1000;
			
			// the pts of the audio frame we are adding is the closest pts of audio data we've added before plus the difference of the bytes it takes to get to our audioOffset(so we are kind of guessing the pts based on closest previously known pts)
			now = closestPts + diffPts;			
			long nowPts = 0;

			// rescale to audio stream timebase
			nowPts = av_rescale_q(now, (AVRational){1, 1000}, audio_st->codec->time_base);

			// rescale to container timebase
			nowPts = av_rescale_q(nowPts, audio_st->codec->time_base, audio_st->time_base);

			if (logging && verbose) AS3_trace("Assumed pts %f, actual pts %d", audio_sync_opts, nowPts);

			// the difference between the assumed pts and the real pts is larger than 2 samples
			if (fabs(nowPts - audio_sync_opts) > 2048) {
				audio_sync_opts = nowPts;
				if (logging && verbose) AS3_trace("Difference between assumed and actual pts > 2 samples, correcting pts");
			}

			// theoretical pts that would be if we used monotonic pts
			audio_sync_opts = av_rescale_q(audio_sync_opts + audio_frame->nb_samples, audio_st->codec->time_base, audio_st->time_base);

			if (audio_frame->pts == AV_NOPTS_VALUE) {
				// rescale to codec pts
	                        //(ms / 1000) * sample_rate = samples
				audio_frame->pts = audio_sync_opts;
			}

			if (logging && verbose) AS3_trace("Audio PTS, audioOffset %d, diff %f, diff in samples %f, diff in ms %f, now is %f, final audio pts is %d", audioOffset, diff, diffSamples, diffPts, now, audio_frame->pts);
		}                 		
	
		// make sure we add good pts		
//		if (audio_frame->pts > oldAudioPts) {
			ret = avcodec_encode_audio2(c, &pkt, audio_frame, &got_packet);

			if (ret < 0) {
				std::string error_str = std::string (av_err2str(ret));
				throw MyException("Error encoding audio frame: " + error_str);
			}

			if (got_packet) {

				pkt.stream_index = st->index;

				// TODO: Does FFmpeg rescale the codec pts to stream pts when writing the frame? if not add it here
	
				/* Write the compressed frame to the media file. */
				ret = av_interleaved_write_frame(oc, &pkt);
				audioFramesWritten++;
				if (ret != 0) {
					std::string error_str = std::string (av_err2str(ret));
					throw MyException("Error while writing audio frame: " + error_str);
				}
	
			}
			oldAudioPts = audio_frame->pts;
/*		} else {
			AS3_trace("Warning: Audio PTS wrong, dropping audio frame");
		}*/

		// free the output as soon as possible, so after encoding 
		if (c->channels == 2)
		{
			free(output);
		}

		av_free(audio_frame);
	}

	void FW_ffmpeg_encode::close_audio(AVFormatContext *oc, AVStream *st)
	{
	    avcodec_close(st->codec);
//		av_free(audio_frame);
	}

	/**************************************************************/
	/* video output */

	void FW_ffmpeg_encode::open_video(AVFormatContext *oc, AVCodec *codec, AVStream *st)
	{
	    int ret;
		
	    AVCodecContext *c = st->codec;
	    /* switch off erroring out on experimental codec */
//	    c->strict_std_compliance = -2;

	    /* open the codec */
	    ret = avcodec_open2(c, codec, NULL);
	    if (ret < 0) {
		std::string error_str = std::string (av_err2str(ret));
		throw MyException("Could not open video codec: " + error_str);
	    }

	    /* allocate and init a re-usable frame */
#if LIBAVCODEC_VERSION_INT >= AV_VERSION_INT(55, 45, 101)
		frame = avcodec_alloc_frame();
#else
		frame = avcodec_alloc_frame();
#endif
	    if (!frame) {
		throw MyException("Could not allocate video frame.");
	    }   	

	    /* Allocate the encoded raw picture. */
	    ret = avpicture_alloc(&dst_picture, c->pix_fmt, c->width, c->height);
	    if (ret < 0) {
		std::string error_str = std::string (av_err2str(ret));
		throw MyException("Could not allocate picture: " + error_str);
	    }

	    /* If the output format is not YUV420P, then a temporary YUV420P
	     * picture is needed too. It is then converted to the required
	     * output format. */
	    if (c->pix_fmt != AV_PIX_FMT_YUV420P) {
	        ret = avpicture_alloc(&src_picture, AV_PIX_FMT_YUV420P, c->width, c->height);
	        if (ret < 0) {
			std::string error_str = std::string (av_err2str(ret));
			throw MyException("Could not allocate temporary picture: " + error_str);
	        }
	    }

	    /* copy data and linesize picture pointers to frame */
	    *((AVPicture *)frame) = dst_picture;
	}

	void FW_ffmpeg_encode::write_video_frame(AVFormatContext *oc, unsigned char *imageBytes, AVStream *st)
	{
		int ret;
		if (logging && verbose) AS3_trace("Encoding video frame...");

		AVCodecContext *c = st->codec;
		
		if (c->pix_fmt != AV_PIX_FMT_ARGB) {
			/* as we get ARGB picture from Flash, we must convert it
				* to the codec pixel format if needed */
			if (!sws_ctx) {
				sws_ctx = sws_getContext(c->width, c->height, AV_PIX_FMT_ARGB,
											c->width, c->height, c->pix_fmt,
											sws_flags, NULL, NULL, NULL);
				if (!sws_ctx) {
					throw MyException("Could not initialize the conversion context");                                                                                                      
				}
			}
	
		const int srcstride[3] = {c->width * 4, 0, 0};
		uint8_t *data_pos[3] = {imageBytes, NULL, NULL};
		sws_scale(sws_ctx,
					(const uint8_t * const *)data_pos, srcstride,
					0, c->height, dst_picture.data, dst_picture.linesize);
		} else {
			// if destination format is ARGB(unlikely), copy image directly
			dst_picture.data[0] = imageBytes;
			dst_picture.linesize[0] = c->width * 4;
		}


		if (oc->oformat->flags & AVFMT_RAWPICTURE) {
	        	/* Raw video case - directly store the picture in the packet */
		        AVPacket pkt;
		        av_init_packet(&pkt);
		
		        pkt.flags        |= AV_PKT_FLAG_KEY;
		        pkt.stream_index  = st->index;
		        pkt.data          = dst_picture.data[0];
		        pkt.size          = sizeof(AVPicture);
			ret = av_interleaved_write_frame(oc, &pkt);
			videoFramesWritten++;
			if (!encodingStarted) {
				encodingStartedFrame = videoFramesPrepared;
				encodingStarted = true;
			}
		}
		else {
			AVPacket pkt = { 0 };
			int got_packet;
			av_init_packet(&pkt);

			/* encode the image */
			ret = avcodec_encode_video2(c, &pkt, frame, &got_packet);
			if (ret < 0) {
				std::string error_str = std::string (av_err2str(ret));
				throw MyException("Error encoding video frame: " + error_str);                                                                                                     
			}

	        	/* If size is zero, it means the image was buffered. */

	      	if (!ret && got_packet && pkt.size) {
					pkt.stream_index = st->index;
					// TODO: Does FFmpeg rescale the codec pts to stream pts when writing the frame? if not add it here

					/* Write the compressed frame to the media file. */
					ret = av_interleaved_write_frame(oc, &pkt);
					videoFramesWritten++;
			} else {
				ret = 0;
			}
		}
		if (ret != 0) {
			std::string error_str = std::string (av_err2str(ret));
			throw MyException("Error encoding video frame: " + error_str);                                                                                                     
		}
	}

	void FW_ffmpeg_encode::close_video(AVFormatContext *oc, AVStream *st)
	{
		if (video_st->codec->pix_fmt != AV_PIX_FMT_YUV420P) {
			av_free(src_picture.data[0]);
		}
		av_free(dst_picture.data[0]);
		av_free(frame);
		avcodec_close(st->codec);
	}
	                                                                             
	char* FW_ffmpeg_encode::allocString(char const *msg) {
	    size_t needed = snprintf(NULL, 0, "%s", msg);
	    char  *buffer = (char*)malloc(needed);
	    snprintf(buffer, needed, "%s", msg);
	    return buffer;
	}

	size_t FW_ffmpeg_encode::getStringLength(char const *msg) {
	    size_t needed = snprintf(NULL, 0, "%s", msg);
	    return needed;
	}

	void FW_ffmpeg_encode::encode_it()
	{
		for (;;)
		{
			if (videoFramesSent >= videoDataVector.size()) break;

	        /* Compute current audio and video time. */
	        if (audio_st)
	            audio_pts = (double)audio_st->pts.val * audio_st->time_base.num / audio_st->time_base.den;
	        else
	            audio_pts = 0.0;
	
	        if (video_st)
	            video_pts = (double)video_st->pts.val * video_st->time_base.num / video_st->time_base.den;
	        else
	            video_pts = 0.0;

                double video_pts_max = 0;

                // Realtime mode: Compute video_pts_max based on timestamp of LAST frame in the queue instead of assumed fps (time_base.den) - ASSUMED fps might be wrong in case our recording fps
		// goes under target video fps. On the other hand in non-realtime mode we work with assumed fps.

		if ((!realtime && PTSmode == PTS_AUTO) || PTSmode == PTS_MONO) {
			video_pts_max = ((double)videoFramesTotal) / (double)video_st->codec->time_base.den;
		} else {
			// take the pts of the last video frame
			double video_pts_max_millis = videoDataVector_timeStamps.at(videoDataVector_timeStamps.size() - 1);
			// we want the pts max in seconds, so divide by 1000
			video_pts_max = video_pts_max_millis / 1000;
		}

			double audio_pts_max = 0;

			if (audio_st) {
				if (((!realtime && PTSmode == PTS_AUTO) || PTSmode == PTS_MONO) || !audioRealtime) {
					audio_pts_max = (double)audioDataSizeTotal / ((double)audio_st->codec->sample_rate * (double)av_get_bytes_per_sample(audio_st->codec->sample_fmt) * (double)audio_st->codec->channels);
				} else {
					double audio_pts_max_millis = audioData_timeStamps.at(audioData_timeStamps.size() - 1);
					audio_pts_max = audio_pts_max_millis / 1000;
					double diff = audioDataSizeTotal - audioData_timeStampsBytes.at(audioData_timeStampsBytes.size() - 1);			
					double diffSamples = diff / (float)av_get_bytes_per_sample(audio_st->codec->sample_fmt);	
					double diffPts = diffSamples / audio_st->codec->sample_rate;
					audio_pts_max += diffPts;
				}
			}

			if ((!video_st || video_pts > video_pts_max)) {
				break;
			}

			if (verbose && logging) AS3_trace("videoFramesSent: %lu, audioFramesSent: %lu, video_pts: %f, audio_pts: %f", videoFramesSent, audioFramesSent, video_pts, audio_pts);
				
			/* write interleaved audio and video frames */
			if (!video_st || (video_st && audio_st && audio_pts < video_pts && audio_pts < audio_pts_max && audioFramesSent < floor(audioFramesTotal) ))
			{
				if (verbose) AS3_trace("video_pts %f audio_pts %f video_pts_max %f audio_pts_max %f", video_pts, audio_pts, video_pts_max, audio_pts_max);

				#ifdef __AVM2
				if (verbose) AS3_trace(
					"audio frame byte size 1: %d, audio frame byte size 2: %d\n",
					audio_st->codec->frame_size * audio_st->codec->channels * 4,
					audio_st->codec->frame_size * av_get_bytes_per_sample(audio_st->codec->sample_fmt) * audio_st->codec->channels
				);
				#endif
				int audioOffset = (audioFramesSent * (audio_st->codec->frame_size * audio_st->codec->channels * av_get_bytes_per_sample(audio_st->codec->sample_fmt)));
				uint8_t* audioFrameDataPtr = audioData + audioOffset;
				write_audio_frame(oc, audioFrameDataPtr, audioOffset, audio_st);
				audioFramesSent++;
			}
			else if (video_st)
			{
				#ifdef DEMO
				blitLogo(videoDataVector.at(videoFramesSent), global_width, 85, 60);
				#endif				
				if ((realtime && PTSmode == PTS_AUTO) || PTSmode == PTS_REALTIME) {
					if (logging && verbose) AS3_trace("PTS is realtime");
					double now = videoDataVector_timeStamps.at(videoFramesSent);
					// rescale to codec pts
					frame->pts = av_rescale_q(now, (AVRational){1, 1000}, video_st->codec->time_base);
					// rescale to container pts
					frame->pts = av_rescale_q(frame->pts, video_st->codec->time_base, video_st->time_base);										
				}                 				
				if (frame->pts > oldPts) {
					write_video_frame(oc, videoDataVector.at(videoFramesSent), video_st);
					oldPts = frame->pts;
				}
				if ((!realtime && PTSmode == PTS_AUTO) || PTSmode == PTS_MONO) {
					if (logging && verbose) AS3_trace("PTS is mono");
					frame->pts += av_rescale_q(1, video_st->codec->time_base, video_st->time_base);
				} 

				free(videoDataVector.at(videoFramesSent));
				videoFramesSent++;
			}
		}
	}

	void FW_ffmpeg_encode::AS3_trace(char *str, ...) {

	#ifdef __AVM2
	// could be fine with simply fprintf, stderr to trace but in case this code is running in thread its not enough


		va_list argptr;
		
		va_start(argptr, str);
		size_t needed = vsnprintf(NULL, 0, str, argptr);
		va_end(argptr);

		char *str_buffer = (char*)malloc(needed);
			
		va_start(argptr, str);
		vsprintf(str_buffer, str, argptr);
		va_end(argptr);

		inline_as3(
			"import flash.events.StatusEvent;\n"
			"var str:String = CModule.readString(%0, %1);"
			"CModule.rootSprite.dispatchEvent( new StatusEvent( StatusEvent.STATUS, false, false, 'trace', str ));\n"
			"trace('[FlashyWrappers] ' + str);"
			: : "r"(str_buffer), "r"(needed)
		);

		free(str_buffer);
	#endif
	}

	bool FW_ffmpeg_encode::initThreadFinished() {
		if (initFinished) return true; else return false;
	}

	bool FW_ffmpeg_encode::canFinish() {
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

	void FW_ffmpeg_encode::flush() {
		
		// Flush any remaining packets from video encoder
		// **********************************************

		// send the remaining audio frames in case it didn't happen yet
		while (audioFramesSent < floor(audioFramesTotal)) {
			int audioOffset = (audioFramesSent * (audio_st->codec->frame_size * audio_st->codec->channels * av_get_bytes_per_sample(audio_st->codec->sample_fmt)));
			uint8_t* audioFrameDataPtr = audioData + audioOffset;
			write_audio_frame(oc, audioFrameDataPtr, audioOffset, audio_st);
			audioFramesSent++;
		}
		int got_packet;
		for (got_packet = 1; got_packet;) {
			AVPacket pkt = { 0 };
			av_init_packet(&pkt);
			ret = avcodec_encode_video2(video_st->codec, &pkt, NULL, &got_packet);

			if (ret < 0) {
				std::string error_str = std::string (av_err2str(ret));
			        throw MyException("Error encoding video frame while flushing" + error_str);
			}

			if (got_packet) {
				if (!ret && got_packet && pkt.size) {
					pkt.stream_index = video_st->index;
					/* Write the compressed frame to the media file. */
					ret = av_interleaved_write_frame(oc, &pkt);
					videoFramesWritten++;
				}
				av_free_packet(&pkt);
			}
		}
		// Flush the rest of audio packets
		// *******************************
		if (audio_st != NULL) {
			for (got_packet = 1; got_packet;) {
				AVPacket pkt = { 0 };
				av_init_packet(&pkt);
				ret = avcodec_encode_audio2(audio_st->codec, &pkt, NULL, &got_packet);
				if (ret < 0) {
					std::string error_str = std::string(av_err2str(ret));
					throw MyException("Error encoding audio frame while flushing" + error_str);
				}
				if (got_packet) {
					if (!ret && got_packet && pkt.size) {
						pkt.stream_index = audio_st->index;
						ret = av_interleaved_write_frame(oc, &pkt);
						audioFramesWritten++;
					}
					av_free_packet(&pkt);
				}
			}
		}
	}

	FW_ffmpeg_encode::FW_ffmpeg_encode() {
		hThread = NULL;
		hInitThread = NULL;
		sws_flags = SWS_BILINEAR;
		output_buffer_size = 0;
		videoFramesSent = 0;
		videoFramesPrepared = 0;
		audioFramesSent = 0;
		audioFramesTotal_intermediate = 0;
		audioDataSizeTotal_intermediate = 0;
		videoFramesTotal_intermediate = 0;
		videoFramesWritten = 0;
		audioFramesWritten = 0;
		videoFramesTotal = 0;
		audioFramesTotal = 0;
		audioDataSizeTotal = 0;
		threadEncodingRunning = false;
		threadDataAvailable = false;
		threadRunning = true;
		initFinished = false;
		recordAudio = true;
		verbose = false;
		logging = true;
		PTSmode = PTS_AUTO;
		framedropMode = FRAMEDROP_AUTO;
		RAMadjusterFPSDivider = 2;
		RAMadjusterTolerance = 1.5;
		RAMadjusterCount = 0;
	}

	void FW_ffmpeg_encode::ffmpeg_setFrames(int f) {
		videoFramesTotal = f;
	}

	// this finishes ffmpeg_init on background thread to avoid any lags
	int FW_ffmpeg_encode::finishInit() {

		if (_intermediate_buffer_length > 0 && hThread == NULL) {

#ifdef WIN32
			hThread = (HANDLE)_beginthread(FW_ffmpeg_encode::runencoderthread, 0, (void*)this);
#else
			int val = pthread_create(&hThread, NULL, FW_ffmpeg_encode::runencoderthread, (void*)this);
			if (val != 0) {
				throw MyException("Couldn't create encoder thread");
			}
#endif
		}

		/* Initialize libavcodec, and register all codecs and formats. */
		av_register_all();

		// internal buffer for ffmpeg's IO 
//		buffer = (unsigned char*)av_malloc(8192);
//		pb = avio_alloc_context(buffer, 8192, 1, &return_buffer, readFunction, writeFunction, seekFunction); 

	        ret = avio_open(&pb, "video.mp4", AVIO_FLAG_WRITE);
		if (ret < 0) {
			throw MyException("Cannot open the output file in VFS.");
		}

		avformat_alloc_output_context2(&oc, NULL, (const char*)str_container, "video.mp4");

		if (!oc) {
			throw MyException("Cannot allocate output context. Make sure you specified a supported container with this version of FlashyWrappers.");
		}

		oc->pb = pb;
		fmt = oc->oformat;

		/* Add the audio and video streams using the default format codecs
		* and initialize the codecs. */
		video_st = NULL;
		audio_st = NULL;

		if (fmt->video_codec != AV_CODEC_ID_NONE) {
			AVCodec *encoder = avcodec_find_encoder_by_name((const char*)str_codec_video);
			if (encoder == NULL) {
				throw MyException("Video encoder not found! Make sure your build of FlashyWrappers comes with the specified video codec.");
			} else video_st = add_video_stream(oc, &video_codec, encoder->id, sca_w, sca_h, sca_bitrate, sca_fps, sca_speed, sca_keyframe_frequency, sca_quality);
		}
	  
		#ifndef NOAUDIO
		if (fmt->audio_codec != AV_CODEC_ID_NONE && strlen((const char*)str_codec_audio) != 0) {
			AVCodec *encoder = avcodec_find_encoder_by_name((const char*)str_codec_audio);
			if (encoder == NULL) {
				throw MyException("Audio encoder not found! Make sure your build of FlashyWrappers comes with the specified audio codec.");
			} else audio_st = add_audio_stream(oc, &audio_codec, encoder->id, audio_sample_rate, audio_channels, audio_bit_rate, sca_fps);
		} else {
			if (strlen((const char*)str_codec_audio) == 0) {
				recordAudio = false;
			}
		}

		#endif

		if (verbose && logging) AS3_trace("video_st: %p, audio_st %p", video_st, audio_st);
	
		/* Now that all the parameters are set, we can open the audio and
		* video codecs and allocate the necessary encode buffers. */
		if (video_st) open_video(oc, video_codec, video_st);
		if (audio_st) open_audio(oc, audio_codec, audio_st);

		av_dump_format(oc, 0, "", 1);

		/* Write the stream header, if any. */

		if (video_st->codec->codec_id == AV_CODEC_ID_H264) {
			AVDictionary *dict = NULL;
			av_dict_set( &dict, "movflags", "faststart", 0 );
			ret = avformat_write_header(oc, &dict);
		} else {
			ret = avformat_write_header(oc, NULL);
		}

		// TODO: check if frame size is 0, if yes then set it to 1024 (I think)

		if (ret < 0) {
			std::string error_str = std::string (av_err2str(ret));
			throw MyException("Error occured while opening output stream: " + error_str);                                                                                                     
		}

		if (frame) frame->pts = 0;
	
		audioData = (unsigned char*) malloc(0);
		audioData_intermediate = (unsigned char*)malloc(0);		

		#ifdef __AVM2	
		free(str_container);
		free(str_codec_video);
		free(str_codec_audio);
		#endif

		// mark initialisation as finished
		initFinished = true;

		timeval tv;
		gettimeofday (&tv, NULL);
		double millis = double(tv.tv_sec) * 1000 + (tv.tv_usec) * 0.001;
		AS3_trace("Finished at timestamp: %f", millis);
	}

	void FW_ffmpeg_encode::configureRAMAdjuster(double RAMadjusterFPSDivider, double RAMadjusterTolerance) {
		this->RAMadjusterFPSDivider = RAMadjusterFPSDivider;
		this->RAMadjusterTolerance = RAMadjusterTolerance;
		if (logging && verbose) AS3_trace("Changing RAM Guard configuration, fps divider %f, buffer overflow tolerance %f %", this->RAMadjusterFPSDivider, this->RAMadjusterTolerance * 100);
	}


	int FW_ffmpeg_encode::ffmpeg_init(unsigned char* str_container, unsigned char* str_codec_video, unsigned char* str_codec_audio, int sca_w, int sca_h, int sca_fps, int sca_speed, int sca_bitrate, int sca_quality, int sca_keyframe_frequency, int audio_sample_rate, int audio_channels, int audio_bit_rate, unsigned long _intermediate_buffer_length, int _realtime, int _audio) {

	        this->str_container = str_container;
		this->str_codec_video = str_codec_video;
	        this->str_codec_audio = str_codec_audio;
	        this->sca_w = sca_w;
	        this->sca_h = sca_h;
	        this->sca_fps = sca_fps;
	        this->sca_speed = sca_speed;
	        this->sca_bitrate = sca_bitrate;
	        this->sca_quality = sca_quality;
	        this->sca_keyframe_frequency = sca_keyframe_frequency;
	        this->audio_sample_rate = audio_sample_rate;
	        this->audio_channels = audio_channels;
	        this->audio_bit_rate = audio_bit_rate;
	        this->_intermediate_buffer_length = _intermediate_buffer_length;
	        this->_realtime = _realtime;
	        this->_audio = _audio;
		this->audioRealtime = false;

		if (logging) AS3_trace("Initializing...");

		this->realtime = _realtime;
		this->startRecording = 0;
		this->oldPts = -1;
		this->oldAudioPts = -1;

		// reset the scaling context to NULL
		sws_ctx = NULL;

		// reset the fps sync component vars
		step = 0;
		delta = 0;
		millisOld = 0;
		stepAccum = 0;
		stepTarget = 0;
		adjustedThisFlush = false;

		encodingStartedFrame = 0;
		encodingStarted = false;

		// if stage fps is set then we compute the step value
		if ((realtime && framedropMode == FRAMEDROP_AUTO) || framedropMode == FRAMEDROP_ON) {
			step = 1000 / (float)sca_fps;
		}

		this->global_fps = sca_fps;
		this->intermediate_buffer_length = _intermediate_buffer_length;

		if (logging && verbose) AS3_trace( "Intermediate buffer length %lu", intermediate_buffer_length);

		if (_intermediate_buffer_length > 0 && hInitThread == NULL) {
#ifdef WIN32
			hThread = (HANDLE)_beginthread(FW_ffmpeg_encode::runInitThread, 0, (void*)this);
#else
			int val = pthread_create(&hInitThread, NULL, FW_ffmpeg_encode::runInitThread, (void*)this);
			if (val != 0) {
				throw MyException("Couldn't create init thread");
			}
#endif
		} else {
			finishInit();		
		}    

		return 1;
	}

	void FW_ffmpeg_encode::setPTSMode(uint32_t mode) {
		PTSmode = mode;
	}

	void FW_ffmpeg_encode::setFramedropMode(uint32_t mode) {
		framedropMode = mode;
		if (mode == FRAMEDROP_AUTO) {
			if (realtime) {
				step = 1000 / (float)this->global_fps;
			} else {
				step = 0;
			}
		} 
		if (mode == FRAMEDROP_OFF) {
			step = 0;
		}
		if (mode == FRAMEDROP_ON) {
			step = 1000 / (float)this->global_fps;
		}
	}

	void FW_ffmpeg_encode::ffmpeg_finish() {
	    /* Write the trailer, if any. The trailer must be written before you
	     * close the CodecContexts open when you wrote the header; otherwise
	     * av_write_trailer() may try to use memory that was freed on
	     * av_codec_close(). */
		// if anything is left in the intermediate buffers, we need to get it to the encoder now

		// If some encoding is still running after calling finish, wait..

		av_write_trailer(oc);
		avio_close(pb);
		if (verbose && logging) AS3_trace("Done!");	
	}

	std::stringstream *FW_ffmpeg_encode::ffmpeg_getStream() {
		std::ifstream file( "video.mp4" );
		if ( file )
		{
			return_buffer << file.rdbuf();		
			file.close();
		} else {
			AS3_trace("Can't open VFS video file!");
		}
	 	return &return_buffer;
	}

	long FW_ffmpeg_encode::ffmpeg_getVideoFramesSent() {
		return videoFramesSent;
	}

	void FW_ffmpeg_encode::ffmpeg_reset() {
		/* Close each codec. */
		if (video_st)
			close_video(oc, video_st);
		if (audio_st)
			close_audio(oc, audio_st);

		/* free the stream */
		avformat_free_context(oc);
//		av_free(buffer);

		// free domain memory buffer
		return_buffer.str("");
		free(audioData);

		// important bugfix - don't forget to free the SWS context otherwise it will keep reusing the old frame size data
		if (sws_ctx != NULL) {
			sws_freeContext(sws_ctx);
			sws_ctx = NULL;
		}

		RAMadjusterFPSDivider = 2;
		RAMadjusterTolerance = 1.5;
		RAMadjusterCount = 0;

		encodingStarted = false;
		adjustedThisFlush = false;
		encodingStartedFrame = 0;

		videoDataVector.clear();
		videoDataVector_timeStamps.clear();
		videoDataVector_intermediate.clear();
		videoDataVector_timeStamps_intermediate.clear();

		audioData_timeStamps.clear();
		audioData_timeStampsBytes.clear();
		audioData_timeStamps_intermediate.clear();
		audioData_timeStampsBytes_intermediate.clear();

		audioDataSizeTotal = 0;
		videoFramesSent = 0;
		videoFramesPrepared = 0;
		videoFramesWritten = 0;
		videoFramesTotal = 0;
		audioFramesTotal = 0;
		audioFramesSent = 0;
		audioFramesWritten = 0;
		audioFramesTotal_intermediate = 0;
		audioDataSizeTotal_intermediate = 0;
		videoFramesTotal_intermediate = 0;
		threadEncodingRunning = false;
		threadDataAvailable = false;
		audioRealtime = false;
		threadRunning = true;
		initFinished = false;
		hInitThread = NULL;
	}

	 bool FW_ffmpeg_encode::flush_intermediate_frames() {
		bool flushed = false;

		if (videoDataVector_intermediate.size() > 0) {
			flushed = true;
		}

		// flush intermediate audio data
		if (recordAudio && audioDataSizeTotal_intermediate > 0)  {
			audioData = (unsigned char*)realloc(audioData, audioDataSizeTotal + audioDataSizeTotal_intermediate);
			memcpy(audioData + audioDataSizeTotal, audioData_intermediate, audioDataSizeTotal_intermediate);
			audioDataSizeTotal += audioDataSizeTotal_intermediate;
			audioFramesTotal += (float)audioDataSizeTotal_intermediate / (float)(audio_st->codec->frame_size * av_get_bytes_per_sample(audio_st->codec->sample_fmt) * audio_st->codec->channels);
			// reset the intermediate audio data, we can free because we memcpied
			free(audioData_intermediate);
			audioDataSizeTotal_intermediate = 0;
			audioData_intermediate = NULL;
			audioData_intermediate = (unsigned char*)malloc(0);
		}

		// flush intermediate audio timestamps
		for (int a = 0; a < audioData_timeStamps_intermediate.size(); a++) {
			audioData_timeStamps.push_back(audioData_timeStamps_intermediate[a]);
			audioData_timeStampsBytes.push_back(audioData_timeStampsBytes_intermediate[a]);
		}

		// flush intermediate video data
		for (int a = 0; a < videoDataVector_intermediate.size(); a++) {
			videoDataVector.push_back(videoDataVector_intermediate[a]);
			videoDataVector_timeStamps.push_back(videoDataVector_timeStamps_intermediate[a]);
		}
		
		// reset the intermediate video data, we don't want to free the frames inside the vector, those will be freed during the encoding
		videoFramesTotal_intermediate = 0;
		videoDataVector_intermediate.clear();
		videoDataVector_timeStamps_intermediate.clear();

		// reset the intermediate audio data
		audioData_timeStamps_intermediate.clear();
		audioData_timeStampsBytes_intermediate.clear();

		// thread can start eating the data :P
		if (flushed) threadDataAvailable = true;

		return flushed;
	}

	int FW_ffmpeg_encode::ffmpeg_addVideoData(unsigned char *sca_data, int sca_data_size)
	{
		// only count time in realtime mode
		double millis;
		if (realtime) {
#ifndef _MSC_VER
			timeval tv;
			gettimeofday (&tv, NULL);
			millis = double(tv.tv_sec) * 1000 + (tv.tv_usec) * 0.001;
#else
			millis = GetTickCount();
#endif
		        if (millisOld != 0) {
		            delta = millis - millisOld;
		            stepAccum += delta;
		        } else delta = millis;
		       	millisOld = millis;
		}

		// only add frames when needed, in non-realtime all of those vars are 0(just adding zeroes) so this condition is always true
	        if (stepAccum >= stepTarget) {
	            stepTarget += step;

		if (logging && verbose) {
			bool b1 = false;
			bool b2 = false;
			if (threadEncodingRunning) b1 = true;
			if (threadDataAvailable) b2 = true;
			AS3_trace("Adding video data - size: %lu, videoDataVector_intermediate.size() %d, threadEncodingRunning %d, threadDataAvailable %d", sca_data_size, videoDataVector_intermediate.size(), b1, b2);
		}

		unsigned char *data = sca_data;
		
		/* THREADING, ie realtime mode since 2.4 */
		if (intermediate_buffer_length > 0) {
			if (startRecording == 0) {
				startRecording = millis;
			}

			// check every 20 frames how fast the encoder is consuming and whenever its keeping up with the incoming frames
			videoFramesPrepared++;

			if (!adjustedThisFlush && videoFramesPrepared > encodingStartedFrame + 10) {
				encodingStartedFrame = videoFramesPrepared;
				if (logging && verbose) AS3_trace("Buffer size %d vs compare %d ", videoDataVector_intermediate.size(), (int)((float)intermediate_buffer_length * this->RAMadjusterTolerance));

				// check if the encoder is keeping up, it not increase the step(decrease fps in effect)
				if (videoDataVector_intermediate.size() > (int)((float)intermediate_buffer_length * this->RAMadjusterTolerance)) {
					if (logging && verbose) AS3_trace("Encoder isn't keeping up, there are already %d frames in intermediate buffer(expected around %d) - original step is %f(%f fps), increasing step to save RAM", videoDataVector_intermediate.size(), intermediate_buffer_length, step, 1000 / step);
					adjustedThisFlush = true;
					step = step * this->RAMadjusterFPSDivider;
					if (logging && verbose) AS3_trace("Adjusted step to %f(%f fps), let's see how that works out...", step, 1000 / step);
					RAMadjusterCount++;
				}
			
				// check if the encoder started keeping up again and we previously decreased - this has more strict tolerance though to not raise the fps back up all the time - buffer size has to be 2x closer to the expected size
/*				if (RAMadjusterCount > 0 && videoDataVector_intermediate.size() <= (int)((float)intermediate_buffer_length * (this->RAMadjusterTolerance * 0.5) )) {
					if (logging && verbose) AS3_trace("Encoder is keeping up now, there are %d frames in intermediate buffer", videoDataVector_intermediate.size(), intermediate_buffer_length);
					adjustedThisFlush = true;
					step = step / this->RAMadjusterFPSDivider;
					if (logging && verbose) AS3_trace("Adjusted step back to %f(%f fps)...", step, 1000 / step);
					RAMadjusterCount--;
				}*/

			}

			double timestamp = (millis - startRecording);
			if (logging && verbose) AS3_trace("Pushing video timestamp: %f", timestamp);

			videoDataVector_intermediate.push_back(data);
			videoDataVector_timeStamps_intermediate.push_back(timestamp);
			// if encoder thread is NOT eating data right now give it more data
			if (videoDataVector_intermediate.size() > intermediate_buffer_length && !threadEncodingRunning && !threadDataAvailable) {
				adjustedThisFlush = false;
				try_flushAndThread();
			}
		} else {
			/* NO THREADING */
			videoDataVector.push_back(data);
			encode_it();			
		}

		// Don't allow more than 30 seconds in DEMO (new since 2.25)
		#ifdef DEMO	
			if ((float)videoFramesSent / (float)this->global_fps > 30) { 
				throw MyException("No more than 30 seconds recording allowed in free mode. Visit http://www.flashywrappers.com to buy an upgrade.");
			}
		#endif
		return 1;
		} else {
			// free the data!
			free(sca_data);
			return 0;
		}
	}

	bool FW_ffmpeg_encode::try_flushAndThread() {
		if (flush_intermediate_frames()) {
			if (logging && verbose) AS3_trace("flush_intermediate_frames true");
			// create the encoder thread if not created
			if (hThread == NULL) {
#ifdef WIN32
				hThread = (HANDLE)_beginthread(FW_ffmpeg_encode::runencoderthread, 0, (void*)this);
#else
				int val = pthread_create(&hThread, NULL, FW_ffmpeg_encode::runencoderthread, (void*)this);
				if (val != 0) {
					throw MyException("Couldn't create encoder thread");
				}
#endif
			}
			return true;
		}
		else {
			if (verbose && logging) AS3_trace("flush_intermediate_frames false");
		}
		return false;
	}

	#ifdef WIN32
	void FW_ffmpeg_encode::runencoderthread(void *param) {
	#else
	void *FW_ffmpeg_encode::runencoderthread(void *param) {
	#endif
		FW_ffmpeg_encode *fw = ((FW_ffmpeg_encode*)param);
		while (fw->threadRunning) {
			if (fw->threadDataAvailable) {
				fw->threadEncodingRunning = true;
				fw->threadDataAvailable = false;
				fw->encode_it();
				fw->threadEncodingRunning = false;
			}
		}
		#ifdef WIN32
		_endthread();
		#endif	
	}

	#ifdef WIN32
	void FW_ffmpeg_encode::runInitThread(void *param) {
	#else
	void *FW_ffmpeg_encode::runInitThread(void *param) {
	#endif
		FW_ffmpeg_encode *fw = ((FW_ffmpeg_encode*)param);	
		fw->finishInit();
		#ifdef WIN32
		_endthread();
		#endif	
	}

	void FW_ffmpeg_encode::ffmpeg_addAudioData(unsigned char *sca_data, int sca_data_size)
	{
		timeval tv2;
		gettimeofday (&tv2, NULL);
		double millis2 = double(tv2.tv_sec) * 1000 + (tv2.tv_usec) * 0.001;

		if (intermediate_buffer_length == 0) {
			audioData = (unsigned char*) realloc(audioData, audioDataSizeTotal + sca_data_size);
			memcpy(audioData + audioDataSizeTotal, sca_data, sca_data_size);
			audioDataSizeTotal += sca_data_size;
			audioFramesTotal += (float)sca_data_size / (float)(audio_st->codec->frame_size * av_get_bytes_per_sample(audio_st->codec->sample_fmt) * audio_st->codec->channels);
		} else {
			double millis;

			// what time is it now when adding audio frame?
			if (realtime) {
	#ifndef _MSC_VER
				timeval tv;
				gettimeofday (&tv, NULL);
				millis = double(tv.tv_sec) * 1000 + (tv.tv_usec) * 0.001;
	#else
				millis = GetTickCount();
	#endif
			        if (millisOld != 0) {
			            delta = millis - millisOld;
			            stepAccum += delta;
			        } else delta = millis;
			       	millisOld = millis;
			}

			if (startRecording == 0) {
				startRecording = millis;
			}

			audioData_intermediate = (unsigned char*)realloc(audioData_intermediate, audioDataSizeTotal_intermediate + sca_data_size);
			memcpy(audioData_intermediate + audioDataSizeTotal_intermediate, sca_data, sca_data_size);

			double audioDuration = 0;
			// compute audio duration in ms
			if (audio_st->codec->sample_rate != 0) {
				audioDuration = (((float)sca_data_size / (float)(av_get_bytes_per_sample(audio_st->codec->sample_fmt) * audio_st->codec->channels)) / (float)audio_st->codec->sample_rate) * 1000;
			}

			if (logging && verbose) AS3_trace("Audio duration: %d", audioDuration);

			// in case its 0-duration do not add 
			if (audioDuration > 0) {
				
				double timestamp = (millis - startRecording);
				// only substract duration for microphone
				if (audio_st->codec->channels == 1) {
					timestamp -= audioDuration;
				}

				if (logging && verbose) AS3_trace("Pushing audio timestamp: %f %f %f %f audioDuration part 1 %f audioDuration part 2 %f data size %f", timestamp, millis, startRecording, audioDuration, (float)(av_get_bytes_per_sample(audio_st->codec->sample_fmt) * audio_st->codec->channels), (float)(audio_st->codec->sample_rate) * 1000, (float)sca_data_size);

				// add timestamp minus audio duration
				audioData_timeStamps_intermediate.push_back(timestamp);

				// add timestamp byte mark - to which byte does this timestamp belong?
				// important(source of bug): we want to save position in the "global" audio memory, no the intermediate one
				audioData_timeStampsBytes_intermediate.push_back(audioDataSizeTotal + audioDataSizeTotal_intermediate);

				audioDataSizeTotal_intermediate += sca_data_size;

				// TODO: the below should be reset in flush() if we ever use it in the future
				audioFramesTotal_intermediate += (float)sca_data_size / (float)(audio_st->codec->frame_size * av_get_bytes_per_sample(audio_st->codec->sample_fmt) * audio_st->codec->channels);
			}

		}
		if (logging && verbose) AS3_trace("Added audio data - size: %lu, audio data size total: %lu", sca_data_size, audioDataSizeTotal);
	}
