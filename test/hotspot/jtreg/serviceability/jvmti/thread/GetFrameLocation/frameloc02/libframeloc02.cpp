/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include <string.h>
#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

#define PASSED 0
#define STATUS_FAILED 2

typedef struct {
  const char *cls_sig;
  const char *name;
  const char *sig;
  jlocation loc;
} frame_info;

static jvmtiEnv *jvmti_env = nullptr;
static jint result = PASSED;
static frame_info fi =
    {"Lframeloc02;", "check",
        "(Ljava/lang/Thread;)I", -1};

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jint res = jvm->GetEnv((void **) &jvmti_env, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti_env == nullptr) {
    LOG("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }

  return JNI_OK;
}

JNIEXPORT jint JNICALL
Java_frameloc02_check(JNIEnv *env, jclass cls, jthread thr) {
  jvmtiError err;
  jclass klass;
  jmethodID mid;
  jlocation loc;
  char *cls_sig, *name, *sig, *generic;
  char buffer[32];

  LOG(">>> acquiring frame location ...\n");

  err = jvmti_env->GetFrameLocation(thr, 0, &mid, &loc);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetFrameLocation) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return result;
  }

  LOG(">>> retrieving class/method info ...\n");

  err = jvmti_env->GetMethodDeclaringClass(mid, &klass);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetMethodDeclaringClass) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return result;
  }
  err = jvmti_env->GetClassSignature(klass, &cls_sig, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetClassSignature) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return result;
  }
  err = jvmti_env->GetMethodName(mid, &name, &sig, &generic);
  if (err != JVMTI_ERROR_NONE) {
    LOG("(GetMethodName) unexpected error: %s (%d)\n", TranslateError(err), err);
    result = STATUS_FAILED;
    return result;
  }

  LOG(">>>      class: \"%s\"\n", cls_sig);
  LOG(">>>     method: \"%s%s\"\n", name, sig);
  LOG(">>>   location: %s\n", jlong_to_string(loc, buffer));

  if (cls_sig == nullptr || strcmp(cls_sig, fi.cls_sig) != 0) {
    LOG("(GetFrameLocation) wrong class: \"%s\"\n", cls_sig);
    LOG(", expected: \"%s\"\n", fi.cls_sig);
    result = STATUS_FAILED;
  }
  if (name == nullptr || strcmp(name, fi.name) != 0) {
    LOG("(GetFrameLocation) wrong method name: \"%s\"", name);
    LOG(", expected: \"%s\"\n", fi.name);
    result = STATUS_FAILED;
  }
  if (sig == nullptr || strcmp(sig, fi.sig) != 0) {
    LOG("(GetFrameLocation) wrong method signature: \"%s\"", sig);
    LOG(", expected: \"%s\"\n", fi.sig);
    result = STATUS_FAILED;
  }
  if (loc != fi.loc) {
    LOG("(GetFrameLocation) wrong location: %s", jlong_to_string(loc, buffer));
    LOG(", expected: %s\n", jlong_to_string(fi.loc, buffer));
    result = STATUS_FAILED;
  }

  LOG(">>> ... done\n");

  return result;
}

}
