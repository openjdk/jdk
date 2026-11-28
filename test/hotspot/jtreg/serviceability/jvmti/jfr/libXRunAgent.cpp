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

#include <atomic>
#include <stdio.h>
#include <string.h>

#include "jvmti.h"

static std::atomic<jint> startup_count(0);
static jvmtiEnv* retained_env = nullptr;

static inline jint increment_startup_count() {
  ++startup_count;
  return JNI_OK;
}

static inline jint read_startup_count() {
  return startup_count.load();
}

static jint create_environment_without_callback(JavaVM* vm) {
  return vm->GetEnv(reinterpret_cast<void**>(&retained_env), JVMTI_VERSION);
}

static inline bool equals(const char* x, const char* y) {
  return strcmp(x, y) == 0;
}

/*
 * Agent_OnLoad options format: <operation>.
 * create-environment-without-callback: retain one JVMTI environment without callbacks.
 * Deliberately omit JVM_OnLoad: -Xrun agent conversion transforms this into to the same startup path as -agentlib.
 */

extern "C" JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* vm, char* options, void* reserved) {
  increment_startup_count();
  if (options != nullptr && equals(options, "create-environment-without-callback")) {
    return create_environment_without_callback(vm);
  }
  return JNI_ERR;
}

extern "C" JNIEXPORT jint JNICALL Java_TestXRunAgentEvents_nativeLookupBeforeDisposal(JNIEnv* jni_env, jclass klass) {
  jvmtiPhase phase;
  if (retained_env == nullptr || retained_env->GetPhase(&phase) != JVMTI_ERROR_NONE || phase != JVMTI_PHASE_LIVE) {
    jni_env->FatalError("Expected a live JVMTI environment from Agent_OnLoad");
  }
  return read_startup_count();
}

extern "C" JNIEXPORT void JNICALL Java_TestXRunAgentEvents_disposeEnvironment(JNIEnv* jni_env, jclass klass) {
  if (retained_env == nullptr || retained_env->DisposeEnvironment() != JVMTI_ERROR_NONE) {
    jni_env->FatalError("Could not dispose the Agent_OnLoad environment");
  }
  retained_env = nullptr;
}

extern "C" JNIEXPORT jint JNICALL Java_TestXRunAgentEvents_nativeLookupAfterDisposal(JNIEnv* jni_env, jclass klass) {
  if (retained_env != nullptr) {
    jni_env->FatalError("Agent_OnLoad environment was not disposed");
  }
  return read_startup_count();
}

extern "C" JNIEXPORT void JNICALL Agent_OnUnload(JavaVM* vm) {
  puts("unloaded:XRunAgent");
  fflush(stdout);
}
