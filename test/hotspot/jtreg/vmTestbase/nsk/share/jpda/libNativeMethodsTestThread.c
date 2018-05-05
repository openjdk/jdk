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
#include "jni.h"
#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

#ifndef JNI_ENV_PTR

#ifdef __cplusplus
#define JNI_ENV_ARG_2(x, y) y
#define JNI_ENV_ARG_3(x, y, z) y, z
#define JNI_ENV_ARG_4(x, y, z, a) y, z, a
#define JNI_ENV_PTR(x) x
#else
#define JNI_ENV_ARG_2(x,y) x, y
#define JNI_ENV_ARG_3(x, y, z) x, y, z
#define JNI_ENV_ARG_4(x, y, z, a) x, y, z, a
#define JNI_ENV_PTR(x) (*x)
#endif

#endif

static void logMessage(JNIEnv *env, jobject thisObject, jstring message)
{
        jclass klass;
        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));
        JNI_ENV_PTR(env)->CallVoidMethod(
        JNI_ENV_ARG_4(
                env,
                thisObject,
                JNI_ENV_PTR(env)->GetMethodID(JNI_ENV_ARG_4(env, klass, "log", "(Ljava/lang/String;)V")),
                message));
}

JNIEXPORT void JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_VoidMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        logMessage(env, thisObject, message);
}

JNIEXPORT jboolean JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_BooleanMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedBooleanValue",
                "Z"));

        return JNI_ENV_PTR(env)->GetStaticBooleanField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jbyte JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_ByteMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedByteValue",
                "B"));

        return JNI_ENV_PTR(env)->GetStaticByteField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jshort JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_ShortMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedShortValue",
                "S"));

        return JNI_ENV_PTR(env)->GetStaticShortField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jchar JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_CharMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedCharValue",
                "C"));

        return JNI_ENV_PTR(env)->GetStaticCharField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jint JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_IntMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedIntValue",
                "I"));

        return JNI_ENV_PTR(env)->GetStaticIntField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jlong JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_LongMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedLongValue",
                "J"));

        return JNI_ENV_PTR(env)->GetStaticLongField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jfloat JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_FloatMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedFloatValue",
                "F"));

        return JNI_ENV_PTR(env)->GetStaticFloatField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jdouble JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_DoubleMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedDoubleValue",
                "D"));

        return JNI_ENV_PTR(env)->GetStaticDoubleField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jobject JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_ObjectArrayMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedObjectArrayValue",
                "[Ljava/lang/Object;"));

        return JNI_ENV_PTR(env)->GetStaticObjectField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jobject JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_StringMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedStringValue",
                "Ljava/lang/String;"));

        return JNI_ENV_PTR(env)->GetStaticObjectField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jobject JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_ThreadMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedThreadValue",
                "Ljava/lang/Thread;"));

        return JNI_ENV_PTR(env)->GetStaticObjectField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jobject JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_ThreadGroupMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedThreadGroupValue",
                "Ljava/lang/ThreadGroup;"));

        return JNI_ENV_PTR(env)->GetStaticObjectField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jobject JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_ClassObjectMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedClassObjectValue",
                "Ljava/lang/Class;"));

        return JNI_ENV_PTR(env)->GetStaticObjectField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jobject JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_ClassLoaderMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedClassLoaderValue",
                "Ljava/lang/ClassLoader;"));

        return JNI_ENV_PTR(env)->GetStaticObjectField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jobject JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_ObjectMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedObjectValue",
                "Ljava/lang/Object;"));

        return JNI_ENV_PTR(env)->GetStaticObjectField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jobject JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_BooleanWrapperMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedBooleanWrapperValue",
                "Ljava/lang/Boolean;"));

        return JNI_ENV_PTR(env)->GetStaticObjectField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jobject JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_ByteWrapperMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedByteWrapperValue",
                "Ljava/lang/Byte;"));

        return JNI_ENV_PTR(env)->GetStaticObjectField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jobject JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_ShortWrapperMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedShortWrapperValue",
                "Ljava/lang/Short;"));

        return JNI_ENV_PTR(env)->GetStaticObjectField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jobject JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_CharWrapperMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedCharWrapperValue",
                "Ljava/lang/Character;"));

        return JNI_ENV_PTR(env)->GetStaticObjectField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jobject JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_IntWrapperMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedIntWrapperValue",
                "Ljava/lang/Integer;"));

        return JNI_ENV_PTR(env)->GetStaticObjectField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jobject JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_LongWrapperMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedLongWrapperValue",
                "Ljava/lang/Long;"));

        return JNI_ENV_PTR(env)->GetStaticObjectField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jobject JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_FloatWrapperMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedFloatWrapperValue",
                "Ljava/lang/Float;"));

        return JNI_ENV_PTR(env)->GetStaticObjectField(JNI_ENV_ARG_3(env, klass, valueField));
}

JNIEXPORT jobject JNICALL
Java_nsk_share_jpda_NativeMethodsTestThread_DoubleWrapperMethod(JNIEnv *env,
        jobject thisObject, jstring message)
{
        jclass klass;
        jfieldID valueField;

        logMessage(env, thisObject, message);

        klass = JNI_ENV_PTR(env)->GetObjectClass(JNI_ENV_ARG_2(env, thisObject));

        valueField = JNI_ENV_PTR(env)->GetStaticFieldID(JNI_ENV_ARG_4(
                env,
                klass,
                "expectedDoubleWrapperValue",
                "Ljava/lang/Double;"));

        return JNI_ENV_PTR(env)->GetStaticObjectField(JNI_ENV_ARG_3(env, klass, valueField));
}

#ifdef __cplusplus
}
#endif
