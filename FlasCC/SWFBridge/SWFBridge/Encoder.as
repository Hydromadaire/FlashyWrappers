/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * SWF Bridge to encoder SWC compiled into this bridge for fixing Adobe IDE's issues.
 *
 */

package {

	import flash.display.MovieClip;
	import flash.display.Sprite;
	import com.rainbowcreatures.FWVideoEncoder;
	import flash.events.Event;
	import flash.display.BitmapData;
	import flash.media.Sound;
	import flash.utils.ByteArray;
	import flash.system.Worker;
	import flash.system.Security;

	public class Encoder extends MovieClip
	{
		// encoder instance
		private var myEncoder:FWVideoEncoder = null;

		public function Encoder()
		{
			// constructor code
			Security.allowDomain("*");			
			// in case this is not the main thread just setup the encoder and exit to allow setting up the encoder thread
			if (!Worker.current.isPrimordial) {
				trace("FW: Starting worker thread...");
				FWVideoEncoder.getInstance(this);
				if (myEncoder != null) myEncoder.start();
				return;
			}	
		}

		// get instance of the encoder
		public function getInstance(root:Sprite, domainMemoryPrealloc:int = 0):FWVideoEncoder
		{	
			trace("Called getInstance");		
			root.addChild(this);
//			trace("Parent:" + this);			
			myEncoder = FWVideoEncoder.getInstance(this, domainMemoryPrealloc);
			return myEncoder;
		}				
	}
}