/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * This used to be the FFmpeg common code for all platforms, but in time only remained used by Flash (FlasCC / Crossbridge). You can still use this 
 * if you'd like to build FFmpeg based Windows / OS X or whatever platform encoder.
 *
 */

#include <sstream>
#include <vector>

#ifndef FW_FFMPEG_IO_H
#define FW_FFMPEG_IO_H

#if defined(WIN32) || defined(ANDROID)
#ifndef INT64_C
  #define INT64_C(c) (c ## LL)
   #define UINT64_C(c) (c ## ULL)
#endif
#endif

#ifdef __cplusplus

extern "C" {

#endif 

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <iostream>
#include <math.h>
#include <vector>

#include <libavutil/opt.h>
#include <libavutil/mathematics.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>

#ifdef __cplusplus

}

#endif

/* C functions for ffmpeg defining write, read and seek */

// for avioContext
extern "C" {
int writeFunction(void* opaque, uint8_t* buf, int buf_size);

int readFunction(void* opaque, uint8_t* buf, int buf_size);

int64_t seekFunction(void *opaque,int64_t offset, int whence);
}
#endif
