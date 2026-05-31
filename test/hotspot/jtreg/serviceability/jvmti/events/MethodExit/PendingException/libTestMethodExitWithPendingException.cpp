/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "jni.h"
#include "jvmti_common.hpp"

jvmtiEnv* jvmti_env;

bool method_exit_posted = false;
// This method exit callback actually works only for 2 methods:
// 1) for ExceptionExit it verifies that method exit
//    has been popped by exception and calls 'upCall' method using JNI.
// 2) for upCall method it verifies that event has correct
//    return value and was not popped by exception.
// The event callback just exits for all other methods.
static void JNICALL
cbMethodExit(jvmtiEnv* jvmti, JNIEnv* jni, jthread thread, jmethodID method,
             jboolean was_popped_by_exception, jvalue return_value) {
  const char * mname = get_method_name(jvmti, jni, method);
  if (strcmp("upCall", mname) == 0) {
    if (was_popped_by_exception) {
      fatal(jni, "The method's was_popped_by_exception value is incorrect.");
    }
    jstring upcall_result = (jstring) return_value.l;
    const char *str = jni->GetStringUTFChars(upcall_result, nullptr);
    if (str == nullptr) {
      fatal(jni, "Failed to convert Java string to C string.");
    }
    if (strcmp("MyNewString", str) != 0) {
      fatal(jni, "The upCall result value is incorrect.");
    }
    method_exit_posted = true;
  }
  if (strcmp("exceptionExit", mname) != 0) {
    return;
  }
  if (!was_popped_by_exception) {
    fatal(jni, "Should have was_popped_by_esxception = true.");
  }
  jclass main_class = jni->FindClass("TestMethodExitWithPendingException");
  if (main_class == nullptr) {
    fatal(jni, "Can't find TestMethodExitWithPendingException class.");
    return;
  }
  jmethodID upcall_method = jni->GetStaticMethodID(main_class,
                                                   "upCall", "()Ljava/lang/String;");
  if (upcall_method == nullptr) {
    fatal(jni, "Can't find upCall method.");
  }
  // Call 'upCall' method while current thread has exception
  // that has been thrown but hasn't been caught yet.
  jstring upcall_result = (jstring) jni->CallStaticObjectMethod(main_class, upcall_method);
  const char *str = jni->GetStringUTFChars(upcall_result, nullptr);
  if (str == nullptr) {
    fatal(jni, "Failed to convert Java string to C string.");
    return;
  }
  if (strcmp("MyNewString", str) != 0) {
    fatal(jni, "The upCall result value is incorrect.");
  }
  jni->ReleaseStringUTFChars(upcall_result, str);
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
  jvmtiEnv *jvmti = nullptr;
  jint res = vm->GetEnv((void **) &jvmti, JVMTI_VERSION_21);
  if (res != JNI_OK) {
    return JNI_ERR;
  }
  jvmtiError err = JVMTI_ERROR_NONE;
  jvmtiCapabilities capabilities;
  (void) memset(&capabilities, 0, sizeof (capabilities));
  capabilities.can_generate_method_exit_events = true;
  err = jvmti->AddCapabilities(&capabilities);
  check_jvmti_error(err, "AddCapabilities");
  jvmtiEventCallbacks callbacks;
  (void) memset(&callbacks, 0, sizeof (callbacks));
  callbacks.MethodExit = &cbMethodExit;
  err = jvmti->SetEventCallbacks(&callbacks, (int) sizeof (jvmtiEventCallbacks));
  check_jvmti_error(err, "SetEventCallbacks");
  jvmti_env = jvmti;
  return JNI_OK;
}


extern "C" {

JNIEXPORT void JNICALL
Java_TestMethodExitWithPendingException_enable(JNIEnv *jni, jclass clazz) {
  jthread thread = get_current_thread(jvmti_env, jni);
  jvmti_env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_METHOD_EXIT, thread);
}


JNIEXPORT void JNICALL
Java_TestMethodExitWithPendingException_disableAndCheck(JNIEnv *jni, jclass clazz) {
  jthread thread = get_current_thread(jvmti_env, jni);
  jvmti_env->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_METHOD_EXIT, thread);
  if (!method_exit_posted) {
    fatal(jni, "Failed to post method exit event.");
  }
}

} // extern "C"
