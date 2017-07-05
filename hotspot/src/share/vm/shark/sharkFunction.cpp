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

#include "precompiled.hpp"
#include "ci/ciTypeFlow.hpp"
#include "memory/allocation.hpp"
#include "shark/llvmHeaders.hpp"
#include "shark/llvmValue.hpp"
#include "shark/sharkBuilder.hpp"
#include "shark/sharkEntry.hpp"
#include "shark/sharkFunction.hpp"
#include "shark/sharkState.hpp"
#include "shark/sharkTopLevelBlock.hpp"
#include "shark/shark_globals.hpp"
#include "utilities/debug.hpp"

using namespace llvm;

void SharkFunction::initialize(const char *name) {
  // Create the function
  _function = Function::Create(
    entry_point_type(),
    GlobalVariable::InternalLinkage,
    name);

  // Get our arguments
  Function::arg_iterator ai = function()->arg_begin();
  Argument *method = ai++;
  method->setName("method");
  Argument *osr_buf = NULL;
  if (is_osr()) {
    osr_buf = ai++;
    osr_buf->setName("osr_buf");
  }
  Argument *base_pc = ai++;
  base_pc->setName("base_pc");
  code_buffer()->set_base_pc(base_pc);
  Argument *thread = ai++;
  thread->setName("thread");
  set_thread(thread);

  // Create the list of blocks
  set_block_insertion_point(NULL);
  _blocks = NEW_RESOURCE_ARRAY(SharkTopLevelBlock*, block_count());
  for (int i = 0; i < block_count(); i++) {
    ciTypeFlow::Block *b = flow()->pre_order_at(i);

    // Work around a bug in pre_order_at() that does not return
    // the correct pre-ordering.  If pre_order_at() were correct
    // this line could simply be:
    // _blocks[i] = new SharkTopLevelBlock(this, b);
    _blocks[b->pre_order()] = new SharkTopLevelBlock(this, b);
  }

  // Walk the tree from the start block to determine which
  // blocks are entered and which blocks require phis
  SharkTopLevelBlock *start_block = block(flow()->start_block_num());
  assert(start_block->start() == flow()->start_bci(), "blocks out of order");
  start_block->enter();

  // Initialize all entered blocks
  for (int i = 0; i < block_count(); i++) {
    if (block(i)->entered())
      block(i)->initialize();
  }

  // Create and push our stack frame
  set_block_insertion_point(&function()->front());
  builder()->SetInsertPoint(CreateBlock());
  _stack = SharkStack::CreateBuildAndPushFrame(this, method);

  // Create the entry state
  SharkState *entry_state;
  if (is_osr()) {
    entry_state = new SharkOSREntryState(start_block, method, osr_buf);

    // Free the OSR buffer
    builder()->CreateCall(builder()->osr_migration_end(), osr_buf);
  }
  else {
    entry_state = new SharkNormalEntryState(start_block, method);

    // Lock if necessary
    if (is_synchronized()) {
      SharkTopLevelBlock *locker =
        new SharkTopLevelBlock(this, start_block->ciblock());
      locker->add_incoming(entry_state);

      set_block_insertion_point(start_block->entry_block());
      locker->acquire_method_lock();

      entry_state = locker->current_state();
    }
  }

  // Transition into the method proper
  start_block->add_incoming(entry_state);
  builder()->CreateBr(start_block->entry_block());

  // Parse the blocks
  for (int i = 0; i < block_count(); i++) {
    if (!block(i)->entered())
      continue;

    if (i + 1 < block_count())
      set_block_insertion_point(block(i + 1)->entry_block());
    else
      set_block_insertion_point(NULL);

    block(i)->emit_IR();
  }
  do_deferred_zero_checks();
}

class DeferredZeroCheck : public SharkTargetInvariants {
 public:
  DeferredZeroCheck(SharkTopLevelBlock* block, SharkValue* value)
    : SharkTargetInvariants(block),
      _block(block),
      _value(value),
      _bci(block->bci()),
      _state(block->current_state()->copy()),
      _check_block(builder()->GetInsertBlock()),
      _continue_block(function()->CreateBlock("not_zero")) {
    builder()->SetInsertPoint(continue_block());
  }

 private:
  SharkTopLevelBlock* _block;
  SharkValue*         _value;
  int                 _bci;
  SharkState*         _state;
  BasicBlock*         _check_block;
  BasicBlock*         _continue_block;

 public:
  SharkTopLevelBlock* block() const {
    return _block;
  }
  SharkValue* value() const {
    return _value;
  }
  int bci() const {
    return _bci;
  }
  SharkState* state() const {
    return _state;
  }
  BasicBlock* check_block() const {
    return _check_block;
  }
  BasicBlock* continue_block() const {
    return _continue_block;
  }

 public:
  SharkFunction* function() const {
    return block()->function();
  }

 public:
  void process() const {
    builder()->SetInsertPoint(check_block());
    block()->do_deferred_zero_check(value(), bci(), state(), continue_block());
  }
};

void SharkFunction::add_deferred_zero_check(SharkTopLevelBlock* block,
                                            SharkValue*         value) {
  deferred_zero_checks()->append(new DeferredZeroCheck(block, value));
}

void SharkFunction::do_deferred_zero_checks() {
  for (int i = 0; i < deferred_zero_checks()->length(); i++)
    deferred_zero_checks()->at(i)->process();
}
