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


#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stddef.h>

#include "jni.h"
#include "jvmti.h"

#include "agent_util.h"

#include "Monitor.hpp"
#include "Thread.hpp"
#include "Agent.hpp"

/* Implementation of the Agent class */

/* Given a jvmtiEnv* and jthread, find the Thread instance */
Thread *
Agent::get_thread(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
{
    jvmtiError err;
    Thread    *t;

    /* This should always be in the Thread Local Storage */
    t = NULL;
    err = jvmti->GetThreadLocalStorage(thread, (void**)&t);
    check_jvmti_error(jvmti, err, "get thread local storage");
    if ( t == NULL ) {
        /* This jthread has never been seen before? */
        stdout_message("WARNING: Never before seen jthread?\n");
        t = new Thread(jvmti, env, thread);
        err = jvmti->SetThreadLocalStorage(thread, (const void*)t);
        check_jvmti_error(jvmti, err, "set thread local storage");
    }
    return t;
}

/* Given a jvmtiEnv* and jobject, find the Monitor instance or create one */
Monitor *
Agent::get_monitor(jvmtiEnv *jvmti, JNIEnv *env, jobject object)
{
    jvmtiError err;
    Monitor   *m;
    jlong      tag;

    m   = NULL;
    tag = (jlong)0;
    err = jvmti->GetTag(object, &tag);
    check_jvmti_error(jvmti, err, "get tag");
    /*LINTED*/
    m = (Monitor *)(void *)(ptrdiff_t)tag;
    if ( m == NULL ) {
        m = new Monitor(jvmti, env, object);
        /* Save monitor on list */
        if (monitor_count == monitor_list_size) {
            monitor_list_size += monitor_list_grow_size;
            monitor_list = (Monitor**)realloc((void*)monitor_list,
                (monitor_list_size)*(int)sizeof(Monitor*));
        }
        monitor_list[monitor_count] = m;
        m->set_slot(monitor_count);
        monitor_count++;
        /*LINTED*/
        tag = (jlong)(ptrdiff_t)(void *)m;
        err = jvmti->SetTag(object, tag);
        check_jvmti_error(jvmti, err, "set tag");
    }
    return m;
}

/* VM initialization and VM death calls to Agent */
Agent::Agent(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
{
    jvmtiError err;

    stdout_message("Agent created..\n");
    stdout_message("VMInit...\n");
    /* Start monitor list */
    monitor_count = 0;
    monitor_list_size = initial_monitor_list_size;
    monitor_list = (Monitor**)
        malloc(monitor_list_size*(int)sizeof(Monitor*));
}

Agent::~Agent()
{
    stdout_message("Agent reclaimed..\n");
}

void Agent::vm_death(jvmtiEnv *jvmti, JNIEnv *env)
{
    jvmtiError err;

    /* Delete all Monitors we allocated */
    for ( int i = 0; i < (int)monitor_count; i++ ) {
        delete monitor_list[i];
    }
    free(monitor_list);
    /* Print death message */
    stdout_message("VMDeath...\n");
}

/* Thread start event, setup a new thread */
void Agent::thread_start(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
{
    jvmtiError err;
    Thread    *t;

    /* Allocate a new Thread instance, put it in the Thread Local
     *    Storage for easy access later.
     */
    t = new Thread(jvmti, env, thread);
    err = jvmti->SetThreadLocalStorage(thread, (const void*)t);
    check_jvmti_error(jvmti, err, "set thread local storage");
}


/* Thread end event, we need to reclaim the space */
void Agent::thread_end(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
{
    jvmtiError err;
    Thread    *t;

    /* Find the thread */
    t = get_thread(jvmti, env, thread);

    /* Clear out the Thread Local Storage */
    err = jvmti->SetThreadLocalStorage(thread, (const void*)NULL);
    check_jvmti_error(jvmti, err, "set thread local storage");

    /* Reclaim the C++ object space */
    delete t;
}

/* Monitor contention begins for a thread. */
void Agent::monitor_contended_enter(jvmtiEnv* jvmti, JNIEnv *env,
             jthread thread, jobject object)
{
    get_monitor(jvmti, env, object)->contended();
    get_thread(jvmti, env, thread)->
                monitor_contended_enter(jvmti, env, thread, object);
}

/* Monitor contention ends for a thread. */
void Agent::monitor_contended_entered(jvmtiEnv* jvmti, JNIEnv *env,
               jthread thread, jobject object)
{
    /* Do nothing for now */
}

/* Monitor wait begins for a thread. */
void Agent::monitor_wait(jvmtiEnv* jvmti, JNIEnv *env,
             jthread thread, jobject object, jlong timeout)
{
    get_monitor(jvmti, env, object)->waited();
    get_thread(jvmti, env, thread)->
                monitor_wait(jvmti, env, thread, object, timeout);
}

/* Monitor wait ends for a thread. */
void Agent::monitor_waited(jvmtiEnv* jvmti, JNIEnv *env,
               jthread thread, jobject object, jboolean timed_out)
{
    if ( timed_out ) {
        get_monitor(jvmti, env, object)->timeout();
    }
    get_thread(jvmti, env, thread)->
                monitor_waited(jvmti, env, thread, object, timed_out);
}

/* A tagged object has been freed */
void Agent::object_free(jvmtiEnv* jvmti, jlong tag)
{
    /* We just cast the tag to a C++ pointer and delete it.
     *   we know it can only be a Monitor *.
     */
    Monitor   *m;
    /*LINTED*/
    m = (Monitor *)(ptrdiff_t)tag;
    if (monitor_count > 1) {
        /* Move the last element to this Monitor's slot */
        int slot = m->get_slot();
        Monitor *last = monitor_list[monitor_count-1];
        monitor_list[slot] = last;
        last->set_slot(slot);
    }
    monitor_count--;
    delete m;
}
