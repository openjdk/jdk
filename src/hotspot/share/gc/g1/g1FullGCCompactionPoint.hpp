/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1FULLGCCOMPACTIONPOINT_HPP
#define SHARE_GC_G1_G1FULLGCCOMPACTIONPOINT_HPP

#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/pair.hpp"

class G1FullCollector;
class G1HeapRegion;
class PreservedMarks;

class G1FullGCCompactionPoint : public CHeapObj<mtGC> {
  G1FullCollector* _collector;
  G1HeapRegion* _current_region;
  HeapWord* _compaction_top;
  PreservedMarks* _preserved_stack;
  GrowableArray<G1HeapRegion*>* _compaction_regions;
  GrowableArrayIterator<G1HeapRegion*> _compaction_region_iterator;

  bool object_will_fit(size_t size);
  void initialize_values();
  void switch_region();
  G1HeapRegion* next_region();
  uint find_contiguous_before(G1HeapRegion* hr, uint num_regions);

public:
  G1FullGCCompactionPoint(G1FullCollector* collector, PreservedMarks* preserved_stack);
  ~G1FullGCCompactionPoint();

  bool has_regions();
  bool is_initialized();
  void initialize(G1HeapRegion* hr);
  void update();
  void forward(oop object, size_t size);
  void forward_humongous(G1HeapRegion* hr);
  void add(G1HeapRegion* hr);
  void add_humongous(G1HeapRegion* hr);

  void remove_at_or_above(uint bottom);
  G1HeapRegion* current_region();

  GrowableArray<G1HeapRegion*>* regions();

  PreservedMarks* preserved_stack() const {
    assert(_preserved_stack != nullptr, "must be initialized");
    return _preserved_stack;
  }

  void set_preserved_stack(PreservedMarks* preserved_stack) {
    assert(_preserved_stack == nullptr, "only initialize once");
    _preserved_stack = preserved_stack;
  }
};

#endif // SHARE_GC_G1_G1FULLGCCOMPACTIONPOINT_HPP
