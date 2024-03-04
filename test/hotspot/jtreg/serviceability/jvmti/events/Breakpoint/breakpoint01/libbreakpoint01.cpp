/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include <string.h>
#include "jni.h"
#include "jni_md.h"
#include "jvmti.h"

#include "jvmti_common.hpp"

extern "C" {

#define STATUS_FAILED 2
#define PASSED 0


#define METH_NUM 4
static const char *METHODS[][2] = {
    {"bpMethod", "()V"},
    {"bpMethod2", "()I"},
    {"bpMethodV", "()V"},
    {"bpMethod2V", "()I"}
};

static const jboolean METHODS_ATTRS[METH_NUM] = {JNI_FALSE, JNI_FALSE, JNI_TRUE, JNI_TRUE};


static const char *CLASS_SIG = "Lbreakpoint01;";

static const char *THREAD_NAME = "breakpoint01Thr";

static volatile int bpEvents[METH_NUM];
static volatile jint result = PASSED;
static jvmtiEnv *jvmti = nullptr;
static jvmtiEventCallbacks callbacks;

static volatile int callbacksEnabled = JNI_TRUE;
static jrawMonitorID agent_lock;

static void initCounters() {
  for (int i = 0; i < METH_NUM; i++) {
    bpEvents[i] = 0;
  }
}

static void setBP(jvmtiEnv *jvmti, JNIEnv *jni, jclass klass) {
  for (int i = 0; i < METH_NUM; i++) {
    jmethodID mid = jni->GetMethodID(klass, METHODS[i][0], METHODS[i][1]);
    if (mid == nullptr) {
      jni->FatalError("failed to get ID for the java method\n");
    }

    jvmtiError err = jvmti->SetBreakpoint(mid, 0);
    if (err != JVMTI_ERROR_NONE) {
      jni->FatalError("failed to set breakpoint\n");
    }
  }
}

/** callback functions **/
void JNICALL
ClassLoad(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread, jclass klass) {
  char *sig, *generic;
  jvmtiError err;

  RawMonitorLocker rml(jvmti, jni, agent_lock);
  if (callbacksEnabled) {
    // GetClassSignature may be called only during the start or the live phase
    err = jvmti->GetClassSignature(klass, &sig, &generic);
    if (err != JVMTI_ERROR_NONE) {
      jni->FatalError("failed to obtain a class signature\n");
    }

    if (sig != nullptr && (strcmp(sig, CLASS_SIG) == 0)) {
      LOG("ClassLoad event received for the class %s setting breakpoints ...\n", sig);
      setBP(jvmti, jni, klass);
    }
  }
}

void JNICALL
Breakpoint(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread, jmethodID method, jlocation location) {
  jclass klass;
  char *clsSig, *generic, *methNam, *methSig;
  jvmtiThreadInfo thr_info;
  int checkStatus = PASSED;

  LOG(">>>> Breakpoint event received\n");

  /* checking thread info */
  jvmtiError err = jvmti->GetThreadInfo(thread, &thr_info);
  if (err != JVMTI_ERROR_NONE) {
    result = STATUS_FAILED;
    LOG("TEST FAILED: unable to get thread info during Breakpoint callback\n\n");
    return;
  }

  const char* thr_name = thr_info.name == nullptr ? "null" : thr_info.name;
  const char* thr_virtual_tag = jni->IsVirtualThread(thread) == JNI_TRUE ? "virtual" : "platform";
  const char* thr_daemon_tag = thr_info.is_daemon == JNI_TRUE ? "deamon" : "user";
  if (thr_info.name == nullptr || strcmp(thr_info.name, THREAD_NAME) != 0) {
    result = checkStatus = STATUS_FAILED;
    LOG("TEST FAILED: Breakpoint event with unexpected thread info:\n");
    LOG("\tname: \"%s\"\ttype: %s %s thread\n\n", thr_name, thr_virtual_tag, thr_daemon_tag);
  } else {
    LOG("CHECK PASSED: thread name: \"%s\"\ttype: %s %s thread\n", thr_info.name, thr_virtual_tag, thr_daemon_tag);
  }

  /* checking location */
  if (location != 0) {
    result = checkStatus = STATUS_FAILED;
    LOG("TEST FAILED: Breakpoint event with unexpected location %ld:\n\n", (long) location);
  } else {
    LOG("CHECK PASSED: location: %ld as expected\n", (long) location);
  }

  /* checking method info */
  err = jvmti->GetMethodDeclaringClass(method, &klass);
  if (err != JVMTI_ERROR_NONE) {
    result = checkStatus = STATUS_FAILED;
    LOG("TEST FAILED: unable to get method declaring class during Breakpoint callback\n\n");
    return;
  }
  err = jvmti->GetClassSignature(klass, &clsSig, &generic);
  if (err != JVMTI_ERROR_NONE) {
    result = checkStatus = STATUS_FAILED;
    LOG("TEST FAILED: unable to obtain a class signature during Breakpoint callback\n\n");
    return;
  }
  if (clsSig == nullptr || strcmp(clsSig, CLASS_SIG) != 0) {
    result = checkStatus = STATUS_FAILED;
    LOG("TEST FAILED: Breakpoint event with unexpected class signature: %s\n\n", (clsSig == nullptr) ? "null" : clsSig);
  } else {
    LOG("CHECK PASSED: class signature: \"%s\"\n", clsSig);
  }

  err = jvmti->GetMethodName(method, &methNam, &methSig, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    result = checkStatus = STATUS_FAILED;
    LOG("TEST FAILED: unable to get method name during Breakpoint callback\n\n");
    return;
  }

  for (int i = 0; i < METH_NUM; i++) {
    if (strcmp(methNam, METHODS[i][0]) == 0 &&
        strcmp(methSig, METHODS[i][1]) == 0) {
      LOG("CHECK PASSED: method name: \"%s\"\tsignature: \"%s\" %d\n", methNam, methSig, i);
      jboolean isVirtual = jni->IsVirtualThread(thread);
      if (isVirtual != METHODS_ATTRS[i]) {
        LOG("TEST FAILED: IsVirtualThread check failed with unexpected result %d  when expected is %d\n",
            isVirtual, METHODS_ATTRS[i]);
        result = checkStatus = STATUS_FAILED;
      }
      if (checkStatus == PASSED) {
        bpEvents[i]++;
      }
      break;
    }
  }
  err = jvmti->Deallocate((unsigned char *) methNam);
  if (err != JVMTI_ERROR_NONE) {
    result = STATUS_FAILED;
    LOG("TEST FAILED: unable to deallocate memory pointed to method name\n\n");
  }

  err = jvmti->Deallocate((unsigned char *) methSig);
  if (err != JVMTI_ERROR_NONE) {
    result = STATUS_FAILED;
    LOG("TEST FAILED: unable to deallocate memory pointed to method signature\n\n");
  }

  LOG("<<<<\n\n");
}

void JNICALL
VMStart(jvmtiEnv *jvmti, JNIEnv *jni) {
  RawMonitorLocker rml(jvmti, jni, agent_lock);
  callbacksEnabled = JNI_TRUE;
}

void JNICALL
VMDeath(jvmtiEnv *jvmti, JNIEnv *jni) {
  RawMonitorLocker rml(jvmti, jni, agent_lock);
  callbacksEnabled = JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_breakpoint01_check(JNIEnv *jni, jobject obj) {
  for (int i = 0; i < METH_NUM; i++) {
    if (bpEvents[i] != 1) {
      result = STATUS_FAILED;
      LOG("TEST FAILED: wrong number of Breakpoint events\n"
          "\tfor the method \"%s %s\":\n"
          "\t\tgot: %d\texpected: 1\n",
          METHODS[i][0], METHODS[i][1], bpEvents[i]);
    } else {
      LOG("CHECK PASSED: %d Breakpoint event(s) for the method \"%s %s\" as expected\n",
          bpEvents[i], METHODS[i][0], METHODS[i][1]);
    }
  }

  return result;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiError err;
  jint res;

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_9);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  initCounters();

  memset(&caps, 0, sizeof(jvmtiCapabilities));
  caps.can_generate_breakpoint_events = 1;
  caps.can_support_virtual_threads = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  err = jvmti->GetCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  if (!caps.can_generate_single_step_events)
    LOG("Warning: generation of single step events is not implemented\n");

  /* set event callback */
  LOG("setting event callbacks ...\n");
  (void) memset(&callbacks, 0, sizeof(callbacks));
  callbacks.ClassLoad = &ClassLoad;
  callbacks.Breakpoint = &Breakpoint;
  callbacks.VMStart = &VMStart;
  callbacks.VMDeath = &VMDeath;

  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE)
    return JNI_ERR;

  LOG("setting event callbacks done\nenabling JVMTI events ...\n");

  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_START, nullptr);
  if (err != JVMTI_ERROR_NONE)
    return JNI_ERR;
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, nullptr);
  if (err != JVMTI_ERROR_NONE)
    return JNI_ERR;
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_LOAD, nullptr);
  if (err != JVMTI_ERROR_NONE)
    return JNI_ERR;
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, nullptr);
  if (err != JVMTI_ERROR_NONE)
    return JNI_ERR;
  LOG("enabling the events done\n\n");

  agent_lock = create_raw_monitor(jvmti, "agent_lock");
  if (agent_lock == nullptr)
    return JNI_ERR;

  return JNI_OK;
}

}
