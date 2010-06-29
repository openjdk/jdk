/*
 * Copyright (c) 1999, 2006, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_c1_ValueStack.cpp.incl"


// Implementation of ValueStack

ValueStack::ValueStack(IRScope* scope, int locals_size, int max_stack_size)
: _scope(scope)
, _locals(locals_size, NULL)
, _stack(max_stack_size)
, _lock_stack(false)
, _locks(1)
{
  assert(scope != NULL, "scope must exist");
}

ValueStack* ValueStack::copy() {
  ValueStack* s = new ValueStack(scope(), locals_size(), max_stack_size());
  s->_stack.appendAll(&_stack);
  s->_locks.appendAll(&_locks);
  s->replace_locals(this);
  return s;
}


ValueStack* ValueStack::copy_locks() {
  int sz = scope()->lock_stack_size();
  if (stack_size() == 0) {
    sz = 0;
  }
  ValueStack* s = new ValueStack(scope(), locals_size(), sz);
  s->_lock_stack = true;
  s->_locks.appendAll(&_locks);
  s->replace_locals(this);
  if (sz > 0) {
    assert(sz <= stack_size(), "lock stack underflow");
    for (int i = 0; i < sz; i++) {
      s->_stack.append(_stack[i]);
    }
  }
  return s;
}

bool ValueStack::is_same(ValueStack* s) {
  assert(s != NULL, "state must exist");
  assert(scope      () == s->scope      (), "scopes       must correspond");
  assert(locals_size() == s->locals_size(), "locals sizes must correspond");
  return is_same_across_scopes(s);
}


bool ValueStack::is_same_across_scopes(ValueStack* s) {
  assert(s != NULL, "state must exist");
  assert(stack_size () == s->stack_size (), "stack  sizes must correspond");
  assert(locks_size () == s->locks_size (), "locks  sizes must correspond");
  // compare each stack element with the corresponding stack element of s
  int index;
  Value value;
  for_each_stack_value(this, index, value) {
    if (value->type()->tag() != s->stack_at(index)->type()->tag()) return false;
  }
  for_each_lock_value(this, index, value) {
    if (value != s->lock_at(index)) return false;
  }
  return true;
}


ValueStack* ValueStack::caller_state() const {
  return scope()->caller_state();
}


void ValueStack::clear_locals() {
  for (int i = _locals.length() - 1; i >= 0; i--) {
    _locals.at_put(i, NULL);
  }
}


void ValueStack::replace_locals(ValueStack* with) {
  assert(locals_size() == with->locals_size(), "number of locals must match");
  for (int i = locals_size() - 1; i >= 0; i--) {
    _locals.at_put(i, with->_locals.at(i));
  }
}

void ValueStack::pin_stack_for_linear_scan() {
  for_each_state_value(this, v,
    if (v->as_Constant() == NULL && v->as_Local() == NULL) {
      v->pin(Instruction::PinStackForStateSplit);
    }
  );
}


// apply function to all values of a list; factored out from values_do(f)
void ValueStack::apply(Values list, ValueVisitor* f) {
  for (int i = 0; i < list.length(); i++) {
    Value* va = list.adr_at(i);
    Value v0 = *va;
    if (v0 != NULL) {
      if (!v0->type()->is_illegal()) {
        assert(v0->as_HiWord() == NULL, "should never see HiWord during traversal");
        f->visit(va);
#ifdef ASSERT
        Value v1 = *va;
        if (v0 != v1) {
          assert(v1->type()->is_illegal() || v0->type()->tag() == v1->type()->tag(), "types must match");
          if (v0->type()->is_double_word()) {
            list.at_put(i + 1, v0->hi_word());
          }
        }
#endif
        if (v0->type()->is_double_word()) i++;
      }
    }
  }
}


void ValueStack::values_do(ValueVisitor* f) {
  apply(_stack, f);
  apply(_locks, f);

  ValueStack* state = this;
  for_each_state(state) {
    apply(state->_locals, f);
  }
}


Values* ValueStack::pop_arguments(int argument_size) {
  assert(stack_size() >= argument_size, "stack too small or too many arguments");
  int base = stack_size() - argument_size;
  Values* args = new Values(argument_size);
  for (int i = base; i < stack_size();) args->push(stack_at_inc(i));
  truncate_stack(base);
  return args;
}


int ValueStack::lock(IRScope* scope, Value obj) {
  _locks.push(obj);
  scope->set_min_number_of_locks(locks_size());
  return locks_size() - 1;
}


int ValueStack::unlock() {
  _locks.pop();
  return locks_size();
}


ValueStack* ValueStack::push_scope(IRScope* scope) {
  assert(scope->caller() == _scope, "scopes must have caller/callee relationship");
  ValueStack* res = new ValueStack(scope,
                                   scope->method()->max_locals(),
                                   max_stack_size() + scope->method()->max_stack());
  // Preserves stack and monitors.
  res->_stack.appendAll(&_stack);
  res->_locks.appendAll(&_locks);
  assert(res->_stack.size() <= res->max_stack_size(), "stack overflow");
  return res;
}


ValueStack* ValueStack::pop_scope() {
  assert(_scope->caller() != NULL, "scope must have caller");
  IRScope* scope = _scope->caller();
  int max_stack = max_stack_size() - _scope->method()->max_stack();
  assert(max_stack >= 0, "stack underflow");
  ValueStack* res = new ValueStack(scope,
                                   scope->method()->max_locals(),
                                   max_stack);
  // Preserves stack and monitors. Restores local and store state from caller scope.
  res->_stack.appendAll(&_stack);
  res->_locks.appendAll(&_locks);
  ValueStack* caller = caller_state();
  if (caller != NULL) {
    for (int i = 0; i < caller->_locals.length(); i++) {
      res->_locals.at_put(i, caller->_locals.at(i));
    }
    assert(res->_locals.length() == res->scope()->method()->max_locals(), "just checking");
  }
  assert(res->_stack.size() <= res->max_stack_size(), "stack overflow");
  return res;
}


void ValueStack::setup_phi_for_stack(BlockBegin* b, int index) {
  assert(stack_at(index)->as_Phi() == NULL || stack_at(index)->as_Phi()->block() != b, "phi function already created");

  ValueType* t = stack_at(index)->type();
  Value phi = new Phi(t, b, -index - 1);
  _stack[index] = phi;

#ifdef ASSERT
  if (t->is_double_word()) {
    _stack[index + 1] = phi->hi_word();
  }
#endif
}

void ValueStack::setup_phi_for_local(BlockBegin* b, int index) {
  assert(local_at(index)->as_Phi() == NULL || local_at(index)->as_Phi()->block() != b, "phi function already created");

  ValueType* t = local_at(index)->type();
  Value phi = new Phi(t, b, index);
  store_local(index, phi);
}

#ifndef PRODUCT
void ValueStack::print() {
  if (stack_is_empty()) {
    tty->print_cr("empty stack");
  } else {
    InstructionPrinter ip;
    for (int i = 0; i < stack_size();) {
      Value t = stack_at_inc(i);
      tty->print("%2d  ", i);
      ip.print_instr(t);
      tty->cr();
    }
  }
  if (!no_active_locks()) {
    InstructionPrinter ip;
    for (int i = 0; i < locks_size(); i--) {
      Value t = lock_at(i);
      tty->print("lock %2d  ", i);
      if (t == NULL) {
        tty->print("this");
      } else {
        ip.print_instr(t);
      }
      tty->cr();
    }
  }
  if (locals_size() > 0) {
    InstructionPrinter ip;
    for (int i = 0; i < locals_size();) {
      Value l = _locals[i];
      tty->print("local %d ", i);
      if (l == NULL) {
        tty->print("null");
        i ++;
      } else {
        ip.print_instr(l);
        if (l->type()->is_illegal() || l->type()->is_single_word()) i ++; else i += 2;
      }
      tty->cr();
    }
  }
}


void ValueStack::verify() {
  Unimplemented();
}
#endif // PRODUCT
