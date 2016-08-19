These files were used for all platforms but ultimately were only used for FlasCC, for production. This is FFmpeg based encoder, and because FFmpeg is crossplatform you can still use this
to build encoders for various platforms and create ANE's of it (or possibly other plugins such as for Unity). 

FlashyWrappers was in time rearchitected to use native encoders(for example MediaCodec for Windows or AVFoundation for OS X), because of the mp4 licensing which is "free" when using integrated OS encoders.