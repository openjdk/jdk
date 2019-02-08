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
 */

#include "precompiled.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTracer.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcLocker.hpp"
#include "jfr/jfrEvents.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/safepointVerifiers.hpp"
#if INCLUDE_JFR
#include "jfr/metadata/jfrSerializer.hpp"
#endif

#if INCLUDE_JFR
class ZStatisticsCounterTypeConstant : public JfrSerializer {
public:
  virtual void serialize(JfrCheckpointWriter& writer) {
    writer.write_count(ZStatCounter::count());
    for (ZStatCounter* counter = ZStatCounter::first(); counter != NULL; counter = counter->next()) {
      writer.write_key(counter->id());
      writer.write(counter->name());
    }
  }
};

class ZStatisticsSamplerTypeConstant : public JfrSerializer {
public:
  virtual void serialize(JfrCheckpointWriter& writer) {
    writer.write_count(ZStatSampler::count());
    for (ZStatSampler* sampler = ZStatSampler::first(); sampler != NULL; sampler = sampler->next()) {
      writer.write_key(sampler->id());
      writer.write(sampler->name());
    }
  }
};

static void register_jfr_type_serializers() {
  JfrSerializer::register_serializer(TYPE_ZSTATISTICSCOUNTERTYPE,
                                     false /* require_safepoint */,
                                     true /* permit_cache */,
                                     new ZStatisticsCounterTypeConstant());
  JfrSerializer::register_serializer(TYPE_ZSTATISTICSSAMPLERTYPE,
                                     false /* require_safepoint */,
                                     true /* permit_cache */,
                                     new ZStatisticsSamplerTypeConstant());
}
#endif

ZTracer* ZTracer::_tracer = NULL;

ZTracer::ZTracer() :
    GCTracer(Z) {}

void ZTracer::initialize() {
  assert(_tracer == NULL, "Already initialized");
  _tracer = new (ResourceObj::C_HEAP, mtGC) ZTracer();
  JFR_ONLY(register_jfr_type_serializers());
}

void ZTracer::send_stat_counter(uint32_t counter_id, uint64_t increment, uint64_t value) {
  NoSafepointVerifier nsv(true, !SafepointSynchronize::is_at_safepoint());

  EventZStatisticsCounter e;
  if (e.should_commit()) {
    e.set_id(counter_id);
    e.set_increment(increment);
    e.set_value(value);
    e.commit();
  }
}

void ZTracer::send_stat_sampler(uint32_t sampler_id, uint64_t value) {
  NoSafepointVerifier nsv(true, !SafepointSynchronize::is_at_safepoint());

  EventZStatisticsSampler e;
  if (e.should_commit()) {
    e.set_id(sampler_id);
    e.set_value(value);
    e.commit();
  }
}

void ZTracer::send_thread_phase(const char* name, const Ticks& start, const Ticks& end) {
  NoSafepointVerifier nsv(true, !SafepointSynchronize::is_at_safepoint());

  EventZThreadPhase e(UNTIMED);
  if (e.should_commit()) {
    e.set_gcId(GCId::current_or_undefined());
    e.set_name(name);
    e.set_starttime(start);
    e.set_endtime(end);
    e.commit();
  }
}

void ZTracer::send_page_alloc(size_t size, size_t used, size_t free, size_t cache, bool nonblocking, bool noreserve) {
  NoSafepointVerifier nsv(true, !SafepointSynchronize::is_at_safepoint());

  EventZPageAllocation e;
  if (e.should_commit()) {
    e.set_pageSize(size);
    e.set_usedAfter(used);
    e.set_freeAfter(free);
    e.set_inCacheAfter(cache);
    e.set_nonBlocking(nonblocking);
    e.set_noReserve(noreserve);
    e.commit();
  }
}

void ZTracer::report_stat_counter(const ZStatCounter& counter, uint64_t increment, uint64_t value) {
  send_stat_counter(counter.id(), increment, value);
}

void ZTracer::report_stat_sampler(const ZStatSampler& sampler, uint64_t value) {
  send_stat_sampler(sampler.id(), value);
}

void ZTracer::report_thread_phase(const ZStatPhase& phase, const Ticks& start, const Ticks& end) {
  send_thread_phase(phase.name(), start, end);
}

void ZTracer::report_thread_phase(const char* name, const Ticks& start, const Ticks& end) {
  send_thread_phase(name, start, end);
}

void ZTracer::report_page_alloc(size_t size, size_t used, size_t free, size_t cache, ZAllocationFlags flags) {
  send_page_alloc(size, used, free, cache, flags.non_blocking(), flags.no_reserve());
}
