/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

#include <jni.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>


JNIEXPORT void JNICALL
Java_NoClassDefFoundErrorTest_callDefineClass(JNIEnv *env, jclass klass, jstring className) {
    const char *c_name = (*env)->GetStringUTFChars(env, className, NULL);
    (*env)->DefineClass(env, c_name, NULL, NULL, 0);
}

JNIEXPORT void JNICALL
Java_NoClassDefFoundErrorTest_callFindClass(JNIEnv *env, jclass klass, jstring className) {
    const char *c_name;
    jclass cls;
    if (className == NULL) {
        c_name = NULL;
    } else {
        c_name = (*env)->GetStringUTFChars(env, className, NULL);
    }
    cls = (*env)->FindClass(env, c_name);
}


static char* giant_string() {
#ifdef _LP64
    size_t len = ((size_t)INT_MAX) + 3;
    char* c_name = malloc(len * sizeof(char));
    if (c_name != NULL) {
        memset(c_name, 'Y', len - 1);
        c_name[len - 1] = '\0';
    }
    return c_name;
#else
    /* On 32-bit, gcc warns us against using values larger than ptrdiff_t for malloc,
     * memset and as array subscript. Since the chance of a 2GB allocation to be
     * successful is slim (would typically reach or exceed the user address space
     * size), lets just not bother. Returning NULL will cause the test to be silently
     * skipped. */
    return NULL;
#endif
}

JNIEXPORT jboolean JNICALL
Java_NoClassDefFoundErrorTest_tryCallDefineClass(JNIEnv *env, jclass klass) {
    char* c_name = giant_string();
    if (c_name != NULL) {
        (*env)->DefineClass(env, c_name, NULL, NULL, 0);
        free(c_name);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_NoClassDefFoundErrorTest_tryCallFindClass(JNIEnv *env, jclass klass) {
    char* c_name = giant_string();
    if (c_name != NULL) {
        jclass cls = (*env)->FindClass(env, c_name);
        free(c_name);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}
