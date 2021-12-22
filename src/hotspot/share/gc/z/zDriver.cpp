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

static const ZStatPhaseCollection ZPhaseCollectionMinor("Minor Garbage Collection", true /* minor */);
static const ZStatPhaseCollection ZPhaseCollectionMajor("Major Garbage Collection", false /* minor */);

static const ZStatPhaseGeneration ZPhaseGenerationYoung[] {
  ZStatPhaseGeneration("Young Generation (Minor)", ZGenerationId::young),
  ZStatPhaseGeneration("Young Generation (Major Preclean)", ZGenerationId::young),
  ZStatPhaseGeneration("Young Generation (Major Roots)", ZGenerationId::young)
};

static const ZStatPhaseGeneration ZPhaseGenerationOld("Old Generation (Major)", ZGenerationId::old);

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
static const ZStatPhaseConcurrent ZPhaseConcurrentProcessNonStrongOld("Concurrent Process Non-Strong (Old)");
static const ZStatPhaseConcurrent ZPhaseConcurrentResetRelocationSetOld("Concurrent Reset Relocation Set (Old)");
static const ZStatPhaseConcurrent ZPhaseConcurrentSelectRelocationSetOld("Concurrent Select Relocation Set (Old)");
static const ZStatPhasePause      ZPhasePauseRelocateStartOld("Pause Relocate Start (Old)");
static const ZStatPhaseConcurrent ZPhaseConcurrentRelocatedOld("Concurrent Relocate (Old)");
static const ZStatPhaseConcurrent ZPhaseConcurrentRemapRootsOld("Concurrent Remap Roots (Old)");

static const ZStatSampler         ZSamplerJavaThreads("System", "Java Threads", ZStatUnitThreads);

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

class ZDriverLocker : public StackObj {
public:
  ZDriverLocker() {
    ZDriver::lock();
  }

  ~ZDriverLocker() {
    ZDriver::unlock();
  }
};

class ZDriverUnlocker : public StackObj {
public:
  ZDriverUnlocker() {
    ZDriver::unlock();
  }

  ~ZDriverUnlocker() {
    ZDriver::lock();
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

static ZCollectedHeap* collected_heap() {
  return ZCollectedHeap::heap();
}

static ZYoungCollector* young_collector() {
  return ZCollector::young();
}

static ZOldCollector* old_collector() {
  return ZCollector::old();
}

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
    ZServiceabilityPauseTracer tracer;

    collected_heap()->increment_total_collections(false /* full */);
    young_collector()->mark_start();
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
    ZServiceabilityPauseTracer tracer;

    collected_heap()->increment_total_collections(true /* full */);
    young_collector()->mark_start();
    old_collector()->mark_start();
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
    ZServiceabilityPauseTracer tracer;
    return young_collector()->mark_end();
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
    ZServiceabilityPauseTracer tracer;
    young_collector()->relocate_start();
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
      cause == GCCause::_z_allocation_stall) {
    // Boost
    const uint boosted_nworkers = MAX2(nworkers, ParallelGCThreads);
    return boosted_nworkers;
  }

  // Use requested number of worker threads
  return nworkers;
}

static uint select_active_young_worker_threads(const ZDriverRequest& request) {
  if (UseDynamicNumberOfGCThreads) {
    return select_active_worker_threads_dynamic(request.cause(), request.young_nworkers());
  } else {
    return select_active_worker_threads_static(request.cause(), request.young_nworkers());
  }
}

static uint select_active_old_worker_threads(const ZDriverRequest& request) {
  if (UseDynamicNumberOfGCThreads) {
    return select_active_worker_threads_dynamic(request.cause(), request.old_nworkers());
  } else {
    return select_active_worker_threads_static(request.cause(), request.old_nworkers());
  }
}

// Macro to execute a abortion check. Note that we can't abort between
// pause_relocate_start() and concurrent_relocate(). We need to let
// concurrent_relocate() call abort_page() on the remaining entries
// in the relocation set.
#define abortpoint()                  \
  do {                                \
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

static void pause_mark_start_young() {
  if (young_collector()->type() == ZYoungType::major_roots) {
    pause<VM_ZMarkStartYoungAndOld>();
  } else {
    pause<VM_ZMarkStartYoung>();
  }
}

static void concurrent_mark_young() {
  ZStatTimerYoung timer(ZPhaseConcurrentMarkYoung);
  young_collector()->mark_roots();
  young_collector()->mark_follow();
}

static bool pause_mark_end_young() {
  return pause<VM_ZMarkEndYoung>();
}

static void concurrent_mark_continue_young() {
  ZStatTimerYoung timer(ZPhaseConcurrentMarkContinueYoung);
  young_collector()->mark_follow();
}

static void concurrent_mark_free_young() {
  ZStatTimerYoung timer(ZPhaseConcurrentMarkFreeYoung);
  young_collector()->mark_free();
}

static void concurrent_reset_relocation_set_young() {
  ZStatTimerYoung timer(ZPhaseConcurrentResetRelocationSetYoung);
  young_collector()->reset_relocation_set();
}

static void concurrent_select_relocation_set_young() {
  ZStatTimerYoung timer(ZPhaseConcurrentSelectRelocationSetYoung);
  const bool promote_all = young_collector()->type() == ZYoungType::major_preclean;
  young_collector()->select_relocation_set(promote_all);
}

static void pause_relocate_start_young() {
  pause<VM_ZRelocateStartYoung>();
}

static void concurrent_relocate_young() {
  ZStatTimerYoung timer(ZPhaseConcurrentRelocatedYoung);
  young_collector()->relocate();
}

static void handle_alloc_stalling_for_young() {
  ZHeap::heap()->handle_alloc_stalling_for_young();
}

class ZDriverScopeYoung : public StackObj {
private:
  ZYoungTypeSetter _type_setter;
  ZStatTimer       _stat_timer;

public:
  ZDriverScopeYoung(ZYoungType type, ConcurrentGCTimer* gc_timer) :
      _type_setter(type),
      _stat_timer(ZPhaseGenerationYoung[(int)type], gc_timer) {
    // Update statistics and set the GC timer
    young_collector()->at_collection_start(gc_timer);
  }

  ~ZDriverScopeYoung() {
    // Update statistics and clear the GC timer
    young_collector()->at_collection_end();
  }
};

static void collect_young(ZYoungType type, ConcurrentGCTimer* timer) {
  ZDriverScopeYoung scope(type, timer);

  // Phase 1: Pause Mark Start
  pause_mark_start_young();

  // Phase 2: Concurrent Mark
  concurrent_mark_young();

  abortpoint();

  // Phase 3: Pause Mark End
  while (!pause_mark_end_young()) {
    // Phase 3.5: Concurrent Mark Continue
    concurrent_mark_continue_young();

    abortpoint();
  }

  // Phase 4: Concurrent Mark Free
  concurrent_mark_free_young();

  abortpoint();

  // Phase 5: Concurrent Reset Relocation Set
  concurrent_reset_relocation_set_young();

  abortpoint();

  // Phase 6: Concurrent Select Relocation Set
  concurrent_select_relocation_set_young();

  abortpoint();

  // Phase 7: Pause Relocate Start
  pause_relocate_start_young();

  // Phase 8: Concurrent Relocate
  concurrent_relocate_young();
}

ZDriverMinor::ZDriverMinor() :
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
}

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
  GCIdMark                   _gc_id;
  GCCause::Cause             _gc_cause;
  GCCauseSetter              _gc_cause_setter;
  ZStatTimer                 _stat_timer;
  ZServiceabilityCycleTracer _tracer;

public:
  ZDriverScopeMinor(const ZDriverRequest& request, ConcurrentGCTimer* gc_timer) :
      _gc_id(),
      _gc_cause(request.cause()),
      _gc_cause_setter(collected_heap(), _gc_cause),
      _stat_timer(ZPhaseCollectionMinor, gc_timer),
      _tracer(true /* minor */) {
    // Select number of young worker threads to use
    const uint young_nworkers = select_active_young_worker_threads(request);
    young_collector()->set_active_workers(young_nworkers);
  }
};

void ZDriverMinor::gc(const ZDriverRequest& request) {
  ZDriverScopeMinor scope(request, &_gc_timer);
  collect_young(ZYoungType::minor, &_gc_timer);
}

void ZDriverMinor::handle_alloc_stalls() const {
  handle_alloc_stalling_for_young();
}

void ZDriverMinor::run_service() {
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
  }
}

void ZDriverMinor::stop_service() {
  ZDriverRequest request(GCCause::_no_gc, 0, 0);
  _port.send_async(request);
}

class VM_ZMarkEndOld : public VM_ZOperation {
public:
  virtual VMOp_Type type() const {
    return VMOp_ZMarkEndOld;
  }

  virtual bool do_operation() {
    ZStatTimerOld timer(ZPhasePauseMarkEndOld);
    ZServiceabilityPauseTracer tracer;
    return old_collector()->mark_end();
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
    ZServiceabilityPauseTracer tracer;
    old_collector()->relocate_start();
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
  old_collector()->mark_roots();
  old_collector()->mark_follow();
  ZBreakpoint::at_before_marking_completed();
}

static bool pause_mark_end_old() {
  ZDriverLocker locker;
  return pause<VM_ZMarkEndOld>();
}

static void concurrent_mark_continue_old() {
  ZStatTimerOld timer(ZPhaseConcurrentMarkContinueOld);
  old_collector()->mark_follow();
}

static void concurrent_mark_free_old() {
  ZStatTimerOld timer(ZPhaseConcurrentMarkFreeOld);
  old_collector()->mark_free();
}

static void concurrent_process_non_strong_references_old() {
  ZStatTimerOld timer(ZPhaseConcurrentProcessNonStrongOld);
  ZBreakpoint::at_after_reference_processing_started();
  old_collector()->process_non_strong_references();
}

static void concurrent_reset_relocation_set_old() {
  ZStatTimerOld timer(ZPhaseConcurrentResetRelocationSetOld);
  old_collector()->reset_relocation_set();
}

static void pause_verify_old() {
  // Note that we block out concurrent young collections when performing the
  // verification. The verification checks that store good oops in the
  // old generation have a corresponding remembered set entry, or is in
  // a store barrier buffer (hence asynchronously creating such entries).
  // That lookup would otherwise race with installation of base pointers
  // into the store barrier buffer. We dodge that race by blocking out
  // young collections during this verification.
  if (ZVerifyRoots || ZVerifyObjects) {
    // Limited verification
    ZDriverLocker locker;
    VM_ZVerifyOld op;
    VMThread::execute(&op);
  }
}

static void concurrent_select_relocation_set_old() {
  ZStatTimerOld timer(ZPhaseConcurrentSelectRelocationSetOld);
  old_collector()->select_relocation_set(false /* promote_all */);
}

static void pause_relocate_start_old() {
  pause<VM_ZRelocateStartOld>();
}

static void concurrent_relocate_old() {
  ZStatTimerOld timer(ZPhaseConcurrentRelocatedOld);
  old_collector()->relocate();
}

static void concurrent_remap_roots_old() {
  ZStatTimerOld timer(ZPhaseConcurrentRemapRootsOld);
  old_collector()->remap_roots();
}

static void handle_alloc_stalling_for_old() {
  ZHeap::heap()->handle_alloc_stalling_for_old();
}

static bool should_clear_soft_references(GCCause::Cause cause) {
  // Clear soft references if implied by the GC cause
  switch (cause) {
  case GCCause::_wb_full_gc:
  case GCCause::_metadata_GC_clear_soft_refs:
  case GCCause::_z_allocation_stall:
    return true;

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
    break;

  default:
    fatal("Unsupported GC cause (%s)", GCCause::to_string(cause));
    break;
  }

  // Preclean young if threads are stalled waiting for an old collection
  if (ZHeap::heap()->is_alloc_stalling_for_old()) {
    return true;
  }

  // Preclean young if implied by configuration
  return ScavengeBeforeFullGC;
}

class ZDriverScopeOld : public StackObj {
private:
  ZStatTimer      _stat_timer;
  ZDriverUnlocker _unlocker;

public:
  ZDriverScopeOld(ConcurrentGCTimer* gc_timer) :
      _stat_timer(ZPhaseGenerationOld, gc_timer),
      _unlocker() {
    // Update statistics and set the GC timer
    old_collector()->at_collection_start(gc_timer);
  }

  ~ZDriverScopeOld() {
    // Update statistics and clear the GC timer
    old_collector()->at_collection_end();
  }
};

static void collect_old(ConcurrentGCTimer* timer) {
  ZDriverScopeOld scope(timer);

  // Phase 1: Concurrent Mark
  concurrent_mark_old();

  abortpoint();

  // Phase 2: Pause Mark End
  while (!pause_mark_end_old()) {
    // Phase 2.5: Concurrent Mark Continue
    concurrent_mark_continue_old();

    abortpoint();
  }

  // Phase 3: Concurrent Mark Free
  concurrent_mark_free_old();

  abortpoint();

  // Phase 4: Concurrent Process Non-Strong References
  concurrent_process_non_strong_references_old();

  abortpoint();

  // Phase 5: Concurrent Reset Relocation Set
  concurrent_reset_relocation_set_old();

  abortpoint();

  // Phase 6: Pause Verify
  pause_verify_old();

  // Phase 7: Concurrent Select Relocation Set
  concurrent_select_relocation_set_old();

  abortpoint();

  {
    ZDriverLocker locker;

    // Phase 8: Concurrent Remap Roots
    concurrent_remap_roots_old();

    abortpoint();

    // Phase 9: Pause Relocate Start
    pause_relocate_start_old();
  }

  // Phase 10: Concurrent Relocate
  concurrent_relocate_old();
}

ZDriverMajor::ZDriverMajor() :
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
  case GCCause::_wb_full_gc:
  case GCCause::_dcmd_gc_run:
  case GCCause::_java_lang_system_gc:
  case GCCause::_full_gc_alot:
  case GCCause::_jvmti_force_gc:
  case GCCause::_metadata_GC_clear_soft_refs:
    // Start synchronous GC
    _port.send_sync(request);
    break;

  case GCCause::_z_timer:
  case GCCause::_z_warmup:
  case GCCause::_z_allocation_rate:
  case GCCause::_z_allocation_stall:
  case GCCause::_z_proactive:
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
  GCIdMark                   _gc_id;
  GCCause::Cause             _gc_cause;
  GCCauseSetter              _gc_cause_setter;
  ZStatTimer                 _stat_timer;
  ZServiceabilityCycleTracer _tracer;

public:
  ZDriverScopeMajor(const ZDriverRequest& request, ConcurrentGCTimer* gc_timer) :
      _gc_id(),
      _gc_cause(request.cause()),
      _gc_cause_setter(collected_heap(), _gc_cause),
      _stat_timer(ZPhaseCollectionMajor, gc_timer),
      _tracer(false /* minor */) {
    // Set up soft reference policy
    const bool clear = should_clear_soft_references(request.cause());
    old_collector()->set_soft_reference_policy(clear);

    // Select number of young worker threads to use
    const uint young_nworkers = select_active_young_worker_threads(request);
    young_collector()->set_active_workers(young_nworkers);

    // Select number of old worker threads to use
    const uint old_nworkers = select_active_old_worker_threads(request);
    old_collector()->set_active_workers(old_nworkers);
  }

  ~ZDriverScopeMajor() {
    // Update data used by soft reference policy
    collected_heap()->update_capacity_and_used_at_gc();

    // Signal that we have completed a visit to all live objects
    collected_heap()->record_whole_heap_examined_timestamp();
  }
};

void ZDriverMajor::gc(const ZDriverRequest& request) {
  ZDriverScopeMajor scope(request, &_gc_timer);

  if (should_preclean_young(request.cause())) {
    // Collect young generation and promote everything to old generation
    collect_young(ZYoungType::major_preclean, &_gc_timer);
  }

  abortpoint();

  // Collect young generation and gather roots pointing into old generation
  collect_young(ZYoungType::major_roots, &_gc_timer);

  abortpoint();

  // Handle allocations waiting for a young collection
  handle_alloc_stalling_for_young();

  abortpoint();

  // Collect old generation
  collect_old(&_gc_timer);
}

void ZDriverMajor::handle_alloc_stalls() const {
  handle_alloc_stalling_for_old();
}

void ZDriverMajor::run_service() {
  // Main loop
  for (;;) {
    // Wait for GC request
    const ZDriverRequest request = _port.receive();

    ZDriverLocker locker;

    ZBreakpoint::at_before_gc();

    abortpoint();

    // Run GC
    gc(request);

    abortpoint();

    // Notify GC completed
    _port.ack();

    // Handle allocation stalls
    handle_alloc_stalls();

    ZBreakpoint::at_after_gc();
  }
}

void ZDriverMajor::stop_service() {
  ZDriverRequest request(GCCause::_no_gc, 0, 0);
  _port.send_async(request);
}
