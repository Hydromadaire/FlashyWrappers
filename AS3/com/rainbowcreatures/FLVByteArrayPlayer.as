/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * This uses a library I've found somewhere on internet to take mp4 file container and convert it into flv container, making it possible to replay mp4's in Flash from memory.
 * There is a glitch or two still with the replay(the first keyframe is not replayed it seems).
 * 
 */

package com.rainbowcreatures {

	import flash.utils.ByteArray;
	import flash.net.URLRequest;
	import flash.net.URLLoader;
	import flash.events.Event;
	import flash.net.URLLoaderDataFormat;
	import flash.display.Sprite;
	import flash.events.NetStatusEvent;
	import flash.net.NetStream;
	import flash.net.NetConnection;
	import flash.net.NetStreamAppendBytesAction;
	import flash.media.Video;
	import cc.minos.codec.mov.*;
	import cc.minos.codec.flv.*;	

    /**
     * ...
     * @author Hadi Tavakoli
     */
    public class FLVByteArrayPlayer extends Sprite 
    {
		private var netConnection:NetConnection;
		private var netStream:NetStream;
		private var video:Video = null;
		private var bytes:ByteArray;
		private var flv_frame0_time:int = 0;
		private var flv_frame0_pos:int = 0;
		private var flv_frame1_time:int = 0;
		private var flv_frame1_pos:int = 0;

		public function playVideo(_bytes:ByteArray):Video {
			// switch the container from mp4 to flv
			var mp4:Mp4Codec = new Mp4Codec();
			mp4.decode(_bytes);
			var flv:FlvCodec = new FlvCodec();
			bytes = flv.encode(mp4);				
			// try to fix an issue with 1st keyfrmaes
			flv_frame0_time = flv.keyframesList[0].time;
			flv_frame0_pos = flv.keyframesList[0].position;
			flv_frame1_time = flv.keyframesList[1].time;
			flv_frame1_pos = flv.keyframesList[1].position;
			video = new Video();
			netConnection = new NetConnection();
			netConnection.addEventListener(NetStatusEvent.NET_STATUS, netConnectionStatusHandler);		
			netConnection.connect(null);
			return video;
		}

		public function onMetaData(data:Array):void {
		}
			
		private function netConnectionStatusHandler(ev:NetStatusEvent):void
		{
			switch(ev.info.code)
			{
				case 'NetConnection.Connect.Success':
					netConnection.removeEventListener(NetStatusEvent.NET_STATUS, netConnectionStatusHandler);		
					netStream = new NetStream(netConnection);
					// add status handler for NS
					netStream.addEventListener(NetStatusEvent.NET_STATUS, nsStatusHandler);
					netStream.client = this;	
					netStream.play(null);	
					video.attachNetStream(netStream);
					// hack by Marteen, trying to make this work properly(to get replay without freezing)
					var buf:ByteArray = new ByteArray();
					buf.writeBytes(bytes,0,flv_frame1_pos+10240);
					netStream.appendBytes(buf);
					netStream.seek(flv_frame0_time - 0.01);
					buf = new ByteArray();
					buf.writeBytes(bytes,flv_frame0_pos);
					netStream.appendBytesAction(NetStreamAppendBytesAction.RESET_SEEK);
					netStream.appendBytes(buf);
					netStream.appendBytesAction(NetStreamAppendBytesAction.END_SEQUENCE);
				break;
			}
		}

		// detect when buffer is empty = video stopped playing
		private function nsStatusHandler(ev:NetStatusEvent):void {
			switch(ev.info.code)
			{
				case 'NetStream.Buffer.Empty':
					netStream.removeEventListener(NetStatusEvent.NET_STATUS, nsStatusHandler);	
					netConnection.close();
					if (video.parent != null) {
						video.parent.removeChild(video);
					}
				break;
			}
		}
	}
}
