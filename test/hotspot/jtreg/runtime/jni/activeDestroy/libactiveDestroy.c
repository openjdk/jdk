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

#include <stdio.h>
#include <stdlib.h>

#include "jni.h"

static const char* jni_error_code(int ret) {
  switch(ret) {
  case JNI_OK: return "JNI_OK";
  case JNI_ERR: return "JNI_ERR";
  case JNI_EDETACHED: return "JNI_EDETACHED";
  case JNI_EVERSION: return "JNI_EVERSION";
  case JNI_ENOMEM: return "JNI_ENOMEM";
  case JNI_EEXIST: return "JNI_EEXIST";
  case JNI_EINVAL: return "JNI_EINVAL";
  default: return "Invalid JNI error code";
  }
}

JNIEXPORT jboolean JNICALL
Java_TestActiveDestroy_tryDestroyJavaVM(JNIEnv *env, jclass cls) {
  JavaVM* jvm;
  int res = (*env)->GetJavaVM(env, &jvm);
  if (res != JNI_OK) {
    fprintf(stderr, "GetJavaVM failed: %s\n", jni_error_code(res));
    exit(1);
  }
  printf("Calling DestroyJavaVM from active thread\n");
  res = (*jvm)->DestroyJavaVM(jvm);
  printf("DestroyJavaVM returned: %s\n", jni_error_code(res));
  return res == JNI_OK;
}
