/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include <assert.h>
#include <string.h>

#include "jni.h"
#include "jni_util.h"
#include "jlong.h"
#include "jvm.h"
#include "jdk_internal_loader_RawNativeLibraries.h"

static jfieldID handleID;

static jboolean initIDs(JNIEnv *env)
{
    if (handleID == 0) {
        jclass rnlClz =
            (*env)->FindClass(env, "jdk/internal/loader/RawNativeLibraries$RawNativeLibraryImpl");
        if (rnlClz == 0)
            return JNI_FALSE;
        handleID = (*env)->GetFieldID(env, rnlClz, "handle", "J");
        if (handleID == 0)
            return JNI_FALSE;
    }
    return JNI_TRUE;
}

/*
 * Class:     jdk_internal_loader_RawNativeLibraries
 * Method:    Java_jdk_internal_loader_RawNativeLibraries_load0
 * Signature: (Ljdk/internal/loader/RawNativeLibraries/RawNativeLibraryImpl;Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_jdk_internal_loader_RawNativeLibraries_load0
  (JNIEnv *env, jclass cls, jobject lib, jstring name)
{
    const char *cname;
    void * handle;

    if (!initIDs(env))
        return JNI_FALSE;

    cname = JNU_GetStringPlatformChars(env, name, 0);
    if (cname == 0)
        return JNI_FALSE;
    handle = JVM_LoadLibrary(cname, JNI_FALSE);
    (*env)->SetLongField(env, lib, handleID, ptr_to_jlong(handle));

    JNU_ReleaseStringPlatformChars(env, name, cname);
    return handle != 0L;
}

/*
 * Class:     jdk_internal_loader_RawNativeLibraries
 * Method:    unload0
 * Signature: (Ljava/lang/String;J)V
 */
JNIEXPORT void JNICALL Java_jdk_internal_loader_RawNativeLibraries_unload0
  (JNIEnv *env, jclass cls, jstring name, jlong address)
{
    void *handle;
    const char *cname;

    if (!initIDs(env))
        return;
    cname = JNU_GetStringPlatformChars(env, name, 0);
    if (cname == NULL) {
        return;
    }
    handle = jlong_to_ptr(address);

    JVM_UnloadLibrary(handle);
    JNU_ReleaseStringPlatformChars(env, name, cname);
}

