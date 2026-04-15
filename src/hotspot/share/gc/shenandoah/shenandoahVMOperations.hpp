/*
 * Copyright (c) 2013, 2021, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHVMOPERATIONS_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHVMOPERATIONS_HPP

#include "gc/shared/gcVMOperations.hpp"

class ShenandoahConcurrentGC;
class ShenandoahDegenGC;
class ShenandoahFullGC;

// VM_operations for the Shenandoah Collector.
//
// VM_ShenandoahOperation
//   - VM_ShenandoahInitMark: initiate concurrent marking
//   - VM_ShenandoahFinalMarkStartEvac: finish up concurrent marking, and start evacuation
//   - VM_ShenandoahInitUpdateRefs: initiate update references
//   - VM_ShenandoahFinalUpdateRefs: finish up update references
//   - VM_ShenandoahFinalRoots: finish up roots on a non-evacuating cycle
//   - VM_ShenandoahReferenceOperation:
//       - VM_ShenandoahFullGC: do full GC
//       - VM_ShenandoahDegeneratedGC: do STW degenerated GC

class VM_ShenandoahOperation : public VM_Operation {
protected:
  uint _gc_id;
  ShenandoahGeneration* _generation;

  void set_active_generation();
public:
  explicit VM_ShenandoahOperation(ShenandoahGeneration* generation)
  : _gc_id(GCId::current())
  , _generation(generation) {
  }

  bool skip_thread_oop_barriers() const override { return true; }

  void log_active_generation(const char* prefix);
  bool doit_prologue() override;
  void doit_epilogue() override;

  bool is_gc_operation() const override { return true; };
};

class VM_ShenandoahReferenceOperation : public VM_ShenandoahOperation {
public:
  explicit VM_ShenandoahReferenceOperation(ShenandoahGeneration* generation)
    : VM_ShenandoahOperation(generation) {};
  bool doit_prologue() override;
  void doit_epilogue() override;
};

class VM_ShenandoahInitMark: public VM_ShenandoahOperation {
  ShenandoahConcurrentGC* const _gc;
public:
  explicit VM_ShenandoahInitMark(ShenandoahConcurrentGC* gc);
  VM_Operation::VMOp_Type type() const override { return VMOp_ShenandoahInitMark; }
  const char* name()             const override { return "Shenandoah Init Marking"; }
  void doit() override;
};

class VM_ShenandoahFinalMarkStartEvac: public VM_ShenandoahOperation {
  ShenandoahConcurrentGC* const _gc;
public:
  explicit VM_ShenandoahFinalMarkStartEvac(ShenandoahConcurrentGC* gc);
  VM_Operation::VMOp_Type type() const override { return VMOp_ShenandoahFinalMarkStartEvac; }
  const char* name()             const override { return "Shenandoah Final Mark and Start Evacuation"; }
  void doit() override;
};

class VM_ShenandoahDegeneratedGC: public VM_ShenandoahReferenceOperation {
  ShenandoahDegenGC* const _gc;
public:
  explicit VM_ShenandoahDegeneratedGC(ShenandoahDegenGC* gc);
  VM_Operation::VMOp_Type type() const override { return VMOp_ShenandoahDegeneratedGC; }
  const char* name()             const override { return "Shenandoah Degenerated GC"; }
  void doit() override;
};

class VM_ShenandoahFullGC : public VM_ShenandoahReferenceOperation {
  GCCause::Cause           _gc_cause;
  ShenandoahFullGC* const  _full_gc;
public:
  explicit VM_ShenandoahFullGC(GCCause::Cause gc_cause, ShenandoahFullGC* full_gc);
  VM_Operation::VMOp_Type type() const override { return VMOp_ShenandoahFullGC; }
  const char* name()             const override { return "Shenandoah Full GC"; }
  void doit() override;
};

class VM_ShenandoahInitUpdateRefs: public VM_ShenandoahOperation {
  ShenandoahConcurrentGC* const _gc;
public:
  explicit VM_ShenandoahInitUpdateRefs(ShenandoahConcurrentGC* gc);
  VM_Operation::VMOp_Type type() const override { return VMOp_ShenandoahInitUpdateRefs; }
  const char* name()             const override { return "Shenandoah Init Update References"; }
  void doit() override;
};

class VM_ShenandoahFinalUpdateRefs: public VM_ShenandoahOperation {
  ShenandoahConcurrentGC* const _gc;
public:
  explicit VM_ShenandoahFinalUpdateRefs(ShenandoahConcurrentGC* gc);
  VM_Operation::VMOp_Type type() const override { return VMOp_ShenandoahFinalUpdateRefs; }
  const char* name()             const override { return "Shenandoah Final Update References"; }
  void doit() override;
};

class VM_ShenandoahFinalRoots: public VM_ShenandoahOperation {
  ShenandoahConcurrentGC* const _gc;
public:
  explicit VM_ShenandoahFinalRoots(ShenandoahConcurrentGC* gc);
  VM_Operation::VMOp_Type type() const override { return VMOp_ShenandoahFinalRoots; }
  const char* name()             const override { return "Shenandoah Final Roots"; }
  void doit() override;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHVMOPERATIONS_HPP
