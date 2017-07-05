/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

#include <stdio.h>
#include <jni.h>
#include "jvm.h"
#include "management.h"

#define ERR_MSG_SIZE 128

const JmmInterface* jmm_interface = NULL;
JavaVM* jvm = NULL;
jint jmm_version = 0;

JNIEXPORT jint JNICALL
   JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv* env;

    jvm = vm;
    if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_2) != JNI_OK) {
        return JNI_ERR;
    }

    jmm_interface = (JmmInterface*) JVM_GetManagement(JMM_VERSION_1_0);
    if (jmm_interface == NULL) {
        JNU_ThrowInternalError(env, "Unsupported Management version");
        return JNI_ERR;
    }

    jmm_version = jmm_interface->GetVersion(env);
    return (*env)->GetVersion(env);
}

void throw_internal_error(JNIEnv* env, const char* msg) {
    char errmsg[128];

    sprintf(errmsg, "errno: %d error: %s\n", errno, msg);
    JNU_ThrowInternalError(env, errmsg);
}
