/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "jvmti_thread.hpp"

extern "C" {

/* ============================================================================= */

#define VTHREAD_CNT   10

static const char* CTHREAD_NAME_START = "ForkJoinPool";
static const int CTHREAD_NAME_START_LEN = (int)strlen("ForkJoinPool");

static jvmtiEnv *jvmti = nullptr;
static jrawMonitorID agent_event_lock = nullptr;
static volatile jthread agent_thread = nullptr;
static jthread tested_vthreads[VTHREAD_CNT];
static int vthread_no = 0;

static jmethodID *test_methods = nullptr;
jint test_method_count = 0;
jclass test_class = nullptr;

static void
set_or_clear_breakpoint(JNIEnv *jni, jboolean set, const char *methodName,
                     jclass klass, jmethodID methods[], int method_count) {
  jlocation location = (jlocation)0L;
  jmethodID method = nullptr;
  jvmtiError err;

  // Find the jmethodID of the specified method
  while (--method_count >= 0) {
    jmethodID meth = methods[method_count];
    char* mname = get_method_name(jvmti, jni, meth);

    if (strcmp(mname, methodName) == 0) {
      LOG("setupBreakpoint: found method %s() to %s a breakpoint\n", mname, set ? "set" : "clear");
      method = meth;
    }
    deallocate(jvmti, jni, (void*)mname);
  }
  if (method == nullptr) {
     LOG("setupBreakpoint: not found method %s() to %s a breakpoint\n",
             methodName, set ? "set" : "clear");
      jni->FatalError("Error in setupBreakpoint: not found method");
  }

  if (set) {
      err = jvmti->SetBreakpoint(method, location);
  } else {
      err = jvmti->ClearBreakpoint(method, location);
  }
  check_jvmti_status(jni, err, "setupBreakpoint: error in JVMTI SetBreakpoint");


}

static void
set_breakpoint(JNIEnv *jni, const char *methodName,
              jclass klass, jmethodID methods[], int method_count) {
  set_or_clear_breakpoint(jni, JNI_TRUE, methodName, klass, methods, method_count);
}

static void
clear_breakpoint(JNIEnv *jni, const char *methodName,
                jclass klass, jmethodID methods[], int method_count) {
  set_or_clear_breakpoint(jni, JNI_FALSE, methodName, klass, methods, method_count);
}

JNIEXPORT void JNICALL
Java_SuspendResumeAll_setBreakpoint(JNIEnv *jni, jclass klass, jclass testKlass) {
  jvmtiError err;

  LOG("setBreakpoint: started\n");

  test_class = (jclass)jni->NewGlobalRef(testKlass);
  err = jvmti->GetClassMethods(testKlass, &test_method_count, &test_methods);
  check_jvmti_status(jni, err, "setBreakpoint: error in JVMTI GetClassMethods for testKlass");

  set_breakpoint(jni, "breakpointCheck", testKlass, test_methods, test_method_count);

  LOG("setBreakpoint: finished\n");
}


static jint
get_cthreads(JNIEnv* jni, jthread** cthreads_p) {
  jthread* tested_cthreads = nullptr;
  jint all_cnt = 0;
  jint ct_cnt = 0;

  jvmtiError err = jvmti->GetAllThreads(&all_cnt, &tested_cthreads);
  check_jvmti_status(jni, err, "get_cthreads: error in JVMTI GetAllThreads");

  for (int idx = 0; idx < all_cnt; idx++) {
    jthread thread = tested_cthreads[idx];
    char* tname = get_thread_name(jvmti, jni, thread);

    if (strncmp(tname, CTHREAD_NAME_START, CTHREAD_NAME_START_LEN) != 0) {
      continue;
    }
    tested_cthreads[ct_cnt++] = thread;
    deallocate(jvmti, jni, (void*)tname);
  }
  *cthreads_p = tested_cthreads;
  return ct_cnt;
}

static void
check_suspended_state(JNIEnv* jni, jthread thread, int thr_idx, char* tname, const char* func_name) {
  void *thread_p = (void*)thread;
  jint state = 0;

  jvmtiError err = jvmti->GetThreadState(thread, &state);
  check_jvmti_status(jni, err, "check_suspended_state: error in JVMTI GetThreadState");

  LOG("## Agent: thread[%d] %p %s: state after suspend: %s (%d)\n",
      thr_idx, thread_p,  tname, TranslateState(state), (int)state);

  if ((state & (JVMTI_THREAD_STATE_SUSPENDED | JVMTI_THREAD_STATE_TERMINATED)) == 0) {
    LOG("\n## Agent: FAILED: %s did not turn on SUSPENDED flag:\n"
        "#  state: %s (%d)\n\n", func_name, TranslateState(state), (int)state);
    set_agent_fail_status();
  }
}

static void
check_resumed_state(JNIEnv* jni, jthread thread, int thr_idx, char* tname, const char* func_name) {
  void *thread_p = (void*)thread;
  jint state = 0;

  jvmtiError err = jvmti->GetThreadState(thread, &state);
  check_jvmti_status(jni, err, "check_resumed_state: error in JVMTI GetThreadState");

  LOG("## Agent: thread[%d] %p %s: state after resume: %s (%d)\n",
      thr_idx, thread_p, tname, TranslateState(state), (int)state);

  if (!((state & (JVMTI_THREAD_STATE_SUSPENDED | JVMTI_THREAD_STATE_TERMINATED)) == 0)) {
    LOG("\n## Agent: FAILED: %s did not turn off SUSPENDED flag:\n"
        "#   state: %s (%d)\n\n", func_name, TranslateState(state), (int)state);
    set_agent_fail_status();
  }
}

static void
test_vthread_suspend_all(JNIEnv* jni, const jthread* thread_list, int suspend_mask) {
  LOG("\n## Agent: test_vthread_suspend_all started\n");

  const jint EXCLUDE_CNT = 2;
  jthread exclude_list[EXCLUDE_CNT] = { nullptr, nullptr };
  for (int idx = 0; idx < EXCLUDE_CNT; idx++) {
    exclude_list[idx] = thread_list[idx];
  }

  jvmtiError err = jvmti->SuspendAllVirtualThreads(EXCLUDE_CNT, exclude_list);
  check_jvmti_status(jni, err, "test_vthread_suspend_all: error in JVMTI SuspendAllVirtualThreads");

  for (int idx = 0; idx < VTHREAD_CNT; idx++) {
    jthread thread = thread_list[idx];
    char* tname = get_thread_name(jvmti, jni, thread);

    if (idx < EXCLUDE_CNT && ((1 << idx) & suspend_mask) == 0) {
      // thread is in exclude list and initially resumed: expected to remain resumed
      check_resumed_state(jni, thread, idx, tname, "SuspendAllVirtualThreads");

      err = jvmti->SuspendThread(thread);
      check_jvmti_status(jni, err, "test_vthread_suspend_all: error in JVMTI SuspendThread");
    } else {
      // thread is not in exclude list or was initially suspended: expected to be suspended
      check_suspended_state(jni, thread, idx, tname, "SuspendAllVirtualThreads");
    }
    deallocate(jvmti, jni, (void*)tname);
  }
  LOG("\n## Agent: test_vthread_suspend_all finished\n");
}

static void
test_vthread_resume_all(JNIEnv* jni, const jthread* thread_list, int suspend_mask) {
  jvmtiError err;
  LOG("\n## Agent: test_vthread_resume_all started\n");

  const jint EXCLUDE_CNT = 2;
  jthread exclude_list[EXCLUDE_CNT] = { nullptr, nullptr };
  for (int idx = 0; idx < EXCLUDE_CNT; idx++) {
    exclude_list[idx] = thread_list[idx];
    // Enable Breakpoint events on excluded thread
    err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, thread_list[idx]);
    check_jvmti_status(jni, err, "enableEvents: error in JVMTI SetEventNotificationMode: enable BREAKPOINT");
  }

  err = jvmti->ResumeAllVirtualThreads(EXCLUDE_CNT, exclude_list);
  check_jvmti_status(jni, err, "test_vthread_resume_all: error in JVMTI ResumeAllVirtualThreads");

  // wait a second to give the breakpoints a chance to be hit.
#ifdef WINDOWS
  Sleep(1000);
#else
  sleep(1);
#endif

  for (int idx = 0; idx < EXCLUDE_CNT; idx++) {
    // Disable Breakpoint events on excluded thread
    err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_BREAKPOINT, thread_list[idx]);
    check_jvmti_status(jni, err, "enableEvents: error in JVMTI SetEventNotificationMode: enable BREAKPOINT");
  }

  for (int idx = 0; idx < VTHREAD_CNT; idx++) {
    jthread thread = thread_list[idx];
    char* tname = get_thread_name(jvmti, jni, thread);

    // The exclude list consists of vthreads #0 and #1, so these two vthreads were not resumed.
    // If they expected to be suspended then resume them explicitly here.
    if (idx < EXCLUDE_CNT && ((1 << idx) & suspend_mask) != 0) {
      // thread is in exclude list and suspended: expected to remain suspended
      check_suspended_state(jni, thread, idx, tname, "ResumeAllVirtualThreads");

      err = jvmti->ResumeThread(thread); // now resume the thread from exclude list
      check_jvmti_status(jni, err, "test_vthread_resume_all: error in JVMTI ResumeThread");
    }
    // thread is expected to be resumed now
    check_resumed_state(jni, thread, idx, tname, "ResumeAllVirtualThreads");

    deallocate(jvmti, jni, (void*)tname);
  }
  LOG("\n## Agent: test_vthread_resume_all: finished\n");
}

static void
check_threads_resumed_state(JNIEnv* jni, const jthread* thread_list, int thread_cnt) {
  LOG("\n## Agent: check_all_vthreads_resumed_state started\n");
  for (int idx = 0; idx < thread_cnt; idx++) {
    jthread thread = thread_list[idx];
    char* tname = get_thread_name(jvmti, jni, thread);

    check_resumed_state(jni, thread, idx, tname, "<Final-Sanity-Check>");
    deallocate(jvmti, jni, (void*)tname);
  }
  LOG("\n## Agent: check_threads_resumed_state: finished\n");
}

JNIEXPORT void JNICALL
Java_SuspendResumeAll_TestSuspendResume(JNIEnv* jni, jclass cls) {
  jthread* tested_cthreads = nullptr;
  jint cthread_cnt = get_cthreads(jni, &tested_cthreads);

  LOG("\n## TestSuspendResume: started\n");

  test_vthread_suspend_all(jni, tested_vthreads, 0x0);
  test_vthread_resume_all(jni, tested_vthreads, 0xFFFFFFFF);

  LOG("\n\n## TestSuspendResume: Check all virtual threads are resumed\n");
  check_threads_resumed_state(jni, tested_vthreads, VTHREAD_CNT);

  LOG("\n\n## TestSuspendResume: Check all carrier threads are resumed\n");
  check_threads_resumed_state(jni, tested_cthreads, cthread_cnt);

  for (int i = 0; i < VTHREAD_CNT; i++) {
    jni->DeleteGlobalRef(tested_vthreads[i]);
  }
  LOG("\n## TestSuspendResume: finished\n");
}

static void JNICALL
VirtualThreadStart(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread) {
  RawMonitorLocker agent_start_locker(jvmti, jni, agent_event_lock);

  tested_vthreads[vthread_no++] = jni->NewGlobalRef(vthread);
}

static void JNICALL
Breakpoint(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread,
           jmethodID method, jlocation location) {
  char* mname = get_method_name(jvmti, jni, method);

  jint state = 0;
  jvmtiError err = jvmti->GetThreadState(thread, &state);
  check_jvmti_status(jni, err, "Breakpoint: error in JVMTI GetThreadState");
  LOG("## Agent: Breakpoint state(0x%x) %s\n", (int)state, TranslateState(state));

  if ((state & JVMTI_THREAD_STATE_SUSPENDED) != 0) {
    LOG("\n## ERROR: Breakpoint: suspended thread is running\n");
    set_agent_fail_status();
  }

  // Turn off breakpoints notificatons for this thread.
  err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_BREAKPOINT, thread);
  if (err != JVMTI_ERROR_NONE) {
    LOG("SetEventNotificationMode error: error in Breakpoint: %s (%d)\n",
           TranslateError(err), err);
  }
  deallocate(jvmti, jni, (void*)mname);
}

JNIEXPORT jint JNICALL
Java_SuspendResumeAll_GetStatus(JNIEnv* jni, jclass cls) {
  return get_agent_status();
}

jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jvmtiError err;

  LOG("Agent init started\n");

  /* create JVMTI environment */
  if (jvm->GetEnv((void **)(&jvmti), JVMTI_VERSION) != JNI_OK) {
    LOG("Agent init: error in getting JvmtiEnv with GetEnv\n");
    return JNI_ERR;
  }

  err = init_agent_data(jvmti, &agent_data);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent init: error in init_agent_data: %s (%d)\n",
           TranslateError(err), err);
    return JNI_ERR;
  }

  /* add specific capabilities for suspending thread */
  jvmtiCapabilities suspendCaps;
  jvmtiEventCallbacks callbacks;

  memset(&suspendCaps, 0, sizeof(suspendCaps));
  suspendCaps.can_generate_breakpoint_events = 1;
  suspendCaps.can_suspend = 1;
  suspendCaps.can_support_virtual_threads = 1;

  err = jvmti->AddCapabilities(&suspendCaps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent init: error in JVMTI AddCapabilities: %s (%d)\n",
           TranslateError(err), err);
    set_agent_fail_status();
    return JNI_ERR;
  }

  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.VirtualThreadStart = &VirtualThreadStart;
  callbacks.Breakpoint  = &Breakpoint;

  err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent init: error in JVMTI SetEventCallbacks: %s (%d)\n",
           TranslateError(err), err);
    set_agent_fail_status();
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                                        JVMTI_EVENT_VIRTUAL_THREAD_START, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent init: error in JVMTI SetEventNotificationMode: %s (%d)\n",
           TranslateError(err), err);
    set_agent_fail_status();
   return JNI_ERR;
  }

  agent_event_lock = create_raw_monitor(jvmti, "_agent_event_lock");

  LOG("Agent init finished\n");
  return JNI_OK;
}

/** Agent library initialization. */
JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

}
