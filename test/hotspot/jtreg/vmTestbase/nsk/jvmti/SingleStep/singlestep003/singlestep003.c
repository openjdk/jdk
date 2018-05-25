/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "nsk_tools.h"
#include "JVMTITools.h"
#include "jvmti_tools.h"
#include "jni_tools.h"

#ifdef __cplusplus
extern "C" {
#endif

#define STATUS_FAILED 2
#define PASSED 0

/* tested methods */
#define METH_NUM 4
static const char *METHODS[][2] = {
    {"bpMethod", "()V"},
    {"nativeMethod", "()V"},
    {"anotherNativeMethod", "(I)V"},
    {"runThis", "([Ljava/lang/String;Ljava/io/PrintStream;)I"}
};

/* event counters for the tested methods and expected numbers
 of the events */
static volatile long stepEv[][2] = {
    {0, 1},
    {0, 0},
    {0, 0},
    {0, 1}
};

static const char *CLASS_SIG =
    "Lnsk/jvmti/SingleStep/singlestep003;";

static volatile jint result = PASSED;
static jvmtiEnv *jvmti = NULL;
static jvmtiEventCallbacks callbacks;

static void setBP(jvmtiEnv *jvmti_env, JNIEnv *env, jclass klass) {
    jmethodID mid;

    if (!NSK_JNI_VERIFY(env, (mid = NSK_CPP_STUB4(GetMethodID,
            env, klass, METHODS[0][0], METHODS[0][1])) != NULL))
        NSK_CPP_STUB2(FatalError, env,
            "failed to get ID for the java method\n");

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB3(SetBreakpoint,
            jvmti_env, mid, 0)))
        NSK_CPP_STUB2(FatalError, env,
            "failed to set breakpoint\n");
}

/** callback functions **/
void JNICALL
ClassLoad(jvmtiEnv *jvmti_env, JNIEnv *env, jthread thread, jclass klass) {
    char *sig, *generic;

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB4(GetClassSignature,
            jvmti_env, klass, &sig, &generic)))
        NSK_CPP_STUB2(FatalError, env,
            "failed to obtain a class signature\n");

    if (sig != NULL && (strcmp(sig, CLASS_SIG) == 0)) {
            NSK_DISPLAY1("ClassLoad event received for the class \"%s\"\n\
\tsetting breakpoint ...\n",
                sig);
            setBP(jvmti_env, env, klass);
    }
}

void JNICALL
Breakpoint(jvmtiEnv *jvmti_env, JNIEnv *env, jthread thr, jmethodID method,
        jlocation loc) {
    jclass klass;
    char *sig, *generic;

    NSK_DISPLAY0("Breakpoint event received\n");
    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB3(GetMethodDeclaringClass,
            jvmti_env, method, &klass)))
        NSK_COMPLAIN0("TEST FAILURE: unable to get method declaring class\n\n");

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB4(GetClassSignature,
            jvmti_env, klass, &sig, &generic)))
        NSK_CPP_STUB2(FatalError, env,
            "Breakpoint: failed to obtain a class signature\n");

    if (sig != NULL && (strcmp(sig, CLASS_SIG) == 0)) {
        NSK_DISPLAY1("method declaring class \"%s\"\n\tenabling SingleStep events ...\n",
            sig);
        if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB4(SetEventNotificationMode,
                jvmti_env, JVMTI_ENABLE, JVMTI_EVENT_SINGLE_STEP, thr))) {
            result = STATUS_FAILED;
            NSK_COMPLAIN0("TEST FAILURE: cannot enable SingleStep events\n\n");
        }
    } else {
        result = STATUS_FAILED;
        NSK_COMPLAIN1("TEST FAILURE: unexpected breakpoint event in method of class \"%s\"\n\n",
            sig);
    }
}

void JNICALL
SingleStep(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
        jmethodID method, jlocation location) {
    jclass klass;
    char *sig, *generic, *methNam, *methSig;
    int i;

    if (result == STATUS_FAILED) {
        return;
    }

    NSK_DISPLAY0(">>>> SingleStep event received\n");

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB5(GetMethodName,
            jvmti_env, method, &methNam, &methSig, NULL))) {
        result = STATUS_FAILED;
        NSK_COMPLAIN0("TEST FAILED: unable to get method name during SingleStep callback\n\n");
        return;
    }
    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB3(GetMethodDeclaringClass,
            jvmti_env, method, &klass))) {
        result = STATUS_FAILED;
        NSK_COMPLAIN0("TEST FAILED: unable to get method declaring class during SingleStep callback\n\n");
        return;
    }
    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB4(GetClassSignature,
            jvmti_env, klass, &sig, &generic))) {
        result = STATUS_FAILED;
        NSK_COMPLAIN0("TEST FAILED: unable to obtain a class signature during SingleStep callback\n\n");
        return;
    }

    if (sig != NULL) {
        if (stepEv[METH_NUM-1][0] == 1) {
            result = STATUS_FAILED;
            NSK_COMPLAIN0("TEST FAILED: SingleStep event received after disabling the event generation\n\n");
            return;
        }

        for (i=0; i<METH_NUM; i++) {
            if ((strcmp(methNam,METHODS[i][0]) == 0) &&
                    (strcmp(methSig,METHODS[i][1]) == 0) &&
                    (strcmp(sig,CLASS_SIG) == 0)) {
                stepEv[i][0]++;

                if (stepEv[i][1] == 1)
                    NSK_DISPLAY3("CHECK PASSED: SingleStep event received for the method:\n\
\t \"%s %s\" of class \"%s\"\n\tas expected\n",
                        methNam, methSig, sig);
                else {
                    result = STATUS_FAILED;
                    NSK_COMPLAIN3("TEST FAILED: SingleStep event received for the method:\n\
\t \"%s %s\" of class \"%s\"\n",
                    methNam, methSig, sig);
                }

                if (i == (METH_NUM-1)) {
                    NSK_DISPLAY0("Disabling the single step event generation\n");
                    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB4(SetEventNotificationMode,
                            jvmti_env, JVMTI_DISABLE, JVMTI_EVENT_SINGLE_STEP, thread))) {
                        result = STATUS_FAILED;
                        NSK_COMPLAIN0("TEST FAILED: cannot disable SingleStep events\n\n");
                    }
                }
            }
        }
    }

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(Deallocate,
            jvmti_env, (unsigned char*) methNam))) {
        result = STATUS_FAILED;
        NSK_COMPLAIN0("TEST FAILED: unable to deallocate memory pointed to method name\n\n");
    }
    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(Deallocate,
            jvmti_env, (unsigned char*) methSig))) {
        result = STATUS_FAILED;
        NSK_COMPLAIN0("TEST FAILED: unable to deallocate memory pointed to method signature\n\n");
    }

    NSK_DISPLAY0("<<<<\n\n");
}
/************************/

/* dummy method used only to provoke SingleStep events */
JNIEXPORT void JNICALL
Java_nsk_jvmti_SingleStep_singlestep003_anotherNativeMethod(
        JNIEnv *env, jobject obj, jint i) {
    NSK_DISPLAY0("inside the anotherNativeMethod()\n\n");
}

/* dummy method used only to provoke SingleStep events */
JNIEXPORT void JNICALL
Java_nsk_jvmti_SingleStep_singlestep003_nativeMethod(
        JNIEnv *env, jobject obj) {
    jint i = 0;

    NSK_DISPLAY0("inside the nativeMethod()\n\n");
    i++;

    Java_nsk_jvmti_SingleStep_singlestep003_anotherNativeMethod(env, obj, i);
}

JNIEXPORT jint JNICALL
Java_nsk_jvmti_SingleStep_singlestep003_check(
        JNIEnv *env, jobject obj) {
    int i;

    for (i=0; i<METH_NUM; i++)
        if (stepEv[i][0] == 0) {
            if (stepEv[i][1] == 0) {
                NSK_DISPLAY1("CHECK PASSED: no SingleStep events for the method \"%s\" as expected\n\n",
                    METHODS[i][0]);
            }
            else {
                result = STATUS_FAILED;
                NSK_COMPLAIN1("TEST FAILED: no SingleStep events for the method \"%s\"\n\n",
                    METHODS[i][0]);
            }
        }

    return result;
}

#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_singlestep003(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_singlestep003(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_singlestep003(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
    jvmtiCapabilities caps;

    /* init framework and parse options */
    if (!NSK_VERIFY(nsk_jvmti_parseOptions(options)))
        return JNI_ERR;

    /* create JVMTI environment */
    if (!NSK_VERIFY((jvmti =
            nsk_jvmti_createJVMTIEnv(jvm, reserved)) != NULL))
        return JNI_ERR;

    /* add capability to generate compiled method events */
    memset(&caps, 0, sizeof(jvmtiCapabilities));
    caps.can_generate_breakpoint_events = 1;
    caps.can_generate_single_step_events = 1;
    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(AddCapabilities,
            jvmti, &caps)))
        return JNI_ERR;

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(GetCapabilities,
            jvmti, &caps)))
        return JNI_ERR;
    if (!caps.can_generate_single_step_events)
        NSK_DISPLAY0("Warning: generation of single step events is not implemented\n");

    /* set event callback */
    NSK_DISPLAY0("setting event callbacks ...\n");
    (void) memset(&callbacks, 0, sizeof(callbacks));
    callbacks.ClassLoad = &ClassLoad;
    callbacks.Breakpoint = &Breakpoint;
    callbacks.SingleStep = &SingleStep;
    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB3(SetEventCallbacks,
            jvmti, &callbacks, sizeof(callbacks))))
        return JNI_ERR;

    NSK_DISPLAY0("setting event callbacks done\nenabling JVMTI events ...\n");
    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB4(SetEventNotificationMode,
            jvmti, JVMTI_ENABLE, JVMTI_EVENT_CLASS_LOAD, NULL)))
        return JNI_ERR;
    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB4(SetEventNotificationMode,
            jvmti, JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, NULL)))
        return JNI_ERR;
    NSK_DISPLAY0("enabling the events done\n\n");

    return JNI_OK;
}

#ifdef __cplusplus
}
#endif
