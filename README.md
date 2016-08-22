# FlashyWrappers SDK

Building
--------

*Windows / Android / Flash*
The .bat files are used to build FW on Windows.

*Windows*

Windows came in 2 flavors. Like for all encoders, FFmpeg based encoder came first. This is the only platform where I still kept it. The reason is, when debugging FFmpeg based encoder for Flash, 
it was often useful to make a build for Windows with identical code - debugging FlasCC / Crossbridge can be pretty horrible. The FFmpeg flavor of FlashyWrappers Windows was built with either MingW
 or MSVC(lately only MSVC) - the define USE_MEDIACODEC determines if the encoder is FFmpeg or MediaCodec based.

*OS X / iOS*

Those platforms use identical source code file, luckily AVFoundation is almost identical on OS X and iOS. These are currently not ready for release yet but they are included in case you can't wait.

*Hello world*

This source code was taken from the Hello World example.

```javascript
package  {
	
	import flash.display.MovieClip;
	import com.rainbowcreatures.*;
	import com.rainbowcreatures.swf.*;
	import flash.events.StatusEvent; // in case you're not importing this already
	import flash.events.Event; // in case you're not importing this already
	import flash.events.IOErrorEvent;
	import flash.utils.ByteArray;
	import flash.net.FileReference;
	import flash.text.TextField;
	import flash.text.TextFormat;

	[SWF(width=600,height=400, frameRate='24')]		
	public class Helloworld extends MovieClip {
		
		var myEncoder:FWVideoEncoder;
		var frameIndex:Number = 0;
		var maxFrames:Number = 50;
		var txt:TextField = new TextField();
		
		public function Helloworld() {
			// constructor code
			var tf:TextFormat = new TextFormat("Arial", 30, 0XAA5050);
			txt.text = "Hello world!";			
			txt.setTextFormat(tf);
			txt.width = 200;
			txt.y = 180;
			addChild(txt);			
			myEncoder = FWVideoEncoder.getInstance(this);			
			myEncoder.addEventListener(StatusEvent.STATUS, onStatus);
			myEncoder.load("../../lib/FlashPlayer/mp4/");			
		}
		
		private function onStatus(e:StatusEvent):void {
			trace("Got status");
			if (e.code == "ready") {
				myEncoder.setDimensions(stage.stageWidth, stage.stageHeight);
				myEncoder.start(24);
				trace("FlashyWrappers ready! Init...");
				addEventListener(Event.ENTER_FRAME, onFrame);
			}
			if (e.code == "encoded") {
				var bo:ByteArray = myEncoder.getVideo();
				trace("Recording finished, video length: " + bo.length);								
				
				// save the file for check
				var saveFile:FileReference = new FileReference();
				saveFile.addEventListener(Event.COMPLETE, saveCompleteHandler);
				saveFile.addEventListener(IOErrorEvent.IO_ERROR, saveIOErrorHandler);
				saveFile.save(bo, "video.mp4");								
			}
		}
		
		private function onFrame(e:Event):void {
			// animate the rectangle
			txt.x++;

			// clear the background, otherwise all transparency will be black by default in the video
			graphics.beginFill(0XFFFFFF, 1);
			graphics.drawRect(0, 0, stage.stageWidth, stage.stageHeight);
			graphics.endFill();
			
			// render one frame of your stuff into someMovieClip, then capture and
			// add one frame into the FlashyWrappers video encoder like this:
			myEncoder.capture(this);
			frameIndex++;
			if (frameIndex >= maxFrames - 1) {
				removeEventListener(Event.ENTER_FRAME, onFrame);				
				myEncoder.finish();
			}
		}							

		// file was saved
		private function saveCompleteHandler(e:Event):void {
			trace("Video saved!");
		}
		
		// some error happened
		private function saveIOErrorHandler(e:IOErrorEvent):void {
			trace("Video NOT saved:(");
		}

	}	
}

```
