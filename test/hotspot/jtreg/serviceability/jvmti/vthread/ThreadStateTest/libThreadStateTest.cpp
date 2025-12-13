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

#include <jni.h>
#include <jvmti.h>
#include <stdio.h>
#include <string.h>
#include "jvmti_common.hpp"

// set by Agent_OnLoad
static jvmtiEnv* jvmti = nullptr;
static jrawMonitorID agent_event_lock = nullptr;
static int frame_pops_cnt = 0;
static const jint EXP_VT_STATE = JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_RUNNABLE;
static const jint EXP_CT_STATE = JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_WAITING |
                                 JVMTI_THREAD_STATE_WAITING_INDEFINITELY;
static const jint MAX_FRAME_COUNT = 32;

extern "C" {

static void JNICALL
SingleStep(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread,
           jmethodID method, jlocation location) {
}

static void JNICALL
FramePop(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread,
         jmethodID method, jboolean by_exception) {
  const char* tname = get_thread_name(jvmti, jni, thread);
  const char* mname = get_method_name(jvmti, jni, method);

  RawMonitorLocker event_locker(jvmti, jni, agent_event_lock);
  LOG("FramePop event #%d: thread: %s method: %s\n", ++frame_pops_cnt, tname, mname);
  deallocate(jvmti, jni, (void*)tname);
  deallocate(jvmti, jni, (void*)mname);
}

static void JNICALL
MonitorContended(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread,
                 jobject object) {
}

static void JNICALL
check_thread_state(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread, jint state, jint exp_state, const char* msg) {
  if ((state & ~JVMTI_THREAD_STATE_SUSPENDED) != exp_state) {
    const char* tname = get_thread_name(jvmti, jni, thread);

    LOG("FAILED: %p: %s: thread state: %x expected state: %x\n",
        (void*)thread, tname, state, exp_state);

    deallocate(jvmti, jni, (void*)tname);
    jni->FatalError(msg);
  }
}

JNIEXPORT void JNICALL
Java_ThreadStateTest_setFramePopEvent(JNIEnv* jni, jclass klass, jthread thread) {
  RawMonitorLocker event_locker(jvmti, jni, agent_event_lock);

  jvmtiError err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FRAME_POP, thread);
  if (err != JVMTI_ERROR_NONE) {
    if (err == JVMTI_ERROR_THREAD_NOT_ALIVE || err == JVMTI_ERROR_NO_MORE_FRAMES) {
      return;
    } else {
      check_jvmti_status(jni, err, "setFramePopEvent error in JVMTI SetEventNotificationMode for JVMTI_EVENT_FRAME_POP");
    }
  }
  err = jvmti->SuspendThread(thread);
  if (err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
    return;
  }
  check_jvmti_status(jni, err, "setFramePopEvent error in JVMTI SuspendThread");

  err = jvmti->NotifyFramePop(thread, 4);
  if (err != JVMTI_ERROR_NO_MORE_FRAMES && err != JVMTI_ERROR_OPAQUE_FRAME) {
    check_jvmti_status(jni, err, "setFramePopEvent error in JVMTI NotifyFramePop");
  }

  err = jvmti->ResumeThread(thread);
  check_jvmti_status(jni, err, "setFramePopEvent error in JVMTI ResumeThread");
}

JNIEXPORT void JNICALL
Java_ThreadStateTest_setSingleSteppingMode(JNIEnv* jni, jclass klass, jboolean enable) {
  jvmtiError err = jvmti->SetEventNotificationMode(enable ? JVMTI_ENABLE : JVMTI_DISABLE, JVMTI_EVENT_SINGLE_STEP, nullptr);
  check_jvmti_status(jni, err, "setSingleSteppingMode: error in JVMTI SetEventNotificationMode for JVMTI_EVENT_SINGLE_STEP");
}

JNIEXPORT void JNICALL
Java_ThreadStateTest_setMonitorContendedMode(JNIEnv* jni, jclass klass, jboolean enable) {
  jvmtiError err = jvmti->SetEventNotificationMode(enable ? JVMTI_ENABLE : JVMTI_DISABLE, JVMTI_EVENT_MONITOR_CONTENDED_ENTER, nullptr);
  check_jvmti_status(jni, err, "setMonitorContendedMode: error in JVMTI SetEventNotificationMode for JVMTI_EVENT_MONITOR_CONTENDED_ENTER");
}

JNIEXPORT void JNICALL
Java_ThreadStateTest_testGetThreadState(JNIEnv* jni, jclass klass, jthread vthread) {
  jthread cthread = get_carrier_thread(jvmti, jni, vthread);
  jint ct_state = get_thread_state(jvmti, jni, cthread);
  jint vt_state = get_thread_state(jvmti, jni, vthread);

  check_thread_state(jvmti, jni, cthread, ct_state, EXP_CT_STATE,
                     "Failed: unexpected carrier thread state from JVMTI GetThreadState");
  check_thread_state(jvmti, jni, vthread, vt_state, EXP_VT_STATE,
                     "Failed: unexpected virtual thread state from JVMTI GetThreadState");
}

JNIEXPORT void JNICALL
Java_ThreadStateTest_testGetThreadListStackTraces(JNIEnv* jni, jclass klass, jthread vthread) {
  jthread cthread = get_carrier_thread(jvmti, jni, vthread);
  jthread threads[2] = { cthread, vthread };
  jvmtiStackInfo* stackInfo = nullptr;

  jvmtiError err = jvmti->GetThreadListStackTraces(2, threads, MAX_FRAME_COUNT, &stackInfo);
  check_jvmti_status(jni, err, "testGetThreadState: error in JVMTI GetThreadListStackTraces");

  check_thread_state(jvmti, jni, cthread, stackInfo[0].state, EXP_CT_STATE,
                     "Failed: unexpected carrier thread state from JVMTI GetThreadListStackTraces");
  check_thread_state(jvmti, jni, vthread, stackInfo[1].state, EXP_VT_STATE,
                     "Failed: unexpected virtual thread state from JVMTI GetThreadListStackTraces");
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  jvmtiEventCallbacks callbacks;
  jvmtiCapabilities caps;
  jvmtiError err;

  printf("Agent_OnLoad: started\n");
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    LOG("Agent_OnLoad: error in GetEnv");
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_generate_single_step_events = 1;
  caps.can_generate_frame_pop_events = 1;
  caps.can_suspend = 1;
  caps.can_support_virtual_threads = 1;
  caps.can_generate_monitor_events = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: error in JVMTI AddCapabilities: %d\n", err);
  }

  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.SingleStep = &SingleStep;
  callbacks.FramePop = &FramePop;
  callbacks.MonitorContendedEnter  = &MonitorContended;
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: Error in JVMTI SetEventCallbacks: %d\n", err);
  }
  agent_event_lock = create_raw_monitor(jvmti, "agent_event_lock");
  printf("Agent_OnLoad: finished\n");

  return 0;
}

} // extern "C"
