/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "jvmti_common.hpp"

extern "C" {

#define MAX_FRAMES 100

static jvmtiEnv *jvmti = nullptr;
static int vthread_start_count = 0;
static int vthread_end_count = 0;
static bool status = JNI_TRUE;

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

static void
check_suspended_state(JNIEnv* jni, jthread thread) {
  jint state = 0;

  char* tname = get_thread_name(jvmti, jni, thread);

  jvmtiError err = jvmti->GetThreadState(thread, &state);
  check_jvmti_status(jni, err, "check_suspended_state: error in JVMTI GetThreadState");
  LOG("## Agent: %p %s: state after suspend: %s (%d)\n", thread,  tname, TranslateState(state), (int)state);

  if ((state & (JVMTI_THREAD_STATE_SUSPENDED | JVMTI_THREAD_STATE_TERMINATED)) == 0) {
    LOG("\n## Agent: FAILED: SUSPENDED flag is not set:\n");
    status = JNI_FALSE;
  }
  deallocate(jvmti, jni, (void*)tname);
}

static void
check_resumed_state(JNIEnv* jni, jthread thread) {
  jint state = 0;

  char* tname = get_thread_name(jvmti, jni, thread);

  jvmtiError err = jvmti->GetThreadState(thread, &state);
  check_jvmti_status(jni, err, "check_resumed_state: error in JVMTI GetThreadState");
  LOG("## Agent: %p %s: state after resume: %s (%d)\n", thread,  tname, TranslateState(state), (int)state);

  if ((state & (JVMTI_THREAD_STATE_SUSPENDED | JVMTI_THREAD_STATE_TERMINATED)) != 0) {
    LOG("\n## Agent: FAILED: SUSPENDED flag is set:\n");
    status = JNI_FALSE;
  }
  deallocate(jvmti, jni, (void*)tname);
}

static void
test_unsupported_jvmti_functions(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread, jthreadGroup group) {
  jvmtiCapabilities caps;
  jvmtiError err;
  jboolean is_vthread;
  jthread* threads_ptr = nullptr;
  jthreadGroup* groups_ptr = nullptr;
  jvmtiStackInfo *stack_info;
  jint thread_cnt = 0;
  jint group_cnt = 0;
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

  LOG("Testing GetThreadCpuTime\n");
  err = jvmti->GetThreadCpuTime(vthread, &nanos);
  check_jvmti_error_unsupported_operation(jni, "GetThreadCpuTime", err);

  LOG("Testing RunAgentThread\n");
  err = jvmti->RunAgentThread(vthread, agent_proc, (const void*)nullptr, JVMTI_THREAD_NORM_PRIORITY);
  check_jvmti_error_unsupported_operation(jni, "RunAgentThread", err);

  LOG("Testing GetAllThreads\n");
  err = jvmti->GetAllThreads(&thread_cnt, &threads_ptr);
  check_jvmti_status(jni, err, "test_unsupported_jvmti_functions: error in JVMTI GetAllThreads");
  for (int idx = 0; idx < thread_cnt; idx++) {
    jthread thread = threads_ptr[idx];
    if (jni->IsVirtualThread(thread)) {
      fatal(jni, "GetAllThreads should not include virtual threads");
    }
  }

  LOG("Testing GetAllStackTraces\n");
  err = jvmti->GetAllStackTraces(MAX_FRAMES, &stack_info, &thread_cnt);
  check_jvmti_status(jni, err, "test_unsupported_jvmti_functions: error in JVMTI GetAllStackTraces");
  for (int idx = 0; idx < thread_cnt; idx++) {
    jthread thread = threads_ptr[idx];
    if (jni->IsVirtualThread(thread)) {
      fatal(jni, "GetAllStackTraces should not include virtual threads");
    }
  }

  LOG("Testing GetThreadGroupChildren\n");
  err = jvmti->GetThreadGroupChildren(group, &thread_cnt, &threads_ptr, &group_cnt, &groups_ptr);
  check_jvmti_status(jni, err, "test_unsupported_jvmti_functions: error in JVMTI GetThreadGroupChildren");
  for (int idx = 0; idx < thread_cnt; idx++) {
    jthread thread = threads_ptr[idx];
    if (jni->IsVirtualThread(thread)) {
      fatal(jni, "GetThreadGroupChildren should not include virtual threads");
    }
  }

  LOG("test_unsupported_jvmti_functions: finished\n");
}

static void
test_supported_jvmti_functions(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread) {
  jvmtiError err;

  LOG("test_supported_jvmti_functions: started\n");

  LOG("Testing SuspendThread\n");
  err = jvmti->SuspendThread(vthread);
  check_jvmti_status(jni, err, "test_supported_jvmti_functions: error in JVMTI SuspendThread");
  check_suspended_state(jni, vthread);

  LOG("Testing ResumeThread\n");
  err = jvmti->ResumeThread(vthread);
  check_jvmti_status(jni, err, "test_supported_jvmti_functions: error in JVMTI ResumeThread");
  check_resumed_state(jni, vthread);

  LOG("Testing SuspendAllVirtualThreads\n");
  err = jvmti->SuspendAllVirtualThreads(0, nullptr);
  check_jvmti_status(jni, err, "test_supported_jvmti_functions: error in JVMTI SuspendAllVirtualThreads");
  check_suspended_state(jni, vthread);

  LOG("Testing ResumeAllVirtualThreads\n");
  err = jvmti->ResumeAllVirtualThreads(0, nullptr);
  check_jvmti_status(jni, err, "test_supported_jvmti_functions: error in JVMTI ResumeAllVirtualThreads");
  check_resumed_state(jni, vthread);

  LOG("test_supported_jvmti_functions: finished\n");
}

JNIEXPORT jboolean JNICALL
Java_BoundVThreadTest_testJvmtiFunctions(JNIEnv *jni, jclass cls, jthread vthread, jthreadGroup group) {
  jvmtiError err = JVMTI_ERROR_NONE;
  jthread current;

  LOG("testJvmtiFunctions: started\n");

  test_unsupported_jvmti_functions(jvmti, jni, vthread, group);

  current = get_current_thread(jvmti, jni);
  if (!jni->IsVirtualThread(current)) {
    test_supported_jvmti_functions(jvmti, jni, vthread);
  }

  LOG("testJvmtiFunctions: finished\n");

  return JNI_TRUE;
}

static void JNICALL
VirtualThreadStart(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread) {
  vthread_start_count++;
}

static void JNICALL
VirtualThreadEnd(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread) {
  vthread_end_count++;
}

extern JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiEventCallbacks callbacks;
  jvmtiCapabilities caps;
  jvmtiError err;

  LOG("Agent_OnLoad started\n");
  if (jvm->GetEnv((void **)(&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof (caps));
  caps.can_signal_thread = 1;
  caps.can_pop_frame = 1;
  caps.can_force_early_return = 1;
  caps.can_support_virtual_threads = 1;
  caps.can_get_thread_cpu_time = 1;
  caps.can_get_current_thread_cpu_time = 1;
  caps.can_suspend = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI AddCapabilities: %d\n", err);
    return JNI_ERR;
  }

  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.VirtualThreadStart = &VirtualThreadStart;
  callbacks.VirtualThreadEnd = &VirtualThreadEnd;

  err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI SetEventCallbacks: %d\n", err);
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VIRTUAL_THREAD_START, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI SetEventNotificationMode: %d\n", err);
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VIRTUAL_THREAD_END, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI SetEventNotificationMode: %d\n", err);
    return JNI_ERR;
  }

  LOG("Agent_OnLoad finished\n");
  return JNI_OK;
}

JNIEXPORT jboolean JNICALL
Java_BoundVThreadTest_check(JNIEnv *jni, jclass cls) {
  LOG("\n");
  LOG("check: started\n");

  LOG("check: vthread_start_count: %d\n", vthread_start_count);
  LOG("check: vthread_end_count: %d\n", vthread_end_count);

  if (vthread_start_count == 0) {
    status = JNI_FALSE;
    LOG("FAILED: vthread_start_count == 0\n");
  }
  if (vthread_end_count == 0) {
    status = JNI_FALSE;
    LOG("FAILED: vthread_end_count == 0\n");
  }

  LOG("check: finished\n");
  LOG("\n");
  return status;
}

} // extern "C"
