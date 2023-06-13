/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#include <jni.h>
#include <stdlib.h>
#include <pthread.h>
#include <stdio.h>

#define die(x) do { printf("%s:%s\n",x , __func__); perror(x); exit(EXIT_FAILURE); } while (0)

#ifndef _Included_CompleteExit
#define _Included_CompleteExit
#ifdef __cplusplus
extern "C" {
#endif

static JavaVM* jvm;
static pthread_t attacher;

static jobject t1, t2;

static void* do_test() {
  JNIEnv* env;
  int res = (*jvm)->AttachCurrentThread(jvm, (void**)&env, NULL);
  if (res != JNI_OK) die("AttachCurrentThread");

  if ((*env)->MonitorEnter(env, t1) != 0) die("MonitorEnter");
  if ((*env)->MonitorEnter(env, t2) != 0) die("MonitorEnter");

  if ((*jvm)->DetachCurrentThread(jvm) != JNI_OK) die("DetachCurrentThread");
  pthread_exit(NULL);

  return NULL;
}

/*
 * Class:     CompleteExit
 * Method:    startThread
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_CompleteExit_testIt(JNIEnv* env, jclass jc, jobject o1, jobject o2) {
    void* ret;
    pthread_attr_t attr;

    (*env)->GetJavaVM(env, &jvm);

    t1 = (*env)->NewGlobalRef(env, o1);
    t2 = (*env)->NewGlobalRef(env, o2);

    if (pthread_attr_init(&attr) != 0) die("pthread_attr_init");
    if (pthread_create(&attacher, &attr, do_test, NULL) != 0) die("pthread_create");
    if (pthread_join(attacher, &ret) != 0) die("pthread_join");
}

#ifdef __cplusplus
}
#endif
#endif
