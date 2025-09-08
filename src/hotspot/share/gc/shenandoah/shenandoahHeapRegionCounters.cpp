/*
 * Copyright (c) 2016, 2020, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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


#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegionCounters.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/perfData.inline.hpp"
#include "utilities/defaultStream.hpp"

ShenandoahHeapRegionCounters::ShenandoahHeapRegionCounters() :
  _last_sample_millis(0)
{
  if (UsePerfData && ShenandoahRegionSampling) {
    EXCEPTION_MARK;
    ResourceMark rm;
    ShenandoahHeap* heap = ShenandoahHeap::heap();
    size_t num_regions = heap->num_regions();
    const char* cns = PerfDataManager::name_space("shenandoah", "regions");
    _name_space = NEW_C_HEAP_ARRAY(char, strlen(cns)+1, mtGC);
    strcpy(_name_space, cns);

    const char* cname = PerfDataManager::counter_name(_name_space, "timestamp");
    _timestamp = PerfDataManager::create_long_variable(SUN_GC, cname, PerfData::U_None, CHECK);

    cname = PerfDataManager::counter_name(_name_space, "max_regions");
    PerfDataManager::create_constant(SUN_GC, cname, PerfData::U_None, num_regions, CHECK);

    cname = PerfDataManager::counter_name(_name_space, "protocol_version");
    PerfDataManager::create_constant(SUN_GC, cname, PerfData::U_None, VERSION_NUMBER, CHECK);

    cname = PerfDataManager::counter_name(_name_space, "region_size");
    PerfDataManager::create_constant(SUN_GC, cname, PerfData::U_None, ShenandoahHeapRegion::region_size_bytes() >> 10, CHECK);

    cname = PerfDataManager::counter_name(_name_space, "status");
    _status = PerfDataManager::create_long_variable(SUN_GC, cname,
                                                    PerfData::U_None, CHECK);

    _regions_data = NEW_C_HEAP_ARRAY(PerfVariable*, num_regions, mtGC);
    // Initializing performance data resources for each region
    for (uint i = 0; i < num_regions; i++) {
      const char* reg_name = PerfDataManager::name_space(_name_space, "region", i);
      const char* data_name = PerfDataManager::counter_name(reg_name, "data");
      const char* ns = PerfDataManager::ns_to_string(SUN_GC);
      const char* fullname = PerfDataManager::counter_name(ns, data_name);
      assert(!PerfDataManager::exists(fullname), "must not exist");
      _regions_data[i] = PerfDataManager::create_long_variable(SUN_GC, data_name, PerfData::U_None, CHECK);
    }
  }
}

ShenandoahHeapRegionCounters::~ShenandoahHeapRegionCounters() {
  if (_name_space != nullptr) FREE_C_HEAP_ARRAY(char, _name_space);
}

void ShenandoahHeapRegionCounters::write_snapshot(PerfLongVariable** regions,
                                             PerfLongVariable* ts,
                                             PerfLongVariable* status,
                                             size_t num_regions,
                                             size_t region_size, size_t protocol_version) {
  LogTarget(Trace, gc, region) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);

    ls.print_cr(JLONG_FORMAT " " JLONG_FORMAT " %zu %zu %zu",
            ts->get_value(), status->get_value(), num_regions, region_size, protocol_version);
    if (num_regions > 0) {
      ls.print(JLONG_FORMAT, regions[0]->get_value());
    }
    for (uint i = 1; i < num_regions; ++i) {
      ls.print(" " JLONG_FORMAT, regions[i]->get_value());
    }
    ls.cr();
  }
}

void ShenandoahHeapRegionCounters::update() {
  if (ShenandoahRegionSampling) {
    jlong current = nanos_to_millis(os::javaTimeNanos());
    jlong last = _last_sample_millis;
    if (current - last > ShenandoahRegionSamplingRate && Atomic::cmpxchg(&_last_sample_millis, last, current) == last) {

      ShenandoahHeap* heap = ShenandoahHeap::heap();
      _status->set_value(encode_heap_status(heap));
      _timestamp->set_value(os::elapsed_counter());

      {
        ShenandoahHeapLocker locker(heap->lock());
        size_t rs = ShenandoahHeapRegion::region_size_bytes();
        size_t num_regions = heap->num_regions();
        for (uint i = 0; i < num_regions; i++) {
          ShenandoahHeapRegion* r = heap->get_region(i);
          jlong data = 0;
          data |= ((100 * r->used() / rs)                & PERCENT_MASK) << USED_SHIFT;
          data |= ((100 * r->get_live_data_bytes() / rs) & PERCENT_MASK) << LIVE_SHIFT;
          data |= ((100 * r->get_tlab_allocs() / rs)     & PERCENT_MASK) << TLAB_SHIFT;
          data |= ((100 * r->get_gclab_allocs() / rs)    & PERCENT_MASK) << GCLAB_SHIFT;
          data |= ((100 * r->get_plab_allocs() / rs)     & PERCENT_MASK) << PLAB_SHIFT;
          data |= ((100 * r->get_shared_allocs() / rs)   & PERCENT_MASK) << SHARED_SHIFT;

          data |= (r->age() & AGE_MASK) << AGE_SHIFT;
          data |= (r->affiliation() & AFFILIATION_MASK) << AFFILIATION_SHIFT;
          data |= (r->state_ordinal() & STATUS_MASK) << STATUS_SHIFT;
          _regions_data[i]->set_value(data);
        }

        // If logging enabled, dump current region snapshot to log file
        write_snapshot(_regions_data, _timestamp, _status, num_regions, rs >> 10, VERSION_NUMBER);
      }
    }
  }
}

static int encode_phase(ShenandoahHeap* heap) {
  if (heap->is_evacuation_in_progress() || heap->is_full_gc_move_in_progress()) {
    return 2;
  }
  if (heap->is_update_refs_in_progress() || heap->is_full_gc_move_in_progress()) {
    return 3;
  }
  if (heap->is_concurrent_mark_in_progress() || heap->is_concurrent_weak_root_in_progress() || heap->is_full_gc_in_progress()) {
    return 1;
  }
  assert(heap->is_idle(), "Unexpected gc_state: %d", heap->gc_state());
  return 0;
}

static int get_generation_shift(ShenandoahGeneration* generation) {
  switch (generation->type()) {
    case NON_GEN:
    case GLOBAL:
      return 0;
    case OLD:
      return 2;
    case YOUNG:
      return 4;
    default:
      ShouldNotReachHere();
      return -1;
  }
}

jlong ShenandoahHeapRegionCounters::encode_heap_status(ShenandoahHeap* heap) {

  if (heap->is_idle() && !heap->is_full_gc_in_progress()) {
    return 0;
  }

  jlong status = 0;
  if (!heap->mode()->is_generational()) {
    status = encode_phase(heap);
  } else {
    int phase = encode_phase(heap);
    ShenandoahGeneration* generation = heap->active_generation();
    assert(generation != nullptr, "Expected active generation in this mode.");
    int shift = get_generation_shift(generation);
    status |= ((phase & 0x3) << shift);
    if (heap->is_concurrent_old_mark_in_progress()) {
      status |= (1 << 2);
    }
    log_develop_trace(gc)("%s, phase=%u, old_mark=%s, status=" JLONG_FORMAT,
                          generation->name(), phase, BOOL_TO_STR(heap->is_concurrent_old_mark_in_progress()), status);
  }

  if (heap->is_degenerated_gc_in_progress()) {
    status |= (1 << 6);
  }
  if (heap->is_full_gc_in_progress()) {
    status |= (1 << 7);
  }

  return status;
}
