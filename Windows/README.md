The Windows version is recommended to be built in MSVC. There is an older branch for MingW - for that you'll need FFmpeg built in MingW(included).

For MSVC build you don't need FFmpeg as it uses MediaCodec Windows API, which provides native mp4 encoders - UNLESS you want to build FFmpeg Windows encoder, which is still contained 
within the sources and can be enabled by NOT setting USE_MEDIACODEC define when building. You'll need FFmpeg built in MSVC to build that version(also included).

The default solution config should be set to Mediacodec build and you shouldn't need any other external dependencies than what is supplied (untested). This creates a .dll file needed by the ANE.

Build the ANE using buildane_MSVC.bat in the project root folder.