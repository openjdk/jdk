/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include "jni.h"

JNIEXPORT void JNICALL Java_MutateFinals_jniSetObjectField(JNIEnv *env, jclass ignore, jobject obj, jobject value) {
    jclass clazz = (*env)->GetObjectClass(env, obj);
    jfieldID fid = (*env)->GetFieldID(env, clazz, "value", "Ljava/lang/Object;");
    if (fid != NULL) {
        (*env)->SetObjectField(env, obj, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetBooleanField(JNIEnv *env, jclass ignore, jobject obj, jboolean value) {
    jclass clazz = (*env)->GetObjectClass(env, obj);
    jfieldID fid = (*env)->GetFieldID(env, clazz, "value", "Z");
    if (fid != NULL) {
        (*env)->SetBooleanField(env, obj, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetByteField(JNIEnv *env, jclass ignore, jobject obj, jbyte value) {
    jclass clazz = (*env)->GetObjectClass(env, obj);
    jfieldID fid = (*env)->GetFieldID(env, clazz, "value", "B");
    if (fid != NULL) {
        (*env)->SetByteField(env, obj, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetCharField(JNIEnv *env, jclass ignore, jobject obj, jchar value) {
    jclass clazz = (*env)->GetObjectClass(env, obj);
    jfieldID fid = (*env)->GetFieldID(env, clazz, "value", "C");
    if (fid != NULL) {
        (*env)->SetCharField(env, obj, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetShortField(JNIEnv *env, jclass ignore, jobject obj, jshort value) {
    jclass clazz = (*env)->GetObjectClass(env, obj);
    jfieldID fid = (*env)->GetFieldID(env, clazz, "value", "S");
    if (fid != NULL) {
        (*env)->SetShortField(env, obj, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetIntField(JNIEnv *env, jclass ignore, jobject obj, jint value) {
    jclass clazz = (*env)->GetObjectClass(env, obj);
    jfieldID fid = (*env)->GetFieldID(env, clazz, "value", "I");
    if (fid != NULL) {
        (*env)->SetIntField(env, obj, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetLongField(JNIEnv *env, jclass ignore, jobject obj, jlong value) {
    jclass clazz = (*env)->GetObjectClass(env, obj);
    jfieldID fid = (*env)->GetFieldID(env, clazz, "value", "J");
    if (fid != NULL) {
        (*env)->SetLongField(env, obj, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetFloatField(JNIEnv *env, jclass ignore, jobject obj, jfloat value) {
    jclass clazz = (*env)->GetObjectClass(env, obj);
    jfieldID fid = (*env)->GetFieldID(env, clazz, "value", "F");
    if (fid != NULL) {
        (*env)->SetFloatField(env, obj, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetDoubleField(JNIEnv *env, jclass ignore, jobject obj, jdouble value) {
    jclass clazz = (*env)->GetObjectClass(env, obj);
    jfieldID fid = (*env)->GetFieldID(env, clazz, "value", "D");
    if (fid != NULL) {
        (*env)->SetDoubleField(env, obj, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetStaticObjectField(JNIEnv *env, jclass ignore, jclass clazz, jobject value) {
    jfieldID fid = (*env)->GetStaticFieldID(env, clazz, "value", "Ljava/lang/Object;");
    if (fid != NULL) {
        (*env)->SetStaticObjectField(env, clazz, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetStaticBooleanField(JNIEnv *env, jclass ignore, jclass clazz, jboolean value) {
    jfieldID fid = (*env)->GetStaticFieldID(env, clazz, "value", "Z");
    if (fid != NULL) {
        (*env)->SetStaticBooleanField(env, clazz, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetStaticByteField(JNIEnv *env, jclass ignore, jclass clazz, jbyte value) {
    jfieldID fid = (*env)->GetStaticFieldID(env, clazz, "value", "B");
    if (fid != NULL) {
        (*env)->SetStaticByteField(env, clazz, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetStaticCharField(JNIEnv *env, jclass ignore, jclass clazz, jchar value) {
    jfieldID fid = (*env)->GetStaticFieldID(env, clazz, "value", "C");
    if (fid != NULL) {
        (*env)->SetStaticCharField(env, clazz, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetStaticShortField(JNIEnv *env, jclass ignore, jclass clazz, jshort value) {
    jfieldID fid = (*env)->GetStaticFieldID(env, clazz, "value", "S");
    if (fid != NULL) {
        (*env)->SetStaticShortField(env, clazz, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetStaticIntField(JNIEnv *env, jclass ignore, jclass clazz, jint value) {
    jfieldID fid = (*env)->GetStaticFieldID(env, clazz, "value", "I");
    if (fid != NULL) {
        (*env)->SetStaticIntField(env, clazz, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetStaticLongField(JNIEnv *env, jclass ignore, jclass clazz, jlong value) {
    jfieldID fid = (*env)->GetStaticFieldID(env, clazz, "value", "J");
    if (fid != NULL) {
        (*env)->SetStaticLongField(env, clazz, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetStaticFloatField(JNIEnv *env, jclass ignore, jclass clazz, jfloat value) {
    jfieldID fid = (*env)->GetStaticFieldID(env, clazz, "value", "F");
    if (fid != NULL) {
        (*env)->SetStaticFloatField(env, clazz, fid, value);
    }
}

JNIEXPORT void JNICALL Java_MutateFinals_jniSetStaticDoubleField(JNIEnv *env, jclass ignore, jclass clazz, jdouble value) {
    jfieldID fid = (*env)->GetStaticFieldID(env, clazz, "value", "D");
    if (fid != NULL) {
        (*env)->SetStaticDoubleField(env, clazz, fid, value);
    }
}