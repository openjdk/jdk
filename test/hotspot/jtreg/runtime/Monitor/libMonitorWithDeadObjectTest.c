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
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#ifdef __cplusplus
extern "C" {
#endif

static JavaVM* jvm;
static pthread_t attacher;

#define die(x) do { printf("%s:%s\n",x , __func__); perror(x); exit(EXIT_FAILURE); } while (0)

static void check_exception(JNIEnv* env, const char* msg) {
  if ((*env)->ExceptionCheck(env)) {
    fprintf(stderr, "Error: %s", msg);
    exit(-1);
  }
}

#define check(env, what, msg)                      \
  check_exception((env), (msg));                   \
  do {                                             \
    if ((what) == 0) {                             \
      fprintf(stderr, #what "is null: %s", (msg)); \
      exit(-2);                                    \
    }                                              \
  } while (0)

static jobject create_object(JNIEnv* env) {
  jclass clazz = (*env)->FindClass(env, "java/lang/Object");
  check(env, clazz, "No class");

  jmethodID constructor = (*env)->GetMethodID(env, clazz, "<init>", "()V");
  check(env, constructor, "No constructor");

  jobject obj = (*env)->NewObject(env, clazz, constructor);
  check(env, constructor, "No object");

  return obj;
}

static void system_gc(JNIEnv* env) {
  jclass clazz = (*env)->FindClass(env, "java/lang/System");
  check(env, clazz, "No class");

  jmethodID method = (*env)->GetStaticMethodID(env, clazz, "gc", "()V");
  check(env, method, "No method");

  (*env)->CallStaticVoidMethod(env, clazz, method);
  check_exception(env, "Calling System.gc()");
}

static void thread_dump_with_locked_monitors(JNIEnv* env) {
  jclass ManagementFactoryClass = (*env)->FindClass(env, "java/lang/management/ManagementFactory");
  check(env, ManagementFactoryClass, "No ManagementFactory class");

  jmethodID getThreadMXBeanMethod = (*env)->GetStaticMethodID(env, ManagementFactoryClass, "getThreadMXBean", "()Ljava/lang/management/ThreadMXBean;");
  check(env, getThreadMXBeanMethod, "No getThreadMXBean method");

  jobject threadBean = (*env)->CallStaticObjectMethod(env, ManagementFactoryClass, getThreadMXBeanMethod);
  check(env, threadBean, "Calling getThreadMXBean()");

  jclass ThreadMXBeanClass = (*env)->FindClass(env, "java/lang/management/ThreadMXBean");
  check(env, ThreadMXBeanClass, "No ThreadMXBean class");

  jmethodID dumpAllThreadsMethod = (*env)->GetMethodID(env, ThreadMXBeanClass, "dumpAllThreads", "(ZZ)[Ljava/lang/management/ThreadInfo;");
  check(env, dumpAllThreadsMethod, "No dumpAllThreads method");

  // The 'lockedMonitors == true' is what causes the monitor with a dead object to be examined.
  jobject array = (*env)->CallObjectMethod(env, threadBean, dumpAllThreadsMethod, JNI_TRUE /* lockedMonitors */, JNI_FALSE /* lockedSynchronizers*/);
  check(env, array, "Calling dumpAllThreads(true, false)");
}

static void create_monitor_with_dead_object(JNIEnv* env) {
  jobject obj = create_object(env);

  if ((*env)->MonitorEnter(env, obj) != 0) die("MonitorEnter");

  // Drop the last strong reference to the object associated with the monitor.
  // The monitor only keeps a weak reference to the object.
  (*env)->DeleteLocalRef(env, obj);

  // Let the GC clear the weak reference to the object.
  system_gc(env);
}

static void* create_monitor_with_dead_object_in_thread(void* arg) {
  JNIEnv* env;
  int res = (*jvm)->AttachCurrentThread(jvm, (void**)&env, NULL);
  if (res != JNI_OK) die("AttachCurrentThread");

  // Make the correct incantation to create a monitor with a dead object.
  create_monitor_with_dead_object(env);

  // DetachCurrentThread will try to unlock held monitors. This has been a
  // source of at least two bugs:
  // - When the object reference in the monitor was cleared, the monitor
  //   iterator code would skip it, preventing it from being unlocked when
  //   the owner thread detached, leaving it lingering in the system.
  // - When the monitor iterator API was rewritten the code was changed to
  //   assert that we didn't have "owned" monitors with dead objects. This
  //   test provokes that situation and that asserts.
  if ((*jvm)->DetachCurrentThread(jvm) != JNI_OK) die("DetachCurrentThread");

  return NULL;
}

static void* create_monitor_with_dead_object_and_dump_threads_in_thread(void* arg) {
  JNIEnv* env;
  int res = (*jvm)->AttachCurrentThread(jvm, (void**)&env, NULL);
  if (res != JNI_OK) die("AttachCurrentThread");

  // Make the correct incantation to create a monitor with a dead object.
  create_monitor_with_dead_object(env);

  // Perform a thread dump that checks for all thread's monitors.
  // That code didn't expect the monitor iterators to return monitors
  // with dead objects and therefore asserted/crashed.
  thread_dump_with_locked_monitors(env);

  if ((*jvm)->DetachCurrentThread(jvm) != JNI_OK) die("DetachCurrentThread");

  return NULL;
}

JNIEXPORT void JNICALL Java_MonitorWithDeadObjectTest_createMonitorWithDeadObject(JNIEnv* env, jclass jc) {
  void* ret;

  (*env)->GetJavaVM(env, &jvm);

  if (pthread_create(&attacher, NULL, create_monitor_with_dead_object_in_thread, NULL) != 0) die("pthread_create");
  if (pthread_join(attacher, &ret) != 0) die("pthread_join");
}

JNIEXPORT void JNICALL Java_MonitorWithDeadObjectTest_createMonitorWithDeadObjectDumpThreadsBeforeDetach(JNIEnv* env, jclass jc) {
  void* ret;

  (*env)->GetJavaVM(env, &jvm);

  if (pthread_create(&attacher, NULL, create_monitor_with_dead_object_and_dump_threads_in_thread, NULL) != 0) die("pthread_create");
  if (pthread_join(attacher, &ret) != 0) die("pthread_join");
}

#ifdef __cplusplus
}
#endif
