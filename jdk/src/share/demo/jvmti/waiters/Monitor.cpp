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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jni.h"
#include "jvmti.h"

#include "agent_util.h"

#include "Monitor.hpp"

/* Implementation of the Monitor class */

Monitor::Monitor(jvmtiEnv *jvmti, JNIEnv *env, jobject object)
{
    jvmtiError err;
    jclass     klass;
    char      *signature;

    /* Clear counters */
    contends  = 0;
    waits     = 0;
    timeouts  = 0;

    /* Get the class name for this monitor object */
    (void)strcpy(name, "Unknown");
    klass = env->GetObjectClass(object);
    if ( klass == NULL ) {
        fatal_error("ERROR: Cannot find jclass from jobject\n");
    }
    err = jvmti->GetClassSignature(klass, &signature, NULL);
    check_jvmti_error(jvmti, err, "get class signature");
    if ( signature != NULL ) {
        (void)strncpy(name, signature, (int)sizeof(name)-1);
        deallocate(jvmti, signature);
    }
}

Monitor::~Monitor()
{
    stdout_message("Monitor %s summary: %d contends, %d waits, %d timeouts\n",
        name, contends, waits, timeouts);
}

int Monitor::get_slot()
{
    return slot;
}

void Monitor::set_slot(int aslot)
{
    slot = aslot;
}

void Monitor::contended()
{
    contends++;
}

void Monitor::waited()
{
    waits++;
}

void Monitor::timeout()
{
    timeouts++;
}
