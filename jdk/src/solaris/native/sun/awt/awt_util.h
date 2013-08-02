/*
 * Copyright (c) 1995, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _AWT_UTIL_H_
#define _AWT_UTIL_H_

#ifndef HEADLESS
#include "gdefs.h"

/*
 * Expected types of arguments of the macro.
 * (JNIEnv*, const char*, const char*, jboolean, jobject)
 */
#define WITH_XERROR_HANDLER(env, handlerClassName, getInstanceSignature,                          \
                            handlerHasFlag, handlerRef) do {                                      \
    handlerRef = JNU_CallStaticMethodByName(env, NULL, handlerClassName, "getInstance",           \
        getInstanceSignature).l;                                                                  \
    if (handlerHasFlag == JNI_TRUE) {                                                             \
        JNU_CallMethodByName(env, NULL, handlerRef, "setErrorOccurredFlag", "(Z)V", JNI_FALSE);   \
    }                                                                                             \
    JNU_CallStaticMethodByName(env, NULL, "sun/awt/X11/XErrorHandlerUtil", "WITH_XERROR_HANDLER", \
        "(Lsun/awt/X11/XErrorHandler;)V", handlerRef);                                            \
} while (0)

/*
 * Expected types of arguments of the macro.
 * (JNIEnv*, jboolean)
 */
#define RESTORE_XERROR_HANDLER(env, doXSync) do {                                                 \
    JNU_CallStaticMethodByName(env, NULL, "sun/awt/X11/XErrorHandlerUtil",                        \
        "RESTORE_XERROR_HANDLER", "(Z)V", doXSync);                                               \
} while (0)

/*
 * Expected types of arguments of the macro.
 * (JNIEnv*, const char*, const char*, jboolean, jobject, jboolean, No type - C expression)
 */
#define EXEC_WITH_XERROR_HANDLER(env, handlerClassName, getInstanceSignature, handlerHasFlag,     \
                                 handlerRef, errorOccurredFlag, code) do {                        \
    handlerRef = NULL;                                                                            \
    WITH_XERROR_HANDLER(env, handlerClassName, getInstanceSignature, handlerHasFlag, handlerRef); \
    do {                                                                                          \
        code;                                                                                     \
    } while (0);                                                                                  \
    RESTORE_XERROR_HANDLER(env, JNI_TRUE);                                                        \
    if (handlerHasFlag == JNI_TRUE) {                                                             \
        GET_HANDLER_ERROR_OCCURRED_FLAG(env, handlerRef, errorOccurredFlag);                      \
    }                                                                                             \
} while (0)

/*
 * Expected types of arguments of the macro.
 * (JNIEnv*, jobject, jboolean)
 */
#define GET_HANDLER_ERROR_OCCURRED_FLAG(env, handlerRef, errorOccurredFlag) do {                  \
    if (handlerRef != NULL) {                                                                     \
        errorOccurredFlag = JNU_CallMethodByName(env, NULL, handlerRef, "getErrorOccurredFlag",   \
            "()Z").z;                                                                             \
    }                                                                                             \
} while (0)
#endif /* !HEADLESS */

#ifndef INTERSECTS
#define INTERSECTS(r1_x1,r1_x2,r1_y1,r1_y2,r2_x1,r2_x2,r2_y1,r2_y2) \
!((r2_x2 <= r1_x1) ||\
  (r2_y2 <= r1_y1) ||\
  (r2_x1 >= r1_x2) ||\
  (r2_y1 >= r1_y2))
#endif

#ifndef MIN
#define MIN(a,b) ((a) < (b) ? (a) : (b))
#endif
#ifndef MAX
#define MAX(a,b) ((a) > (b) ? (a) : (b))
#endif

struct DPos {
    int32_t x;
    int32_t y;
    int32_t mapped;
    void *data;
    void *peer;
    int32_t echoC;
};

extern void awtJNI_ThreadYield(JNIEnv *env);

/*
 * Functions for accessing fields by name and signature
 */

JNIEXPORT jobject JNICALL
JNU_GetObjectField(JNIEnv *env, jobject self, const char *name,
                   const char *sig);

JNIEXPORT jboolean JNICALL
JNU_SetObjectField(JNIEnv *env, jobject self, const char *name,
                   const char *sig, jobject val);

JNIEXPORT jlong JNICALL
JNU_GetLongField(JNIEnv *env, jobject self, const char *name);

JNIEXPORT jint JNICALL
JNU_GetIntField(JNIEnv *env, jobject self, const char *name);

JNIEXPORT jboolean JNICALL
JNU_SetIntField(JNIEnv *env, jobject self, const char *name, jint val);

JNIEXPORT jboolean JNICALL
JNU_SetLongField(JNIEnv *env, jobject self, const char *name, jlong val);

JNIEXPORT jboolean JNICALL
JNU_GetBooleanField(JNIEnv *env, jobject self, const char *name);

JNIEXPORT jboolean JNICALL
JNU_SetBooleanField(JNIEnv *env, jobject self, const char *name, jboolean val);

JNIEXPORT jint JNICALL
JNU_GetCharField(JNIEnv *env, jobject self, const char *name);

#endif           /* _AWT_UTIL_H_ */
