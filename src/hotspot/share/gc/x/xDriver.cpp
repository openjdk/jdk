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
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "gc/x/xAbort.inline.hpp"
#include "gc/x/xBreakpoint.hpp"
#include "gc/x/xCollectedHeap.hpp"
#include "gc/x/xDriver.hpp"
#include "gc/x/xHeap.inline.hpp"
#include "gc/x/xMessagePort.inline.hpp"
#include "gc/x/xServiceability.hpp"
#include "gc/x/xStat.hpp"
#include "gc/x/xVerify.hpp"
#include "interpreter/oopMapCache.hpp"
#include "logging/log.hpp"
#include "memory/universe.hpp"
#include "runtime/threads.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"

static const XStatPhaseCycle      XPhaseCycle("Garbage Collection Cycle");
static const XStatPhasePause      XPhasePauseMarkStart("Pause Mark Start");
static const XStatPhaseConcurrent XPhaseConcurrentMark("Concurrent Mark");
static const XStatPhaseConcurrent XPhaseConcurrentMarkContinue("Concurrent Mark Continue");
static const XStatPhaseConcurrent XPhaseConcurrentMarkFree("Concurrent Mark Free");
static const XStatPhasePause      XPhasePauseMarkEnd("Pause Mark End");
static const XStatPhaseConcurrent XPhaseConcurrentProcessNonStrongReferences("Concurrent Process Non-Strong References");
static const XStatPhaseConcurrent XPhaseConcurrentResetRelocationSet("Concurrent Reset Relocation Set");
static const XStatPhaseConcurrent XPhaseConcurrentSelectRelocationSet("Concurrent Select Relocation Set");
static const XStatPhasePause      XPhasePauseRelocateStart("Pause Relocate Start");
static const XStatPhaseConcurrent XPhaseConcurrentRelocated("Concurrent Relocate");
static const XStatCriticalPhase   XCriticalPhaseGCLockerStall("GC Locker Stall", false /* verbose */);
static const XStatSampler         XSamplerJavaThreads("System", "Java Threads", XStatUnitThreads);

XDriverRequest::XDriverRequest() :
    XDriverRequest(GCCause::_no_gc) {}

XDriverRequest::XDriverRequest(GCCause::Cause cause) :
    XDriverRequest(cause, ConcGCThreads) {}

XDriverRequest::XDriverRequest(GCCause::Cause cause, uint nworkers) :
    _cause(cause),
    _nworkers(nworkers) {}

bool XDriverRequest::operator==(const XDriverRequest& other) const {
  return _cause == other._cause;
}

GCCause::Cause XDriverRequest::cause() const {
  return _cause;
}

uint XDriverRequest::nworkers() const {
  return _nworkers;
}

class VM_XOperation : public VM_Operation {
private:
  const uint _gc_id;
  bool       _gc_locked;
  bool       _success;

public:
  VM_XOperation() :
      _gc_id(GCId::current()),
      _gc_locked(false),
      _success(false) {}

  virtual bool needs_inactive_gc_locker() const {
    // An inactive GC locker is needed in operations where we change the bad
    // mask or move objects. Changing the bad mask will invalidate all oops,
    // which makes it conceptually the same thing as moving all objects.
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
    // Abort if GC locker state is incompatible
    if (needs_inactive_gc_locker() && GCLocker::check_active_before_gc()) {
      _gc_locked = true;
      return;
    }

    // Setup GC id and active marker
    GCIdMark gc_id_mark(_gc_id);
    IsSTWGCActiveMark gc_active_mark;

    // Verify before operation
    XVerify::before_zoperation();

    // Execute operation
    _success = do_operation();

    // Update statistics
    XStatSample(XSamplerJavaThreads, Threads::number_of_threads());
  }

  virtual void doit_epilogue() {
    Heap_lock->unlock();

    // GC thread root traversal likely used OopMapCache a lot, which
    // might have created lots of old entries. Trigger the cleanup now.
    OopMapCache::trigger_cleanup();
  }

  bool gc_locked() const {
    return _gc_locked;
  }

  bool success() const {
    return _success;
  }
};

class VM_XMarkStart : public VM_XOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_XMarkStart;
  }

  virtual bool needs_inactive_gc_locker() const {
    return true;
  }

  virtual bool do_operation() {
    XStatTimer timer(XPhasePauseMarkStart);
    XServiceabilityPauseTracer tracer;

    XCollectedHeap::heap()->increment_total_collections(true /* full */);

    XHeap::heap()->mark_start();
    return true;
  }
};

class VM_XMarkEnd : public VM_XOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_XMarkEnd;
  }

  virtual bool do_operation() {
    XStatTimer timer(XPhasePauseMarkEnd);
    XServiceabilityPauseTracer tracer;
    return XHeap::heap()->mark_end();
  }
};

class VM_XRelocateStart : public VM_XOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_XRelocateStart;
  }

  virtual bool needs_inactive_gc_locker() const {
    return true;
  }

  virtual bool do_operation() {
    XStatTimer timer(XPhasePauseRelocateStart);
    XServiceabilityPauseTracer tracer;
    XHeap::heap()->relocate_start();
    return true;
  }
};

class VM_XVerify : public VM_Operation {
public:
  virtual VMOp_Type type() const {
    return VMOp_XVerify;
  }

  virtual bool skip_thread_oop_barriers() const {
    return true;
  }

  virtual void doit() {
    XVerify::after_weak_processing();
  }
};

XDriver::XDriver() :
    _gc_cycle_port(),
    _gc_locker_port() {
  set_name("XDriver");
  create_and_start();
}

bool XDriver::is_busy() const {
  return _gc_cycle_port.is_busy();
}

void XDriver::collect(const XDriverRequest& request) {
  switch (request.cause()) {
  case GCCause::_heap_dump:
  case GCCause::_heap_inspection:
  case GCCause::_wb_young_gc:
  case GCCause::_wb_full_gc:
  case GCCause::_dcmd_gc_run:
  case GCCause::_java_lang_system_gc:
  case GCCause::_full_gc_alot:
  case GCCause::_scavenge_alot:
  case GCCause::_jvmti_force_gc:
  case GCCause::_metadata_GC_clear_soft_refs:
  case GCCause::_codecache_GC_aggressive:
    // Start synchronous GC
    _gc_cycle_port.send_sync(request);
    break;

  case GCCause::_z_timer:
  case GCCause::_z_warmup:
  case GCCause::_z_allocation_rate:
  case GCCause::_z_allocation_stall:
  case GCCause::_z_proactive:
  case GCCause::_z_high_usage:
  case GCCause::_codecache_GC_threshold:
  case GCCause::_metadata_GC_threshold:
    // Start asynchronous GC
    _gc_cycle_port.send_async(request);
    break;

  case GCCause::_gc_locker:
    // Restart VM operation previously blocked by the GC locker
    _gc_locker_port.signal();
    break;

  case GCCause::_wb_breakpoint:
    XBreakpoint::start_gc();
    _gc_cycle_port.send_async(request);
    break;

  default:
    // Other causes not supported
    fatal("Unsupported GC cause (%s)", GCCause::to_string(request.cause()));
    break;
  }
}

template <typename T>
bool XDriver::pause() {
  for (;;) {
    T op;
    VMThread::execute(&op);
    if (op.gc_locked()) {
      // Wait for GC to become unlocked and restart the VM operation
      XStatTimer timer(XCriticalPhaseGCLockerStall);
      _gc_locker_port.wait();
      continue;
    }

    // Notify VM operation completed
    _gc_locker_port.ack();

    return op.success();
  }
}

void XDriver::pause_mark_start() {
  pause<VM_XMarkStart>();
}

void XDriver::concurrent_mark() {
  XStatTimer timer(XPhaseConcurrentMark);
  XBreakpoint::at_after_marking_started();
  XHeap::heap()->mark(true /* initial */);
  XBreakpoint::at_before_marking_completed();
}

bool XDriver::pause_mark_end() {
  return pause<VM_XMarkEnd>();
}

void XDriver::concurrent_mark_continue() {
  XStatTimer timer(XPhaseConcurrentMarkContinue);
  XHeap::heap()->mark(false /* initial */);
}

void XDriver::concurrent_mark_free() {
  XStatTimer timer(XPhaseConcurrentMarkFree);
  XHeap::heap()->mark_free();
}

void XDriver::concurrent_process_non_strong_references() {
  XStatTimer timer(XPhaseConcurrentProcessNonStrongReferences);
  XBreakpoint::at_after_reference_processing_started();
  XHeap::heap()->process_non_strong_references();
}

void XDriver::concurrent_reset_relocation_set() {
  XStatTimer timer(XPhaseConcurrentResetRelocationSet);
  XHeap::heap()->reset_relocation_set();
}

void XDriver::pause_verify() {
  if (ZVerifyRoots || ZVerifyObjects) {
    VM_XVerify op;
    VMThread::execute(&op);
  }
}

void XDriver::concurrent_select_relocation_set() {
  XStatTimer timer(XPhaseConcurrentSelectRelocationSet);
  XHeap::heap()->select_relocation_set();
}

void XDriver::pause_relocate_start() {
  pause<VM_XRelocateStart>();
}

void XDriver::concurrent_relocate() {
  XStatTimer timer(XPhaseConcurrentRelocated);
  XHeap::heap()->relocate();
}

void XDriver::check_out_of_memory() {
  XHeap::heap()->check_out_of_memory();
}

static bool should_clear_soft_references(const XDriverRequest& request) {
  // Clear soft references if implied by the GC cause
  if (request.cause() == GCCause::_wb_full_gc ||
      request.cause() == GCCause::_metadata_GC_clear_soft_refs ||
      request.cause() == GCCause::_z_allocation_stall) {
    // Clear
    return true;
  }

  // Don't clear
  return false;
}

static uint select_active_worker_threads_dynamic(const XDriverRequest& request) {
  // Use requested number of worker threads
  return request.nworkers();
}

static uint select_active_worker_threads_static(const XDriverRequest& request) {
  const GCCause::Cause cause = request.cause();
  const uint nworkers = request.nworkers();

  // Boost number of worker threads if implied by the GC cause
  if (cause == GCCause::_wb_full_gc ||
      cause == GCCause::_java_lang_system_gc ||
      cause == GCCause::_metadata_GC_clear_soft_refs ||
      cause == GCCause::_z_allocation_stall) {
    // Boost
    const uint boosted_nworkers = MAX2(nworkers, ParallelGCThreads);
    return boosted_nworkers;
  }

  // Use requested number of worker threads
  return nworkers;
}

static uint select_active_worker_threads(const XDriverRequest& request) {
  if (UseDynamicNumberOfGCThreads) {
    return select_active_worker_threads_dynamic(request);
  } else {
    return select_active_worker_threads_static(request);
  }
}

class XDriverGCScope : public StackObj {
private:
  GCIdMark                   _gc_id;
  GCCause::Cause             _gc_cause;
  GCCauseSetter              _gc_cause_setter;
  XStatTimer                 _timer;
  XServiceabilityCycleTracer _tracer;

public:
  XDriverGCScope(const XDriverRequest& request) :
      _gc_id(),
      _gc_cause(request.cause()),
      _gc_cause_setter(XCollectedHeap::heap(), _gc_cause),
      _timer(XPhaseCycle),
      _tracer() {
    // Update statistics
    XStatCycle::at_start();

    // Set up soft reference policy
    const bool clear = should_clear_soft_references(request);
    XHeap::heap()->set_soft_reference_policy(clear);

    // Select number of worker threads to use
    const uint nworkers = select_active_worker_threads(request);
    XHeap::heap()->set_active_workers(nworkers);
  }

  ~XDriverGCScope() {
    // Update statistics
    XStatCycle::at_end(_gc_cause, XHeap::heap()->active_workers());

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
    if (should_terminate()) {         \
      return;                         \
    }                                 \
  } while (false)

void XDriver::gc(const XDriverRequest& request) {
  XDriverGCScope scope(request);

  // Phase 1: Pause Mark Start
  pause_mark_start();

  // Phase 2: Concurrent Mark
  concurrent(mark);

  // Phase 3: Pause Mark End
  while (!pause_mark_end()) {
    // Phase 3.5: Concurrent Mark Continue
    concurrent(mark_continue);
  }

  // Phase 4: Concurrent Mark Free
  concurrent(mark_free);

  // Phase 5: Concurrent Process Non-Strong References
  concurrent(process_non_strong_references);

  // Phase 6: Concurrent Reset Relocation Set
  concurrent(reset_relocation_set);

  // Phase 7: Pause Verify
  pause_verify();

  // Phase 8: Concurrent Select Relocation Set
  concurrent(select_relocation_set);

  // Phase 9: Pause Relocate Start
  pause_relocate_start();

  // Phase 10: Concurrent Relocate
  concurrent(relocate);
}

void XDriver::run_service() {
  // Main loop
  while (!should_terminate()) {
    // Wait for GC request
    const XDriverRequest request = _gc_cycle_port.receive();
    if (request.cause() == GCCause::_no_gc) {
      continue;
    }

    XBreakpoint::at_before_gc();

    // Run GC
    gc(request);

    if (should_terminate()) {
      // Abort
      break;
    }

    // Notify GC completed
    _gc_cycle_port.ack();

    // Check for out of memory condition
    check_out_of_memory();

    XBreakpoint::at_after_gc();
  }
}

void XDriver::stop_service() {
  XAbort::abort();
  _gc_cycle_port.send_async(GCCause::_no_gc);
}
