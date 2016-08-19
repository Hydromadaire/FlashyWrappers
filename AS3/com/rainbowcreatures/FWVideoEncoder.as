/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * This is the native extension wrapper, common for all platforms.
 * 
 */

package com.rainbowcreatures {

	import flash.display.BitmapData;
	import flash.display.Bitmap;
	import flash.media.Video;
	import flash.events.Event;
	import flash.display.Stage;

	// AIR imports
	CONFIG::AIR {
		import com.FlasCC.ISpecialFile;
		import flash.external.ExtensionContext;
		import flash.desktop.NativeApplication;
		import flash.events.NativeProcessExitEvent;
		import flash.events.IOErrorEvent;
		import flash.events.ProgressEvent;
		import flash.desktop.NativeProcess;
		import flash.desktop.NativeProcessStartupInfo;
		import flash.filesystem.File;
		import flash.filesystem.FileMode;
		import flash.filesystem.FileStream;
		import flash.display3D.textures;
	}
	CONFIG::FLASCC {
		// FlasCC / Crossbridge : import the encoder functions
//		import flash.external.ExternalInterface;
		import com.fw_ffmpeg.configureRAMAdjuster;
		import com.fw_ffmpeg.fw_setAudioRealtime;
		import com.fw_ffmpeg.fw_setLogging;
		import com.fw_ffmpeg.fw_setPTSMode;
		import com.fw_ffmpeg.fw_setFramedropMode;
		import com.fw_ffmpeg.ffmpeg_encodeitBase64;
		import com.fw_ffmpeg.ffmpeg_encodeit;
		import com.fw_ffmpeg.ffmpeg_init;
		import com.fw_ffmpeg.ffmpeg_initFinished;
		import com.fw_ffmpeg.ffmpeg_addVideoData;
		import com.fw_ffmpeg.ffmpeg_addAudioData;
		import com.fw_ffmpeg.ffmpeg_setFrames;
		import com.fw_ffmpeg.ffmpeg_canFinish;
		import com.fw_ffmpeg.ffmpeg_getVideoFramesSent;
		import com.fw_ffmpeg.decoder_doFrame;
		import com.fw_ffmpeg.decoder_bufferFrames;
		import com.fw_ffmpeg.decoder_getVideoFramesDecoded;
		import com.fw_ffmpeg.decoder_flushVideoStream;
		import com.fw_ffmpeg.decoder_flushAudioStream;
		import com.fw_ffmpeg.decoder_getVideoWidth;
		import com.fw_ffmpeg.decoder_getVideoHeight;
		import com.fw_ffmpeg.decoder_getEOF;
		import com.fw_ffmpeg.decoder_getAudioStreamLength;
		import com.fw_flascc.CModule;
		import com.fw_flascc.vfs.ISpecialFile;
	}

	import flash.system.MessageChannel;
	import flash.system.WorkerDomain;
	import flash.system.Worker;
	import flash.system.ApplicationDomain;
	import flash.events.SampleDataEvent;
	import flash.events.EventDispatcher;
	import flash.events.Event;
	import flash.display.BitmapData;
	import flash.utils.ByteArray;	
	import flash.geom.Rectangle;
	import flash.geom.Matrix;
	import flash.geom.Point;
	import flash.events.StatusEvent;
	import flash.display.MovieClip;
	import flash.display3D.Context3D;
	import flash.display.DisplayObject;
	import flash.display.Sprite;	
	import flash.media.Sound;
	import flash.utils.Endian;
	import flash.media.Microphone;	

	import flash.net.URLLoader;
	import flash.net.URLLoaderDataFormat;
	import flash.net.URLRequest;
	import flash.media.SoundCodec;

	// Runtime check of the platform 
	import flash.system.Capabilities;

	/**
	 * FlashyWrappers allows you to encode mp4 videos directly in AIR / Flash using the platform encoders(hardware accelerated, where available) or cross-compiled FFmpeg in Flash web player. You can capture AIR's / Flash screen or supply your own data through frame ByteArrays.
	 * It is also possible to supply audio data and to an extent capture the playing audio, using FWSoundMixer.
	 * 
	 */
	public class FWVideoEncoder extends MovieClip implements ISpecialFile {

		// public constants
		CONFIG::AIR {
			[Embed(source="lg.png")]
			/**
			 * @private
			 */
			public static var lg:Class;
			private var lgBMP:Bitmap = new lg() as Bitmap;
		}

		/**
		 * Calculate the capture rectangle
		 * 
		 * This mode works only with NO_SCALE stage scaling mode and TOP_LEFT stage alignment. 
		 * 
		 * @see #setCaptureRectangle() 
		 * 
		 */
		public static const CAPTURERECT_MODE_CALCULATE:int = 0;
		
		/**
		 * Measure the capture rectangle with visual guides.
		 * 
		 * This mode works with all stage scaling and alignment modes, but might not be 100% accurate. Specifically,
		 * the resuling capture rectangle is aligned to multiplies of 4 and the pixels which are automatically rendered 
		 * need to be measured. However, sometimes the pixels might get scaled up/down too much.
		 * 
		 * @see #setCaptureRectangle()
		 * 
		 */
		public static const CAPTURERECT_MODE_VISUAL:int = 1;

		/**
		 * Framedrop mode auto.
		 * 
		 * Causes FlashyWrappers to automatically determine if framedrop should be on and off (the default).
		 * 
		 * @see #FRAMEDROP_OFF
		 * @see #FRAMEDROP_ON
		 * @see #forceFramedropMode()
		 */
		public static const FRAMEDROP_AUTO:int = 0;
		
		/**
		 * Framedrop mode off.
		 * 
		 * Doesn't drop any frames, non-realtime uses this by default.
		 * 
		 * @see #FRAMEDROP_AUTO
		 * @see #FRAMEDROP_ON
		 * @see #forceFramedropMode()
		 * 
		 */
		public static const FRAMEDROP_OFF:int = 1;
		
		/**
		 * Framedrop mode on.
		 * 
		 * Drops frames whenever they are not needed, realtime mode uses this by default.
		 * 
		 * @see #FRAMEDROP_AUTO
		 * @see #FRAMEDROP_OFF
		 * @see #forceFramedropMode()
		 * 
		 */
		public static const FRAMEDROP_ON:int = 2;

		// logging modes
		/**
		 * Basic logging.
		 * 
		 * This is the default, it logs only basic things, not useful for bug reports.
		 * 
		 * @see #LOGGING_VERBOSE
		 * @see #setLogging()
		 * 
		 */
		public static const LOGGING_BASIC:int = 0;
		
		/**
		 * Verbose logging.
		 * 
		 * This is meant only for bug reports / debugging, do not use in release.
		 * 
		 * @see #LOGGING_BASIC
		 * @see #setLogging()
		 */
		public static const LOGGING_VERBOSE:int = 1;

		// pts force modes
		/**
		 * Automatic PTS calculation.
		 * 
		 * Determines whenever to use mono or realtime PTS - the default.
		 * 
		 * @see #PTS_MONO
		 * @see #PTS_REALTIME 
		 * @see #forcePTSMode()
		 * 
		 */
		public static const PTS_AUTO:int = 0;
		
		/**
		 * Mono PTS calculation.
		 * 
		 * The PTS are increased in monotonic steps - its the default for non-realtime mode.
		 *
		 * @see #PTS_AUTO
		 * @see #PTS_REALTIME
		 * @see #forcePTSMode()
		 * 
		 */
		public static const PTS_MONO:int = 1;
		
		/**
		 * Realtime PTS calculation.
		 * 
		 * The PTS are based on clock - its the default for realtime mode.
		 * 
		 * @see #PTS_AUTO
		 * @see #PTS_MONO
		 * @see #forcePTSMode()
		 * 
		 */
		public static const PTS_REALTIME:int = 2;

		// audio recording modes
		/**
		 * Audio recording from microphone.
		 * 
		 * Only microphone audio is recorded. On mobile this might trigger native microphone recording, on desktop only uses Flash / AIR Microphone class for recording.
		 * 
		 * @see #AUDIO_MONO
		 * @see #AUDIO_STEREO
		 * @see #AUDIO_OFF
		 * @see #start()
		 * @see #setRecordAudio()
		 * 
		 */
		public static const AUDIO_MICROPHONE:String = "audioMicrophone";
		
		/**
		 * Audio recording in MONO.
		 * 
		 * Is prepared for floating point 1-channel PCM data, such as data from microphone.
		 * 
		 * @see #AUDIO_STEREO
		 * @see #AUDIO_MICROPHONE
		 * @see #AUDIO_OFF
		 * @see #start()
		 * @see #setRecordAudio()
		 * 
		 */
		public static const AUDIO_MONO:String = "audioMono";
		
		/**
		 * Audio recording is STEREO.
		 * 
		 * Is prepared for floating point 2-channel PCM data, such as data from FWSoundMixer, or Sound class.
		 * 
		 * @see #AUDIO_MICROPHONE
		 * @see #AUDIO_MONO		 
		 * @see #AUDIO_OFF
		 * @see #start()
		 * @see #setRecordAudio()
		 * 
		 */
		public static const AUDIO_STEREO:String = "audioStereo";
//		public static const AUDIO_CAPTURE:String = "audioCapture";

		/**
		 * Use this when listening for FW status events, indicates FW was loaded and is ready to start.
		 * 
		 * @see #STATUS_STARTED
		 * @see #STATUS_FINISHED
		 * @see #STATUS_STOPPED
		 * @see #STATUS_GALLERY_SAVED
		 * @see #STATUS_GALLERY_FAILED
		 */
		public static const STATUS_READY:String = "ready";
		
		/**
		 * Use this when listening for FW status events, indicates FW was started and is ready to record.
		 * 
		 * @see #STATUS_READY
		 * @see #STATUS_FINISHED
		 * @see #STATUS_STOPPED
		 * @see #STATUS_GALLERY_SAVED
		 * @see #STATUS_GALLERY_FAILED
		 * 
		 */
		public static const STATUS_STARTED:String = "started";
		
		/**
		 * Use this when listening for FW status events, indicates FW video was encoded and is ready to be saved / worked with.
		 * 
 		 * @see #STATUS_READY
		 * @see #STATUS_FINISHED
		 * @see #STATUS_STOPPED
		 * @see #STATUS_GALLERY_SAVED
		 * @see #STATUS_GALLERY_FAILED 
		 * 
		 */
		public static const STATUS_FINISHED:String = "encoded";

		/**
		 * Use this when listening for FW status events, indicates FW encoding was forced to stop, for example when the app was suspended. You will need to make your app UI react appropriately, as if you finished recording. Currently used only on mobile.
		 *
		 * @see #STATUS_READY
		 * @see #STATUS_STARTED
		 * @see #STATUS_FINISHED
		 * @see #STATUS_GALLERY_SAVED
		 * @see #STATUS_GALLERY_FAILED  
		 */
		public static const STATUS_STOPPED:String = "stopped";

		/**
		 * Use this when listening for FW status events, indicates FW video was saved to gallery / camera roll. Only on mobile.
		 *
		 * @see #STATUS_READY
		 * @see #STATUS_STARTED
		 * @see #STATUS_STOPPED
		 * @see #STATUS_FINISHED
		 * @see #STATUS_ENCODING_CANCEL
		 * @see #STATUS_GALLERY_FAILED   
		 */
		public static const STATUS_GALLERY_SAVED:String = "gallery_saved";
		
		/**
		 * Use this when listening for FW status events, indicates FW video was NOT saved to gallery / camera roll. Only on mobile. The reason might be permissions, not enough space or wrong video format (resolution perhaps).
		 *
		 * @see #STATUS_READY
		 * @see #STATUS_STARTED
		 * @see #STATUS_STOPPED
		 * @see #STATUS_FINISHED
		 * @see #STATUS_ENCODING_CANCEL
		 * @see #STATUS_GALLERY_SAVED
		 */
		public static const STATUS_GALLERY_FAILED:String = "gallery_failed";

		/**
		 * Gallery permission status events, only for iOS. 
		 *
		 * @see #STATUS_IOS_GALLERY_RESTRICTED
		 * @see #STATUS_IOS_GALLERY_DENIED
		 * @see #iOS_askPhotoPermissions()
		 */
		public static const STATUS_IOS_GALLERY_AUTHORIZED:String = "ios_gallery_authorized";


		/**
		 * Gallery permission status events, only for iOS. 
		 *
		 * @see #STATUS_IOS_GALLERY_AUTHORIZED
		 * @see #STATUS_IOS_GALLERY_DENIED
		 * @see #iOS_askPhotoPermissions()
		 *
		 */

		public static const STATUS_IOS_GALLERY_RESTRICTED:String = "ios_gallery_restricted";

		/**
		 * Gallery permission status events, only for iOS. 
		 *
		 * @see #STATUS_IOS_GALLERY_AUTHORIZED
		 * @see #STATUS_IOS_GALLERY_DENIED
		 * @see #iOS_askPhotoPermissions()
		 *
		 */
		public static const STATUS_IOS_GALLERY_DENIED:String = "ios_gallery_denied";


		/**
		 * Audio recording is OFF.
		 * 
		 * Doesn't record any audio and internally avoids any audio code paths. Also useful to try this in case
		 * of issues / bugs, to rule out anything audio related.
		 * 
		 * @see #AUDIO_MICROPHONE
		 * @see #AUDIO_MONO
		 * @see #AUDIO_STEREO
		 * @see #start()
		 */
		public static const AUDIO_OFF:String = "audioOff";		

		// platforms
		/**
		 *  iOS platform
		 */
		public static const PLATFORM_IOS:String = "IOS";
		/** 
		 * Android platform
		 */
		public static const PLATFORM_ANDROID:String = "ANDROID";
		/**
		 * Windows platform
		 */
		public static const PLATFORM_WINDOWS:String = "WINDOWS";
		/**
		 * Mac platform
		 */
		public static const PLATFORM_MAC:String = "MAC";
		/**
		 * Flash Player platform
		 */
		public static const PLATFORM_FLASH:String = "FLASH";

		/**
		 * Mobile platform
		 */
		public static const PLATFORM_TYPE_MOBILE:String = "MOBILE";
		/**
		 * Desktop platform
		 */
		public static const PLATFORM_TYPE_DESKTOP:String = "DESKTOP";

		// codecs, only used for ffmpeg based platform builds, otherwise platform dependant (usually mp4 / h264 / aac)
		private	var tempMeasureSprite:Sprite = null;
		private var codec_container:String = "";
		private var codec_audio:String = "";
		private var codec_video:String = "";
		private var audio:Boolean = true;
		private var recordAudio:String = "";

		private var logging:int = LOGGING_BASIC;

		private static var instance:FWVideoEncoder = null;
		private var clearFrame:Boolean = true;
		private var videoWidth:Number = 0;
		private var videoHeight:Number = 0;
		private var b:BitmapData = null;
		private var bTemp:BitmapData = null;
		private var mat:Matrix;
		private var lastCaptureWidth:int = 0;
		private var lastCaptureHeight:int = 0;
		private var video_bo:ByteArray = new ByteArray();
		private	var stage_fps:Number = -1;
		private var video_fps:Number = 0;

		// if this is not null, the activeSoundtrack needs to be extracted each frame when calling addVideoFrame
		private var activeSoundtrack:Sound = null;
		private var activeSoundtrackByteArray:ByteArray = new ByteArray();
		private var addSilence:Boolean = false;

		/**
		 * Pre-allocate domain memory for the Flash recorder?
		 * 
		 * This might reduce the memory growth as it should avoid memory fragmentation.
		 * 
		 */
		public var allocateDomainMemory:int = 0;
		private var dispatchTraceEvent:Boolean = false;

		/**
		 * On iOS and Android, this changes the temp video filename when saving videos to mobile.
		 * Currently doesn't work on other platforms.
		 * 
		 */
		public var mobileFilename:String = "video_merged.mp4";

		/**
		 * Current platform which FW has detected.
		 * 
		 * @see #PLATFORM_IOS
		 * @see #PLATFORM_ANDROID
		 * @see #PLATFORM_WINDOWS
		 * @see #PLATFORM_MAC
		 * @see #PLATFORM_FLASH
		 * 
		 */
		public var platform:String = "FLASH";
		
		/**
		 * Current platform type which FW has detected.
		 * 
		 * @see #PLATFORM_TYPE_DESKTOP
		 * @see #PLATFORM_TYPE_MOBILE
		 */
		public var platform_type:String = "DESKTOP";

		/**
		 * Capture Stage3D layer with MovieClip layer (doesn't affect OpenGL capture on mobile).
		 * 
		 * Indicates whenever we want to capture Stage3D together with MovieClip in one call - if this is set to yes, the frame is not sent to the encoder after capturing Stage3D,
		 * but only after capturing the top MovieClip layer. The default is false, which means that Stage3D and MovieClip captures are both sent to encoder straight away.
		 */
		public var stage3DWithMC:Boolean = false;		

		/**
		 * Is FW recording? This is true after calling start in general.
		 * 
		 */
		
		public var isRecording:Boolean = false;
		
		/**
		 * Are we in the "post-processing" phase, this is useful mostly on iOS where this phase exists and is pretty long currently.
		 * 
		 */
		public var isEncoding:Boolean = false;


		/**
		 * On Windows, if this contains filename, the video will be saved to this file natively, instead of copying it to ByteArray. When you use this,
		 * getVideo() always returns an empty ByteArray. This can avoid memory issues in 32-bit Win AIR if the video is too large.
		 * 
		 */
		public var saveToFileWin:String = "";

		// stupid frame counter for Android, this needs to be edited out later on
		private var a:int = 0;

		// are we in multithreaded mode?
		private var MT:Boolean = false;

		// realtime?
		private var realtime:Boolean = true;
		private var realtimeSet:Boolean = false;

		private var targetWidth:int = 0;
		private var targetHeight:int = 0;

		// number of total frames (in MT that means number of frames sent so far, non-MT means total frames set before encoding started)
		private var numFramesTotal:int = 0;

		// Integrated microphone recording			
		private var microphone:Microphone = null;
		private var micPermissionAsked:Boolean = false;
		private var samplesMic:ByteArray = new ByteArray();
		private var recordFlashMicrophone:Boolean = false;

		private var worker:Worker = null;
		private var workerListenerCreated:Boolean = false;
		private var workerToMain:MessageChannel;

		// iOS specific vars
		// -----------------
		// WAV audio file for AVFoundation

		CONFIG::AIR {
		// those 3 for ANE "fixing" on Mac...
		private static var callback_error:Function = null,   
  		callback_ioerror:Function = null,   
	    	callback_output:Function = null;
    
		private var _ctx:ExtensionContext;
		private var audioFile:File;
		private var audioStream:FileStream;
		private var audioLength:Number = 0;
		private var audioFilePath:String = "audio.wav";

		// Video file for AVFoundation
		private var videoFilePath:String;
		private var videoFile:File;
		
		/**
		 * Only for mobile, reference to the temp MP4 video file which already includes audio.
		 * 
		 * @see #mergedFilePath
		 */
		public var mergedFile:File;
		}	

		private var audioRate:Number = 44100;
		private var audioChannels:Number = 2;	
			
		/**
		 * The path to the merged video file.
		 *
		 * @see #mergedFile 
		 */
		public var mergedFilePath:String;
		public var iOS_recordToWAV:int = 0;
		public var iOS_nativeQuality:int = 2;

		private var fullscreenErrorReported:Boolean = false;

		// If this is true, it checks if the threads finished on enter frame
		private var checkThreadsFinished:Boolean = false;

		// If this is true, it checks if the threads finished on enter frame
		private var checkInitThreadFinished:Boolean = false;

		// constructor, init

		/** 
		 * Parent sprite to FW encoder. This should have stage reference (stage != null). 
		 * 
		 * FW uses this to find out various stage properties, such as width, height, fps etc.
		 * 
		 */			
		public var _parent:Sprite = null;
		private var ba:ByteArray = new ByteArray();

		// new hack to allow FW run as background worker
		private var runningAsWorker:Boolean = true;


		public function FWVideoEncoder():void {

			// determine the platform 

			// we're in AIR
			if (Capabilities.playerType == 'Desktop') {
				if((Capabilities.os.indexOf("Windows") >= 0)) {
					platform = "WINDOWS";
					platform_type = "DESKTOP";
				}
				else if((Capabilities.os.indexOf("Mac") >= 0)) {
					platform = "MAC";
					platform_type = "DESKTOP";
				} 
				else if((Capabilities.os.indexOf("iPhone") >= 0)) {
					platform = "IOS";
					platform_type = "MOBILE";
				}
				else if((Capabilities.os.indexOf("Linux") >= 0)) {
					platform = "ANDROID";
					platform_type = "MOBILE";
				}
			} else {
				// we're in Flash Player, website or standalone
				platform = "FLASH";
				platform_type = "DESKTOP";
			}

			// set codecs if needed(for ffmpeg)
			if (CONFIG::CODECS == 'OGG') {		
				codec_container = "ogg";
				codec_audio = "libvorbis";
				codec_video = "libtheora";
			}
			if (CONFIG::CODECS == 'MP4') {		
				codec_container = "mp4";
				codec_audio = "aac";
				codec_video = "libopenh264";
			}

			activeSoundtrackByteArray.endian = Endian.LITTLE_ENDIAN;

			// create ANE context for AIR
			CONFIG::AIR {
				_ctx = ExtensionContext.createExtensionContext('com.rainbowcreatures.FWEncoderANE', '');
				if (!_ctx) {
					throwError("Failed to initialize ANE context(is the native part missing?). Make sure the ANE configuration is right."); 
				}
				// catch events from the ANE
				_ctx.addEventListener( StatusEvent.STATUS, onStatus );

				// on mobile, deal with suspending the app to stop encoding properly and dispatch an event about that
			
				if (platform == 'IOS' || platform == 'ANDROID') {				
					NativeApplication.nativeApplication.addEventListener(Event.DEACTIVATE, onDeactivate);			
				}
			}
			CONFIG::FLASCC {
				addEventListener(Event.ADDED_TO_STAGE, onAdded);					
			}
		}

		private function FWtrace(s:String):void {
			trace('[FlashyWrappers] ' + s);

			// for later - dispatch trace event optionally, to log to browser etc.
			if (dispatchTraceEvent) {
				instance.dispatchEvent( new StatusEvent( StatusEvent.STATUS, false, false, 'trace', s));
			}
		}
		
		private function fixANE(extensionID:String, onExit:Function=null):Boolean {

		CONFIG::AIR {     

	       	var error : Error = null;
               	var fileFailed : int = 0;
               	var fileFixed : int = 0;
               
               try 
               {
               if (!NativeProcess.isSupported) {  
                    throwError("fixANE Unsupported exit");
                    if (onExit!=null)  
                         onExit(extensionID, false);  
                    return false;  
               }  
               // init event listners   
               if (callback_output==null)  
                    callback_output =   
                         function(event:ProgressEvent):void {  
                              var process:NativeProcess = event.target as NativeProcess;  
                              FWtrace("OUT -" + process.standardOutput.readUTFBytes(process.standardError.bytesAvailable));   
                         };  
               if (callback_error==null)  
                    callback_error =   
                         function(event:ProgressEvent):void {  
                              var process:NativeProcess = event.target as NativeProcess;  
                              throwError("fixANE ERROR -" + process.standardError.readUTFBytes(process.standardError.bytesAvailable));   
                         };  
               if (callback_ioerror==null)  
                    callback_ioerror =   
                         function(event:IOErrorEvent):void {  
                              FWtrace(event.toString());  
                         };  
               
               var ext_dir:File;  
               try {  
                    ext_dir = ExtensionContext.getExtensionDirectory(extensionID);  
               } catch (e:*) {  
                    FWtrace("GetExtensionDirectory error "+e);
                    if (onExit!=null)  
                         onExit(extensionID, false);  
                    return false;  
               }  
               if (!ext_dir.isDirectory) {  
                    FWtrace(ext_dir + "not isDirectory ")
                    if (onExit!=null)  
                         onExit(extensionID, false);  
                    return false;  
               }  
               var ane_dir:File = ext_dir.resolvePath("META-INF/ANE/");  
               var ext_stream:FileStream = new FileStream();  
               ext_stream.open(ane_dir.resolvePath("extension.xml"), FileMode.READ);  
               var ext_xml:XML = XML(ext_stream.readUTFBytes(ext_stream.bytesAvailable));  
               ext_stream.close();  
               var defaultNS:Namespace = ext_xml.namespace("");  
               var framework:String = ext_xml.defaultNS::platforms.defaultNS::platform.(@name=="MacOS-x86").defaultNS::applicationDeployment.defaultNS::nativeLibrary.text();  
               var ane64:Boolean = false;
               if (!framework) {  
               		framework = ext_xml.defaultNS::platforms.defaultNS::platform.(@name=="MacOS-x86-64").defaultNS::applicationDeployment.defaultNS::nativeLibrary.text();  
               		if (!framework) {
                   	 	FWtrace("No MacOS framework " + framework);
                   		if (onExit!=null)  
                        	 onExit(extensionID, false);  
                    	return false;  
                    } else ane64 = true;
               }  
               
               var framework_dir:File;
               if (!ane64) {
               		framework_dir = ane_dir.resolvePath('MacOS-x86/'+framework); 
               } else {
            	   framework_dir = ane_dir.resolvePath('MacOS-x86-64/'+framework);
               } 
               // list of symlink files  
               var symlink:Vector.<String> = new Vector.<String>(3, true);  
               symlink[0] = 'Resources';  
               symlink[1] = framework_dir.name.substr(0, framework_dir.name.length-framework_dir.extension.length-1);  
               symlink[2] = 'Versions/Current';  
	//		   symlink[3] = 'Frameworks';
	//		   symlink[4] = 'Versions/A/Frameworks/Frameworks';
               var fileToFix:int = symlink.length;
            	trace("Fixing main executable");
            	var fMain:File;
            	if (!ane64) {
            		fMain = framework_dir.resolvePath('Versions/Current/encoderANEMac');
            	} else {
            		fMain = framework_dir.resolvePath('Versions/Current/encoderANEMac64');
            	}
            	trace("Framework dir:" + framework_dir.nativePath); 
                var nativeProcessStartupInfo:NativeProcessStartupInfo = new NativeProcessStartupInfo();  
                              nativeProcessStartupInfo.executable = new File('/bin/chmod');  
                              trace("fMain parent:" + fMain.parent);
                              nativeProcessStartupInfo.workingDirectory = fMain.parent;  
                              nativeProcessStartupInfo.arguments = new Vector.<String>(2, true);  
                              nativeProcessStartupInfo.arguments[0] = "777";  
                              nativeProcessStartupInfo.arguments[1] = fMain.name;   
                              var process:NativeProcess = new NativeProcess();      
                              process.start(nativeProcessStartupInfo);  
                              process.addEventListener(ProgressEvent.STANDARD_OUTPUT_DATA, callback_output);  
                              process.addEventListener(ProgressEvent.STANDARD_ERROR_DATA, callback_error);  
                              process.addEventListener(IOErrorEvent.STANDARD_OUTPUT_IO_ERROR, callback_ioerror);  
                              process.addEventListener(IOErrorEvent.STANDARD_ERROR_IO_ERROR, callback_ioerror);  
                              process.addEventListener(  
                                   NativeProcessExitEvent.EXIT,   
                                   function (event:NativeProcessExitEvent):void {  
                                        if (event.exitCode==0)  
                                        {     
                                        }
                                        else  
                                        {   
											FWtrace("failed");
                                        }
                                        
                                   }  
                              );   
               symlink.every(  
                    function(item:String, index:int, a:Vector.<String>):Boolean {  
                         var f:File = framework_dir.resolvePath(item);  
			 			 FWtrace("Fixing symlink: " + item);
                         if (!f.isSymbolicLink) {  
                              var fs:FileStream = new FileStream();  
                              fs.open(f, FileMode.READ);  
                              var lnk:String = fs.readUTFBytes(fs.bytesAvailable);  
                              fs.close();                          
                              var nativeProcessStartupInfo:NativeProcessStartupInfo = new NativeProcessStartupInfo();  
                              nativeProcessStartupInfo.executable = new File('/bin/ln');  
                              nativeProcessStartupInfo.workingDirectory = f.parent;  
                              nativeProcessStartupInfo.arguments = new Vector.<String>(3, true);  
                              nativeProcessStartupInfo.arguments[0] = "-Fs";  
                              nativeProcessStartupInfo.arguments[1] = lnk;  
                              nativeProcessStartupInfo.arguments[2] = f.name;  
                              var process:NativeProcess = new NativeProcess();      
                              process.start(nativeProcessStartupInfo);  
                              process.addEventListener(ProgressEvent.STANDARD_OUTPUT_DATA, callback_output);  
                              process.addEventListener(ProgressEvent.STANDARD_ERROR_DATA, callback_error);  
                              process.addEventListener(IOErrorEvent.STANDARD_OUTPUT_IO_ERROR, callback_ioerror);  
                              process.addEventListener(IOErrorEvent.STANDARD_ERROR_IO_ERROR, callback_ioerror);  
                              process.addEventListener(  
                                   NativeProcessExitEvent.EXIT,   
                                   function (event:NativeProcessExitEvent):void {  
                                        if (event.exitCode==0)  
                                        {     fileFixed++;  
                                        }
                                        else  
                                        {   
						FWtrace("failed");
						fileFailed++  
                                        }
                                        
                                   }  
                              );  
                         } else  
                              fileFixed++;  // count as fixed if it doesn't appear to be in need of fixing
                         return true;  
                    }  
               );  
          } // end of outer try 
          catch (err : *)
          {     
		error = err;
		FWtrace("fixANE error "+err);          
          }
         
          if (onExit!=null)  
          {
               	onExit(extensionID, fileFailed==0 && (error==null));  
          }
          return(true);
          }
	      return(true);
        }
     

		// cancel the recording if its going on
		// for now we are keeping this private because it works only on iOS, and we're using that for internal reasons (when suspending the app)
		private function cancel():void {
			CONFIG::AIR {			
				if (platform == 'IOS' || platform == 'ANDROID') {
					if (isRecording) {
						FWtrace("Deactivated while recording, stopping...");

						// clean up
				
//						_ctx.call('fw_finish');
						finish();
				
						// this makes sure none of the encoding methods(such as adding video or audio frames) called from the app crash the ANE as soon
				
						// as the encoding is stopped on the ANE level
				
					}
				}		
			}
		}	

		private function initMic():void {		
			if (microphone == null) {
				microphone = Microphone.getMicrophone();
				microphone.codec = SoundCodec.NELLYMOSER;
				microphone.rate = 44;
				microphone.encodeQuality = 10;
				microphone.setSilenceLevel(0);
				// important: audio ByteArray needs to be in little endian 
				samplesMic.endian = Endian.LITTLE_ENDIAN;
			}
		}

		/**
		 * Force the app to ask microphone recording permissions.
		 * 
		 */
		public function askMicPermission():void {
			// init microphone
			if (microphone == null) initMic();
			microphone.addEventListener(SampleDataEvent.SAMPLE_DATA, sndDataMic);
			microphone.removeEventListener(SampleDataEvent.SAMPLE_DATA, sndDataMic);
		}	

		// take the audio samples from microphone and add them into myEncoder
		private function sndDataTest(event:SampleDataEvent):void {
//			ExternalInterface.call("console.log", "Microphone rate when testing: " + microphone.rate);				
			microphone.dispatchEvent(new Event(Event.COMPLETE));
			microphone.removeEventListener(SampleDataEvent.SAMPLE_DATA, sndDataTest);
		}

		// take the audio samples from microphone and add them into myEncoder
		private function sndDataMic(event:SampleDataEvent):void {			
//			ExternalInterface.call("console.log", "Microphone rate when not testing: " + microphone.rate);				
			if (event.data.bytesAvailable > 0)
			{
				while(event.data.bytesAvailable > 0)
				{
					samplesMic.writeFloat(event.data.readFloat());
				}
			}
			if (samplesMic.length >= 8192) {
				samplesMic.position = 0;
				addAudioFrame(samplesMic);
				samplesMic.length = 0;
			}
		}
			
		// suspend event handler for iOS now
		private function onDeactivate(e:Event):void 
		{
			CONFIG::AIR {			
			
				// finish recording
			
				FWtrace("App deactivated");
				if (platform == 'IOS') {
					NativeApplication.nativeApplication.executeInBackground = true;
				}
				cancel();							
				if (isRecording) {
					NativeApplication.nativeApplication.addEventListener(Event.ACTIVATE, onActivate);		
				}
			}	
		}		
				
		// back from suspension mode on iOS now
		private function onActivate(e:Event):void {
			
			CONFIG::AIR {			

				NativeApplication.nativeApplication.removeEventListener(Event.ACTIVATE, onActivate);
			
				// return the display FBO back to AIR
 
				// we know that the recording was stopped so we inform the app to not bother anymore			
				instance.dispatchEvent( new StatusEvent( StatusEvent.STATUS, false, false, "stopped", ""));
				if (platform == 'IOS') {
					NativeApplication.nativeApplication.executeInBackground = false;
				}
			}	
		}

		/**
		 * Get the FW encoder instance. Always use this before doing anything else with FW.
		 * 
		 * @param parent The parent sprite which must contain stage reference(stage != null).
		 * @param domainMemory Only for Flash Player, allows to specify domain memory amount to preallocate.
		 * 
		 */
		public static function getInstance(parent:Sprite = null, domainMemory:int = 0):FWVideoEncoder {
			if (instance == null) {
				// no instance
				CONFIG::AIR {
					instance = new FWVideoEncoder();
					instance._parent = parent;
				}
				CONFIG::FLASCC {
					if (parent == null) throw new Error("[FlashyWrappers] You must call getInstance with valid parent. FlashyWrappers for Flash Player must be added to stage and have access to the root DisplayObject through it.");
					instance = new FWVideoEncoder();
					instance._parent = parent;
					instance.allocateDomainMemory = domainMemory;
					parent.addChild(instance);
					// TODO old code, for the case when FW was not in separate SWF...probably erase in time
					if (!Worker.current.isPrimordial && !(instance.runningAsWorker)) {
						return null;
					}
				}
			} 
			return instance;
		}
		
		/**
		 * Configure the RAM guard for FW Flash. This lowers the encoder FPS dynamically based on RAM usage.
		 * 
		 * Only works in Flash Player currently.
		 * 
		 * @param RAMadjusterFPSDivider How much the fps drops if we get over a certain thresold.
		 * @param RAMadjusterTolerance A multiplier - how much over the thresold can we go before RAM guard kicks in and slashes the fps. The default is 1.5, which means 1.5x times over max.
		 * 
		 */
		public function configureRAMGuard(RAMadjusterFPSDivider:Number = 2, RAMadjusterTolerance:Number = 1.5):void {
			CONFIG::FLASCC {
				configureRAMAdjuster(RAMadjusterFPSDivider, RAMadjusterTolerance);
			}			
			CONFIG::FLASCC {
				FWtrace('Warning: RAM Guard not implemented for AIR');
			}
		}
		
		// This is library setup for FlasCC / Crossbridge only
		private function onAdded(e:Event):void {
			CONFIG::FLASCC {
				FWtrace("Added to stage!");
				removeEventListener(Event.ADDED_TO_STAGE, onAdded);					
		      		addEventListener(Event.ENTER_FRAME, enterFrame);			
				addEventListener(Event.REMOVED_FROM_STAGE, onRemoved);

				com.fw_flascc.CModule.rootSprite = instance;
				com.fw_flascc.CModule.rootSprite.addEventListener( StatusEvent.STATUS, onStatus );
	
				// be more transparent about memory
				com.fw_flascc.CModule.throwWhenOutOfMemory = true;

				if (com.fw_flascc.CModule.runningAsWorker()) {
					return;
				} 
				com.fw_flascc.CModule.vfs.console = instance;

				// try to prevent FlasCC memory fragmentation
				if (allocateDomainMemory > 0) {
					var p:int = CModule.malloc(1024 * 1024 * allocateDomainMemory); // pre-allocate a block domain memory, the size should be according to your project
					if (!p) throw(new Error("You have opened too many pages, close some of them or restart your browser!"));
					CModule.malloc(1);//take up the domain memory
					CModule.free(p);
				}
				com.fw_flascc.CModule.startAsync(instance);
			}
		}

		// This is library setup for FlasCC / Crossbridge only
		private function onRemoved(e:Event):void {
			CONFIG::FLASCC {
				FWtrace("Removed from stage!");
		      		removeEventListener(Event.ENTER_FRAME, enterFrame);			
				removeEventListener(Event.REMOVED_FROM_STAGE, onRemoved);
				// shrink the domain memory
				FWtrace("Domain memory length:" + com.fw_flascc.CModule.ram.length + ", shrinking to: " + ApplicationDomain.MIN_DOMAIN_MEMORY_LENGTH);
				com.fw_flascc.CModule.ram.length = ApplicationDomain.MIN_DOMAIN_MEMORY_LENGTH;
	                        com.fw_flascc.CModule.ram.position = 0;
			}
		}

		// What to do when message is recieved -> convert to status event
		private function onWorkerToMain(e:Event):void {
			trace("Got msg from worker");
			var message:String = workerToMain.receive() as String;
			FWtrace(message);
		}

		// This is library setup for FlasCC / Crossbridge only
		private function enterFrame(e:Event):void {
			CONFIG::FLASCC {
				com.fw_flascc.CModule.serviceUIRequests();

				// create message channels for logging if enabled
				if (logging) {
					// wait for background worker to spawn (this would be better done with status event)
					if (WorkerDomain.current.listWorkers().length > 1 && !workerListenerCreated) {                
					
						FWtrace("Creating background worker message channel listener");
						workerListenerCreated = true;

				                // Create messaging channels for 1-way messaging
						// This traverses all the workers in our app and select the one which is not the primordial worker (=thats our FW background worker)
						for (var a:int = 0; a < WorkerDomain.current.listWorkers().length; a++) {
							if (!WorkerDomain.current.listWorkers()[a].isPrimordial) {
								worker = WorkerDomain.current.listWorkers()[a];
							}
						}
						if (worker.isPrimordial) {
							FWtrace("Background worker is primordeal, that shouldn't happen!");
						} 

						// Here we create a channel for communication from the bg worker to our main worker
				                workerToMain = worker.createMessageChannel(Worker.current);
                
				                // Inject messaging channels as a shared property
				                worker.setSharedProperty("workerToMain", workerToMain);
                
				                // Listen to the response from Worker
				                workerToMain.addEventListener(Event.CHANNEL_MESSAGE, onWorkerToMain);					
					}

					// if the background threads are lost then erase the channels
					if (WorkerDomain.current.listWorkers().length == 1 && workerListenerCreated) {                
						FWtrace("Removing background worker message channel listener");
				                workerToMain.removeEventListener(Event.CHANNEL_MESSAGE, onWorkerToMain);					
						workerListenerCreated = false;
					}
				}
			}

			if (checkThreadsFinished) {
				var res:Boolean = false;
				if (!MT) {	
					res = true;
				} else {
					CONFIG::AIR {
						if (platform == 'ANDROID') {
							res = true;
						} else {
							res = _ctx.call('fw_ffmpeg_canFinish') as Boolean;			
						}
					} 
					CONFIG::FLASCC {
						res = ffmpeg_canFinish() as Boolean;
					}
				}
				// safe to call finish now
				if (res) {
					checkThreadsFinished = false;
					// in AIR we remove this event listener again, in FlasCC we don't remove it, it is removed elsewhere
					CONFIG::AIR {
						removeEventListener(Event.ENTER_FRAME, enterFrame);
					}
					finishInternal();
				}
			}
			if (checkInitThreadFinished) {
				var res:Boolean = false;
				if (!MT) {	
					res = true;
				} else {
					CONFIG::AIR {
						// TODO: Not implemented yet in AIR
						res = _ctx.call('fw_ffmpeg_initFinished') as Boolean;			
					} 
					CONFIG::FLASCC {
						res = ffmpeg_initFinished() as Boolean;
					}
				}
				// safe to call finish now
				if (res) {
					checkInitThreadFinished = false;
					// in AIR we remove this event listener again, in FlasCC we don't remove it, it is removed elsewhere
					CONFIG::AIR {
						removeEventListener(Event.ENTER_FRAME, enterFrame);
					}
					// dispatch event
					startRecording();
					instance.dispatchEvent( new StatusEvent( StatusEvent.STATUS, false, false, "started", ""));
				}
			}
		}

		private function startRecording():void {
			// microphone recording init
			if (recordFlashMicrophone) {
				if (microphone == null) {
					initMic();
				}
				if (!micPermissionAsked) {
					micPermissionAsked = true;
					askMicPermission();
				}
				microphone.addEventListener(SampleDataEvent.SAMPLE_DATA, this.sndDataMic);									
			}
			isRecording = true;
		}

		/**
		 * On iOS, force asking about access to photos (camera roll). 
		 * 
		 * This will display the native dialog aksing access to photos only for the first time. For this time, but also for subsequent calls, 
		 * it will dispatch events informing your app about the status of this permission (whenever or not your app can save the videos to camera roll).
		 * This allows your app to both spawn the first time native dialog at the time of your choosing, but also to remind the user to enable the apps photos permission
		 * in iOS settings, in case you detect it is disabled.
		 * 
		 * @see #STATUS_READY
		 * 
		 */		
		public function iOS_askPhotoPermissions():void {
			CONFIG::AIR {
				if (platform == "IOS") {
					_ctx.call('fw_askPhotoPermissions');	
				}
			}
		}
		
		/**
		 * Load the encoder. After getInstance, always do this. Dispatches STATUS_READY when loaded.
		 * 
		 * In reality, this does nothing on mobile, but in Flash Player, this loads the encoder SWF. So if you want to keep your code
		 * crossplatform include this line and listen for the "ready" event.
		 * 
		 * @param pathToBridge <b>Only matters in Flash, not AIR</b>. Path to the SWF the Flash Player FW is using. 
		 * @see #STATUS_READY
		 * 
		 */
		public function load(pathToBridge:String = ""):void {
			// encoder is ready instantly in the ANE version
			// in FlasCC / Crossbridge **standalone** version this is also true - otherwise the default assumption is FlasCC / Crossbridge version will be loaded from external SWF
			if (platform != "MAC") {
				if (platform != "ANDROID" && platform != "IOS") {
					instance.dispatchEvent( new StatusEvent( StatusEvent.STATUS, false, false, "ready", ""));
				} else {
					a = 0;
					_parent.addEventListener(Event.ENTER_FRAME, onMobileFrame);
				}
			} else {
				// if we're on Mac we need to "fix" the ANE, this is mainly for inside the IDE's which destroy all the symlinks when unzipping the ANE into tmp folders
				//fixANE("com.rainbowcreatures.FWEncoderANE", onFix);
				instance.dispatchEvent( new StatusEvent( StatusEvent.STATUS, false, false, "ready", ""));
			}
		}

		private var captureRectX:int = 0;
		private var captureRectY:int = 0;
		private var captureRectW:int = 0;
		private var captureRectH:int = 0;
		private var captureRectColor:int = 0xFF00FFFF;
		private var captureRectMode:int = 0;
	
		/**
		 * Set capture rectangle on mobile, in realtime.
		 * 
		 * Supported only on mobile(iOS and Android), in non-realtime mode. This must be called before encoder load() method.
		 * After setting the capture rectangle, do not set video dimensions, they will be set automatically.
		 * Also, do not use any arguments in capture method - just use as if you were capturing fullscreen.
		 * 
		 * @param x The top left corner x of the capture rectangle.
		 * @param y The top left corner y of the capture rectangle.
		 * @param w The width of the capture rectangle, in AIR's coordinate system.
		 * @param h The height of the capture rectangle, in AIR's coordinate system. 
		 * @param color The color of the visual markers, when using the visual capture method.
		 * @param mode One of the capture modes, either visual or calculate.
		 * 
		 * @see #CAPTURERECT_MODE_CALCULATE
		 * @see #CAPTURERECT_MODE_VISUAL
		 */

		public function setCaptureRectangle(x:int, y:int, w:int, h:int, color:int = 0xFF00FFFF, mode:int = 0):void {
			if (platform != 'IOS' && platform != 'ANDROID') {				
	     			throwError("setCaptureRectangle is implemented only for iOS / Android. You can capture display objects directly elsewhere (or convert to BitmapData and capture parts of that).");				
			}

			CONFIG::AIR {			

			captureRectX = x;
			captureRectY = y;
			captureRectW = w;
			captureRectH = h;
			captureRectColor = color;
			captureRectMode = mode;
			if (captureRectMode == CAPTURERECT_MODE_VISUAL) {
				// visual mode to measure capture rectangle, add guide pixels
				if (captureRectW > 0 && captureRectH > 0) {
					tempMeasureSprite = new Sprite();
					var bd:BitmapData = new BitmapData(captureRectW + 1, captureRectH + 1, true, 0x00000000);
					bd.setPixel32(0, 0, captureRectColor);
					bd.setPixel32(1, 1, captureRectColor);
					bd.setPixel32(0, 1, captureRectColor);
					bd.setPixel32(1, 0, captureRectColor);
					bd.setPixel32(bd.width - 1, bd.height - 1, captureRectColor);
					bd.setPixel32(bd.width - 2, bd.height - 2, captureRectColor);
					bd.setPixel32(bd.width - 1, bd.height - 2, captureRectColor);
					bd.setPixel32(bd.width - 2, bd.height - 1, captureRectColor);
					var bitmap:Bitmap = new Bitmap(bd);
					tempMeasureSprite.addChild(bitmap);
					tempMeasureSprite.x = captureRectX;
					tempMeasureSprite.y = captureRectY;
					_parent.stage.addChild(tempMeasureSprite);
					_ctx.call('fw_setCaptureRectangle', captureRectX, captureRectY, captureRectW, captureRectH, captureRectColor, captureRectMode);
					//_ctx.call('fw_setCaptureStage', _parent.stage.stageWidth, _parent.stage.stageHeight);	
				}

			} else {
				// we must call the setCaptureRectangle manually here
				_ctx.call('fw_setCaptureRectangle', captureRectX, captureRectY, captureRectW, captureRectH, captureRectColor, captureRectMode);
				_ctx.call('fw_setCaptureStage', _parent.stage.stageWidth, _parent.stage.stageHeight);

			}
			}
		}

		private function measureCaptureRectangle():void {
			CONFIG::AIR {			
			// this will call fw_setCaptureRectangle but internally, after measuing the rectangle from the guide points
			_ctx.call('fw_measureRectStart', captureRectColor);
			tempMeasureSprite.parent.removeChild(tempMeasureSprite);
			captureRectX = 0;
			captureRectY = 0;
			captureRectW = 0;
			captureRectH = 0;
			}
		}

		private function onMobileFrame(e:Event):void {
			if (a == 1) {
			}
			if (a == 5) {
				if (captureRectMode == CAPTURERECT_MODE_VISUAL && captureRectW > 0 && captureRectH > 0) {
					measureCaptureRectangle();
				}
			}
			if (a == 6) {
				instance.dispatchEvent( new StatusEvent( StatusEvent.STATUS, false, false, "ready", ""));
				_parent.removeEventListener(Event.ENTER_FRAME, onMobileFrame);
			}
			a++;
		}
		
		/**
		 * Flash Player only - experimental audio recording mode, which uses clock timestamps mixed with monotonic PTS to record audio.
		 * 
		 * Try this if you're having audio sync difficulties in Flash Player.
		 * 
		 * @param r set the mode to true or false. 
		 * 
		 */
		public function setAudioRealtime(r:Boolean):void {
			var ri:int = 0;
			if (r) {
				ri = 1;
			}
			CONFIG::AIR {			
				FWtrace("Warning: setAudioRealtime not implemented for AIR(you can ignore this if you're not intending to use that method for AIR)");
			}
			CONFIG::FLASCC {
				fw_setAudioRealtime(ri);
			}
		}
		
		/**
		 * Set logging level.
		 * 
		 * @param level the logging level.
		 * @see #LOGGING_BASIC
		 * @see #LOGGING_VERBOSE
		 * 
		 */
		public function setLogging(level:int):void {
			var logging1:Boolean = false;
			var logging2:Boolean = false;
			if (level == LOGGING_BASIC) {
				logging1 = true;
				logging2 = false;
			}
			if (level == LOGGING_VERBOSE) {
				logging1 = true;
				logging2 = true;
			}
			CONFIG::AIR {			
				_ctx.call('fw_setLogging', logging1, logging2);
			}
			CONFIG::FLASCC {
				fw_setLogging(logging1, logging2);
			}
		}
		
		/**
		 * Force PTS calculatin mode.
		 * 
		 * @param ptsMode the pts mode.
		 * 
		 * @see #PTS_AUTO
		 * @see #PTS_MONO
		 * @see #PTS_REALTIME
		 * 
		 */
		public function forcePTSMode(ptsMode:int):void {
			CONFIG::AIR {			
				_ctx.call('fw_setPTSMode', ptsMode);
			}
			CONFIG::FLASCC {
				fw_setPTSMode(ptsMode);
			}
		}

		/**
		 * Force framedrop mode.
		 * 
		 * @param framedropMode the framedrop mode.
		 * 
		 * @see #FRAMEDROP_AUTO
		 * @see #FRAMEDROP_OFF
		 * @see #FRAMEDROP_ON 
		 */
		public function forceFramedropMode(framedropMode:int):void {
			CONFIG::AIR {			
				_ctx.call('fw_setFramedropMode', framedropMode);
			}
			CONFIG::FLASCC {
				fw_setFramedropMode(framedropMode);
			}
		}
				
		private function onFix(extensionID:String, success:Boolean):void {  
     		if (success) {  
     			FWtrace("WARNING: THE INTERNAL SYMLINKS OF FLASHYWRAPPERS ANE WERE AUTOMATICALLY FIXED, BUT YOU WILL NEED TO BUILD & LAUNCH ONCE MORE FOR THE ANE ERROR ABOUT UNDEFINED _ctx.call(..) TO GO AWAY. For Flash CS/CC, use this for clearing the cache: rm -rf /Users/##USERNAME##/Library/Caches/TemporaryItems/Tmp_ANE_File_Unzipped_Packages/.Your release build will work fine, the need to 'fix' only affects IDE.");
          		instance.dispatchEvent( new StatusEvent( StatusEvent.STATUS, false, false, "ready", ""));
     		}  else {
     			throwError("Couldn't fix the symlinks in ANE. If this is a release build you shouldn't see this error!");
     		}
		}  
		
		// status event handler for ANE's
		private function onStatus( event:StatusEvent ):void {

//			FWtrace("Got event from extension, level:" + event.level + ", code: " + event.code);

			// this is for FW running as background worker(for Flash in MT mode or for older AIR versions in MT mode as well)
			// it makes sure to send any status events through worker message channel
			
			if (!Worker.current.isPrimordial && !runningAsWorker) {		
		                workerToMain = Worker.current.getSharedProperty("workerToMain");
				if (workerToMain != null) {
					workerToMain.send(event.level);
				}
			} 

			CONFIG::AIR {			
				if (event.code != 'error') {
					// on iOS, if we get encode, we will fill video_bo before sending out the "encoded" event to our app
					if (platform == 'IOS' || platform == 'MAC' || platform == 'ANDROID') {
						if (event.code == 'encoded') {
							isEncoding = false;
							video_bo.length = 0;
							var inStream:FileStream = new FileStream(); 
		        				inStream.open(mergedFile, FileMode.READ); 
			        			inStream.readBytes(video_bo); 
							inStream.close(); 
							NativeApplication.nativeApplication.executeInBackground = false;
						}
					} 
					dispatchEvent( new StatusEvent( StatusEvent.STATUS, false, false, event.code,  event.level));
					
				} else {
					throwError(event.level);
				}
			}
		}

		private function throwError(text:String):void {
			throw new Error( "[FlashyWrappers error] " + text);
		}
		
		/**
		 * Set video dimensions. 
		 * 
		 * Try to set the dimensions to multiplies of 16, especially on mobile, and maximum Full HD (1920 x 1080).
		 * This is not strictly needed but you'll be warned if you don't set as recommended.
		 * 
		 * @param h video height
		 * @param w video width.
		 * 
		 */
		public function setDimensions(w:Number, h:Number):void {
			if (w % 16 > 0 || h % 16 > 0) {
				FWtrace("It is recommended your video dimensions are multiplies of 16. On some platforms, like iOS, other dimensions might cause issues.");
			}
			if (w > 1920 || h > 1080) {
				FWtrace("Your video dimensions might be too high and cause issues on mobile platforms (for example the video might not be playable or show up in gallery).");
			}
			videoWidth = w;
			videoHeight = h;
		}

		/** 
		 * Set video fps.
		 * 
		 * If fps is lower than stage fps frames will be automatically dropped only if framedrop is on.
		 * 
		 * @param fps frames per second of the video.
		 */
		public function setFps(fps:int):void {
			video_fps = fps;
		}

		/**
		 * Set realtime mode.
		 * 
		 * This affects both PTS calculation and framedrop modes. On mobile it also affects whenever OpenGL capture will be used.
		 * In realtime mode on mobile, OpenGL capture(which is the fastest method to capture AIR content) is used.
		 * 
		 * @param r realtime mode true / false.
		 * 
		 */
		public function setRealtime(r:Boolean):void {
			realtimeSet = true;
			realtime = r;
		}
		
		/** 
		 * Set audio mode. 
		 * 
		 * Warning: Audio is not magically recorded - you need to supply audio data, except for microphone where FW tries to "magically" 
		 * record it using a method depending on platform.
		 * 
		 * @see #AUDIO_OFF
		 * @see #AUDIO_MICROPHONE
		 * @see #AUDIO_MONO
		 * @see #AUDIO_STEREO
		 */
		public function setRecordAudio(mode:String):void {
			recordAudio = mode;
		}	

		/**
		 * Set number of audio channels.
		 * 
		 * @param ch number of channels, usually 1 for mono or 2 for stereo.
		 * 
		 * 
		 */
		private function setAudioChannels(ch:int):void {
			audioChannels = ch;
		}	

		/**
		 * Special method to get Android encoder quirks string. Currently this can return only one value, "quirk_mtk".
		 * If this string is returned, it indicates to you that FW will force recording dimensions to 1280 x 720 if you don't do it yourself.
		 * It is instead recommended to adjust your code to work with this resolution on Android if MTK is detected, until a better solution is found. 
		 * The MTK encoder was found to basically not work reliably in any other resolutions with standard encoder settings. This affects some Lenovo, Meizu
		 * and possibly more devices.
		 * 
		 * 
		 */
		public function android_getEncoderQuirks():String {	
			CONFIG::AIR {
				return _ctx.call('fw_fixAndroidEncoderQuirks', false) as String;
			}
			CONFIG::FLASCC {
				return "";
			}
		}

		/**
		 * Start FW recording.
		 * 
		 * You can currently set many of those params in separate methods - if you do, you don't need to set them again in start.
		 * 
		 * @param fps fps of the video.
		 * @param _recordAudio one of the audio recording modes.
		 * @param _realtime do you wish to recording in realtime.
		 * @param w video width.
		 * @param h video height.
		 * @param bitrate requested video bitrate.
		 * @param audio_sample_rate requested audio sample rate.
		 * @param keyframe_freq keyframe frequency, for advanced users only.
		 * @param frameOffset for desktop and advanced users only, can shift the captured DisplayObject by this offset in the final video.
		 * 
		 * @see #setRealtime()
		 * @see #setDimensions()
		 * @see #setFps()
		 * @see #setAudioChannels()
		 * @see #setRecordAudio()
		 */
		public function start(fps:int = 20, _recordAudio:String = "audioOff", _realtime:Boolean = true, w:Number = 0, h:Number = 0, bitrate:int = 1000000, audio_sample_rate:int = 44100, audio_bit_rate:int = 64000, keyframe_freq:Number = 0, frameOffset:Point = null):void {
		        activeSoundtrack = null;
			addSilence = false;
			isEncoding = false;
			recordFlashMicrophone = false;
			audio = true;
			lastCaptureWidth = 0;
			lastCaptureHeight = 0;

			// if realtime is not set, use the one in start			
			if (!realtimeSet) {				
				realtime = _realtime;												
			} else realtimeSet = false;

			// in non-realtime mode, do not allow capturing rectangles(doesn't have sense, non-realtime mode allows capturing targets and its not prepared for it in native code anyway)
			if (!realtime) {
				captureRectX = 0;
				captureRectY = 0;
				captureRectW = 0;
				captureRectH = 0;
			}

			// if the video resolution was set in start, use that
			if (w != 0 && h != 0) {
				setDimensions(w, h);
			}

			// try to set record audio mode
			if (recordAudio == "") {
				recordAudio = _recordAudio;
			}

			if (platform == 'ANDROID') {
				CONFIG::AIR {
					_ctx.call('fw_setNativeMicrophoneRecording', false);
				}
			}
			
			if (recordAudio == AUDIO_MICROPHONE) {
				// for now, use native microphone recording in Android for microphone
				CONFIG::AIR {
					if (platform == 'ANDROID') {
						_ctx.call('fw_setNativeMicrophoneRecording', true);
					} else {
						recordFlashMicrophone = true;
					}
				}
				CONFIG::FLASCC {
					recordFlashMicrophone = true;
				}
				setAudioChannels(1);
			}

			if (recordAudio == AUDIO_MONO) {
				setAudioChannels(1);
			}

			if (recordAudio == AUDIO_STEREO) {
				setAudioChannels(2);
			}
			
			var audioCapture:int = 0;
			/*if (recordAudio == AUDIO_CAPTURE) {
				setAudioChannels(2);
				if (platform == 'IOS') {
					audioCapture = 1;
				} else {
					FWtrace("Warning: Audio capture not supported on platform " + platform + ", no audio will be captured unless you send audio data to FW.");
				}
			}*/
			
			if (recordAudio == AUDIO_OFF) {
				audio = false;
			}

			if (_parent == null || _parent.stage == null) {
				throwError("Please specify root DisplayObject with stage reference in myEncoder.getInstance(specifyHere). Parent set was: " + _parent);				
			} else {
				// if video_fps is not specified in using setFps, we'll use fps value in start (20 default)
//				stage_fps = _parent.stage.frameRate;
				if (video_fps == 0) video_fps = fps;
			}

			// if width or height are not set override them with stage width and height
			if (videoWidth == 0 || videoHeight == 0) {
				if (platform != 'IOS' && platform != 'ANDROID') {
					if (_parent != null) {
						videoWidth = _parent.stage.stageWidth;
						videoHeight = _parent.stage.stageHeight;				
					}
				} else {
					// only in realtime mode on iOS we set the dimensions to negative, so that the native code will get the dimensions from AIR app dimensions
					if (realtime) {
						videoWidth = -1;
						videoHeight = -1;
					} else {
						// otherwise get the dimensions from stage
						if (_parent != null) {
							videoWidth = _parent.stage.stageWidth;
							videoHeight = _parent.stage.stageHeight;				
						}
					}
				}
			} else {
				// width and height are set.
				// in case we're on iOS, AND set realtime = true, FW forces accelerated capturing which also forces 1024x768 resolution. We should warn about the dimensions of the movie ignored in realtime mode.
				if (platform == 'ANDROID') {
					FWtrace("Warning, dimensions " + videoWidth + " x " + videoHeight + " WILL be ignored because realtime is set to true. In realtime mode on mobile, the OpenGL capture will keep either 1024 x 768 (in standard resolution) or 2048 x 1536 (in high resolution). If you want your captured video to be in different resolutions, you'll have to set realtime mode to false.");
				} 
			}

			if (frameOffset != null) {
				mat.translate(frameOffset.x, frameOffset.y);
			}

			CONFIG::AIR {
				if (platform != 'ANDROID') {
					_ctx.call('fw_ffmpeg_create');
				} 
				if (platform == 'ANDROID') {			
					if (android_getEncoderQuirks() == "quirk_mtk") {
						videoWidth = 1280;
						videoHeight = 720;
					}
				}
			}

//			Upcoming, not stable yet
//			_ctx.call('decoder_create');

			// check if the audio sample rate seems "normal" and warn the user if it isn't
			var validAudioRates:Array = [8000, 11025, 16000, 22050, 32000, 37800, 44056, 44100, 47250, 48000, 50000, 50400, 88200, 96000 ];
			var audioRateFound:Boolean = false;
			for (var a:int = 0; a < validAudioRates.length; a++) {
				if (audio_sample_rate == validAudioRates[a]) {
					audioRateFound = true;
				}
			}
			if (!audioRateFound) FWtrace("Your audio sample rate of " + audio_sample_rate + " is not a common sample, you might be facing issues on some platforms. Try to use any of these: " + validAudioRates.join(", ")); 
			audioRate = audio_sample_rate;

			// setup audio variables
			var _codec_audio:String = "";
			var buffer_freq:Number = 0;

			if (audio) {
				_codec_audio = codec_audio;							
			}

			// if keyframe freq. not set then use fps
			if (keyframe_freq == 0) {
				keyframe_freq = video_fps;
			}

			numFramesTotal = 0;

			// determine which strategy to use for realtime mode based on all platforms
			if (realtime) {	
				if (platform == 'WINDOWS') {
					MT = false;
				}
				if (platform == 'FLASH') {
					MT = true;
				}
				if (platform == 'MAC') {
					MT = false;
				}
				if (platform == 'IOS' || platform == 'ANDROID') {
					MT = false;
					// detect if Direct or GPU mode is set, otherwise fail, because realtime mode doesn't work with CPU
					CONFIG::AIR {						
						var appXML:XML = NativeApplication.nativeApplication.applicationDescriptor;
						var ns:Namespace = appXML.namespace(); 
						var renderMode:String = appXML.ns::initialWindow.ns::renderMode;
                                                if (renderMode != 'direct' && renderMode != 'gpu') {
							throwError("When using realtime mode on mobile, you need to publish with Direct or GPU mode - realtime mode uses OpenGL capture which works only in these 2 AIR render modes.");
						}
					}
				}
			} else {
				// IMPORTANT FIX!!
				MT = false;

				// Android needs HW accelerated modes on even in non-realtime
				if (platform == 'ANDROID') {
					// detect if Direct or GPU mode is set, otherwise fail, because realtime mode doesn't work with CPU
					CONFIG::AIR {						
						var appXML:XML = NativeApplication.nativeApplication.applicationDescriptor;
						var ns:Namespace = appXML.namespace(); 
						var renderMode:String = appXML.ns::initialWindow.ns::renderMode;
                                                if (renderMode != 'direct' && renderMode != 'gpu') {
							throwError("On Android, you need to publish with Direct or GPU mode.");
						}
					}
				}
			}

			// setup multithreading buffer flushing frequency
			if (MT) {
				buffer_freq = video_fps;
			}
				
			// if we're calling init again, dispose the old bitmapData			
			if (b != null) {
				b.dispose();
				b = null;
			}
			if (bTemp != null) {
				bTemp.dispose();
				bTemp = null;
			}
		
			if (platform == 'ANDROID') {
				CONFIG::DEMO {
					_parent.addChild(lgBMP);
					_ctx.call('fw_setLogo', lgBMP.bitmapData.getPixels(new Rectangle(0, 0, lgBMP.width, lgBMP.height)));
				}
			}

			// for fullscreen mode, capturing doesn't go through BitmapData at all, don't create them
			if (videoWidth != -1 && videoHeight != -1) {
				// create temp bitmapdata
				b = new BitmapData(videoWidth, videoHeight, false);				
			} else {
				// fullscreen supported only on iOS for now, throw error if w / h = -1 on other platforms
				if (platform != "IOS" && platform != "MAC" && platform != "ANDROID") {
					throwError("fullscreen initialization with dimensions set to -1 is not supported on platform " + platform + ". Please wrap your init into platform dependant code (see examples / manual) and init with -1 only for supported platforms.");
				}
			}
		
			// init
			if (platform != "IOS" && platform != "MAC" && platform != "ANDROID") {
				CONFIG::AIR {
					_ctx.call('fw_ffmpeg_init', codec_container, codec_video, _codec_audio, videoWidth, videoHeight, video_fps, 0, bitrate, 0, keyframe_freq, audio_sample_rate, audioChannels, audio_bit_rate, buffer_freq, int(realtime), int(audio));
					startRecording();
					instance.dispatchEvent( new StatusEvent( StatusEvent.STATUS, false, false, "started", ""));
				} 
				CONFIG::FLASCC {
					ffmpeg_init(codec_container, codec_video, _codec_audio, videoWidth, videoHeight, video_fps, 0, bitrate, 0, keyframe_freq, audio_sample_rate, audioChannels, audio_bit_rate, buffer_freq, int(realtime), int(audio));
					checkInitThreadFinished = true;
				}
			} else {
				CONFIG::AIR {
					// on iOS, we need to deal with the temp files created by AVFoundation
					if (platform == 'IOS' || platform == 'MAC') {
						var appDir:File = File.applicationStorageDirectory;
						var fileString:String = appDir.nativePath;
						appDir.preventBackup = true;
						videoFilePath = fileString + "/video.mp4";
						mergedFilePath = fileString + "/" + mobileFilename;
						videoFile = appDir.resolvePath(videoFilePath);
						mergedFile = appDir.resolvePath(mergedFilePath);
						if (videoFile.exists) videoFile.deleteFile();
						if (mergedFile.exists) mergedFile.deleteFile();
						 	
						_ctx.call('fw_ffmpeg_init', videoFilePath, videoWidth, videoHeight, 1, video_fps, iOS_nativeQuality, bitrate, keyframe_freq, buffer_freq, stage_fps, iOS_recordToWAV, audio_sample_rate, audioChannels, audio_bit_rate, int(realtime), int(audio), audioCapture);
					
						// Init temp WAV stream	- not anymore, we can do audio natively now
						if (iOS_recordToWAV) {
				        		audioFilePath = fileString + "/audio.wav";
							FWtrace("Creating temp audio file " + audioFilePath);
				    			audioFile = appDir.resolvePath(audioFilePath);
				        		if (audioFile.exists) {
				        		    audioFile.deleteFile();
				        		}
							audioLength = 0;
				        		audioStream = new FileStream();			
			        			audioStream.open(audioFile, FileMode.WRITE);		
							audioStream.endian = Endian.LITTLE_ENDIAN;			
							writeHeaders(audioChannels, 16, audioRate);			
						}
					}
					
					if (platform == 'ANDROID') {
						mergedFilePath = android_getVideoPath();
						mergedFile = new File(android_getVideoPath());
						FWtrace("Merged file path: " + mergedFilePath);
						if (mergedFile.exists) {
							FWtrace("Erasing old merged file");
							mergedFile.deleteFile();
						}
						_ctx.call('fw_ffmpeg_init', videoWidth, videoHeight, video_fps, bitrate, stage_fps, mergedFilePath, audio_sample_rate, audioChannels, int(_realtime), int(audio));						
					}

					startRecording();
					instance.dispatchEvent( new StatusEvent( StatusEvent.STATUS, false, false, "started", ""));
				} 
			}

		}

		private function android_getVideoPath():String {
			CONFIG::AIR {
				return (_ctx.call('fw_getAndroidVideoPath') as String) + mobileFilename;
			}
			return "";
		}
		
		// write WAV header
		private function writeHeaders( channels:int=2, bits:int=16, rate:int=44100 ):void {  
			CONFIG::AIR {
				var bytes:ByteArray = new ByteArray();  
			    	bytes.length = 0;  
			    	bytes.endian = Endian.LITTLE_ENDIAN;    
			    	bytes.writeUTFBytes( "RIFF" );   // 4
			    	bytes.writeInt( uint( 0 + 44 ) );  // 4
			    	bytes.writeUTFBytes( "WAVE" );  // 4
		        	bytes.writeUTFBytes( "fmt " );  // 4
			    	bytes.writeInt( uint( 16 ) );  // 4
			    	bytes.writeShort( uint( 1 ) );  // 2
			    	bytes.writeShort( channels );  // 2
			    	bytes.writeInt( rate );  // 4
			    	bytes.writeInt( uint( rate * channels * ( bits >> 3 ) ) );   // 4
			    	bytes.writeShort( uint( channels * ( bits >> 3 ) ) );  // 2
			    	bytes.writeShort( bits );   // 2
			    	bytes.writeUTFBytes( "data" );  // 4
			    	bytes.writeInt( 0 );  // 4
				audioStream.writeBytes(bytes, 0, bytes.length);
			}
		}  

		// close the WAV and write the audio data size into the header
		private function finishAudio():void {			
			CONFIG::AIR {
				audioStream.position = 4;
				audioStream.writeInt(uint(audioLength + 44));
				audioStream.position = 40;
				audioStream.writeInt(audioLength);
				audioStream.close();
			}
		}
				
		/**
		 * Add ARGB frame ByteArray to the video encoder.
		 * 
		 * This is the lowest level way to add video frames. It is not recommended to use it on mobile, as that will cause a 
		 * pixels to texture upload(which is pretty slow).
		 * 
		 * @param pixels the ARGB pixels you wish to send to the encoder.
		 * 
		 */
		public function addVideoFrame(pixels:ByteArray):void {
			if (isRecording) {
				pixels.position = 0;
				var frameAdded:int = 0;
				if (!realtime && (pixels.length > videoWidth * videoHeight * 4)) {
					throwError("You are adding video frames larger than dimensions of the video - in case of addVideoFrame you must make sure the scaling of your frames corresponds to the video dimensions.");
				}
				CONFIG::AIR {
					frameAdded = _ctx.call('fw_ffmpeg_addVideoFrame', pixels) as int;   
				} 
				CONFIG::FLASCC {
					frameAdded = ffmpeg_addVideoData(pixels);
					var blah:int = 0;
				}
				// the frame might have been skipped to compensate for fps differences...
				if (frameAdded) numFramesTotal++;
				setTotalFrames(numFramesTotal);
			
				if (activeSoundtrack != null) {
					activeSoundtrack.extract(activeSoundtrackByteArray, (Math.round(audioRate / video_fps)));
					if (activeSoundtrackByteArray.length == 0) {
						FWtrace("activeSoundtrack finished, stopped adding soundtrack");
						activeSoundtrack = null;
						// after finishing with adding soundtrack we must keep adding silence so some encoders won't freak out in case we're still adding video frames
						// for example Windows might start lagging etc.
						addSilence = true;
						activeSoundtrackByteArray.length = 0;
						activeSoundtrackByteArray.length = (Math.round(audioRate / video_fps)) * 16;	
					} else {
						activeSoundtrackByteArray.position = 0;
						addAudioFrame(activeSoundtrackByteArray);
						activeSoundtrackByteArray.position = 0;
						activeSoundtrackByteArray.length = 0;
					}
				} else {
					if (addSilence) {
						activeSoundtrackByteArray.position = 0;
						addAudioFrame(activeSoundtrackByteArray);
						activeSoundtrackByteArray.position = 0;
					}
				}
			} else {
				throwError("You're trying to add video frames but you're not recording! Make sure you started the encoder with init and you're not trying to record after finishing.");
			}
		}

		/**
		 * Add 32-bit floating PCM audio data to encoder.
		 * 
		 * This is the lowest level to add audio data to the encoder. 
		 * 
		 * @param data the 32-bit floating PCM audio data ByteArray. Currently there is no size limit on the data, FW tries to handle this by feeding it to the encoder on another thread, on some platforms.
		 * 
		 */
		public function addAudioFrame(data:ByteArray):void {
			if (isRecording) {				
				data.position = 0;
				if (data.length == 0) throwError("The audio data you're sending is empty! Please make sure to send valid audio data, the device would have exploded if we didn't catch it here.");
				if (platform != "IOS" && platform != "MAC") {
					CONFIG::AIR {
						_ctx.call('fw_ffmpeg_addAudioFrame', data);   
					}
					CONFIG::FLASCC {
						ffmpeg_addAudioData(data);   						
					}
				} else {
					CONFIG::AIR {
						if (iOS_recordToWAV) {
							// on iOS, audio is being written to temp WAV track file
							var bytes:ByteArray = new ByteArray();
							bytes.length = data.length / 2;			
							_ctx.call('fw_processByteArrayAudio', data, bytes);
							audioStream.writeBytes(bytes, 0, bytes.length);
							audioLength += bytes.length;
						} else {
							_ctx.call('fw_ffmpeg_addAudioFrame', data);
						}
					}
				}
			} else {
				throwError("You're trying to add audio frames but you're not recording! Make sure you started the encoder with init and you're not trying to record after finishing.");
			}
		}

		/**
		 * Add 16-bit shorts to encoder.
		 * 
		 * This is only useful for Android, on all other platforms FW expects 32-bit PCM! Actually, Android also accepts 32-bit floats 
		 * through addAudioData, then converts those to 16-bit shorts, which takes some extra time.  
		 * FWSoundMixer uses this method to add shorts directly on Android.
		 *  
		 * @param data the audio data composed of shorts
		 * 
		 */
		public function addAudioFrameShorts(data:ByteArray):void {
			if (isRecording) {				
				data.position = 0;
				if (platform == "ANDROID") {
					CONFIG::AIR {
					_ctx.call('fw_ffmpeg_addAudioFrameShorts', data);   
					}
				} else {
					FWtrace('addAudioFrameShorts not implemented or needed for anything else than Android. Please use addAudioFrame.');
				}
			} else {
				throwError("You're trying to add audio frames but you're not recording! Make sure you started the encoder with init and you're not trying to record after finishing.");
			}
		}
		
		private function setTotalFrames(count:int):void {			
			// 2.5: Allow this only in non-MT mode on desktop now, where it's still needed. Otherwise FW is handling the total number of frames monitoring automatically
			if (platform != "IOS" && platform != "MAC" && platform != "ANDROID") {
				numFramesTotal = count;
				CONFIG::AIR {
					_ctx.call('fw_ffmpeg_setFrames', count);

				}				
				CONFIG::FLASCC {				
					ffmpeg_setFrames(count);		
				}

			}
		}

		/**
		 * Add soundtrack to the video (typically mp3). Only safe in non-realtime mode, for realtime mode audio mixing use FWSoundMixer.
		 * 
		 * Internally, this will cause "extract" method to be run which might be slow (it could even lag your app for a bit).
		 * 
		 * @param s the Sound instance you wish to add.  
		 * 
		 */ 
		public function addSoundtrack(s:Sound):void {
			if (isRecording) {
				if (audio) {
					if (realtime) {
						FWtrace("[WARNING] Using addSoundtrack in realtime mode is not recommended. This method doesn't synchronize when frames are dropped in realtime mode. If you want to use realtime mode and add a soundtrack with everything being synchronized, use FWSoundMixer.");
					}
					trace("bytes: " + s.bytesTotal);
					if (s.bytesTotal > 1024 * 1024 * 5) {
						FWtrace("Sound > 5 MB, will use extract during capture.");
						activeSoundtrack = s;
					} else {
						var rawAudioBytes:ByteArray = new ByteArray();
						rawAudioBytes.endian = Endian.LITTLE_ENDIAN;
						s.extract(rawAudioBytes, (s.length / 1000) * 44100);
						rawAudioBytes.position = 0;
						addAudioFrame(rawAudioBytes);
						rawAudioBytes.length = 0;
					}
				} else {
					throwError("[FlashyWrappers error] addSountrack called but you've set audio to false in init. Please set audio to true in init."); 
				}
			} else {
				throwError("You're trying to add soundtrack too soon - this should be done only after recieving 'started' event from FlashyWrappers encoder");
			}
		}

		/**
		 * Finish the recording. This will cause STATUS_FINISHED to be fired after finishing.
		 * 
		 * @see #STATUS_FINISHED
		 * 
		 */
		public function finish():void {
			if (isRecording) {
				if (recordFlashMicrophone) {
					microphone.removeEventListener(SampleDataEvent.SAMPLE_DATA, sndDataMic);									
					recordFlashMicrophone = false;
				}
				checkThreadsFinished = true;
				// in AIR we must add the enter frame listener, in FlasCC it has been already added
				CONFIG::AIR {
					addEventListener(Event.ENTER_FRAME, enterFrame);
				}
			} else {
				throwError("Calling finish but you didn't start recording.");
			}
		}

		/**
		 * Get the ByteArray containing the encoded video.
		 * 
		 * @return the video. 
		 */
		public function getVideo():ByteArray {
			return video_bo;
		}

		/**
		 *
		 * Play the last recorded video using one of the playing methods depending on the platform.
		 * It places the container on the coordinates you specify, in the dimensions you specify and performs any scaling on the video.
		 * 
		 * This will automatically select the best playing method based on the current platform. Only works for MP4 files(not OGV).
		 * In Flash, this tries to play the file directly from ByteArray by stripping away MP4 container, replacing it with FLV container. This might have small issues, but should work in general. 
 		 * On mobile, the video is replayed from the last recorded temporary file, using VideoTexture.
		 * On desktop, the video is replayed from the last recorded temporary file, using Video.
		 */
		public function playVideo(x:Number, y:Number, width:Number, height:Number, useVideoTexture:Boolean = false, videoContext3D:Context3D = null):void {
			if (_parent != null && _parent.stage != null) {
				try {
				CONFIG::AIR {
					var i:int = 0;
					// where is the temp video file?
					var filePath:String = "";
					// on Windows
					if (platform == 'WINDOWS') {
						// if we didn't specify saving straight to file, it will be in ByteArray, so we'll have to write this temp video
						if (saveToFileWin == "") {
							var bo:ByteArray = getVideo();
			   				var file:File = File.applicationStorageDirectory.resolvePath("video_merged.mp4");
							FWtrace('Writing temp video for replay: ' + file.nativePath + '...');
		    					var fileStream:FileStream = new FileStream();
		    					fileStream.open(file, FileMode.WRITE);
							fileStream.writeBytes(bo, 0, bo.length);
		    					fileStream.close();
							filePath = file.nativePath;
						} else {
							// if we specified saving straight to file, its there already
							filePath = saveToFileWin;
						}
					} else {
						// for all other AIR platforms(Android, iOS, Mac) we are already using temp file called "mergedFile", so get its path
						filePath = mergedFile.nativePath;
					}
					var player:MP4Player = new MP4Player();
					// now, play the video either using video texture or not
					if (!useVideoTexture) {
						FWtrace('NOT using videoTexture for replay...');
						var v:Video = player.playVideo(filePath);
						v.x = x;
						v.y = y;
						v.width = width;
						v.height = height;					
						_parent.stage.addChild(v);
					} else {
						FWtrace('Using videoTexture for replay...');
						player.textureX = x;
						player.textureY = y;
						player.textureW = width;
						player.textureH = height;
						if (videoContext3D != null) {
							FWtrace('Supplying context3D...');
							player.context3D = videoContext3D;
							player.playVideoTexture(filePath);
						} else {			
							FWtrace('Context3D not supplied, creating one...');
							player.createContext3D(_parent.stage, function():void {
								FWtrace('Done, playing video in videoTexture...');
								player.playVideoTexture(filePath);
							});
						}
					}
				}	
				CONFIG::FLASCC {
					// in case of Flash we only have the mp4 in ByteArray, so must try to replay from that
					var player:FLVByteArrayPlayer = new FLVByteArrayPlayer();
					var v:Video = player.playVideo(video_bo);
					v.x = x;
					v.y = y;
					v.width = width;
					v.height = height;					
					_parent.stage.addChild(v);
				}
				} catch (error:Error) {
					FWtrace("Error while trying to replay video.");
					throw error;
				}
			} else {
				throwError("playVideo needs access to stage, please make sure myEncoder.getInstance(parent) 'parent' has access to stage(stage is not null).");	
			}
		}

		private function saveVideo(filename:String):void {
			CONFIG::AIR {
				if (platform == 'WINDOWS') {
					_ctx.call('winAPI_saveStream', filename);
				} else {
					if (platform == 'MAC') {
						var bo:ByteArray = getVideo();
			   			var file:File = File.userDirectory.resolvePath(filename);
		    				var fileStream:FileStream = new FileStream();
		    				fileStream.open(file, FileMode.WRITE);
	   					fileStream.writeBytes(bo, 0, bo.length);
		    				fileStream.close();
						bo.length = 0;
					} else {
						trace("saveVideo not implemented on platform " + platform);
					}
				} 			
			}
		}
						
		private function finishInternal():void {
			FWtrace("finishInternal called");
			if (isRecording) {
				isRecording = false;
				// IMPORTANT: reset some values when finishing
				recordAudio = "";
				videoWidth = 0;
				videoHeight = 0;
				video_fps = 0;

				if (platform != "IOS" && platform != "MAC") {
					if (platform != 'ANDROID') {
						// *** WINDOWS, FLASH
						// ******************
						video_bo.length = 0;
						CONFIG::AIR {				
							if (saveToFileWin != "") {
								saveVideo(saveToFileWin);
							} else {
								// get the size of the result first
								var size:Number = getStreamSize();
								FWtrace("Size of video bytes: " + size);
								for (var a:int = 0; a < size; a++) video_bo.writeByte(0);					
								video_bo.position = 0
								// write the stream into the bytearray
								writeStream(video_bo);
							}
							// IMPORTANT call free just before dispatching "encoded" event. There was a bug which caused the _free dispatched a bit later sometimes and then it could happen that we started
							// new encoding (created new class instance), and THEN _free was called (still in this method) which screwed up the instance (set to NULL).
							_ctx.call('fw_ffmpeg_free');			
						}
						CONFIG::FLASCC {
							video_bo = ffmpeg_encodeit();
						}
						video_bo.position = 0;
						instance.dispatchEvent( new StatusEvent( StatusEvent.STATUS, false, false, "encoded", ""));
					} else {
						// *** ANDROID
						// ***********
						CONFIG::AIR {
							_ctx.call('fw_ffmpeg_free');
							// read the temp file, it should be available
							if (platform == 'ANDROID') {
								CONFIG::DEMO {
									lgBMP.parent.removeChild(lgBMP);
								}
								// force redrawing of the stage after encoding
								if (_parent != null) {
									_parent.stage.invalidate();
								}
								isEncoding = true;
							}
						}
					}
				} else {
					// *** IOS, MAC
					// ************

					CONFIG::AIR {				
						// hand control back to AIR for rendering
						_ctx.call("fw_bindFlashFBO");
						// finish audio, video will be finished at the start of encodeIt call
						if (iOS_recordToWAV) {
							finishAudio();				
						}
						isEncoding = true;
						FWtrace("merging mp4, tracks " + videoFilePath + " and " + audioFilePath + ": " + mergedFilePath); 								
						_ctx.call('fw_finish');	
						_ctx.call('fw_ffmpeg_getStream', videoFilePath, audioFilePath, mergedFilePath);	
						// TODO determine best place for free in this case yet (to avoid the bug above)
						_ctx.call('fw_ffmpeg_free');
					}
				}
			} else {
				throwError("You're trying to call stop() but you didn't start recording.");
			}
		}
		
		
		/**
		 * Get encoding progress.
		 * 
		 * @return returns a number from 0 to 1 indicating the encoding progress. 
		 * 
		 */
		public function getEncodingProgress():Number {
			if (isRecording) {
				CONFIG::AIR {
					if (platform != 'ANDROID') {
						return (_ctx.call('fw_ffmpeg_getVideoFramesSent') as Number) / numFramesTotal;			
					} else {
						return 1;
					}
				} 
				CONFIG::FLASCC {			
					return ffmpeg_getVideoFramesSent() / numFramesTotal;
				}
			}
			if (isEncoding && platform == 'IOS') {
				CONFIG::AIR {
					return _ctx.call('fw_getExportProgress') as Number;
				}
			}	
			return 1;	
		}                                           

		private function writeStream(b:ByteArray):void {
			CONFIG::AIR {
				_ctx.call('fw_ffmpeg_getStream', b);
			}
		}

		private function getStreamSize():Number {
			CONFIG::AIR {
				return _ctx.call('fw_ffmpeg_getStreamSize') as Number;
			} 
			CONFIG::FLASCC {
				return 0;
			}
		}
		
		/** 
		 * Forcibly dispose the ANE.
		 * 
		 * Do not use this in case you want to use the extension again. It already tries to deallocate everything after finishing
		 * each recording, so you don't need to call dispose to do that.
		 * 
		 */
		public function dispose():void {
			CONFIG::AIR {
				_ctx.dispose();
			}
		}

		/**
		 * Automatically capture AIR stage content, Stage3D and/or display object.
		 * <br>
		 * This tries to recognize which target is being captured and then selects the appropriate method to capture it. 
		 * <br>
		 * <li>On mobile realtime, if you do not specify any target, FW will capture both Stage3D + display objects fullscreen using OpenGL (fast).</li>
		 * <li>On mobile realtime, if you specify target DisplayObject(or Context3D), FW will refuse to capture. The recommended way is to capture a rectangle instead.</li>
		 * <li>On mobile non-realtime, if you do not specify any target, FW will capture fullscreen using addVideoFrame and scaling (slower).</li>
		 * <li>On mobile non-realtime, if you specify target, FW will capture only the DisplayObject using addVideoFrame and scaling (slower).</li>
		 * <br>
		 * <li>On desktop realtime and non-realtime, if you do not specify any target, FW will capture fullscreen using addVideoFrame and scaling, it <b>won't</b> capture additional layers such as Stage3D.</li>
		 * <li>On desktop realtime and non-realtime, if you specify target DisplayObject(or Context3D), FW will capture only that target.</li>
		 * <br>
		 * Note that on desktop you can still capture both Stage3D + display objects on top, but you'll need to call capture twice - once with Context3D target, another time without arguments to capture the Flash stage. You must also enable stage3DWithMC to make sure the frame is not sent to the encoder after capturing Stage3D but only after the subsequent capture() call, where you capture the Flash stage on top.
		 * 
		 * @param target the target to capture, this can be null(the default), which means fullscreen, DisplayObject or Stage3D.    
		 * @see #stage3DWithMC
		 * 
		 */
		public function capture(target:* = null):void {
			if (isRecording) {
				if ((platform == 'IOS' || platform == 'ANDROID') && realtime) {
					// in case we're capturing fullscreen on mobile, use the OpenGL capture					
					numFramesTotal++;
					if (target == null) {
						CONFIG::AIR {
							_ctx.call('fw_captureFrame', false);
						}
					} else {
						throwError('You cannot capture target in realtime mode on mobile. See setCaptureRectangle if trying to capture part of screen.');
						return;													
					}
				} else {
					var mode:int = -1;
					if (target == null) {
						mode = 0;
					} else {
						if (target is DisplayObject) {
							mode = 1;
							
						}
						if (target is Context3D) {
							mode = 2;
							if (platform == 'ANDROID') {
								throwError('You cannot capture Stage3D in non-realtime mode on mobile. Capture fullscreen with no arguments, this will capture the whole AIR stage, including Stage3D (there\'s currently no other way).');
								return;								
							}
						}
					}
					if (mode == -1) {
						FWtrace('Unsupported target in capture, use either null to capture the whole stage, DisplayObject or Context3D');
						return;
					}

					// capturing DisplayObject or stage (target is null)
					// clear the frame Bitmap assuming the captured DisplayObject might be transparent, so we want to "clean up" under it
					// do not clean when capturing Context3D
					// do not clean when trying to capture Context3D with MC on top(otherwise capturing MC with clean erases the underlying Context3D layer)
					if (clearFrame && !stage3DWithMC && mode != 2) b.fillRect(b.rect, 0x000000);				

					// if no DisplayObject is specified we will capture the whole stage
					if (mode == 0) target = _parent.stage;
					if (mode == 0) {
						targetWidth = target.stageWidth;
						targetHeight = target.stageHeight;
					} else {	
						if (mode == 1) {
							targetWidth = target.width;
							targetHeight = target.height;
						} else {
							targetWidth = target.backBufferWidth;
							targetHeight = target.backBufferHeight;
						}
					}

					// recreate scale matrix in case we are capturing a different DisplayObject
					if (targetWidth != lastCaptureWidth && targetHeight != lastCaptureHeight) {
						// if we're in Context3D mode, create temp bitmap for it with the right size
						if (mode == 2) {
							if (bTemp != null) { bTemp.dispose(); bTemp = null}
							// for Context3D which renders everything in full width/height
							bTemp = new BitmapData(targetWidth, targetHeight, false);	
						}

						// create the frame transform matrix based on the scale and offset params
						mat = new Matrix();
				    		mat.scale(videoWidth / targetWidth, videoHeight / targetHeight);			
					}

					lastCaptureWidth = targetWidth;
					lastCaptureHeight = targetHeight;

					if (mode < 2) {
						// for DisplayObject, render it straight to bitmap, using the transforms we computed (ie. scale down to fit the video basically)
						b.draw(target, mat);
						flushFrame();			
					} else {
						// for Context3D, first render the full content into the temp bitmap we created above						
						target.drawToBitmapData(bTemp);
						// then scale this bitmap into the final composition 
						b.draw(bTemp, mat);			
						if (!stage3DWithMC) flushFrame();
					}
				} 								
			}
		}

		private function flushFrame():void {
			ba.position = 0;
			b.copyPixelsToByteArray(b.rect, ba);
			ba.position = 0;
			addVideoFrame(ba);			
		}

		[Deprecated("Use capture() method instead with DisplayObject as argument")] 
		public function captureMC(MC:DisplayObject):void {
			if (isRecording) {
				b.draw(MC, mat);
				flushFrame();			
			}			
		}

		[Deprecated("Use capture() method instead with Context3D as argument")] 
		public function captureStage3D(c:Context3D, flushToEncoder:Boolean = true):void {
			capture(c);
		}

		// iOS specific methods
		// ====================

		/**
		 * Only for iOS - alternative to addSoundtrack. 
		 * 
		 * This has the advantage of not using "extract" method which is very slow. It mixes in mp3 in the post-processing phase.
		 * 
		 * @param filename the name of the mp3 we want to mix in.
		 * @param duration duration of the mp3 in seconds (currently needed)
		 * 
		 */		
		public function iOS_startAudioMix(filename:String, duration:Number):void {
			if (platform == "IOS" || platform == "MAC") {
				CONFIG::AIR {			
					if (isRecording) {
						// assume the files (mp3, wav etc) for mixing are in the app’s directory
						var appDir:File = File.applicationDirectory;					
						var fileString:String = appDir.nativePath;					
						var audioMixPath:String = fileString + "/" + filename;				
						_ctx.call('fw_addAudioMix', audioMixPath, duration);			
					}
				}
			} else {
				FWtrace("iOS_startAudioMix not supported on platform " + platform);			
			}
		}
		
				
		/**
		 * Only for iOS - stop audio mix added before.
		 * 
		 * This can be used if you don't know when you want to stop the audio mix. At the time of 
		 * calling this method the mix will be stopped.
		 * 
		 */ 
		public function iOS_stopAudioMix(filename:String):void {
			if (platform == "IOS" || platform == "MAC") {
				CONFIG::AIR {						
					if (isRecording) {
						// assume the files (mp3, wav etc) for mixing are in the app’s directory
						var appDir:File = File.applicationDirectory;						
						var fileString:String = appDir.nativePath;					
						var audioMixPath:String = fileString + "/" + filename;				
						_ctx.call('fw_stopAudioMix', audioMixPath);
					}			
				}	
			} else {
				FWtrace("iOS_stopAudioMix not supported on platform " + platform);			
			}		
		}
		
		[Deprecated("Use saveToGallery() method instead")] 
		public function iOS_saveToCameraRoll(path:String = ""):void {
			FWtrace("saveToCameraRoll");
			var videoPath:String = "";
			// by default, use video_merged.mp4 on iOS
			if (path == "") videoPath = mergedFilePath; else videoPath = path;
			if (platform == "IOS") {
				FWtrace("Saving " + videoPath + " to camera roll ...");
				CONFIG::AIR {						
					_ctx.call('fw_saveToCameraRoll', videoPath, "");
				}
			} else {
				FWtrace("iOS_saveToCameraRoll not supported on platform " + platform);
			}
		}
		
		private function iOS_setHighresRecording(b:Boolean = false):void {
		if (platform == "IOS") {
				CONFIG::AIR {	
					var bInt:int = 0;	
					if (b) bInt = 1;			
					_ctx.call('fw_setHighresRecording', bInt);
				}
			} else {
				FWtrace("iOS_* not supported on platform " + platform);
			} 
			
		}
			
		private function iOS_ReplayKitAvailable():int {
			if (platform == "IOS") {
				CONFIG::AIR {						
					return _ctx.call('fw_ReplayKitAvailable') as int;
				}
			} else {
				FWtrace("iOS_ReplayKit* not supported on platform " + platform);
			} 
			return 0;
		}
    	private function iOS_ReplayKitStart(recordMicrophone:int = 0):int {
    		if (platform == "IOS") {
				CONFIG::AIR {						
					return _ctx.call('fw_ReplayKitStart', recordMicrophone) as int;
				}
			} else {
				FWtrace("iOS_ReplayKit* not supported on platform " + platform);
			} 	
			return 0;
    	}
  	  	private function iOS_ReplayKitIsRecording():int {
  	  		if (platform == "IOS") {
				CONFIG::AIR {						
					return _ctx.call('fw_ReplayKitIsRecording') as int;
				}
			} else {
				FWtrace("iOS_ReplayKit* not supported on platform " + platform);
			} 
			return 0;
  	  	}
    	private function iOS_ReplayKitStop():int {
    		if (platform == "IOS") {
				CONFIG::AIR {						
					return _ctx.call('fw_ReplayKitStop') as int;
				}
			} else {
				FWtrace("iOS_ReplayKit* not supported on platform " + platform);
			} 
			return 0;
    	}
   		private function iOS_ReplayKitDiscard():int {
   			if (platform == "IOS") {
				CONFIG::AIR {						
					return _ctx.call('fw_ReplayKitDiscard') as int;
				}
			} else {
				FWtrace("iOS_ReplayKit* not supported on platform " + platform);
			} 
			return 0;
   		}
    	private function iOS_ReplayKitPreview():int {
    		if (platform == "IOS") {
				CONFIG::AIR {						
					return _ctx.call('fw_ReplayKitPreview') as int;
				}
			} else {
				FWtrace("iOS_ReplayKit* not supported on platform " + platform);
			} 
			return 0;
    	}

		/** 
		 * Save video to gallery / camera roll. Only for mobile.
		 * 
		 * @param filename the internal video filename, if left empty it will use "video.mp4".
		 * @param album the album name, if left empty it will use the default album / no album.
		 * 
		 */
		public function saveToGallery(filename:String = "", album:String = ""):void {
			CONFIG::AIR {
			if (platform == "IOS") {
				var videoPath:String = "";
				if (filename == "") {
					videoPath = mergedFilePath; 
				} else {
					var src:File = File.applicationStorageDirectory.resolvePath(mobileFilename);
					var dst:File = File.applicationStorageDirectory.resolvePath(filename);
					
					try {
						src.copyTo(dst, true);
					} catch (error:Error) {
						FWtrace("Error while copying file to the final destination for gallery.");
					}
	
					videoPath = dst.nativePath;
				}
				FWtrace("Saving " + videoPath + " to camera roll ...");
				CONFIG::AIR {						
					_ctx.call('fw_saveToCameraRoll', videoPath, album);
				}

			} 
			if (platform == "ANDROID") {
				if (filename == "") filename = "video.mp4";
				FWtrace("Saving " + filename + " to gallery ...");
				CONFIG::AIR {						
					_ctx.call('fw_saveToGallery', album, filename, mergedFilePath);				
				}
			}	
			if (platform == "MAC" || platform == "WINDOWS" || platform == "FLASH") {
				FWtrace("saveToGallery not implemented for platform " + platform);
			}		
			}
		}
				
		[Deprecated("Use capture() method instead null as argument")] 
		public function iOS_captureFullscreen(waitForRendering:Boolean = false):void {
			if (platform == "IOS" || platform == "MAC") {
				if (isRecording) { 
					numFramesTotal++;
					CONFIG::AIR {												
						_ctx.call('fw_captureFrame', waitForRendering);
					}
				} else {
					throwError("You're trying to capture frames on iOS but you didn't setup the encoder! Please use myEncoder.init(...) to start recording first.");
				}
			} else {
				if (!fullscreenErrorReported) {
					fullscreenErrorReported = true;
					FWtrace("iOS_captureFullscreen not supported on platform " + platform);
				}
			}
		}
					
		CONFIG::FLASCC {			
		public function write(fd:int, buf:int, nbyte:int, errno_ptr:int):int
		{
			var str:String = com.fw_flascc.CModule.readString(buf, nbyte);
			trace(str);
			return nbyte;
		}

		public function read(fd:int, buf:int, nbyte:int, errno_ptr:int):int
		{
			return 0;
		}

		public function fcntl(fd:int, com:int, data:int, errnoPtr:int):int
		{
			return 0;
		}

		public function ioctl(fd:int, com:int, data:int, errnoPtr:int):int
		{
			return 0;
		}

		}

	}	
}