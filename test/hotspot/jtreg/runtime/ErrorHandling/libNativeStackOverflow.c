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

#include <jni.h>

#if defined(__clang_major__)
  #pragma clang optimize off
  #pragma GCC diagnostic ignored "-Winfinite-recursion"
#elif defined(__GNUC__)
  #pragma GCC optimize ("O0")
  #if (__GNUC__ >= 12)
    #pragma GCC diagnostic ignored "-Winfinite-recursion"
  #endif
#elif defined (__xlC__)
  #pragma option_override(function_name, "opt(level, 0)")
#endif

/*
 * Class:     NativeStackOverflowTest_Crasher
 * Method:    crash
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_NativeStackOverflowTest_00024Crasher_crash
  (JNIEnv* env, jclass cls) {
  Java_NativeStackOverflowTest_00024Crasher_crash(env, cls);
}
