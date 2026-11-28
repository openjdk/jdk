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

#include <stdio.h>
#include <string.h>

#include "jvmti.h"

static void JNICALL dispose_in_vm_start(jvmtiEnv* env, JNIEnv* jni) {
  jvmtiError error = env->DisposeEnvironment();
  if (error != JVMTI_ERROR_NONE) {
    fprintf(stderr, "DisposeEnvironment failed: %d\n", error);
    return;
  }
  puts("disposed-before-vm-init");
  fflush(stdout);
}

static jint create_environment_without_callback(JavaVM* vm) {
  jvmtiEnv* env = nullptr;
  return vm->GetEnv(reinterpret_cast<void**>(&env), JVMTI_VERSION);
}

static jint dispose_environment_in_vm_start(JavaVM* vm) {
  jvmtiEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  jvmtiEventCallbacks callbacks = {};
  callbacks.VMStart = &dispose_in_vm_start;
  if (env->SetEventCallbacks(&callbacks, sizeof(callbacks)) != JVMTI_ERROR_NONE ||
      env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_START, nullptr) != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  return JNI_OK;
}

/*
 * Agent_OnLoad options format: <operation>.
 *
 * create-environment-without-callback:
 *     Retain a JVMTI environment without registering VMInit, so timing uses the fallback.
 * dispose-environment-in-vm-start:
 *     Dispose the associated environment in VMStart, before initialization timing begins.
 *
 * Options are not modified. Entry-point markers distinguish ordinary startup from legacy -Xrun.
 */

static inline bool equals(const char* x, const char* y) {
  return strcmp(x, y) == 0;
}

extern "C" JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* vm, char* options, void* reserved) {
  if (options == nullptr) {
    return JNI_ERR;
  }
  printf("Agent_OnLoad:%s\n", options);
  fflush(stdout);
  if (equals(options, "create-environment-without-callback")) {
    return create_environment_without_callback(vm);
  }
  if (equals(options, "dispose-environment-in-vm-start")) {
    return dispose_environment_in_vm_start(vm);
  }
  return JNI_ERR;
}

// A legacy entry point prevents -Xrun conversion to Agent_OnLoad. It creates no JVMTI environment.
extern "C" JNIEXPORT jint JNICALL JVM_OnLoad(JavaVM* vm, char* options, void* reserved) {
  printf("JVM_OnLoad:%s\n", options == nullptr ? "none" : options);
  fflush(stdout);
  return JNI_OK;
}
