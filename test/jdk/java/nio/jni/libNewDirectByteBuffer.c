/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include <stdlib.h>
#include "jni.h"

// private static native ByteBuffer newDirectByteBuffer(long size)
JNIEXPORT jobject JNICALL
Java_NewDirectByteBuffer_newDirectByteBuffer
    (JNIEnv *env, jclass cls, jlong size)
{
    // Allocate memory, on failure throwing an OOME or returning NULL
    // if throwing the OOME fails
    void* addr = malloc(size);
    if (addr == NULL) {
        jclass rtExCls = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
        if ((*env)->ThrowNew(env, rtExCls, "malloc failed") < 0) {
            return NULL;
        }
    }

    // Create the direct byte buffer, freeing the native memory if an exception
    // is thrown while constructing the buffer
    jobject dbb = (*env)->NewDirectByteBuffer(env, addr, size);
    if ((*env)->ExceptionOccurred(env) != NULL) {
        free(addr);
    }
    return dbb;
}

// private static native long getDirectBufferCapacity(ByteBuffer buf)
JNIEXPORT jlong JNICALL
Java_NewDirectByteBuffer_getDirectBufferCapacity
    (JNIEnv *env, jclass cls, jobject buf)
{
    return (*env)->GetDirectBufferCapacity(env, buf);
}

// private static native void freeDirectBufferMemory(ByteBuffer buf)
JNIEXPORT void JNICALL
Java_NewDirectByteBuffer_freeDirectBufferMemory
    (JNIEnv *env, jclass cls, jobject buf)
{
    void* addr = (*env)->GetDirectBufferAddress(env, buf);
    free(addr);
}
