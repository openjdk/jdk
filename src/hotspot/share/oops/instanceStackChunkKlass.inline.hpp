/* Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_INSTANCESTACKCHUNKKLASS_INLINE_HPP
#define SHARE_OOPS_INSTANCESTACKCHUNKKLASS_INLINE_HPP

#include "oops/instanceStackChunkKlass.hpp"

#include "oops/oop.inline.hpp"
#include "oops/stackChunkOop.inline.hpp"
#include "runtime/continuationJavaClasses.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/devirtualizer.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

inline size_t InstanceStackChunkKlass::instance_size(size_t stack_size_in_words) const {
  return align_object_size(size_helper() + stack_size_in_words + gc_data_size(stack_size_in_words));
}

inline size_t InstanceStackChunkKlass::bitmap_size_in_bits(size_t stack_size_in_words) {
  // Need one bit per potential narrowOop* or oop* address.
  size_t size_in_bits = stack_size_in_words << (LogBitsPerWord - LogBitsPerHeapOop);

  return align_up(size_in_bits, BitsPerWord);
}

inline size_t InstanceStackChunkKlass::gc_data_size(size_t stack_size_in_words) {
  // At the moment all GCs are okay with GC data big enough to fit a bit map
  return bitmap_size(stack_size_in_words);
}

inline size_t InstanceStackChunkKlass::bitmap_size(size_t stack_size_in_words) {
  return bitmap_size_in_bits(stack_size_in_words) >> LogBitsPerWord;
}

template <typename T, class OopClosureType>
void InstanceStackChunkKlass::oop_oop_iterate(oop obj, OopClosureType* closure) {
  stackChunkOop chunk = stackChunkOopDesc::cast(obj);
  if (Devirtualizer::do_metadata(closure)) {
    Devirtualizer::do_klass(closure, this);
  }
  oop_oop_iterate_stack<T>(chunk, closure);
  oop_oop_iterate_header<T>(chunk, closure);
  oop_oop_iterate_lockstack<T>(chunk, closure, chunk->range());
}

template <typename T, class OopClosureType>
void InstanceStackChunkKlass::oop_oop_iterate_reverse(oop obj, OopClosureType* closure) {
  assert(!Devirtualizer::do_metadata(closure), "Code to handle metadata is not implemented");
  stackChunkOop chunk = stackChunkOopDesc::cast(obj);
  oop_oop_iterate_stack<T>(chunk, closure);
  oop_oop_iterate_header<T>(chunk, closure);
  oop_oop_iterate_lockstack<T>(chunk, closure, chunk->range());
}

template <typename T, class OopClosureType>
void InstanceStackChunkKlass::oop_oop_iterate_bounded(oop obj, OopClosureType* closure, MemRegion mr) {
  stackChunkOop chunk = stackChunkOopDesc::cast(obj);
  if (Devirtualizer::do_metadata(closure)) {
    if (mr.contains(obj)) {
      Devirtualizer::do_klass(closure, this);
    }
  }
  oop_oop_iterate_stack_bounded<T>(chunk, closure, mr);
  oop_oop_iterate_header_bounded<T>(chunk, closure, mr);
  oop_oop_iterate_lockstack<T>(chunk, closure, mr);
}

template <typename T, class OopClosureType>
void InstanceStackChunkKlass::oop_oop_iterate_header(stackChunkOop chunk, OopClosureType* closure) {
  T* parent_addr = chunk->field_addr<T>(jdk_internal_vm_StackChunk::parent_offset());
  T* cont_addr = chunk->field_addr<T>(jdk_internal_vm_StackChunk::cont_offset());
  Devirtualizer::do_oop(closure, parent_addr);
  Devirtualizer::do_oop(closure, cont_addr);
}

template <typename T, class OopClosureType>
void InstanceStackChunkKlass::oop_oop_iterate_header_bounded(stackChunkOop chunk, OopClosureType* closure, MemRegion mr) {
  T* parent_addr = chunk->field_addr<T>(jdk_internal_vm_StackChunk::parent_offset());
  T* cont_addr = chunk->field_addr<T>(jdk_internal_vm_StackChunk::cont_offset());
  if (mr.contains(parent_addr)) {
    Devirtualizer::do_oop(closure, parent_addr);
  }
  if (mr.contains(cont_addr)) {
    Devirtualizer::do_oop(closure, cont_addr);
  }
}

template <typename T, class OopClosureType>
void InstanceStackChunkKlass::oop_oop_iterate_stack_bounded(stackChunkOop chunk, OopClosureType* closure, MemRegion mr) {
  if (chunk->has_bitmap()) {
    intptr_t* start = chunk->sp_address() - frame::metadata_words_at_bottom;
    intptr_t* end = chunk->end_address();
    // mr.end() can actually be less than start. In that case, we only walk the metadata
    if ((intptr_t*)mr.start() > start) {
      start = (intptr_t*)mr.start();
    }
    if ((intptr_t*)mr.end() < end) {
      end = (intptr_t*)mr.end();
    }
    oop_oop_iterate_stack_with_bitmap<T>(chunk, closure, start, end);
  } else {
    oop_oop_iterate_stack_slow(chunk, closure, mr);
  }
}

template <typename T, class OopClosureType>
void InstanceStackChunkKlass::oop_oop_iterate_stack(stackChunkOop chunk, OopClosureType* closure) {
  if (chunk->has_bitmap()) {
    oop_oop_iterate_stack_with_bitmap<T>(chunk, closure, chunk->sp_address() - frame::metadata_words_at_bottom, chunk->end_address());
  } else {
    oop_oop_iterate_stack_slow(chunk, closure, chunk->range());
  }
}

template <typename T, class OopClosureType>
class StackChunkOopIterateBitmapClosure {
  stackChunkOop _chunk;
  OopClosureType* const _closure;

public:
  StackChunkOopIterateBitmapClosure(stackChunkOop chunk, OopClosureType* closure) : _chunk(chunk), _closure(closure) {}

  bool do_bit(BitMap::idx_t index) {
    Devirtualizer::do_oop(_closure, _chunk->address_for_bit<T>(index));
    return true;
  }
};

template <typename T, class OopClosureType>
void InstanceStackChunkKlass::oop_oop_iterate_stack_with_bitmap(stackChunkOop chunk, OopClosureType* closure,
                                                                intptr_t* start, intptr_t* end) {
  if (Devirtualizer::do_metadata(closure)) {
    do_methods(chunk, closure);
  }

  if (end > start) {
    StackChunkOopIterateBitmapClosure<T, OopClosureType> bitmap_closure(chunk, closure);
    chunk->bitmap().iterate(&bitmap_closure, chunk->bit_index_for((T*)start), chunk->bit_index_for((T*)end));
  }
}

#endif // SHARE_OOPS_INSTANCESTACKCHUNKKLASS_INLINE_HPP
