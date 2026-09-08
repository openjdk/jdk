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

#include "gc/g1/g1HeapRegion.hpp"
#include "gc/g1/g1YoungRegions.hpp"
#include "utilities/debug.hpp"
#include "utilities/growableArray.hpp"

void G1YoungRegions::add_to_nodes(G1HeapRegion* r) {
  _regions_on_node.add(r);
}

void G1YoungRegions::clear_data() {
  _used_bytes.store_relaxed(0);
  _regions_on_node.clear();
}

void G1EdenRegions::add(G1HeapRegion* r) {
  assert(r->is_eden(), "must be");
  add_to_nodes(r);
  _num_regions++;
}

void G1EdenRegions::clear() {
  clear_data();
  _num_regions = 0;
}

void G1SurvivorRegions::add(G1HeapRegion* r) {
  assert(r->is_survivor(), "should be flagged as survivor region");
  add_to_nodes(r);
  _regions.append(r);
}

void G1SurvivorRegions::convert_to_eden() {
  for (G1HeapRegion* r : _regions) {
    r->set_eden_pre_gc();
  }
  clear();
}

void G1SurvivorRegions::clear() {
  clear_data();
  _regions.clear();
}
