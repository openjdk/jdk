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

#ifndef HPROF_EVENT_H
#define HPROF_EVENT_H

/* From BCI: */
void event_object_init(JNIEnv *env, jthread thread, jobject obj);
void event_newarray(JNIEnv *env, jthread thread, jobject obj);
void event_call(JNIEnv *env, jthread thread,
                ClassIndex cnum, MethodIndex mnum);
void event_return(JNIEnv *env, jthread thread,
                ClassIndex cnum, MethodIndex mnum);

/* From JVMTI: */
void event_class_load(JNIEnv *env, jthread thread, jclass klass, jobject loader);
void event_class_prepare(JNIEnv *env, jthread thread, jclass klass, jobject loader);
void event_thread_start(JNIEnv *env_id, jthread thread);
void event_thread_end(JNIEnv *env_id, jthread thread);
void event_exception_catch(JNIEnv *env, jthread thread, jmethodID method,
                jlocation location, jobject exception);

#endif
