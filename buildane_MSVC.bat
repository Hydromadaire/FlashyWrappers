CALL config.bat
CALL %FLEX_PATH%acompc -define=CONFIG::DEMO,false -define+=CONFIG::AIR,true -define+=CONFIG::FLASCC,false -define+=CONFIG::CODECS,"'MP4'" -source-path="AS3/" -include-sources AS3/com/hurlant/ -include-sources AS3/cc/ -include-sources AS3/com/rainbowcreatures/ -include-sources AS3/com/adobe/ -include-sources AS3/com/FlasCC/ -swf-version=31 -output com.rainbowcreatures.FWVideoEncoder.swc
mkdir tmp
unzip -o -q com.rainbowcreatures.FWVideoEncoder.swc -d tmp
copy Windows\extension.xml tmp\*.* /Y
copy Windows\MSVC_project\Release\*.dll tmp\*.*  /Y
copy com.rainbowcreatures.FWVideoEncoder.swc tmp\*.* /Y
copy tmp\library.swf Mac\library.swf /Y
CALL %AIR_PATH%adt.bat -package -target ane Windows/ane/FWEncoderANE.ane tmp/extension.xml -swc tmp/com.rainbowcreatures.FWVideoEncoder.swc -platform Windows-x86 -C tmp nativeExtension.dll library.swf
copy Windows\ane\*.ane ..\releases\%VERSION%\lib\AIR\Windows\*.*
rem del tmp\*.* /Q
rem del tmp /Q
del *.swc