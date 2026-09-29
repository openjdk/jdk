/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Rivos Inc. All rights reserved.
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

#ifdef riscv64
/* RV64 ABI pass all integers as 64-bit, in registers or on stack
 * As compiler may choose to load smaller width than 64-bit if passed on stack,
 * this test may not find any bugs.
 * Therefore we trick the compiler todo 64-bit loads,
 * by saying these args are jlongs.
 */
JNIEXPORT jint JNICALL Java_compiler_calls_TestManyArgs_checkargs(JNIEnv* env, jclass jclazz,
                                                                  jlong arg0, jlong arg1, jlong arg2,
                                                                  jlong arg3, jlong arg4, jlong arg5,
                                                                  jlong arg6, jlong arg7, jlong arg8,
                                                                  jlong arg9, jlong arg10, jlong arg11)
#else
JNIEXPORT jint JNICALL Java_compiler_calls_TestManyArgs_checkargs(JNIEnv* env, jclass jclazz,
                                                                  jint arg0, jshort arg1, jbyte arg2,
                                                                  jint arg3, jshort arg4, jbyte arg5,
                                                                  jint arg6, jshort arg7, jbyte arg8,
                                                                  jint arg9, jshort arg10, jbyte arg11)
#endif
{
    if (arg0 != 0xf) return 1;
    if (arg1 != 0xf) return 1;
    if (arg2 != 0xf) return 1;
    if (arg3 != 0xf) return 1;
    if (arg4 != 0xf) return 1;
    if (arg5 != 0xf) return 1;
    if (arg6 != 0xf) return 1;
    if (arg7 != 0xf) return 1;
    if (arg8 != 0xf) return 1;
    if (arg9 != 0xf) return 1;
    if (arg10 != 0xf) return 1;
    if (arg11 != 0xf) return 1;
    return 0;
}

JNIEXPORT
void JNICALL Java_compiler_calls_TestManyArgs_scramblestack(JNIEnv* env, jclass jclazz)
{
    volatile char stack[12*8];
    for (unsigned int i = 0; i < sizeof(stack); i++) {
        stack[i] = (char)0xff;
    }
}
