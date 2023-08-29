/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include <cstdlib>
#include <cstring>
#include <jvmti.h>
#include <mutex>
#include "jvmti_common.h"

#ifdef _WIN32
#define VARIADICJNI __cdecl
#else
#define VARIADICJNI JNICALL
#endif

namespace {
  std::mutex lock;
  jvmtiEnv *jvmti = nullptr;
  int thread_end_cnt = 0;
  int thread_unmount_cnt = 0;
  int thread_mount_cnt = 0;

  void JNICALL VirtualThreadEnd(jvmtiEnv *jvmti, JNIEnv* jni, jthread virtual_thread) {
    std::lock_guard<std::mutex> lockGuard(lock);
    thread_end_cnt++;
  }

  void VARIADICJNI VirtualThreadMount(jvmtiEnv* jvmti, ...) {
    std::lock_guard<std::mutex> lockGuard(lock);
    thread_mount_cnt++;
  }

  void VARIADICJNI VirtualThreadUnmount(jvmtiEnv* jvmti, ...) {
    std::lock_guard<std::mutex> lockGuard(lock);
    thread_unmount_cnt++;
  }
}

extern "C" {

void
check_jvmti_err(jvmtiError err, const char* msg) {
  if (err != JVMTI_ERROR_NONE) {
    LOG("Error in JVMTI %s: %s(%d)\n", msg, TranslateError(err), err);
    abort();
  }
}

JNIEXPORT jint JNICALL
Java_VThreadEventTest_threadEndCount(JNIEnv* jni, jclass clazz) {
  std::lock_guard<std::mutex> lockGuard(lock);
  return thread_end_cnt;
}

JNIEXPORT jint JNICALL
Java_VThreadEventTest_threadMountCount(JNIEnv* jni, jclass clazz) {
  std::lock_guard<std::mutex> lockGuard(lock);
  return thread_mount_cnt;
}

JNIEXPORT jint JNICALL
Java_VThreadEventTest_threadUnmountCount(JNIEnv* jni, jclass clazz) {
  std::lock_guard<std::mutex> lockGuard(lock);
  return thread_unmount_cnt;
}

JNIEXPORT jint JNICALL
Agent_OnAttach(JavaVM *vm, char *options, void *reserved) {
  jvmtiEventCallbacks callbacks;
  jvmtiCapabilities caps;
  jvmtiError err;

  LOG("Agent_OnAttach started\n");
  if (vm->GetEnv(reinterpret_cast<void **>(&jvmti), JVMTI_VERSION) != JNI_OK || !jvmti) {
    LOG("Could not initialize JVMTI env\n");
    return JNI_ERR;
  }
  memset(&caps, 0, sizeof(caps));
  caps.can_support_virtual_threads = 1;
  check_jvmti_err(jvmti->AddCapabilities(&caps), "AddCapabilities");

  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.VirtualThreadEnd = &VirtualThreadEnd;

  err = jvmti->SetEventCallbacks(&callbacks, (jint)sizeof(callbacks));
  check_jvmti_err(err, "SetEventCallbacks");

  err = set_ext_event_callback(jvmti, "VirtualThreadMount", VirtualThreadMount);
  check_jvmti_err(err, "SetExtEventCallback for VirtualThreadMount");

  err = set_ext_event_callback(jvmti, "VirtualThreadUnmount", VirtualThreadUnmount);
  check_jvmti_err(err, "SetExtEventCallback for VirtualThreadUnmount");

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VIRTUAL_THREAD_END, nullptr);
  check_jvmti_err(err, "SetEventNotificationMode for VirtualThreadEnd");

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, EXT_EVENT_VIRTUAL_THREAD_MOUNT, nullptr);
  check_jvmti_err(err, "SetEventNotificationMode for VirtualThreadMount");

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, EXT_EVENT_VIRTUAL_THREAD_UNMOUNT, nullptr);
  check_jvmti_err(err, "SetEventNotificationMode for VirtualThreadUnmount");

  LOG("vthread events enabled\n");
  return JVMTI_ERROR_NONE;
}

} // extern "C"

