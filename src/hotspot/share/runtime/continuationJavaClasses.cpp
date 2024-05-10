/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "precompiled.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/javaClassesImpl.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "runtime/continuationJavaClasses.hpp"

// Support for jdk.internal.vm.ContinuationScope

int jdk_internal_vm_ContinuationScope::_name_offset;

#define CONTINUATIONSCOPE_FIELDS_DO(macro) \
  macro(_name_offset, k, vmSymbols::name_name(), string_signature, false);

void jdk_internal_vm_ContinuationScope::compute_offsets() {
  InstanceKlass* k = vmClasses::ContinuationScope_klass();
  CONTINUATIONSCOPE_FIELDS_DO(FIELD_COMPUTE_OFFSET);
}

#if INCLUDE_CDS
void jdk_internal_vm_ContinuationScope::serialize_offsets(SerializeClosure* f) {
  CONTINUATIONSCOPE_FIELDS_DO(FIELD_SERIALIZE_OFFSET);
}
#endif

// Support for jdk.internal.vm.Continuation
int jdk_internal_vm_Continuation::_scope_offset;
int jdk_internal_vm_Continuation::_target_offset;
int jdk_internal_vm_Continuation::_tail_offset;
int jdk_internal_vm_Continuation::_parent_offset;
int jdk_internal_vm_Continuation::_yieldInfo_offset;
int jdk_internal_vm_Continuation::_mounted_offset;
int jdk_internal_vm_Continuation::_done_offset;
int jdk_internal_vm_Continuation::_preempted_offset;

#define CONTINUATION_FIELDS_DO(macro) \
  macro(_scope_offset,     k, vmSymbols::scope_name(),     continuationscope_signature, false); \
  macro(_target_offset,    k, vmSymbols::target_name(),    runnable_signature,          false); \
  macro(_parent_offset,    k, vmSymbols::parent_name(),    continuation_signature,      false); \
  macro(_yieldInfo_offset, k, vmSymbols::yieldInfo_name(), object_signature,            false); \
  macro(_tail_offset,      k, vmSymbols::tail_name(),      stackchunk_signature,        false); \
  macro(_mounted_offset,   k, vmSymbols::mounted_name(),   bool_signature,              false); \
  macro(_done_offset,      k, vmSymbols::done_name(),      bool_signature,              false); \
  macro(_preempted_offset, k, "preempted",                 bool_signature,              false);

void jdk_internal_vm_Continuation::compute_offsets() {
  InstanceKlass* k = vmClasses::Continuation_klass();
  CONTINUATION_FIELDS_DO(FIELD_COMPUTE_OFFSET);
}

#if INCLUDE_CDS
void jdk_internal_vm_Continuation::serialize_offsets(SerializeClosure* f) {
  CONTINUATION_FIELDS_DO(FIELD_SERIALIZE_OFFSET);
}
#endif

// Support for jdk.internal.vm.StackChunk

int jdk_internal_vm_StackChunk::_parent_offset;
int jdk_internal_vm_StackChunk::_size_offset;
int jdk_internal_vm_StackChunk::_sp_offset;
int jdk_internal_vm_StackChunk::_pc_offset;
int jdk_internal_vm_StackChunk::_bottom_offset;
int jdk_internal_vm_StackChunk::_flags_offset;
int jdk_internal_vm_StackChunk::_maxThawingSize_offset;
int jdk_internal_vm_StackChunk::_cont_offset;

#define STACKCHUNK_FIELDS_DO(macro) \
  macro(_parent_offset,  k, vmSymbols::parent_name(),  stackchunk_signature, false); \
  macro(_size_offset,    k, vmSymbols::size_name(),    int_signature,        false); \
  macro(_sp_offset,      k, vmSymbols::sp_name(),      int_signature,        false); \
  macro(_bottom_offset,  k, vmSymbols::bottom_name(),  int_signature,        false);

void jdk_internal_vm_StackChunk::compute_offsets() {
  InstanceKlass* k = vmClasses::StackChunk_klass();
  STACKCHUNK_FIELDS_DO(FIELD_COMPUTE_OFFSET);
  STACKCHUNK_INJECTED_FIELDS(INJECTED_FIELD_COMPUTE_OFFSET);
}

#if INCLUDE_CDS
void jdk_internal_vm_StackChunk::serialize_offsets(SerializeClosure* f) {
  STACKCHUNK_FIELDS_DO(FIELD_SERIALIZE_OFFSET);
  STACKCHUNK_INJECTED_FIELDS(INJECTED_FIELD_SERIALIZE_OFFSET);
}
#endif

