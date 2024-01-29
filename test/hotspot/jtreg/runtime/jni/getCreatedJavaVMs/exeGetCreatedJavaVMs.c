/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/* This code tests concurrent creation of and then attach to a JVM.
 * Two threads race to create the JVM, the loser then checks GetCreatedJavaVMs
 * and attaches to the returned JVM. Prior to the fix this could crash as the
 * JVM is not fully initialized.
 */
#include "jni.h"
#include <string.h>
#include <pthread.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>

#define NUM_THREADS 2

void *thread_runner(void *threadid) {
  int tid;
  tid = (int)(intptr_t)threadid;

  JavaVM *vm;
  JNIEnv *env = 0;

  JavaVMInitArgs vm_args;
  JavaVMOption options[0];
  vm_args.version = JNI_VERSION_1_2;
  vm_args.nOptions = 0;
  vm_args.options = options;
  vm_args.ignoreUnrecognized = JNI_FALSE;

  printf("[%d] BEGIN JNI_CreateJavaVM\n", tid);
  jint create_res = JNI_CreateJavaVM(&vm, (void **)&env, &vm_args);
  printf("[%d] END JNI_CreateJavaVM\n", tid);

  if (create_res != JNI_OK) {
    printf("[%d] Error creating JVM: %d\n", tid, create_res);
    if (create_res == JNI_EEXIST) {
      jsize count;
      printf("[%d] BEGIN JNI_GetCreatedJavaVMs\n", tid);
      jint get_res = JNI_GetCreatedJavaVMs(&vm, 1, &count);
      printf("[%d] END JNI_GetCreatedJavaVMs\n", tid);

      if (get_res != JNI_OK) {
        printf("[%d] Error obtaining created VMs: %d\n", tid, get_res);
        pthread_exit(NULL);
      } else {
        printf("[%d] Obtained %d created VMs\n", tid, count);
      }
      if (count > 0) {
        printf("[%d] BEGIN AttachCurrentThread\n", tid);
        get_res = (*vm)->AttachCurrentThread(vm, (void **)&env, NULL);
        printf("[%d] END AttachCurrentThread - %s\n", tid,
               (get_res == JNI_OK ? "succeeded" : "failed"));
        if (get_res == JNI_OK) {
          (*vm)->DetachCurrentThread(vm);
        }
      }
      pthread_exit(NULL);
    } else {
      pthread_exit(NULL);
    }
  } else {
    printf("[%d] Created a JVM\n", tid);
  }

  pthread_exit(NULL);
}

int main (int argc, char* argv[]) {
  pthread_t threads[NUM_THREADS];
  pthread_attr_t attr;
  pthread_attr_init(&attr);
  size_t stack_size = 0x100000;
  pthread_attr_setstacksize(&attr, stack_size);
  for (int i = 0; i < NUM_THREADS; i++ ) {
    printf("[*] Creating thread %d\n", i);
    int status = pthread_create(&threads[i], &attr, thread_runner, (void *)(intptr_t)i);
    if (status != 0) {
      printf("[*] Error creating thread %d - %d\n", i, status);
      exit(-1);
    }
  }
  pthread_attr_destroy(&attr);
  for (int i = 0; i < NUM_THREADS; i++ ) {
    pthread_join(threads[i], NULL);
  }
  return 0;
}
