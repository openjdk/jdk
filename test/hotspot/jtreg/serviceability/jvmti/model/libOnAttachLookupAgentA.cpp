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

#include "jvmti.h"

static jvmtiEnv* retained_env = nullptr;

static jint idle(int value) {
  return value;
}

static jint create_environment(JavaVM* vm, int value) {
  if (retained_env != nullptr || vm->GetEnv(reinterpret_cast<void**>(&retained_env), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  return value;
}

static jint dispose_environment(int value) {
  if (retained_env == nullptr || retained_env->DisposeEnvironment() != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  retained_env = nullptr;
  return value;
}

/*
 * Agent_OnAttach options format: <operation>, <slot>, <value>.
 *
 * Operation selects the action. The -success/-fail suffix describes the expected startup outcome:
 *
 * idle-success / idle-fail:
 *     Leave the retained JVMTI environment unchanged.
 * create-environment-success / create-environment-fail:
 *     Create and retain a JVMTI environment in the empty slot.
 * dispose-environment-success:
 *     Dispose the retained JVMTI environment and clear the slot.
 *
 * Slot must be 0; this library has one retained-environment slot.
 * Idle does not use the retained slot, but still requires 0.
 *
 * Value supplies the exact Agent_OnAttach return code, not a native lookup result.
 * Unexpected operation failures return JNI_ERR.
 */

static jint parse(const char* options, char* operation, int* slot, int* value) {
  if (options == nullptr) {
    return JNI_ERR;
  }
  if (sscanf(options, "%63[^,],%d,%d", operation, slot, value) != 3) {
    return JNI_ERR;
  }
  return *slot != 0 ? JNI_ERR : JNI_OK;
}

static inline bool equals(const char* x, const char* y) {
  return strcmp(x, y) == 0;
}

extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* options, void* reserved) {
  char operation[64] = {};
  int slot = -1;
  int value = -1;
  if (parse(options, &operation[0], &slot, &value) != JNI_OK) {
    return JNI_ERR;
  }
  char* suffix = strrchr(operation, '-');
  if (suffix == nullptr) {
    return JNI_ERR;
  }
  *suffix = '\0';
  if (equals(operation, "idle")) {
    return idle(value);
  }
  if (equals(operation, "create-environment")) {
    return create_environment(vm, value);
  }
  if (equals(operation, "dispose-environment")) {
    return dispose_environment(value);
  }
  return JNI_ERR;
}

extern "C" JNIEXPORT jint JNICALL Java_TestNativeLookupLifetime_firstResolutionAgentA(JNIEnv* jni, jclass klass) {
  return 101;
}

extern "C" JNIEXPORT jint JNICALL Java_TestNativeLookupLifetime_nextResolutionAgentA(JNIEnv* jni, jclass klass) {
  return 102;
}

extern "C" JNIEXPORT jint JNICALL Java_TestNativeLookupOrder_afterAgentBSuccess(JNIEnv* jni, jclass klass) {
  return 101;
}

extern "C" JNIEXPORT jint JNICALL Java_TestNativeLookupOrder_afterAgentASuccess(JNIEnv* jni, jclass klass) {
  return 101;
}

extern "C" JNIEXPORT jint JNICALL Java_TestNativeLookupOrder_afterRepeatedAttach(JNIEnv* jni, jclass klass) {
  return 101;
}

extern "C" JNIEXPORT void JNICALL Agent_OnUnload(JavaVM* vm) {
  puts("unloaded:OnAttachLookupAgentA");
  fflush(stdout);
}
