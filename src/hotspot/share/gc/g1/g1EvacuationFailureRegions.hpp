/*
 * Copyright (c) 2021, Huawei and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1EVACUATIONFAILUREREGIONS_HPP
#define SHARE_GC_G1_G1EVACUATIONFAILUREREGIONS_HPP

#include "utilities/concurrentHashTable.hpp"
#include "utilities/concurrentHashTable.inline.hpp"

class HeapRegionClosure;
class HeapRegionClaimer;

class G1EvacuationFailureRegions {

  class HashTableConfig : public StackObj {
  public:
    using Value = uint;

    static uintx get_hash(Value const& value, bool* is_dead);
    static void* allocate_node(void* context, size_t size, Value const& value);
    static void free_node(void* context, void* memory, Value const& value);
  };

  class HashTableLookUp : public StackObj {
    uint _region_idx;
  public:
    using Value = uint;
    explicit HashTableLookUp(uint region_idx) : _region_idx(region_idx) { }

    // TODO: refine it?
    uintx get_hash() const { return _region_idx; }

    bool equals(Value* value, bool* is_dead) {
      *is_dead = false;
      return *value == _region_idx;
    }
  };

  typedef ConcurrentHashTable<HashTableConfig, mtSymbol> HashTable;

  HashTable* _table;
  uint* _evac_failure_regions;
  volatile uint _evac_failure_regions_cur_length;

public:
  G1EvacuationFailureRegions();
  ~G1EvacuationFailureRegions();
  void initialize();

  bool record(uint region_idx) {
    HashTableLookUp lookup(region_idx);
    bool success = _table->insert(Thread::current(), lookup, region_idx);
    if (success) {
      size_t offset = Atomic::fetch_and_add(&_evac_failure_regions_cur_length, 1u);
      _evac_failure_regions[offset] = region_idx;
    }
    return success;
  }
  void par_iterate(HeapRegionClosure* closure, HeapRegionClaimer* _hrclaimer, uint worker_id);
  void reset();
  bool contains(uint region_idx) const;
  uint num_regions_failed_evacuation() const {
    return Atomic::load(&_evac_failure_regions_cur_length);
  }
};


#endif //SHARE_GC_G1_G1EVACUATIONFAILUREREGIONS_HPP
