/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#include "JNICB.h"
#include "jlong.h"

#define CHECK_NULL(thing, message) \
    if (thing == NULL) { \
        jclass cls = (*env)->FindClass(env, "java/lang/Exception"); \
        (*env)->ThrowNew(env, cls, message); \
        return 0; \
    }

JNIEXPORT jlong JNICALL Java_org_openjdk_bench_jdk_incubator_foreign_JNICB_makeCB
  (JNIEnv *env, jclass cls, jstring holderName, jstring methodName, jstring descriptor) {

  const char* holderNameC = (*env)->GetStringUTFChars(env, holderName, NULL);
  const char* methodNameC = (*env)->GetStringUTFChars(env, methodName, NULL);
  const char* descriptorC = (*env)->GetStringUTFChars(env, descriptor, NULL);

  JNICB cb = malloc(sizeof *cb);
  CHECK_NULL(cb, "Can not allocate cb");

  jclass holder = (*env)->FindClass(env, holderNameC);
  CHECK_NULL(holder, "Can not find class");
  holder = (jclass) (*env)->NewGlobalRef(env, holder);
  cb->holder = holder;

  jmethodID methodID = (*env)->GetStaticMethodID(env, holder, methodNameC, descriptorC);
  CHECK_NULL(methodID, "Can not find method");
  //methodID = (jmethodID) (*env)->NewGlobalRef(env, methodID); // DON'T DO THIS! -> Crashes GC
  cb->mid = methodID;

  (*env)->ReleaseStringUTFChars(env, holderName, holderNameC);
  (*env)->ReleaseStringUTFChars(env, methodName, methodNameC);
  (*env)->ReleaseStringUTFChars(env, descriptor, descriptorC);

  return ptr_to_jlong(cb);
}
