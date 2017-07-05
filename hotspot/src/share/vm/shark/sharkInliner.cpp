/*
 * Copyright (c) 1999, 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2009 Red Hat, Inc.
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

#include "precompiled.hpp"
#include "ci/ciField.hpp"
#include "ci/ciMethod.hpp"
#include "ci/ciStreams.hpp"
#include "interpreter/bytecodes.hpp"
#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"
#include "shark/sharkBlock.hpp"
#include "shark/sharkConstant.hpp"
#include "shark/sharkInliner.hpp"
#include "shark/sharkIntrinsics.hpp"
#include "shark/sharkState.hpp"
#include "shark/sharkValue.hpp"
#include "shark/shark_globals.hpp"

using namespace llvm;

class SharkInlineBlock : public SharkBlock {
 public:
  SharkInlineBlock(ciMethod* target, SharkState* state)
    : SharkBlock(state, target),
      _outer_state(state),
      _entry_state(new SharkState(this)) {
    for (int i = target->max_locals() - 1; i >= 0; i--) {
      SharkValue *value = NULL;
      if (i < target->arg_size())
        value = outer_state()->pop();
      entry_state()->set_local(i, value);
    }
  }

 private:
  SharkState* _outer_state;
  SharkState* _entry_state;

 private:
  SharkState* outer_state() {
    return _outer_state;
  }
  SharkState* entry_state() {
    return _entry_state;
  }

 public:
  void emit_IR() {
    parse_bytecode(0, target()->code_size());
  }

 private:
  void do_return(BasicType type) {
    if (type != T_VOID) {
      SharkValue *result = pop_result(type);
      outer_state()->push(result);
      if (result->is_two_word())
        outer_state()->push(NULL);
    }
  }
};

class SharkInlinerHelper : public StackObj {
 public:
  SharkInlinerHelper(ciMethod* target, SharkState* entry_state)
    : _target(target),
      _entry_state(entry_state),
      _iter(target) {}

 private:
  ciBytecodeStream _iter;
  SharkState*      _entry_state;
  ciMethod*        _target;

 public:
  ciBytecodeStream* iter() {
    return &_iter;
  }
  SharkState* entry_state() const {
    return _entry_state;
  }
  ciMethod* target() const {
    return _target;
  }

 public:
  Bytecodes::Code bc() {
    return iter()->cur_bc();
  }
  int max_locals() const {
    return target()->max_locals();
  }
  int max_stack() const {
    return target()->max_stack();
  }

  // Inlinability check
 public:
  bool is_inlinable();

 private:
  void initialize_for_check();

  bool do_getstatic() {
    return do_field_access(true, false);
  }
  bool do_getfield() {
    return do_field_access(true, true);
  }
  bool do_putfield() {
    return do_field_access(false, true);
  }
  bool do_field_access(bool is_get, bool is_field);

  // Local variables for inlinability check
 private:
  bool* _locals;

 public:
  bool* local_addr(int index) const {
    assert(index >= 0 && index < max_locals(), "bad local variable index");
    return &_locals[index];
  }
  bool local(int index) const {
    return *local_addr(index);
  }
  void set_local(int index, bool value) {
    *local_addr(index) = value;
  }

  // Expression stack for inlinability check
 private:
  bool* _stack;
  bool* _sp;

 public:
  int stack_depth() const {
    return _sp - _stack;
  }
  bool* stack_addr(int slot) const {
    assert(slot >= 0 && slot < stack_depth(), "bad stack slot");
    return &_sp[-(slot + 1)];
  }
  void push(bool value) {
    assert(stack_depth() < max_stack(), "stack overrun");
    *(_sp++) = value;
  }
  bool pop() {
    assert(stack_depth() > 0, "stack underrun");
    return *(--_sp);
  }

  // Methods for two-word locals
 public:
  void push_pair_local(int index) {
    push(local(index));
    push(local(index + 1));
  }
  void pop_pair_local(int index) {
    set_local(index + 1, pop());
    set_local(index, pop());
  }

  // Code generation
 public:
  void do_inline() {
    (new SharkInlineBlock(target(), entry_state()))->emit_IR();
  }
};

// Quick checks so we can bail out before doing too much
bool SharkInliner::may_be_inlinable(ciMethod *target) {
  // We can't inline native methods
  if (target->is_native())
    return false;

  // Not much point inlining abstract ones, and in any
  // case we'd need a stack frame to throw the exception
  if (target->is_abstract())
    return false;

  // Don't inline anything huge
  if (target->code_size() > SharkMaxInlineSize)
    return false;

  // Monitors aren't allowed without a frame to put them in
  if (target->is_synchronized() || target->has_monitor_bytecodes())
    return false;

  // We don't do control flow
  if (target->has_exception_handlers() || target->has_jsrs())
    return false;

  // Don't try to inline constructors, as they must
  // eventually call Object.<init> which we can't inline.
  // Note that this catches <clinit> too, but why would
  // we be compiling that?
  if (target->is_initializer())
    return false;

  // Mustn't inline Object.<init>
  // Should be caught by the above, but just in case...
  if (target->intrinsic_id() == vmIntrinsics::_Object_init)
    return false;

  return true;
}

// Full-on detailed check, for methods that pass the quick checks
// Inlined methods have no stack frame, so we can't do anything
// that would require one.  This means no safepoints (and hence
// no loops) and no VM calls.  No VM calls means, amongst other
// things, that no exceptions can be created, which means no null
// checks or divide-by-zero checks are allowed.  The lack of null
// checks in particular would eliminate practically everything,
// but we can get around that restriction by relying on the zero-
// check eliminator to strip the checks.  To do that, we need to
// walk through the method, tracking which values are and are not
// zero-checked.
bool SharkInlinerHelper::is_inlinable() {
  ResourceMark rm;
  initialize_for_check();

  SharkConstant *sc;
  bool a, b, c, d;

  iter()->reset_to_bci(0);
  while (iter()->next() != ciBytecodeStream::EOBC()) {
    switch (bc()) {
    case Bytecodes::_nop:
      break;

    case Bytecodes::_aconst_null:
      push(false);
      break;

    case Bytecodes::_iconst_0:
      push(false);
      break;
    case Bytecodes::_iconst_m1:
    case Bytecodes::_iconst_1:
    case Bytecodes::_iconst_2:
    case Bytecodes::_iconst_3:
    case Bytecodes::_iconst_4:
    case Bytecodes::_iconst_5:
      push(true);
      break;

    case Bytecodes::_lconst_0:
      push(false);
      push(false);
      break;
    case Bytecodes::_lconst_1:
      push(true);
      push(false);
      break;

    case Bytecodes::_fconst_0:
    case Bytecodes::_fconst_1:
    case Bytecodes::_fconst_2:
      push(false);
      break;

    case Bytecodes::_dconst_0:
    case Bytecodes::_dconst_1:
      push(false);
      push(false);
      break;

    case Bytecodes::_bipush:
      push(iter()->get_constant_u1() != 0);
      break;
    case Bytecodes::_sipush:
      push(iter()->get_constant_u2() != 0);
      break;

    case Bytecodes::_ldc:
    case Bytecodes::_ldc_w:
    case Bytecodes::_ldc2_w:
      sc = SharkConstant::for_ldc(iter());
      if (!sc->is_loaded())
        return false;
      push(sc->is_nonzero());
      if (sc->is_two_word())
        push(false);
      break;

    case Bytecodes::_iload_0:
    case Bytecodes::_fload_0:
    case Bytecodes::_aload_0:
      push(local(0));
      break;
    case Bytecodes::_lload_0:
    case Bytecodes::_dload_0:
      push_pair_local(0);
      break;

    case Bytecodes::_iload_1:
    case Bytecodes::_fload_1:
    case Bytecodes::_aload_1:
      push(local(1));
      break;
    case Bytecodes::_lload_1:
    case Bytecodes::_dload_1:
      push_pair_local(1);
      break;

    case Bytecodes::_iload_2:
    case Bytecodes::_fload_2:
    case Bytecodes::_aload_2:
      push(local(2));
      break;
    case Bytecodes::_lload_2:
    case Bytecodes::_dload_2:
      push_pair_local(2);
      break;

    case Bytecodes::_iload_3:
    case Bytecodes::_fload_3:
    case Bytecodes::_aload_3:
      push(local(3));
      break;
    case Bytecodes::_lload_3:
    case Bytecodes::_dload_3:
      push_pair_local(3);
      break;

    case Bytecodes::_iload:
    case Bytecodes::_fload:
    case Bytecodes::_aload:
      push(local(iter()->get_index()));
      break;
    case Bytecodes::_lload:
    case Bytecodes::_dload:
      push_pair_local(iter()->get_index());
      break;

    case Bytecodes::_istore_0:
    case Bytecodes::_fstore_0:
    case Bytecodes::_astore_0:
      set_local(0, pop());
      break;
    case Bytecodes::_lstore_0:
    case Bytecodes::_dstore_0:
      pop_pair_local(0);
      break;

    case Bytecodes::_istore_1:
    case Bytecodes::_fstore_1:
    case Bytecodes::_astore_1:
      set_local(1, pop());
      break;
    case Bytecodes::_lstore_1:
    case Bytecodes::_dstore_1:
      pop_pair_local(1);
      break;

    case Bytecodes::_istore_2:
    case Bytecodes::_fstore_2:
    case Bytecodes::_astore_2:
      set_local(2, pop());
      break;
    case Bytecodes::_lstore_2:
    case Bytecodes::_dstore_2:
      pop_pair_local(2);
      break;

    case Bytecodes::_istore_3:
    case Bytecodes::_fstore_3:
    case Bytecodes::_astore_3:
      set_local(3, pop());
      break;
    case Bytecodes::_lstore_3:
    case Bytecodes::_dstore_3:
      pop_pair_local(3);
      break;

    case Bytecodes::_istore:
    case Bytecodes::_fstore:
    case Bytecodes::_astore:
      set_local(iter()->get_index(), pop());
      break;
    case Bytecodes::_lstore:
    case Bytecodes::_dstore:
      pop_pair_local(iter()->get_index());
      break;

    case Bytecodes::_pop:
      pop();
      break;
    case Bytecodes::_pop2:
      pop();
      pop();
      break;
    case Bytecodes::_swap:
      a = pop();
      b = pop();
      push(a);
      push(b);
      break;
    case Bytecodes::_dup:
      a = pop();
      push(a);
      push(a);
      break;
    case Bytecodes::_dup_x1:
      a = pop();
      b = pop();
      push(a);
      push(b);
      push(a);
      break;
    case Bytecodes::_dup_x2:
      a = pop();
      b = pop();
      c = pop();
      push(a);
      push(c);
      push(b);
      push(a);
      break;
    case Bytecodes::_dup2:
      a = pop();
      b = pop();
      push(b);
      push(a);
      push(b);
      push(a);
      break;
    case Bytecodes::_dup2_x1:
      a = pop();
      b = pop();
      c = pop();
      push(b);
      push(a);
      push(c);
      push(b);
      push(a);
      break;
    case Bytecodes::_dup2_x2:
      a = pop();
      b = pop();
      c = pop();
      d = pop();
      push(b);
      push(a);
      push(d);
      push(c);
      push(b);
      push(a);
      break;

    case Bytecodes::_getfield:
      if (!do_getfield())
        return false;
      break;
    case Bytecodes::_getstatic:
      if (!do_getstatic())
        return false;
      break;
    case Bytecodes::_putfield:
      if (!do_putfield())
        return false;
      break;

    case Bytecodes::_iadd:
    case Bytecodes::_isub:
    case Bytecodes::_imul:
    case Bytecodes::_iand:
    case Bytecodes::_ixor:
    case Bytecodes::_ishl:
    case Bytecodes::_ishr:
    case Bytecodes::_iushr:
      pop();
      pop();
      push(false);
      break;
    case Bytecodes::_ior:
      a = pop();
      b = pop();
      push(a && b);
      break;
    case Bytecodes::_idiv:
    case Bytecodes::_irem:
      if (!pop())
        return false;
      pop();
      push(false);
      break;
    case Bytecodes::_ineg:
      break;

    case Bytecodes::_ladd:
    case Bytecodes::_lsub:
    case Bytecodes::_lmul:
    case Bytecodes::_land:
    case Bytecodes::_lxor:
      pop();
      pop();
      pop();
      pop();
      push(false);
      push(false);
      break;
    case Bytecodes::_lor:
      a = pop();
      b = pop();
      push(a && b);
      break;
    case Bytecodes::_ldiv:
    case Bytecodes::_lrem:
      pop();
      if (!pop())
        return false;
      pop();
      pop();
      push(false);
      push(false);
      break;
    case Bytecodes::_lneg:
      break;
    case Bytecodes::_lshl:
    case Bytecodes::_lshr:
    case Bytecodes::_lushr:
      pop();
      pop();
      pop();
      push(false);
      push(false);
      break;

    case Bytecodes::_fadd:
    case Bytecodes::_fsub:
    case Bytecodes::_fmul:
    case Bytecodes::_fdiv:
    case Bytecodes::_frem:
      pop();
      pop();
      push(false);
      break;
    case Bytecodes::_fneg:
      break;

    case Bytecodes::_dadd:
    case Bytecodes::_dsub:
    case Bytecodes::_dmul:
    case Bytecodes::_ddiv:
    case Bytecodes::_drem:
      pop();
      pop();
      pop();
      pop();
      push(false);
      push(false);
      break;
    case Bytecodes::_dneg:
      break;

    case Bytecodes::_iinc:
      set_local(iter()->get_index(), false);
      break;

    case Bytecodes::_lcmp:
      pop();
      pop();
      pop();
      pop();
      push(false);
      break;

    case Bytecodes::_fcmpl:
    case Bytecodes::_fcmpg:
      pop();
      pop();
      push(false);
      break;

    case Bytecodes::_dcmpl:
    case Bytecodes::_dcmpg:
      pop();
      pop();
      pop();
      pop();
      push(false);
      break;

    case Bytecodes::_i2l:
      push(false);
      break;
    case Bytecodes::_i2f:
      pop();
      push(false);
      break;
    case Bytecodes::_i2d:
      pop();
      push(false);
      push(false);
      break;

    case Bytecodes::_l2i:
    case Bytecodes::_l2f:
      pop();
      pop();
      push(false);
      break;
    case Bytecodes::_l2d:
      pop();
      pop();
      push(false);
      push(false);
      break;

    case Bytecodes::_f2i:
      pop();
      push(false);
      break;
    case Bytecodes::_f2l:
    case Bytecodes::_f2d:
      pop();
      push(false);
      push(false);
      break;

    case Bytecodes::_d2i:
    case Bytecodes::_d2f:
      pop();
      pop();
      push(false);
      break;
    case Bytecodes::_d2l:
      pop();
      pop();
      push(false);
      push(false);
      break;

    case Bytecodes::_i2b:
    case Bytecodes::_i2c:
    case Bytecodes::_i2s:
      pop();
      push(false);
      break;

    case Bytecodes::_return:
    case Bytecodes::_ireturn:
    case Bytecodes::_lreturn:
    case Bytecodes::_freturn:
    case Bytecodes::_dreturn:
    case Bytecodes::_areturn:
      break;

    default:
      return false;
    }
  }

  return true;
}

void SharkInlinerHelper::initialize_for_check() {
  _locals = NEW_RESOURCE_ARRAY(bool, max_locals());
  _stack = NEW_RESOURCE_ARRAY(bool, max_stack());

  memset(_locals, 0, max_locals() * sizeof(bool));
  for (int i = 0; i < target()->arg_size(); i++) {
    SharkValue *arg = entry_state()->stack(target()->arg_size() - 1 - i);
    if (arg && arg->zero_checked())
      set_local(i, true);
  }

  _sp = _stack;
}

bool SharkInlinerHelper::do_field_access(bool is_get, bool is_field) {
  assert(is_get || is_field, "can't inline putstatic");

  // If the holder isn't linked then there isn't a lot we can do
  if (!target()->holder()->is_linked())
    return false;

  // Get the field
  bool will_link;
  ciField *field = iter()->get_field(will_link);
  if (!will_link)
    return false;

  // If the field is mismatched then an exception needs throwing
  if (is_field == field->is_static())
    return false;

  // Pop the value off the stack if necessary
  if (!is_get) {
    pop();
    if (field->type()->is_two_word())
      pop();
  }

  // Pop and null-check the receiver if necessary
  if (is_field) {
    if (!pop())
      return false;
  }

  // Push the result if necessary
  if (is_get) {
    bool result_pushed = false;
    if (field->is_constant() && field->is_static()) {
      SharkConstant *sc = SharkConstant::for_field(iter());
      if (sc->is_loaded()) {
        push(sc->is_nonzero());
        result_pushed = true;
      }
    }

    if (!result_pushed)
      push(false);

    if (field->type()->is_two_word())
      push(false);
  }

  return true;
}

bool SharkInliner::attempt_inline(ciMethod *target, SharkState *state) {
  if (!Inline) {
    return false;
  }

  if (SharkIntrinsics::is_intrinsic(target)) {
    SharkIntrinsics::inline_intrinsic(target, state);
    return true;
  }

  if (may_be_inlinable(target)) {
    SharkInlinerHelper inliner(target, state);
    if (inliner.is_inlinable()) {
      inliner.do_inline();
      return true;
    }
  }
  return false;
}
