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

class SharkBuilder : public llvm::IRBuilder<> {
  friend class SharkCompileInvariants;

 public:
  SharkBuilder(SharkCodeBuffer* code_buffer);

  // The code buffer we are building into.
 private:
  SharkCodeBuffer* _code_buffer;

 protected:
  SharkCodeBuffer* code_buffer() const {
    return _code_buffer;
  }

  // Helpers for accessing structures.
 public:
  llvm::Value* CreateAddressOfStructEntry(llvm::Value* base,
                                          ByteSize offset,
                                          const llvm::Type* type,
                                          const char *name = "");
  llvm::LoadInst* CreateValueOfStructEntry(llvm::Value* base,
                                           ByteSize offset,
                                           const llvm::Type* type,
                                           const char *name = "");

  // Helpers for accessing arrays.
 public:
  llvm::LoadInst* CreateArrayLength(llvm::Value* arrayoop);
  llvm::Value* CreateArrayAddress(llvm::Value*      arrayoop,
                                  const llvm::Type* element_type,
                                  int               element_bytes,
                                  ByteSize          base_offset,
                                  llvm::Value*      index,
                                  const char*       name = "");
  llvm::Value* CreateArrayAddress(llvm::Value* arrayoop,
                                  BasicType    basic_type,
                                  ByteSize     base_offset,
                                  llvm::Value* index,
                                  const char*  name = "");
  llvm::Value* CreateArrayAddress(llvm::Value* arrayoop,
                                  BasicType    basic_type,
                                  llvm::Value* index,
                                  const char*  name = "");

  // Helpers for creating intrinsics and external functions.
 private:
  static const llvm::Type* make_type(char type, bool void_ok);
  static const llvm::FunctionType* make_ftype(const char* params,
                                              const char* ret);
  llvm::Value* make_function(const char* name,
                             const char* params,
                             const char* ret);
  llvm::Value* make_function(address     func,
                             const char* params,
                             const char* ret);

  // Intrinsics and external functions, part 1: VM calls.
  //   These are functions declared with JRT_ENTRY and JRT_EXIT,
  //   macros which flip the thread from _thread_in_Java to
  //   _thread_in_vm and back.  VM calls always safepoint, and can
  //   therefore throw exceptions.  VM calls require of setup and
  //   teardown, and must be called with SharkTopLevelBlock::call_vm.
 public:
  llvm::Value* find_exception_handler();
  llvm::Value* monitorenter();
  llvm::Value* monitorexit();
  llvm::Value* new_instance();
  llvm::Value* newarray();
  llvm::Value* anewarray();
  llvm::Value* multianewarray();
  llvm::Value* register_finalizer();
  llvm::Value* safepoint();
  llvm::Value* throw_ArithmeticException();
  llvm::Value* throw_ArrayIndexOutOfBoundsException();
  llvm::Value* throw_ClassCastException();
  llvm::Value* throw_NullPointerException();

  // Intrinsics and external functions, part 2: High-level non-VM calls.
  //   These are called like normal functions.  The stack is not set
  //   up for walking so they must not safepoint or throw exceptions,
  //   or call anything that might.
 public:
  llvm::Value* f2i();
  llvm::Value* f2l();
  llvm::Value* d2i();
  llvm::Value* d2l();
  llvm::Value* is_subtype_of();
  llvm::Value* current_time_millis();
  llvm::Value* sin();
  llvm::Value* cos();
  llvm::Value* tan();
  llvm::Value* atan2();
  llvm::Value* sqrt();
  llvm::Value* log();
  llvm::Value* log10();
  llvm::Value* pow();
  llvm::Value* exp();
  llvm::Value* fabs();
  llvm::Value* unsafe_field_offset_to_byte_offset();
  llvm::Value* osr_migration_end();

  // Intrinsics and external functions, part 3: semi-VM calls.
  //   These are special cases that do VM call stuff but are invoked
  //   as though they were normal calls.  This is acceptable so long
  //   as the method that calls them returns to its immediately that
  //   the semi VM call returns.
 public:
  llvm::Value* throw_StackOverflowError();
  llvm::Value* uncommon_trap();
  llvm::Value* deoptimized_entry_point();

  // Intrinsics and external functions, part 4: Native-Java transition.
  //   This is a special case in that it is invoked during a thread
  //   state transition.  The stack must be set up for walking, and it
  //   may throw exceptions, but the state is _thread_in_native_trans.
 public:
  llvm::Value* check_special_condition_for_native_trans();

  // Intrinsics and external functions, part 5: Low-level non-VM calls.
  //   These have the same caveats as the high-level non-VM calls
  //   above.  They are not accessed directly; rather, you should
  //   access them via the various Create* methods below.
 private:
  llvm::Value* cmpxchg_int();
  llvm::Value* cmpxchg_ptr();
  llvm::Value* frame_address();
  llvm::Value* memory_barrier();
  llvm::Value* memset();
  llvm::Value* unimplemented();
  llvm::Value* should_not_reach_here();
  llvm::Value* dump();

  // Public interface to low-level non-VM calls.
 public:
  llvm::CallInst* CreateCmpxchgInt(llvm::Value* exchange_value,
                                   llvm::Value* dst,
                                   llvm::Value* compare_value);
  llvm::CallInst* CreateCmpxchgPtr(llvm::Value* exchange_value,
                                   llvm::Value* dst,
                                   llvm::Value* compare_value);
  llvm::CallInst* CreateGetFrameAddress();
  llvm::CallInst* CreateMemoryBarrier(int flags);
  llvm::CallInst* CreateMemset(llvm::Value* dst,
                               llvm::Value* value,
                               llvm::Value* len,
                               llvm::Value* align);
  llvm::CallInst* CreateUnimplemented(const char* file, int line);
  llvm::CallInst* CreateShouldNotReachHere(const char* file, int line);
  NOT_PRODUCT(llvm::CallInst* CreateDump(llvm::Value* value));

  // Flags for CreateMemoryBarrier.
 public:
  enum BarrierFlags {
    BARRIER_LOADLOAD   = 1,
    BARRIER_LOADSTORE  = 2,
    BARRIER_STORELOAD  = 4,
    BARRIER_STORESTORE = 8
  };

  // HotSpot memory barriers
 public:
  void CreateUpdateBarrierSet(BarrierSet* bs, llvm::Value* field);

  // Helpers for accessing the code buffer.
 public:
  llvm::Value* code_buffer_address(int offset);
  llvm::Value* CreateInlineOop(jobject object, const char* name = "");
  llvm::Value* CreateInlineOop(ciObject* object, const char* name = "") {
    return CreateInlineOop(object->constant_encoding(), name);
  }
  llvm::Value* CreateInlineData(void*             data,
                                size_t            size,
                                const llvm::Type* type,
                                const char*       name = "");

  // Helpers for creating basic blocks.
  // NB don't use unless SharkFunction::CreateBlock is unavailable.
  // XXX these are hacky and should be removed.
 public:
  llvm::BasicBlock* GetBlockInsertionPoint() const;
  llvm::BasicBlock* CreateBlock(llvm::BasicBlock* ip,
                                const char*       name="") const;
};
