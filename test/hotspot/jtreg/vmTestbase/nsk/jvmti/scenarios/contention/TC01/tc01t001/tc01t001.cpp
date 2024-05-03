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

extern "C" {

/* ========================================================================== */

/* scaffold objects */
static jlong timeout = 0;

/* test objects */
static jthread thread = nullptr;
static jobject object_M1 = nullptr;
static jobject object_M2 = nullptr;

/* ========================================================================== */

static int prepare(jvmtiEnv* jvmti, JNIEnv* jni) {
    const char* THREAD_NAME = "Debuggee Thread";
    const char* FIELD_SIG = "Ljava/lang/Object;";
    jvmtiThreadInfo info;
    jthread *threads = nullptr;
    jint threads_count = 0;
    jfieldID field = nullptr;
    jclass klass = nullptr;
    int i;

    NSK_DISPLAY0("Prepare: find tested thread\n");

    /* get all live threads */
    if (!NSK_JVMTI_VERIFY(jvmti->GetAllThreads(&threads_count, &threads)))
        return NSK_FALSE;

    if (!NSK_VERIFY(threads_count > 0 && threads != nullptr))
        return NSK_FALSE;

    /* find tested thread */
    for (i = 0; i < threads_count; i++) {
        if (!NSK_VERIFY(threads[i] != nullptr))
            return NSK_FALSE;

        /* get thread information */
        if (!NSK_JVMTI_VERIFY(jvmti->GetThreadInfo(threads[i], &info)))
            return NSK_FALSE;

        NSK_DISPLAY3("    thread #%d (%s): %p\n", i, info.name, threads[i]);

        /* find by name */
        if (info.name != nullptr && (strcmp(info.name, THREAD_NAME) == 0)) {
            thread = threads[i];
        }

        if (info.name != nullptr) {
            if (!NSK_JVMTI_VERIFY(jvmti->Deallocate((unsigned char*)info.name)))
                return NSK_FALSE;
        }
    }

    /* deallocate threads list */
    if (!NSK_JVMTI_VERIFY(jvmti->Deallocate((unsigned char*)threads)))
        return NSK_FALSE;

    /* get tested thread class */
    if (!NSK_JNI_VERIFY(jni, (klass = jni->GetObjectClass(thread)) != nullptr))
        return NSK_FALSE;

    /* get tested thread field 'M1' */
    if (!NSK_JNI_VERIFY(jni, (field = jni->GetFieldID(klass, "M1", FIELD_SIG)) != nullptr))
        return NSK_FALSE;

    if (!NSK_JNI_VERIFY(jni, (object_M1 = jni->GetObjectField(thread, field)) != nullptr))
        return NSK_FALSE;

    /* get tested thread field 'M2' */
    if (!NSK_JNI_VERIFY(jni, (field = jni->GetFieldID(klass, "M2", FIELD_SIG)) != nullptr))
        return NSK_FALSE;

    if (!NSK_JNI_VERIFY(jni, (object_M2 = jni->GetObjectField(thread, field)) != nullptr))
        return NSK_FALSE;

    return NSK_TRUE;
}

/* Check function GetObjectMonitorUsage
 */
static int checkGetObjectMonitorUsage(jvmtiEnv* jvmti, JNIEnv* jni,
        jobject object) {
    jvmtiMonitorUsage inf;
    jvmtiThreadInfo tinf;
    int result = NSK_TRUE;
    int i;

    NSK_DISPLAY1("Checking GetObjectMonitorUsage for 0x%p\n", object);
    if (!NSK_JVMTI_VERIFY(jvmti->GetObjectMonitorUsage(object, &inf)))
        return NSK_FALSE;

    if (nsk_getVerboseMode()) {
        if (inf.owner == nullptr) {
            NSK_DISPLAY0("\towner: none (0x0)\n");
        } else {
            if (!NSK_JVMTI_VERIFY(jvmti->GetThreadInfo(inf.owner, &tinf))) {
                result = NSK_FALSE;
            } else {
                NSK_DISPLAY2("\towner: %s (0x%p)\n", tinf.name, inf.owner);
                if (tinf.name != nullptr) {
                    if (!NSK_JVMTI_VERIFY(jvmti->Deallocate((unsigned char*)tinf.name)))
                        result = NSK_FALSE;
                }
            }
        }

        NSK_DISPLAY1("\tentry_count: %d\n", inf.entry_count);
        NSK_DISPLAY1("\twaiter_count: %d\n", inf.waiter_count);
        if (inf.waiter_count > 0) {
            NSK_DISPLAY0("\twaiters:\n");
            for (i = 0; i < inf.waiter_count; i++) {
                if (!NSK_JVMTI_VERIFY(jvmti->GetThreadInfo(inf.waiters[i], &tinf))) {
                    result = NSK_FALSE;
                } else {
                    NSK_DISPLAY3("\t\t%2d: %s (0x%p)\n",
                        i, tinf.name, inf.waiters[i]);
                    if (tinf.name != nullptr) {
                        if (!NSK_JVMTI_VERIFY(jvmti->Deallocate((unsigned char*)tinf.name)))
                            result = NSK_FALSE;
                    }
                }
            }
        }

        NSK_DISPLAY1("\tnotify_waiter_count: %d\n", inf.notify_waiter_count);
        if (inf.notify_waiter_count > 0) {
            NSK_DISPLAY0("\tnotify_waiters:\n");
            for (i = 0; i < inf.notify_waiter_count; i++) {
                if (!NSK_JVMTI_VERIFY(jvmti->GetThreadInfo(inf.notify_waiters[i], &tinf))) {
                    result = NSK_FALSE;
                } else {
                    NSK_DISPLAY3("\t\t%2d: %s (0x%p)\n",
                        i, tinf.name, inf.notify_waiters[i]);
                    if (tinf.name != nullptr) {
                        if (!NSK_JVMTI_VERIFY(jvmti->Deallocate((unsigned char*)tinf.name)))
                            result = NSK_FALSE;
                    }
                }
            }
        }
    }

    /* check owner to be debugee thread */
    if (!NSK_JNI_VERIFY(jni, (jni->IsSameObject(inf.owner, thread)) == JNI_TRUE))
        result = NSK_FALSE;

    if (!NSK_VERIFY(inf.entry_count == 2))
        result = NSK_FALSE;

    if (!NSK_VERIFY(inf.waiter_count == 0))
        result = NSK_FALSE;

    if (!NSK_VERIFY(inf.notify_waiter_count == 0))
        result = NSK_FALSE;

    /* deallocate monitor waiters arrays */
    if (inf.waiters != nullptr) {
        if (!NSK_JVMTI_VERIFY(jvmti->Deallocate((unsigned char*)inf.waiters)))
            result = NSK_FALSE;
    }
    if (inf.notify_waiters != nullptr) {
        if (!NSK_JVMTI_VERIFY(jvmti->Deallocate((unsigned char*)inf.notify_waiters)))
            result = NSK_FALSE;
    }

    return result;
}

/* ========================================================================== */

/* agent algorithm */
static void JNICALL
agentProc(jvmtiEnv* jvmti, JNIEnv* jni, void* arg) {

    /* wait for initial sync */
    if (!nsk_jvmti_waitForSync(timeout))
        return;

    if (!prepare(jvmti, jni)) {
        nsk_jvmti_setFailStatus();
        return;
    }

    NSK_DISPLAY0("Testcase #1: check checkGetObjectMonitorUsage for M1\n");
    if (!checkGetObjectMonitorUsage(jvmti, jni, object_M1)) {
        nsk_jvmti_setFailStatus();
    }

    NSK_DISPLAY0("Testcase #2: check checkGetObjectMonitorUsage for M2\n");
    if (!checkGetObjectMonitorUsage(jvmti, jni, object_M2)) {
        nsk_jvmti_setFailStatus();
    }

    /* resume debugee after last sync */
    if (!nsk_jvmti_resumeSync())
        return;
}

/* ========================================================================== */

/* agent library initialization */
#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_tc01t001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_tc01t001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_tc01t001(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint Agent_Initialize(JavaVM *jvm, char *options, void *reserved) {
    jvmtiEnv* jvmti = nullptr;
    jvmtiCapabilities caps;

    /* init framework and parse options */
    if (!NSK_VERIFY(nsk_jvmti_parseOptions(options)))
        return JNI_ERR;

    timeout = nsk_jvmti_getWaitTime() * 60000;
    NSK_DISPLAY1("Timeout: %d msc\n", (int)timeout);

    /* create JVMTI environment */
    if (!NSK_VERIFY((jvmti =
            nsk_jvmti_createJVMTIEnv(jvm, reserved)) != nullptr))
        return JNI_ERR;

    /* add capabilities */
    memset(&caps, 0, sizeof(caps));
    caps.can_get_monitor_info = 1;
    if (!NSK_JVMTI_VERIFY(jvmti->AddCapabilities(&caps)))
        return JNI_ERR;

    /* register agent proc and arg */
    if (!NSK_VERIFY(nsk_jvmti_setAgentProc(agentProc, nullptr)))
        return JNI_ERR;

    return JNI_OK;
}

/* ========================================================================== */

}
