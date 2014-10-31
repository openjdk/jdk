/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_VM_OPERATIONS_G1_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_VM_OPERATIONS_G1_HPP

#include "gc_implementation/g1/g1AllocationContext.hpp"
#include "gc_implementation/shared/vmGCOperations.hpp"

// VM_operations for the G1 collector.
// VM_GC_Operation:
//   - VM_CGC_Operation
//   - VM_G1CollectFull
//   - VM_G1OperationWithAllocRequest
//     - VM_G1CollectForAllocation
//     - VM_G1IncCollectionPause

class VM_G1OperationWithAllocRequest: public VM_GC_Operation {
protected:
  size_t    _word_size;
  HeapWord* _result;
  bool      _pause_succeeded;
  AllocationContext_t _allocation_context;

public:
  VM_G1OperationWithAllocRequest(unsigned int gc_count_before,
                                 size_t       word_size,
                                 GCCause::Cause gc_cause)
    : VM_GC_Operation(gc_count_before, gc_cause),
      _word_size(word_size), _result(NULL), _pause_succeeded(false) { }
  HeapWord* result() { return _result; }
  bool pause_succeeded() { return _pause_succeeded; }
  void set_allocation_context(AllocationContext_t context) { _allocation_context = context; }
  AllocationContext_t  allocation_context() { return _allocation_context; }
};

class VM_G1CollectFull: public VM_GC_Operation {
public:
  VM_G1CollectFull(unsigned int gc_count_before,
                   unsigned int full_gc_count_before,
                   GCCause::Cause cause)
    : VM_GC_Operation(gc_count_before, cause, full_gc_count_before, true) { }
  virtual VMOp_Type type() const { return VMOp_G1CollectFull; }
  virtual void doit();
  virtual const char* name() const {
    return "full garbage-first collection";
  }
};

class VM_G1CollectForAllocation: public VM_G1OperationWithAllocRequest {
public:
  VM_G1CollectForAllocation(unsigned int gc_count_before,
                            size_t       word_size);
  virtual VMOp_Type type() const { return VMOp_G1CollectForAllocation; }
  virtual void doit();
  virtual const char* name() const {
    return "garbage-first collection to satisfy allocation";
  }
};

class VM_G1IncCollectionPause: public VM_G1OperationWithAllocRequest {
private:
  bool         _should_initiate_conc_mark;
  bool         _should_retry_gc;
  double       _target_pause_time_ms;
  unsigned int _old_marking_cycles_completed_before;
public:
  VM_G1IncCollectionPause(unsigned int   gc_count_before,
                          size_t         word_size,
                          bool           should_initiate_conc_mark,
                          double         target_pause_time_ms,
                          GCCause::Cause gc_cause);
  virtual VMOp_Type type() const { return VMOp_G1IncCollectionPause; }
  virtual bool doit_prologue();
  virtual void doit();
  virtual void doit_epilogue();
  virtual const char* name() const {
    return "garbage-first incremental collection pause";
  }
  bool should_retry_gc() const { return _should_retry_gc; }
};

// Concurrent GC stop-the-world operations such as remark and cleanup;
// consider sharing these with CMS's counterparts.
class VM_CGC_Operation: public VM_Operation {
  VoidClosure* _cl;
  const char* _printGCMessage;
  bool _needs_pll;

protected:
  // java.lang.ref.Reference support
  void acquire_pending_list_lock();
  void release_and_notify_pending_list_lock();

public:
  VM_CGC_Operation(VoidClosure* cl, const char *printGCMsg, bool needs_pll)
    : _cl(cl), _printGCMessage(printGCMsg), _needs_pll(needs_pll) { }
  virtual VMOp_Type type() const { return VMOp_CGC_Operation; }
  virtual void doit();
  virtual bool doit_prologue();
  virtual void doit_epilogue();
  virtual const char* name() const {
    return "concurrent gc";
  }
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_VM_OPERATIONS_G1_HPP
