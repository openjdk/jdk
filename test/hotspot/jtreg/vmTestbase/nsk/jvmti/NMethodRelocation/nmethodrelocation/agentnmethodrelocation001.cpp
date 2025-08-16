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

#include "jvmti.h"
#include "agent_common.hpp"
#include "jvmti_tools.hpp"

extern "C" {

/* scaffold objects */
static jlong timeout = 0;

#define EVENTS_COUNT    2

/* tested events */
static jvmtiEvent eventsList[EVENTS_COUNT] = {
    JVMTI_EVENT_COMPILED_METHOD_LOAD,
    JVMTI_EVENT_COMPILED_METHOD_UNLOAD
};

/** Agent library initialization. */
#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_setevntcallb001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_setevntcallb001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_setevntcallb001(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif

/* ============================================================================= */

/** Agent algorithm. */
static void JNICALL
agentProc(jvmtiEnv* jvmti, JNIEnv* jni, void* arg) {
    NSK_DISPLAY0("Wait for tested method forced to compile\n");
    if (!nsk_jvmti_waitForSync(timeout))
        return;

    if (!nsk_jvmti_enableEvents(JVMTI_ENABLE, EVENTS_COUNT, eventsList, nullptr)) {
        nsk_jvmti_setFailStatus();
    }

    if (!nsk_jvmti_enableEvents(JVMTI_DISABLE, EVENTS_COUNT, eventsList, nullptr)) {
        nsk_jvmti_setFailStatus();
    }

    NSK_DISPLAY0("Let debugee to finish\n");
    if (!nsk_jvmti_resumeSync())
        return;
}

/* ============================================================================= */

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

jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
    jvmtiEnv* jvmti = nullptr;

    if (!NSK_VERIFY(nsk_jvmti_parseOptions(options)))
        return JNI_ERR;

    timeout = nsk_jvmti_getWaitTime() * 60 * 1000;

    if (!NSK_VERIFY((jvmti =
            nsk_jvmti_createJVMTIEnv(jvm, reserved)) != nullptr))
        return JNI_ERR;

    if (!NSK_VERIFY(nsk_jvmti_setAgentProc(agentProc, nullptr)))
        return JNI_ERR;

    /* add required capabilities */
    {
        jvmtiCapabilities caps;
        memset(&caps, 0, sizeof(caps));
        caps.can_generate_compiled_method_load_events = 1;
        if (!NSK_JVMTI_VERIFY(jvmti->AddCapabilities(&caps)))
            return JNI_ERR;
    }

    /* set event callbacks */
    {
        jvmtiEventCallbacks eventCallbacks;
        memset(&eventCallbacks, 0, sizeof(eventCallbacks));
        eventCallbacks.CompiledMethodLoad = callbackCompiledMethodLoad;
        eventCallbacks.CompiledMethodUnload = callbackCompiledMethodUnload;
        if (!NSK_JVMTI_VERIFY(jvmti->SetEventCallbacks(&eventCallbacks, sizeof(eventCallbacks))))
            return JNI_ERR;

        if (!NSK_JVMTI_VERIFY(jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_COMPILED_METHOD_LOAD, nullptr)))
            return JNI_ERR;
        if (!NSK_JVMTI_VERIFY(jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_COMPILED_METHOD_UNLOAD, nullptr)))
            return JNI_ERR;
    }

    return JNI_OK;
}

/* ============================================================================= */
}
