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

static jvmtiEnv* environments[2] = {nullptr, nullptr};
static int visits[2] = {0, 0};

static jint check_no_environments(int value) {
  return environments[0] == nullptr && environments[1] == nullptr ? value : JNI_ERR;
}

static jint create_environment(JavaVM* vm, int slot, int value) {
  if (environments[slot] != nullptr ||
      vm->GetEnv(reinterpret_cast<void**>(&environments[slot]), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  visits[slot] = 0;
  return value;
}

static jint visit_environment(int slot, int value) {
  jvmtiPhase phase;
  if (environments[slot] == nullptr || value != visits[slot] + 1 ||
      environments[slot]->GetPhase(&phase) != JVMTI_ERROR_NONE || phase != JVMTI_PHASE_LIVE) {
    return JNI_ERR;
  }
  visits[slot] = value;
  return JNI_OK;
}

static jint dispose_environment(int slot, int value) {
  if (environments[slot] == nullptr || environments[slot]->DisposeEnvironment() != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  environments[slot] = nullptr;
  return value;
}

static jint create_dispose_environment(JavaVM* vm, int value) {
  jvmtiEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JVMTI_VERSION) != JNI_OK ||
      env->DisposeEnvironment() != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  return value;
}

/*
 * Agent_OnAttach options format: <operation>, <slot>, <value>.
 *
 * Operation selects the action. The -success/-fail suffix describes the expected startup outcome:
 *
 * check-no-environments-success / check-no-environments-fail:
 *     Check that neither slot holds a JVMTI environment.
 * create-environment-success / create-environment-fail:
 *     Create a JVMTI environment in an empty slot and reset its visit count.
 * visit-environment-success:
 *     Check the next visit number and live phase, then advance the count.
 * dispose-environment-success / dispose-environment-fail:
 *     Dispose the JVMTI environment in the selected slot and clear the slot.
 * create-dispose-environment-success / create-dispose-environment-fail:
 *     Create and dispose a JVMTI environment within this attach call.
 *
 * Slot selects retained JVMTI environment 0 or 1.
 * The slot is unused by check-no-environments and create-dispose-environment,
 * but both operations still require a valid slot number.
 *
 * Value supplies the exact return code, except for visit-environment-success,
 * where it is the expected next visit number.
 * Unexpected operation failures return JNI_ERR.
 */

static jint parse(const char* options, char* operation, int* slot, int* value) {
  if (options == nullptr) {
    return JNI_ERR;
  }
  if (sscanf(options, "%63[^,],%d,%d", operation, slot, value) != 3) {
    return JNI_ERR;
  }
  return *slot < 0 || *slot >= 2 ? JNI_ERR : JNI_OK;
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
  if (equals(operation, "check-no-environments")) {
    return check_no_environments(value);
  }
  if (equals(operation, "create-environment")) {
    return create_environment(vm, slot, value);
  }
  if (equals(operation, "visit-environment")) {
    return visit_environment(slot, value);
  }
  if (equals(operation, "dispose-environment")) {
    return dispose_environment(slot, value);
  }
  if (equals(operation, "create-dispose-environment")) {
    return create_dispose_environment(vm, value);
  }
  return JNI_ERR;
}

// No Agent_OnLoad: TestAgentLibraryLoadErrors uses this library to test that diagnostic.
