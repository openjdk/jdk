/*
 * Copyright 2004 - 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
#include <string.h>
#include <windows.h>
#include <locale.h>

#include "jni.h"
#include "jni_util.h"

static void getParent(const TCHAR *path, TCHAR *dest) {
    char* lastSlash = max(strrchr(path, '\\'), strrchr(path, '/'));
    if (lastSlash == NULL) {
        *dest = 0;
        return;
    }
    if (path != dest)
        strcpy(dest, path);
    *lastSlash = 0;
}

BOOL useNativeConverter(JNIEnv *env) {
    static BOOL initialized;
    static BOOL useNative;
    if (!initialized) {
        HMODULE jvm = GetModuleHandle("jvm");
        useNative = FALSE;
        if (jvm != NULL) {
            TCHAR *jvmPath = NULL;
            int bufferSize = MAX_PATH;
            while (jvmPath == NULL) {
                DWORD result;
                jvmPath = malloc(bufferSize);
                if (jvmPath == NULL)
                    return FALSE;
                result = GetModuleFileName(jvm, jvmPath, bufferSize);
                if (result == 0)
                    return FALSE;
                if (result == bufferSize) { // didn't fit
                    bufferSize += MAX_PATH; // increase buffer size, try again
                    free(jvmPath);
                    jvmPath = NULL;
                }
            }

            getParent(jvmPath, jvmPath);
            useNative = (!strcmp("kernel", jvmPath + strlen(jvmPath) -
                    strlen("kernel"))); // true if jvm.dll lives in "kernel"
            if (useNative)
                setlocale(LC_ALL, "");
            free(jvmPath);
        }
        initialized = TRUE;
    }
    return useNative;
}

jstring nativeNewStringPlatform(JNIEnv *env, const char *str) {
    static String_char_constructor = NULL;
    if (useNativeConverter(env)) {
        // use native Unicode conversion so Kernel isn't required during
        // System.initProperties
        jcharArray chars = 0;
        wchar_t *utf16;
        int len;
        jstring result = NULL;

        if (getFastEncoding() == NO_ENCODING_YET)
            initializeEncoding(env);

        len = mbstowcs(NULL, str, strlen(str));
        if (len == -1)
            return NULL;
        utf16 = calloc(len + 1, 2);
        if (mbstowcs(utf16, str, len) == -1)
            return NULL;
        chars = (*env)->NewCharArray(env, len);
        if (chars == NULL)
            return NULL;
        (*env)->SetCharArrayRegion(env, chars, 0, len, utf16);
        if (String_char_constructor == NULL)
            String_char_constructor = (*env)->GetMethodID(env,
                    JNU_ClassString(env), "<init>", "([C)V");
        result = (*env)->NewObject(env, JNU_ClassString(env),
                String_char_constructor, chars);
        free(utf16);
        return result;
    }
    else
        return NULL;
}


char* nativeGetStringPlatformChars(JNIEnv *env, jstring jstr, jboolean *isCopy) {
    if (useNativeConverter(env)) {
        // use native Unicode conversion so Kernel isn't required during
        // System.initProperties
        char *result = NULL;
        size_t len;
        const jchar* utf16 = (*env)->GetStringChars(env, jstr, NULL);
        len = wcstombs(NULL, utf16, (*env)->GetStringLength(env, jstr) * 4) + 1;
        if (len == -1)
            return NULL;
        result = (char*) malloc(len);
        if (result != NULL) {
            if (wcstombs(result, utf16, len) == -1)
                return NULL;
            (*env)->ReleaseStringChars(env, jstr, utf16);
            if (isCopy)
                *isCopy = JNI_TRUE;
        }
        return result;
    }
    else
        return NULL;
}
