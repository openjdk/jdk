/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

#define MAX_FRAME_COUNT 30
#define MAX_WORKER_THREADS 10

enum Slots {
  SlotInvalid0 = -1,
  SlotString = 0,
  SlotThread = 1,
  SlotInt = 2,
  SlotLong = 3,
  SlotUnaligned = 4,
  SlotFloat = 5,
  SlotDouble = 6,
};

typedef struct Values {
  jobject msg;
  jobject tt;
  jint ii;
  jlong ll;
  jfloat ff;
  jdouble dd;
} Values;

static const int MAX_EVENTS_TO_PROCESS = 20;
static jvmtiEnv *jvmti = nullptr;
static volatile jboolean completed = JNI_FALSE;

static void
set_breakpoint(JNIEnv *jni, jclass klass, const char *mname, jlocation location) {
  // Find the jmethodID of the specified method
  jmethodID method = find_method(jvmti, jni, klass, mname);
  jvmtiError err;

  if (method == nullptr) {
    LOG("set_breakpoint: Failed to find method %s()\n", mname);
    fatal(jni, "set_breakpoint: not found method");
  }
  err = jvmti->SetBreakpoint(method, location);
  check_jvmti_status(jni, err, "set_breakpoint: error in JVMTI SetBreakpoint");
}

static void
clear_breakpoint(JNIEnv *jni, jmethodID method, jlocation location) {
  jvmtiError err = jvmti->ClearBreakpoint(method, location);
  check_jvmti_status(jni, err, "clear_breakpoint: error in JVMTI ClearBreakpoint");
}

static jint
find_method_depth(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread, const char *mname) {
  jvmtiFrameInfo frames[MAX_FRAME_COUNT];
  jint count = -1;
  jvmtiError err;

  err = jvmti->GetStackTrace(vthread, 0, MAX_FRAME_COUNT, frames, &count);
  if (err == JVMTI_ERROR_WRONG_PHASE || err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
    return -1; // VM or target thread completed its work
  }
  check_jvmti_status(jni, err, "find_method_depth: error in JVMTI GetStackTrace");

  for (int depth = 0; depth < count; depth++) {
    jmethodID method = frames[depth].method;
    char* name = nullptr;
    char* sign = nullptr;

    err = jvmti->GetMethodName(method, &name, &sign, nullptr);
    if (err == JVMTI_ERROR_WRONG_PHASE || err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
      return -1; // VM or target thread completed its work
    }
    check_jvmti_status(jni, err, "find_method_depth: error in JVMTI GetMethodName");

    if (strcmp(name, mname) == 0) {
      return depth;
    }
  }
  return -1;
}

static void
test_GetLocal(jvmtiEnv *jvmti, JNIEnv *jni, jthread cthread, jthread vthread,
              int depth, int frame_count, Values *exp_values) {
  jobject msg = nullptr;
  jobject tt = nullptr;
  jint ii = 0;
  jlong ll = 0L;
  jfloat ff = 0.0;
  jdouble dd = 0.0;
  jvmtiError err;

  LOG("test_GetLocal: mounted: %d depth: %d fcount: %d\n", cthread != nullptr, depth, frame_count);

  int dep = find_method_depth(jvmti, jni, vthread, "producer");
  if (dep == -1) {
    fatal(jni, "test_GetLocal: got vthread with no producer(String msg) method");
  }
  if (dep != depth) {
    fatal(jni, "test_GetLocal: got vthread with unexpected depth of producer(String msg) method");
  }

  // #0: Test JVMTI GetLocalInstance function for carrier thread
  if (cthread != nullptr) {
    suspend_thread(jvmti, jni, cthread);

    err = jvmti->GetLocalInstance(cthread, 3, &msg);
    check_jvmti_status(jni, err, "error in JVMTI GetLocalInstance for carrier thread top frame Continuation.run");
    LOG("JVMTI GetLocalInstance succeed for carrier thread top frame Continuation.run()\n");

    resume_thread(jvmti, jni, cthread);
  }

  // #1: Test JVMTI GetLocalObject function with negative frame depth
  err = jvmti->GetLocalObject(vthread, -1, SlotString, &msg);
  if (err != JVMTI_ERROR_ILLEGAL_ARGUMENT) {
    LOG("JVMTI GetLocalObject with negative frame depth returned error: %d\n", err);
    fatal(jni, "JVMTI GetLocalObject with negative frame depth failed to return JVMTI_ERROR_ILLEGAL_ARGUMENT");
  }

  // #2: Test JVMTI GetLocalObject function with big frame depth
  err = jvmti->GetLocalObject(vthread, frame_count, SlotString, &msg);
  if (err != JVMTI_ERROR_NO_MORE_FRAMES) {
    LOG("JVMTI GetLocalObject with big frame depth returned error: %d\n", err);
    fatal(jni, "JVMTI GetLocalObject with big frame depth failed to return JVMTI_ERROR_NO_MORE_FRAMES");
  }

  // #3: Test JVMTI GetLocalObject function with invalid slot -1
  err = jvmti->GetLocalObject(vthread, depth, SlotInvalid0, &msg);
  if (err != JVMTI_ERROR_INVALID_SLOT) {
    LOG("JVMTI GetLocalObject with invalid slot -1 returned error: %d\n", err);
    fatal(jni, "JVMTI GetLocalObject with invalid slot -1 failed to return JVMTI_ERROR_INVALID_SLOT");
  }

  // #4: Test JVMTI GetLocalObject function with unaligned slot 4
  err = jvmti->GetLocalObject(vthread, depth, SlotUnaligned, &msg);
  if (err != JVMTI_ERROR_INVALID_SLOT && err != JVMTI_ERROR_TYPE_MISMATCH) {
    LOG("JVMTI GetLocalObject with unaligned slot 4 returned error: %d\n", err);
    fatal(jni, "JVMTI GetLocalObject with unaligned slot 4 failed"
               " to return JVMTI_ERROR_INVALID_SLOT or JVMTI_ERROR_TYPE_MISMATCH");
  }

  // #5: Test JVMTI GetLocalObject function with nullptr value_ptr
  err = jvmti->GetLocalObject(vthread, depth, SlotString, nullptr);
  if (err != JVMTI_ERROR_NULL_POINTER) {
    LOG("JVMTI GetLocalObject with null value_ptr returned error: %d\n", err);
    fatal(jni, "JVMTI GetLocalObject with null value_ptr failed to return JVMTI_ERROR_NULL_POINTER");
  }

  // #6: Test JVMTI GetLocal<Type> functions with a good vthread
  err = jvmti->GetLocalObject(vthread, depth, SlotString, &msg);
  check_jvmti_status(jni, err, "error in JVMTI GetLocalObject with good vthread");

  const char* str = jni->GetStringUTFChars((jstring)msg, nullptr);
  LOG("    local String value at slot %d: %s\n", SlotString, str);
  const char* exp_str = "msg: ...";
  if (strncmp(str, exp_str, 5) != 0) {
    LOG("    Failed: Expected local String value: %s, got: %s\n", exp_str, str);
    fatal(jni, "Got unexpected local String value");
  }
  jni->ReleaseStringUTFChars((jstring)msg, str);

  err = jvmti->GetLocalObject(vthread, depth, SlotThread, &tt);
  check_jvmti_status(jni, err, "error in JVMTI GetLocalObject with good vthread");

  LOG("    local Thread value at slot %d: %p\n", SlotThread, (void*)tt);
  if (exp_values->tt != nullptr && !jni->IsSameObject(tt, exp_values->tt)) {
    LOG("    Failed: Expected local Thread value: %p, got: %p\n", exp_values->tt, tt);
    fatal(jni, "JVMTI GetLocalObject returned unexpected local Thread value");
  }

  err = jvmti->GetLocalInt(vthread, depth, SlotInt, &ii);
  check_jvmti_status(jni, err, "error in JVMTI GetLocalInt with good vthread");

  LOG("    local int value at slot %d: %d\n", SlotInt, ii);
  if (ii != exp_values->ii) {
    LOG("    Failed: Expected local int value: %d, got %d\n", exp_values->ii, ii);
    fatal(jni, "JVMTI GetLocalInt returned unexpected local int value");
  }

  err = jvmti->GetLocalLong(vthread, depth, SlotLong, &ll);
  check_jvmti_status(jni, err, "error in JVMTI GetLocalLong with good vthread");

  LOG("    local long value at slot %d: %lld\n", SlotLong, (long long)ll);
  if (ll != exp_values->ll) {
    LOG("    Failed: Expected local long value: %lld, got %lld\n", (long long)exp_values->ll, (long long)ll);
    fatal(jni, "JVMTI GetLocalLong returned unexpected local long value");
  }

  err = jvmti->GetLocalFloat(vthread, depth, SlotFloat, &ff);
  check_jvmti_status(jni, err, "error in JVMTI GetLocalFloat with good vthread");

  LOG("    local float value at slot %d: %f\n", SlotFloat, ff);
  if (ff < exp_values->ff || ff > exp_values->ff + 0.00001) {
    LOG("    Failed: Expected local float value: %f, got %f\n", exp_values->ff, ff);
    fatal(jni, "JVMTI GetLocalFloat returned unexpected local float value");
  }

  err = jvmti->GetLocalDouble(vthread, depth, SlotDouble, &dd);
  check_jvmti_status(jni, err, "error in JVMTI GetLocalDouble with good vthread");

  LOG("    local double value at slot %d: %f\n", SlotDouble, dd);
  if (dd < exp_values->dd || dd > exp_values->dd + 0.00000000000001) {
    LOG("    Failed: Expected local double value: %f, got %f\n", exp_values->dd, dd);
    fatal(jni, "JVMTI GetLocalDouble returned unexpected local double value");
  }

  if (msg != 0) jni->DeleteLocalRef(msg);
  if (tt != 0) jni->DeleteLocalRef(tt);
}

static bool
test_SetLocal(jvmtiEnv *jvmti, JNIEnv *jni, jthread cthread, jthread vthread,
              int depth, int frame_count, Values *values, bool at_event) {
  jvmtiError err;

  LOG("test_SetLocal: mounted: %d depth: %d fcount: %d\n", cthread != nullptr, depth, frame_count);

  // #1: Test JVMTI SetLocalObject function with negative frame depth
  err = jvmti->SetLocalObject(vthread, -1, SlotString, values->tt);
  if (err != JVMTI_ERROR_ILLEGAL_ARGUMENT) {
    LOG("JVMTI SetLocalObject with negative frame depth returned error: %d\n", err);
    fatal(jni, "JVMTI SetLocalObject with negative frame depth failed to return JVMTI_ERROR_ILLEGAL_ARGUMENT");
  }

  // #2: Test JVMTI SetLocalObject function with big frame depth
  err = jvmti->SetLocalObject(vthread, frame_count, SlotString, values->tt);
  if (err != JVMTI_ERROR_NO_MORE_FRAMES) {
    LOG("JVMTI SetLocalObject with big frame depth failed to return JVMTI_ERROR_NO_MORE_FRAMES error: %d\n", err);
    fatal(jni, "JVMTI SetLocalObject with big frame depth failed to return JVMTI_ERROR_NO_MORE_FRAMES");
  }

  // #3: Test JVMTI SetLocalObject function with invalid slot -1
  err = jvmti->SetLocalObject(vthread, depth, SlotInvalid0, values->tt);
  if (depth > 0 || cthread == nullptr) {
    // JVMTI_ERROR_OPAQUE_FRAME can be returned for unmouted vthread or depth > 0
    if (err != JVMTI_ERROR_OPAQUE_FRAME) {
      LOG("JVMTI SetLocalObject for unmounted vthread or depth > 0 failed to return JVMTI_ERROR_OPAQUE_FRAME: %d\n", err);
      fatal(jni, "JVMTI SetLocalObject for unmounted vthread or depth > 0 failed to return JVMTI_ERROR_OPAQUE_FRAME");
    }
  }
  else if (err != JVMTI_ERROR_INVALID_SLOT) {
    LOG("JVMTI SetLocalObject with invalid slot -1 returned error: %d\n", err);
    fatal(jni, "JVMTI SetLocalObject with invalid slot -1 failed to return JVMTI_ERROR_INVALID_SLOT");
  }

  // #4: Test JVMTI SetLocalObject function with unaligned slot 4
  err = jvmti->SetLocalObject(vthread, depth, SlotUnaligned, values->tt);
  if (depth > 0 || cthread == nullptr) {
    // JVMTI_ERROR_OPAQUE_FRAME can be returned for unmouted vthread or depth > 0
    if (err != JVMTI_ERROR_OPAQUE_FRAME) {
      LOG("JVMTI SetLocalObject for unmounted vthread or depth > 0 failed to return JVMTI_ERROR_OPAQUE_FRAME: %d\n", err);
      fatal(jni, "JVMTI SetLocalObject for unmounted vthread or depth > 0 failed to return JVMTI_ERROR_OPAQUE_FRAME");
    }
  }
  else if (cthread != nullptr && err != JVMTI_ERROR_INVALID_SLOT && err != JVMTI_ERROR_TYPE_MISMATCH) {
    LOG("JVMTI SetLocalObject with unaligned slot 4 returned error: %d\n", err);
    fatal(jni, "JVMTI SetLocalObject with unaligned slot 4 failed"
               " to return JVMTI_ERROR_INVALID_SLOT or JVMTI_ERROR_TYPE_MISMATCH");
  }

  // #6: Test JVMTI SetLocal<Type> functions with a good vthread
  err = jvmti->SetLocalObject(vthread, depth, SlotThread, values->tt);
  if (depth > 0 || cthread == nullptr) {
    if (err != JVMTI_ERROR_OPAQUE_FRAME) {
      LOG("JVMTI SetLocalObject for unmounted vthread or depth > 0 failed to return JVMTI_ERROR_OPAQUE_FRAME: %d\n", err);
      fatal(jni, "JVMTI SetLocalObject for unmounted vthread pr depth > 0failed to return JVMTI_ERROR_OPAQUE_FRAME");
    }
    return false; // skip testing other types for unmounted vthread
  } else if (!at_event && err == JVMTI_ERROR_OPAQUE_FRAME) {
    LOG("JVMTI SetLocalObject for mounted vthread at depth=0 returned JVMTI_ERROR_OPAQUE_FRAME: %d\n", err);
    return false; // skip testing other types for compiled frame that can't be deoptimized
  }
  check_jvmti_status(jni, err, "error in JVMTI SetLocalObject with good vthread");

  err = jvmti->SetLocalInt(vthread, depth, SlotInt, values->ii);
  check_jvmti_status(jni, err, "error in JVMTI SetLocalInt with good vthread");

  err = jvmti->SetLocalLong(vthread, depth, SlotLong, values->ll);
  check_jvmti_status(jni, err, "error in JVMTI SetLocalLong with good vthread");

  err = jvmti->SetLocalFloat(vthread, depth, SlotFloat, values->ff);
  check_jvmti_status(jni, err, "error in JVMTI SetLocalFloat with good vthread");

  err = jvmti->SetLocalDouble(vthread, depth, SlotDouble, values->dd);
  check_jvmti_status(jni, err, "error in JVMTI SetLocalDouble with good vthread");
  return true;
}

static void
test_GetSetLocal(jvmtiEnv *jvmti, JNIEnv* jni, jthread vthread, int depth, int frame_count, bool at_event) {
  Values values0 = { nullptr, nullptr, 1, 2L, (jfloat)3.2F, (jdouble)4.500000047683716 };
  Values values1 = { nullptr, nullptr, 2, 3L, (jfloat)4.2F, (jdouble)5.500000047683716 };
  jthread cthread = get_carrier_thread(jvmti, jni, vthread);

  values0.tt = vthread;
  values1.tt = cthread;

  LOG("test_GetSetLocal: test_GetLocal with values0\n");
  test_GetLocal(jvmti, jni, cthread, vthread, depth, frame_count, &values0);
  LOG("test_GetSetLocal: test_SetLocal at_event: %d with values1\n", at_event);
  bool success = test_SetLocal(jvmti, jni, cthread, vthread, depth, frame_count, &values1, at_event);

  if (!success) {
    goto End; // skip testing for compiled frame that can't be deoptimized
  }
  if (depth > 0 || cthread == nullptr) {
    // No values are expected to be set by SetLocal above as
    // unmounted virtual thread case is not supported.
    // So, we expect local values to remain the same.
    LOG("test_GetSetLocal: test_GetLocal with values0\n");
    test_GetLocal(jvmti, jni, cthread, vthread, depth, frame_count, &values0);
  } else {
    LOG("test_GetSetLocal: test_GetLocal with values1\n");
    test_GetLocal(jvmti, jni, cthread, vthread, depth, frame_count, &values1);
    LOG("test_GetSetLocal: test_SetLocal at_event: %d with values0 to restore original local values\n", at_event);
    test_SetLocal(jvmti, jni, cthread, vthread, depth, frame_count, &values0, at_event);
  }
 End:
  LOG("test_GetSetLocal: finished\n\n");
  if (cthread != 0) jni->DeleteLocalRef(cthread);
}

static void JNICALL
Breakpoint(jvmtiEnv *jvmti, JNIEnv* jni, jthread vthread,
           jmethodID method, jlocation location) {
  char* mname = get_method_name(jvmti, jni, method);
  char* tname = get_thread_name(jvmti, jni, vthread);
  const char* virt = jni->IsVirtualThread(vthread) ? "virtual" : "carrier";
  const jint depth = 0; // the depth is always 0 in case of breakpoint

  LOG("\nBreakpoint: %s on %s thread: %s - Started\n", mname, virt, tname);

  // disable BREAKPOINT events
  jvmtiError err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_BREAKPOINT, vthread);
  check_jvmti_status(jni, err, "error in JVMTI SetEventNotificationMode: disable BREAKPOINT");

  clear_breakpoint(jni, method, location);

  {
    int frame_count = get_frame_count(jvmti, jni, vthread);

    test_GetSetLocal(jvmti, jni, vthread, depth, frame_count, true /* at_event */);

    // vthread passed to callback has to refer to current thread,
    // so we can also test with nullptr in place of vthread.
    test_GetSetLocal(jvmti, jni, nullptr, depth, frame_count, true /* at_event */);
  }
  deallocate(jvmti, jni, (void*)mname);
  deallocate(jvmti, jni, (void*)tname);

  completed = JNI_TRUE; // done with testing in the agent
  LOG("Breakpoint: %s on %s thread: %s - Finished\n", mname, virt, tname);
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options,
                                           void *reserved) {
  jvmtiEventCallbacks callbacks;
  jvmtiCapabilities caps;
  jvmtiError err;

  LOG("Agent_OnLoad started\n");
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }

  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.Breakpoint  = &Breakpoint;

  memset(&caps, 0, sizeof(caps));
  caps.can_support_virtual_threads = 1;
  caps.can_access_local_variables = 1;
  caps.can_generate_breakpoint_events = 1;
  caps.can_suspend = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI AddCapabilities: %d\n", err);
    return JNI_ERR;
  }
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI SetEventCallbacks: %d\n", err);
    return JNI_ERR;
  }
  LOG("Agent_OnLoad finished\n");
  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_GetSetLocalTest_enableEvents(JNIEnv *jni, jclass klass, jthread vthread) {
  const jlocation ProducerLocation = (jlocation)30;

  LOG("enableEvents: started\n");

  set_breakpoint(jni, klass, "producer", ProducerLocation);

  jvmtiError err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, vthread);
  check_jvmti_status(jni, err, "enableEvents: error in JVMTI SetEventNotificationMode: enable BREAKPOINT");

  LOG("enableEvents: finished\n");
}

JNIEXPORT void JNICALL
Java_GetSetLocalTest_testSuspendedVirtualThreads(JNIEnv *jni, jclass klass, jthread vthread) {
  char* tname = get_thread_name(jvmti, jni, vthread);
  bool seen_depth_0 = false;
  bool seen_depth_positive = false;
  bool seen_unmounted = false;
  int iter = 0;

  LOG("testSuspendedVirtualThreads: started for virtual thread: %s\n", tname);

  // Test each of these cases only once: unmounted, positive depth, frame count 0.
  while (iter++ < 50 && (!seen_depth_0 || !seen_depth_positive || !seen_unmounted)) {
    jmethodID method = nullptr;
    jlocation location = 0;

    sleep_ms(1);

    jvmtiError err = jvmti->SuspendThread(vthread);
    if (err == JVMTI_ERROR_WRONG_PHASE || err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
      break; // VM or target thread completed its work
    }
    check_jvmti_status(jni, err, "testSuspendedVirtualThreads: error in JVMTI SuspendThread");

    jthread cthread = get_carrier_thread(jvmti, jni, vthread);
    int depth = find_method_depth(jvmti, jni, vthread, "producer");
    int frame_count = get_frame_count(jvmti, jni, vthread);

    if (depth != -1) {
      err = jvmti->GetFrameLocation(vthread, depth, &method, &location);
      check_jvmti_status(jni, err, "testSuspendedVirtualThreads: error in JVMTI GetFrameLocation");
    }
    bool case_0 = !seen_depth_0 && depth == 0 && (int)location >= 30;
    bool case_1 = !seen_depth_positive && depth > 0 && (int)location >= 30;
    bool case_2 = !seen_unmounted && depth >= 0 && cthread == nullptr;

    if (case_0) {
      LOG("testSuspendedVirtualThreads: DEPTH == 0\n");
      seen_depth_0 = true;
    }
    if (case_1) {
      LOG("testSuspendedVirtualThreads: DEPTH > 0\n");
      seen_depth_positive = true;
    }
    if (case_2) {
      LOG("testSuspendedVirtualThreads: UNMOUNTED VTHREAD\n");
      seen_unmounted = true;
    }
    if (depth >=0 && (case_0 || case_1 || case_2)) {
      LOG("testSuspendedVirtualThreads: iter: %d\n", iter);
#if 0
      print_stack_trace(jvmti, jni, vthread);
#endif
      test_GetSetLocal(jvmti, jni, vthread, depth, frame_count, false /* !at_event */);
    }

    err = jvmti->ResumeThread(vthread);
    if (err == JVMTI_ERROR_WRONG_PHASE || err == JVMTI_ERROR_THREAD_NOT_ALIVE) {
      break; // VM or target thread completed its work
    }
    check_jvmti_status(jni, err, "testSuspendedVirtualThreads: error in JVMTI ResumeThread");

    if (cthread != 0) jni->DeleteLocalRef(cthread);
  }
  deallocate(jvmti, jni, (void*)tname);
  LOG("testSuspendedVirtualThreads: finished\n");
}

JNIEXPORT jboolean JNICALL
Java_GetSetLocalTest_completed(JNIEnv *jni, jclass klass) {
  if (completed) {
    completed = JNI_FALSE;
    return JNI_TRUE;
  }
  return JNI_FALSE;
}

} // extern "C"
