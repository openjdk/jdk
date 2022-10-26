/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_CONTINUATIONENTRY_INLINE_HPP
#define SHARE_VM_RUNTIME_CONTINUATIONENTRY_INLINE_HPP

#include "runtime/continuationEntry.hpp"

#include "oops/access.hpp"
#include "runtime/frame.hpp"
#include "utilities/align.hpp"

#include CPU_HEADER_INLINE(continuationEntry)

inline intptr_t* ContinuationEntry::bottom_sender_sp() const {
  // the entry frame is extended if the bottom frame has stack arguments
  int entry_frame_extension = argsize() > 0 ? argsize() + frame::metadata_words_at_top : 0;
  intptr_t* sp = entry_sp() - entry_frame_extension;
#ifdef _LP64
  sp = align_down(sp, frame::frame_alignment);
#endif
  return sp;
}

inline oop ContinuationEntry::cont_oop() const {
  oop snapshot = _cont;
  return NativeAccess<>::oop_load(&snapshot);
}

inline oop ContinuationEntry::cont_oop_or_null(const ContinuationEntry* ce) {
  return ce == nullptr ? nullptr : ce->cont_oop();
}

inline oop ContinuationEntry::scope() const {
  return Continuation::continuation_scope(cont_oop());
}

#endif // SHARE_VM_RUNTIME_CONTINUATIONENTRY_INLINE_HPP
