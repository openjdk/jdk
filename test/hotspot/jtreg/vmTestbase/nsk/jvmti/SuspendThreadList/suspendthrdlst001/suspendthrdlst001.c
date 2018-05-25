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

#include <string.h>
#include "jvmti.h"
#include "agent_common.h"
#include "jni_tools.h"
#include "jvmti_tools.h"

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================= */

/* scaffold objects */
static jlong timeout = 0;

/* constant names */
#define THREAD_NAME     "TestedThread"

/* constants */
#define DEFAULT_THREADS_COUNT   10

static int threadsCount = 0;

/* ============================================================================= */

static int fillThreadsByName(jvmtiEnv* jvmti, JNIEnv* jni,
                                const char name[], int foundCount, jthread foundThreads[]);

/** Agent algorithm. */
static void JNICALL
agentProc(jvmtiEnv* jvmti, JNIEnv* jni, void* arg) {

    NSK_DISPLAY0("Wait for threads to start\n");
    if (!nsk_jvmti_waitForSync(timeout))
        return;

    /* perform testing */
    {
        jthread* threads = NULL;
        jvmtiError* results = NULL;
        int i;

        NSK_DISPLAY1("Allocate threads array: %d threads\n", threadsCount);
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB3(Allocate, jvmti, (threadsCount * sizeof(jthread)),
                                                (unsigned char**)&threads))) {
            nsk_jvmti_setFailStatus();
            return;
        }
        NSK_DISPLAY1("  ... allocated array: %p\n", (void*)threads);

        NSK_DISPLAY1("Allocate results array: %d threads\n", threadsCount);
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB3(Allocate, jvmti, (threadsCount * sizeof(jvmtiError)),
                                                (unsigned char**)&results))) {
            nsk_jvmti_setFailStatus();
            return;
        }
        NSK_DISPLAY1("  ... allocated array: %p\n", (void*)threads);

        NSK_DISPLAY1("Find threads: %d threads\n", threadsCount);
        if (!NSK_VERIFY(fillThreadsByName(jvmti, jni, THREAD_NAME, threadsCount, threads)))
            return;

        NSK_DISPLAY0("Suspend threads list\n");
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB4(SuspendThreadList, jvmti, threadsCount, threads, results))) {
            nsk_jvmti_setFailStatus();
            return;
        }

        NSK_DISPLAY0("Check threads results:\n");
        for (i = 0; i < threadsCount; i++) {
            NSK_DISPLAY3("  ... thread #%d: %s (%d)\n",
                                i, TranslateError(results[i]), (int)results[i]);
            if (!NSK_JVMTI_VERIFY(results[i]))
                nsk_jvmti_setFailStatus();
        }

        NSK_DISPLAY0("Let threads to run and finish\n");
        if (!nsk_jvmti_resumeSync())
            return;

        NSK_DISPLAY0("Get state vector for each thread\n");
        for (i = 0; i < threadsCount; i++) {
            jint state = 0;

            NSK_DISPLAY2("  thread #%d (%p):\n", i, (void*)threads[i]);
            if (!NSK_JVMTI_VERIFY(
                    NSK_CPP_STUB3(GetThreadState, jvmti, threads[i], &state))) {
                nsk_jvmti_setFailStatus();
            }
            NSK_DISPLAY2("  ... got state vector: %s (%d)\n",
                            TranslateState(state), (int)state);

            if ((state & JVMTI_THREAD_STATE_SUSPENDED) == 0) {
                NSK_COMPLAIN3("SuspendThreadList() does not turn on flag SUSPENDED for thread #%i:\n"
                              "#   state: %s (%d)\n",
                                i, TranslateState(state), (int)state);
                nsk_jvmti_setFailStatus();
            }
        }

        NSK_DISPLAY0("Resume threads list\n");
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB4(ResumeThreadList, jvmti, threadsCount, threads, results))) {
            nsk_jvmti_setFailStatus();
            return;
        }

        NSK_DISPLAY0("Wait for thread to finish\n");
        if (!nsk_jvmti_waitForSync(timeout))
            return;

        NSK_DISPLAY0("Delete threads references\n");
        for (i = 0; i < threadsCount; i++) {
            if (threads[i] != NULL)
                NSK_TRACE(NSK_CPP_STUB2(DeleteGlobalRef, jni, threads[i]));
        }

        NSK_DISPLAY1("Deallocate threads array: %p\n", (void*)threads);
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(Deallocate, jvmti, (unsigned char*)threads))) {
            nsk_jvmti_setFailStatus();
        }

        NSK_DISPLAY1("Deallocate results array: %p\n", (void*)results);
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(Deallocate, jvmti, (unsigned char*)results))) {
            nsk_jvmti_setFailStatus();
        }
    }

    NSK_DISPLAY0("Let debugee to finish\n");
    if (!nsk_jvmti_resumeSync())
        return;
}

/* ============================================================================= */

/** Find threads whose name starts with specified name prefix. */
static int fillThreadsByName(jvmtiEnv* jvmti, JNIEnv* jni,
                        const char name[], int foundCount, jthread foundThreads[]) {
    jint count = 0;
    jthread* threads = NULL;

    size_t len = strlen(name);
    int found = 0;
    int i;

    for (i = 0; i < foundCount; i++) {
        foundThreads[i] = NULL;
    }

    if (!NSK_JVMTI_VERIFY(
            NSK_CPP_STUB3(GetAllThreads, jvmti, &count, &threads))) {
        nsk_jvmti_setFailStatus();
        return NSK_FALSE;
    }

    found = 0;
    for (i = 0; i < count; i++) {
        jvmtiThreadInfo info;

        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB3(GetThreadInfo, jvmti, threads[i], &info))) {
            nsk_jvmti_setFailStatus();
            break;
        }

        if (info.name != NULL && strncmp(name, info.name, len) == 0) {
            NSK_DISPLAY3("  ... found thread #%d: %p (%s)\n",
                                    found, threads[i], info.name);
            if (found < foundCount)
                foundThreads[found] = threads[i];
            found++;
        }

    }

    if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(Deallocate, jvmti, (unsigned char*)threads))) {
        nsk_jvmti_setFailStatus();
        return NSK_FALSE;
    }

    if (found != foundCount) {
        NSK_COMPLAIN3("Unexpected number of tested threads found:\n"
                      "#   name:     %s\n"
                      "#   found:    %d\n"
                      "#   expected: %d\n",
                      name, found, foundCount);
        nsk_jvmti_setFailStatus();
        return NSK_FALSE;
    }

    NSK_DISPLAY1("Make global references for threads: %d threads\n", foundCount);
    for (i = 0; i < foundCount; i++) {
        if (!NSK_JNI_VERIFY(jni, (foundThreads[i] = (jthread)
                    NSK_CPP_STUB2(NewGlobalRef, jni, foundThreads[i])) != NULL)) {
            nsk_jvmti_setFailStatus();
            return NSK_FALSE;
        }
        NSK_DISPLAY2("  ... thread #%d: %p\n", i, foundThreads[i]);
    }

    return NSK_TRUE;
}

/* ============================================================================= */

/** Agent library initialization. */
#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_suspendthrdlst001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_suspendthrdlst001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_suspendthrdlst001(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
    jvmtiEnv* jvmti = NULL;

    /* init framework and parse options */
    if (!NSK_VERIFY(nsk_jvmti_parseOptions(options)))
        return JNI_ERR;

    timeout = nsk_jvmti_getWaitTime() * 60 * 1000;

    /* get options */
    threadsCount = nsk_jvmti_findOptionIntValue("threads", DEFAULT_THREADS_COUNT);
    if (!NSK_VERIFY(threadsCount > 0))
        return JNI_ERR;

    /* create JVMTI environment */
    if (!NSK_VERIFY((jvmti =
            nsk_jvmti_createJVMTIEnv(jvm, reserved)) != NULL))
        return JNI_ERR;

    /* add specific capabilities for suspending thread */
    {
        jvmtiCapabilities suspendCaps;
        memset(&suspendCaps, 0, sizeof(suspendCaps));
        suspendCaps.can_suspend = 1;
        if (!NSK_JVMTI_VERIFY(
                NSK_CPP_STUB2(AddCapabilities, jvmti, &suspendCaps)))
            return JNI_ERR;
    }

    /* register agent proc and arg */
    if (!NSK_VERIFY(nsk_jvmti_setAgentProc(agentProc, NULL)))
        return JNI_ERR;

    return JNI_OK;
}

/* ============================================================================= */

#ifdef __cplusplus
}
#endif
