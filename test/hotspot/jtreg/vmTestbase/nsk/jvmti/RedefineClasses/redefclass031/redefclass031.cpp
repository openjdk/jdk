/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include <jvmti.h>
#include "agent_common.h"
#include "JVMTITools.h"

extern "C" {


#define STATUS_FAILED 2
#define PASSED 0

static jvmtiEnv *jvmti = nullptr;
static jvmtiCapabilities caps;
static jvmtiEventCallbacks callbacks;
static int watch_ev = 0;    /* ignore JVMTI events by default */
static int gen_ev = 0;      /* number of generated events */
static int result = PASSED; /* total result of the test */
static jthread test_thread = nullptr;

static jrawMonitorID watch_ev_monitor;

static void set_watch_ev(JNIEnv *env, int value) {
    jvmti->RawMonitorEnter(watch_ev_monitor);

    if (value) {
        jvmtiError err = jvmti->GetCurrentThread(&test_thread);
        if (err != JVMTI_ERROR_NONE) {
            printf("Failed to get current thread: %s (%d)\n", TranslateError(err), err);
            result = STATUS_FAILED;
        } else {
            test_thread = env->NewGlobalRef(test_thread);
        }
    } else if (test_thread != nullptr) {
        env->DeleteGlobalRef(test_thread);
        test_thread = nullptr;
    }

    watch_ev = value;

    jvmti->RawMonitorExit(watch_ev_monitor);
}

void JNICALL
NativeMethodBind(jvmtiEnv *jvmti_env, JNIEnv *env, jthread thr, jmethodID methodID, void * pAddress, void ** pNewAddress) {
    jvmti->RawMonitorEnter(watch_ev_monitor);

    if (watch_ev) {
        // we are interested only in events on the test thread and VMThread.
        // In case of VMThread we most likely get crash (VMThread is not a Java Thread),
        // but lets check GetThreadInfo - it returns error for non-Java threads.
        if (env->IsSameObject(test_thread, thr)) {
            printf("#### JVMTI_EVENT_NATIVE_METHOD_BIND occured on test thread ####\n");
            gen_ev++;
        } else {
            jvmtiThreadInfo inf;
            jvmtiError err = jvmti_env->GetThreadInfo(thr, &inf);
            if (err != JVMTI_ERROR_NONE) {
                printf("#### JVMTI_EVENT_NATIVE_METHOD_BIND: Failed to get thread info: %s (%d) ####\n",
                    TranslateError(err), err);
                result = STATUS_FAILED;
            } else {
                printf("got JVMTI_EVENT_NATIVE_METHOD_BIND event on thread '%s', ignoring", inf.name);
                jvmti_env->Deallocate((unsigned char *)inf.name);
                if (inf.thread_group != nullptr) {
                    env->DeleteLocalRef(inf.thread_group);
                }
                if (inf.context_class_loader != nullptr) {
                    env->DeleteLocalRef(inf.context_class_loader);
                }
            }
        }
    }

    jvmti->RawMonitorExit(watch_ev_monitor);
}

#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_redefclass031(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_redefclass031(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_redefclass031(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint  Agent_Initialize(JavaVM *vm, char *options, void *reserved) {
    jint res;
    jvmtiError err;

    res = vm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
    if (res != JNI_OK) {
        printf("%s: Failed to call GetEnv: error=%d\n", __FILE__, res);
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

    if (!caps.can_redefine_classes) {
        printf("Warning: RedefineClasses is not implemented\n");
    }

    callbacks.NativeMethodBind = &NativeMethodBind;
    err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (err != JVMTI_ERROR_NONE) {
        printf("(SetEventCallbacks) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        return JNI_ERR;
    }

    err = jvmti->CreateRawMonitor("watch_ev_monitor", &watch_ev_monitor);
    if (err != JVMTI_ERROR_NONE) {
        printf("(CreateRawMonitor) unexpected error: %s (%d)\n",
               TranslateError(err), err);
        return JNI_ERR;
    }

    return JNI_OK;
}

JNIEXPORT jint JNICALL
Java_nsk_jvmti_RedefineClasses_redefclass031_makeRedefinition(JNIEnv *env,
        jclass cls, jint vrb, jclass redefCls, jbyteArray classBytes) {
    jvmtiError err;
    jvmtiClassDefinition classDef;

    if (jvmti == nullptr) {
        printf("JVMTI client was not properly loaded!\n");
        return STATUS_FAILED;
    }

    if (!caps.can_redefine_classes) {
        return PASSED;
    }

    err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_NATIVE_METHOD_BIND, nullptr);
    if (err != JVMTI_ERROR_NONE) {
        printf("Failed to enable JVMTI_EVENT_NATIVE_METHOD_BIND: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }

/* filling the structure jvmtiClassDefinition */
    classDef.klass = redefCls;
    classDef.class_byte_count = env->GetArrayLength(classBytes);
    classDef.class_bytes = (unsigned char *) env->GetByteArrayElements(classBytes, nullptr);

    set_watch_ev(env, 1); /* watch JVMTI events */

    if (vrb == 1)
        printf(">>>>>>>> Invoke RedefineClasses():\n\tnew class byte count=%d\n",
            classDef.class_byte_count);
    err = jvmti->RedefineClasses(1, &classDef);
    if (err != JVMTI_ERROR_NONE) {
        printf("TEST FAILED: the function RedefineClasses() returned error %d: %s\n",
            err, TranslateError(err));
        printf("\tFor more info about this error see the JVMTI spec.\n");
        result = STATUS_FAILED;
    }
    else if (vrb == 1)
        printf("Check #1 PASSED: RedefineClasses() is successfully done\n");

    set_watch_ev(env, 0); /* again ignore JVMTI events */

    if (gen_ev) {
        printf("TEST FAILED: %d unexpected JVMTI events were generated by the function RedefineClasses()\n",
            gen_ev);
        result = STATUS_FAILED;
    } else if (vrb == 1)
        printf("Check #2 PASSED: No unexpected JVMTI events were generated by the function RedefineClasses()\n");

    return(result);
}

JNIEXPORT void JNICALL
Java_nsk_jvmti_RedefineClasses_redefclass031r_nativeMethod(JNIEnv * pEnv, jclass klass)
{
    printf("redefclass031r::nativeMethod is called.\n");
}


}
