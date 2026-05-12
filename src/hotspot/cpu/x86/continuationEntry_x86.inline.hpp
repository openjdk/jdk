/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_CONTINUATIONENTRY_X86_INLINE_HPP
#define CPU_X86_CONTINUATIONENTRY_X86_INLINE_HPP

#include "runtime/continuationEntry.hpp"

#include "oops/method.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/registerMap.hpp"
#include "utilities/align.hpp"
#include "utilities/macros.hpp"

inline frame ContinuationEntry::to_frame() const {
  static CodeBlob* cb = CodeCache::find_blob_fast(entry_pc());
  assert(cb != nullptr, "");
  assert(cb->as_nmethod()->method()->is_continuation_enter_intrinsic(), "");
  return frame(entry_sp(), entry_sp(), entry_fp(), entry_pc(), cb);
}

inline intptr_t* ContinuationEntry::entry_fp() const {
  return (intptr_t*)((address)this + size());
}

inline intptr_t* ContinuationEntry::bottom_sender_sp() const {
  // the entry frame is extended if the bottom frame has stack arguments
  int entry_frame_extension = entry_frame_extension_words();
  intptr_t* sp = entry_sp() - entry_frame_extension;
#ifdef _LP64
  sp = align_down(sp, frame::frame_alignment);
#endif
  return sp;
}

inline bool ContinuationEntry::should_flush_stack_processing(uintptr_t watermark) const {
  intptr_t* boundary_sp = (intptr_t*)((uintptr_t)entry_sp() + ContinuationEntry::size());
  return watermark <= (uintptr_t)boundary_sp;
}

inline bool ContinuationEntry::is_valid_bottom_frame_sp(intptr_t* sp) const {
  return sp != nullptr && sp <= entry_sp();
}

inline void ContinuationEntry::update_register_map(RegisterMap* map) const {
  intptr_t** fp = (intptr_t**)(bottom_sender_sp() - frame::sender_sp_offset);
  frame::update_map_with_saved_link(map, fp);
}

#endif // CPU_X86_CONTINUATIONENTRY_X86_INLINE_HPP
