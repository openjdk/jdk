/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <string.h>
#include "jni_tools.hpp"
#include "agent_common.hpp"
#include "jvmti_tools.hpp"

#define PASSED 0
#define STATUS_FAILED 2

extern "C" {

/* ========================================================================== */

/* scaffold objects */
static jlong timeout = 0;

/* event counts */
static int CompiledMethodLoadEventsCount = 0;
static int CompiledMethodUnloadEventsCount = 0;

/* ========================================================================== */

/** callback functions **/

static void JNICALL
CompiledMethodLoad(jvmtiEnv *jvmti_env, jmethodID method,
        jint code_size, const void* code_addr, jint map_length,
        const jvmtiAddrLocationMap* map, const void* compile_info) {
    char *name = nullptr;
    char *signature = nullptr;

    CompiledMethodLoadEventsCount++;

    if (!NSK_JVMTI_VERIFY(jvmti_env->GetMethodName(method, &name, &signature, nullptr))) {
        nsk_jvmti_setFailStatus();
        return;
    }
    NSK_DISPLAY3("CompiledMethodLoad event: %s%s (0x%p)\n",
        name, signature, code_addr);
    if (name != nullptr)
        jvmti_env->Deallocate((unsigned char*)name);
    if (signature != nullptr)
        jvmti_env->Deallocate((unsigned char*)signature);
}

static void JNICALL
CompiledMethodUnload(jvmtiEnv *jvmti_env, jmethodID method,
        const void* code_addr) {
    char *name = nullptr;
    char *sig = nullptr;
    jvmtiError err;
    CompiledMethodUnloadEventsCount++;

    NSK_DISPLAY0("CompiledMethodUnload event received\n");
    // Check for the case that the class has been unloaded
    err = jvmti_env->GetMethodName(method, &name, &sig, nullptr);
    if (err == JVMTI_ERROR_NONE) {
        NSK_DISPLAY3("for: \tmethod: name=\"%s\" signature=\"%s\"\n\tnative address=0x%p\n",
          name, sig, code_addr);
        jvmti_env->Deallocate((unsigned char*)name);
        jvmti_env->Deallocate((unsigned char*)sig);
    }
}

/* ========================================================================== */

/** Agent algorithm. */
static void JNICALL
agentProc(jvmtiEnv* jvmti, JNIEnv* jni, void* arg) {

    if (!nsk_jvmti_waitForSync(timeout))
        return;

    NSK_DISPLAY1("CompiledMethodLoad events received: %d\n",
        CompiledMethodLoadEventsCount);
    if (CompiledMethodLoadEventsCount == 0) {
        NSK_DISPLAY0("# WARNING: no CompiledMethodLoad events\n");
        NSK_DISPLAY0("#    (VM might not compile any methods at all)\n");
    }

    NSK_DISPLAY1("CompiledMethodUnload events received: %d\n",
        CompiledMethodUnloadEventsCount);
    if (CompiledMethodUnloadEventsCount == 0) {
        NSK_DISPLAY0("# WARNING: no CompiledMethodUnload events\n");
        NSK_DISPLAY0("#    (VM might not compile any methods at all)\n");
    }

    if (!nsk_jvmti_resumeSync())
        return;
}

/* ========================================================================== */

/** Agent library initialization. */
#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_ma10t006(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_ma10t006(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_ma10t006(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
    jvmtiEnv* jvmti = nullptr;
    jvmtiCapabilities caps;
    jvmtiEventCallbacks callbacks;

    NSK_DISPLAY0("Agent_OnLoad\n");

    if (!NSK_VERIFY(nsk_jvmti_parseOptions(options)))
        return JNI_ERR;

    timeout = nsk_jvmti_getWaitTime() * 60 * 1000;

    if (!NSK_VERIFY((jvmti =
            nsk_jvmti_createJVMTIEnv(jvm, reserved)) != nullptr))
        return JNI_ERR;

    if (!NSK_VERIFY(nsk_jvmti_setAgentProc(agentProc, nullptr)))
        return JNI_ERR;

    memset(&caps, 0, sizeof(caps));
    caps.can_generate_compiled_method_load_events = 1;
    if (!NSK_JVMTI_VERIFY(jvmti->AddCapabilities(&caps))) {
        return JNI_ERR;
    }

    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.CompiledMethodLoad = &CompiledMethodLoad;
    callbacks.CompiledMethodUnload = &CompiledMethodUnload;
    if (!NSK_VERIFY(nsk_jvmti_init_MA(&callbacks)))
        return JNI_ERR;

    if (!NSK_JVMTI_VERIFY(jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_COMPILED_METHOD_LOAD, nullptr)))
        return JNI_ERR;
    if (!NSK_JVMTI_VERIFY(jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_COMPILED_METHOD_UNLOAD, nullptr)))
        return JNI_ERR;

    return JNI_OK;
}

/* ========================================================================== */

}
