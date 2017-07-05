/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_CONCURRENTMARKSWEEP_VMCMSOPERATIONS_HPP
#define SHARE_VM_GC_IMPLEMENTATION_CONCURRENTMARKSWEEP_VMCMSOPERATIONS_HPP

#include "gc_implementation/concurrentMarkSweep/concurrentMarkSweepGeneration.hpp"
#include "gc_implementation/shared/vmGCOperations.hpp"
#include "gc_interface/gcCause.hpp"
#include "runtime/vm_operations.hpp"

// The VM_CMS_Operation is slightly different from
// a VM_GC_Operation -- and would not have subclassed easily
// to VM_GC_Operation without several changes to VM_GC_Operation.
// To minimize the changes, we have replicated some of the VM_GC_Operation
// functionality here. We will consolidate that back by doing subclassing
// as appropriate in Dolphin.
//
//  VM_Operation
//    VM_CMS_Operation
//    - implements the common portion of work done in support
//      of CMS' stop-world phases (initial mark and remark).
//
//      VM_CMS_Initial_Mark
//      VM_CMS_Final_Mark
//

// Forward decl.
class CMSCollector;

class VM_CMS_Operation: public VM_Operation {
 protected:
  CMSCollector*  _collector;                 // associated collector
  bool           _prologue_succeeded;     // whether doit_prologue succeeded

  bool lost_race() const;

  // java.lang.ref.Reference support
  void acquire_pending_list_lock();
  void release_and_notify_pending_list_lock();

 public:
  VM_CMS_Operation(CMSCollector* collector):
    _collector(collector),
    _prologue_succeeded(false) {}
  ~VM_CMS_Operation() {}

  // The legal collector state for executing this CMS op.
  virtual const CMSCollector::CollectorState legal_state() const = 0;

  // Whether the pending list lock needs to be held
  virtual const bool needs_pll() const = 0;

  // Execute operations in the context of the caller,
  // prior to execution of the vm operation itself.
  virtual bool doit_prologue();
  // Execute operations in the context of the caller,
  // following completion of the vm operation.
  virtual void doit_epilogue();

  virtual bool evaluate_at_safepoint() const { return true; }
  virtual bool is_cheap_allocated() const { return false; }
  virtual bool allow_nested_vm_operations() const  { return false; }
  bool prologue_succeeded() const { return _prologue_succeeded; }

  void verify_before_gc();
  void verify_after_gc();
};


// VM_CMS_Operation for the initial marking phase of CMS.
class VM_CMS_Initial_Mark: public VM_CMS_Operation {
 public:
  VM_CMS_Initial_Mark(CMSCollector* _collector) :
    VM_CMS_Operation(_collector) {}

  virtual VMOp_Type type() const { return VMOp_CMS_Initial_Mark; }
  virtual void doit();

  virtual const CMSCollector::CollectorState legal_state() const {
    return CMSCollector::InitialMarking;
  }

  virtual const bool needs_pll() const {
    return false;
  }
};

// VM_CMS_Operation for the final remark phase of CMS.
class VM_CMS_Final_Remark: public VM_CMS_Operation {
 public:
  VM_CMS_Final_Remark(CMSCollector* _collector) :
    VM_CMS_Operation(_collector) {}
  virtual VMOp_Type type() const { return VMOp_CMS_Final_Remark; }
  virtual void doit();

  virtual const CMSCollector::CollectorState legal_state() const {
    return CMSCollector::FinalMarking;
  }

  virtual const bool needs_pll() const {
    return true;
  }
};


// VM operation to invoke a concurrent collection of the heap as a
// GenCollectedHeap heap.
class VM_GenCollectFullConcurrent: public VM_GC_Operation {
 public:
  VM_GenCollectFullConcurrent(unsigned int gc_count_before,
                              unsigned int full_gc_count_before,
                              GCCause::Cause gc_cause)
    : VM_GC_Operation(gc_count_before, full_gc_count_before, true /* full */) {
    _gc_cause = gc_cause;
    assert(FullGCCount_lock != NULL, "Error");
    assert(UseAsyncConcMarkSweepGC, "Else will hang caller");
  }
  ~VM_GenCollectFullConcurrent() {}
  virtual VMOp_Type type() const { return VMOp_GenCollectFullConcurrent; }
  virtual void doit();
  virtual void doit_epilogue();
  virtual bool is_cheap_allocated() const { return false; }
  virtual bool evaluate_at_safepoint() const;
};

#endif // SHARE_VM_GC_IMPLEMENTATION_CONCURRENTMARKSWEEP_VMCMSOPERATIONS_HPP
