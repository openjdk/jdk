/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include "jni_util.h"
#include "jdk_util.h"

#include "java_lang_Float.h"
#include "java_lang_Double.h"
#include "java_io_ObjectOutputStream.h"

/*
 * Class:     java_io_ObjectOutputStream
 * Method:    floatsToBytes
 * Signature: ([FI[BII)V
 *
 * Convert nfloats float values to their byte representations.  Float values
 * are read from array src starting at offset srcpos and written to array
 * dst starting at offset dstpos.
 */
JNIEXPORT void JNICALL
Java_java_io_ObjectOutputStream_floatsToBytes(JNIEnv *env,
                                              jclass this,
                                              jfloatArray src,
                                              jint srcpos,
                                              jbyteArray dst,
                                              jint dstpos,
                                              jint nfloats)
{
    union {
        int i;
        float f;
    } u;
    jfloat *floats;
    jbyte *bytes;
    jsize srcend;
    jint ival;
    float fval;

    if (nfloats == 0)
        return;

    /* fetch source array */
    if (src == NULL) {
        JNU_ThrowNullPointerException(env, NULL);
        return;
    }
    floats = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (floats == NULL)         /* exception thrown */
        return;

    /* fetch dest array */
    if (dst == NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, src, floats, JNI_ABORT);
        JNU_ThrowNullPointerException(env, NULL);
        return;
    }
    bytes = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (bytes == NULL) {        /* exception thrown */
        (*env)->ReleasePrimitiveArrayCritical(env, src, floats, JNI_ABORT);
        return;
    }

    /* do conversion */
    srcend = srcpos + nfloats;
    for ( ; srcpos < srcend; srcpos++) {
        fval = (float) floats[srcpos];
        if (ISNANF(fval)) {          /* collapse NaNs */
            ival = 0x7fc00000;
        } else {
            u.f = fval;
            ival = (jint) u.i;
        }
        bytes[dstpos++] = (ival >> 24) & 0xFF;
        bytes[dstpos++] = (ival >> 16) & 0xFF;
        bytes[dstpos++] = (ival >> 8) & 0xFF;
        bytes[dstpos++] = (ival >> 0) & 0xFF;
    }

    (*env)->ReleasePrimitiveArrayCritical(env, src, floats, JNI_ABORT);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, bytes, 0);
}

/*
 * Class:     java_io_ObjectOutputStream
 * Method:    doublesToBytes
 * Signature: ([DI[BII)V
 *
 * Convert ndoubles double values to their byte representations.  Double
 * values are read from array src starting at offset srcpos and written to
 * array dst starting at offset dstpos.
 */
JNIEXPORT void JNICALL
Java_java_io_ObjectOutputStream_doublesToBytes(JNIEnv *env,
                                               jclass this,
                                               jdoubleArray src,
                                               jint srcpos,
                                               jbyteArray dst,
                                               jint dstpos,
                                               jint ndoubles)
{
    union {
        jlong l;
        double d;
    } u;
    jdouble *doubles;
    jbyte *bytes;
    jsize srcend;
    jdouble dval;
    jlong lval;

    if (ndoubles == 0)
        return;

    /* fetch source array */
    if (src == NULL) {
        JNU_ThrowNullPointerException(env, NULL);
        return;
    }
    doubles = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (doubles == NULL)                /* exception thrown */
        return;

    /* fetch dest array */
    if (dst == NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, src, doubles, JNI_ABORT);
        JNU_ThrowNullPointerException(env, NULL);
        return;
    }
    bytes = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (bytes == NULL) {        /* exception thrown */
        (*env)->ReleasePrimitiveArrayCritical(env, src, doubles, JNI_ABORT);
        return;
    }

    /* do conversion */
    srcend = srcpos + ndoubles;
    for ( ; srcpos < srcend; srcpos++) {
        dval = doubles[srcpos];
        if (ISNAND((double) dval)) {         /* collapse NaNs */
            lval = jint_to_jlong(0x7ff80000);
            lval = jlong_shl(lval, 32);
        } else {
            jdouble_to_jlong_bits(&dval);
            u.d = (double) dval;
            lval = u.l;
        }
        bytes[dstpos++] = (lval >> 56) & 0xFF;
        bytes[dstpos++] = (lval >> 48) & 0xFF;
        bytes[dstpos++] = (lval >> 40) & 0xFF;
        bytes[dstpos++] = (lval >> 32) & 0xFF;
        bytes[dstpos++] = (lval >> 24) & 0xFF;
        bytes[dstpos++] = (lval >> 16) & 0xFF;
        bytes[dstpos++] = (lval >> 8) & 0xFF;
        bytes[dstpos++] = (lval >> 0) & 0xFF;
    }

    (*env)->ReleasePrimitiveArrayCritical(env, src, doubles, JNI_ABORT);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, bytes, 0);
}
