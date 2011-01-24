/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_SHARK_SHARKSTACK_HPP
#define SHARE_VM_SHARK_SHARKSTACK_HPP

#include "shark/llvmHeaders.hpp"
#include "shark/sharkInvariants.hpp"
#include "shark/sharkType.hpp"

class SharkFunction;
class SharkNativeWrapper;
class SharkStackWithNormalFrame;
class SharkStackWithNativeFrame;

class SharkStack : public SharkCompileInvariants {
 public:
  static SharkStack* CreateBuildAndPushFrame(
    SharkFunction* function, llvm::Value* method);
  static SharkStack* CreateBuildAndPushFrame(
    SharkNativeWrapper* wrapper, llvm::Value* method);

 protected:
  SharkStack(const SharkCompileInvariants* parent)
    : SharkCompileInvariants(parent) {}

 protected:
  void initialize(llvm::Value* method);

 protected:
  void CreateStackOverflowCheck(llvm::Value* sp);

  // Properties of the method being compiled
 protected:
  virtual int arg_size() const = 0;
  virtual int max_locals() const = 0;
  virtual int max_stack() const = 0;
  virtual int max_monitors() const = 0;

  // BasicBlock creation
 protected:
  virtual llvm::BasicBlock* CreateBlock(const char* name = "") const = 0;

  // Interpreter entry point for bailouts
 protected:
  virtual address interpreter_entry_point() const = 0;

  // Interface with the Zero stack
 private:
  llvm::Value* zero_stack() const {
    return builder()->CreateAddressOfStructEntry(
      thread(),
      JavaThread::zero_stack_offset(),
      SharkType::zeroStack_type(),
      "zero_stack");
  }
  llvm::Value* stack_base() const {
    return builder()->CreateValueOfStructEntry(
      zero_stack(),
      ZeroStack::base_offset(),
      SharkType::intptr_type(),
      "stack_base");
  }
  llvm::Value* stack_pointer_addr() const {
    return builder()->CreateAddressOfStructEntry(
      zero_stack(),
      ZeroStack::sp_offset(),
      llvm::PointerType::getUnqual(SharkType::intptr_type()),
      "stack_pointer_addr");
  }
  llvm::Value* frame_pointer_addr() const {
    return builder()->CreateAddressOfStructEntry(
      thread(),
      JavaThread::top_zero_frame_offset(),
      llvm::PointerType::getUnqual(SharkType::intptr_type()),
      "frame_pointer_addr");
  }

 public:
  llvm::LoadInst* CreateLoadStackPointer(const char *name = "") {
    return builder()->CreateLoad(stack_pointer_addr(), name);
  }
  llvm::StoreInst* CreateStoreStackPointer(llvm::Value* value) {
    return builder()->CreateStore(value, stack_pointer_addr());
  }
  llvm::LoadInst* CreateLoadFramePointer(const char *name = "") {
    return builder()->CreateLoad(frame_pointer_addr(), name);
  }
  llvm::StoreInst* CreateStoreFramePointer(llvm::Value* value) {
    return builder()->CreateStore(value, frame_pointer_addr());
  }
  llvm::Value* CreatePopFrame(int result_slots);

  // Interface with the frame anchor
 private:
  llvm::Value* last_Java_sp_addr() const {
    return builder()->CreateAddressOfStructEntry(
      thread(),
      JavaThread::last_Java_sp_offset(),
      llvm::PointerType::getUnqual(SharkType::intptr_type()),
      "last_Java_sp_addr");
  }
  llvm::Value* last_Java_fp_addr() const {
    return builder()->CreateAddressOfStructEntry(
      thread(),
      JavaThread::last_Java_fp_offset(),
      llvm::PointerType::getUnqual(SharkType::intptr_type()),
      "last_Java_fp_addr");
  }

 public:
  void CreateSetLastJavaFrame() {
    // Note that whenever _last_Java_sp != NULL other anchor fields
    // must be valid.  The profiler apparently depends on this.
    NOT_PRODUCT(CreateAssertLastJavaSPIsNull());
    builder()->CreateStore(CreateLoadFramePointer(), last_Java_fp_addr());
    // XXX There's last_Java_pc as well, but I don't think anything uses it
    // Also XXX: should we fence here?  Zero doesn't...
    builder()->CreateStore(CreateLoadStackPointer(), last_Java_sp_addr());
    // Also also XXX: we could probably cache the sp (and the fp we know??)
  }
  void CreateResetLastJavaFrame() {
    builder()->CreateStore(LLVMValue::intptr_constant(0), last_Java_sp_addr());
  }

 private:
  void CreateAssertLastJavaSPIsNull() const PRODUCT_RETURN;

  // Our method's frame
 private:
  llvm::Value* _frame;
  int          _extended_frame_size;
  int          _stack_slots_offset;

 public:
  int extended_frame_size() const {
    return _extended_frame_size;
  }
  int oopmap_frame_size() const {
    return extended_frame_size() - arg_size();
  }

  // Offsets of things in the frame
 private:
  int _monitors_slots_offset;
  int _oop_tmp_slot_offset;
  int _method_slot_offset;
  int _pc_slot_offset;
  int _locals_slots_offset;

 public:
  int stack_slots_offset() const {
    return _stack_slots_offset;
  }
  int oop_tmp_slot_offset() const {
    return _oop_tmp_slot_offset;
  }
  int method_slot_offset() const {
    return _method_slot_offset;
  }
  int pc_slot_offset() const {
    return _pc_slot_offset;
  }
  int locals_slots_offset() const {
    return _locals_slots_offset;
  }
  int monitor_offset(int index) const {
    assert(index >= 0 && index < max_monitors(), "invalid monitor index");
    return _monitors_slots_offset +
      (max_monitors() - 1 - index) * frame::interpreter_frame_monitor_size();
  }
  int monitor_object_offset(int index) const {
    return monitor_offset(index) +
      (BasicObjectLock::obj_offset_in_bytes() >> LogBytesPerWord);
  }
  int monitor_header_offset(int index) const {
    return monitor_offset(index) +
      ((BasicObjectLock::lock_offset_in_bytes() +
        BasicLock::displaced_header_offset_in_bytes()) >> LogBytesPerWord);
  }

  // Addresses of things in the frame
 public:
  llvm::Value* slot_addr(int               offset,
                         const llvm::Type* type = NULL,
                         const char*       name = "") const;

  llvm::Value* monitor_addr(int index) const {
    return slot_addr(
      monitor_offset(index),
      SharkType::monitor_type(),
      "monitor");
  }
  llvm::Value* monitor_object_addr(int index) const {
    return slot_addr(
      monitor_object_offset(index),
      SharkType::oop_type(),
      "object_addr");
  }
  llvm::Value* monitor_header_addr(int index) const {
    return slot_addr(
      monitor_header_offset(index),
      SharkType::intptr_type(),
      "displaced_header_addr");
  }

  // oopmap helpers
 public:
  static int oopmap_slot_munge(int offset) {
    return offset << (LogBytesPerWord - LogBytesPerInt);
  }
  static VMReg slot2reg(int offset) {
    return VMRegImpl::stack2reg(oopmap_slot_munge(offset));
  }
};

class SharkStackWithNormalFrame : public SharkStack {
  friend class SharkStack;

 protected:
  SharkStackWithNormalFrame(SharkFunction* function, llvm::Value* method);

 private:
  SharkFunction* _function;

 private:
  SharkFunction* function() const {
    return _function;
  }

  // Properties of the method being compiled
 private:
  int arg_size() const;
  int max_locals() const;
  int max_stack() const;
  int max_monitors() const;

  // BasicBlock creation
 private:
  llvm::BasicBlock* CreateBlock(const char* name = "") const;

  // Interpreter entry point for bailouts
 private:
  address interpreter_entry_point() const;
};

class SharkStackWithNativeFrame : public SharkStack {
  friend class SharkStack;

 protected:
  SharkStackWithNativeFrame(SharkNativeWrapper* wrapper, llvm::Value* method);

 private:
  SharkNativeWrapper* _wrapper;

 private:
  SharkNativeWrapper* wrapper() const {
    return _wrapper;
  }

  // Properties of the method being compiled
 private:
  int arg_size() const;
  int max_locals() const;
  int max_stack() const;
  int max_monitors() const;

  // BasicBlock creation
 private:
  llvm::BasicBlock* CreateBlock(const char* name = "") const;

  // Interpreter entry point for bailouts
 private:
  address interpreter_entry_point() const;
};

#endif // SHARE_VM_SHARK_SHARKSTACK_HPP
