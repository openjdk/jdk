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

#ifndef HPROF_CLASS_H
#define HPROF_CLASS_H

void            class_init(void);
ClassIndex      class_find_or_create(const char *sig, LoaderIndex loader);
ClassIndex      class_create(const char *sig, LoaderIndex loader);
SerialNumber    class_get_serial_number(ClassIndex index);
StringIndex     class_get_signature(ClassIndex index);
ClassStatus     class_get_status(ClassIndex index);
void            class_add_status(ClassIndex index, ClassStatus status);
void            class_all_status_remove(ClassStatus status);
void            class_do_unloads(JNIEnv *env);
void            class_list(void);
void            class_delete_global_references(JNIEnv* env);
void            class_cleanup(void);
void            class_set_methods(ClassIndex index, const char**name,
                                const char**descr,  int count);
jmethodID       class_get_methodID(JNIEnv *env, ClassIndex index,
                                MethodIndex mnum);
jclass          class_new_classref(JNIEnv *env, ClassIndex index,
                                jclass classref);
void            class_delete_classref(JNIEnv *env, ClassIndex index);
jclass          class_get_class(JNIEnv *env, ClassIndex index);
void            class_set_inst_size(ClassIndex index, jint inst_size);
jint            class_get_inst_size(ClassIndex index);
void            class_set_object_index(ClassIndex index,
                                ObjectIndex object_index);
ObjectIndex     class_get_object_index(ClassIndex index);
ClassIndex      class_get_super(ClassIndex index);
void            class_set_super(ClassIndex index, ClassIndex super);
void            class_set_loader(ClassIndex index, LoaderIndex loader);
LoaderIndex     class_get_loader(ClassIndex index);
void            class_prime_system_classes(void);
jint            class_get_all_fields(JNIEnv *env, ClassIndex cnum,
                                     jint *pfield_count, FieldInfo **pfield);

#endif
