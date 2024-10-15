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

extern "C" {

#define MAX_FRAME_COUNT 30
#define MAX_WORKER_THREADS 10

typedef struct Tinfo {
  jboolean just_scheduled;
  char* tname;
} Tinfo;

static const int MAX_EVENTS_TO_PROCESS = 20;
static jvmtiEnv *jvmti = nullptr;
static jrawMonitorID events_monitor = nullptr;
static Tinfo tinfo[MAX_WORKER_THREADS];

static int vthread_mount_count = 0;
static int vthread_unmount_count = 0;
static jboolean passed = JNI_TRUE;

static Tinfo*
find_tinfo(JNIEnv* jni, const char* tname) {
  Tinfo* inf = nullptr;
  int idx = 0;

  // Find slot with named worker thread or empty slot
  for (; idx < MAX_WORKER_THREADS; idx++) {
    inf = &tinfo[idx];
    if (inf->tname == nullptr) {
      inf->tname = (char*)malloc(strlen(tname) + 1);
      strcpy(inf->tname, tname);
      break;
    }
    if (strcmp(inf->tname, tname) == 0) {
      break;
    }
  }
  if (idx >= MAX_WORKER_THREADS) {
    fatal(jni, "find_tinfo: found more than 10 worker threads!");
  }
  return inf; // return slot
}

static jint
find_method_depth(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread, const char *mname) {
  jvmtiFrameInfo frames[MAX_FRAME_COUNT];
  jint count = -1;
  jvmtiError err;

  err = jvmti->GetStackTrace(vthread, 0, MAX_FRAME_COUNT, frames, &count);
  check_jvmti_status(jni, err, "find_method_depth: error in JVMTI GetStackTrace");

  for (int depth = 0; depth < count; depth++) {
    jmethodID method = frames[depth].method;
    char* name = nullptr;
    char* sign = nullptr;

    err = jvmti->GetMethodName(method, &name, &sign, nullptr);
    check_jvmti_status(jni, err, "find_method_depth: error in JVMTI GetMethodName");

    if (strcmp(name, mname) == 0) {
      return depth;
    }
  }
  return -1;
}

static void
print_vthread_event_info(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread, jthread vthread, const char *event_name) {
  char* tname = get_thread_name(jvmti, jni, vthread);
  Tinfo* inf = find_tinfo(jni, tname); // Find slot with named worker thread

  LOG("\n#### %s event: thread: %s, vthread: %p\n", event_name, tname, vthread);

  if (strcmp(event_name, "VirtualThreadStart") == 0) {
    inf->just_scheduled = JNI_TRUE;
  }
  else {
    if (inf->tname == nullptr && strcmp(event_name, "VirtualThreadEnd") != 0) {
      fatal(jni, "VThread event: worker thread not found!");
    }
    if (strcmp(event_name, "VirtualThreadUnmount") == 0) {
      if (inf->just_scheduled) {
        fatal(jni, "VirtualThreadUnmount: event without VirtualThreadMount before!");
      }
    }
    inf->just_scheduled = JNI_FALSE;
  }
  deallocate(jvmti, jni, (void*)tname);
}

static void
test_GetVirtualThread(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread, jthread vthread, const char *event_name) {
  jobject thread_vthread = nullptr;
  jvmtiError err;

  LOG("\ntest_GetVirtualThread: event: %s\n", event_name);

  // #1: Test JVMTI GetVirtualThread extension function nullptr thread (current)
  err = GetVirtualThread(jvmti, jni, nullptr, &thread_vthread);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetVirtualThread with null thread (current)");

  if (thread_vthread == nullptr) {
    fatal(jni, "event handler: JVMTI GetVirtualThread with nullptr thread (current) failed to return non-null vthread");
  }
  LOG("JVMTI GetVirtualThread with nullptr thread (current) returned non-null vthread as expected\n");

  // #2: Test JVMTI GetVirtualThread extension function with a bad thread
  err = GetVirtualThread(jvmti, jni, vthread, &thread_vthread);
  if (err != JVMTI_ERROR_INVALID_THREAD) {
    fatal(jni, "event handler: JVMTI GetVirtualThread with bad thread failed to return JVMTI_ERROR_INVALID_THREAD");
  }

  // #3: Test JVMTI GetVirtualThread extension function with a good thread
  err = GetVirtualThread(jvmti, jni,thread, &thread_vthread);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetVirtualThread");

  if (thread_vthread == nullptr) {
    fatal(jni, "event handler: JVMTI GetVirtualThread with good thread failed to return non-null vthread");
  }
  LOG("JVMTI GetVirtualThread with good thread returned non-null vthread as expected\n");
}

static void
test_GetCarrierThread(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread, jthread vthread, const char *event_name) {
  jthread vthread_thread = nullptr;
  jvmtiError err;

  LOG("\ntest_GetCarrierThread: event: %s\n", event_name);

  // #1: Test JVMTI GetCarrierThread extension function with nullptr vthread
  err = GetCarrierThread(jvmti, jni, nullptr, &vthread_thread);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetCarrierThread");

  // #2: Test JVMTI GetCarrierThread extension function with a bad vthread
  err = GetCarrierThread(jvmti, jni, thread, &vthread_thread);
  if (err != JVMTI_ERROR_INVALID_THREAD) {
    fatal(jni, "event handler: JVMTI GetCarrierThread with bad vthread failed to return JVMTI_ERROR_INVALID_THREAD");
  }

  // #3: Test JVMTI GetCarrierThread extension function with a good vthread
  err = GetCarrierThread(jvmti, jni, vthread, &vthread_thread);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetCarrierThread");

  if (vthread_thread == nullptr) {
    fatal(jni, "event handler: JVMTI GetCarrierThread with good vthread failed to return non-null carrier thread");
  }
  LOG("JVMTI GetCarrierThread with good vthread returned non-null carrier thread as expected\n");
}

static void
test_GetThreadInfo(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread, const char *event_name) {
  jvmtiError err;
  jvmtiThreadInfo thr_info;
  jvmtiThreadGroupInfo ginfo;
  jint class_count = -1;
  jclass* classes = nullptr;
  jboolean found = JNI_FALSE;

  LOG("test_GetThreadInfo: started\n");

  // #1: Test JVMTI GetThreadInfo function with a good vthread
  err = jvmti->GetThreadInfo(vthread, &thr_info);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetThreadInfo");

  LOG("GetThreadInfo: name: %s, prio: %d, is_daemon: %d\n",
         thr_info.name, thr_info.priority, thr_info.is_daemon);

  // #2: Test JVMTI GetThreadGroupInfo
  err = jvmti->GetThreadGroupInfo(thr_info.thread_group, &ginfo);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetThreadGroupInfo");

  LOG("GetThreadGroupInfo: name: %s, max prio: %d, is_daemon: %d\n",
         ginfo.name, ginfo.max_priority, ginfo.is_daemon);

  // #3: Test JVMTI GetClassLoaderClasses
  err = jvmti->GetClassLoaderClasses(thr_info.context_class_loader, &class_count, &classes);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetClassLoaderClasses");

  LOG("thr_info.context_class_loader: %p, class_count: %d\n", thr_info.context_class_loader, class_count);

  // #4: Test the thr_info.context_class_loader has the VThreadTest class
  for (int idx = 0; idx < class_count; idx++) {
    char* sign = nullptr;
    err = jvmti->GetClassSignature(classes[idx], &sign, nullptr);
    check_jvmti_status(jni, err, "event handler: error in JVMTI GetClassSignature");

    if (strstr(sign, "VThreadTest") != nullptr) {
      found = JNI_TRUE;
      break;
    }
  }
  if (found == JNI_FALSE) {
    fatal(jni, "event handler: VThreadTest class was not found in virtual thread context_class_loader classes");
  }
  LOG("test_GetThreadInfo: finished\n");
}


static int
test_GetFrameCount(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread, const char *event_name) {
  jint frame_count = -1;
  jvmtiError err;

  // #1: Test JVMTI GetFrameCount function with nullptr count_ptr pointer
  err = jvmti->GetFrameCount(vthread, nullptr);
  if (err != JVMTI_ERROR_NULL_POINTER) {
    LOG("JVMTI GetFrameCount with null count_ptr returned error: %d\n", err);
    fatal(jni, "event handler: JVMTI GetFrameCount with null count_ptr pointer failed to return JVMTI_ERROR_NULL_POINTER");
  }

  // #2: Test JVMTI GetFrameCount function with a good vthread
  err = jvmti->GetFrameCount(vthread, &frame_count);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetFrameCount");

  if (frame_count < 0) {
    fatal(jni, "event handler: JVMTI GetFrameCount with good vthread returned negative frame_count\n");
  }
  LOG("JVMTI GetFrameCount with good vthread returned frame_count: %d\n", frame_count);

  return frame_count;
}

static void
test_GetFrameLocation(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread, const char *event_name, jint frame_count) {
  jmethodID method = nullptr;
  jlocation location = -1;
  jvmtiError err;

  // #1: Test JVMTI GetFrameLocation function with negative frame depth
  err = jvmti->GetFrameLocation(vthread, -1, &method, &location);
  if (err != JVMTI_ERROR_ILLEGAL_ARGUMENT) {
    LOG("JVMTI GetFrameLocation with negative frame depth returned error: %d\n", err);
    fatal(jni, "event handler: JVMTI GetFrameLocation with negative frame depth failed to return JVMTI_ERROR_ILLEGAL_ARGUMENT");
  }

  // #2: Test JVMTI GetFrameLocation function with nullptr method_ptr
  err = jvmti->GetFrameLocation(vthread, 0, nullptr, &location);
  if (err != JVMTI_ERROR_NULL_POINTER) {
    LOG("JVMTI GetFrameLocation with null method_ptr returned error: %d\n", err);
    fatal(jni, "event handler: JVMTI GetFrameLocation with null method_ptr failed to return JVMTI_ERROR_NULL_POINTER");
  }

  // #3: Test JVMTI GetFrameLocation function with nullptr location_ptr
  err = jvmti->GetFrameLocation(vthread, 0, &method, nullptr);
  if (err != JVMTI_ERROR_NULL_POINTER) {
    LOG("JVMTI GetFrameCount with null location_ptr returned error: %d\n", err);
    fatal(jni, "event handler: JVMTI GetFrameLocation with null location_ptr failed to return JVMTI_ERROR_NULL_POINTER");
  }

  // #4: Test JVMTI GetFrameLocation function with a good vthread
  if (frame_count == 0) {
    err = jvmti->GetFrameLocation(vthread, 0, &method, &location);
    if (err != JVMTI_ERROR_NO_MORE_FRAMES) {
      LOG("JVMTI GetFrameLocation for empty stack returned error: %d\n", err);
      fatal(jni, "event handler: JVMTI GetFrameLocation for empty stack failed to return JVMTI_ERROR_NO_MORE_FRAMES");
    }
    LOG("JVMTI GetFrameLocation for empty stack returned JVMTI_ERROR_NO_MORE_FRAMES as expected\n");
  } else {
    err = jvmti->GetFrameLocation(vthread, frame_count, &method, &location);
    if (err != JVMTI_ERROR_NO_MORE_FRAMES) {
      LOG("JVMTI GetFrameLocation for bid depth == frame_count returned error: %d\n", err);
      fatal(jni, "event handler: JVMTI GetFrameLocation for too big depth failed to return JVMTI_ERROR_NO_MORE_FRAMES");
    }
    LOG("JVMTI GetFrameLocation for too big depth returned JVMTI_ERROR_NO_MORE_FRAMES as expected\n");

    err = jvmti->GetFrameLocation(vthread, 1, &method, &location);
    check_jvmti_status(jni, err, "event handler: error in JVMTI GetFrameLocation");

    if (location < 0) {
      fatal(jni, "event handler: JVMTI GetFrameLocation with good vthread returned negative location\n");
    }
    LOG("JVMTI GetFrameLocation with good vthread returned location: %d\n", (int) location);
  }
}

static void
test_GetStackTrace(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread, const char *event_name, jint frame_count) {
  jvmtiFrameInfo frames[MAX_FRAME_COUNT];
  jint count = -1;
  jmethodID method = nullptr;
  jvmtiError err;

  LOG("\n");

  // #1: Test JVMTI GetStackTrace function with bad start_depth
  err = jvmti->GetStackTrace(vthread, -(frame_count + 1), MAX_FRAME_COUNT, frames, &count);
  if (err != JVMTI_ERROR_ILLEGAL_ARGUMENT) {
    LOG("JVMTI GetStackTrace with negative start_depth returned error: %d\n", err);
    fatal(jni, "event handler: JVMTI GetStackTrace with verynegative start_depth failed to return JVMTI_ERROR_ILLEGAL_ARGUMENT");
  }
  err = jvmti->GetStackTrace(vthread, (frame_count + 1), MAX_FRAME_COUNT, frames, &count);
  if (err != JVMTI_ERROR_ILLEGAL_ARGUMENT) {
    LOG("JVMTI GetStackTrace with very big start_depth returned error: %d\n", err);
    fatal(jni, "event handler: JVMTI GetStackTrace with very big start_depth failed to return JVMTI_ERROR_ILLEGAL_ARGUMENT");
  }

  // #2: Test JVMTI GetStackTrace function with negative max_frame_count
  err = jvmti->GetStackTrace(vthread, 0, -1, frames, &count);
  if (err != JVMTI_ERROR_ILLEGAL_ARGUMENT) {
    LOG("JVMTI GetStackTrace with negative max_frame_count returned error: %d\n", err);
    fatal(jni, "event handler: JVMTI GetStackTrace with negative max_frame_count failed to return JVMTI_ERROR_ILLEGAL_ARGUMENT");
  }

  // #3: Test JVMTI GetStackTrace function with nullptr frame_buffer pointer
  err = jvmti->GetStackTrace(vthread, 0, MAX_FRAME_COUNT, nullptr, &count);
  if (err != JVMTI_ERROR_NULL_POINTER) {
    LOG("JVMTI GetStackTrace with null frame_buffer pointer returned error: %d\n", err);
    fatal(jni, "event handler: JVMTI GetStackTrace witt null frame_buffer pointer failed to return JVMTI_ERROR_NULL_POINTER");
  }

  // #4: Test JVMTI GetStackTrace function with nullptr count_ptr pointer
  err = jvmti->GetStackTrace(vthread, 0, MAX_FRAME_COUNT, frames, nullptr);
  if (err != JVMTI_ERROR_NULL_POINTER) {
    LOG("JVMTI GetStackTrace with null count_ptr pointer returned error: %d\n", err);
    fatal(jni, "event handler: JVMTI GetStackTrace witt null count_ptr pointer failed to return JVMTI_ERROR_NULL_POINTER");
  }

  // #5: Test JVMTI GetStackTrace function with a good vthread
  if (frame_count == 0) {
    err = jvmti->GetStackTrace(vthread, 1, MAX_FRAME_COUNT, frames, &count);
    if (err != JVMTI_ERROR_ILLEGAL_ARGUMENT) {
      LOG("JVMTI GetStackTrace for empty stack returned error: %d\n", err);
      fatal(jni, "event handler: JVMTI GetStackTrace for empty stack failed to return JVMTI_ERROR_ILLEGAL_ARGUMENT");
    }
  } else {
    err = jvmti->GetStackTrace(vthread, 0, MAX_FRAME_COUNT, frames, &count);
    check_jvmti_status(jni, err, "event handler: error in JVMTI GetStackTrace");

    if (count <= 0) {
      fatal(jni, "event handler: JVMTI GetStackTrace with good vthread returned negative frame count\n");
    }
    print_stack_trace_frames(jvmti, jni, count, frames);
  }
}

enum Slots { SlotInvalid0 = -1, SlotObj = 0, SlotInt = 1, SlotLong = 2, SlotUnaligned = 3, SlotFloat = 4, SlotDouble = 5 };

static void
test_GetLocal(jvmtiEnv *jvmti, JNIEnv *jni, jthread cthread, jthread vthread, const char *event_name, jint frame_count) {
  jmethodID method = nullptr;
  jobject obj = nullptr;
  jint ii = 0;
  jlong ll = 0L;
  jfloat ff = 0.0;
  jdouble dd = 0.0;
  jint depth = -1;
  jvmtiError err;

  if (strcmp(event_name, "VirtualThreadMount") != 0 && strcmp(event_name, "VirtualThreadUnmount") != 0) {
    return; // Check GetLocal at VirtualThreadMount/VirtualThreadUnmount events only
  }

  // #0: Test JVMTI GetLocalInstance function for carrier thread
  {
    suspend_thread(jvmti, jni, cthread);

    err = jvmti->GetLocalInstance(cthread, 3, &obj);
    check_jvmti_status(jni, err, "event handler: error in JVMTI GetLocalInstance for carrier thread top frame Continuation.run");
    LOG("JVMTI GetLocalInstance succeed for carrier thread top frame Continuation.run()\n");

    resume_thread(jvmti, jni, cthread);
  }

  depth = find_method_depth(jvmti, jni, vthread, "producer");
  if (depth == -1) {
    return; // skip testing CONSUMER vthreads wich have no producer(String msg) method
  }
  LOG("Testing GetLocal<Type> for method: producer(Ljava/Lang/String;)V at depth: %d\n", depth);

  // #1: Test JVMTI GetLocalObject function with negative frame depth
  err = jvmti->GetLocalObject(vthread, -1, SlotObj, &obj);
  if (err != JVMTI_ERROR_ILLEGAL_ARGUMENT) {
    LOG("JVMTI GetLocalObject with negative frame depth returned error: %d\n", err);
    fatal(jni, "JVMTI GetLocalObject with negative frame depth failed to return JVMTI_ERROR_ILLEGAL_ARGUMENT");
  }

  // #2: Test JVMTI GetLocalObject function with big frame depth
  err = jvmti->GetLocalObject(vthread, frame_count, SlotObj, &obj);
  if (err != JVMTI_ERROR_NO_MORE_FRAMES) {
    LOG("JVMTI GetLocalObject with big frame depth returned error: %d\n", err);
    fatal(jni, "JVMTI GetLocalObject with big frame depth failed to return JVMTI_ERROR_NO_MORE_FRAMES");
  }

  // #3: Test JVMTI GetLocalObject function with invalid slot -1
  err = jvmti->GetLocalObject(vthread, depth, SlotInvalid0, &obj);
  if (err != JVMTI_ERROR_INVALID_SLOT) {
    LOG("JVMTI GetLocalObject with invalid slot -1 returned error: %d\n", err);
    fatal(jni, "JVMTI GetLocalObject with invalid slot -1 failed to return JVMTI_ERROR_INVALID_SLOT");
  }

  // #4: Test JVMTI GetLocalObject function with unaligned slot 3
  err = jvmti->GetLocalObject(vthread, depth, SlotUnaligned, &obj);
  if (err != JVMTI_ERROR_INVALID_SLOT && err != JVMTI_ERROR_TYPE_MISMATCH) {
    LOG("JVMTI GetLocalObject with unaligned slot 3 returned error: %d\n", err);
    fatal(jni, "JVMTI GetLocalObject with unaligned slot 3 failed"
               " to return JVMTI_ERROR_INVALID_SLOT or JVMTI_ERROR_TYPE_MISMATCH");
  }

  // #5: Test JVMTI GetLocalObject function with null value_ptr
  err = jvmti->GetLocalObject(vthread, depth, SlotObj, nullptr);
  if (err != JVMTI_ERROR_NULL_POINTER) {
    LOG("JVMTI GetLocalObject with null method_ptr returned error: %d\n", err);
    fatal(jni, "JVMTI GetLocalObject with null method_ptr failed to return JVMTI_ERROR_NULL_POINTER");
  }

  // #6: Test JVMTI GetLocal<Type> functions with a good vthread
  err = jvmti->GetLocalObject(vthread, depth, SlotObj, &obj);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetLocalObject with good vthread");

  const char* str = jni->GetStringUTFChars((jstring)obj, nullptr);
  LOG("    local String value at slot %d: %s\n", SlotObj, str);
  const char* exp_str = "msg: ...";
  if (strncmp(str, exp_str, 5) != 0) {
    LOG("    Failed: Expected local String value: %s, got: %s\n", exp_str, str);
    fatal(jni, "Got unexpected local String value");
  }
  jni->ReleaseStringUTFChars((jstring)obj, str);

  err = jvmti->GetLocalInt(vthread, depth, SlotInt, &ii);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetLocalInt with good vthread");

  LOG("    local int value at slot %d: %d\n", SlotInt, ii);
  if (ii != 1) {
    LOG("    Failed: Expected local int value: 1, got %d\n", ii);
    fatal(jni, "Got unexpected local int value");
  }

  err = jvmti->GetLocalLong(vthread, depth, SlotLong, &ll);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetLocalLong with good vthread");

  LOG("    local long value at slot %d: %lld\n", SlotLong, (long long)ll);
  if (ll != 2L) {
    LOG("    Failed: Expected local long value: 2L, got %lld\n", (long long)ll);
    fatal(jni, "Got unexpected local long value");
  }

  err = jvmti->GetLocalFloat(vthread, depth, SlotFloat, &ff);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetLocalFloat with good vthread");

  LOG("    local float value at slot %d: %f\n", SlotFloat, ff);
  if (ff < 3.200000 || ff > 3.200001) {
    LOG("    Failed: Expected local float value: 3.200000, got %f\n", ff);
    fatal(jni, "Got unexpected local float value");
  }

  err = jvmti->GetLocalDouble(vthread, depth, SlotDouble, &dd);
  check_jvmti_status(jni, err, "event handler: error in JVMTI GetLocalDouble with good vthread");

  LOG("    local double value at slot %d: %f\n", SlotDouble, dd);
  if (dd < 4.500000047683716 || dd > 4.500000047683717) {
    LOG("    Failed: Expected local double value: 4.500000047683716, got %f\n", dd);
    fatal(jni, "Got unexpected local double value");
  }
}

static void
processVThreadEvent(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread, const char *event_name) {
  static int vthread_events_cnt = 0;
  char* tname = get_thread_name(jvmti, jni, vthread);

  if (strcmp(event_name, "VirtualThreadEnd") != 0 &&
      strcmp(event_name, "VirtualThreadStart")  != 0) {
    if (vthread_events_cnt++ > MAX_EVENTS_TO_PROCESS) {
      return; // No need to test all events
    }
  }
  LOG("processVThreadEvent: event: %s, thread: %s\n", event_name, tname);

  jthread cthread = get_carrier_thread(jvmti, jni, vthread);

  print_vthread_event_info(jvmti, jni, cthread, vthread, event_name);

  deallocate(jvmti, jni, (void*)tname);

  if (strcmp(event_name, "VirtualThreadEnd") == 0) {
    return; // skip further testing as GetVirtualThread can return nullptr
  }

  test_GetVirtualThread(jvmti, jni, cthread, vthread, event_name);
  test_GetCarrierThread(jvmti, jni, cthread, vthread, event_name);

  if (strcmp(event_name, "VirtualThreadStart") == 0) {
    test_GetThreadInfo(jvmti, jni, vthread, event_name);
    return; // skip testing of GetFrame* for VirtualThreadStart events
  }
  jint frame_count = test_GetFrameCount(jvmti, jni, vthread, event_name);
  test_GetFrameLocation(jvmti, jni, vthread, event_name, frame_count);
  test_GetStackTrace(jvmti, jni, vthread, event_name, frame_count);
  test_GetLocal(jvmti, jni, cthread, vthread, event_name, frame_count);
}

static void JNICALL
VirtualThreadStart(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread) {
  RawMonitorLocker rml(jvmti, jni, events_monitor);
  processVThreadEvent(jvmti, jni, vthread, "VirtualThreadStart");
}

static void JNICALL
VirtualThreadEnd(jvmtiEnv *jvmti, JNIEnv *jni, jthread vthread) {
  RawMonitorLocker rml(jvmti, jni, events_monitor);
  processVThreadEvent(jvmti, jni, vthread, "VirtualThreadEnd");
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

  RawMonitorLocker rml(jvmti, jni, events_monitor);
  vthread_mount_count++;
  processVThreadEvent(jvmti, jni, thread, "VirtualThreadMount");
}

// Parameters: (jvmtiEnv *jvmti, JNIEnv* jni, jthread thread)
static void JNICALL
VirtualThreadUnmount(jvmtiEnv *jvmti, ...) {
  va_list ap;
  JNIEnv* jni = nullptr;
  jthread thread = nullptr;

  va_start(ap, jvmti);
  jni = va_arg(ap, JNIEnv*);
  thread = va_arg(ap, jthread);
  va_end(ap);

  RawMonitorLocker rml(jvmti, jni, events_monitor);
  vthread_unmount_count++;
  processVThreadEvent(jvmti, jni, thread, "VirtualThreadUnmount");
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
  callbacks.VirtualThreadStart = &VirtualThreadStart;
  callbacks.VirtualThreadEnd = &VirtualThreadEnd;

  err = set_ext_event_callback(jvmti, "VirtualThreadMount", VirtualThreadMount);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: Error in JVMTI SetExtEventCallback for VirtualThreadMount: %s(%d)\n",
           TranslateError(err), err);
    return JNI_ERR;
  }
  err = set_ext_event_callback(jvmti, "VirtualThreadUnmount", VirtualThreadUnmount);
  if (err != JVMTI_ERROR_NONE) {
    LOG("Agent_OnLoad: Error in JVMTI SetExtEventCallback for VirtualThreadUnmount: %s(%d)\n",
           TranslateError(err), err);
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_support_virtual_threads = 1;
  caps.can_access_local_variables = 1;
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

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, EXT_EVENT_VIRTUAL_THREAD_MOUNT, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI SetEventNotificationMode: %d\n", err);
    return JNI_ERR;
  }

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, EXT_EVENT_VIRTUAL_THREAD_UNMOUNT, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    LOG("error in JVMTI SetEventNotificationMode: %d\n", err);
    return JNI_ERR;
  }

  events_monitor = create_raw_monitor(jvmti, "Events Monitor");
  LOG("Agent_OnLoad finished\n");
  return JNI_OK;
}

JNIEXPORT jboolean JNICALL
Java_VThreadTest_check(JNIEnv *jni, jclass cls) {
  LOG("\n");
  LOG("check: started\n");

  LOG("check: vthread_mount_count:   %d\n", vthread_mount_count);
  LOG("check: vthread_unmount_count: %d\n", vthread_unmount_count);

  if (vthread_mount_count == 0) {
    passed = JNI_FALSE;
    LOG("FAILED: vthread_mount_count == 0\n");
  }
  if (vthread_unmount_count == 0) {
    passed = JNI_FALSE;
    LOG("FAILED: vthread_unmount_count == 0\n");
  }
  LOG("check: finished\n");
  LOG("\n");
  return passed;
}

} // extern "C"
