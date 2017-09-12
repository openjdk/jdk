/*
 * Copyright (c) 1999, 2017, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2009, 2010 Red Hat, Inc.
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
#include "shark/llvmHeaders.hpp"
#include "shark/sharkNativeWrapper.hpp"
#include "shark/sharkType.hpp"

using namespace llvm;

void SharkNativeWrapper::initialize(const char *name) {
  // Create the function
  _function = Function::Create(
    SharkType::entry_point_type(),
    GlobalVariable::InternalLinkage,
    name);

  // Get our arguments
  Function::arg_iterator ai = function()->arg_begin();
  Argument *method = ai++;
  method->setName("method");
  Argument *base_pc = ai++;
  base_pc->setName("base_pc");
  code_buffer()->set_base_pc(base_pc);
  Argument *thread = ai++;
  thread->setName("thread");
  set_thread(thread);

  // Create and push our stack frame
  builder()->SetInsertPoint(CreateBlock());
  _stack = SharkStack::CreateBuildAndPushFrame(this, method);
  NOT_PRODUCT(method = NULL);

  // Create the oopmap.  We use the one oopmap for every call site in
  // the wrapper, which results in the odd mild inefficiency but is a
  // damn sight easier to code.
  OopMap *oopmap = new OopMap(
    SharkStack::oopmap_slot_munge(stack()->oopmap_frame_size()),
    SharkStack::oopmap_slot_munge(arg_size()));

  // Set up the oop_tmp slot if required:
  //  - For static methods we use it to handlize the class argument
  //    for the call, and to protect the same during slow path locks
  //    (if synchronized).
  //  - For methods returning oops, we use it to protect the return
  //    value across safepoints or slow path unlocking.
  if (is_static() || is_returning_oop()) {
    _oop_tmp_slot = stack()->slot_addr(
      stack()->oop_tmp_slot_offset(),
      SharkType::oop_type(),
      "oop_tmp_slot");

    oopmap->set_oop(SharkStack::slot2reg(stack()->oop_tmp_slot_offset()));
  }

  // Set up the monitor slot, for synchronized methods
  if (is_synchronized()) {
    Unimplemented();
    _lock_slot_offset = 23;
  }

  // Start building the argument list
  std::vector<Type*> param_types;
  std::vector<Value*> param_values;
  PointerType *box_type = PointerType::getUnqual(SharkType::oop_type());

  // First argument is the JNIEnv
  param_types.push_back(SharkType::jniEnv_type());
  param_values.push_back(
    builder()->CreateAddressOfStructEntry(
      thread,
      JavaThread::jni_environment_offset(),
      SharkType::jniEnv_type(),
      "jni_environment"));

  // For static methods, the second argument is the class
  if (is_static()) {
    builder()->CreateStore(
      builder()->CreateInlineOop(
        JNIHandles::make_local(
          target()->method_holder()->java_mirror())),
      oop_tmp_slot());

    param_types.push_back(box_type);
    param_values.push_back(oop_tmp_slot());

    _receiver_slot_offset = stack()->oop_tmp_slot_offset();
  }
  else if (is_returning_oop()) {
    // The oop_tmp slot is registered in the oopmap,
    // so we need to clear it.  This is one of the
    // mild inefficiencies I mentioned earlier.
    builder()->CreateStore(LLVMValue::null(), oop_tmp_slot());
  }

  // Parse the arguments
  for (int i = 0; i < arg_size(); i++) {
    int slot_offset = stack()->locals_slots_offset() + arg_size() - 1 - i;
    int adjusted_offset = slot_offset;
    BasicBlock *null, *not_null, *merge;
    Value *box;
    PHINode *phi;

    switch (arg_type(i)) {
    case T_VOID:
      break;

    case T_OBJECT:
    case T_ARRAY:
      null     = CreateBlock("null");
      not_null = CreateBlock("not_null");
      merge    = CreateBlock("merge");

      box = stack()->slot_addr(slot_offset, SharkType::oop_type());
      builder()->CreateCondBr(
        builder()->CreateICmp(
          ICmpInst::ICMP_EQ,
          builder()->CreateLoad(box),
          LLVMValue::null()),
        null, not_null);

      builder()->SetInsertPoint(null);
      builder()->CreateBr(merge);

      builder()->SetInsertPoint(not_null);
      builder()->CreateBr(merge);

      builder()->SetInsertPoint(merge);
      phi = builder()->CreatePHI(box_type, 0, "boxed_object");
      phi->addIncoming(ConstantPointerNull::get(box_type), null);
      phi->addIncoming(box, not_null);
      box = phi;

      param_types.push_back(box_type);
      param_values.push_back(box);

      oopmap->set_oop(SharkStack::slot2reg(slot_offset));

      if (i == 0 && !is_static())
        _receiver_slot_offset = slot_offset;

      break;

    case T_LONG:
    case T_DOUBLE:
      adjusted_offset--;
      // fall through

    default:
      Type *param_type = SharkType::to_stackType(arg_type(i));

      param_types.push_back(param_type);
      param_values.push_back(
        builder()->CreateLoad(stack()->slot_addr(adjusted_offset, param_type)));
    }
  }

  // The oopmap is now complete, and everything is written
  // into the frame except the PC.
  int pc_offset = code_buffer()->create_unique_offset();

  _oop_maps = new OopMapSet();
  oop_maps()->add_gc_map(pc_offset, oopmap);

  builder()->CreateStore(
    builder()->code_buffer_address(pc_offset),
    stack()->slot_addr(stack()->pc_slot_offset()));

  // Set up the Java frame anchor
  stack()->CreateSetLastJavaFrame();

  // Lock if necessary
  if (is_synchronized())
    Unimplemented();

  // Change the thread state to _thread_in_native
  CreateSetThreadState(_thread_in_native);

  // Make the call
  BasicType result_type = target()->result_type();
  Type* return_type;
  if (result_type == T_VOID)
    return_type = SharkType::void_type();
  else if (is_returning_oop())
    return_type = box_type;
  else
    return_type = SharkType::to_arrayType(result_type);
  Value* native_function = builder()->CreateIntToPtr(
     LLVMValue::intptr_constant((intptr_t) target()->native_function()),
     PointerType::getUnqual(
       FunctionType::get(return_type, param_types, false)));
  Value *result = builder()->CreateCall(
    native_function, llvm::makeArrayRef(param_values));

  // Start the transition back to _thread_in_Java
  CreateSetThreadState(_thread_in_native_trans);

  // Make sure new state is visible in the GC thread
  if (os::is_MP()) {
    if (UseMembar)
      builder()->CreateFence(llvm::SequentiallyConsistent, llvm::CrossThread);
    else
      CreateWriteMemorySerializePage();
  }

  // Handle safepoint operations, pending suspend requests,
  // and pending asynchronous exceptions.
  BasicBlock *check_thread = CreateBlock("check_thread");
  BasicBlock *do_safepoint = CreateBlock("do_safepoint");
  BasicBlock *safepointed  = CreateBlock("safepointed");

  Value *global_state = builder()->CreateLoad(
    builder()->CreateIntToPtr(
      LLVMValue::intptr_constant(
        (intptr_t) SafepointSynchronize::address_of_state()),
      PointerType::getUnqual(SharkType::jint_type())),
    "global_state");

  builder()->CreateCondBr(
    builder()->CreateICmpNE(
      global_state,
      LLVMValue::jint_constant(SafepointSynchronize::_not_synchronized)),
    do_safepoint, check_thread);

  builder()->SetInsertPoint(check_thread);
  Value *thread_state = builder()->CreateValueOfStructEntry(
    thread,
    JavaThread::suspend_flags_offset(),
    SharkType::jint_type(),
    "thread_state");

  builder()->CreateCondBr(
    builder()->CreateICmpNE(
      thread_state,
      LLVMValue::jint_constant(0)),
    do_safepoint, safepointed);

  builder()->SetInsertPoint(do_safepoint);
  builder()->CreateCall(
    builder()->check_special_condition_for_native_trans(), thread);
  builder()->CreateBr(safepointed);

  // Finally we can change the thread state to _thread_in_Java
  builder()->SetInsertPoint(safepointed);
  CreateSetThreadState(_thread_in_Java);

  // Clear the frame anchor
  stack()->CreateResetLastJavaFrame();

  // If there is a pending exception then we can just unwind and
  // return.  It seems totally wrong that unlocking is skipped here
  // but apparently the template interpreter does this so we do too.
  BasicBlock *exception    = CreateBlock("exception");
  BasicBlock *no_exception = CreateBlock("no_exception");

  builder()->CreateCondBr(
    builder()->CreateICmpEQ(
      CreateLoadPendingException(),
      LLVMValue::null()),
    no_exception, exception);

  builder()->SetInsertPoint(exception);
  CreateResetHandleBlock();
  stack()->CreatePopFrame(0);
  builder()->CreateRet(LLVMValue::jint_constant(0));

  builder()->SetInsertPoint(no_exception);

  // If the result was an oop then unbox it before
  // releasing the handle it might be protected by
  if (is_returning_oop()) {
    BasicBlock *null     = builder()->GetInsertBlock();
    BasicBlock *not_null = CreateBlock("not_null");
    BasicBlock *merge    = CreateBlock("merge");

    builder()->CreateCondBr(
      builder()->CreateICmpNE(result, ConstantPointerNull::get(box_type)),
      not_null, merge);

    builder()->SetInsertPoint(not_null);
#error Needs to be updated for tagged jweak; see JNIHandles.
    Value *unboxed_result = builder()->CreateLoad(result);
    builder()->CreateBr(merge);

    builder()->SetInsertPoint(merge);
    PHINode *phi = builder()->CreatePHI(SharkType::oop_type(), 0, "result");
    phi->addIncoming(LLVMValue::null(), null);
    phi->addIncoming(unboxed_result, not_null);
    result = phi;
  }

  // Reset handle block
  CreateResetHandleBlock();

  // Unlock if necessary.
  if (is_synchronized())
    Unimplemented();

  // Unwind and return
  Value *result_addr = stack()->CreatePopFrame(type2size[result_type]);
  if (result_type != T_VOID) {
    bool needs_cast = false;
    bool is_signed = false;
    switch (result_type) {
    case T_BOOLEAN:
      result = builder()->CreateICmpNE(result, LLVMValue::jbyte_constant(0));
      needs_cast = true;
      break;

    case T_CHAR:
      needs_cast = true;
      break;

    case T_BYTE:
    case T_SHORT:
      needs_cast = true;
      is_signed = true;
      break;
    }
    if (needs_cast) {
      result = builder()->CreateIntCast(
        result, SharkType::to_stackType(result_type), is_signed);
    }

    builder()->CreateStore(
      result,
      builder()->CreateIntToPtr(
        result_addr,
        PointerType::getUnqual(SharkType::to_stackType(result_type))));
  }
  builder()->CreateRet(LLVMValue::jint_constant(0));
}
