/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * Experimantal: This was never used in production, but managed to get it almost working. The aim was to create ogg/ogv decoder for Flash/AIR.
 *
 */

#if defined(__MINGW32__)
#define _alloca __builtin_alloca
#endif

#include "FW_ffmpeg_decode.h"

std::stringstream *FW_ffmpeg_decode::getVideoStream() {
 	return &videoFrames;
}

std::stringstream *FW_ffmpeg_decode::getAudioStream() {
 	return &audioFrames;
}

int FW_ffmpeg_decode::getVideoFramesCount() {
	return video_frame_count;
}

int FW_ffmpeg_decode::getVideoWidth() {
	return video_width;
}

int FW_ffmpeg_decode::getVideoHeight() {
	return video_height;
}

int FW_ffmpeg_decode::getVideoFrameSize() {
	return video_width * video_height * 4;
}

#ifdef WIN32
void FW_ffmpeg_decode::rundecoderthread(void *param) {
#else
void *FW_ffmpeg_decode::rundecoderthread(void *param) {
#endif
	FW_ffmpeg_decode *fw = ((FW_ffmpeg_decode*)param);
	fw->threadDecodingRunning = true;
	int res = fw->bufferFrames();
	fw->threadDecodingRunning = false;
	// eof is set when there's no more stuff to return from decoder (no regular or flushed frames)
	if (res == -99) {
		fw->eof = true;
	}
	fw->resethThread();
	#ifdef WIN32
	_endthread();
	#endif	
}

void FW_ffmpeg_decode::resethThread() {
	hThread = NULL;
}

int FW_ffmpeg_decode::decode_packet(int *got_frame, int cached)
{

    int ret = 0;
    int decoded = pkt.size;

    if (pkt.stream_index == video_stream_idx) {
        /* decode video frame */
        ret = avcodec_decode_video2(video_dec_ctx, frame, got_frame, &pkt);
        if (ret < 0) {				
		std::string error_str = std::string (av_err2str(ret));
		throw MyException("Error decoding video frame: " + error_str);
        }

	// got frame from decoder

        if (*got_frame) {
                video_frame_count++;

		// convert to BGRA so we can display frame into BitmapData

		uint8_t* const srcPtrs[3] = {frame->data[0], frame->data[1], frame->data[2]};
		int srcStrides[3] = {frame->linesize[0], frame->linesize[1], frame->linesize[2]};

		uint8_t* const dstPtrs[1] = {frameBGRA->data[0]};
		int dstStrides[1] = {frameBGRA->linesize[0]};

          	sws_scale(sws_ctx, srcPtrs, srcStrides, 0, video_height, dstPtrs, dstStrides);

		// add to buffer for now
		videoFrames.write((char*)frameBGRA->data[0], frameBGRA->linesize[0] * video_height);
        }
    } else if (pkt.stream_index == audio_stream_idx) {
        /* decode audio frame */
        ret = avcodec_decode_audio4(audio_dec_ctx, frame, got_frame, &pkt);
        if (ret < 0) {
		std::string error_str = std::string (av_err2str(ret));
		throw MyException("Error decoding audio frame: " + error_str);
        }
        /* Some audio decoders decode only part of the packet, and have to be
         * called again with the remainder of the packet data.
         * Sample: fate-suite/lossless-audio/luckynight-partial.shn
         * Also, some decoders might over-read the packet. */
        decoded = FFMIN(ret, pkt.size);

        if (*got_frame) {
            size_t unpadded_linesize = frame->nb_samples * av_get_bytes_per_sample((AVSampleFormat)frame->format);
/*            printf("audio_frame%s n:%d nb_samples:%d pts:%s\n",
                   cached ? "(cached)" : "",
                   audio_frame_count++, frame->nb_samples,
                   av_ts2timestr(frame->pts, &audio_dec_ctx->time_base));*/
		audio_frame_count++;

//		fprintf(log, "\nBytes per sample:%d", av_get_bytes_per_sample((AVSampleFormat)frame->format));

            /* Write the raw audio data samples of the first plane. This works
             * fine for packed formats (e.g. AV_SAMPLE_FMT_S16). However,
             * most audio decoders output planar audio, which uses a separate
             * plane of audio samples for each channel (e.g. AV_SAMPLE_FMT_S16P).
             * In other words, this code will write only the first audio channel
             * in these cases.
             * You should use libswresample or libavfilter to convert the frame
             * to packed data. */

            /* add audio frame to buffer */
	    audioFrames.write((char*)frame->extended_data[0], unpadded_linesize);

//            fwrite(frame->extended_data[0], 1, unpadded_linesize, log);
        }
	return decoded;
    }

    return ret;
}

int FW_ffmpeg_decode::open_codec_context(int *stream_idx,
                              AVFormatContext *fmt_ctx, enum AVMediaType type)
{
    int ret;
    AVStream *st;
    AVCodecContext *dec_ctx = NULL;
    AVCodec *dec = NULL;

    ret = av_find_best_stream(fmt_ctx, type, -1, -1, NULL, 0);
    if (ret < 0) {
	fprintf(stderr, "Could not find %s stream in input video", av_get_media_type_string(type));            
        return ret;
    } else {
        *stream_idx = ret;
        st = fmt_ctx->streams[*stream_idx];

        /* find decoder for the stream */
        dec_ctx = st->codec;
        dec = avcodec_find_decoder(dec_ctx->codec_id);
        if (!dec) {
            throw MyException("Failed to find " + std::string(av_get_media_type_string(type)) + " codec");            
        }

        if ((ret = avcodec_open2(dec_ctx, dec, NULL)) < 0) {
            throw MyException("Failed to open " + std::string(av_get_media_type_string(type)) + " codec");            
        }
    }
    return 0;
}

int FW_ffmpeg_decode::get_format_from_sample_fmt(const char **fmt,
                                      enum AVSampleFormat sample_fmt)
{
    int i;
    struct sample_fmt_entry {
        enum AVSampleFormat sample_fmt; const char *fmt_be, *fmt_le;
    } sample_fmt_entries[] = {
        { AV_SAMPLE_FMT_U8,  "u8",    "u8"    },
        { AV_SAMPLE_FMT_S16, "s16be", "s16le" },
        { AV_SAMPLE_FMT_S32, "s32be", "s32le" },
        { AV_SAMPLE_FMT_FLT, "f32be", "f32le" },
        { AV_SAMPLE_FMT_DBL, "f64be", "f64le" },
    };
    *fmt = NULL;

    for (i = 0; i < FF_ARRAY_ELEMS(sample_fmt_entries); i++) {
        struct sample_fmt_entry *entry = &sample_fmt_entries[i];
        fprintf(log, "Comparsion: %d vs %d", sample_fmt, entry->sample_fmt);
        if (sample_fmt == entry->sample_fmt) {
            *fmt = AV_NE(entry->fmt_be, entry->fmt_le);
            return 0;
        }
    }

    throw new MyException(
            "sample format " + std::string(av_get_sample_fmt_name(sample_fmt)) + " is not supported as output format");
    return -1;                 
}

void FW_ffmpeg_decode::reset() {
	flush = false;
	hThread = NULL;	
	fmt_ctx = NULL;
	video_dec_ctx = NULL;
        audio_dec_ctx = NULL;
	video_stream = NULL;
	audio_stream = NULL;
	fmt_ctx = NULL;
	video_dec_ctx = NULL;
        audio_dec_ctx = NULL;
	video_stream = NULL;
	audio_stream = NULL;
	eof = false;
	video_width = 0;
	video_height = 0;
	audio_dst_data = NULL;
	audio_dst_linesize = 0;
	audio_dst_bufsize = 0;
	video_stream_idx = -1;
	audio_stream_idx = -1;
	frame = NULL;
	frameBGRA = NULL;
	video_frame_count = 0;
	audio_frame_count = 0;
	sws_ctx = NULL;
	threadDecodingRunning = false;

}

int FW_ffmpeg_decode::init (char *videoBuffer, size_t videoBufferLength)
{
	int ret = 0;

	return_buffer.write((const char*)videoBuffer, videoBufferLength);
	return_buffer.seekp(0, std::ios_base::beg);        

	/* Write your buffer to disk. */
//	log = fopen("c:\\Users\\Pavel\\Documents\\audio","wb");

//	FILE *pFile = fopen("c:\\Users\\Pavel\\Documents\\video_hovno.ogv","wb");
	log = fopen("c:\\Users\\Pavel\\Documents\\video_log.txt","wa");

/*        if (pFile!=NULL)
	{
		fwrite(videoBuffer, videoBufferLength, 1, pFile);
		fclose(pFile);
	} else {
		fprintf(stderr, "Error");
	}*/

	reset();

	// internal buffer for ffmpeg's IO 
	buffer = (unsigned char*)av_malloc(8192);

	pb = avio_alloc_context(buffer, 8192, 0, &return_buffer, readFunction, 0, seekFunction); 
	pb->seekable = 0;

	/* register all formats and codecs */
	av_register_all();
	
	/* allocate our own format context, set pb to custom pb with custom IO functions to make ffmpeg read from memory buffer */
	fmt_ctx = avformat_alloc_context();
	fmt_ctx->pb = pb;

	// Determining the input format:
	// Now we set the ProbeData-structure for av_probe_input_format:

/*	AVProbeData probeData;
	probeData.buf = (unsigned char*)videoBuffer;
	probeData.buf_size = videoBufferLength;
	probeData.filename = "";
	fmt_ctx->iformat = av_probe_input_format(&probeData, 1);
	fmt_ctx->flags = AVFMT_FLAG_CUSTOM_IO;*/

	/* open input file */
	int ret2 = 0;
	if ((ret2 = avformat_open_input(&fmt_ctx, "video.ogv", NULL, NULL)) < 0) {
		std::string error_str = std::string (av_err2str(ret2));
		throw MyException("Could not open source video: " + error_str);
	}

	/* retrieve stream information */
	if ((ret2 = avformat_find_stream_info(fmt_ctx, NULL)) < 0) {
		std::string error_str = std::string (av_err2str(ret2));
		throw MyException("Could not get stream info: " + error_str);
	}

	frame = avcodec_alloc_frame();
	frameBGRA = avcodec_alloc_frame();

	if (!frame) {
		finish();
		throw MyException("Could not allocate frame");
		ret = AVERROR(ENOMEM);
	}

	if (!frameBGRA) {
		finish();
		throw MyException("Could not allocate BGRA frame");
		ret = AVERROR(ENOMEM);
	}

	if (open_codec_context(&video_stream_idx, fmt_ctx, AVMEDIA_TYPE_VIDEO) >= 0) {
		video_stream = fmt_ctx->streams[video_stream_idx];
		video_dec_ctx = video_stream->codec;

		video_width = video_dec_ctx->width;
		video_height = video_dec_ctx->height;	

		// create conversion context to BGRA if the frames are not in it
		if (video_dec_ctx->pix_fmt != AV_PIX_FMT_BGRA) {
			if (!sws_ctx) {
				sws_ctx = sws_getContext(video_dec_ctx->width, video_dec_ctx->height, video_dec_ctx->pix_fmt,
							 video_dec_ctx->width, video_dec_ctx->height, AV_PIX_FMT_BGRA,
											SWS_BILINEAR, NULL, NULL, NULL);
				if (!sws_ctx) {
					finish();
					throw MyException("Could not initialize the conversion context");                                                                                                      
				}
			}
		}	

		// alocate src and dst picture for something->BGRA conversion
	        ret = avpicture_alloc(&src_picture, video_dec_ctx->pix_fmt, video_dec_ctx->width, video_dec_ctx->height);
	        if (ret < 0) {
			finish();
			std::string error_str = std::string (av_err2str(ret));
			throw MyException("Could not allocate the src picture for conversion: " + error_str);
	        }

	        ret = avpicture_alloc(&dst_picture, AV_PIX_FMT_BGRA, video_dec_ctx->width, video_dec_ctx->height);
	        if (ret < 0) {
			finish();
			std::string error_str = std::string (av_err2str(ret));
			throw MyException("Could not allocate the dst picture for conversion: " + error_str);
	        }

		*((AVPicture *)frameBGRA) = dst_picture;
	
	        if (ret < 0) {
		    finish();
		    throw MyException("Could not allocate raw video buffer");
	        }
	}

	// try to open audio stream
	if (open_codec_context(&audio_stream_idx, fmt_ctx, AVMEDIA_TYPE_AUDIO) >= 0) {
	        audio_stream = fmt_ctx->streams[audio_stream_idx];
	        audio_dec_ctx = audio_stream->codec;

/*		int nb_planes;	
		audio_stream = fmt_ctx->streams[audio_stream_idx];
		audio_dec_ctx = audio_stream->codec;
		nb_planes = av_sample_fmt_is_planar(audio_dec_ctx->sample_fmt) ? audio_dec_ctx->channels : 1;
		uint8_t *alloc = (uint8_t*)(av_mallocz(sizeof(uint8_t *) * nb_planes));
		audio_dst_data = &alloc;
		if (!audio_dst_data) {
			finish();
			throw MyException("Could not allocate raw audio buffer");			
		}         */
	}

	/* dump input information to stderr */
	av_dump_format(fmt_ctx, 0, src_filename, 0);

	if (!audio_stream && !video_stream) {	
		finish();
		throw MyException("Could not find audio and video stream in the input, aborting!");		
	}

	/* initialize packet, set data to NULL, let the demuxer fill it */

	av_init_packet(&pkt);
	pkt.data = NULL;
	pkt.size = 0;

	if (video_stream)
	        fprintf(stderr, "Demuxing video from buffer");
	if (audio_stream) {
	        fprintf(stderr, "Demuxing audio from buffer");
	        enum AVSampleFormat sfmt = audio_dec_ctx->sample_fmt;
	        int n_channels = audio_dec_ctx->channels;
	        const char *fmt;	
		fprintf(log, "(3) Sample format is:%s", av_get_sample_fmt_name(sfmt));
	        if (av_sample_fmt_is_planar(sfmt)) {
	            const char *packed = av_get_sample_fmt_name(sfmt);
	            fprintf(log, "Warning: the sample format the decoder produced is planar "
	                   "(%s). This example will output the first channel only.\n",
	                   packed ? packed : "?");
	            sfmt = av_get_packed_sample_fmt(sfmt);
	            n_channels = 1;
	        }

	        if ((ret = get_format_from_sample_fmt(&fmt, sfmt)) < 0) {
			finish();
			throw MyException("Unknown sample format");					
		}	           
		fprintf(log, "get_sample_format_from_sample_fmt: %d", ret);
	}
	
	return ret < 0;
}

// just temp, try to decode one frame
int FW_ffmpeg_decode::doFrame() {

    int got_frame;	
    int res;

    /* read frames from the buffer */
    if ((res = av_read_frame(fmt_ctx, &pkt)) >= 0) {
        AVPacket orig_pkt = pkt;
        int ret_decode = decode_packet(&got_frame, 0);
	if (ret_decode < 0) return ret_decode;
	pkt.data += ret_decode;
	pkt.size -= ret_decode;
	av_free_packet(&orig_pkt);
	if (got_frame) {
		return 2;
	} 
	return ret_decode;
    } 
    return res;

    /* flush cached frames */
/*    pkt.data = NULL;
    pkt.size = 0;
    do {
        decode_packet(&got_frame, 1);
    } while (got_frame);*/
}

// run buffer frames on bg thread
void FW_ffmpeg_decode::runBufferFrames(int count) {
	decodeBufferCount = count;
	if (hThread == NULL) {
#ifdef WIN32
		hThread = (HANDLE)_beginthread(FW_ffmpeg_decode::rundecoderthread, 0, (void*)this);
#else
		int val = pthread_create(&hThread, NULL, FW_ffmpeg_decode::rundecoderthread, (void*)this);
		if (val != 0) {
			throw MyException("Couldn't create decoder thread");
		}
#endif
	}
}

// try to buffer & decode X number of frames on background thread
int FW_ffmpeg_decode::bufferFrames() {

	int got_frame;	
	int res;
	bool eof = false;
	int video_frame_countTarget = video_frame_count + decodeBufferCount;

	// buffer specified number of frames until we can't read any further  
	if (!flush) {
	    while (video_frame_count < video_frame_countTarget && !eof) {
	    	if ((res = av_read_frame(fmt_ctx, &pkt)) >= 0)  {
		        AVPacket orig_pkt = pkt;
		        int ret_decode = decode_packet(&got_frame, 0);
			if (ret_decode < 0) return ret_decode;
			pkt.data += ret_decode;
			pkt.size -= ret_decode;
			av_free_packet(&orig_pkt);
		} else {
			// TODO: this can also mean error!
			eof = true;
			flush = true;
		}
	    }
	    // return only if we're not about to flush
	    if (!flush) return res;
	} 

	// if we're about to flush then do it but only while the buffer count allows it(otherwise do it in the next cycle)
	if (flush) {
	    /* flush cached frames */
	    pkt.data = NULL;
	    pkt.size = 0;
	    do {
	        decode_packet(&got_frame, 1);
	    } while (video_frame_count < video_frame_countTarget && got_frame);
	    if (got_frame) {
		    // still has frames to flush
		    return -98;
	    } else {
	  	    // all frames flushed
		    return -99;
	    }
	}
}

// get the video buffer(we need to fill this before decoding)
std::stringstream *FW_ffmpeg_decode::getVideoBuffer() {
	return &return_buffer;
}

// clear all allocated stuff
void FW_ffmpeg_decode::finish() {
	if (video_dec_ctx) {
		avcodec_close(video_dec_ctx);
		av_free(dst_picture.data[0]);
		av_free(src_picture.data[0]);
	}
	if (audio_dec_ctx) avcodec_close(audio_dec_ctx);
	if (fmt_ctx != NULL) avformat_close_input(&fmt_ctx);
	if (frame != NULL) av_free(frame);
	if (frameBGRA != NULL) av_free(frameBGRA);
	if (audio_dst_data != NULL) av_free(audio_dst_data);
	fclose(log);
}

// destructor
FW_ffmpeg_decode::~FW_ffmpeg_decode() {
	fprintf(stderr, "Destroying decoder");
	finish();	
}