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
static int ExceptionEventsCount = 0;
static int ExceptionCatchEventsCount = 0;

// test thread
static const char *testThreadName = "Debuggee Thread";

/* ========================================================================== */

void releaseThreadInfo(jvmtiEnv *jvmti_env, JNIEnv *jni_env, jvmtiThreadInfo *info) {
    jvmti_env->Deallocate((unsigned char *)info->name);
    if (info->thread_group != nullptr) {
        jni_env->DeleteLocalRef(info->thread_group);
    }
    if (info->context_class_loader != nullptr) {
        jni_env->DeleteLocalRef(info->context_class_loader);
    }
}

static bool isTestThread(const char *msg, jvmtiEnv *jvmti_env, JNIEnv *jni_env, jthread thread) {
    jvmtiThreadInfo inf;
    jvmtiError err = jvmti_env->GetThreadInfo(thread, &inf);
    if (err != JVMTI_ERROR_NONE) {
        printf("%s: Failed to get thread info: %s (%d)\n", msg, TranslateError(err), err);
        return false;
    }

    bool result = strcmp(testThreadName, inf.name) == 0;
    if (!result) {
        NSK_DISPLAY2("%s: event on unexpected thread %s\n", msg, inf.name);
    }

    releaseThreadInfo(jvmti_env, jni_env, &inf);

    return result;
}

/** callback functions **/

static void JNICALL
Exception(jvmtiEnv *jvmti_env, JNIEnv *jni_env, jthread thread,
        jmethodID method, jlocation location, jobject exception,
        jmethodID catch_method, jlocation catch_location) {
    jclass klass = nullptr;
    char *signature = nullptr;

    if (!isTestThread("Exception", jvmti_env, jni_env, thread)) {
        return;
    }

    ExceptionEventsCount++;

    if (!NSK_JNI_VERIFY(jni_env, (klass = jni_env->GetObjectClass(exception)) != nullptr)) {
        nsk_jvmti_setFailStatus();
        return;
    }
    if (!NSK_JVMTI_VERIFY(jvmti_env->GetClassSignature(klass, &signature, nullptr))) {
        nsk_jvmti_setFailStatus();
        return;
    }
    NSK_DISPLAY1("Exception event: %s\n", signature);
    if (signature != nullptr)
        jvmti_env->Deallocate((unsigned char*)signature);
}

void JNICALL
ExceptionCatch(jvmtiEnv *jvmti_env, JNIEnv *jni_env, jthread thread,
        jmethodID method, jlocation location, jobject exception) {
    jclass klass = nullptr;
    char *signature = nullptr;

    if (!isTestThread("ExceptionCatch", jvmti_env, jni_env, thread)) {
        return;
    }

    ExceptionCatchEventsCount++;

    if (!NSK_JNI_VERIFY(jni_env, (klass = jni_env->GetObjectClass(exception)) != nullptr)) {
        nsk_jvmti_setFailStatus();
        return;
    }
    if (!NSK_JVMTI_VERIFY(jvmti_env->GetClassSignature(klass, &signature, nullptr))) {
        nsk_jvmti_setFailStatus();
        return;
    }
    NSK_DISPLAY1("ExceptionCatch event: %s\n", signature);
    if (signature != nullptr)
        jvmti_env->Deallocate((unsigned char*)signature);
}

/* ========================================================================== */

/** Agent algorithm. */
static void JNICALL
agentProc(jvmtiEnv* jvmti, JNIEnv* jni, void* arg) {

    if (!nsk_jvmti_waitForSync(timeout))
        return;

    if (!NSK_JVMTI_VERIFY(jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_EXCEPTION, nullptr)))
        nsk_jvmti_setFailStatus();
    if (!NSK_JVMTI_VERIFY(jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_EXCEPTION_CATCH, nullptr)))
        nsk_jvmti_setFailStatus();

    /* resume debugee and wait for sync */
    if (!nsk_jvmti_resumeSync())
        return;
    if (!nsk_jvmti_waitForSync(timeout))
        return;

    if (!NSK_JVMTI_VERIFY(jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_EXCEPTION, nullptr)))
        nsk_jvmti_setFailStatus();
    if (!NSK_JVMTI_VERIFY(jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_EXCEPTION_CATCH, nullptr)))
        nsk_jvmti_setFailStatus();

    NSK_DISPLAY1("Exception events received: %d\n",
        ExceptionEventsCount);
    if (!NSK_VERIFY(ExceptionEventsCount == 3))
        nsk_jvmti_setFailStatus();

    NSK_DISPLAY1("ExceptionCatch events received: %d\n",
        ExceptionCatchEventsCount);
    if (!NSK_VERIFY(ExceptionCatchEventsCount == 3))
        nsk_jvmti_setFailStatus();

    if (!nsk_jvmti_resumeSync())
        return;
}

/* ========================================================================== */

/** Agent library initialization. */
#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_ma10t001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_ma10t001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_ma10t001(JavaVM *jvm, char *options, void *reserved) {
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
    caps.can_generate_exception_events = 1;
    if (!NSK_JVMTI_VERIFY(jvmti->AddCapabilities(&caps))) {
        return JNI_ERR;
    }

    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.Exception = &Exception;
    callbacks.ExceptionCatch = &ExceptionCatch;
    if (!NSK_VERIFY(nsk_jvmti_init_MA(&callbacks)))
        return JNI_ERR;

    return JNI_OK;
}

/* ========================================================================== */

}
