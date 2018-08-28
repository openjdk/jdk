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

#include <string.h>
#include "jvmti.h"
#include "agent_common.h"
#include "jni_tools.h"
#include "jvmti_tools.h"
#include "JVMTITools.h"

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================= */

/* scaffold objects */
static JNIEnv* jni = NULL;
static jvmtiEnv *jvmti = NULL;
static jlong timeout = 0;
static jrawMonitorID syncLock = NULL;

/* constant names */
#define JVMTI_EVENT_COUNT   (int)(JVMTI_MAX_EVENT_TYPE_VAL - JVMTI_MIN_EVENT_TYPE_VAL + 1)
#define EXPECTED_CLASS_NAME "nsk.jvmti.scenarios.events.EM06.em06t001a"
#define CLASS_LOADER_COUNT_PARAM "classLoaderCount"

static int classLoaderCount = 0;
static int classloadEventCount = 0;
static int classprepareEventCount = 0;

/* ============================================================================= */

/* callbacks */

void
handler(jvmtiEvent event, jvmtiEnv* jvmti, JNIEnv* jni_env,
                    jthread thread, jclass klass) {

    jmethodID methodID;
    jclass classObject;
    jstring jclassName;
    const char *className;

    if (!NSK_JNI_VERIFY(jni_env, (classObject =
            NSK_CPP_STUB2(GetObjectClass, jni_env, klass)) != NULL)) {
        nsk_jvmti_setFailStatus();
        return;
    }

    if (!NSK_JNI_VERIFY(jni_env, (methodID =
            NSK_CPP_STUB4(GetMethodID, jni_env, classObject,
                        "getName", "()Ljava/lang/String;")) != NULL)) {
        nsk_jvmti_setFailStatus();
        return;
    }

    jclassName = (jstring) (jstring) (jstring) (jstring) (jstring) (jstring) (jstring) (jstring) (jstring) NSK_CPP_STUB3(CallObjectMethod, jni_env, klass,
                        methodID);

    className = NSK_CPP_STUB3(GetStringUTFChars, jni_env, jclassName, 0);

    if ( className != NULL && (strcmp(className, EXPECTED_CLASS_NAME)==0) ) {

        if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(RawMonitorEnter, jvmti, syncLock)))
            nsk_jvmti_setFailStatus();

        switch (event) {
            case JVMTI_EVENT_CLASS_LOAD:
                classloadEventCount++; break;
            case JVMTI_EVENT_CLASS_PREPARE:
                classprepareEventCount++; break;
            default:
                NSK_COMPLAIN1("Unexpected event %s", TranslateEvent(event));
                nsk_jvmti_setFailStatus();
        }

        if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(RawMonitorExit, jvmti, syncLock)))
            nsk_jvmti_setFailStatus();

    }

    NSK_CPP_STUB3(ReleaseStringUTFChars, jni_env, jclassName, className);
}

JNIEXPORT void JNICALL
cbClassLoad(jvmtiEnv* jvmti, JNIEnv* jni_env, jthread thread, jclass klass) {

    handler(JVMTI_EVENT_CLASS_LOAD, jvmti, jni_env, thread, klass);
}

JNIEXPORT void JNICALL
cbClassPrepare(jvmtiEnv* jvmti, JNIEnv* jni_env, jthread thread, jclass klass) {

    handler(JVMTI_EVENT_CLASS_PREPARE, jvmti, jni_env, thread, klass);
}

/* ============================================================================= */

static int
enableEvent(jvmtiEventMode enable, jvmtiEvent event) {
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB4(SetEventNotificationMode, jvmti, enable,
                                            event, NULL))) {
        nsk_jvmti_setFailStatus();
        return NSK_FALSE;
    }

    return NSK_TRUE;
}

/* ============================================================================= */

/**
 * Testcase: check tested events.
 *   - check if expected events received for each method
 *
 * Returns NSK_TRUE if test may continue; or NSK_FALSE for test break.
 */
int checkEvents() {

    int result = NSK_TRUE;

    if (classloadEventCount == classLoaderCount) {
        NSK_DISPLAY1("Expected number of JVMTI_EVENT_CLASS_LOAD events %d\n",
                            classloadEventCount);
    } else {
        NSK_COMPLAIN2("Unexpected number of JVMTI_EVENT_CLASS_LOAD events %d\n\texpected value %d\n",
                            classloadEventCount,
                            classLoaderCount);
        result = NSK_FALSE;
    }

    if (classprepareEventCount == classLoaderCount) {
        NSK_DISPLAY1("Expected number of JVMTI_EVENT_CLASS_PREPARE events %d\n",
                            classloadEventCount);
    } else {
        NSK_COMPLAIN2("Unexpected number of JVMTI_EVENT_CLASS_PREPARE events %d\n\texpected value %d\n",
                            classprepareEventCount,
                            classLoaderCount);
        result = NSK_FALSE;
    }

    return result;
}

/* ============================================================================= */

static int
setCallBacks() {
    jvmtiEventCallbacks eventCallbacks;
    memset(&eventCallbacks, 0, sizeof(eventCallbacks));

    eventCallbacks.ClassLoad    = cbClassLoad;
    eventCallbacks.ClassPrepare = cbClassPrepare;

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB3(SetEventCallbacks, jvmti,
                                &eventCallbacks,
                                sizeof(eventCallbacks))))
        return NSK_FALSE;

    return NSK_TRUE;
}

/* ============================================================================= */

/** Agent algorithm. */
static void JNICALL
agentProc(jvmtiEnv* jvmti, JNIEnv* agentJNI, void* arg) {

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB3(CreateRawMonitor, jvmti, "_syncLock", &syncLock))) {
        nsk_jvmti_setFailStatus();
        return;
    }

    jni = agentJNI;

    NSK_DISPLAY0("Wait for debuggee to become ready\n");
    if (!nsk_jvmti_waitForSync(timeout))
        return;

    if (!setCallBacks()) {
        return;
    }

    if (!enableEvent(JVMTI_ENABLE, JVMTI_EVENT_CLASS_LOAD)
            || !enableEvent(JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE)) {
        NSK_COMPLAIN0("Events could not be enabled");
        nsk_jvmti_setFailStatus();
        return;
    }

    NSK_DISPLAY0("Let debuggee to load class\n");
    if (!nsk_jvmti_resumeSync())
        return;

    if (!nsk_jvmti_waitForSync(timeout))
        return;

    if (!checkEvents()) {
        nsk_jvmti_setFailStatus();
    }

    if (!enableEvent(JVMTI_DISABLE, JVMTI_EVENT_CLASS_LOAD)
            || !enableEvent(JVMTI_DISABLE, JVMTI_EVENT_CLASS_PREPARE)) {
        NSK_COMPLAIN0("Events could not be disabled");
        nsk_jvmti_setFailStatus();
    }

    NSK_DISPLAY0("Let debuggee to finish\n");
    if (!nsk_jvmti_resumeSync())
        return;

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(DestroyRawMonitor, jvmti, syncLock)))
        nsk_jvmti_setFailStatus();

}

/* ============================================================================= */

/** Agent library initialization. */
#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_em06t001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_em06t001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_em06t001(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {

    if (!NSK_VERIFY(nsk_jvmti_parseOptions(options)))
        return JNI_ERR;

    timeout = nsk_jvmti_getWaitTime() * 60 * 1000;
    classLoaderCount = nsk_jvmti_findOptionIntValue(CLASS_LOADER_COUNT_PARAM, 100);

    if (!NSK_VERIFY((jvmti = nsk_jvmti_createJVMTIEnv(jvm, reserved)) != NULL))
        return JNI_ERR;

    if (!NSK_VERIFY(nsk_jvmti_setAgentProc(agentProc, NULL)))
        return JNI_ERR;

    return JNI_OK;
}

/* ============================================================================= */


#ifdef __cplusplus
}
#endif
