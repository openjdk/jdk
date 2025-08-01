/*
 * Copyright (c) 2001, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1VMOPERATIONS_HPP
#define SHARE_GC_G1_G1VMOPERATIONS_HPP

#include "gc/shared/gcId.hpp"
#include "gc/shared/gcVMOperations.hpp"

// VM_operations for the G1 collector.

class VM_G1CollectFull : public VM_GC_Collect_Operation {
protected:
  bool skip_operation() const override;

public:
  VM_G1CollectFull(uint gc_count_before,
                   uint full_gc_count_before,
                   GCCause::Cause cause) :
    VM_GC_Collect_Operation(gc_count_before, cause, full_gc_count_before, true) { }
  VMOp_Type type() const override { return VMOp_G1CollectFull; }
  void doit() override;
};

class VM_G1TryInitiateConcMark : public VM_GC_Collect_Operation {
  bool _transient_failure;
  bool _mark_in_progress;
  bool _cycle_already_in_progress;
  bool _whitebox_attached;
  bool _terminating;
  // The concurrent start pause may be cancelled for some reasons. Keep track of
  // this.
  bool _gc_succeeded;

public:
  VM_G1TryInitiateConcMark(uint gc_count_before,
                           GCCause::Cause gc_cause);
  virtual VMOp_Type type() const { return VMOp_G1TryInitiateConcMark; }
  virtual bool doit_prologue();
  virtual void doit();
  bool transient_failure() const { return _transient_failure; }
  bool mark_in_progress() const { return _mark_in_progress; }
  bool cycle_already_in_progress() const { return _cycle_already_in_progress; }
  bool whitebox_attached() const { return _whitebox_attached; }
  bool terminating() const { return _terminating; }
  bool gc_succeeded() const { return _gc_succeeded && VM_GC_Operation::gc_succeeded(); }
};

class VM_G1CollectForAllocation : public VM_CollectForAllocation {

public:
  VM_G1CollectForAllocation(size_t         word_size,
                            uint           gc_count_before,
                            GCCause::Cause gc_cause);
  virtual VMOp_Type type() const { return VMOp_G1CollectForAllocation; }
  virtual void doit();
};

// Concurrent G1 stop-the-world operations such as remark and cleanup.
class VM_G1PauseConcurrent : public VM_Operation {
  uint         _gc_id;
  const char*  _message;

protected:
  VM_G1PauseConcurrent(const char* message) :
    _gc_id(GCId::current()), _message(message) { }
  virtual void work() = 0;

public:
  bool doit_prologue() override;
  void doit_epilogue() override;
  void doit() override;
  bool is_gc_operation() const override { return true; }
};

class VM_G1PauseRemark : public VM_G1PauseConcurrent {
public:
  VM_G1PauseRemark() : VM_G1PauseConcurrent("Pause Remark") { }
  VMOp_Type type() const override { return VMOp_G1PauseRemark; }
  void work() override;
};

class VM_G1PauseCleanup : public VM_G1PauseConcurrent {
public:
  VM_G1PauseCleanup() : VM_G1PauseConcurrent("Pause Cleanup") { }
  VMOp_Type type() const override { return VMOp_G1PauseCleanup; }
  void work() override;
};

#endif // SHARE_GC_G1_G1VMOPERATIONS_HPP
