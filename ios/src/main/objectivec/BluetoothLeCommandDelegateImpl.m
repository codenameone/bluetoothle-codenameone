/*
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 */

#import "CDVCommandDelegateImpl.h"
#import "CDVCommandQueue.h"
#import "CDVPluginResult.h"
#import "CDVViewController.h"
#import "com_codename1_bluetoothle_BluetoothCallbackRegistry.h"

@implementation BluetoothLeCommandDelegateImpl : NSObject

//@synthesize urlTransformer;


- (NSString*)pathForResource:(NSString*)resourcepath
{
    return nil;
}

- (void)flushCommandQueueWithDelayedJs
{
    //_delayResponses = YES;
    //[_commandQueue executePending];
    //_delayResponses = NO;
}

- (void)evalJsHelper2:(NSString*)js
{
    
}

- (void)evalJsHelper:(NSString*)js
{
    
}

- (BOOL)isValidCallbackId:(NSString*)callbackId
{
    
    return YES;
}

- (void)sendPluginResult:(CDVPluginResult*)result callbackId:(NSString*)callbackId
{
    CDV_EXEC_LOG(@"Exec(%@): Sending result. Status=%@", callbackId, result.status);
    // This occurs when there is are no win/fail callbacks for the call.
    if ([@"INVALID" isEqualToString:callbackId]) {
        return;
    }
    // This occurs when the callback id is malformed.
    if (![self isValidCallbackId:callbackId]) {
        NSLog(@"Invalid callback id received by sendPluginResult");
        return;
    }
    int status = [result.status intValue];
    BOOL keepCallback = [result.keepCallback boolValue];
    NSString* argumentsAsJSON = [result argumentsAsJSON];
    BOOL debug = NO;

#ifdef DEBUG
    debug = YES;
#endif

    //[self evalJsHelper:js];
    NSLog(@"Result %@", argumentsAsJSON);
    // Honor pluginResult's status + keepCallback. The 2-argument
    // BluetoothCallbackRegistry.sendResult defaults to success=true,
    // keepCallback=false, which silently drops the callback after the
    // first event delivered through this path — same root-cause bug as
    // CallbackContext.sendPluginResult on Android. Use the 4-argument
    // overload so multi-event operations (startScan -> scanStarted then
    // scanResult, subscribe -> subscribed then subscribedResult,
    // connect -> connected then disconnected, etc) actually deliver
    // every event to the user's listener.
    // CDVCommandStatus_OK == 1 in Cordova; matches PluginResult.Status.OK.ordinal()
    // on the Android side.
    com_codename1_bluetoothle_BluetoothCallbackRegistry_sendResult___java_lang_String_java_lang_String_boolean_boolean(
        CN1_THREAD_GET_STATE_PASS_ARG
        fromNSString(CN1_THREAD_GET_STATE_PASS_ARG callbackId),
        fromNSString(CN1_THREAD_GET_STATE_PASS_ARG argumentsAsJSON),
        (status == 1) ? JAVA_TRUE : JAVA_FALSE,
        keepCallback ? JAVA_TRUE : JAVA_FALSE
    );

}

- (void)evalJs:(NSString*)js
{
    //[self evalJs:js scheduledOnRunLoop:YES];
}

- (void)evalJs:(NSString*)js scheduledOnRunLoop:(BOOL)scheduledOnRunLoop
{
    
}

- (id)getCommandInstance:(NSString*)pluginName
{
    //return [_viewController getCommandInstance:pluginName];
    return nil;
}

- (void)runInBackground:(void (^)())block
{
    //dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), block);
}

- (NSString*)userAgent
{
    //return [_viewController userAgent];
    return nil;
}

- (NSDictionary*)settings
{
    //return _viewController.settings;
    return nil;
}

@end