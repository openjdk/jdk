/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

#ifndef HPROF_TRACE_H
#define HPROF_TRACE_H

void         trace_increment_all_sample_costs(jint count, jthread *threads,
                        SerialNumber *thread_serial_nums, int depth,
                        jboolean skip_init);

void         trace_get_all_current(jint count, jthread *threads,
                        SerialNumber *thread_serial_nums, int depth,
                        jboolean skip_init, TraceIndex *traces,
                        jboolean always_care);

TraceIndex   trace_get_current(jthread thread,
                        SerialNumber thread_serial_num, int depth,
                        jboolean skip_init,
                        FrameIndex *frames_buffer,
                        jvmtiFrameInfo *jframes_buffer);

void         trace_init(void);
TraceIndex   trace_find_or_create(SerialNumber thread_serial_num,
                        jint n_frames, FrameIndex *frames,
                        jvmtiFrameInfo *jframes_buffer);
SerialNumber trace_get_serial_number(TraceIndex index);
void         trace_increment_cost(TraceIndex index,
                        jint num_hits, jlong self_cost, jlong total_cost);
void         trace_list(void);
void         trace_cleanup(void);

void         trace_clear_cost(void);
void         trace_output_unmarked(JNIEnv *env);
void         trace_output_cost(JNIEnv *env, double cutoff);
void         trace_output_cost_in_prof_format(JNIEnv *env);

#endif
