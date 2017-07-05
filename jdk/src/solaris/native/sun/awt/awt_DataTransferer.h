/*
 * Copyright 2000-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef AWT_DATATRANSFERER_H
#define AWT_DATATRANSFERER_H

#include <X11/Intrinsic.h>
#include <inttypes.h>

#define _XA_DELETE          "DELETE"
#define _XA_FILENAME        "FILE_NAME"
#define _XA_HOSTNAME        "HOST_NAME"
#define _XA_NULL            "NULL"
#define _DT_FILENAME        "_DT_NETFILE"

#define AWT_DND_POLL_INTERVAL ((unsigned long)250) /* milliseconds */

typedef struct {
    jobject    source;
    jobject    transferable;
    jobject    formatMap;
    jlongArray formats;
} awt_convertDataCallbackStruct;

extern XContext awt_convertDataContext; /* XContext is not 64 bits */

extern Atom XA_TARGETS;

/*
 * Single routine to convert to target FILE_NAME or _DT_FILENAME
 */
Boolean
convertFileType(jbyteArray data, Atom * type, XtPointer * value,
                unsigned long *length, int32_t *format);

Boolean
awt_convertData(Widget w, Atom * selection, Atom * target, Atom * type,
                XtPointer * value, unsigned long *length, int32_t *format);

jlongArray
get_selection_targets(JNIEnv *env, Atom selection, Time time_stamp);

jlongArray
getSelectionTargetsHelper(JNIEnv* env, XtPointer value, unsigned long length);

jbyteArray
get_selection_data(JNIEnv *env, Atom selection, Atom format, Time time_stamp);

void
awt_cleanupConvertDataContext(JNIEnv *env, Atom selectionAtom);

/*
 * NOTE: You need these macros only if you take care of performance, since they
 * provide proper caching. Otherwise you can use JNU_CallMethodByName etc.
 */

/*
 * This macro defines a function which returns the class for the specified
 * class name with proper caching and error handling.
 */
#define DECLARE_JAVA_CLASS(javaclazz, name)                                    \
static jclass                                                                  \
get_ ## javaclazz(JNIEnv* env) {                                               \
    static jclass javaclazz = NULL;                                            \
                                                                               \
    if (JNU_IsNull(env, javaclazz)) {                                          \
        jclass javaclazz ## Local = (*env)->FindClass(env, name);              \
                                                                               \
        if (!JNU_IsNull(env, javaclazz ## Local)) {                            \
            javaclazz = (jclass)(*env)->NewGlobalRef(env, javaclazz ## Local); \
            (*env)->DeleteLocalRef(env, javaclazz ## Local);                   \
            if (JNU_IsNull(env, javaclazz)) {                                  \
                JNU_ThrowOutOfMemoryError(env, "");                            \
            }                                                                  \
        }                                                                      \
                                                                               \
        if (!JNU_IsNull(env, ((*env)->ExceptionOccurred(env)))) {              \
            (*env)->ExceptionDescribe(env);                                    \
            (*env)->ExceptionClear(env);                                       \
        }                                                                      \
    }                                                                          \
                                                                               \
    DASSERT(!JNU_IsNull(env, javaclazz));                                      \
                                                                               \
    return javaclazz;                                                          \
}

/*
 * The following macros defines blocks of code which retrieve a method of the
 * specified class identified with the specified name and signature.
 * The specified class should be previously declared with DECLARE_JAVA_CLASS.
 * These macros should be placed at the beginning of a block, after definition
 * of local variables, but before the code begins.
 */
#define DECLARE_VOID_JAVA_METHOD(method, javaclazz, name, signature)           \
    static jmethodID method = NULL;                                            \
                                                                               \
    if (JNU_IsNull(env, method)) {                                             \
        jclass clazz = get_ ## javaclazz(env);                                 \
                                                                               \
        if (JNU_IsNull(env, clazz)) {                                          \
            return;                                                            \
        }                                                                      \
                                                                               \
        method = (*env)->GetMethodID(env, clazz, name, signature);             \
                                                                               \
        if ((*env)->ExceptionCheck(env) == JNI_TRUE) {                         \
            (*env)->ExceptionDescribe(env);                                    \
            (*env)->ExceptionClear(env);                                       \
        }                                                                      \
                                                                               \
        if (JNU_IsNull(env, method)) {                                         \
            DASSERT(False);                                                    \
            return;                                                            \
        }                                                                      \
    }

#define DECLARE_BOOLEAN_JAVA_METHOD(method, javaclazz, name, signature)        \
    static jmethodID method = NULL;                                            \
                                                                               \
    if (JNU_IsNull(env, method)) {                                             \
        jclass clazz = get_ ## javaclazz(env);                                 \
                                                                               \
        if (JNU_IsNull(env, clazz)) {                                          \
            return False;                                                      \
        }                                                                      \
                                                                               \
        method = (*env)->GetMethodID(env, clazz, name, signature);             \
                                                                               \
        if ((*env)->ExceptionCheck(env) == JNI_TRUE) {                         \
            (*env)->ExceptionDescribe(env);                                    \
            (*env)->ExceptionClear(env);                                       \
        }                                                                      \
                                                                               \
        if (JNU_IsNull(env, method)) {                                         \
            DASSERT(False);                                                    \
            return False;                                                      \
        }                                                                      \
    }

#define DECLARE_JINT_JAVA_METHOD(method, javaclazz, name, signature)           \
    static jmethodID method = NULL;                                            \
                                                                               \
    if (JNU_IsNull(env, method)) {                                             \
        jclass clazz = get_ ## javaclazz(env);                                 \
                                                                               \
        if (JNU_IsNull(env, clazz)) {                                          \
            return java_awt_dnd_DnDConstants_ACTION_NONE;                      \
        }                                                                      \
                                                                               \
        method = (*env)->GetMethodID(env, clazz, name, signature);             \
                                                                               \
        if ((*env)->ExceptionCheck(env) == JNI_TRUE) {                         \
            (*env)->ExceptionDescribe(env);                                    \
            (*env)->ExceptionClear(env);                                       \
        }                                                                      \
                                                                               \
        if (JNU_IsNull(env, method)) {                                         \
            DASSERT(False);                                                    \
            return java_awt_dnd_DnDConstants_ACTION_NONE;                      \
        }                                                                      \
    }

#define DECLARE_OBJECT_JAVA_METHOD(method, javaclazz, name, signature)         \
    static jmethodID method = NULL;                                            \
                                                                               \
    if (JNU_IsNull(env, method)) {                                             \
        jclass clazz = get_ ## javaclazz(env);                                 \
                                                                               \
        if (JNU_IsNull(env, clazz)) {                                          \
            return NULL;                                                       \
        }                                                                      \
                                                                               \
        method = (*env)->GetMethodID(env, clazz, name, signature);             \
                                                                               \
        if ((*env)->ExceptionCheck(env) == JNI_TRUE) {                         \
            (*env)->ExceptionDescribe(env);                                    \
            (*env)->ExceptionClear(env);                                       \
        }                                                                      \
                                                                               \
        if (JNU_IsNull(env, method)) {                                         \
            DASSERT(False);                                                    \
            return NULL;                                                       \
        }                                                                      \
    }

#define DECLARE_STATIC_OBJECT_JAVA_METHOD(method, javaclazz, name, signature)  \
    static jmethodID method = NULL;                                            \
    jclass clazz = get_ ## javaclazz(env);                                     \
                                                                               \
    if (JNU_IsNull(env, clazz)) {                                              \
        return NULL;                                                           \
    }                                                                          \
                                                                               \
    if (JNU_IsNull(env, method)) {                                             \
        method = (*env)->GetStaticMethodID(env, clazz, name, signature);       \
                                                                               \
        if ((*env)->ExceptionCheck(env) == JNI_TRUE) {                         \
            (*env)->ExceptionDescribe(env);                                    \
            (*env)->ExceptionClear(env);                                       \
        }                                                                      \
                                                                               \
        if (JNU_IsNull(env, method)) {                                         \
            DASSERT(False);                                                    \
            return NULL;                                                       \
        }                                                                      \
    }

#define DECLARE_STATIC_VOID_JAVA_METHOD(method, javaclazz, name, signature)    \
    static jmethodID method = NULL;                                            \
    jclass clazz = get_ ## javaclazz(env);                                     \
                                                                               \
    if (JNU_IsNull(env, clazz)) {                                              \
        return;                                                                \
    }                                                                          \
                                                                               \
    if (JNU_IsNull(env, method)) {                                             \
        method = (*env)->GetStaticMethodID(env, clazz, name, signature);       \
                                                                               \
        if ((*env)->ExceptionCheck(env) == JNI_TRUE) {                         \
            (*env)->ExceptionDescribe(env);                                    \
            (*env)->ExceptionClear(env);                                       \
        }                                                                      \
                                                                               \
        if (JNU_IsNull(env, method)) {                                         \
            DASSERT(False);                                                    \
            return;                                                            \
        }                                                                      \
    }

#define DECLARE_STATIC_JINT_JAVA_METHOD(method, javaclazz, name, signature)    \
    static jmethodID method = NULL;                                            \
    jclass clazz = get_ ## javaclazz(env);                                     \
                                                                               \
    if (JNU_IsNull(env, clazz)) {                                              \
        return java_awt_dnd_DnDConstants_ACTION_NONE;                          \
    }                                                                          \
                                                                               \
    if (JNU_IsNull(env, method)) {                                             \
        method = (*env)->GetStaticMethodID(env, clazz, name, signature);       \
                                                                               \
        if ((*env)->ExceptionCheck(env) == JNI_TRUE) {                         \
            (*env)->ExceptionDescribe(env);                                    \
            (*env)->ExceptionClear(env);                                       \
        }                                                                      \
                                                                               \
        if (JNU_IsNull(env, method)) {                                         \
            DASSERT(False);                                                    \
            return java_awt_dnd_DnDConstants_ACTION_NONE;                      \
        }                                                                      \
    }

#endif /* AWT_DATATRANSFERER_H */
