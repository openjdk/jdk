/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include <cstring>
#include <jvmti.h>
#include <atomic>
#include "jvmti_common.hpp"

extern "C" {

const int MAX_COUNT = 50;
static jvmtiEnv *jvmti = nullptr;
static std::atomic<int> thread_end_cnt(0);
static std::atomic<int> thread_unmount_cnt(0);
static std::atomic<int> thread_mount_cnt(0);

void JNICALL VirtualThreadEnd(jvmtiEnv *jvmti, JNIEnv* jni, jthread vthread) {
  thread_end_cnt++;
}

void JNICALL VirtualThreadMount(jvmtiEnv* jvmti, ...) {
  jvmtiError err;
  jint count = 0;
  jvmtiFrameInfo frameInfo[MAX_COUNT];

  va_list ap;
  JNIEnv* jni = nullptr;
  jthread thread = nullptr;

  va_start(ap, jvmti);
  jni = va_arg(ap, JNIEnv*);
  thread = va_arg(ap, jthread);
  va_end(ap);

  err = jvmti->GetStackTrace(thread, 0, MAX_COUNT, frameInfo, &count);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetStackTrace call");

  // Verify top 3 methods to filter events from Continuation::try_preempt().
  const int verification_count = 3;
  const char* expected_methods[verification_count] = {"yieldContinuation", "park", "parkVirtualThread"};

  if (count < verification_count) return;

  for (int idx = 0; idx < verification_count; idx++) {
    char* methodName = get_method_name(jvmti, jni, frameInfo[idx].method);
    if (strcmp(methodName, expected_methods[idx]) != 0) {
      deallocate(jvmti, jni, methodName);
      return;
    }
    deallocate(jvmti, jni, methodName);
  }
  thread_mount_cnt++;
}

void JNICALL VirtualThreadUnmount(jvmtiEnv* jvmti, ...) {
  jvmtiError err;
  jint count = 0;
  jvmtiFrameInfo frameInfo[MAX_COUNT];

  va_list ap;
  JNIEnv* jni = nullptr;
  jthread thread = nullptr;

  va_start(ap, jvmti);
  jni = va_arg(ap, JNIEnv*);
  thread = va_arg(ap, jthread);
  va_end(ap);

  err = jvmti->GetStackTrace(thread, 0, MAX_COUNT, frameInfo, &count);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetStackTrace call");

  // Verify top 3 methods to filter events from Continuation::try_preempt().
  const int verification_count = 3;
  const char* expected_methods[verification_count] = {"run", "enter0", "enter"};

  if (count < verification_count) return;

  for (int idx = 0; idx < verification_count; idx++) {
    char* methodName = get_method_name(jvmti, jni, frameInfo[idx].method);
    if (strcmp(methodName, expected_methods[idx]) != 0) {
      deallocate(jvmti, jni, methodName);
      return;
    }
    deallocate(jvmti, jni, methodName);
  }
  thread_unmount_cnt++;
}

JNIEXPORT jint JNICALL
Java_VThreadEventTest_threadEndCount(JNIEnv* jni, jclass clazz) {
  return thread_end_cnt;
}

JNIEXPORT jint JNICALL
Java_VThreadEventTest_threadMountCount(JNIEnv* jni, jclass clazz) {
  return thread_mount_cnt;
}

JNIEXPORT jint JNICALL
Java_VThreadEventTest_threadUnmountCount(JNIEnv* jni, jclass clazz) {
  return thread_unmount_cnt;
}

JNIEXPORT jint JNICALL
Agent_OnAttach(JavaVM *vm, char *options, void *reserved) {
  jvmtiEventCallbacks callbacks;
  jvmtiCapabilities caps;
  jvmtiError err;
  JNIEnv *env;
  jint res;
  jclass clazz;
  jmethodID mid;

  LOG("Agent_OnAttach started\n");
  if (vm->GetEnv(reinterpret_cast<void **>(&jvmti), JVMTI_VERSION) != JNI_OK || !jvmti) {
    LOG("Could not initialize JVMTI env\n");
    return JNI_ERR;
  }
  memset(&caps, 0, sizeof(caps));
  caps.can_support_virtual_threads = 1;
  check_jvmti_error(jvmti->AddCapabilities(&caps), "AddCapabilities");

  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.VirtualThreadEnd = &VirtualThreadEnd;

  err = jvmti->SetEventCallbacks(&callbacks, (jint)sizeof(callbacks));
  check_jvmti_error(err, "SetEventCallbacks");

  err = set_ext_event_callback(jvmti, "VirtualThreadMount", VirtualThreadMount);
  check_jvmti_error(err, "SetExtEventCallback for VirtualThreadMount");

  err = set_ext_event_callback(jvmti, "VirtualThreadUnmount", VirtualThreadUnmount);
  check_jvmti_error(err, "SetExtEventCallback for VirtualThreadUnmount");

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VIRTUAL_THREAD_END, nullptr);
  check_jvmti_error(err, "SetEventNotificationMode for VirtualThreadEnd");

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, EXT_EVENT_VIRTUAL_THREAD_MOUNT, nullptr);
  check_jvmti_error(err, "SetEventNotificationMode for VirtualThreadMount");

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, EXT_EVENT_VIRTUAL_THREAD_UNMOUNT, nullptr);
  check_jvmti_error(err, "SetEventNotificationMode for VirtualThreadUnmount");

  LOG("vthread events enabled\n");

  // call VThreadEventTest.agentStarted to notify test that agent has started

  res = vm->GetEnv((void **) &env, JNI_VERSION_21);
  if (res != JNI_OK) {
    LOG("GetEnv failed: %d\n", res);
    return JNI_ERR;
  }

  clazz = env->FindClass("VThreadEventTest");
  if (clazz == nullptr) {
      LOG("FindClass failed\n");
      return JNI_ERR;
  }

  mid = env->GetStaticMethodID(clazz, "agentStarted", "()V");
  if (mid == nullptr) {
      LOG("GetStaticMethodID failed\n");
      return JNI_ERR;
  }

  env->CallStaticVoidMethod(clazz, mid);
  if (env->ExceptionOccurred()) {
      LOG("CallStaticVoidMethod failed\n");
      return JNI_ERR;
  }

  LOG("Agent_OnAttach done\n");

  return JVMTI_ERROR_NONE;
}

} // extern "C"

