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

#include "jni.h"
#include "jvmti.h"

static jvmtiEnv *jvmti;

extern "C" JNIEXPORT void JNICALL Java_AsyncThreadDumpTest_init(JNIEnv *env, jclass clazz) {
  JavaVM* vm;
  jint res;
  res = env->GetJavaVM(&vm);
  if (res != 0) {
    env->FatalError("GetJavaVM failed");
  } else {
    res = vm->GetEnv((void**)&jvmti, JVMTI_VERSION);
    if (res != JNI_OK) {
      env->FatalError("GetEnv failed");
    }
  }
}

extern "C" JNIEXPORT void JNICALL Java_AsyncThreadDumpTest_printThread(JNIEnv *env, jclass clazz, jobject thread) {
  jvmtiError err;
  jvmtiFrameInfo frames[100];
  jint count;
  err = jvmti->GetStackTrace(thread, 0, 100, frames, &count);
  if (err != JVMTI_ERROR_NONE) {
    env->FatalError("GetStackTrace failed");
    return;
  }

  for (jint i = 0; i < count; i++) {
    jclass klass = nullptr;
    err = jvmti->GetMethodDeclaringClass(frames[i].method, &klass);
    if (err != JVMTI_ERROR_NONE) {
      env->FatalError("GetMethodDeclaringClass failed");
      return;
    }

    char *klassSig = nullptr;
    err = jvmti->GetClassSignature(klass, &klassSig, nullptr);
    if (err != JVMTI_ERROR_NONE) {
      env->FatalError("GetClassSignature failed");
      return;
    }
    for (char* s = klassSig; *s != '\0'; s++) {
      if (*s == '/') {
        *s = '.';
      } else if (*s == ';') {
        *s = '\0';
      }
    }

    char *methodName = nullptr;
    err = jvmti->GetMethodName(frames[i].method, &methodName, nullptr, nullptr);
    if (err != JVMTI_ERROR_NONE) {
      env->FatalError("GetMethodName failed");
      return;
    }

    if (err == JVMTI_ERROR_NONE) {
      printf("  - %s.%s\n", klassSig + 1/*skip 'L'*/, methodName);
    }
  }
  fflush(nullptr);
}
