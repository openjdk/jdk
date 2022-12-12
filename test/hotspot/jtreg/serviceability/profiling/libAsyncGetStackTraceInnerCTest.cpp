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


// checkNativeChain() -> checkCMethod() -> checkJavaInner() -> checkNativeLeaf() -> ASGST() chain

// a non JNI method, so we see a C frame
[[gnu::noinline]] __attribute__((noinline)) static bool checkCMethod(JNIEnv* env, jclass cls) {
  jmethodID method = env->GetStaticMethodID(cls, "checkJavaInner", "()Z");
  if (method == NULL) {
    fprintf(stderr, "Failed to get method ID for checkJavaInner\n");
    return false;
  }
  return env->CallStaticBooleanMethod(cls, method);
}

[[gnu::noinline]] __attribute__((noinline)) JNIEXPORT jboolean JNICALL
Java_profiling_innerc_ASGSTInnerCTest_checkNativeChain(JNIEnv* env, jclass cls) {
  return checkCMethod(env, cls);
}

JNIEXPORT jboolean JNICALL
Java_profiling_innerc_ASGSTInnerCTest_checkNativeLeaf(JNIEnv* env, jclass cls) {
  const int MAX_DEPTH = 16;
  ASGST_CallTrace trace;
  ASGST_CallFrame frames[MAX_DEPTH];
  trace.frames = frames;
  trace.frame_info = NULL;
  trace.num_frames = 0;

  // with ASGST_INCLUDE_C_FRAMES

  AsyncGetStackTrace(&trace, MAX_DEPTH, NULL, ASGST_INCLUDE_C_FRAMES);

  if (trace.num_frames <= 0) {
    fprintf(stderr, "chain: The num_frames must be positive: %d\n", trace.num_frames);
    return false;
  }

  if (trace.frames[0].type != ASGST_FRAME_NATIVE) {
    fprintf(stderr, "chain: The first frame must be a Java frame: %d\n", trace.frames[0].type);
    return false;
  }

  printTrace<1>(stdout, trace, {std::make_pair("checkCMethod", (void*)&checkCMethod)});

  ASGST_JavaFrame first_frame = trace.frames[0].java_frame;
  if (first_frame.bci != 0) {
    fprintf(stderr, "chain: The first frame must have a bci of 0 as it is a native frame: %d\n", first_frame.bci);
    return false;
  }
  if (first_frame.method_id == NULL) {
    fprintf(stderr, "chain: The first frame must have a method_id: %p\n", first_frame.method_id);
    return false;
  }

  if (trace.num_frames != 11) {
    fprintf(stderr, "chain: The number of frames must be 12: %d\n", trace.num_frames);
    return false;
  }

  if (!doesFrameBelongToJavaMethod(trace.frames[0], ASGST_FRAME_NATIVE,
        "checkNativeLeaf", "chain frame 0") ||
      !doesFrameBelongToJavaMethod(trace.frames[1], ASGST_FRAME_JAVA,
        "checkJavaInner", "chain frame 1") ||
      !isStubFrame(trace.frames[2], "chain frame 2") ||
      !areFramesCPPFrames(trace.frames, 3, 7, "chain frames 3-5") ||
      !doesFrameBelongToMethod(trace.frames[7], &checkCMethod, "chain frame 7") ||
      !doesFrameBelongToJavaMethod(trace.frames[8], ASGST_FRAME_NATIVE,
        "checkNativeChain", "chain frame 8"),
      !doesFrameBelongToJavaMethod(trace.frames[9], ASGST_FRAME_JAVA,
        "main", "chain frame 9") ||
      !doesFrameBelongToJavaMethod(trace.frames[10], ASGST_FRAME_JAVA,
        "invokeStatic", "chain frame 10")) {
    return false;
  }

  return true;
}

}
