/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1HeapTransition.hpp"
#include "gc/g1/g1Policy.hpp"
#include "logging/logStream.hpp"
#include "memory/metaspaceUtils.hpp"

G1HeapTransition::Data::Data(G1CollectedHeap* g1_heap) :
  _num_eden_regions(g1_heap->eden_regions_count()),
  _num_survivor_regions(g1_heap->survivor_regions_count()),
  _num_old_regions(g1_heap->old_regions_count()),
  _num_humongous_regions(g1_heap->humongous_regions_count()),
  _meta_sizes(MetaspaceUtils::get_combined_statistics()),
  _num_eden_regions_per_node(nullptr),
  _num_survivor_regions_per_node(nullptr) {

  uint node_count = G1NUMA::numa()->num_active_nodes();

  if (node_count > 1) {
    LogTarget(Debug, gc, heap, numa) lt;

    if (lt.is_enabled()) {
      _num_eden_regions_per_node = NEW_C_HEAP_ARRAY(uint, node_count, mtGC);
      _num_survivor_regions_per_node = NEW_C_HEAP_ARRAY(uint, node_count, mtGC);

      for (uint i = 0; i < node_count; i++) {
        _num_eden_regions_per_node[i] = g1_heap->eden_regions_count(i);
        _num_survivor_regions_per_node[i] = g1_heap->survivor_regions_count(i);
      }
    }
  }
}

G1HeapTransition::Data::~Data() {
  FREE_C_HEAP_ARRAY(_num_eden_regions_per_node);
  FREE_C_HEAP_ARRAY(_num_survivor_regions_per_node);
}

G1HeapTransition::G1HeapTransition(G1CollectedHeap* g1_heap) : _g1_heap(g1_heap), _before(g1_heap) { }

struct G1HeapTransition::DetailedUsage : public StackObj {
  size_t _eden_used;
  size_t _survivor_used;
  size_t _old_used;
  size_t _humongous_used;

  size_t _eden_region_count;
  size_t _survivor_region_count;
  size_t _old_region_count;
  size_t _humongous_region_count;

  DetailedUsage() :
    _eden_used(0), _survivor_used(0), _old_used(0), _humongous_used(0),
    _eden_region_count(0), _survivor_region_count(0), _old_region_count(0),
    _humongous_region_count(0) {}
};

class G1HeapTransition::DetailedUsageClosure: public G1HeapRegionClosure {
public:
  DetailedUsage _usage;
  bool do_heap_region(G1HeapRegion* r) {
    if (r->is_old()) {
      _usage._old_used += r->used();
      _usage._old_region_count++;
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
      assert(r->used() == 0, "Expected used to be 0 but it was %zu", r->used());
    }
    return false;
  }
};

static void log_regions(const char* msg, size_t num_before, size_t num_after, size_t capacity,
                        uint* num_per_node_before, uint* num_per_node_after) {
  LogTarget(Info, gc, heap) lt;

  if (lt.is_enabled()) {
    LogStream ls(lt);

    ls.print("%s regions: %zu->%zu(%zu)",
             msg, num_before, num_after, capacity);
    // Not null only if gc+heap+numa at Debug level is enabled.
    if (num_per_node_before != nullptr && num_per_node_after != nullptr) {
      G1NUMA* numa = G1NUMA::numa();
      uint num_nodes = numa->num_active_nodes();
      const uint* node_ids = numa->node_ids();
      ls.print(" (");
      for (uint i = 0; i < num_nodes; i++) {
        ls.print("%u: %u->%u", node_ids[i], num_per_node_before[i], num_per_node_after[i]);
        // Skip adding below if it is the last one.
        if (i != num_nodes - 1) {
          ls.print(", ");
        }
      }
      ls.print(")");
    }
    ls.print_cr("");
  }
}

void G1HeapTransition::print() {
  Data after(_g1_heap);

  size_t num_eden_after_gc = _g1_heap->policy()->target_num_young_regions() - after._num_survivor_regions;
  size_t num_survivor_before_gc = _g1_heap->policy()->max_survivor_regions();

  DetailedUsage usage;
  if (log_is_enabled(Trace, gc, heap)) {
    DetailedUsageClosure blk;
    _g1_heap->heap_region_iterate(&blk);
    usage = blk._usage;
    assert(usage._eden_region_count == 0, "Expected no eden regions, but got %zu", usage._eden_region_count);
    assert(usage._survivor_region_count == after._num_survivor_regions, "Expected survivors to be %zu but was %zu",
           after._num_survivor_regions, usage._survivor_region_count);
    assert(usage._old_region_count == after._num_old_regions, "Expected old to be %zu but was %zu",
           after._num_old_regions, usage._old_region_count);
    assert(usage._humongous_region_count == after._num_humongous_regions, "Expected humongous to be %zu but was %zu",
           after._num_humongous_regions, usage._humongous_region_count);
  }

  log_regions("Eden", _before._num_eden_regions, after._num_eden_regions, num_eden_after_gc,
              _before._num_eden_regions_per_node, after._num_eden_regions_per_node);
  log_trace(gc, heap)(" Used: 0K, Waste: 0K");

  log_regions("Survivor", _before._num_survivor_regions, after._num_survivor_regions, num_survivor_before_gc,
              _before._num_survivor_regions_per_node, after._num_survivor_regions_per_node);
  log_trace(gc, heap)(" Used: %zuK, Waste: %zuK",
                      usage._survivor_used / K,
                      ((after._num_survivor_regions * G1HeapRegion::GrainBytes) - usage._survivor_used) / K);

  log_info(gc, heap)("Old regions: %zu->%zu",
                     _before._num_old_regions, after._num_old_regions);
  log_trace(gc, heap)(" Used: %zuK, Waste: %zuK",
                      usage._old_used / K,
                      ((after._num_old_regions * G1HeapRegion::GrainBytes) - usage._old_used) / K);

  log_info(gc, heap)("Humongous regions: %zu->%zu",
                     _before._num_humongous_regions, after._num_humongous_regions);
  log_trace(gc, heap)(" Used: %zuK, Waste: %zuK",
                      usage._humongous_used / K,
                      ((after._num_humongous_regions * G1HeapRegion::GrainBytes) - usage._humongous_used) / K);

  MetaspaceUtils::print_metaspace_change(_before._meta_sizes);
}
