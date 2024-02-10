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

#include <stdlib.h>
#include <string.h>
#include <jvmti.h>
#include "jvmti_common.h"

extern "C" {

#define STATUS_FAILED 2
#define PASSED 0

/* counter for the wrong NativeMethodBind events
   received during non-start or non-live phase */
static volatile int wrongBindEv = 0;

static volatile jint result = PASSED;
static jvmtiEnv *jvmti = nullptr;
static jvmtiEventCallbacks callbacks;
static jrawMonitorID counter_lock;

/** callback functions **/
void JNICALL
NativeMethodBind(jvmtiEnv *jvmti, JNIEnv *jni, jthread thread,
                 jmethodID method, void *addr, void **new_addr) {
  jvmtiPhase phase;
  jvmtiError err;
  char *methNam = nullptr, *methSig = nullptr;

  RawMonitorLocker rml(jvmti, jni, counter_lock);
  err = jvmti->GetPhase(&phase);
  if (err != JVMTI_ERROR_NONE) {
    LOG(">>>> Error getting phase\n");
    result = STATUS_FAILED;
    return;
  }

  if (phase != JVMTI_PHASE_LIVE && phase != JVMTI_PHASE_START) {
    return;
  }

  err = jvmti->GetMethodName(method, &methNam, &methSig, nullptr);

  if (err != JVMTI_ERROR_NONE) {
    result = STATUS_FAILED;
    LOG("TEST FAILED: unable to get method name during NativeMethodBind callback\n\n");
    return;
  } else {
    LOG("NativeMethodBind received for \"%s %s\"\n", methNam, methSig);
  }

  if (methNam != nullptr) {
    err = jvmti->Deallocate((unsigned char *) methNam);
    if (err != JVMTI_ERROR_NONE) {
      result = STATUS_FAILED;
      LOG("TEST FAILED: unable to deallocate memory pointed to method name\n\n");
    }
  }

  if (methSig != nullptr) {
    err = jvmti->Deallocate((unsigned char *) methSig);
    if (err != JVMTI_ERROR_NONE) {
      result = STATUS_FAILED;
      LOG("TEST FAILED: unable to deallocate memory pointed to method signature\n\n");
    }
  }

}

void JNICALL
VMDeath(jvmtiEnv *jvmti, JNIEnv *jni) {
  LOG("VMDeath event received\n");

  if (wrongBindEv != 0) {
    COMPLAIN("TEST FAILED: there are %d NativeMethodBind events\n"
             "sent during non-start or non-live phase of the VM execution\n", wrongBindEv);
  }

  if (result == STATUS_FAILED) {
    jni->FatalError("Result is failed.");
  }
}
/************************/

/* dummy method used only to provoke NativeMethodBind events */
JNIEXPORT jint JNICALL
Java_nativemethbind02_nativeMethod(JNIEnv *jni, jobject obj) {
  LOG("inside the nativeMethod()\n\n");
  return PASSED;
}

jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jvmtiCapabilities caps;
  jvmtiError err;
  jint res;

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_9);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  /* create a raw monitor */
  counter_lock = create_raw_monitor(jvmti, "_counter_lock");

  /* add capability to generate compiled method events */
  memset(&caps, 0, sizeof(jvmtiCapabilities));
  caps.can_generate_native_method_bind_events = 1;

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }

  err = jvmti->GetCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  if (!caps.can_generate_native_method_bind_events)
    LOG("Warning: generation of native method bind events is not implemented\n");

  /* set event callback */
  LOG("setting event callbacks ...\n");
  (void) memset(&callbacks, 0, sizeof(callbacks));
  callbacks.NativeMethodBind = &NativeMethodBind;
  callbacks.VMDeath = &VMDeath;
  err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE)
    return JNI_ERR;

  LOG("setting event callbacks done\nenabling JVMTI events ...\n");
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_NATIVE_METHOD_BIND, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    return JNI_ERR;
  }
  LOG("enabling the events done\n\n");

  return JNI_OK;
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

}
