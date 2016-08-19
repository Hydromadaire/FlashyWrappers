FLASCC:=X
FFMPEG_INCLUDES_OGG:=./ffmpeg_ogg_theora_vorbis/include/
FFMPEG_LIBS_OGG:=./ffmpeg_ogg_theora_vorbis/lib/
FFMPEG_INCLUDES_MP4:=./ffmpeg_mp4_openh264_aac/include/
FFMPEG_LIBS_MP4:=./ffmpeg_mp4_openh264_aac/lib/

all:
	@echo "";
	@echo "FlashyWrappers 2.0";
	@echo "---------------------------------------------------------------";
	@echo "FFmpeg wrapper";
	@echo "Usage: make link_release FLASCC=/cygdrive/c/PATH_TO_FLASCC/sdk/";
	@echo "---------------------------------------------------------------";
	@if [ -d $(FLASCC)/usr/bin ] ; then true ; \
	else echo "Couldn't locate FLASCC sdk directory, please invoke make with \"make FLASCC=/path/to/FLASCC/sdk ...\"" ; exit 1 ; \

openh264_release:
	$(common_start)
	@echo "-> Compile the C into abc - RELEASE"
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/boost_atomic/libs/atomic/src/lockpool.cpp -o lockpool.abc -I../common/boost_atomic/
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_exception.cpp -o fw_exception.abc
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_ffmpeg_IO.cpp -o fw_ffmpeg_IO.abc -I$(FFMPEG_INCLUDES_MP4)
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_ffmpeg_encode.cpp -o fw_ffmpeg_encode_release.abc -I../common/boost_atomic/ -I$(FFMPEG_INCLUDES_MP4)
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_ffmpeg_decode.cpp -o fw_ffmpeg_decode_release.abc -I../common/boost_atomic/ -I$(FFMPEG_INCLUDES_MP4)
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall FW_wrapper.cpp -o fw_ffmpeg_encode_wrapper_release.abc -I../common/boost_atomic/ -I$(FFMPEG_INCLUDES_MP4)
	make link_openh264_release

openh264_demo:
	$(common_start)
	@echo "-> Compile the C into abc - RELEASE"
	"$(FLASCC)/usr/bin/g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/boost_atomic/libs/atomic/src/lockpool.cpp -o lockpool.abc -I../common/boost_atomic/
	"$(FLASCC)/usr/bin/g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_exception.cpp -o fw_exception.abc
	"$(FLASCC)/usr/bin/g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_ffmpeg_IO.cpp -o fw_ffmpeg_IO.abc -I$(FFMPEG_INCLUDES_MP4)
	"$(FLASCC)/usr/bin/g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_ffmpeg_encode.cpp -o fw_ffmpeg_encode_release.abc -I../common/boost_atomic/ -I$(FFMPEG_INCLUDES_MP4)
	"$(FLASCC)/usr/bin/g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_ffmpeg_decode.cpp -o fw_ffmpeg_decode_release.abc -I../common/boost_atomic/ -I$(FFMPEG_INCLUDES_MP4)
	"$(FLASCC)/usr/bin/g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall FW_wrapper.cpp -o fw_ffmpeg_encode_wrapper_release.abc -I../common/boost_atomic/ -I$(FFMPEG_INCLUDES_MP4)
	make link_openh264_release

x264_release:
	$(common_start)
	@echo "-> Compile the C into abc - RELEASE"
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/boost_atomic/libs/atomic/src/lockpool.cpp -o lockpool.abc -I../common/boost_atomic/
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_exception.cpp -o fw_exception.abc
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_ffmpeg_IO.cpp -o fw_ffmpeg_IO.abc
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_ffmpeg_encode.cpp -o fw_ffmpeg_encode_release.abc -I../common/boost_atomic/
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall FW_wrapper.cpp -o fw_ffmpeg_encode_wrapper_release.abc -I../common/boost_atomic/
	make link_x264_release

webm_release:
	$(common_start)
	@echo "-> Compile the C into abc - RELEASE"
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/boost_atomic/libs/atomic/src/lockpool.cpp -o lockpool.abc -I../common/boost_atomic/
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_exception.cpp -o fw_exception.abc
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_ffmpeg_IO.cpp -o fw_ffmpeg_IO.abc
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_ffmpeg_encode.cpp -o fw_ffmpeg_encode_release.abc -I../common/boost_atomic/
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall FW_wrapper.cpp -o fw_ffmpeg_encode_wrapper_release.abc -I../common/boost_atomic/
	make link_webm_release

ogg_release:
	$(common_start)
	@echo "-> Compile the C into abc - RELEASE"
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/boost_atomic/libs/atomic/src/lockpool.cpp -o lockpool.abc -I../common/boost_atomic/
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_exception.cpp -o fw_exception.abc
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_ffmpeg_IO.cpp -o fw_ffmpeg_IO.abc -I$(FFMPEG_INCLUDES_OGG)
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_ffmpeg_encode.cpp -o fw_ffmpeg_encode_release.abc -I../common/boost_atomic/ -I$(FFMPEG_INCLUDES_OGG)
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_ffmpeg_decode.cpp -o fw_ffmpeg_decode_release.abc -I../common/boost_atomic/ -I$(FFMPEG_INCLUDES_OGG)
	"$(FLASCC)/usr/bin/g++" -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall FW_wrapper.cpp -o fw_ffmpeg_encode_wrapper_release.abc -I../common/boost_atomic/ -I$(FFMPEG_INCLUDES_OGG)
	make link_ogg_release

ogg_demo:
	$(common_start)
	@echo "-> Compile the C into abc - DEMO"
	"$(FLASCC)/usr/bin/g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/boost_atomic/libs/atomic/src/lockpool.cpp -o lockpool.abc -I../common/boost_atomic/
	"$(FLASCC)/usr/bin/g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_exception.cpp -o fw_exception.abc
	"$(FLASCC)/usr/bin/g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_ffmpeg_IO.cpp -o fw_ffmpeg_IO.abc -I$(FFMPEG_INCLUDES_OGG)
	"$(FLASCC)/usr/bin/g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_ffmpeg_encode.cpp -o fw_ffmpeg_encode_release.abc -I../common/boost_atomic/ -I$(FFMPEG_INCLUDES_OGG)
	"$(FLASCC)/usr/bin/g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall ../common/FW_ffmpeg_decode.cpp -o fw_ffmpeg_decode_release.abc -I../common/boost_atomic/ -I$(FFMPEG_INCLUDES_OGG)
	"$(FLASCC)/usr/bin/g++" -DDEMO -DBOOST_ALL_NO_LIB -D__STDC_CONSTANT_MACROS -D__AVM2 -c -pthread -O4 -Wall FW_wrapper.cpp -o fw_ffmpeg_encode_wrapper_release.abc -I../common/boost_atomic/ -I$(FFMPEG_INCLUDES_OGG)
	make link_ogg_release

vfs:

#
# Build the VFS for Crossbridge. This build the VFS.abc file, which is needed to be linked into the final SWC to enable file operations
# on the virtual filesystem in Crossbridge. This really only needs to be built once.
#

	$(FLASCC)/usr/bin/genfs --type=embed myvfs VFS
	$(AS3COMPILERARGS)	-import $(call nativepath,$(FLASCC)/usr/lib/builtin.abc) \
				-import $(call nativepath,$(FLASCC)/usr/lib/playerglobal.abc) \
				-import $(call nativepath,$(FLASCC)/usr/lib/BinaryData.abc) \
				-import $(call nativepath,$(FLASCC)/usr/lib/ISpecialFile.abc) \
				-import $(call nativepath,$(FLASCC)/usr/lib/IBackingStore.abc) \
				-import $(call nativepath,$(FLASCC)/usr/lib/IVFS.abc) \
				-import $(call nativepath,$(FLASCC)/usr/lib/InMemoryBackingStore.abc) \
				-import $(call nativepath,$(FLASCC)/usr/lib/PlayerKernel.abc) \
				VFS*.as -outdir ../ -out ./

clean:
	rm -f *.abc

link_ogg_release:
	@echo "-> Compiling ogg encoder SWC"                                                                                                                                                                  
	"$(FLASCC)/usr/bin/g++" -D__STDC_CONSTANT_MACROS -D__AVM2 -O4 -Wall -pthread VFS.abc lockpool.abc fw_exception.abc fw_ffmpeg_encode_wrapper_release.abc fw_ffmpeg_encode_release.abc fw_ffmpeg_decode_release.abc fw_ffmpeg_IO.abc -emit-swc=com.fw_flascc -o fw_ffmpeg_encode_ogg_release.swc -L$(FFMPEG_LIBS_OGG) -lswscale -lavformat -lavcodec -lavutil -lvorbis -lvorbisenc -ltheora -liconv -logg

link_webm_release:
	@echo "-> Compiling ogg encoder SWC"                                                                                                                                                                  
	"$(FLASCC)/usr/bin/g++" -D__STDC_CONSTANT_MACROS -D__AVM2 -O4 -Wall -pthread VFS.abc lockpool.abc fw_exception.abc fw_ffmpeg_encode_wrapper_release.abc fw_ffmpeg_encode_release.abc fw_ffmpeg_IO.abc -emit-swc=com.fw_flascc -o fw_ffmpeg_encode_ogg_release.swc -L$(FFMPEG_LIBS_OGG) -lswscale -lavformat -lavcodec -lavutil -lvorbis -lvorbisenc -lvpx -liconv -logg

link_openh264_demo:
	@echo "-> Compiling openh264 encoder SWC"                                                                                                                                                                  
	"$(FLASCC)/usr/bin/g++" -D__STDC_CONSTANT_MACROS -D__AVM2 -O4 -Wall -pthread -O4 -Wall VFS.abc lockpool.abc fw_exception.abc fw_ffmpeg_encode_demo.abc fw_ffmpeg_encode_wrapper_demo.abc fw_ffmpeg_decode_release.abc fw_ffmpeg_IO.abc -emit-swc=com.fw_flascc -o fw_ffmpeg_encode_openh264_demo.swc -L$(FFMPEG_LIBS_MP4) -lswscale -lavformat -lavcodec -lavutil -lopenh264 -liconv 

link_openh264_release:
	@echo "-> Compiling openh264 encoder SWC"                                                                                                                                                                  
	"$(FLASCC)/usr/bin/g++" -D__STDC_CONSTANT_MACROS -D__AVM2 -O4 -Wall -pthread -O4 -Wall VFS.abc lockpool.abc fw_exception.abc fw_ffmpeg_encode_wrapper_release.abc fw_ffmpeg_encode_release.abc fw_ffmpeg_decode_release.abc fw_ffmpeg_IO.abc -emit-swc=com.fw_flascc -swf-version=17 -o fw_ffmpeg_encode_mp4_release.swc -L$(FFMPEG_LIBS_MP4) -lswscale -lavformat -lavcodec -lavutil -lopenh264 -liconv

link_x264_demo:
	@echo "-> Compiling x264 encoder SWC"                                                                                                                                                                  
	"$(FLASCC)/usr/bin/g++" -D__STDC_CONSTANT_MACROS -D__AVM2 -O4 -Wall -pthread -O4 -Wall VFS.abc lockpool.abc fw_exception.abc fw_ffmpeg_encode_demo.abc fw_ffmpeg_encode_wrapper_demo.abc fw_ffmpeg_IO.abc -emit-swc=com.fw_flascc -o fw_ffmpeg_encode_x264_demo.swc -lswscale -lavformat -lavcodec -lavutil -lx264 -lmp3lame

link_x264_release:
	@echo "-> Compiling x264 encoder SWC"                                                                                                                                                                  
	"$(FLASCC)/usr/bin/g++" -D__STDC_CONSTANT_MACROS -D__AVM2 -O4 -Wall -pthread -O4 -Wall VFS.abc lockpool.abc fw_exception.abc fw_ffmpeg_encode_release.abc fw_ffmpeg_encode_wrapper_release.abc fw_ffmpeg_IO.abc -emit-swc=com.fw_flascc -o fw_ffmpeg_encode_x264_release.swc -lswscale -lavformat -lavcodec -lavutil -lx264 -lmp3lame
	
define common_start =
	@if [ -d $(FLASCC)/usr/bin ] ; then true ; \
	else echo "Couldn't locate FLASCC sdk directory, please invoke make with \"make FLASCC=/path/to/FLASCC/sdk ...\"" ; exit 1 ; \
	fi
endef
