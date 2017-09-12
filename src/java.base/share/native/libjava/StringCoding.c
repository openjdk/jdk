/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <jni.h>
#include <jni_util.h>
#include <stdio.h>
#include <jvm.h>

#include "java_lang_StringCoding.h"

static void
printToFile(JNIEnv *env, jstring s, FILE *file)
{
    char *sConverted;
    int length = 0;
    int i;
    const jchar *sAsArray;

    if (s == NULL) {
        JNU_ThrowNullPointerException(env, NULL);
        return;
    }

    sAsArray = (*env)->GetStringChars(env, s, NULL);
    if (!sAsArray)
        return;
    length = (*env)->GetStringLength(env, s);
    if (length == 0) {
        (*env)->ReleaseStringChars(env, s, sAsArray);
        return;
    }
    sConverted = (char *) malloc(length + 1);
    if (!sConverted) {
        (*env)->ReleaseStringChars(env, s, sAsArray);
        JNU_ThrowOutOfMemoryError(env, NULL);
        return;
    }

    for(i = 0; i < length; i++) {
        sConverted[i] = (0x7f & sAsArray[i]);
    }
    sConverted[length] = '\0';
    jio_fprintf(file, "%s", sConverted);
    (*env)->ReleaseStringChars(env, s, sAsArray);
    free(sConverted);
}

JNIEXPORT void JNICALL
Java_java_lang_StringCoding_err(JNIEnv *env, jclass cls, jstring s)
{
    printToFile(env, s, stderr);
}
