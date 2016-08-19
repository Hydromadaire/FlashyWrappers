/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * iOS / OS X port of FlashyWrappers. This was planned to be refactored similar to Windows where the encoder is in separate class, but never managed to get to it - feel free!
 * Whats really missing is the ability to record audio from AIR (in general recording audio was an issue) there might be dead code here which attempts that.
 * Also, iOS ReplayKit framework support was almost ready but then because AIR SDK didn't support the latest iOS SDK it couldn't be compiled so its untested. 
 *
 */

#ifndef encoder_iOS_ANE_FWAudioMix_h
#define encoder_iOS_ANE_FWAudioMix_h

class FWAudioMix {
public:
    char* str_filename;
    double audioTimeStamp;
    double audioMixDuration;
    bool active;
    
    FWAudioMix() {
        str_filename = NULL;
        audioTimeStamp = 0;
        audioMixDuration = 0;
        active = false;
    }
    
    ~FWAudioMix() {
        if (str_filename != NULL) free(str_filename);
        str_filename = NULL;
    }
};

#endif
