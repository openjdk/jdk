/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
#include <jvmti.h>
#include <stdio.h>
#include <string.h>
#include "jvmti_common.hpp"

static jvmtiEnv* jvmti = nullptr;
static const jint MAX_FRAME_COUNT = 32;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_GetAllStackTracesVThreadTest_checkGetAllStackTraces(JNIEnv* jni, jclass clazz,
                                                   jobjectArray vthreads, jint count) {
    jvmtiStackInfo* stack_info = nullptr;
    jint thread_count = 0;

    jvmtiError err = jvmti->GetAllStackTraces(MAX_FRAME_COUNT, &stack_info, &thread_count);
    check_jvmti_status(jni, err, "checkGetAllStackTraces: error in JVMTI GetAllStackTraces");

    LOG("GetAllStackTraces returned %d threads\n", thread_count);

    for (int k = 0; k < thread_count; k++) {
        jvmtiThreadInfo tinfo;
        err = jvmti->GetThreadInfo(stack_info[k].thread, &tinfo);
        if (err == JVMTI_ERROR_NONE) {
            jboolean isVirtual = jni->IsVirtualThread(stack_info[k].thread);
            LOG("  [%d] %s (frames: %d, state: %x, virtual: %d)\n",
                k, tinfo.name, stack_info[k].frame_count, stack_info[k].state, isVirtual);
            jvmti->Deallocate((unsigned char*)tinfo.name);
        }
    }

    int found = 0;
    for (int i = 0; i < count; i++) {
        jobject vt = jni->GetObjectArrayElement(vthreads, i);
        for (int j = 0; j < thread_count; j++) {
            if (jni->IsSameObject(vt, stack_info[j].thread)) {
                jvmtiThreadInfo info;
                err = jvmti->GetThreadInfo(stack_info[j].thread, &info);
                check_jvmti_status(jni, err, "checkGetAllStackTraces: error in GetThreadInfo");
                LOG("Unexpectedly found virtual thread: %s\n", info.name);
                jvmti->Deallocate((unsigned char*)info.name);
                found++;
                break;
            }
        }
    }

    LOG("Found %d virtual threads in GetAllStackTraces (expected 0)\n", found);
    jvmti->Deallocate((unsigned char*)stack_info);
    return (found == 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
    if (jvm->GetEnv((void**)(&jvmti), JVMTI_VERSION) != JNI_OK) {
        LOG("Agent_OnLoad: error in GetEnv\n");
        return JNI_ERR;
    }

    jvmtiCapabilities caps;
    memset(&caps, 0, sizeof(caps));
    caps.can_support_virtual_threads = 1;

    jvmtiError err = jvmti->AddCapabilities(&caps);
    if (err != JVMTI_ERROR_NONE) {
        LOG("Agent_OnLoad: error in AddCapabilities: %d\n", err);
        return JNI_ERR;
    }

    LOG("Agent_OnLoad: loaded successfully\n");
    return 0;
}

} // extern "C"
