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
#include "runtime/stackWatermarkSet.inline.hpp"
#if INCLUDE_ZGC
#include "gc/z/zHeap.inline.hpp"
#endif

#include CPU_HEADER_INLINE(continuationEntry)

inline bool is_stack_watermark_processed(const JavaThread* thread, const void* addr) {
  StackWatermark* sw = StackWatermarkSet::get(const_cast<JavaThread*>(thread), StackWatermarkKind::gc);
  assert(sw != nullptr, "Wrong GC");

  if (!sw->processing_started()) {
    return false;
  }

  uintptr_t watermark = sw->watermark();
  if (watermark == 0) {
    // completed
    return true;
  }

  return uintptr_t(addr) <= watermark;
}

inline oop ContinuationEntry::cont_oop(const JavaThread* thread) const {
#if INCLUDE_ZGC
  if (UseZGC) {
    assert(!ZHeap::heap()->is_in((uintptr_t)(void*)&_cont), "Should not be in the heap");
    assert(is_stack_watermark_processed(thread != nullptr ? thread : JavaThread::current(), &_cont), "Not processed");
  }
#endif
  return RawAccess<>::oop_load((oop*)&_cont);
}


#endif // SHARE_VM_RUNTIME_CONTINUATIONENTRY_INLINE_HPP
