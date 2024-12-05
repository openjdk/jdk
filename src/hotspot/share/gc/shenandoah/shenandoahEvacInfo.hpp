/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHEVACINFO_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHEVACINFO_HPP

#include "memory/allocation.hpp"

class ShenandoahEvacuationInformation : public StackObj {
  // Values for ShenandoahEvacuationInformation jfr event, sizes stored as bytes
  size_t _collection_set_regions;
  size_t _collection_set_used_before;
  size_t _collection_set_used_after;
  size_t _collected_old;
  size_t _collected_promoted;
  size_t _collected_young;
  size_t _regions_promoted_humongous;
  size_t _regions_promoted_regular;
  size_t _regular_promoted_garbage;
  size_t _regular_promoted_free;
  size_t _regions_freed;
  size_t _regions_immediate;
  size_t _immediate_size;

public:
  ShenandoahEvacuationInformation() :
    _collection_set_regions(0), _collection_set_used_before(0), _collection_set_used_after(0),
    _collected_old(0), _collected_promoted(0), _collected_young(0), _regions_promoted_humongous(0),
    _regions_promoted_regular(0), _regular_promoted_garbage(0), _regular_promoted_free(0),
    _regions_freed(0), _regions_immediate(0), _immediate_size(0) { }

  void set_collection_set_regions(size_t collection_set_regions) {
    _collection_set_regions = collection_set_regions;
  }

  void set_collection_set_used_before(size_t used) {
    _collection_set_used_before = used;
  }

  void set_collection_set_used_after(size_t used) {
    _collection_set_used_after = used;
  }

  void set_collected_old(size_t collected) {
    _collected_old = collected;
  }

  void set_collected_promoted(size_t collected) {
    _collected_promoted = collected;
  }

  void set_collected_young(size_t collected) {
    _collected_young = collected;
  }

  void set_regions_freed(size_t freed) {
    _regions_freed = freed;
  }

  void set_regions_promoted_humongous(size_t humongous) {
    _regions_promoted_humongous = humongous;
  }

  void set_regions_promoted_regular(size_t regular) {
    _regions_promoted_regular = regular;
  }

  void set_regular_promoted_garbage(size_t garbage) {
    _regular_promoted_garbage = garbage;
  }

  void set_regular_promoted_free(size_t free) {
    _regular_promoted_free = free;
  }

  void set_regions_immediate(size_t immediate) {
    _regions_immediate = immediate;
  }

  void set_immediate_size(size_t size) {
    _immediate_size = size;
  }

  size_t collection_set_regions()     { return _collection_set_regions; }
  size_t collection_set_used_before() { return _collection_set_used_before; }
  size_t collection_set_used_after()  { return _collection_set_used_after; }
  size_t collected_old()              { return _collected_old; }
  size_t collected_promoted()         { return _collected_promoted; }
  size_t collected_young()            { return _collected_young; }
  size_t regions_promoted_humongous() { return _regions_promoted_humongous; }
  size_t regions_promoted_regular()   { return _regions_promoted_regular; }
  size_t regular_promoted_garbage()   { return _regular_promoted_garbage; }
  size_t regular_promoted_free()      { return _regular_promoted_free; }
  size_t regions_freed()              { return _regions_freed; }
  size_t regions_immediate()          { return _regions_immediate; }
  size_t immediate_size()             { return _immediate_size; }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHEVACINFO_HPP
