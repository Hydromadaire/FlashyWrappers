package com.rainbowcreatures.FlashyWrappersAndroidHW;

// FW Audio packet used for storing audio into FW intermediate audio buffer

public class FlashyWrappersAudioPacket {
	public long _pts = 0;
	public byte[] _data = null;
	// how far are we when sending this packet to the muxer
	public long dataPointer = 0;
	public FlashyWrappersAudioPacket(long pts, byte[] data) {
		_pts = pts;
		_data = data.clone();		
	}
}
