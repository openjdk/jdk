/*
 * Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2008, 2009, 2010 Red Hat, Inc.
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
#include "ci/ciInstance.hpp"
#include "ci/ciObjArrayKlass.hpp"
#include "ci/ciStreams.hpp"
#include "ci/ciType.hpp"
#include "ci/ciTypeFlow.hpp"
#include "interpreter/bytecodes.hpp"
#include "memory/allocation.hpp"
#include "runtime/deoptimization.hpp"
#include "shark/llvmHeaders.hpp"
#include "shark/llvmValue.hpp"
#include "shark/sharkBuilder.hpp"
#include "shark/sharkCacheDecache.hpp"
#include "shark/sharkConstant.hpp"
#include "shark/sharkInliner.hpp"
#include "shark/sharkState.hpp"
#include "shark/sharkTopLevelBlock.hpp"
#include "shark/sharkValue.hpp"
#include "shark/shark_globals.hpp"
#include "utilities/debug.hpp"

using namespace llvm;

void SharkTopLevelBlock::scan_for_traps() {
  // If typeflow found a trap then don't scan past it
  int limit_bci = ciblock()->has_trap() ? ciblock()->trap_bci() : limit();

  // Scan the bytecode for traps that are always hit
  iter()->reset_to_bci(start());
  while (iter()->next_bci() < limit_bci) {
    iter()->next();

    ciField *field;
    ciMethod *method;
    ciInstanceKlass *klass;
    bool will_link;
    bool is_field;

    switch (bc()) {
    case Bytecodes::_ldc:
    case Bytecodes::_ldc_w:
    case Bytecodes::_ldc2_w:
      if (!SharkConstant::for_ldc(iter())->is_loaded()) {
        set_trap(
          Deoptimization::make_trap_request(
            Deoptimization::Reason_uninitialized,
            Deoptimization::Action_reinterpret), bci());
        return;
      }
      break;

    case Bytecodes::_getfield:
    case Bytecodes::_getstatic:
    case Bytecodes::_putfield:
    case Bytecodes::_putstatic:
      field = iter()->get_field(will_link);
      assert(will_link, "typeflow responsibility");
      is_field = (bc() == Bytecodes::_getfield || bc() == Bytecodes::_putfield);

      // If the bytecode does not match the field then bail out to
      // the interpreter to throw an IncompatibleClassChangeError
      if (is_field == field->is_static()) {
        set_trap(
          Deoptimization::make_trap_request(
            Deoptimization::Reason_unhandled,
            Deoptimization::Action_none), bci());
        return;
      }

      // Bail out if we are trying to access a static variable
      // before the class initializer has completed.
      if (!is_field && !field->holder()->is_initialized()) {
        if (!static_field_ok_in_clinit(field)) {
          set_trap(
            Deoptimization::make_trap_request(
              Deoptimization::Reason_uninitialized,
              Deoptimization::Action_reinterpret), bci());
          return;
        }
      }
      break;

    case Bytecodes::_invokestatic:
    case Bytecodes::_invokespecial:
    case Bytecodes::_invokevirtual:
    case Bytecodes::_invokeinterface:
      ciSignature* sig;
      method = iter()->get_method(will_link, &sig);
      assert(will_link, "typeflow responsibility");
      // We can't compile calls to method handle intrinsics, because we use
      // the interpreter entry points and they expect the top frame to be an
      // interpreter frame. We need to implement the intrinsics for Shark.
      if (method->is_method_handle_intrinsic() || method->is_compiled_lambda_form()) {
        if (SharkPerformanceWarnings) {
          warning("JSR292 optimization not yet implemented in Shark");
        }
        set_trap(
          Deoptimization::make_trap_request(
            Deoptimization::Reason_unhandled,
            Deoptimization::Action_make_not_compilable), bci());
          return;
      }
      if (!method->holder()->is_linked()) {
        set_trap(
          Deoptimization::make_trap_request(
            Deoptimization::Reason_uninitialized,
            Deoptimization::Action_reinterpret), bci());
          return;
      }

      if (bc() == Bytecodes::_invokevirtual) {
        klass = ciEnv::get_instance_klass_for_declared_method_holder(
          iter()->get_declared_method_holder());
        if (!klass->is_linked()) {
          set_trap(
            Deoptimization::make_trap_request(
              Deoptimization::Reason_uninitialized,
              Deoptimization::Action_reinterpret), bci());
            return;
        }
      }
      break;

    case Bytecodes::_new:
      klass = iter()->get_klass(will_link)->as_instance_klass();
      assert(will_link, "typeflow responsibility");

      // Bail out if the class is unloaded
      if (iter()->is_unresolved_klass() || !klass->is_initialized()) {
        set_trap(
          Deoptimization::make_trap_request(
            Deoptimization::Reason_uninitialized,
            Deoptimization::Action_reinterpret), bci());
        return;
      }

      // Bail out if the class cannot be instantiated
      if (klass->is_abstract() || klass->is_interface() ||
          klass->name() == ciSymbol::java_lang_Class()) {
        set_trap(
          Deoptimization::make_trap_request(
            Deoptimization::Reason_unhandled,
            Deoptimization::Action_reinterpret), bci());
        return;
      }
      break;
    case Bytecodes::_invokedynamic:
    case Bytecodes::_invokehandle:
      if (SharkPerformanceWarnings) {
        warning("JSR292 optimization not yet implemented in Shark");
      }
      set_trap(
        Deoptimization::make_trap_request(
          Deoptimization::Reason_unhandled,
          Deoptimization::Action_make_not_compilable), bci());
      return;
    }
  }

  // Trap if typeflow trapped (and we didn't before)
  if (ciblock()->has_trap()) {
    set_trap(
      Deoptimization::make_trap_request(
        Deoptimization::Reason_unloaded,
        Deoptimization::Action_reinterpret,
        ciblock()->trap_index()), ciblock()->trap_bci());
    return;
  }
}

bool SharkTopLevelBlock::static_field_ok_in_clinit(ciField* field) {
  assert(field->is_static(), "should be");

  // This code is lifted pretty much verbatim from C2's
  // Parse::static_field_ok_in_clinit() in parse3.cpp.
  bool access_OK = false;
  if (target()->holder()->is_subclass_of(field->holder())) {
    if (target()->is_static()) {
      if (target()->name() == ciSymbol::class_initializer_name()) {
        // It's OK to access static fields from the class initializer
        access_OK = true;
      }
    }
    else {
      if (target()->name() == ciSymbol::object_initializer_name()) {
        // It's also OK to access static fields inside a constructor,
        // because any thread calling the constructor must first have
        // synchronized on the class by executing a "new" bytecode.
        access_OK = true;
      }
    }
  }
  return access_OK;
}

SharkState* SharkTopLevelBlock::entry_state() {
  if (_entry_state == NULL) {
    assert(needs_phis(), "should do");
    _entry_state = new SharkPHIState(this);
  }
  return _entry_state;
}

void SharkTopLevelBlock::add_incoming(SharkState* incoming_state) {
  if (needs_phis()) {
    ((SharkPHIState *) entry_state())->add_incoming(incoming_state);
  }
  else if (_entry_state == NULL) {
    _entry_state = incoming_state;
  }
  else {
    assert(entry_state()->equal_to(incoming_state), "should be");
  }
}

void SharkTopLevelBlock::enter(SharkTopLevelBlock* predecessor,
                               bool is_exception) {
  // This block requires phis:
  //  - if it is entered more than once
  //  - if it is an exception handler, because in which
  //    case we assume it's entered more than once.
  //  - if the predecessor will be compiled after this
  //    block, in which case we can't simple propagate
  //    the state forward.
  if (!needs_phis() &&
      (entered() ||
       is_exception ||
       (predecessor && predecessor->index() >= index())))
    _needs_phis = true;

  // Recurse into the tree
  if (!entered()) {
    _entered = true;

    scan_for_traps();
    if (!has_trap()) {
      for (int i = 0; i < num_successors(); i++) {
        successor(i)->enter(this, false);
      }
    }
    compute_exceptions();
    for (int i = 0; i < num_exceptions(); i++) {
      SharkTopLevelBlock *handler = exception(i);
      if (handler)
        handler->enter(this, true);
    }
  }
}

void SharkTopLevelBlock::initialize() {
  char name[28];
  snprintf(name, sizeof(name),
           "bci_%d%s",
           start(), is_backedge_copy() ? "_backedge_copy" : "");
  _entry_block = function()->CreateBlock(name);
}

void SharkTopLevelBlock::decache_for_Java_call(ciMethod *callee) {
  SharkJavaCallDecacher(function(), bci(), callee).scan(current_state());
  for (int i = 0; i < callee->arg_size(); i++)
    xpop();
}

void SharkTopLevelBlock::cache_after_Java_call(ciMethod *callee) {
  if (callee->return_type()->size()) {
    ciType *type;
    switch (callee->return_type()->basic_type()) {
    case T_BOOLEAN:
    case T_BYTE:
    case T_CHAR:
    case T_SHORT:
      type = ciType::make(T_INT);
      break;

    default:
      type = callee->return_type();
    }

    push(SharkValue::create_generic(type, NULL, false));
  }
  SharkJavaCallCacher(function(), callee).scan(current_state());
}

void SharkTopLevelBlock::decache_for_VM_call() {
  SharkVMCallDecacher(function(), bci()).scan(current_state());
}

void SharkTopLevelBlock::cache_after_VM_call() {
  SharkVMCallCacher(function()).scan(current_state());
}

void SharkTopLevelBlock::decache_for_trap() {
  SharkTrapDecacher(function(), bci()).scan(current_state());
}

void SharkTopLevelBlock::emit_IR() {
  builder()->SetInsertPoint(entry_block());

  // Parse the bytecode
  parse_bytecode(start(), limit());

  // If this block falls through to the next then it won't have been
  // terminated by a bytecode and we have to add the branch ourselves
  if (falls_through() && !has_trap())
    do_branch(ciTypeFlow::FALL_THROUGH);
}

SharkTopLevelBlock* SharkTopLevelBlock::bci_successor(int bci) const {
  // XXX now with Linear Search Technology (tm)
  for (int i = 0; i < num_successors(); i++) {
    ciTypeFlow::Block *successor = ciblock()->successors()->at(i);
    if (successor->start() == bci)
      return function()->block(successor->pre_order());
  }
  ShouldNotReachHere();
}

void SharkTopLevelBlock::do_zero_check(SharkValue *value) {
  if (value->is_phi() && value->as_phi()->all_incomers_zero_checked()) {
    function()->add_deferred_zero_check(this, value);
  }
  else {
    BasicBlock *continue_block = function()->CreateBlock("not_zero");
    SharkState *saved_state = current_state();
    set_current_state(saved_state->copy());
    zero_check_value(value, continue_block);
    builder()->SetInsertPoint(continue_block);
    set_current_state(saved_state);
  }

  value->set_zero_checked(true);
}

void SharkTopLevelBlock::do_deferred_zero_check(SharkValue* value,
                                                int         bci,
                                                SharkState* saved_state,
                                                BasicBlock* continue_block) {
  if (value->as_phi()->all_incomers_zero_checked()) {
    builder()->CreateBr(continue_block);
  }
  else {
    iter()->force_bci(start());
    set_current_state(saved_state);
    zero_check_value(value, continue_block);
  }
}

void SharkTopLevelBlock::zero_check_value(SharkValue* value,
                                          BasicBlock* continue_block) {
  BasicBlock *zero_block = builder()->CreateBlock(continue_block, "zero");

  Value *a, *b;
  switch (value->basic_type()) {
  case T_BYTE:
  case T_CHAR:
  case T_SHORT:
  case T_INT:
    a = value->jint_value();
    b = LLVMValue::jint_constant(0);
    break;
  case T_LONG:
    a = value->jlong_value();
    b = LLVMValue::jlong_constant(0);
    break;
  case T_OBJECT:
  case T_ARRAY:
    a = value->jobject_value();
    b = LLVMValue::LLVMValue::null();
    break;
  default:
    tty->print_cr("Unhandled type %s", type2name(value->basic_type()));
    ShouldNotReachHere();
  }

  builder()->CreateCondBr(
    builder()->CreateICmpNE(a, b), continue_block, zero_block);

  builder()->SetInsertPoint(zero_block);
  if (value->is_jobject()) {
    call_vm(
      builder()->throw_NullPointerException(),
      builder()->CreateIntToPtr(
        LLVMValue::intptr_constant((intptr_t) __FILE__),
        PointerType::getUnqual(SharkType::jbyte_type())),
      LLVMValue::jint_constant(__LINE__),
      EX_CHECK_NONE);
  }
  else {
    call_vm(
      builder()->throw_ArithmeticException(),
      builder()->CreateIntToPtr(
        LLVMValue::intptr_constant((intptr_t) __FILE__),
        PointerType::getUnqual(SharkType::jbyte_type())),
      LLVMValue::jint_constant(__LINE__),
      EX_CHECK_NONE);
  }

  Value *pending_exception = get_pending_exception();
  clear_pending_exception();
  handle_exception(pending_exception, EX_CHECK_FULL);
}

void SharkTopLevelBlock::check_bounds(SharkValue* array, SharkValue* index) {
  BasicBlock *out_of_bounds = function()->CreateBlock("out_of_bounds");
  BasicBlock *in_bounds     = function()->CreateBlock("in_bounds");

  Value *length = builder()->CreateArrayLength(array->jarray_value());
  // we use an unsigned comparison to catch negative values
  builder()->CreateCondBr(
    builder()->CreateICmpULT(index->jint_value(), length),
    in_bounds, out_of_bounds);

  builder()->SetInsertPoint(out_of_bounds);
  SharkState *saved_state = current_state()->copy();

  call_vm(
    builder()->throw_ArrayIndexOutOfBoundsException(),
    builder()->CreateIntToPtr(
      LLVMValue::intptr_constant((intptr_t) __FILE__),
      PointerType::getUnqual(SharkType::jbyte_type())),
    LLVMValue::jint_constant(__LINE__),
    index->jint_value(),
    EX_CHECK_NONE);

  Value *pending_exception = get_pending_exception();
  clear_pending_exception();
  handle_exception(pending_exception, EX_CHECK_FULL);

  set_current_state(saved_state);

  builder()->SetInsertPoint(in_bounds);
}

void SharkTopLevelBlock::check_pending_exception(int action) {
  assert(action & EAM_CHECK, "should be");

  BasicBlock *exception    = function()->CreateBlock("exception");
  BasicBlock *no_exception = function()->CreateBlock("no_exception");

  Value *pending_exception = get_pending_exception();
  builder()->CreateCondBr(
    builder()->CreateICmpEQ(pending_exception, LLVMValue::null()),
    no_exception, exception);

  builder()->SetInsertPoint(exception);
  SharkState *saved_state = current_state()->copy();
  if (action & EAM_MONITOR_FUDGE) {
    // The top monitor is marked live, but the exception was thrown
    // while setting it up so we need to mark it dead before we enter
    // any exception handlers as they will not expect it to be there.
    set_num_monitors(num_monitors() - 1);
    action ^= EAM_MONITOR_FUDGE;
  }
  clear_pending_exception();
  handle_exception(pending_exception, action);
  set_current_state(saved_state);

  builder()->SetInsertPoint(no_exception);
}

void SharkTopLevelBlock::compute_exceptions() {
  ciExceptionHandlerStream str(target(), start());

  int exc_count = str.count();
  _exc_handlers = new GrowableArray<ciExceptionHandler*>(exc_count);
  _exceptions   = new GrowableArray<SharkTopLevelBlock*>(exc_count);

  int index = 0;
  for (; !str.is_done(); str.next()) {
    ciExceptionHandler *handler = str.handler();
    if (handler->handler_bci() == -1)
      break;
    _exc_handlers->append(handler);

    // Try and get this exception's handler from typeflow.  We should
    // do it this way always, really, except that typeflow sometimes
    // doesn't record exceptions, even loaded ones, and sometimes it
    // returns them with a different handler bci.  Why???
    SharkTopLevelBlock *block = NULL;
    ciInstanceKlass* klass;
    if (handler->is_catch_all()) {
      klass = java_lang_Throwable_klass();
    }
    else {
      klass = handler->catch_klass();
    }
    for (int i = 0; i < ciblock()->exceptions()->length(); i++) {
      if (klass == ciblock()->exc_klasses()->at(i)) {
        block = function()->block(ciblock()->exceptions()->at(i)->pre_order());
        if (block->start() == handler->handler_bci())
          break;
        else
          block = NULL;
      }
    }

    // If typeflow let us down then try and figure it out ourselves
    if (block == NULL) {
      for (int i = 0; i < function()->block_count(); i++) {
        SharkTopLevelBlock *candidate = function()->block(i);
        if (candidate->start() == handler->handler_bci()) {
          if (block != NULL) {
            NOT_PRODUCT(warning("there may be trouble ahead"));
            block = NULL;
            break;
          }
          block = candidate;
        }
      }
    }
    _exceptions->append(block);
  }
}

void SharkTopLevelBlock::handle_exception(Value* exception, int action) {
  if (action & EAM_HANDLE && num_exceptions() != 0) {
    // Clear the stack and push the exception onto it
    while (xstack_depth())
      pop();
    push(SharkValue::create_jobject(exception, true));

    // Work out how many options we have to check
    bool has_catch_all = exc_handler(num_exceptions() - 1)->is_catch_all();
    int num_options = num_exceptions();
    if (has_catch_all)
      num_options--;

    // Marshal any non-catch-all handlers
    if (num_options > 0) {
      bool all_loaded = true;
      for (int i = 0; i < num_options; i++) {
        if (!exc_handler(i)->catch_klass()->is_loaded()) {
          all_loaded = false;
          break;
        }
      }

      if (all_loaded)
        marshal_exception_fast(num_options);
      else
        marshal_exception_slow(num_options);
    }

    // Install the catch-all handler, if present
    if (has_catch_all) {
      SharkTopLevelBlock* handler = this->exception(num_options);
      assert(handler != NULL, "catch-all handler cannot be unloaded");

      builder()->CreateBr(handler->entry_block());
      handler->add_incoming(current_state());
      return;
    }
  }

  // No exception handler was found; unwind and return
  handle_return(T_VOID, exception);
}

void SharkTopLevelBlock::marshal_exception_fast(int num_options) {
  Value *exception_klass = builder()->CreateValueOfStructEntry(
    xstack(0)->jobject_value(),
    in_ByteSize(oopDesc::klass_offset_in_bytes()),
    SharkType::klass_type(),
    "exception_klass");

  for (int i = 0; i < num_options; i++) {
    Value *check_klass =
      builder()->CreateInlineMetadata(exc_handler(i)->catch_klass(), SharkType::klass_type());

    BasicBlock *not_exact   = function()->CreateBlock("not_exact");
    BasicBlock *not_subtype = function()->CreateBlock("not_subtype");

    builder()->CreateCondBr(
      builder()->CreateICmpEQ(check_klass, exception_klass),
      handler_for_exception(i), not_exact);

    builder()->SetInsertPoint(not_exact);
    builder()->CreateCondBr(
      builder()->CreateICmpNE(
        builder()->CreateCall2(
          builder()->is_subtype_of(), check_klass, exception_klass),
        LLVMValue::jbyte_constant(0)),
      handler_for_exception(i), not_subtype);

    builder()->SetInsertPoint(not_subtype);
  }
}

void SharkTopLevelBlock::marshal_exception_slow(int num_options) {
  int *indexes = NEW_RESOURCE_ARRAY(int, num_options);
  for (int i = 0; i < num_options; i++)
    indexes[i] = exc_handler(i)->catch_klass_index();

  Value *index = call_vm(
    builder()->find_exception_handler(),
    builder()->CreateInlineData(
      indexes,
      num_options * sizeof(int),
      PointerType::getUnqual(SharkType::jint_type())),
    LLVMValue::jint_constant(num_options),
    EX_CHECK_NO_CATCH);

  BasicBlock *no_handler = function()->CreateBlock("no_handler");
  SwitchInst *switchinst = builder()->CreateSwitch(
    index, no_handler, num_options);

  for (int i = 0; i < num_options; i++) {
    switchinst->addCase(
      LLVMValue::jint_constant(i),
      handler_for_exception(i));
  }

  builder()->SetInsertPoint(no_handler);
}

BasicBlock* SharkTopLevelBlock::handler_for_exception(int index) {
  SharkTopLevelBlock *successor = this->exception(index);
  if (successor) {
    successor->add_incoming(current_state());
    return successor->entry_block();
  }
  else {
    return make_trap(
      exc_handler(index)->handler_bci(),
      Deoptimization::make_trap_request(
        Deoptimization::Reason_unhandled,
        Deoptimization::Action_reinterpret));
  }
}

void SharkTopLevelBlock::maybe_add_safepoint() {
  if (current_state()->has_safepointed())
    return;

  BasicBlock *orig_block = builder()->GetInsertBlock();
  SharkState *orig_state = current_state()->copy();

  BasicBlock *do_safepoint = function()->CreateBlock("do_safepoint");
  BasicBlock *safepointed  = function()->CreateBlock("safepointed");

  Value *state = builder()->CreateLoad(
    builder()->CreateIntToPtr(
      LLVMValue::intptr_constant(
        (intptr_t) SafepointSynchronize::address_of_state()),
      PointerType::getUnqual(SharkType::jint_type())),
    "state");

  builder()->CreateCondBr(
    builder()->CreateICmpEQ(
      state,
      LLVMValue::jint_constant(SafepointSynchronize::_synchronizing)),
    do_safepoint, safepointed);

  builder()->SetInsertPoint(do_safepoint);
  call_vm(builder()->safepoint(), EX_CHECK_FULL);
  BasicBlock *safepointed_block = builder()->GetInsertBlock();
  builder()->CreateBr(safepointed);

  builder()->SetInsertPoint(safepointed);
  current_state()->merge(orig_state, orig_block, safepointed_block);

  current_state()->set_has_safepointed(true);
}

void SharkTopLevelBlock::maybe_add_backedge_safepoint() {
  if (current_state()->has_safepointed())
    return;

  for (int i = 0; i < num_successors(); i++) {
    if (successor(i)->can_reach(this)) {
      maybe_add_safepoint();
      break;
    }
  }
}

bool SharkTopLevelBlock::can_reach(SharkTopLevelBlock* other) {
  for (int i = 0; i < function()->block_count(); i++)
    function()->block(i)->_can_reach_visited = false;

  return can_reach_helper(other);
}

bool SharkTopLevelBlock::can_reach_helper(SharkTopLevelBlock* other) {
  if (this == other)
    return true;

  if (_can_reach_visited)
    return false;
  _can_reach_visited = true;

  if (!has_trap()) {
    for (int i = 0; i < num_successors(); i++) {
      if (successor(i)->can_reach_helper(other))
        return true;
    }
  }

  for (int i = 0; i < num_exceptions(); i++) {
    SharkTopLevelBlock *handler = exception(i);
    if (handler && handler->can_reach_helper(other))
      return true;
  }

  return false;
}

BasicBlock* SharkTopLevelBlock::make_trap(int trap_bci, int trap_request) {
  BasicBlock *trap_block = function()->CreateBlock("trap");
  BasicBlock *orig_block = builder()->GetInsertBlock();
  builder()->SetInsertPoint(trap_block);

  int orig_bci = bci();
  iter()->force_bci(trap_bci);

  do_trap(trap_request);

  builder()->SetInsertPoint(orig_block);
  iter()->force_bci(orig_bci);

  return trap_block;
}

void SharkTopLevelBlock::do_trap(int trap_request) {
  decache_for_trap();
  builder()->CreateRet(
    builder()->CreateCall2(
      builder()->uncommon_trap(),
      thread(),
      LLVMValue::jint_constant(trap_request)));
}

void SharkTopLevelBlock::call_register_finalizer(Value *receiver) {
  BasicBlock *orig_block = builder()->GetInsertBlock();
  SharkState *orig_state = current_state()->copy();

  BasicBlock *do_call = function()->CreateBlock("has_finalizer");
  BasicBlock *done    = function()->CreateBlock("done");

  Value *klass = builder()->CreateValueOfStructEntry(
    receiver,
    in_ByteSize(oopDesc::klass_offset_in_bytes()),
    SharkType::oop_type(),
    "klass");

  Value *access_flags = builder()->CreateValueOfStructEntry(
    klass,
    Klass::access_flags_offset(),
    SharkType::jint_type(),
    "access_flags");

  builder()->CreateCondBr(
    builder()->CreateICmpNE(
      builder()->CreateAnd(
        access_flags,
        LLVMValue::jint_constant(JVM_ACC_HAS_FINALIZER)),
      LLVMValue::jint_constant(0)),
    do_call, done);

  builder()->SetInsertPoint(do_call);
  call_vm(builder()->register_finalizer(), receiver, EX_CHECK_FULL);
  BasicBlock *branch_block = builder()->GetInsertBlock();
  builder()->CreateBr(done);

  builder()->SetInsertPoint(done);
  current_state()->merge(orig_state, orig_block, branch_block);
}

void SharkTopLevelBlock::handle_return(BasicType type, Value* exception) {
  assert (exception == NULL || type == T_VOID, "exception OR result, please");

  if (num_monitors()) {
    // Protect our exception across possible monitor release decaches
    if (exception)
      set_oop_tmp(exception);

    // We don't need to check for exceptions thrown here.  If
    // we're returning a value then we just carry on as normal:
    // the caller will see the pending exception and handle it.
    // If we're returning with an exception then that exception
    // takes priority and the release_lock one will be ignored.
    while (num_monitors())
      release_lock(EX_CHECK_NONE);

    // Reload the exception we're throwing
    if (exception)
      exception = get_oop_tmp();
  }

  if (exception) {
    builder()->CreateStore(exception, pending_exception_address());
  }

  Value *result_addr = stack()->CreatePopFrame(type2size[type]);
  if (type != T_VOID) {
    builder()->CreateStore(
      pop_result(type)->generic_value(),
      builder()->CreateIntToPtr(
        result_addr,
        PointerType::getUnqual(SharkType::to_stackType(type))));
  }

  builder()->CreateRet(LLVMValue::jint_constant(0));
}

void SharkTopLevelBlock::do_arraylength() {
  SharkValue *array = pop();
  check_null(array);
  Value *length = builder()->CreateArrayLength(array->jarray_value());
  push(SharkValue::create_jint(length, false));
}

void SharkTopLevelBlock::do_aload(BasicType basic_type) {
  SharkValue *index = pop();
  SharkValue *array = pop();

  check_null(array);
  check_bounds(array, index);

  Value *value = builder()->CreateLoad(
    builder()->CreateArrayAddress(
      array->jarray_value(), basic_type, index->jint_value()));

  Type *stack_type = SharkType::to_stackType(basic_type);
  if (value->getType() != stack_type)
    value = builder()->CreateIntCast(value, stack_type, basic_type != T_CHAR);

  switch (basic_type) {
  case T_BYTE:
  case T_CHAR:
  case T_SHORT:
  case T_INT:
    push(SharkValue::create_jint(value, false));
    break;

  case T_LONG:
    push(SharkValue::create_jlong(value, false));
    break;

  case T_FLOAT:
    push(SharkValue::create_jfloat(value));
    break;

  case T_DOUBLE:
    push(SharkValue::create_jdouble(value));
    break;

  case T_OBJECT:
    // You might expect that array->type()->is_array_klass() would
    // always be true, but it isn't.  If ciTypeFlow detects that a
    // value is always null then that value becomes an untyped null
    // object.  Shark doesn't presently support this, so a generic
    // T_OBJECT is created.  In this case we guess the type using
    // the BasicType we were supplied.  In reality the generated
    // code will never be used, as the null value will be caught
    // by the above null pointer check.
    // http://icedtea.classpath.org/bugzilla/show_bug.cgi?id=324
    push(
      SharkValue::create_generic(
        array->type()->is_array_klass() ?
          ((ciArrayKlass *) array->type())->element_type() :
          ciType::make(basic_type),
        value, false));
    break;

  default:
    tty->print_cr("Unhandled type %s", type2name(basic_type));
    ShouldNotReachHere();
  }
}

void SharkTopLevelBlock::do_astore(BasicType basic_type) {
  SharkValue *svalue = pop();
  SharkValue *index  = pop();
  SharkValue *array  = pop();

  check_null(array);
  check_bounds(array, index);

  Value *value;
  switch (basic_type) {
  case T_BYTE:
  case T_CHAR:
  case T_SHORT:
  case T_INT:
    value = svalue->jint_value();
    break;

  case T_LONG:
    value = svalue->jlong_value();
    break;

  case T_FLOAT:
    value = svalue->jfloat_value();
    break;

  case T_DOUBLE:
    value = svalue->jdouble_value();
    break;

  case T_OBJECT:
    value = svalue->jobject_value();
    // XXX assignability check
    break;

  default:
    tty->print_cr("Unhandled type %s", type2name(basic_type));
    ShouldNotReachHere();
  }

  Type *array_type = SharkType::to_arrayType(basic_type);
  if (value->getType() != array_type)
    value = builder()->CreateIntCast(value, array_type, basic_type != T_CHAR);

  Value *addr = builder()->CreateArrayAddress(
    array->jarray_value(), basic_type, index->jint_value(), "addr");

  builder()->CreateStore(value, addr);

  if (basic_type == T_OBJECT) // XXX or T_ARRAY?
    builder()->CreateUpdateBarrierSet(oopDesc::bs(), addr);
}

void SharkTopLevelBlock::do_return(BasicType type) {
  if (target()->intrinsic_id() == vmIntrinsics::_Object_init)
    call_register_finalizer(local(0)->jobject_value());
  maybe_add_safepoint();
  handle_return(type, NULL);
}

void SharkTopLevelBlock::do_athrow() {
  SharkValue *exception = pop();
  check_null(exception);
  handle_exception(exception->jobject_value(), EX_CHECK_FULL);
}

void SharkTopLevelBlock::do_goto() {
  do_branch(ciTypeFlow::GOTO_TARGET);
}

void SharkTopLevelBlock::do_jsr() {
  push(SharkValue::address_constant(iter()->next_bci()));
  do_branch(ciTypeFlow::GOTO_TARGET);
}

void SharkTopLevelBlock::do_ret() {
  assert(local(iter()->get_index())->address_value() ==
         successor(ciTypeFlow::GOTO_TARGET)->start(), "should be");
  do_branch(ciTypeFlow::GOTO_TARGET);
}

// All propagation of state from one block to the next (via
// dest->add_incoming) is handled by these methods:
//   do_branch
//   do_if_helper
//   do_switch
//   handle_exception

void SharkTopLevelBlock::do_branch(int successor_index) {
  SharkTopLevelBlock *dest = successor(successor_index);
  builder()->CreateBr(dest->entry_block());
  dest->add_incoming(current_state());
}

void SharkTopLevelBlock::do_if(ICmpInst::Predicate p,
                               SharkValue*         b,
                               SharkValue*         a) {
  Value *llvm_a, *llvm_b;
  if (a->is_jobject()) {
    llvm_a = a->intptr_value(builder());
    llvm_b = b->intptr_value(builder());
  }
  else {
    llvm_a = a->jint_value();
    llvm_b = b->jint_value();
  }
  do_if_helper(p, llvm_b, llvm_a, current_state(), current_state());
}

void SharkTopLevelBlock::do_if_helper(ICmpInst::Predicate p,
                                      Value*              b,
                                      Value*              a,
                                      SharkState*         if_taken_state,
                                      SharkState*         not_taken_state) {
  SharkTopLevelBlock *if_taken  = successor(ciTypeFlow::IF_TAKEN);
  SharkTopLevelBlock *not_taken = successor(ciTypeFlow::IF_NOT_TAKEN);

  builder()->CreateCondBr(
    builder()->CreateICmp(p, a, b),
    if_taken->entry_block(), not_taken->entry_block());

  if_taken->add_incoming(if_taken_state);
  not_taken->add_incoming(not_taken_state);
}

void SharkTopLevelBlock::do_switch() {
  int len = switch_table_length();

  SharkTopLevelBlock *dest_block = successor(ciTypeFlow::SWITCH_DEFAULT);
  SwitchInst *switchinst = builder()->CreateSwitch(
    pop()->jint_value(), dest_block->entry_block(), len);
  dest_block->add_incoming(current_state());

  for (int i = 0; i < len; i++) {
    int dest_bci = switch_dest(i);
    if (dest_bci != switch_default_dest()) {
      dest_block = bci_successor(dest_bci);
      switchinst->addCase(
        LLVMValue::jint_constant(switch_key(i)),
        dest_block->entry_block());
      dest_block->add_incoming(current_state());
    }
  }
}

ciMethod* SharkTopLevelBlock::improve_virtual_call(ciMethod*   caller,
                                              ciInstanceKlass* klass,
                                              ciMethod*        dest_method,
                                              ciType*          receiver_type) {
  // If the method is obviously final then we are already done
  if (dest_method->can_be_statically_bound())
    return dest_method;

  // Array methods are all inherited from Object and are monomorphic
  if (receiver_type->is_array_klass() &&
      dest_method->holder() == java_lang_Object_klass())
    return dest_method;

  // This code can replace a virtual call with a direct call if this
  // class is the only one in the entire set of loaded classes that
  // implements this method.  This makes the compiled code dependent
  // on other classes that implement the method not being loaded, a
  // condition which is enforced by the dependency tracker.  If the
  // dependency tracker determines a method has become invalid it
  // will mark it for recompilation, causing running copies to be
  // deoptimized.  Shark currently can't deoptimize arbitrarily like
  // that, so this optimization cannot be used.
  // http://icedtea.classpath.org/bugzilla/show_bug.cgi?id=481

  // All other interesting cases are instance classes
  if (!receiver_type->is_instance_klass())
    return NULL;

  // Attempt to improve the receiver
  ciInstanceKlass* actual_receiver = klass;
  ciInstanceKlass *improved_receiver = receiver_type->as_instance_klass();
  if (improved_receiver->is_loaded() &&
      improved_receiver->is_initialized() &&
      !improved_receiver->is_interface() &&
      improved_receiver->is_subtype_of(actual_receiver)) {
    actual_receiver = improved_receiver;
  }

  // Attempt to find a monomorphic target for this call using
  // class heirachy analysis.
  ciInstanceKlass *calling_klass = caller->holder();
  ciMethod* monomorphic_target =
    dest_method->find_monomorphic_target(calling_klass, klass, actual_receiver);
  if (monomorphic_target != NULL) {
    assert(!monomorphic_target->is_abstract(), "shouldn't be");

    function()->dependencies()->assert_unique_concrete_method(actual_receiver, monomorphic_target);

    // Opto has a bunch of type checking here that I don't
    // understand.  It's to inhibit casting in one direction,
    // possibly because objects in Opto can have inexact
    // types, but I can't even tell which direction it
    // doesn't like.  For now I'm going to block *any* cast.
    if (monomorphic_target != dest_method) {
      if (SharkPerformanceWarnings) {
        warning("found monomorphic target, but inhibited cast:");
        tty->print("  dest_method = ");
        dest_method->print_short_name(tty);
        tty->cr();
        tty->print("  monomorphic_target = ");
        monomorphic_target->print_short_name(tty);
        tty->cr();
      }
      monomorphic_target = NULL;
    }
  }

  // Replace the virtual call with a direct one.  This makes
  // us dependent on that target method not getting overridden
  // by dynamic class loading.
  if (monomorphic_target != NULL) {
    dependencies()->assert_unique_concrete_method(
      actual_receiver, monomorphic_target);
    return monomorphic_target;
  }

  // Because Opto distinguishes exact types from inexact ones
  // it can perform a further optimization to replace calls
  // with non-monomorphic targets if the receiver has an exact
  // type.  We don't mark types this way, so we can't do this.


  return NULL;
}

Value *SharkTopLevelBlock::get_direct_callee(ciMethod* method) {
  return builder()->CreateBitCast(
    builder()->CreateInlineMetadata(method, SharkType::Method_type()),
                                    SharkType::Method_type(),
                                    "callee");
}

Value *SharkTopLevelBlock::get_virtual_callee(SharkValue* receiver,
                                              int vtable_index) {
  Value *klass = builder()->CreateValueOfStructEntry(
    receiver->jobject_value(),
    in_ByteSize(oopDesc::klass_offset_in_bytes()),
    SharkType::oop_type(),
    "klass");

  return builder()->CreateLoad(
    builder()->CreateArrayAddress(
      klass,
      SharkType::Method_type(),
      vtableEntry::size() * wordSize,
      in_ByteSize(InstanceKlass::vtable_start_offset() * wordSize),
      LLVMValue::intptr_constant(vtable_index)),
    "callee");
}

Value* SharkTopLevelBlock::get_interface_callee(SharkValue *receiver,
                                                ciMethod*   method) {
  BasicBlock *loop       = function()->CreateBlock("loop");
  BasicBlock *got_null   = function()->CreateBlock("got_null");
  BasicBlock *not_null   = function()->CreateBlock("not_null");
  BasicBlock *next       = function()->CreateBlock("next");
  BasicBlock *got_entry  = function()->CreateBlock("got_entry");

  // Locate the receiver's itable
  Value *object_klass = builder()->CreateValueOfStructEntry(
    receiver->jobject_value(), in_ByteSize(oopDesc::klass_offset_in_bytes()),
    SharkType::klass_type(),
    "object_klass");

  Value *vtable_start = builder()->CreateAdd(
    builder()->CreatePtrToInt(object_klass, SharkType::intptr_type()),
    LLVMValue::intptr_constant(
      InstanceKlass::vtable_start_offset() * HeapWordSize),
    "vtable_start");

  Value *vtable_length = builder()->CreateValueOfStructEntry(
    object_klass,
    in_ByteSize(InstanceKlass::vtable_length_offset() * HeapWordSize),
    SharkType::jint_type(),
    "vtable_length");
  vtable_length =
    builder()->CreateIntCast(vtable_length, SharkType::intptr_type(), false);

  bool needs_aligning = HeapWordsPerLong > 1;
  Value *itable_start = builder()->CreateAdd(
    vtable_start,
    builder()->CreateShl(
      vtable_length,
      LLVMValue::intptr_constant(exact_log2(vtableEntry::size() * wordSize))),
    needs_aligning ? "" : "itable_start");
  if (needs_aligning) {
    itable_start = builder()->CreateAnd(
      builder()->CreateAdd(
        itable_start, LLVMValue::intptr_constant(BytesPerLong - 1)),
      LLVMValue::intptr_constant(~(BytesPerLong - 1)),
      "itable_start");
  }

  // Locate this interface's entry in the table
  Value *iklass = builder()->CreateInlineMetadata(method->holder(), SharkType::klass_type());
  BasicBlock *loop_entry = builder()->GetInsertBlock();
  builder()->CreateBr(loop);
  builder()->SetInsertPoint(loop);
  PHINode *itable_entry_addr = builder()->CreatePHI(
    SharkType::intptr_type(), 0, "itable_entry_addr");
  itable_entry_addr->addIncoming(itable_start, loop_entry);

  Value *itable_entry = builder()->CreateIntToPtr(
    itable_entry_addr, SharkType::itableOffsetEntry_type(), "itable_entry");

  Value *itable_iklass = builder()->CreateValueOfStructEntry(
    itable_entry,
    in_ByteSize(itableOffsetEntry::interface_offset_in_bytes()),
    SharkType::klass_type(),
    "itable_iklass");

  builder()->CreateCondBr(
    builder()->CreateICmpEQ(itable_iklass, LLVMValue::nullKlass()),
    got_null, not_null);

  // A null entry means that the class doesn't implement the
  // interface, and wasn't the same as the class checked when
  // the interface was resolved.
  builder()->SetInsertPoint(got_null);
  builder()->CreateUnimplemented(__FILE__, __LINE__);
  builder()->CreateUnreachable();

  builder()->SetInsertPoint(not_null);
  builder()->CreateCondBr(
    builder()->CreateICmpEQ(itable_iklass, iklass),
    got_entry, next);

  builder()->SetInsertPoint(next);
  Value *next_entry = builder()->CreateAdd(
    itable_entry_addr,
    LLVMValue::intptr_constant(itableOffsetEntry::size() * wordSize));
  builder()->CreateBr(loop);
  itable_entry_addr->addIncoming(next_entry, next);

  // Locate the method pointer
  builder()->SetInsertPoint(got_entry);
  Value *offset = builder()->CreateValueOfStructEntry(
    itable_entry,
    in_ByteSize(itableOffsetEntry::offset_offset_in_bytes()),
    SharkType::jint_type(),
    "offset");
  offset =
    builder()->CreateIntCast(offset, SharkType::intptr_type(), false);

  return builder()->CreateLoad(
    builder()->CreateIntToPtr(
      builder()->CreateAdd(
        builder()->CreateAdd(
          builder()->CreateAdd(
            builder()->CreatePtrToInt(
              object_klass, SharkType::intptr_type()),
            offset),
          LLVMValue::intptr_constant(
            method->itable_index() * itableMethodEntry::size() * wordSize)),
        LLVMValue::intptr_constant(
          itableMethodEntry::method_offset_in_bytes())),
      PointerType::getUnqual(SharkType::Method_type())),
    "callee");
}

void SharkTopLevelBlock::do_call() {
  // Set frequently used booleans
  bool is_static = bc() == Bytecodes::_invokestatic;
  bool is_virtual = bc() == Bytecodes::_invokevirtual;
  bool is_interface = bc() == Bytecodes::_invokeinterface;

  // Find the method being called
  bool will_link;
  ciSignature* sig;
  ciMethod *dest_method = iter()->get_method(will_link, &sig);

  assert(will_link, "typeflow responsibility");
  assert(dest_method->is_static() == is_static, "must match bc");

  // Find the class of the method being called.  Note
  // that the superclass check in the second assertion
  // is to cope with a hole in the spec that allows for
  // invokeinterface instructions where the resolved
  // method is a virtual method in java.lang.Object.
  // javac doesn't generate code like that, but there's
  // no reason a compliant Java compiler might not.
  ciInstanceKlass *holder_klass  = dest_method->holder();
  assert(holder_klass->is_loaded(), "scan_for_traps responsibility");
  assert(holder_klass->is_interface() ||
         holder_klass->super() == NULL ||
         !is_interface, "must match bc");

  bool is_forced_virtual = is_interface && holder_klass == java_lang_Object_klass();

  ciKlass *holder = iter()->get_declared_method_holder();
  ciInstanceKlass *klass =
    ciEnv::get_instance_klass_for_declared_method_holder(holder);

  if (is_forced_virtual) {
    klass = java_lang_Object_klass();
  }

  // Find the receiver in the stack.  We do this before
  // trying to inline because the inliner can only use
  // zero-checked values, not being able to perform the
  // check itself.
  SharkValue *receiver = NULL;
  if (!is_static) {
    receiver = xstack(dest_method->arg_size() - 1);
    check_null(receiver);
  }

  // Try to improve non-direct calls
  bool call_is_virtual = is_virtual || is_interface;
  ciMethod *call_method = dest_method;
  if (call_is_virtual) {
    ciMethod *optimized_method = improve_virtual_call(
      target(), klass, dest_method, receiver->type());
    if (optimized_method) {
      call_method = optimized_method;
      call_is_virtual = false;
    }
  }

  // Try to inline the call
  if (!call_is_virtual) {
    if (SharkInliner::attempt_inline(call_method, current_state())) {
      return;
    }
  }

  // Find the method we are calling
  Value *callee;
  if (call_is_virtual) {
    if (is_virtual || is_forced_virtual) {
      assert(klass->is_linked(), "scan_for_traps responsibility");
      int vtable_index = call_method->resolve_vtable_index(
        target()->holder(), klass);
      assert(vtable_index >= 0, "should be");
      callee = get_virtual_callee(receiver, vtable_index);
    }
    else {
      assert(is_interface, "should be");
      callee = get_interface_callee(receiver, call_method);
    }
  }
  else {
    callee = get_direct_callee(call_method);
  }

  // Load the SharkEntry from the callee
  Value *base_pc = builder()->CreateValueOfStructEntry(
    callee, Method::from_interpreted_offset(),
    SharkType::intptr_type(),
    "base_pc");

  // Load the entry point from the SharkEntry
  Value *entry_point = builder()->CreateLoad(
    builder()->CreateIntToPtr(
      builder()->CreateAdd(
        base_pc,
        LLVMValue::intptr_constant(in_bytes(ZeroEntry::entry_point_offset()))),
      PointerType::getUnqual(
        PointerType::getUnqual(SharkType::entry_point_type()))),
    "entry_point");

  // Make the call
  decache_for_Java_call(call_method);
  Value *deoptimized_frames = builder()->CreateCall3(
    entry_point, callee, base_pc, thread());

  // If the callee got deoptimized then reexecute in the interpreter
  BasicBlock *reexecute      = function()->CreateBlock("reexecute");
  BasicBlock *call_completed = function()->CreateBlock("call_completed");
  builder()->CreateCondBr(
    builder()->CreateICmpNE(deoptimized_frames, LLVMValue::jint_constant(0)),
    reexecute, call_completed);

  builder()->SetInsertPoint(reexecute);
  builder()->CreateCall2(
    builder()->deoptimized_entry_point(),
    builder()->CreateSub(deoptimized_frames, LLVMValue::jint_constant(1)),
    thread());
  builder()->CreateBr(call_completed);

  // Cache after the call
  builder()->SetInsertPoint(call_completed);
  cache_after_Java_call(call_method);

  // Check for pending exceptions
  check_pending_exception(EX_CHECK_FULL);

  // Mark that a safepoint check has occurred
  current_state()->set_has_safepointed(true);
}

bool SharkTopLevelBlock::static_subtype_check(ciKlass* check_klass,
                                              ciKlass* object_klass) {
  // If the class we're checking against is java.lang.Object
  // then this is a no brainer.  Apparently this can happen
  // in reflective code...
  if (check_klass == java_lang_Object_klass())
    return true;

  // Perform a subtype check.  NB in opto's code for this
  // (GraphKit::static_subtype_check) it says that static
  // interface types cannot be trusted, and if opto can't
  // trust them then I assume we can't either.
  if (object_klass->is_loaded() && !object_klass->is_interface()) {
    if (object_klass == check_klass)
      return true;

    if (check_klass->is_loaded() && object_klass->is_subtype_of(check_klass))
      return true;
  }

  return false;
}

void SharkTopLevelBlock::do_instance_check() {
  // Get the class we're checking against
  bool will_link;
  ciKlass *check_klass = iter()->get_klass(will_link);

  // Get the class of the object we're checking
  ciKlass *object_klass = xstack(0)->type()->as_klass();

  // Can we optimize this check away?
  if (static_subtype_check(check_klass, object_klass)) {
    if (bc() == Bytecodes::_instanceof) {
      pop();
      push(SharkValue::jint_constant(1));
    }
    return;
  }

  // Need to check this one at runtime
  if (will_link)
    do_full_instance_check(check_klass);
  else
    do_trapping_instance_check(check_klass);
}

bool SharkTopLevelBlock::maybe_do_instanceof_if() {
  // Get the class we're checking against
  bool will_link;
  ciKlass *check_klass = iter()->get_klass(will_link);

  // If the class is unloaded then the instanceof
  // cannot possibly succeed.
  if (!will_link)
    return false;

  // Keep a copy of the object we're checking
  SharkValue *old_object = xstack(0);

  // Get the class of the object we're checking
  ciKlass *object_klass = old_object->type()->as_klass();

  // If the instanceof can be optimized away at compile time
  // then any subsequent checkcasts will be too so we handle
  // it normally.
  if (static_subtype_check(check_klass, object_klass))
    return false;

  // Perform the instance check
  do_full_instance_check(check_klass);
  Value *result = pop()->jint_value();

  // Create the casted object
  SharkValue *new_object = SharkValue::create_generic(
    check_klass, old_object->jobject_value(), old_object->zero_checked());

  // Create two copies of the current state, one with the
  // original object and one with all instances of the
  // original object replaced with the new, casted object.
  SharkState *new_state = current_state();
  SharkState *old_state = new_state->copy();
  new_state->replace_all(old_object, new_object);

  // Perform the check-and-branch
  switch (iter()->next_bc()) {
  case Bytecodes::_ifeq:
    // branch if not an instance
    do_if_helper(
      ICmpInst::ICMP_EQ,
      LLVMValue::jint_constant(0), result,
      old_state, new_state);
    break;

  case Bytecodes::_ifne:
    // branch if an instance
    do_if_helper(
      ICmpInst::ICMP_NE,
      LLVMValue::jint_constant(0), result,
      new_state, old_state);
    break;

  default:
    ShouldNotReachHere();
  }

  return true;
}

void SharkTopLevelBlock::do_full_instance_check(ciKlass* klass) {
  BasicBlock *not_null      = function()->CreateBlock("not_null");
  BasicBlock *subtype_check = function()->CreateBlock("subtype_check");
  BasicBlock *is_instance   = function()->CreateBlock("is_instance");
  BasicBlock *not_instance  = function()->CreateBlock("not_instance");
  BasicBlock *merge1        = function()->CreateBlock("merge1");
  BasicBlock *merge2        = function()->CreateBlock("merge2");

  enum InstanceCheckStates {
    IC_IS_NULL,
    IC_IS_INSTANCE,
    IC_NOT_INSTANCE,
  };

  // Pop the object off the stack
  Value *object = pop()->jobject_value();

  // Null objects aren't instances of anything
  builder()->CreateCondBr(
    builder()->CreateICmpEQ(object, LLVMValue::null()),
    merge2, not_null);
  BasicBlock *null_block = builder()->GetInsertBlock();

  // Get the class we're checking against
  builder()->SetInsertPoint(not_null);
  Value *check_klass = builder()->CreateInlineMetadata(klass, SharkType::klass_type());

  // Get the class of the object being tested
  Value *object_klass = builder()->CreateValueOfStructEntry(
    object, in_ByteSize(oopDesc::klass_offset_in_bytes()),
    SharkType::klass_type(),
    "object_klass");

  // Perform the check
  builder()->CreateCondBr(
    builder()->CreateICmpEQ(check_klass, object_klass),
    is_instance, subtype_check);

  builder()->SetInsertPoint(subtype_check);
  builder()->CreateCondBr(
    builder()->CreateICmpNE(
      builder()->CreateCall2(
        builder()->is_subtype_of(), check_klass, object_klass),
      LLVMValue::jbyte_constant(0)),
    is_instance, not_instance);

  builder()->SetInsertPoint(is_instance);
  builder()->CreateBr(merge1);

  builder()->SetInsertPoint(not_instance);
  builder()->CreateBr(merge1);

  // First merge
  builder()->SetInsertPoint(merge1);
  PHINode *nonnull_result = builder()->CreatePHI(
    SharkType::jint_type(), 0, "nonnull_result");
  nonnull_result->addIncoming(
    LLVMValue::jint_constant(IC_IS_INSTANCE), is_instance);
  nonnull_result->addIncoming(
    LLVMValue::jint_constant(IC_NOT_INSTANCE), not_instance);
  BasicBlock *nonnull_block = builder()->GetInsertBlock();
  builder()->CreateBr(merge2);

  // Second merge
  builder()->SetInsertPoint(merge2);
  PHINode *result = builder()->CreatePHI(
    SharkType::jint_type(), 0, "result");
  result->addIncoming(LLVMValue::jint_constant(IC_IS_NULL), null_block);
  result->addIncoming(nonnull_result, nonnull_block);

  // Handle the result
  if (bc() == Bytecodes::_checkcast) {
    BasicBlock *failure = function()->CreateBlock("failure");
    BasicBlock *success = function()->CreateBlock("success");

    builder()->CreateCondBr(
      builder()->CreateICmpNE(
        result, LLVMValue::jint_constant(IC_NOT_INSTANCE)),
      success, failure);

    builder()->SetInsertPoint(failure);
    SharkState *saved_state = current_state()->copy();

    call_vm(
      builder()->throw_ClassCastException(),
      builder()->CreateIntToPtr(
        LLVMValue::intptr_constant((intptr_t) __FILE__),
        PointerType::getUnqual(SharkType::jbyte_type())),
      LLVMValue::jint_constant(__LINE__),
      EX_CHECK_NONE);

    Value *pending_exception = get_pending_exception();
    clear_pending_exception();
    handle_exception(pending_exception, EX_CHECK_FULL);

    set_current_state(saved_state);
    builder()->SetInsertPoint(success);
    push(SharkValue::create_generic(klass, object, false));
  }
  else {
    push(
      SharkValue::create_jint(
        builder()->CreateIntCast(
          builder()->CreateICmpEQ(
            result, LLVMValue::jint_constant(IC_IS_INSTANCE)),
          SharkType::jint_type(), false), false));
  }
}

void SharkTopLevelBlock::do_trapping_instance_check(ciKlass* klass) {
  BasicBlock *not_null = function()->CreateBlock("not_null");
  BasicBlock *is_null  = function()->CreateBlock("null");

  // Leave the object on the stack so it's there if we trap
  builder()->CreateCondBr(
    builder()->CreateICmpEQ(xstack(0)->jobject_value(), LLVMValue::null()),
    is_null, not_null);
  SharkState *saved_state = current_state()->copy();

  // If it's not null then we need to trap
  builder()->SetInsertPoint(not_null);
  set_current_state(saved_state->copy());
  do_trap(
    Deoptimization::make_trap_request(
      Deoptimization::Reason_uninitialized,
      Deoptimization::Action_reinterpret));

  // If it's null then we're ok
  builder()->SetInsertPoint(is_null);
  set_current_state(saved_state);
  if (bc() == Bytecodes::_checkcast) {
    push(SharkValue::create_generic(klass, pop()->jobject_value(), false));
  }
  else {
    pop();
    push(SharkValue::jint_constant(0));
  }
}

void SharkTopLevelBlock::do_new() {
  bool will_link;
  ciInstanceKlass* klass = iter()->get_klass(will_link)->as_instance_klass();
  assert(will_link, "typeflow responsibility");

  BasicBlock *got_tlab            = NULL;
  BasicBlock *heap_alloc          = NULL;
  BasicBlock *retry               = NULL;
  BasicBlock *got_heap            = NULL;
  BasicBlock *initialize          = NULL;
  BasicBlock *got_fast            = NULL;
  BasicBlock *slow_alloc_and_init = NULL;
  BasicBlock *got_slow            = NULL;
  BasicBlock *push_object         = NULL;

  SharkState *fast_state = NULL;

  Value *tlab_object = NULL;
  Value *heap_object = NULL;
  Value *fast_object = NULL;
  Value *slow_object = NULL;
  Value *object      = NULL;

  // The fast path
  if (!Klass::layout_helper_needs_slow_path(klass->layout_helper())) {
    if (UseTLAB) {
      got_tlab          = function()->CreateBlock("got_tlab");
      heap_alloc        = function()->CreateBlock("heap_alloc");
    }
    retry               = function()->CreateBlock("retry");
    got_heap            = function()->CreateBlock("got_heap");
    initialize          = function()->CreateBlock("initialize");
    slow_alloc_and_init = function()->CreateBlock("slow_alloc_and_init");
    push_object         = function()->CreateBlock("push_object");

    size_t size_in_bytes = klass->size_helper() << LogHeapWordSize;

    // Thread local allocation
    if (UseTLAB) {
      Value *top_addr = builder()->CreateAddressOfStructEntry(
        thread(), Thread::tlab_top_offset(),
        PointerType::getUnqual(SharkType::intptr_type()),
        "top_addr");

      Value *end = builder()->CreateValueOfStructEntry(
        thread(), Thread::tlab_end_offset(),
        SharkType::intptr_type(),
        "end");

      Value *old_top = builder()->CreateLoad(top_addr, "old_top");
      Value *new_top = builder()->CreateAdd(
        old_top, LLVMValue::intptr_constant(size_in_bytes));

      builder()->CreateCondBr(
        builder()->CreateICmpULE(new_top, end),
        got_tlab, heap_alloc);

      builder()->SetInsertPoint(got_tlab);
      tlab_object = builder()->CreateIntToPtr(
        old_top, SharkType::oop_type(), "tlab_object");

      builder()->CreateStore(new_top, top_addr);
      builder()->CreateBr(initialize);

      builder()->SetInsertPoint(heap_alloc);
    }

    // Heap allocation
    Value *top_addr = builder()->CreateIntToPtr(
        LLVMValue::intptr_constant((intptr_t) Universe::heap()->top_addr()),
      PointerType::getUnqual(SharkType::intptr_type()),
      "top_addr");

    Value *end = builder()->CreateLoad(
      builder()->CreateIntToPtr(
        LLVMValue::intptr_constant((intptr_t) Universe::heap()->end_addr()),
        PointerType::getUnqual(SharkType::intptr_type())),
      "end");

    builder()->CreateBr(retry);
    builder()->SetInsertPoint(retry);

    Value *old_top = builder()->CreateLoad(top_addr, "top");
    Value *new_top = builder()->CreateAdd(
      old_top, LLVMValue::intptr_constant(size_in_bytes));

    builder()->CreateCondBr(
      builder()->CreateICmpULE(new_top, end),
      got_heap, slow_alloc_and_init);

    builder()->SetInsertPoint(got_heap);
    heap_object = builder()->CreateIntToPtr(
      old_top, SharkType::oop_type(), "heap_object");

    Value *check = builder()->CreateAtomicCmpXchg(top_addr, old_top, new_top, llvm::SequentiallyConsistent);
    builder()->CreateCondBr(
      builder()->CreateICmpEQ(old_top, check),
      initialize, retry);

    // Initialize the object
    builder()->SetInsertPoint(initialize);
    if (tlab_object) {
      PHINode *phi = builder()->CreatePHI(
        SharkType::oop_type(), 0, "fast_object");
      phi->addIncoming(tlab_object, got_tlab);
      phi->addIncoming(heap_object, got_heap);
      fast_object = phi;
    }
    else {
      fast_object = heap_object;
    }

    builder()->CreateMemset(
      builder()->CreateBitCast(
        fast_object, PointerType::getUnqual(SharkType::jbyte_type())),
      LLVMValue::jbyte_constant(0),
      LLVMValue::jint_constant(size_in_bytes),
      LLVMValue::jint_constant(HeapWordSize));

    Value *mark_addr = builder()->CreateAddressOfStructEntry(
      fast_object, in_ByteSize(oopDesc::mark_offset_in_bytes()),
      PointerType::getUnqual(SharkType::intptr_type()),
      "mark_addr");

    Value *klass_addr = builder()->CreateAddressOfStructEntry(
      fast_object, in_ByteSize(oopDesc::klass_offset_in_bytes()),
      PointerType::getUnqual(SharkType::klass_type()),
      "klass_addr");

    // Set the mark
    intptr_t mark;
    if (UseBiasedLocking) {
      Unimplemented();
    }
    else {
      mark = (intptr_t) markOopDesc::prototype();
    }
    builder()->CreateStore(LLVMValue::intptr_constant(mark), mark_addr);

    // Set the class
    Value *rtklass = builder()->CreateInlineMetadata(klass, SharkType::klass_type());
    builder()->CreateStore(rtklass, klass_addr);
    got_fast = builder()->GetInsertBlock();

    builder()->CreateBr(push_object);
    builder()->SetInsertPoint(slow_alloc_and_init);
    fast_state = current_state()->copy();
  }

  // The slow path
  call_vm(
    builder()->new_instance(),
    LLVMValue::jint_constant(iter()->get_klass_index()),
    EX_CHECK_FULL);
  slow_object = get_vm_result();
  got_slow = builder()->GetInsertBlock();

  // Push the object
  if (push_object) {
    builder()->CreateBr(push_object);
    builder()->SetInsertPoint(push_object);
  }
  if (fast_object) {
    PHINode *phi = builder()->CreatePHI(SharkType::oop_type(), 0, "object");
    phi->addIncoming(fast_object, got_fast);
    phi->addIncoming(slow_object, got_slow);
    object = phi;
    current_state()->merge(fast_state, got_fast, got_slow);
  }
  else {
    object = slow_object;
  }

  push(SharkValue::create_jobject(object, true));
}

void SharkTopLevelBlock::do_newarray() {
  BasicType type = (BasicType) iter()->get_index();

  call_vm(
    builder()->newarray(),
    LLVMValue::jint_constant(type),
    pop()->jint_value(),
    EX_CHECK_FULL);

  ciArrayKlass *array_klass = ciArrayKlass::make(ciType::make(type));
  push(SharkValue::create_generic(array_klass, get_vm_result(), true));
}

void SharkTopLevelBlock::do_anewarray() {
  bool will_link;
  ciKlass *klass = iter()->get_klass(will_link);
  assert(will_link, "typeflow responsibility");

  ciObjArrayKlass *array_klass = ciObjArrayKlass::make(klass);
  if (!array_klass->is_loaded()) {
    Unimplemented();
  }

  call_vm(
    builder()->anewarray(),
    LLVMValue::jint_constant(iter()->get_klass_index()),
    pop()->jint_value(),
    EX_CHECK_FULL);

  push(SharkValue::create_generic(array_klass, get_vm_result(), true));
}

void SharkTopLevelBlock::do_multianewarray() {
  bool will_link;
  ciArrayKlass *array_klass = iter()->get_klass(will_link)->as_array_klass();
  assert(will_link, "typeflow responsibility");

  // The dimensions are stack values, so we use their slots for the
  // dimensions array.  Note that we are storing them in the reverse
  // of normal stack order.
  int ndims = iter()->get_dimensions();

  Value *dimensions = stack()->slot_addr(
    stack()->stack_slots_offset() + max_stack() - xstack_depth(),
    ArrayType::get(SharkType::jint_type(), ndims),
    "dimensions");

  for (int i = 0; i < ndims; i++) {
    builder()->CreateStore(
      xstack(ndims - 1 - i)->jint_value(),
      builder()->CreateStructGEP(dimensions, i));
  }

  call_vm(
    builder()->multianewarray(),
    LLVMValue::jint_constant(iter()->get_klass_index()),
    LLVMValue::jint_constant(ndims),
    builder()->CreateStructGEP(dimensions, 0),
    EX_CHECK_FULL);

  // Now we can pop the dimensions off the stack
  for (int i = 0; i < ndims; i++)
    pop();

  push(SharkValue::create_generic(array_klass, get_vm_result(), true));
}

void SharkTopLevelBlock::acquire_method_lock() {
  Value *lockee;
  if (target()->is_static()) {
    lockee = builder()->CreateInlineOop(target()->holder()->java_mirror());
  }
  else
    lockee = local(0)->jobject_value();

  iter()->force_bci(start()); // for the decache in acquire_lock
  acquire_lock(lockee, EX_CHECK_NO_CATCH);
}

void SharkTopLevelBlock::do_monitorenter() {
  SharkValue *lockee = pop();
  check_null(lockee);
  acquire_lock(lockee->jobject_value(), EX_CHECK_FULL);
}

void SharkTopLevelBlock::do_monitorexit() {
  pop(); // don't need this (monitors are block structured)
  release_lock(EX_CHECK_NO_CATCH);
}

void SharkTopLevelBlock::acquire_lock(Value *lockee, int exception_action) {
  BasicBlock *try_recursive = function()->CreateBlock("try_recursive");
  BasicBlock *got_recursive = function()->CreateBlock("got_recursive");
  BasicBlock *not_recursive = function()->CreateBlock("not_recursive");
  BasicBlock *acquired_fast = function()->CreateBlock("acquired_fast");
  BasicBlock *lock_acquired = function()->CreateBlock("lock_acquired");

  int monitor = num_monitors();
  Value *monitor_addr        = stack()->monitor_addr(monitor);
  Value *monitor_object_addr = stack()->monitor_object_addr(monitor);
  Value *monitor_header_addr = stack()->monitor_header_addr(monitor);

  // Store the object and mark the slot as live
  builder()->CreateStore(lockee, monitor_object_addr);
  set_num_monitors(monitor + 1);

  // Try a simple lock
  Value *mark_addr = builder()->CreateAddressOfStructEntry(
    lockee, in_ByteSize(oopDesc::mark_offset_in_bytes()),
    PointerType::getUnqual(SharkType::intptr_type()),
    "mark_addr");

  Value *mark = builder()->CreateLoad(mark_addr, "mark");
  Value *disp = builder()->CreateOr(
    mark, LLVMValue::intptr_constant(markOopDesc::unlocked_value), "disp");
  builder()->CreateStore(disp, monitor_header_addr);

  Value *lock = builder()->CreatePtrToInt(
    monitor_header_addr, SharkType::intptr_type());
  Value *check = builder()->CreateAtomicCmpXchg(mark_addr, disp, lock, llvm::Acquire);
  builder()->CreateCondBr(
    builder()->CreateICmpEQ(disp, check),
    acquired_fast, try_recursive);

  // Locking failed, but maybe this thread already owns it
  builder()->SetInsertPoint(try_recursive);
  Value *addr = builder()->CreateAnd(
    disp,
    LLVMValue::intptr_constant(~markOopDesc::lock_mask_in_place));

  // NB we use the entire stack, but JavaThread::is_lock_owned()
  // uses a more limited range.  I don't think it hurts though...
  Value *stack_limit = builder()->CreateValueOfStructEntry(
    thread(), Thread::stack_base_offset(),
    SharkType::intptr_type(),
    "stack_limit");

  assert(sizeof(size_t) == sizeof(intptr_t), "should be");
  Value *stack_size = builder()->CreateValueOfStructEntry(
    thread(), Thread::stack_size_offset(),
    SharkType::intptr_type(),
    "stack_size");

  Value *stack_start =
    builder()->CreateSub(stack_limit, stack_size, "stack_start");

  builder()->CreateCondBr(
    builder()->CreateAnd(
      builder()->CreateICmpUGE(addr, stack_start),
      builder()->CreateICmpULT(addr, stack_limit)),
    got_recursive, not_recursive);

  builder()->SetInsertPoint(got_recursive);
  builder()->CreateStore(LLVMValue::intptr_constant(0), monitor_header_addr);
  builder()->CreateBr(acquired_fast);

  // Create an edge for the state merge
  builder()->SetInsertPoint(acquired_fast);
  SharkState *fast_state = current_state()->copy();
  builder()->CreateBr(lock_acquired);

  // It's not a recursive case so we need to drop into the runtime
  builder()->SetInsertPoint(not_recursive);
  call_vm(
    builder()->monitorenter(), monitor_addr,
    exception_action | EAM_MONITOR_FUDGE);
  BasicBlock *acquired_slow = builder()->GetInsertBlock();
  builder()->CreateBr(lock_acquired);

  // All done
  builder()->SetInsertPoint(lock_acquired);
  current_state()->merge(fast_state, acquired_fast, acquired_slow);
}

void SharkTopLevelBlock::release_lock(int exception_action) {
  BasicBlock *not_recursive = function()->CreateBlock("not_recursive");
  BasicBlock *released_fast = function()->CreateBlock("released_fast");
  BasicBlock *slow_path     = function()->CreateBlock("slow_path");
  BasicBlock *lock_released = function()->CreateBlock("lock_released");

  int monitor = num_monitors() - 1;
  Value *monitor_addr        = stack()->monitor_addr(monitor);
  Value *monitor_object_addr = stack()->monitor_object_addr(monitor);
  Value *monitor_header_addr = stack()->monitor_header_addr(monitor);

  // If it is recursive then we're already done
  Value *disp = builder()->CreateLoad(monitor_header_addr);
  builder()->CreateCondBr(
    builder()->CreateICmpEQ(disp, LLVMValue::intptr_constant(0)),
    released_fast, not_recursive);

  // Try a simple unlock
  builder()->SetInsertPoint(not_recursive);

  Value *lock = builder()->CreatePtrToInt(
    monitor_header_addr, SharkType::intptr_type());

  Value *lockee = builder()->CreateLoad(monitor_object_addr);

  Value *mark_addr = builder()->CreateAddressOfStructEntry(
    lockee, in_ByteSize(oopDesc::mark_offset_in_bytes()),
    PointerType::getUnqual(SharkType::intptr_type()),
    "mark_addr");

  Value *check = builder()->CreateAtomicCmpXchg(mark_addr, lock, disp, llvm::Release);
  builder()->CreateCondBr(
    builder()->CreateICmpEQ(lock, check),
    released_fast, slow_path);

  // Create an edge for the state merge
  builder()->SetInsertPoint(released_fast);
  SharkState *fast_state = current_state()->copy();
  builder()->CreateBr(lock_released);

  // Need to drop into the runtime to release this one
  builder()->SetInsertPoint(slow_path);
  call_vm(builder()->monitorexit(), monitor_addr, exception_action);
  BasicBlock *released_slow = builder()->GetInsertBlock();
  builder()->CreateBr(lock_released);

  // All done
  builder()->SetInsertPoint(lock_released);
  current_state()->merge(fast_state, released_fast, released_slow);

  // The object slot is now dead
  set_num_monitors(monitor);
}
