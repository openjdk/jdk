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

#include <jni.h>
#include <jvmti.h>
#include <stdio.h>
#include <string.h>
#include "jvmti_common.h"

static jvmtiEnv* jvmti = nullptr;
static const jint MAX_FRAME_COUNT = 32;

extern "C" {

JNIEXPORT jint JNICALL
Java_ThreadListStackTracesTest_getStateSingle(JNIEnv* jni, jclass clazz, jthread vthread) {
  jvmtiStackInfo* info = nullptr;

  jvmtiError err = jvmti->GetThreadListStackTraces(1, &vthread, MAX_FRAME_COUNT, &info);
  check_jvmti_status(jni, err, "getStateSingle: error in JVMTI GetThreadListStackTraces");

  return info[0].state;
}

JNIEXPORT jint JNICALL
Java_ThreadListStackTracesTest_getStateMultiple(JNIEnv* jni, jclass clazz, jthread vhread, jthread other) {
  jthread threads[2] = { vhread, other };
  jvmtiStackInfo* info = nullptr;

  jvmtiError err = jvmti->GetThreadListStackTraces(2, threads, MAX_FRAME_COUNT, &info);
  check_jvmti_status(jni, err, "getStateMultiple: error in JVMTI GetThreadListStackTraces");

  return info[0].state;
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    LOG("Agent_OnLoad: error in GetEnv");
    return JNI_ERR;
  }
  return 0;
}

} // extern "C"
