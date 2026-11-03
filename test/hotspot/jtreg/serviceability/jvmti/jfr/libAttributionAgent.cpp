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
 *
 */

#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "agentTestSupport.hpp"

static jvmtiEnv* outer_env = nullptr;
static jvmtiEnv* inner_env = nullptr;
static jvmtiEnv* retained_env = nullptr;

static void JNICALL dispose_on_worker(JNIEnv* jni_env, jclass klass, jlong address) {
  dispose_environment(jni_env, reinterpret_cast<jvmtiEnv*>(static_cast<intptr_t>(address)));
}

static jint create_environment_after_nested_attach(JavaVM* vm, JNIEnv* jni_env) {
  java_hook(jni_env, "TestStartupAttribution", "attachNestedAgent");
  return vm->GetEnv(reinterpret_cast<void**>(&outer_env), JVMTI_VERSION);
}

static jint create_two_environments_dispose_one(JavaVM* vm, JNIEnv* jni_env) {
  java_hook(jni_env, "TestStartupAttribution", "verifyNestedAttachThread");
  if (vm->GetEnv(reinterpret_cast<void**>(&inner_env), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  // A second environment must not be associated with the still-unassociated outer startup.
  jvmtiEnv* extra_env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&extra_env), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  dispose_environment(jni_env, extra_env);
  return JNI_OK;
}

static jint dispose_environments(JNIEnv* jni_env) {
  dispose_environment(jni_env, outer_env);
  outer_env = nullptr;
  dispose_environment(jni_env, inner_env);
  inner_env = nullptr;
  return JNI_OK;
}

static jint create_dispose_create_environment(JavaVM* vm, JNIEnv* jni_env) {
  jvmtiEnv* first_env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&first_env), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  dispose_environment(jni_env, first_env);
  return vm->GetEnv(reinterpret_cast<void**>(&retained_env), JVMTI_VERSION);
}

static jint create_environment_dispose_on_worker(JavaVM* vm, JNIEnv* jni_env) {
  jvmtiEnv* pending_env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&pending_env), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  jclass klass = application_class(jni_env, "TestStartupAttribution");
  JNINativeMethod disposal = {
    const_cast<char*>("disposeOnWorker"), const_cast<char*>("(J)V"),
    reinterpret_cast<void*>(&dispose_on_worker)
  };
  if (jni_env->RegisterNatives(klass, &disposal, 1) != JNI_OK) {
    check_java(jni_env);
    return JNI_ERR;
  }
  jmethodID dispose_method = jni_env->GetStaticMethodID(klass, "disposeEnvironmentOnWorker", "(J)V");
  check_java(jni_env);
  jni_env->CallStaticVoidMethod(klass, dispose_method, static_cast<jlong>(reinterpret_cast<intptr_t>(pending_env)));
  check_java(jni_env);
  jni_env->DeleteLocalRef(klass);
  return JNI_OK;
}

/*
 * Agent_OnAttach options format: <operation>.
 *
 * Each attach passes one operation string to Agent_OnAttach and expects JNI_OK (0).
 * These operations do not use the slot and value arguments of the other attach tests.
 *
 * create-environment-after-nested-attach-success:
 *     Attach this library recursively on the current thread, then create and retain
 *     the outer startup's JVMTI environment.
 * create-two-environments-dispose-one-success:
 *     Verify same-thread nesting, create two JVMTI environments, retain the first,
 *     and dispose the second. The first belongs to this startup; the second must
 *     remain unassociated, even though the outer startup has no environment yet.
 * dispose-environments-success:
 *     Dispose the retained environments from the outer and nested startups.
 * create-dispose-create-environment-success:
 *     Within one startup, create and dispose the first JVMTI environment, then create
 *     and retain a second. Disposal must not make this startup eligible for association again.
 * create-environment-dispose-on-worker-success:
 *     Create a JVMTI environment on the startup thread and dispose it on a worker.
 *     Wait for disposal before returning from Agent_OnAttach.
 *
 * Dispatch ignores the descriptive suffix.
 */

static jint get_jni_env(JavaVM* vm, const char* options, size_t buffer_size, JNIEnv** jni_env) {
  if (options == nullptr || strlen(options) >= buffer_size) {
    return JNI_ERR;
  }
  return vm->GetEnv(reinterpret_cast<void**>(jni_env), JNI_VERSION_1_8);
}

static inline bool equals(const char* x, const char* y) {
  return strcmp(x, y) == 0;
}

extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* options, void* reserved) {
  char operation[64] = {};
  JNIEnv* jni_env = nullptr;
  if (get_jni_env(vm, options, sizeof(operation), &jni_env) != JNI_OK) {
    return JNI_ERR;
  }
  strcpy(operation, options);
  char* suffix = strrchr(operation, '-');
  if (suffix == nullptr) {
    return JNI_ERR;
  }
  *suffix = '\0';
  if (equals(operation, "create-environment-after-nested-attach")) {
    return create_environment_after_nested_attach(vm, jni_env);
  }
  if (equals(operation, "create-two-environments-dispose-one")) {
    return create_two_environments_dispose_one(vm, jni_env);
  }
  if (equals(operation, "dispose-environments")) {
    return dispose_environments(jni_env);
  }
  if (equals(operation, "create-dispose-create-environment")) {
    return create_dispose_create_environment(vm, jni_env);
  }
  if (equals(operation, "create-environment-dispose-on-worker")) {
    return create_environment_dispose_on_worker(vm, jni_env);
  }
  return JNI_ERR;
}

extern "C" JNIEXPORT void JNICALL Java_TestStartupAttribution_verifyRetainedEnvironment(JNIEnv* jni_env, jclass klass) {
  jvmtiPhase phase;
  if (retained_env == nullptr || retained_env->GetPhase(&phase) != JVMTI_ERROR_NONE || phase != JVMTI_PHASE_LIVE) {
    jni_env->FatalError("Retained JVMTI environment is not live");
  }
}

extern "C" JNIEXPORT void JNICALL Java_TestStartupAttribution_disposeRetainedEnvironment(JNIEnv* jni_env, jclass klass) {
  dispose_environment(jni_env, retained_env);
  retained_env = nullptr;
}

extern "C" JNIEXPORT void JNICALL Agent_OnUnload(JavaVM* vm) {
  puts("unloaded:AttributionAgent");
  fflush(stdout);
}
