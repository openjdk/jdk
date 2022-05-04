/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include <stdlib.h>
#include <string.h>
#include "jvmti.h"
#include "agent_common.h"
#include "JVMTITools.h"

extern "C" {


#define PASSED  0
#define STATUS_FAILED  2

typedef struct {
    int cnt;
    const char **thrNames;
} info;

typedef struct  {
    info expected;
    info unexpected;
} threadInfo;

static jvmtiEnv *jvmti;
static jrawMonitorID lock1;
static jrawMonitorID lock2;
static jboolean printdump = JNI_FALSE;
static jint result = PASSED;

static const char main_name[] = "main";
static const char thread1_name[] = "thread1";
static const char sys_thread_name[] = "SysThread";

static const char *main_only[] = { main_name };
static const char *thr1_only[] = { thread1_name };
static const char *sys_only[] = { sys_thread_name };
static const char *main_thr1[] = { main_name, thread1_name };
static const char *main_sys[] = { main_name, sys_thread_name };
static const char *thr1_sys[] = { thread1_name, sys_thread_name };

static threadInfo thrInfo[] = {
    {{1, main_only},    {2, thr1_sys}},
    {{1, main_only},    {2, thr1_sys}},
    {{2, main_thr1},    {1, sys_only}},
    {{1, main_only},    {2, thr1_sys}},
    {{2, main_sys},     {1, thr1_only}}
};

jthread jthr(JNIEnv *env) {
    jclass thrClass;
    jmethodID cid;
    jthread res;
    thrClass = env->FindClass("java/lang/Thread");
    cid = env->GetMethodID(thrClass, "<init>", "(Ljava/lang/String;)V");
    jstring thread_name = env->NewStringUTF(sys_thread_name);
    res = env->NewObject(thrClass, cid, thread_name);
    env->DeleteLocalRef(thread_name);
    return res;
}

static void JNICALL
sys_thread(jvmtiEnv* jvmti, JNIEnv* jni, void *p) {
    jvmtiError err;

    err = jvmti->RawMonitorEnter(lock2);
    if (err != JVMTI_ERROR_NONE) {
        printf("Failed to enter raw monitor 2 (thread): %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }

    /* allows the main thread to wait until the child thread is running */
    err = jvmti->RawMonitorEnter(lock1);
    if (err != JVMTI_ERROR_NONE) {
        printf("Failed to enter raw monitor 1 (thread): %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }
    err = jvmti->RawMonitorNotify(lock1);
    if (err != JVMTI_ERROR_NONE) {
        printf("Failed to notify raw monitor (thread): %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }
    err = jvmti->RawMonitorExit(lock1);
    if (err != JVMTI_ERROR_NONE) {
        printf("Failed to exit raw monitor 1 (thread): %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }

    /* keeps the child thread from exiting */
    err = jvmti->RawMonitorWait(lock2, (jlong)0);
    if (err != JVMTI_ERROR_NONE) {
        printf("Failed to wait raw monitor (thread): %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }
    err = jvmti->RawMonitorExit(lock2);
    if (err != JVMTI_ERROR_NONE) {
        printf("Failed to exit raw monitor 2 (thread): %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }
}

#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_allthr001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_allthr001(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_allthr001(JavaVM *jvm, char *options, void *reserved) {
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
        printf("Wrong result of a valid call to GetEnv !\n");
        return JNI_ERR;
    }

    err = jvmti->CreateRawMonitor("_lock1", &lock1);
    if (err != JVMTI_ERROR_NONE) {
        printf("Failed to create raw monitor 1, err = %d\n", err);
        return JNI_ERR;
    }

    err = jvmti->CreateRawMonitor("_lock2", &lock2);
    if (err != JVMTI_ERROR_NONE) {
        printf("Failed to create raw monitor 2, err = %d\n", err);
        return JNI_ERR;
    }

    return JNI_OK;
}

void releaseThreadInfo(JNIEnv *env, jvmtiThreadInfo *info) {
    jvmti->Deallocate((unsigned char *)info->name);
    if (info->thread_group != NULL) {
        env->DeleteLocalRef(info->thread_group);
    }
    if (info->context_class_loader != NULL) {
        env->DeleteLocalRef(info->context_class_loader);
    }
}

JNIEXPORT void checkInfo(JNIEnv *env, int ind) {
    jint threadsCount = -1;
    jthread *threads;
    jvmtiError err;
    int expected = 0;
    jvmtiThreadInfo inf;

    if (printdump == JNI_TRUE) {
        printf(" >>> Check: %d\n", ind);
    }

    if (ind == 4) {
        err = jvmti->RawMonitorEnter(lock1);
        if (err != JVMTI_ERROR_NONE) {
            printf("Failed to enter raw monitor (check): %s (%d)\n",
                   TranslateError(err), err);
            result = STATUS_FAILED;
        }
        err = jvmti->RunAgentThread(jthr(env), sys_thread, NULL,
                                       JVMTI_THREAD_NORM_PRIORITY);
        if (err != JVMTI_ERROR_NONE) {
            printf("Failed to start agent thread: %s (%d)\n",
                   TranslateError(err), err);
            result = STATUS_FAILED;
        }
        err = jvmti->RawMonitorWait(lock1, (jlong)0);
        if (err != JVMTI_ERROR_NONE) {
            printf("Failed to wait raw monitor (check): %s (%d)\n",
                   TranslateError(err), err);
            result = STATUS_FAILED;
        }
        err = jvmti->RawMonitorExit(lock1);
        if (err != JVMTI_ERROR_NONE) {
            printf("Failed to exit raw monitor (check): %s (%d)\n",
                   TranslateError(err), err);
            result = STATUS_FAILED;
        }
    }

    err = jvmti->GetAllThreads(&threadsCount, &threads);
    if (err != JVMTI_ERROR_NONE) {
        printf("Failed to get all threads (check): %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
        return;
    }

    // check unexpected threads
    for (int i = 0; i < threadsCount; i++) {
        err = jvmti->GetThreadInfo(threads[i], &inf);
        if (err != JVMTI_ERROR_NONE) {
            printf("Failed to get thread info: %s (%d)\n",
                   TranslateError(err), err);
            result = STATUS_FAILED;
            return;
        }
        if (printdump == JNI_TRUE) {
            printf(" >>> %s", inf.name);
        }
        bool found = false;
        for (int j = 0; j < thrInfo[ind].unexpected.cnt && !found; j++) {
            found = (inf.name != NULL && strcmp(inf.name, thrInfo[ind].unexpected.thrNames[j]) == 0);
        }
        if (found) {
            printf("Point %d: detected unexpected thread %s\n", ind, inf.name);
            result = STATUS_FAILED;
        }
        releaseThreadInfo(env, &inf);
    }
    if (printdump == JNI_TRUE) {
        printf("\n");
    }

    // verify all expected threads are present
    for (int i = 0; i < thrInfo[ind].expected.cnt; i++) {
        bool found = false;
        for (int j = 0; j < threadsCount && !found; j++) {
            err = jvmti->GetThreadInfo(threads[j], &inf);
            if (err != JVMTI_ERROR_NONE) {
                printf("Failed to get thread info: %s (%d)\n",
                       TranslateError(err), err);
                result = STATUS_FAILED;
                return;
            }
            found = (inf.name != NULL && strcmp(inf.name, thrInfo[ind].expected.thrNames[j]) == 0);
            releaseThreadInfo(env, &inf);
        }
        if (!found) {
            printf("Point %d: thread %s not detected\n",
                   ind, thrInfo[ind].expected.thrNames[i]);
            result = STATUS_FAILED;
        }
    }

    err = jvmti->Deallocate((unsigned char *)threads);
    if (err != JVMTI_ERROR_NONE) {
        printf("Failed to deallocate array: %s (%d)\n",
               TranslateError(err), err);
        result = STATUS_FAILED;
    }

    if (ind == 4) {
        err = jvmti->RawMonitorEnter(lock2);
        if (err != JVMTI_ERROR_NONE) {
            printf("Failed to enter raw monitor (check): %s (%d)\n",
                   TranslateError(err), err);
            result = STATUS_FAILED;
        }
        err = jvmti->RawMonitorNotify(lock2);
        if (err != JVMTI_ERROR_NONE) {
            printf("Failed to notify raw monitor (check): %s (%d)\n",
                   TranslateError(err), err);
            result = STATUS_FAILED;
        }
        err = jvmti->RawMonitorExit(lock2);
        if (err != JVMTI_ERROR_NONE) {
            printf("Failed to exit raw monitor (check): %s (%d)\n",
                   TranslateError(err), err);
            result = STATUS_FAILED;
        }
    }
}

JNIEXPORT void JNICALL
Java_nsk_jvmti_GetAllThreads_allthr001_checkInfo(JNIEnv *env, jclass cls, jint ind) {
    checkInfo(env, ind);
}

JNIEXPORT jint JNICALL Java_nsk_jvmti_GetAllThreads_allthr001_getRes(JNIEnv *env, jclass cls) {
    return result;
}

}
