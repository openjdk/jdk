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
#include <stdio.h>
#include <string.h>
#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

static const int MAX_FRAMES = 256;
static jvmtiEnv* jvmti = nullptr;
static std::atomic<long> carrier_traces{0};
static std::atomic<long> wrong_traces{0};
static std::atomic<bool> recorded{false};
static char first_wrong[4096];

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  if (jvm->GetEnv((void**)&jvmti, JVMTI_VERSION) != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }
  return JNI_OK;
}

static bool is_carrier(JNIEnv* jni, jthread thread) {
  jclass cls = jni->GetObjectClass(thread);
  char* sig = nullptr;
  check_jvmti_status(jni, jvmti->GetClassSignature(cls, &sig, nullptr), "GetClassSignature failed");
  bool result = strcmp(sig, "Ljdk/internal/misc/CarrierThread;") == 0;
  deallocate(jvmti, jni, sig);
  jni->DeleteLocalRef(cls);
  return result;
}

static bool method_is_run(JNIEnv* jni, jmethodID method) {
  char* m = nullptr;
  check_jvmti_status(jni, jvmti->GetMethodName(method, &m, nullptr, nullptr), "GetMethodName failed");
  bool result = strcmp(m, "run") == 0;
  deallocate(jvmti, jni, m);
  return result;
}

static void record_wrong(JNIEnv* jni, const jvmtiStackInfo& si) {
  wrong_traces++;
  if (recorded.exchange(true)) {
    return;
  }
  int len = snprintf(first_wrong, sizeof(first_wrong), "%d frames:", si.frame_count);
  for (jint i = 0; i < si.frame_count && len < (int)sizeof(first_wrong) - 1; i++) {
    char* c = get_method_class_name(jvmti, jni, si.frame_buffer[i].method);
    char* m = nullptr;
    check_jvmti_status(jni, jvmti->GetMethodName(si.frame_buffer[i].method, &m, nullptr, nullptr), "GetMethodName failed");
    len += snprintf(first_wrong + len, sizeof(first_wrong) - len, " %s.%s", c, m);
    deallocate(jvmti, jni, c);
    deallocate(jvmti, jni, m);
  }
}

JNIEXPORT void JNICALL
Java_GetAllStackTracesCarrierTest_sample(JNIEnv* jni, jclass cls) {
  jvmtiStackInfo* infos = nullptr;
  jint count = 0;
  check_jvmti_status(jni, jvmti->GetAllStackTraces(MAX_FRAMES, &infos, &count), "GetAllStackTraces failed");
  for (jint i = 0; i < count; i++) {
    const jvmtiStackInfo& si = infos[i];
    if ((si.state & JVMTI_THREAD_STATE_ALIVE) == 0 || !is_carrier(jni, si.thread)) {
      continue;
    }
    carrier_traces++;
    if (si.frame_count == 0) {
      record_wrong(jni, si);
      continue;
    }
    if (si.frame_count < MAX_FRAMES && !method_is_run(jni, si.frame_buffer[si.frame_count - 1].method)) {
      record_wrong(jni, si);
    }
  }
  deallocate(jvmti, jni, (char*)infos);
}

JNIEXPORT jlong JNICALL
Java_GetAllStackTracesCarrierTest_carrierTraces(JNIEnv* jni, jclass cls) {
  return carrier_traces;
}

JNIEXPORT jlong JNICALL
Java_GetAllStackTracesCarrierTest_wrongTraces(JNIEnv* jni, jclass cls) {
  return wrong_traces;
}

JNIEXPORT jstring JNICALL
Java_GetAllStackTracesCarrierTest_firstWrongTrace(JNIEnv* jni, jclass cls) {
  return jni->NewStringUTF(recorded ? first_wrong : "<none>");
}

}
