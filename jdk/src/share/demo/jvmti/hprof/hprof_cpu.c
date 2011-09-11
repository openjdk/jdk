/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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


#include "hprof.h"

/* This file contains the cpu loop for the option cpu=samples */

/* The cpu_loop thread basically waits for gdata->sample_interval millisecs
 *   then wakes up, and for each running thread it gets their stack trace,
 *   and updates the traces with 'hits'.
 *
 * No threads are suspended or resumed, and the thread sampling is in the
 *   file hprof_tls.c, which manages all active threads. The sampling
 *   technique (what is sampled) is also in hprof_tls.c.
 *
 * No adjustments are made to the pause time or sample interval except
 *   by the user via the interval=n option (default is 10ms).
 *
 * This thread can cause havoc when started prematurely or not terminated
 *   properly, see cpu_sample_init() and cpu_term(), and their calls in hprof_init.c.
 *
 * The listener loop (hprof_listener.c) can dynamically turn on or off the
 *  sampling of all or selected threads.
 *
 */

/* Private functions */

static void JNICALL
cpu_loop_function(jvmtiEnv *jvmti, JNIEnv *env, void *p)
{
    int         loop_trip_counter;
    jboolean    cpu_loop_running;

    loop_trip_counter          = 0;

    rawMonitorEnter(gdata->cpu_loop_lock); {
        gdata->cpu_loop_running = JNI_TRUE;
        cpu_loop_running = gdata->cpu_loop_running;
        /* Notify cpu_sample_init() that we have started */
        rawMonitorNotifyAll(gdata->cpu_loop_lock);
    } rawMonitorExit(gdata->cpu_loop_lock);

    rawMonitorEnter(gdata->cpu_sample_lock); /* Only waits inside loop let go */

    while ( cpu_loop_running ) {

        ++loop_trip_counter;

        LOG3("cpu_loop()", "iteration", loop_trip_counter);

        /* If a dump is in progress, we pause sampling. */
        rawMonitorEnter(gdata->dump_lock); {
            if (gdata->dump_in_process) {
                gdata->pause_cpu_sampling = JNI_TRUE;
            }
        } rawMonitorExit(gdata->dump_lock);

        /* Check to see if we need to pause sampling (listener_loop command) */
        if (gdata->pause_cpu_sampling) {

            /*
             * Pause sampling for now. Reset sample controls if
             * sampling is resumed again.
             */

            rawMonitorWait(gdata->cpu_sample_lock, 0);

            rawMonitorEnter(gdata->cpu_loop_lock); {
                cpu_loop_running = gdata->cpu_loop_running;
            } rawMonitorExit(gdata->cpu_loop_lock);

            /* Continue the while loop, which will terminate if done. */
            continue;
        }

        /* This is the normal short timed wait before getting a sample */
        rawMonitorWait(gdata->cpu_sample_lock,  (jlong)gdata->sample_interval);

        /* Make sure we really want to continue */
        rawMonitorEnter(gdata->cpu_loop_lock); {
            cpu_loop_running = gdata->cpu_loop_running;
        } rawMonitorExit(gdata->cpu_loop_lock);

        /* Break out if we are done */
        if ( !cpu_loop_running ) {
            break;
        }

        /*
         * If a dump request came in after we checked at the top of
         * the while loop, then we catch that fact here. We
         * don't want to perturb the data that is being dumped so
         * we just ignore the data from this sampling loop.
         */
        rawMonitorEnter(gdata->dump_lock); {
            if (gdata->dump_in_process) {
                gdata->pause_cpu_sampling = JNI_TRUE;
            }
        } rawMonitorExit(gdata->dump_lock);

        /* Sample all the threads and update trace costs */
        if ( !gdata->pause_cpu_sampling) {
            tls_sample_all_threads(env);
        }

        /* Check to see if we need to finish */
        rawMonitorEnter(gdata->cpu_loop_lock); {
            cpu_loop_running = gdata->cpu_loop_running;
        } rawMonitorExit(gdata->cpu_loop_lock);

    }
    rawMonitorExit(gdata->cpu_sample_lock);

    rawMonitorEnter(gdata->cpu_loop_lock); {
        /* Notify cpu_sample_term() that we are done. */
        rawMonitorNotifyAll(gdata->cpu_loop_lock);
    } rawMonitorExit(gdata->cpu_loop_lock);

    LOG2("cpu_loop()", "clean termination");
}

/* External functions */

void
cpu_sample_init(JNIEnv *env)
{
    gdata->cpu_sampling  = JNI_TRUE;

    /* Create the raw monitors needed */
    gdata->cpu_loop_lock = createRawMonitor("HPROF cpu loop lock");
    gdata->cpu_sample_lock = createRawMonitor("HPROF cpu sample lock");

    rawMonitorEnter(gdata->cpu_loop_lock); {
        createAgentThread(env, "HPROF cpu sampling thread",
                            &cpu_loop_function);
        /* Wait for cpu_loop_function() to notify us it has started. */
        rawMonitorWait(gdata->cpu_loop_lock, 0);
    } rawMonitorExit(gdata->cpu_loop_lock);
}

void
cpu_sample_off(JNIEnv *env, ObjectIndex object_index)
{
    jint count;

    count = 1;
    if (object_index != 0) {
        tls_set_sample_status(object_index, 0);
        count = tls_sum_sample_status();
    }
    if ( count == 0 ) {
        gdata->pause_cpu_sampling = JNI_TRUE;
    } else {
        gdata->pause_cpu_sampling = JNI_FALSE;
    }
}

void
cpu_sample_on(JNIEnv *env, ObjectIndex object_index)
{
    if ( gdata->cpu_loop_lock == NULL ) {
        cpu_sample_init(env);
    }

    if (object_index == 0) {
        gdata->cpu_sampling             = JNI_TRUE;
        gdata->pause_cpu_sampling       = JNI_FALSE;
    } else {
        jint     count;

        tls_set_sample_status(object_index, 1);
        count = tls_sum_sample_status();
        if ( count > 0 ) {
            gdata->pause_cpu_sampling   = JNI_FALSE;
        }
    }

    /* Notify the CPU sampling thread that sampling is on */
    rawMonitorEnter(gdata->cpu_sample_lock); {
        rawMonitorNotifyAll(gdata->cpu_sample_lock);
    } rawMonitorExit(gdata->cpu_sample_lock);

}

void
cpu_sample_term(JNIEnv *env)
{
    gdata->pause_cpu_sampling   = JNI_FALSE;
    rawMonitorEnter(gdata->cpu_sample_lock); {
        /* Notify the CPU sampling thread to get out of any sampling Wait */
        rawMonitorNotifyAll(gdata->cpu_sample_lock);
    } rawMonitorExit(gdata->cpu_sample_lock);
    rawMonitorEnter(gdata->cpu_loop_lock); {
        if ( gdata->cpu_loop_running ) {
            gdata->cpu_loop_running = JNI_FALSE;
            /* Wait for cpu_loop_function() thread to tell us it completed. */
            rawMonitorWait(gdata->cpu_loop_lock, 0);
        }
    } rawMonitorExit(gdata->cpu_loop_lock);
}
