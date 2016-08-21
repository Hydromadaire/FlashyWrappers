iOS 64-bit / OS X 64-bit

Both targets use the same XCode project. You can just switch between the targets to build for the platform of your choice.

OS X

Make sure to include AIR framework and setup it properly in XCode. You need to include TWO items into frameworks, both the Adobe AIR.framework and also the 64-bit binary contained inside the framework, or the build will give around 12 errors complaining about missing _FREXXXX symbols for 64 bit architecture.

See http://blogs.adobe.com/flashplayer/2015/12/air-64-bit-on-mac-osx.html