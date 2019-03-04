/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1HeapTransition.hpp"
#include "gc/g1/g1Policy.hpp"
#include "logging/log.hpp"
#include "memory/metaspace.hpp"

G1HeapTransition::Data::Data(G1CollectedHeap* g1_heap) {
  _eden_length = g1_heap->eden_regions_count();
  _survivor_length = g1_heap->survivor_regions_count();
  _old_length = g1_heap->old_regions_count();
  _archive_length = g1_heap->archive_regions_count();
  _humongous_length = g1_heap->humongous_regions_count();
  _metaspace_used_bytes = MetaspaceUtils::used_bytes();
}

G1HeapTransition::G1HeapTransition(G1CollectedHeap* g1_heap) : _g1_heap(g1_heap), _before(g1_heap) { }

struct DetailedUsage : public StackObj {
  size_t _eden_used;
  size_t _survivor_used;
  size_t _old_used;
  size_t _archive_used;
  size_t _humongous_used;

  size_t _eden_region_count;
  size_t _survivor_region_count;
  size_t _old_region_count;
  size_t _archive_region_count;
  size_t _humongous_region_count;

  DetailedUsage() :
    _eden_used(0), _survivor_used(0), _old_used(0), _archive_used(0), _humongous_used(0),
    _eden_region_count(0), _survivor_region_count(0), _old_region_count(0),
    _archive_region_count(0), _humongous_region_count(0) {}
};

class DetailedUsageClosure: public HeapRegionClosure {
public:
  DetailedUsage _usage;
  bool do_heap_region(HeapRegion* r) {
    if (r->is_old()) {
      _usage._old_used += r->used();
      _usage._old_region_count++;
    } else if (r->is_archive()) {
      _usage._archive_used += r->used();
      _usage._archive_region_count++;
    } else if (r->is_survivor()) {
      _usage._survivor_used += r->used();
      _usage._survivor_region_count++;
    } else if (r->is_eden()) {
      _usage._eden_used += r->used();
      _usage._eden_region_count++;
    } else if (r->is_humongous()) {
      _usage._humongous_used += r->used();
      _usage._humongous_region_count++;
    } else {
      assert(r->used() == 0, "Expected used to be 0 but it was " SIZE_FORMAT, r->used());
    }
    return false;
  }
};

void G1HeapTransition::print() {
  Data after(_g1_heap);

  size_t eden_capacity_length_after_gc = _g1_heap->policy()->young_list_target_length() - after._survivor_length;
  size_t survivor_capacity_length_before_gc = _g1_heap->policy()->max_survivor_regions();

  DetailedUsage usage;
  if (log_is_enabled(Trace, gc, heap)) {
    DetailedUsageClosure blk;
    _g1_heap->heap_region_iterate(&blk);
    usage = blk._usage;
    assert(usage._eden_region_count == 0, "Expected no eden regions, but got " SIZE_FORMAT, usage._eden_region_count);
    assert(usage._survivor_region_count == after._survivor_length, "Expected survivors to be " SIZE_FORMAT " but was " SIZE_FORMAT,
        after._survivor_length, usage._survivor_region_count);
    assert(usage._old_region_count == after._old_length, "Expected old to be " SIZE_FORMAT " but was " SIZE_FORMAT,
        after._old_length, usage._old_region_count);
    assert(usage._archive_region_count == after._archive_length, "Expected archive to be " SIZE_FORMAT " but was " SIZE_FORMAT,
        after._archive_length, usage._archive_region_count);
    assert(usage._humongous_region_count == after._humongous_length, "Expected humongous to be " SIZE_FORMAT " but was " SIZE_FORMAT,
        after._humongous_length, usage._humongous_region_count);
  }

  log_info(gc, heap)("Eden regions: " SIZE_FORMAT "->" SIZE_FORMAT "("  SIZE_FORMAT ")",
                     _before._eden_length, after._eden_length, eden_capacity_length_after_gc);
  log_trace(gc, heap)(" Used: 0K, Waste: 0K");

  log_info(gc, heap)("Survivor regions: " SIZE_FORMAT "->" SIZE_FORMAT "("  SIZE_FORMAT ")",
                     _before._survivor_length, after._survivor_length, survivor_capacity_length_before_gc);
  log_trace(gc, heap)(" Used: " SIZE_FORMAT "K, Waste: " SIZE_FORMAT "K",
      usage._survivor_used / K, ((after._survivor_length * HeapRegion::GrainBytes) - usage._survivor_used) / K);

  log_info(gc, heap)("Old regions: " SIZE_FORMAT "->" SIZE_FORMAT,
                     _before._old_length, after._old_length);
  log_trace(gc, heap)(" Used: " SIZE_FORMAT "K, Waste: " SIZE_FORMAT "K",
      usage._old_used / K, ((after._old_length * HeapRegion::GrainBytes) - usage._old_used) / K);

  log_info(gc, heap)("Archive regions: " SIZE_FORMAT "->" SIZE_FORMAT,
                     _before._archive_length, after._archive_length);
  log_trace(gc, heap)(" Used: " SIZE_FORMAT "K, Waste: " SIZE_FORMAT "K",
      usage._archive_used / K, ((after._archive_length * HeapRegion::GrainBytes) - usage._archive_used) / K);

  log_info(gc, heap)("Humongous regions: " SIZE_FORMAT "->" SIZE_FORMAT,
                     _before._humongous_length, after._humongous_length);
  log_trace(gc, heap)(" Used: " SIZE_FORMAT "K, Waste: " SIZE_FORMAT "K",
      usage._humongous_used / K, ((after._humongous_length * HeapRegion::GrainBytes) - usage._humongous_used) / K);

  MetaspaceUtils::print_metaspace_change(_before._metaspace_used_bytes);
}
