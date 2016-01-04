/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_VMGCOPERATIONS_HPP
#define SHARE_VM_GC_SHARED_VMGCOPERATIONS_HPP

#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "memory/heapInspection.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/handles.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/vm_operations.hpp"

// The following class hierarchy represents
// a set of operations (VM_Operation) related to GC.
//
//  VM_Operation
//      VM_GC_Operation
//          VM_GC_HeapInspection
//          VM_GenCollectFull
//          VM_GenCollectFullConcurrent
//          VM_ParallelGCSystemGC
//          VM_CollectForAllocation
//              VM_GenCollectForAllocation
//              VM_ParallelGCFailedAllocation
//  VM_GC_Operation
//   - implements methods common to all classes in the hierarchy:
//     prevents multiple gc requests and manages lock on heap;
//
//  VM_GC_HeapInspection
//   - prints class histogram on SIGBREAK if PrintClassHistogram
//     is specified; and also the attach "inspectheap" operation
//
//  VM_CollectForAllocation
//  VM_GenCollectForAllocation
//  VM_ParallelGCFailedAllocation
//   - this operation is invoked when allocation is failed;
//     operation performs garbage collection and tries to
//     allocate afterwards;
//
//  VM_GenCollectFull
//  VM_GenCollectFullConcurrent
//  VM_ParallelGCSystemGC
//   - these operations preform full collection of heaps of
//     different kind
//

class VM_GC_Operation: public VM_Operation {
 protected:
  BasicLock      _pending_list_basic_lock; // for refs pending list notification (PLL)
  uint           _gc_count_before;         // gc count before acquiring PLL
  uint           _full_gc_count_before;    // full gc count before acquiring PLL
  bool           _full;                    // whether a "full" collection
  bool           _prologue_succeeded;      // whether doit_prologue succeeded
  GCCause::Cause _gc_cause;                // the putative cause for this gc op
  bool           _gc_locked;               // will be set if gc was locked

  virtual bool skip_operation() const;

  // java.lang.ref.Reference support
  void acquire_pending_list_lock();
  void release_and_notify_pending_list_lock();

 public:
  VM_GC_Operation(uint gc_count_before,
                  GCCause::Cause _cause,
                  uint full_gc_count_before = 0,
                  bool full = false) {
    _full = full;
    _prologue_succeeded = false;
    _gc_count_before    = gc_count_before;

    // A subclass constructor will likely overwrite the following
    _gc_cause           = _cause;

    _gc_locked = false;

    _full_gc_count_before = full_gc_count_before;
    // In ParallelScavengeHeap::mem_allocate() collections can be
    // executed within a loop and _all_soft_refs_clear can be set
    // true after they have been cleared by a collection and another
    // collection started so that _all_soft_refs_clear can be true
    // when this collection is started.  Don't assert that
    // _all_soft_refs_clear have to be false here even though
    // mutators have run.  Soft refs will be cleared again in this
    // collection.
  }
  ~VM_GC_Operation();

  // Acquire the reference synchronization lock
  virtual bool doit_prologue();
  // Do notifyAll (if needed) and release held lock
  virtual void doit_epilogue();

  virtual bool allow_nested_vm_operations() const  { return true; }
  bool prologue_succeeded() const { return _prologue_succeeded; }

  void set_gc_locked() { _gc_locked = true; }
  bool gc_locked() const  { return _gc_locked; }

  static void notify_gc_begin(bool full = false);
  static void notify_gc_end();
};


class VM_GC_HeapInspection: public VM_GC_Operation {
 private:
  outputStream* _out;
  bool _full_gc;
  bool _csv_format; // "comma separated values" format for spreadsheet.
  bool _print_help;
  bool _print_class_stats;
  const char* _columns;
 public:
  VM_GC_HeapInspection(outputStream* out, bool request_full_gc) :
    VM_GC_Operation(0 /* total collections,      dummy, ignored */,
                    GCCause::_heap_inspection /* GC Cause */,
                    0 /* total full collections, dummy, ignored */,
                    request_full_gc) {
    _out = out;
    _full_gc = request_full_gc;
    _csv_format = false;
    _print_help = false;
    _print_class_stats = false;
    _columns = NULL;
  }

  ~VM_GC_HeapInspection() {}
  virtual VMOp_Type type() const { return VMOp_GC_HeapInspection; }
  virtual bool skip_operation() const;
  virtual void doit();
  void set_csv_format(bool value) {_csv_format = value;}
  void set_print_help(bool value) {_print_help = value;}
  void set_print_class_stats(bool value) {_print_class_stats = value;}
  void set_columns(const char* value) {_columns = value;}
 protected:
  bool collect();
};

class VM_CollectForAllocation : public VM_GC_Operation {
 protected:
  size_t    _word_size; // Size of object to be allocated (in number of words)
  HeapWord* _result;    // Allocation result (NULL if allocation failed)

 public:
  VM_CollectForAllocation(size_t word_size, uint gc_count_before, GCCause::Cause cause);

  HeapWord* result() const {
    return _result;
  }
};

class VM_GenCollectForAllocation : public VM_CollectForAllocation {
 private:
  bool        _tlab;                       // alloc is of a tlab.
 public:
  VM_GenCollectForAllocation(size_t word_size,
                             bool tlab,
                             uint gc_count_before)
    : VM_CollectForAllocation(word_size, gc_count_before, GCCause::_allocation_failure),
      _tlab(tlab) {
    assert(word_size != 0, "An allocation should always be requested with this operation.");
  }
  ~VM_GenCollectForAllocation()  {}
  virtual VMOp_Type type() const { return VMOp_GenCollectForAllocation; }
  virtual void doit();
};

// VM operation to invoke a collection of the heap as a
// GenCollectedHeap heap.
class VM_GenCollectFull: public VM_GC_Operation {
 private:
  GenCollectedHeap::GenerationType _max_generation;
 public:
  VM_GenCollectFull(uint gc_count_before,
                    uint full_gc_count_before,
                    GCCause::Cause gc_cause,
                    GenCollectedHeap::GenerationType max_generation)
    : VM_GC_Operation(gc_count_before, gc_cause, full_gc_count_before, true /* full */),
      _max_generation(max_generation) { }
  ~VM_GenCollectFull() {}
  virtual VMOp_Type type() const { return VMOp_GenCollectFull; }
  virtual void doit();
};

class VM_CollectForMetadataAllocation: public VM_GC_Operation {
 private:
  MetaWord*                _result;
  size_t                   _size;     // size of object to be allocated
  Metaspace::MetadataType  _mdtype;
  ClassLoaderData*         _loader_data;

 public:
  VM_CollectForMetadataAllocation(ClassLoaderData* loader_data,
                                  size_t size,
                                  Metaspace::MetadataType mdtype,
                                  uint gc_count_before,
                                  uint full_gc_count_before,
                                  GCCause::Cause gc_cause);

  virtual VMOp_Type type() const { return VMOp_CollectForMetadataAllocation; }
  virtual void doit();
  MetaWord* result() const       { return _result; }

  bool initiate_concurrent_GC();
};

class SvcGCMarker : public StackObj {
 private:
  JvmtiGCMarker _jgcm;
 public:
  typedef enum { MINOR, FULL, OTHER } reason_type;

  SvcGCMarker(reason_type reason ) {
    VM_GC_Operation::notify_gc_begin(reason == FULL);
  }

  ~SvcGCMarker() {
    VM_GC_Operation::notify_gc_end();
  }
};

#endif // SHARE_VM_GC_SHARED_VMGCOPERATIONS_HPP
