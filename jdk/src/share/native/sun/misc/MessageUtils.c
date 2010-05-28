/*
 * Copyright (c) 1998, Oracle and/or its affiliates. All rights reserved.
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
#include <jlong.h>
#include <stdio.h>
#include <jvm.h>

#include "sun_misc_MessageUtils.h"

static void
printToFile(JNIEnv *env, jstring s, FILE *file)
{
    char *sConverted;
    int length;
    int i;
    const jchar *sAsArray;

    if (s == NULL) {
      s = (*env)->NewStringUTF(env, "null");
      if (s == NULL) return;
    }

    sAsArray = (*env)->GetStringChars(env, s, NULL);
    length = (*env)->GetStringLength(env, s);
    sConverted = (char *) malloc(length + 1);
    for(i = 0; i < length; i++) {
        sConverted[i] = (0x7f & sAsArray[i]);
    }
    sConverted[length] = '\0';
    jio_fprintf(file, "%s", sConverted);
    (*env)->ReleaseStringChars(env, s, sAsArray);
    free(sConverted);
}

JNIEXPORT void JNICALL
Java_sun_misc_MessageUtils_toStderr(JNIEnv *env, jclass cls, jstring s)
{
    printToFile(env, s, stderr);
}

JNIEXPORT void JNICALL
Java_sun_misc_MessageUtils_toStdout(JNIEnv *env, jclass cls, jstring s)
{
    printToFile(env, s, stdout);
}
