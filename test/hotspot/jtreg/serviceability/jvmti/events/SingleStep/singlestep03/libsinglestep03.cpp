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
#include <jvmti.h>
#include "jvmti_common.h"

extern "C" {

#define STATUS_FAILED 2
#define PASSED 0

/* tested methods */
#define METH_NUM 4
static const char *METHODS[][2] = {
    {"bpMethod", "()V"},
    {"nativeMethod", "()V"},
    {"anotherNativeMethod", "(I)V"},
    {"runThis", "()I"}
};

/* event counters for the tested methods and expected numbers
 of the events */
static volatile long stepEv[][2] = {
    {0, 1},
    {0, 0},
    {0, 0},
    {0, 1}
};

static const char *CLASS_SIG =
    "Lsinglestep03;";

static volatile jint result = PASSED;
static jvmtiEnv *jvmti = nullptr;
static jvmtiEventCallbacks callbacks;

static volatile int callbacksEnabled = JNI_FALSE;
static jrawMonitorID agent_lock;

static void setBP(jvmtiEnv *jvmti, JNIEnv *jni, jclass klass) {
  jmethodID mid;
  jvmtiError err;

  mid = jni->GetMethodID(klass, METHODS[0][0], METHODS[0][1]);
  if (mid == nullptr) {
    jni->FatalError("failed to get ID for the java method\n");
  }

  err = jvmti->SetBreakpoint(mid, 0);
  if (err != JVMTI_ERROR_NONE) {
    jni->FatalError("failed to set breakpoint\n");
  }
}

/** callback functions **/
void JNICALL
ClassLoad(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread, jclass klass) {
  char *sig, *generic;
  jvmtiError err;

  RawMonitorLocker rml(jvmti, jni, agent_lock);

  if (!callbacksEnabled) {
    return;
  }

  err = jvmti->GetClassSignature(klass, &sig, &generic);
  if (err != JVMTI_ERROR_NONE) {
    jni->FatalError("failed to obtain a class signature\n");
  }

  if (sig != nullptr && (strcmp(sig, CLASS_SIG) == 0)) {
    LOG("ClassLoad event received for the class \"%s\"\n"
        "\tsetting breakpoint ...\n", sig);
    setBP(jvmti, jni, klass);
  }
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

void JNICALL
Breakpoint(jvmtiEnv *jvmti, JNIEnv *jni, jthread thr, jmethodID method, jlocation loc) {
  jclass klass;
  char *sig, *generic;
  jvmtiError err;

  RawMonitorLocker rml(jvmti, jni, agent_lock);

  if (!callbacksEnabled) {
    return;
  }

  LOG("Breakpoint event received\n");
  err = jvmti->GetMethodDeclaringClass(method, &klass);
  if (err != JVMTI_ERROR_NONE) {
    COMPLAIN("TEST FAILURE: unable to get method declaring class\n\n");
  }

  err = jvmti->GetClassSignature(klass, &sig, &generic);
  if (err != JVMTI_ERROR_NONE) {
    jni->FatalError("Breakpoint: failed to obtain a class signature\n");
  }

  if (sig != nullptr && (strcmp(sig, CLASS_SIG) == 0)) {
    LOG("method declaring class \"%s\"\n\tenabling SingleStep events ...\n", sig);
    err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_SINGLE_STEP, thr);
    if (err != JVMTI_ERROR_NONE) {
      result = STATUS_FAILED;
      COMPLAIN("TEST FAILURE: cannot enable SingleStep events\n\n");
    }
  } else {
    result = STATUS_FAILED;
    COMPLAIN("TEST FAILURE: unexpected breakpoint event in method of class \"%s\"\n\n", sig);
  }

}

void JNICALL
SingleStep(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread, jmethodID method, jlocation location) {
  jvmtiError err;
  jclass klass;
  char *sig, *generic, *methNam, *methSig;

  if (result == STATUS_FAILED) {
    return;
  }

  LOG(">>>> SingleStep event received\n");

  err = jvmti->GetMethodName(method, &methNam, &methSig, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    result = STATUS_FAILED;
    COMPLAIN("TEST FAILED: unable to get method name during SingleStep callback\n\n");
    return;
  }

  err = jvmti->GetMethodDeclaringClass(method, &klass);
  if (err != JVMTI_ERROR_NONE) {
    result = STATUS_FAILED;
    COMPLAIN("TEST FAILED: unable to get method declaring class during SingleStep callback\n\n");
    return;
  }

  err = jvmti->GetClassSignature(klass, &sig, &generic);
  if (err != JVMTI_ERROR_NONE) {
    result = STATUS_FAILED;
    COMPLAIN("TEST FAILED: unable to obtain a class signature during SingleStep callback\n\n");
    return;
  }

  if (sig != nullptr) {
    if (stepEv[METH_NUM - 1][0] == 1) {
      result = STATUS_FAILED;
      COMPLAIN("TEST FAILED: SingleStep event received after disabling the event generation\n\n");
      return;
    }

    for (int i = 0; i < METH_NUM; i++) {
      if ((strcmp(methNam, METHODS[i][0]) == 0) &&
          (strcmp(methSig, METHODS[i][1]) == 0) &&
          (strcmp(sig, CLASS_SIG) == 0)) {
        stepEv[i][0]++;

        if (stepEv[i][1] == 1) {
          LOG("CHECK PASSED: SingleStep event received for the method:\n"
              "\t \"%s %s\" of class \"%s\"\n"
              "\tas expected\n",
              methNam, methSig, sig);
        } else {
          result = STATUS_FAILED;
          LOG("TEST FAILED: SingleStep event received for the method:\n"
              "\t \"%s %s\" of class \"%s\"\n",
              methNam, methSig, sig);
        }

        if (i == (METH_NUM - 1)) {
          LOG("Disabling the single step event generation\n");
          err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_SINGLE_STEP, thread);
          if (err != JVMTI_ERROR_NONE) {
            result = STATUS_FAILED;
            COMPLAIN("TEST FAILED: cannot disable SingleStep events\n\n");
          }
        }
      }
    }
  }

  err = jvmti->Deallocate((unsigned char *) methNam);
  if (err != JVMTI_ERROR_NONE) {
    result = STATUS_FAILED;
    COMPLAIN("TEST FAILED: unable to deallocate memory pointed to method name\n\n");
  }
  err = jvmti->Deallocate((unsigned char *) methSig);
  if (err != JVMTI_ERROR_NONE) {
    result = STATUS_FAILED;
    COMPLAIN("TEST FAILED: unable to deallocate memory pointed to method signature\n\n");
  }

  LOG("<<<<\n\n");
}

/* dummy method used only to provoke SingleStep events */
JNIEXPORT void JNICALL
Java_singlestep03_anotherNativeMethod(JNIEnv *jni, jobject obj, jint i) {
  LOG("inside the anotherNativeMethod()\n\n");
}

/* dummy method used only to provoke SingleStep events */
JNIEXPORT void JNICALL
Java_singlestep03_nativeMethod(
    JNIEnv *jni, jobject obj) {
  jint i = 0;

  LOG("inside the nativeMethod()\n\n");
  i++;

  Java_singlestep03_anotherNativeMethod(jni, obj, i);
}

JNIEXPORT jint JNICALL Java_singlestep03_check(JNIEnv *jni, jobject obj) {

  for (int i = 0; i < METH_NUM; i++)
    if (stepEv[i][0] == 0) {
      if (stepEv[i][1] == 0) {
        LOG("CHECK PASSED: no SingleStep events for the method \"%s\" as expected\n\n",
                     METHODS[i][0]);
      } else {
        result = STATUS_FAILED;
        COMPLAIN("TEST FAILED: no SingleStep events for the method \"%s\"\n\n",
                      METHODS[i][0]);
      }
    }

  return result;
}

jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiError err;
  jint res;

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  /* add capability to generate compiled method events */
  memset(&caps, 0, sizeof(jvmtiCapabilities));
  caps.can_generate_breakpoint_events = 1;
  caps.can_generate_single_step_events = 1;
  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(AddCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  err = jvmti->GetCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }
  if (!caps.can_generate_single_step_events)
    LOG("Warning: generation of single step events is not implemented\n");

  /* set event callback */
  LOG("setting event callbacks ...\n");
  (void) memset(&callbacks, 0, sizeof(callbacks));
  callbacks.ClassLoad = &ClassLoad;
  callbacks.Breakpoint = &Breakpoint;
  callbacks.SingleStep = &SingleStep;
  callbacks.VMStart = &VMStart;
  callbacks.VMDeath = &VMDeath;

  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  LOG("setting event callbacks done\nenabling JVMTI events ...\n");
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_START, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_LOAD, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  LOG("enabling the events done\n\n");

  agent_lock = create_raw_monitor(jvmti, "agent lock");

  return JNI_OK;
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

}
