/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_INTERFACE_COLLECTEDHEAP_HPP
#define SHARE_VM_GC_INTERFACE_COLLECTEDHEAP_HPP

#include "gc_interface/gcCause.hpp"
#include "memory/allocation.hpp"
#include "memory/barrierSet.hpp"
#include "runtime/handles.hpp"
#include "runtime/perfData.hpp"
#include "runtime/safepoint.hpp"

// A "CollectedHeap" is an implementation of a java heap for HotSpot.  This
// is an abstract class: there may be many different kinds of heaps.  This
// class defines the functions that a heap must implement, and contains
// infrastructure common to all heaps.

class BarrierSet;
class ThreadClosure;
class AdaptiveSizePolicy;
class Thread;
class CollectorPolicy;

//
// CollectedHeap
//   SharedHeap
//     GenCollectedHeap
//     G1CollectedHeap
//   ParallelScavengeHeap
//
class CollectedHeap : public CHeapObj {
  friend class VMStructs;
  friend class IsGCActiveMark; // Block structured external access to _is_gc_active
  friend class constantPoolCacheKlass; // allocate() method inserts is_conc_safe

#ifdef ASSERT
  static int       _fire_out_of_memory_count;
#endif

  // Used for filler objects (static, but initialized in ctor).
  static size_t _filler_array_max_size;

  // Used in support of ReduceInitialCardMarks; only consulted if COMPILER2 is being used
  bool _defer_initial_card_mark;

 protected:
  MemRegion _reserved;
  BarrierSet* _barrier_set;
  bool _is_gc_active;
  int _n_par_threads;

  unsigned int _total_collections;          // ... started
  unsigned int _total_full_collections;     // ... started
  NOT_PRODUCT(volatile size_t _promotion_failure_alot_count;)
  NOT_PRODUCT(volatile size_t _promotion_failure_alot_gc_number;)

  // Reason for current garbage collection.  Should be set to
  // a value reflecting no collection between collections.
  GCCause::Cause _gc_cause;
  GCCause::Cause _gc_lastcause;
  PerfStringVariable* _perf_gc_cause;
  PerfStringVariable* _perf_gc_lastcause;

  // Constructor
  CollectedHeap();

  // Do common initializations that must follow instance construction,
  // for example, those needing virtual calls.
  // This code could perhaps be moved into initialize() but would
  // be slightly more awkward because we want the latter to be a
  // pure virtual.
  void pre_initialize();

  // Create a new tlab
  virtual HeapWord* allocate_new_tlab(size_t size);

  // Accumulate statistics on all tlabs.
  virtual void accumulate_statistics_all_tlabs();

  // Reinitialize tlabs before resuming mutators.
  virtual void resize_all_tlabs();

 protected:
  // Allocate from the current thread's TLAB, with broken-out slow path.
  inline static HeapWord* allocate_from_tlab(Thread* thread, size_t size);
  static HeapWord* allocate_from_tlab_slow(Thread* thread, size_t size);

  // Allocate an uninitialized block of the given size, or returns NULL if
  // this is impossible.
  inline static HeapWord* common_mem_allocate_noinit(size_t size, bool is_noref, TRAPS);

  // Like allocate_init, but the block returned by a successful allocation
  // is guaranteed initialized to zeros.
  inline static HeapWord* common_mem_allocate_init(size_t size, bool is_noref, TRAPS);

  // Same as common_mem version, except memory is allocated in the permanent area
  // If there is no permanent area, revert to common_mem_allocate_noinit
  inline static HeapWord* common_permanent_mem_allocate_noinit(size_t size, TRAPS);

  // Same as common_mem version, except memory is allocated in the permanent area
  // If there is no permanent area, revert to common_mem_allocate_init
  inline static HeapWord* common_permanent_mem_allocate_init(size_t size, TRAPS);

  // Helper functions for (VM) allocation.
  inline static void post_allocation_setup_common(KlassHandle klass,
                                                  HeapWord* obj, size_t size);
  inline static void post_allocation_setup_no_klass_install(KlassHandle klass,
                                                            HeapWord* objPtr,
                                                            size_t size);

  inline static void post_allocation_setup_obj(KlassHandle klass,
                                               HeapWord* obj, size_t size);

  inline static void post_allocation_setup_array(KlassHandle klass,
                                                 HeapWord* obj, size_t size,
                                                 int length);

  // Clears an allocated object.
  inline static void init_obj(HeapWord* obj, size_t size);

  // Filler object utilities.
  static inline size_t filler_array_hdr_size();
  static inline size_t filler_array_min_size();
  static inline size_t filler_array_max_size();

  DEBUG_ONLY(static void fill_args_check(HeapWord* start, size_t words);)
  DEBUG_ONLY(static void zap_filler_array(HeapWord* start, size_t words, bool zap = true);)

  // Fill with a single array; caller must ensure filler_array_min_size() <=
  // words <= filler_array_max_size().
  static inline void fill_with_array(HeapWord* start, size_t words, bool zap = true);

  // Fill with a single object (either an int array or a java.lang.Object).
  static inline void fill_with_object_impl(HeapWord* start, size_t words, bool zap = true);

  // Verification functions
  virtual void check_for_bad_heap_word_value(HeapWord* addr, size_t size)
    PRODUCT_RETURN;
  virtual void check_for_non_bad_heap_word_value(HeapWord* addr, size_t size)
    PRODUCT_RETURN;
  debug_only(static void check_for_valid_allocation_state();)

 public:
  enum Name {
    Abstract,
    SharedHeap,
    GenCollectedHeap,
    ParallelScavengeHeap,
    G1CollectedHeap
  };

  virtual CollectedHeap::Name kind() const { return CollectedHeap::Abstract; }

  /**
   * Returns JNI error code JNI_ENOMEM if memory could not be allocated,
   * and JNI_OK on success.
   */
  virtual jint initialize() = 0;

  // In many heaps, there will be a need to perform some initialization activities
  // after the Universe is fully formed, but before general heap allocation is allowed.
  // This is the correct place to place such initialization methods.
  virtual void post_initialize() = 0;

  MemRegion reserved_region() const { return _reserved; }
  address base() const { return (address)reserved_region().start(); }

  // Future cleanup here. The following functions should specify bytes or
  // heapwords as part of their signature.
  virtual size_t capacity() const = 0;
  virtual size_t used() const = 0;

  // Return "true" if the part of the heap that allocates Java
  // objects has reached the maximal committed limit that it can
  // reach, without a garbage collection.
  virtual bool is_maximal_no_gc() const = 0;

  virtual size_t permanent_capacity() const = 0;
  virtual size_t permanent_used() const = 0;

  // Support for java.lang.Runtime.maxMemory():  return the maximum amount of
  // memory that the vm could make available for storing 'normal' java objects.
  // This is based on the reserved address space, but should not include space
  // that the vm uses internally for bookkeeping or temporary storage (e.g.,
  // perm gen space or, in the case of the young gen, one of the survivor
  // spaces).
  virtual size_t max_capacity() const = 0;

  // Returns "TRUE" if "p" points into the reserved area of the heap.
  bool is_in_reserved(const void* p) const {
    return _reserved.contains(p);
  }

  bool is_in_reserved_or_null(const void* p) const {
    return p == NULL || is_in_reserved(p);
  }

  // Returns "TRUE" if "p" points to the head of an allocated object in the
  // heap. Since this method can be expensive in general, we restrict its
  // use to assertion checking only.
  virtual bool is_in(const void* p) const = 0;

  bool is_in_or_null(const void* p) const {
    return p == NULL || is_in(p);
  }

  // Let's define some terms: a "closed" subset of a heap is one that
  //
  // 1) contains all currently-allocated objects, and
  //
  // 2) is closed under reference: no object in the closed subset
  //    references one outside the closed subset.
  //
  // Membership in a heap's closed subset is useful for assertions.
  // Clearly, the entire heap is a closed subset, so the default
  // implementation is to use "is_in_reserved".  But this may not be too
  // liberal to perform useful checking.  Also, the "is_in" predicate
  // defines a closed subset, but may be too expensive, since "is_in"
  // verifies that its argument points to an object head.  The
  // "closed_subset" method allows a heap to define an intermediate
  // predicate, allowing more precise checking than "is_in_reserved" at
  // lower cost than "is_in."

  // One important case is a heap composed of disjoint contiguous spaces,
  // such as the Garbage-First collector.  Such heaps have a convenient
  // closed subset consisting of the allocated portions of those
  // contiguous spaces.

  // Return "TRUE" iff the given pointer points into the heap's defined
  // closed subset (which defaults to the entire heap).
  virtual bool is_in_closed_subset(const void* p) const {
    return is_in_reserved(p);
  }

  bool is_in_closed_subset_or_null(const void* p) const {
    return p == NULL || is_in_closed_subset(p);
  }

  // XXX is_permanent() and is_in_permanent() should be better named
  // to distinguish one from the other.

  // Returns "TRUE" if "p" is allocated as "permanent" data.
  // If the heap does not use "permanent" data, returns the same
  // value is_in_reserved() would return.
  // NOTE: this actually returns true if "p" is in reserved space
  // for the space not that it is actually allocated (i.e. in committed
  // space). If you need the more conservative answer use is_permanent().
  virtual bool is_in_permanent(const void *p) const = 0;

  bool is_in_permanent_or_null(const void *p) const {
    return p == NULL || is_in_permanent(p);
  }

  // Returns "TRUE" if "p" is in the committed area of  "permanent" data.
  // If the heap does not use "permanent" data, returns the same
  // value is_in() would return.
  virtual bool is_permanent(const void *p) const = 0;

  bool is_permanent_or_null(const void *p) const {
    return p == NULL || is_permanent(p);
  }

  // An object is scavengable if its location may move during a scavenge.
  // (A scavenge is a GC which is not a full GC.)
  // Currently, this just means it is not perm (and not null).
  // This could change if we rethink what's in perm-gen.
  bool is_scavengable(const void *p) const {
    return !is_in_permanent_or_null(p);
  }

  // Returns "TRUE" if "p" is a method oop in the
  // current heap, with high probability. This predicate
  // is not stable, in general.
  bool is_valid_method(oop p) const;

  void set_gc_cause(GCCause::Cause v) {
     if (UsePerfData) {
       _gc_lastcause = _gc_cause;
       _perf_gc_lastcause->set_value(GCCause::to_string(_gc_lastcause));
       _perf_gc_cause->set_value(GCCause::to_string(v));
     }
    _gc_cause = v;
  }
  GCCause::Cause gc_cause() { return _gc_cause; }

  // Number of threads currently working on GC tasks.
  int n_par_threads() { return _n_par_threads; }

  // May be overridden to set additional parallelism.
  virtual void set_par_threads(int t) { _n_par_threads = t; };

  // Preload classes into the shared portion of the heap, and then dump
  // that data to a file so that it can be loaded directly by another
  // VM (then terminate).
  virtual void preload_and_dump(TRAPS) { ShouldNotReachHere(); }

  // General obj/array allocation facilities.
  inline static oop obj_allocate(KlassHandle klass, int size, TRAPS);
  inline static oop array_allocate(KlassHandle klass, int size, int length, TRAPS);
  inline static oop large_typearray_allocate(KlassHandle klass, int size, int length, TRAPS);

  // Special obj/array allocation facilities.
  // Some heaps may want to manage "permanent" data uniquely. These default
  // to the general routines if the heap does not support such handling.
  inline static oop permanent_obj_allocate(KlassHandle klass, int size, TRAPS);
  // permanent_obj_allocate_no_klass_install() does not do the installation of
  // the klass pointer in the newly created object (as permanent_obj_allocate()
  // above does).  This allows for a delay in the installation of the klass
  // pointer that is needed during the create of klassKlass's.  The
  // method post_allocation_install_obj_klass() is used to install the
  // klass pointer.
  inline static oop permanent_obj_allocate_no_klass_install(KlassHandle klass,
                                                            int size,
                                                            TRAPS);
  inline static void post_allocation_install_obj_klass(KlassHandle klass,
                                                       oop obj,
                                                       int size);
  inline static oop permanent_array_allocate(KlassHandle klass, int size, int length, TRAPS);

  // Raw memory allocation facilities
  // The obj and array allocate methods are covers for these methods.
  // The permanent allocation method should default to mem_allocate if
  // permanent memory isn't supported.
  virtual HeapWord* mem_allocate(size_t size,
                                 bool is_noref,
                                 bool is_tlab,
                                 bool* gc_overhead_limit_was_exceeded) = 0;
  virtual HeapWord* permanent_mem_allocate(size_t size) = 0;

  // The boundary between a "large" and "small" array of primitives, in words.
  virtual size_t large_typearray_limit() = 0;

  // Utilities for turning raw memory into filler objects.
  //
  // min_fill_size() is the smallest region that can be filled.
  // fill_with_objects() can fill arbitrary-sized regions of the heap using
  // multiple objects.  fill_with_object() is for regions known to be smaller
  // than the largest array of integers; it uses a single object to fill the
  // region and has slightly less overhead.
  static size_t min_fill_size() {
    return size_t(align_object_size(oopDesc::header_size()));
  }

  static void fill_with_objects(HeapWord* start, size_t words, bool zap = true);

  static void fill_with_object(HeapWord* start, size_t words, bool zap = true);
  static void fill_with_object(MemRegion region, bool zap = true) {
    fill_with_object(region.start(), region.word_size(), zap);
  }
  static void fill_with_object(HeapWord* start, HeapWord* end, bool zap = true) {
    fill_with_object(start, pointer_delta(end, start), zap);
  }

  // Some heaps may offer a contiguous region for shared non-blocking
  // allocation, via inlined code (by exporting the address of the top and
  // end fields defining the extent of the contiguous allocation region.)

  // This function returns "true" iff the heap supports this kind of
  // allocation.  (Default is "no".)
  virtual bool supports_inline_contig_alloc() const {
    return false;
  }
  // These functions return the addresses of the fields that define the
  // boundaries of the contiguous allocation area.  (These fields should be
  // physically near to one another.)
  virtual HeapWord** top_addr() const {
    guarantee(false, "inline contiguous allocation not supported");
    return NULL;
  }
  virtual HeapWord** end_addr() const {
    guarantee(false, "inline contiguous allocation not supported");
    return NULL;
  }

  // Some heaps may be in an unparseable state at certain times between
  // collections. This may be necessary for efficient implementation of
  // certain allocation-related activities. Calling this function before
  // attempting to parse a heap ensures that the heap is in a parsable
  // state (provided other concurrent activity does not introduce
  // unparsability). It is normally expected, therefore, that this
  // method is invoked with the world stopped.
  // NOTE: if you override this method, make sure you call
  // super::ensure_parsability so that the non-generational
  // part of the work gets done. See implementation of
  // CollectedHeap::ensure_parsability and, for instance,
  // that of GenCollectedHeap::ensure_parsability().
  // The argument "retire_tlabs" controls whether existing TLABs
  // are merely filled or also retired, thus preventing further
  // allocation from them and necessitating allocation of new TLABs.
  virtual void ensure_parsability(bool retire_tlabs);

  // Return an estimate of the maximum allocation that could be performed
  // without triggering any collection or expansion activity.  In a
  // generational collector, for example, this is probably the largest
  // allocation that could be supported (without expansion) in the youngest
  // generation.  It is "unsafe" because no locks are taken; the result
  // should be treated as an approximation, not a guarantee, for use in
  // heuristic resizing decisions.
  virtual size_t unsafe_max_alloc() = 0;

  // Section on thread-local allocation buffers (TLABs)
  // If the heap supports thread-local allocation buffers, it should override
  // the following methods:
  // Returns "true" iff the heap supports thread-local allocation buffers.
  // The default is "no".
  virtual bool supports_tlab_allocation() const {
    return false;
  }
  // The amount of space available for thread-local allocation buffers.
  virtual size_t tlab_capacity(Thread *thr) const {
    guarantee(false, "thread-local allocation buffers not supported");
    return 0;
  }
  // An estimate of the maximum allocation that could be performed
  // for thread-local allocation buffers without triggering any
  // collection or expansion activity.
  virtual size_t unsafe_max_tlab_alloc(Thread *thr) const {
    guarantee(false, "thread-local allocation buffers not supported");
    return 0;
  }

  // Can a compiler initialize a new object without store barriers?
  // This permission only extends from the creation of a new object
  // via a TLAB up to the first subsequent safepoint. If such permission
  // is granted for this heap type, the compiler promises to call
  // defer_store_barrier() below on any slow path allocation of
  // a new object for which such initializing store barriers will
  // have been elided.
  virtual bool can_elide_tlab_store_barriers() const = 0;

  // If a compiler is eliding store barriers for TLAB-allocated objects,
  // there is probably a corresponding slow path which can produce
  // an object allocated anywhere.  The compiler's runtime support
  // promises to call this function on such a slow-path-allocated
  // object before performing initializations that have elided
  // store barriers. Returns new_obj, or maybe a safer copy thereof.
  virtual oop new_store_pre_barrier(JavaThread* thread, oop new_obj);

  // Answers whether an initializing store to a new object currently
  // allocated at the given address doesn't need a store
  // barrier. Returns "true" if it doesn't need an initializing
  // store barrier; answers "false" if it does.
  virtual bool can_elide_initializing_store_barrier(oop new_obj) = 0;

  // If a compiler is eliding store barriers for TLAB-allocated objects,
  // we will be informed of a slow-path allocation by a call
  // to new_store_pre_barrier() above. Such a call precedes the
  // initialization of the object itself, and no post-store-barriers will
  // be issued. Some heap types require that the barrier strictly follows
  // the initializing stores. (This is currently implemented by deferring the
  // barrier until the next slow-path allocation or gc-related safepoint.)
  // This interface answers whether a particular heap type needs the card
  // mark to be thus strictly sequenced after the stores.
  virtual bool card_mark_must_follow_store() const = 0;

  // If the CollectedHeap was asked to defer a store barrier above,
  // this informs it to flush such a deferred store barrier to the
  // remembered set.
  virtual void flush_deferred_store_barrier(JavaThread* thread);

  // Can a compiler elide a store barrier when it writes
  // a permanent oop into the heap?  Applies when the compiler
  // is storing x to the heap, where x->is_perm() is true.
  virtual bool can_elide_permanent_oop_store_barriers() const = 0;

  // Does this heap support heap inspection (+PrintClassHistogram?)
  virtual bool supports_heap_inspection() const = 0;

  // Perform a collection of the heap; intended for use in implementing
  // "System.gc".  This probably implies as full a collection as the
  // "CollectedHeap" supports.
  virtual void collect(GCCause::Cause cause) = 0;

  // This interface assumes that it's being called by the
  // vm thread. It collects the heap assuming that the
  // heap lock is already held and that we are executing in
  // the context of the vm thread.
  virtual void collect_as_vm_thread(GCCause::Cause cause) = 0;

  // Returns the barrier set for this heap
  BarrierSet* barrier_set() { return _barrier_set; }

  // Returns "true" iff there is a stop-world GC in progress.  (I assume
  // that it should answer "false" for the concurrent part of a concurrent
  // collector -- dld).
  bool is_gc_active() const { return _is_gc_active; }

  // Total number of GC collections (started)
  unsigned int total_collections() const { return _total_collections; }
  unsigned int total_full_collections() const { return _total_full_collections;}

  // Increment total number of GC collections (started)
  // Should be protected but used by PSMarkSweep - cleanup for 1.4.2
  void increment_total_collections(bool full = false) {
    _total_collections++;
    if (full) {
      increment_total_full_collections();
    }
  }

  void increment_total_full_collections() { _total_full_collections++; }

  // Return the AdaptiveSizePolicy for the heap.
  virtual AdaptiveSizePolicy* size_policy() = 0;

  // Return the CollectorPolicy for the heap
  virtual CollectorPolicy* collector_policy() const = 0;

  // Iterate over all the ref-containing fields of all objects, calling
  // "cl.do_oop" on each. This includes objects in permanent memory.
  virtual void oop_iterate(OopClosure* cl) = 0;

  // Iterate over all objects, calling "cl.do_object" on each.
  // This includes objects in permanent memory.
  virtual void object_iterate(ObjectClosure* cl) = 0;

  // Similar to object_iterate() except iterates only
  // over live objects.
  virtual void safe_object_iterate(ObjectClosure* cl) = 0;

  // Behaves the same as oop_iterate, except only traverses
  // interior pointers contained in permanent memory. If there
  // is no permanent memory, does nothing.
  virtual void permanent_oop_iterate(OopClosure* cl) = 0;

  // Behaves the same as object_iterate, except only traverses
  // object contained in permanent memory. If there is no
  // permanent memory, does nothing.
  virtual void permanent_object_iterate(ObjectClosure* cl) = 0;

  // NOTE! There is no requirement that a collector implement these
  // functions.
  //
  // A CollectedHeap is divided into a dense sequence of "blocks"; that is,
  // each address in the (reserved) heap is a member of exactly
  // one block.  The defining characteristic of a block is that it is
  // possible to find its size, and thus to progress forward to the next
  // block.  (Blocks may be of different sizes.)  Thus, blocks may
  // represent Java objects, or they might be free blocks in a
  // free-list-based heap (or subheap), as long as the two kinds are
  // distinguishable and the size of each is determinable.

  // Returns the address of the start of the "block" that contains the
  // address "addr".  We say "blocks" instead of "object" since some heaps
  // may not pack objects densely; a chunk may either be an object or a
  // non-object.
  virtual HeapWord* block_start(const void* addr) const = 0;

  // Requires "addr" to be the start of a chunk, and returns its size.
  // "addr + size" is required to be the start of a new chunk, or the end
  // of the active area of the heap.
  virtual size_t block_size(const HeapWord* addr) const = 0;

  // Requires "addr" to be the start of a block, and returns "TRUE" iff
  // the block is an object.
  virtual bool block_is_obj(const HeapWord* addr) const = 0;

  // Returns the longest time (in ms) that has elapsed since the last
  // time that any part of the heap was examined by a garbage collection.
  virtual jlong millis_since_last_gc() = 0;

  // Perform any cleanup actions necessary before allowing a verification.
  virtual void prepare_for_verify() = 0;

  // Generate any dumps preceding or following a full gc
  void pre_full_gc_dump();
  void post_full_gc_dump();

  virtual void print() const = 0;
  virtual void print_on(outputStream* st) const = 0;

  // Print all GC threads (other than the VM thread)
  // used by this heap.
  virtual void print_gc_threads_on(outputStream* st) const = 0;
  void print_gc_threads() { print_gc_threads_on(tty); }
  // Iterator for all GC threads (other than VM thread)
  virtual void gc_threads_do(ThreadClosure* tc) const = 0;

  // Print any relevant tracing info that flags imply.
  // Default implementation does nothing.
  virtual void print_tracing_info() const = 0;

  // Heap verification
  virtual void verify(bool allow_dirty, bool silent, bool option) = 0;

  // Non product verification and debugging.
#ifndef PRODUCT
  // Support for PromotionFailureALot.  Return true if it's time to cause a
  // promotion failure.  The no-argument version uses
  // this->_promotion_failure_alot_count as the counter.
  inline bool promotion_should_fail(volatile size_t* count);
  inline bool promotion_should_fail();

  // Reset the PromotionFailureALot counters.  Should be called at the end of a
  // GC in which promotion failure ocurred.
  inline void reset_promotion_should_fail(volatile size_t* count);
  inline void reset_promotion_should_fail();
#endif  // #ifndef PRODUCT

#ifdef ASSERT
  static int fired_fake_oom() {
    return (CIFireOOMAt > 1 && _fire_out_of_memory_count >= CIFireOOMAt);
  }
#endif

 public:
  // This is a convenience method that is used in cases where
  // the actual number of GC worker threads is not pertinent but
  // only whether there more than 0.  Use of this method helps
  // reduce the occurrence of ParallelGCThreads to uses where the
  // actual number may be germane.
  static bool use_parallel_gc_threads() { return ParallelGCThreads > 0; }
};

// Class to set and reset the GC cause for a CollectedHeap.

class GCCauseSetter : StackObj {
  CollectedHeap* _heap;
  GCCause::Cause _previous_cause;
 public:
  GCCauseSetter(CollectedHeap* heap, GCCause::Cause cause) {
    assert(SafepointSynchronize::is_at_safepoint(),
           "This method manipulates heap state without locking");
    _heap = heap;
    _previous_cause = _heap->gc_cause();
    _heap->set_gc_cause(cause);
  }

  ~GCCauseSetter() {
    assert(SafepointSynchronize::is_at_safepoint(),
          "This method manipulates heap state without locking");
    _heap->set_gc_cause(_previous_cause);
  }
};

#endif // SHARE_VM_GC_INTERFACE_COLLECTEDHEAP_HPP
