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
#include <atomic>

#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

static jvmtiEnv *jvmti = nullptr;

static std::atomic<bool> is_completed_test_in_event;

static void
check_jvmti_error_unsupported_operation(JNIEnv* jni, const char* msg, jvmtiError err) {
  if (err != JVMTI_ERROR_UNSUPPORTED_OPERATION) {
    LOG("%s failed: expected JVMTI_ERROR_UNSUPPORTED_OPERATION instead of: %d\n", msg, err);
    fatal(jni, msg);
  }
}

static void
check_jvmti_error_opaque_frame(JNIEnv* jni, const char* msg, jvmtiError err) {
  if (err != JVMTI_ERROR_OPAQUE_FRAME) {
    LOG("%s failed: expected JVMTI_ERROR_OPAQUE_FRAME instead of: %d\n", msg, err);
    fatal(jni, msg);
  }
}

static void JNICALL
agent_proc(jvmtiEnv* jvmti, JNIEnv* jni, void* arg) {
  fatal(jni, "agent function was not expected to be called");
}

JNIEXPORT jboolean JNICALL
Java_VThreadUnsupportedTest_isCompletedTestInEvent(JNIEnv *env, jobject obj) {
  return is_completed_test_in_event.load();
}

/*
 * Execute JVMTI functions that don't support vthreads and check they return error
 * code JVMTI_ERROR_UNSUPPORTED_OPERATION or JVMTI_ERROR_OPAQUE_FRAME correctly.
 */
static void
test_unsupported_jvmti_functions(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread) {
  jvmtiError err;
  jboolean is_vthread;
  jvmtiCapabilities caps;
  void* local_storage_data = nullptr;
  jlong nanos;

  LOG("test_unsupported_jvmti_functions: started\n");

  is_vthread = jni->IsVirtualThread(vthread);
  if (is_vthread != JNI_TRUE) {
    fatal(jni, "IsVirtualThread failed to return JNI_TRUE");
  }

  err = jvmti->GetCapabilities(&caps);
  check_jvmti_status(jni, err, "GetCapabilities");

  if (caps.can_support_virtual_threads != JNI_TRUE) {
    fatal(jni, "Virtual threads are not supported");
  }

  LOG("Testing JVMTI functions which should not accept a virtual thread argument\n");

  LOG("Testing GetThreadCpuTime\n");
  err = jvmti->GetThreadCpuTime(vthread, &nanos);
  check_jvmti_error_unsupported_operation(jni, "GetThreadCpuTime", err);

  jthread cur_thread = get_current_thread(jvmti, jni);
  if (jni->IsVirtualThread(cur_thread)) {
    LOG("Testing GetCurrentThreadCpuTime\n");
    err = jvmti->GetCurrentThreadCpuTime(&nanos);
    check_jvmti_error_unsupported_operation(jni, "GetCurrentThreadCpuTime", err);
  }

  err = jvmti->RunAgentThread(vthread, agent_proc, nullptr, JVMTI_THREAD_NORM_PRIORITY);
  check_jvmti_error_unsupported_operation(jni, "RunAgentThread", err);

  LOG("test_unsupported_jvmti_functions: finished\n");
}

JNIEXPORT jboolean JNICALL
Java_VThreadUnsupportedTest_testJvmtiFunctionsInJNICall(JNIEnv *jni, jobject obj, jthread vthread) {
  jvmtiError err = JVMTI_ERROR_NONE;

  LOG("testJvmtiFunctionsInJNICall: started\n");

  test_unsupported_jvmti_functions(jvmti, jni, vthread);

  LOG("testJvmtiFunctionsInJNICall: finished\n");

  return JNI_TRUE;
}

// Parameters: (jvmtiEnv *jvmti, JNIEnv* jni, jthread thread)
static void JNICALL
VirtualThreadMount(jvmtiEnv *jvmti, ...) {
  va_list ap;
  JNIEnv* jni = nullptr;
  jthread thread = nullptr;

  va_start(ap, jvmti);
  jni = va_arg(ap, JNIEnv*);
  thread = va_arg(ap, jthread);
  va_end(ap);

  LOG("Got VirtualThreadMount event\n");
  fflush(stdout);
  test_unsupported_jvmti_functions(jvmti, jni, thread);

  jlong nanos;
  jvmtiError err = jvmti->GetCurrentThreadCpuTime(&nanos);
  check_jvmti_error_unsupported_operation(jni, "GetCurrentThreadCpuTime", err);

  is_completed_test_in_event.store(true);
}

extern JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiError err;

  LOG("Agent_OnLoad started\n");
  if (jvm->GetEnv((void **)(&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }

  is_completed_test_in_event.store(false);

  err = set_ext_event_callback(jvmti, "VirtualThreadMount", VirtualThreadMount);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: Error in JVMTI SetExtEventCallback for VirtualThreadMount: %s(%d)\n",
           TranslateError(err), err);
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof (caps));
  caps.can_suspend = 1;
  caps.can_pop_frame = 1;
  caps.can_force_early_return = 1;
  caps.can_signal_thread = 1;
  caps.can_support_virtual_threads = 1;
  caps.can_access_local_variables = 1;
  caps.can_get_thread_cpu_time = 1;
  caps.can_get_current_thread_cpu_time = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI AddCapabilities: %d\n", err);
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, EXT_EVENT_VIRTUAL_THREAD_MOUNT, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI SetEventNotificationMode: %d\n", err);
    return JNI_ERR;
  }

  LOG("Agent_OnLoad finished\n");
  return JNI_OK;
}

} // extern "C"
