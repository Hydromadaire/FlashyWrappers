/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * I've been trying to implement cross-platform video player using various AIR / Flash methods to replay videos. This didn't make it into production yet but there shouldn't be that much left to finish.
 * 
 */

package com.rainbowcreatures {

	import flash.events.NetStatusEvent;
	import flash.net.NetStream;
	import flash.net.NetConnection;
	import flash.media.Video;
	import flash.events.AsyncErrorEvent;
	import flash.display3D.Context3D;
	import flash.display.Stage3D;
	import flash.display3D.textures.*;
	import flash.events.Event;
	import flash.display.Stage;
	import flash.geom.Rectangle;

	public class MP4Player {

		private var nc:NetConnection = null;
		private	var vid:Video = null;
		private var ns:NetStream = null;
		private var texture:VideoTexture = null;
		public var context3D:Context3D = null;
		private var stage3D:Stage3D = null;
		private var _onStage3DCreated:Function = null;
		private var batch:SpriteBatch3D = null;
		public var textureX:Number = 0;
		public var textureY:Number = 0;
		public var textureW:Number = 320;
		public var textureH:Number = 240;

		public function playVideo(file:String):Video {
			nc = new NetConnection(); 
			nc.connect(null);
			vid = new Video(); 
			ns = new NetStream(nc); 
			ns.addEventListener(NetStatusEvent.NET_STATUS,netStatusHandler); 
			ns.addEventListener(AsyncErrorEvent.ASYNC_ERROR, asyncErrorHandler); 
			vid.attachNetStream(ns);
			ns.client = this;
			ns.play(file); 
			return vid;
		}

		public function playVideoTexture(file:String):VideoTexture {
			if (context3D == null) throw new Error('Supply "context3D" first by setting the property context3D!');
			batch = new SpriteBatch3D(context3D);
   			context3D.configureBackBuffer(textureW, textureH, 0, false);
			context3D.enableErrorChecking = true;
			nc = new NetConnection(); 
			nc.connect(null);
			ns = new NetStream(nc); 
			ns.addEventListener(NetStatusEvent.NET_STATUS,netStatusHandler); 
			ns.addEventListener(AsyncErrorEvent.ASYNC_ERROR, asyncErrorHandler); 
			texture = context3D.createVideoTexture();
			texture.attachNetStream(ns);
			texture.addEventListener(Event.TEXTURE_READY, renderFrame); // Need to add event 
			ns.client = this;
			ns.play(file); 
			return texture;	
		}

		public function createContext3D(stage:Stage, onStage3DCreated:Function):void {
		        _onStage3DCreated = onStage3DCreated;
			if( stage.stage3Ds.length > 0 )
			{
			    stage3D = stage.stage3Ds[0];   
			    stage3D.addEventListener( Event.CONTEXT3D_CREATE, myContext3DHandler );
			    stage3D.requestContext3D();
			} else throw new Error('Cannot create Stage3D');
			
		}         

		private function myContext3DHandler ( event : Event ) : void
		{
		    if (Context3D.supportsVideoTexture)
		    {
			context3D = stage3D.context3D;
			_onStage3DCreated();
		    } else throw new Error('VideoTexture not supported');
		}

		function renderFrame(e:Event):void
		{
			context3D.clear(1, 0, 0);  
			batch.begin(textureW, textureH);
			batch.draw(texture, new Rectangle(textureX - (textureW / 2), textureY - (textureH / 2), textureW, textureH));
			batch.end();
			context3D.present();
		}

		public function onMetaData(data:Array):void {
		}

		private function netStatusHandler(event:NetStatusEvent):void 
		{ 
		} 
 
		private function asyncErrorHandler(event:AsyncErrorEvent):void 
		{ 
			throw event.error;
		}

		public function onPlayStatus(info:Object):void  
		{  
		          switch(info.code) {  
		                    case "NetStream.Play.Complete":  
					ns.removeEventListener(NetStatusEvent.NET_STATUS, netStatusHandler);	
					nc.close();
					if (texture == null) {
						if (vid.parent != null) {
							vid.parent.removeChild(vid);
						}
					}
		                    break;  
		          }  
		}

	}
}