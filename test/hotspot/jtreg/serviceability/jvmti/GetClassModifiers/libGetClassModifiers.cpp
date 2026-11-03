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

#include "jni.h"
#include "jvmti.h"

extern "C" {

static jvmtiEnv *jvmti;

JNIEXPORT void JNICALL Java_GetClassModifiers_init(JNIEnv *env, jclass clazz) {
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

JNIEXPORT jint JNICALL Java_GetClassModifiers_getClassModifiers(JNIEnv *env, jclass ignore, jclass target) {
  jvmtiError err;
  jint modifiers;
  err = jvmti->GetClassModifiers(target, &modifiers);
  if (err != JVMTI_ERROR_NONE) {
    env->FatalError("GetClassModifiers failed");
  }
  return modifiers;
}

}
