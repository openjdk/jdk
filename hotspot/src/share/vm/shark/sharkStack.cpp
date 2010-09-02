/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_sharkStack.cpp.incl"

using namespace llvm;

void SharkStack::initialize(Value* method) {
  bool setup_sp_and_method = (method != NULL);

  int locals_words  = max_locals();
  int extra_locals  = locals_words - arg_size();
  int header_words  = SharkFrame::header_words;
  int monitor_words = max_monitors()*frame::interpreter_frame_monitor_size();
  int stack_words   = max_stack();
  int frame_words   = header_words + monitor_words + stack_words;

  _extended_frame_size = frame_words + locals_words;

  // Update the stack pointer
  Value *stack_pointer = builder()->CreateSub(
    CreateLoadStackPointer(),
    LLVMValue::intptr_constant((frame_words + extra_locals) * wordSize));
  CreateStackOverflowCheck(stack_pointer);
  if (setup_sp_and_method)
    CreateStoreStackPointer(stack_pointer);

  // Create the frame
  _frame = builder()->CreateIntToPtr(
    stack_pointer,
    PointerType::getUnqual(
      ArrayType::get(SharkType::intptr_type(), extended_frame_size())),
    "frame");
  int offset = 0;

  // Expression stack
  _stack_slots_offset = offset;
  offset += stack_words;

  // Monitors
  _monitors_slots_offset = offset;
  offset += monitor_words;

  // Temporary oop slot
  _oop_tmp_slot_offset = offset++;

  // Method pointer
  _method_slot_offset = offset++;
  if (setup_sp_and_method) {
    builder()->CreateStore(
      method, slot_addr(method_slot_offset(), SharkType::methodOop_type()));
  }

  // Unextended SP
  builder()->CreateStore(stack_pointer, slot_addr(offset++));

  // PC
  _pc_slot_offset = offset++;

  // Frame header
  builder()->CreateStore(
    LLVMValue::intptr_constant(ZeroFrame::SHARK_FRAME), slot_addr(offset++));
  Value *fp = slot_addr(offset++);

  // Local variables
  _locals_slots_offset = offset;
  offset += locals_words;

  // Push the frame
  assert(offset == extended_frame_size(), "should do");
  builder()->CreateStore(CreateLoadFramePointer(), fp);
  CreateStoreFramePointer(
    builder()->CreatePtrToInt(fp, SharkType::intptr_type()));
}

// This function should match ZeroStack::overflow_check
void SharkStack::CreateStackOverflowCheck(Value* sp) {
  BasicBlock *zero_ok  = CreateBlock("zero_stack_ok");
  BasicBlock *overflow = CreateBlock("stack_overflow");
  BasicBlock *abi_ok   = CreateBlock("abi_stack_ok");

  // Check the Zero stack
  builder()->CreateCondBr(
    builder()->CreateICmpULT(sp, stack_base()),
    overflow, zero_ok);

  // Check the ABI stack
  builder()->SetInsertPoint(zero_ok);
  Value *stack_top = builder()->CreateSub(
    builder()->CreateValueOfStructEntry(
      thread(),
      Thread::stack_base_offset(),
      SharkType::intptr_type(),
      "abi_base"),
    builder()->CreateValueOfStructEntry(
      thread(),
      Thread::stack_size_offset(),
      SharkType::intptr_type(),
      "abi_size"));
  Value *free_stack = builder()->CreateSub(
    builder()->CreatePtrToInt(
      builder()->CreateGetFrameAddress(),
      SharkType::intptr_type(),
      "abi_sp"),
    stack_top);
  builder()->CreateCondBr(
    builder()->CreateICmpULT(
      free_stack,
      LLVMValue::intptr_constant(StackShadowPages * os::vm_page_size())),
    overflow, abi_ok);

  // Handle overflows
  builder()->SetInsertPoint(overflow);
  builder()->CreateCall(builder()->throw_StackOverflowError(), thread());
  builder()->CreateRet(LLVMValue::jint_constant(0));

  builder()->SetInsertPoint(abi_ok);
}

Value* SharkStack::CreatePopFrame(int result_slots) {
  assert(result_slots >= 0 && result_slots <= 2, "should be");
  int locals_to_pop = max_locals() - result_slots;

  Value *fp = CreateLoadFramePointer();
  Value *sp = builder()->CreateAdd(
    fp,
    LLVMValue::intptr_constant((1 + locals_to_pop) * wordSize));

  CreateStoreStackPointer(sp);
  CreateStoreFramePointer(
    builder()->CreateLoad(
      builder()->CreateIntToPtr(
        fp, PointerType::getUnqual(SharkType::intptr_type()))));

  return sp;
}

Value* SharkStack::slot_addr(int         offset,
                             const Type* type,
                             const char* name) const {
  bool needs_cast = type && type != SharkType::intptr_type();

  Value* result = builder()->CreateStructGEP(
    _frame, offset, needs_cast ? "" : name);

  if (needs_cast) {
    result = builder()->CreateBitCast(
      result, PointerType::getUnqual(type), name);
  }
  return result;
}

// The bits that differentiate stacks with normal and native frames on top

SharkStack* SharkStack::CreateBuildAndPushFrame(SharkFunction* function,
                                                Value*         method) {
  return new SharkStackWithNormalFrame(function, method);
}
SharkStack* SharkStack::CreateBuildAndPushFrame(SharkNativeWrapper* wrapper,
                                                Value*              method) {
  return new SharkStackWithNativeFrame(wrapper, method);
}

SharkStackWithNormalFrame::SharkStackWithNormalFrame(SharkFunction* function,
                                                     Value*         method)
  : SharkStack(function), _function(function) {
  // For normal frames, the stack pointer and the method slot will
  // be set during each decache, so it is not necessary to do them
  // at the time the frame is created.  However, we set them for
  // non-PRODUCT builds to make crash dumps easier to understand.
  initialize(PRODUCT_ONLY(NULL) NOT_PRODUCT(method));
}
SharkStackWithNativeFrame::SharkStackWithNativeFrame(SharkNativeWrapper* wrp,
                                                     Value*              method)
  : SharkStack(wrp), _wrapper(wrp) {
  initialize(method);
}

int SharkStackWithNormalFrame::arg_size() const {
  return function()->arg_size();
}
int SharkStackWithNativeFrame::arg_size() const {
  return wrapper()->arg_size();
}

int SharkStackWithNormalFrame::max_locals() const {
  return function()->max_locals();
}
int SharkStackWithNativeFrame::max_locals() const {
  return wrapper()->arg_size();
}

int SharkStackWithNormalFrame::max_stack() const {
  return function()->max_stack();
}
int SharkStackWithNativeFrame::max_stack() const {
  return 0;
}

int SharkStackWithNormalFrame::max_monitors() const {
  return function()->max_monitors();
}
int SharkStackWithNativeFrame::max_monitors() const {
  return wrapper()->is_synchronized() ? 1 : 0;
}

BasicBlock* SharkStackWithNormalFrame::CreateBlock(const char* name) const {
  return function()->CreateBlock(name);
}
BasicBlock* SharkStackWithNativeFrame::CreateBlock(const char* name) const {
  return wrapper()->CreateBlock(name);
}

address SharkStackWithNormalFrame::interpreter_entry_point() const {
  return (address) CppInterpreter::normal_entry;
}
address SharkStackWithNativeFrame::interpreter_entry_point() const {
  return (address) CppInterpreter::native_entry;
}

#ifndef PRODUCT
void SharkStack::CreateAssertLastJavaSPIsNull() const {
#ifdef ASSERT
  BasicBlock *fail = CreateBlock("assert_failed");
  BasicBlock *pass = CreateBlock("assert_ok");

  builder()->CreateCondBr(
    builder()->CreateICmpEQ(
      builder()->CreateLoad(last_Java_sp_addr()),
      LLVMValue::intptr_constant(0)),
    pass, fail);

  builder()->SetInsertPoint(fail);
  builder()->CreateShouldNotReachHere(__FILE__, __LINE__);
  builder()->CreateUnreachable();

  builder()->SetInsertPoint(pass);
#endif // ASSERT
}
#endif // !PRODUCT
