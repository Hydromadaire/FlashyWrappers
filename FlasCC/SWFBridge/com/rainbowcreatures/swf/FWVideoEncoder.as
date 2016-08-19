/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * This file "simulates" the FW interface and calls into the SWF for the methods.
 * For example: myEncoder.setFps(fps);
 * Where myEncoder is really reference to the SWFBridge SWF file, setFps is the method we are calling from inside the SWF file.
 *
 */

package com.rainbowcreatures.swf {

	import flash.display.LoaderInfo;
	import flash.display.Loader;
	import flash.net.URLLoader;
	import flash.utils.ByteArray;
	import flash.net.URLLoaderDataFormat;
	import flash.net.URLRequest;
	import flash.display.MovieClip;
	import flash.display.Sprite;
	import flash.display.DisplayObject;
	import flash.events.EventDispatcher;
	import flash.events.IOErrorEvent;		
	import flash.events.StatusEvent;
	import flash.events.Event;
	import flash.display3D.Context3D;
	import flash.geom.Point;
	import flash.media.Sound;
	import flash.system.LoaderContext;
	import flash.system.ApplicationDomain;
	import flash.system.System;

	public class FWVideoEncoder extends EventDispatcher {

		// logging modes
		public static const LOGGING_BASIC:int = 0;
		public static const LOGGING_VERBOSE:int = 1;

		// framedrop force modes
		public static const FRAMEDROP_AUTO:int = 0;
		public static const FRAMEDROP_OFF:int = 1;
		public static const FRAMEDROP_ON:int = 2;

		// pts force modes
		public static const PTS_AUTO:int = 0;
		public static const PTS_MONO:int = 1;
		public static const PTS_REALTIME:int = 2;

		// audio recording modes
		public static const AUDIO_MICROPHONE:String = "audioMicrophone";
		public static const AUDIO_MONO:String = "audioMono";
		public static const AUDIO_STEREO:String = "audioStereo";
		public static const AUDIO_OFF:String = "audioOff";

		// platforms
		public static const PLATFORM_IOS:String = "IOS";
		public static const PLATFORM_ANDROID:String = "ANDROID";
		public static const PLATFORM_WINDOWS:String = "WINDOWS";
		public static const PLATFORM_MAC:String = "MAC";
		public static const PLATFORM_FLASH:String = "FLASH";

		public static const PLATFORM_TYPE_MOBILE:String = "MOBILE";
		public static const PLATFORM_TYPE_DESKTOP:String = "DESKTOP";

		public static const STATUS_IOS_GALLERY_AUTHORIZED:String = "ios_gallery_authorized";
		public static const STATUS_IOS_GALLERY_RESTRICTED:String = "ios_gallery_restricted";
		public static const STATUS_IOS_GALLERY_DENIED:String = "ios_gallery_denied";

		private var loader:Loader;

		private static var instance:FWVideoEncoder = null;
		private var parentMC:Sprite = null;
		private var encoderMc:MovieClip = null;
		private var myEncoder:Object = null;
		public var platform:String = "FLASH";

		private var fps:int = 0;
		private var recordAudio:String = "audioOff";
		private var realtime:Boolean = true;
		private var w:Number = 0;
		private var h:Number = 0;
		private var bitrate:int = 1000000;
		private var audio_sample_rate:int = 44100;
		private var audio_bit_rate:int = 64000;
		private var keyframe_freq:Number = 0;
		private var frameOffset:Point = null;
		private var SWFBridgeLoaded:Boolean = false;
		private var _SWFBridgePath:String = "";
		private var SWFBridgePreload:Boolean = false;

		public var domainMemoryPrealloc:int = 0;

		// in the constructor we'll load the SWFBridge
		public function FWVideoEncoder(parent:Sprite):void {			
			parentMC = parent;
		}

		// RAM adjuster
		public function configureRAMGuard(RAMadjusterFPSDivider:Number = 2, RAMadjusterTolerance:Number = 1.5):void {
			myEncoder.configureRAMGuard(RAMadjusterFPSDivider, RAMadjusterTolerance);
		}

		// needed for the SWF Bridge                                                                  
		public function load(path:String):void {
			unload();
			_SWFBridgePath = path;
			var request:URLRequest = new URLRequest(_SWFBridgePath + "FW_SWFBridge_ffmpeg.swf?v=" + CONFIG::VERSION);
			loader = new Loader();
			loader.contentLoaderInfo.addEventListener(Event.COMPLETE, onEncoderLoaded);
			loader.contentLoaderInfo.addEventListener(IOErrorEvent.IO_ERROR, function():void {
				throw new Error("[FlashyWrappers error] FW_SWFBridge_ffmpeg.swf couldn't be loaded! Please make sure it's in the same path as your main SWF or specify the path in the 'load' method like this: myEncoder.load('path/to/FW_SWFBridge_ffmpeg/')");  
			});

			// separate ApplicationDomain of this SWF from the currentDomain of the caller
			var loaderContext:LoaderContext = new LoaderContext(false, new ApplicationDomain(null), null);
			loader.load(request, loaderContext);
		}

		// unload SWF bridge
		public function unload():void {
			if (SWFBridgeLoaded) {
				encoderMc.parent.removeChild(encoderMc);
				encoderMc = null;
				loader.unload();			
				// see at least in debug mode if memory is stable
				System.gc();
				SWFBridgeLoaded = false;
			}
		}

		// after the encoder is loaded, dispatch the ready event
		private function onEncoderLoaded(e:Event):void {
			var loaderInfo:LoaderInfo = e.target as LoaderInfo;
			encoderMc = loaderInfo.content as MovieClip;			
			if (encoderMc) {
				trace("[FlashyWrappers] Got encoder class from FW_SWFBridge_ffmpeg");
				// assign myEncoder from the loaded SWF into the myEncoder object, so we can call its methods
				myEncoder = encoderMc["getInstance"](parentMC, domainMemoryPrealloc);			
				myEncoder.addEventListener(StatusEvent.STATUS, onStatus);
				SWFBridgeLoaded = true;
				dispatchEvent( new StatusEvent( StatusEvent.STATUS, false, false, "ready", ""));
			} else {
				throw new Error("[FlashyWrappers error] Couldn't find the encoder class in FW_SWFBridge_ffmpeg!");
			}
		}

		// hand over status event from the encoder
		private function onStatus(e:StatusEvent):void {
			dispatchEvent(e);
		}

		// parent is for compatibility with FlasCC / Crossbridge version
		public static function getInstance(parent:Sprite = null, domainMemory:Number = 0):FWVideoEncoder {
//			trace("SWFBridge getInstance, parent:" + parent + ", current instance:" + instance);
			if (instance == null) {
				// no instance
				instance = new FWVideoEncoder(parent);
				instance.domainMemoryPrealloc = domainMemory;
			} 
			return instance;
		}

		// set fps
		public function setFps(fps:int):void {
			myEncoder.setFps(fps);
		}

		// set dimensions
		public function setDimensions(w:Number, h:Number):void {
			myEncoder.setDimensions(w, h);
		}

		// set dimensions
		public function setAudioRealtime(realtime:Boolean):void {
			myEncoder.setAudioRealtime(realtime);
		}

		// log and verbose settings
		public function setLogging(level:int):void {
			myEncoder.setLogging(level);
		}

		public function playVideo(x:Number, y:Number, width:Number, height:Number):void {
			myEncoder.playVideo(x, y, width, height);
		}

		// ask mic permission
		public function askMicPermission():void {
			myEncoder.askMicPermission();
		}

		// initialize FlashyWrappers
		public function start(fps:int = 0, recordAudio:String = "audioOff", realtime:Boolean = true, w:Number = 0, h:Number = 0, bitrate:int = 1000000, audio_sample_rate:int = 44100, audio_bit_rate:int = 64000, keyframe_freq:Number = 0, frameOffset:Point = null):void {
			myEncoder.start(fps, recordAudio, realtime, w, h, bitrate, audio_sample_rate, audio_bit_rate, keyframe_freq, frameOffset);
		}

		public function addVideoFrame(pixels:ByteArray):void {
			myEncoder.addVideoFrame(pixels);
		}

		public function addAudioFrame(data:ByteArray):void {
			myEncoder.addAudioFrame(data);
		}

		private function setTotalFrames(count:int):void {
			myEncoder.setTotalFrames(count);
		}

		public function setRecordAudio(mode:String):void {
			myEncoder.setRecordAudio(mode);
		}

		public function getEncodingProgress():Number {
			return myEncoder.getEncodingProgress();
		}

		public function finish():void {
			myEncoder.finish();
		}

		private function encodeItBase64():void {
			myEncoder.encodeItBase64();
		}

		public function capture(target:* = null):void {	
			myEncoder.capture(target);
		}

		[Deprecated("Use capture() method instead with no argument")] 
		public function iOS_captureFullscreen(waitForRendering:Boolean = false):void {
			myEncoder.iOS_captureFullscreen();
		}

		public function iOS_startAudioMix(filename:String, duration:Number):void {
			myEncoder.iOS_startAudioMix(filename, duration);
		}

		public function iOS_stopAudioMix(filename:String):void {
			myEncoder.iOS_stopAudioMix(filename);
		}

		public function iOS_saveToCameraRoll(path:String = ""):void {
			myEncoder.iOS_saveToCameraRoll(path);
		}

		public function iOS_askPhotoPermissions():void {			
		}

		public function android_getEncoderQuirks():String {	
			return "";
		}

		public function set stage3DWithMC(val:Boolean):void {
			myEncoder.stage3DWithMC = val;
		}

		public function get stage3DWithMC():Boolean {
			return myEncoder.stage3DWithMC;
		}

		public function set platform_type(val:Boolean):void {
			myEncoder.platform_type = val;
		}

		public function get platform_type():Boolean {
			return myEncoder.platform_type;
		}

		public function getVideo():ByteArray {
			return myEncoder.getVideo();
		}

		[Deprecated("Use capture() method instead with DisplayObject as argument")] 
		public function captureMC(MC:DisplayObject):void {	
			myEncoder.captureMC(MC);
		}

		[Deprecated("Use capture() method instead with Context3D as argument")] 
		public function captureStage3D(c:Context3D, flushToEncoder:Boolean = true):void {
			myEncoder.captureStage3D(c, flushToEncoder);
		}

		public function addSoundtrack(s:Sound):void {
			myEncoder.addSoundtrack(s);
		}

		public function forcePTSMode(ptsMode:int):void {
			myEncoder.forcePTSMode(ptsMode);
		}

		public function forceFramedropMode(framedropMode:int):void {
			myEncoder.forceFramedropMode(framedropMode);
		}

/*
		// 0 = decoder finished, 2 = got frame, 1 = didn't get any frame
		private function decoderDoFrame():int {
			return myEncoder.decoderDoFrame();
		}

		// returns how many video frames was decoded so far from the video
		private function decoderGetVideoFramesDecoded():int {
			return myEncoder.decoderGetVideoFramesDecoded();
		}

		// flush the decoders raw video data into ByteArray
		private function decoderFlushVideoStream(ba:ByteArray):void {
			myEncoder.decoderFlushVideoStream(ba);
		}

		// flush the decoders raw audio data into ByteArray
		private function decoderFlushAudioStream(ba:ByteArray):void {
			myEncoder.decoderFlushAudioStream(ba);
		}

		// buffer several frames for decoding in background thread
		private function decoderBufferFrames(frameCount:int = 20):void {
			myEncoder.decoderBufferFrames(frameCount);
		}

		private function decoderGetVideoWidth():Number {
			return myEncoder.decoderGetVideoWidth();
		}

		private function decoderGetVideoHeight():Number {
			return myEncoder.decoderGetVideoHeight();
		}
		
		private function decoderGetEOF():Boolean {
			return myEncoder.decoderGetEOF();
		}

		private function decoderGetAudioStreamLength():Number {
			return myEncoder.decoderGetAudioStreamLength();
		}
*/

		public function dispose():void {
			myEncoder.dispose();
		}

	}	
}