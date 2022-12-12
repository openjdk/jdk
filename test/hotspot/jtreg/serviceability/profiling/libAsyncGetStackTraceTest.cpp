/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Google and/or its affiliates. All rights reserved.
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

#include <assert.h>
#include <dlfcn.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/time.h>
#include <ucontext.h>
#include "jni.h"
#include "jvmti.h"
#include "profile.h"
#include "util.hpp"



// AsyncGetStackTrace needs class loading events to be turned on!
static void JNICALL OnClassLoad(jvmtiEnv *jvmti, JNIEnv *jni_env,
                                jthread thread, jclass klass) {
}

static void JNICALL OnClassPrepare(jvmtiEnv *jvmti, JNIEnv *jni_env,
                                   jthread thread, jclass klass) {
  // We need to do this to "prime the pump" and get jmethodIDs primed.
  GetJMethodIDs(klass);
}

static void JNICALL OnVMInit(jvmtiEnv *jvmti, JNIEnv *jni_env, jthread thread) {
  jint class_count = 0;

  // Get any previously loaded classes that won't have gone through the
  // OnClassPrepare callback to prime the jmethods for AsyncGetStackTrace.
  JvmtiDeallocator<jclass*> classes;
  jvmtiError err = jvmti->GetLoadedClasses(&class_count, classes.get_addr());
  if (err != JVMTI_ERROR_NONE) {
    fprintf(stderr, "OnVMInit: Error in GetLoadedClasses: %d\n", err);
    return;
  }

  // Prime any class already loaded and try to get the jmethodIDs set up.
  jclass *classList = classes.get();
  for (int i = 0; i < class_count; ++i) {
    GetJMethodIDs(classList[i]);
  }
}

extern "C" {

static
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jint res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION);
  if (res != JNI_OK || jvmti == NULL) {
    fprintf(stderr, "Error: wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  jvmtiError err;
  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_get_line_numbers = 1;
  caps.can_get_source_file_name = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    fprintf(stderr, "AgentInitialize: Error in AddCapabilities: %d\n", err);
    return JNI_ERR;
  }

  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.ClassLoad = &OnClassLoad;
  callbacks.VMInit = &OnVMInit;
  callbacks.ClassPrepare = &OnClassPrepare;

  err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    fprintf(stderr, "AgentInitialize: Error in SetEventCallbacks: %d\n", err);
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_LOAD, NULL);
  if (err != JVMTI_ERROR_NONE) {
    fprintf(stderr, "AgentInitialize: Error in SetEventNotificationMode for CLASS_LOAD: %d\n", err);
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, NULL);
  if (err != JVMTI_ERROR_NONE) {
    fprintf(stderr,
            "AgentInitialize: Error in SetEventNotificationMode for CLASS_PREPARE: %d\n",
            err);
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, NULL);
  if (err != JVMTI_ERROR_NONE) {
    fprintf(
        stderr, "AgentInitialize: Error in SetEventNotificationMode for VM_INIT: %d\n",
        err);
    return JNI_ERR;
  }

  return JNI_OK;
}

JNIEXPORT
jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT
jint JNICALL Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT
jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved) {
  return JNI_VERSION_1_8;
}

static int success = 1;

static void* checkForNonJava(void *arg);

// Check that we can get a stack trace for a non-java thread.
// ucontext in the same method, stack of height 2, as it is called by checkForNonJava
static bool checkForNonJava2() {
  ucontext_t context;
  getcontext(&context);

  const int MAX_DEPTH = 16;
  ASGST_CallTrace trace;
  ASGST_CallFrame frames[MAX_DEPTH];
  trace.frames = frames;

  AsyncGetStackTrace(&trace, MAX_DEPTH, &context,
    ASGST_INCLUDE_C_FRAMES | ASGST_INCLUDE_NON_JAVA_THREADS);
  if (trace.num_frames < 0) {
    fprintf(stderr, "checkForNonJava2: No frames found for non-java thread\n");
    return false;
  }
  if (trace.kind != ASGST_CPP_TRACE) {
    fprintf(stderr, "checkForNonJava2: Expected C kind for non-java thread\n");
    return false;
  }
  if (trace.num_frames != 2) {
    fprintf(stderr, "checkForNonJava2: Expected 2 frames for non-java thread, "
      "but got %d\n", trace.num_frames);
    return false;
  }
  if (!doesFrameBelongToMethod(trace.frames[0], (void*)checkForNonJava2, "checkForNonJava2 frame 0")) {
    return false;
  }
  if (!doesFrameBelongToMethod(trace.frames[1], (void*)checkForNonJava, "checkForNonJava2 frame 1")) {
    return false;
  }
  return true;
}

// Check that we can get a stack trace for a non-java thread without walking C frames
// ucontext in the same method
static bool checkForNonJavaNoCFrames() {
  ucontext_t context;
  getcontext(&context);

  const int MAX_DEPTH = 16;
  ASGST_CallTrace trace;
  ASGST_CallFrame frames[MAX_DEPTH];
  trace.frames = frames;

  AsyncGetStackTrace(&trace, MAX_DEPTH, &context, ASGST_INCLUDE_NON_JAVA_THREADS);
  if (trace.num_frames != 0) {
    fprintf(stderr, "checkForNonJavaNoCFrames: Frames found for non-java thread\n");
    return false;
  }
  if (trace.kind != ASGST_CPP_TRACE) {
    fprintf(stderr, "checkForNonJavaNoCFrames: Expected C kind for non-java thread\n");
    return false;
  }
  return true;
}

// Check that we can get the right error code if we try to walk a non-java thread without
// ASGST_INCLUDE_NON_JAVA_THREADS enabled
// ucontext in the same method
static bool checkForNonJavaNoJavaFramesIncluded() {
  ucontext_t context;
  getcontext(&context);

  const int MAX_DEPTH = 16;
  ASGST_CallTrace trace;
  ASGST_CallFrame frames[MAX_DEPTH];
  trace.frames = frames;

  AsyncGetStackTrace(&trace, MAX_DEPTH, &context, 0);
  if (trace.num_frames != ASGST_THREAD_NOT_JAVA) {
    fprintf(stderr, "NoJavaFramesIncluded: Found incorrect error code %d\n", trace.num_frames);
    return false;
  }
  if (trace.kind != ASGST_CPP_TRACE) {
    fprintf(stderr, "NoJavaFramesIncluded: Expected C kind for non-java thread\n");
    return false;
  }
  return true;
}


// Check that we can get a stack trace for a non-java thread.
// ucontext in the same method, stack of height 1, starting method of the pthread
static void* checkForNonJava(void *arg) {

  ucontext_t context;
  getcontext(&context);

  const int MAX_DEPTH = 16;
  ASGST_CallTrace trace;
  ASGST_CallFrame frames[MAX_DEPTH];
  trace.frames = frames;



  AsyncGetStackTrace(&trace, MAX_DEPTH, &context,
    ASGST_INCLUDE_C_FRAMES | ASGST_INCLUDE_NON_JAVA_THREADS);
  if (trace.num_frames < 0) {
    fprintf(stderr, "checkForNonJava: No frames found for non-java thread\n");
    return NULL;
  }
  if (trace.kind != ASGST_CPP_TRACE) {
    fprintf(stderr, "checkForNonJava: Expected C kind for non-java thread\n");
    return NULL;
  }
  if (trace.num_frames != 1) {
    fprintf(stderr, "checkForNonJava: Expected 1 frame for non-java thread, but got %d\n", trace.num_frames);
    return NULL;
  }
  doesFrameBelongToMethod(trace.frames[0], (void*)&checkForNonJava, "checkForNonJava frame 0");
  if (!checkForNonJava2() || !checkForNonJavaNoCFrames() || !checkForNonJavaNoJavaFramesIncluded()) {
    return NULL;
  }
  return &success;
}


static bool checkForNonJavaFromThread() {

  pthread_t thread;
  int s = pthread_create(&thread, NULL, &checkForNonJava, NULL);
  if (s != 0) {
    fprintf(stderr, "Failed to create thread\n");
    return false;
  }
  void *result;
  pthread_join(thread, &result);
  return result == &success;
}


bool checkWithSkippedCFrames() {
  const int MAX_DEPTH = 16;
  ASGST_CallTrace trace;
  ASGST_CallFrame frames[MAX_DEPTH];
  trace.frames = frames;
  trace.frame_info = NULL;
  trace.num_frames = 0;

  AsyncGetStackTrace(&trace, MAX_DEPTH, NULL, 0);

  // For now, just check that the first frame is (-3, checkAsyncGetStackTraceCall).
  if (trace.num_frames <= 0) {
    fprintf(stderr, "JNICALL: The num_frames must be positive: %d\n", trace.num_frames);
    return false;
  }

  if (trace.frames[0].type != ASGST_FRAME_NATIVE) {
    fprintf(stderr, "JNICALL: The first frame must be a Java frame: %d\n", trace.frames[0].type);
    return false;
  }

  ASGST_JavaFrame first_frame = trace.frames[0].java_frame;
  if (first_frame.bci != 0) {
    fprintf(stderr, "JNICALL: The first frame must have a bci of 0 as it is a native frame: %d\n", first_frame.bci);
    return false;
  }
  if (first_frame.method_id == NULL) {
    fprintf(stderr, "JNICALL: The first frame must have a method_id: %p\n", first_frame.method_id);
    return false;
  }

  if (trace.num_frames != 3) {
    fprintf(stderr, "JNICALL: The number of frames must be 4: %d\n", trace.num_frames);
    return false;
  }

  if (!doesFrameBelongToJavaMethod(trace.frames[0], ASGST_FRAME_NATIVE,
        "checkAsyncGetStackTraceCall", "JNICALL frame 0") ||
      !doesFrameBelongToJavaMethod(trace.frames[1], ASGST_FRAME_JAVA,
        "main", "JNICALL frame 1") ||
      !doesFrameBelongToJavaMethod(trace.frames[2], ASGST_FRAME_JAVA,
        "invokeStatic", "JNICALL frame 2")) {
    return false;
  }


  ASGST_CallFrame frame = trace.frames[0];
  if (frame.type != ASGST_FRAME_NATIVE) {
    fprintf(stderr, "Native frame is expected to have type %u but instead it is %u\n", ASGST_FRAME_NATIVE, frame.type);
    return false;
  }

  return checkForNonJavaFromThread();
}


JNIEXPORT jboolean JNICALL
Java_profiling_sanity_ASGSTBaseTest_checkAsyncGetStackTraceCall(JNIEnv* env, jclass cls) {
  const int MAX_DEPTH = 16;
  ASGST_CallTrace trace;
  ASGST_CallFrame frames[MAX_DEPTH];
  trace.frames = frames;
  trace.frame_info = NULL;
  trace.num_frames = 0;

  AsyncGetStackTrace(&trace, MAX_DEPTH, NULL, ASGST_INCLUDE_C_FRAMES);

  // For now, just check that the first frame is (-3, checkAsyncGetStackTraceCall).
  if (trace.num_frames <= 0) {
    fprintf(stderr, "JNICALL: The num_frames must be positive: %d\n", trace.num_frames);
    return false;
  }

  if (trace.frames[0].type != ASGST_FRAME_NATIVE) {
    fprintf(stderr, "JNICALL: The first frame must be a Java frame: %d\n", trace.frames[0].type);
    return false;
  }

  ASGST_JavaFrame first_frame = trace.frames[0].java_frame;
  if (first_frame.bci != 0) {
    fprintf(stderr, "JNICALL: The first frame must have a bci of 0 as it is a native frame: %d\n", first_frame.bci);
    return false;
  }
  if (first_frame.method_id == NULL) {
    fprintf(stderr, "JNICALL: The first frame must have a method_id: %p\n", first_frame.method_id);
    return false;
  }

  if (trace.num_frames != 3) {
    fprintf(stderr, "JNICALL: The number of frames must be 4: %d\n", trace.num_frames);
    return false;
  }

  if (!doesFrameBelongToJavaMethod(trace.frames[0], ASGST_FRAME_NATIVE,
        "checkAsyncGetStackTraceCall", "JNICALL frame 0") ||
      !doesFrameBelongToJavaMethod(trace.frames[1], ASGST_FRAME_JAVA,
        "main", "JNICALL frame 1") ||
      !doesFrameBelongToJavaMethod(trace.frames[2], ASGST_FRAME_JAVA,
        "invokeStatic", "JNICALL frame 2")) {
    return false;
  }


  ASGST_CallFrame frame = trace.frames[0];
  if (frame.type != ASGST_FRAME_NATIVE) {
    fprintf(stderr, "Native frame is expected to have type %u but instead it is %u\n", ASGST_FRAME_NATIVE, frame.type);
    return false;
  }

  return checkForNonJavaFromThread() && checkWithSkippedCFrames();
}
}