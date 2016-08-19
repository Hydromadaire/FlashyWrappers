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
#ifndef AUDIOSAMPLE_H
#define AUDIOSAMPLE_H

#include "FlashRuntimeExtensions.h"
#include "stdafx.h"
#include "stdlib.h"
#include <VersionHelpers.h>

class WindowsEncoder;

// the audio sample class
class FWAudioSample {

	private:

	WindowsEncoder *encoder;

	public:

	short *data;
	LONGLONG duration;
	uint32_t length;
	// how far are we with sending this packet to the muxer
	uint32_t dataPointer;
	double pts;

	FWAudioSample(WindowsEncoder *_encoder, float *_data, uint32_t _length, double _pts);
	~FWAudioSample();
	LONGLONG getPartLength(uint32_t length);
};

#endif