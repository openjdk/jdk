/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


/* Example of using JVMTI_EVENT_GARBAGE_COLLECTION_START and
 *                  JVMTI_EVENT_GARBAGE_COLLECTION_FINISH events.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jni.h"
#include "jvmti.h"

/* For stdout_message(), fatal_error(), and check_jvmti_error() */
#include "agent_util.h"

/* Global static data */
static jvmtiEnv     *jvmti;
static int           gc_count;
static jrawMonitorID lock;

/* Worker thread that waits for garbage collections */
static void JNICALL
worker(jvmtiEnv* jvmti, JNIEnv* jni, void *p)
{
    jvmtiError err;

    stdout_message("GC worker started...\n");

    for (;;) {
        err = (*jvmti)->RawMonitorEnter(jvmti, lock);
        check_jvmti_error(jvmti, err, "raw monitor enter");
        while (gc_count == 0) {
            err = (*jvmti)->RawMonitorWait(jvmti, lock, 0);
            if (err != JVMTI_ERROR_NONE) {
                err = (*jvmti)->RawMonitorExit(jvmti, lock);
                check_jvmti_error(jvmti, err, "raw monitor wait");
                return;
            }
        }
        gc_count = 0;

        err = (*jvmti)->RawMonitorExit(jvmti, lock);
        check_jvmti_error(jvmti, err, "raw monitor exit");

        /* Perform arbitrary JVMTI/JNI work here to do post-GC cleanup */
        stdout_message("post-GarbageCollectionFinish actions...\n");
    }
}

/* Creates a new jthread */
static jthread
alloc_thread(JNIEnv *env)
{
    jclass    thrClass;
    jmethodID cid;
    jthread   res;

    thrClass = (*env)->FindClass(env, "java/lang/Thread");
    if ( thrClass == NULL ) {
        fatal_error("Cannot find Thread class\n");
    }
    cid      = (*env)->GetMethodID(env, thrClass, "<init>", "()V");
    if ( cid == NULL ) {
        fatal_error("Cannot find Thread constructor method\n");
    }
    res      = (*env)->NewObject(env, thrClass, cid);
    if ( res == NULL ) {
        fatal_error("Cannot create new Thread object\n");
    }
    return res;
}

/* Callback for JVMTI_EVENT_VM_INIT */
static void JNICALL
vm_init(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
{
    jvmtiError err;

    stdout_message("VMInit...\n");

    err = (*jvmti)->RunAgentThread(jvmti, alloc_thread(env), &worker, NULL,
        JVMTI_THREAD_MAX_PRIORITY);
    check_jvmti_error(jvmti, err, "running agent thread");
}

/* Callback for JVMTI_EVENT_GARBAGE_COLLECTION_START */
static void JNICALL
gc_start(jvmtiEnv* jvmti_env)
{
    stdout_message("GarbageCollectionStart...\n");
}

/* Callback for JVMTI_EVENT_GARBAGE_COLLECTION_FINISH */
static void JNICALL
gc_finish(jvmtiEnv* jvmti_env)
{
    jvmtiError err;

    stdout_message("GarbageCollectionFinish...\n");

    err = (*jvmti)->RawMonitorEnter(jvmti, lock);
    check_jvmti_error(jvmti, err, "raw monitor enter");
    gc_count++;
    err = (*jvmti)->RawMonitorNotify(jvmti, lock);
    check_jvmti_error(jvmti, err, "raw monitor notify");
    err = (*jvmti)->RawMonitorExit(jvmti, lock);
    check_jvmti_error(jvmti, err, "raw monitor exit");
}

/* Agent_OnLoad() is called first, we prepare for a VM_INIT event here. */
JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *vm, char *options, void *reserved)
{
    jint                rc;
    jvmtiError          err;
    jvmtiCapabilities   capabilities;
    jvmtiEventCallbacks callbacks;

    /* Get JVMTI environment */
    rc = (*vm)->GetEnv(vm, (void **)&jvmti, JVMTI_VERSION);
    if (rc != JNI_OK) {
        fatal_error("ERROR: Unable to create jvmtiEnv, rc=%d\n", rc);
        return -1;
    }

    /* Get/Add JVMTI capabilities */
    (void)memset(&capabilities, 0, sizeof(capabilities));
    capabilities.can_generate_garbage_collection_events = 1;
    err = (*jvmti)->AddCapabilities(jvmti, &capabilities);
    check_jvmti_error(jvmti, err, "add capabilities");

    /* Set callbacks and enable event notifications */
    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.VMInit                  = &vm_init;
    callbacks.GarbageCollectionStart  = &gc_start;
    callbacks.GarbageCollectionFinish = &gc_finish;
    err = (*jvmti)->SetEventCallbacks(jvmti, &callbacks, sizeof(callbacks));
    check_jvmti_error(jvmti, err, "set event callbacks");
    err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                        JVMTI_EVENT_VM_INIT, NULL);
    check_jvmti_error(jvmti, err, "set event notification");
    err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                        JVMTI_EVENT_GARBAGE_COLLECTION_START, NULL);
    check_jvmti_error(jvmti, err, "set event notification");
    err = (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                        JVMTI_EVENT_GARBAGE_COLLECTION_FINISH, NULL);
    check_jvmti_error(jvmti, err, "set event notification");

    /* Create the necessary raw monitor */
    err = (*jvmti)->CreateRawMonitor(jvmti, "lock", &lock);
    check_jvmti_error(jvmti, err, "create raw monitor");
    return 0;
}

/* Agent_OnUnload() is called last */
JNIEXPORT void JNICALL
Agent_OnUnload(JavaVM *vm)
{
}
