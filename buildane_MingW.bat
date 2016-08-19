CALL config.bat
CALL %FLEX_PATH%acompc -swf-version=18 -target-player=11.5 -define+=CONFIG::AIR,true -define+=CONFIG::FLASCC,false -define+=CONFIG::CODECS,"'OGG'" -source-path="%cd%"  -include-sources "com/rainbowcreatures/" -output com.rainbowcreatures.FWVideoEncoder.swc
mkdir tmp
unzip -o -q com.rainbowcreatures.FWVideoEncoder.swc -d tmp
copy Windows\extension.xml tmp\*.* /Y
copy Windows\MingW\ogg\nativeExtension.dll tmp\*.* /Y
copy com.rainbowcreatures.FWVideoEncoder.swc tmp\*.* /Y
CALL %AIR_PATH%adt.bat -package -target ane Windows/ane/FWEncoderANE.ane tmp/extension.xml -swc tmp/com.rainbowcreatures.FWVideoEncoder.swc -platform Windows-x86 -C tmp nativeExtension.dll library.swf
copy Windows\ane\*.ane ..\releases\%VERSION%\lib\AIR\Windows\*.*
rem del tmp\*.* /Q
rem del tmp /Q
del *.swc