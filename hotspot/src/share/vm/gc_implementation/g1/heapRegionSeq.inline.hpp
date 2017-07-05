/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSEQ_INLINE_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSEQ_INLINE_HPP

#include "gc_implementation/g1/heapRegionSeq.hpp"

inline HeapRegion* HeapRegionSeq::addr_to_region(const void* addr) {
  assert(_seq_bottom != NULL, "bad _seq_bottom in addr_to_region");
  if ((char*) addr >= _seq_bottom) {
    size_t diff = (size_t) pointer_delta((HeapWord*) addr,
                                         (HeapWord*) _seq_bottom);
    int index = (int) (diff >> HeapRegion::LogOfHRGrainWords);
    assert(index >= 0, "invariant / paranoia");
    if (index < _regions.length()) {
      HeapRegion* hr = _regions.at(index);
      assert(hr->is_in_reserved(addr),
             "addr_to_region is wrong...");
      return hr;
    }
  }
  return NULL;
}

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSEQ_INLINE_HPP
