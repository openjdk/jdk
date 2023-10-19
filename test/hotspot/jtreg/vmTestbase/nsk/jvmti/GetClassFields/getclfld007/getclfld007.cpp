/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include <string.h>
#include "jvmti.h"
#include "agent_common.h"
#include "JVMTITools.h"

extern "C" {


#define PASSED 0
#define STATUS_FAILED 2

static jvmtiEnv *jvmti = NULL;
static jint result = PASSED;


// compares 'value' with jobject_arr[index]
static bool equals_str(JNIEnv *env, const char *value, jobjectArray jobject_arr, jint index) {
    jstring jstr = (jstring)env->GetObjectArrayElement(jobject_arr, index);
    const char* utf = env->GetStringUTFChars(jstr, NULL);
    bool res = false;
    if (utf != NULL) {
        res = strcmp(value, utf) == 0;
        env->ReleaseStringUTFChars(jstr, utf);
    } else {
        printf("GetStringUTFChars failed\n");
        result = STATUS_FAILED;
    }
    env->DeleteLocalRef(jstr);
    return res;
}

#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_getclfld007(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_getclfld007(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_getclfld007(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
    jint res;

    res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
    if (res != JNI_OK || jvmti == NULL) {
        printf("Wrong result of a valid call to GetEnv!\n");
        return JNI_ERR;
    }

    return JNI_OK;
}

JNIEXPORT void JNICALL
Java_nsk_jvmti_GetClassFields_getclfld007_check(JNIEnv *env, jclass cls, jclass clazz, jobjectArray fieldArr) {
    jvmtiError err;
    jint fcount;
    jfieldID *fields;
    char *name, *sig;
    int j;

    if (jvmti == NULL) {
        printf("JVMTI client was not properly loaded!\n");
        fflush(0);
        result = STATUS_FAILED;
        return;
    }

    // fieldArr contains 2 elements for each field
    jint field_count = env->GetArrayLength(fieldArr) / 2;

    err = jvmti->GetClassFields(clazz, &fcount, &fields);
    if (err != JVMTI_ERROR_NONE) {
        printf("GetClassFields unexpected error: %s (%d)\n",
               TranslateError(err), err);
        fflush(0);
        result = STATUS_FAILED;
        return;
    }

    if (fcount != field_count) {
        printf("wrong number of fields: %d, expected: %d\n",
               fcount, field_count);
        result = STATUS_FAILED;
    }
    for (j = 0; j < fcount; j++) {
        if (fields[j] == NULL) {
            printf("(%d) fieldID = null\n", j);
            result = STATUS_FAILED;
            continue;
        }
        err = jvmti->GetFieldName(clazz, fields[j], &name, &sig, NULL);
        if (err != JVMTI_ERROR_NONE) {
            printf("(GetFieldName#%d) unexpected error: %s (%d)\n",
                   j, TranslateError(err), err);
            result = STATUS_FAILED;
            continue;
        }
        printf(">>>   [%d]: %s, sig = \"%s\"\n", j, name, sig);
        if ((j < field_count) &&
               (name == NULL || sig == NULL ||
                !equals_str(env, name, fieldArr, j * 2) ||
                !equals_str(env, sig, fieldArr, j * 2 + 1))) {
            printf("(%d) wrong field: \"%s%s\"", j, name, sig);
            result = STATUS_FAILED;
        }
        jvmti->Deallocate((unsigned char *)name);
        jvmti->Deallocate((unsigned char *)sig);
    }
    fflush(0);
}

JNIEXPORT int JNICALL
Java_nsk_jvmti_GetClassFields_getclfld007_getRes(JNIEnv *env, jclass cls) {
    return result;
}

}
