/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "jvmti.h"
#include "jvmti_common.h"

extern "C" {

#define PASSED 0
#define STATUS_FAILED 2

static jvmtiEnv *jvmti = nullptr;
static jvmtiCapabilities caps;
static jint result = PASSED;
static int check_idx = 0;

jint  Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
  jint res;
  jvmtiError err;

  res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv !\n");
    return JNI_ERR;
  }

  err = jvmti->GetPotentialCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetPotentialCapabilities) unexpected error: %s (%d)\n",
        TranslateError(err), err);
    return JNI_ERR;
  }

  err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(AddCapabilities) unexpected error: %s (%d)\n",
        TranslateError(err), err);
    return JNI_ERR;
  }

  err = jvmti->GetCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetCapabilities) unexpected error: %s (%d)\n",
        TranslateError(err), err);
    return JNI_ERR;
  }

  if (!caps.can_get_monitor_info) {
    LOG("Warning: GetObjectMonitorUsage is not implemented\n");
  }

  return JNI_OK;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

JNIEXPORT jint JNICALL
Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
  return Agent_Initialize(jvm, options, reserved);
}

static void dealloc(JNIEnv *jni, char* mem) {
  jvmtiError err = jvmti->Deallocate((unsigned char*)mem);
  check_jvmti_status(jni, err, "error in JVMTI Deallocate");
}

static void
print_monitor_info(JNIEnv *jni, jvmtiMonitorUsage &inf) {
  jvmtiError err;
  jvmtiThreadInfo tinf;

  LOG(">>> [%d]\n", check_idx);
  if (inf.owner == nullptr) {
    LOG(">>>          owner:               none (0x0)\n", check_idx);
  } else {
    err = jvmti->GetThreadInfo(inf.owner, &tinf);
    check_jvmti_status(jni, err, "error in JVMTI GetThreadInfo");
    LOG(">>>          owner:               %s (0x%p)\n",
        tinf.name, inf.owner);
    dealloc(jni, tinf.name);
  }
  LOG(">>>          entry_count:         %d\n", inf.entry_count);
  LOG(">>>          waiter_count:        %d\n", inf.waiter_count);
  LOG(">>>          notify_waiter_count: %d\n", inf.notify_waiter_count);

  if (inf.waiter_count > 0) {
    LOG(">>>  waiters:\n");
    for (int j = 0; j < inf.waiter_count; j++) {
      err = jvmti->GetThreadInfo(inf.waiters[j], &tinf);
      check_jvmti_status(jni, err, "error in JVMTI GetThreadInfo");
      LOG(">>>                %2d: %s (0x%p)\n",
          j, tinf.name, inf.waiters[j]);
      dealloc(jni, tinf.name);
    }
  }
  if (inf.notify_waiter_count > 0) {
    LOG(">>>  notify_waiters:\n");
    for (int j = 0; j < inf.notify_waiter_count; j++) {
      err = jvmti->GetThreadInfo(inf.notify_waiters[j], &tinf);
      check_jvmti_status(jni, err, "error in JVMTI GetThreadInfo");
      LOG(">>>                %2d: %s (0x%p)\n",
          j, tinf.name, inf.notify_waiters[j]);
      dealloc(jni, tinf.name);
    }
  }
}

JNIEXPORT void JNICALL
Java_ObjectMonitorUsage_check(JNIEnv *jni, jclass cls, jobject obj, jthread owner,
        jint entryCount, jint waiterCount, jint notifyWaiterCount) {
  jvmtiError err;
  jvmtiMonitorUsage inf;

  check_idx++;

  err = jvmti->GetObjectMonitorUsage(obj, &inf);
  check_jvmti_status(jni, err, "error in JVMTI GetObjectMonitorUsage");

  print_monitor_info(jni, inf);

  if (!jni->IsSameObject(owner, inf.owner)) {
    LOG("(%d) unexpected owner: 0x%p\n", check_idx, inf.owner);
    result = STATUS_FAILED;
  }
  if (inf.entry_count != entryCount) {
    LOG("(%d) entry_count expected: %d, actually: %d\n",
        check_idx, entryCount, inf.entry_count);
    result = STATUS_FAILED;
  }
  if (inf.waiter_count != waiterCount) {
    LOG("(%d) waiter_count expected: %d, actually: %d\n",
        check_idx, waiterCount, inf.waiter_count);
    result = STATUS_FAILED;
  }
  if (inf.notify_waiter_count != notifyWaiterCount) {
    LOG("(%d) notify_waiter_count expected: %d, actually: %d\n",
        check_idx, notifyWaiterCount, inf.notify_waiter_count);
    result = STATUS_FAILED;
  }
}

JNIEXPORT jint JNICALL
Java_ObjectMonitorUsage_getRes(JNIEnv *jni, jclass cls) {
  return result;
}

} // exnern "C"
