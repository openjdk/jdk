/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_COLLECTEDHEAP_HPP
#define SHARE_VM_GC_SHARED_COLLECTEDHEAP_HPP

#include "gc/shared/gcCause.hpp"
#include "gc/shared/gcWhen.hpp"
#include "memory/allocation.hpp"
#include "runtime/handles.hpp"
#include "runtime/perfData.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/events.hpp"

// A "CollectedHeap" is an implementation of a java heap for HotSpot.  This
// is an abstract class: there may be many different kinds of heaps.  This
// class defines the functions that a heap must implement, and contains
// infrastructure common to all heaps.

class AdaptiveSizePolicy;
class BarrierSet;
class CollectorPolicy;
class GCHeapSummary;
class GCTimer;
class GCTracer;
class MetaspaceSummary;
class Thread;
class ThreadClosure;
class VirtualSpaceSummary;
class nmethod;

class GCMessage : public FormatBuffer<1024> {
 public:
  bool is_before;

 public:
  GCMessage() {}
};

class CollectedHeap;

class GCHeapLog : public EventLogBase<GCMessage> {
 private:
  void log_heap(CollectedHeap* heap, bool before);

 public:
  GCHeapLog() : EventLogBase<GCMessage>("GC Heap History") {}

  void log_heap_before(CollectedHeap* heap) {
    log_heap(heap, true);
  }
  void log_heap_after(CollectedHeap* heap) {
    log_heap(heap, false);
  }
};

//
// CollectedHeap
//   GenCollectedHeap
//   G1CollectedHeap
//   ParallelScavengeHeap
//
class CollectedHeap : public CHeapObj<mtInternal> {
  friend class VMStructs;
  friend class IsGCActiveMark; // Block structured external access to _is_gc_active

 private:
#ifdef ASSERT
  static int       _fire_out_of_memory_count;
#endif

  GCHeapLog* _gc_heap_log;

  // Used in support of ReduceInitialCardMarks; only consulted if COMPILER2
  // or INCLUDE_JVMCI is being used
  bool _defer_initial_card_mark;

  MemRegion _reserved;

 protected:
  BarrierSet* _barrier_set;
  bool _is_gc_active;

  // Used for filler objects (static, but initialized in ctor).
  static size_t _filler_array_max_size;

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

  // Create a new tlab. All TLAB allocations must go through this.
  virtual HeapWord* allocate_new_tlab(size_t size);

  // Accumulate statistics on all tlabs.
  virtual void accumulate_statistics_all_tlabs();

  // Reinitialize tlabs before resuming mutators.
  virtual void resize_all_tlabs();

  // Allocate from the current thread's TLAB, with broken-out slow path.
  inline static HeapWord* allocate_from_tlab(KlassHandle klass, Thread* thread, size_t size);
  static HeapWord* allocate_from_tlab_slow(KlassHandle klass, Thread* thread, size_t size);

  // Allocate an uninitialized block of the given size, or returns NULL if
  // this is impossible.
  inline static HeapWord* common_mem_allocate_noinit(KlassHandle klass, size_t size, TRAPS);

  // Like allocate_init, but the block returned by a successful allocation
  // is guaranteed initialized to zeros.
  inline static HeapWord* common_mem_allocate_init(KlassHandle klass, size_t size, TRAPS);

  // Helper functions for (VM) allocation.
  inline static void post_allocation_setup_common(KlassHandle klass, HeapWord* obj);
  inline static void post_allocation_setup_no_klass_install(KlassHandle klass,
                                                            HeapWord* objPtr);

  inline static void post_allocation_setup_obj(KlassHandle klass, HeapWord* obj, int size);

  inline static void post_allocation_setup_array(KlassHandle klass,
                                                 HeapWord* obj, int length);

  // Clears an allocated object.
  inline static void init_obj(HeapWord* obj, size_t size);

  // Filler object utilities.
  static inline size_t filler_array_hdr_size();
  static inline size_t filler_array_min_size();

  DEBUG_ONLY(static void fill_args_check(HeapWord* start, size_t words);)
  DEBUG_ONLY(static void zap_filler_array(HeapWord* start, size_t words, bool zap = true);)

  // Fill with a single array; caller must ensure filler_array_min_size() <=
  // words <= filler_array_max_size().
  static inline void fill_with_array(HeapWord* start, size_t words, bool zap = true);

  // Fill with a single object (either an int array or a java.lang.Object).
  static inline void fill_with_object_impl(HeapWord* start, size_t words, bool zap = true);

  virtual void trace_heap(GCWhen::Type when, const GCTracer* tracer);

  // Verification functions
  virtual void check_for_bad_heap_word_value(HeapWord* addr, size_t size)
    PRODUCT_RETURN;
  virtual void check_for_non_bad_heap_word_value(HeapWord* addr, size_t size)
    PRODUCT_RETURN;
  debug_only(static void check_for_valid_allocation_state();)

 public:
  enum Name {
    GenCollectedHeap,
    ParallelScavengeHeap,
    G1CollectedHeap
  };

  static inline size_t filler_array_max_size() {
    return _filler_array_max_size;
  }

  virtual Name kind() const = 0;

  virtual const char* name() const = 0;

  /**
   * Returns JNI error code JNI_ENOMEM if memory could not be allocated,
   * and JNI_OK on success.
   */
  virtual jint initialize() = 0;

  // In many heaps, there will be a need to perform some initialization activities
  // after the Universe is fully formed, but before general heap allocation is allowed.
  // This is the correct place to place such initialization methods.
  virtual void post_initialize();

  // Stop any onging concurrent work and prepare for exit.
  virtual void stop() {}

  void initialize_reserved_region(HeapWord *start, HeapWord *end);
  MemRegion reserved_region() const { return _reserved; }
  address base() const { return (address)reserved_region().start(); }

  virtual size_t capacity() const = 0;
  virtual size_t used() const = 0;

  // Return "true" if the part of the heap that allocates Java
  // objects has reached the maximal committed limit that it can
  // reach, without a garbage collection.
  virtual bool is_maximal_no_gc() const = 0;

  // Support for java.lang.Runtime.maxMemory():  return the maximum amount of
  // memory that the vm could make available for storing 'normal' java objects.
  // This is based on the reserved address space, but should not include space
  // that the vm uses internally for bookkeeping or temporary storage
  // (e.g., in the case of the young gen, one of the survivor
  // spaces).
  virtual size_t max_capacity() const = 0;

  // Returns "TRUE" if "p" points into the reserved area of the heap.
  bool is_in_reserved(const void* p) const {
    return _reserved.contains(p);
  }

  bool is_in_reserved_or_null(const void* p) const {
    return p == NULL || is_in_reserved(p);
  }

  // Returns "TRUE" iff "p" points into the committed areas of the heap.
  // This method can be expensive so avoid using it in performance critical
  // code.
  virtual bool is_in(const void* p) const = 0;

  DEBUG_ONLY(bool is_in_or_null(const void* p) const { return p == NULL || is_in(p); })

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

  // An object is scavengable if its location may move during a scavenge.
  // (A scavenge is a GC which is not a full GC.)
  virtual bool is_scavengable(const void *p) = 0;

  void set_gc_cause(GCCause::Cause v) {
     if (UsePerfData) {
       _gc_lastcause = _gc_cause;
       _perf_gc_lastcause->set_value(GCCause::to_string(_gc_lastcause));
       _perf_gc_cause->set_value(GCCause::to_string(v));
     }
    _gc_cause = v;
  }
  GCCause::Cause gc_cause() { return _gc_cause; }

  // General obj/array allocation facilities.
  inline static oop obj_allocate(KlassHandle klass, int size, TRAPS);
  inline static oop array_allocate(KlassHandle klass, int size, int length, TRAPS);
  inline static oop array_allocate_nozero(KlassHandle klass, int size, int length, TRAPS);

  inline static void post_allocation_install_obj_klass(KlassHandle klass,
                                                       oop obj);

  // Raw memory allocation facilities
  // The obj and array allocate methods are covers for these methods.
  // mem_allocate() should never be
  // called to allocate TLABs, only individual objects.
  virtual HeapWord* mem_allocate(size_t size,
                                 bool* gc_overhead_limit_was_exceeded) = 0;

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

  // Return the address "addr" aligned by "alignment_in_bytes" if such
  // an address is below "end".  Return NULL otherwise.
  inline static HeapWord* align_allocation_or_fail(HeapWord* addr,
                                                   HeapWord* end,
                                                   unsigned short alignment_in_bytes);

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

  // Section on thread-local allocation buffers (TLABs)
  // If the heap supports thread-local allocation buffers, it should override
  // the following methods:
  // Returns "true" iff the heap supports thread-local allocation buffers.
  // The default is "no".
  virtual bool supports_tlab_allocation() const = 0;

  // The amount of space available for thread-local allocation buffers.
  virtual size_t tlab_capacity(Thread *thr) const = 0;

  // The amount of used space for thread-local allocation buffers for the given thread.
  virtual size_t tlab_used(Thread *thr) const = 0;

  virtual size_t max_tlab_size() const;

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

  // Perform a collection of the heap; intended for use in implementing
  // "System.gc".  This probably implies as full a collection as the
  // "CollectedHeap" supports.
  virtual void collect(GCCause::Cause cause) = 0;

  // Perform a full collection
  virtual void do_full_collection(bool clear_all_soft_refs) = 0;

  // This interface assumes that it's being called by the
  // vm thread. It collects the heap assuming that the
  // heap lock is already held and that we are executing in
  // the context of the vm thread.
  virtual void collect_as_vm_thread(GCCause::Cause cause);

  // Returns the barrier set for this heap
  BarrierSet* barrier_set() { return _barrier_set; }
  void set_barrier_set(BarrierSet* barrier_set);

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

  // Iterate over all objects, calling "cl.do_object" on each.
  virtual void object_iterate(ObjectClosure* cl) = 0;

  // Similar to object_iterate() except iterates only
  // over live objects.
  virtual void safe_object_iterate(ObjectClosure* cl) = 0;

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
 private:
  void full_gc_dump(GCTimer* timer, const char* when);
 public:
  void pre_full_gc_dump(GCTimer* timer);
  void post_full_gc_dump(GCTimer* timer);

  VirtualSpaceSummary create_heap_space_summary();
  GCHeapSummary create_heap_summary();

  MetaspaceSummary create_metaspace_summary();

  // Print heap information on the given outputStream.
  virtual void print_on(outputStream* st) const = 0;
  // The default behavior is to call print_on() on tty.
  virtual void print() const {
    print_on(tty);
  }
  // Print more detailed heap information on the given
  // outputStream. The default behavior is to call print_on(). It is
  // up to each subclass to override it and add any additional output
  // it needs.
  virtual void print_extended_on(outputStream* st) const {
    print_on(st);
  }

  virtual void print_on_error(outputStream* st) const;

  // Print all GC threads (other than the VM thread)
  // used by this heap.
  virtual void print_gc_threads_on(outputStream* st) const = 0;
  // The default behavior is to call print_gc_threads_on() on tty.
  void print_gc_threads() {
    print_gc_threads_on(tty);
  }
  // Iterator for all GC threads (other than VM thread)
  virtual void gc_threads_do(ThreadClosure* tc) const = 0;

  // Print any relevant tracing info that flags imply.
  // Default implementation does nothing.
  virtual void print_tracing_info() const = 0;

  void print_heap_before_gc();
  void print_heap_after_gc();

  // Registering and unregistering an nmethod (compiled code) with the heap.
  // Override with specific mechanism for each specialized heap type.
  virtual void register_nmethod(nmethod* nm);
  virtual void unregister_nmethod(nmethod* nm);

  void trace_heap_before_gc(const GCTracer* gc_tracer);
  void trace_heap_after_gc(const GCTracer* gc_tracer);

  // Heap verification
  virtual void verify(VerifyOption option) = 0;

  // Non product verification and debugging.
#ifndef PRODUCT
  // Support for PromotionFailureALot.  Return true if it's time to cause a
  // promotion failure.  The no-argument version uses
  // this->_promotion_failure_alot_count as the counter.
  inline bool promotion_should_fail(volatile size_t* count);
  inline bool promotion_should_fail();

  // Reset the PromotionFailureALot counters.  Should be called at the end of a
  // GC in which promotion failure occurred.
  inline void reset_promotion_should_fail(volatile size_t* count);
  inline void reset_promotion_should_fail();
#endif  // #ifndef PRODUCT

#ifdef ASSERT
  static int fired_fake_oom() {
    return (CIFireOOMAt > 1 && _fire_out_of_memory_count >= CIFireOOMAt);
  }
#endif

 public:
  // Copy the current allocation context statistics for the specified contexts.
  // For each context in contexts, set the corresponding entries in the totals
  // and accuracy arrays to the current values held by the statistics.  Each
  // array should be of length len.
  // Returns true if there are more stats available.
  virtual bool copy_allocation_context_stats(const jint* contexts,
                                             jlong* totals,
                                             jbyte* accuracy,
                                             jint len) {
    return false;
  }

  /////////////// Unit tests ///////////////

  NOT_PRODUCT(static void test_is_in();)
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

#endif // SHARE_VM_GC_SHARED_COLLECTEDHEAP_HPP
