/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, JetBrains s.r.o.. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"

#include <CoreFoundation/CoreFoundation.h>
#include <CoreServices/CoreServices.h>

static JavaVM *jvm;
// MacOSXWatchService.callback()
static jmethodID        callbackMID;
// The instance of MacOSXWatchService that is associated with this thread
static __thread jobject watchService;

JNIEXPORT void JNICALL
Java_sun_nio_fs_MacOSXWatchService_initIDs(JNIEnv* env, jclass clazz)
{
    if ((*env)->GetJavaVM(env, &jvm) < 0) {
      JNU_ThrowInternalError(env, "GetJavaVM() call failed");
      return;
    }

    callbackMID = (*env)->GetMethodID(env, clazz,
                                      "handleEvents", "(J[Ljava/lang/String;J)V");
}

extern CFStringRef
toCFString(JNIEnv *env, jstring javaString);

static jboolean
convertToJavaStringArray(JNIEnv* env,
                         char **eventPaths,
                         const jsize numEventsToReport,
                         jobjectArray javaEventPathsArray)
{
    for (jsize i = 0; i < numEventsToReport; i++) {
        const jstring path = JNU_NewStringPlatform(env, eventPaths[i]);
        CHECK_NULL_RETURN(path, FALSE);
        (*env)->SetObjectArrayElement(env, javaEventPathsArray, i, path);
    }

    return JNI_TRUE;
}

static void
callJavaCallback(JNIEnv* env,
                 jlong streamRef,
                 jobjectArray javaEventPathsArray,
                 jlong eventFlags)
{
    if (callbackMID != NULL && watchService != NULL) {
        // We are called on the run loop thread, so it's OK to use
        // the thread-local reference to the watch service.
        (*env)->CallVoidMethod(env, watchService, callbackMID,
                               streamRef, javaEventPathsArray, eventFlags);
    }
}

/**
 * Callback that is invoked on the run loop thread and informs of new
 * file-system events from an FSEventStream.
 */
static void
callback(ConstFSEventStreamRef streamRef,
         __unused void *clientCallBackInfo,
         size_t numEventsTotal,
         void *eventPaths,
         const FSEventStreamEventFlags eventFlags[],
         __unused const FSEventStreamEventId eventIds[])
{
    JNIEnv *env = (JNIEnv *) JNU_GetEnv(jvm, JNI_VERSION_1_2);

    // We can get more events at once than the number of Java array elements,
    // so report them in chunks.
    const size_t MAX_EVENTS_TO_REPORT_AT_ONCE = (INT_MAX - 2);

    fprintf(stdout, "WatchService: callback with %ld events from stream %lx\n", numEventsTotal,
    ptr_to_jlong(streamRef));
    fflush(stdout);

    jboolean success = JNI_TRUE;
    for(size_t eventIndex = 0; success && (eventIndex < numEventsTotal); ) {
        const size_t numEventsRemaining = (numEventsTotal - eventIndex);
        const jsize  numEventsToReport  = (numEventsRemaining > MAX_EVENTS_TO_REPORT_AT_ONCE)
                                        ? MAX_EVENTS_TO_REPORT_AT_ONCE
                                        : numEventsRemaining;

        const jboolean localFramePushed
            = ((*env)->PushLocalFrame(env, numEventsToReport + 5) == JNI_OK);
        success = localFramePushed;

        jobjectArray javaEventPathsArray = NULL;
        if (success) {
            javaEventPathsArray = (*env)->NewObjectArray(env,
                                                         (jsize)numEventsToReport,
                                                         JNU_ClassString(env),
                                                         NULL);
            success = (javaEventPathsArray != NULL);
        }

        if (success) {
            success = convertToJavaStringArray(env,
                                               &((char**)eventPaths)[eventIndex],
                                               numEventsToReport,
                                               javaEventPathsArray);
        }

        callJavaCallback(env,
                         ptr_to_jlong(streamRef),
                         javaEventPathsArray,
                         ptr_to_jlong(&eventFlags[eventIndex]));

        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
        }

        if (localFramePushed) {
            (*env)->PopLocalFrame(env, NULL);
        }

        eventIndex += numEventsToReport;
    }
}

/**
 * Creates a new FSEventStream and returns FSEventStreamRef for it.
 */
JNIEXPORT jlong JNICALL
Java_sun_nio_fs_MacOSXWatchService_eventStreamCreate(JNIEnv* env,
                                                     __unused jclass clazz,
                                                     jstring dir,
                                                     jdouble latencyInSeconds,
                                                     jint flags)
{
    const CFStringRef path = toCFString(env, dir);
    CHECK_NULL_RETURN(path, 0);
    const CFArrayRef pathsToWatch = CFArrayCreate(NULL, (const void **) &path,
                                                  1, NULL);
    CHECK_NULL_RETURN(pathsToWatch, 0);

    const FSEventStreamRef stream = FSEventStreamCreate(
            NULL,
            &callback,
            NULL,
            pathsToWatch,
            kFSEventStreamEventIdSinceNow,
            (CFAbsoluteTime) latencyInSeconds,
            flags);

    fprintf(stdout, "WatchService: created event stream %lx\n", ptr_to_jlong(stream));
    fflush(stdout);
    return ptr_to_jlong(stream);
}


/**
 * Schedules the given FSEventStream with the given run loop. Starts the stream
 * so that the run loop can receive events from the stream.
 */
JNIEXPORT void JNICALL
Java_sun_nio_fs_MacOSXWatchService_eventStreamSchedule(__unused JNIEnv* env,
                                                       __unused jclass clazz,
                                                       jlong eventStreamRef,
                                                       jlong runLoopRef)
{
    const FSEventStreamRef stream  = jlong_to_ptr(eventStreamRef);
    const CFRunLoopRef     runLoop = jlong_to_ptr(runLoopRef);

    FSEventStreamScheduleWithRunLoop(stream, runLoop, kCFRunLoopDefaultMode);
    FSEventStreamStart(stream);
    fprintf(stdout, "WatchService: scheduled event stream %lx on run loop %lx\n",
            ptr_to_jlong(stream), ptr_to_jlong(runLoopRef));
    fflush(stdout);
}

/**
 * Performs the steps necessary to dispose of the given FSEventStreamRef.
 * The stream must have been started and scheduled with a run loop.
 */
JNIEXPORT void JNICALL
Java_sun_nio_fs_MacOSXWatchService_eventStreamStop(__unused JNIEnv* env,
                                                   __unused jclass clazz,
                                                   jlong eventStreamRef)
{
    const FSEventStreamRef streamRef = jlong_to_ptr(eventStreamRef);

    FSEventStreamStop(streamRef);       // Unregister with the FS Events service.
                                        // No more callbacks from this stream.
    FSEventStreamInvalidate(streamRef); // De-schedule from any runloops.
    FSEventStreamRelease(streamRef);    // Decrement the stream's refcount.

    fprintf(stdout, "WatchService: stopped event stream %lx\n", ptr_to_jlong(streamRef));
    fflush(stdout);
}

/**
 * Returns the CFRunLoop object for the current thread.
 */
JNIEXPORT jlong JNICALL
Java_sun_nio_fs_MacOSXWatchService_CFRunLoopGetCurrent(__unused JNIEnv* env,
                                                       __unused jclass clazz)
{
    fprintf(stdout, "WatchService: get run loop -> %lx\n",  ptr_to_jlong(CFRunLoopGetCurrent()));
    fflush(stdout);
    return ptr_to_jlong(CFRunLoopGetCurrent());
}

/**
 * Simply calls CFRunLoopRun() to run current thread's run loop for as long as
 * there are event sources attached to it.
 */
JNIEXPORT void JNICALL
Java_sun_nio_fs_MacOSXWatchService_CFRunLoopRun(JNIEnv* env,
                                                __unused jclass clazz,
                                                jlong watchServiceObject)
{
    // Thread-local pointer to the WatchService instance will be used by the callback
    // on this thread.
    watchService = (*env)->NewGlobalRef(env, jlong_to_ptr(watchServiceObject));
    fprintf(stdout, "WatchService: running run loop %lx\n", ptr_to_jlong(CFRunLoopGetCurrent()));
    fflush(stdout);
    CFRunLoopRun();
    (*env)->DeleteGlobalRef(env, watchService);
    watchService = NULL;
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_MacOSXWatchService_CFRunLoopStop(__unused JNIEnv* env,
                                                 __unused jclass clazz,
                                                 jlong runLoopRef)
{
    CFRunLoopStop(jlong_to_ptr(runLoopRef));
    fprintf(stdout, "WatchService: stopped run loop %lx\n", runLoopRef);
    fflush(stdout);
}
