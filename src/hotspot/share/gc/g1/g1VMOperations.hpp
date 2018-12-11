/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1VMOPERATIONS_HPP
#define SHARE_VM_GC_G1_G1VMOPERATIONS_HPP

#include "gc/shared/gcId.hpp"
#include "gc/shared/gcVMOperations.hpp"

// VM_operations for the G1 collector.
// VM_GC_Operation:
//   - VM_G1Concurrent
//   - VM_G1CollectForAllocation
//   - VM_G1CollectFull

class VM_G1CollectFull : public VM_GC_Operation {
public:
  VM_G1CollectFull(uint gc_count_before,
                   uint full_gc_count_before,
                   GCCause::Cause cause) :
    VM_GC_Operation(gc_count_before, cause, full_gc_count_before, true) { }
  virtual VMOp_Type type() const { return VMOp_G1CollectFull; }
  virtual void doit();
};

class VM_G1CollectForAllocation : public VM_CollectForAllocation {
  bool         _pause_succeeded;

  bool         _should_initiate_conc_mark;
  bool         _should_retry_gc;
  double       _target_pause_time_ms;
  uint         _old_marking_cycles_completed_before;

public:
  VM_G1CollectForAllocation(size_t         word_size,
                            uint           gc_count_before,
                            GCCause::Cause gc_cause,
                            bool           should_initiate_conc_mark,
                            double         target_pause_time_ms);
  virtual VMOp_Type type() const { return VMOp_G1CollectForAllocation; }
  virtual bool doit_prologue();
  virtual void doit();
  virtual void doit_epilogue();
  bool should_retry_gc() const { return _should_retry_gc; }
  bool pause_succeeded() { return _pause_succeeded; }
};

// Concurrent G1 stop-the-world operations such as remark and cleanup.
class VM_G1Concurrent : public VM_Operation {
  VoidClosure* _cl;
  const char*  _message;
  uint         _gc_id;

public:
  VM_G1Concurrent(VoidClosure* cl, const char* message) :
    _cl(cl), _message(message), _gc_id(GCId::current()) { }
  virtual VMOp_Type type() const { return VMOp_G1Concurrent; }
  virtual void doit();
  virtual bool doit_prologue();
  virtual void doit_epilogue();
};

#endif // SHARE_VM_GC_G1_G1VMOPERATIONS_HPP
