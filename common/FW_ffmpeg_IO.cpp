/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * Custom FFmpeg IO functions. This was originally needed to work with videos in memory instead on harddrive(hardly possible in Flash security sandbox).
 * In time, I realized that we can just use VFS of Flash to simulate harddrive which eliminates the need to use the custom IO, but still keeping it here for reference.
 * This used to be the FFmpeg common code for all platforms, but in time only remained used by Flash (FlasCC / Crossbridge). You can still use this 
 * if you'd like to build FFmpeg based Windows / OS X or whatever platform encoder.
 *
 */

#include "FW_ffmpeg_IO.h"
#include <iostream>
#include <fstream>
using namespace std;

#if !defined(PRId64 )
#define PRId64 "I64d"
#endif

	// for avioContext
	int writeFunction(void* opaque, uint8_t* buf, int buf_size) {
	   	fprintf(stderr, "write");
		((std::stringstream*)opaque)->write(reinterpret_cast<char*>(buf), buf_size);
		return buf_size;
	}

	int readFunction(void* opaque, uint8_t* buf, int buf_size) {
	   	fprintf(stderr, "read");
		std::stringstream *retbuf = ((std::stringstream*)opaque);
		retbuf->read(reinterpret_cast<char*>(buf), buf_size);
		return retbuf->gcount();
	}

	int64_t seekFunction(void *opaque,int64_t offset, int whence)
	{
	   	fprintf(stderr, "seek to %d", offset);
		std::stringstream *retbuf = ((std::stringstream*)opaque);
		if (whence == SEEK_SET) {
			retbuf->seekp(offset, std::ios_base::beg);
		}
		if (whence == SEEK_CUR) {
			retbuf->seekp(offset, std::ios_base::cur);
		}
		if (whence == SEEK_END) {
			retbuf->seekp(offset, std::ios_base::end);
		}
		if (whence == AVSEEK_SIZE) {
			int64_t old = retbuf->tellp();
			retbuf->seekp(0, std::ios_base::end);
			int size = retbuf->tellp();
			retbuf->seekp(old, std::ios_base::beg);        
			return size;
		}
		return retbuf->tellp();
	}
