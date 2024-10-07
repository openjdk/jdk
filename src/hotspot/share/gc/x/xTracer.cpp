/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcId.hpp"
#include "gc/x/xGlobals.hpp"
#include "gc/x/xStat.hpp"
#include "gc/x/xTracer.hpp"
#include "jfr/jfrEvents.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_JFR
#include "jfr/metadata/jfrSerializer.hpp"
#endif

#if INCLUDE_JFR

class XPageTypeConstant : public JfrSerializer {
public:
  virtual void serialize(JfrCheckpointWriter& writer) {
    writer.write_count(3);
    writer.write_key(XPageTypeSmall);
    writer.write("Small");
    writer.write_key(XPageTypeMedium);
    writer.write("Medium");
    writer.write_key(XPageTypeLarge);
    writer.write("Large");
  }
};

class XStatisticsCounterTypeConstant : public JfrSerializer {
public:
  virtual void serialize(JfrCheckpointWriter& writer) {
    writer.write_count(XStatCounter::count());
    for (XStatCounter* counter = XStatCounter::first(); counter != nullptr; counter = counter->next()) {
      writer.write_key(counter->id());
      writer.write(counter->name());
    }
  }
};

class XStatisticsSamplerTypeConstant : public JfrSerializer {
public:
  virtual void serialize(JfrCheckpointWriter& writer) {
    writer.write_count(XStatSampler::count());
    for (XStatSampler* sampler = XStatSampler::first(); sampler != nullptr; sampler = sampler->next()) {
      writer.write_key(sampler->id());
      writer.write(sampler->name());
    }
  }
};

static void register_jfr_type_serializers() {
  JfrSerializer::register_serializer(TYPE_ZPAGETYPETYPE,
                                     true /* permit_cache */,
                                     new XPageTypeConstant());
  JfrSerializer::register_serializer(TYPE_ZSTATISTICSCOUNTERTYPE,
                                     true /* permit_cache */,
                                     new XStatisticsCounterTypeConstant());
  JfrSerializer::register_serializer(TYPE_ZSTATISTICSSAMPLERTYPE,
                                     true /* permit_cache */,
                                     new XStatisticsSamplerTypeConstant());
}

#endif // INCLUDE_JFR

XTracer* XTracer::_tracer = nullptr;

XTracer::XTracer() :
    GCTracer(Z) {}

void XTracer::initialize() {
  assert(_tracer == nullptr, "Already initialized");
  _tracer = new XTracer();
  JFR_ONLY(register_jfr_type_serializers();)
}

void XTracer::send_stat_counter(const XStatCounter& counter, uint64_t increment, uint64_t value) {
  NoSafepointVerifier nsv;

  EventZStatisticsCounter e;
  if (e.should_commit()) {
    e.set_id(counter.id());
    e.set_increment(increment);
    e.set_value(value);
    e.commit();
  }
}

void XTracer::send_stat_sampler(const XStatSampler& sampler, uint64_t value) {
  NoSafepointVerifier nsv;

  EventZStatisticsSampler e;
  if (e.should_commit()) {
    e.set_id(sampler.id());
    e.set_value(value);
    e.commit();
  }
}

void XTracer::send_thread_phase(const char* name, const Ticks& start, const Ticks& end) {
  NoSafepointVerifier nsv;

  EventZThreadPhase e(UNTIMED);
  if (e.should_commit()) {
    e.set_gcId(GCId::current_or_undefined());
    e.set_name(name);
    e.set_starttime(start);
    e.set_endtime(end);
    e.commit();
  }
}

void XTracer::send_thread_debug(const char* name, const Ticks& start, const Ticks& end) {
  NoSafepointVerifier nsv;

  EventZThreadDebug e(UNTIMED);
  if (e.should_commit()) {
    e.set_gcId(GCId::current_or_undefined());
    e.set_name(name);
    e.set_starttime(start);
    e.set_endtime(end);
    e.commit();
  }
}
