/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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

class SharkNativeWrapper : public SharkCompileInvariants {
  friend class SharkStackWithNativeFrame;

 public:
  static SharkNativeWrapper* build(SharkBuilder* builder,
                                   methodHandle  target,
                                   const char*   name,
                                   BasicType*    arg_types,
                                   BasicType     return_type) {
    return new SharkNativeWrapper(builder,
                                  target,
                                  name,
                                  arg_types,
                                  return_type);
  }

 private:
  SharkNativeWrapper(SharkBuilder* builder,
                     methodHandle  target,
                     const char*   name,
                     BasicType*    arg_types,
                     BasicType     return_type)
    : SharkCompileInvariants(NULL, builder),
      _target(target),
      _arg_types(arg_types),
      _return_type(return_type),
      _lock_slot_offset(0) { initialize(name); }

 private:
  void initialize(const char* name);

 private:
  methodHandle    _target;
  BasicType*      _arg_types;
  BasicType       _return_type;
  llvm::Function* _function;
  SharkStack*     _stack;
  llvm::Value*    _oop_tmp_slot;
  OopMapSet*      _oop_maps;
  int             _receiver_slot_offset;
  int             _lock_slot_offset;

  // The method being compiled.
 protected:
  methodHandle target() const {
    return _target;
  }

  // Properties of the method.
 protected:
  int arg_size() const {
    return target()->size_of_parameters();
  }
  BasicType arg_type(int i) const {
    return _arg_types[i];
  }
  BasicType return_type() const {
    return _return_type;
  }
  bool is_static() const {
    return target()->is_static();
  }
  bool is_synchronized() const {
    return target()->is_synchronized();
  }
  bool is_returning_oop() const {
    return target()->is_returning_oop();
  }

  // The LLVM function we are building.
 public:
  llvm::Function* function() const {
    return _function;
  }

  // The Zero stack and our frame on it.
 protected:
  SharkStack* stack() const {
    return _stack;
  }

  // Temporary oop storage.
 protected:
  llvm::Value* oop_tmp_slot() const {
    assert(is_static() || is_returning_oop(), "should be");
    return _oop_tmp_slot;
  }

  // Information required by nmethod::new_native_nmethod().
 public:
  int frame_size() const {
    return stack()->oopmap_frame_size();
  }
  ByteSize receiver_offset() const {
    return in_ByteSize(_receiver_slot_offset * wordSize);
  }
  ByteSize lock_offset() const {
    return in_ByteSize(_lock_slot_offset * wordSize);
  }
  OopMapSet* oop_maps() const {
    return _oop_maps;
  }

  // Helpers.
 private:
  llvm::BasicBlock* CreateBlock(const char* name = "") const {
    return llvm::BasicBlock::Create(SharkContext::current(), name, function());
  }
  llvm::Value* thread_state_address() const {
    return builder()->CreateAddressOfStructEntry(
      thread(), JavaThread::thread_state_offset(),
      llvm::PointerType::getUnqual(SharkType::jint_type()),
      "thread_state_address");
  }
  llvm::Value* pending_exception_address() const {
    return builder()->CreateAddressOfStructEntry(
      thread(), Thread::pending_exception_offset(),
      llvm::PointerType::getUnqual(SharkType::oop_type()),
      "pending_exception_address");
  }
  void CreateSetThreadState(JavaThreadState state) const {
    builder()->CreateStore(
      LLVMValue::jint_constant(state), thread_state_address());
  }
  void CreateWriteMemorySerializePage() const {
    builder()->CreateStore(
      LLVMValue::jint_constant(1),
      builder()->CreateIntToPtr(
        builder()->CreateAdd(
          LLVMValue::intptr_constant(
            (intptr_t) os::get_memory_serialize_page()),
          builder()->CreateAnd(
            builder()->CreateLShr(
              builder()->CreatePtrToInt(thread(), SharkType::intptr_type()),
              LLVMValue::intptr_constant(os::get_serialize_page_shift_count())),
            LLVMValue::intptr_constant(os::get_serialize_page_mask()))),
        llvm::PointerType::getUnqual(SharkType::jint_type())));
  }
  void CreateResetHandleBlock() const {
    llvm::Value *active_handles = builder()->CreateValueOfStructEntry(
      thread(),
      JavaThread::active_handles_offset(),
      SharkType::jniHandleBlock_type(),
      "active_handles");
    builder()->CreateStore(
      LLVMValue::intptr_constant(0),
      builder()->CreateAddressOfStructEntry(
        active_handles,
        in_ByteSize(JNIHandleBlock::top_offset_in_bytes()),
        llvm::PointerType::getUnqual(SharkType::intptr_type()),
        "top"));
  }
  llvm::LoadInst* CreateLoadPendingException() const {
    return builder()->CreateLoad(
      pending_exception_address(), "pending_exception");
  }
};
