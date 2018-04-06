/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include <string.h>
#include <unistd.h>
#include <jni.h>
#if defined (__SUNPRO_C) && __SUNPRO_C >= 0x5140
#pragma error_messages(off, SEC_ARR_OUTSIDE_BOUND_READ)
#endif

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jstring JNICALL Java_SEGVOverflow_nativesegv(JNIEnv *env, jobject obj) {
  char *buffer1;
  char *buffer2;
  char *buffer3;
  char ch;

  jstring ret = NULL;

  // sleep for a bit to let the libadimalloc library initialize
  sleep(5);

  // allocate three buffers
  buffer1 = (char *)malloc(64);
  buffer2 = (char *)malloc(64);
  buffer3 = (char *)malloc(64);
  if ((buffer1 == NULL) || (buffer2 == NULL) || (buffer3 == NULL)) {
    // this return will result in a test failure
    return ret;
  }

  // Read past the end of each buffer multiple times to increase the probability
  // that an ADI version mismatch occurs so an ADI fault is triggered.
  ch = buffer1[70];
  ch = buffer2[70];
  ch = buffer3[70];
  ch = buffer1[140];
  ch = buffer2[140];
  ch = buffer3[140];

  // create a failed test return value because this test should have cored
  buffer1 = "TEST FAILED, a read past the end of a buffer succeeded.";
  ret = (*env)->NewStringUTF(env, buffer1);

  return ret;
}

#ifdef __cplusplus
}
#endif
