/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

extern "C" {

static jvmtiEnv* jvmti = nullptr;
static jrawMonitorID monitor;

JNIEXPORT jint JNICALL
Java_AsyncExceptionOnMonitorEnter_createRawMonitor(JNIEnv *jni, jclass cls) {
  jvmtiError err;
  char name[32];

  snprintf(name, sizeof(name), "MyRawMonitor");
  err = jvmti->CreateRawMonitor(name, &monitor);
  if (err != JVMTI_ERROR_NONE) {
    printf("CreateRawMonitor unexpected error: (%d)\n", err);
  }
  return err;
}

JNIEXPORT jint JNICALL
Java_AsyncExceptionOnMonitorEnter_enterRawMonitor(JNIEnv *jni, jclass cls) {
  jvmtiError err;
  err = jvmti->RawMonitorEnter(monitor);
  if (err != JVMTI_ERROR_NONE) {
      printf("RawMonitorEnter unexpected error: (%d)\n", err);
  }
  return err;
}

JNIEXPORT jint JNICALL
Java_AsyncExceptionOnMonitorEnter_exitRawMonitor(JNIEnv *jni, jclass cls) {
  jvmtiError err;
  err = jvmti->RawMonitorExit(monitor);
  if (err != JVMTI_ERROR_NONE) {
      printf("RawMonitorExit unexpected error: (%d)\n", err);
  }
  return err;
}

JNIEXPORT void JNICALL
Java_AsyncExceptionOnMonitorEnter_destroyRawMonitor(JNIEnv *jni, jclass cls) {
  jvmtiError err;
  err = jvmti->DestroyRawMonitor(monitor);
  // Almost always worker2 will be stopped before being able to release the
  // JVMTI monitor so just ignore those errors.
  if (err != JVMTI_ERROR_NONE && err != JVMTI_ERROR_NOT_MONITOR_OWNER) {
    printf("DestroyRawMonitor unexpected error: (%d)\n", err);
  }
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  // create JVMTI environment
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }
  return JNI_OK;
}

}
