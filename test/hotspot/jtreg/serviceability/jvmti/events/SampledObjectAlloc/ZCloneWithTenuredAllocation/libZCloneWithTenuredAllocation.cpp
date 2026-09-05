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
#include <cstring>

extern "C" {

static jvmtiEnv* jvmti = nullptr;

static jclass test_class = nullptr;
static jclass payload_class = nullptr;
static jclass marker_pair_array_class = nullptr;
static jmethodID tenure_method = nullptr;

static std::atomic<bool> in_callback(false);
static jobject sample = nullptr;

static bool is_same_class(JNIEnv* jni, jclass first, jclass second) {
  return jni->IsSameObject(first, second) == JNI_TRUE;
}

static bool should_tenure(JNIEnv* jni, jclass object_klass) {
  return is_same_class(jni, object_klass, payload_class) ||
         is_same_class(jni, object_klass, marker_pair_array_class);
}

static void record_sampled_object(JNIEnv* jni, jobject object) {
  if (sample != nullptr) {
    jni->DeleteGlobalRef(sample);
  }

  sample = jni->NewGlobalRef(object);
  if (sample == nullptr) {
    jni->FatalError("Could not create sampled object reference");
  }
}

static void call_tenure(JNIEnv* jni, jobject object) {
  jni->CallStaticVoidMethod(test_class, tenure_method, object);
  if (jni->ExceptionCheck()) {
    jni->ExceptionDescribe();
    jni->FatalError("ZCloneWithTenuredAllocation.tenure failed");
  }
}

JNIEXPORT void JNICALL
SampledObjectAlloc(jvmtiEnv* jvmti_env,
                   JNIEnv* jni,
                   jthread thread,
                   jobject object,
                   jclass object_klass,
                   jlong size) {
  if (!should_tenure(jni, object_klass)) {
    return;
  }

  if (in_callback.exchange(true)) {
    return;
  }

  record_sampled_object(jni, object);
  call_tenure(jni, object);

  in_callback.store(false);
}

JNIEXPORT void JNICALL
Java_ZCloneWithTenuredAllocation_init(JNIEnv* env, jclass cls, jclass payload_cls, jclass marker_pair_array_cls) {
  if (test_class != nullptr || payload_class != nullptr || marker_pair_array_class != nullptr || tenure_method != nullptr) {
    env->FatalError("ZCloneWithTenuredAllocation.init called more than once");
  }

  test_class = (jclass)env->NewGlobalRef(cls);
  payload_class = (jclass)env->NewGlobalRef(payload_cls);
  marker_pair_array_class = (jclass)env->NewGlobalRef(marker_pair_array_cls);

  tenure_method = env->GetStaticMethodID(cls, "tenure", "(Ljava/lang/Object;)V");
  if (tenure_method == nullptr) {
    env->FatalError("Could not find ZCloneWithTenuredAllocation.tenure");
  }
}

JNIEXPORT jboolean JNICALL
Java_ZCloneWithTenuredAllocation_isSampledObject(JNIEnv* env, jclass cls, jobject object) {
  return env->IsSameObject(object, sample);
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

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_SAMPLED_OBJECT_ALLOC, nullptr);
  check_jvmti_error(err, "SetEventNotificationMode");

  return JNI_OK;
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* jvm, char* options, void* reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

}
