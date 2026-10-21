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

#include <cstring>

extern "C" {

static jvmtiEnv* jvmti = nullptr;


static jclass payload_class = nullptr;

static jint sampled_value = -1;

JNIEXPORT void JNICALL
SampledObjectAlloc(jvmtiEnv* jvmti_env,
                   JNIEnv* jni,
                   jthread thread,
                   jobject object,
                   jclass object_class,
                   jlong size) {
  if (jni->IsSameObject(object_class, payload_class)) {
    jobject sample = jni->NewGlobalRef(object);
    if (sample == nullptr) {
      jni->FatalError("Could not create sampled object reference");
    }

    jfieldID field = jni->GetFieldID(payload_class, "value", "I");
    if (field == nullptr) {
      jni->FatalError("Could not find Payload.intValue");
    }

    sampled_value = jni->GetIntField(object, field);
  }
}

JNIEXPORT jint JNICALL
Java_SampledObjecAfterCloningTest_sampledObjectField(JNIEnv* jni, jclass cls, jobject object) {
  return sampled_value;
}


JNIEXPORT void JNICALL
Java_SampledObjecAfterCloningTest_init(JNIEnv* env, jclass cls, jclass payload_cls) {
  payload_class = (jclass)env->NewGlobalRef(payload_cls);

  jvmtiError err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_SAMPLED_OBJECT_ALLOC, nullptr);
  check_jvmti_error(err, "SetEventNotificationMode");
}

static jint Agent_Initialize(JavaVM* jvm, char* options, void* reserved) {
  jint res = jvm->GetEnv((void**)&jvmti, JVMTI_VERSION_9);
  if (res != JNI_OK || jvmti == nullptr) {
    return JNI_ERR;
  }

  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_generate_sampled_object_alloc_events = 1;
  jvmtiError err = jvmti->AddCapabilities(&caps);
  check_jvmti_error(err, "AddCapabilities");

  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.SampledObjectAlloc = &SampledObjectAlloc;

  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  check_jvmti_error(err, "SetEventCallbacks");

  err = jvmti->SetHeapSamplingInterval(0);
  check_jvmti_error(err, "SetHeapSamplingInterval");

  return JNI_OK;
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* jvm, char* options, void* reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

}
