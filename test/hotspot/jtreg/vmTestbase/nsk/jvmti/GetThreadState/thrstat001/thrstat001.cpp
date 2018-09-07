/*
 * Copyright (c) 2004, 2018, Oracle and/or its affiliates. All rights reserved.
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


#define PASSED  0
#define STATUS_FAILED  2
#define WAIT_START 100
#define WAIT_TIME (2*60*1000)

static jvmtiEnv *jvmti = NULL;
static jvmtiCapabilities caps;
static jvmtiEventCallbacks callbacks;
static jrawMonitorID access_lock;
static jint result = PASSED;
static jboolean printdump = JNI_FALSE;
static jthread thr_ptr = NULL;
static jint state[] = {
    JVMTI_THREAD_STATE_RUNNABLE,
    JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER,
    JVMTI_THREAD_STATE_IN_OBJECT_WAIT
};

static int entry_count = 0;
static int entry_error_count = 0;
static int exit_count = 0;
static int exit_error_count = 0;

void JNICALL VMInit(jvmtiEnv *jvmti_env, JNIEnv *env, jthread thr) {
    jvmtiError err;

    err = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE,
        JVMTI_EVENT_THREAD_START, NULL);
    if (err != JVMTI_ERROR_NONE) {
        printf("Failed to enable THREAD_START event: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }

    if (caps.can_generate_method_entry_events) {
        err = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE,
            JVMTI_EVENT_METHOD_ENTRY, NULL);
        if (err != JVMTI_ERROR_NONE) {
            printf("Failed to enable METHOD_ENTRY event: %s (%d)\n",
                   TranslateError(err), err);
            result = STATUS_FAILED;
        }
    }

    if (caps.can_generate_method_exit_events) {
        err = jvmti_env->SetEventNotificationMode(JVMTI_ENABLE,
            JVMTI_EVENT_METHOD_EXIT, NULL);
        if (err != JVMTI_ERROR_NONE) {
            printf("Failed to enable METHOD_EXIT event: %s (%d)\n",
                   TranslateError(err), err);
            result = STATUS_FAILED;
        }
    }
}

void JNICALL
ThreadStart(jvmtiEnv *jvmti_env, JNIEnv *env, jthread thread) {
    jvmtiError err;
    jvmtiThreadInfo thrInfo;

    err = jvmti_env->RawMonitorEnter(access_lock);
    if (err != JVMTI_ERROR_NONE) {
        printf("(RawMonitorEnter#TS) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }

    err = jvmti_env->GetThreadInfo(thread, &thrInfo);
    if (err != JVMTI_ERROR_NONE) {
        printf("(GetThreadInfo#TS) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }
    if (thrInfo.name != NULL && strcmp(thrInfo.name, "thr1") == 0) {
        thr_ptr = env->NewGlobalRef(thread);
        if (printdump == JNI_TRUE) {
            printf(">>> ThreadStart: \"%s\", 0x%p\n", thrInfo.name, thr_ptr);
        }
    }

    err = jvmti_env->RawMonitorExit(access_lock);
    if (err != JVMTI_ERROR_NONE) {
        printf("(RawMonitorExit#TS) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }
}

void JNICALL MethodEntry(jvmtiEnv *jvmti_env, JNIEnv *env,
        jthread thread, jmethodID mid) {
    jvmtiError err;
    jvmtiThreadInfo thrInfo;
    jint thrState;

    err = jvmti_env->RawMonitorEnter(access_lock);
    if (err != JVMTI_ERROR_NONE) {
        printf("(RawMonitorEnter#ME) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }

    entry_count++;
    err = jvmti_env->GetThreadState(thread, &thrState);
    if (err != JVMTI_ERROR_NONE) {
        printf("(GetThreadState#ME) unexpected error: %s (%d)\n",
            TranslateError(err), err);
        result = STATUS_FAILED;
    }
    if ((thrState & JVMTI_THREAD_STATE_RUNNABLE) == 0) {
        if (entry_error_count == 0) {
            err = jvmti_env->GetThreadInfo(thread, &thrInfo);
            if (err != JVMTI_ERROR_NONE) {
                printf("(GetThreadInfo#ME) unexpected error: %s (%d)\n",
                       TranslateError(err), err);
                result = STATUS_FAILED;
            }
            printf("Wrong thread \"%s\" state on MethodEntry event:\n",
                   thrInfo.name);
            printf("    expected: JVMTI_THREAD_STATE_RUNNABLE\n");
            printf("    got: %s (%d)\n",
                   TranslateState(thrState), thrState);
        }
        entry_error_count++;
        result = STATUS_FAILED;
    }

    err = jvmti_env->RawMonitorExit(access_lock);
    if (err != JVMTI_ERROR_NONE) {
        printf("(RawMonitorExit#ME) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }

}

void JNICALL MethodExit(jvmtiEnv *jvmti_env, JNIEnv *env,
        jthread thread, jmethodID mid,
        jboolean was_poped_by_exception, jvalue return_value) {
    jvmtiError err;
    jvmtiThreadInfo thrInfo;
    jint thrState;

    err = jvmti_env->RawMonitorEnter(access_lock);
    if (err != JVMTI_ERROR_NONE) {
        printf("(RawMonitorEnter#MX) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }

    exit_count++;
    err = jvmti_env->GetThreadState(thread, &thrState);
    if (err != JVMTI_ERROR_NONE) {
        printf("(GetThreadState#MX) unexpected error: %s (%d)\n",
            TranslateError(err), err);
        result = STATUS_FAILED;
    }
    if ((thrState & JVMTI_THREAD_STATE_RUNNABLE) == 0) {
        if (exit_error_count == 0) {
            err = jvmti_env->GetThreadInfo(thread, &thrInfo);
            if (err != JVMTI_ERROR_NONE) {
                printf("(GetThreadInfo#MX) unexpected error: %s (%d)\n",
                       TranslateError(err), err);
                result = STATUS_FAILED;
            }
            printf("Wrong thread \"%s\" state on MethodExit event:\n",
                   thrInfo.name);
            printf("    expected: JVMTI_THREAD_STATE_RUNNABLE\n");
            printf("    got: %s (%d)\n",
                   TranslateState(thrState), thrState);
        }
        exit_error_count++;
        result = STATUS_FAILED;
    }

    err = jvmti_env->RawMonitorExit(access_lock);
    if (err != JVMTI_ERROR_NONE) {
        printf("(RawMonitorExit#MX) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }
}

#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_thrstat001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_thrstat001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_thrstat001(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint  Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
    jint res;
    jvmtiError err;

    if (options != NULL && strcmp(options, "printdump") == 0) {
        printdump = JNI_TRUE;
    }

    res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
    if (res != JNI_OK || jvmti == NULL) {
        printf("Wrong result of a valid call to GetEnv!\n");
        return JNI_ERR;
    }

    err = jvmti->GetPotentialCapabilities(&caps);
    if (err != JVMTI_ERROR_NONE) {
        printf("(GetPotentialCapabilities) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        return JNI_ERR;
    }

    err = jvmti->AddCapabilities(&caps);
    if (err != JVMTI_ERROR_NONE) {
        printf("(AddCapabilities) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        return JNI_ERR;
    }

    err = jvmti->GetCapabilities(&caps);
    if (err != JVMTI_ERROR_NONE) {
        printf("(GetCapabilities) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        return JNI_ERR;
    }

    err = jvmti->CreateRawMonitor("_access_lock", &access_lock);
    if (err != JVMTI_ERROR_NONE) {
        printf("(CreateRawMonitor) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        return JNI_ERR;
    }

    callbacks.VMInit = &VMInit;
    callbacks.ThreadStart = &ThreadStart;
    if (caps.can_generate_method_entry_events) {
        callbacks.MethodEntry = &MethodEntry;
    } else {
        printf("Warning: MethodEntry event is not implemented\n");
    }
    if (caps.can_generate_method_exit_events) {
        callbacks.MethodExit = &MethodExit;
    } else {
        printf("Warning: MethodExit event is not implemented\n");
    }
    err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (err != JVMTI_ERROR_NONE) {
        printf("(SetEventCallbacks) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        return JNI_ERR;
    }

    err = jvmti->SetEventNotificationMode(JVMTI_ENABLE,
        JVMTI_EVENT_VM_INIT, NULL);
    if (err != JVMTI_ERROR_NONE) {
        printf("Failed to enable VM_INIT event: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }

    return JNI_OK;
}

JNIEXPORT void JNICALL
Java_nsk_jvmti_GetThreadState_thrstat001_checkStatus(JNIEnv *env,
        jclass cls, jint statInd) {
    jvmtiError err;
    jrawMonitorID wait_lock;
    jint thrState;
    jint millis;

    if (jvmti == NULL) {
        printf("JVMTI client was not properly loaded!\n");
        result = STATUS_FAILED;
        return;
    }

    if (thr_ptr == NULL) {
        printf("Missing thread \"thr1\" start event\n");
        result = STATUS_FAILED;
        return;
    }

    /* wait until thread gets an expected state */
    err = jvmti->CreateRawMonitor("_wait_lock", &wait_lock);
    if (err != JVMTI_ERROR_NONE) {
        printf("(CreateRawMonitor) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }
    for (millis = WAIT_START; millis < WAIT_TIME; millis <<= 1) {
        err = jvmti->GetThreadState(thr_ptr, &thrState);
        if (err != JVMTI_ERROR_NONE) {
            printf("(GetThreadState#%d) unexpected error: %s (%d)\n",
                statInd, TranslateError(err), err);
            result = STATUS_FAILED;
        }
        if ((thrState & state[statInd]) != 0) {
            break;
        }
        err = jvmti->RawMonitorEnter(wait_lock);
        if (err != JVMTI_ERROR_NONE) {
            printf("(RawMonitorEnter) unexpected error: %s (%d)\n",
                   TranslateError(err), err);
            result = STATUS_FAILED;
        }
        err = jvmti->RawMonitorWait(wait_lock, (jlong)millis);
        if (err != JVMTI_ERROR_NONE) {
            printf("(RawMonitorWait) unexpected error: %s (%d)\n",
                   TranslateError(err), err);
            result = STATUS_FAILED;
        }
        err = jvmti->RawMonitorExit(wait_lock);
        if (err != JVMTI_ERROR_NONE) {
            printf("(RawMonitorExit) unexpected error: %s (%d)\n",
                   TranslateError(err), err);
            result = STATUS_FAILED;
        }
    }
    err = jvmti->DestroyRawMonitor(wait_lock);
    if (err != JVMTI_ERROR_NONE) {
        printf("(DestroyRawMonitor) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }

    if (printdump == JNI_TRUE) {
        printf(">>> thread \"thr1\" (0x%p) state: %s (%d)\n",
            thr_ptr, TranslateState(thrState), thrState);
    }

    if ((thrState & state[statInd]) == 0) {
        printf("Wrong thread \"thr1\" (0x%p) state:\n", thr_ptr);
        printf("    expected: %s (%d)\n",
            TranslateState(state[statInd]), state[statInd]);
        printf("      actual: %s (%d)\n",
            TranslateState(thrState), thrState);
        result = STATUS_FAILED;
    }
}

JNIEXPORT jint JNICALL
Java_nsk_jvmti_GetThreadState_thrstat001_getRes(JNIEnv *env, jclass cls) {
    jvmtiError err;

    err = jvmti->SetEventNotificationMode(JVMTI_DISABLE,
        JVMTI_EVENT_THREAD_START, NULL);
    if (err != JVMTI_ERROR_NONE) {
        printf("Failed to disable THREAD_START event: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }

    if (caps.can_generate_method_entry_events) {
        err = jvmti->SetEventNotificationMode(JVMTI_DISABLE,
            JVMTI_EVENT_METHOD_ENTRY, NULL);
        if (err != JVMTI_ERROR_NONE) {
            printf("Failed to disable METHOD_ENTRY event: %s (%d)\n",
                   TranslateError(err), err);
            result = STATUS_FAILED;
        }
    }

    if (caps.can_generate_method_exit_events) {
        err = jvmti->SetEventNotificationMode(JVMTI_DISABLE,
            JVMTI_EVENT_METHOD_EXIT, NULL);
        if (err != JVMTI_ERROR_NONE) {
            printf("Failed to disable METHOD_EXIT event: %s (%d)\n",
                   TranslateError(err), err);
            result = STATUS_FAILED;
        }
    }

    if (printdump == JNI_TRUE) {
        printf(">>> total number of method entry events = %d\n", entry_count);
        printf(">>> total number of method exit events = %d\n", exit_count);
    }

    if (entry_error_count != 0) {
        printf("Total number of errors on METHOD_ENTRY: %d of %d events\n",
               entry_error_count, entry_count);
    }

    if (exit_error_count != 0) {
        printf("Total number of errors on METHOD_EXIT: %d of %d events\n",
               exit_error_count, exit_count);
    }

    return result;
}

}
