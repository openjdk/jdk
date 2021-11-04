/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderDataGraph.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "gc/z/zAbort.inline.hpp"
#include "gc/z/zBreakpoint.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zDriver.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zJNICritical.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zServiceability.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zVerify.hpp"
#include "logging/log.hpp"
#include "memory/universe.hpp"
#include "runtime/atomic.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"

static const ZStatPhaseYoungCycle ZPhaseYoungCycle("Young Garbage Collection Cycle");
static const ZStatPhaseOldCycle   ZPhaseOldCycle("Old Garbage Collection Cycle");
static const ZStatPhaseMinorCycle ZPhaseMinorCycle("Minor Garbage Collection Cycle");
static const ZStatPhaseMajorCycle ZPhaseMajorCycle("Major Garbage Collection Cycle");

static const ZStatPhasePause      ZPhasePauseYoungMarkStart("Pause Young Mark Start");
static const ZStatPhaseConcurrent ZPhaseConcurrentYoungMark("Concurrent Young Mark");
static const ZStatPhaseConcurrent ZPhaseConcurrentYoungMarkContinue("Concurrent Young Mark Continue");
static const ZStatPhasePause      ZPhasePauseYoungMarkEnd("Pause Young Mark End");
static const ZStatPhaseConcurrent ZPhaseConcurrentYoungMarkFree("Concurrent Young Mark Free");
static const ZStatPhaseConcurrent ZPhaseConcurrentYoungResetRelocationSet("Concurrent Young Reset Relocation Set");
static const ZStatPhaseConcurrent ZPhaseConcurrentYoungSelectRelocationSet("Concurrent Young Select Relocation Set");
static const ZStatPhasePause      ZPhasePauseYoungRelocateStart("Pause Young Relocate Start");
static const ZStatPhaseConcurrent ZPhaseConcurrentYoungRelocated("Concurrent Young Relocate");

static const ZStatPhasePause      ZPhasePauseOldMarkStart("Pause Old Mark Start");
static const ZStatPhaseConcurrent ZPhaseConcurrentOldMark("Concurrent Old Mark");
static const ZStatPhaseConcurrent ZPhaseConcurrentOldMarkContinue("Concurrent Old Mark Continue");
static const ZStatPhasePause      ZPhasePauseOldMarkEnd("Pause Old Mark End");
static const ZStatPhaseConcurrent ZPhaseConcurrentOldMarkFree("Concurrent Old Mark Free");
static const ZStatPhaseConcurrent ZPhaseConcurrentOldProcessNonStrongReferences("Concurrent Old Process Non-Strong References");
static const ZStatPhaseConcurrent ZPhaseConcurrentOldResetRelocationSet("Concurrent Old Reset Relocation Set");
static const ZStatPhaseConcurrent ZPhaseConcurrentOldSelectRelocationSet("Concurrent Old Select Relocation Set");
static const ZStatPhasePause      ZPhasePauseOldRelocateStart("Pause Old Relocate Start");
static const ZStatPhaseConcurrent ZPhaseConcurrentOldRelocated("Concurrent Old Relocate");
static const ZStatPhaseConcurrent ZPhaseConcurrentOldRootsRemap("Concurrent Old Roots Remap");

static const ZStatSampler         ZSamplerJavaThreads("System", "Java Threads", ZStatUnitThreads);

ZDriverRequest::ZDriverRequest() :
    ZDriverRequest(GCCause::_no_gc) {}

ZDriverRequest::ZDriverRequest(GCCause::Cause cause) :
    ZDriverRequest(cause, ConcGCThreads) {}

ZDriverRequest::ZDriverRequest(GCCause::Cause cause, uint nworkers) :
    _cause(cause),
    _nworkers(nworkers) {}

bool ZDriverRequest::operator==(const ZDriverRequest& other) const {
  return _cause == other._cause;
}

GCCause::Cause ZDriverRequest::cause() const {
  return _cause;
}

uint ZDriverRequest::nworkers() const {
  return _nworkers;
}

class VM_ZOperation : public VM_Operation {
private:
  const uint _gc_id;
  bool       _success;

public:
  VM_ZOperation() :
      _gc_id(GCId::current()),
      _success(false) {}

  virtual bool block_jni_critical() const {
    // Blocking JNI critical regions is needed in operations where we change
    // the bad mask or move objects. Changing the bad mask will invalidate all
    // oops, which makes it conceptually the same thing as moving all objects.
    return false;
  }

  virtual bool skip_thread_oop_barriers() const {
    return true;
  }

  virtual bool do_operation() = 0;

  virtual bool doit_prologue() {
    Heap_lock->lock();
    return true;
  }

  virtual void doit() {
    // Setup GC id and active marker
    GCIdMark gc_id_mark(_gc_id);
    IsGCActiveMark gc_active_mark;

    // Verify before operation
    // FIXME: Need to prevent verification when young collection pauses happen
    // during old resurrection block window.
    if (!ZResurrection::is_blocked()) {
      ZVerify::before_zoperation();
    }

    // Execute operation
    _success = do_operation();

    // Update statistics
    ZStatSample(ZSamplerJavaThreads, Threads::number_of_threads());
  }

  virtual void doit_epilogue() {
    Heap_lock->unlock();
  }

  bool success() const {
    return _success;
  }
};

class VM_ZYoungMarkStart : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZYoungMarkStart;
  }

  virtual bool block_jni_critical() const {
    return true;
  }

  virtual bool do_operation() {
    ZStatTimerYoung timer(ZPhasePauseYoungMarkStart);
    ZServiceabilityPauseTracer tracer(ZCollectorId::young);

    ZCollectedHeap::heap()->increment_total_collections(false /* full */);
    ZHeap::heap()->young_collector()->mark_start();
    return true;
  }
};

class VM_ZYoungMarkEnd : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZYoungMarkEnd;
  }

  virtual bool do_operation() {
    ZStatTimerYoung timer(ZPhasePauseYoungMarkEnd);
    ZServiceabilityPauseTracer tracer(ZCollectorId::young);
    return ZHeap::heap()->young_collector()->mark_end();
  }
};

class VM_ZYoungRelocateStart : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZYoungRelocateStart;
  }

  virtual bool block_jni_critical() const {
    return true;
  }

  virtual bool do_operation() {
    ZStatTimerYoung timer(ZPhasePauseYoungRelocateStart);
    ZServiceabilityPauseTracer tracer(ZCollectorId::young);
    ZHeap::heap()->young_collector()->relocate_start();
    return true;
  }
};

static uint select_active_worker_threads_dynamic(GCCause::Cause cause, uint nworkers) {
  // Use requested number of worker threads
  return nworkers;
}

static uint select_active_worker_threads_static(GCCause::Cause cause, uint nworkers) {
  // Boost number of worker threads if implied by the GC cause
  if (cause == GCCause::_wb_full_gc ||
      cause == GCCause::_java_lang_system_gc ||
      cause == GCCause::_metadata_GC_clear_soft_refs ||
      cause == GCCause::_z_major_allocation_stall) {
    // Boost
    const uint boosted_nworkers = MAX2(nworkers, ParallelGCThreads);
    return boosted_nworkers;
  }

  // Use requested number of worker threads
  return nworkers;
}

static uint select_active_young_worker_threads(const ZDriverRequest& request) {
  if (UseDynamicNumberOfGCThreads) {
    return select_active_worker_threads_dynamic(request.cause(), request.nworkers());
  } else {
    return select_active_worker_threads_static(request.cause(), request.nworkers());
  }
}

static uint select_active_old_worker_threads(const ZDriverRequest& request) {
  if (UseDynamicNumberOfGCThreads) {
    return select_active_worker_threads_dynamic(request.cause(), ConcGCThreads);
  } else {
    return select_active_worker_threads_static(request.cause(), ConcGCThreads);
  }
}

// Macro to execute a termination check after a concurrent phase. Note
// that it's important that the abortion check comes after the call
// to the function f, since we can't abort between pause_relocate_start()
// and concurrent_relocate(). We need to let concurrent_relocate() call
// abort_page() on the remaining entries in the relocation set.
#define abortable(f)                  \
  do {                                \
    f();                              \
    if (ZAbort::should_abort()) {     \
      return;                         \
    }                                 \
  } while (false)

ZDriverMinor::ZDriverMinor() :
    _port(),
    _lock(),
    _active(false),
    _blocked(false),
    _await(false),
    _aborted(false) {
  set_name("ZDriverMinor");
  create_and_start();
}

bool ZDriverMinor::is_busy() const {
  return _port.is_busy();
}

bool ZDriverMinor::is_active() {
  ZLocker<ZConditionLock> locker(&_lock);
  return _active;
}

void ZDriverMinor::active() {
  ZLocker<ZConditionLock> locker(&_lock);
  while (_blocked) {
    _lock.wait();
  }
  _active = true;
  _lock.notify_all();
}

void ZDriverMinor::inactive() {
  ZLocker<ZConditionLock> locker(&_lock);
  _active = false;
  _await = false;
  _lock.notify_all();
}

void ZDriverMinor::aborted() {
  ZLocker<ZConditionLock> locker(&_lock);
  _aborted = true;
  _lock.notify_all();
}

void ZDriverMinor::block() {
  ZLocker<ZConditionLock> locker(&_lock);
  _blocked = true;
  while (_active) {
    _lock.wait();
  }
}

void ZDriverMinor::unblock() {
  ZLocker<ZConditionLock> locker(&_lock);
  _blocked = false;
  _await = true;
  _lock.notify_all();
}

void ZDriverMinor::start() {
  // Start an asynchronous cycle before unblocking. This avoid starting
  // a new cycle if one is already about to start when we unblock.
  collect(GCCause::_z_major_young);
  unblock();
}

void ZDriverMinor::await() {
  ZLocker<ZConditionLock> locker(&_lock);
  while (_await && !_aborted) {
    _lock.wait();
  }
}

void ZDriverMinor::collect(const ZDriverRequest& request) {
  switch (request.cause()) {
  case GCCause::_wb_young_gc:
  case GCCause::_scavenge_alot:
  case GCCause::_z_minor_timer:
  case GCCause::_z_minor_allocation_rate:
  case GCCause::_z_major_young:
    // Start asynchronous GC
    _port.send_async(request);
    break;

  case GCCause::_z_minor_high_usage:
  case GCCause::_z_major_young_preclean:
    // Start synchronous GC
    _port.send_sync(request);
    break;

  default:
    // Other causes not supported
    fatal("Unsupported GC cause (%s)", GCCause::to_string(request.cause()));
    break;
  }
}

template <typename T>
bool ZDriverMinor::pause() {
  T op;

  if (op.block_jni_critical()) {
    ZJNICritical::block();
  }

  VMThread::execute(&op);

  if (op.block_jni_critical()) {
    ZJNICritical::unblock();
  }

  return op.success();
}

void ZDriverMinor::pause_mark_start(const ZDriverRequest& request) {
  ZYoungCollector* collector = ZHeap::heap()->young_collector();
  if (collector->should_skip_mark_start()) {
    // An old mark start also performs a young mark start. So the next
    // young collection after an old mark start, doesn't run young mark start.
    // The number of GC threads has already been selected when this happens.
    return;
  }

  // Select number of worker threads to use
  const uint nworkers = select_active_young_worker_threads(request);
  collector->set_active_workers(nworkers);

  pause<VM_ZYoungMarkStart>();
}

void ZDriverMinor::concurrent_mark() {
  ZStatTimerYoung timer(ZPhaseConcurrentYoungMark);
  ZHeap::heap()->young_collector()->mark_roots();
  ZHeap::heap()->young_collector()->mark_follow();
}

bool ZDriverMinor::pause_mark_end() {
  return pause<VM_ZYoungMarkEnd>();
}

void ZDriverMinor::concurrent_mark_continue() {
  ZStatTimerYoung timer(ZPhaseConcurrentYoungMarkContinue);
  ZHeap::heap()->young_collector()->mark_follow();
}

void ZDriverMinor::concurrent_mark_free() {
  ZStatTimerYoung timer(ZPhaseConcurrentYoungMarkFree);
  ZHeap::heap()->young_collector()->mark_free();
}

void ZDriverMinor::concurrent_reset_relocation_set() {
  ZStatTimerYoung timer(ZPhaseConcurrentYoungResetRelocationSet);
  ZHeap::heap()->young_collector()->reset_relocation_set();
}

void ZDriverMinor::concurrent_select_relocation_set() {
  ZStatTimerYoung timer(ZPhaseConcurrentYoungSelectRelocationSet);
  ZHeap::heap()->young_collector()->select_relocation_set();
}

void ZDriverMinor::pause_relocate_start() {
  pause<VM_ZYoungRelocateStart>();
}

void ZDriverMinor::concurrent_relocate() {
  ZStatTimerYoung timer(ZPhaseConcurrentYoungRelocated);
  ZHeap::heap()->young_collector()->relocate();
}

class ZDriverMinorGCScope : public StackObj {
private:
  ZStatTimerMinor _timer;

public:
  ZDriverMinorGCScope(const ZDriverRequest& request) :
      _timer(ZPhaseMinorCycle) {
    ZYoungCollector* collector = ZHeap::heap()->young_collector();

    // Update statistics
    collector->set_at_collection_start();
  }
};

class ZDriverYoungGCScope : public StackObj {
private:
  GCIdMark                   _gc_id;
  GCCause::Cause             _gc_cause;
  GCCauseSetter              _gc_cause_setter;
  ZStatTimerYoung            _timer;
  ZServiceabilityCycleTracer _tracer;

public:
  ZDriverYoungGCScope(const ZDriverRequest& request) :
      _gc_id(),
      _gc_cause(request.cause()),
      _gc_cause_setter(ZCollectedHeap::heap(), request.cause()),
      _timer(ZPhaseYoungCycle),
      _tracer(ZCollectorId::young) {
    ZYoungCollector* collector = ZHeap::heap()->young_collector();

    // Update statistics
    collector->set_at_generation_collection_start();
  }

  ~ZDriverYoungGCScope() {
    ZYoungCollector* collector = ZHeap::heap()->young_collector();

    // Update statistics
    collector->stat_cycle()->at_end(_gc_cause, collector->active_workers());
  }
};

void ZDriverMinor::gc(const ZDriverRequest& request) {
  ZDriverYoungGCScope scope(request);

  // Phase 1: Pause Mark Start
  pause_mark_start(request);

  // Phase 2: Concurrent Mark
  abortable(concurrent_mark);

  // Phase 3: Pause Mark End
  while (!pause_mark_end()) {
    // Phase 3.5: Concurrent Mark Continue
    abortable(concurrent_mark_continue);
  }

  // Phase 4: Concurrent Mark Free
  abortable(concurrent_mark_free);

  // Phase 5: Concurrent Reset Relocation Set
  abortable(concurrent_reset_relocation_set);

  // Phase 6: Concurrent Select Relocation Set
  abortable(concurrent_select_relocation_set);

  // Phase 7: Pause Relocate Start
  pause_relocate_start();

  // Phase 8: Concurrent Relocate
  abortable(concurrent_relocate);
}

void ZDriverMinor::run_service() {
  // Main loop
  while (!ZAbort::should_abort()) {
    // Wait for GC request
    const ZDriverRequest request = _port.receive();
    if (request.cause() == GCCause::_no_gc) {
      continue;
    }

    active();

    if (request.cause() == GCCause::_z_major_young ||
        request.cause() == GCCause::_z_major_young_preclean) {
      // Run a young collection for a major GC
      gc(request);
    } else {
      // Run a young collection for a minor GC
      ZDriverMinorGCScope scope(request);
      gc(request);
    }

    // Notify GC completed
    _port.ack();

    inactive();
  }

  aborted();
}

void ZDriverMinor::stop_service() {
  _port.send_async(GCCause::_no_gc);
}

class VM_ZOldMarkStart : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZOldMarkStart;
  }

  virtual bool block_jni_critical() const {
    return true;
  }

  virtual bool do_operation() {
    //ClassLoaderDataGraph::clear_claimed_marks(ClassLoaderData::_claim_strong);
    ClassLoaderDataGraph::verify_claimed_marks_not(ClassLoaderData::_claim_strong);

    ZStatTimerOld timer(ZPhasePauseOldMarkStart);
    ZServiceabilityPauseTracer tracer(ZCollectorId::old);

    ZCollectedHeap::heap()->increment_total_collections(true /* full */);

    ZYoungCollector* young_collector = ZHeap::heap()->young_collector();
    ZOldCollector* old_collector = ZHeap::heap()->old_collector();

    old_collector->mark_start();

    young_collector->mark_start();
    young_collector->skip_mark_start();
    return true;
  }
};

class VM_ZOldMarkEnd : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZOldMarkEnd;
  }

  virtual bool do_operation() {
    ZStatTimerOld timer(ZPhasePauseOldMarkEnd);
    ZServiceabilityPauseTracer tracer(ZCollectorId::old);
    return ZHeap::heap()->old_collector()->mark_end();
  }
};

class VM_ZOldRelocateStart : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZOldRelocateStart;
  }

  virtual bool block_jni_critical() const {
    return true;
  }

  virtual bool do_operation() {
    ZStatTimerOld timer(ZPhasePauseOldRelocateStart);
    ZServiceabilityPauseTracer tracer(ZCollectorId::old);
    ZHeap::heap()->old_collector()->relocate_start();
    return true;
  }
};

class VM_ZOldVerify : public VM_Operation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZOldVerify;
  }

  virtual bool skip_thread_oop_barriers() const {
    return true;
  }

  virtual void doit() {
    ZVerify::after_weak_processing();
  }
};

ZDriverMajor::ZDriverMajor(ZDriverMinor* minor) :
    _port(),
    _lock(),
    _active(false),
    _promote_all(false),
    _minor(minor) {
  set_name("ZDriverMajor");
  create_and_start();
}

bool ZDriverMajor::is_busy() const {
  return _port.is_busy();
}

bool ZDriverMajor::promote_all() {
  return _promote_all;
}

void ZDriverMajor::minor_block() {
  _minor->block();
}

void ZDriverMajor::minor_unblock() {
  _minor->unblock();
}

void ZDriverMajor::minor_start() {
  _minor->start();
}

void ZDriverMajor::minor_await() {
  _minor->await();
}

void ZDriverMajor::collect(const ZDriverRequest& request) {
  switch (request.cause()) {
  case GCCause::_wb_conc_mark:
  case GCCause::_wb_full_gc:
  case GCCause::_dcmd_gc_run:
  case GCCause::_java_lang_system_gc:
  case GCCause::_full_gc_alot:
  case GCCause::_jvmti_force_gc:
  case GCCause::_metadata_GC_clear_soft_refs:
    // Start synchronous GC
    _port.send_sync(request);
    break;

  case GCCause::_z_major_timer:
  case GCCause::_z_major_warmup:
  case GCCause::_z_major_allocation_rate:
  case GCCause::_z_major_allocation_stall:
  case GCCause::_z_major_proactive:
  case GCCause::_metadata_GC_threshold:
    // Start asynchronous GC
    _port.send_async(request);
    break;

  case GCCause::_wb_breakpoint:
    ZBreakpoint::start_gc();
    _port.send_async(request);
    break;

  default:
    // Delegate other causes to minor driver
    _minor->collect(request);
    break;
  }
}

template <typename T>
bool ZDriverMajor::pause() {
  T op;

  if (op.block_jni_critical()) {
    ZJNICritical::block();
  }

  VMThread::execute(&op);

  if (op.block_jni_critical()) {
    ZJNICritical::unblock();
  }

  return op.success();
}

void ZDriverMajor::pause_mark_start() {
  pause<VM_ZOldMarkStart>();
}

void ZDriverMajor::concurrent_mark() {
  ZStatTimerOld timer(ZPhaseConcurrentOldMark);
  ZBreakpoint::at_after_marking_started();
  ZHeap::heap()->old_collector()->mark_roots();
  ZHeap::heap()->old_collector()->mark_follow();
  ZBreakpoint::at_before_marking_completed();
}

bool ZDriverMajor::pause_mark_end() {
  return pause<VM_ZOldMarkEnd>();
}

void ZDriverMajor::concurrent_mark_continue() {
  ZStatTimerOld timer(ZPhaseConcurrentOldMarkContinue);
  ZHeap::heap()->old_collector()->mark_follow();
}

void ZDriverMajor::concurrent_mark_free() {
  ZStatTimerOld timer(ZPhaseConcurrentOldMarkFree);
  ZHeap::heap()->old_collector()->mark_free();
}

void ZDriverMajor::concurrent_process_non_strong_references() {
  ZStatTimerOld timer(ZPhaseConcurrentOldProcessNonStrongReferences);
  ZBreakpoint::at_after_reference_processing_started();
  ZHeap::heap()->old_collector()->process_non_strong_references();
}

void ZDriverMajor::concurrent_reset_relocation_set() {
  ZStatTimerOld timer(ZPhaseConcurrentOldResetRelocationSet);
  ZHeap::heap()->old_collector()->reset_relocation_set();
}

void ZDriverMajor::pause_verify() {
  // Note that we block out concurrent young collections when performing the
  // verification. The verification checks that store good oops in the
  // old generation have a corresponding remembered set entry, or is in
  // a store barrier buffer (hence asynchronously creating such entries).
  // That lookup would otherwise race with installation of base pointers
  // into the store barrier buffer. We dodge that race by blocking out
  // young collections during this verification.
  if (VerifyBeforeGC || VerifyDuringGC || VerifyAfterGC) {
    // Full verification
    minor_block();
    VM_Verify op;
    VMThread::execute(&op);
    minor_unblock();
  } else if (ZVerifyRoots || ZVerifyObjects) {
    // Limited verification
    minor_block();
    VM_ZOldVerify op;
    VMThread::execute(&op);
    minor_unblock();
  }
}

void ZDriverMajor::concurrent_select_relocation_set() {
  ZStatTimerOld timer(ZPhaseConcurrentOldSelectRelocationSet);
  ZHeap::heap()->old_collector()->select_relocation_set();
}

void ZDriverMajor::pause_relocate_start() {
  pause<VM_ZOldRelocateStart>();
}

void ZDriverMajor::concurrent_relocate() {
  ZStatTimerOld timer(ZPhaseConcurrentOldRelocated);
  ZHeap::heap()->old_collector()->relocate();
}

void ZDriverMajor::concurrent_roots_remap() {
  ZStatTimerOld timer(ZPhaseConcurrentOldRootsRemap);
  ZHeap::heap()->old_collector()->roots_remap();
}

void ZDriverMajor::check_out_of_memory() {
  ZHeap::heap()->check_out_of_memory();
}

static bool should_clear_soft_references(const ZDriverRequest& request) {
  // Clear soft references if implied by the GC cause
  if (request.cause() == GCCause::_wb_full_gc ||
      request.cause() == GCCause::_metadata_GC_clear_soft_refs ||
      request.cause() == GCCause::_z_major_allocation_stall) {
    // Clear
    return true;
  }

  // Don't clear
  return false;
}

class ZDriverMajorGCScope : public StackObj {
private:
  GCIdMark                   _gc_id;
  GCCause::Cause             _gc_cause;
  GCCauseSetter              _gc_cause_setter;
  ZStatTimerMajor            _timer;

public:
  ZDriverMajorGCScope(const ZDriverRequest& request) :
      _gc_id(),
      _gc_cause(request.cause()),
      _gc_cause_setter(ZCollectedHeap::heap(), _gc_cause),
      _timer(ZPhaseMajorCycle) {
    ZOldCollector* const collector = ZHeap::heap()->old_collector();

    collector->set_at_collection_start();

    // Set up soft reference policy
    const bool clear = should_clear_soft_references(request);
    collector->set_soft_reference_policy(clear);
  }

  ~ZDriverMajorGCScope() {
    // Update data used by soft reference policy
    Universe::heap()->update_capacity_and_used_at_gc();

    // Signal that we have completed a visit to all live objects
    Universe::heap()->record_whole_heap_examined_timestamp();
  }
};

class ZDriverOldGCScope : public StackObj {
private:
  GCIdMark                   _gc_id;
  GCCause::Cause             _gc_cause;
  GCCauseSetter              _gc_cause_setter;
  ZStatTimerOld              _timer;
  ZServiceabilityCycleTracer _tracer;

public:
  ZDriverOldGCScope(const ZDriverRequest& request) :
      _gc_id(),
      _gc_cause(GCCause::_z_major_old),
      _gc_cause_setter(ZCollectedHeap::heap(), _gc_cause),
      _timer(ZPhaseOldCycle),
      _tracer(ZCollectorId::old) {
    ZYoungCollector* const young_collector = ZHeap::heap()->young_collector();
    ZOldCollector* const old_collector = ZHeap::heap()->old_collector();

    // Active workers is expected to be set in mark_start. It isn't set yet,
    // but will be set to ConcGCThreads. We set it explicitly now to match
    // the expectations.
    const uint young_nworkers = select_active_young_worker_threads(request);
    young_collector->set_active_workers(young_nworkers);

    // Select number of old worker threads to use
    const uint old_nworkers = select_active_old_worker_threads(request);
    old_collector->set_active_workers(old_nworkers);

    // Update statistics
    old_collector->set_at_generation_collection_start();
  }

  ~ZDriverOldGCScope() {
    ZOldCollector* collector = ZHeap::heap()->old_collector();

    // Update statistics
    collector->stat_cycle()->at_end(_gc_cause, collector->active_workers());
  }
};

void ZDriverMajor::gc(const ZDriverRequest& request) {
  ZDriverMajorGCScope major_scope(request);

  if (_promote_all) {
    _minor->collect(GCCause::_z_major_young_preclean);
  }

  minor_block();

  _promote_all = false;

  if (ZAbort::should_abort()) {
    return;
  }

  ZDriverOldGCScope old_scope(request);

  // Phase 1: Pause Mark Starts
  pause_mark_start();

  minor_start();
  minor_await();

  // Phase 2: Concurrent Mark
  abortable(concurrent_mark);

  // FIXME: Is this still needed now that purge dead remset is gone?
  minor_block();

  // Phase 3: Pause Mark End
  while (!pause_mark_end()) {
    minor_unblock();
    // Phase 3.5: Concurrent Mark Continue
    abortable(concurrent_mark_continue);
    minor_block();
  }

  minor_unblock();

  // Phase 4: Concurrent Mark Free
  abortable(concurrent_mark_free);

  // Phase 5: Concurrent Process Non-Strong References
  abortable(concurrent_process_non_strong_references);

  // Phase 6: Concurrent Reset Relocation Set
  abortable(concurrent_reset_relocation_set);

  // Phase 7: Pause Verify
  pause_verify();

  // Phase 8: Concurrent Select Relocation Set
  abortable(concurrent_select_relocation_set);

  minor_block();

  // Phase 9: Concurrent Roots Remap
  abortable(concurrent_roots_remap);

  // Phase 10: Pause Relocate Start
  pause_relocate_start();

  minor_unblock();

  // Phase 11: Concurrent Relocate
  abortable(concurrent_relocate);

  minor_block();
}

bool ZDriverMajor::should_collect_young_before_major(GCCause::Cause cause) {
  if (cause != GCCause::_metadata_GC_threshold &&
      cause != GCCause::_z_major_timer &&
      cause != GCCause::_z_major_warmup &&
      cause != GCCause::_z_major_allocation_rate &&
      cause != GCCause::_z_major_proactive) {
    // Cause is not relaxed to skip young preclean before major
    return true;
  }

  if (ZHeap::heap()->has_alloc_stalled()) {
    // Even if the cause is relaxed, we have to collect young before major
    // if there is a stall, to ensure OOM is thrown correctly.
    return true;
  }

  // We are now allowed to relax young before major, unless someone
  // specified explicitly that we should not.
  return ScavengeBeforeFullGC;
}

void ZDriverMajor::run_service() {
  // Main loop
  while (!ZAbort::should_abort()) {
    // Wait for GC request
    const ZDriverRequest request = _port.receive();
    if (request.cause() == GCCause::_no_gc) {
      continue;
    }

    ZBreakpoint::at_before_gc();

    minor_block();

    _promote_all = should_collect_young_before_major(request.cause());

    minor_unblock();

    // Run GC
    gc(request);

    // Notify GC completed
    _port.ack();

    minor_unblock();

    // Check for out of memory condition
    check_out_of_memory();

    ZBreakpoint::at_after_gc();
  }
}

void ZDriverMajor::stop_service() {
  _port.send_async(GCCause::_no_gc);
}
