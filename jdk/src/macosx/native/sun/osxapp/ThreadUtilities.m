/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

static BOOL sCocoaComponentCompatibility = NO;
static NSTimeInterval sCocoaComponentCompatibilityTimeout = 0.5;
static BOOL sLoggingEnabled = YES;

#ifdef AWT_THREAD_ASSERTS_ENV_ASSERT
int sAWTThreadAsserts = 0;
#endif /* AWT_THREAD_ASSERTS_ENV_ASSERT */


// This is for backward compatibility for those people using CocoaComponent
// Since we've flipped the AWT threading model for Tiger (10.4), all the rules
// for CocoaComponent are wrong.
// So for existing CocoaComponent users, we can't be synchronous.
// Making things totally asynchronous breaks a _lot_, so we try to be
// synchronous and time out after a little bit.
#define NOT_READY 0
#define READY 1
#define IN_PROGRESS 2

BOOL sInPerformFromJava = NO;
NSUInteger sPerformCount = 0;

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
- (void) performCompatible;
- (void) _performCompatible:(NSConditionLock *)resultLock;
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

    sPerformCount++;

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

- (void) performCompatible {
    // We check if we are on the AppKit thread because frequently, apps
    // using CocoaComponent are doing things on the wrong thread!
    if (pthread_main_np()) {
        [fTarget performSelector:fSelector withObject:fArg];
    } else {
        // Setup the lock
        NSConditionLock *resultLock =
            [[NSConditionLock alloc] initWithCondition:NOT_READY];

        // Make sure that if we return early, nothing gets released out
        // from under us
        [resultLock retain];
        [fTarget retain];
        [fArg retain];
        [self retain];
        // Do an asynchronous perform to the main thread.
        [self performSelectorOnMainThread:@selector(_performCompatible:)
              withObject:resultLock waitUntilDone:NO modes:sAWTPerformModes];

        // Wait for a little bit for it to finish
        [resultLock lockWhenCondition:READY beforeDate:[NSDate dateWithTimeIntervalSinceNow:sCocoaComponentCompatibilityTimeout]];

        // If the _performCompatible is actually in progress,
        // we should let it finish
        if ([resultLock condition] == IN_PROGRESS) {
            [resultLock lockWhenCondition:READY];
        }

        if ([resultLock condition] == NOT_READY && sLoggingEnabled) {
            NSLog(@"[Java CocoaComponent compatibility mode]: Operation timed out due to possible deadlock: selector '%@' on target '%@' with args '%@'", NSStringFromSelector(fSelector), fTarget, fArg);
        }

        [resultLock unlock];
        [resultLock autorelease];
    }
}

- (void) _performCompatible:(NSConditionLock *)resultLock {
    // notify that the perform is in progress!
    [resultLock lock];
    [resultLock unlockWithCondition:IN_PROGRESS];

    sPerformCount++;

    // Actually do the work.
    @try {
        [fTarget performSelector:fSelector withObject:fArg];
    } @catch (NSException *e) {
        NSLog(@"*** CPerformer: ignoring exception '%@' raised during performCompatible of selector '%@' on target '%@' with args '%@'", e, NSStringFromSelector(fSelector), fTarget, fArg);
    } @finally {
        // notify done!
        [resultLock lock];
        [resultLock unlockWithCondition:READY];

        // Clean up after ourselves
        [resultLock autorelease];
        [fTarget autorelease];
        [fArg autorelease];
        [self autorelease];
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
    if (sCocoaComponentCompatibility && wait && inAWT) {
        [performer performCompatible];
        [performer autorelease];
    } else {
        [performer performSelectorOnMainThread:@selector(perform) withObject:nil waitUntilDone:wait modes:((inAWT) ? sAWTPerformModes : sPerformModes)]; // AWT_THREADING Safe (cover method)
        [performer release];
    }
}

@end


void OSXAPP_SetJavaVM(JavaVM *vm)
{
    jvm = vm;
}

