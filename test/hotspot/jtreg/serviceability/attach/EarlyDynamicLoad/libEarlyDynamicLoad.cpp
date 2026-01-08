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

#include <jvmti.h>
#include <cstdio>
#include <cstring>

extern "C" {

static void JNICALL VMStartCallback(jvmtiEnv* jvmti, JNIEnv* env) {
  putchar('1');
  fflush(stdout);
  getchar();
}

JNIEXPORT int Agent_OnLoad(JavaVM* vm, char* options, void* reserved) {
  jvmtiEnv* jvmti;
  if (vm->GetEnv((void**) &jvmti, JVMTI_VERSION_1_0) != JVMTI_ERROR_NONE) {
    fprintf(stderr, "JVMTI error occurred during GetEnv\n");
    return JNI_ERR;
  }

  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.VMStart = VMStartCallback;

  if (jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks)) != JVMTI_ERROR_NONE) {
    fprintf(stderr, "JVMTI error occurred during SetEventCallbacks\n");
    return JNI_ERR;
  }
  if (jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_START, nullptr) != JVMTI_ERROR_NONE) {
    fprintf(stderr, "JVMTI error occurred during SetEventNotificationMode\n");
    return JNI_ERR;
  }

  return JNI_OK;
}

}
