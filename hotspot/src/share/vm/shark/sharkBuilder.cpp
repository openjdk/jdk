/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciMethod.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/cardTableModRefBS.hpp"
#include "memory/resourceArea.hpp"
#include "oops/method.hpp"
#include "runtime/os.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.hpp"
#include "shark/llvmHeaders.hpp"
#include "shark/llvmValue.hpp"
#include "shark/sharkBuilder.hpp"
#include "shark/sharkContext.hpp"
#include "shark/sharkRuntime.hpp"
#include "utilities/debug.hpp"

using namespace llvm;

SharkBuilder::SharkBuilder(SharkCodeBuffer* code_buffer)
  : IRBuilder<>(SharkContext::current()),
    _code_buffer(code_buffer) {
}

// Helpers for accessing structures
Value* SharkBuilder::CreateAddressOfStructEntry(Value*      base,
                                                ByteSize    offset,
                                                Type* type,
                                                const char* name) {
  return CreateBitCast(CreateStructGEP(base, in_bytes(offset)), type, name);
}

LoadInst* SharkBuilder::CreateValueOfStructEntry(Value*      base,
                                                 ByteSize    offset,
                                                 Type* type,
                                                 const char* name) {
  return CreateLoad(
    CreateAddressOfStructEntry(
      base, offset, PointerType::getUnqual(type)),
    name);
}

// Helpers for accessing arrays

LoadInst* SharkBuilder::CreateArrayLength(Value* arrayoop) {
  return CreateValueOfStructEntry(
    arrayoop, in_ByteSize(arrayOopDesc::length_offset_in_bytes()),
    SharkType::jint_type(), "length");
}

Value* SharkBuilder::CreateArrayAddress(Value*      arrayoop,
                                        Type* element_type,
                                        int         element_bytes,
                                        ByteSize    base_offset,
                                        Value*      index,
                                        const char* name) {
  Value* offset = CreateIntCast(index, SharkType::intptr_type(), false);
  if (element_bytes != 1)
    offset = CreateShl(
      offset,
      LLVMValue::intptr_constant(exact_log2(element_bytes)));
  offset = CreateAdd(
    LLVMValue::intptr_constant(in_bytes(base_offset)), offset);

  return CreateIntToPtr(
    CreateAdd(CreatePtrToInt(arrayoop, SharkType::intptr_type()), offset),
    PointerType::getUnqual(element_type),
    name);
}

Value* SharkBuilder::CreateArrayAddress(Value*      arrayoop,
                                        BasicType   basic_type,
                                        ByteSize    base_offset,
                                        Value*      index,
                                        const char* name) {
  return CreateArrayAddress(
    arrayoop,
    SharkType::to_arrayType(basic_type),
    type2aelembytes(basic_type),
    base_offset, index, name);
}

Value* SharkBuilder::CreateArrayAddress(Value*      arrayoop,
                                        BasicType   basic_type,
                                        Value*      index,
                                        const char* name) {
  return CreateArrayAddress(
    arrayoop, basic_type,
    in_ByteSize(arrayOopDesc::base_offset_in_bytes(basic_type)),
    index, name);
}

// Helpers for creating intrinsics and external functions.

Type* SharkBuilder::make_type(char type, bool void_ok) {
  switch (type) {
    // Primitive types
  case 'c':
    return SharkType::jbyte_type();
  case 'i':
    return SharkType::jint_type();
  case 'l':
    return SharkType::jlong_type();
  case 'x':
    return SharkType::intptr_type();
  case 'f':
    return SharkType::jfloat_type();
  case 'd':
    return SharkType::jdouble_type();

    // Pointers to primitive types
  case 'C':
  case 'I':
  case 'L':
  case 'X':
  case 'F':
  case 'D':
    return PointerType::getUnqual(make_type(tolower(type), false));

    // VM objects
  case 'T':
    return SharkType::thread_type();
  case 'M':
    return PointerType::getUnqual(SharkType::monitor_type());
  case 'O':
    return SharkType::oop_type();
  case 'K':
    return SharkType::klass_type();

    // Miscellaneous
  case 'v':
    assert(void_ok, "should be");
    return SharkType::void_type();
  case '1':
    return SharkType::bit_type();

  default:
    ShouldNotReachHere();
  }
}

FunctionType* SharkBuilder::make_ftype(const char* params,
                                             const char* ret) {
  std::vector<Type*> param_types;
  for (const char* c = params; *c; c++)
    param_types.push_back(make_type(*c, false));

  assert(strlen(ret) == 1, "should be");
  Type *return_type = make_type(*ret, true);

  return FunctionType::get(return_type, param_types, false);
}

// Create an object representing an intrinsic or external function by
// referencing the symbol by name.  This is the LLVM-style approach,
// but it cannot be used on functions within libjvm.so its symbols
// are not exported.  Note that you cannot make this work simply by
// exporting the symbols, as some symbols have the same names as
// symbols in the standard libraries (eg, atan2, fabs) and would
// obscure them were they visible.
Value* SharkBuilder::make_function(const char* name,
                                   const char* params,
                                   const char* ret) {
  return SharkContext::current().get_external(name, make_ftype(params, ret));
}

// Create an object representing an external function by inlining a
// function pointer in the code.  This is not the LLVM way, but it's
// the only way to access functions in libjvm.so and functions like
// __kernel_dmb on ARM which is accessed via an absolute address.
Value* SharkBuilder::make_function(address     func,
                                   const char* params,
                                   const char* ret) {
  return CreateIntToPtr(
    LLVMValue::intptr_constant((intptr_t) func),
    PointerType::getUnqual(make_ftype(params, ret)));
}

// VM calls

Value* SharkBuilder::find_exception_handler() {
  return make_function(
    (address) SharkRuntime::find_exception_handler, "TIi", "i");
}

Value* SharkBuilder::monitorenter() {
  return make_function((address) SharkRuntime::monitorenter, "TM", "v");
}

Value* SharkBuilder::monitorexit() {
  return make_function((address) SharkRuntime::monitorexit, "TM", "v");
}

Value* SharkBuilder::new_instance() {
  return make_function((address) SharkRuntime::new_instance, "Ti", "v");
}

Value* SharkBuilder::newarray() {
  return make_function((address) SharkRuntime::newarray, "Tii", "v");
}

Value* SharkBuilder::anewarray() {
  return make_function((address) SharkRuntime::anewarray, "Tii", "v");
}

Value* SharkBuilder::multianewarray() {
  return make_function((address) SharkRuntime::multianewarray, "TiiI", "v");
}

Value* SharkBuilder::register_finalizer() {
  return make_function((address) SharkRuntime::register_finalizer, "TO", "v");
}

Value* SharkBuilder::safepoint() {
  return make_function((address) SafepointSynchronize::block, "T", "v");
}

Value* SharkBuilder::throw_ArithmeticException() {
  return make_function(
    (address) SharkRuntime::throw_ArithmeticException, "TCi", "v");
}

Value* SharkBuilder::throw_ArrayIndexOutOfBoundsException() {
  return make_function(
    (address) SharkRuntime::throw_ArrayIndexOutOfBoundsException, "TCii", "v");
}

Value* SharkBuilder::throw_ClassCastException() {
  return make_function(
    (address) SharkRuntime::throw_ClassCastException, "TCi", "v");
}

Value* SharkBuilder::throw_NullPointerException() {
  return make_function(
    (address) SharkRuntime::throw_NullPointerException, "TCi", "v");
}

// High-level non-VM calls

Value* SharkBuilder::f2i() {
  return make_function((address) SharedRuntime::f2i, "f", "i");
}

Value* SharkBuilder::f2l() {
  return make_function((address) SharedRuntime::f2l, "f", "l");
}

Value* SharkBuilder::d2i() {
  return make_function((address) SharedRuntime::d2i, "d", "i");
}

Value* SharkBuilder::d2l() {
  return make_function((address) SharedRuntime::d2l, "d", "l");
}

Value* SharkBuilder::is_subtype_of() {
  return make_function((address) SharkRuntime::is_subtype_of, "KK", "c");
}

Value* SharkBuilder::current_time_millis() {
  return make_function((address) os::javaTimeMillis, "", "l");
}

Value* SharkBuilder::sin() {
  return make_function("llvm.sin.f64", "d", "d");
}

Value* SharkBuilder::cos() {
  return make_function("llvm.cos.f64", "d", "d");
}

Value* SharkBuilder::tan() {
  return make_function((address) ::tan, "d", "d");
}

Value* SharkBuilder::atan2() {
  return make_function((address) ::atan2, "dd", "d");
}

Value* SharkBuilder::sqrt() {
  return make_function("llvm.sqrt.f64", "d", "d");
}

Value* SharkBuilder::log() {
  return make_function("llvm.log.f64", "d", "d");
}

Value* SharkBuilder::log10() {
  return make_function("llvm.log10.f64", "d", "d");
}

Value* SharkBuilder::pow() {
  return make_function("llvm.pow.f64", "dd", "d");
}

Value* SharkBuilder::exp() {
  return make_function("llvm.exp.f64", "d", "d");
}

Value* SharkBuilder::fabs() {
  return make_function((address) ::fabs, "d", "d");
}

Value* SharkBuilder::unsafe_field_offset_to_byte_offset() {
  extern jlong Unsafe_field_offset_to_byte_offset(jlong field_offset);
  return make_function((address) Unsafe_field_offset_to_byte_offset, "l", "l");
}

Value* SharkBuilder::osr_migration_end() {
  return make_function((address) SharedRuntime::OSR_migration_end, "C", "v");
}

// Semi-VM calls

Value* SharkBuilder::throw_StackOverflowError() {
  return make_function((address) ZeroStack::handle_overflow, "T", "v");
}

Value* SharkBuilder::uncommon_trap() {
  return make_function((address) SharkRuntime::uncommon_trap, "Ti", "i");
}

Value* SharkBuilder::deoptimized_entry_point() {
  return make_function((address) CppInterpreter::main_loop, "iT", "v");
}

// Native-Java transition

Value* SharkBuilder::check_special_condition_for_native_trans() {
  return make_function(
    (address) JavaThread::check_special_condition_for_native_trans,
    "T", "v");
}

Value* SharkBuilder::frame_address() {
  return make_function("llvm.frameaddress", "i", "C");
}

Value* SharkBuilder::memset() {
  // LLVM 2.8 added a fifth isVolatile field for memset
  // introduced with LLVM r100304
  return make_function("llvm.memset.p0i8.i32", "Cciii", "v");
}

Value* SharkBuilder::unimplemented() {
  return make_function((address) report_unimplemented, "Ci", "v");
}

Value* SharkBuilder::should_not_reach_here() {
  return make_function((address) report_should_not_reach_here, "Ci", "v");
}

Value* SharkBuilder::dump() {
  return make_function((address) SharkRuntime::dump, "Cx", "v");
}

// Public interface to low-level non-VM calls

CallInst* SharkBuilder::CreateGetFrameAddress() {
  return CreateCall(frame_address(), LLVMValue::jint_constant(0));
}

CallInst* SharkBuilder::CreateMemset(Value* dst,
                                     Value* value,
                                     Value* len,
                                     Value* align) {
  return CreateCall5(memset(), dst, value, len, align,
                     LLVMValue::jint_constant(0));
}

CallInst* SharkBuilder::CreateUnimplemented(const char* file, int line) {
  return CreateCall2(
    unimplemented(),
    CreateIntToPtr(
      LLVMValue::intptr_constant((intptr_t) file),
      PointerType::getUnqual(SharkType::jbyte_type())),
    LLVMValue::jint_constant(line));
}

CallInst* SharkBuilder::CreateShouldNotReachHere(const char* file, int line) {
  return CreateCall2(
    should_not_reach_here(),
    CreateIntToPtr(
      LLVMValue::intptr_constant((intptr_t) file),
      PointerType::getUnqual(SharkType::jbyte_type())),
    LLVMValue::jint_constant(line));
}

#ifndef PRODUCT
CallInst* SharkBuilder::CreateDump(Value* value) {
  const char *name;
  if (value->hasName())
    // XXX this leaks, but it's only debug code
    name = os::strdup(value->getName().str().c_str());
  else
    name = "unnamed_value";

  if (isa<PointerType>(value->getType()))
    value = CreatePtrToInt(value, SharkType::intptr_type());
  else if (value->getType()->
           isIntegerTy()
           )
    value = CreateIntCast(value, SharkType::intptr_type(), false);
  else
    Unimplemented();

  return CreateCall2(
    dump(),
    CreateIntToPtr(
      LLVMValue::intptr_constant((intptr_t) name),
      PointerType::getUnqual(SharkType::jbyte_type())),
    value);
}
#endif // PRODUCT

// HotSpot memory barriers

void SharkBuilder::CreateUpdateBarrierSet(BarrierSet* bs, Value* field) {
  if (bs->kind() != BarrierSet::CardTableForRS &&
      bs->kind() != BarrierSet::CardTableExtension) {
    Unimplemented();
  }

  CreateStore(
    LLVMValue::jbyte_constant(CardTableModRefBS::dirty_card_val()),
    CreateIntToPtr(
      CreateAdd(
        LLVMValue::intptr_constant(
          (intptr_t) (barrier_set_cast<CardTableModRefBS>(bs)->byte_map_base)),
        CreateLShr(
          CreatePtrToInt(field, SharkType::intptr_type()),
          LLVMValue::intptr_constant(CardTableModRefBS::card_shift))),
      PointerType::getUnqual(SharkType::jbyte_type())));
}

// Helpers for accessing the code buffer

Value* SharkBuilder::code_buffer_address(int offset) {
  return CreateAdd(
    code_buffer()->base_pc(),
    LLVMValue::intptr_constant(offset));
}

Value* SharkBuilder::CreateInlineOop(jobject object, const char* name) {
  return CreateLoad(
    CreateIntToPtr(
      code_buffer_address(code_buffer()->inline_oop(object)),
      PointerType::getUnqual(SharkType::oop_type())),
    name);
}

Value* SharkBuilder::CreateInlineMetadata(Metadata* metadata, llvm::PointerType* type, const char* name) {
  assert(metadata != NULL, "inlined metadata must not be NULL");
  assert(metadata->is_metaspace_object(), "sanity check");
  return CreateLoad(
    CreateIntToPtr(
      code_buffer_address(code_buffer()->inline_Metadata(metadata)),
      PointerType::getUnqual(type)),
    name);
}

Value* SharkBuilder::CreateInlineData(void*       data,
                                      size_t      size,
                                      Type* type,
                                      const char* name) {
  return CreateIntToPtr(
    code_buffer_address(code_buffer()->inline_data(data, size)),
    type,
    name);
}

// Helpers for creating basic blocks.

BasicBlock* SharkBuilder::GetBlockInsertionPoint() const {
  BasicBlock *cur = GetInsertBlock();

  // BasicBlock::Create takes an insertBefore argument, so
  // we need to find the block _after_ the current block
  Function::iterator iter = cur->getParent()->begin();
  Function::iterator end  = cur->getParent()->end();
  while (iter != end) {
    iter++;
    if (&*iter == cur) {
      iter++;
      break;
    }
  }

  if (iter == end)
    return NULL;
  else
    return iter;
}

BasicBlock* SharkBuilder::CreateBlock(BasicBlock* ip, const char* name) const {
  return BasicBlock::Create(
    SharkContext::current(), name, GetInsertBlock()->getParent(), ip);
}

LoadInst* SharkBuilder::CreateAtomicLoad(Value* ptr, unsigned align, AtomicOrdering ordering, SynchronizationScope synchScope, bool isVolatile, const char* name) {
  return Insert(new LoadInst(ptr, name, isVolatile, align, ordering, synchScope), name);
}

StoreInst* SharkBuilder::CreateAtomicStore(Value* val, Value* ptr, unsigned align, AtomicOrdering ordering, SynchronizationScope synchScope, bool isVolatile, const char* name) {
  return Insert(new StoreInst(val, ptr, isVolatile, align, ordering, synchScope), name);
}
