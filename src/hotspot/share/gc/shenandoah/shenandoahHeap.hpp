/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2013, 2021, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHHEAP_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHHEAP_HPP

#include "gc/shared/ageTable.hpp"
#include "gc/shared/markBitMap.hpp"
#include "gc/shared/softRefPolicy.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shenandoah/shenandoahAgeCensus.hpp"
#include "gc/shenandoah/heuristics/shenandoahSpaceInfo.hpp"
#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahLock.hpp"
#include "gc/shenandoah/shenandoahEvacOOMHandler.hpp"
#include "gc/shenandoah/shenandoahEvacTracker.hpp"
#include "gc/shenandoah/shenandoahGenerationType.hpp"
#include "gc/shenandoah/shenandoahMmuTracker.hpp"
#include "gc/shenandoah/shenandoahPadding.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"
#include "gc/shenandoah/shenandoahUnload.hpp"
#include "memory/metaspace.hpp"
#include "services/memoryManager.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/stack.hpp"

class ConcurrentGCTimer;
class ObjectIterateScanRootClosure;
class PLAB;
class ShenandoahCollectorPolicy;
class ShenandoahControlThread;
class ShenandoahRegulatorThread;
class ShenandoahGCSession;
class ShenandoahGCStateResetter;
class ShenandoahGeneration;
class ShenandoahYoungGeneration;
class ShenandoahOldGeneration;
class ShenandoahHeuristics;
class ShenandoahOldHeuristics;
class ShenandoahYoungHeuristics;
class ShenandoahMarkingContext;
class ShenandoahPhaseTimings;
class ShenandoahHeap;
class ShenandoahHeapRegion;
class ShenandoahHeapRegionClosure;
class ShenandoahCollectionSet;
class ShenandoahFreeSet;
class ShenandoahConcurrentMark;
class ShenandoahFullGC;
class ShenandoahMonitoringSupport;
class ShenandoahMode;
class ShenandoahPacer;
class ShenandoahReferenceProcessor;
class ShenandoahVerifier;
class ShenandoahWorkerThreads;
class VMStructs;

// Used for buffering per-region liveness data.
// Needed since ShenandoahHeapRegion uses atomics to update liveness.
// The ShenandoahHeap array has max-workers elements, each of which is an array of
// uint16_t * max_regions. The choice of uint16_t is not accidental:
// there is a tradeoff between static/dynamic footprint that translates
// into cache pressure (which is already high during marking), and
// too many atomic updates. uint32_t is too large, uint8_t is too small.
typedef uint16_t ShenandoahLiveData;
#define SHENANDOAH_LIVEDATA_MAX ((ShenandoahLiveData)-1)

class ShenandoahRegionIterator : public StackObj {
private:
  ShenandoahHeap* _heap;

  shenandoah_padding(0);
  volatile size_t _index;
  shenandoah_padding(1);

  // No implicit copying: iterators should be passed by reference to capture the state
  NONCOPYABLE(ShenandoahRegionIterator);

public:
  ShenandoahRegionIterator();
  ShenandoahRegionIterator(ShenandoahHeap* heap);

  // Reset iterator to default state
  void reset();

  // Returns next region, or null if there are no more regions.
  // This is multi-thread-safe.
  inline ShenandoahHeapRegion* next();

  // This is *not* MT safe. However, in the absence of multithreaded access, it
  // can be used to determine if there is more work to do.
  bool has_next() const;
};

class ShenandoahHeapRegionClosure : public StackObj {
public:
  virtual void heap_region_do(ShenandoahHeapRegion* r) = 0;
  virtual bool is_thread_safe() { return false; }
};

template<ShenandoahGenerationType GENERATION>
class ShenandoahGenerationRegionClosure : public ShenandoahHeapRegionClosure {
 public:
  explicit ShenandoahGenerationRegionClosure(ShenandoahHeapRegionClosure* cl) : _cl(cl) {}
  void heap_region_do(ShenandoahHeapRegion* r);
  virtual bool is_thread_safe() { return _cl->is_thread_safe(); }
 private:
  ShenandoahHeapRegionClosure* _cl;
};

typedef ShenandoahLock    ShenandoahHeapLock;
typedef ShenandoahLocker  ShenandoahHeapLocker;
typedef Stack<oop, mtGC>  ShenandoahScanObjectStack;

// Shenandoah GC is low-pause concurrent GC that uses Brooks forwarding pointers
// to encode forwarding data. See BrooksPointer for details on forwarding data encoding.
// See ShenandoahControlThread for GC cycle structure.
//
class ShenandoahHeap : public CollectedHeap {
  friend class ShenandoahAsserts;
  friend class VMStructs;
  friend class ShenandoahGCSession;
  friend class ShenandoahGCStateResetter;
  friend class ShenandoahParallelObjectIterator;
  friend class ShenandoahSafepoint;
  // Supported GC
  friend class ShenandoahConcurrentGC;
  friend class ShenandoahOldGC;
  friend class ShenandoahDegenGC;
  friend class ShenandoahFullGC;
  friend class ShenandoahUnload;

// ---------- Locks that guard important data structures in Heap
//
private:
  ShenandoahHeapLock _lock;
  ShenandoahGeneration* _gc_generation;

  // true iff we are concurrently coalescing and filling old-gen HeapRegions
  bool _prepare_for_old_mark;

public:
  ShenandoahHeapLock* lock() {
    return &_lock;
  }

  ShenandoahGeneration* active_generation() const {
    // last or latest generation might be a better name here.
    return _gc_generation;
  }

  void set_gc_generation(ShenandoahGeneration* generation) {
    _gc_generation = generation;
  }

  ShenandoahHeuristics* heuristics();
  ShenandoahOldHeuristics* old_heuristics();
  ShenandoahYoungHeuristics* young_heuristics();

  bool doing_mixed_evacuations();
  bool is_old_bitmap_stable() const;
  bool is_gc_generation_young() const;

// ---------- Initialization, termination, identification, printing routines
//
public:
  static ShenandoahHeap* heap();

  const char* name()          const override { return "Shenandoah"; }
  ShenandoahHeap::Name kind() const override { return CollectedHeap::Shenandoah; }

  ShenandoahHeap(ShenandoahCollectorPolicy* policy);
  jint initialize() override;
  void post_initialize() override;
  void initialize_heuristics_generations();
  virtual void print_init_logger() const;
  void initialize_serviceability() override;

  void print_on(outputStream* st)              const override;
  void print_extended_on(outputStream *st)     const override;
  void print_tracing_info()                    const override;
  void print_heap_regions_on(outputStream* st) const;

  void stop() override;

  void prepare_for_verify() override;
  void verify(VerifyOption vo) override;

  bool verify_generation_usage(bool verify_old, size_t old_regions, size_t old_bytes, size_t old_waste,
                               bool verify_young, size_t young_regions, size_t young_bytes, size_t young_waste);

// WhiteBox testing support.
  bool supports_concurrent_gc_breakpoints() const override {
    return true;
  }

// ---------- Heap counters and metrics
//
private:
  size_t _initial_size;
  size_t _minimum_size;
  size_t _promotion_potential;
  size_t _pad_for_promote_in_place;    // bytes of filler
  size_t _promotable_humongous_regions;
  size_t _regular_regions_promoted_in_place;

  volatile size_t _soft_max_size;
  shenandoah_padding(0);
  volatile size_t _committed;
  shenandoah_padding(1);

  void increase_used(const ShenandoahAllocRequest& req);

public:
  void increase_used(ShenandoahGeneration* generation, size_t bytes);
  void decrease_used(ShenandoahGeneration* generation, size_t bytes);
  void increase_humongous_waste(ShenandoahGeneration* generation, size_t bytes);
  void decrease_humongous_waste(ShenandoahGeneration* generation, size_t bytes);

  void increase_committed(size_t bytes);
  void decrease_committed(size_t bytes);

  void reset_bytes_allocated_since_gc_start();

  size_t min_capacity()      const;
  size_t max_capacity()      const override;
  size_t soft_max_capacity() const;
  size_t initial_capacity()  const;
  size_t capacity()          const override;
  size_t used()              const override;
  size_t committed()         const;

  void set_soft_max_capacity(size_t v);

// ---------- Workers handling
//
private:
  uint _max_workers;
  ShenandoahWorkerThreads* _workers;
  ShenandoahWorkerThreads* _safepoint_workers;

public:
  uint max_workers();
  void assert_gc_workers(uint nworker) NOT_DEBUG_RETURN;

  WorkerThreads* workers() const;
  WorkerThreads* safepoint_workers() override;

  void gc_threads_do(ThreadClosure* tcl) const override;

// ---------- Heap regions handling machinery
//
private:
  MemRegion _heap_region;
  bool      _heap_region_special;
  size_t    _num_regions;
  ShenandoahHeapRegion** _regions;
  uint8_t* _affiliations;       // Holds array of enum ShenandoahAffiliation, including FREE status in non-generational mode
  ShenandoahRegionIterator _update_refs_iterator;

public:

  inline HeapWord* base() const { return _heap_region.start(); }

  inline size_t num_regions() const { return _num_regions; }
  inline bool is_heap_region_special() { return _heap_region_special; }

  inline ShenandoahHeapRegion* heap_region_containing(const void* addr) const;
  inline size_t heap_region_index_containing(const void* addr) const;

  inline ShenandoahHeapRegion* get_region(size_t region_idx) const;

  void heap_region_iterate(ShenandoahHeapRegionClosure* blk) const;
  void parallel_heap_region_iterate(ShenandoahHeapRegionClosure* blk) const;

  inline ShenandoahMmuTracker* mmu_tracker() { return &_mmu_tracker; };

// ---------- GC state machinery
//
// GC state describes the important parts of collector state, that may be
// used to make barrier selection decisions in the native and generated code.
// Multiple bits can be set at once.
//
// Important invariant: when GC state is zero, the heap is stable, and no barriers
// are required.
//
public:
  enum GCStateBitPos {
    // Heap has forwarded objects: needs LRB barriers.
    HAS_FORWARDED_BITPOS   = 0,

    // Heap is under marking: needs SATB barriers.
    // For generational mode, it means either young or old marking, or both.
    MARKING_BITPOS    = 1,

    // Heap is under evacuation: needs LRB barriers. (Set together with HAS_FORWARDED)
    EVACUATION_BITPOS = 2,

    // Heap is under updating: needs no additional barriers.
    UPDATEREFS_BITPOS = 3,

    // Heap is under weak-reference/roots processing: needs weak-LRB barriers.
    WEAK_ROOTS_BITPOS  = 4,

    // Young regions are under marking, need SATB barriers.
    YOUNG_MARKING_BITPOS = 5,

    // Old regions are under marking, need SATB barriers.
    OLD_MARKING_BITPOS = 6
  };

  enum GCState {
    STABLE        = 0,
    HAS_FORWARDED = 1 << HAS_FORWARDED_BITPOS,
    MARKING       = 1 << MARKING_BITPOS,
    EVACUATION    = 1 << EVACUATION_BITPOS,
    UPDATEREFS    = 1 << UPDATEREFS_BITPOS,
    WEAK_ROOTS    = 1 << WEAK_ROOTS_BITPOS,
    YOUNG_MARKING = 1 << YOUNG_MARKING_BITPOS,
    OLD_MARKING   = 1 << OLD_MARKING_BITPOS
  };

private:
  ShenandoahSharedBitmap _gc_state;
  ShenandoahSharedFlag   _degenerated_gc_in_progress;
  ShenandoahSharedFlag   _full_gc_in_progress;
  ShenandoahSharedFlag   _full_gc_move_in_progress;
  ShenandoahSharedFlag   _progress_last_gc;
  ShenandoahSharedFlag   _concurrent_strong_root_in_progress;

  // TODO: Revisit the following comment.  It may not accurately represent the true behavior when evacuations fail due to
  // difficulty finding memory to hold evacuated objects.
  //
  // Note that the typical total expenditure on evacuation is less than the associated evacuation reserve because we generally
  // reserve ShenandoahEvacWaste (> 1.0) times the anticipated evacuation need.  In the case that there is an excessive amount
  // of waste, it may be that one thread fails to grab a new GCLAB, this does not necessarily doom the associated evacuation
  // effort.  If this happens, the requesting thread blocks until some other thread manages to evacuate the offending object.
  // Only after "all" threads fail to evacuate an object do we consider the evacuation effort to have failed.

  size_t _promoted_reserve;            // Bytes reserved within old-gen to hold the results of promotion
  volatile size_t _promoted_expended;  // Bytes of old-gen memory expended on promotions

  size_t _old_evac_reserve;            // Bytes reserved within old-gen to hold evacuated objects from old-gen collection set
  size_t _young_evac_reserve;          // Bytes reserved within young-gen to hold evacuated objects from young-gen collection set

  bool _upgraded_to_full;

  ShenandoahAgeCensus* _age_census;    // Age census used for adapting tenuring threshold in generational mode

  // At the end of final mark, but before we begin evacuating, heuristics calculate how much memory is required to
  // hold the results of evacuating to young-gen and to old-gen.  These quantitites, stored in _promoted_reserve,
  // _old_evac_reserve, and _young_evac_reserve, are consulted prior to rebuilding the free set (ShenandoahFreeSet)
  // in preparation for evacuation.  When the free set is rebuilt, we make sure to reserve sufficient memory in the
  // collector and old_collector sets to hold if _has_evacuation_reserve_quantities is true.  The other time we
  // rebuild the freeset is at the end of GC, as we prepare to idle GC until the next trigger.  In this case,
  // _has_evacuation_reserve_quantities is false because we don't yet know how much memory will need to be evacuated
  // in the next GC cycle.  When _has_evacuation_reserve_quantities is false, the free set rebuild operation reserves
  // for the collector and old_collector sets based on alternative mechanisms, such as ShenandoahEvacReserve,
  // ShenandoahOldEvacReserve, and ShenandoahOldCompactionReserve.  In a future planned enhancement, the reserve
  // for old_collector set when not _has_evacuation_reserve_quantities is based in part on anticipated promotion as
  // determined by analysis of live data found during the previous GC pass which is one less than the current tenure age.
  bool _has_evacuation_reserve_quantities;

  void set_gc_state_all_threads(char state);
  void set_gc_state_mask(uint mask, bool value);

public:
  char gc_state() const;
  static address gc_state_addr();

  void set_evacuation_reserve_quantities(bool is_valid);
  void set_concurrent_young_mark_in_progress(bool in_progress);
  void set_concurrent_old_mark_in_progress(bool in_progress);
  void set_evacuation_in_progress(bool in_progress);
  void set_update_refs_in_progress(bool in_progress);
  void set_degenerated_gc_in_progress(bool in_progress);
  void set_full_gc_in_progress(bool in_progress);
  void set_full_gc_move_in_progress(bool in_progress);
  void set_has_forwarded_objects(bool cond);
  void set_concurrent_strong_root_in_progress(bool cond);
  void set_concurrent_weak_root_in_progress(bool cond);
  void set_prepare_for_old_mark_in_progress(bool cond);
  void set_aging_cycle(bool cond);

  inline bool is_stable() const;
  inline bool is_idle() const;
  inline bool has_evacuation_reserve_quantities() const;
  inline bool is_concurrent_mark_in_progress() const;
  inline bool is_concurrent_young_mark_in_progress() const;
  inline bool is_concurrent_old_mark_in_progress() const;
  inline bool is_update_refs_in_progress() const;
  inline bool is_evacuation_in_progress() const;
  inline bool is_degenerated_gc_in_progress() const;
  inline bool is_full_gc_in_progress() const;
  inline bool is_full_gc_move_in_progress() const;
  inline bool has_forwarded_objects() const;
  inline bool is_gc_in_progress_mask(uint mask) const;
  inline bool is_stw_gc_in_progress() const;
  inline bool is_concurrent_strong_root_in_progress() const;
  inline bool is_concurrent_weak_root_in_progress() const;
  inline bool is_prepare_for_old_mark_in_progress() const;
  inline bool is_aging_cycle() const;
  inline bool upgraded_to_full() { return _upgraded_to_full; }
  inline void start_conc_gc() { _upgraded_to_full = false; }
  inline void record_upgrade_to_full() { _upgraded_to_full = true; }

  inline void clear_promotion_potential() { _promotion_potential = 0; };
  inline void set_promotion_potential(size_t val) { _promotion_potential = val; };
  inline size_t get_promotion_potential() { return _promotion_potential; };

  inline void set_pad_for_promote_in_place(size_t pad) { _pad_for_promote_in_place = pad; }
  inline size_t get_pad_for_promote_in_place() { return _pad_for_promote_in_place; }

  inline void reserve_promotable_humongous_regions(size_t region_count) { _promotable_humongous_regions = region_count; }
  inline void reserve_promotable_regular_regions(size_t region_count) { _regular_regions_promoted_in_place = region_count; }

  inline size_t get_promotable_humongous_regions() { return _promotable_humongous_regions; }
  inline size_t get_regular_regions_promoted_in_place() { return _regular_regions_promoted_in_place; }

  // Returns previous value
  inline size_t set_promoted_reserve(size_t new_val);
  inline size_t get_promoted_reserve() const;
  inline void augment_promo_reserve(size_t increment);

  inline void reset_promoted_expended();
  inline size_t expend_promoted(size_t increment);
  inline size_t unexpend_promoted(size_t decrement);
  inline size_t get_promoted_expended();

  // Returns previous value
  inline size_t set_old_evac_reserve(size_t new_val);
  inline size_t get_old_evac_reserve() const;
  inline void augment_old_evac_reserve(size_t increment);

  // Returns previous value
  inline size_t set_young_evac_reserve(size_t new_val);
  inline size_t get_young_evac_reserve() const;

  // Return the age census object for young gen (in generational mode)
  inline ShenandoahAgeCensus* age_census() const;

private:
  void manage_satb_barrier(bool active);

  enum CancelState {
    // Normal state. GC has not been cancelled and is open for cancellation.
    // Worker threads can suspend for safepoint.
    CANCELLABLE,

    // GC has been cancelled. Worker threads can not suspend for
    // safepoint but must finish their work as soon as possible.
    CANCELLED
  };

  double _cancel_requested_time;
  ShenandoahSharedEnumFlag<CancelState> _cancelled_gc;

  // Returns true if cancel request was successfully communicated.
  // Returns false if some other thread already communicated cancel
  // request.  A true return value does not mean GC has been
  // cancelled, only that the process of cancelling GC has begun.
  bool try_cancel_gc();

public:
  inline bool cancelled_gc() const;
  inline bool check_cancelled_gc_and_yield(bool sts_active = true);

  inline void clear_cancelled_gc(bool clear_oom_handler = true);

  void cancel_concurrent_mark();
  void cancel_gc(GCCause::Cause cause);

public:
  // Elastic heap support
  void entry_uncommit(double shrink_before, size_t shrink_until);
  void op_uncommit(double shrink_before, size_t shrink_until);

private:
  // GC support
  // Evacuation
  void evacuate_collection_set(bool concurrent);
  // Concurrent root processing
  void prepare_concurrent_roots();
  void finish_concurrent_roots();
  // Concurrent class unloading support
  void do_class_unloading();
  // Reference updating
  void prepare_update_heap_references(bool concurrent);
  void update_heap_references(bool concurrent);
  // Final update region states
  void update_heap_region_states(bool concurrent);

  void rendezvous_threads();
  void recycle_trash();
public:
  void rebuild_free_set(bool concurrent);
  void notify_gc_progress()    { _progress_last_gc.set();   }
  void notify_gc_no_progress() { _progress_last_gc.unset(); }

//
// Mark support
private:
  ShenandoahYoungGeneration* _young_generation;
  ShenandoahGeneration*      _global_generation;
  ShenandoahOldGeneration*   _old_generation;

  ShenandoahControlThread*   _control_thread;
  ShenandoahRegulatorThread* _regulator_thread;
  ShenandoahCollectorPolicy* _shenandoah_policy;
  ShenandoahMode*            _gc_mode;
  ShenandoahFreeSet*         _free_set;
  ShenandoahPacer*           _pacer;
  ShenandoahVerifier*        _verifier;

  ShenandoahPhaseTimings*       _phase_timings;
  ShenandoahEvacuationTracker*  _evac_tracker;
  ShenandoahMmuTracker          _mmu_tracker;
  ShenandoahGenerationSizer     _generation_sizer;

  ShenandoahRegulatorThread* regulator_thread()        { return _regulator_thread;  }

public:
  ShenandoahControlThread*   control_thread()          { return _control_thread;    }
  ShenandoahYoungGeneration* young_generation()  const { return _young_generation;  }
  ShenandoahGeneration*      global_generation() const { return _global_generation; }
  ShenandoahOldGeneration*   old_generation()    const { return _old_generation;    }
  ShenandoahGeneration*      generation_for(ShenandoahAffiliation affiliation) const;
  const ShenandoahGenerationSizer* generation_sizer()  const { return &_generation_sizer;  }

  size_t max_size_for(ShenandoahGeneration* generation) const;
  size_t min_size_for(ShenandoahGeneration* generation) const;

  ShenandoahCollectorPolicy* shenandoah_policy() const { return _shenandoah_policy; }
  ShenandoahMode*            mode()              const { return _gc_mode;           }
  ShenandoahFreeSet*         free_set()          const { return _free_set;          }
  ShenandoahPacer*           pacer()             const { return _pacer;             }

  ShenandoahPhaseTimings*      phase_timings()   const { return _phase_timings;     }
  ShenandoahEvacuationTracker* evac_tracker()    const { return  _evac_tracker;     }

  void on_cycle_start(GCCause::Cause cause, ShenandoahGeneration* generation);
  void on_cycle_end(ShenandoahGeneration* generation);

  ShenandoahVerifier*        verifier();

// ---------- VM subsystem bindings
//
private:
  ShenandoahMonitoringSupport* _monitoring_support;
  MemoryPool*                  _memory_pool;
  MemoryPool*                  _young_gen_memory_pool;
  MemoryPool*                  _old_gen_memory_pool;

  GCMemoryManager              _stw_memory_manager;
  GCMemoryManager              _cycle_memory_manager;
  ConcurrentGCTimer*           _gc_timer;
  SoftRefPolicy                _soft_ref_policy;

  // For exporting to SA
  int                          _log_min_obj_alignment_in_bytes;
public:
  ShenandoahMonitoringSupport* monitoring_support() const    { return _monitoring_support;    }
  GCMemoryManager* cycle_memory_manager()                    { return &_cycle_memory_manager; }
  GCMemoryManager* stw_memory_manager()                      { return &_stw_memory_manager;   }
  SoftRefPolicy* soft_ref_policy()                  override { return &_soft_ref_policy;      }

  GrowableArray<GCMemoryManager*> memory_managers() override;
  GrowableArray<MemoryPool*> memory_pools() override;
  MemoryUsage memory_usage() override;
  GCTracer* tracer();
  ConcurrentGCTimer* gc_timer() const;

// ---------- Class Unloading
//
private:
  ShenandoahSharedFlag  _is_aging_cycle;
  ShenandoahSharedFlag _unload_classes;
  ShenandoahUnload     _unloader;

public:
  void set_unload_classes(bool uc);
  bool unload_classes() const;

  // Perform STW class unloading and weak root cleaning
  void parallel_cleaning(bool full_gc);

private:
  void stw_unload_classes(bool full_gc);
  void stw_process_weak_roots(bool full_gc);
  void stw_weak_refs(bool full_gc);

  inline void assert_lock_for_affiliation(ShenandoahAffiliation orig_affiliation,
                                          ShenandoahAffiliation new_affiliation);

  // Heap iteration support
  void scan_roots_for_iteration(ShenandoahScanObjectStack* oop_stack, ObjectIterateScanRootClosure* oops);
  bool prepare_aux_bitmap_for_iteration();
  void reclaim_aux_bitmap_for_iteration();

// ---------- Generic interface hooks
// Minor things that super-interface expects us to implement to play nice with
// the rest of runtime. Some of the things here are not required to be implemented,
// and can be stubbed out.
//
public:
  bool is_maximal_no_gc() const override shenandoah_not_implemented_return(false);

  inline bool is_in(const void* p) const override;

  inline bool is_in_active_generation(oop obj) const;
  inline bool is_in_young(const void* p) const;
  inline bool is_in_old(const void* p) const;
  inline bool is_old(oop pobj) const;

  inline ShenandoahAffiliation region_affiliation(const ShenandoahHeapRegion* r);
  inline void set_affiliation(ShenandoahHeapRegion* r, ShenandoahAffiliation new_affiliation);

  inline ShenandoahAffiliation region_affiliation(size_t index);

  bool requires_barriers(stackChunkOop obj) const override;

  MemRegion reserved_region() const { return _reserved; }
  bool is_in_reserved(const void* addr) const { return _reserved.contains(addr); }

  void collect(GCCause::Cause cause) override;
  void do_full_collection(bool clear_all_soft_refs) override;

  // Used for parsing heap during error printing
  HeapWord* block_start(const void* addr) const;
  bool block_is_obj(const HeapWord* addr) const;
  bool print_location(outputStream* st, void* addr) const override;

  // Used for native heap walkers: heap dumpers, mostly
  void object_iterate(ObjectClosure* cl) override;
  // Parallel heap iteration support
  ParallelObjectIteratorImpl* parallel_object_iterator(uint workers) override;

  // Keep alive an object that was loaded with AS_NO_KEEPALIVE.
  void keep_alive(oop obj) override;

// ---------- Safepoint interface hooks
//
public:
  void safepoint_synchronize_begin() override;
  void safepoint_synchronize_end() override;

// ---------- Code roots handling hooks
//
public:
  void register_nmethod(nmethod* nm) override;
  void unregister_nmethod(nmethod* nm) override;
  void verify_nmethod(nmethod* nm) override {}

// ---------- Pinning hooks
//
public:
  // Shenandoah supports per-object (per-region) pinning
  void pin_object(JavaThread* thread, oop obj) override;
  void unpin_object(JavaThread* thread, oop obj) override;

  void sync_pinned_region_status();
  void assert_pinned_region_status() NOT_DEBUG_RETURN;

// ---------- Concurrent Stack Processing support
//
public:
  bool uses_stack_watermark_barrier() const override { return true; }

// ---------- Allocation support
//
private:
  // How many bytes to transfer between old and young after we have finished recycling collection set regions?
  size_t _old_regions_surplus;
  size_t _old_regions_deficit;

  HeapWord* allocate_memory_under_lock(ShenandoahAllocRequest& request, bool& in_new_region, bool is_promotion);

  inline HeapWord* allocate_from_gclab(Thread* thread, size_t size);
  HeapWord* allocate_from_gclab_slow(Thread* thread, size_t size);
  HeapWord* allocate_new_gclab(size_t min_size, size_t word_size, size_t* actual_size);

  inline HeapWord* allocate_from_plab(Thread* thread, size_t size, bool is_promotion);
  HeapWord* allocate_from_plab_slow(Thread* thread, size_t size, bool is_promotion);
  HeapWord* allocate_new_plab(size_t min_size, size_t word_size, size_t* actual_size);

public:
  HeapWord* allocate_memory(ShenandoahAllocRequest& request, bool is_promotion);
  HeapWord* mem_allocate(size_t size, bool* what) override;
  MetaWord* satisfy_failed_metadata_allocation(ClassLoaderData* loader_data,
                                               size_t size,
                                               Metaspace::MetadataType mdtype) override;

  void notify_mutator_alloc_words(size_t words, size_t waste);

  HeapWord* allocate_new_tlab(size_t min_size, size_t requested_size, size_t* actual_size) override;
  size_t tlab_capacity(Thread *thr) const override;
  size_t unsafe_max_tlab_alloc(Thread *thread) const override;
  size_t max_tlab_size() const override;
  size_t tlab_used(Thread* ignored) const override;

  void ensure_parsability(bool retire_labs) override;

  void labs_make_parsable();
  void tlabs_retire(bool resize);
  void gclabs_retire(bool resize);

  inline void set_old_region_surplus(size_t surplus) { _old_regions_surplus = surplus; };
  inline void set_old_region_deficit(size_t deficit) { _old_regions_deficit = deficit; };

  inline size_t get_old_region_surplus() { return _old_regions_surplus; };
  inline size_t get_old_region_deficit() { return _old_regions_deficit; };

// ---------- Marking support
//
private:
  ShenandoahMarkingContext* _marking_context;
  MemRegion  _bitmap_region;
  MemRegion  _aux_bitmap_region;
  MarkBitMap _verification_bit_map;
  MarkBitMap _aux_bit_map;

  size_t _bitmap_size;
  size_t _bitmap_regions_per_slice;
  size_t _bitmap_bytes_per_slice;

  size_t _pretouch_heap_page_size;
  size_t _pretouch_bitmap_page_size;

  bool _bitmap_region_special;
  bool _aux_bitmap_region_special;

  ShenandoahLiveData** _liveness_cache;

public:
  inline ShenandoahMarkingContext* complete_marking_context() const;
  inline ShenandoahMarkingContext* marking_context() const;

  template<class T>
  inline void marked_object_iterate(ShenandoahHeapRegion* region, T* cl);

  template<class T>
  inline void marked_object_iterate(ShenandoahHeapRegion* region, T* cl, HeapWord* limit);

  template<class T>
  inline void marked_object_oop_iterate(ShenandoahHeapRegion* region, T* cl, HeapWord* limit);

  // SATB barriers hooks
  inline bool requires_marking(const void* entry) const;

  // Support for bitmap uncommits
  bool commit_bitmap_slice(ShenandoahHeapRegion *r);
  bool uncommit_bitmap_slice(ShenandoahHeapRegion *r);
  bool is_bitmap_slice_committed(ShenandoahHeapRegion* r, bool skip_self = false);

  // Liveness caching support
  ShenandoahLiveData* get_liveness_cache(uint worker_id);
  void flush_liveness_cache(uint worker_id);

  size_t pretouch_heap_page_size() { return _pretouch_heap_page_size; }

// ---------- Evacuation support
//
private:
  ShenandoahCollectionSet* _collection_set;
  ShenandoahEvacOOMHandler _oom_evac_handler;
  ShenandoahSharedFlag _old_gen_oom_evac;

  inline oop try_evacuate_object(oop src, Thread* thread, ShenandoahHeapRegion* from_region, ShenandoahAffiliation target_gen);
  void handle_old_evacuation(HeapWord* obj, size_t words, bool promotion);
  void handle_old_evacuation_failure();

public:
  void report_promotion_failure(Thread* thread, size_t size);

  static address in_cset_fast_test_addr();

  ShenandoahCollectionSet* collection_set() const { return _collection_set; }

  // Checks if object is in the collection set.
  inline bool in_collection_set(oop obj) const;

  // Checks if location is in the collection set. Can be interior pointer, not the oop itself.
  inline bool in_collection_set_loc(void* loc) const;

  // Evacuates or promotes object src. Returns the evacuated object, either evacuated
  // by this thread, or by some other thread.
  inline oop evacuate_object(oop src, Thread* thread);

  // Call before/after evacuation.
  inline void enter_evacuation(Thread* t);
  inline void leave_evacuation(Thread* t);

  inline bool clear_old_evacuation_failure();

// ---------- Generational support
//
private:
  RememberedScanner* _card_scan;

public:
  inline RememberedScanner* card_scan() { return _card_scan; }
  void clear_cards_for(ShenandoahHeapRegion* region);
  void mark_card_as_dirty(void* location);
  void retire_plab(PLAB* plab);
  void retire_plab(PLAB* plab, Thread* thread);
  void cancel_old_gc();

  void adjust_generation_sizes_for_next_cycle(size_t old_xfer_limit, size_t young_cset_regions, size_t old_cset_regions);

// ---------- Helper functions
//
public:
  template <class T>
  inline void conc_update_with_forwarded(T* p);

  template <class T>
  inline void update_with_forwarded(T* p);

  static inline void atomic_update_oop(oop update,       oop* addr,       oop compare);
  static inline void atomic_update_oop(oop update, narrowOop* addr,       oop compare);
  static inline void atomic_update_oop(oop update, narrowOop* addr, narrowOop compare);

  static inline bool atomic_update_oop_check(oop update,       oop* addr,       oop compare);
  static inline bool atomic_update_oop_check(oop update, narrowOop* addr,       oop compare);
  static inline bool atomic_update_oop_check(oop update, narrowOop* addr, narrowOop compare);

  static inline void atomic_clear_oop(      oop* addr,       oop compare);
  static inline void atomic_clear_oop(narrowOop* addr,       oop compare);
  static inline void atomic_clear_oop(narrowOop* addr, narrowOop compare);

  size_t trash_humongous_region_at(ShenandoahHeapRegion *r);

  static inline void increase_object_age(oop obj, uint additional_age);

  // Return the object's age (at a safepoint or when object isn't
  // mutable by the mutator)
  static inline uint get_object_age(oop obj);

  // Return the object's age, or a sentinel value when the age can't
  // necessarily be determined because of concurrent locking by the
  // mutator
  static inline uint get_object_age_concurrent(oop obj);

  void transfer_old_pointers_from_satb();

  void log_heap_status(const char *msg) const;

private:
  void trash_cset_regions();

// ---------- Testing helpers functions
//
private:
  ShenandoahSharedFlag _inject_alloc_failure;

  void try_inject_alloc_failure();
  bool should_inject_alloc_failure();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHHEAP_HPP
