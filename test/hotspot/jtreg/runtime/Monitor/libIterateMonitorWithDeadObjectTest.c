/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include <unistd.h>

#define die(x) do { printf("%s:%s\n",x , __func__); perror(x); exit(EXIT_FAILURE); } while (0)

#ifndef _Included_IterateMonitorWithDeadObjectTest
#define _Included_IterateMonitorWithDeadObjectTest
#ifdef __cplusplus
extern "C" {
#endif

static JavaVM* jvm;
static pthread_t attacher;

static jobject create_object(JNIEnv* env) {
  jclass clazz = (*env)->FindClass(env, "java/lang/Object");
  if (clazz == 0) die("No class");

  jmethodID constructor = (*env)->GetMethodID(env, clazz, "<init>", "()V");
  if (constructor == 0) die("No constructor");

  return (*env)->NewObject(env, clazz, constructor);
}

static void system_gc(JNIEnv* env) {
  jclass clazz = (*env)->FindClass(env, "java/lang/System");
  if (clazz == 0) die("No class");

  jmethodID method = (*env)->GetStaticMethodID(env, clazz, "gc", "()V");
  if (method == 0) die("No method");

  (*env)->CallStaticVoidMethod(env, clazz, method);
}

static void thread_dump_with_locked_monitors(JNIEnv* env) {
  jclass ManagementFactoryClass = (*env)->FindClass(env, "java/lang/management/ManagementFactory");
  if (ManagementFactoryClass == 0) die("No ManagementFactory class");

  jmethodID getThreadMXBeanMethod = (*env)->GetStaticMethodID(env, ManagementFactoryClass, "getThreadMXBean", "()Ljava/lang/management/ThreadMXBean;");
  if (getThreadMXBeanMethod == 0) die("No getThreadMXBean method");

  jobject threadBean = (*env)->CallStaticObjectMethod(env, ManagementFactoryClass, getThreadMXBeanMethod);

  jclass ThreadMXBeanClass = (*env)->FindClass(env, "java/lang/management/ThreadMXBean");
  if (ThreadMXBeanClass == 0) die("No ThreadMXBean class");

  jmethodID dumpAllThreadsMethod = (*env)->GetMethodID(env, ThreadMXBeanClass, "dumpAllThreads", "(ZZ)[Ljava/lang/management/ThreadInfo;");
  if (dumpAllThreadsMethod == 0) die("No dumpAllThreads method");

  // The 'lockedMonitors == true' is what triggers the collection of the monitor with the dead object.
  (*env)->CallObjectMethod(env, ThreadMXBeanClass, dumpAllThreadsMethod, threadBean, JNI_TRUE /* lockedMonitors */, JNI_FALSE /* lockedSynchronizers*/);
}

static void* do_test() {
  JNIEnv* env;
  int res = (*jvm)->AttachCurrentThread(jvm, (void**)&env, NULL);
  if (res != JNI_OK) die("AttachCurrentThread");

  jobject obj = create_object(env);

  if ((*env)->MonitorEnter(env, obj) != 0) die("MonitorEnter");

  // Drop the last strong reference to the object associated with the monitor.
  // The monitor only keeps a weak reference to the object.
  (*env)->DeleteLocalRef(env, obj);

  // Let the GC clear the weak reference to the object.
  system_gc(env);

  // Perform a thread dump that checks for all thread's monitors.
  // That code didn't expect the monitor iterators to return monitors
  // with dead objects and therefore asserted/crashed.
  thread_dump_with_locked_monitors(env);

  // DetachCurrenThread will try to unlock held monitors. This has been a
  // source of at least two bugs:
  // - When the object reference in the monitor was made weak, the code
  //   didn't unlock the monitor, leaving it lingering in the system.
  // - When the monitor iterator API was rewritten the code was changed to
  //   assert that we didn't have "owned" monitors with dead objects. This
  //   test provokes that situation and those asserts.
  if ((*jvm)->DetachCurrentThread(jvm) != JNI_OK) die("DetachCurrentThread");
  pthread_exit(NULL);

  return NULL;
}

/*
 * Class:     IterateMonitorWithDeadObjectTest
 * Method:    runTestAndDetachThread
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_IterateMonitorWithDeadObjectTest_runTestAndDetachThread(JNIEnv* env, jclass jc) {
    pthread_attr_t attr;
    void* ret;

    (*env)->GetJavaVM(env, &jvm);

    if (pthread_attr_init(&attr) != 0) die("pthread_attr_init");
    if (pthread_create(&attacher, &attr, do_test, NULL) != 0) die("pthread_create");
}

/*
 * Class:     IterateMonitorWithDeadObjectTest
 * Method:    joinTestThread
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_IterateMonitorWithDeadObjectTest_joinTestThread(JNIEnv* env, jclass jc) {
    void* ret;
    if (pthread_join(attacher, &ret) != 0) die("pthread_join");
}

#ifdef __cplusplus
}
#endif
#endif
