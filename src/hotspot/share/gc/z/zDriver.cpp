/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zDriver.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zMessagePort.inline.hpp"
#include "gc/z/zServiceability.hpp"
#include "gc/z/zStat.hpp"
#include "logging/log.hpp"
#include "memory/universe.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"

static const ZStatPhaseCycle      ZPhaseCycle("Garbage Collection Cycle");
static const ZStatPhasePause      ZPhasePauseMarkStart("Pause Mark Start");
static const ZStatPhaseConcurrent ZPhaseConcurrentMark("Concurrent Mark");
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkContinue("Concurrent Mark Continue");
static const ZStatPhasePause      ZPhasePauseMarkEnd("Pause Mark End");
static const ZStatPhaseConcurrent ZPhaseConcurrentProcessNonStrongReferences("Concurrent Process Non-Strong References");
static const ZStatPhaseConcurrent ZPhaseConcurrentResetRelocationSet("Concurrent Reset Relocation Set");
static const ZStatPhaseConcurrent ZPhaseConcurrentSelectRelocationSet("Concurrent Select Relocation Set");
static const ZStatPhasePause      ZPhasePauseRelocateStart("Pause Relocate Start");
static const ZStatPhaseConcurrent ZPhaseConcurrentRelocated("Concurrent Relocate");
static const ZStatCriticalPhase   ZCriticalPhaseGCLockerStall("GC Locker Stall", false /* verbose */);
static const ZStatSampler         ZSamplerJavaThreads("System", "Java Threads", ZStatUnitThreads);

class VM_ZOperation : public VM_Operation {
private:
  const uint _gc_id;
  bool       _gc_locked;
  bool       _success;

public:
  VM_ZOperation() :
      _gc_id(GCId::current()),
      _gc_locked(false),
      _success(false) {}

  virtual bool needs_inactive_gc_locker() const {
    // An inactive GC locker is needed in operations where we change the bad
    // mask or move objects. Changing the bad mask will invalidate all oops,
    // which makes it conceptually the same thing as moving all objects.
    return false;
  }

  virtual bool do_operation() = 0;

  virtual bool doit_prologue() {
    Heap_lock->lock();
    return true;
  }

  virtual void doit() {
    // Abort if GC locker state is incompatible
    if (needs_inactive_gc_locker() && GCLocker::check_active_before_gc()) {
      _gc_locked = true;
      return;
    }

    // Setup GC id and active marker
    GCIdMark gc_id_mark(_gc_id);
    IsGCActiveMark gc_active_mark;

    // Execute operation
    _success = do_operation();

    // Update statistics
    ZStatSample(ZSamplerJavaThreads, Threads::number_of_threads());
  }

  virtual void doit_epilogue() {
    Heap_lock->unlock();
  }

  bool gc_locked() const {
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

class VM_ZMarkStart : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZMarkStart;
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

class VM_ZMarkEnd : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZMarkEnd;
  }

  virtual bool do_operation() {
    ZStatTimer timer(ZPhasePauseMarkEnd);
    ZServiceabilityMarkEndTracer tracer;
    return ZHeap::heap()->mark_end();
  }
};

class VM_ZRelocateStart : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZRelocateStart;
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

template <typename T>
bool ZDriver::pause() {
  for (;;) {
    T op;
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

void ZDriver::pause_mark_start() {
  pause<VM_ZMarkStart>();
}

void ZDriver::concurrent_mark() {
  ZStatTimer timer(ZPhaseConcurrentMark);
  ZHeap::heap()->mark(true /* initial */);
}

bool ZDriver::pause_mark_end() {
  return pause<VM_ZMarkEnd>();
}

void ZDriver::concurrent_mark_continue() {
  ZStatTimer timer(ZPhaseConcurrentMarkContinue);
  ZHeap::heap()->mark(false /* initial */);
}

void ZDriver::concurrent_process_non_strong_references() {
  ZStatTimer timer(ZPhaseConcurrentProcessNonStrongReferences);
  ZHeap::heap()->process_non_strong_references();
}

void ZDriver::concurrent_reset_relocation_set() {
  ZStatTimer timer(ZPhaseConcurrentResetRelocationSet);
  ZHeap::heap()->reset_relocation_set();
}

void ZDriver::pause_verify() {
  if (VerifyBeforeGC || VerifyDuringGC || VerifyAfterGC) {
    VM_Verify op;
    VMThread::execute(&op);
  }
}

void ZDriver::concurrent_select_relocation_set() {
  ZStatTimer timer(ZPhaseConcurrentSelectRelocationSet);
  ZHeap::heap()->select_relocation_set();
}

void ZDriver::pause_relocate_start() {
  pause<VM_ZRelocateStart>();
}

void ZDriver::concurrent_relocate() {
  ZStatTimer timer(ZPhaseConcurrentRelocated);
  ZHeap::heap()->relocate();
}

void ZDriver::check_out_of_memory() {
  ZHeap::heap()->check_out_of_memory();
}

class ZDriverGCScope : public StackObj {
private:
  GCIdMark      _gc_id;
  GCCauseSetter _gc_cause_setter;
  ZStatTimer    _timer;

public:
  ZDriverGCScope(GCCause::Cause cause) :
      _gc_id(),
      _gc_cause_setter(ZCollectedHeap::heap(), cause),
      _timer(ZPhaseCycle) {
    // Update statistics
    ZStatCycle::at_start();
  }

  ~ZDriverGCScope() {
    // Calculate boost factor
    const double boost_factor = (double)ZHeap::heap()->nconcurrent_worker_threads() /
                                (double)ZHeap::heap()->nconcurrent_no_boost_worker_threads();

    // Update statistics
    ZStatCycle::at_end(boost_factor);

    // Update data used by soft reference policy
    Universe::update_heap_info_at_gc();
  }
};

void ZDriver::gc(GCCause::Cause cause) {
  ZDriverGCScope scope(cause);

  // Phase 1: Pause Mark Start
  pause_mark_start();

  // Phase 2: Concurrent Mark
  concurrent_mark();

  // Phase 3: Pause Mark End
  while (!pause_mark_end()) {
    // Phase 3.5: Concurrent Mark Continue
    concurrent_mark_continue();
  }

  // Phase 4: Concurrent Process Non-Strong References
  concurrent_process_non_strong_references();

  // Phase 5: Concurrent Reset Relocation Set
  concurrent_reset_relocation_set();

  // Phase 6: Pause Verify
  pause_verify();

  // Phase 7: Concurrent Select Relocation Set
  concurrent_select_relocation_set();

  // Phase 8: Pause Relocate Start
  pause_relocate_start();

  // Phase 9: Concurrent Relocate
  concurrent_relocate();
}

void ZDriver::run_service() {
  // Main loop
  while (!should_terminate()) {
    // Wait for GC request
    const GCCause::Cause cause = _gc_cycle_port.receive();
    if (cause == GCCause::_no_gc) {
      continue;
    }

    // Run GC
    gc(cause);

    // Notify GC completed
    _gc_cycle_port.ack();

    // Check for out of memory condition
    check_out_of_memory();
  }
}

void ZDriver::stop_service() {
  _gc_cycle_port.send_async(GCCause::_no_gc);
}
