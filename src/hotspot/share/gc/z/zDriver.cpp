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

static const ZStatPhaseCollection ZPhaseCollectionMinor("Minor Garbage Collection");
static const ZStatPhaseCollection ZPhaseCollectionMajor("Major Garbage Collection");
static const ZStatPhaseGeneration ZPhaseGenerationYoung("Young Generation", ZGenerationId::young);
static const ZStatPhaseGeneration ZPhaseGenerationOld("Old Generation", ZGenerationId::old);

static const ZStatPhasePause      ZPhasePauseMarkStartYoung("Pause Mark Start (Young)");
static const ZStatPhasePause      ZPhasePauseMarkStartYoungAndOld("Pause Mark Start (Young + Old)");
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkYoung("Concurrent Mark (Young)");
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkContinueYoung("Concurrent Mark Continue (Young)");
static const ZStatPhasePause      ZPhasePauseMarkEndYoung("Pause Mark End (Young)");
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkFreeYoung("Concurrent Mark Free (Young)");
static const ZStatPhaseConcurrent ZPhaseConcurrentResetRelocationSetYoung("Concurrent Reset Relocation Set (Young)");
static const ZStatPhaseConcurrent ZPhaseConcurrentSelectRelocationSetYoung("Concurrent Select Relocation Set (Young)");
static const ZStatPhasePause      ZPhasePauseRelocateStartYoung("Pause Relocate Start (Young)");
static const ZStatPhaseConcurrent ZPhaseConcurrentRelocatedYoung("Concurrent Relocate (Young)");

static const ZStatPhaseConcurrent ZPhaseConcurrentMarkOld("Concurrent Mark (Old)");
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkContinueOld("Concurrent Mark Continue (Old)");
static const ZStatPhasePause      ZPhasePauseMarkEndOld("Pause Mark End (Old)");
static const ZStatPhaseConcurrent ZPhaseConcurrentMarkFreeOld("Concurrent Mark Free (Old)");
static const ZStatPhaseConcurrent ZPhaseConcurrentProcessNonStrongReferencesOld("Concurrent Process Non-Strong References (Old)");
static const ZStatPhaseConcurrent ZPhaseConcurrentResetRelocationSetOld("Concurrent Reset Relocation Set (Old)");
static const ZStatPhaseConcurrent ZPhaseConcurrentSelectRelocationSetOld("Concurrent Select Relocation Set (Old)");
static const ZStatPhasePause      ZPhasePauseRelocateStartOld("Pause Relocate Start (Old)");
static const ZStatPhaseConcurrent ZPhaseConcurrentRelocatedOld("Concurrent Relocate (Old)");
static const ZStatPhaseConcurrent ZPhaseConcurrentRootsRemapOld("Concurrent Roots Remap (Old)");

static const ZStatSampler         ZSamplerJavaThreads("System", "Java Threads", ZStatUnitThreads);

ZLock* ZDriverLock::_lock;

void ZDriverLock::initialize() {
  _lock = new ZLock();
}

void ZDriverLock::lock() {
  _lock->lock();
}

void ZDriverLock::unlock() {
  _lock->unlock();
}

class ZDriverLocker : public StackObj {
public:
  ZDriverLocker() {
    ZDriverLock::lock();
  }

  ~ZDriverLocker() {
    ZDriverLock::unlock();
  }
};

class ZDriverUnlocker : public StackObj {
public:
  ZDriverUnlocker() {
    ZDriverLock::unlock();
  }

  ~ZDriverUnlocker() {
    ZDriverLock::lock();
  }
};

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

class VM_ZMarkStartYoung : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZMarkStartYoung;
  }

  virtual bool block_jni_critical() const {
    return true;
  }

  virtual bool do_operation() {
    ZStatTimerYoung timer(ZPhasePauseMarkStartYoung);
    ZServiceabilityPauseTracer tracer(ZCollectorId::young);

    ZCollectedHeap::heap()->increment_total_collections(false /* full */);
    ZHeap::heap()->young_collector()->mark_start();
    return true;
  }
};

class VM_ZMarkStartYoungAndOld : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZMarkStartYoungAndOld;
  }

  virtual bool block_jni_critical() const {
    return true;
  }

  virtual bool do_operation() {
    ZStatTimerYoung timer(ZPhasePauseMarkStartYoungAndOld);
    ZServiceabilityPauseTracer tracer(ZCollectorId::young);

    ZCollectedHeap::heap()->increment_total_collections(true /* full */);
    ZHeap::heap()->young_collector()->mark_start();
    ZHeap::heap()->old_collector()->mark_start();
    return true;
  }
};

class VM_ZMarkEndYoung : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZMarkEndYoung;
  }

  virtual bool do_operation() {
    ZStatTimerYoung timer(ZPhasePauseMarkEndYoung);
    ZServiceabilityPauseTracer tracer(ZCollectorId::young);
    return ZHeap::heap()->young_collector()->mark_end();
  }
};

class VM_ZRelocateStartYoung : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZRelocateStartYoung;
  }

  virtual bool block_jni_critical() const {
    return true;
  }

  virtual bool do_operation() {
    ZStatTimerYoung timer(ZPhasePauseRelocateStartYoung);
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
  uint nworkers = ZConcOldGCThreads != 0 ? ZConcOldGCThreads : ConcGCThreads;
  if (UseDynamicNumberOfGCThreads) {
    return select_active_worker_threads_dynamic(request.cause(), nworkers);
  } else {
    return select_active_worker_threads_static(request.cause(), nworkers);
  }
}

// Macro to execute a termination check after a concurrent phase. Note
// that it's important that the abortion check comes after the call
// to the function f, since we can't abort between pause_relocate_start()
// and concurrent_relocate(). We need to let concurrent_relocate() call
// abort_page() on the remaining entries in the relocation set.
#define abortable(f)                  \
  do {                                \
    f;                                \
    if (ZAbort::should_abort()) {     \
      return;                         \
    }                                 \
  } while (false)

template <typename T>
static bool pause() {
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

static void pause_mark_start_young(bool initiate_old) {
  if (initiate_old) {
    pause<VM_ZMarkStartYoungAndOld>();
  } else {
    pause<VM_ZMarkStartYoung>();
  }
}

static void concurrent_mark_young() {
  ZStatTimerYoung timer(ZPhaseConcurrentMarkYoung);
  ZHeap::heap()->young_collector()->mark_roots();
  ZHeap::heap()->young_collector()->mark_follow();
}

static bool pause_mark_end_young() {
  return pause<VM_ZMarkEndYoung>();
}

static void concurrent_mark_continue_young() {
  ZStatTimerYoung timer(ZPhaseConcurrentMarkContinueYoung);
  ZHeap::heap()->young_collector()->mark_follow();
}

static void concurrent_mark_free_young() {
  ZStatTimerYoung timer(ZPhaseConcurrentMarkFreeYoung);
  ZHeap::heap()->young_collector()->mark_free();
}

static void concurrent_reset_relocation_set_young() {
  ZStatTimerYoung timer(ZPhaseConcurrentResetRelocationSetYoung);
  ZHeap::heap()->young_collector()->reset_relocation_set();
}

static void concurrent_select_relocation_set_young(bool promote_all) {
  ZStatTimerYoung timer(ZPhaseConcurrentSelectRelocationSetYoung);
  ZHeap::heap()->young_collector()->select_relocation_set(promote_all);
}

static void pause_relocate_start_young() {
  pause<VM_ZRelocateStartYoung>();
}

static void concurrent_relocate_young() {
  ZStatTimerYoung timer(ZPhaseConcurrentRelocatedYoung);
  ZHeap::heap()->young_collector()->relocate();
}

static void check_out_of_memory_young() {
  ZHeap::heap()->check_out_of_memory_young();
}

class ZDriverScopeYoung : public StackObj {
private:
  ZStatTimerYoung            _timer;
  ZServiceabilityCycleTracer _tracer;

public:
  ZDriverScopeYoung() :
      _timer(ZPhaseGenerationYoung),
      _tracer(ZCollectorId::young) {
    ZYoungCollector* const young_collector = ZHeap::heap()->young_collector();

    // Update statistics
    young_collector->set_at_generation_collection_start();
  }

  ~ZDriverScopeYoung() {
    ZYoungCollector* const young_collector = ZHeap::heap()->young_collector();

    // Update statistics
    young_collector->stat_cycle()->at_end(young_collector->active_workers());
  }
};

static void collect_young_inner(bool promote_all, bool initiate_old) {
  ZDriverScopeYoung scope;

  // Phase 1: Pause Mark Start
  pause_mark_start_young(initiate_old);

  // Phase 2: Concurrent Mark
  abortable(concurrent_mark_young());

  // Phase 3: Pause Mark End
  while (!pause_mark_end_young()) {
    // Phase 3.5: Concurrent Mark Continue
    abortable(concurrent_mark_continue_young());
  }

  // Phase 4: Concurrent Mark Free
  abortable(concurrent_mark_free_young());

  // Phase 5: Concurrent Reset Relocation Set
  abortable(concurrent_reset_relocation_set_young());

  // Phase 6: Concurrent Select Relocation Set
  abortable(concurrent_select_relocation_set_young(promote_all));

  // Phase 7: Pause Relocate Start
  pause_relocate_start_young();

  // Phase 8: Concurrent Relocate
  abortable(concurrent_relocate_young());
}

static void collect_young() {
  collect_young_inner(false /* promote_all */, false /* initiate_old */);
}

static void collect_young_promote_all() {
  collect_young_inner(true /* promote_all */, false /* initiate_old */);
}

static void collect_young_initiate_old() {
  collect_young_inner(false /* promote_all */, true /* initiate_old */);
}

ZDriverMinor::ZDriverMinor() :
    _port() {
  set_name("ZDriverMinor");
  create_and_start();
}

bool ZDriverMinor::is_busy() const {
  return _port.is_busy();
}

void ZDriverMinor::collect(const ZDriverRequest& request) {
  switch (request.cause()) {
  case GCCause::_wb_young_gc:
  case GCCause::_scavenge_alot:
  case GCCause::_z_minor_timer:
  case GCCause::_z_minor_allocation_rate:
  case GCCause::_z_minor_allocation_stall:
  case GCCause::_z_minor_high_usage:
    // Start asynchronous GC
    _port.send_async(request);
    break;

  default:
    // Other causes not supported
    fatal("Unsupported GC cause (%s)", GCCause::to_string(request.cause()));
    break;
  }
}

class ZDriverScopeMinor : public StackObj {
private:
  GCIdMark        _gc_id;
  GCCause::Cause  _gc_cause;
  GCCauseSetter   _gc_cause_setter;
  ZStatTimerMinor _timer;

public:
  ZDriverScopeMinor(const ZDriverRequest& request) :
      _gc_id(),
      _gc_cause(request.cause()),
      _gc_cause_setter(ZCollectedHeap::heap(), _gc_cause),
      _timer(ZPhaseCollectionMinor) {
    ZYoungCollector* const young_collector = ZHeap::heap()->young_collector();

    // Update statistics
    young_collector->set_at_collection_start();

    // Select number of young worker threads to use
    const uint young_nworkers = select_active_young_worker_threads(request);
    young_collector->set_active_workers(young_nworkers);
  }
};

void ZDriverMinor::gc(const ZDriverRequest& request) {
  ZDriverScopeMinor scope(request);
  abortable(collect_young());
}

void ZDriverMinor::check_out_of_memory() const {
  check_out_of_memory_young();
}

void ZDriverMinor::run_service() {
  // Main loop
  while (!ZAbort::should_abort()) {
    // Wait for GC request
    const ZDriverRequest request = _port.receive();
    if (request.cause() == GCCause::_no_gc) {
      continue;
    }

    ZDriverLocker locker;

    if (ZAbort::should_abort()) {
      return;
    }

    // Run GC
    gc(request);

    if (ZAbort::should_abort()) {
      return;
    }

    // Notify GC completed
    _port.ack();

    // Check out of memory
    check_out_of_memory();
  }
}

void ZDriverMinor::stop_service() {
  _port.send_async(GCCause::_no_gc);
}

class VM_ZMarkEndOld : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZMarkEndOld;
  }

  virtual bool do_operation() {
    ZStatTimerOld timer(ZPhasePauseMarkEndOld);
    ZServiceabilityPauseTracer tracer(ZCollectorId::old);
    return ZHeap::heap()->old_collector()->mark_end();
  }
};

class VM_ZRelocateStartOld : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZRelocateStartOld;
  }

  virtual bool block_jni_critical() const {
    return true;
  }

  virtual bool do_operation() {
    ZStatTimerOld timer(ZPhasePauseRelocateStartOld);
    ZServiceabilityPauseTracer tracer(ZCollectorId::old);
    ZHeap::heap()->old_collector()->relocate_start();
    return true;
  }
};

class VM_ZVerifyOld : public VM_Operation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZVerifyOld;
  }

  virtual bool skip_thread_oop_barriers() const {
    return true;
  }

  virtual void doit() {
    ZVerify::after_weak_processing();
  }
};

static void concurrent_mark_old() {
  ZStatTimerOld timer(ZPhaseConcurrentMarkOld);
  ZBreakpoint::at_after_marking_started();
  ZHeap::heap()->old_collector()->mark_roots();
  ZHeap::heap()->old_collector()->mark_follow();
  ZBreakpoint::at_before_marking_completed();
}

static bool pause_mark_end_old() {
  ZDriverLocker locker;
  return pause<VM_ZMarkEndOld>();
}

static void concurrent_mark_continue_old() {
  ZStatTimerOld timer(ZPhaseConcurrentMarkContinueOld);
  ZHeap::heap()->old_collector()->mark_follow();
}

static void concurrent_mark_free_old() {
  ZStatTimerOld timer(ZPhaseConcurrentMarkFreeOld);
  ZHeap::heap()->old_collector()->mark_free();
}

static void concurrent_process_non_strong_references_old() {
  ZStatTimerOld timer(ZPhaseConcurrentProcessNonStrongReferencesOld);
  ZBreakpoint::at_after_reference_processing_started();
  ZHeap::heap()->old_collector()->process_non_strong_references();
}

static void concurrent_reset_relocation_set_old() {
  ZStatTimerOld timer(ZPhaseConcurrentResetRelocationSetOld);
  ZHeap::heap()->old_collector()->reset_relocation_set();
}

static void pause_verify_old() {
  // Note that we block out concurrent young collections when performing the
  // verification. The verification checks that store good oops in the
  // old generation have a corresponding remembered set entry, or is in
  // a store barrier buffer (hence asynchronously creating such entries).
  // That lookup would otherwise race with installation of base pointers
  // into the store barrier buffer. We dodge that race by blocking out
  // young collections during this verification.
  if (VerifyBeforeGC || VerifyDuringGC || VerifyAfterGC) {
    // Full verification
    ZDriverLocker locker;
    VM_Verify op;
    VMThread::execute(&op);
  } else if (ZVerifyRoots || ZVerifyObjects) {
    // Limited verification
    ZDriverLocker locker;
    VM_ZVerifyOld op;
    VMThread::execute(&op);
  }
}

static void concurrent_select_relocation_set_old() {
  ZStatTimerOld timer(ZPhaseConcurrentSelectRelocationSetOld);
  ZHeap::heap()->old_collector()->select_relocation_set(false /* promote_all */);
}

static void pause_relocate_start_old() {
  pause<VM_ZRelocateStartOld>();
}

static void concurrent_relocate_old() {
  ZStatTimerOld timer(ZPhaseConcurrentRelocatedOld);
  ZHeap::heap()->old_collector()->relocate();
}

static void concurrent_roots_remap_old() {
  ZStatTimerOld timer(ZPhaseConcurrentRootsRemapOld);
  ZHeap::heap()->old_collector()->roots_remap();
}

static void check_out_of_memory_old() {
  ZHeap::heap()->check_out_of_memory_old();
}

static bool should_clear_soft_references(GCCause::Cause cause) {
  // Clear soft references if implied by the GC cause
  switch (cause) {
  case GCCause::_wb_full_gc:
  case GCCause::_metadata_GC_clear_soft_refs:
  case GCCause::_z_major_allocation_stall:
    return true;

  case GCCause::_wb_breakpoint:
  case GCCause::_dcmd_gc_run:
  case GCCause::_java_lang_system_gc:
  case GCCause::_full_gc_alot:
  case GCCause::_jvmti_force_gc:
  case GCCause::_z_major_timer:
  case GCCause::_z_major_warmup:
  case GCCause::_z_major_allocation_rate:
  case GCCause::_z_major_proactive:
  case GCCause::_metadata_GC_threshold:
    break;

  default:
    fatal("Unsupported GC cause (%s)", GCCause::to_string(cause));
  }

  // Clear soft references if threads are stalled waiting for an old collection
  if (ZHeap::heap()->is_alloc_stalling_for_old()) {
    return true;
  }

  // Don't clear
  return false;
}

static bool should_collect_young_before_old(GCCause::Cause cause) {
  // Collect young if implied by the GC cause
  switch (cause) {
  case GCCause::_wb_full_gc:
  case GCCause::_wb_breakpoint:
  case GCCause::_dcmd_gc_run:
  case GCCause::_java_lang_system_gc:
  case GCCause::_full_gc_alot:
  case GCCause::_jvmti_force_gc:
  case GCCause::_metadata_GC_clear_soft_refs:
  case GCCause::_z_major_allocation_stall:
    return true;

  case GCCause::_z_major_timer:
  case GCCause::_z_major_warmup:
  case GCCause::_z_major_allocation_rate:
  case GCCause::_z_major_proactive:
  case GCCause::_metadata_GC_threshold:
    break;

  default:
    fatal("Unsupported GC cause (%s)", GCCause::to_string(cause));
  }

  // Collect young if threads are stalled waiting for an old collection
  if (ZHeap::heap()->is_alloc_stalling_for_old()) {
    return true;
  }

  // Collect young if implied by configuration
  return ScavengeBeforeFullGC;
}

class ZDriverScopeOld : public StackObj {
private:
  ZStatTimerOld              _timer;
  ZServiceabilityCycleTracer _tracer;
  ZDriverUnlocker            _unlocker;

public:
  ZDriverScopeOld() :
      _timer(ZPhaseGenerationOld),
      _tracer(ZCollectorId::old),
      _unlocker() {
    ZOldCollector* const old_collector = ZHeap::heap()->old_collector();

    // Update statistics
    old_collector->set_at_generation_collection_start();
  }

  ~ZDriverScopeOld() {
    ZOldCollector* const old_collector = ZHeap::heap()->old_collector();

    // Update statistics
    old_collector->stat_cycle()->at_end(old_collector->active_workers());
  }
};

static void collect_old() {
  ZDriverScopeOld scope;

  // Phase 1: Concurrent Mark
  abortable(concurrent_mark_old());

  // Phase 2: Pause Mark End
  while (!pause_mark_end_old()) {
    // Phase 2.5: Concurrent Mark Continue
    abortable(concurrent_mark_continue_old());
  }

  // Phase 3: Concurrent Mark Free
  abortable(concurrent_mark_free_old());

  // Phase 4: Concurrent Process Non-Strong References
  abortable(concurrent_process_non_strong_references_old());

  // Phase 5: Concurrent Reset Relocation Set
  abortable(concurrent_reset_relocation_set_old());

  // Phase 6: Pause Verify
  pause_verify_old();

  // Phase 7: Concurrent Select Relocation Set
  abortable(concurrent_select_relocation_set_old());

  {
    ZDriverLocker locker;

    // Phase 8: Concurrent Roots Remap
    abortable(concurrent_roots_remap_old());

    // Phase 9: Pause Relocate Start
    pause_relocate_start_old();
  }

  // Phase 10: Concurrent Relocate
  abortable(concurrent_relocate_old());
}

ZDriverMajor::ZDriverMajor(ZDriverMinor* minor) :
    _port(),
    _minor(minor) {
  set_name("ZDriverMajor");
  create_and_start();
}

bool ZDriverMajor::is_busy() const {
  return _port.is_busy();
}

void ZDriverMajor::collect(const ZDriverRequest& request) {
  switch (request.cause()) {
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

class ZDriverScopeMajor : public StackObj {
private:
  GCIdMark        _gc_id;
  GCCause::Cause  _gc_cause;
  GCCauseSetter   _gc_cause_setter;
  ZStatTimerMajor _timer;

public:
  ZDriverScopeMajor(const ZDriverRequest& request) :
      _gc_id(),
      _gc_cause(request.cause()),
      _gc_cause_setter(ZCollectedHeap::heap(), _gc_cause),
      _timer(ZPhaseCollectionMajor) {
    ZYoungCollector* const young_collector = ZHeap::heap()->young_collector();
    ZOldCollector* const old_collector = ZHeap::heap()->old_collector();

    // Update statistics
    old_collector->set_at_collection_start();

    // Set up soft reference policy
    const bool clear = should_clear_soft_references(request.cause());
    old_collector->set_soft_reference_policy(clear);

    // Select number of young worker threads to use
    const uint young_nworkers = select_active_young_worker_threads(request);
    young_collector->set_active_workers(young_nworkers);

    // Select number of old worker threads to use
    const uint old_nworkers = select_active_old_worker_threads(request);
    old_collector->set_active_workers(old_nworkers);
  }

  ~ZDriverScopeMajor() {
    // Update data used by soft reference policy
    Universe::heap()->update_capacity_and_used_at_gc();

    // Signal that we have completed a visit to all live objects
    Universe::heap()->record_whole_heap_examined_timestamp();
  }
};

void ZDriverMajor::gc(const ZDriverRequest& request) {
  ZDriverScopeMajor scope(request);

  if (should_collect_young_before_old(request.cause())) {
    abortable(collect_young_promote_all());
  }

  abortable(collect_young_initiate_old());
  check_out_of_memory_young();
  abortable(collect_old());
}

void ZDriverMajor::check_out_of_memory() const {
  check_out_of_memory_old();
}

void ZDriverMajor::run_service() {
  // Main loop
  while (!ZAbort::should_abort()) {
    // Wait for GC request
    const ZDriverRequest request = _port.receive();
    if (request.cause() == GCCause::_no_gc) {
      continue;
    }

    ZDriverLocker locker;

    ZBreakpoint::at_before_gc();

    if (ZAbort::should_abort()) {
      return;
    }

    // Run GC
    gc(request);

    if (ZAbort::should_abort()) {
      return;
    }

    // Notify GC completed
    _port.ack();

    // Check out of memory
    check_out_of_memory();

    ZBreakpoint::at_after_gc();
  }
}

void ZDriverMajor::stop_service() {
  _port.send_async(GCCause::_no_gc);
}
