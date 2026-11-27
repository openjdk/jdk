/*
 * Copyright (c) 2026, Datadog, Inc. All rights reserved.
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

#include <atomic>
#include <mutex>
#include <stdio.h>
#include <string.h>
#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

static const int MAX_FRAMES = 512;
static jvmtiEnv* jvmti = nullptr;
static std::atomic<long> samples{0};
static std::atomic<long> wrong_samples{0};
static std::mutex first_wrong_lock;
static char first_wrong[4096];

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  if (jvm->GetEnv((void**)&jvmti, JVMTI_VERSION) != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }
  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_support_virtual_threads = 1;
  if (jvmti->AddCapabilities(&caps) != JVMTI_ERROR_NONE) {
    LOG("AddCapabilities failed\n");
    return JNI_ERR;
  }
  return JNI_OK;
}

static void record_wrong(const char* text) {
  if (wrong_samples++ == 0) {
    std::lock_guard<std::mutex> guard(first_wrong_lock);
    snprintf(first_wrong, sizeof(first_wrong), "%s", text);
  }
}

static bool class_is(JNIEnv* jni, jobject obj, const char* signature) {
  jclass cls = jni->GetObjectClass(obj);
  char* sig = nullptr;
  check_jvmti_status(jni, jvmti->GetClassSignature(cls, &sig, nullptr), "GetClassSignature failed");
  bool result = strcmp(sig, signature) == 0;
  deallocate(jvmti, jni, sig);
  jni->DeleteLocalRef(cls);
  return result;
}

// Checks that a stack trace is not empty and ends in bottom_method of bottom_class
// (any class if null). A trace cut off by max_frame_count is not checked.
static void check_trace(JNIEnv* jni, const char* what, const jvmtiFrameInfo* frames, jint count,
                        const char* bottom_class, const char* bottom_method) {
  samples++;
  if (count == MAX_FRAMES) {
    return;
  }
  char text[1024];
  if (count == 0) {
    snprintf(text, sizeof(text), "%s: empty stack trace", what);
    record_wrong(text);
    return;
  }
  char* cname = get_method_class_name(jvmti, jni, frames[count - 1].method);
  char* mname = nullptr;
  check_jvmti_status(jni, jvmti->GetMethodName(frames[count - 1].method, &mname, nullptr, nullptr),
                     "GetMethodName failed");
  if (strcmp(mname, bottom_method) != 0 || (bottom_class != nullptr && strcmp(cname, bottom_class) != 0)) {
    snprintf(text, sizeof(text), "%s: %d frames, bottom %s.%s", what, count, cname, mname);
    record_wrong(text);
  }
  deallocate(jvmti, jni, cname);
  deallocate(jvmti, jni, mname);
}

static void check_error(const char* what, jvmtiError err) {
  samples++;
  if (err != JVMTI_ERROR_NONE) {
    char text[256];
    snprintf(text, sizeof(text), "%s: error %d", what, err);
    record_wrong(text);
  }
}

JNIEXPORT void JNICALL
Java_StackTraceTransitionStressTest_sampleJvmti(JNIEnv* jni, jclass cls, jobjectArray vthreads) {
  // Carriers, as GetAllStackTraces reports all platform threads.
  jvmtiStackInfo* infos = nullptr;
  jint count = 0;
  check_jvmti_status(jni, jvmti->GetAllStackTraces(MAX_FRAMES, &infos, &count), "GetAllStackTraces failed");
  for (jint i = 0; i < count; i++) {
    if ((infos[i].state & JVMTI_THREAD_STATE_ALIVE) != 0 &&
        class_is(jni, infos[i].thread, "Ljdk/internal/misc/CarrierThread;")) {
      check_trace(jni, "GetAllStackTraces, carrier", infos[i].frame_buffer, infos[i].frame_count, nullptr, "run");
    }
  }
  deallocate(jvmti, jni, (char*)infos);

  // Virtual threads, one by one and as a list.
  jint n = jni->GetArrayLength(vthreads);
  jthread* threads = new jthread[n];
  jvmtiFrameInfo frames[MAX_FRAMES];
  for (jint i = 0; i < n; i++) {
    threads[i] = jni->GetObjectArrayElement(vthreads, i);
    jint frame_count = 0;
    check_error("GetStackTrace, virtual thread",
                jvmti->GetStackTrace(threads[i], 0, MAX_FRAMES, frames, &frame_count));
    check_trace(jni, "GetStackTrace, virtual thread", frames, frame_count, "jdk/internal/vm/Continuation", "enter");
    jint depth = 0;
    check_error("GetFrameCount, virtual thread", jvmti->GetFrameCount(threads[i], &depth));
    if (depth == 0) {
      record_wrong("GetFrameCount, virtual thread: 0 frames");
    }
    jmethodID method = nullptr;
    jlocation location = 0;
    check_error("GetFrameLocation, virtual thread", jvmti->GetFrameLocation(threads[i], 0, &method, &location));
  }
  jvmtiStackInfo* list = nullptr;
  check_jvmti_status(jni, jvmti->GetThreadListStackTraces(n, threads, MAX_FRAMES, &list),
                     "GetThreadListStackTraces failed");
  for (jint i = 0; i < n; i++) {
    check_trace(jni, "GetThreadListStackTraces, virtual thread", list[i].frame_buffer, list[i].frame_count,
                "jdk/internal/vm/Continuation", "enter");
  }
  deallocate(jvmti, jni, (char*)list);
  for (jint i = 0; i < n; i++) {
    jni->DeleteLocalRef(threads[i]);
  }
  delete[] threads;
}

JNIEXPORT jlong JNICALL
Java_StackTraceTransitionStressTest_samples(JNIEnv* jni, jclass cls) {
  return samples;
}

JNIEXPORT jlong JNICALL
Java_StackTraceTransitionStressTest_wrongSamples(JNIEnv* jni, jclass cls) {
  return wrong_samples;
}

JNIEXPORT jstring JNICALL
Java_StackTraceTransitionStressTest_firstWrongSample(JNIEnv* jni, jclass cls) {
  std::lock_guard<std::mutex> guard(first_wrong_lock);
  return jni->NewStringUTF(wrong_samples == 0 ? "<none>" : first_wrong);
}

JNIEXPORT void JNICALL
Java_StackTraceTransitionStressTest_reportWrong(JNIEnv* jni, jclass cls, jstring sample) {
  const char* text = jni->GetStringUTFChars(sample, nullptr);
  record_wrong(text);
  jni->ReleaseStringUTFChars(sample, text);
}

}
