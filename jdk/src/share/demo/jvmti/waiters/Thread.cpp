/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include "jvmti.h"

#include "agent_util.h"

#include "Thread.hpp"

/* Implementation of the Thread class */

Thread::Thread(jvmtiEnv *jvmti, JNIEnv *env, jthread thread)
{
    jvmtiError      err;
    jvmtiThreadInfo info;

    /* Get and save the name of the thread */
    info.name = NULL;
    (void)strcpy(name, "Unknown");
    err = jvmti->GetThreadInfo(thread, &info);
    check_jvmti_error(jvmti, err, "get thread info");
    if ( info.name != NULL ) {
        (void)strncpy(name, info.name, (int)sizeof(name)-1);
        name[(int)sizeof(name)-1] = 0;
        deallocate(jvmti, info.name);
    }

    /* Clear thread counters */
    contends = 0;
    waits    = 0;
    timeouts = 0;
}

Thread::~Thread()
{
    /* Send out summary message */
    stdout_message("Thread %s summary: %d waits plus %d contended\n",
        name, waits, contends);
}

void Thread::monitor_contended_enter(jvmtiEnv* jvmti, JNIEnv *env,
             jthread thread, jobject object)
{
    contends++;
}

void Thread::monitor_wait(jvmtiEnv* jvmti, JNIEnv *env,
               jthread thread, jobject object, jlong timeout)
{
    waits++;
}

void Thread::monitor_waited(jvmtiEnv* jvmti, JNIEnv *env,
               jthread thread, jobject object, jboolean timed_out)
{
    if ( timed_out ) {
        timeouts++;
    }
}
