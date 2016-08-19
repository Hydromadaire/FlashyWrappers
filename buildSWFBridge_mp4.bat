CALL config.bat
call %AIR_PATH%\amxmlc -swf-version=31 -use-network=false -debug=false -static-link-runtime-shared-libraries -library-path=./FlasCC/fw_ffmpeg_encode_mp4_release.swc -library-path=%FLEX_PATH% ./FlasCC/SWFBridge/SWFBridge/Encoder.as -o ./FlasCC/SWFBridge/SWFBridge/FW_SWFBridge_ffmpeg_mp4.swf
@%AIR_PATH%\compc -swf-version=31 -target-player=20 -define+=CONFIG::VERSION,"'255'" -static-link-runtime-shared-libraries -library-path=%FLEX_PATH% -include-sources FlasCC/SWFBridge/com/rainbowcreatures/swf/FWVideoEncoder.as FlasCC/SWFBridge/com/rainbowcreatures/Dummy.as -output ./FlasCC/SWFBridge/fw_ffmpeg_encode_mp4_release_swf.swc
copy FlasCC\SWFBridge\fw_ffmpeg_encode_mp4_release_swf.swc ..\releases\%VERSION%\lib\FlashPlayer\mp4\fw_ffmpeg_encode_release_swf.swc
copy FlasCC\SWFBridge\SWFBridge\FW_SWFBridge_ffmpeg_mp4.swf ..\releases\%VERSION%\lib\FlashPlayer\mp4\FW_SWFBridge_ffmpeg.swf
