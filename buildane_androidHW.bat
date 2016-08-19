CALL config.bat
REM java -jar %ANDROID_SDK%\tools\proguard\lib\proguard.jar -injars ./AndroidHW/FlashyWrappers.jar -outjars ./AndroidHW/FlashyWrappers_obfuscated.jar -libraryjars "%ANDROID_SDK%\platforms\android-18\android.jar";"%AIR_PATH%..\lib\android\FlashRuntimeExtensions.jar" @AndroidHW/proguard.cfg -verbose
REM copy /Y AndroidHW\FlashyWrappers_obfuscated.jar AndroidHW\FlashyWrappers.jar
CALL %AIR_PATH%acompc -define=CONFIG::AIR,true -define=CONFIG::FLASCC,false -define+=CONFIG::CODECS,"'MP4'" -define=CONFIG::DEMO,true -source-path="./AndroidHW/" -source-path="AS3/" -include-sources AS3/com/hurlant/ -include-sources AS3/cc/ -include-sources AS3/com/rainbowcreatures/ -include-sources AS3/com/adobe/ -include-sources AS3/com/FlasCC/ -swf-version=18 -target-player=11.5 -output com.rainbowcreatures.FWVideoEncoder.swc
rmdir Android-ARM /S /Q
mkdir Android-ARM
copy com.rainbowcreatures.FWVideoEncoder.swc .\AndroidHW\ane\
unzip -o -q com.rainbowcreatures.FWVideoEncoder.swc -d tmp
copy tmp\library.swf Android-ARM
copy AndroidHW\eclipse_project\FWAndroidHW\FlashyWrappers.jar Android-ARM
mkdir Android-ARM\res
mkdir Android-ARM\libs
CALL %AIR_PATH%adt.bat -package -target ane AndroidHW/ane/FWEncoderANE.ane AndroidHW/extension.xml -swc com.rainbowcreatures.FWVideoEncoder.swc -platform Android-ARM -C .\Android-ARM\ .
rem del tmp\*.* /Q
rem del tmp /Q
del *.swc
copy AndroidHW\ane\FWEncoderANE.ane ..\releases\%VERSION%\lib\AIR\Android