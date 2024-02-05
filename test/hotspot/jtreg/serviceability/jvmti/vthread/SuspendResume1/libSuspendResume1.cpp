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
#include "jvmti_common.h"
#include "jvmti_thread.h"

extern "C" {

/* ============================================================================= */

#define VTHREAD_CNT   20

static const char* CTHREAD_NAME_START = "ForkJoinPool";
static const int CTHREAD_NAME_START_LEN = (int)strlen("ForkJoinPool");

static jvmtiEnv *jvmti = nullptr;
static jrawMonitorID agent_event_lock = nullptr;
static jthread tested_vthreads[VTHREAD_CNT];
static int vthread_no = 0;


static void
test_get_stack_trace(JNIEnv *jni, jthread thread) {
  print_stack_trace(jvmti, jni, thread);
}

static void
test_get_thread_list_stack_traces(JNIEnv *jni, bool is_virt, jint thread_cnt, jthread* thread_list) {
  jvmtiStackInfo* stack_info_arr = nullptr;

  LOG("## Agent: test_get_thread_list_stack_traces started: is virtual: %d, count: %d\n\n",
         is_virt, thread_cnt);

  jvmtiError err = jvmti->GetThreadListStackTraces(thread_cnt, thread_list,
                                        MAX_FRAME_COUNT_PRINT_STACK_TRACE, &stack_info_arr);
  check_jvmti_status(jni, err, "test_get_thread_list_stack_traces: error in JVMTI GetThreadListStackTraces");

  for (int idx = 0; idx < thread_cnt; idx++) {
    jthread thread = stack_info_arr[idx].thread;

    print_stack_trace(jvmti, jni, thread);
  }
  LOG("## Agent: test_get_thread_list_stack_traces finished: virtual: %d, count: %d\n\n",
         is_virt, thread_cnt);
}

static void
test_get_frame_location(JNIEnv* jni, jthread thread, char* tname) {
  const jint DEPTH = 1;
  jlocation loc = 0;
  jmethodID method = nullptr;
  char* name = nullptr;
  char* sign = nullptr;
  jboolean is_virtual = jni->IsVirtualThread(thread);

  jvmtiError err = jvmti->GetFrameLocation(thread, DEPTH, &method, &loc);
  if (err != JVMTI_ERROR_NONE) {
    if (err != JVMTI_ERROR_NO_MORE_FRAMES) { // TMP work around
      check_jvmti_status(jni, err, "test_get_frame_location: error in JVMTI GetFrameLocation");
    } else {
      LOG("## Agent: test_get_frame_location: ignoring JVMTI_ERROR_NO_MORE_FRAMES in GetFrameLocation\n\n");
    }
    return;
  }
  err = jvmti->GetMethodName(method, &name, &sign, nullptr);
  check_jvmti_status(jni, err, "test_get_frame_location: error in JVMTI GetMethodName");

  LOG("Agent: GetFrameLocation: frame for current thread %s: method: %s%s, loc: %lld\n",
         tname, name, sign, (long long)loc);
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
  jboolean is_virtual = jni->IsVirtualThread(thread);
  const char* tkind = is_virtual ? "virtual" : "carrier";
  jint state = 0;

  jvmtiError err = jvmti->GetThreadState(thread, &state);
  check_jvmti_status(jni, err, "check_suspended_state: error in JVMTI GetThreadState");

  LOG("## Agent: thread[%d] %p %s: state after suspend: %s (%d)\n",
         thr_idx, thread_p,  tname, TranslateState(state), (int)state);

  if ((state & (JVMTI_THREAD_STATE_SUSPENDED | JVMTI_THREAD_STATE_TERMINATED)) == 0) {
    LOG("## Agent: FAILED: %s did not turn on SUSPENDED flag for %s thread:\n"
        "#  state: %s (%d)\n", func_name, tkind, TranslateState(state), (int)state);
    if (!is_virtual) {
      jthread vthread = get_virtual_thread(jvmti, jni, thread);

      err = jvmti->GetThreadState(vthread, &state);
      check_jvmti_status(jni, err, "check_suspended_state: error in JVMTI GetThreadState for vthread");

      LOG("## Agent: %s:  virtual thread of carrier thread has state: %s (%d)\n",
          func_name, TranslateState(state), (int)state);
      fflush(0);
    }
    set_agent_fail_status();
    fatal(jni, "check_resumed_state: expected SUSPENDED flag in thread state");
  }
}

static void
check_resumed_state(JNIEnv* jni, jthread thread, int thr_idx, char* tname, const char* func_name) {
  void *thread_p = (void*)thread;
  jboolean is_virtual = jni->IsVirtualThread(thread);
  const char* tkind = is_virtual ? "virtual" : "carrier";
  jint state = 0;

  jvmtiError err = jvmti->GetThreadState(thread, &state);
  check_jvmti_status(jni, err, "check_resumed_state: error in JVMTI GetThreadState");

  LOG("## Agent: thread[%d] %p %s: state after resume: %s (%d)\n",
         thr_idx, thread_p, tname, TranslateState(state), (int)state);

  if (!((state & (JVMTI_THREAD_STATE_SUSPENDED | JVMTI_THREAD_STATE_TERMINATED)) == 0)) {
    LOG("## Agent: FAILED: %s did not turn off SUSPENDED flag for %s thread:\n"
        "#   state: %s (%d)\n", func_name, tkind, TranslateState(state), (int)state);
    if (!is_virtual) {
      jthread vthread = get_virtual_thread(jvmti, jni, thread);

      err = jvmti->GetThreadState(vthread, &state);
      check_jvmti_status(jni, err, "check_resumed_state: error in JVMTI GetThreadState for vthread");

      LOG("## Agent: %s:  virtual thread of carrier thread has state: %s (%d)\n",
          func_name, TranslateState(state), (int)state);
      fflush(0);
    }
    set_agent_fail_status();
    fatal(jni, "check_resumed_state: NOT expected SUSPENDED flag in thread state");
  }
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

static void
test_thread_suspend(JNIEnv* jni, jthread thread, int thr_idx, char* tname) {
  jvmtiError err = jvmti->SuspendThread(thread);
  check_jvmti_status(jni, err, "test_thread_suspend: error in JVMTI SuspendThread");

  check_suspended_state(jni, thread, thr_idx, tname, "SuspendThread");
}

static void
test_thread_resume(JNIEnv* jni, jthread thread, int thr_idx, char* tname) {
  jvmtiError err = jvmti->ResumeThread(thread);

  if (err == JVMTI_ERROR_THREAD_NOT_SUSPENDED && !jni->IsVirtualThread(thread)) {
    jthread vthread = get_virtual_thread(jvmti, jni, thread);
    jint state = 0;

    err = jvmti->GetThreadState(vthread, &state);
    check_jvmti_status(jni, err, "test_thread_resume: error in JVMTI GetThreadState for vthread");

    LOG("## Agent: test_thread_resume:  virtual thread of carrier thread has state: %s (%d)\n",
        TranslateState(state), (int)state);
    fflush(0);
  }
  check_jvmti_status(jni, err, "test_thread_resume: error in JVMTI ResumeThread");

  check_resumed_state(jni, thread, thr_idx, tname, "ResumeThread");
}

static void
test_thread_suspend_list(JNIEnv* jni, const jthread* thread_list) {
  jvmtiError results[VTHREAD_CNT] = {JVMTI_ERROR_NONE}; // VTHREAD_CNT is max

  LOG("\n## Agent: test_thread_suspend_list started\n");

  jvmtiError err = jvmti->SuspendThreadList(VTHREAD_CNT, thread_list, results);
  check_jvmti_status(jni, err, "test_thread_suspend_list: error in JVMTI SuspendThreadList");

  for (int idx = 0; idx < VTHREAD_CNT; idx++) {
    jthread thread = thread_list[idx];
    char* tname = get_thread_name(jvmti, jni, thread);

    check_suspended_state(jni, thread, idx, tname,"SuspendThreadList");
    deallocate(jvmti, jni, (void*)tname);
  }
  LOG("\n## Agent: test_thread_suspend_list finished\n");
}

static void
test_thread_resume_list(JNIEnv* jni, const jthread* thread_list) {
  jvmtiError results[VTHREAD_CNT] = {JVMTI_ERROR_NONE}; // VTHREAD_CNT is max

  LOG("\n## Agent: test_thread_resume_list: started\n");

  jvmtiError err = jvmti->ResumeThreadList(VTHREAD_CNT, thread_list, results);
  check_jvmti_status(jni, err, "test_thread_resume_list: error in JVMTI ResumeThreadList");

  for (int idx = 0; idx < VTHREAD_CNT; idx++) {
    jthread thread = thread_list[idx];
    char* tname = get_thread_name(jvmti, jni, thread);

    check_resumed_state(jni, thread, idx, tname, "ResumeThreadList");
    deallocate(jvmti, jni, (void*)tname);
  }
  LOG("\n## Agent: test_thread_resume_list: finished\n");
}

static void
test_threads_suspend_resume(JNIEnv* jni, jint thread_cnt, jthread* tested_threads) {

  for (int idx = 0; idx < thread_cnt; idx++) {
    jthread thread = tested_threads[idx];
    char* tname = get_thread_name(jvmti, jni, thread);

    LOG("\n");
    test_thread_suspend(jni, thread, idx, tname);
    test_thread_resume(jni, thread, idx, tname);

    deallocate(jvmti, jni, (void*)tname);
  }
}

static void
test_jvmti_functions_for_one_thread(JNIEnv* jni, jthread thread) {
  jint frame_count = 0;
  char* tname = get_thread_name(jvmti, jni, thread);

  // test JVMTI GetFrameCount
  jvmtiError err = jvmti->GetFrameCount(thread, &frame_count);
  check_jvmti_status(jni, err, "test_jvmti_functions_for_one_thread: error in JVMTI GetStackTrace");

  LOG("## Agent: thread %s frame count: %d\n", tname, frame_count);

  // test JVMTI GetFrameLocation
  test_get_frame_location(jni, thread, tname);

  // test JVMTI GetStackTrace
  test_get_stack_trace(jni, thread);

  deallocate(jvmti, jni, (void*)tname);
}

static void
test_jvmti_functions_for_threads(JNIEnv* jni, bool is_virt, jint thread_cnt, jthread* thread_list) {
  jvmtiError results[VTHREAD_CNT] = {JVMTI_ERROR_NONE}; // VTHREAD_CNT is max

  LOG("\n## Agent: test_jvmti_functions_for_threads started: virtual: %d\n\n", is_virt);


  // iterate over all vthreads
  for (int idx = 0; idx < thread_cnt; idx++) {
    jthread thread = thread_list[idx];
    test_jvmti_functions_for_one_thread(jni, thread);
  }

  // test JVMTI GetTheadListStackTraces
  test_get_thread_list_stack_traces(jni, is_virt, 1, thread_list);          // test with one thread
  test_get_thread_list_stack_traces(jni, is_virt, thread_cnt, thread_list); // test with multiple threads

  LOG("\n## Agent: test_jvmti_functions_for_threads finished: virtual: %d\n", is_virt);

}

JNIEXPORT void JNICALL
Java_SuspendResume1_TestSuspendResume(JNIEnv* jni, jclass cls) {
  jthread* tested_cthreads = nullptr;
  jint cthread_cnt = 0;

  LOG("\n## TestSuspendResume: Test carrier threads\n");
  cthread_cnt = get_cthreads(jni, &tested_cthreads);
  test_threads_suspend_resume(jni, cthread_cnt, tested_cthreads);
  test_jvmti_functions_for_threads(jni, false /*virtual */, cthread_cnt, tested_cthreads);

  LOG("\n## TestSuspendResume: Test virtual threads\n");
  test_threads_suspend_resume(jni, VTHREAD_CNT, tested_vthreads);
  test_jvmti_functions_for_threads(jni, true /* virtual */, VTHREAD_CNT, tested_vthreads);

  test_thread_suspend_list(jni, tested_vthreads);
  test_thread_resume_list(jni, tested_vthreads);

  LOG("\n\n## TestSuspendResume: Check all carrier threads are resumed\n");
  check_threads_resumed_state(jni, tested_cthreads, cthread_cnt);

  for (int i = 0; i < VTHREAD_CNT; i++) {
    jni->DeleteGlobalRef(tested_vthreads[i]);
  }
  LOG("\n## TestSuspendResume: finished\n");
}

JNIEXPORT jint JNICALL
Java_SuspendResume1_GetStatus(JNIEnv* jni, jclass cls) {
  return get_agent_status();
}

static void JNICALL
VirtualThreadStart(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread) {
  RawMonitorLocker agent_start_locker(jvmti, jni, agent_event_lock);

  tested_vthreads[vthread_no++] = jni->NewGlobalRef(vthread);
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

