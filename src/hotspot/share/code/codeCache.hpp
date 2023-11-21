/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CODE_CODECACHE_HPP
#define SHARE_CODE_CODECACHE_HPP

#include "code/codeBlob.hpp"
#include "code/nmethod.hpp"
#include "gc/shared/gcBehaviours.hpp"
#include "memory/allocation.hpp"
#include "memory/heap.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/numberSeq.hpp"

// The CodeCache implements the code cache for various pieces of generated
// code, e.g., compiled java methods, runtime stubs, transition frames, etc.
// The entries in the CodeCache are all CodeBlob's.

// -- Implementation --
// The CodeCache consists of one or more CodeHeaps, each of which contains
// CodeBlobs of a specific CodeBlobType. Currently heaps for the following
// types are available:
//  - Non-nmethods: Non-nmethods like Buffers, Adapters and Runtime Stubs
//  - Profiled nmethods: nmethods that are profiled, i.e., those
//    executed at level 2 or 3
//  - Non-Profiled nmethods: nmethods that are not profiled, i.e., those
//    executed at level 1 or 4 and native methods
//  - All: Used for code of all types if code cache segmentation is disabled.
//
// In the rare case of the non-nmethod code heap getting full, non-nmethod code
// will be stored in the non-profiled code heap as a fallback solution.
//
// Depending on the availability of compilers and compilation mode there
// may be fewer heaps. The size of the code heaps depends on the values of
// ReservedCodeCacheSize, NonProfiledCodeHeapSize and ProfiledCodeHeapSize
// (see CodeCache::heap_available(..) and CodeCache::initialize_heaps(..)
// for details).
//
// Code cache segmentation is controlled by the flag SegmentedCodeCache.
// If turned off, all code types are stored in a single code heap. By default
// code cache segmentation is turned on if tiered mode is enabled and
// ReservedCodeCacheSize >= 240 MB.
//
// All methods of the CodeCache accepting a CodeBlobType only apply to
// CodeBlobs of the given type. For example, iteration over the
// CodeBlobs of a specific type can be done by using CodeCache::first_blob(..)
// and CodeCache::next_blob(..) and providing the corresponding CodeBlobType.
//
// IMPORTANT: If you add new CodeHeaps to the code cache or change the
// existing ones, make sure to adapt the dtrace scripts (jhelper.d) for
// Solaris and BSD.

class ExceptionCache;
class KlassDepChange;
class OopClosure;
class ShenandoahParallelCodeHeapIterator;
class NativePostCallNop;
class DeoptimizationScope;

class CodeCache : AllStatic {
  friend class VMStructs;
  friend class JVMCIVMStructs;
  template <class T, class Filter, bool is_compiled_method> friend class CodeBlobIterator;
  friend class WhiteBox;
  friend class CodeCacheLoader;
  friend class ShenandoahParallelCodeHeapIterator;
 private:
  // CodeHeaps of the cache
  static GrowableArray<CodeHeap*>* _heaps;
  static GrowableArray<CodeHeap*>* _compiled_heaps;
  static GrowableArray<CodeHeap*>* _nmethod_heaps;
  static GrowableArray<CodeHeap*>* _allocable_heaps;

  static address _low_bound;                                 // Lower bound of CodeHeap addresses
  static address _high_bound;                                // Upper bound of CodeHeap addresses
  static volatile int _number_of_nmethods_with_dependencies; // Total number of nmethods with dependencies

  static uint8_t           _unloading_cycle;          // Global state for recognizing old nmethods that need to be unloaded
  static uint64_t          _gc_epoch;                 // Global state for tracking when nmethods were found to be on-stack
  static uint64_t          _cold_gc_count;            // Global state for determining how many GCs are needed before an nmethod is cold
  static size_t            _last_unloading_used;
  static double            _last_unloading_time;
  static TruncatedSeq      _unloading_gc_intervals;
  static TruncatedSeq      _unloading_allocation_rates;
  static volatile bool     _unloading_threshold_gc_requested;
  static nmethod* volatile _unlinked_head;

  static ExceptionCache* volatile _exception_cache_purge_list;

  // CodeHeap management
  static void initialize_heaps();                             // Initializes the CodeHeaps
  // Check the code heap sizes set by the user via command line
  static void check_heap_sizes(size_t non_nmethod_size, size_t profiled_size, size_t non_profiled_size, size_t cache_size, bool all_set);
  // Creates a new heap with the given name and size, containing CodeBlobs of the given type
  static void add_heap(ReservedSpace rs, const char* name, CodeBlobType code_blob_type);
  static CodeHeap* get_code_heap_containing(void* p);         // Returns the CodeHeap containing the given pointer, or nullptr
  static CodeHeap* get_code_heap(const void* cb);             // Returns the CodeHeap for the given CodeBlob
  static CodeHeap* get_code_heap(CodeBlobType code_blob_type);         // Returns the CodeHeap for the given CodeBlobType
  // Returns the name of the VM option to set the size of the corresponding CodeHeap
  static const char* get_code_heap_flag_name(CodeBlobType code_blob_type);
  static ReservedCodeSpace reserve_heap_memory(size_t size, size_t rs_ps); // Reserves one continuous chunk of memory for the CodeHeaps

  // Iteration
  static CodeBlob* first_blob(CodeHeap* heap);                // Returns the first CodeBlob on the given CodeHeap
  static CodeBlob* first_blob(CodeBlobType code_blob_type);            // Returns the first CodeBlob of the given type
  static CodeBlob* next_blob(CodeHeap* heap, CodeBlob* cb);   // Returns the next CodeBlob on the given CodeHeap

 private:
  static size_t bytes_allocated_in_freelists();
  static int    allocated_segments();
  static size_t freelists_length();

  // Make private to prevent unsafe calls.  Not all CodeBlob*'s are embedded in a CodeHeap.
  static bool contains(CodeBlob *p) { fatal("don't call me!"); return false; }

 public:
  // Initialization
  static void initialize();
  static size_t page_size(bool aligned = true, size_t min_pages = 1); // Returns the page size used by the CodeCache

  static int code_heap_compare(CodeHeap* const &lhs, CodeHeap* const &rhs);

  static void add_heap(CodeHeap* heap);
  static const GrowableArray<CodeHeap*>* heaps() { return _heaps; }
  static const GrowableArray<CodeHeap*>* compiled_heaps() { return _compiled_heaps; }
  static const GrowableArray<CodeHeap*>* nmethod_heaps() { return _nmethod_heaps; }

  // Allocation/administration
  static CodeBlob* allocate(uint size, CodeBlobType code_blob_type, bool handle_alloc_failure = true, CodeBlobType orig_code_blob_type = CodeBlobType::All); // allocates a new CodeBlob
  static void commit(CodeBlob* cb);                        // called when the allocated CodeBlob has been filled
  static void free(CodeBlob* cb);                          // frees a CodeBlob
  static void free_unused_tail(CodeBlob* cb, size_t used); // frees the unused tail of a CodeBlob (only used by TemplateInterpreter::initialize())
  static bool contains(void *p);                           // returns whether p is included
  static bool contains(nmethod* nm);                       // returns whether nm is included
  static void blobs_do(void f(CodeBlob* cb));              // iterates over all CodeBlobs
  static void blobs_do(CodeBlobClosure* f);                // iterates over all CodeBlobs
  static void nmethods_do(void f(nmethod* nm));            // iterates over all nmethods
  static void metadata_do(MetadataClosure* f);             // iterates over metadata in alive nmethods

  // Lookup
  static CodeBlob* find_blob(void* start);              // Returns the CodeBlob containing the given address
  static CodeBlob* find_blob_fast(void* start);         // Returns the CodeBlob containing the given address
  static CodeBlob* find_blob_and_oopmap(void* start, int& slot);         // Returns the CodeBlob containing the given address
  static int find_oopmap_slot_fast(void* start);        // Returns a fast oopmap slot if there is any; -1 otherwise
  static nmethod*  find_nmethod(void* start);           // Returns the nmethod containing the given address
  static CompiledMethod* find_compiled(void* start);

  static int       blob_count();                        // Returns the total number of CodeBlobs in the cache
  static int       blob_count(CodeBlobType code_blob_type);
  static int       adapter_count();                     // Returns the total number of Adapters in the cache
  static int       adapter_count(CodeBlobType code_blob_type);
  static int       nmethod_count();                     // Returns the total number of nmethods in the cache
  static int       nmethod_count(CodeBlobType code_blob_type);

  // GC support
  static void verify_oops();

  // Helper scope object managing code cache unlinking behavior, i.e. sets and
  // restores the closure that determines which nmethods are going to be removed
  // during the unlinking part of code cache unloading.
  class UnlinkingScope : StackObj {
    ClosureIsUnloadingBehaviour _is_unloading_behaviour;
    IsUnloadingBehaviour*       _saved_behaviour;

  public:
    UnlinkingScope(BoolObjectClosure* is_alive);
    ~UnlinkingScope();
  };

  // Code cache unloading heuristics
  static uint64_t cold_gc_count();
  static void update_cold_gc_count();
  static void gc_on_allocation();

  // The GC epoch and marking_cycle code below is there to support sweeping
  // nmethods in loom stack chunks.
  static uint64_t gc_epoch();
  static bool is_gc_marking_cycle_active();
  static uint64_t previous_completed_gc_marking_cycle();
  static void on_gc_marking_cycle_start();
  static void on_gc_marking_cycle_finish();
  // Arm nmethods so that special actions are taken (nmethod_entry_barrier) for
  // on-stack nmethods. It's used in two places:
  // 1. Used before the start of concurrent marking so that oops inside
  //    on-stack nmethods are visited.
  // 2. Used at the end of (stw/concurrent) marking so that nmethod::_gc_epoch
  //    is up-to-date, which provides more accurate estimate of
  //    nmethod::is_cold.
  static void arm_all_nmethods();

  static void flush_unlinked_nmethods();
  static void register_unlinked(nmethod* nm);
  static void do_unloading(bool unloading_occurred);
  static uint8_t unloading_cycle() { return _unloading_cycle; }

  static void increment_unloading_cycle();

  static void release_exception_cache(ExceptionCache* entry);
  static void purge_exception_caches();

  // Printing/debugging
  static void print();                           // prints summary
  static void print_internals();
  static void print_memory_overhead();
  static void verify();                          // verifies the code cache
  static void print_trace(const char* event, CodeBlob* cb, uint size = 0) PRODUCT_RETURN;
  static void print_summary(outputStream* st, bool detailed = true); // Prints a summary of the code cache usage
  static void log_state(outputStream* st);
  LINUX_ONLY(static void write_perf_map();)
  static const char* get_code_heap_name(CodeBlobType code_blob_type)  { return (heap_available(code_blob_type) ? get_code_heap(code_blob_type)->name() : "Unused"); }
  static void report_codemem_full(CodeBlobType code_blob_type, bool print);

  // Dcmd (Diagnostic commands)
  static void print_codelist(outputStream* st);
  static void print_layout(outputStream* st);

  // The full limits of the codeCache
  static address low_bound()                          { return _low_bound; }
  static address low_bound(CodeBlobType code_blob_type);
  static address high_bound()                         { return _high_bound; }
  static address high_bound(CodeBlobType code_blob_type);

  // Profiling
  static size_t capacity();
  static size_t unallocated_capacity(CodeBlobType code_blob_type);
  static size_t unallocated_capacity();
  static size_t max_capacity();

  static double reverse_free_ratio();

  static size_t max_distance_to_non_nmethod();
  static bool is_non_nmethod(address addr);

  static void clear_inline_caches();                  // clear all inline caches
  static void cleanup_inline_caches_whitebox();       // clean bad nmethods from inline caches

  // Returns true if an own CodeHeap for the given CodeBlobType is available
  static bool heap_available(CodeBlobType code_blob_type);

  // Returns the CodeBlobType for the given CompiledMethod
  static CodeBlobType get_code_blob_type(CompiledMethod* cm) {
    return get_code_heap(cm)->code_blob_type();
  }

  static bool code_blob_type_accepts_compiled(CodeBlobType code_blob_type) {
    bool result = code_blob_type == CodeBlobType::All || code_blob_type <= CodeBlobType::MethodProfiled;
    return result;
  }

  static bool code_blob_type_accepts_nmethod(CodeBlobType type) {
    return type == CodeBlobType::All || type <= CodeBlobType::MethodProfiled;
  }

  static bool code_blob_type_accepts_allocable(CodeBlobType type) {
    return type <= CodeBlobType::All;
  }


  // Returns the CodeBlobType for the given compilation level
  static CodeBlobType get_code_blob_type(int comp_level) {
    if (comp_level == CompLevel_none ||
        comp_level == CompLevel_simple ||
        comp_level == CompLevel_full_optimization) {
      // Non profiled methods
      return CodeBlobType::MethodNonProfiled;
    } else if (comp_level == CompLevel_limited_profile ||
               comp_level == CompLevel_full_profile) {
      // Profiled methods
      return CodeBlobType::MethodProfiled;
    }
    ShouldNotReachHere();
    return static_cast<CodeBlobType>(0);
  }

  static void verify_clean_inline_caches();
  static void verify_icholder_relocations();

  // Deoptimization
 private:
  static void mark_for_deoptimization(DeoptimizationScope* deopt_scope, KlassDepChange& changes);

 public:
  static void mark_all_nmethods_for_deoptimization(DeoptimizationScope* deopt_scope);
  static void mark_for_deoptimization(DeoptimizationScope* deopt_scope, Method* dependee);
  static void make_marked_nmethods_deoptimized();

  // Marks dependents during classloading
  static void mark_dependents_on(DeoptimizationScope* deopt_scope, InstanceKlass* dependee);

  // RedefineClasses support
  // Marks in case of evolution
  static void mark_dependents_for_evol_deoptimization(DeoptimizationScope* deopt_scope);
  static void mark_all_nmethods_for_evol_deoptimization(DeoptimizationScope* deopt_scope);
  static void old_nmethods_do(MetadataClosure* f) NOT_JVMTI_RETURN;
  static void unregister_old_nmethod(CompiledMethod* c) NOT_JVMTI_RETURN;

  // Support for fullspeed debugging
  static void mark_dependents_on_method_for_breakpoint(const methodHandle& dependee);

  // tells if there are nmethods with dependencies
  static bool has_nmethods_with_dependencies();

  static int get_codemem_full_count(CodeBlobType code_blob_type) {
    CodeHeap* heap = get_code_heap(code_blob_type);
    return (heap != nullptr) ? heap->full_count() : 0;
  }

  // CodeHeap State Analytics.
  // interface methods for CodeHeap printing, called by CompileBroker
  static void aggregate(outputStream *out, size_t granularity);
  static void discard(outputStream *out);
  static void print_usedSpace(outputStream *out);
  static void print_freeSpace(outputStream *out);
  static void print_count(outputStream *out);
  static void print_space(outputStream *out);
  static void print_age(outputStream *out);
  static void print_names(outputStream *out);
};


// Iterator to iterate over code blobs in the CodeCache.
// The relaxed iterators only hold the CodeCache_lock across next calls
template <class T, class Filter, bool is_relaxed> class CodeBlobIterator : public StackObj {
 public:
  enum LivenessFilter { all_blobs, only_not_unloading };

 private:
  CodeBlob* _code_blob;   // Current CodeBlob
  GrowableArrayIterator<CodeHeap*> _heap;
  GrowableArrayIterator<CodeHeap*> _end;
  bool _only_not_unloading;

  void initialize_iteration(T* nm) {
  }

  bool next_impl() {
    for (;;) {
      // Walk through heaps as required
      if (!next_blob()) {
        if (_heap == _end) {
          return false;
        }
        ++_heap;
        continue;
      }

      // Filter is_unloading as required
      if (_only_not_unloading) {
        CompiledMethod* cm = _code_blob->as_compiled_method_or_null();
        if (cm != nullptr && cm->is_unloading()) {
          continue;
        }
      }

      return true;
    }
  }

 public:
  CodeBlobIterator(LivenessFilter filter, T* nm = nullptr)
    : _only_not_unloading(filter == only_not_unloading)
  {
    if (Filter::heaps() == nullptr) {
      // The iterator is supposed to shortcut since we have
      // _heap == _end, but make sure we do not have garbage
      // in other fields as well.
      _code_blob = nullptr;
      return;
    }
    _heap = Filter::heaps()->begin();
    _end = Filter::heaps()->end();
    // If set to nullptr, initialized by first call to next()
    _code_blob = nm;
    if (nm != nullptr) {
      while(!(*_heap)->contains(_code_blob)) {
        ++_heap;
      }
      assert((*_heap)->contains(_code_blob), "match not found");
    }
  }

  // Advance iterator to next blob
  bool next() {
    if (is_relaxed) {
      MutexLocker ml(CodeCache_lock, Mutex::_no_safepoint_check_flag);
      return next_impl();
    } else {
      assert_locked_or_safepoint(CodeCache_lock);
      return next_impl();
    }
  }

  bool end()  const { return _code_blob == nullptr; }
  T* method() const { return (T*)_code_blob; }

private:

  // Advance iterator to the next blob in the current code heap
  bool next_blob() {
    if (_heap == _end) {
      return false;
    }
    CodeHeap *heap = *_heap;
    // Get first method CodeBlob
    if (_code_blob == nullptr) {
      _code_blob = CodeCache::first_blob(heap);
      if (_code_blob == nullptr) {
        return false;
      } else if (Filter::apply(_code_blob)) {
        return true;
      }
    }
    // Search for next method CodeBlob
    _code_blob = CodeCache::next_blob(heap, _code_blob);
    while (_code_blob != nullptr && !Filter::apply(_code_blob)) {
      _code_blob = CodeCache::next_blob(heap, _code_blob);
    }
    return _code_blob != nullptr;
  }
};

struct CompiledMethodFilter {
  static bool apply(CodeBlob* cb) { return cb->is_compiled(); }
  static const GrowableArray<CodeHeap*>* heaps() { return CodeCache::compiled_heaps(); }
};


struct NMethodFilter {
  static bool apply(CodeBlob* cb) { return cb->is_nmethod(); }
  static const GrowableArray<CodeHeap*>* heaps() { return CodeCache::nmethod_heaps(); }
};

struct AllCodeBlobsFilter {
  static bool apply(CodeBlob* cb) { return true; }
  static const GrowableArray<CodeHeap*>* heaps() { return CodeCache::heaps(); }
};

typedef CodeBlobIterator<CompiledMethod, CompiledMethodFilter, false /* is_relaxed */> CompiledMethodIterator;
typedef CodeBlobIterator<CompiledMethod, CompiledMethodFilter, true /* is_relaxed */> RelaxedCompiledMethodIterator;
typedef CodeBlobIterator<nmethod, NMethodFilter, false /* is_relaxed */> NMethodIterator;
typedef CodeBlobIterator<CodeBlob, AllCodeBlobsFilter, false /* is_relaxed */> AllCodeBlobsIterator;

#endif // SHARE_CODE_CODECACHE_HPP
