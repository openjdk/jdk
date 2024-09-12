/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_CONTINUATIONJAVACLASSES_INLINE_HPP
#define SHARE_RUNTIME_CONTINUATIONJAVACLASSES_INLINE_HPP

#include "runtime/continuationJavaClasses.hpp"

#include "logging/log.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/stackChunkOop.inline.hpp"
#include "runtime/atomic.hpp"

inline oop jdk_internal_vm_Continuation::scope(oop continuation) {
  return continuation->obj_field(_scope_offset);
}

inline oop jdk_internal_vm_Continuation::parent(oop continuation) {
  return continuation->obj_field(_parent_offset);
}

inline stackChunkOop jdk_internal_vm_Continuation::tail(oop continuation) {
  return stackChunkOopDesc::cast(continuation->obj_field(_tail_offset));
}

inline void jdk_internal_vm_Continuation::set_tail(oop continuation, stackChunkOop value) {
  continuation->obj_field_put(_tail_offset, value);
}

inline bool jdk_internal_vm_Continuation::done(oop continuation) {
  return continuation->bool_field(_done_offset);
}

inline bool jdk_internal_vm_Continuation::is_preempted(oop continuation) {
  return continuation->bool_field(_preempted_offset);
}

inline void jdk_internal_vm_Continuation::set_preempted(oop continuation, bool value) {
  continuation->bool_field_put(_preempted_offset, (jboolean)value);
}

// ----------------------------------------------------------------------

inline oop jdk_internal_vm_StackChunk::parent(oop chunk) {
  return chunk->obj_field(_parent_offset);
}

inline void jdk_internal_vm_StackChunk::set_parent(oop chunk, oop value) {
  chunk->obj_field_put(_parent_offset, value);
}

template<typename P>
inline void jdk_internal_vm_StackChunk::set_parent_raw(oop chunk, oop value) {
  RawAccess<>::oop_store(chunk->field_addr<P>(_parent_offset), value);
}

template<DecoratorSet decorators>
inline void jdk_internal_vm_StackChunk::set_parent_access(oop chunk, oop value) {
  chunk->obj_field_put_access<decorators>(_parent_offset, value);
}

inline oop jdk_internal_vm_StackChunk::cont(oop chunk) {
  return chunk->obj_field(_cont_offset);
}

template<typename P>
inline oop jdk_internal_vm_StackChunk::cont_raw(oop chunk) {
  return (oop)RawAccess<>::oop_load(chunk->field_addr<P>(_cont_offset));
}

inline void jdk_internal_vm_StackChunk::set_cont(oop chunk, oop value) {
  chunk->obj_field_put(_cont_offset, value);
}

template<typename P>
inline void jdk_internal_vm_StackChunk::set_cont_raw(oop chunk, oop value) {
  RawAccess<>::oop_store(chunk->field_addr<P>(_cont_offset), value);
}

template<DecoratorSet decorators>
inline void jdk_internal_vm_StackChunk::set_cont_access(oop chunk, oop value) {
  chunk->obj_field_put_access<decorators>(_cont_offset, value);
}

inline int jdk_internal_vm_StackChunk::size(oop chunk) {
  return chunk->int_field(_size_offset);
}

inline void jdk_internal_vm_StackChunk::set_size(HeapWord* chunk, int value) {
  // Used by StackChunkAllocator before the Object has been finished,
  // so don't cast too oop and use int_field_put in this function.
  assert(_size_offset != 0, "must be set");
  *(int*)(((char*)chunk) + _size_offset) = (int)value;
}

inline void jdk_internal_vm_StackChunk::set_bottom(HeapWord* chunk, int value) {
  // Used by StackChunkAllocator before the Object has been finished,
  // so don't cast too oop and use int_field_put in this function.
  assert(_bottom_offset != 0, "must be set");
  *(int*)(((char*)chunk) + _bottom_offset) = (int)value;
}

inline int jdk_internal_vm_StackChunk::sp(oop chunk) {
  return chunk->int_field_relaxed(_sp_offset);
}

inline void jdk_internal_vm_StackChunk::set_sp(oop chunk, int value) {
  chunk->int_field_put_relaxed(_sp_offset, value);
}

inline void jdk_internal_vm_StackChunk::set_sp(HeapWord* chunk, int value) {
  // Used by StackChunkAllocator before the Object has been finished,
  // so don't cast too oop and use int_field_put in this function.
  assert(_sp_offset != 0, "must be set");
  *(int*)(((char*)chunk) + _sp_offset) = (int)value;
}

inline address jdk_internal_vm_StackChunk::pc(oop chunk) {
  return chunk->address_field(_pc_offset);
}

inline void jdk_internal_vm_StackChunk::set_pc(oop chunk, address value) {
  chunk->address_field_put(_pc_offset, value);
}

inline int jdk_internal_vm_StackChunk::bottom(oop chunk) {
  return chunk->int_field(_bottom_offset);
}

inline void jdk_internal_vm_StackChunk::set_bottom(oop chunk, int value) {
  chunk->int_field_put(_bottom_offset, value);
}

inline uint8_t jdk_internal_vm_StackChunk::flags(oop chunk) {
  return Atomic::load(chunk->field_addr<uint8_t>(_flags_offset));
}

inline void jdk_internal_vm_StackChunk::set_flags(oop chunk, uint8_t value) {
  Atomic::store(chunk->field_addr<uint8_t>(_flags_offset), value);
}

inline uint8_t jdk_internal_vm_StackChunk::flags_acquire(oop chunk) {
  return Atomic::load_acquire(chunk->field_addr<uint8_t>(_flags_offset));
}

inline void jdk_internal_vm_StackChunk::release_set_flags(oop chunk, uint8_t value) {
  Atomic::release_store(chunk->field_addr<uint8_t>(_flags_offset), value);
}

inline bool jdk_internal_vm_StackChunk::try_set_flags(oop chunk, uint8_t expected_value, uint8_t new_value) {
  return Atomic::cmpxchg(chunk->field_addr<uint8_t>(_flags_offset), expected_value, new_value) == expected_value;
}

inline int jdk_internal_vm_StackChunk::maxThawingSize(oop chunk) {
  return chunk->int_field(_maxThawingSize_offset);
}

inline void jdk_internal_vm_StackChunk::set_maxThawingSize(oop chunk, int value) {
#ifdef ASSERT
  jint old = maxThawingSize(chunk);
  log_develop_trace(continuations)("%s max_size: %d -> %d", value >= old ? "add" : "sub", old, value);
#endif
  chunk->int_field_put(_maxThawingSize_offset, value);
}

#endif // SHARE_RUNTIME_CONTINUATIONJAVACLASSES_INLINE_HPP
