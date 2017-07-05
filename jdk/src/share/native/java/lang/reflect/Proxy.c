/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include "jni_util.h"

#include "java_lang_reflect_Proxy.h"

/* defined in libverify.so/verify.dll (src file common/check_format.c) */
extern jboolean VerifyFixClassname(char *utf_name);

/*
 * Class:     java_lang_reflect_Proxy
 * Method:    defineClass0
 * Signature: (Ljava/lang/ClassLoader;Ljava/lang/String;[BII)Ljava/lang/Class;
 *
 * The implementation of this native static method is a copy of that of
 * the native instance method Java_java_lang_ClassLoader_defineClass0()
 * with the implicit "this" parameter becoming the "loader" parameter.
 */
JNIEXPORT jclass JNICALL
Java_java_lang_reflect_Proxy_defineClass0(JNIEnv *env,
                                          jclass ignore,
                                          jobject loader,
                                          jstring name,
                                          jbyteArray data,
                                          jint offset,
                                          jint length)
{
    jbyte *body;
    char *utfName;
    jclass result = 0;
    char buf[128];

    if (data == NULL) {
        JNU_ThrowNullPointerException(env, 0);
        return 0;
    }

    /* Work around 4153825. malloc crashes on Solaris when passed a
     * negative size.
     */
    if (length < 0) {
        JNU_ThrowArrayIndexOutOfBoundsException(env, 0);
        return 0;
    }

    body = (jbyte *)malloc(length);

    if (body == 0) {
        JNU_ThrowOutOfMemoryError(env, 0);
        return 0;
    }

    (*env)->GetByteArrayRegion(env, data, offset, length, body);

    if ((*env)->ExceptionOccurred(env))
        goto free_body;

    if (name != NULL) {
        jsize len = (*env)->GetStringUTFLength(env, name);
        jsize unicode_len = (*env)->GetStringLength(env, name);
        if (len >= (jsize)sizeof(buf)) {
            utfName = malloc(len + 1);
            if (utfName == NULL) {
                JNU_ThrowOutOfMemoryError(env, NULL);
                goto free_body;
            }
        } else {
            utfName = buf;
        }
        (*env)->GetStringUTFRegion(env, name, 0, unicode_len, utfName);
        VerifyFixClassname(utfName);
    } else {
        utfName = NULL;
    }

    result = (*env)->DefineClass(env, utfName, loader, body, length);

    if (utfName && utfName != buf)
        free(utfName);

 free_body:
    free(body);
    return result;
}
