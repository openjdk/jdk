/*
 * Copyright (c) 1996, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <assert.h>
#include <stdlib.h>
#include <string.h>

#include "check_classname.h"
#include "java_lang_ClassLoader.h"
#include "jlong.h"
#include "jni.h"
#include "jni_util.h"
#include "jvm.h"

static JNINativeMethod methods[] = {
    {"retrieveDirectives",  "()Ljava/lang/AssertionStatusDirectives;", (void *)&JVM_AssertionStatusDirectives}
};

JNIEXPORT void JNICALL
Java_java_lang_ClassLoader_registerNatives(JNIEnv *env, jclass cls)
{
    (*env)->RegisterNatives(env, cls, methods,
                            sizeof(methods)/sizeof(JNINativeMethod));
}

/* Convert java string to UTF char*. Use local buffer if possible,
   otherwise malloc new memory. Returns null IFF malloc failed. */
static char*
getUTF(JNIEnv *env, jstring str, char* localBuf, int bufSize)
{
    char* utfStr = NULL;

    int len = (*env)->GetStringUTFLength(env, str);
    int unicode_len = (*env)->GetStringLength(env, str);
    if (len >= bufSize) {
        utfStr = malloc(len + 1);
        if (utfStr == NULL) {
            JNU_ThrowOutOfMemoryError(env, NULL);
            return NULL;
        }
    } else {
        utfStr = localBuf;
    }
    (*env)->GetStringUTFRegion(env, str, 0, unicode_len, utfStr);

    return utfStr;
}

JNIEXPORT jclass JNICALL
Java_java_lang_ClassLoader_defineClass1(JNIEnv *env,
                                        jclass cls,
                                        jobject loader,
                                        jstring name,
                                        jbyteArray data,
                                        jint offset,
                                        jint length,
                                        jobject pd,
                                        jstring source)
{
    jbyte *body;
    char *utfName;
    jclass result = 0;
    char buf[128];
    char* utfSource;
    char sourceBuf[1024];

    if (data == NULL) {
        JNU_ThrowNullPointerException(env, 0);
        return NULL;
    }

    /* Work around 4153825. malloc crashes on Solaris when passed a
     * negative size.
     */
    if (length < 0) {
        JNU_ThrowArrayIndexOutOfBoundsException(env, 0);
        return NULL;
    }

    // On AIX malloc(0) returns NULL which looks like an out-of-memory
    // condition; so adjust it to malloc(1)
    #ifdef _AIX
        body = (jbyte *)malloc(length == 0 ? 1 : length);
    #else
        body = (jbyte *)malloc(length);
    #endif

    if (body == NULL) {
        JNU_ThrowOutOfMemoryError(env, 0);
        return NULL;
    }

    (*env)->GetByteArrayRegion(env, data, offset, length, body);

    if ((*env)->ExceptionCheck(env)) {
        goto free_body;
    }

    if (name != NULL) {
        utfName = getUTF(env, name, buf, sizeof(buf));
        if (utfName == NULL) {
            goto free_body;
        }
        fixClassname(utfName);
    } else {
        utfName = NULL;
    }

    if (source != NULL) {
        utfSource = getUTF(env, source, sourceBuf, sizeof(sourceBuf));
        if (utfSource == NULL) {
            goto free_utfName;
        }
    } else {
        utfSource = NULL;
    }
    result = JVM_DefineClassWithSource(env, utfName, loader, body, length, pd, utfSource);

    if (utfSource && utfSource != sourceBuf)
        free(utfSource);

 free_utfName:
    if (utfName && utfName != buf)
        free(utfName);

 free_body:
    free(body);
    return result;
}

JNIEXPORT jclass JNICALL
Java_java_lang_ClassLoader_defineClass2(JNIEnv *env,
                                        jclass cls,
                                        jobject loader,
                                        jstring name,
                                        jobject data,
                                        jint offset,
                                        jint length,
                                        jobject pd,
                                        jstring source)
{
    jbyte *body;
    char *utfName;
    jclass result = 0;
    char buf[128];
    char* utfSource;
    char sourceBuf[1024];

    assert(data != NULL); // caller fails if data is null.
    assert(length >= 0);  // caller passes ByteBuffer.remaining() for length, so never neg.
    // caller passes ByteBuffer.position() for offset, and capacity() >= position() + remaining()
    assert((*env)->GetDirectBufferCapacity(env, data) >= (offset + length));

    body = (*env)->GetDirectBufferAddress(env, data);

    if (body == NULL) {
        JNU_ThrowNullPointerException(env, 0);
        return NULL;
    }

    body += offset;

    if (name != NULL) {
        utfName = getUTF(env, name, buf, sizeof(buf));
        if (utfName == NULL) {
            return result;
        }
        fixClassname(utfName);
    } else {
        utfName = NULL;
    }

    if (source != NULL) {
        utfSource = getUTF(env, source, sourceBuf, sizeof(sourceBuf));
        if (utfSource == NULL) {
            goto free_utfName;
        }
    } else {
        utfSource = NULL;
    }
    result = JVM_DefineClassWithSource(env, utfName, loader, body, length, pd, utfSource);

    if (utfSource && utfSource != sourceBuf)
        free(utfSource);

 free_utfName:
    if (utfName && utfName != buf)
        free(utfName);

    return result;
}

JNIEXPORT jclass JNICALL
Java_java_lang_ClassLoader_defineClass0(JNIEnv *env,
                                        jclass cls,
                                        jobject loader,
                                        jclass lookup,
                                        jstring name,
                                        jbyteArray data,
                                        jint offset,
                                        jint length,
                                        jobject pd,
                                        jboolean initialize,
                                        jint flags,
                                        jobject classData)
{
    jbyte *body;
    char *utfName;
    jclass result = 0;
    char buf[128];

    if (data == NULL) {
        JNU_ThrowNullPointerException(env, 0);
        return NULL;
    }

    /* Work around 4153825. malloc crashes on Solaris when passed a
     * negative size.
     */
    if (length < 0) {
        JNU_ThrowArrayIndexOutOfBoundsException(env, 0);
        return NULL;
    }

    // On AIX malloc(0) returns NULL which looks like an out-of-memory
    // condition; so adjust it to malloc(1)
    #ifdef _AIX
        body = (jbyte *)malloc(length == 0 ? 1 : length);
    #else
        body = (jbyte *)malloc(length);
    #endif

    if (body == NULL) {
        JNU_ThrowOutOfMemoryError(env, 0);
        return NULL;
    }

    (*env)->GetByteArrayRegion(env, data, offset, length, body);

    if ((*env)->ExceptionCheck(env))
        goto free_body;

    if (name != NULL) {
        utfName = getUTF(env, name, buf, sizeof(buf));
        if (utfName == NULL) {
            goto free_body;
        }
        fixClassname(utfName);
    } else {
        utfName = NULL;
    }

    result = JVM_LookupDefineClass(env, lookup, utfName, body, length, pd, initialize, flags, classData);

    if (utfName && utfName != buf)
        free(utfName);

 free_body:
    free(body);
    return result;
}

/*
 * Returns NULL if class not found.
 */
JNIEXPORT jclass JNICALL
Java_java_lang_ClassLoader_findBootstrapClass(JNIEnv *env, jclass dummy,
                                              jstring classname)
{
    char *clname;
    jclass cls = 0;
    char buf[128];

    if (classname == NULL) {
        return NULL;
    }

    clname = getUTF(env, classname, buf, sizeof(buf));
    if (clname == NULL) {
        return NULL;
    }
    fixClassname(clname);

    if (!verifyClassname(clname, JNI_TRUE)) {  /* expects slashed name */
        goto done;
    }

    cls = JVM_FindClassFromBootLoader(env, clname);

 done:
    if (clname != buf) {
        free(clname);
    }

    return cls;
}

JNIEXPORT jclass JNICALL
Java_java_lang_ClassLoader_findLoadedClass0(JNIEnv *env, jobject loader,
                                           jstring name)
{
    if (name == NULL) {
        return NULL;
    } else {
        return JVM_FindLoadedClass(env, loader, name);
    }
}
