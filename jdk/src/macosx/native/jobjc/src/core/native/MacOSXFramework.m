/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

#include "com_apple_jobjc_MacOSXFramework.h"

#include <dlfcn.h>
#include <JavaNativeFoundation/JavaNativeFoundation.h>

/*
 * Class:     com_apple_jobjc_MacOSXFramework
 * Method:    retainFramework
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_com_apple_jobjc_MacOSXFramework_retainFramework
(JNIEnv *env, jclass clazz, jstring frameworkName)
{
    if (frameworkName == NULL) return ptr_to_jlong(NULL);
    const char *frameworkNameCStr = (*env)->GetStringUTFChars(env, frameworkName, JNI_FALSE);
    const void *library = dlopen(frameworkNameCStr, RTLD_LOCAL);
    (*env)->ReleaseStringUTFChars(env, frameworkName, frameworkNameCStr);
    return ptr_to_jlong(library);
}

/*
 * Class:     com_apple_jobjc_MacOSXFramework
 * Method:    releaseFramework
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_apple_jobjc_MacOSXFramework_releaseFramework
(JNIEnv *env, jclass clazz, jlong frameworkPtr)
{
    dlclose(jlong_to_ptr(frameworkPtr));
}

JNIEXPORT void JNICALL Java_com_apple_jobjc_MacOSXFramework_getConstant
(JNIEnv *env, jclass clazz, jlong frameworkPtr, jstring constSymbol, jlong retBuffer, jint size)
{
    const char *symbol = (*env)->GetStringUTFChars(env, constSymbol, JNI_FALSE);
    void *handle = frameworkPtr ? jlong_to_ptr(frameworkPtr) : RTLD_DEFAULT;
    void *data = dlsym(handle, symbol);
    (*env)->ReleaseStringUTFChars(env, constSymbol, symbol);

    if(!data)
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/RuntimeException"), dlerror());
    else
        memcpy(jlong_to_ptr(retBuffer), data, (size_t) size);
}
