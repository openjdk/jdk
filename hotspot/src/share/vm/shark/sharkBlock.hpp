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

#ifndef SHARE_VM_SHARK_SHARKBLOCK_HPP
#define SHARE_VM_SHARK_SHARKBLOCK_HPP

#include "ci/ciMethod.hpp"
#include "ci/ciStreams.hpp"
#include "memory/allocation.hpp"
#include "shark/llvmHeaders.hpp"
#include "shark/sharkBuilder.hpp"
#include "shark/sharkConstant.hpp"
#include "shark/sharkInvariants.hpp"
#include "shark/sharkState.hpp"
#include "shark/sharkValue.hpp"
#include "utilities/debug.hpp"

class SharkState;

class SharkBlock : public SharkTargetInvariants {
 protected:
  SharkBlock(const SharkTargetInvariants* parent)
    : SharkTargetInvariants(parent),
      _iter(target()),
      _current_state(NULL) {}

  SharkBlock(const SharkCompileInvariants* parent, ciMethod* target)
    : SharkTargetInvariants(parent, target),
      _iter(target),
      _current_state(NULL) {}

 private:
  ciBytecodeStream _iter;
  SharkState*      _current_state;

 public:
  ciBytecodeStream* iter() {
    return &_iter;
  }
  Bytecodes::Code bc() {
    return iter()->cur_bc();
  }
  int bci() {
    return iter()->cur_bci();
  }

  // Entry state
 protected:
  virtual SharkState* entry_state();

  // Current state
 private:
  SharkState* initial_current_state();

 public:
  SharkState* current_state() {
    if (_current_state == NULL)
      set_current_state(initial_current_state());
    return _current_state;
  }

 protected:
  void set_current_state(SharkState* current_state) {
    _current_state = current_state;
  }

  // Local variables
 protected:
  SharkValue* local(int index) {
    SharkValue *value = current_state()->local(index);
    assert(value != NULL, "shouldn't be");
    assert(value->is_one_word() ||
           (index + 1 < max_locals() &&
            current_state()->local(index + 1) == NULL), "should be");
    return value;
  }
  void set_local(int index, SharkValue* value) {
    assert(value != NULL, "shouldn't be");
    current_state()->set_local(index, value);
    if (value->is_two_word())
      current_state()->set_local(index + 1, NULL);
  }

  // Expression stack (raw)
 protected:
  void xpush(SharkValue* value) {
    current_state()->push(value);
  }
  SharkValue* xpop() {
    return current_state()->pop();
  }
  SharkValue* xstack(int slot) {
    SharkValue *value = current_state()->stack(slot);
    assert(value != NULL, "shouldn't be");
    assert(value->is_one_word() ||
           (slot > 0 &&
            current_state()->stack(slot - 1) == NULL), "should be");
    return value;
  }
  int xstack_depth() {
    return current_state()->stack_depth();
  }

  // Expression stack (cooked)
 protected:
  void push(SharkValue* value) {
    assert(value != NULL, "shouldn't be");
    xpush(value);
    if (value->is_two_word())
      xpush(NULL);
  }
  SharkValue* pop() {
    int size = current_state()->stack(0) == NULL ? 2 : 1;
    if (size == 2)
      xpop();
    SharkValue *value = xpop();
    assert(value && value->size() == size, "should be");
    return value;
  }
  SharkValue* pop_result(BasicType type) {
    SharkValue *result = pop();

#ifdef ASSERT
    switch (result->basic_type()) {
    case T_BOOLEAN:
    case T_BYTE:
    case T_CHAR:
    case T_SHORT:
      assert(type == T_INT, "type mismatch");
      break;

    case T_ARRAY:
      assert(type == T_OBJECT, "type mismatch");
      break;

    default:
      assert(result->basic_type() == type, "type mismatch");
    }
#endif // ASSERT

    return result;
  }

  // Code generation
 public:
  virtual void emit_IR();

 protected:
  void parse_bytecode(int start, int limit);

  // Helpers
 protected:
  virtual void do_zero_check(SharkValue* value);

  // Zero checking
 protected:
  void check_null(SharkValue* object) {
    zero_check(object);
  }
  void check_divide_by_zero(SharkValue* value) {
    zero_check(value);
  }
 private:
  void zero_check(SharkValue* value) {
    if (!value->zero_checked())
      do_zero_check(value);
  }

  // Safepoints
 protected:
  virtual void maybe_add_backedge_safepoint();

  // Traps
 protected:
  virtual bool has_trap();
  virtual int  trap_request();
  virtual int  trap_bci();
  virtual void do_trap(int trap_request);

  // arraylength
 protected:
  virtual void do_arraylength();

  // *aload and *astore
 protected:
  virtual void do_aload(BasicType basic_type);
  virtual void do_astore(BasicType basic_type);

  // *div and *rem
 private:
  void do_idiv() {
    do_div_or_rem(false, false);
  }
  void do_irem() {
    do_div_or_rem(false, true);
  }
  void do_ldiv() {
    do_div_or_rem(true, false);
  }
  void do_lrem() {
    do_div_or_rem(true, true);
  }
  void do_div_or_rem(bool is_long, bool is_rem);

  // get* and put*
 private:
  void do_getstatic() {
    do_field_access(true, false);
  }
  void do_getfield() {
    do_field_access(true, true);
  }
  void do_putstatic() {
    do_field_access(false, false);
  }
  void do_putfield() {
    do_field_access(false, true);
  }
  void do_field_access(bool is_get, bool is_field);

  // lcmp and [fd]cmp[lg]
 private:
  void do_lcmp();
  void do_fcmp(bool is_double, bool unordered_is_greater);

  // *return and athrow
 protected:
  virtual void do_return(BasicType type);
  virtual void do_athrow();

  // goto*
 protected:
  virtual void do_goto();

  // jsr* and ret
 protected:
  virtual void do_jsr();
  virtual void do_ret();

  // if*
 protected:
  virtual void do_if(llvm::ICmpInst::Predicate p, SharkValue* b, SharkValue* a);

  // *switch
 protected:
  int switch_default_dest();
  int switch_table_length();
  int switch_key(int i);
  int switch_dest(int i);

  virtual void do_switch();

  // invoke*
 protected:
  virtual void do_call();

  // checkcast and instanceof
 protected:
  virtual void do_instance_check();
  virtual bool maybe_do_instanceof_if();

  // new and *newarray
 protected:
  virtual void do_new();
  virtual void do_newarray();
  virtual void do_anewarray();
  virtual void do_multianewarray();

  // monitorenter and monitorexit
 protected:
  virtual void do_monitorenter();
  virtual void do_monitorexit();
};

#endif // SHARE_VM_SHARK_SHARKBLOCK_HPP
