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

#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

static jvmtiEnv* jvmti = nullptr;
static jrawMonitorID threadLock1 = nullptr;
static char threadLockName1[] = "threadLock1";

static jrawMonitorID threadLock2 = nullptr;
static char threadLockName2[] = "threadLock2";

JNIEXPORT jint JNICALL
Java_PrintRawMonitorLockTest_createRawMonitors(JNIEnv *jni, jclass cls) {
  if (jvmti->CreateRawMonitor(threadLockName1, &threadLock1) != JNI_OK) {
    return JNI_ERR;
  }
  return jvmti->CreateRawMonitor(threadLockName2, &threadLock2);
}

JNIEXPORT jint JNICALL
Java_PrintRawMonitorLockTest_rawMonitorEnter(JNIEnv *jni, jclass cls, int id) {
  if (id == 1) {
    return jvmti->RawMonitorEnter(threadLock1);
  } else if (id == 2) {
    return jvmti->RawMonitorEnter(threadLock2);
  } else {
    return JNI_ERR;
  }
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  LOG("\nAgent_OnLoad started");
  // create JVMTI environment
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  LOG("Agent_OnLoad finished\n");
  return JNI_OK;
}

}
