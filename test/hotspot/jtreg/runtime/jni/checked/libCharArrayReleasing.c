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
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jni.h"
#include "jni_util.h"

// Test the behaviour of the JNI "char" releasing functions, under Xcheck:jni,
// when they are passed "char" arrays obtained from different sources:
// - source_mode indicates which array to use
//   - 0: use a raw malloc'd array
//   - 1: use an array from GetCharArrayElements
//   - 2: use an array from GetStringChars
//   - 3: use an array from GetStringUTFChars
//   - 4: use an array from GetPrimitiveArrayCritical
// - release_mode indicates which releasing function to use
//   - 0: ReleaseCharArrayElements
//   - 1: ReleaseStringChars
//   - 2: ReleaseStringUTFChars
//   - 3: ReleasePrimitiveArrayCritical
//

static char* source[] = {
  "malloc",
  "GetCharArrayElements",
  "GetStringChars",
  "GetStringUTFChars",
  "GetPrimitiveArrayCritical"
};

static char* release_func[] = {
  "ReleaseCharArrayElements",
  "ReleaseStringChars",
  "ReleaseStringUTFChars",
  "ReleasePrimitiveArrayCritical"
};

JNIEXPORT void JNICALL
Java_TestCharArrayReleasing_testIt(JNIEnv *env, jclass cls, jint source_mode,
                               jint release_mode) {

  // First create some Java objects to be used as the sources for jchar[]
  // extraction.
  const int len = 10;
  jcharArray ca = (*env)->NewCharArray(env, len);
  jstring str = (*env)->NewStringUTF(env, "A_String");

  jthrowable exc = (*env)->ExceptionOccurred(env);
  if (exc != NULL) {
    fprintf(stderr, "ERROR: Unexpected exception during test set up:\n");
    (*env)->ExceptionDescribe(env);
    exit(2);
  }

  fprintf(stdout, "Testing release function %s with array from %s\n",
          release_func[release_mode], source[source_mode]);
  fflush(stdout);

  jboolean is_copy = JNI_FALSE;
  jchar* to_release;
  switch(source_mode) {
  case 0: {
    to_release = malloc(10 * sizeof(jchar));
    break;
  }
  case 1: {
    to_release = (*env)->GetCharArrayElements(env, ca, &is_copy);
    break;
  }
  case 2: {
    to_release = (jchar*) (*env)->GetStringChars(env, str, &is_copy);
    break;
  }
  case 3: {
    to_release = (jchar*) (*env)->GetStringUTFChars(env, str, &is_copy);
    break;
  }
  case 4: {
    to_release = (jchar*) (*env)->GetPrimitiveArrayCritical(env, ca, &is_copy);
    break;
  }
  default: fprintf(stderr, "Unexpected source_mode %d\n", source_mode);
    exit(1);
  }

  switch (release_mode) {
  case 0:
    (*env)->ReleaseCharArrayElements(env, ca, to_release, 0);
    break;
  case 1:
    (*env)->ReleaseStringChars(env, str, to_release);
    break;
  case 2:
    (*env)->ReleaseStringUTFChars(env, str, (const char*)to_release);
    break;
  case 3:
    (*env)->ReleasePrimitiveArrayCritical(env, ca, to_release, 0);
    break;
  default: fprintf(stderr, "Unexpected release_mode %d\n", source_mode);
    exit(1);
  }

}
