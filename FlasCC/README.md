The Flash version is very specific: 

1) First, you need to build the C++ files into SWC by now abandoned Crossbridge(former FlasCC) crosscompiler. For example, build openh264 release swc, in cygwin console of Crossbridge type:

make openh264_release FLASCC=/c/path/to/Crossbridge/sdk/

2) This should produce fw_ffmpeg_encode_mp4_release.swc

3) Then, you need to wrap the SWC into SWF and build a "SWF Bridge" with its own SWC, which acts as interface into the wrapped SWF encoder. The reason for the SWF file historically was multithreading - when workers are spawned in separete
SWF, then only that separate SWF is cloned, not your whole SWF. Another reason for SWFBridge was problems with building apps when including FlashyWrappers SWC directly - it was very slow and sometimes created weird build errors.

To create a wrapper and SWF bridge for the example above, launch "buildflascc_wrapper_mp4.bat" in the projects root folder.

NOTE: The other most used build configuration will be make ogg_release, and then using buildflascc_wrapper.bat, which will build the corresponding SWF bridge. In theory you can create encoders for any other formats that FFmpeg supports(webm was created in the past, there are still remains of its config in the makefile).
NOTE2: I've included precompiled FFmpeg libs which were used to build FlashyWrappers for Flash. Also I've included configuration files for both so you can pretty easily try to rebuild FlashyWrappers with the latest FFmpeg, by using those config files again. The latest FFmpeg will demand changing FW's sourcecode though.