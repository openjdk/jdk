/*
 * Copyright (c) 2004, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>

#include "jni.h"
#include "jni_util.h"
#include "jlong.h"
#include "jvm.h"
#include "jdk_util.h"

#include "sun_misc_VM.h"

/* Only register the performance-critical methods */
static JNINativeMethod methods[] = {
    {"getNanoTimeAdjustment", "(J)J", (void *)&JVM_GetNanoTimeAdjustment}
};

JNIEXPORT jobject JNICALL
Java_sun_misc_VM_latestUserDefinedLoader(JNIEnv *env, jclass cls) {
    return JVM_LatestUserDefinedLoader(env);
}

typedef void (JNICALL *GetJvmVersionInfo_fp)(JNIEnv*, jvm_version_info*, size_t);

JNIEXPORT void JNICALL
Java_sun_misc_VM_initialize(JNIEnv *env, jclass cls) {
    GetJvmVersionInfo_fp func_p;

    if (!JDK_InitJvmHandle()) {
        JNU_ThrowInternalError(env, "Handle for JVM not found for symbol lookup");
        return;
    }

    // Registers implementations of native methods described in methods[]
    // above.
    // In particular, registers JVM_GetNanoTimeAdjustment as the implementation
    // of the native sun.misc.VM.getNanoTimeAdjustment - avoiding the cost of
    // introducing a Java_sun_misc_VM_getNanoTimeAdjustment  wrapper
    (*env)->RegisterNatives(env, cls,
                            methods, sizeof(methods)/sizeof(methods[0]));

    func_p = (GetJvmVersionInfo_fp) JDK_FindJvmEntry("JVM_GetVersionInfo");
     if (func_p != NULL) {
        jvm_version_info info;

        memset(&info, 0, sizeof(info));

        /* obtain the JVM version info */
        (*func_p)(env, &info, sizeof(info));
    }
}

