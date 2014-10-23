/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1REGIONTOSPACEMAPPER_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1REGIONTOSPACEMAPPER_HPP

#include "gc_implementation/g1/g1PageBasedVirtualSpace.hpp"
#include "memory/allocation.hpp"
#include "utilities/debug.hpp"

class G1MappingChangedListener VALUE_OBJ_CLASS_SPEC {
 public:
  // Fired after commit of the memory, i.e. the memory this listener is registered
  // for can be accessed.
  // Zero_filled indicates that the memory can be considered as filled with zero bytes
  // when called.
  virtual void on_commit(uint start_idx, size_t num_regions, bool zero_filled) = 0;
};

// Maps region based commit/uncommit requests to the underlying page sized virtual
// space.
class G1RegionToSpaceMapper : public CHeapObj<mtGC> {
 private:
  G1MappingChangedListener* _listener;
 protected:
  // Backing storage.
  G1PageBasedVirtualSpace _storage;
  size_t _commit_granularity;
  size_t _region_granularity;
  // Mapping management
  BitMap _commit_map;

  G1RegionToSpaceMapper(ReservedSpace rs, size_t commit_granularity, size_t region_granularity, MemoryType type);

  void fire_on_commit(uint start_idx, size_t num_regions, bool zero_filled);
 public:
  MemRegion reserved() { return _storage.reserved(); }

  void set_mapping_changed_listener(G1MappingChangedListener* listener) { _listener = listener; }

  virtual ~G1RegionToSpaceMapper() {
    _commit_map.resize(0, /* in_resource_area */ false);
  }

  bool is_committed(uintptr_t idx) const {
    return _commit_map.at(idx);
  }

  virtual void commit_regions(uintptr_t start_idx, size_t num_regions = 1) = 0;
  virtual void uncommit_regions(uintptr_t start_idx, size_t num_regions = 1) = 0;

  // Creates an appropriate G1RegionToSpaceMapper for the given parameters.
  // The byte_translation_factor defines how many bytes in a region correspond to
  // a single byte in the data structure this mapper is for.
  // Eg. in the card table, this value corresponds to the size a single card
  // table entry corresponds to.
  static G1RegionToSpaceMapper* create_mapper(ReservedSpace rs,
                                              size_t os_commit_granularity,
                                              size_t region_granularity,
                                              size_t byte_translation_factor,
                                              MemoryType type);
};

#endif /* SHARE_VM_GC_IMPLEMENTATION_G1_G1REGIONTOSPACEMAPPER_HPP */
