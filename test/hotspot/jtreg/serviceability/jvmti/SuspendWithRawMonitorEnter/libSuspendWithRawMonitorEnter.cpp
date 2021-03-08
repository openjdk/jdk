/*
 * Copyright (c) 2001, 2021, Oracle and/or its affiliates. All rights reserved.
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

static jint iGlobalStatus = 0;
static jvmtiEnv* jvmti = NULL;
static jint printdebug = 0;
static jrawMonitorID threadLock = NULL;
static char threadLockName[] = "threadLock";

#define LOG(...) \
  do { \
    printf(__VA_ARGS__); \
    printf("\n"); \
    fflush(stdout); \
  } while (0)

static void
check_jvmti_status(JNIEnv* jni, jvmtiError err, const char* msg) {
  if (err != JVMTI_ERROR_NONE) {
    LOG("check_jvmti_status: JVMTI function returned error: %d", err);
    iGlobalStatus = 2;
    jni->FatalError(msg);
  }
}

static void print_debug(jint id, const char* mesg) {
  const char *thr;

  switch (id) {
  // These id values need to match SuspendWithRawMonitorEnter.java:
  case 0:  thr = "main";      break;
  case 1:  thr = "blocker";   break;
  case 2:  thr = "contender"; break;
  case 3:  thr = "resumer";   break;
  default: thr = "unknown";   break;
  }

  (void)fprintf(stderr, "%s: %s", thr, mesg);
}
#define DEBUG_MESG(id, m) { if (printdebug) print_debug(id, m); }

JNIEXPORT void JNICALL
Java_SuspendWithRawMonitorEnter_CreateRawMonitor(JNIEnv *jni, jclass cls, jint id) {
  jvmtiError err = jvmti->CreateRawMonitor(threadLockName, &threadLock);
  check_jvmti_status(jni, err, "Java_SuspendWithRawMonitorEnter_CreateRawMonitor: error in JVMTI CreateRawMonitor");
  DEBUG_MESG(id, "created threadLock\n");
}

JNIEXPORT void JNICALL
Java_SuspendWithRawMonitorEnter_DestroyRawMonitor(JNIEnv *jni, jclass cls, jint id) {
  jvmtiError err = jvmti->DestroyRawMonitor(threadLock);
  check_jvmti_status(jni, err, "Java_SuspendWithRawMonitorEnter_DestroyRawMonitor: error in JVMTI DestroyRawMonitor");
  DEBUG_MESG(id, "destroyed threadLock\n");
}

JNIEXPORT jint JNICALL
Java_SuspendWithRawMonitorEnter_GetResult(JNIEnv *env, jclass cls) {
    return iGlobalStatus;
}

JNIEXPORT void JNICALL
Java_SuspendWithRawMonitorEnter_SetPrintDebug(JNIEnv *env, jclass cls) {
    printdebug = 1;
}

JNIEXPORT void JNICALL
Java_SuspendWithRawMonitorEnter_SuspendThread(JNIEnv *jni, jclass cls, jint id, jthread thr) {
  DEBUG_MESG(id, "before suspend thread\n");
  jvmtiError err = jvmti->SuspendThread(thr);
  check_jvmti_status(jni, err, "Java_SuspendWithRawMonitorEnter_SuspendThread: error in JVMTI SuspendThread");
  DEBUG_MESG(id, "suspended thread\n");
}

JNIEXPORT jint JNICALL
Java_SuspendWithRawMonitorEnterWorker_GetPrintDebug(JNIEnv *env, jclass cls) {
    return printdebug;
}

JNIEXPORT void JNICALL
Java_SuspendWithRawMonitorEnterWorker_RawMonitorEnter(JNIEnv *jni, jclass cls, jint id) {
  DEBUG_MESG(id, "before enter threadLock\n");
  jvmtiError err = jvmti->RawMonitorEnter(threadLock);
  check_jvmti_status(jni, err, "Java_SuspendWithRawMonitorEnterWorker_RawMonitorEnter: error in JVMTI RawMonitorEnter");
  DEBUG_MESG(id, "enter threadLock\n");
}

JNIEXPORT void JNICALL
Java_SuspendWithRawMonitorEnterWorker_RawMonitorExit(JNIEnv *jni, jclass cls, jint id) {
  DEBUG_MESG(id, "before exit threadLock\n");
  jvmtiError err = jvmti->RawMonitorExit(threadLock);
  check_jvmti_status(jni, err, "Java_SuspendWithRawMonitorEnterWorker_RawMonitorExit: error in JVMTI RawMonitorExit");
  DEBUG_MESG(id, "exit threadLock\n");
}

JNIEXPORT void JNICALL
Java_SuspendWithRawMonitorEnterWorker_ResumeThread(JNIEnv *jni, jclass cls, jint id, jthread thr) {
  DEBUG_MESG(id, "before resume thread\n");
  jvmtiError err = jvmti->ResumeThread(thr);
  check_jvmti_status(jni, err, "Java_SuspendWithRawMonitorEnterWorker_ResumeThread: error in JVMTI ResumeThread");
  DEBUG_MESG(id, "resumed thread\n");
}


/** Agent library initialization. */

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  LOG("\nAgent_OnLoad started");

  // create JVMTI environment
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION) != JNI_OK) {
    return JNI_ERR;
  }

  // add specific capabilities for suspending thread
  jvmtiCapabilities suspendCaps;
  memset(&suspendCaps, 0, sizeof(suspendCaps));
  suspendCaps.can_suspend = 1;

  jvmtiError err = jvmti->AddCapabilities(&suspendCaps);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  LOG("Agent_OnLoad finished\n");
  return JNI_OK;
}

}
