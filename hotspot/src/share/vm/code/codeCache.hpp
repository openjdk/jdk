/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CODE_CODECACHE_HPP
#define SHARE_VM_CODE_CODECACHE_HPP

#include "code/codeBlob.hpp"
#include "code/nmethod.hpp"
#include "memory/allocation.hpp"
#include "memory/heap.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/mutexLocker.hpp"

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
// Depending on the availability of compilers and TieredCompilation there
// may be fewer heaps. The size of the code heaps depends on the values of
// ReservedCodeCacheSize, NonProfiledCodeHeapSize and ProfiledCodeHeapSize
// (see CodeCache::heap_available(..) and CodeCache::initialize_heaps(..)
// for details).
//
// Code cache segmentation is controlled by the flag SegmentedCodeCache.
// If turned off, all code types are stored in a single code heap. By default
// code cache segmentation is turned on if TieredCompilation is enabled and
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

class OopClosure;
class DepChange;

class CodeCache : AllStatic {
  friend class VMStructs;
  friend class JVMCIVMStructs;
  friend class NMethodIterator;
  friend class WhiteBox;
  friend class CodeCacheLoader;
 private:
  // CodeHeaps of the cache
  static GrowableArray<CodeHeap*>* _heaps;

  static address _low_bound;                            // Lower bound of CodeHeap addresses
  static address _high_bound;                           // Upper bound of CodeHeap addresses
  static int _number_of_nmethods_with_dependencies;     // Total number of nmethods with dependencies
  static bool _needs_cache_clean;                       // True if inline caches of the nmethods needs to be flushed
  static nmethod* _scavenge_root_nmethods;              // linked via nm->scavenge_root_link()

  static void mark_scavenge_root_nmethods() PRODUCT_RETURN;
  static void verify_perm_nmethods(CodeBlobClosure* f_or_null) PRODUCT_RETURN;

  // CodeHeap management
  static void initialize_heaps();                             // Initializes the CodeHeaps
  // Check the code heap sizes set by the user via command line
  static void check_heap_sizes(size_t non_nmethod_size, size_t profiled_size, size_t non_profiled_size, size_t cache_size, bool all_set);
  // Creates a new heap with the given name and size, containing CodeBlobs of the given type
  static void add_heap(ReservedSpace rs, const char* name, int code_blob_type);
  static CodeHeap* get_code_heap(const CodeBlob* cb);         // Returns the CodeHeap for the given CodeBlob
  static CodeHeap* get_code_heap(int code_blob_type);         // Returns the CodeHeap for the given CodeBlobType
  // Returns the name of the VM option to set the size of the corresponding CodeHeap
  static const char* get_code_heap_flag_name(int code_blob_type);
  static size_t heap_alignment();                             // Returns the alignment of the CodeHeaps in bytes
  static ReservedCodeSpace reserve_heap_memory(size_t size);  // Reserves one continuous chunk of memory for the CodeHeaps

  // Iteration
  static CodeBlob* first_blob(CodeHeap* heap);                // Returns the first CodeBlob on the given CodeHeap
  static CodeBlob* first_blob(int code_blob_type);            // Returns the first CodeBlob of the given type
  static CodeBlob* next_blob(CodeHeap* heap, CodeBlob* cb);   // Returns the first alive CodeBlob on the given CodeHeap
  static CodeBlob* next_blob(CodeBlob* cb);                   // Returns the next CodeBlob of the given type succeeding the given CodeBlob

  static size_t bytes_allocated_in_freelists();
  static int    allocated_segments();
  static size_t freelists_length();

 public:
  // Initialization
  static void initialize();

  // Allocation/administration
  static CodeBlob* allocate(int size, int code_blob_type, bool strict = false); // allocates a new CodeBlob
  static void commit(CodeBlob* cb);                        // called when the allocated CodeBlob has been filled
  static int  alignment_unit();                            // guaranteed alignment of all CodeBlobs
  static int  alignment_offset();                          // guaranteed offset of first CodeBlob byte within alignment unit (i.e., allocation header)
  static void free(CodeBlob* cb);                          // frees a CodeBlob
  static bool contains(void *p);                           // returns whether p is included
  static void blobs_do(void f(CodeBlob* cb));              // iterates over all CodeBlobs
  static void blobs_do(CodeBlobClosure* f);                // iterates over all CodeBlobs
  static void nmethods_do(void f(nmethod* nm));            // iterates over all nmethods
  static void alive_nmethods_do(void f(nmethod* nm));      // iterates over all alive nmethods

  // Lookup
  static CodeBlob* find_blob(void* start);              // Returns the CodeBlob containing the given address
  static CodeBlob* find_blob_unsafe(void* start);       // Same as find_blob but does not fail if looking up a zombie method
  static nmethod*  find_nmethod(void* start);           // Returns the nmethod containing the given address

  static int       blob_count();                        // Returns the total number of CodeBlobs in the cache
  static int       blob_count(int code_blob_type);
  static int       adapter_count();                     // Returns the total number of Adapters in the cache
  static int       adapter_count(int code_blob_type);
  static int       nmethod_count();                     // Returns the total number of nmethods in the cache
  static int       nmethod_count(int code_blob_type);

  // GC support
  static void gc_epilogue();
  static void gc_prologue();
  static void verify_oops();
  // If "unloading_occurred" is true, then unloads (i.e., breaks root links
  // to) any unmarked codeBlobs in the cache.  Sets "marked_for_unloading"
  // to "true" iff some code got unloaded.
  static void do_unloading(BoolObjectClosure* is_alive, bool unloading_occurred);
  static void asserted_non_scavengable_nmethods_do(CodeBlobClosure* f = NULL) PRODUCT_RETURN;
  static void scavenge_root_nmethods_do(CodeBlobClosure* f);

  static nmethod* scavenge_root_nmethods()            { return _scavenge_root_nmethods; }
  static void set_scavenge_root_nmethods(nmethod* nm) { _scavenge_root_nmethods = nm; }
  static void add_scavenge_root_nmethod(nmethod* nm);
  static void drop_scavenge_root_nmethod(nmethod* nm);
  static void prune_scavenge_root_nmethods();

  // Printing/debugging
  static void print();                           // prints summary
  static void print_internals();
  static void print_memory_overhead();
  static void verify();                          // verifies the code cache
  static void print_trace(const char* event, CodeBlob* cb, int size = 0) PRODUCT_RETURN;
  static void print_summary(outputStream* st, bool detailed = true); // Prints a summary of the code cache usage
  static void log_state(outputStream* st);
  static const char* get_code_heap_name(int code_blob_type)  { return (heap_available(code_blob_type) ? get_code_heap(code_blob_type)->name() : "Unused"); }
  static void report_codemem_full(int code_blob_type, bool print);

  // Dcmd (Diagnostic commands)
  static void print_codelist(outputStream* st);
  static void print_layout(outputStream* st);

  // The full limits of the codeCache
  static address low_bound()                          { return _low_bound; }
  static address low_bound(int code_blob_type);
  static address high_bound()                         { return _high_bound; }
  static address high_bound(int code_blob_type);

  // Profiling
  static size_t capacity();
  static size_t unallocated_capacity(int code_blob_type);
  static size_t unallocated_capacity();
  static size_t max_capacity();

  static double reverse_free_ratio(int code_blob_type);

  static bool needs_cache_clean()                     { return _needs_cache_clean; }
  static void set_needs_cache_clean(bool v)           { _needs_cache_clean = v;    }
  static void clear_inline_caches();                  // clear all inline caches

  // Returns true if an own CodeHeap for the given CodeBlobType is available
  static bool heap_available(int code_blob_type);

  // Returns the CodeBlobType for the given nmethod
  static int get_code_blob_type(nmethod* nm) {
    return get_code_heap(nm)->code_blob_type();
  }

  // Returns the CodeBlobType for the given compilation level
  static int get_code_blob_type(int comp_level) {
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
    return 0;
  }

  static void verify_clean_inline_caches();
  static void verify_icholder_relocations();

  // Deoptimization
 private:
  static int  mark_for_deoptimization(DepChange& changes);
#ifdef HOTSWAP
  static int  mark_for_evol_deoptimization(instanceKlassHandle dependee);
#endif // HOTSWAP

 public:
  static void mark_all_nmethods_for_deoptimization();
  static int  mark_for_deoptimization(Method* dependee);
  static void make_marked_nmethods_not_entrant();

  // Flushing and deoptimization
  static void flush_dependents_on(instanceKlassHandle dependee);
#ifdef HOTSWAP
  // Flushing and deoptimization in case of evolution
  static void flush_evol_dependents_on(instanceKlassHandle dependee);
#endif // HOTSWAP
  // Support for fullspeed debugging
  static void flush_dependents_on_method(methodHandle dependee);

  // tells how many nmethods have dependencies
  static int number_of_nmethods_with_dependencies();

  static int get_codemem_full_count(int code_blob_type) {
    CodeHeap* heap = get_code_heap(code_blob_type);
    return (heap != NULL) ? heap->full_count() : 0;
  }
};


// Iterator to iterate over nmethods in the CodeCache.
class NMethodIterator : public StackObj {
 private:
  CodeBlob* _code_blob;   // Current CodeBlob
  int _code_blob_type;    // Refers to current CodeHeap

 public:
  NMethodIterator() {
    initialize(NULL); // Set to NULL, initialized by first call to next()
  }

  NMethodIterator(nmethod* nm) {
    initialize(nm);
  }

  // Advance iterator to next nmethod
  bool next() {
    assert_locked_or_safepoint(CodeCache_lock);
    assert(_code_blob_type < CodeBlobType::NumTypes, "end reached");

    bool result = next_nmethod();
    while (!result && (_code_blob_type < CodeBlobType::MethodProfiled)) {
      // Advance to next code heap if segmented code cache
      _code_blob_type++;
      result = next_nmethod();
    }
    return result;
  }

  // Advance iterator to next alive nmethod
  bool next_alive() {
    bool result = next();
    while(result && !_code_blob->is_alive()) {
      result = next();
    }
    return result;
  }

  bool end()        const   { return _code_blob == NULL; }
  nmethod* method() const   { return (nmethod*)_code_blob; }

private:
  // Initialize iterator to given nmethod
  void initialize(nmethod* nm) {
    _code_blob = (CodeBlob*)nm;
    if (!SegmentedCodeCache) {
      // Iterate over all CodeBlobs
      _code_blob_type = CodeBlobType::All;
    } else if (nm != NULL) {
      _code_blob_type = CodeCache::get_code_blob_type(nm);
    } else {
      // Only iterate over method code heaps, starting with non-profiled
      _code_blob_type = CodeBlobType::MethodNonProfiled;
    }
  }

  // Advance iterator to the next nmethod in the current code heap
  bool next_nmethod() {
    // Get first method CodeBlob
    if (_code_blob == NULL) {
      _code_blob = CodeCache::first_blob(_code_blob_type);
      if (_code_blob == NULL) {
        return false;
      } else if (_code_blob->is_nmethod()) {
        return true;
      }
    }
    // Search for next method CodeBlob
    _code_blob = CodeCache::next_blob(_code_blob);
    while (_code_blob != NULL && !_code_blob->is_nmethod()) {
      _code_blob = CodeCache::next_blob(_code_blob);
    }
    return _code_blob != NULL;
  }
};

#endif // SHARE_VM_CODE_CODECACHE_HPP
