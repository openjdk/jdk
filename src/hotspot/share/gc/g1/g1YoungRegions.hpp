/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1YOUNGREGIONS_HPP
#define SHARE_GC_G1_G1YOUNGREGIONS_HPP

#include "gc/g1/g1RegionsOnNodes.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "utilities/growableArray.hpp"

class G1HeapRegion;

// Common base class for tracking information about Eden and Survivor region sets.
class G1YoungRegions {
  Atomic<size_t> _used_bytes;
  G1RegionsOnNodes _regions_on_node;

protected:
  void add_to_nodes(G1HeapRegion* r);
  void clear_data();

  G1YoungRegions() : _used_bytes(0), _regions_on_node() { }
  ~G1YoungRegions() = default;
  NONCOPYABLE(G1YoungRegions);

public:
  uint regions_on_node(uint node_index) const {
    return _regions_on_node.num_regions_per_node(node_index);
  }

  void add_used_bytes(size_t used_bytes) { _used_bytes.add_then_fetch(used_bytes, memory_order_relaxed); }
  size_t used_bytes() const { return _used_bytes.load_relaxed(); }
};

class G1EdenRegions : public G1YoungRegions {
  uint _num_regions;

public:
  G1EdenRegions() : G1YoungRegions(), _num_regions(0) { }

  void add(G1HeapRegion* r);
  void clear();

  uint num_regions() const { return _num_regions; }
};

// Set of current survivor regions.
class G1SurvivorRegions : public G1YoungRegions {
  GrowableArray<G1HeapRegion*> _regions;

public:
  G1SurvivorRegions() : G1YoungRegions(), _regions(8, mtGC) { }

  void add(G1HeapRegion* r);
  void clear();

  uint num_regions() const { return (uint)_regions.length(); }

  void convert_to_eden();

  const GrowableArray<G1HeapRegion*>& regions() const { return _regions; }
};

#endif // SHARE_GC_G1_G1YOUNGREGIONS_HPP
