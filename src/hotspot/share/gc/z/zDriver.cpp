/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "gc/shared/vmGCOperations.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zDriver.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zMessagePort.inline.hpp"
#include "gc/z/zServiceability.hpp"
#include "gc/z/zStat.hpp"
#include "logging/log.hpp"
#include "runtime/vm_operations.hpp"
#include "runtime/vmThread.hpp"

static const ZStatPhaseCycle      ZPhaseCycle("Garbage Collection Cycle");
static const ZStatPhasePause      ZPhasePauseMarkStart("Pause Mark Start");
static const ZStatPhaseConcurrent ZPhaseConcurrentMark("Concurrent Mark");
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkContinue("Concurrent Mark Continue");
static const ZStatPhasePause      ZPhasePauseMarkEnd("Pause Mark End");
static const ZStatPhaseConcurrent ZPhaseConcurrentProcessNonStrongReferences("Concurrent Process Non-Strong References");
static const ZStatPhaseConcurrent ZPhaseConcurrentResetRelocationSet("Concurrent Reset Relocation Set");
static const ZStatPhaseConcurrent ZPhaseConcurrentDestroyDetachedPages("Concurrent Destroy Detached Pages");
static const ZStatPhaseConcurrent ZPhaseConcurrentSelectRelocationSet("Concurrent Select Relocation Set");
static const ZStatPhaseConcurrent ZPhaseConcurrentPrepareRelocationSet("Concurrent Prepare Relocation Set");
static const ZStatPhasePause      ZPhasePauseRelocateStart("Pause Relocate Start");
static const ZStatPhaseConcurrent ZPhaseConcurrentRelocated("Concurrent Relocate");
static const ZStatCriticalPhase   ZCriticalPhaseGCLockerStall("GC Locker Stall", false /* verbose */);
static const ZStatSampler         ZSamplerJavaThreads("System", "Java Threads", ZStatUnitThreads);

class ZOperationClosure : public StackObj {
public:
  virtual const char* name() const = 0;

  virtual bool needs_inactive_gc_locker() const {
    // An inactive GC locker is needed in operations where we change the good
    // mask or move objects. Changing the good mask will invalidate all oops,
    // which makes it conceptually the same thing as moving all objects.
    return false;
  }

  virtual bool do_operation() = 0;
};

class VM_ZOperation : public VM_Operation {
private:
  ZOperationClosure* _cl;
  uint               _gc_id;
  bool               _gc_locked;
  bool               _success;

public:
  VM_ZOperation(ZOperationClosure* cl) :
      _cl(cl),
      _gc_id(GCId::current()),
      _gc_locked(false),
      _success(false) {}

  virtual VMOp_Type type() const {
    return VMOp_ZOperation;
  }

  virtual const char* name() const {
    return _cl->name();
  }

  virtual bool doit_prologue() {
    Heap_lock->lock();
    return true;
  }

  virtual void doit() {
    assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

    ZStatSample(ZSamplerJavaThreads, Threads::number_of_threads());

    // JVMTI support
    SvcGCMarker sgcm(SvcGCMarker::OTHER);

    // Setup GC id
    GCIdMark gcid(_gc_id);

    if (_cl->needs_inactive_gc_locker() && GCLocker::check_active_before_gc()) {
      // GC locker is active, bail out
      _gc_locked = true;
    } else {
      // Execute operation
      IsGCActiveMark mark;
      _success = _cl->do_operation();
    }
  }

  virtual void doit_epilogue() {
    Heap_lock->unlock();
  }

  bool gc_locked() {
    return _gc_locked;
  }

  bool success() const {
    return _success;
  }
};

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

class ZMarkStartClosure : public ZOperationClosure {
public:
  virtual const char* name() const {
    return "ZMarkStart";
  }

  virtual bool needs_inactive_gc_locker() const {
    return true;
  }

  virtual bool do_operation() {
    ZStatTimer timer(ZPhasePauseMarkStart);
    ZServiceabilityMarkStartTracer tracer;

    // Set up soft reference policy
    const bool clear = should_clear_soft_references();
    ZHeap::heap()->set_soft_reference_policy(clear);

    // Set up boost mode
    const bool boost = should_boost_worker_threads();
    ZHeap::heap()->set_boost_worker_threads(boost);

    ZCollectedHeap::heap()->increment_total_collections(true /* full */);

    ZHeap::heap()->mark_start();
    return true;
  }
};

class ZMarkEndClosure : public ZOperationClosure {
public:
  virtual const char* name() const {
    return "ZMarkEnd";
  }

  virtual bool do_operation() {
    ZStatTimer timer(ZPhasePauseMarkEnd);
    ZServiceabilityMarkEndTracer tracer;

    return ZHeap::heap()->mark_end();
  }
};

class ZRelocateStartClosure : public ZOperationClosure {
public:
  virtual const char* name() const {
    return "ZRelocateStart";
  }

  virtual bool needs_inactive_gc_locker() const {
    return true;
  }

  virtual bool do_operation() {
    ZStatTimer timer(ZPhasePauseRelocateStart);
    ZServiceabilityRelocateStartTracer tracer;

    ZHeap::heap()->relocate_start();
    return true;
  }
};

ZDriver::ZDriver() :
    _gc_cycle_port(),
    _gc_locker_port() {
  set_name("ZDriver");
  create_and_start();
}

bool ZDriver::vm_operation(ZOperationClosure* cl) {
  for (;;) {
    VM_ZOperation op(cl);
    VMThread::execute(&op);
    if (op.gc_locked()) {
      // Wait for GC to become unlocked and restart the VM operation
      ZStatTimer timer(ZCriticalPhaseGCLockerStall);
      _gc_locker_port.wait();
      continue;
    }

    // Notify VM operation completed
    _gc_locker_port.ack();

    return op.success();
  }
}

void ZDriver::collect(GCCause::Cause cause) {
  switch (cause) {
  case GCCause::_wb_young_gc:
  case GCCause::_wb_conc_mark:
  case GCCause::_wb_full_gc:
  case GCCause::_dcmd_gc_run:
  case GCCause::_java_lang_system_gc:
  case GCCause::_full_gc_alot:
  case GCCause::_scavenge_alot:
  case GCCause::_jvmti_force_gc:
  case GCCause::_metadata_GC_clear_soft_refs:
    // Start synchronous GC
    _gc_cycle_port.send_sync(cause);
    break;

  case GCCause::_z_timer:
  case GCCause::_z_warmup:
  case GCCause::_z_allocation_rate:
  case GCCause::_z_allocation_stall:
  case GCCause::_z_proactive:
  case GCCause::_metadata_GC_threshold:
    // Start asynchronous GC
    _gc_cycle_port.send_async(cause);
    break;

  case GCCause::_gc_locker:
    // Restart VM operation previously blocked by the GC locker
    _gc_locker_port.signal();
    break;

  default:
    // Other causes not supported
    fatal("Unsupported GC cause (%s)", GCCause::to_string(cause));
    break;
  }
}

GCCause::Cause ZDriver::start_gc_cycle() {
  // Wait for GC request
  return _gc_cycle_port.receive();
}

class ZDriverCycleScope : public StackObj {
private:
  GCIdMark      _gc_id;
  GCCauseSetter _gc_cause_setter;
  ZStatTimer    _timer;

public:
  ZDriverCycleScope(GCCause::Cause cause) :
      _gc_id(),
      _gc_cause_setter(ZCollectedHeap::heap(), cause),
      _timer(ZPhaseCycle) {
    // Update statistics
    ZStatCycle::at_start();
  }

  ~ZDriverCycleScope() {
    // Calculate boost factor
    const double boost_factor = (double)ZHeap::heap()->nconcurrent_worker_threads() /
                                (double)ZHeap::heap()->nconcurrent_no_boost_worker_threads();

    // Update statistics
    ZStatCycle::at_end(boost_factor);

    // Update data used by soft reference policy
    Universe::update_heap_info_at_gc();
  }
};

void ZDriver::run_gc_cycle(GCCause::Cause cause) {
  ZDriverCycleScope scope(cause);

  // Phase 1: Pause Mark Start
  {
    ZMarkStartClosure cl;
    vm_operation(&cl);
  }

  // Phase 2: Concurrent Mark
  {
    ZStatTimer timer(ZPhaseConcurrentMark);
    ZHeap::heap()->mark();
  }

  // Phase 3: Pause Mark End
  {
    ZMarkEndClosure cl;
    while (!vm_operation(&cl)) {
      // Phase 3.5: Concurrent Mark Continue
      ZStatTimer timer(ZPhaseConcurrentMarkContinue);
      ZHeap::heap()->mark();
    }
  }

  // Phase 4: Concurrent Process Non-Strong References
  {
    ZStatTimer timer(ZPhaseConcurrentProcessNonStrongReferences);
    ZHeap::heap()->process_non_strong_references();
  }

  // Phase 5: Concurrent Reset Relocation Set
  {
    ZStatTimer timer(ZPhaseConcurrentResetRelocationSet);
    ZHeap::heap()->reset_relocation_set();
  }

  // Phase 6: Concurrent Destroy Detached Pages
  {
    ZStatTimer timer(ZPhaseConcurrentDestroyDetachedPages);
    ZHeap::heap()->destroy_detached_pages();
  }

  // Phase 7: Concurrent Select Relocation Set
  {
    ZStatTimer timer(ZPhaseConcurrentSelectRelocationSet);
    ZHeap::heap()->select_relocation_set();
  }

  // Phase 8: Concurrent Prepare Relocation Set
  {
    ZStatTimer timer(ZPhaseConcurrentPrepareRelocationSet);
    ZHeap::heap()->prepare_relocation_set();
  }

  // Phase 9: Pause Relocate Start
  {
    ZRelocateStartClosure cl;
    vm_operation(&cl);
  }

  // Phase 10: Concurrent Relocate
  {
    ZStatTimer timer(ZPhaseConcurrentRelocated);
    ZHeap::heap()->relocate();
  }
}

void ZDriver::end_gc_cycle() {
  // Notify GC cycle completed
  _gc_cycle_port.ack();

  // Check for out of memory condition
  ZHeap::heap()->check_out_of_memory();
}

void ZDriver::run_service() {
  // Main loop
  while (!should_terminate()) {
    const GCCause::Cause cause = start_gc_cycle();
    if (cause != GCCause::_no_gc) {
      run_gc_cycle(cause);
      end_gc_cycle();
    }
  }
}

void ZDriver::stop_service() {
  _gc_cycle_port.send_async(GCCause::_no_gc);
}
