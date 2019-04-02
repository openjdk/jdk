/*
 * Copyright (c) 2013, 2019, Red Hat, Inc. All rights reserved.
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
#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahHeapLock.hpp"
#include "gc/shenandoah/shenandoahEvacOOMHandler.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"
#include "services/memoryManager.hpp"

class ConcurrentGCTimer;
class ReferenceProcessor;
class ShenandoahAllocTracker;
class ShenandoahCollectorPolicy;
class ShenandoahControlThread;
class ShenandoahGCSession;
class ShenandoahHeuristics;
class ShenandoahMarkingContext;
class ShenandoahPhaseTimings;
class ShenandoahHeap;
class ShenandoahHeapRegion;
class ShenandoahHeapRegionClosure;
class ShenandoahCollectionSet;
class ShenandoahFreeSet;
class ShenandoahConcurrentMark;
class ShenandoahMarkCompact;
class ShenandoahMonitoringSupport;
class ShenandoahPacer;
class ShenandoahTraversalGC;
class ShenandoahVerifier;
class ShenandoahWorkGang;
class VMStructs;

class ShenandoahRegionIterator : public StackObj {
private:
  ShenandoahHeap* _heap;

  DEFINE_PAD_MINUS_SIZE(0, DEFAULT_CACHE_LINE_SIZE, sizeof(volatile size_t));
  volatile size_t _index;
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, 0);

  // No implicit copying: iterators should be passed by reference to capture the state
  ShenandoahRegionIterator(const ShenandoahRegionIterator& that);
  ShenandoahRegionIterator& operator=(const ShenandoahRegionIterator& o);

public:
  ShenandoahRegionIterator();
  ShenandoahRegionIterator(ShenandoahHeap* heap);

  // Reset iterator to default state
  void reset();

  // Returns next region, or NULL if there are no more regions.
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

class ShenandoahUpdateRefsClosure: public OopClosure {
private:
  ShenandoahHeap* _heap;

  template <class T>
  inline void do_oop_work(T* p);

public:
  ShenandoahUpdateRefsClosure();
  inline void do_oop(oop* p);
  inline void do_oop(narrowOop* p);
};

#ifdef ASSERT
class ShenandoahAssertToSpaceClosure : public OopClosure {
private:
  template <class T>
  void do_oop_work(T* p);
public:
  void do_oop(narrowOop* p);
  void do_oop(oop* p);
};
#endif

class ShenandoahAlwaysTrueClosure : public BoolObjectClosure {
public:
  bool do_object_b(oop p) { return true; }
};

class ShenandoahForwardedIsAliveClosure: public BoolObjectClosure {
private:
  ShenandoahMarkingContext* const _mark_context;
public:
  ShenandoahForwardedIsAliveClosure();
  bool do_object_b(oop obj);
};

class ShenandoahIsAliveClosure: public BoolObjectClosure {
private:
  ShenandoahMarkingContext* const _mark_context;
public:
  ShenandoahIsAliveClosure();
  bool do_object_b(oop obj);
};

class ShenandoahIsAliveSelector : public StackObj {
private:
  ShenandoahIsAliveClosure _alive_cl;
  ShenandoahForwardedIsAliveClosure _fwd_alive_cl;
public:
  BoolObjectClosure* is_alive_closure();
};

// Shenandoah GC is low-pause concurrent GC that uses Brooks forwarding pointers
// to encode forwarding data. See BrooksPointer for details on forwarding data encoding.
// See ShenandoahControlThread for GC cycle structure.
//
class ShenandoahHeap : public CollectedHeap {
  friend class ShenandoahAsserts;
  friend class VMStructs;
  friend class ShenandoahGCSession;

// ---------- Locks that guard important data structures in Heap
//
private:
  ShenandoahHeapLock _lock;

public:
  ShenandoahHeapLock* lock() {
    return &_lock;
  }

  void assert_heaplock_owned_by_current_thread()     NOT_DEBUG_RETURN;
  void assert_heaplock_not_owned_by_current_thread() NOT_DEBUG_RETURN;
  void assert_heaplock_or_safepoint()                NOT_DEBUG_RETURN;

// ---------- Initialization, termination, identification, printing routines
//
public:
  static ShenandoahHeap* heap();
  static ShenandoahHeap* heap_no_check();

  const char* name()          const { return "Shenandoah"; }
  ShenandoahHeap::Name kind() const { return CollectedHeap::Shenandoah; }

  ShenandoahHeap(ShenandoahCollectorPolicy* policy);
  jint initialize();
  void post_initialize();
  void initialize_heuristics();

  void initialize_serviceability();

  void print_on(outputStream* st)              const;
  void print_extended_on(outputStream *st)     const;
  void print_tracing_info()                    const;
  void print_gc_threads_on(outputStream* st)   const;
  void print_heap_regions_on(outputStream* st) const;

  void stop();

  void prepare_for_verify();
  void verify(VerifyOption vo);

// ---------- Heap counters and metrics
//
private:
           size_t _initial_size;
  DEFINE_PAD_MINUS_SIZE(0, DEFAULT_CACHE_LINE_SIZE, sizeof(volatile size_t));
  volatile size_t _used;
  volatile size_t _committed;
  volatile size_t _bytes_allocated_since_gc_start;
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, 0);

public:
  void increase_used(size_t bytes);
  void decrease_used(size_t bytes);
  void set_used(size_t bytes);

  void increase_committed(size_t bytes);
  void decrease_committed(size_t bytes);
  void increase_allocated(size_t bytes);

  size_t bytes_allocated_since_gc_start();
  void reset_bytes_allocated_since_gc_start();

  size_t max_capacity()     const;
  size_t initial_capacity() const;
  size_t capacity()         const;
  size_t used()             const;
  size_t committed()        const;

// ---------- Workers handling
//
private:
  uint _max_workers;
  ShenandoahWorkGang* _workers;
  ShenandoahWorkGang* _safepoint_workers;

public:
  uint max_workers();
  void assert_gc_workers(uint nworker) NOT_DEBUG_RETURN;

  WorkGang* workers() const;
  WorkGang* get_safepoint_workers();

  void gc_threads_do(ThreadClosure* tcl) const;

// ---------- Heap regions handling machinery
//
private:
  MemRegion _heap_region;
  bool      _heap_region_special;
  size_t    _num_regions;
  ShenandoahHeapRegion** _regions;
  ShenandoahRegionIterator _update_refs_iterator;

public:
  inline size_t num_regions() const { return _num_regions; }
  inline bool is_heap_region_special() { return _heap_region_special; }

  inline ShenandoahHeapRegion* const heap_region_containing(const void* addr) const;
  inline size_t heap_region_index_containing(const void* addr) const;

  inline ShenandoahHeapRegion* const get_region(size_t region_idx) const;

  void heap_region_iterate(ShenandoahHeapRegionClosure* blk) const;
  void parallel_heap_region_iterate(ShenandoahHeapRegionClosure* blk) const;

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
    MARKING_BITPOS    = 1,

    // Heap is under evacuation: needs LRB barriers. (Set together with HAS_FORWARDED)
    EVACUATION_BITPOS = 2,

    // Heap is under updating: needs no additional barriers.
    UPDATEREFS_BITPOS = 3,

    // Heap is under traversal collection
    TRAVERSAL_BITPOS  = 4,
  };

  enum GCState {
    STABLE        = 0,
    HAS_FORWARDED = 1 << HAS_FORWARDED_BITPOS,
    MARKING       = 1 << MARKING_BITPOS,
    EVACUATION    = 1 << EVACUATION_BITPOS,
    UPDATEREFS    = 1 << UPDATEREFS_BITPOS,
    TRAVERSAL     = 1 << TRAVERSAL_BITPOS,
  };

private:
  ShenandoahSharedBitmap _gc_state;
  ShenandoahSharedFlag   _degenerated_gc_in_progress;
  ShenandoahSharedFlag   _full_gc_in_progress;
  ShenandoahSharedFlag   _full_gc_move_in_progress;
  ShenandoahSharedFlag   _progress_last_gc;

  void set_gc_state_all_threads(char state);
  void set_gc_state_mask(uint mask, bool value);

public:
  char gc_state() const;
  static address gc_state_addr();

  void set_concurrent_mark_in_progress(bool in_progress);
  void set_evacuation_in_progress(bool in_progress);
  void set_update_refs_in_progress(bool in_progress);
  void set_degenerated_gc_in_progress(bool in_progress);
  void set_full_gc_in_progress(bool in_progress);
  void set_full_gc_move_in_progress(bool in_progress);
  void set_concurrent_traversal_in_progress(bool in_progress);
  void set_has_forwarded_objects(bool cond);

  inline bool is_stable() const;
  inline bool is_idle() const;
  inline bool is_concurrent_mark_in_progress() const;
  inline bool is_update_refs_in_progress() const;
  inline bool is_evacuation_in_progress() const;
  inline bool is_degenerated_gc_in_progress() const;
  inline bool is_full_gc_in_progress() const;
  inline bool is_full_gc_move_in_progress() const;
  inline bool is_concurrent_traversal_in_progress() const;
  inline bool has_forwarded_objects() const;
  inline bool is_gc_in_progress_mask(uint mask) const;

// ---------- GC cancellation and degeneration machinery
//
// Cancelled GC flag is used to notify concurrent phases that they should terminate.
//
public:
  enum ShenandoahDegenPoint {
    _degenerated_unset,
    _degenerated_traversal,
    _degenerated_outside_cycle,
    _degenerated_mark,
    _degenerated_evac,
    _degenerated_updaterefs,
    _DEGENERATED_LIMIT,
  };

  static const char* degen_point_to_string(ShenandoahDegenPoint point) {
    switch (point) {
      case _degenerated_unset:
        return "<UNSET>";
      case _degenerated_traversal:
        return "Traversal";
      case _degenerated_outside_cycle:
        return "Outside of Cycle";
      case _degenerated_mark:
        return "Mark";
      case _degenerated_evac:
        return "Evacuation";
      case _degenerated_updaterefs:
        return "Update Refs";
      default:
        ShouldNotReachHere();
        return "ERROR";
    }
  };

private:
  enum CancelState {
    // Normal state. GC has not been cancelled and is open for cancellation.
    // Worker threads can suspend for safepoint.
    CANCELLABLE,

    // GC has been cancelled. Worker threads can not suspend for
    // safepoint but must finish their work as soon as possible.
    CANCELLED,

    // GC has not been cancelled and must not be cancelled. At least
    // one worker thread checks for pending safepoint and may suspend
    // if a safepoint is pending.
    NOT_CANCELLED
  };

  ShenandoahSharedEnumFlag<CancelState> _cancelled_gc;
  bool try_cancel_gc();

public:
  static address cancelled_gc_addr();

  inline bool cancelled_gc() const;
  inline bool check_cancelled_gc_and_yield(bool sts_active = true);

  inline void clear_cancelled_gc();

  void cancel_gc(GCCause::Cause cause);

// ---------- GC operations entry points
//
public:
  // Entry points to STW GC operations, these cause a related safepoint, that then
  // call the entry method below
  void vmop_entry_init_mark();
  void vmop_entry_final_mark();
  void vmop_entry_final_evac();
  void vmop_entry_init_updaterefs();
  void vmop_entry_final_updaterefs();
  void vmop_entry_init_traversal();
  void vmop_entry_final_traversal();
  void vmop_entry_full(GCCause::Cause cause);
  void vmop_degenerated(ShenandoahDegenPoint point);

  // Entry methods to normally STW GC operations. These set up logging, monitoring
  // and workers for net VM operation
  void entry_init_mark();
  void entry_final_mark();
  void entry_final_evac();
  void entry_init_updaterefs();
  void entry_final_updaterefs();
  void entry_init_traversal();
  void entry_final_traversal();
  void entry_full(GCCause::Cause cause);
  void entry_degenerated(int point);

  // Entry methods to normally concurrent GC operations. These set up logging, monitoring
  // for concurrent operation.
  void entry_reset();
  void entry_mark();
  void entry_preclean();
  void entry_cleanup();
  void entry_evac();
  void entry_updaterefs();
  void entry_traversal();
  void entry_uncommit(double shrink_before);

private:
  // Actual work for the phases
  void op_init_mark();
  void op_final_mark();
  void op_final_evac();
  void op_init_updaterefs();
  void op_final_updaterefs();
  void op_init_traversal();
  void op_final_traversal();
  void op_full(GCCause::Cause cause);
  void op_degenerated(ShenandoahDegenPoint point);
  void op_degenerated_fail();
  void op_degenerated_futile();

  void op_reset();
  void op_mark();
  void op_preclean();
  void op_cleanup();
  void op_conc_evac();
  void op_stw_evac();
  void op_updaterefs();
  void op_traversal();
  void op_uncommit(double shrink_before);

  // Messages for GC trace events, they have to be immortal for
  // passing around the logging/tracing systems
  const char* init_mark_event_message() const;
  const char* final_mark_event_message() const;
  const char* conc_mark_event_message() const;
  const char* degen_event_message(ShenandoahDegenPoint point) const;

// ---------- GC subsystems
//
private:
  ShenandoahControlThread*   _control_thread;
  ShenandoahCollectorPolicy* _shenandoah_policy;
  ShenandoahHeuristics*      _heuristics;
  ShenandoahFreeSet*         _free_set;
  ShenandoahConcurrentMark*  _scm;
  ShenandoahTraversalGC*     _traversal_gc;
  ShenandoahMarkCompact*     _full_gc;
  ShenandoahPacer*           _pacer;
  ShenandoahVerifier*        _verifier;

  ShenandoahAllocTracker*    _alloc_tracker;
  ShenandoahPhaseTimings*    _phase_timings;

  ShenandoahControlThread*   control_thread()          { return _control_thread;    }
  ShenandoahMarkCompact*     full_gc()                 { return _full_gc;           }

public:
  ShenandoahCollectorPolicy* shenandoah_policy() const { return _shenandoah_policy; }
  ShenandoahHeuristics*      heuristics()        const { return _heuristics;        }
  ShenandoahFreeSet*         free_set()          const { return _free_set;          }
  ShenandoahConcurrentMark*  concurrent_mark()         { return _scm;               }
  ShenandoahTraversalGC*     traversal_gc()            { return _traversal_gc;      }
  ShenandoahPacer*           pacer()             const { return _pacer;             }

  ShenandoahPhaseTimings*    phase_timings()     const { return _phase_timings;     }
  ShenandoahAllocTracker*    alloc_tracker()     const { return _alloc_tracker;     }

  ShenandoahVerifier*        verifier();

// ---------- VM subsystem bindings
//
private:
  ShenandoahMonitoringSupport* _monitoring_support;
  MemoryPool*                  _memory_pool;
  GCMemoryManager              _stw_memory_manager;
  GCMemoryManager              _cycle_memory_manager;
  ConcurrentGCTimer*           _gc_timer;
  SoftRefPolicy                _soft_ref_policy;

  // For exporting to SA
  int                          _log_min_obj_alignment_in_bytes;
public:
  ShenandoahMonitoringSupport* monitoring_support() { return _monitoring_support;    }
  GCMemoryManager* cycle_memory_manager()           { return &_cycle_memory_manager; }
  GCMemoryManager* stw_memory_manager()             { return &_stw_memory_manager;   }
  SoftRefPolicy* soft_ref_policy()                  { return &_soft_ref_policy;      }

  GrowableArray<GCMemoryManager*> memory_managers();
  GrowableArray<MemoryPool*> memory_pools();
  MemoryUsage memory_usage();
  GCTracer* tracer();
  GCTimer* gc_timer() const;
  CollectorPolicy* collector_policy() const;

// ---------- Reference processing
//
private:
  AlwaysTrueClosure    _subject_to_discovery;
  ReferenceProcessor*  _ref_processor;
  ShenandoahSharedFlag _process_references;

  void ref_processing_init();

public:
  ReferenceProcessor* ref_processor() { return _ref_processor; }
  void set_process_references(bool pr);
  bool process_references() const;

// ---------- Class Unloading
//
private:
  ShenandoahSharedFlag _unload_classes;

public:
  void set_unload_classes(bool uc);
  bool unload_classes() const;

  // Delete entries for dead interned string and clean up unreferenced symbols
  // in symbol table, possibly in parallel.
  void unload_classes_and_cleanup_tables(bool full_gc);

// ---------- Generic interface hooks
// Minor things that super-interface expects us to implement to play nice with
// the rest of runtime. Some of the things here are not required to be implemented,
// and can be stubbed out.
//
public:
  AdaptiveSizePolicy* size_policy() shenandoah_not_implemented_return(NULL);
  bool is_maximal_no_gc() const shenandoah_not_implemented_return(false);

  bool is_in(const void* p) const;

  size_t obj_size(oop obj) const;
  virtual ptrdiff_t cell_header_size() const;

  void collect(GCCause::Cause cause);
  void do_full_collection(bool clear_all_soft_refs);

  // Used for parsing heap during error printing
  HeapWord* block_start(const void* addr) const;
  bool block_is_obj(const HeapWord* addr) const;

  // Used for native heap walkers: heap dumpers, mostly
  void object_iterate(ObjectClosure* cl);
  void safe_object_iterate(ObjectClosure* cl);

  // Used by RMI
  jlong millis_since_last_gc();

// ---------- Safepoint interface hooks
//
public:
  void safepoint_synchronize_begin();
  void safepoint_synchronize_end();

// ---------- Code roots handling hooks
//
public:
  void register_nmethod(nmethod* nm);
  void unregister_nmethod(nmethod* nm);
  void flush_nmethod(nmethod* nm) {}
  void verify_nmethod(nmethod* nm) {}

// ---------- Pinning hooks
//
public:
  // Shenandoah supports per-object (per-region) pinning
  bool supports_object_pinning() const { return true; }

  oop pin_object(JavaThread* thread, oop obj);
  void unpin_object(JavaThread* thread, oop obj);

// ---------- Allocation support
//
private:
  HeapWord* allocate_memory_under_lock(ShenandoahAllocRequest& request, bool& in_new_region);
  inline HeapWord* allocate_from_gclab(Thread* thread, size_t size);
  HeapWord* allocate_from_gclab_slow(Thread* thread, size_t size);
  HeapWord* allocate_new_gclab(size_t min_size, size_t word_size, size_t* actual_size);
  void retire_and_reset_gclabs();

public:
  HeapWord* allocate_memory(ShenandoahAllocRequest& request);
  HeapWord* mem_allocate(size_t size, bool* what);
  MetaWord* satisfy_failed_metadata_allocation(ClassLoaderData* loader_data,
                                               size_t size,
                                               Metaspace::MetadataType mdtype);

  oop obj_allocate(Klass* klass, int size, TRAPS);
  oop array_allocate(Klass* klass, int size, int length, bool do_zero, TRAPS);
  oop class_allocate(Klass* klass, int size, TRAPS);

  void notify_mutator_alloc_words(size_t words, bool waste);

  // Shenandoah supports TLAB allocation
  bool supports_tlab_allocation() const { return true; }

  HeapWord* allocate_new_tlab(size_t min_size, size_t requested_size, size_t* actual_size);
  size_t tlab_capacity(Thread *thr) const;
  size_t unsafe_max_tlab_alloc(Thread *thread) const;
  size_t max_tlab_size() const;
  size_t tlab_used(Thread* ignored) const;

  HeapWord* tlab_post_allocation_setup(HeapWord* obj);
  void fill_with_dummy_object(HeapWord* start, HeapWord* end, bool zap);
  size_t min_dummy_object_size() const;

  void resize_tlabs();

  void ensure_parsability(bool retire_tlabs);
  void make_parsable(bool retire_tlabs);

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

  bool _bitmap_region_special;
  bool _aux_bitmap_region_special;

  // Used for buffering per-region liveness data.
  // Needed since ShenandoahHeapRegion uses atomics to update liveness.
  //
  // The array has max-workers elements, each of which is an array of
  // jushort * max_regions. The choice of jushort is not accidental:
  // there is a tradeoff between static/dynamic footprint that translates
  // into cache pressure (which is already high during marking), and
  // too many atomic updates. size_t/jint is too large, jbyte is too small.
  jushort** _liveness_cache;

public:
  inline ShenandoahMarkingContext* complete_marking_context() const;
  inline ShenandoahMarkingContext* marking_context() const;
  inline void mark_complete_marking_context();
  inline void mark_incomplete_marking_context();

  template<class T>
  inline void marked_object_iterate(ShenandoahHeapRegion* region, T* cl);

  template<class T>
  inline void marked_object_iterate(ShenandoahHeapRegion* region, T* cl, HeapWord* limit);

  template<class T>
  inline void marked_object_oop_iterate(ShenandoahHeapRegion* region, T* cl, HeapWord* limit);

  void reset_mark_bitmap();

  // SATB barriers hooks
  template<bool RESOLVE>
  inline bool requires_marking(const void* entry) const;
  void force_satb_flush_all_threads();

  // Support for bitmap uncommits
  bool commit_bitmap_slice(ShenandoahHeapRegion *r);
  bool uncommit_bitmap_slice(ShenandoahHeapRegion *r);
  bool is_bitmap_slice_committed(ShenandoahHeapRegion* r, bool skip_self = false);

  // Liveness caching support
  jushort* get_liveness_cache(uint worker_id);
  void flush_liveness_cache(uint worker_id);

// ---------- Evacuation support
//
private:
  ShenandoahCollectionSet* _collection_set;
  ShenandoahEvacOOMHandler _oom_evac_handler;

  void evacuate_and_update_roots();

public:
  static address in_cset_fast_test_addr();

  ShenandoahCollectionSet* collection_set() const { return _collection_set; }

  template <class T>
  inline bool in_collection_set(T obj) const;

  // Avoid accidentally calling the method above with ShenandoahHeapRegion*, which would be *wrong*.
  inline bool in_collection_set(ShenandoahHeapRegion* r) shenandoah_not_implemented_return(false);

  // Evacuates object src. Returns the evacuated object, either evacuated
  // by this thread, or by some other thread.
  inline oop evacuate_object(oop src, Thread* thread);

  // Call before/after evacuation.
  void enter_evacuation();
  void leave_evacuation();

// ---------- Helper functions
//
public:
  template <class T>
  inline oop evac_update_with_forwarded(T* p);

  template <class T>
  inline oop maybe_update_with_forwarded(T* p);

  template <class T>
  inline oop maybe_update_with_forwarded_not_null(T* p, oop obj);

  template <class T>
  inline oop update_with_forwarded_not_null(T* p, oop obj);

  inline oop atomic_compare_exchange_oop(oop n, narrowOop* addr, oop c);
  inline oop atomic_compare_exchange_oop(oop n, oop* addr, oop c);

  void trash_humongous_region_at(ShenandoahHeapRegion *r);

  void deduplicate_string(oop str);

  void stop_concurrent_marking();

private:
  void trash_cset_regions();
  void update_heap_references(bool concurrent);

// ---------- Testing helpers functions
//
private:
  ShenandoahSharedFlag _inject_alloc_failure;

  void try_inject_alloc_failure();
  bool should_inject_alloc_failure();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHHEAP_HPP
