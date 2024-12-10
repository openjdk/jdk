/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <string.h>
#include "jvmti.h"
#include "jvmti_common.hpp"

#ifdef __cplusplus
extern "C" {
#endif

static jvmtiEnv *jvmti;
static jvmtiCapabilities caps;
static jvmtiEventCallbacks callbacks;
static volatile jint pop_count;
static char* volatile last_notify_method;
static volatile jboolean failed = JNI_FALSE;
static jboolean seenMain = JNI_FALSE;

static jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved);

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
  return JNI_VERSION_9;
}

static void JNICALL
FramePop(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread,
         jmethodID method, jboolean wasPoppedByException) {
  jvmtiError err;
  jclass cls = nullptr;
  char* csig = nullptr;
  char* name = nullptr;

  err = jvmti->GetMethodDeclaringClass(method, &cls);
  check_jvmti_status(jni, err, "FramePop: Failed in JVMTI GetMethodDeclaringClass");

  err = jvmti->GetClassSignature(cls, &csig, nullptr);
  check_jvmti_status(jni, err, "FramePop: Failed in JVMTI GetClassSignature");

  name = get_method_name(jvmti, jni, method);
  LOG("FramePop(%d) event from method: %s %s\n", pop_count + 1, csig, name);

  if (strcmp(name, "main") != 0) { // ignore FRAME_POP for main that comes in as the test exits
    if (strcmp(name, (char*)last_notify_method) != 0) {
      LOG("ERROR: FramePop event is for wrong method: expected %s, got %s\n", last_notify_method, name);
      failed = JNI_TRUE;
    }
  }
  pop_count++;
  deallocate(jvmti, jni, csig);
  deallocate(jvmti, jni, name);
}

static
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jint res;
  jvmtiError err;

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_9);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("GetEnv(JVMTI_VERSION_9) failed error(%d)", res);
    return JNI_ERR;
  }
  err = jvmti->GetPotentialCapabilities(&caps);
  check_jvmti_error(err, "Agent: GetPotentialCapabilities failed");

  err = jvmti->AddCapabilities(&caps);
  check_jvmti_error(err, "Agent: AddCapabilities failed");

  err = jvmti->GetCapabilities(&caps);
  check_jvmti_error(err, "Agent: GetCapabilities failed");

  if (caps.can_generate_frame_pop_events) {
    callbacks.FramePop = &FramePop;
    err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    check_jvmti_error(err, "Agent: SetEventCallbacks failed");
  }
  return JNI_OK;
}

JNIEXPORT jboolean JNICALL
Java_NotifyFramePopStressTest_canGenerateFramePopEvents(JNIEnv *env, jclass cls) {
  return caps.can_generate_frame_pop_events ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_NotifyFramePopStressTest_setFramePopNotificationMode(JNIEnv *env, jclass cl, jthread thread) {
  set_event_notification_mode(jvmti, env, JVMTI_ENABLE, JVMTI_EVENT_FRAME_POP, thread);
}

/*
 * Call NotifyFramePop on the current frame.
 */
JNIEXPORT jboolean JNICALL
Java_NotifyFramePopStressTest_notifyFramePop(JNIEnv *jni, jclass cls, jthread thread) {
  jmethodID method;
  jlocation loc;
  char* name;
  jvmtiError err;
  jboolean isMain;

  err = jvmti->GetFrameLocation(thread, 0, &method, &loc);
  check_jvmti_status(jni, err, "notifyFramePop: Failed in JVMTI GetFrameLocation");

  name = get_method_name(jvmti, jni, method);

  // We only want to do a NotifyFramePop once for the main method. The sole purpose is
  // to force the thread into interpOnly mode, which seems to help the test's timing
  // in a way that makes it more likely to reproduce the issue.
  isMain = (strcmp(name, "main") == 0);
  if (isMain) {
    if (seenMain) {
      deallocate(jvmti, jni, name);
      return JNI_FALSE; // Only do NotifyFramePop once for main()
    } else {
      seenMain = JNI_TRUE;
    }
  }

  err= jvmti->NotifyFramePop(thread, 0);
  if (err == JVMTI_ERROR_OPAQUE_FRAME || err == JVMTI_ERROR_DUPLICATE) {
    //LOG("\nNotifyFramePop for method %s returned acceptable error: %s\n", name, TranslateError(err));
    deallocate(jvmti, jni, name);
    return JNI_FALSE;
  }
  check_jvmti_status(jni, err, "notifyFramePop: Failed in JVMTI notifyFramePop");
  LOG("\nNotifyFramePop called for method %s\n", name);

  if (isMain) {
    LOG("notifyFramePop not counting main method\n");
    deallocate(jvmti, jni, name);
    return JNI_FALSE;
  } else {
    deallocate(jvmti, jni, last_notify_method);
    last_notify_method = name;
    return JNI_TRUE;
  }
}

JNIEXPORT void JNICALL
Java_NotifyFramePopStressTest_suspend(JNIEnv *jni, jclass cls, jthread thread) {
  suspend_thread(jvmti, jni, thread);
}

JNIEXPORT void JNICALL
Java_NotifyFramePopStressTest_resume(JNIEnv *jni, jclass cls, jthread thread) {
  resume_thread(jvmti, jni, thread);
}

JNIEXPORT jint JNICALL
Java_NotifyFramePopStressTest_getPopCount(JNIEnv *env, jclass cls) {
  return pop_count;
}

JNIEXPORT jboolean JNICALL
Java_NotifyFramePopStressTest_failed(JNIEnv *env, jclass cls) {
  return failed;
}

#ifdef __cplusplus
}
#endif
