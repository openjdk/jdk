/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "jdk_internal_loader_NativeLibraries.h"

typedef jint (JNICALL *JNI_OnLoad_t)(JavaVM *, void *);
typedef void (JNICALL *JNI_OnUnload_t)(JavaVM *, void *);

static jfieldID handleID;
static jfieldID jniVersionID;
static void *procHandle;

static jboolean initIDs(JNIEnv *env)
{
    if (handleID == 0) {
        jclass nlClz =
            (*env)->FindClass(env, "jdk/internal/loader/NativeLibraries$NativeLibraryImpl");
        if (nlClz == 0)
            return JNI_FALSE;
        handleID = (*env)->GetFieldID(env, nlClz, "handle", "J");
        if (handleID == 0)
            return JNI_FALSE;
        jniVersionID = (*env)->GetFieldID(env, nlClz, "jniVersion", "I");
        if (jniVersionID == 0)
            return JNI_FALSE;
        procHandle = getProcessHandle();
    }
    return JNI_TRUE;
}

/*
 * Support for finding JNI_On(Un)Load_<lib_name> if it exists.
 * If cname == NULL then just find normal JNI_On(Un)Load entry point
 */
static void *findJniFunction(JNIEnv *env, void *handle,
                                    const char *cname, jboolean isLoad) {
    const char *sym;
    void *entryName = NULL;
    char *jniFunctionName;
    size_t len;

    // Check for JNI_On(Un)Load<_libname> function
    sym = isLoad ? "JNI_OnLoad" : "JNI_OnUnload";

    // sym + '_' + cname + '\0'
    if ((len = strlen(sym) + (cname != NULL ? (strlen(cname) + 1) : 0) + 1) >
        FILENAME_MAX) {
        goto done;
    }
    jniFunctionName = malloc(len);
    if (jniFunctionName == NULL) {
        JNU_ThrowOutOfMemoryError(env, NULL);
        goto done;
    }
    strcpy(jniFunctionName, sym);
    if (cname != NULL) {
        strcat(jniFunctionName, "_");
        strcat(jniFunctionName, cname);
    }
    entryName = JVM_FindLibraryEntry(handle, jniFunctionName);
    free(jniFunctionName);

 done:
    return entryName;
}

/*
 * Class:     jdk_internal_loader_NativeLibraries
 * Method:    load
 * Signature: (Ljdk/internal/loader/NativeLibraries/NativeLibraryImpl;Ljava/lang/String;ZZ)Z
 */
JNIEXPORT jboolean JNICALL
Java_jdk_internal_loader_NativeLibraries_load
  (JNIEnv *env, jclass cls, jobject lib, jstring name,
   jboolean isBuiltin, jboolean throwExceptionIfFail)
{
    const char *cname;
    jint jniVersion;
    jthrowable cause;
    void * handle;
    jboolean loaded = JNI_FALSE;

    if (!initIDs(env))
        return JNI_FALSE;

    cname = JNU_GetStringPlatformChars(env, name, 0);
    if (cname == 0)
        return JNI_FALSE;
    handle = isBuiltin ? procHandle : JVM_LoadLibrary(cname, throwExceptionIfFail);
    if (handle) {
        JNI_OnLoad_t JNI_OnLoad;
        JNI_OnLoad = (JNI_OnLoad_t)findJniFunction(env, handle,
                                                   isBuiltin ? cname : NULL,
                                                   JNI_TRUE);
        if (JNI_OnLoad) {
            JavaVM *jvm;
            (*env)->GetJavaVM(env, &jvm);
            jniVersion = (*JNI_OnLoad)(jvm, NULL);
        } else {
            jniVersion = 0x00010001;
        }

        cause = (*env)->ExceptionOccurred(env);
        if (cause) {
            (*env)->ExceptionClear(env);
            (*env)->Throw(env, cause);
            if (!isBuiltin) {
                JVM_UnloadLibrary(handle);
            }
            goto done;
        }

        if (!JVM_IsSupportedJNIVersion(jniVersion) ||
            (isBuiltin && jniVersion < JNI_VERSION_1_8)) {
            char msg[256];
            jio_snprintf(msg, sizeof(msg),
                         "unsupported JNI version 0x%08X required by %s",
                         jniVersion, cname);
            JNU_ThrowByName(env, "java/lang/UnsatisfiedLinkError", msg);
            if (!isBuiltin) {
                JVM_UnloadLibrary(handle);
            }
            goto done;
        }
        (*env)->SetIntField(env, lib, jniVersionID, jniVersion);
    } else {
        cause = (*env)->ExceptionOccurred(env);
        if (cause) {
            (*env)->ExceptionClear(env);
            (*env)->SetLongField(env, lib, handleID, (jlong)0);
            (*env)->Throw(env, cause);
        }
        goto done;
    }

    (*env)->SetLongField(env, lib, handleID, ptr_to_jlong(handle));
    loaded = JNI_TRUE;

 done:
    JNU_ReleaseStringPlatformChars(env, name, cname);
    return loaded;
}

/*
 * Class:     jdk_internal_loader_NativeLibraries
 * Method:    unload
 * Signature: (Ljava/lang/String;ZJ)V
 */
JNIEXPORT void JNICALL
Java_jdk_internal_loader_NativeLibraries_unload
(JNIEnv *env, jclass cls, jstring name, jboolean isBuiltin, jlong address)
{
    void *handle;
    JNI_OnUnload_t JNI_OnUnload;
    const char *cname;

    if (!initIDs(env))
        return;
    cname = JNU_GetStringPlatformChars(env, name, 0);
    if (cname == NULL) {
        return;
    }
    handle = jlong_to_ptr(address);

    JNI_OnUnload = (JNI_OnUnload_t )findJniFunction(env, handle,
                                                    isBuiltin ? cname : NULL,
                                                    JNI_FALSE);
    if (JNI_OnUnload) {
        JavaVM *jvm;
        (*env)->GetJavaVM(env, &jvm);
        (*JNI_OnUnload)(jvm, NULL);
    }
    if (!isBuiltin) {
        JVM_UnloadLibrary(handle);
    }
    JNU_ReleaseStringPlatformChars(env, name, cname);
}

/*
 * Class:     jdk_internal_loader_NativeLibrary
 * Method:    findEntry0
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL
Java_jdk_internal_loader_NativeLibrary_findEntry0
  (JNIEnv *env, jclass cls, jlong handle, jstring name)
{
    const char *cname;
    jlong res;

    cname = (*env)->GetStringUTFChars(env, name, 0);
    if (cname == 0)
        return jlong_zero;
    res = ptr_to_jlong(JVM_FindLibraryEntry(jlong_to_ptr(handle), cname));
    (*env)->ReleaseStringUTFChars(env, name, cname);
    return res;
}

/*
 * Class:     jdk_internal_loader_NativeLibraries
 * Method:    findBuiltinLib
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_jdk_internal_loader_NativeLibraries_findBuiltinLib
  (JNIEnv *env, jclass cls, jstring name)
{
    const char *cname;
    char *libName;
    size_t prefixLen = strlen(JNI_LIB_PREFIX);
    size_t suffixLen = strlen(JNI_LIB_SUFFIX);
    size_t len;
    jstring lib;
    void *ret;

    if (name == NULL) {
        JNU_ThrowInternalError(env, "NULL filename for native library");
        return NULL;
    }
    procHandle = getProcessHandle();
    cname = JNU_GetStringPlatformChars(env, name, 0);
    if (cname == NULL) {
        return NULL;
    }
    // Copy name Skipping PREFIX
    len = strlen(cname);
    if (len <= (prefixLen+suffixLen)) {
        JNU_ReleaseStringPlatformChars(env, name, cname);
        return NULL;
    }
    libName = malloc(len + 1); //+1 for null if prefix+suffix == 0
    if (libName == NULL) {
        JNU_ReleaseStringPlatformChars(env, name, cname);
        JNU_ThrowOutOfMemoryError(env, NULL);
        return NULL;
    }
    if (len > prefixLen) {
        strcpy(libName, cname+prefixLen);
    }
    JNU_ReleaseStringPlatformChars(env, name, cname);

    // Strip SUFFIX
    libName[strlen(libName)-suffixLen] = '\0';

    // Check for JNI_OnLoad_libname function
    ret = findJniFunction(env, procHandle, libName, JNI_TRUE);
    if (ret != NULL) {
        lib = JNU_NewStringPlatform(env, libName);
        free(libName);
        return lib;
    }
    free(libName);
    return NULL;
}
