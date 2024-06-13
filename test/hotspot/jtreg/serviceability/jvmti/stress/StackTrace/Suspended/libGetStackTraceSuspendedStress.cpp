/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

#define MAX_FRAME_COUNT 80

static const char CONTINUATION_CLASS_NAME[] = "jdk/internal/vm/Continuation";
static const char CONTINUATION_METHOD_NAME[] = "enter";

static jrawMonitorID event_mon = nullptr;

static void
test_stack_trace(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread) {
  jvmtiFrameInfo frames[MAX_FRAME_COUNT];
  jint count = -1;
  jmethodID method = nullptr;
  jvmtiError err;

  err = jvmti->GetStackTrace(vthread, 0, MAX_FRAME_COUNT, frames, &count);
  check_jvmti_status(jni, err, "Error in GetStackTrace");

  if (count <= 0) {
    LOG("Stacktrace in virtual thread is incorrect.\n");
    print_thread_info(jvmti, jni, vthread);
    print_stack_trace_frames(jvmti, jni, count, frames);
    LOG("Incorrect frame count %d\n", count);
    fatal(jni, "Incorrect frame count: count <= 0");
  }
  method = frames[count -1].method;
  const char* class_name = get_method_class_name(jvmti, jni, method);
  const char* method_name = get_method_name(jvmti, jni, method);

  if (strcmp(CONTINUATION_CLASS_NAME, class_name) !=0 || strcmp(CONTINUATION_METHOD_NAME, method_name) != 0) {
    LOG("Stacktrace in virtual thread is incorrect (doesn't start from enter(...):\n");
    print_stack_trace_frames(jvmti, jni, count, frames);

    fatal(jni, "incorrect stacktrace.");
  }

  jint frame_count = -1;
  check_jvmti_status(jni, jvmti->GetFrameCount(vthread, &frame_count), "Error in GetFrameCount");
  if (frame_count != count) {
    LOG("Incorrect frame count %d while %d expected\n", frame_count, count);

    LOG("Suspended vthread 1st stack trace:\n");
    print_stack_trace_frames(jvmti, jni, count, frames);

    LOG("Suspended vthread 2nd stack trace:\n");
    print_stack_trace(jvmti, jni, vthread);

    fatal(jni, "Incorrect frame count: frame_count != count");
  }
}

void
check_link_consistency(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread) {
  jthread cthread = get_carrier_thread(jvmti, jni, vthread);
  jint vstate = get_thread_state(jvmti, jni, vthread);
  jint cstate = get_thread_state(jvmti, jni, cthread);

  if ((vstate & JVMTI_THREAD_STATE_SUSPENDED) == 0) {
    print_thread_info(jvmti, jni, vthread);
    print_stack_trace(jvmti, jni, vthread);
    fatal(jni, "Virtual thread IS expected to be suspended");
  }
  if ((cstate & JVMTI_THREAD_STATE_SUSPENDED) != 0) {
    print_thread_info(jvmti, jni, cthread);
    print_stack_trace(jvmti, jni, cthread);
    fatal(jni, "Carrier thread is NOT expected to be suspended");
  }

  if (cthread != nullptr) {
    jthread cthread_to_vthread = get_virtual_thread(jvmti, jni, cthread);

    if (!jni->IsSameObject(vthread, cthread_to_vthread)) {
      LOG("\nCarrier: ");
      print_thread_info(jvmti, jni, cthread);
      LOG("Expected: ");
      print_thread_info(jvmti, jni, vthread);
      LOG("Resulted: ");
      print_thread_info(jvmti, jni, cthread_to_vthread);
      fatal(jni, "GetVirtualThread(GetCarrierThread(vthread)) != vthread");
    }
  }
}

static void
check_vthread_consistency_suspended(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread) {
  jvmtiError err;
  jboolean is_virtual = jni->IsVirtualThread(vthread);
  //const char* name = get_thread_name(jvmti, jni, vthread);

  if (!is_virtual) {
    jni->FatalError("Agent: check_vthread_consistency_suspended: vthread is expected to be virtual");
  }
  jthread cthread = get_carrier_thread(jvmti, jni, vthread);
  //const char* cname = (cthread == nullptr) ? "<no cthread>" : get_thread_name(jvmti, jni, cthread);

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_SINGLE_STEP, vthread);
  check_jvmti_status(jni, err, "Error in JVMTI SetEventNotificationMode: enable SINGLE_STEP");

  if (cthread != nullptr) { // pre-condition for reliable testing
    test_stack_trace(jvmti, jni, vthread);
    check_link_consistency(jvmti, jni, vthread);
  }

  err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_SINGLE_STEP, vthread);
  check_jvmti_status(jni, err, "Error in JVMTI SetEventNotificationMode: disable SINGLE_STEP");
}

#if 1
static int i = 0;
#endif

static void JNICALL
SingleStep(jvmtiEnv *jvmti, JNIEnv* jni, jthread thread,
           jmethodID method, jlocation location) {
  RawMonitorLocker rml(jvmti, jni, event_mon);

  LOG("Agent: Got SingleStep event:\n");
  print_stack_trace(jvmti, jni, thread);

  jthread cthread = get_carrier_thread(jvmti, jni, thread);
  if (cthread != nullptr) {
    print_stack_trace(jvmti, jni, cthread);
  }

#if 1
  i = 1 / (*(&i)); // NOT expected Singlestep event: crash to get full stack trace
#endif
  jni->FatalError("SingleStep event is NOT expected");
}

/** Agent algorithm. */
static void JNICALL
agentProc(jvmtiEnv * jvmti, JNIEnv * jni, void * arg) {

  static jlong timeout = 0;
  LOG("Agent: waiting to start\n");
  if (!agent_wait_for_sync(timeout))
    return;
  if (!agent_resume_sync())
    return;

  LOG("Agent: started\n");

  int iter = 0;
  while (true) {
    jthread *threads = nullptr;
    jint count = 0;
    jvmtiError err;

    err = jvmti->GetAllThreads(&count, &threads);
    if (err == JVMTI_ERROR_WRONG_PHASE) {
      return;
    }
    check_jvmti_status(jni, err,  "Error in GetAllThreads");

    for (int i = 0; i < count; i++) {
      jthread cthread = threads[i];
      jthread vthread = nullptr;

      err = GetVirtualThread(jvmti, jni, cthread, &vthread);
      if (err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
        continue;
      }
      if (err == JVMTI_ERROR_WRONG_PHASE) {
        return;
      }
      check_jvmti_status(jni, err,  "Error in GetVirtualThread");
      if (iter > 50 && vthread != nullptr) {
        // char* cname = get_thread_name(jvmti, jni, cthread);
        // char* vname = get_thread_name(jvmti, jni, vthread);

        err = jvmti->SuspendThread(vthread);
        if (err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
          continue;
        }
        check_jvmti_status(jni, err, "Error in SuspendThread");
        // LOG("Agent: suspended vt: %s ct: %s\n", vname, cname);

        check_vthread_consistency_suspended(jvmti, jni, vthread);

        check_jvmti_status(jni, jvmti->ResumeThread(vthread), "Error in ResumeThread");
        // LOG("Agent: resumed vt: %s ct: %s\n", vname, cname);
      }
    }
    check_jvmti_status(jni, jvmti->Deallocate((unsigned char *) threads), "Error in Deallocate");
  //check_jvmti_status(jni, jvmti->Deallocate((unsigned char *) cname), "Error in Deallocate");
  //check_jvmti_status(jni, jvmti->Deallocate((unsigned char *) vname), "Error in Deallocate");

    iter++;
    sleep_ms(20);
  }
  LOG("Agent: finished\n");
}


extern JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiEventCallbacks callbacks;
  jvmtiCapabilities caps;
  jvmtiError err;
  jvmtiEnv* jvmti;

  LOG("Agent_OnLoad started\n");
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_generate_single_step_events = 1;
  caps.can_support_virtual_threads = 1;
  caps.can_suspend = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI AddCapabilities: %d\n", err);
  }

  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.SingleStep  = &SingleStep;
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: Error in JVMTI SetEventCallbacks: %d\n", err);
  }

  event_mon = create_raw_monitor(jvmti, "Events Monitor");

  err = init_agent_data(jvmti, &agent_data);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  if (set_agent_proc(agentProc, nullptr) != JNI_TRUE) {
    return JNI_ERR;
  }

  LOG("Agent_OnLoad finished\n");
  return 0;
}
