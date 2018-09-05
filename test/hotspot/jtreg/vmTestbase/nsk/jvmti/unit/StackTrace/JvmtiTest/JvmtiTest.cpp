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

/* This test case to test the following:
 *      StackTrace esp. new args
 */

#include <stdio.h>
#include <string.h>
#include "jvmti.h"
#include "agent_common.h"

#ifdef __cplusplus
extern "C" {
#endif

#ifndef JNI_ENV_ARG

#ifdef __cplusplus
#define JNI_ENV_ARG(x, y) y
#define JNI_ENV_ARG1(x)
#define JNI_ENV_PTR(x) x
#else
#define JNI_ENV_ARG(x,y) x, y
#define JNI_ENV_ARG1(x) x
#define JNI_ENV_PTR(x) (*x)
#endif

#endif

#define JVMTI_ENV_ARG JNI_ENV_ARG
#define JVMTI_ENV_ARG1 JNI_ENV_ARG1
#define JVMTI_ENV_PTR JNI_ENV_PTR

#define JVMTI_ERROR_CHECK(str,res) if ( res != JVMTI_ERROR_NONE) { printf(str); printf("%d\n",res); return res;}
#define JVMTI_ERROR_CHECK_EXPECTED_ERROR(str,res,err) if ( res != err) { printf(str); printf("unexpected error %d\n",res); return res;}

#define JVMTI_ERROR_CHECK_VOID(str,res) if ( res != JVMTI_ERROR_NONE) { printf(str); printf("%d\n",res); iGlobalStatus = 2; }

#define JVMTI_ERROR_CHECK_EXPECTED_ERROR_VOID(str,res,err) if ( res != err) { printf(str); printf("unexpected error %d\n",res); iGlobalStatus = 2; }

#define THREADS_LIMIT 2000


jvmtiEnv *jvmti;
jint iGlobalStatus = 0;
jthread susp_thrd[THREADS_LIMIT];
static jvmtiEventCallbacks callbacks;
static jvmtiCapabilities jvmti_caps;
jrawMonitorID jraw_monitor[20];

int process_once = 0;



int printdump = 0;


void debug_printf(const char *fmt, ...) {
    va_list args;

    va_start(args, fmt);
    if (printdump) {
        vprintf(fmt, args);
    }
    va_end(args);
}


#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_JvmtiTest(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_JvmtiTest(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_JvmtiTest(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint Agent_Initialize(JavaVM * jvm, char *options, void *reserved) {
    jint res;

    if (options && strlen(options) > 0) {
        if (strstr(options, "printdump")) {
            printdump = 1;
        }
    }

    res = JNI_ENV_PTR(jvm)->
        GetEnv(JNI_ENV_ARG(jvm, (void **) &jvmti), JVMTI_VERSION_1_1);
    if (res < 0) {
        printf("Wrong result of a valid call to GetEnv!\n");
        return JNI_ERR;
    }


    /* Add capabilities */
    res = JVMTI_ENV_PTR(jvmti)->GetPotentialCapabilities(JVMTI_ENV_ARG(jvmti, &jvmti_caps));
    JVMTI_ERROR_CHECK("GetPotentialCapabilities returned error", res);

    res = JVMTI_ENV_PTR(jvmti)->AddCapabilities(JVMTI_ENV_ARG(jvmti, &jvmti_caps));
    JVMTI_ERROR_CHECK("GetPotentialCapabilities returned error", res);

    return JNI_OK;
}


JNIEXPORT jint JNICALL
Java_nsk_jvmti_unit_StackTrace_JvmtiTest_GetResult(JNIEnv * env, jclass cls) {
    return iGlobalStatus;
}


JNIEXPORT void JNICALL
Java_nsk_jvmti_unit_StackTrace_JvmtiTest_CreateRawMonitor(JNIEnv * env, jclass klass, jint i) {
    jvmtiError ret;
    char sz[128];

    sprintf(sz, "Rawmonitor-%d",i);
    debug_printf("jvmti create raw monitor \n");
    ret = JVMTI_ENV_PTR(jvmti)->CreateRawMonitor(JVMTI_ENV_ARG(jvmti, sz), &jraw_monitor[i]);

    if (ret != JVMTI_ERROR_NONE) {
        printf("Error: ForceGarbageCollection %d \n", ret);
        iGlobalStatus = 2;
    }
}

JNIEXPORT void JNICALL
Java_nsk_jvmti_unit_StackTrace_JvmtiTest_RawMonitorEnter(JNIEnv * env, jclass cls, jint i) {
    jvmtiError ret;

    debug_printf("jvmti Raw monitor enter \n");
    ret = JVMTI_ENV_PTR(jvmti)->RawMonitorEnter(JVMTI_ENV_ARG(jvmti, jraw_monitor[i]));

    if (ret != JVMTI_ERROR_NONE) {
        printf("Error: Raw monitor enter %d \n", ret);
        iGlobalStatus = 2;
    }
}

JNIEXPORT void JNICALL
Java_nsk_jvmti_unit_StackTrace_JvmtiTest_RawMonitorExit(JNIEnv * env, jclass cls, jint i) {
    jvmtiError ret;

    debug_printf("jvmti raw monitor exit \n");
    ret = JVMTI_ENV_PTR(jvmti)->RawMonitorExit(JVMTI_ENV_ARG(jvmti, jraw_monitor[i]));

    if (ret != JVMTI_ERROR_NONE) {
        printf("Error: RawMonitorExit %d \n", ret);
        iGlobalStatus = 2;
    }
}

JNIEXPORT void JNICALL
Java_nsk_jvmti_unit_StackTrace_JvmtiTest_RawMonitorWait(JNIEnv * env, jclass cls, jint i) {
    jvmtiError ret;

    debug_printf("jvmti RawMonitorWait \n");
    ret = JVMTI_ENV_PTR(jvmti)->RawMonitorWait(JVMTI_ENV_ARG(jvmti, jraw_monitor[i]), -1);

    if (ret != JVMTI_ERROR_NONE) {
        printf("Error: RawMonitorWait %d \n", ret);
        iGlobalStatus = 2;
    }
}

JNIEXPORT void JNICALL
Java_nsk_jvmti_unit_StackTrace_JvmtiTest_RawMonitorNotify(JNIEnv * env, jclass cls, jint i) {
    jvmtiError ret;

    debug_printf("jvmti RawMonitorNotify \n");
    ret = JVMTI_ENV_PTR(jvmti)->RawMonitorNotifyAll(JVMTI_ENV_ARG(jvmti, jraw_monitor[i]));

    if (ret != JVMTI_ERROR_NONE) {
        printf("Error: RawMonitorNotify %d \n", ret);
        iGlobalStatus = 2;
    }
}

JNIEXPORT int JNICALL
Java_nsk_jvmti_unit_StackTrace_JvmtiTest_GetFrameCount(JNIEnv * env, jclass cls, jobject thr) {
    jvmtiError ret;
    jint count;

    debug_printf("jvmti GetFrameCount \n");
    ret = JVMTI_ENV_PTR(jvmti)->GetFrameCount(JVMTI_ENV_ARG(jvmti, (jthread)thr),  &count);
    if (ret != JVMTI_ERROR_NONE) {
        printf("Error: GetFrameCount returned  %d \n", ret);
        iGlobalStatus = 2;
    }
    return count;
}

JNIEXPORT void JNICALL
Java_nsk_jvmti_unit_StackTrace_JvmtiTest_GetStackTrace(JNIEnv * env, jclass cls, jobject thr) {
    jvmtiError ret;
    jvmtiFrameInfo *stack_buffer = NULL;
    jvmtiFrameInfo *compare_buffer = NULL;
    jint max_count = 20;
    jint count;
    jclass klass;
    char *mname;
    char *signature;
    char *clname;
    int i;
    int j;


    debug_printf("jvmti GetStackTrace \n");

    ret = JVMTI_ENV_PTR(jvmti)->Allocate(JVMTI_ENV_ARG(jvmti, sizeof(jvmtiFrameInfo) * max_count), (unsigned char**)&stack_buffer);
    if (ret != JVMTI_ERROR_NONE) {
        printf("Error: Allocate failed with  %d \n", ret);
        iGlobalStatus = 2;
    }

    ret = JVMTI_ENV_PTR(jvmti)->Allocate(JVMTI_ENV_ARG(jvmti, sizeof(jvmtiFrameInfo) * max_count), (unsigned char**)&compare_buffer);
    if (ret != JVMTI_ERROR_NONE) {
        printf("Error: Allocate failed with  %d \n", ret);
        iGlobalStatus = 2;
    }


    ret = JVMTI_ENV_PTR(jvmti)->SuspendThread(JVMTI_ENV_ARG(jvmti, thr));
    if (ret != JVMTI_ERROR_NONE) {
        printf("Error: SuspendThread failed with  %d \n", ret);
        iGlobalStatus = 2;
    }

    ret = JVMTI_ENV_PTR(jvmti)->GetStackTrace(JVMTI_ENV_ARG(jvmti, thr), 0, max_count , stack_buffer, &count);
    if (ret != JVMTI_ERROR_NONE) {
        printf("Error: GetStackTrace %d \n", ret);
        iGlobalStatus = 2;
    }

    for (i = 1; i < count; ++i) {
      jint res_count = 48343;
      ret = JVMTI_ENV_PTR(jvmti)->GetStackTrace(JVMTI_ENV_ARG(jvmti, thr), i, max_count , compare_buffer, &res_count);
      if (ret != JVMTI_ERROR_NONE) {
        printf("Error: GetStackTrace2 %d \n", ret);
        iGlobalStatus = 2;
      }
      if (res_count != count - i) {
        printf("Error: mismatch res_count = %d, count = %d, i = %d\n", res_count, count, i);
        iGlobalStatus = 2;
      }
      for (j = 0; j < res_count; j++) {
        if (compare_buffer[j].method != stack_buffer[j+i].method) {
            printf("Error: wrong method\n");
            iGlobalStatus = 2;
        }
      }
    }

    for (i = 1; i < count; ++i) {
      jint res_count = 48343;
      ret = JVMTI_ENV_PTR(jvmti)->GetStackTrace(JVMTI_ENV_ARG(jvmti, thr), -i, max_count , compare_buffer, &res_count);
      if (ret != JVMTI_ERROR_NONE) {
        printf("Error: GetStackTrace2 %d \n", ret);
        iGlobalStatus = 2;
      }
      if (res_count != i) {
        printf("Error: mismatch res_count = %d, i = %d\n", res_count, i);
        iGlobalStatus = 2;
      }
      for (j = 0; j < res_count; j++) {
        if (compare_buffer[j].method != stack_buffer[j+count-i].method) {
            printf("Error: wrong neg method\n");
            iGlobalStatus = 2;
        }
      }
    }

    debug_printf("Count = %d\n", count);


    ret = JVMTI_ENV_PTR(jvmti)->ResumeThread(JVMTI_ENV_ARG(jvmti, thr));
    if (ret != JVMTI_ERROR_NONE) {
        printf("Error: ResumeThread failed with  %d \n", ret);
        iGlobalStatus = 2;
    }

    debug_printf(" Java Stack trace ---\n");

    for (i = 0; i < count; i++) {
        ret = JVMTI_ENV_PTR(jvmti)->GetMethodDeclaringClass(JVMTI_ENV_ARG(jvmti, stack_buffer[i].method), &klass);
        if (ret != JVMTI_ERROR_NONE) {
            printf("Error: GetMethodDeclaringClass %d  \n", ret);
            iGlobalStatus = 2;
            return;
        }

        ret = JVMTI_ENV_PTR(jvmti)->GetClassSignature(JVMTI_ENV_ARG(jvmti, klass), &clname, NULL);
        if (ret != JVMTI_ERROR_NONE) {
            printf("Error: GetClassSignature %d  \n", ret);
            iGlobalStatus = 2;
            return;
        }

        ret = JVMTI_ENV_PTR(jvmti)->GetMethodName(JVMTI_ENV_ARG(jvmti, stack_buffer[i].method), &mname, &signature, NULL);
        if (ret != JVMTI_ERROR_NONE) {
            printf("Error: GetMethodName %d  \n", ret);
            iGlobalStatus = 2;
            return;
        }

        debug_printf("[%d]  %s::%s(%s) at %lld \n",i,clname, mname, signature, stack_buffer[i].location);


    }


    ret = JVMTI_ENV_PTR(jvmti)->Deallocate(JVMTI_ENV_ARG(jvmti, (unsigned char *)stack_buffer));
    if (ret != JVMTI_ERROR_NONE) {
        printf("Error: Deallocate failed with  %d \n", ret);
        iGlobalStatus = 2;
    }


}

JNIEXPORT void JNICALL
Java_nsk_jvmti_unit_StackTrace_JvmtiTest_SaveThreadInfo(JNIEnv * env, jclass cls, jobject oobj) {

}

#ifdef __cplusplus
}
#endif
