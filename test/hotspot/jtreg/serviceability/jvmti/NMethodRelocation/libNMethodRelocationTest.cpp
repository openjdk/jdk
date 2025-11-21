/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include <inttypes.h>
#include <jvmti.h>
#include <stdio.h>
#include <string.h>

/**
 * Callback for COMPILED_METHOD_LOAD event.
 */
JNIEXPORT void JNICALL
callbackCompiledMethodLoad(jvmtiEnv* jvmti, jmethodID method,
                            jint code_size, const void* code_addr,
                            jint map_length, const jvmtiAddrLocationMap* map,
                            const void* compile_info) {
    char* name = nullptr;
    char* sig = nullptr;

    if (jvmti->GetMethodName(method, &name, &sig, nullptr) != JVMTI_ERROR_NONE) {
        printf("    [Could not retrieve method name]\n");
        fflush(stdout);
        return;
    }

    printf("<COMPILED_METHOD_LOAD>:   name: %s, code: 0x%016" PRIxPTR "\n",
        name, (uintptr_t)code_addr);
    fflush(stdout);
}

/**
 * Callback for COMPILED_METHOD_UNLOAD event.
 */
JNIEXPORT void JNICALL
callbackCompiledMethodUnload(jvmtiEnv* jvmti, jmethodID method,
                             const void* code_addr) {
    char* name = nullptr;
    char* sig = nullptr;

    if (jvmti->GetMethodName(method, &name, &sig, nullptr) != JVMTI_ERROR_NONE) {
        printf("    [Could not retrieve method name]\n");
        fflush(stdout);
        return;
    }
    printf("<COMPILED_METHOD_UNLOAD>:   name: %s, code: 0x%016" PRIxPTR "\n",
        name, (uintptr_t)code_addr);
    fflush(stdout);
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
    jvmtiEnv* jvmti = nullptr;
    jvmtiError error;

    if (jvm->GetEnv((void **)&jvmti, JVMTI_VERSION_1_0) != JNI_OK) {
        printf("Unable to access JVMTI!\n");
        return JNI_ERR;
    }

    // Add required capabilities
    jvmtiCapabilities caps;
    memset(&caps, 0, sizeof(caps));
    caps.can_generate_compiled_method_load_events = 1;
    error = jvmti->AddCapabilities(&caps);
    if (error != JVMTI_ERROR_NONE) {
        printf("ERROR: Unable to add capabilities, error=%d\n", error);
        return JNI_ERR;
    }

    // Set event callbacks
    jvmtiEventCallbacks eventCallbacks;
    memset(&eventCallbacks, 0, sizeof(eventCallbacks));
    eventCallbacks.CompiledMethodLoad = callbackCompiledMethodLoad;
    eventCallbacks.CompiledMethodUnload = callbackCompiledMethodUnload;
    error = jvmti->SetEventCallbacks(&eventCallbacks, sizeof(eventCallbacks));
    if (error != JVMTI_ERROR_NONE) {
        printf("ERROR: Unable to set event callbacks, error=%d\n", error);
        return JNI_ERR;
    }

    // Enable events
    error = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_COMPILED_METHOD_LOAD, nullptr);
    if (error != JVMTI_ERROR_NONE) {
        printf("ERROR: Unable to enable COMPILED_METHOD_LOAD event, error=%d\n", error);
        return JNI_ERR;
    }

    error = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_COMPILED_METHOD_UNLOAD, nullptr);
    if (error != JVMTI_ERROR_NONE) {
        printf("ERROR: Unable to enable COMPILED_METHOD_UNLOAD event, error=%d\n", error);
        return JNI_ERR;
    }

    return JNI_OK;
}
