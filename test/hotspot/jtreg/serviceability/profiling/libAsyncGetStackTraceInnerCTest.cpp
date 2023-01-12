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

  /*
  Expect something like the following output (only the pcs should be different):

  Frame 0: Native frame, method = checkNativeLeaf, bci = 0
  Frame 1: Java frame, method = checkJavaInner, bci = 0
  Frame 2: Stub frame, pc = 0x7ff77bfedd21 (0x7ff77bfedd21)
  Frame 3: CPP frame, pc = 0x7ff7932087b0 (0x7ff7932087b0)
  Frame 4: CPP frame, pc = 0x7ff7933569c9 (0x7ff7933569c9)
  Frame 5: CPP frame, pc = 0x7ff793358891 (0x7ff793358891)
  Frame 6: CPP frame, pc = 0x7ff794830b05 (0x7ff794830b05)
  Frame 7: CPP frame, pc = 0x7ff7948305be (checkCMethod)
  Frame 8: Native frame, method = checkNativeChain, bci = 0
  Frame 9: Java frame, method = main, bci = 0
  Frame 10: Java frame, method = invokeStatic, bci = 10

  with
  C frame:
  C frame: JavaCalls::call_helper(JavaValue*, methodHandle const&, JavaCallArguments*, JavaThread*)
  C frame: jni_invoke_static(JNIEnv_*, JavaValue*, _jobject*, JNICallType, _jmethodID*, JNI_ArgumentPusher*, JavaThread*) [clone .constprop.1]
  C frame: jni_CallStaticBooleanMethodV
  C frame: JNIEnv_::CallStaticBooleanMethod(_jclass*, _jmethodID*, ...)
  C frame: checkCMethod

  Or in slowdebug (due to a lack of inlining):

  Frame 0: Native frame, method = checkNativeLeaf, bci = 0
  Frame 1: Java frame, method = checkJavaInner, bci = 0
  Frame 2: Stub frame, pc = 0x7f2237e8cd21 (0x7f2237e8cd21)
  Frame 3: CPP frame, pc = 0x7f224e8a3d5b (0x7f224e8a3d5b) ((null))
  Frame 4: CPP frame, pc = 0x7f224ed71fa4 (0x7f224ed71fa4) ((null))
  Frame 5: CPP frame, pc = 0x7f224e8a378d (0x7f224e8a378d) ((null))
  Frame 6: CPP frame, pc = 0x7f224e96bfbb (0x7f224e96bfbb) ((null))
  Frame 7: CPP frame, pc = 0x7f224e976e86 (0x7f224e976e86) ((null))
  Frame 8: CPP frame, pc = 0x7f225039da2b (0x7f225039da2b) (_ZNSt5arrayISt4pairIPKcmELm1EE4dataEv)
  Frame 9: CPP frame, pc = 0x7f225039d485 (checkCMethod)
  Frame 10: CPP frame, pc = 0x7f225039d4d6 (checkCMethod)
  Frame 11: Native frame, method = checkNativeChain, bci = 0
  Frame 12: Java frame, method = main, bci = 0
  Frame 13: Java frame, method = invokeStatic, bci = 10

  with
  C frame:
  C frame: JavaCalls::call_helper(JavaValue*, methodHandle const&, JavaCallArguments*, JavaThread*)
  C frame: os::os_exception_wrapper(void (*)(JavaValue*, methodHandle const&, JavaCallArguments*, JavaThread*), JavaValue*, methodHandle const&, JavaCallArguments*, JavaThread*)
  C frame: JavaCalls::call(JavaValue*, methodHandle const&, JavaCallArguments*, JavaThread*)
  C frame: jni_invoke_static(JNIEnv_*, JavaValue*, _jobject*, JNICallType, _jmethodID*, JNI_ArgumentPusher*, JavaThread*)
  C frame: jni_CallStaticBooleanMethodV
  C frame: JNIEnv_::CallStaticBooleanMethod(_jclass*, _jmethodID*, ...)
  C frame: checkCMethod
  C frame: Java_profiling_innerc_ASGSTInnerCTest_checkNativeChain
  */
  ASGST_JavaFrame first_frame = trace.frames[0].java_frame;
  if (first_frame.bci != 0) {
    fprintf(stderr, "chain: The first frame must have a bci of 0 as it is a native frame: %d\n", first_frame.bci);
    return false;
  }
  if (first_frame.method_id == NULL) {
    fprintf(stderr, "chain: The first frame must have a method_id: %p\n", first_frame.method_id);
    return false;
  }

  #ifdef DEBUG
  if (trace.num_frames != 11 && trace.num_frames != 14) {
    fprintf(stderr, "chain: The number of frames must be 11 or 14: %d\n", trace.num_frames);
    return false;
  }
  #else
  if (trace.num_frames != 11) {
    fprintf(stderr, "chain: The number of frames must be 11: %d\n", trace.num_frames);
    return false;
  }
  #endif

  if (trace.num_frames == 11 &&
     (!doesFrameBelongToJavaMethod(trace.frames[0], ASGST_FRAME_NATIVE,
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
        "invokeStatic", "chain frame 10"))) {
    return false;
  }
  if (trace.num_frames == 14 &&
     (!doesFrameBelongToJavaMethod(trace.frames[0], ASGST_FRAME_NATIVE,
        "checkNativeLeaf", "chain frame 0") ||
      !doesFrameBelongToJavaMethod(trace.frames[1], ASGST_FRAME_JAVA,
        "checkJavaInner", "chain frame 1") ||
      !isStubFrame(trace.frames[2], "chain frame 2") ||
      !areFramesCPPFrames(trace.frames, 3, 9, "chain frames 3-7") ||
      !doesFrameBelongToMethod(trace.frames[9], &checkCMethod, "chain frame 9") ||
      !doesFrameBelongToMethod(trace.frames[10], &Java_profiling_innerc_ASGSTInnerCTest_checkNativeChain, "chain frame 10") ||
      !doesFrameBelongToJavaMethod(trace.frames[11], ASGST_FRAME_NATIVE,
        "checkNativeChain", "chain frame 11"),
      !doesFrameBelongToJavaMethod(trace.frames[12], ASGST_FRAME_JAVA,
        "main", "chain frame 12") ||
      !doesFrameBelongToJavaMethod(trace.frames[13], ASGST_FRAME_JAVA,
        "invokeStatic", "chain frame 13"))) {
    return false;
  }

  return true;
}

}
