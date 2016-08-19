# This is an old branch which was used to build Windows using MinGW. I've included also the FFmpeg libs used in this. If someone is interested in making this alive again you can.

FLEX:=/c/Pavel/Projekty_libs/Flex_SDK
FFMPEG_INCLUDES_OGG:=/c/MinGW/ffmpeg_ogg_theora_vorbis/include/
FFMPEG_LIBS_OGG:=/c/MinGW/ffmpeg_ogg_theora_vorbis/lib/
FFMPEG_INCLUDES_OGG_DEBUG:=/c/MinGW/ffmpeg_ogg_theora_vorbis_dll_debug/include/
FFMPEG_LIBS_OGG_DEBUG:=/c/MinGW/ffmpeg_ogg_theora_vorbis_dll_debug/lib/
FFMPEG_INCLUDES_MP4:=/c/MinGW/ffmpeg_mp4_openh264_aac/include/
FFMPEG_LIBS_MP4:=/c/MinGW/ffmpeg_mp4_openh264_aac/lib/
FFMPEG_INCLUDES_MP4_DLL:=/c/MinGW/ffmpeg_mp4_openh264_aac_dll/include/
FFMPEG_LIBS_MP4_DLL:=/c/MinGW/ffmpeg_mp4_openh264_aac_dll/lib/

all:
	@echo "";
	@echo "FlashyWrappers";
	@echo "---------------------------------------------------------------";
	@echo "FFmpeg wrapper";
	@echo "Usage: make ogg_release FLEX=/c/PATH_TO_FLEX/";
	@echo "---------------------------------------------------------------";
	@if [ -d $(FLEX)/include ] ; then true ; \
	else echo "Couldn't locate FLEX sdk directory, please invoke make with \"make FLEX=/path/to/FLEX/ ...\"" ; exit 1 ; \
	fi

ogg_release:
	@echo "-> Compile the C into abc - RELEASE"
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 -Werror ../boost_atomic/libs/atomic/src/lockpool.cpp -o MingW/ogg/lockpool.o -I../boost_atomic/
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 -Werror ../FW_exception.cpp -o MingW/ogg/fw_exception.o
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 -Werror ../FW_ffmpeg_IO.cpp -o MingW/ogg/fw_ffmpeg_IO.o -I$(FFMPEG_INCLUDES_OGG)
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 -Werror ../FW_ffmpeg_encode.cpp -o MingW/ogg/fw_ffmpeg_encode_release.o -I../boost_atomic/ -I$(FFMPEG_INCLUDES_OGG)
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 -Werror ../FW_ffmpeg_decode.cpp -o MingW/ogg/fw_ffmpeg_decode_release.o -I../boost_atomic/ -I$(FFMPEG_INCLUDES_OGG)
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 -Werror -I$(FLEX)/include/ -I../boost_atomic/ -I/MSVC/fw_ffmpeg_encoder_WinANE/ -I../ MSVC/fw_ffmpeg_encoder_WinANE/fw_ffmpeg_encoder_WinANE.cpp -o MingW/ogg/fw_ffmpeg_encode_wrapper_release.o -I$(FFMPEG_INCLUDES_OGG)
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 -Werror MSVC/fw_ffmpeg_encoder_WinANE/dllmain.cpp -o MingW/ogg/dllmain.o                                                                                                           
	@echo "-> Build DLL"                                                                                                                                                                  
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -O2 -static-libgcc -static-libstdc++ -Wall MingW/ogg/lockpool.o MingW/ogg/dllmain.o MingW/ogg/fw_exception.o MingW/ogg/fw_ffmpeg_encode_wrapper_release.o MingW/ogg/fw_ffmpeg_decode_release.o MingW/ogg/fw_ffmpeg_encode_release.o MingW/ogg/fw_ffmpeg_IO.o --shared -o MingW/ogg/nativeExtension.dll -fpermissive -L$(FFMPEG_LIBS_OGG) -lswscale -lavformat -lavcodec -lavutil -lvorbis -lvorbisenc -ltheora -logg -liconv -L$(FLEX)/lib/win/ -lFlashRuntimeExtensions

ogg_demo:
	@echo "-> Compile the C into abc - DEMO"
	"g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 -Werror ../boost_atomic/libs/atomic/src/lockpool.cpp -o MingW/ogg/lockpool.o -I../boost_atomic/
	"g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 -Werror ../FW_exception.cpp -o MingW/ogg/fw_exception.o
	"g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 -Werror ../FW_ffmpeg_IO.cpp -o MingW/ogg/fw_ffmpeg_IO.o -I$(FFMPEG_INCLUDES_OGG)
	"g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 -Werror ../FW_ffmpeg_encode.cpp -o MingW/ogg/fw_ffmpeg_encode_release.o -I../boost_atomic/ -I$(FFMPEG_INCLUDES_OGG)
	"g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 -Werror ../FW_ffmpeg_decode.cpp -o MingW/ogg/fw_ffmpeg_decode_release.o -I../boost_atomic/ -I$(FFMPEG_INCLUDES_OGG)
	"g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 -Werror -I$(FLEX)/include/ -I../boost_atomic/ -I/MSVC/fw_ffmpeg_encoder_WinANE/ -I../ MSVC/fw_ffmpeg_encoder_WinANE/fw_ffmpeg_encoder_WinANE.cpp -o MingW/ogg/fw_ffmpeg_encode_wrapper_release.o  -I$(FFMPEG_INCLUDES_OGG)
	"g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 -Werror MSVC/fw_ffmpeg_encoder_WinANE/dllmain.cpp -o MingW/ogg/dllmain.o                                                                                                           
	@echo "-> Build DLL"                                                                                                                                                                  
	"g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -O2 -static-libgcc -static-libstdc++ -Wall MingW/ogg/lockpool.o MingW/ogg/dllmain.o MingW/ogg/fw_exception.o MingW/ogg/fw_ffmpeg_encode_wrapper_release.o MingW/ogg/fw_ffmpeg_decode_release.o MingW/ogg/fw_ffmpeg_encode_release.o MingW/ogg/fw_ffmpeg_IO.o --shared -o MingW/ogg/nativeExtension.dll -fpermissive -L$(FFMPEG_LIBS_OGG) -lswscale -lavformat -lavcodec -lavutil -lvorbis -lvorbisenc -ltheora -logg -liconv -L$(FLEX)/lib/win/ -lFlashRuntimeExtensions

ogg_debug:
	@echo "-> Compile the C into abc - DEBUG"
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O0 -g ../boost_atomic/libs/atomic/src/lockpool.cpp -o MingW/ogg/lockpool.o -I../boost_atomic/
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O0 -g ../FW_exception.cpp -o MingW/ogg/fw_exception.o
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O0 -g ../FW_ffmpeg_IO.cpp -o MingW/ogg/fw_ffmpeg_IO.o -I$(FFMPEG_INCLUDES_OGG_DEBUG)
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O0 -g ../FW_ffmpeg_encode.cpp -o MingW/ogg/fw_ffmpeg_encode_release.o -I../boost_atomic/ -I$(FFMPEG_INCLUDES_OGG_DEBUG)
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O0 -g ../FW_ffmpeg_decode.cpp -o MingW/ogg/fw_ffmpeg_decode_release.o -I../boost_atomic/ -I$(FFMPEG_INCLUDES_OGG_DEBUG)
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O0 -g -I$(FLEX)/include/ -I../boost_atomic/ -I/MSVC/fw_ffmpeg_encoder_WinANE/ -I../ MSVC/fw_ffmpeg_encoder_WinANE/fw_ffmpeg_encoder_WinANE.cpp -o MingW/ogg/fw_ffmpeg_encode_wrapper_release.o -I$(FFMPEG_INCLUDES_OGG_DEBUG)
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O0 -g MSVC/fw_ffmpeg_encoder_WinANE/dllmain.cpp -o MingW/ogg/dllmain.o                                                                                                           
	@echo "-> Build DLL"                                                                                                                                                                  
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -O0 -g -static-libgcc -static-libstdc++ -Wall MingW/ogg/lockpool.o MingW/ogg/dllmain.o MingW/ogg/fw_exception.o MingW/ogg/fw_ffmpeg_encode_wrapper_release.o MingW/ogg/fw_ffmpeg_encode_release.o MingW/ogg/fw_ffmpeg_decode_release.o MingW/ogg/fw_ffmpeg_IO.o --shared -o MingW/ogg/nativeExtension.dll -fpermissive -L$(FFMPEG_LIBS_OGG_DEBUG) -lswscale -lavformat -lavcodec -lavutil -lvorbis -lvorbisenc -ltheora -logg -liconv -L$(FLEX)/lib/win/ -lFlashRuntimeExtensions

mp4_demo:
	@echo "-> Compile the C into abc - DEMO"
	"g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 ../boost_atomic/libs/atomic/src/lockpool.cpp -o MingW/mp4/lockpool.o -I../boost_atomic/
	"g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 ../FW_exception.cpp -o MingW/mp4/fw_exception.o
	"g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 ../FW_ffmpeg_IO.cpp -o MingW/mp4/fw_ffmpeg_IO.o -I$(FFMPEG_INCLUDES_MP4)
	"g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 ../FW_ffmpeg_encode.cpp -o MingW/mp4/fw_ffmpeg_encode_release.o -I../boost_atomic/ -I$(FFMPEG_INCLUDES_MP4)
	"g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 ../FW_ffmpeg_decode.cpp -o MingW/mp4/fw_ffmpeg_decode_release.o -I../boost_atomic/ -I$(FFMPEG_INCLUDES_MP4)
	"g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 -I$(FLEX)/include/ -I../boost_atomic/ -I/MSVC/fw_ffmpeg_encoder_WinANE/ -I../ MSVC/fw_ffmpeg_encoder_WinANE/fw_ffmpeg_encoder_WinANE.cpp -o MingW/mp4/fw_ffmpeg_encode_wrapper_release.o -I$(FFMPEG_INCLUDES_MP4)
	"g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 MSVC/fw_ffmpeg_encoder_WinANE/dllmain.cpp -o MingW/mp4/dllmain.o                                                                                                           
	@echo "-> Build DLL"                                                                                                                                                                  
	"g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -O2 -static-libgcc -static-libstdc++ -Wall MingW/mp4/lockpool.o MingW/mp4/dllmain.o MingW/mp4/fw_exception.o MingW/mp4/fw_ffmpeg_encode_wrapper_release.o MingW/mp4/fw_ffmpeg_decode_release.o MingW/mp4/fw_ffmpeg_encode_release.o MingW/mp4/fw_ffmpeg_IO.o --shared -o MingW/mp4/nativeExtension.dll -fpermissive -L$(FFMPEG_LIBS_MP4) -lswscale -lavformat -lavcodec -lavutil -lvorbis -lvorbisenc -lopenh264 -liconv -L$(FLEX)/lib/win/ -lFlashRuntimeExtensions

mp4_release:
	@echo "-> Compile the C into abc - RELEASE"
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 ../boost_atomic/libs/atomic/src/lockpool.cpp -o MingW/mp4/lockpool.o -I../boost_atomic/
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 ../FW_exception.cpp -o MingW/mp4/fw_exception.o
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 ../FW_ffmpeg_IO.cpp -o MingW/mp4/fw_ffmpeg_IO.o -I$(FFMPEG_INCLUDES_MP4)
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 ../FW_ffmpeg_encode.cpp -o MingW/mp4/fw_ffmpeg_encode_release.o -I../boost_atomic/ -I$(FFMPEG_INCLUDES_MP4)
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 ../FW_ffmpeg_decode.cpp -o MingW/mp4/fw_ffmpeg_decode_release.o -I../boost_atomic/ -I$(FFMPEG_INCLUDES_MP4)
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 -I$(FLEX)/include/ -I../boost_atomic/ -I/MSVC/fw_ffmpeg_encoder_WinANE/ -I../ MSVC/fw_ffmpeg_encoder_WinANE/fw_ffmpeg_encoder_WinANE.cpp -o MingW/mp4/fw_ffmpeg_encode_wrapper_release.o -I$(FFMPEG_INCLUDES_MP4)
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O2 MSVC/fw_ffmpeg_encoder_WinANE/dllmain.cpp -o MingW/mp4/dllmain.o                                                                                                           
	@echo "-> Build DLL"                                                                                                                                                                  
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -O2 -static-libgcc -static-libstdc++ -Wall MingW/mp4/lockpool.o MingW/mp4/dllmain.o MingW/mp4/fw_exception.o MingW/mp4/fw_ffmpeg_encode_wrapper_release.o MingW/mp4/fw_ffmpeg_decode_release.o MingW/mp4/fw_ffmpeg_encode_release.o MingW/mp4/fw_ffmpeg_IO.o --shared -o MingW/mp4/nativeExtension.dll -fpermissive -L$(FFMPEG_LIBS_MP4) -lswscale -lavformat -lavcodec -lavutil -lvorbis -lvorbisenc -lopenh264 -liconv -L$(FLEX)/lib/win/ -lFlashRuntimeExtensions

mp4_debug:
	@echo "-> Compile the C into abc - DEBUG"
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O0 -g ../boost_atomic/libs/atomic/src/lockpool.cpp -o MingW/mp4/lockpool.o -I../boost_atomic/
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O0 -g ../FW_exception.cpp -o MingW/mp4/fw_exception.o
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O0 -g ../FW_ffmpeg_IO.cpp -o MingW/mp4/fw_ffmpeg_IO.o -I$(FFMPEG_INCLUDES_MP4)
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O0 -g ../FW_ffmpeg_encode.cpp -o MingW/mp4/fw_ffmpeg_encode_release.o -I../boost_atomic/ -I$(FFMPEG_INCLUDES_MP4)
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O0 -g ../FW_ffmpeg_decode.cpp -o MingW/mp4/fw_ffmpeg_decode_release.o -I../boost_atomic/ -I$(FFMPEG_INCLUDES_MP4)
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O0 -g -I$(FLEX)/include/ -I../boost_atomic/ -I/MSVC/fw_ffmpeg_encoder_WinANE/ -I../ MSVC/fw_ffmpeg_encoder_WinANE/fw_ffmpeg_encoder_WinANE.cpp -o MingW/mp4/fw_ffmpeg_encode_wrapper_release.o -I$(FFMPEG_INCLUDES_MP4)
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -c -O0 -g MSVC/fw_ffmpeg_encoder_WinANE/dllmain.cpp -o MingW/mp4/dllmain.o                                                                                                           
	@echo "-> Build DLL"                                                                                                                                                                  
	"g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -O0 -g -static-libgcc -static-libstdc++ -Wall MingW/mp4/lockpool.o MingW/mp4/dllmain.o MingW/mp4/fw_exception.o MingW/mp4/fw_ffmpeg_encode_wrapper_release.o MingW/mp4/fw_ffmpeg_decode_release.o MingW/mp4/fw_ffmpeg_encode_release.o MingW/mp4/fw_ffmpeg_IO.o --shared -o MingW/mp4/nativeExtension.dll -fpermissive -L$(FFMPEG_LIBS_MP4) -lswscale -lavformat -lavcodec -lavutil -lvorbis -lvorbisenc -lopenh264 -liconv -L$(FLEX)/lib/win/ -lFlashRuntimeExtensions
