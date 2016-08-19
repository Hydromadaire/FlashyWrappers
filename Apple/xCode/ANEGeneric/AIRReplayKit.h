/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * iOS / OS X port of FlashyWrappers. This was planned to be refactored similar to Windows where the encoder is in separate class, but never managed to get to it - feel free!
 * Whats really missing is the ability to record audio from AIR (in general recording audio was an issue) there might be dead code here which attempts that.
 * Also, iOS ReplayKit framework support was almost ready but then because AIR SDK didn't support the latest iOS SDK it couldn't be compiled so its untested. 
 *
 */

#ifndef AIRReplayKit_h
#define AIRReplayKit_h
#import <ReplayKit/ReplayKit.h>

@interface AIRReplayKit : NSObject<RPPreviewViewControllerDelegate, RPScreenRecorderDelegate>
{
}

@property(nonatomic, readonly, getter=getLastError)  NSString* lastError;

@property(nonatomic, readonly, getter=getPreviewController) RPPreviewViewController* previewController;

- (BOOL)screenRecordingAvailable;
+ (BOOL)hasInstance;
+ (AIRReplayKit*)Instance;
- (NSString*)getLastError;
- (RPPreviewViewController*)getPreviewController;
- (BOOL)startRecoring:(BOOL)enableMicrophone;
- (BOOL)recording;
- (BOOL)stopRecording;
- (void)screenRecorder:(nonnull RPScreenRecorder*)screenRecorder didStopRecordingWithError:(nonnull NSError*)error previewViewController:(nullable RPPreviewViewController*)previewViewController;
- (BOOL)preview;
- (BOOL)discard;
- (void)previewControllerDidFinish:(RPPreviewViewController*)previewController;
@end


#endif /* AIRReplayKit_h */
