/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
  uint _collection_set_regions;
  uint _allocation_regions;
  size_t _collection_set_used_before;
  size_t _collection_set_used_after;
  size_t _alloc_regions_used_before;
  size_t _bytes_used;
  uint   _regions_freed;

public:
  G1EvacInfo() :
    _collection_set_regions(0), _allocation_regions(0), _collection_set_used_before(0),
    _collection_set_used_after(0), _alloc_regions_used_before(0),
    _bytes_used(0), _regions_freed(0) { }

  void set_collection_set_regions(uint collection_set_regions) {
    _collection_set_regions = collection_set_regions;
  }

  void set_allocation_regions(uint allocation_regions) {
    _allocation_regions = allocation_regions;
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

  void set_regions_freed(uint freed) {
    _regions_freed += freed;
  }

  uint   collection_set_regions()     { return _collection_set_regions; }
  uint   allocation_regions()         { return _allocation_regions; }
  size_t collection_set_used_before() { return _collection_set_used_before; }
  size_t collection_set_used_after()  { return _collection_set_used_after; }
  size_t alloc_regions_used_before()  { return _alloc_regions_used_before; }
  size_t bytes_used()                 { return _bytes_used; }
  uint   regions_freed()              { return _regions_freed; }
};

#endif // SHARE_GC_G1_G1EVACINFO_HPP
