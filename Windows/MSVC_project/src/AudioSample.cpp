/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * Windows FFmpeg / MediaCodec encoder
 *
 */

#include "AudioSample.h"
#include "WindowsEncoder.h"
FWAudioSample::FWAudioSample(WindowsEncoder *_encoder, float *_data, uint32_t _length, double _pts) {
	 dataPointer = 0;
	 pts = _pts;
	 encoder = _encoder;
	 length = _length;
	 // first convert the floating point audio data to shorts
	 short *PCMaudio = encoder->floatToPCM((float*)_data, length);
	 // because of the conversion the length is half of the original
	 length = length / 2;
	 uint32_t nAudioSamplesPerChannel = ((length / encoder->foundation_audio_channels) * 8) / 16;
	 duration = (nAudioSamplesPerChannel * (LONGLONG)10000000) / encoder->foundation_audio_sample_rate;
	 data = PCMaudio;
}

LONGLONG FWAudioSample::getPartLength(uint32_t length) {
	uint32_t nAudioSamplesPerChannel = ((length / encoder->foundation_audio_channels) * 8) / 16;
	return (nAudioSamplesPerChannel * (LONGLONG)10000000) / encoder->foundation_audio_sample_rate;
}

FWAudioSample::~FWAudioSample() {
	if (data != NULL) delete(data);
	data = NULL;
}