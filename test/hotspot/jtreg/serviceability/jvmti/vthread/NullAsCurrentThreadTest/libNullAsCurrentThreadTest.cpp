/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>
#include "jvmti.h"
#include "jvmti_common.h"

extern "C" {

static const jint MAX_FRAME_CNT = 30;
static jvmtiEnv *jvmti = nullptr;
static int vt_support_enabled = 0;
static jboolean failed_status = JNI_FALSE;

static void
check(JNIEnv* jni, const char* msg, int err) {
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent: %s failed with error code %d\n", msg, err);
    fatal(jni, msg);
  }
}

static void
checkStackTraces(jvmtiEnv* jvmti, JNIEnv* jni, jvmtiFrameInfo* frames0, jvmtiFrameInfo* frames1, jint cnt) {
  jvmtiError err;

  LOG("Agent: GetStackTrace: current thread frame count: %d\n", cnt);

  for (int idx = 0; idx < cnt; idx++) {
    jmethodID method0 = frames0[idx].method;
    jmethodID method1 = frames1[idx].method;
    char* name0 = nullptr;
    char* name1 = nullptr;
    char* sign0 = nullptr;
    char* sign1 = nullptr;

    err = jvmti->GetMethodName(method0, &name0, &sign0, nullptr);
    check_jvmti_status(jni, err, "GetMethodName");

    if (method0 != method1) {
      err = jvmti->GetMethodName(method1, &name1, &sign1, nullptr);
      check_jvmti_status(jni, err, "GetMethodName");

      failed_status = JNI_TRUE;
      LOG("\t methods at frame depth #%d do not match: %s%s != %s%s\n",
             idx, name0, sign0, name1, sign1);
    }
    LOG("\t%s%s\n", name0, sign0);
    deallocate(jvmti, jni, (void*)name0);
    deallocate(jvmti, jni, (void*)name1);
    deallocate(jvmti, jni, (void*)sign0);
    deallocate(jvmti, jni, (void*)sign1);
  }
  LOG("\n");
}

static void
testGetThreadInfo(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread) {
  jvmtiError err;
  jvmtiThreadInfo inf0;
  jvmtiThreadInfo inf1;

  err = jvmti->GetThreadInfo(nullptr, &inf0);
  check(jni, "GetThreadInfo", err);

  err = jvmti->GetThreadInfo(thread, &inf1);
  check(jni, "GetThreadInfo", err);

  const char* name = (inf0.name == nullptr) ? "<Unnamed thread>" : inf0.name;
  LOG("Agent: GetThreadInfo: current thread: %s\n", name);

  if (strcmp(inf0.name, inf1.name) != 0) {
    failed_status = JNI_TRUE;
    LOG("Agent: GetThreadInfo: current thread names do not match: %s != %s\n",
           inf0.name, inf1.name);
  }
  jobject loader0 = inf0.context_class_loader;
  jobject loader1 = inf1.context_class_loader;

  if (jni->IsSameObject(loader0, loader1) == JNI_FALSE) {
    failed_status = JNI_TRUE;
    LOG("Agent: GetThreadInfo: current thread context class loaders do not match\n");
  }
  if (inf0.priority != inf1.priority) {
    failed_status = JNI_TRUE;
    LOG("Agent: GetThreadInfo: current thread priorities do not match: %d != %d\n",
           inf0.priority, inf1.priority);
  }
  jobject tgrp0 = inf0.thread_group;
  jobject tgrp1 = inf1.thread_group;

  if (jni->IsSameObject(tgrp0, tgrp1) == JNI_FALSE) {
    failed_status = JNI_TRUE;
    LOG("Agent: GetThreadInfo: current thread groups do not match\n");
  }
}

static void
testGetThreadState(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread) {
  jvmtiError err;
  jint state0 = 0;
  jint state1 = 0;

  err = jvmti->GetThreadState(nullptr, &state0);
  check_jvmti_status(jni, err, "GetThreadState");

  err = jvmti->GetThreadState(thread, &state1);
  check_jvmti_status(jni, err, "GetThreadState");

  if (state0 != state1) {
    failed_status = JNI_TRUE;
    LOG("Agent: GetThreadState: current thread states do not match: %xd != %xd\n",
           state0, state1);
  } else {
    LOG("Agent: GetThreadState: current thread state: %0x\n", state0);
  }
}

static void
testGetFrameCount(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread) {
  jvmtiError err;
  jint count0 = 0;
  jint count1 = 0;

  err = jvmti->GetFrameCount(thread, &count0);
  check_jvmti_status(jni, err,"GetFrameCount");

  err = jvmti->GetFrameCount(nullptr, &count1);
  check_jvmti_status(jni, err,"GetFrameCount");

  if (count0 != count1) {
    failed_status = JNI_TRUE;
    LOG("Agent: GetFrameCount: current thread frame counts do not match: %d != %d\n",
           count0, count1);
  } else {
    LOG("Agent: GetFrameCount: current thread frame count: %d\n", count0);
  }
}

static void
testGetFrameLocation(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread) {
  jvmtiError err;
  const jint DEPTH = 1;
  jlocation loc0 = 0;
  jlocation loc1 = 0;
  jmethodID method0 = nullptr;
  jmethodID method1 = nullptr;
  char* name0 = nullptr;
  char* name1 = nullptr;
  char* sign0 = nullptr;
  char* sign1 = nullptr;

  err = jvmti->GetFrameLocation(nullptr, DEPTH, &method0, &loc0);
  check_jvmti_status(jni, err, "GetFrameLocation");

  err = jvmti->GetFrameLocation(thread, DEPTH, &method1, &loc1);
  check_jvmti_status(jni, err, "GetFrameLocation");

  err = jvmti->GetMethodName(method0, &name0, &sign0, nullptr);
  check_jvmti_status(jni, err, "GetMethodName");

  if (method0 != method1) {
    err = jvmti->GetMethodName(method1, &name1, &sign1, nullptr);
    check_jvmti_status(jni, err, "GetMethodName");

    failed_status = JNI_TRUE;
    LOG("Agent: GetFrameLocation: current thread frame #1 methods do not match:\n"
           " %s%s != %s%s\n", name0, sign0, name1, sign1);
  }
  if (loc0 != loc1) {
    failed_status = JNI_TRUE;
    LOG("Agent: GetFrameLocation: current thread frame #1 locations do not match: %lld != %lld\n",
           (long long)loc0, (long long)loc1);
  }
  LOG("Agent: GetFrameLocation: current thread frame: method: %s%s, loc: %lld\n",
         name0, sign0, (long long)loc0);
}

static void
testGetStackTrace(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread) {
  jvmtiError err;
  jvmtiFrameInfo frames0[MAX_FRAME_CNT];
  jvmtiFrameInfo frames1[MAX_FRAME_CNT];
  jint count0 = 0;
  jint count1 = 0;

  memset(frames0, 0, sizeof(frames0));
  memset(frames1, 0, sizeof(frames1));

  err = jvmti->GetStackTrace(nullptr, 0, MAX_FRAME_CNT, frames0, &count0);
  check_jvmti_status(jni, err, "GetStackTrace");

  err = jvmti->GetStackTrace(thread, 0, MAX_FRAME_CNT, frames1, &count1);
  check_jvmti_status(jni, err, "GetStackTrace");

  if (count0 != count1) {
    failed_status = JNI_TRUE;
    LOG("Agent: GetStackTrace: current thread frame counts do not match: %d != %d\n",
           count0, count1);
  }
  checkStackTraces(jvmti, jni, frames0, frames1, count0);
}

static void
testGetOwnedMonitorInfo(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread) {
  jvmtiError err;
  jint count0 = 0;
  jint count1 = 0;
  jobject* monitors0 = nullptr;
  jobject* monitors1 = nullptr;

  err = jvmti->GetOwnedMonitorInfo(thread, &count0, &monitors0);
  check_jvmti_status(jni, err, "GetOwnedMonitorInfo");

  err = jvmti->GetOwnedMonitorInfo(thread, &count1, &monitors1);
  check_jvmti_status(jni, err, "GetOwnedMonitorInfo");

  if (count0 != count1) {
    failed_status = JNI_TRUE;
    LOG("Agent: GetOwnedMonitorInfo: current thread monitors counts do not match: %d != %d\n",
           count0, count1);
  }
  LOG("Agent: GetOwnedMonitorInfo: current thread owns monitors: %d\n", count0);
  for (int idx = 0; idx < count0; idx++) {
    jobject mon0 = monitors0[idx];
    jobject mon1 = monitors1[idx];

    if (jni->IsSameObject(mon0, mon1) == JNI_FALSE) {
      failed_status = JNI_TRUE;
      LOG("Agent: GetOwnedMonitorInfo: current thread monitors #%d do not match\n", idx);
    }
    LOG("\t monitor #%d: %p\n", idx, (void*)mon0);
  }
  err = jvmti->Deallocate((unsigned char*)monitors0);
  check_jvmti_status(jni, err, "Deallocate");

  err = jvmti->Deallocate((unsigned char*)monitors1);
  check_jvmti_status(jni, err, "Deallocate");
}

static void
testGetOwnedMonitorStackDepthInfo(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread) {
  jvmtiError err;
  jint count0 = 0;
  jint count1 = 0;
  jvmtiMonitorStackDepthInfo* inf0 = nullptr;
  jvmtiMonitorStackDepthInfo* inf1 = nullptr;

  err = jvmti->GetOwnedMonitorStackDepthInfo(nullptr, &count0, &inf0);
  check_jvmti_status(jni, err, "GetOwnedMonitorStackDepthInfo");

  err = jvmti->GetOwnedMonitorStackDepthInfo(thread, &count1, &inf1);
  check_jvmti_status(jni, err, "GetOwnedMonitorStackDepthInfo");

  if (count0 != count1) {
    failed_status = JNI_TRUE;
    LOG("Agent: GetOwnedMonitorStackDepthInfo: current thread monitors counts do not match: %d != %d\n",
           count0, count1);
  }
  LOG("Agent: GetOwnedMonitorStackDepthInfo: current thread owns monitors: %d\n", count0);
  for (int idx = 0; idx < count0; idx++) {
    jvmtiMonitorStackDepthInfo slot0 = inf0[idx];
    jvmtiMonitorStackDepthInfo slot1 = inf1[idx];

    if (jni->IsSameObject(slot0.monitor, slot1.monitor) == JNI_FALSE) {
      failed_status = JNI_TRUE;
      LOG("Agent: GetOwnedMonitorStackDepthInfo: current thread monitors #%d do not match\n", idx);
    }
    if (slot0.stack_depth != slot1.stack_depth) {
      failed_status = JNI_TRUE;
      LOG("Agent: GetOwnedMonitorStackDepthInfo: current thread monitor #%d depths do not match\n", idx);
    }
    LOG("\t monitor #%d at depth %d: %p\n", idx, slot0.stack_depth, (void*)slot0.monitor);
  }
  err = jvmti->Deallocate((unsigned char*)inf0);
  check_jvmti_status(jni, err, "Deallocate");

  err = jvmti->Deallocate((unsigned char*)inf1);
  check_jvmti_status(jni, err, "Deallocate");
}

static void
testGetCurrentContendedMonitor(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread) {
  jvmtiError err;
  jobject monitor0 = nullptr;
  jobject monitor1 = nullptr;

  err = jvmti->GetCurrentContendedMonitor(nullptr, &monitor0);
  check_jvmti_status(jni, err, "GetCurrentContendedMonitor");

  err = jvmti->GetCurrentContendedMonitor(thread, &monitor1);
  check_jvmti_status(jni, err, "GetCurrentContendedMonitor");

  if (jni->IsSameObject(monitor0, monitor1) == JNI_FALSE) {
    failed_status = JNI_TRUE;
    LOG("Agent: GetCurrentContendedMonitor: current thread contended monitors do not match\n");
  } else {
    LOG("Agent: GetCurrentContendedMonitor: current thread has contended monitor: %p\n",
           (void*)monitor0);
  }
}

/*
 * Execute JVMTI functions with nullptr jthread and check the result is correct.
 */
JNIEXPORT void JNICALL
Java_NullAsCurrentThreadTest_testJvmtiFunctions(JNIEnv *jni, jclass cls) {
  jvmtiError err;
  jthread cur_thr = nullptr;

  err = jvmti->GetCurrentThread(&cur_thr);
  check(jni, "GetCurrentThread", err);

  LOG("Testing JMTI functions accepting null jthread as current thread\n");

  testGetThreadInfo(jvmti, jni, cur_thr);
  testGetThreadState(jvmti, jni, cur_thr);
  testGetFrameLocation(jvmti, jni, cur_thr);

  testGetFrameCount(jvmti, jni, cur_thr);
  testGetStackTrace(jvmti, jni, cur_thr);

  testGetOwnedMonitorInfo(jvmti, jni, cur_thr);
  testGetOwnedMonitorStackDepthInfo(jvmti, jni, cur_thr);
  testGetCurrentContendedMonitor(jvmti, jni, cur_thr);
}

JNIEXPORT jboolean JNICALL
Java_NullAsCurrentThreadTest_failedStatus(JNIEnv *env, jclass clas) {
  return failed_status;
}

// Parameters: (jvmtiEnv *jvmti, JNIEnv* jni, jthread thread)
static void JNICALL
VirtualThreadMount(jvmtiEnv *jvmti, ...) {
  LOG("Got VirtualThreadMount event\n");
  fflush(stdout);
}

extern JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiEventCallbacks callbacks;
  jvmtiCapabilities caps;
  jvmtiError err;

  LOG("Agent_OnLoad: started: can_support_virtual_threads: %d\n", vt_support_enabled);
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }

  if (strcmp(options, "EnableVirtualThreadSupport") == 0) {
    vt_support_enabled = 1;
  }

  memset(&callbacks, 0, sizeof(callbacks));
  memset(&caps, 0, sizeof(caps));
  caps.can_support_virtual_threads = vt_support_enabled;
  caps.can_get_owned_monitor_info = 1;
  caps.can_get_owned_monitor_stack_depth_info = 1;
  caps.can_get_current_contended_monitor = 1;

  err = set_ext_event_callback(jvmti, "VirtualThreadMount", VirtualThreadMount);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: Error in JVMTI SetExtEventCallback for VirtualThreadMount: %s(%d)\n",
           TranslateError(err), err);
    return JNI_ERR;
  }

  if (vt_support_enabled) {
    err = jvmti->AddCapabilities(&caps);
    if (err != JVMTI_ERROR_NONE) {
      LOG("Agent_Onload: error in JVMTI AddCapabilities: %d\n", err);
    }
    err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
    if (err != JVMTI_ERROR_NONE) {
      LOG("Agent_OnLoad: error in JVMTI SetEventCallbacks: %d\n", err);
    }
    err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, EXT_EVENT_VIRTUAL_THREAD_MOUNT, nullptr);
    if (err != JVMTI_ERROR_NONE) {
      LOG("Agent_OnLoad: error in JVMTI SetEventNotificationMode: %d\n", err);
    }
  }
  LOG("Agent_OnLoad: finished\n");
  return 0;
}

} // extern "C"
