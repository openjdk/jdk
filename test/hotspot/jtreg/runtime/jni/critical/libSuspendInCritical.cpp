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

#include <atomic>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jvmti.h"
#include "jvmti_common.hpp"

static jvmtiEnv* jvmti = nullptr;

static std::atomic<jboolean> stay_in_critical_native{JNI_TRUE};
static std::atomic<jlong> native_counter{0};

extern "C" {

JNIEXPORT void JNICALL
Java_SuspendInCritical_suspendThread(JNIEnv* jni, jclass cls, jthread t) {
  suspend_thread(jvmti, jni, t);
}

JNIEXPORT void JNICALL
Java_SuspendInCritical_resumeThread(JNIEnv* jni, jclass cls, jthread t) {
  resume_thread(jvmti, jni, t);
}

JNIEXPORT jboolean JNICALL
Java_SuspendInCritical_isSuspended(JNIEnv* jni, jclass cls, jthread t) {
  jint state = get_thread_state(jvmti, jni, t);
  return (state & JVMTI_THREAD_STATE_SUSPENDED) != 0;
}

JNIEXPORT void JNICALL
Java_SuspendInCritical_leaveCriticalNative(JNIEnv* jni, jclass cls) {
  stay_in_critical_native = JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_SuspendInCritical_getNativeCounter(JNIEnv* jni, jclass cls) {
  return native_counter;
}

JNIEXPORT void JNICALL
  Java_SuspendInCritical_doCritical(JNIEnv* jni, jclass cls, jbyteArray bytes, jstring str) {
  jboolean is_copy = JNI_FALSE;
  jbyte* b;
  if (bytes != nullptr) {
    LOG("CriticalThread doing GetPrimitiveArrayCritical\n");
    b = (jbyte*) jni->GetPrimitiveArrayCritical(bytes, &is_copy);
    if (b == nullptr) {
      jni->FatalError("GetPrimitiveArrayCritical returned null!");
    }
  }

  const jchar* c;
  if (str != nullptr) {
    LOG("CriticalThread doing GetStringCritical\n");
    c = jni->GetStringCritical(str, &is_copy);
    if (c == nullptr) {
      jni->FatalError("GetStringCritical returned null!");
    }
  }

  LOG("CriticalThread should now be in deferred suspension\n");
  // Do some visible work
  while (stay_in_critical_native) {
    native_counter++;
    sleep_ms(1);
  }

  LOG("CriticalThread released for Java upcall\n");
  // Now perform the Java upcall
  jmethodID upcall_mid = jni->GetStaticMethodID(cls, "upcall", "()V");
  if (upcall_mid == nullptr) {
    // Unexpected exception - let it propagate
    if (bytes != nullptr) {
      jni->ReleasePrimitiveArrayCritical(bytes, b, 0);
    }
    if (str != nullptr) {
      jni->ReleaseStringCritical(str, c);
    }
    return;
  }
  jni->CallStaticVoidMethod(cls, upcall_mid);

  if (bytes != nullptr) {
    jni->ReleasePrimitiveArrayCritical(bytes, b, 0);
  }
  if (str != nullptr) {
    jni->ReleaseStringCritical(str, c);
  }

  // Now we should suspend as we return to Java
  LOG("CriticalThread returning to Java\n");
}

/** Agent library initialization. */

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  LOG("\nAgent_OnLoad started\n");

  // create JVMTI environment
  if (jvm->GetEnv((void **) (&jvmti), JVMTI_VERSION_1_1) != JNI_OK) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  // add specific capabilities for suspending thread
  jvmtiCapabilities suspendCaps;
  memset(&suspendCaps, 0, sizeof(suspendCaps));
  suspendCaps.can_suspend = 1;

  jvmtiError err = jvmti->AddCapabilities(&suspendCaps);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(AddCapabilities) unexpected error: %s (%d)\n", TranslateError(err), err);
    return JNI_ERR;
  }
  LOG("Agent_OnLoad finished\n");
  return JNI_OK;
}

} // extern C
