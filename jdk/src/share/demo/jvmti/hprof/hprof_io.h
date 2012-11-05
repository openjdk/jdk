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


#ifndef HPROF_IO_H
#define HPROF_IO_H

void io_flush(void);
void io_setup(void);
void io_cleanup(void);

void io_write_file_header(void);
void io_write_file_footer(void);

void io_write_class_load(SerialNumber class_serial_num, ObjectIndex index,
                        SerialNumber trace_serial_num, char *csig);
void io_write_class_unload(SerialNumber class_serial_num, ObjectIndex index);

void io_write_sites_header(const char * comment_str, jint flags,
                        double cutoff, jint total_live_bytes,
                        jint total_live_instances, jlong total_alloced_bytes,
                        jlong total_alloced_instances, jint count);
void io_write_sites_elem(jint index, double ratio, double accum_percent,
                        char *csig, SerialNumber class_serial_num,
                        SerialNumber trace_serial_num,
                        jint n_live_bytes, jint n_live_instances,
                        jint n_alloced_bytes, jint n_alloced_instances);
void io_write_sites_footer(void);

void io_write_thread_start(SerialNumber thread_serial_num, TlsIndex tls_index,
                        SerialNumber trace_serial_num, char *thread_name,
                        char *thread_group_name, char *thread_parent_name);
void io_write_thread_end(SerialNumber thread_serial_num);

void io_write_frame(FrameIndex index, SerialNumber serial_num,
                    char *mname, char *msig,
                    char *sname, SerialNumber class_serial_num,
                        jint lineno);

void io_write_trace_header(SerialNumber trace_serial_num,
                        SerialNumber thread_serial_num, jint n_frames,
                        char * phase_str);
void io_write_trace_elem(SerialNumber trace_serial_num,
                         FrameIndex frame_index, SerialNumber frame_serial_num,
                         char *csig, char *mname,
                         char *sname, jint lineno);
void io_write_trace_footer(SerialNumber trace_serial_num,
                        SerialNumber thread_serial_num, jint n_frames);

void io_write_cpu_samples_header(jlong total_cost, jint n_items);
void io_write_cpu_samples_elem(jint index, double percent, double accum,
                        jint num_hits, jlong cost,
                        SerialNumber trace_serial_num, jint n_frames,
                        char *csig, char *mname);
void io_write_cpu_samples_footer(void);

void io_write_heap_summary(jlong total_live_bytes, jlong total_live_instances,
                        jlong total_alloced_bytes,
                        jlong total_alloced_instances);

void io_write_oldprof_header(void);
void io_write_oldprof_elem(jint num_hits, jint num_frames, char *csig_callee,
                        char *mname_callee, char *msig_callee,
                        char *csig_caller, char *mname_caller,
                        char *msig_caller, jlong cost);
void io_write_oldprof_footer(void);

void io_write_monitor_header(jlong total_time);
void io_write_monitor_elem(jint index, double percent, double accum,
                        jint num_hits, SerialNumber trace_serial_num,
                        char *sig);
void io_write_monitor_footer(void);

void io_write_monitor_sleep(jlong timeout, SerialNumber thread_serial_num);
void io_write_monitor_wait(char *sig, jlong timeout,
                        SerialNumber thread_serial_num);
void io_write_monitor_waited(char *sig, jlong time_waited,
                        SerialNumber thread_serial_num);
void io_write_monitor_exit(char *sig, SerialNumber thread_serial_num);

void io_write_monitor_dump_header(void);
void io_write_monitor_dump_thread_state(SerialNumber thread_serial_num,
                        SerialNumber trace_serial_num,
                        jint threadState);
void io_write_monitor_dump_state(char *sig,
                        SerialNumber thread_serial_num, jint entry_count,
                        SerialNumber *waiters, jint waiter_count,
                        SerialNumber *notify_waiters, jint notify_waiter_count);
void io_write_monitor_dump_footer(void);

void io_heap_header(jlong total_live_instances, jlong total_live_bytes);

void io_heap_root_thread_object(ObjectIndex thread_id,
                        SerialNumber thread_serial_num,
                        SerialNumber trace_serial_num);
void io_heap_root_unknown(ObjectIndex obj_id);
void io_heap_root_jni_global(ObjectIndex obj_id, SerialNumber gref_serial_num,
                        SerialNumber trace_serial_num);
void io_heap_root_jni_local(ObjectIndex obj_id,
                        SerialNumber thread_serial_num, jint frame_depth);
void io_heap_root_system_class(ObjectIndex obj_id, char *sig, SerialNumber class_serial_num);
void io_heap_root_monitor(ObjectIndex obj_id);
void io_heap_root_thread(ObjectIndex obj_id,
                        SerialNumber thread_serial_num);
void io_heap_root_java_frame(ObjectIndex obj_id,
                        SerialNumber thread_serial_num, jint frame_depth);
void io_heap_root_native_stack(ObjectIndex obj_id,
                        SerialNumber thread_serial_num);

void io_heap_class_dump(ClassIndex cnum, char *sig, ObjectIndex class_id,
                        SerialNumber trace_serial_num,
                        ObjectIndex super_id, ObjectIndex loader_id,
                        ObjectIndex signers_id, ObjectIndex domain_id,
                        jint inst_size,
                        jint n_cpool, ConstantPoolValue *cpool,
                        jint n_fields, FieldInfo *fields, jvalue *fvalues);

void io_heap_instance_dump(ClassIndex cnum, ObjectIndex obj_id,
                        SerialNumber trace_serial_num,
                        ObjectIndex class_id, jint size,
                        char *sig, FieldInfo *fields,
                        jvalue *fvalues, jint n_fields);

void io_heap_object_array(ObjectIndex obj_id, SerialNumber trace_serial_num,
                        jint size, jint num_elements, char *sig,
                        ObjectIndex *values, ObjectIndex class_id);
void io_heap_prim_array(ObjectIndex obj_id, SerialNumber trace_serial_num,
                        jint size, jint num_elements, char *sig,
                        void *elements);

void io_heap_footer(void);

#endif
