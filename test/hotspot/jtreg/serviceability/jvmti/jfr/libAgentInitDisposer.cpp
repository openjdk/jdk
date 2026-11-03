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

#include "agentTestSupport.hpp"

static void JNICALL dispose_both_on_vm_init(jvmtiEnv* env, JNIEnv* jni_env, jthread thread) {
  java_hook(jni_env, "TestAgentInitDuration", "disposeEnvironmentFromOtherLibraryCallback");
  dispose_environment(jni_env, env);
}

static jint dispose_environments_in_callback(JavaVM* vm) {
  jvmtiEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  jvmtiEventCallbacks callbacks = {};
  callbacks.VMInit = &dispose_both_on_vm_init;
  if (env->SetEventCallbacks(&callbacks, sizeof(callbacks)) != JVMTI_ERROR_NONE ||
      env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, nullptr) != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  return JNI_OK;
}

/*
 * Agent_OnLoad options format: <operation>.
 *
 * dispose-environments-in-callback:
 *     Register VMInit; in that callback, invoke the Java test to resolve the other
 *     library's native disposal method before its agent's VMInit callback can run,
 *     then dispose this callback's own JVMTI environment.
 *
 * Expected startup returns JNI_OK; unexpected operation failures return JNI_ERR.
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
  return JNI_ERR;
}

extern "C" JNIEXPORT void JNICALL Agent_OnUnload(JavaVM* vm) {
  puts("unloaded:AgentInitDisposer");
  fflush(stdout);
}
