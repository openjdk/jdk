/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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
#include <string.h>
#include "jni.h"
#include "jni_util.h"

#ifdef __APPLE__
#include <crt_externs.h>
#define environ (*_NSGetEnviron())
#endif

JNIEXPORT jobjectArray JNICALL
Java_java_lang_ProcessEnvironment_environ(JNIEnv *env, jclass ign)
{
    /* This is one of the rare times it's more portable to declare an
     * external symbol explicitly, rather than via a system header.
     * The declaration is standardized as part of UNIX98, but there is
     * no standard (not even de-facto) header file where the
     * declaration is to be found.  See:
     * http://www.opengroup.org/onlinepubs/007908799/xbd/envvar.html */
#ifndef __APPLE__
    extern char ** environ; /* environ[i] looks like: VAR=VALUE\0 */
#endif

    jsize count = 0;
    jsize i, j;
    jobjectArray result;
    jclass byteArrCls = (*env)->FindClass(env, "[B");

    for (i = 0; environ[i]; i++) {
        /* Ignore corrupted environment variables */
        if (strchr(environ[i], '=') != NULL)
            count++;
    }

    result = (*env)->NewObjectArray(env, 2*count, byteArrCls, 0);
    if (result == NULL) return NULL;

    for (i = 0, j = 0; environ[i]; i++) {
        const char * varEnd = strchr(environ[i], '=');
        /* Ignore corrupted environment variables */
        if (varEnd != NULL) {
            jbyteArray var, val;
            const char * valBeg = varEnd + 1;
            jsize varLength = varEnd - environ[i];
            jsize valLength = strlen(valBeg);
            var = (*env)->NewByteArray(env, varLength);
            if (var == NULL) return NULL;
            val = (*env)->NewByteArray(env, valLength);
            if (val == NULL) return NULL;
            (*env)->SetByteArrayRegion(env, var, 0, varLength,
                                       (jbyte*) environ[i]);
            (*env)->SetByteArrayRegion(env, val, 0, valLength,
                                       (jbyte*) valBeg);
            (*env)->SetObjectArrayElement(env, result, 2*j  , var);
            (*env)->SetObjectArrayElement(env, result, 2*j+1, val);
            (*env)->DeleteLocalRef(env, var);
            (*env)->DeleteLocalRef(env, val);
            j++;
        }
    }

    return result;
}
