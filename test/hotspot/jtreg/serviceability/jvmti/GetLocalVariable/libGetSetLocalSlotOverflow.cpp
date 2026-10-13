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
#include <limits.h>
#include "jvmti.h"
#include "jvmti_common.hpp"

#ifdef __cplusplus
extern "C" {
#endif

// The runner() frame at depth 1; INT_MAX makes (slot + extra_slot) overflow
// for the long/double accessors.
static const jint Depth = 1;
static const jint OverflowSlot = INT_MAX; // 0x7fffffff

static jvmtiEnv *jvmti = nullptr;

// Each access must come back as JVMTI_ERROR_INVALID_SLOT. On an unfixed VM the
// overflowing bounds check is bypassed and the subsequent locals->at(INT_MAX)
// access crashes the VM before we ever see a return code.
static bool expect_invalid_slot(const char* what, jvmtiError err) {
  if (err == JVMTI_ERROR_INVALID_SLOT) {
    LOG(" PASS: %s returned JVMTI_ERROR_INVALID_SLOT (%d) for slot=INT_MAX\n", what, err);
    return true;
  }
  LOG(" FAIL: %s returned %d for slot=INT_MAX, expected JVMTI_ERROR_INVALID_SLOT (%d)\n",
         what, err, JVMTI_ERROR_INVALID_SLOT);
  return false;
}

JNIEXPORT jboolean JNICALL
Java_GetSetLocalSlotOverflow_testOverflow(JNIEnv *env, jclass cls, jobject thread, jboolean isVirtual) {
  if (jvmti == nullptr) {
    LOG("JVMTI client was not properly loaded!\n");
    return JNI_FALSE;
  }

  jlong   lval = 0;
  jdouble dval = 0;

  // T_LONG / T_DOUBLE => extra_slot == 1 => INT_MAX + 1 overflows to INT_MIN.
  bool ok = true;
  ok &= expect_invalid_slot("GetLocalLong",   jvmti->GetLocalLong(thread, Depth, OverflowSlot, &lval));
  ok &= expect_invalid_slot("GetLocalDouble", jvmti->GetLocalDouble(thread, Depth, OverflowSlot, &dval));

  // JVMTI only supports SetLocal on the topmost frame of a virtual thread. The
  // runner() frame is not topmost, so on a virtual thread SetLocal is rejected
  // before the slot check runs and cannot exercise the overflow -- skip it there.
  if (!isVirtual) {
    ok &= expect_invalid_slot("SetLocalLong",   jvmti->SetLocalLong(thread, Depth, OverflowSlot, (jlong)0));
    ok &= expect_invalid_slot("SetLocalDouble", jvmti->SetLocalDouble(thread, Depth, OverflowSlot, (jdouble)0));
  }
  return ok ? JNI_TRUE : JNI_FALSE;
}

static jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jint res;
  jvmtiError err;
  static jvmtiCapabilities caps;

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_9);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }
  caps.can_access_local_variables = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("AddCapabilities: unexpected error: %d\n", err);
    return JNI_ERR;
  }
  err = jvmti->GetCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("GetCapabilities: unexpected error: %d\n", err);
    return JNI_ERR;
  }
  if (!caps.can_access_local_variables) {
    LOG("Warning: Access to local variables is not implemented\n");
    return JNI_ERR;
  }
  return JNI_OK;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL
Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

#ifdef __cplusplus
}
#endif
