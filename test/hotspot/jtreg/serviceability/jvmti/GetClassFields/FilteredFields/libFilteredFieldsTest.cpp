/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "jvmti_common.hpp"

extern "C" {

static jvmtiEnv *jvmti = nullptr;


jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
    jint res = jvm->GetEnv((void **)&jvmti, JVMTI_VERSION_1_1);
    if (res != JNI_OK || jvmti == nullptr) {
        printf("Wrong result of a valid call to GetEnv!\n");
        fflush(0);
        return JNI_ERR;
    }
    return JNI_OK;
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}


JNIEXPORT jint JNICALL
Java_FilteredFieldsTest_getJVMTIFieldCount(JNIEnv *env, jclass cls, jclass clazz) {
    if (jvmti == nullptr) {
        env->FatalError("JVMTI agent was not properly loaded");
    }

    jint fcount = 0;
    jfieldID *fields = nullptr;

    check_jvmti_status(env, jvmti->GetClassFields(clazz, &fcount, &fields), "GetClassFields failed");

    printf("GetClassFields returned %d fields:\n", (int)fcount);
    for (int i = 0; i < fcount; i++) {
        char *name;
        jvmtiError err = jvmti->GetFieldName(clazz, fields[i], &name, nullptr, nullptr);
        if (err != JVMTI_ERROR_NONE) {
            printf("GetFieldName(%d) returned error: %s (%d)\n",
                   i, TranslateError(err), err);
            continue;
        }
        printf("  [%d]: %s\n", i, name);
        jvmti->Deallocate((unsigned char *)name);
    }
    fflush(0);
    return fcount;
}

}
