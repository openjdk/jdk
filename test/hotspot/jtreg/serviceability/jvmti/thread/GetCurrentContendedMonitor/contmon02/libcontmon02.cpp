/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

static jvmtiEnv *jvmti;

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {

  jint res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    LOG("Wrong result of a valid call to GetEnv !\n");
    return JNI_ERR;
  }

  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(jvmtiCapabilities));
  caps.can_get_current_contended_monitor = 1;
  jvmtiError err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(AddCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }

  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_contmon02_checkMonitor(JNIEnv *jni, jclass cls, jint point, jthread thread) {
  jobject monitor;
  jvmtiError err = jvmti->GetCurrentContendedMonitor(thread, &monitor);
  check_jvmti_status(jni, err, "Error in GetCurrentContendedMonitor");
  if (monitor != nullptr) {
    LOG("(#%d) unexpected monitor object: 0x%p\n", point, monitor);
    fatal(jni, "GetCurrentContendedMonitor return unexpected monitor.");
  }
}

}
