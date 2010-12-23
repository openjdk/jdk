/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2008, 2009 Red Hat, Inc.
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

#ifndef SHARE_VM_SHARK_SHARKSTATE_HPP
#define SHARE_VM_SHARK_SHARKSTATE_HPP

#include "ci/ciMethod.hpp"
#include "memory/allocation.hpp"
#include "shark/llvmHeaders.hpp"
#include "shark/sharkBuilder.hpp"
#include "shark/sharkInvariants.hpp"
#include "shark/sharkValue.hpp"

class SharkState : public SharkTargetInvariants {
 public:
  SharkState(const SharkTargetInvariants* parent)
    : SharkTargetInvariants(parent),
      _method(NULL),
      _oop_tmp(NULL),
      _has_safepointed(false) { initialize(NULL); }

  SharkState(const SharkState* state)
    : SharkTargetInvariants(state),
      _method(state->_method),
      _oop_tmp(state->_oop_tmp),
      _has_safepointed(state->_has_safepointed) { initialize(state); }

 private:
  void initialize(const SharkState* state);

 private:
  llvm::Value* _method;
  SharkValue** _locals;
  SharkValue** _stack;
  SharkValue** _sp;
  int          _num_monitors;
  llvm::Value* _oop_tmp;
  bool         _has_safepointed;

  // Method
 public:
  llvm::Value** method_addr() {
    return &_method;
  }
  llvm::Value* method() const {
    return _method;
  }
 protected:
  void set_method(llvm::Value* method) {
    _method = method;
  }

  // Local variables
 public:
  SharkValue** local_addr(int index) const {
    assert(index >= 0 && index < max_locals(), "bad local variable index");
    return &_locals[index];
  }
  SharkValue* local(int index) const {
    return *local_addr(index);
  }
  void set_local(int index, SharkValue* value) {
    *local_addr(index) = value;
  }

  // Expression stack
 public:
  SharkValue** stack_addr(int slot) const {
    assert(slot >= 0 && slot < stack_depth(), "bad stack slot");
    return &_sp[-(slot + 1)];
  }
  SharkValue* stack(int slot) const {
    return *stack_addr(slot);
  }
 protected:
  void set_stack(int slot, SharkValue* value) {
    *stack_addr(slot) = value;
  }
 public:
  int stack_depth() const {
    return _sp - _stack;
  }
  void push(SharkValue* value) {
    assert(stack_depth() < max_stack(), "stack overrun");
    *(_sp++) = value;
  }
  SharkValue* pop() {
    assert(stack_depth() > 0, "stack underrun");
    return *(--_sp);
  }

  // Monitors
 public:
  int num_monitors() const {
    return _num_monitors;
  }
  void set_num_monitors(int num_monitors) {
    _num_monitors = num_monitors;
  }

  // Temporary oop slot
 public:
  llvm::Value** oop_tmp_addr() {
    return &_oop_tmp;
  }
  llvm::Value* oop_tmp() const {
    return _oop_tmp;
  }
  void set_oop_tmp(llvm::Value* oop_tmp) {
    _oop_tmp = oop_tmp;
  }

  // Safepointed status
 public:
  bool has_safepointed() const {
    return _has_safepointed;
  }
  void set_has_safepointed(bool has_safepointed) {
    _has_safepointed = has_safepointed;
  }

  // Comparison
 public:
  bool equal_to(SharkState* other);

  // Copy and merge
 public:
  SharkState* copy() const {
    return new SharkState(this);
  }
  void merge(SharkState*       other,
             llvm::BasicBlock* other_block,
             llvm::BasicBlock* this_block);

  // Value replacement
 public:
  void replace_all(SharkValue* old_value, SharkValue* new_value);
};

class SharkTopLevelBlock;

// SharkNormalEntryState objects are used to create the state
// that the method will be entered with for a normal invocation.
class SharkNormalEntryState : public SharkState {
 public:
  SharkNormalEntryState(SharkTopLevelBlock* block,
                        llvm::Value*        method);
};

// SharkOSREntryState objects are used to create the state
// that the method will be entered with for an OSR invocation.
class SharkOSREntryState : public SharkState {
 public:
  SharkOSREntryState(SharkTopLevelBlock* block,
                     llvm::Value*        method,
                     llvm::Value*        osr_buf);
};

// SharkPHIState objects are used to manage the entry state
// for blocks with more than one entry path or for blocks
// entered from blocks that will be compiled later.
class SharkPHIState : public SharkState {
 public:
  SharkPHIState(SharkTopLevelBlock* block);

 private:
  SharkTopLevelBlock* _block;

 private:
  SharkTopLevelBlock* block() const {
    return _block;
  }

 public:
  void add_incoming(SharkState* incoming_state);
};

#endif // SHARE_VM_SHARK_SHARKSTATE_HPP
