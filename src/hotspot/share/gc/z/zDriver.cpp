/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

static const ZStatPhaseCycle      ZPhaseMinorCycle(ZCycleId::_minor, "Minor Garbage Collection Cycle");
static const ZStatPhaseCycle      ZPhaseMajorCycle(ZCycleId::_major, "Major Garbage Collection Cycle");

static const ZStatPhasePause      ZPhasePauseMinorMarkStart("Pause Minor Mark Start");
static const ZStatPhaseConcurrent ZPhaseConcurrentMinorMark("Concurrent Minor Mark");
static const ZStatPhaseConcurrent ZPhaseConcurrentMinorMarkContinue("Concurrent Minor Mark Continue");
static const ZStatPhasePause      ZPhasePauseMinorMarkEnd("Pause Minor Mark End");
static const ZStatPhaseConcurrent ZPhaseConcurrentMinorResetRelocationSet("Concurrent Minor Reset Relocation Set");
static const ZStatPhaseConcurrent ZPhaseConcurrentMinorSelectRelocationSet("Concurrent Minor Select Relocation Set");
static const ZStatPhasePause      ZPhasePauseMinorRelocateStart("Pause Minor Relocate Start");
static const ZStatPhaseConcurrent ZPhaseConcurrentMinorRelocated("Concurrent Minor Relocate");

static const ZStatPhasePause      ZPhasePauseMajorMarkStart("Pause Major Mark Start");
static const ZStatPhaseConcurrent ZPhaseConcurrentMajorMark("Concurrent Major Mark");
static const ZStatPhaseConcurrent ZPhaseConcurrentMajorMarkContinue("Concurrent Major Mark Continue");
static const ZStatPhasePause      ZPhasePauseMajorMarkEnd("Pause Major Mark End");
static const ZStatPhaseConcurrent ZPhaseConcurrentMajorProcessNonStrongReferences("Concurrent Major Process Non-Strong References");
static const ZStatPhaseConcurrent ZPhaseConcurrentMajorResetRelocationSet("Concurrent Major Reset Relocation Set");
static const ZStatPhaseConcurrent ZPhaseConcurrentMajorSelectRelocationSet("Concurrent Major Select Relocation Set");
static const ZStatPhasePause      ZPhasePauseMajorRelocateStart("Pause Major Relocate Start");
static const ZStatPhaseConcurrent ZPhaseConcurrentMajorRelocated("Concurrent Major Relocate");
static const ZStatPhaseConcurrent ZPhaseConcurrentMajorRootsRemap("Concurrent Major Roots Remap");

static const ZStatSampler         ZSamplerJavaThreads("System", "Java Threads", ZStatUnitThreads);

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
    // FIXME: Need to prevent verification when minor collection pauses happen
    // during major resurrection block window.
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

class VM_ZMinorMarkStart : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZMinorMarkStart;
  }

  virtual bool block_jni_critical() const {
    return true;
  }

  virtual bool do_operation() {
    ZStatTimerMinor timer(ZPhasePauseMinorMarkStart);
    ZServiceabilityPauseTracer tracer;

    ZCollectedHeap::heap()->increment_total_collections(false /* full */);
    ZHeap::heap()->minor_cycle()->mark_start();
    return true;
  }
};

class VM_ZMinorMarkEnd : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZMinorMarkEnd;
  }

  virtual bool do_operation() {
    ZStatTimerMinor timer(ZPhasePauseMinorMarkEnd);
    ZServiceabilityPauseTracer tracer;
    return ZHeap::heap()->minor_cycle()->mark_end();
  }
};

class VM_ZMinorRelocateStart : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZMinorRelocateStart;
  }

  virtual bool block_jni_critical() const {
    return true;
  }

  virtual bool do_operation() {
    ZStatTimerMinor timer(ZPhasePauseMinorRelocateStart);
    ZServiceabilityPauseTracer tracer;
    ZHeap::heap()->minor_cycle()->relocate_start();
    return true;
  }
};

ZDriverMinor::ZDriverMinor() :
    _port(),
    _lock(),
    _active(false),
    _blocked(false),
    _await(false) {
  set_name("ZDriverMinor");
  create_and_start();
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
  collect(GCCause::_z_minor_inside_major);
  unblock();
}

void ZDriverMinor::await() {
  ZLocker<ZConditionLock> locker(&_lock);
  while (_await) {
    _lock.wait();
  }
}

void ZDriverMinor::collect(GCCause::Cause cause) {
  switch (cause) {
  case GCCause::_wb_young_gc:
  case GCCause::_scavenge_alot:
  case GCCause::_z_minor_timer:
  case GCCause::_z_minor_warmup:
  case GCCause::_z_minor_allocation_rate:
  case GCCause::_z_minor_allocation_stall:
  case GCCause::_z_minor_proactive:
  case GCCause::_z_minor_high_usage:
  case GCCause::_z_minor_inside_major:
    // Start asynchronous GC
    _port.send_async(cause);
    break;

  case GCCause::_z_minor_before_major:
    // Start synchronous GC
    _port.send_sync(cause);
    break;

  default:
    // Other causes not supported
    fatal("Unsupported GC cause (%s)", GCCause::to_string(cause));
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

void ZDriverMinor::pause_mark_start() {
  if (ZHeap::heap()->minor_cycle()->should_skip_mark_start()) {
    // A major mark start also performs a minor mark start. So the next
    // minor cycle after a major mark start, doesn't run minor mark start.
    return;
  }

  pause<VM_ZMinorMarkStart>();
}

void ZDriverMinor::concurrent_mark() {
  ZStatTimerMinor timer(ZPhaseConcurrentMinorMark);
  ZHeap::heap()->minor_cycle()->mark_roots();
  ZHeap::heap()->minor_cycle()->mark_follow();
}

bool ZDriverMinor::pause_mark_end() {
  return pause<VM_ZMinorMarkEnd>();
}

void ZDriverMinor::concurrent_mark_continue() {
  ZStatTimerMinor timer(ZPhaseConcurrentMinorMarkContinue);
  ZHeap::heap()->minor_cycle()->mark_follow();
}

void ZDriverMinor::concurrent_reset_relocation_set() {
  ZStatTimerMinor timer(ZPhaseConcurrentMinorResetRelocationSet);
  ZHeap::heap()->minor_cycle()->reset_relocation_set();
}

void ZDriverMinor::concurrent_select_relocation_set() {
  ZStatTimerMinor timer(ZPhaseConcurrentMinorSelectRelocationSet);
  ZHeap::heap()->minor_cycle()->select_relocation_set();
}

void ZDriverMinor::pause_relocate_start() {
  pause<VM_ZMinorRelocateStart>();
}

void ZDriverMinor::concurrent_relocate() {
  ZStatTimerMinor timer(ZPhaseConcurrentMinorRelocated);
  ZHeap::heap()->minor_cycle()->relocate();
}

class ZDriverMinorGCScope : public StackObj {
private:
  GCIdMark                   _gc_id;
  GCCause::Cause             _gc_cause;
  GCCauseSetter              _gc_cause_setter;
  ZStatTimerMinor            _timer;
  ZServiceabilityCycleTracer _tracer;

public:
  ZDriverMinorGCScope(GCCause::Cause cause) :
      _gc_id(),
      _gc_cause(cause),
      _gc_cause_setter(ZCollectedHeap::heap(), cause),
      _timer(ZPhaseMinorCycle),
      _tracer() {
    // Update statistics
    ZHeap::heap()->minor_cycle()->stat_cycle()->at_start();
  }

  ~ZDriverMinorGCScope() {
    // Calculate boost factor
    ZMinorCycle* cycle = ZHeap::heap()->minor_cycle();
    const double boost_factor = (double)cycle->nconcurrent_worker_threads() /
                                (double)cycle->nconcurrent_no_boost_worker_threads();

    // Update statistics
    ZHeap::heap()->minor_cycle()->stat_cycle()->at_end(_gc_cause, boost_factor);
  }
};

void ZDriverMinor::gc(GCCause::Cause cause) {
  ZDriverMinorGCScope scope(cause);

  // Phase 1: Pause Mark Start
  pause_mark_start();

  // Phase 2: Concurrent Mark
  concurrent_mark();

  // Phase 3: Pause Mark End
  while (!pause_mark_end()) {
    // Phase 3.5: Concurrent Mark Continue
    concurrent_mark_continue();
  }

  // Phase 4: Concurrent Reset Relocation Set
  concurrent_reset_relocation_set();

  // Phase 5: Concurrent Select Relocation Set
  concurrent_select_relocation_set();

  // Phase 6: Pause Relocate Start
  pause_relocate_start();

  // Phase 7: Concurrent Relocate
  concurrent_relocate();
}

void ZDriverMinor::run_service() {
  // Main loop
  while (!should_terminate()) {
    // Wait for GC request
    const GCCause::Cause cause = _port.receive();
    if (cause == GCCause::_no_gc) {
      continue;
    }

    active();

    // Run GC
    gc(cause);

    // Notify GC completed
    _port.ack();

    inactive();
  }
}

void ZDriverMinor::stop_service() {
  _port.send_async(GCCause::_no_gc);
}

static bool should_clear_soft_references() {
  // Clear if one or more allocations have stalled
  const bool stalled = ZHeap::heap()->is_alloc_stalled();
  if (stalled) {
    // Clear
    return true;
  }

  // Clear if implied by the GC cause
  const GCCause::Cause cause = ZCollectedHeap::heap()->gc_cause();
  if (cause == GCCause::_wb_full_gc ||
      cause == GCCause::_metadata_GC_clear_soft_refs) {
    // Clear
    return true;
  }

  // Don't clear
  return false;
}

static bool should_boost_worker_threads() {
  // Boost worker threads if one or more allocations have stalled
  const bool stalled = ZHeap::heap()->is_alloc_stalled();
  if (stalled) {
    // Boost
    return true;
  }

  // Boost worker threads if implied by the GC cause
  const GCCause::Cause cause = ZCollectedHeap::heap()->gc_cause();
  if (cause == GCCause::_wb_full_gc ||
      cause == GCCause::_java_lang_system_gc ||
      cause == GCCause::_metadata_GC_clear_soft_refs) {
    // Boost
    return true;
  }

  // Don't boost
  return false;
}

class VM_ZMajorMarkStart : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZMajorMarkStart;
  }

  virtual bool block_jni_critical() const {
    return true;
  }

  virtual bool do_operation() {
    //ClassLoaderDataGraph::clear_claimed_marks(ClassLoaderData::_claim_strong);
    ClassLoaderDataGraph::verify_claimed_marks_not(ClassLoaderData::_claim_strong);

    ZStatTimerMajor timer(ZPhasePauseMajorMarkStart);
    ZServiceabilityPauseTracer tracer;
    ZMajorCycle* cycle = ZHeap::heap()->major_cycle();

    // Set up soft reference policy
    const bool clear = should_clear_soft_references();
    cycle->set_soft_reference_policy(clear);

    // Set up boost mode
    const bool boost = should_boost_worker_threads();
    cycle->set_boost_worker_threads(boost);

    ZCollectedHeap::heap()->increment_total_collections(true /* full */);

    cycle->mark_start();
    ZHeap::heap()->minor_cycle()->mark_start();
    ZHeap::heap()->minor_cycle()->skip_mark_start();
    return true;
  }
};

class VM_ZMajorMarkEnd : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZMajorMarkEnd;
  }

  virtual bool do_operation() {
    ZStatTimerMajor timer(ZPhasePauseMajorMarkEnd);
    ZServiceabilityPauseTracer tracer;
    return ZHeap::heap()->major_cycle()->mark_end();
  }
};

class VM_ZMajorRelocateStart : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZMajorRelocateStart;
  }

  virtual bool block_jni_critical() const {
    return true;
  }

  virtual bool do_operation() {
    ZStatTimerMajor timer(ZPhasePauseMajorRelocateStart);
    ZServiceabilityPauseTracer tracer;
    ZHeap::heap()->major_cycle()->relocate_start();
    return true;
  }
};

class VM_ZMajorVerify : public VM_Operation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZMajorVerify;
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
    _minor(minor) {
  set_name("ZDriverMajor");
  create_and_start();
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

void ZDriverMajor::collect(GCCause::Cause cause) {
  switch (cause) {
  case GCCause::_wb_conc_mark:
  case GCCause::_wb_full_gc:
  case GCCause::_dcmd_gc_run:
  case GCCause::_java_lang_system_gc:
  case GCCause::_full_gc_alot:
  case GCCause::_jvmti_force_gc:
  case GCCause::_metadata_GC_clear_soft_refs:
    // Start synchronous GC
    _port.send_sync(cause);
    break;

  case GCCause::_z_major_timer:
  case GCCause::_z_major_warmup:
  case GCCause::_z_major_allocation_rate:
  case GCCause::_z_major_allocation_stall:
  case GCCause::_z_major_proactive:
  case GCCause::_z_major_high_usage:
  case GCCause::_metadata_GC_threshold:
    // Start asynchronous GC
    _port.send_async(cause);
    break;

  case GCCause::_wb_breakpoint:
    ZBreakpoint::start_gc();
    _port.send_async(cause);
    break;

  default:
    // Delegate other causes to minor driver
    _minor->collect(cause);
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
  pause<VM_ZMajorMarkStart>();
}

void ZDriverMajor::concurrent_mark() {
  ZStatTimerMajor timer(ZPhaseConcurrentMajorMark);
  ZBreakpoint::at_after_marking_started();
  ZHeap::heap()->major_cycle()->mark_roots();
  ZHeap::heap()->major_cycle()->mark_follow();
  ZBreakpoint::at_before_marking_completed();
}

bool ZDriverMajor::pause_mark_end() {
  return pause<VM_ZMajorMarkEnd>();
}

void ZDriverMajor::concurrent_mark_continue() {
  ZStatTimerMajor timer(ZPhaseConcurrentMajorMarkContinue);
  ZHeap::heap()->major_cycle()->mark_follow();
}

void ZDriverMajor::concurrent_process_non_strong_references() {
  ZStatTimerMajor timer(ZPhaseConcurrentMajorProcessNonStrongReferences);
  ZBreakpoint::at_after_reference_processing_started();
  ZHeap::heap()->major_cycle()->process_non_strong_references();
}

void ZDriverMajor::concurrent_reset_relocation_set() {
  ZStatTimerMajor timer(ZPhaseConcurrentMajorResetRelocationSet);
  ZHeap::heap()->major_cycle()->reset_relocation_set();
}

void ZDriverMajor::pause_verify() {
  if (VerifyBeforeGC || VerifyDuringGC || VerifyAfterGC) {
    // Full verification
    VM_Verify op;
    VMThread::execute(&op);
  } else if (ZVerifyRoots || ZVerifyObjects) {
    // Limited verification
    VM_ZMajorVerify op;
    VMThread::execute(&op);
  }
}

void ZDriverMajor::concurrent_select_relocation_set() {
  ZStatTimerMajor timer(ZPhaseConcurrentMajorSelectRelocationSet);
  ZHeap::heap()->major_cycle()->select_relocation_set();
}

void ZDriverMajor::pause_relocate_start() {
  pause<VM_ZMajorRelocateStart>();
}

void ZDriverMajor::concurrent_relocate() {
  ZStatTimerMajor timer(ZPhaseConcurrentMajorRelocated);
  ZHeap::heap()->major_cycle()->relocate();
}

void ZDriverMajor::concurrent_roots_remap() {
  ZStatTimerMajor timer(ZPhaseConcurrentMajorRootsRemap);
  ZHeap::heap()->major_cycle()->roots_remap();
}

void ZDriverMajor::check_out_of_memory() {
  ZHeap::heap()->check_out_of_memory();
}

class ZDriverMajorGCScope : public StackObj {
private:
  GCIdMark                   _gc_id;
  GCCause::Cause             _gc_cause;
  GCCauseSetter              _gc_cause_setter;
  ZStatTimerMajor            _timer;
  ZServiceabilityCycleTracer _tracer;

public:
  ZDriverMajorGCScope(GCCause::Cause cause) :
      _gc_id(),
      _gc_cause(cause),
      _gc_cause_setter(ZCollectedHeap::heap(), cause),
      _timer(ZPhaseMajorCycle),
      _tracer() {

    // Update statistics
    ZHeap::heap()->major_cycle()->stat_cycle()->at_start();
  }

  ~ZDriverMajorGCScope() {
    // Calculate boost factor
    ZMajorCycle* cycle = ZHeap::heap()->major_cycle();
    const double boost_factor = (double)cycle->nconcurrent_worker_threads() /
                                (double)cycle->nconcurrent_no_boost_worker_threads();

    // Update statistics
    cycle->stat_cycle()->at_end(_gc_cause, boost_factor);

    // Update data used by soft reference policy
    Universe::heap()->update_capacity_and_used_at_gc();

    // Signal that we have completed a visit to all live objects
    Universe::heap()->record_whole_heap_examined_timestamp();
  }
};

// Macro to execute a termination check after a concurrent phase. Note
// that it's important that the termination check comes after the call
// to the function f, since we can't abort between pause_relocate_start()
// and concurrent_relocate(). We need to let concurrent_relocate() call
// abort_page() on the remaining entries in the relocation set.
#define concurrent(f)                 \
  do {                                \
    concurrent_##f();                 \
    if (false && should_terminate()) {         \
      minor_block();                  \
      return;                         \
    }                                 \
  } while (false)

void ZDriverMajor::gc(GCCause::Cause cause) {
  ZDriverMajorGCScope scope(cause);

  // Phase 1: Pause Mark Starts
  pause_mark_start();

  minor_start();

  // Phase 2: Concurrent Mark
  concurrent(mark);

  minor_await();

  // FIXME: Is this still needed now that purge dead remset is gone?
  minor_block();

  // Phase 3: Pause Mark End
  while (!pause_mark_end()) {
    minor_unblock();
    // Phase 3.5: Concurrent Mark Continue
    concurrent(mark_continue);
    minor_block();
  }

  minor_unblock();

  // Phase 5: Concurrent Process Non-Strong References
  concurrent(process_non_strong_references);

  // Phase 6: Concurrent Reset Relocation Set
  concurrent(reset_relocation_set);

  // Phase 7: Pause Verify
  pause_verify();

  // Phase 8: Concurrent Select Relocation Set
  concurrent(select_relocation_set);

  minor_block();

  // Phase 9: Concurrent Roots Remap
  concurrent_roots_remap();

  // Phase 10: Pause Relocate Start
  pause_relocate_start();

  minor_unblock();

  // Phase 11: Concurrent Relocate
  concurrent(relocate);

  minor_block();
}

void ZDriverMajor::run_service() {
  // Main loop
  while (!should_terminate()) {
    // Wait for GC request
    const GCCause::Cause cause = _port.receive();
    if (cause == GCCause::_no_gc) {
      continue;
    }

    ZBreakpoint::at_before_gc();

    if (ScavengeBeforeFullGC) {
      _minor->collect(GCCause::_z_minor_before_major);
    }

    minor_block();

    // Run GC
    gc(cause);

    // Notify GC completed
    _port.ack();

    minor_unblock();

    // Check for out of memory condition
    check_out_of_memory();

    ZBreakpoint::at_after_gc();
  }
}

void ZDriverMajor::stop_service() {
  // Temporary disabled until ZDriverMinor knows how to abort
  //ZAbort::abort();
  _port.send_async(GCCause::_no_gc);
}
