/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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


#ifndef HPROF_TLS_H
#define HPROF_TLS_H

void         tls_init(void);
TlsIndex     tls_find_or_create(JNIEnv *env, jthread thread);
void         tls_agent_thread(JNIEnv *env, jthread thread);
SerialNumber tls_get_thread_serial_number(TlsIndex index);
void         tls_list(void);
void         tls_delete_global_references(JNIEnv *env);
void         tls_garbage_collect(JNIEnv *env);
void         tls_cleanup(void);
void         tls_thread_ended(JNIEnv *env, TlsIndex index);
void         tls_sample_all_threads(JNIEnv *env);

MonitorIndex tls_get_monitor(TlsIndex index);
void         tls_set_monitor(TlsIndex index, MonitorIndex monitor_index);

void         tls_set_thread_object_index(TlsIndex index,
                        ObjectIndex thread_object_index);

jint         tls_get_tracker_status(JNIEnv *env, jthread thread,
                        jboolean skip_init, jint **ppstatus, TlsIndex* pindex,
                        SerialNumber *pthread_serial_num,
                        TraceIndex *ptrace_index);

void         tls_set_sample_status(ObjectIndex object_index, jint sample_status);
jint         tls_sum_sample_status(void);

void         tls_dump_traces(JNIEnv *env);

void         tls_monitor_start_timer(TlsIndex index);
jlong        tls_monitor_stop_timer(TlsIndex index);

void         tls_dump_monitor_state(JNIEnv *env);

void         tls_push_method(TlsIndex index, jmethodID method);
void         tls_pop_method(TlsIndex index, jthread thread, jmethodID method);
void         tls_pop_exception_catch(TlsIndex index, jthread thread, jmethodID method);

TraceIndex   tls_get_trace(TlsIndex index, JNIEnv *env,
                           int depth, jboolean skip_init);

void         tls_set_in_heap_dump(TlsIndex index, jint in_heap_dump);
jint         tls_get_in_heap_dump(TlsIndex index);
void         tls_clear_in_heap_dump(void);

TlsIndex     tls_find(SerialNumber thread_serial_num);

#endif
