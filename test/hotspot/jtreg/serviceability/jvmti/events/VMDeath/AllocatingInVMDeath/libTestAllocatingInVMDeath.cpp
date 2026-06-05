/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "jni.h"
#include "jvmti_common.hpp"

static void JNICALL
cbVMDeath(jvmtiEnv* jvmti, JNIEnv* jni) {
  jclass clz = jni->FindClass("DoWork");
  if (clz == nullptr) {
    fatal(jni, "Can't find DoWork class.");
    return;
  }
  jmethodID mid = jni->GetStaticMethodID(clz, "upCall", "()V");
  if (mid == nullptr) {
    fatal(jni, "Can't find upCall method.");
  }
  jni->CallStaticObjectMethod(clz, mid);
  if (jni->ExceptionOccurred()) {
    jni->ExceptionDescribe();
    fatal(jni, "cbVMDeath: unexpected exception occurred in Java upcall method.");
  }
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
  jvmtiEnv *jvmti = nullptr;
  jint res = vm->GetEnv((void **) &jvmti, JVMTI_VERSION_21);
  if (res != JNI_OK) {
    return JNI_ERR;
  }
  jvmtiError err = JVMTI_ERROR_NONE;

  jvmtiEventCallbacks callbacks;
  (void) memset(&callbacks, 0, sizeof (callbacks));
  callbacks.VMDeath = &cbVMDeath;
  err = jvmti->SetEventCallbacks(&callbacks, (int) sizeof (jvmtiEventCallbacks));
  check_jvmti_error(err, "SetEventCallbacks");
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, nullptr);
  check_jvmti_error(err, "SetEventNotificationMode");
  return JNI_OK;
}
