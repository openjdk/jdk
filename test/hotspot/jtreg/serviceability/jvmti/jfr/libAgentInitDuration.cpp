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

#include <stdio.h>
#include <string.h>

#include "agentTestSupport.hpp"

static jvmtiEnv* retained_env = nullptr;

static void JNICALL vm_start(jvmtiEnv* env, JNIEnv* jni_env) {
  java_hook(jni_env, "TestAgentInitDuration", "vmStarted");
}

static void JNICALL dispose_both_on_vm_init(jvmtiEnv* env, JNIEnv* jni_env, jthread thread) {
  java_hook(jni_env, "TestAgentInitDuration", "beginDisposeBothCallback");
  dispose_environment(jni_env, retained_env);
  retained_env = nullptr;
  java_hook(jni_env, "TestAgentInitDuration", "verifyDisposeEnvironmentBeforeCallbackSnapshot");
  dispose_environment(jni_env, env);
}

static void JNICALL dispose_own_on_vm_init(jvmtiEnv* env, JNIEnv* jni_env, jthread thread) {
  java_hook(jni_env, "TestAgentInitDuration", "beginDisposeOwnCallback");
  dispose_environment(jni_env, env);
  retained_env = nullptr;
  java_hook(jni_env, "TestAgentInitDuration", "verifyDisposeEnvironmentInCallbackSnapshot");
}

static void JNICALL dispose_on_worker_on_vm_init(jvmtiEnv* env, JNIEnv* jni_env, jthread thread) {
  java_hook(jni_env, "TestAgentInitDuration", "disposeEnvironmentOnWorker");
}

static jint register_callbacks(jvmtiEnv* env, const jvmtiEventCallbacks& callbacks) {
  if (env->SetEventCallbacks(&callbacks, sizeof(callbacks)) != JVMTI_ERROR_NONE ||
      env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, nullptr) != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  if (callbacks.VMStart != nullptr &&
      env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_START, nullptr) != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  return JNI_OK;
}

static jint dispose_environments_in_callback(JavaVM* vm) {
  jvmtiEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  jvmtiEventCallbacks callbacks = {};
  callbacks.VMInit = &dispose_both_on_vm_init;
  return register_callbacks(env, callbacks);
}

static jint dispose_environment_in_callback(JavaVM* vm) {
  jvmtiEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  jvmtiEventCallbacks callbacks = {};
  callbacks.VMInit = &dispose_own_on_vm_init;
  retained_env = env;
  callbacks.VMStart = &vm_start;
  return register_callbacks(env, callbacks);
}

static jint dispose_environment_on_worker(JavaVM* vm) {
  jvmtiEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  jvmtiEventCallbacks callbacks = {};
  callbacks.VMInit = &dispose_on_worker_on_vm_init;
  retained_env = env;
  callbacks.VMStart = &vm_start;
  return register_callbacks(env, callbacks);
}

static jint create_environment_without_callback(JavaVM* vm) {
  jvmtiEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  retained_env = env;
  return JNI_OK;
}

/*
 * Agent_OnLoad options format: <operation>.
 *
 * Each invocation obtains its own JVMTI environment. Here, callback means VMInit.
 *
 * dispose-environments-in-callback:
 *     Register VMInit; in that callback, dispose the other invocation's retained environment
 *     before its VMInit callback can run, then dispose the callback's own environment.
 * dispose-environment-in-callback:
 *     Register VMStart and VMInit; if VMInit runs, dispose the callback's own environment.
 * dispose-environment-on-worker:
 *     Register VMStart and VMInit; release a worker to dispose the retained environment
 *     just before VMInit returns, allowing disposal to compete with timing publication.
 * create-environment-without-callback:
 *     Register no callbacks; retain the environment for the Java test to dispose.
 */

static inline bool equals(const char* x, const char* y) {
  return strcmp(x, y) == 0;
}

extern "C" JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* vm, char* options, void* reserved) {
  if (options == nullptr) {
    return JNI_ERR;
  }
  if (equals(options, "dispose-environments-in-callback")) {
    return dispose_environments_in_callback(vm);
  }
  if (equals(options, "dispose-environment-in-callback")) {
    return dispose_environment_in_callback(vm);
  }
  if (equals(options, "dispose-environment-on-worker")) {
    return dispose_environment_on_worker(vm);
  }
  if (equals(options, "create-environment-without-callback")) {
    return create_environment_without_callback(vm);
  }
  return JNI_ERR;
}

extern "C" JNIEXPORT void JNICALL Java_TestAgentInitDuration_disposeRetainedEnvironment(JNIEnv* jni_env, jclass klass) {
  dispose_environment(jni_env, retained_env);
  retained_env = nullptr;
}

extern "C" JNIEXPORT void JNICALL Agent_OnUnload(JavaVM* vm) {
  puts("unloaded:AgentInitDuration");
  fflush(stdout);
}
