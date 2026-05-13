/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "jvmti.h"
#include "jvmti_common.hpp"

#include <atomic>

extern "C" {

static std::atomic<int> events_counter(0);
static jvmtiEnv* jvmti;
static jclass expected_class;

static bool
is_test_class(jvmtiEnv* jvmti, JNIEnv* jni, jclass cls) {
  char* sig = nullptr;
  check_jvmti_error(jvmti->GetClassSignature(cls, &sig, nullptr), "GetClassSignature");

  LOG("Object class: %s\n", sig);
  jvmti->Deallocate((unsigned char *)sig);
  bool res = (jni->IsSameObject(cls, expected_class) == JNI_TRUE);
  return res;
}

JNIEXPORT void JNICALL
Java_SampledObjectAllocValue_enableEvents(JNIEnv* jni, jclass klass, jthread thread, jclass tested_class) {
  if (events_counter != 0) {
    fatal(jni, "SampledObjectAlloc events counter should be zero");
  }
  LOG("enableEvents: events_counter: %d\n", events_counter.load());

  expected_class = (jclass)jni->NewGlobalRef(tested_class);
  jvmtiError err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_SAMPLED_OBJECT_ALLOC, thread);
  check_jvmti_error(err, "SetEventNotificationMode for SAMPLED_OBJECT_ALLOC");
}

JNIEXPORT void JNICALL
SampledObjectAlloc(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread, jobject object, jclass klass, jlong size) {
  if (klass == nullptr) {
    fatal(jni, "klass in SampledObjectAlloc callback is not expected to be null");
  }
  if (!is_test_class(jvmti, jni, klass)) {
    return; // interested in tested class only
  }
  if (size == 0L) {
    fatal(jni, "size in SampledObjectAlloc callback is not expected to be 0");
  }
  if (object != nullptr) {
    fatal(jni, "object in SampledObjectAlloc callback is expected to be null for value object allocations");
  }
  events_counter++;
  LOG("SampledObjectAlloc: events_counter: %d\n", events_counter.load());
}

void JNICALL
VMDeath(jvmtiEnv* jvmti, JNIEnv* jni) {
  jvmtiError err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_SAMPLED_OBJECT_ALLOC, nullptr);
  check_jvmti_error(err, "SetEventNotificationMode for SAMPLED_OBJECT_ALLOC");

  if (events_counter == 0) {
    fatal(jni, "SampledObjectAlloc events counter shouldn't be zero");
  }
  LOG("VMDeath: events_counter: %d\n", events_counter.load());
}

jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiEventCallbacks callbacks;
  jvmtiError err;
  jint res;

  LOG("Agent_Initialize\n");
  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_9);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  memset(&caps, 0, sizeof(caps));
  caps.can_generate_sampled_object_alloc_events = 1;
  if (jvmti->AddCapabilities(&caps) != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.VMDeath = &VMDeath;
  callbacks.SampledObjectAlloc = &SampledObjectAlloc;

  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  check_jvmti_error(err, "SetEventCallbacks");

  // Interval should be small enough to triggger sampling event.
  err = jvmti->SetHeapSamplingInterval(100);
  check_jvmti_error(err, "SetHeapSamplingInterval");

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, nullptr);
  check_jvmti_error(err, "SetEventNotificationMode for VM_DEATH");

  return JNI_OK;
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}
}
