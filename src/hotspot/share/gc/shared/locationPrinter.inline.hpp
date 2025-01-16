/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_LOCATIONPRINTER_INLINE_HPP
#define SHARE_GC_SHARED_LOCATIONPRINTER_INLINE_HPP

#include "gc/shared/locationPrinter.hpp"

#include "memory/resourceArea.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oopsHierarchy.hpp"

template <typename CollectedHeapT>
oop BlockLocationPrinter<CollectedHeapT>::base_oop_or_null(void* addr) {
  if (is_valid_obj(addr)) {
    // We were just given an oop directly.
    return cast_to_oop(addr);
  }

  // Try to find addr using block_start (not implemented for all GCs/generations).
  HeapWord* p = CollectedHeapT::heap()->block_start(addr);
  if (p != nullptr && CollectedHeapT::heap()->block_is_obj(p)) {
    if (!is_valid_obj(p)) {
      return nullptr;
    }
    return cast_to_oop(p);
  }

  return nullptr;
}

template <typename CollectedHeapT>
bool BlockLocationPrinter<CollectedHeapT>::print_location(outputStream* st, void* addr) {
  ResourceMark rm;
  // Check if addr points into Java heap.
  bool in_heap = CollectedHeapT::heap()->is_in(addr);
  if (in_heap) {
    // base_oop_or_null() might be unimplemented and return null for some GCs/generations
    oop o = base_oop_or_null(addr);
    if (o != nullptr) {
      if ((void*)o == addr) {
        st->print(PTR_FORMAT " is an oop: ", p2i(addr));
      } else {
        st->print(PTR_FORMAT " is pointing into object: " , p2i(addr));
      }
      o->print_on(st);
      return true;
    }
  } else if (CollectedHeapT::heap()->is_in_reserved(addr)) {
    st->print_cr(PTR_FORMAT " is an unallocated location in the heap", p2i(addr));
    return true;
  }

  // Compressed oop needs to be decoded first.
#ifdef _LP64
  if (UseCompressedOops && ((uintptr_t)addr &~ (uintptr_t)max_juint) == 0) {
    narrowOop narrow_oop = CompressedOops::narrow_oop_cast((uintptr_t)addr);
    oop o = CompressedOops::decode_raw(narrow_oop);

    if (is_valid_obj(o)) {
      st->print(UINT32_FORMAT " is a compressed pointer to object: ",
                CompressedOops::narrow_oop_value(narrow_oop));
      o->print_on(st);
      return true;
    }
  }
#endif

  if (in_heap) {
    st->print_cr(PTR_FORMAT " is an unknown heap location", p2i(addr));
    return true;
  }
  return false;
}

#endif // SHARE_GC_SHARED_LOCATIONPRINTER_INLINE_HPP
