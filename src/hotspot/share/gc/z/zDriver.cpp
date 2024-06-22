/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/z/zAbort.inline.hpp"
#include "gc/z/zBreakpoint.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zDirector.hpp"
#include "gc/z/zDriver.hpp"
#include "gc/z/zGCIdPrinter.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zServiceability.hpp"
#include "gc/z/zStat.hpp"

static const ZStatPhaseCollection ZPhaseCollectionMinor("Minor Collection", true /* minor */);
static const ZStatPhaseCollection ZPhaseCollectionMajor("Major Collection", false /* minor */);

template <typename DriverT>
class ZGCCauseSetter : public GCCauseSetter {
private:
  DriverT* _driver;

public:
  ZGCCauseSetter(DriverT* driver, GCCause::Cause cause)
    : GCCauseSetter(ZCollectedHeap::heap(), cause),
      _driver(driver) {
    _driver->set_gc_cause(cause);
  }

  ~ZGCCauseSetter() {
    _driver->set_gc_cause(GCCause::_no_gc);
  }
};

ZLock*        ZDriver::_lock;
ZDriverMinor* ZDriver::_minor;
ZDriverMajor* ZDriver::_major;

void ZDriver::initialize() {
  _lock = new ZLock();
}

void ZDriver::lock() {
  _lock->lock();
}

void ZDriver::unlock() {
  _lock->unlock();
}

void ZDriver::set_minor(ZDriverMinor* minor) {
  _minor = minor;
}

void ZDriver::set_major(ZDriverMajor* major) {
  _major = major;
}

ZDriverMinor* ZDriver::minor() {
  return _minor;
}

ZDriverMajor* ZDriver::major() {
  return _major;
}

ZDriverLocker::ZDriverLocker() {
  ZDriver::lock();
}

ZDriverLocker::~ZDriverLocker() {
  ZDriver::unlock();
}

ZDriverUnlocker::ZDriverUnlocker() {
  ZDriver::unlock();
}

ZDriverUnlocker::~ZDriverUnlocker() {
  ZDriver::lock();
}

ZDriver::ZDriver()
  : _gc_cause(GCCause::_no_gc) {}

void ZDriver::set_gc_cause(GCCause::Cause cause) {
  _gc_cause = cause;
}

GCCause::Cause ZDriver::gc_cause() {
  return _gc_cause;
}

ZDriverMinor::ZDriverMinor()
  : ZDriver(),
    _port(),
    _gc_timer(),
    _jfr_tracer(),
    _used_at_start() {
  ZDriver::set_minor(this);
  set_name("ZDriverMinor");
  create_and_start();
}

bool ZDriverMinor::is_busy() const {
  return _port.is_busy();
}

void ZDriverMinor::collect(const ZDriverRequest& request) {
  switch (request.cause()) {
  case GCCause::_wb_young_gc:
    // Start synchronous GC
    _port.send_sync(request);
    break;

  case GCCause::_scavenge_alot:
  case GCCause::_z_timer:
  case GCCause::_z_allocation_rate:
  case GCCause::_z_allocation_stall:
  case GCCause::_z_high_usage:
    // Start asynchronous GC
    _port.send_async(request);
    break;

  default:
    fatal("Unsupported GC cause (%s)", GCCause::to_string(request.cause()));
    break;
  }
};

GCTracer* ZDriverMinor::jfr_tracer() {
  return &_jfr_tracer;
}

void ZDriverMinor::set_used_at_start(size_t used) {
  _used_at_start = used;
}

size_t ZDriverMinor::used_at_start() const {
  return _used_at_start;
}

class ZDriverScopeMinor : public StackObj {
private:
  GCIdMark                     _gc_id;
  GCCause::Cause               _gc_cause;
  ZGCCauseSetter<ZDriverMinor> _gc_cause_setter;
  ZStatTimer                   _stat_timer;
  ZServiceabilityCycleTracer   _tracer;

public:
  ZDriverScopeMinor(const ZDriverRequest& request, ConcurrentGCTimer* gc_timer)
    : _gc_id(),
      _gc_cause(request.cause()),
      _gc_cause_setter(ZDriver::minor(), _gc_cause),
      _stat_timer(ZPhaseCollectionMinor, gc_timer),
      _tracer(true /* minor */) {
    // Select number of worker threads to use
    ZGeneration::young()->set_active_workers(request.young_nworkers());
  }
};

void ZDriverMinor::gc(const ZDriverRequest& request) {
  ZDriverScopeMinor scope(request, &_gc_timer);
  ZGCIdMinor minor_id(gc_id());
  ZGeneration::young()->collect(ZYoungType::minor, &_gc_timer);
}

static void handle_alloc_stalling_for_young() {
  ZHeap::heap()->handle_alloc_stalling_for_young();
}

void ZDriverMinor::handle_alloc_stalls() const {
  handle_alloc_stalling_for_young();
}

void ZDriverMinor::run_thread() {
  // Main loop
  for (;;) {
    // Wait for GC request
    const ZDriverRequest request = _port.receive();

    ZDriverLocker locker;

    abortpoint();

    // Run GC
    gc(request);

    abortpoint();

    // Notify GC completed
    _port.ack();

    // Handle allocation stalls
    handle_alloc_stalls();

    // Good point to consider back-to-back GC
    ZDirector::evaluate_rules();
  }
}

void ZDriverMinor::terminate() {
  const ZDriverRequest request(GCCause::_no_gc, 0, 0);
  _port.send_async(request);
}

static bool should_clear_soft_references(GCCause::Cause cause) {
  // Clear soft references if implied by the GC cause
  switch (cause) {
  case GCCause::_wb_full_gc:
  case GCCause::_metadata_GC_clear_soft_refs:
  case GCCause::_z_allocation_stall:
    return true;

  case GCCause::_heap_dump:
  case GCCause::_heap_inspection:
  case GCCause::_wb_breakpoint:
  case GCCause::_dcmd_gc_run:
  case GCCause::_java_lang_system_gc:
  case GCCause::_full_gc_alot:
  case GCCause::_jvmti_force_gc:
  case GCCause::_z_timer:
  case GCCause::_z_warmup:
  case GCCause::_z_allocation_rate:
  case GCCause::_z_proactive:
  case GCCause::_metadata_GC_threshold:
  case GCCause::_codecache_GC_threshold:
  case GCCause::_codecache_GC_aggressive:
    break;

  default:
    fatal("Unsupported GC cause (%s)", GCCause::to_string(cause));
    break;
  }

  // Clear soft references if threads are stalled waiting for an old collection
  if (ZHeap::heap()->is_alloc_stalling_for_old()) {
    return true;
  }

  // Don't clear
  return false;
}

static bool should_preclean_young(GCCause::Cause cause) {
  // Preclean young if implied by the GC cause
  switch (cause) {
  case GCCause::_heap_dump:
  case GCCause::_heap_inspection:
  case GCCause::_wb_full_gc:
  case GCCause::_wb_breakpoint:
  case GCCause::_dcmd_gc_run:
  case GCCause::_java_lang_system_gc:
  case GCCause::_full_gc_alot:
  case GCCause::_jvmti_force_gc:
  case GCCause::_metadata_GC_clear_soft_refs:
  case GCCause::_z_allocation_stall:
    return true;

  case GCCause::_z_timer:
  case GCCause::_z_warmup:
  case GCCause::_z_allocation_rate:
  case GCCause::_z_proactive:
  case GCCause::_metadata_GC_threshold:
  case GCCause::_codecache_GC_threshold:
  case GCCause::_codecache_GC_aggressive:
    break;

  default:
    fatal("Unsupported GC cause (%s)", GCCause::to_string(cause));
    break;
  }

  // Preclean young if threads are stalled waiting for an old collection
  if (ZHeap::heap()->is_alloc_stalling_for_old()) {
    return true;
  }

  // It is important that when soft references are cleared, we also pre-clean the young
  // generation, as we might otherwise throw premature OOM. Therefore, all causes that
  // trigger soft ref cleaning must also trigger pre-cleaning of young gen. If allocations
  // stalled when checking for soft ref cleaning, then since we hold the driver locker all
  // the way until we check for young gen pre-cleaning, we can be certain that we should
  // catch that above and perform young gen pre-cleaning.
  assert(!should_clear_soft_references(cause), "Clearing soft references without pre-cleaning young gen");

  return false;
}

ZDriverMajor::ZDriverMajor()
  : ZDriver(),
    _port(),
    _gc_timer(),
    _jfr_tracer(),
    _used_at_start() {
  ZDriver::set_major(this);
  set_name("ZDriverMajor");
  create_and_start();
}

bool ZDriverMajor::is_busy() const {
  return _port.is_busy();
}

void ZDriverMajor::collect(const ZDriverRequest& request) {
  switch (request.cause()) {
  case GCCause::_heap_dump:
  case GCCause::_heap_inspection:
  case GCCause::_wb_full_gc:
  case GCCause::_dcmd_gc_run:
  case GCCause::_java_lang_system_gc:
  case GCCause::_full_gc_alot:
  case GCCause::_jvmti_force_gc:
  case GCCause::_metadata_GC_clear_soft_refs:
  case GCCause::_codecache_GC_aggressive:
    // Start synchronous GC
    _port.send_sync(request);
    break;

  case GCCause::_z_timer:
  case GCCause::_z_warmup:
  case GCCause::_z_allocation_rate:
  case GCCause::_z_allocation_stall:
  case GCCause::_z_proactive:
  case GCCause::_codecache_GC_threshold:
  case GCCause::_metadata_GC_threshold:
    // Start asynchronous GC
    _port.send_async(request);
    break;

  case GCCause::_wb_breakpoint:
    ZBreakpoint::start_gc();
    _port.send_async(request);
    break;

  default:
    fatal("Unsupported GC cause (%s)", GCCause::to_string(request.cause()));
    break;
  }
}

GCTracer* ZDriverMajor::jfr_tracer() {
  return &_jfr_tracer;
}

void ZDriverMajor::set_used_at_start(size_t used) {
  _used_at_start = used;
}

size_t ZDriverMajor::used_at_start() const {
  return _used_at_start;
}

class ZDriverScopeMajor : public StackObj {
private:
  GCIdMark                     _gc_id;
  GCCause::Cause               _gc_cause;
  ZGCCauseSetter<ZDriverMajor> _gc_cause_setter;
  ZStatTimer                   _stat_timer;
  ZServiceabilityCycleTracer   _tracer;

public:
  ZDriverScopeMajor(const ZDriverRequest& request, ConcurrentGCTimer* gc_timer)
    : _gc_id(),
      _gc_cause(request.cause()),
      _gc_cause_setter(ZDriver::major(), _gc_cause),
      _stat_timer(ZPhaseCollectionMajor, gc_timer),
      _tracer(false /* minor */) {
    // Select number of worker threads to use
    ZGeneration::young()->set_active_workers(request.young_nworkers());
    ZGeneration::old()->set_active_workers(request.old_nworkers());
  }

  ~ZDriverScopeMajor() {
    // Update data used by soft reference policy
    ZCollectedHeap::heap()->update_capacity_and_used_at_gc();

    // Signal that we have completed a visit to all live objects
    ZCollectedHeap::heap()->record_whole_heap_examined_timestamp();
  }
};

void ZDriverMajor::collect_young(const ZDriverRequest& request) {
  ZGCIdMajor major_id(gc_id(), 'Y');
  if (should_preclean_young(request.cause())) {
    // Collect young generation and promote everything to old generation
    ZGeneration::young()->collect(ZYoungType::major_full_preclean, &_gc_timer);

    abortpoint();

    // Collect young generation and gather roots pointing into old generation
    ZGeneration::young()->collect(ZYoungType::major_full_roots, &_gc_timer);
  } else {
    // Collect young generation and gather roots pointing into old generation
    ZGeneration::young()->collect(ZYoungType::major_partial_roots, &_gc_timer);
  }

  abortpoint();

  // Handle allocations waiting for a young collection
  handle_alloc_stalling_for_young();
}

void ZDriverMajor::collect_old() {
  ZGCIdMajor major_id(gc_id(), 'O');
  ZGeneration::old()->collect(&_gc_timer);
}

void ZDriverMajor::gc(const ZDriverRequest& request) {
  ZDriverScopeMajor scope(request, &_gc_timer);

  // Collect the young generation
  collect_young(request);

  abortpoint();

  // Collect the old generation
  collect_old();
}

static void handle_alloc_stalling_for_old(bool cleared_soft_refs) {
  ZHeap::heap()->handle_alloc_stalling_for_old(cleared_soft_refs);
}

void ZDriverMajor::handle_alloc_stalls(bool cleared_soft_refs) const {
  handle_alloc_stalling_for_old(cleared_soft_refs);
}

void ZDriverMajor::run_thread() {
  // Main loop
  for (;;) {
    // Wait for GC request
    const ZDriverRequest request = _port.receive();

    ZDriverLocker locker;

    ZBreakpoint::at_before_gc();

    abortpoint();

    // Set up soft reference policy
    const bool clear_soft_refs = should_clear_soft_references(request.cause());
    ZGeneration::old()->set_soft_reference_policy(clear_soft_refs);

    // Run GC
    gc(request);

    abortpoint();

    // Notify GC completed
    _port.ack();

    // Handle allocation stalls
    handle_alloc_stalls(clear_soft_refs);

    ZBreakpoint::at_after_gc();
  }
}

void ZDriverMajor::terminate() {
  const ZDriverRequest request(GCCause::_no_gc, 0, 0);
  _port.send_async(request);
}
