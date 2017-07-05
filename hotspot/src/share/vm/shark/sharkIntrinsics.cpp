/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciMethod.hpp"
#include "shark/llvmHeaders.hpp"
#include "shark/sharkIntrinsics.hpp"
#include "shark/sharkState.hpp"
#include "shark/sharkValue.hpp"
#include "shark/shark_globals.hpp"

using namespace llvm;

bool SharkIntrinsics::is_intrinsic(ciMethod *target) {
  switch (target->intrinsic_id()) {
  case vmIntrinsics::_none:
    return false;

    // java.lang.Math
  case vmIntrinsics::_min:
  case vmIntrinsics::_max:
  case vmIntrinsics::_dabs:
  case vmIntrinsics::_dsin:
  case vmIntrinsics::_dcos:
  case vmIntrinsics::_dtan:
  case vmIntrinsics::_datan2:
  case vmIntrinsics::_dsqrt:
  case vmIntrinsics::_dlog:
  case vmIntrinsics::_dlog10:
  case vmIntrinsics::_dpow:
  case vmIntrinsics::_dexp:
    return true;

    // java.lang.Object
  case vmIntrinsics::_getClass:
    return true;

    // java.lang.System
  case vmIntrinsics::_currentTimeMillis:
    return true;

    // java.lang.Thread
  case vmIntrinsics::_currentThread:
    return true;

    // sun.misc.Unsafe
  case vmIntrinsics::_compareAndSwapInt:
    return true;

  default:
    if (SharkPerformanceWarnings) {
      warning(
        "unhandled intrinsic vmIntrinsic::%s",
        vmIntrinsics::name_at(target->intrinsic_id()));
    }
  }
  return false;
}

void SharkIntrinsics::inline_intrinsic(ciMethod *target, SharkState *state) {
  SharkIntrinsics intrinsic(state, target);
  intrinsic.do_intrinsic();
}

void SharkIntrinsics::do_intrinsic() {
  switch (target()->intrinsic_id()) {
    // java.lang.Math
  case vmIntrinsics::_min:
    do_Math_minmax(llvm::ICmpInst::ICMP_SLE);
    break;
  case vmIntrinsics::_max:
    do_Math_minmax(llvm::ICmpInst::ICMP_SGE);
    break;
  case vmIntrinsics::_dabs:
    do_Math_1to1(builder()->fabs());
    break;
  case vmIntrinsics::_dsin:
    do_Math_1to1(builder()->sin());
    break;
  case vmIntrinsics::_dcos:
    do_Math_1to1(builder()->cos());
    break;
  case vmIntrinsics::_dtan:
    do_Math_1to1(builder()->tan());
    break;
  case vmIntrinsics::_datan2:
    do_Math_2to1(builder()->atan2());
    break;
  case vmIntrinsics::_dsqrt:
    do_Math_1to1(builder()->sqrt());
    break;
  case vmIntrinsics::_dlog:
    do_Math_1to1(builder()->log());
    break;
  case vmIntrinsics::_dlog10:
    do_Math_1to1(builder()->log10());
    break;
  case vmIntrinsics::_dpow:
    do_Math_2to1(builder()->pow());
    break;
  case vmIntrinsics::_dexp:
    do_Math_1to1(builder()->exp());
    break;

    // java.lang.Object
  case vmIntrinsics::_getClass:
    do_Object_getClass();
    break;

    // java.lang.System
  case vmIntrinsics::_currentTimeMillis:
    do_System_currentTimeMillis();
    break;

    // java.lang.Thread
  case vmIntrinsics::_currentThread:
    do_Thread_currentThread();
    break;

    // sun.misc.Unsafe
  case vmIntrinsics::_compareAndSwapInt:
    do_Unsafe_compareAndSwapInt();
    break;

  default:
    ShouldNotReachHere();
  }
}

void SharkIntrinsics::do_Math_minmax(ICmpInst::Predicate p) {
  // Pop the arguments
  SharkValue *sb = state()->pop();
  SharkValue *sa = state()->pop();
  Value *a = sa->jint_value();
  Value *b = sb->jint_value();

  // Perform the test
  BasicBlock *ip       = builder()->GetBlockInsertionPoint();
  BasicBlock *return_a = builder()->CreateBlock(ip, "return_a");
  BasicBlock *return_b = builder()->CreateBlock(ip, "return_b");
  BasicBlock *done     = builder()->CreateBlock(ip, "done");

  builder()->CreateCondBr(builder()->CreateICmp(p, a, b), return_a, return_b);

  builder()->SetInsertPoint(return_a);
  builder()->CreateBr(done);

  builder()->SetInsertPoint(return_b);
  builder()->CreateBr(done);

  builder()->SetInsertPoint(done);
  PHINode *phi = builder()->CreatePHI(a->getType(), 0, "result");
  phi->addIncoming(a, return_a);
  phi->addIncoming(b, return_b);

  // Push the result
  state()->push(
    SharkValue::create_jint(
      phi,
      sa->zero_checked() && sb->zero_checked()));
}

void SharkIntrinsics::do_Math_1to1(Value *function) {
  SharkValue *empty = state()->pop();
  assert(empty == NULL, "should be");
  state()->push(
    SharkValue::create_jdouble(
      builder()->CreateCall(
        function, state()->pop()->jdouble_value())));
  state()->push(NULL);
}

void SharkIntrinsics::do_Math_2to1(Value *function) {
  SharkValue *empty = state()->pop();
  assert(empty == NULL, "should be");
  Value *y = state()->pop()->jdouble_value();
  empty = state()->pop();
  assert(empty == NULL, "should be");
  Value *x = state()->pop()->jdouble_value();

  state()->push(
    SharkValue::create_jdouble(
      builder()->CreateCall2(function, x, y)));
  state()->push(NULL);
}

void SharkIntrinsics::do_Object_getClass() {
  Value *klass = builder()->CreateValueOfStructEntry(
    state()->pop()->jobject_value(),
    in_ByteSize(oopDesc::klass_offset_in_bytes()),
    SharkType::klass_type(),
    "klass");

  state()->push(
    SharkValue::create_jobject(
      builder()->CreateValueOfStructEntry(
        klass,
        Klass::java_mirror_offset(),
        SharkType::oop_type(),
        "java_mirror"),
      true));
}

void SharkIntrinsics::do_System_currentTimeMillis() {
  state()->push(
    SharkValue::create_jlong(
      builder()->CreateCall(builder()->current_time_millis()),
      false));
  state()->push(NULL);
}

void SharkIntrinsics::do_Thread_currentThread() {
  state()->push(
    SharkValue::create_jobject(
      builder()->CreateValueOfStructEntry(
        thread(), JavaThread::threadObj_offset(),
        SharkType::oop_type(),
        "threadObj"),
      true));
}

void SharkIntrinsics::do_Unsafe_compareAndSwapInt() {
  // Pop the arguments
  Value *x      = state()->pop()->jint_value();
  Value *e      = state()->pop()->jint_value();
  SharkValue *empty = state()->pop();
  assert(empty == NULL, "should be");
  Value *offset = state()->pop()->jlong_value();
  Value *object = state()->pop()->jobject_value();
  Value *unsafe = state()->pop()->jobject_value();

  // Convert the offset
  offset = builder()->CreateCall(
    builder()->unsafe_field_offset_to_byte_offset(),
    offset);

  // Locate the field
  Value *addr = builder()->CreateIntToPtr(
    builder()->CreateAdd(
      builder()->CreatePtrToInt(object, SharkType::intptr_type()),
      builder()->CreateIntCast(offset, SharkType::intptr_type(), true)),
    PointerType::getUnqual(SharkType::jint_type()),
    "addr");

  // Perform the operation
  Value *result = builder()->CreateAtomicCmpXchg(addr, e, x, llvm::SequentiallyConsistent);
  // Push the result
  state()->push(
    SharkValue::create_jint(
      builder()->CreateIntCast(
        builder()->CreateICmpEQ(result, e), SharkType::jint_type(), true),
      false));
}
