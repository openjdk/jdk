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


/* Example of using JVMTI events:
 *      JVMTI_EVENT_VM_INIT
 *      JVMTI_EVENT_VM_DEATH
 *      JVMTI_EVENT_THREAD_START
 *      JVMTI_EVENT_THREAD_END
 *      JVMTI_EVENT_MONITOR_CONTENDED_ENTER
 *      JVMTI_EVENT_MONITOR_WAIT
 *      JVMTI_EVENT_MONITOR_WAITED
 *      JVMTI_EVENT_OBJECT_FREE
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jni.h"
#include "jvmti.h"

#include "agent_util.h"

#include "Monitor.hpp"
#include "Thread.hpp"
#include "Agent.hpp"

static jrawMonitorID vm_death_lock;
static jboolean      vm_death_active;

/* Given a jvmtiEnv*, return the C++ Agent class instance */
static Agent *
get_agent(jvmtiEnv *jvmti)
{
    jvmtiError err;
    Agent     *agent;

    agent = NULL;
    err = jvmti->GetEnvironmentLocalStorage((void**)&agent);
    check_jvmti_error(jvmti, err, "get env local storage");
    if ( agent == NULL ) {
        /* This should never happen, but we should check */
        fatal_error("ERROR: GetEnvironmentLocalStorage() returned NULL");
    }
    return agent;
}

/* Enter raw monitor */
static void
menter(jvmtiEnv *jvmti, jrawMonitorID rmon)
{
    jvmtiError err;

    err = jvmti->RawMonitorEnter(rmon);
    check_jvmti_error(jvmti, err, "raw monitor enter");
}

/* Exit raw monitor */
static void
mexit(jvmtiEnv *jvmti, jrawMonitorID rmon)
{
    jvmtiError err;

    err = jvmti->RawMonitorExit(rmon);
    check_jvmti_error(jvmti, err, "raw monitor exit");
}


/* All callbacks need to be extern "C" */
extern "C" {
    static void JNICALL
    vm_init(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
    {
        jvmtiError err;
        Agent     *agent;

        /* Create raw monitor to protect against threads running after death */
        err = jvmti->CreateRawMonitor("Waiters vm_death lock", &vm_death_lock);
        check_jvmti_error(jvmti, err, "create raw monitor");
        vm_death_active = JNI_FALSE;

        /* Create an Agent instance, set JVMTI Local Storage */
        agent = new Agent(jvmti, env, thread);
        err = jvmti->SetEnvironmentLocalStorage((const void*)agent);
        check_jvmti_error(jvmti, err, "set env local storage");

        /* Enable all other events we want */
        err = jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                        JVMTI_EVENT_VM_DEATH, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
        err = jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                        JVMTI_EVENT_THREAD_START, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
        err = jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                        JVMTI_EVENT_THREAD_END, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
        err = jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                        JVMTI_EVENT_MONITOR_CONTENDED_ENTER, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
        err = jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                        JVMTI_EVENT_MONITOR_CONTENDED_ENTERED, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
        err = jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                        JVMTI_EVENT_MONITOR_WAIT, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
        err = jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                        JVMTI_EVENT_MONITOR_WAITED, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
        err = jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                        JVMTI_EVENT_OBJECT_FREE, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
    }
    static void JNICALL
    vm_death(jvmtiEnv *jvmti, JNIEnv *env)
    {
        jvmtiError err;
        Agent     *agent;

        /* Block all callbacks */
        menter(jvmti, vm_death_lock); {
            /* Set flag for other callbacks */
            vm_death_active = JNI_TRUE;

            /* Inform Agent instance of VM_DEATH */
            agent = get_agent(jvmti);
            agent->vm_death(jvmti, env);

            /* Reclaim space of Agent */
            err = jvmti->SetEnvironmentLocalStorage((const void*)NULL);
            check_jvmti_error(jvmti, err, "set env local storage");
            delete agent;
        } mexit(jvmti, vm_death_lock);

    }
    static void JNICALL
    thread_start(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
    {
        menter(jvmti, vm_death_lock); {
            if ( !vm_death_active ) {
                get_agent(jvmti)->thread_start(jvmti, env, thread);
            }
        } mexit(jvmti, vm_death_lock);
    }
    static void JNICALL
    thread_end(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
    {
        menter(jvmti, vm_death_lock); {
            if ( !vm_death_active ) {
                get_agent(jvmti)->thread_end(jvmti, env, thread);
            }
        } mexit(jvmti, vm_death_lock);
    }
    static void JNICALL
    monitor_contended_enter(jvmtiEnv* jvmti, JNIEnv *env,
                 jthread thread, jobject object)
    {
        menter(jvmti, vm_death_lock); {
            if ( !vm_death_active ) {
                get_agent(jvmti)->monitor_contended_enter(jvmti, env,
                                                          thread, object);
            }
        } mexit(jvmti, vm_death_lock);
    }
    static void JNICALL
    monitor_contended_entered(jvmtiEnv* jvmti, JNIEnv *env,
                   jthread thread, jobject object)
    {
        menter(jvmti, vm_death_lock); {
            if ( !vm_death_active ) {
                get_agent(jvmti)->monitor_contended_entered(jvmti, env,
                                                            thread, object);
            }
        } mexit(jvmti, vm_death_lock);
    }
    static void JNICALL
    monitor_wait(jvmtiEnv* jvmti, JNIEnv *env,
                 jthread thread, jobject object, jlong timeout)
    {
        menter(jvmti, vm_death_lock); {
            if ( !vm_death_active ) {
                get_agent(jvmti)->monitor_wait(jvmti, env, thread,
                                               object, timeout);
            }
        } mexit(jvmti, vm_death_lock);
    }
    static void JNICALL
    monitor_waited(jvmtiEnv* jvmti, JNIEnv *env,
                   jthread thread, jobject object, jboolean timed_out)
    {
        menter(jvmti, vm_death_lock); {
            if ( !vm_death_active ) {
                get_agent(jvmti)->monitor_waited(jvmti, env, thread,
                                                 object, timed_out);
            }
        } mexit(jvmti, vm_death_lock);
    }
    static void JNICALL
    object_free(jvmtiEnv* jvmti, jlong tag)
    {
        menter(jvmti, vm_death_lock); {
            if ( !vm_death_active ) {
                get_agent(jvmti)->object_free(jvmti, tag);
            }
        } mexit(jvmti, vm_death_lock);
    }

    /* Agent_OnLoad() is called first, we prepare for a VM_INIT event here. */
    JNIEXPORT jint JNICALL
    Agent_OnLoad(JavaVM *vm, char *options, void *reserved)
    {
        jvmtiEnv           *jvmti;
        jint                rc;
        jvmtiError          err;
        jvmtiCapabilities   capabilities;
        jvmtiEventCallbacks callbacks;

        /* Get JVMTI environment */
        rc = vm->GetEnv((void **)&jvmti, JVMTI_VERSION);
        if (rc != JNI_OK) {
            fatal_error("ERROR: Unable to create jvmtiEnv, GetEnv failed, error=%d\n", rc);
            return -1;
        }

        /* Get/Add JVMTI capabilities */
        (void)memset(&capabilities, 0, sizeof(capabilities));
        capabilities.can_generate_monitor_events        = 1;
        capabilities.can_get_monitor_info               = 1;
        capabilities.can_tag_objects                    = 1;
        capabilities.can_generate_object_free_events    = 1;
        err = jvmti->AddCapabilities(&capabilities);
        check_jvmti_error(jvmti, err, "add capabilities");

        /* Set all callbacks and enable VM_INIT event notification */
        memset(&callbacks, 0, sizeof(callbacks));
        callbacks.VMInit                  = &vm_init;
        callbacks.VMDeath                 = &vm_death;
        callbacks.ThreadStart             = &thread_start;
        callbacks.ThreadEnd               = &thread_end;
        callbacks.MonitorContendedEnter   = &monitor_contended_enter;
        callbacks.MonitorContendedEntered = &monitor_contended_entered;
        callbacks.MonitorWait             = &monitor_wait;
        callbacks.MonitorWaited           = &monitor_waited;
        callbacks.ObjectFree              = &object_free;
        err = jvmti->SetEventCallbacks(&callbacks, (jint)sizeof(callbacks));
        check_jvmti_error(jvmti, err, "set event callbacks");
        err = jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                        JVMTI_EVENT_VM_INIT, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
        return 0;
    }

    /* Agent_OnUnload() is called last */
    JNIEXPORT void JNICALL
    Agent_OnUnload(JavaVM *vm)
    {
    }

} /* of extern "C" */
