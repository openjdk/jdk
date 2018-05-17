/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include <stdlib.h>
#include "jnihelper.h"

// compare most java primitive value types
#define COMP(type) \
int type##comp(const void *s1, const void *s2)\
{\
    type st1 = *((type *)s1);\
    type st2 = *((type *)s2);\
    if (st1 < st2)\
        return -1;\
    else if (st1 > st2)\
        return 1;\
    else\
        return 0;\
}

// basic routine: provide critical sections and calculations
    // enter array CS
    // check isCopy for native referencing
    // enter first string CS
    // leave first string CS
    // enter second string CS
    // leave array CS
    // enter second string CS

#define BODY(type) \
int hash = 0; int i; jboolean isCopy = JNI_FALSE; jchar *nativeStr; jsize size; type *nativeArray; \
size = (*env)->GetArrayLength(env, array); CE \
nativeArray = (type *)(*env)->GetPrimitiveArrayCritical(env, array, &isCopy); CE \
EnterCS(env); \
if (isCopy == JNI_TRUE) return 0;\
qsort(nativeArray, size, sizeof(type), *type##comp);\
\
size = (*env)->GetStringLength(env, str); CE \
nativeStr = (jchar *)(*env)->GetStringCritical(env, str, &isCopy); CE \
if (isCopy == JNI_TRUE) return 0;\
for (i = 0; i < size; ++i)\
    hash += (int)nativeStr[i];\
\
(*env)->ReleasePrimitiveArrayCritical(env, array, nativeArray, 0); CE \
LeaveCS(env); \
(*env)->ReleaseStringCritical(env, str, nativeStr); CE \
\
hash = 0;\
size = (*env)->GetStringLength(env, str); CE \
nativeStr = (jchar *)(*env)->GetStringCritical(env, str, &isCopy); CE \
EnterCS(env); \
if (isCopy == JNI_TRUE) return 0;\
for (i = 0; i < size; ++i)\
    hash += (int)nativeStr[i];\
LeaveCS(env); \
(*env)->ReleaseStringCritical(env, str, nativeStr); CE \
return hash;

static int CSEntered = 0;
static int CSLeft = 0;

void EnterCS(JNIEnv *env)
{
    // unsafe but where are no better ideas
    //++CSEntered;
    //printf("CS Entered -> Entered: %d\n", CSEntered);
//    jclass trace = 0; jmethodID method = 0;
//    trace = (*env)->FindClass(env, "nsk/stress/jni/gclocker/Trace"); CE
//    method = (*env)->GetStaticMethodID(env, trace, "EnterCS", "()V"); CE
//    (*env)->CallStaticVoidMethod(env, trace, method); CE
}

void LeaveCS(JNIEnv *env)
{
    // unsafe but where are no better ideas
    //++CSLeft;
    //printf("CS Left -> Completed: %d\tActive: %d\n", CSLeft, CSEntered - CSLeft);
//    jclass trace = 0; jmethodID method = 0;
//    trace = (*env)->FindClass(env, "nsk/stress/jni/gclocker/Trace"); CE
//    method = (*env)->GetStaticMethodID(env, trace, "LeaveCS", "()V"); CE
//    (*env)->CallStaticVoidMethod(env, trace, method); CE
}

COMP(jint)
COMP(jboolean)
COMP(jchar)
COMP(jshort)
COMP(jbyte)
COMP(jdouble)
COMP(jfloat)
COMP(jlong)

/*
 * Class:     JNIWorker
 * Method:    NativeCall
 * Signature: ([ZLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_nsk_stress_jni_gclocker_JNIWorker_NativeCall___3ZLjava_lang_String_2
  (JNIEnv * env, jobject obj, jbooleanArray array, jstring str)
{
    BODY(jboolean)
}

/*
 * Class:     JNIWorker
 * Method:    NativeCall
 * Signature: ([BLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_nsk_stress_jni_gclocker_JNIWorker_NativeCall___3BLjava_lang_String_2
  (JNIEnv * env, jobject obj, jbyteArray array, jstring str)
{
    BODY(jbyte)
}

/*
 * Class:     JNIWorker
 * Method:    NativeCall
 * Signature: ([CLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_nsk_stress_jni_gclocker_JNIWorker_NativeCall___3CLjava_lang_String_2
  (JNIEnv *env, jobject obj, jcharArray array, jstring str)
{
    BODY(jchar)
}

/*
 * Class:     JNIWorker
 * Method:    NativeCall
 * Signature: ([SLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_nsk_stress_jni_gclocker_JNIWorker_NativeCall___3SLjava_lang_String_2
  (JNIEnv *env, jobject obj, jshortArray array, jstring str)
{
    BODY(jshort)
}

/*
 * Class:     JNIWorker
 * Method:    NativeCall
 * Signature: ([ILjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_nsk_stress_jni_gclocker_JNIWorker_NativeCall___3ILjava_lang_String_2
  (JNIEnv *env, jobject obj, jintArray array, jstring str)
{
    BODY(jint)
}

/*
 * Class:     JNIWorker
 * Method:    NativeCall
 * Signature: ([JLjava/lang/String;)I
 */

JNIEXPORT jint JNICALL Java_nsk_stress_jni_gclocker_JNIWorker_NativeCall___3JLjava_lang_String_2
  (JNIEnv *env, jobject obj, jlongArray array, jstring str)
{
    BODY(jlong)
}

/*
 * Class:     JNIWorker
 * Method:    NativeCall
 * Signature: ([FLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_nsk_stress_jni_gclocker_JNIWorker_NativeCall___3FLjava_lang_String_2
  (JNIEnv *env, jobject obj, jfloatArray array, jstring str)
{
    BODY(jfloat)
}

/*
 * Class:     JNIWorker
 * Method:    NativeCall
 * Signature: ([DLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_nsk_stress_jni_gclocker_JNIWorker_NativeCall___3DLjava_lang_String_2
  (JNIEnv *env, jobject obj, jdoubleArray array, jstring str)
{
    BODY(jdouble)
}
