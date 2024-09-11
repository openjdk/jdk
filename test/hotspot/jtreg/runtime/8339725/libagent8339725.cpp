/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

#include <jvmti.h>
#include <jni.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static jvmtiEnv *_jvmti;
static JavaVM *_jvm;

#define BUFFER_SIZE 100000
static std::atomic<jmethodID> ring_buffer[BUFFER_SIZE];

void get_method_details(jmethodID method) {
  jclass method_class;
  char *class_name = NULL;
  if (_jvmti->GetMethodDeclaringClass(method, &method_class) == JVMTI_ERROR_NONE) {
    if (_jvmti->GetClassSignature(method_class, &class_name, NULL) == JVMTI_ERROR_NONE) {
      _jvmti->Deallocate((unsigned char *)class_name);
    }
  }
}

void* read_ringbuffer(void* arg) {
  JNIEnv *env;
  _jvm->AttachCurrentThreadAsDaemon((void **)&env, NULL);
  for (;;) {
    jmethodID id = ring_buffer[rand() % BUFFER_SIZE].load(std::memory_order_relaxed);
    if (id != (jmethodID)0) {
      get_method_details(id);
    }
  }
  return NULL;
}

static void JNICALL ClassPrepareCallback(jvmtiEnv *jvmti_env,
                                         JNIEnv *jni_env,
                                         jthread thread,
                                         jclass klass) {
  static bool reader_created = false;
  static int ring_buffer_idx = 0;

  char *class_name = NULL;
  if (jvmti_env->GetClassSignature(klass, &class_name, NULL) != JVMTI_ERROR_NONE) {
    return;
  }
  // We only care MyClass and only one thread loads it
  bool is_my_class = strcmp(class_name, "LMyClass;") == 0;
  jvmti_env->Deallocate((unsigned char *)class_name);
  if (!is_my_class) {
    return;
  }

  if (!reader_created) {
    pthread_t tid;
    pthread_create(&tid, NULL, read_ringbuffer, NULL);
    reader_created = true;
  }

  jint method_count;
  jmethodID *methods;
  if (jvmti_env->GetClassMethods(klass, &method_count, &methods) == JVMTI_ERROR_NONE) {
    ring_buffer[ring_buffer_idx++].store(methods[0], std::memory_order_relaxed);
    ring_buffer_idx = ring_buffer_idx % BUFFER_SIZE;
    jvmti_env->Deallocate((unsigned char *)methods);
  }
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  for (int i = 0; i < BUFFER_SIZE; i++) {
    ring_buffer[i].store(0, std::memory_order_relaxed);
  }

  jvmtiEventCallbacks callbacks;
  jvmtiError error;

  _jvm = jvm;

  if (jvm->GetEnv((void **)&_jvmti, JVMTI_VERSION_1_0) != JNI_OK) {
    fprintf(stderr, "Unable to access JVMTI!\n");
    return JNI_ERR;
  }

  // Set up the event callbacks
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.ClassPrepare = &ClassPrepareCallback;

  // Register the callbacks
  error = _jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
  if (error != JVMTI_ERROR_NONE) {
    fprintf(stderr, "Error setting event callbacks: %d\n", error);
    return JNI_ERR;
  }

  // Enable the ClassPrepare event
  error = _jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, NULL);
  if (error != JVMTI_ERROR_NONE) {
    fprintf(stderr, "Error enabling ClassPrepare event: %d\n", error);
    return JNI_ERR;
  }

  return JNI_OK;
}
