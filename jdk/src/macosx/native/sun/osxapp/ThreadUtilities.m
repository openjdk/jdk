/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#import <AppKit/AppKit.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>
#import <objc/message.h>

#import "ThreadUtilities.h"


// The following must be named "jvm", as there are extern references to it in AWT
JavaVM *jvm = NULL;
static JNIEnv *appKitEnv = NULL;

static NSArray *sPerformModes = nil;
static NSArray *sAWTPerformModes = nil;

static BOOL sLoggingEnabled = YES;

#ifdef AWT_THREAD_ASSERTS_ENV_ASSERT
int sAWTThreadAsserts = 0;
#endif /* AWT_THREAD_ASSERTS_ENV_ASSERT */

BOOL sInPerformFromJava = NO;

// This class is used so that performSelectorOnMainThread can be
// controlled a little more easily by us.  It has 2 roles.
// The first is to set/unset a flag (sInPerformFromJava) that code can
// check to see if we are in a synchronous perform initiated by a java thread.
// The second is to implement the CocoaComponent backward compatibility mode.
@interface CPerformer : NSObject {
    id fTarget;
    SEL fSelector;
    id fArg;
    BOOL fWait;
}

- (id) initWithTarget:(id)target selector:(SEL)selector arg:(id)arg wait:(BOOL)wait;
- (void) perform;
@end


@implementation CPerformer

- (id) initWithTarget:(id)target selector:(SEL)selector arg:(id)arg {
    return [self initWithTarget:target selector:selector arg:arg wait:YES];
}

- (id) initWithTarget:(id)target selector:(SEL)selector arg:(id)arg wait:(BOOL)wait {
    self = [super init];
    if (self != nil) {
        fTarget = [target retain];
        fSelector = selector;
        fArg = [arg retain];
        // Only set sInPerformFromJava if this is a synchronous perform
        fWait = wait;
    }
    return self;
}

- (void) dealloc {
    [fTarget release];
    [fArg release];
    [super dealloc];
}
//- (void)finalize { [super finalize]; }

- (void) perform {
    AWT_ASSERT_APPKIT_THREAD;

    // If this is the first time we're going from java thread -> appkit thread,
    // set sInPerformFromJava for the duration of the invocation
    BOOL nestedPerform = sInPerformFromJava;
    if (fWait) {
        sInPerformFromJava = YES;
    }

    // Actually do the work (cheat to avoid a method call)
    @try {
        objc_msgSend(fTarget, fSelector, fArg);
        //[fTarget performSelector:fSelector withObject:fArg];
    } @catch (NSException *e) {
        NSLog(@"*** CPerformer: ignoring exception '%@' raised during perform of selector '%@' on target '%@' with args '%@'", e, NSStringFromSelector(fSelector), fTarget, fArg);
    } @finally {
        // If we actually set sInPerformFromJava, unset it now
        if (!nestedPerform && fWait) {
            sInPerformFromJava = NO;
        }
    }
}
@end


@implementation ThreadUtilities

+ (JNIEnv*)getJNIEnv {
AWT_ASSERT_APPKIT_THREAD;
    if (appKitEnv == NULL) {
        (*jvm)->AttachCurrentThreadAsDaemon(jvm, (void **)&appKitEnv, NULL);
    }
    return appKitEnv;
}

+ (JNIEnv*)getJNIEnvUncached {
    JNIEnv *env = NULL;
    (*jvm)->AttachCurrentThreadAsDaemon(jvm, (void **)&env, nil);
    return env;
}

+ (void)initialize {
    // Headless: BOTH
    // Embedded: BOTH
    // Multiple Calls: NO
    // Caller: Obj-C class initialization
    // Thread: ?

    if (sPerformModes == nil) {
        // Create list of Run Loop modes to perform on
        // The default performSelector, with no mode argument, runs in Default,
        // ModalPanel, and EventTracking modes
        sPerformModes =    [[NSArray alloc] initWithObjects:NSDefaultRunLoopMode, NSModalPanelRunLoopMode, nil];
        sAWTPerformModes = [[NSArray alloc] initWithObjects:NSDefaultRunLoopMode, NSModalPanelRunLoopMode, NSEventTrackingRunLoopMode, [JNFRunLoop javaRunLoopMode], nil];

#ifdef AWT_THREAD_ASSERTS_ENV_ASSERT
        sAWTThreadAsserts = (getenv("COCOA_AWT_DISABLE_THREAD_ASSERTS") == NULL);
#endif /* AWT_THREAD_ASSERTS_ENV_ASSERT */
    }
}

// These methods can behave slightly differently than the normal
// performSelector...  In particular, we define a special runloop mode
// (AWTRunLoopMode) so that we can "block" the main thread against the
// java event thread without deadlocking. See CToolkit.invokeAndWait.
+ (void)performOnMainThread:(SEL)aSelector onObject:(id)target withObject:(id)arg waitUntilDone:(BOOL)wait awtMode:(BOOL)inAWT {
    CPerformer *performer = [[CPerformer alloc] initWithTarget:target selector:aSelector arg:arg wait:wait];
    [performer performSelectorOnMainThread:@selector(perform) withObject:nil waitUntilDone:wait modes:((inAWT) ? sAWTPerformModes : sPerformModes)]; // AWT_THREADING Safe (cover method)
    [performer release];
}

+ (void)performOnMainThreadWaiting:(BOOL)wait block:(void (^)())block {
    if ([NSThread isMainThread] && wait == YES) {
        block(); 
    } else { 
        [JNFRunLoop performOnMainThreadWaiting:wait withBlock:block]; 
    }
}

+ (void)performOnMainThread:(SEL)aSelector on:(id)target withObject:(id)arg waitUntilDone:(BOOL)wait {
    if ([NSThread isMainThread] && wait == YES) {
        [target performSelector:aSelector withObject:arg];
    } else {
        [JNFRunLoop performOnMainThread:aSelector on:target withObject:arg waitUntilDone:wait];
    }
}

@end


void OSXAPP_SetJavaVM(JavaVM *vm)
{
    jvm = vm;
}

