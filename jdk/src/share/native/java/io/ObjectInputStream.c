/*
 * Copyright (c) 1996, 2000, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
#include "jvm.h"
#include "jni_util.h"
#include "jlong.h"

#include "java_lang_Float.h"
#include "java_lang_Double.h"
#include "java_io_ObjectInputStream.h"


/*
 * Class:     java_io_ObjectInputStream
 * Method:    bytesToFloats
 * Signature: ([BI[FII)V
 *
 * Reconstitutes nfloats float values from their byte representations.  Byte
 * values are read from array src starting at offset srcpos; the resulting
 * float values are written to array dst starting at dstpos.
 */
JNIEXPORT void JNICALL
Java_java_io_ObjectInputStream_bytesToFloats(JNIEnv *env,
                                             jclass this,
                                             jbyteArray src,
                                             jint srcpos,
                                             jfloatArray dst,
                                             jint dstpos,
                                             jint nfloats)
{
    union {
        int i;
        float f;
    } u;
    jfloat *floats;
    jbyte *bytes;
    jsize dstend;
    jint ival;

    if (nfloats == 0)
        return;

    /* fetch source array */
    if (src == NULL) {
        JNU_ThrowNullPointerException(env, NULL);
        return;
    }
    bytes = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (bytes == NULL)          /* exception thrown */
        return;

    /* fetch dest array */
    if (dst == NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, src, bytes, JNI_ABORT);
        JNU_ThrowNullPointerException(env, NULL);
        return;
    }
    floats = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (floats == NULL) {       /* exception thrown */
        (*env)->ReleasePrimitiveArrayCritical(env, src, bytes, JNI_ABORT);
        return;
    }

    /* do conversion */
    dstend = dstpos + nfloats;
    for ( ; dstpos < dstend; dstpos++) {
        ival = ((bytes[srcpos + 0] & 0xFF) << 24) +
               ((bytes[srcpos + 1] & 0xFF) << 16) +
               ((bytes[srcpos + 2] & 0xFF) << 8) +
               ((bytes[srcpos + 3] & 0xFF) << 0);
        u.i = (long) ival;
        floats[dstpos] = (jfloat) u.f;
        srcpos += 4;
    }

    (*env)->ReleasePrimitiveArrayCritical(env, src, bytes, JNI_ABORT);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, floats, 0);
}

/*
 * Class:     java_io_ObjectInputStream
 * Method:    bytesToDoubles
 * Signature: ([BI[DII)V
 *
 * Reconstitutes ndoubles double values from their byte representations.
 * Byte values are read from array src starting at offset srcpos; the
 * resulting double values are written to array dst starting at dstpos.
 */
JNIEXPORT void JNICALL
Java_java_io_ObjectInputStream_bytesToDoubles(JNIEnv *env,
                                              jclass this,
                                              jbyteArray src,
                                              jint srcpos,
                                              jdoubleArray dst,
                                              jint dstpos,
                                              jint ndoubles)

{
    union {
        jlong l;
        double d;
    } u;
    jdouble *doubles;
    jbyte *bytes;
    jsize dstend;
    jlong lval;

    if (ndoubles == 0)
        return;

    /* fetch source array */
    if (src == NULL) {
        JNU_ThrowNullPointerException(env, NULL);
        return;
    }
    bytes = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (bytes == NULL)          /* exception thrown */
        return;

    /* fetch dest array */
    if (dst == NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, src, bytes, JNI_ABORT);
        JNU_ThrowNullPointerException(env, NULL);
        return;
    }
    doubles = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (doubles == NULL) {      /* exception thrown */
        (*env)->ReleasePrimitiveArrayCritical(env, src, bytes, JNI_ABORT);
        return;
    }

    /* do conversion */
    dstend = dstpos + ndoubles;
    for ( ; dstpos < dstend; dstpos++) {
        lval = (((jlong) bytes[srcpos + 0] & 0xFF) << 56) +
               (((jlong) bytes[srcpos + 1] & 0xFF) << 48) +
               (((jlong) bytes[srcpos + 2] & 0xFF) << 40) +
               (((jlong) bytes[srcpos + 3] & 0xFF) << 32) +
               (((jlong) bytes[srcpos + 4] & 0xFF) << 24) +
               (((jlong) bytes[srcpos + 5] & 0xFF) << 16) +
               (((jlong) bytes[srcpos + 6] & 0xFF) << 8) +
               (((jlong) bytes[srcpos + 7] & 0xFF) << 0);
        jlong_to_jdouble_bits(&lval);
        u.l = lval;
        doubles[dstpos] = (jdouble) u.d;
        srcpos += 8;
    }

    (*env)->ReleasePrimitiveArrayCritical(env, src, bytes, JNI_ABORT);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, doubles, 0);
}

/*
 * Class:     java_io_ObjectInputStream
 * Method:    latestUserDefinedLoader
 * Signature: ()Ljava/lang/ClassLoader;
 *
 * Returns the first non-null class loader up the execution stack, or null
 * if only code from the null class loader is on the stack.
 */
JNIEXPORT jobject JNICALL
Java_java_io_ObjectInputStream_latestUserDefinedLoader(JNIEnv *env, jclass cls)
{
    return JVM_LatestUserDefinedLoader(env);
}
