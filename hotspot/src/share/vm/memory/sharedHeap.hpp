/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_SHAREDHEAP_HPP
#define SHARE_VM_MEMORY_SHAREDHEAP_HPP

#include "gc_interface/collectedHeap.hpp"
#include "memory/generation.hpp"
#include "memory/permGen.hpp"

// A "SharedHeap" is an implementation of a java heap for HotSpot.  This
// is an abstract class: there may be many different kinds of heaps.  This
// class defines the functions that a heap must implement, and contains
// infrastructure common to all heaps.

class PermGen;
class Generation;
class BarrierSet;
class GenRemSet;
class Space;
class SpaceClosure;
class OopClosure;
class OopsInGenClosure;
class ObjectClosure;
class SubTasksDone;
class WorkGang;
class FlexibleWorkGang;
class CollectorPolicy;
class KlassHandle;

class SharedHeap : public CollectedHeap {
  friend class VMStructs;

  friend class VM_GC_Operation;
  friend class VM_CGC_Operation;

private:
  // For claiming strong_roots tasks.
  SubTasksDone* _process_strong_tasks;

protected:
  // There should be only a single instance of "SharedHeap" in a program.
  // This is enforced with the protected constructor below, which will also
  // set the static pointer "_sh" to that instance.
  static SharedHeap* _sh;

  // All heaps contain a "permanent generation."  This is some ways
  // similar to a generation in a generational system, in other ways not.
  // See the "PermGen" class.
  PermGen* _perm_gen;

  // and the Gen Remembered Set, at least one good enough to scan the perm
  // gen.
  GenRemSet* _rem_set;

  // A gc policy, controls global gc resource issues
  CollectorPolicy *_collector_policy;

  // See the discussion below, in the specification of the reader function
  // for this variable.
  int _strong_roots_parity;

  // If we're doing parallel GC, use this gang of threads.
  FlexibleWorkGang* _workers;

  // Number of parallel threads currently working on GC tasks.
  // O indicates use sequential code; 1 means use parallel code even with
  // only one thread, for performance testing purposes.
  int _n_par_threads;

  // Full initialization is done in a concrete subtype's "initialize"
  // function.
  SharedHeap(CollectorPolicy* policy_);

  // Returns true if the calling thread holds the heap lock,
  // or the calling thread is a par gc thread and the heap_lock is held
  // by the vm thread doing a gc operation.
  bool heap_lock_held_for_gc();
  // True if the heap_lock is held by the a non-gc thread invoking a gc
  // operation.
  bool _thread_holds_heap_lock_for_gc;

public:
  static SharedHeap* heap() { return _sh; }

  CollectorPolicy *collector_policy() const { return _collector_policy; }

  void set_barrier_set(BarrierSet* bs);

  // Does operations required after initialization has been done.
  virtual void post_initialize();

  // Initialization of ("weak") reference processing support
  virtual void ref_processing_init();

  void set_perm(PermGen* perm_gen) { _perm_gen = perm_gen; }

  // This function returns the "GenRemSet" object that allows us to scan
  // generations; at least the perm gen, possibly more in a fully
  // generational heap.
  GenRemSet* rem_set() { return _rem_set; }

  // These function return the "permanent" generation, in which
  // reflective objects are allocated and stored.  Two versions, the second
  // of which returns the view of the perm gen as a generation.
  PermGen* perm() const { return _perm_gen; }
  Generation* perm_gen() const { return _perm_gen->as_gen(); }

  // Iteration functions.
  void oop_iterate(OopClosure* cl) = 0;

  // Same as above, restricted to a memory region.
  virtual void oop_iterate(MemRegion mr, OopClosure* cl) = 0;

  // Iterate over all objects allocated since the last collection, calling
  // "cl->do_object" on each.  The heap must have been initialized properly
  // to support this function, or else this call will fail.
  virtual void object_iterate_since_last_GC(ObjectClosure* cl) = 0;

  // Iterate over all spaces in use in the heap, in an undefined order.
  virtual void space_iterate(SpaceClosure* cl) = 0;

  // A SharedHeap will contain some number of spaces.  This finds the
  // space whose reserved area contains the given address, or else returns
  // NULL.
  virtual Space* space_containing(const void* addr) const = 0;

  bool no_gc_in_progress() { return !is_gc_active(); }

  // Some collectors will perform "process_strong_roots" in parallel.
  // Such a call will involve claiming some fine-grained tasks, such as
  // scanning of threads.  To make this process simpler, we provide the
  // "strong_roots_parity()" method.  Collectors that start parallel tasks
  // whose threads invoke "process_strong_roots" must
  // call "change_strong_roots_parity" in sequential code starting such a
  // task.  (This also means that a parallel thread may only call
  // process_strong_roots once.)
  //
  // For calls to process_strong_roots by sequential code, the parity is
  // updated automatically.
  //
  // The idea is that objects representing fine-grained tasks, such as
  // threads, will contain a "parity" field.  A task will is claimed in the
  // current "process_strong_roots" call only if its parity field is the
  // same as the "strong_roots_parity"; task claiming is accomplished by
  // updating the parity field to the strong_roots_parity with a CAS.
  //
  // If the client meats this spec, then strong_roots_parity() will have
  // the following properties:
  //   a) to return a different value than was returned before the last
  //      call to change_strong_roots_parity, and
  //   c) to never return a distinguished value (zero) with which such
  //      task-claiming variables may be initialized, to indicate "never
  //      claimed".
 private:
  void change_strong_roots_parity();
 public:
  int strong_roots_parity() { return _strong_roots_parity; }

  // Call these in sequential code around process_strong_roots.
  // strong_roots_prologue calls change_strong_roots_parity, if
  // parallel tasks are enabled.
  class StrongRootsScope : public MarkingCodeBlobClosure::MarkScope {
  public:
    StrongRootsScope(SharedHeap* outer, bool activate = true);
    ~StrongRootsScope();
  };
  friend class StrongRootsScope;

  enum ScanningOption {
    SO_None                = 0x0,
    SO_AllClasses          = 0x1,
    SO_SystemClasses       = 0x2,
    SO_Symbols             = 0x4,
    SO_Strings             = 0x8,
    SO_CodeCache           = 0x10
  };

  FlexibleWorkGang* workers() const { return _workers; }

  // Sets the number of parallel threads that will be doing tasks
  // (such as process strong roots) subsequently.
  virtual void set_par_threads(int t);

  // Number of threads currently working on GC tasks.
  int n_par_threads() { return _n_par_threads; }

  // Invoke the "do_oop" method the closure "roots" on all root locations.
  // If "collecting_perm_gen" is false, then roots that may only contain
  // references to permGen objects are not scanned.  If true, the
  // "perm_gen" closure is applied to all older-to-younger refs in the
  // permanent generation.  The "so" argument determines which of roots
  // the closure is applied to:
  // "SO_None" does none;
  // "SO_AllClasses" applies the closure to all entries in the SystemDictionary;
  // "SO_SystemClasses" to all the "system" classes and loaders;
  // "SO_Symbols" applies the closure to all entries in SymbolsTable;
  // "SO_Strings" applies the closure to all entries in StringTable;
  // "SO_CodeCache" applies the closure to all elements of the CodeCache.
  void process_strong_roots(bool activate_scope,
                            bool collecting_perm_gen,
                            ScanningOption so,
                            OopClosure* roots,
                            CodeBlobClosure* code_roots,
                            OopsInGenClosure* perm_blk);

  // Apply "blk" to all the weak roots of the system.  These include
  // JNI weak roots, the code cache, system dictionary, symbol table,
  // string table.
  void process_weak_roots(OopClosure* root_closure,
                          CodeBlobClosure* code_roots,
                          OopClosure* non_root_closure);

  // The functions below are helper functions that a subclass of
  // "SharedHeap" can use in the implementation of its virtual
  // functions.

public:

  // Do anything common to GC's.
  virtual void gc_prologue(bool full) = 0;
  virtual void gc_epilogue(bool full) = 0;

  //
  // New methods from CollectedHeap
  //

  size_t permanent_capacity() const {
    assert(perm_gen(), "NULL perm gen");
    return perm_gen()->capacity();
  }

  size_t permanent_used() const {
    assert(perm_gen(), "NULL perm gen");
    return perm_gen()->used();
  }

  bool is_in_permanent(const void *p) const {
    assert(perm_gen(), "NULL perm gen");
    return perm_gen()->is_in_reserved(p);
  }

  // Different from is_in_permanent in that is_in_permanent
  // only checks if p is in the reserved area of the heap
  // and this checks to see if it in the commited area.
  // This is typically used by things like the forte stackwalker
  // during verification of suspicious frame values.
  bool is_permanent(const void *p) const {
    assert(perm_gen(), "NULL perm gen");
    return perm_gen()->is_in(p);
  }

  HeapWord* permanent_mem_allocate(size_t size) {
    assert(perm_gen(), "NULL perm gen");
    return _perm_gen->mem_allocate(size);
  }

  void permanent_oop_iterate(OopClosure* cl) {
    assert(perm_gen(), "NULL perm gen");
    _perm_gen->oop_iterate(cl);
  }

  void permanent_object_iterate(ObjectClosure* cl) {
    assert(perm_gen(), "NULL perm gen");
    _perm_gen->object_iterate(cl);
  }

  // Some utilities.
  void print_size_transition(outputStream* out,
                             size_t bytes_before,
                             size_t bytes_after,
                             size_t capacity);
};

#endif // SHARE_VM_MEMORY_SHAREDHEAP_HPP
