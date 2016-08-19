# FlashyWrappers SDK

Building
--------

*Windows / Android / Flash*
The .bat files are used to build FW on Windows.

*Windows*

Windows came in 2 flavors. Like for all encoders, FFmpeg based encoder came first. This is the only platform where I still kept it. The reason is, when debugging FFmpeg based encoder for Flash, 
it was often useful to make a build for Windows with identical code - debugging FlasCC / Crossbridge can be pretty horrible. The FFmpeg flavor of FlashyWrappers Windows was built with either MingW
 or MSVC(lately only MSVC) - the define USE_MEDIACODEC determines if the encoder is FFmpeg or MediaCodec based.

*OS X / iOS*

Those platforms use identical source code file, luckily AVFoundation is almost identical on OS X and iOS. These are currently not ready for release yet but they are included in case you can't wait.