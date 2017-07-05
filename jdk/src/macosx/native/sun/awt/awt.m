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

#import <pthread.h>
#import <objc/runtime.h>
#import <Cocoa/Cocoa.h>
#import <Security/AuthSession.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>
#import <JavaRuntimeSupport/JavaRuntimeSupport.h>

#import "NSApplicationAWT.h"
#import "PropertiesUtilities.h"
#import "ThreadUtilities.h"
#import "AWT_debug.h"
#import "ApplicationDelegate.h"

#define DEBUG 0


// The symbol is defined in libosxapp.dylib (ThreadUtilities.m)
extern JavaVM *jvm;

static bool ShouldPrintVerboseDebugging() {
    static int debug = -1;
    if (debug == -1) {
        debug = (int)(getenv("JAVA_AWT_VERBOSE") != NULL) || (DEBUG != 0);
    }
    return (bool)debug;
}

// This is the data necessary to have JNI_OnLoad wait for AppKit to start.
static BOOL sAppKitStarted = NO;
static pthread_mutex_t sAppKitStarted_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t sAppKitStarted_cv = PTHREAD_COND_INITIALIZER;

void setBusy(BOOL isBusy);
static void BusyObserver(CFRunLoopObserverRef ref, CFRunLoopActivity what, void* arg);
static void NotBusyObserver(CFRunLoopObserverRef ref, CFRunLoopActivity what, void* arg);
static void AWT_NSUncaughtExceptionHandler(NSException *exception);

static CFRunLoopObserverRef busyObserver = NULL;
static CFRunLoopObserverRef notBusyObserver = NULL;

static void setUpAWTAppKit(BOOL swt_mode, BOOL headless) {
AWT_ASSERT_APPKIT_THREAD;
    BOOL verbose = ShouldPrintVerboseDebugging();
    if (verbose) AWT_DEBUG_LOG(@"setting up busy observers");

    JNIEnv *env = [ThreadUtilities getJNIEnv];

    // Add CFRunLoopObservers to call into AWT so that AWT knows that the
    //  AWT thread (which is the AppKit main thread) is alive. This way AWT
    //  will not automatically shutdown.
    busyObserver = CFRunLoopObserverCreate(
                        NULL,                        // CFAllocator
                        kCFRunLoopAfterWaiting,      // CFOptionFlags
                        true,                        // repeats
                        NSIntegerMax,                // order
                        &BusyObserver,               // CFRunLoopObserverCallBack
                        NULL);                       // CFRunLoopObserverContext

    notBusyObserver = CFRunLoopObserverCreate(
                        NULL,                        // CFAllocator
                        kCFRunLoopBeforeWaiting,     // CFOptionFlags
                        true,                        // repeats
                        NSIntegerMin,                // order
                        &NotBusyObserver,            // CFRunLoopObserverCallBack
                        NULL);                       // CFRunLoopObserverContext

    CFRunLoopRef runLoop = [[NSRunLoop currentRunLoop] getCFRunLoop];
    CFRunLoopAddObserver(runLoop, busyObserver, kCFRunLoopDefaultMode);
    CFRunLoopAddObserver(runLoop, notBusyObserver, kCFRunLoopDefaultMode);

    CFRelease(busyObserver);
    CFRelease(notBusyObserver);

    if (!headless) setBusy(YES);

    // Set the java name of the AppKit main thread appropriately.
    jclass threadClass = NULL;
    jstring name = NULL;
    jobject curThread = NULL;

    if (!swt_mode) {
        threadClass = (*env)->FindClass(env, "java/lang/Thread");
        if (threadClass == NULL || (*env)->ExceptionCheck(env)) goto cleanup;
        jmethodID currentThreadID = (*env)->GetStaticMethodID(env, threadClass, "currentThread", "()Ljava/lang/Thread;");
        if (currentThreadID == NULL || (*env)->ExceptionCheck(env)) goto cleanup;
        jmethodID setName = (*env)->GetMethodID(env, threadClass, "setName", "(Ljava/lang/String;)V");
        if (setName == NULL || (*env)->ExceptionCheck(env)) goto cleanup;

        curThread = (*env)->CallStaticObjectMethod(env, threadClass, currentThreadID); // AWT_THREADING Safe (known object)
        if (curThread == NULL || (*env)->ExceptionCheck(env)) goto cleanup;
        name = (*env)->NewStringUTF(env, "AWT-AppKit");
        if (name == NULL || (*env)->ExceptionCheck(env)) goto cleanup;
        (*env)->CallVoidMethod(env, curThread, setName, name); // AWT_THREADING Safe (known object)
        if ((*env)->ExceptionCheck(env)) goto cleanup;
    }

cleanup:
    if (threadClass != NULL) {
        (*env)->DeleteLocalRef(env, threadClass);
    }
    if (name != NULL) {
        (*env)->DeleteLocalRef(env, name);
    }
    if (curThread != NULL) {
        (*env)->DeleteLocalRef(env, curThread);
    }
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }

    // Add the exception handler of last resort
    NSSetUncaughtExceptionHandler(AWT_NSUncaughtExceptionHandler);

    if (verbose) AWT_DEBUG_LOG(@"finished setting thread name");
}



// Returns true if java believes it is running headless
BOOL isHeadless(JNIEnv *env) {
    // Just access the property directly, instead of using GraphicsEnvironment.isHeadless.
    //  This is because this may be called while AWT is being loaded, and calling AWT
    //  while it is being loaded will deadlock.
    static JNF_CLASS_CACHE(jc_Toolkit, "java/awt/GraphicsEnvironment");
    static JNF_STATIC_MEMBER_CACHE(jm_isHeadless, jc_Toolkit, "isHeadless", "()Z");
    return JNFCallStaticBooleanMethod(env, jm_isHeadless);
}

BOOL isSWTInWebStart(JNIEnv* env) {
    NSString *swtWebStart = [PropertiesUtilities javaSystemPropertyForKey:@"com.apple.javaws.usingSWT" withEnv:env];
    return [@"true" isCaseInsensitiveLike:swtWebStart];
}

void setBusy(BOOL busy) {
AWT_ASSERT_APPKIT_THREAD;

    JNIEnv *env = [ThreadUtilities getJNIEnv];
    static JNF_CLASS_CACHE(jc_AWTAutoShutdown, "sun/awt/AWTAutoShutdown");

    if (busy) {
        static JNF_STATIC_MEMBER_CACHE(jm_notifyBusyMethod, jc_AWTAutoShutdown, "notifyToolkitThreadBusy", "()V");
        JNFCallStaticVoidMethod(env, jm_notifyBusyMethod);
    } else {
        static JNF_STATIC_MEMBER_CACHE(jm_notifyFreeMethod, jc_AWTAutoShutdown, "notifyToolkitThreadFree", "()V");
        JNFCallStaticVoidMethod(env, jm_notifyFreeMethod);
    }
}

static void BusyObserver(CFRunLoopObserverRef ref, CFRunLoopActivity what, void* arg) {
AWT_ASSERT_APPKIT_THREAD;

    // This is only called with the selector kCFRunLoopAfterWaiting.
#ifndef PRODUCT_BUILD
    assert(what == kCFRunLoopAfterWaiting);
#endif /* PRODUCT_BUILD */

    setBusy(YES);
}

static void NotBusyObserver(CFRunLoopObserverRef ref, CFRunLoopActivity what, void* arg) {
AWT_ASSERT_APPKIT_THREAD;

    // This is only called with the selector kCFRunLoopBeforeWaiting.
#ifndef PRODUCT_BUILD
    assert(what == kCFRunLoopBeforeWaiting);
#endif /* PRODUCT_BUILD */

    setBusy(NO);
}

static void AWT_NSUncaughtExceptionHandler(NSException *exception) {
    NSLog(@"Apple AWT Internal Exception: %@", [exception description]);
}

// This is an empty Obj-C object just so that -peformSelectorOnMainThread can be used.
@interface AWTStarter : NSObject { }
+ (void)start:(BOOL)headless swtMode:(BOOL)swtMode swtModeForWebStart:(BOOL)swtModeForWebStart;
- (void)starter:(NSArray*)args;
+ (void)appKitIsRunning:(id)arg;
@end

@implementation AWTStarter

+ (BOOL) isConnectedToWindowServer {
    SecuritySessionId session_id;
    SessionAttributeBits session_info;
    OSStatus status = SessionGetInfo(callerSecuritySession, &session_id, &session_info);
    if (status != noErr) return NO;
    if (!(session_info & sessionHasGraphicAccess)) return NO;
    return YES;
}

+ (BOOL) markAppAsDaemon {
    id jrsAppKitAWTClass = objc_getClass("JRSAppKitAWT");
    SEL markAppSel = @selector(markAppIsDaemon);
    if (![jrsAppKitAWTClass respondsToSelector:markAppSel]) return NO;
    return (BOOL)[jrsAppKitAWTClass performSelector:markAppSel];
}

+ (void)appKitIsRunning:(id)arg {
    // Headless: NO
    // Embedded: BOTH
    // Multiple Calls: NO
    //  Callers: AppKit's NSApplicationDidFinishLaunchingNotification or +[AWTStarter startAWT:]
AWT_ASSERT_APPKIT_THREAD;

    BOOL verbose = ShouldPrintVerboseDebugging();
    if (verbose) AWT_DEBUG_LOG(@"about to message AppKit started");

    // Signal that AppKit has started (or is already running).
    pthread_mutex_lock(&sAppKitStarted_mutex);
    sAppKitStarted = YES;
    pthread_cond_signal(&sAppKitStarted_cv);
    pthread_mutex_unlock(&sAppKitStarted_mutex);

    if (verbose) AWT_DEBUG_LOG(@"finished messaging AppKit started");
}

+ (void)start:(BOOL)headless swtMode:(BOOL)swtMode swtModeForWebStart:(BOOL)swtModeForWebStart
{
    BOOL verbose = ShouldPrintVerboseDebugging();

    // Headless: BOTH
    // Embedded: BOTH
    // Multiple Calls: NO
    //  Caller: JNI_OnLoad

    // onMainThread is NOT the same at SWT mode!
    // If the JVM was started on the first thread for SWT, but the SWT loads the AWT on a secondary thread,
    // onMainThread here will be false but SWT mode will be true.  If we are currently on the main thread, we don't
    // need to throw AWT startup over to another thread.
    BOOL onMainThread = (pthread_main_np() != 0);

    if (verbose) {
        NSString *msg = [NSString stringWithFormat:@"+[AWTStarter start headless:%d swtMode:%d swtModeForWebStart:%d] { onMainThread:%d }", headless, swtMode, swtModeForWebStart, onMainThread];
        AWT_DEBUG_LOG(msg);
    }

    if (!headless)
    {
        // Listen for the NSApp to start. This indicates that JNI_OnLoad can proceed.
        //  It must wait because there is a chance that another java thread will grab
        //  the AppKit lock before the +[NSApplication sharedApplication] returns.
        //  See <rdar://problem/3492666> for an example.
        [[NSNotificationCenter defaultCenter] addObserver:[AWTStarter class]
                                                 selector:@selector(appKitIsRunning:)
                                                     name:NSApplicationDidFinishLaunchingNotification
                                                   object:nil];

        if (verbose) NSLog(@"+[AWTStarter start:::]: registered NSApplicationDidFinishLaunchingNotification");
    }

    id st = [[AWTStarter alloc] init];

    NSArray * args = [NSArray arrayWithObjects:
                      [NSNumber numberWithBool: onMainThread],
                      [NSNumber numberWithBool: swtMode],
                      [NSNumber numberWithBool: headless],
                      [NSNumber numberWithBool: swtModeForWebStart],
                      [NSNumber numberWithBool: verbose],
                      nil];

    if (onMainThread) {
        [st starter:args];
    } else {
        [st performSelectorOnMainThread: @selector(starter:) withObject:args waitUntilDone:NO];
    }

    if (!headless && !onMainThread) {
        if (verbose) AWT_DEBUG_LOG(@"about to wait on AppKit startup mutex");

        // Wait here for AppKit to have started (or for AWT to have been loaded into
        //  an already running NSApplication).
        pthread_mutex_lock(&sAppKitStarted_mutex);
        while (sAppKitStarted == NO) {
            pthread_cond_wait(&sAppKitStarted_cv, &sAppKitStarted_mutex);
        }
        pthread_mutex_unlock(&sAppKitStarted_mutex);

        // AWT gets here AFTER +[AWTStarter appKitIsRunning:] is called.
        if (verbose) AWT_DEBUG_LOG(@"got out of the AppKit startup mutex");
    }
}

- (void)starter:(NSArray*)args {
    NSAutoreleasePool *pool = [NSAutoreleasePool new];

    BOOL onMainThread = [[args objectAtIndex:0] boolValue];
    BOOL swtMode = [[args objectAtIndex:1] boolValue];
    BOOL headless = [[args objectAtIndex:2] boolValue];
    BOOL swtModeForWebStart = [[args objectAtIndex:3] boolValue];
    BOOL verbose = [[args objectAtIndex:4] boolValue];

    BOOL wasOnMainThread = onMainThread;

    setUpAWTAppKit(swtMode, headless);

    // Headless mode trumps either ordinary AWT or SWT-in-AWT mode.  Declare us a daemon and return.
    if (headless) {
        BOOL didBecomeDaemon = [AWTStarter markAppAsDaemon];
        return;
    }

    if (swtMode || swtModeForWebStart) {
        if (verbose) NSLog(@"in SWT or SWT/WebStart mode");

        // The SWT should call NSApplicationLoad, but they don't know a priori that they will be using the AWT, so they don't.
        NSApplicationLoad();
    }

    // This will create a NSApplicationAWT for standalone AWT programs, unless there is
    //  already a NSApplication instance. If there is already a NSApplication instance,
    //  and -[NSApplication isRunning] returns YES, AWT is embedded inside another
    //  AppKit Application.
    NSApplication *app = [NSApplicationAWT sharedApplication];

    // Don't set the delegate until the NSApplication has been created.
    //  ApplicationDelegate is the support code for com.apple.eawt.
    OSXAPP_SetApplicationDelegate([ApplicationDelegate sharedDelegate]);

    // AWT gets to this point BEFORE NSApplicationDidFinishLaunchingNotification is sent.
    if (![app isRunning]) {
        if (verbose) AWT_DEBUG_LOG(@"+[AWTStarter startAWT]: ![app isRunning]");

        // This is where the AWT AppKit thread parks itself to process events.
        [NSApplicationAWT runAWTLoopWithApp: app];
    } else {
        // We're either embedded, or showing a splash screen
        if (![NSApp isKindOfClass:[NSApplicationAWT class]]) {
            if (verbose) AWT_DEBUG_LOG(@"running embedded");

            // Since we're embedded, no need to be swamping the runloop with the observers.
            CFRunLoopRef runLoop = [[NSRunLoop currentRunLoop] getCFRunLoop];
            CFRunLoopRemoveObserver(runLoop, busyObserver, kCFRunLoopDefaultMode);
            CFRunLoopRemoveObserver(runLoop, notBusyObserver, kCFRunLoopDefaultMode);

            busyObserver = NULL;
            notBusyObserver = NULL;
        } else {
            if (verbose) AWT_DEBUG_LOG(@"running after showing a splash screen");
        }

        // Signal so that JNI_OnLoad can proceed.
        if (!wasOnMainThread) [AWTStarter appKitIsRunning:nil];

        // Proceed to exit this call as there is no reason to run the NSApplication event loop.
    }

    [pool drain];
}

@end


JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    BOOL verbose = ShouldPrintVerboseDebugging();
    if (verbose) AWT_DEBUG_LOG(@"entered JNI_OnLoad");

    // Headless: BOTH
    // Embedded: BOTH
    // Multiple Calls: NO
    //  Caller: JavaVM classloader

    // Keep a static reference for other archives.
    OSXAPP_SetJavaVM(vm);

    JNIEnv *env = NULL;

    // Need JNIEnv for JNF_COCOA_ENTER(env); macro below
    jint status = (*jvm)->GetEnv(jvm, (void **)&env, JNI_VERSION_1_4);
    if (status != JNI_OK || env == NULL) {
        AWT_DEBUG_LOG(@"Can't get JNIEnv");
        return JNI_VERSION_1_4;
    }

    // The following is true when AWT is attempting to connect to the window server
    // when it isn't set up properly to do so.
    // BOOL AWTLoadFailure = YES;    For now we are skipping this check so i'm commenting out this variable as unused
JNF_COCOA_ENTER(env);

    // If -XstartOnFirstThread was used at invocation time, an environment variable will be set.
    // (See java_md.c for the matching setenv call.) When that happens, we assume the SWT will be in use.
    BOOL swt_compatible_mode = NO;

    char envVar[80];
    snprintf(envVar, sizeof(envVar), "JAVA_STARTED_ON_FIRST_THREAD_%d", getpid());
    if (getenv(envVar) != NULL) {
        swt_compatible_mode = YES;
        unsetenv(envVar);
    }

    BOOL swt_in_webstart = isSWTInWebStart(env);
    BOOL headless = isHeadless(env);

    // Make sure we're on the right thread.  If not, we still need the JNIEnv to throw an exception.
    if (pthread_main_np() != 0 && !swt_compatible_mode && !headless) {
        AWT_DEBUG_LOG(@"Apple AWT Java VM was loaded on first thread -- can't start AWT.");
        [JNFException raise:env as:kInternalError reason:"Can't start the AWT because Java was started on the first thread.  Make sure StartOnFirstThread is "
         "not specified in your application's Info.plist or on the command line"];
        return JNI_VERSION_1_4;
    }

    // We need to let Foundation know that this is a multithreaded application, if it isn't already.
    if (![NSThread isMultiThreaded]) {
        [NSThread detachNewThreadSelector:nil toTarget:nil withObject:nil];
    }

//    if (swt_compatible_mode || headless || [AWTStarter isConnectedToWindowServer] || [AWTStarter isRemoteSession]) {
// No need in this check - we will try to launch AWTStarter anyways - to be able to run GUI application remotely
//        AWTLoadFailure = NO;

    [AWTStarter start:headless swtMode:swt_compatible_mode swtModeForWebStart:swt_in_webstart];

//    }

/*    if (AWTLoadFailure) { // We will not reach this code anyways
        [JNFException raise:env as:kInternalError reason:"Can't connect to window server - not enough permissions."];
    } */

JNF_COCOA_EXIT(env);

    if (verbose) AWT_DEBUG_LOG(@"exiting JNI_OnLoad");

    return JNI_VERSION_1_4;
}
