/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <limits.h>
#include <stdio.h>

JNIEXPORT void JNICALL
Java_TestLargeUTF8Length_checkUTF8Length(JNIEnv *env, jclass clz,
                                         jstring str, jlong expected_length) {

  jlong utf8_length;

  // First get truncated length to generate warning
  utf8_length = (*env)->GetStringUTFLength(env, str);

  if (utf8_length != INT_MAX - 1) {
    printf("Error: expected length of %d, but got %lld\n", INT_MAX - 1,
           (long long) utf8_length);
    (*env)->FatalError(env, "Unexpected truncated length");
  }

  // Now get true length
  utf8_length = (*env)->GetStringUTFLengthAsLong(env, str);

  if (utf8_length != expected_length ) {
    printf("Error: expected length of %lld, but got %lld\n",
           (long long) expected_length, (long long) utf8_length);
    (*env)->FatalError(env, "Unexpected true length");
  }


}
