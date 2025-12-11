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

#include <jvmti.h>

#include <string.h>

static jclass MAIN_CLS;
static jmethodID TARGET_ID;

static const char* TARGET_CLASS_NAME = "TestSharedCloseJvmti$EventDuringScopedAccessRunner";
static const char* TARGET_METHOD_NAME = "target";
static const char* TARGET_METHOD_SIG = "()V";

static const char* INTERCEPT_CLASS_NAME = "Ljdk/internal/foreign/MemorySessionImpl;";
static const char* INTERCEPT_METHOD_NAME = "checkValidStateRaw";

void start(jvmtiEnv*, JNIEnv* jni_env, jthread) {

  jclass cls = jni_env->FindClass(TARGET_CLASS_NAME);
  if (cls == nullptr) {
    jni_env->ExceptionDescribe();
    return;
  }

  MAIN_CLS = (jclass) jni_env->NewGlobalRef(cls);

  TARGET_ID = jni_env->GetStaticMethodID(cls, TARGET_METHOD_NAME, TARGET_METHOD_SIG);
  if (TARGET_ID == nullptr) {
    jni_env->ExceptionDescribe();
    return;
  }
}

void method_exit(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread, jmethodID method,
                 jboolean was_popped_by_exception, jvalue return_value) {
  char* method_name = nullptr;
  jvmtiError err = jvmti_env->GetMethodName(method, &method_name, nullptr, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    return;
  }

  if (strcmp(method_name, INTERCEPT_METHOD_NAME) != 0) {
    jvmti_env->Deallocate((unsigned char*) method_name);
    return;
  }

  jclass cls;
  err = jvmti_env->GetMethodDeclaringClass(method, &cls);
  if (err != JVMTI_ERROR_NONE) {
    jvmti_env->Deallocate((unsigned char*) method_name);
    return;
  }

  char* class_sig = nullptr;
  err = jvmti_env->GetClassSignature(cls, &class_sig, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    jvmti_env->Deallocate((unsigned char*) method_name);
    return;
  }

  if (strcmp(class_sig, INTERCEPT_CLASS_NAME) != 0) {
    jvmti_env->Deallocate((unsigned char*) method_name);
    jvmti_env->Deallocate((unsigned char*) class_sig);
    return;
  }

  jni_env->CallStaticVoidMethod(MAIN_CLS, TARGET_ID);
  if (jni_env->ExceptionOccurred()) {
    jni_env->ExceptionDescribe();
  }

  jvmti_env->Deallocate((unsigned char*) method_name);
  jvmti_env->Deallocate((unsigned char*) class_sig);
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
  jvmtiEnv* env;
  jint jni_err = vm->GetEnv((void**) &env, JVMTI_VERSION);
  if (jni_err != JNI_OK) {
    return jni_err;
  }

  jvmtiCapabilities capabilities;
  memset(&capabilities, 0, sizeof(jvmtiCapabilities));
  capabilities.can_generate_method_exit_events = 1;

  jvmtiError err = env->AddCapabilities(&capabilities);
  if (err != JVMTI_ERROR_NONE) {
    return err;
  }

  jvmtiEventCallbacks callbacks;
  callbacks.VMInit = start;
  callbacks.MethodExit = method_exit;

  err = env->SetEventCallbacks(&callbacks, (jint) sizeof(callbacks));
  if (err != JVMTI_ERROR_NONE) {
    return err;
  }

  err = env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_METHOD_EXIT, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    return err;
  }

  err = env->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, nullptr);
  if (err != JVMTI_ERROR_NONE) {
    return err;
  }

  return 0;
}
