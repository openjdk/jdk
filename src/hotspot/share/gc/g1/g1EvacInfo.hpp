/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1EVACINFO_HPP
#define SHARE_GC_G1_G1EVACINFO_HPP

#include "memory/allocation.hpp"

class G1EvacInfo : public StackObj {
  uint _num_collection_set_regions;
  uint _num_allocation_regions;
  size_t _collection_set_used_before;
  size_t _collection_set_used_after;
  size_t _alloc_regions_used_before;
  size_t _bytes_used;
  uint   _num_freed_regions;

public:
  G1EvacInfo() :
    _num_collection_set_regions(0), _num_allocation_regions(0), _collection_set_used_before(0),
    _collection_set_used_after(0), _alloc_regions_used_before(0),
    _bytes_used(0), _num_freed_regions(0) { }

  void set_num_collection_set_regions(uint num_collection_set_regions) {
    _num_collection_set_regions = num_collection_set_regions;
  }

  void set_num_allocation_regions(uint num_allocation_regions) {
    _num_allocation_regions = num_allocation_regions;
  }

  void set_collection_set_used_before(size_t used) {
    _collection_set_used_before = used;
  }

  void increment_collection_set_used_after(size_t used) {
    _collection_set_used_after += used;
  }

  void set_alloc_regions_used_before(size_t used) {
    _alloc_regions_used_before = used;
  }

  void set_bytes_used(size_t used) {
    _bytes_used = used;
  }

  void add_to_num_freed_regions(uint num_freed_regions) {
    _num_freed_regions += num_freed_regions;
  }

  uint   num_collection_set_regions() { return _num_collection_set_regions; }
  uint   num_allocation_regions()     { return _num_allocation_regions; }
  size_t collection_set_used_before() { return _collection_set_used_before; }
  size_t collection_set_used_after()  { return _collection_set_used_after; }
  size_t alloc_regions_used_before()  { return _alloc_regions_used_before; }
  size_t bytes_used()                 { return _bytes_used; }
  uint   num_freed_regions()          { return _num_freed_regions; }
};

#endif // SHARE_GC_G1_G1EVACINFO_HPP
