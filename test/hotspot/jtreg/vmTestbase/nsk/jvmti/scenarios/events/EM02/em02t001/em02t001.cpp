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

extern "C" {

/* ============================================================================= */

/* scaffold objects */
static JNIEnv* jni = NULL;
static jvmtiEnv *jvmti = NULL;
static jlong timeout = 0;
static jrawMonitorID syncLock = NULL;

/* constant names */
#define DEBUGEE_CLASS_NAME      "nsk/jvmti/scenarios/events/EM02/em02t001"
#define START_FIELD_NAME        "startingMonitor"
#define END_FIELD_NAME          "endingMonitor"
#define MAIN_THREAD_NAME        "main"
#define THREAD_FIELD_NAME       "debuggeeThread"
#define OBJECT_FIELD_SIG        "Ljava/lang/Object;"
#define THREAD_FIELD_SIG        "Ljava/lang/Thread;"

static jthread mainThread = NULL;
static jthread debuggeeThread = NULL;
static jobject startObject = NULL;
static jobject endObject = NULL;

#define STEP_AMOUNT 3
#define JVMTI_EVENT_COUNT   (int)(JVMTI_MAX_EVENT_TYPE_VAL - JVMTI_MIN_EVENT_TYPE_VAL + 1)
static int eventCount[JVMTI_EVENT_COUNT];
static int newEventCount[JVMTI_EVENT_COUNT];

/* ============================================================================= */

static jthread
findThread(const char *threadName) {
    jvmtiThreadInfo info;
    jthread *threads = NULL;
    jint threads_count = 0;
    jthread returnValue = NULL;
    int i;

    /* get all live threads */
    if (!NSK_JVMTI_VERIFY(
           NSK_CPP_STUB3(GetAllThreads, jvmti, &threads_count, &threads)))
        return NULL;

    if (!NSK_VERIFY(threads != NULL))
        return NULL;

    /* find tested thread */
    for (i = 0; i < threads_count; i++) {
        if (!NSK_VERIFY(threads[i] != NULL))
            break;

        /* get thread information */
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB3(GetThreadInfo, jvmti, threads[i], &info)))
            break;

        /* find by name */
        if (info.name != NULL && (strcmp(info.name, threadName) == 0)) {
            returnValue = threads[i];
        }
    }

    /* deallocate threads list */
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(Deallocate, jvmti, (unsigned char*)threads)))
        return NULL;

    return returnValue;
}

/* ============================================================================= */

static jobject
getStaticObjField(const char* className, const char* objFieldName,
                    const char* signature) {

    jfieldID fieldID;
    jclass klass = NULL;

    if (!NSK_JNI_VERIFY(jni, (klass =
            NSK_CPP_STUB2(FindClass, jni, className)) != NULL))
        return NULL;

    if (!NSK_JNI_VERIFY(jni, (fieldID =
            NSK_CPP_STUB4(GetStaticFieldID, jni, klass, objFieldName,
                                signature)) != NULL))
        return NULL;

    return NSK_CPP_STUB3(GetStaticObjectField, jni, klass, fieldID);
}

/* ============================================================================= */

static int prepare() {

    if (!NSK_VERIFY((mainThread = findThread(MAIN_THREAD_NAME))!= NULL)) {
        NSK_COMPLAIN1("<%s> thread not found\n", MAIN_THREAD_NAME);
        return NSK_FALSE;
    }

    /* make thread accessable for a long time */
    if (!NSK_JNI_VERIFY(jni, (mainThread =
            NSK_CPP_STUB2(NewGlobalRef, jni, mainThread)) != NULL))
        return NSK_FALSE;

    if (!NSK_VERIFY((startObject =
            getStaticObjField(DEBUGEE_CLASS_NAME, START_FIELD_NAME,
                                    OBJECT_FIELD_SIG)) != NULL))
        return NSK_FALSE;

    /*make object accessable for a long time*/
    if (!NSK_JNI_VERIFY(jni, (startObject =
            NSK_CPP_STUB2(NewGlobalRef, jni, startObject)) != NULL))
        return NSK_FALSE;


    if (!NSK_VERIFY((endObject =
            getStaticObjField(DEBUGEE_CLASS_NAME, END_FIELD_NAME,
                                    OBJECT_FIELD_SIG)) != NULL))
        return NSK_FALSE;

    /*make object accessable for a long time*/
    if (!NSK_JNI_VERIFY(jni, (endObject =
            NSK_CPP_STUB2(NewGlobalRef, jni, endObject)) != NULL))
        return NSK_FALSE;


    if (!NSK_VERIFY((debuggeeThread =
            (jthread)getStaticObjField(DEBUGEE_CLASS_NAME, THREAD_FIELD_NAME,
                                    THREAD_FIELD_SIG)) != NULL))
        return NSK_FALSE;

    /* make thread accessable for a long time */
    if (!NSK_JNI_VERIFY(jni, (debuggeeThread =
            NSK_CPP_STUB2(NewGlobalRef, jni, debuggeeThread)) != NULL))
        return NSK_FALSE;

    return NSK_TRUE;
}

/* ============================================================================= */

static int
clean() {

    /* disable MonitorContendedEnter event */
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB4(SetEventNotificationMode, jvmti, JVMTI_DISABLE,
                JVMTI_EVENT_MONITOR_CONTENDED_ENTER, NULL)))
        nsk_jvmti_setFailStatus();

    /* dispose global references */
    NSK_CPP_STUB2(DeleteGlobalRef, jni, startObject);
    NSK_CPP_STUB2(DeleteGlobalRef, jni, endObject);
    NSK_CPP_STUB2(DeleteGlobalRef, jni, debuggeeThread);
    NSK_CPP_STUB2(DeleteGlobalRef, jni, mainThread);

    startObject = NULL;
    endObject = NULL;
    debuggeeThread = NULL;
    mainThread = NULL;

    return NSK_TRUE;
}

/* ========================================================================== */

static void
showEventStatistics(int step /*int *currentCounts*/) {
    int i;
    const char* str;
    int *currentCounts = (step == 1) ? &eventCount[0] : &newEventCount[0];

    NSK_DISPLAY0("\n");
    NSK_DISPLAY1("Event statistics for %d step:\n", step);
    NSK_DISPLAY0("-----------------------------\n");
    for (i = 0; i < JVMTI_EVENT_COUNT; i++) {
        if (currentCounts[i] > 0) {
            str = TranslateEvent((jvmtiEvent)(i+JVMTI_MIN_EVENT_TYPE_VAL));
            NSK_DISPLAY2("%-40s %7d\n", str, currentCounts[i]);
        }
    }
}

/* ========================================================================== */

/* get thread information */
static void
showThreadInfo(jthread thread) {
    jvmtiThreadInfo info;
    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB3(GetThreadInfo, jvmti, thread, &info)))
        return;

    NSK_DISPLAY2("\tthread (%s): %p\n", info.name, thread);
}

/* ============================================================================= */

static void
changeCount(jvmtiEvent event, int *currentCounts) {

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(RawMonitorEnter, jvmti, syncLock)))
        nsk_jvmti_setFailStatus();

    currentCounts[event - JVMTI_MIN_EVENT_TYPE_VAL]++;

    if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(RawMonitorExit, jvmti, syncLock)))
        nsk_jvmti_setFailStatus();

}

/* ============================================================================= */

int checkEvents(int step) {
    int i;
    jvmtiEvent curr;
    int result = NSK_TRUE;
    int *currentCounts;
    int isExpected = 0;

    switch (step) {
        case 1:
            currentCounts = &eventCount[0];
            break;

        case 2:
        case 3:
            currentCounts = &newEventCount[0];
            break;

        default:
            NSK_COMPLAIN1("Unexpected step no: %d\n", step);
            return NSK_FALSE;
    }

    for (i = 0; i < JVMTI_EVENT_COUNT; i++) {

        curr = (jvmtiEvent)(i + JVMTI_MIN_EVENT_TYPE_VAL);

        switch (step) {
            case 1:
                isExpected = ((curr == JVMTI_EVENT_MONITOR_CONTENDED_ENTER)
                                || (curr == JVMTI_EVENT_MONITOR_CONTENDED_ENTERED)
                                || (curr == JVMTI_EVENT_MONITOR_WAIT)
                                || (curr == JVMTI_EVENT_MONITOR_WAITED)
                                || (curr == JVMTI_EVENT_VM_INIT));
                break;

            case 2:
                isExpected = ((curr == JVMTI_EVENT_MONITOR_CONTENDED_ENTER)
                                || (curr == JVMTI_EVENT_MONITOR_CONTENDED_ENTERED)
                                || (curr == JVMTI_EVENT_MONITOR_WAIT)
                                || (curr == JVMTI_EVENT_MONITOR_WAITED));
                break;

            case 3:
                isExpected = (curr == JVMTI_EVENT_VM_DEATH);
                break;
        }

        if (isExpected) {
            if (currentCounts[i] != 1) {
                    nsk_jvmti_setFailStatus();
                    NSK_COMPLAIN2("Unexpected events number %7d for %s\n\texpected value is 1\n",
                                        currentCounts[i],
                                        TranslateEvent(curr));
                result = NSK_FALSE;
            }
        } else {
            if (currentCounts[i] > 0) {
                NSK_COMPLAIN2("Unexpected event %s was sent %d times\n",
                                    TranslateEvent(curr),
                                    currentCounts[i]);
                result = NSK_FALSE;
            }
        }
    }

    return result;
}

/* ============================================================================= */

/* callbacks */
JNIEXPORT void JNICALL
cbVMInit(jvmtiEnv* jvmti, JNIEnv* jni_env, jthread thread) {
    changeCount(JVMTI_EVENT_VM_INIT, &eventCount[0]);
}

JNIEXPORT void JNICALL
cbVMDeath(jvmtiEnv* jvmti, JNIEnv* jni_env) {
    changeCount(JVMTI_EVENT_VM_DEATH, &newEventCount[0]);
    showEventStatistics(STEP_AMOUNT);
    if (!checkEvents(STEP_AMOUNT)) {
        nsk_jvmti_setFailStatus();
    }

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB2(DestroyRawMonitor, jvmti, syncLock)))
        nsk_jvmti_setFailStatus();

}

void JNICALL
cbException(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                jmethodID method, jlocation location, jobject exception,
                jmethodID catch_method, jlocation catch_location) {
    changeCount(JVMTI_EVENT_EXCEPTION, &eventCount[0]);
}

void JNICALL
cbExceptionCatch(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                jmethodID method, jlocation location, jobject exception) {
    changeCount(JVMTI_EVENT_EXCEPTION_CATCH, &eventCount[0]);
}

void JNICALL
cbSingleStep(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                jmethodID method, jlocation location) {
    changeCount(JVMTI_EVENT_SINGLE_STEP, &eventCount[0]);
}

void JNICALL
cbFramePop(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                jmethodID method, jboolean was_popped_by_exception) {
    changeCount(JVMTI_EVENT_FRAME_POP, &eventCount[0]);
}

void JNICALL
cbBreakpoint(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                jmethodID method, jlocation location) {
    changeCount(JVMTI_EVENT_BREAKPOINT, &eventCount[0]);
}

void JNICALL
cbFieldAccess(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                jmethodID method, jlocation location, jclass field_klass,
                jobject object, jfieldID field) {
    changeCount(JVMTI_EVENT_FIELD_ACCESS, &eventCount[0]);
}

void JNICALL
cbFieldModification(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                jmethodID method, jlocation location, jclass field_klass,
                jobject object, jfieldID field, char signature_type,
                jvalue new_value) {
    changeCount(JVMTI_EVENT_FIELD_MODIFICATION, &eventCount[0]);
}

void JNICALL
cbMethodEntry(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                jmethodID method) {
    changeCount(JVMTI_EVENT_METHOD_ENTRY, &eventCount[0]);
}

void JNICALL
cbMethodExit(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                jmethodID method, jboolean was_popped_by_exception,
                jvalue return_value) {
    changeCount(JVMTI_EVENT_METHOD_EXIT, &eventCount[0]);
}

void JNICALL
cbNativeMethodBind(jvmtiEnv *jvmti_env, JNIEnv* jni_env,jthread thread,
                jmethodID method, void* address, void** new_address_ptr) {
    changeCount(JVMTI_EVENT_NATIVE_METHOD_BIND, &eventCount[0]);
}

void JNICALL
cbCompiledMethodLoad(jvmtiEnv *jvmti_env, jmethodID method, jint code_size,
                const void* code_addr, jint map_length,
                const jvmtiAddrLocationMap* map, const void* compile_info) {
    changeCount(JVMTI_EVENT_COMPILED_METHOD_LOAD, &eventCount[0]);
}

void JNICALL
cbCompiledMethodUnload(jvmtiEnv *jvmti_env, jmethodID method,
                const void* code_addr) {
    changeCount(JVMTI_EVENT_COMPILED_METHOD_UNLOAD, &eventCount[0]);
}

void
handlerMC1(jvmtiEvent event, jvmtiEnv* jvmti, JNIEnv* jni_env,
                            jthread thread, jobject object,
                            jthread expectedThread, jobject expectedObject) {

    if (expectedThread == NULL || expectedObject == NULL)
        return;

    /* check if event is for tested thread and for tested object */
    if (NSK_CPP_STUB3(IsSameObject, jni_env, expectedThread, thread) &&
            NSK_CPP_STUB3(IsSameObject, jni_env, expectedObject, object)) {

        NSK_DISPLAY1("--->%-40s is received\n", TranslateEvent(event));

        showThreadInfo(thread);
        if (NSK_CPP_STUB3(IsSameObject, jni_env, expectedObject, endObject))
            NSK_DISPLAY0("\tobject: 'endingMonitor'\n");
        else
            NSK_DISPLAY0("\tobject: 'startingMonitor'\n");

        changeCount(event, &eventCount[0]);
    }
}

void JNICALL
cbMonitorWait(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                    jobject object, jlong tout) {

    handlerMC1(JVMTI_EVENT_MONITOR_WAIT,
                                jvmti, jni_env,
                                thread, object,
                                mainThread, startObject);
}

void JNICALL
cbMonitorWaited(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                    jobject object, jboolean timed_out) {

    handlerMC1(JVMTI_EVENT_MONITOR_WAITED,
                                jvmti, jni_env,
                                thread, object,
                                mainThread, startObject);
}

JNIEXPORT void JNICALL
cbMonitorContendedEnter(jvmtiEnv* jvmti, JNIEnv* jni_env, jthread thread,
                            jobject object) {

    handlerMC1(JVMTI_EVENT_MONITOR_CONTENDED_ENTER,
                                jvmti, jni_env,
                                thread, object,
                                debuggeeThread, endObject);
}

void JNICALL
cbMonitorContendedEntered(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                            jobject object) {

    handlerMC1(JVMTI_EVENT_MONITOR_CONTENDED_ENTERED,
                                jvmti_env, jni_env,
                                thread, object,
                                debuggeeThread, endObject);
}

void JNICALL
cbGarbageCollectionStart(jvmtiEnv *jvmti_env) {
    changeCount(JVMTI_EVENT_GARBAGE_COLLECTION_START, &eventCount[0]);
}

void JNICALL
cbGarbageCollectionFinish(jvmtiEnv *jvmti_env) {
    changeCount(JVMTI_EVENT_GARBAGE_COLLECTION_FINISH, &eventCount[0]);
}

void JNICALL
cbObjectFree(jvmtiEnv *jvmti_env, jlong tag) {
    changeCount(JVMTI_EVENT_OBJECT_FREE, &eventCount[0]);
}

void JNICALL
cbVMObjectAlloc(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                    jobject object, jclass object_klass, jlong size) {

    changeCount(JVMTI_EVENT_VM_OBJECT_ALLOC, &eventCount[0]);
}

void
handlerMC2(jvmtiEvent event, jvmtiEnv* jvmti, JNIEnv* jni_env,
                            jthread thread, jobject object,
                            jthread expectedThread, jobject expectedObject) {

    if (expectedThread == NULL || expectedObject == NULL)
        return;

    /* check if event is for tested thread and for tested object */
    if (NSK_CPP_STUB3(IsSameObject, jni_env, expectedThread, thread) &&
            NSK_CPP_STUB3(IsSameObject, jni_env, expectedObject, object)) {

        NSK_DISPLAY1("--->%-40s is received (new callbacks)\n", TranslateEvent(event));

        showThreadInfo(thread);
        if (NSK_CPP_STUB3(IsSameObject, jni_env, expectedObject, endObject))
            NSK_DISPLAY0("\tobject: 'endingMonitor'\n");
        else
            NSK_DISPLAY0("\tobject: 'startingMonitor'\n");


        changeCount(event, &newEventCount[0]);
    }
}

void JNICALL
cbNewMonitorWait(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                    jobject object, jlong tout) {

    handlerMC2(JVMTI_EVENT_MONITOR_WAIT,
                                jvmti_env, jni_env,
                                thread, object,
                                mainThread, startObject);
}

void JNICALL
cbNewMonitorWaited(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                    jobject object, jboolean timed_out) {

    handlerMC2(JVMTI_EVENT_MONITOR_WAITED,
                                jvmti, jni_env,
                                thread, object,
                                mainThread, startObject);
}

void JNICALL
cbNewMonitorContendedEntered(jvmtiEnv *jvmti_env, JNIEnv* jni_env, jthread thread,
                            jobject object) {

    handlerMC2(JVMTI_EVENT_MONITOR_CONTENDED_ENTERED,
                                jvmti, jni_env,
                                thread, object,
                                debuggeeThread, endObject);
}

JNIEXPORT void JNICALL
cbNewMonitorContendedEnter(jvmtiEnv* jvmti, JNIEnv* jni_env, jthread thread,
                            jobject object) {

    handlerMC2(JVMTI_EVENT_MONITOR_CONTENDED_ENTER,
                                jvmti, jni_env,
                                thread, object,
                                debuggeeThread, endObject);
}

/* ============================================================================= */

static int enableEvent(jvmtiEvent event) {

    if (nsk_jvmti_isOptionalEvent(event)
            && (event != JVMTI_EVENT_MONITOR_CONTENDED_ENTER)
            && (event != JVMTI_EVENT_MONITOR_CONTENDED_ENTERED)
            && (event != JVMTI_EVENT_MONITOR_WAIT)
            && (event != JVMTI_EVENT_MONITOR_WAITED)) {
        if (!NSK_JVMTI_VERIFY_CODE(JVMTI_ERROR_MUST_POSSESS_CAPABILITY,
                NSK_CPP_STUB4(SetEventNotificationMode, jvmti,
                    JVMTI_ENABLE, event, NULL))) {
            NSK_COMPLAIN1("Unexpected error enabling %s\n",
                TranslateEvent(event));
            return NSK_FALSE;
        }
    } else {
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB4(SetEventNotificationMode, jvmti,
                    JVMTI_ENABLE, event, NULL))) {
            NSK_COMPLAIN1("Unexpected error enabling %s\n",
                TranslateEvent(event));
            return NSK_FALSE;
        }
    }

    return NSK_TRUE;
}

static int enableEventList() {
    int i;
    int result = NSK_TRUE;

    NSK_DISPLAY0("Enable events\n");

    result = enableEvent(JVMTI_EVENT_VM_INIT);

    result = result && enableEvent(JVMTI_EVENT_VM_DEATH);

    /* enabling optional events */
    for (i = 0; i < JVMTI_EVENT_COUNT; i++) {
        jvmtiEvent event = (jvmtiEvent)(i+JVMTI_MIN_EVENT_TYPE_VAL);

        if (nsk_jvmti_isOptionalEvent(event))
            result = result && enableEvent(event);
    }

    if (result == NSK_FALSE) {
        nsk_jvmti_setFailStatus();
        return NSK_FALSE;
    }

    return NSK_TRUE;
}

/* ============================================================================= */

static int
setCallBacks(int step) {

    int i;

    jvmtiEventCallbacks eventCallbacks;
    memset(&eventCallbacks, 0, sizeof(eventCallbacks));

    NSK_DISPLAY0("\n");
    NSK_DISPLAY1("===============step %d===============\n", step);
    NSK_DISPLAY0("\n");
    switch (step) {
        case 1:
            for (i = 0; i < JVMTI_EVENT_COUNT; i++) {
                eventCount[i] = 0;
            }

            eventCallbacks.VMInit                    = cbVMInit;
            eventCallbacks.Exception                 = cbException;
            eventCallbacks.ExceptionCatch            = cbExceptionCatch;
            eventCallbacks.SingleStep                = cbSingleStep;
            eventCallbacks.FramePop                  = cbFramePop;
            eventCallbacks.Breakpoint                = cbBreakpoint;
            eventCallbacks.FieldAccess               = cbFieldAccess;
            eventCallbacks.FieldModification         = cbFieldModification;
            eventCallbacks.MethodEntry               = cbMethodEntry;
            eventCallbacks.MethodExit                = cbMethodExit;
            eventCallbacks.NativeMethodBind          = cbNativeMethodBind;
            eventCallbacks.CompiledMethodLoad        = cbCompiledMethodLoad;
            eventCallbacks.CompiledMethodUnload      = cbCompiledMethodUnload;
            eventCallbacks.MonitorWait               = cbMonitorWait;
            eventCallbacks.MonitorWaited             = cbMonitorWaited;
            eventCallbacks.MonitorContendedEnter     = cbMonitorContendedEnter;
            eventCallbacks.MonitorContendedEntered   = cbMonitorContendedEntered;
            eventCallbacks.GarbageCollectionStart    = cbGarbageCollectionStart;
            eventCallbacks.GarbageCollectionFinish   = cbGarbageCollectionFinish;
            eventCallbacks.ObjectFree                = cbObjectFree;
            eventCallbacks.VMObjectAlloc             = cbVMObjectAlloc;
            break;

        case 2:
            for (i = 0; i < JVMTI_EVENT_COUNT; i++) {
                newEventCount[i] = 0;
            }

            eventCallbacks.MonitorWait               = cbNewMonitorWait;
            eventCallbacks.MonitorWaited             = cbNewMonitorWaited;
            eventCallbacks.MonitorContendedEnter     = cbNewMonitorContendedEnter;
            eventCallbacks.MonitorContendedEntered   = cbNewMonitorContendedEntered;
            break;

        case 3:
            for (i = 0; i < JVMTI_EVENT_COUNT; i++) {
                newEventCount[i] = 0;
            }

            eventCallbacks.VMDeath                   = cbVMDeath;
            break;

    }
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

    int i;
    jni = agentJNI;

    for (i = 1; i <= STEP_AMOUNT; i++) {
        if (i > 1) {
            NSK_DISPLAY0("Check received events\n");

            showEventStatistics(i-1);
            if (!checkEvents(i-1)) {
                nsk_jvmti_setFailStatus();
            }

            if (!setCallBacks(i)) {
                return;
            }

            if (!nsk_jvmti_resumeSync())
                return;
        }

        NSK_DISPLAY0("Wait for debuggee to become ready\n");
        if (!nsk_jvmti_waitForSync(timeout))
            return;

        prepare();

        if (!nsk_jvmti_resumeSync())
            return;


        NSK_DISPLAY0("Waiting events\n"); /* thread started */
        if (!nsk_jvmti_waitForSync(timeout))
            return;

        if (!nsk_jvmti_resumeSync())
            return;

        if (!nsk_jvmti_waitForSync(timeout))
            return;

    }

    if (!clean()) {
        nsk_jvmti_setFailStatus();
        return;
    }

    NSK_DISPLAY0("Let debuggee to finish\n");
    if (!nsk_jvmti_resumeSync())
        return;

}

/* ============================================================================= */

/** Agent library initialization. */
#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_em02t001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_em02t001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_em02t001(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {

    if (!NSK_VERIFY(nsk_jvmti_parseOptions(options)))
        return JNI_ERR;

    timeout = nsk_jvmti_getWaitTime() * 60 * 1000;

    if (!NSK_VERIFY((jvmti = nsk_jvmti_createJVMTIEnv(jvm, reserved)) != NULL))
        return JNI_ERR;

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB3(CreateRawMonitor, jvmti, "_syncLock", &syncLock))) {
        nsk_jvmti_setFailStatus();
        return JNI_ERR;
    }

    {
        jvmtiCapabilities caps;
        memset(&caps, 0, sizeof(caps));

        caps.can_generate_monitor_events = 1;
        if (!NSK_JVMTI_VERIFY(NSK_CPP_STUB2(AddCapabilities, jvmti, &caps)))
            return JNI_ERR;
    }

    if (!setCallBacks(1)) {
        return JNI_ERR;
    }

    nsk_jvmti_showPossessedCapabilities(jvmti);

    if (!enableEventList()) {
        return JNI_ERR;
    }

    if (!NSK_VERIFY(nsk_jvmti_setAgentProc(agentProc, NULL)))
        return JNI_ERR;

    return JNI_OK;
}

/* ============================================================================= */


}
