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

#import <Foundation/Foundation.h>
#import <ReplayKit/ReplayKit.h>
#import "AIRReplayKit.h"
#import "FW_exception.h"

static AIRReplayKit* _replayKit;

@implementation AIRReplayKit
{
    NSString* _lastError;
    RPPreviewViewController* _previewController;
}

+ (AIRReplayKit*)Instance
{
    if (_replayKit == nil)
    {
        _replayKit = [[AIRReplayKit alloc] init];
    }
    return _replayKit;
}

- (BOOL)screenRecordingAvailable
{
    return _previewController != nil;
}

+ (BOOL)hasInstance
{
    return _replayKit != nil;
}

- (NSString *)getLastError
{
    return _lastError;
}


- (RPPreviewViewController*)getPreviewController
{
    return _previewController;
}

- (BOOL)startRecoring:(BOOL)enableMicrophone
{
    RPScreenRecorder* recorder = [RPScreenRecorder sharedRecorder];
    if (recorder == nil)
    {
        throw MyException("Failed to get Screen recorder");
        return NO;
    }
    
    [recorder setDelegate:self];
    [recorder startRecordingWithMicrophoneEnabled:enableMicrophone handler:^(NSError* error){
        if (error != nil)
        {
            _lastError = [error description];
        }
    }];
    
    return YES;
}

- (BOOL)recording
{
    RPScreenRecorder* recorder = [RPScreenRecorder sharedRecorder];
    if (recorder == nil)
    {
        throw MyException("Failed to get Screen recorder");
        return NO;
    }
    return [recorder isRecording];
}

- (BOOL)stopRecording
{
    RPScreenRecorder* recorder = [RPScreenRecorder sharedRecorder];
    if (recorder == nil)
    {
        throw MyException("Failed to get Screen recorder");
        return NO;
    }
    
    [recorder stopRecordingWithHandler:^(RPPreviewViewController* previewViewController, NSError* error){
        if (error != nil)
        {
            _lastError = [error description];
            return;
        }
        if (previewViewController != nil)
        {
            [previewViewController setPreviewControllerDelegate:self];
            _previewController = previewViewController;
        }
    }];
    
    return YES;
}

- (void)screenRecorder:(nonnull RPScreenRecorder*)screenRecorder didStopRecordingWithError:(nonnull NSError*)error previewViewController:(nullable RPPreviewViewController*)previewViewController
{
    if (error != nil)
    {
        _lastError = [error description];
    }
    _previewController = previewViewController;
}

- (BOOL)preview
{
    if (_previewController == nil)
    {
        throw MyException("No recording available");
        return NO;
    }
    
    [_previewController setModalPresentationStyle:UIModalPresentationFullScreen];
    [(UIViewController*)[[[UIApplication sharedApplication] keyWindow] rootViewController] presentViewController:_previewController animated:YES completion:^()
     {
         _previewController = nil;
     }];
    return YES;
}

- (BOOL)discard
{
    if (_previewController == nil)
    {
        return YES;
    }
    
    RPScreenRecorder* recorder = [RPScreenRecorder sharedRecorder];
    if (recorder == nil)
    {
        throw MyException("Failed to get Screen recorder");
        return NO;
    }
    
    [recorder discardRecordingWithHandler:^()
     {
         _previewController = nil;
     }];
    // TODO - the above callback doesn't seem to be working at the moment.
    _previewController = nil;
    
    return YES;
}

- (void)previewControllerDidFinish:(RPPreviewViewController*)previewController
{
    if (previewController != nil)
    {
        [previewController dismissViewControllerAnimated:YES completion:nil];
    }
}

@end
