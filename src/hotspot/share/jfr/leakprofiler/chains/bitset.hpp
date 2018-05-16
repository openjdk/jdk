/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_JFR_LEAKPROFILER_CHAINS_BITSET_HPP
#define SHARE_VM_JFR_LEAKPROFILER_CHAINS_BITSET_HPP

#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/bitMap.inline.hpp"

class JfrVirtualMemory;
class MemRegion;

class BitSet : public CHeapObj<mtTracing> {
 private:
  JfrVirtualMemory* _vmm;
  const HeapWord* const _region_start;
  BitMapView _bits;
  const size_t _region_size;

 public:
  BitSet(const MemRegion& covered_region);
  ~BitSet();

  bool initialize();

  BitMap::idx_t mark_obj(const HeapWord* addr) {
    const BitMap::idx_t bit = addr_to_bit(addr);
    _bits.par_set_bit(bit);
    return bit;
  }

  BitMap::idx_t mark_obj(oop obj) {
    return mark_obj((HeapWord*)obj);
  }

  bool is_marked(const HeapWord* addr) const {
    return is_marked(addr_to_bit(addr));
  }

  bool is_marked(oop obj) const {
    return is_marked((HeapWord*)obj);
  }

  BitMap::idx_t size() const {
    return _bits.size();
  }

  BitMap::idx_t addr_to_bit(const HeapWord* addr) const {
    return pointer_delta(addr, _region_start) >> LogMinObjAlignment;
  }

  bool is_marked(const BitMap::idx_t bit) const {
    return _bits.at(bit);
  }
};

#endif  // SHARE_VM_JFR_LEAKPROFILER_CHAINS_BITSET_HPP
