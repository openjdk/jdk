/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/markBitMap.hpp"
#include "gc/shared/softRefPolicy.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahController.hpp"
#include "gc/shenandoah/shenandoahLock.hpp"
#include "gc/shenandoah/shenandoahEvacOOMHandler.hpp"
#include "gc/shenandoah/shenandoahEvacTracker.hpp"
#include "gc/shenandoah/shenandoahGenerationType.hpp"
#include "gc/shenandoah/shenandoahGenerationSizer.hpp"
#include "gc/shenandoah/shenandoahMmuTracker.hpp"
#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "gc/shenandoah/shenandoahPadding.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"
#include "gc/shenandoah/shenandoahUnload.hpp"
#include "memory/metaspace.hpp"
#include "services/memoryManager.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/stack.hpp"

class ConcurrentGCTimer;
class ObjectIterateScanRootClosure;
class ShenandoahCollectorPolicy;
class ShenandoahGCSession;
class ShenandoahGCStateResetter;
class ShenandoahGeneration;
class ShenandoahYoungGeneration;
class ShenandoahOldGeneration;
class ShenandoahHeuristics;
class ShenandoahMarkingContext;
class ShenandoahMode;
class ShenandoahPhaseTimings;
class ShenandoahHeap;
class ShenandoahHeapRegion;
class ShenandoahHeapRegionClosure;
class ShenandoahCollectionSet;
class ShenandoahFreeSet;
class ShenandoahConcurrentMark;
class ShenandoahFullGC;
class ShenandoahMonitoringSupport;
class ShenandoahPacer;
class ShenandoahReferenceProcessor;
class ShenandoahUncommitThread;
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

  // Indicates the generation whose collection is in
  // progress. Mutator threads aren't allowed to read
  // this field.
  ShenandoahGeneration* _gc_generation;

  // This is set and cleared by only the VMThread
  // at each STW pause (safepoint) to the value seen in
  // _gc_generation. This allows the value to be always consistently
  // seen by all mutators as well as all GC worker threads.
  // In that sense, it's a stable snapshot of _gc_generation that is
  // updated at each STW pause associated with a ShenandoahVMOp.
  ShenandoahGeneration* _active_generation;

public:
  ShenandoahHeapLock* lock() {
    return &_lock;
  }

  ShenandoahGeneration* gc_generation() const {
    // We don't want this field read by a mutator thread
    assert(!Thread::current()->is_Java_thread(), "Not allowed");
    // value of _gc_generation field, see above
    return _gc_generation;
  }

  ShenandoahGeneration* active_generation() const {
    // value of _active_generation field, see above
    return _active_generation;
  }

  // Set the _gc_generation field
  void set_gc_generation(ShenandoahGeneration* generation);

  // Copy the value in the _gc_generation field into
  // the _active_generation field: can only be called at
  // a safepoint by the VMThread.
  void set_active_generation();

  ShenandoahHeuristics* heuristics();

// ---------- Initialization, termination, identification, printing routines
//
public:
  static ShenandoahHeap* heap();

  const char* name()          const override { return "Shenandoah"; }
  ShenandoahHeap::Name kind() const override { return CollectedHeap::Shenandoah; }

  ShenandoahHeap(ShenandoahCollectorPolicy* policy);
  jint initialize() override;
  void post_initialize() override;
  void initialize_mode();
  virtual void initialize_heuristics();
  virtual void print_init_logger() const;
  void initialize_serviceability() override;

  void print_on(outputStream* st)              const override;
  void print_extended_on(outputStream *st)     const override;
  void print_tracing_info()                    const override;
  void print_heap_regions_on(outputStream* st) const;

  void stop() override;

  void prepare_for_verify() override;
  void verify(VerifyOption vo) override;

// WhiteBox testing support.
  bool supports_concurrent_gc_breakpoints() const override {
    return true;
  }

// ---------- Heap counters and metrics
//
private:
  size_t _initial_size;
  size_t _minimum_size;

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

// ---------- Periodic Tasks
//
public:
  // Notify heuristics and region state change logger that the state of the heap has changed
  void notify_heap_changed();

  // Force counters to update
  void set_forced_counters_update(bool value);

  // Update counters if forced flag is set
  void handle_force_counters_update();

// ---------- Workers handling
//
private:
  uint _max_workers;
  ShenandoahWorkerThreads* _workers;
  ShenandoahWorkerThreads* _safepoint_workers;

  virtual void initialize_controller();

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
  bool _gc_state_changed;
  ShenandoahSharedBitmap _gc_state;
  ShenandoahSharedFlag   _heap_changed;
  ShenandoahSharedFlag   _degenerated_gc_in_progress;
  ShenandoahSharedFlag   _full_gc_in_progress;
  ShenandoahSharedFlag   _full_gc_move_in_progress;
  ShenandoahSharedFlag   _concurrent_strong_root_in_progress;

  size_t _gc_no_progress_count;

  // This updates the singlular, global gc state. This must happen on a safepoint.
  void set_gc_state(uint mask, bool value);

public:
  char gc_state() const;

  // This copies the global gc state into a thread local variable for java threads.
  // It is primarily intended to support quick access at barriers.
  void propagate_gc_state_to_java_threads();

  // This is public to support assertions that the state hasn't been changed off of
  // a safepoint and that any changes were propagated to java threads after the safepoint.
  bool has_gc_state_changed() const { return _gc_state_changed; }

  // Returns true if allocations have occurred in new regions or if regions have been
  // uncommitted since the previous calls. This call will reset the flag to false.
  bool has_changed() {
    return _heap_changed.try_unset();
  }

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

  inline bool is_stable() const;
  inline bool is_idle() const;

  inline bool is_concurrent_mark_in_progress() const;
  inline bool is_concurrent_young_mark_in_progress() const;
  inline bool is_concurrent_old_mark_in_progress() const;
  inline bool is_update_refs_in_progress() const;
  inline bool is_evacuation_in_progress() const;
  inline bool is_degenerated_gc_in_progress() const;
  inline bool is_full_gc_in_progress() const;
  inline bool is_full_gc_move_in_progress() const;
  inline bool has_forwarded_objects() const;

  inline bool is_stw_gc_in_progress() const;
  inline bool is_concurrent_strong_root_in_progress() const;
  inline bool is_concurrent_weak_root_in_progress() const;
  bool is_prepare_for_old_mark_in_progress() const;

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
  // Returns true if the soft maximum heap has been changed using management APIs.
  bool check_soft_max_changed();

protected:
  // This is shared between shConcurrentGC and shDegenerateGC so that degenerated
  // GC can resume update refs from where the concurrent GC was cancelled. It is
  // also used in shGenerationalHeap, which uses a different closure for update refs.
  ShenandoahRegionIterator _update_refs_iterator;

private:
  // GC support
  // Evacuation
  virtual void evacuate_collection_set(bool concurrent);
  // Concurrent root processing
  void prepare_concurrent_roots();
  void finish_concurrent_roots();
  // Concurrent class unloading support
  void do_class_unloading();
  // Reference updating
  void prepare_update_heap_references(bool concurrent);
  virtual void update_heap_references(bool concurrent);
  // Final update region states
  void update_heap_region_states(bool concurrent);
  virtual void final_update_refs_update_region_states();

  void rendezvous_threads(const char* name);
  void recycle_trash();
public:
  void rebuild_free_set(bool concurrent);
  void notify_gc_progress();
  void notify_gc_no_progress();
  size_t get_gc_no_progress_count() const;

  // The uncommit thread targets soft max heap, notify this thread when that value has changed.
  void notify_soft_max_changed();

  // An explicit GC request may have freed regions, notify the uncommit thread.
  void notify_explicit_gc_requested();

private:
  ShenandoahGeneration*  _global_generation;

protected:
  // The control thread presides over concurrent collection cycles
  ShenandoahController*  _control_thread;

  // The uncommit thread periodically attempts to uncommit regions that have been empty for longer than ShenandoahUncommitDelay
  ShenandoahUncommitThread*  _uncommit_thread;

  ShenandoahYoungGeneration* _young_generation;
  ShenandoahOldGeneration*   _old_generation;

private:
  ShenandoahCollectorPolicy* _shenandoah_policy;
  ShenandoahMode*            _gc_mode;
  ShenandoahFreeSet*         _free_set;
  ShenandoahPacer*           _pacer;
  ShenandoahVerifier*        _verifier;

  ShenandoahPhaseTimings*       _phase_timings;
  ShenandoahMmuTracker          _mmu_tracker;

public:
  ShenandoahController*   control_thread() const { return _control_thread; }

  ShenandoahGeneration*      global_generation() const { return _global_generation; }
  ShenandoahYoungGeneration* young_generation()  const {
    assert(mode()->is_generational(), "Young generation requires generational mode");
    return _young_generation;
  }

  ShenandoahOldGeneration*   old_generation()    const {
    assert(mode()->is_generational(), "Old generation requires generational mode");
    return _old_generation;
  }

  ShenandoahGeneration*      generation_for(ShenandoahAffiliation affiliation) const;

  ShenandoahCollectorPolicy* shenandoah_policy() const { return _shenandoah_policy; }
  ShenandoahMode*            mode()              const { return _gc_mode;           }
  ShenandoahFreeSet*         free_set()          const { return _free_set;          }
  ShenandoahPacer*           pacer()             const { return _pacer;             }

  ShenandoahPhaseTimings*    phase_timings()     const { return _phase_timings;     }

  ShenandoahEvacOOMHandler*  oom_evac_handler()        { return &_oom_evac_handler; }

  void on_cycle_start(GCCause::Cause cause, ShenandoahGeneration* generation);
  void on_cycle_end(ShenandoahGeneration* generation);

  ShenandoahVerifier*        verifier();

// ---------- VM subsystem bindings
//
private:
  ShenandoahMonitoringSupport* _monitoring_support;
  MemoryPool*                  _memory_pool;
  GCMemoryManager              _stw_memory_manager;
  GCMemoryManager              _cycle_memory_manager;
  ConcurrentGCTimer*           _gc_timer;
  // For exporting to SA
  int                          _log_min_obj_alignment_in_bytes;
public:
  ShenandoahMonitoringSupport* monitoring_support() const    { return _monitoring_support;    }
  GCMemoryManager* cycle_memory_manager()                    { return &_cycle_memory_manager; }
  GCMemoryManager* stw_memory_manager()                      { return &_stw_memory_manager;   }

  GrowableArray<GCMemoryManager*> memory_managers() override;
  GrowableArray<MemoryPool*> memory_pools() override;
  MemoryUsage memory_usage() override;
  GCTracer* tracer();
  ConcurrentGCTimer* gc_timer() const;

// ---------- Class Unloading
//
private:
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

  // Check the pointer is in active part of Java heap.
  // Use is_in_reserved to check if object is within heap bounds.
  bool is_in(const void* p) const override;

  // Returns true if the given oop belongs to a generation that is actively being collected.
  inline bool is_in_active_generation(oop obj) const;
  inline bool is_in_young(const void* p) const;
  inline bool is_in_old(const void* p) const;

  // Returns true iff the young generation is being collected and the given pointer
  // is in the old generation. This is used to prevent the young collection from treating
  // such an object as unreachable.
  inline bool is_in_old_during_young_collection(oop obj) const;

  inline ShenandoahAffiliation region_affiliation(const ShenandoahHeapRegion* r) const;
  inline void set_affiliation(ShenandoahHeapRegion* r, ShenandoahAffiliation new_affiliation);

  inline ShenandoahAffiliation region_affiliation(size_t index) const;

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

// ---------- CDS archive support

  bool can_load_archived_objects() const override { return !ShenandoahCardBarrier; }
  HeapWord* allocate_loaded_archive_space(size_t size) override;
  void complete_loaded_archive_space(MemRegion archive_space) override;

// ---------- Allocation support
//
protected:
  inline HeapWord* allocate_from_gclab(Thread* thread, size_t size);

private:
  HeapWord* allocate_memory_under_lock(ShenandoahAllocRequest& request, bool& in_new_region);
  HeapWord* allocate_from_gclab_slow(Thread* thread, size_t size);
  HeapWord* allocate_new_gclab(size_t min_size, size_t word_size, size_t* actual_size);

public:
  HeapWord* allocate_memory(ShenandoahAllocRequest& request);
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

  // During concurrent reset, the control thread will zero out the mark bitmaps for committed regions.
  // This cannot happen when the uncommit thread is simultaneously trying to uncommit regions and their bitmaps.
  // To prevent these threads from working at the same time, we provide these methods for the control thread to
  // prevent the uncommit thread from working while a collection cycle is in progress.

  // Forbid uncommits (will stop and wait if regions are being uncommitted)
  void forbid_uncommit();

  // Allow the uncommit thread to process regions
  void allow_uncommit();
#ifdef ASSERT
  bool is_uncommit_in_progress();
#endif

  // Liveness caching support
  ShenandoahLiveData* get_liveness_cache(uint worker_id);
  void flush_liveness_cache(uint worker_id);

  size_t pretouch_heap_page_size() { return _pretouch_heap_page_size; }

// ---------- Evacuation support
//
private:
  ShenandoahCollectionSet* _collection_set;
  ShenandoahEvacOOMHandler _oom_evac_handler;

  oop try_evacuate_object(oop src, Thread* thread, ShenandoahHeapRegion* from_region, ShenandoahAffiliation target_gen);

public:
  static address in_cset_fast_test_addr();

  ShenandoahCollectionSet* collection_set() const { return _collection_set; }

  // Checks if object is in the collection set.
  inline bool in_collection_set(oop obj) const;

  // Checks if location is in the collection set. Can be interior pointer, not the oop itself.
  inline bool in_collection_set_loc(void* loc) const;

  // Evacuates or promotes object src. Returns the evacuated object, either evacuated
  // by this thread, or by some other thread.
  virtual oop evacuate_object(oop src, Thread* thread);

  // Call before/after evacuation.
  inline void enter_evacuation(Thread* t);
  inline void leave_evacuation(Thread* t);

// ---------- Helper functions
//
public:
  template <class T>
  inline void conc_update_with_forwarded(T* p);

  template <class T>
  inline void non_conc_update_with_forwarded(T* p);

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

  // Return the object's age, or a sentinel value when the age can't
  // necessarily be determined because of concurrent locking by the
  // mutator
  static inline uint get_object_age(oop obj);

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
