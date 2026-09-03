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

#include <string.h>
#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

static const char* const NATIVE_METHOD_PREFIX = "wrapped_";

// Implements PrefixedNativeStackWalk.wrapped_go(): the "wrapped_" prefix is
// stripped when the VM resolves wrapped_go(), so the entry point it looks for
// is the one of the wrapper method, go().
JNIEXPORT jboolean JNICALL
Java_PrefixedNativeStackWalk_go(JNIEnv* jni, jclass cls) {
  // RegisterNatives on a class of a named module loaded by the boot loader makes
  // the VM determine the calling class at depth 1. That walk steps over this
  // prefixed native frame and its wrapper, which is the code under test. Passing
  // no methods keeps the call itself a no-op: nothing is (re-)bound.
  jclass boot_cls = jni->FindClass("java/lang/String");
  if (boot_cls == nullptr) {
    LOG("FindClass(java/lang/String) failed\n");
    return JNI_FALSE;
  }
  // No methods to register: the walk happens before the (empty) method list is
  // looked at, so the list itself is never read.
  jint res = jni->RegisterNatives(boot_cls, nullptr, 0);
  if (res != 0) {
    LOG("RegisterNatives returned %d, expected 0\n", (int)res);
    return JNI_FALSE;
  }
  LOG("Stack walk over the prefixed native method completed\n");
  return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  jvmtiEnv* jvmti = nullptr;
  if (jvm->GetEnv((void**)&jvmti, JVMTI_VERSION) != JNI_OK || jvmti == nullptr) {
    LOG("Could not initialize JVMTI env\n");
    return JNI_ERR;
  }

  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_set_native_method_prefix = 1;

  jvmtiError err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("AddCapabilities failed with %d\n", err);
    return JNI_ERR;
  }

  err = jvmti->SetNativeMethodPrefix(NATIVE_METHOD_PREFIX);
  if (err != JVMTI_ERROR_NONE) {
    LOG("SetNativeMethodPrefix(\"%s\") failed with %d\n", NATIVE_METHOD_PREFIX, err);
    return JNI_ERR;
  }
  return JNI_OK;
}

} // extern "C"
