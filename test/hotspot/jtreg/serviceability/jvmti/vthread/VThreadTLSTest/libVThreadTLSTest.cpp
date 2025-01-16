/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <cstdlib>
#include <cstring>
#include <jvmti.h>
#include "jvmti_common.hpp"

extern "C" {

static jvmtiEnv *jvmti;

JNIEXPORT jlong JNICALL
Java_VThreadTLSTest_getTLS(JNIEnv* jni, jclass clazz) {
  void* data;
  jvmtiError err = jvmti->GetThreadLocalStorage(nullptr, &data);
  check_jvmti_status(jni, err, "getTLS: Failed in JVMTI GetThreadLocalStorage");
  return (jlong)data;
}

JNIEXPORT void JNICALL
Java_VThreadTLSTest_setTLS(JNIEnv* jni, jclass clazz, jlong value) {
  jvmtiError err = jvmti->SetThreadLocalStorage(nullptr, (void*)value);
  check_jvmti_status(jni, err, "setTLS: Failed in JVMTI SetThreadLocalStorage");
}

jint agent_init(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiError err;

  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    LOG("agent_init: could not initialize JVMTI\n");
    return JNI_ERR;
  }
  memset(&caps, 0, sizeof(caps));
  caps.can_support_virtual_threads = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("agent_init: error in JVMTI AddCapabilities: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }
  return JNI_OK;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  LOG("Agent_OnLoad\n");
  return agent_init(jvm, options, reserved);
}

JNIEXPORT jint JNICALL
Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  LOG("Agent_OnAttach\n");
  return agent_init(jvm, options, reserved);
}

} // extern "C"

