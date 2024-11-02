/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_S390_CONTINUATION_S390_INLINE_HPP
#define CPU_S390_CONTINUATION_S390_INLINE_HPP

#include "oops/stackChunkOop.inline.hpp"
#include "runtime/frame.hpp"
#include "runtime/frame.inline.hpp"

inline void FreezeBase::set_top_frame_metadata_pd(const frame& hf) {
  Unimplemented();
}

template<typename FKind>
inline frame FreezeBase::sender(const frame& f) {
  Unimplemented();
  return frame();
}

template<typename FKind> frame FreezeBase::new_heap_frame(frame& f, frame& caller) {
  Unimplemented();
  return frame();
}

void FreezeBase::adjust_interpreted_frame_unextended_sp(frame& f) {
  Unimplemented();
}

inline void FreezeBase::prepare_freeze_interpreted_top_frame(frame& f) {
  Unimplemented();
}

inline void FreezeBase::relativize_interpreted_frame_metadata(const frame& f, const frame& hf) {
  Unimplemented();
}

inline void FreezeBase::patch_pd(frame& hf, const frame& caller) {
  Unimplemented();
}

inline void FreezeBase::patch_stack_pd(intptr_t* frame_sp, intptr_t* heap_sp) {
  Unimplemented();
}

inline frame ThawBase::new_entry_frame() {
  Unimplemented();
  return frame();
}

template<typename FKind> frame ThawBase::new_stack_frame(const frame& hf, frame& caller, bool bottom) {
  Unimplemented();
  return frame();
}

inline void ThawBase::derelativize_interpreted_frame_metadata(const frame& hf, const frame& f) {
  Unimplemented();
}

inline intptr_t* ThawBase::align(const frame& hf, intptr_t* frame_sp, frame& caller, bool bottom) {
  Unimplemented();
  return nullptr;
}

inline void ThawBase::patch_pd(frame& f, const frame& caller) {
  Unimplemented();
}

inline void ThawBase::patch_pd(frame& f, intptr_t* caller_sp) {
  Unimplemented();
}

inline intptr_t* ThawBase::push_cleanup_continuation() {
  Unimplemented();
  return nullptr;
}

template <typename ConfigT>
inline void Thaw<ConfigT>::patch_caller_links(intptr_t* sp, intptr_t* bottom) {
  Unimplemented();
}

inline void ThawBase::prefetch_chunk_pd(void* start, int size) {
  Unimplemented();
}

#endif // CPU_S390_CONTINUATION_S390_INLINE_HPP
