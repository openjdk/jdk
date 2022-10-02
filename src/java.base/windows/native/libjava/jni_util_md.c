/*
 * Copyright (c) 2004, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include <windows.h>
#include <locale.h>

#include "jvm.h"
#include "jni.h"
#include "jni_util.h"

/*
 * Convenience method.
 * IO on Windows uses the Windows API.
 */
JNIEXPORT void JNICALL
JNU_ThrowIOExceptionWithIOError(JNIEnv *env, const char *defaultDetail) {
    char buf[256];
    size_t n = getLastWinErrorString(buf, 256);

    if (n > 0) {
        jstring s = JNU_NewStringPlatform(env, buf);
        if (s != NULL) {
            jobject x = JNU_NewObjectByName(env, "java/io/IOException",
                                            "(Ljava/lang/String;)V", s);
            if (x != NULL) {
                (*env)->Throw(env, x);
            }
        }
    }
    if (!(*env)->ExceptionOccurred(env)) {
        JNU_ThrowByName(env, "java/io/IOException", defaultDetail);
    }
}

void* getProcessHandle() {
    return (void*)GetModuleHandle(NULL);
}

/*
 * Windows symbols can be simple like JNI_OnLoad or __stdcall format
 * like _JNI_OnLoad@8. We need to handle both.
 */
void buildJniFunctionName(const char *sym, const char *cname,
                          char *jniEntryName) {
    if (cname != NULL) {
        char *p = strrchr(sym, '@');
        if (p != NULL && p != sym) {
            // sym == _JNI_OnLoad@8
            strncpy(jniEntryName, sym, (p - sym));
            jniEntryName[(p-sym)] = '\0';
            // jniEntryName == _JNI_OnLoad
            strcat(jniEntryName, "_");
            strcat(jniEntryName, cname);
            strcat(jniEntryName, p);
            //jniEntryName == _JNI_OnLoad_cname@8
        } else {
            strcpy(jniEntryName, sym);
            strcat(jniEntryName, "_");
            strcat(jniEntryName, cname);
        }
    } else {
        strcpy(jniEntryName, sym);
    }
    return;
}

JNIEXPORT size_t JNICALL
getLastWinErrorString(char *buffer, size_t size) {

    DWORD error;

    if ((error = GetLastError()) != 0) {
        /* DOS error */
        size_t n = (size_t) FormatMessage(
                FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                NULL,
                error,
                0,
                buffer,
                (DWORD) size,
                NULL);
        if (n > 3) {
            /* Drop final '.', CR, LF */
            if (buffer[n - 1] == '\n') n--;
            if (buffer[n - 1] == '\r') n--;
            if (buffer[n - 1] == '.') n--;
            buffer[n] = '\0';
        }
        return n;
    } else {
        return 0;
    }
}

JNIEXPORT void JNICALL
throwByNameWithMessageAndWinError
  (JNIEnv *env, const char *name, const char *message) {

    char buf[256];
    size_t n = getLastWinErrorString(buf, sizeof(buf));
    size_t messagelen = message == NULL ? 0 : strlen(message);

    if (n > 0) {
        jstring s = JNU_NewStringPlatform(env, buf);
        if (s != NULL) {
            jobject x = NULL;
            if (messagelen) {
                jstring s2 = NULL;
                size_t messageextlen = messagelen + 4;
                char *str1 = (char *)malloc((messageextlen) * sizeof(char));
                if (str1 == 0) {
                    JNU_ThrowOutOfMemoryError(env, 0);
                    return;
                }
                jio_snprintf(str1, messageextlen, " (%s)", message);
                s2 = (*env)->NewStringUTF(env, str1);
                free(str1);
                JNU_CHECK_EXCEPTION(env);
                if (s2 != NULL) {
                    jstring s3 = JNU_CallMethodByName(
                                     env, NULL, s, "concat",
                                     "(Ljava/lang/String;)Ljava/lang/String;",
                                     s2).l;
                    (*env)->DeleteLocalRef(env, s2);
                    JNU_CHECK_EXCEPTION(env);
                    if (s3 != NULL) {
                        (*env)->DeleteLocalRef(env, s);
                        s = s3;
                    }
                }
            }
            x = JNU_NewObjectByName(env, name, "(Ljava/lang/String;)V", s);
            if (x != NULL) {
                (*env)->Throw(env, x);
            }
        }
    }

    if (!(*env)->ExceptionOccurred(env)) {
        if (messagelen) {
            JNU_ThrowByName(env, name, message);
        } else {
            JNU_ThrowByName(env, name, "no further information");
        }
    }
}

JNIEXPORT void JNICALL
throwByNameWithWinError(JNIEnv *env, const char *name, const char *defaultDetail) {
    char buf[256];
    size_t n = getLastWinErrorString(buf, sizeof(buf));

    if (n > 0) {
        jstring s = JNU_NewStringPlatform(env, buf);
        if (s != NULL) {
            jobject x = JNU_NewObjectByName(env, name,
                                            "(Ljava/lang/String;)V", s);
            if (x != NULL) {
                (*env)->Throw(env, x);
            }
        }
    }
    if (!(*env)->ExceptionOccurred(env)) {
        JNU_ThrowByName(env, name, defaultDetail);
    }
}
