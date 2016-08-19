Warning: You will need to have AIR SDK and Flex SDK installed

To relink FlashyWrappers:

1) Download FFmpeg sources from http://www.rainbowcreatures.com/ffmpeg-flashywrappers.zip
2) Download vorbis, theora and ogg libraries from http://www.xiph.org/downloads/
3) Configure, make, make install vorbis, theora, ogg in FlasCC 1.0 cygwin
4) Configure, make, make install FFmpeg (you can check the configure commands used to link FlashyWrappers in the documentation pdf) in FlasCC 1.0 cygwin
5) Run make in FlasCC folder using FlasCC 1.0 cygwin - if you installed all libraries it should relink FlashyWrappers
6) Run the .bat file to wrap the SWC into the AS3 class