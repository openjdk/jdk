/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include <stdlib.h>

#ifdef _WIN32
#include <windows.h>
#include <process.h>
typedef  unsigned int THREAD_ID;
#else
#include <pthread.h>
typedef  pthread_t THREAD_ID;
#endif

#include <string.h>

#include "jni.h"

JavaVM* jvm;
jboolean warning = 0;

void generateWarning(JNIEnv *env) {
  jclass class_id;
  jmethodID method_id;

  printf("About to trigger JNI Warning\n");

  // Just call Thread.currentThread() twice in succession without checking
  // for an exception in between.

  class_id = (*env)->FindClass (env, "java/lang/Thread");
  if (class_id == NULL) {
    fprintf(stderr, "Test ERROR. Can't load class Thread\n");
    exit(1);
  }

  method_id = (*env)->GetStaticMethodID(env, class_id, "currentThread",
                                        "()Ljava/lang/Thread;");
  if (method_id == NULL) {
    fprintf(stderr, "Test ERROR. Can't find method currentThread\n");
    exit(1);
  }

  jobject nativeThread = (*env)->CallStaticObjectMethod(env, class_id, method_id, NULL);
  nativeThread = (*env)->CallStaticObjectMethod(env, class_id, method_id, NULL);
}

void generateError(JNIEnv *env) {
  printf("About to trigger JNI FatalError\n");
  (*env)->FatalError(env, "Fatal error generated in test code");
}

#ifdef _WIN32
unsigned
#else
void *
#endif
thread_start(void* unused) {
  JNIEnv *env;
  int res;

  printf("Native thread is running and attaching as daemon ...\n");

  res = (*jvm)->AttachCurrentThreadAsDaemon(jvm, (void **)&env, NULL);
  if (res != JNI_OK) {
    fprintf(stderr, "Test ERROR. Can't attach current thread: %d\n", res);
    exit(1);
  }

  if (warning != 0) {
    generateWarning(env);
  } else {
    generateError(env);
  }

  if ((*env)->ExceptionOccurred(env) != NULL) {
    (*env)->ExceptionDescribe(env);
    exit(1);
  }

  res = (*jvm)->DetachCurrentThread(jvm);
  if (res != JNI_OK) {
    fprintf(stderr, "Test ERROR. Can't detach current thread: %d\n", res);
    exit(1);
  }

  printf("Native thread terminating\n");

#ifndef _WIN32
  return NULL;
#else
  return 0;
#endif
}

JNIEXPORT void JNICALL
Java_TestNativeStack_triggerJNIStackTrace
(JNIEnv *env, jclass cls, jboolean warn) {
  THREAD_ID thread;
  int res = (*env)->GetJavaVM(env, &jvm);
  if (res != JNI_OK) {
    fprintf(stderr, "Test ERROR. Can't extract JavaVM: %d\n", res);
    exit(1);
  }

  warning = warn;

#ifdef _WIN32
  HANDLE hThread = (HANDLE) _beginthreadex(NULL, 0, thread_start,
                                           NULL, 0, &thread);
  if (hThread == 0) {
    fprintf(stderr, "TEST ERROR: _beginthreadex failed: %s\n", strerror(errno));
    exit(1);
  }
  if (WaitForSingleObject(hThread, INFINITE) != WAIT_OBJECT_0) {
    fprintf(stderr, "TEST ERROR: WaitForSingleObject failed: %d\n", GetLastError());
    exit(1);
  }
  CloseHandle(hThread);

#else

  pthread_attr_t attr;
  pthread_attr_init(&attr);
  size_t stack_size = 0x100000;
  pthread_attr_setstacksize(&attr, stack_size);

  if ((res = pthread_create(&thread, &attr, thread_start, NULL)) != 0) {
    fprintf(stderr, "TEST ERROR: pthread_create failed: %s (%d)\n", strerror(res), res);
    exit(1);
  }

  pthread_attr_destroy(&attr);

  if ((res = pthread_join(thread, NULL)) != 0) {
    fprintf(stderr, "TEST ERROR: pthread_join failed: %s (%d)\n", strerror(res), res);
    exit(1);
  }
#endif
}
