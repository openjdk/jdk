/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
#include <stdlib.h>

#include "jni.h"
#include "jni_util.h"
#include <windows.h>

static jstring
environmentBlock9x(JNIEnv *env)
{
    int i;
    jmethodID String_init_ID =
        (*env)->GetMethodID(env, JNU_ClassString(env), "<init>", "([B)V");
    jbyteArray bytes;
    jbyte *blockA = (jbyte *) GetEnvironmentStringsA();
    if (blockA == NULL) {
        /* Both GetEnvironmentStringsW and GetEnvironmentStringsA
         * failed.  Out of memory is our best guess.  */
        JNU_ThrowOutOfMemoryError(env, "GetEnvironmentStrings failed");
        return NULL;
    }

    /* Don't search for "\0\0", since an empty environment block may
       legitimately consist of a single "\0". */
    for (i = 0; blockA[i];)
        while (blockA[i++])
            ;

    if ((bytes = (*env)->NewByteArray(env, i)) == NULL) return NULL;
    (*env)->SetByteArrayRegion(env, bytes, 0, i, blockA);
    FreeEnvironmentStringsA(blockA);
    return (*env)->NewObject(env, JNU_ClassString(env),
                             String_init_ID, bytes);
}

/* Returns a Windows style environment block, discarding final trailing NUL */
JNIEXPORT jstring JNICALL
Java_java_lang_ProcessEnvironment_environmentBlock(JNIEnv *env, jclass klass)
{
    int i;
    jstring envblock;
    jchar *blockW = (jchar *) GetEnvironmentStringsW();
    if (blockW == NULL)
        return environmentBlock9x(env);

    /* Don't search for "\u0000\u0000", since an empty environment
       block may legitimately consist of a single "\u0000".  */
    for (i = 0; blockW[i];)
        while (blockW[i++])
            ;

    envblock = (*env)->NewString(env, blockW, i);
    FreeEnvironmentStringsW(blockW);
    return envblock;
}
