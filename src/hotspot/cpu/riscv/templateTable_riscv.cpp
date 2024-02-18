/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2023, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "interpreter/interp_masm.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "interpreter/templateTable.hpp"
#include "memory/universe.hpp"
#include "oops/method.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "oops/resolvedFieldEntry.hpp"
#include "oops/resolvedIndyEntry.hpp"
#include "oops/resolvedMethodEntry.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.hpp"
#include "utilities/powerOfTwo.hpp"

#define __ _masm->

// Address computation: local variables

static inline Address iaddress(int n) {
  return Address(xlocals, Interpreter::local_offset_in_bytes(n));
}

static inline Address laddress(int n) {
  return iaddress(n + 1);
}

static inline Address faddress(int n) {
  return iaddress(n);
}

static inline Address daddress(int n) {
  return laddress(n);
}

static inline Address aaddress(int n) {
  return iaddress(n);
}

static inline Address iaddress(Register r, Register temp, InterpreterMacroAssembler* _masm) {
  _masm->shadd(temp, r, xlocals, temp, 3);
  return Address(temp, 0);
}

static inline Address laddress(Register r, Register temp, InterpreterMacroAssembler* _masm) {
  _masm->shadd(temp, r, xlocals, temp, 3);
  return Address(temp, Interpreter::local_offset_in_bytes(1));;
}

static inline Address faddress(Register r, Register temp, InterpreterMacroAssembler* _masm) {
  return iaddress(r, temp, _masm);
}

static inline Address daddress(Register r, Register temp, InterpreterMacroAssembler* _masm) {
  return laddress(r, temp, _masm);
}

static inline Address aaddress(Register r, Register temp, InterpreterMacroAssembler* _masm) {
  return iaddress(r, temp, _masm);
}

static inline Address at_rsp() {
  return Address(esp, 0);
}

// At top of Java expression stack which may be different than esp().  It
// isn't for category 1 objects.
static inline Address at_tos   () {
  return Address(esp,  Interpreter::expr_offset_in_bytes(0));
}

static inline Address at_tos_p1() {
  return Address(esp, Interpreter::expr_offset_in_bytes(1));
}

static inline Address at_tos_p2() {
  return Address(esp, Interpreter::expr_offset_in_bytes(2));
}

static inline Address at_tos_p3() {
  return Address(esp, Interpreter::expr_offset_in_bytes(3));
}

static inline Address at_tos_p4() {
  return Address(esp, Interpreter::expr_offset_in_bytes(4));
}

static inline Address at_tos_p5() {
  return Address(esp, Interpreter::expr_offset_in_bytes(5));
}

// Miscellaneous helper routines
// Store an oop (or null) at the Address described by obj.
// If val == noreg this means store a null
static void do_oop_store(InterpreterMacroAssembler* _masm,
                         Address dst,
                         Register val,
                         DecoratorSet decorators) {
  assert(val == noreg || val == x10, "parameter is just for looks");
  __ store_heap_oop(dst, val, x28, x29, x13, decorators);
}

static void do_oop_load(InterpreterMacroAssembler* _masm,
                        Address src,
                        Register dst,
                        DecoratorSet decorators) {
  __ load_heap_oop(dst, src, x28, x29, decorators);
}

Address TemplateTable::at_bcp(int offset) {
  assert(_desc->uses_bcp(), "inconsistent uses_bcp information");
  return Address(xbcp, offset);
}

void TemplateTable::patch_bytecode(Bytecodes::Code bc, Register bc_reg,
                                   Register temp_reg, bool load_bc_into_bc_reg /*=true*/,
                                   int byte_no) {
  if (!RewriteBytecodes) { return; }
  Label L_patch_done;

  switch (bc) {
    case Bytecodes::_fast_aputfield:  // fall through
    case Bytecodes::_fast_bputfield:  // fall through
    case Bytecodes::_fast_zputfield:  // fall through
    case Bytecodes::_fast_cputfield:  // fall through
    case Bytecodes::_fast_dputfield:  // fall through
    case Bytecodes::_fast_fputfield:  // fall through
    case Bytecodes::_fast_iputfield:  // fall through
    case Bytecodes::_fast_lputfield:  // fall through
    case Bytecodes::_fast_sputfield: {
      // We skip bytecode quickening for putfield instructions when
      // the put_code written to the constant pool cache is zero.
      // This is required so that every execution of this instruction
      // calls out to InterpreterRuntime::resolve_get_put to do
      // additional, required work.
      assert(byte_no == f1_byte || byte_no == f2_byte, "byte_no out of range");
      assert(load_bc_into_bc_reg, "we use bc_reg as temp");
      __ load_field_entry(temp_reg, bc_reg);
      if (byte_no == f1_byte) {
        __ la(temp_reg, Address(temp_reg, in_bytes(ResolvedFieldEntry::get_code_offset())));
      } else {
        __ la(temp_reg, Address(temp_reg, in_bytes(ResolvedFieldEntry::put_code_offset())));
      }
      // Load-acquire the bytecode to match store-release in ResolvedFieldEntry::fill_in()
      __ membar(MacroAssembler::AnyAny);
      __ lbu(temp_reg, Address(temp_reg, 0));
      __ membar(MacroAssembler::LoadLoad | MacroAssembler::LoadStore);
      __ mv(bc_reg, bc);
      __ beqz(temp_reg, L_patch_done);
      break;
    }
    default:
      assert(byte_no == -1, "sanity");
      // the pair bytecodes have already done the load.
      if (load_bc_into_bc_reg) {
        __ mv(bc_reg, bc);
      }
  }

  if (JvmtiExport::can_post_breakpoint()) {
    Label L_fast_patch;
    // if a breakpoint is present we can't rewrite the stream directly
    __ load_unsigned_byte(temp_reg, at_bcp(0));
    __ addi(temp_reg, temp_reg, -Bytecodes::_breakpoint); // temp_reg is temporary register.
    __ bnez(temp_reg, L_fast_patch);
    // Let breakpoint table handling rewrite to quicker bytecode
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::set_original_bytecode_at), xmethod, xbcp, bc_reg);
    __ j(L_patch_done);
    __ bind(L_fast_patch);
  }

#ifdef ASSERT
  Label L_okay;
  __ load_unsigned_byte(temp_reg, at_bcp(0));
  __ beq(temp_reg, bc_reg, L_okay);
  __ addi(temp_reg, temp_reg, -(int) Bytecodes::java_code(bc));
  __ beqz(temp_reg, L_okay);
  __ stop("patching the wrong bytecode");
  __ bind(L_okay);
#endif

  // patch bytecode
  __ sb(bc_reg, at_bcp(0));
  __ bind(L_patch_done);
}

// Individual instructions

void TemplateTable::nop() {
  transition(vtos, vtos);
  // nothing to do
}

void TemplateTable::shouldnotreachhere() {
  transition(vtos, vtos);
  __ stop("should not reach here bytecode");
}

void TemplateTable::aconst_null() {
  transition(vtos, atos);
  __ mv(x10, zr);
}

void TemplateTable::iconst(int value) {
  transition(vtos, itos);
  __ mv(x10, value);
}

void TemplateTable::lconst(int value) {
  transition(vtos, ltos);
  __ mv(x10, value);
}

void TemplateTable::fconst(int value) {
  transition(vtos, ftos);
  static float fBuf[2] = {1.0, 2.0};
  __ mv(t0, (intptr_t)fBuf);
  switch (value) {
    case 0:
      __ fmv_w_x(f10, zr);
      break;
    case 1:
      __ flw(f10, Address(t0, 0));
      break;
    case 2:
      __ flw(f10, Address(t0, sizeof(float)));
      break;
    default:
      ShouldNotReachHere();
  }
}

void TemplateTable::dconst(int value) {
  transition(vtos, dtos);
  static double dBuf[2] = {1.0, 2.0};
  __ mv(t0, (intptr_t)dBuf);
  switch (value) {
    case 0:
      __ fmv_d_x(f10, zr);
      break;
    case 1:
      __ fld(f10, Address(t0, 0));
      break;
    case 2:
      __ fld(f10, Address(t0, sizeof(double)));
      break;
    default:
      ShouldNotReachHere();
  }
}

void TemplateTable::bipush() {
  transition(vtos, itos);
  __ load_signed_byte(x10, at_bcp(1));
}

void TemplateTable::sipush() {
  transition(vtos, itos);
  if (AvoidUnalignedAccesses) {
    __ load_signed_byte(x10, at_bcp(1));
    __ load_unsigned_byte(t1, at_bcp(2));
    __ slli(x10, x10, 8);
    __ add(x10, x10, t1);
  } else {
    __ load_unsigned_short(x10, at_bcp(1));
    __ revb_h_h(x10, x10); // reverse bytes in half-word and sign-extend
  }
}

void TemplateTable::ldc(LdcType type) {
  transition(vtos, vtos);
  Label call_ldc, notFloat, notClass, notInt, Done;

  if (is_ldc_wide(type)) {
   __ get_unsigned_2_byte_index_at_bcp(x11, 1);
  } else {
   __ load_unsigned_byte(x11, at_bcp(1));
  }
  __ get_cpool_and_tags(x12, x10);

  const int base_offset = ConstantPool::header_size() * wordSize;
  const int tags_offset = Array<u1>::base_offset_in_bytes();

  // get type
  __ addi(x13, x11, tags_offset);
  __ add(x13, x10, x13);
  __ membar(MacroAssembler::AnyAny);
  __ lbu(x13, Address(x13, 0));
  __ membar(MacroAssembler::LoadLoad | MacroAssembler::LoadStore);

  // unresolved class - get the resolved class
  __ mv(t1, (u1)JVM_CONSTANT_UnresolvedClass);
  __ beq(x13, t1, call_ldc);

  // unresolved class in error state - call into runtime to throw the error
  // from the first resolution attempt
  __ mv(t1, (u1)JVM_CONSTANT_UnresolvedClassInError);
  __ beq(x13, t1, call_ldc);

  // resolved class - need to call vm to get java mirror of the class
  __ mv(t1, (u1)JVM_CONSTANT_Class);
  __ bne(x13, t1, notClass);

  __ bind(call_ldc);
  __ mv(c_rarg1, is_ldc_wide(type) ? 1 : 0);
  call_VM(x10, CAST_FROM_FN_PTR(address, InterpreterRuntime::ldc), c_rarg1);
  __ push_ptr(x10);
  __ verify_oop(x10);
  __ j(Done);

  __ bind(notClass);
  __ mv(t1, (u1)JVM_CONSTANT_Float);
  __ bne(x13, t1, notFloat);

  // ftos
  __ shadd(x11, x11, x12, x11, 3);
  __ flw(f10, Address(x11, base_offset));
  __ push_f(f10);
  __ j(Done);

  __ bind(notFloat);

  __ mv(t1, (u1)JVM_CONSTANT_Integer);
  __ bne(x13, t1, notInt);

  // itos
  __ shadd(x11, x11, x12, x11, 3);
  __ lw(x10, Address(x11, base_offset));
  __ push_i(x10);
  __ j(Done);

  __ bind(notInt);
  condy_helper(Done);

  __ bind(Done);
}

// Fast path for caching oop constants.
void TemplateTable::fast_aldc(LdcType type) {
  transition(vtos, atos);

  const Register result = x10;
  const Register tmp = x11;
  const Register rarg = x12;

  const int index_size = is_ldc_wide(type) ? sizeof(u2) : sizeof(u1);

  Label resolved;

  // We are resolved if the resolved reference cache entry contains a
  // non-null object (String, MethodType, etc.)
  assert_different_registers(result, tmp);
  // register result is trashed by next load, let's use it as temporary register
  __ get_cache_index_at_bcp(tmp, result, 1, index_size);
  __ load_resolved_reference_at_index(result, tmp);
  __ bnez(result, resolved);

  const address entry = CAST_FROM_FN_PTR(address, InterpreterRuntime::resolve_ldc);

  // first time invocation - must resolve first
  __ mv(rarg, (int)bytecode());
  __ call_VM(result, entry, rarg);

  __ bind(resolved);

  { // Check for the null sentinel.
    // If we just called the VM, it already did the mapping for us,
    // but it's harmless to retry.
    Label notNull;

    // Stash null_sentinel address to get its value later
    int32_t offset = 0;
    __ mv(rarg, Universe::the_null_sentinel_addr(), offset);
    __ ld(tmp, Address(rarg, offset));
    __ resolve_oop_handle(tmp, x15, t1);
    __ bne(result, tmp, notNull);
    __ mv(result, zr);  // null object reference
    __ bind(notNull);
  }

  if (VerifyOops) {
    // Safe to call with 0 result
    __ verify_oop(result);
  }
}

void TemplateTable::ldc2_w() {
    transition(vtos, vtos);
    Label notDouble, notLong, Done;
    __ get_unsigned_2_byte_index_at_bcp(x10, 1);

    __ get_cpool_and_tags(x11, x12);
    const int base_offset = ConstantPool::header_size() * wordSize;
    const int tags_offset = Array<u1>::base_offset_in_bytes();

    // get type
    __ add(x12, x12, x10);
    __ load_unsigned_byte(x12, Address(x12, tags_offset));
    __ mv(t1, JVM_CONSTANT_Double);
    __ bne(x12, t1, notDouble);

    // dtos
    __ shadd(x12, x10, x11, x12, 3);
    __ fld(f10, Address(x12, base_offset));
    __ push_d(f10);
    __ j(Done);

    __ bind(notDouble);
    __ mv(t1, (int)JVM_CONSTANT_Long);
    __ bne(x12, t1, notLong);

    // ltos
    __ shadd(x10, x10, x11, x10, 3);
    __ ld(x10, Address(x10, base_offset));
    __ push_l(x10);
    __ j(Done);

    __ bind(notLong);
    condy_helper(Done);
    __ bind(Done);
}

void TemplateTable::condy_helper(Label& Done) {
  const Register obj = x10;
  const Register rarg = x11;
  const Register flags = x12;
  const Register off = x13;

  const address entry = CAST_FROM_FN_PTR(address, InterpreterRuntime::resolve_ldc);

  __ mv(rarg, (int) bytecode());
  __ call_VM(obj, entry, rarg);

  __ get_vm_result_2(flags, xthread);

  // VMr = obj = base address to find primitive value to push
  // VMr2 = flags = (tos, off) using format of CPCE::_flags
  __ mv(off, flags);
  __ mv(t0, ConstantPoolCache::field_index_mask);
  __ andrw(off, off, t0);

  __ add(off, obj, off);
  const Address field(off, 0); // base + R---->base + offset

  __ slli(flags, flags, XLEN - (ConstantPoolCache::tos_state_shift + ConstantPoolCache::tos_state_bits));
  __ srli(flags, flags, XLEN - ConstantPoolCache::tos_state_bits); // (1 << 5) - 4 --> 28~31==> flags:0~3

  switch (bytecode()) {
    case Bytecodes::_ldc:   // fall through
    case Bytecodes::_ldc_w: {
      // tos in (itos, ftos, stos, btos, ctos, ztos)
      Label notInt, notFloat, notShort, notByte, notChar, notBool;
      __ mv(t1, itos);
      __ bne(flags, t1, notInt);
      // itos
      __ lw(x10, field);
      __ push(itos);
      __ j(Done);

      __ bind(notInt);
      __ mv(t1, ftos);
      __ bne(flags, t1, notFloat);
      // ftos
      __ load_float(field);
      __ push(ftos);
      __ j(Done);

      __ bind(notFloat);
      __ mv(t1, stos);
      __ bne(flags, t1, notShort);
      // stos
      __ load_signed_short(x10, field);
      __ push(stos);
      __ j(Done);

      __ bind(notShort);
      __ mv(t1, btos);
      __ bne(flags, t1, notByte);
      // btos
      __ load_signed_byte(x10, field);
      __ push(btos);
      __ j(Done);

      __ bind(notByte);
      __ mv(t1, ctos);
      __ bne(flags, t1, notChar);
      // ctos
      __ load_unsigned_short(x10, field);
      __ push(ctos);
      __ j(Done);

      __ bind(notChar);
      __ mv(t1, ztos);
      __ bne(flags, t1, notBool);
      // ztos
      __ load_signed_byte(x10, field);
      __ push(ztos);
      __ j(Done);

      __ bind(notBool);
      break;
    }

    case Bytecodes::_ldc2_w: {
      Label notLong, notDouble;
      __ mv(t1, ltos);
      __ bne(flags, t1, notLong);
      // ltos
      __ ld(x10, field);
      __ push(ltos);
      __ j(Done);

      __ bind(notLong);
      __ mv(t1, dtos);
      __ bne(flags, t1, notDouble);
      // dtos
      __ load_double(field);
      __ push(dtos);
      __ j(Done);

      __ bind(notDouble);
      break;
    }

    default:
      ShouldNotReachHere();
  }

  __ stop("bad ldc/condy");
}

void TemplateTable::locals_index(Register reg, int offset) {
  __ lbu(reg, at_bcp(offset));
  __ neg(reg, reg);
}

void TemplateTable::iload() {
  iload_internal();
}

void TemplateTable::nofast_iload() {
  iload_internal(may_not_rewrite);
}

void TemplateTable::iload_internal(RewriteControl rc) {
  transition(vtos, itos);
  if (RewriteFrequentPairs && rc == may_rewrite) {
    Label rewrite, done;
    const Register bc = x14;

    // get next bytecode
    __ load_unsigned_byte(x11, at_bcp(Bytecodes::length_for(Bytecodes::_iload)));

    // if _iload, wait to rewrite to iload2.  We only want to rewrite the
    // last two iloads in a pair.  Comparing against fast_iload means that
    // the next bytecode is neither an iload or a caload, and therefore
    // an iload pair.
    __ mv(t1, Bytecodes::_iload);
    __ beq(x11, t1, done);

    // if _fast_iload rewrite to _fast_iload2
    __ mv(t1, Bytecodes::_fast_iload);
    __ mv(bc, Bytecodes::_fast_iload2);
    __ beq(x11, t1, rewrite);

    // if _caload rewrite to _fast_icaload
    __ mv(t1, Bytecodes::_caload);
    __ mv(bc, Bytecodes::_fast_icaload);
    __ beq(x11, t1, rewrite);

    // else rewrite to _fast_iload
    __ mv(bc, Bytecodes::_fast_iload);

    // rewrite
    // bc: new bytecode
    __ bind(rewrite);
    patch_bytecode(Bytecodes::_iload, bc, x11, false);
    __ bind(done);

  }

  // do iload, get the local value into tos
  locals_index(x11);
  __ lw(x10, iaddress(x11, x10, _masm));
}

void TemplateTable::fast_iload2() {
  transition(vtos, itos);
  locals_index(x11);
  __ lw(x10, iaddress(x11, x10, _masm));
  __ push(itos);
  locals_index(x11, 3);
  __ lw(x10, iaddress(x11, x10, _masm));
}

void TemplateTable::fast_iload() {
  transition(vtos, itos);
  locals_index(x11);
  __ lw(x10, iaddress(x11, x10, _masm));
}

void TemplateTable::lload() {
  transition(vtos, ltos);
  __ lbu(x11, at_bcp(1));
  __ slli(x11, x11, LogBytesPerWord);
  __ sub(x11, xlocals, x11);
  __ ld(x10, Address(x11, Interpreter::local_offset_in_bytes(1)));
}

void TemplateTable::fload() {
  transition(vtos, ftos);
  locals_index(x11);
  __ flw(f10, faddress(x11, t0, _masm));
}

void TemplateTable::dload() {
  transition(vtos, dtos);
  __ lbu(x11, at_bcp(1));
  __ slli(x11, x11, LogBytesPerWord);
  __ sub(x11, xlocals, x11);
  __ fld(f10, Address(x11, Interpreter::local_offset_in_bytes(1)));
}

void TemplateTable::aload() {
  transition(vtos, atos);
  locals_index(x11);
  __ ld(x10, iaddress(x11, x10, _masm));
}

void TemplateTable::locals_index_wide(Register reg) {
  __ lhu(reg, at_bcp(2));
  __ revb_h_h_u(reg, reg); // reverse bytes in half-word and zero-extend
  __ neg(reg, reg);
}

void TemplateTable::wide_iload() {
  transition(vtos, itos);
  locals_index_wide(x11);
  __ lw(x10, iaddress(x11, t0, _masm));
}

void TemplateTable::wide_lload() {
  transition(vtos, ltos);
  __ lhu(x11, at_bcp(2));
  __ revb_h_h_u(x11, x11); // reverse bytes in half-word and zero-extend
  __ slli(x11, x11, LogBytesPerWord);
  __ sub(x11, xlocals, x11);
  __ ld(x10, Address(x11, Interpreter::local_offset_in_bytes(1)));
}

void TemplateTable::wide_fload() {
  transition(vtos, ftos);
  locals_index_wide(x11);
  __ flw(f10, faddress(x11, t0, _masm));
}

void TemplateTable::wide_dload() {
  transition(vtos, dtos);
  __ lhu(x11, at_bcp(2));
  __ revb_h_h_u(x11, x11); // reverse bytes in half-word and zero-extend
  __ slli(x11, x11, LogBytesPerWord);
  __ sub(x11, xlocals, x11);
  __ fld(f10, Address(x11, Interpreter::local_offset_in_bytes(1)));
}

void TemplateTable::wide_aload() {
  transition(vtos, atos);
  locals_index_wide(x11);
  __ ld(x10, aaddress(x11, t0, _masm));
}

void TemplateTable::index_check(Register array, Register index) {
  // destroys x11, t0
  // sign extend index for use by indexed load
  // check index
  const Register length = t0;
  __ lwu(length, Address(array, arrayOopDesc::length_offset_in_bytes()));
  if (index != x11) {
    assert(x11 != array, "different registers");
    __ mv(x11, index);
  }
  Label ok;
  __ sign_extend(index, index, 32);
  __ bltu(index, length, ok);
  __ mv(x13, array);
  __ mv(t0, Interpreter::_throw_ArrayIndexOutOfBoundsException_entry);
  __ jr(t0);
  __ bind(ok);
}

void TemplateTable::iaload() {
  transition(itos, itos);
  __ mv(x11, x10);
  __ pop_ptr(x10);
  // x10: array
  // x11: index
  index_check(x10, x11); // leaves index in x11
  __ add(x11, x11, arrayOopDesc::base_offset_in_bytes(T_INT) >> 2);
  __ shadd(x10, x11, x10, t0, 2);
  __ access_load_at(T_INT, IN_HEAP | IS_ARRAY, x10, Address(x10), noreg, noreg);
  __ sign_extend(x10, x10, 32);
}

void TemplateTable::laload() {
  transition(itos, ltos);
  __ mv(x11, x10);
  __ pop_ptr(x10);
  // x10: array
  // x11: index
  index_check(x10, x11); // leaves index in x11
  __ add(x11, x11, arrayOopDesc::base_offset_in_bytes(T_LONG) >> 3);
  __ shadd(x10, x11, x10, t0, 3);
  __ access_load_at(T_LONG, IN_HEAP | IS_ARRAY, x10, Address(x10), noreg, noreg);
}

void TemplateTable::faload() {
  transition(itos, ftos);
  __ mv(x11, x10);
  __ pop_ptr(x10);
  // x10: array
  // x11: index
  index_check(x10, x11); // leaves index in x11
  __ add(x11, x11, arrayOopDesc::base_offset_in_bytes(T_FLOAT) >> 2);
  __ shadd(x10, x11, x10, t0, 2);
  __ access_load_at(T_FLOAT, IN_HEAP | IS_ARRAY, x10, Address(x10), noreg, noreg);
}

void TemplateTable::daload() {
  transition(itos, dtos);
  __ mv(x11, x10);
  __ pop_ptr(x10);
  // x10: array
  // x11: index
  index_check(x10, x11); // leaves index in x11
  __ add(x11, x11, arrayOopDesc::base_offset_in_bytes(T_DOUBLE) >> 3);
  __ shadd(x10, x11, x10, t0, 3);
  __ access_load_at(T_DOUBLE, IN_HEAP | IS_ARRAY, x10, Address(x10), noreg, noreg);
}

void TemplateTable::aaload() {
  transition(itos, atos);
  __ mv(x11, x10);
  __ pop_ptr(x10);
  // x10: array
  // x11: index
  index_check(x10, x11); // leaves index in x11
  __ add(x11, x11, arrayOopDesc::base_offset_in_bytes(T_OBJECT) >> LogBytesPerHeapOop);
  __ shadd(x10, x11, x10, t0, LogBytesPerHeapOop);
  do_oop_load(_masm, Address(x10), x10, IS_ARRAY);
}

void TemplateTable::baload() {
  transition(itos, itos);
  __ mv(x11, x10);
  __ pop_ptr(x10);
  // x10: array
  // x11: index
  index_check(x10, x11); // leaves index in x11
  __ add(x11, x11, arrayOopDesc::base_offset_in_bytes(T_BYTE) >> 0);
  __ shadd(x10, x11, x10, t0, 0);
  __ access_load_at(T_BYTE, IN_HEAP | IS_ARRAY, x10, Address(x10), noreg, noreg);
}

void TemplateTable::caload() {
  transition(itos, itos);
  __ mv(x11, x10);
  __ pop_ptr(x10);
  // x10: array
  // x11: index
  index_check(x10, x11); // leaves index in x11
  __ add(x11, x11, arrayOopDesc::base_offset_in_bytes(T_CHAR) >> 1);
  __ shadd(x10, x11, x10, t0, 1);
  __ access_load_at(T_CHAR, IN_HEAP | IS_ARRAY, x10, Address(x10), noreg, noreg);
}

// iload followed by caload frequent pair
void TemplateTable::fast_icaload() {
  transition(vtos, itos);
  // load index out of locals
  locals_index(x12);
  __ lw(x11, iaddress(x12, x11, _masm));
  __ pop_ptr(x10);

  // x10: array
  // x11: index
  index_check(x10, x11); // leaves index in x11, kills t0
  __ add(x11, x11, arrayOopDesc::base_offset_in_bytes(T_CHAR) >> 1); // addi, max imm is 2^11
  __ shadd(x10, x11, x10, t0, 1);
  __ access_load_at(T_CHAR, IN_HEAP | IS_ARRAY, x10, Address(x10), noreg, noreg);
}

void TemplateTable::saload() {
  transition(itos, itos);
  __ mv(x11, x10);
  __ pop_ptr(x10);
  // x10: array
  // x11: index
  index_check(x10, x11); // leaves index in x11, kills t0
  __ add(x11, x11, arrayOopDesc::base_offset_in_bytes(T_SHORT) >> 1);
  __ shadd(x10, x11, x10, t0, 1);
  __ access_load_at(T_SHORT, IN_HEAP | IS_ARRAY, x10, Address(x10), noreg, noreg);
}

void TemplateTable::iload(int n) {
  transition(vtos, itos);
  __ lw(x10, iaddress(n));
}

void TemplateTable::lload(int n) {
  transition(vtos, ltos);
  __ ld(x10, laddress(n));
}

void TemplateTable::fload(int n) {
  transition(vtos, ftos);
  __ flw(f10, faddress(n));
}

void TemplateTable::dload(int n) {
  transition(vtos, dtos);
  __ fld(f10, daddress(n));
}

void TemplateTable::aload(int n) {
  transition(vtos, atos);
  __ ld(x10, iaddress(n));
}

void TemplateTable::aload_0() {
  aload_0_internal();
}

void TemplateTable::nofast_aload_0() {
  aload_0_internal(may_not_rewrite);
}

void TemplateTable::aload_0_internal(RewriteControl rc) {
  // According to bytecode histograms, the pairs:
  //
  // _aload_0, _fast_igetfield
  // _aload_0, _fast_agetfield
  // _aload_0, _fast_fgetfield
  //
  // occur frequently. If RewriteFrequentPairs is set, the (slow)
  // _aload_0 bytecode checks if the next bytecode is either
  // _fast_igetfield, _fast_agetfield or _fast_fgetfield and then
  // rewrites the current bytecode into a pair bytecode; otherwise it
  // rewrites the current bytecode into _fast_aload_0 that doesn't do
  // the pair check anymore.
  //
  // Note: If the next bytecode is _getfield, the rewrite must be
  //       delayed, otherwise we may miss an opportunity for a pair.
  //
  // Also rewrite frequent pairs
  //   aload_0, aload_1
  //   aload_0, iload_1
  // These bytecodes with a small amount of code are most profitable
  // to rewrite
  if (RewriteFrequentPairs && rc == may_rewrite) {
    Label rewrite, done;
    const Register bc = x14;

    // get next bytecode
    __ load_unsigned_byte(x11, at_bcp(Bytecodes::length_for(Bytecodes::_aload_0)));

    // if _getfield then wait with rewrite
    __ mv(t1, Bytecodes::Bytecodes::_getfield);
    __ beq(x11, t1, done);

    // if _igetfield then rewrite to _fast_iaccess_0
    assert(Bytecodes::java_code(Bytecodes::_fast_iaccess_0) == Bytecodes::_aload_0, "fix bytecode definition");
    __ mv(t1, Bytecodes::_fast_igetfield);
    __ mv(bc, Bytecodes::_fast_iaccess_0);
    __ beq(x11, t1, rewrite);

    // if _agetfield then rewrite to _fast_aaccess_0
    assert(Bytecodes::java_code(Bytecodes::_fast_aaccess_0) == Bytecodes::_aload_0, "fix bytecode definition");
    __ mv(t1, Bytecodes::_fast_agetfield);
    __ mv(bc, Bytecodes::_fast_aaccess_0);
    __ beq(x11, t1, rewrite);

    // if _fgetfield then rewrite to _fast_faccess_0
    assert(Bytecodes::java_code(Bytecodes::_fast_faccess_0) == Bytecodes::_aload_0, "fix bytecode definition");
    __ mv(t1, Bytecodes::_fast_fgetfield);
    __ mv(bc, Bytecodes::_fast_faccess_0);
    __ beq(x11, t1, rewrite);

    // else rewrite to _fast_aload0
    assert(Bytecodes::java_code(Bytecodes::_fast_aload_0) == Bytecodes::_aload_0, "fix bytecode definition");
    __ mv(bc, Bytecodes::Bytecodes::_fast_aload_0);

    // rewrite
    // bc: new bytecode
    __ bind(rewrite);
    patch_bytecode(Bytecodes::_aload_0, bc, x11, false);

    __ bind(done);
  }

  // Do actual aload_0 (must do this after patch_bytecode which might call VM and GC might change oop).
  aload(0);
}

void TemplateTable::istore() {
  transition(itos, vtos);
  locals_index(x11);
  __ sw(x10, iaddress(x11, t0, _masm));
}

void TemplateTable::lstore() {
  transition(ltos, vtos);
  locals_index(x11);
  __ sd(x10, laddress(x11, t0, _masm));
}

void TemplateTable::fstore() {
  transition(ftos, vtos);
  locals_index(x11);
  __ fsw(f10, iaddress(x11, t0, _masm));
}

void TemplateTable::dstore() {
  transition(dtos, vtos);
  locals_index(x11);
  __ fsd(f10, daddress(x11, t0, _masm));
}

void TemplateTable::astore() {
  transition(vtos, vtos);
  __ pop_ptr(x10);
  locals_index(x11);
  __ sd(x10, aaddress(x11, t0, _masm));
}

void TemplateTable::wide_istore() {
  transition(vtos, vtos);
  __ pop_i();
  locals_index_wide(x11);
  __ sw(x10, iaddress(x11, t0, _masm));
}

void TemplateTable::wide_lstore() {
  transition(vtos, vtos);
  __ pop_l();
  locals_index_wide(x11);
  __ sd(x10, laddress(x11, t0, _masm));
}

void TemplateTable::wide_fstore() {
  transition(vtos, vtos);
  __ pop_f();
  locals_index_wide(x11);
  __ fsw(f10, faddress(x11, t0, _masm));
}

void TemplateTable::wide_dstore() {
  transition(vtos, vtos);
  __ pop_d();
  locals_index_wide(x11);
  __ fsd(f10, daddress(x11, t0, _masm));
}

void TemplateTable::wide_astore() {
  transition(vtos, vtos);
  __ pop_ptr(x10);
  locals_index_wide(x11);
  __ sd(x10, aaddress(x11, t0, _masm));
}

void TemplateTable::iastore() {
  transition(itos, vtos);
  __ pop_i(x11);
  __ pop_ptr(x13);
  // x10: value
  // x11: index
  // x13: array
  index_check(x13, x11); // prefer index in x11
  __ add(x11, x11, arrayOopDesc::base_offset_in_bytes(T_INT) >> 2);
  __ shadd(t0, x11, x13, t0, 2);
  __ access_store_at(T_INT, IN_HEAP | IS_ARRAY, Address(t0, 0), x10, noreg, noreg, noreg);
}

void TemplateTable::lastore() {
  transition(ltos, vtos);
  __ pop_i(x11);
  __ pop_ptr(x13);
  // x10: value
  // x11: index
  // x13: array
  index_check(x13, x11); // prefer index in x11
  __ add(x11, x11, arrayOopDesc::base_offset_in_bytes(T_LONG) >> 3);
  __ shadd(t0, x11, x13, t0, 3);
  __ access_store_at(T_LONG, IN_HEAP | IS_ARRAY, Address(t0, 0), x10, noreg, noreg, noreg);
}

void TemplateTable::fastore() {
  transition(ftos, vtos);
  __ pop_i(x11);
  __ pop_ptr(x13);
  // f10: value
  // x11:  index
  // x13:  array
  index_check(x13, x11); // prefer index in x11
  __ add(x11, x11, arrayOopDesc::base_offset_in_bytes(T_FLOAT) >> 2);
  __ shadd(t0, x11, x13, t0, 2);
  __ access_store_at(T_FLOAT, IN_HEAP | IS_ARRAY, Address(t0, 0), noreg /* ftos */, noreg, noreg, noreg);
}

void TemplateTable::dastore() {
  transition(dtos, vtos);
  __ pop_i(x11);
  __ pop_ptr(x13);
  // f10: value
  // x11:  index
  // x13:  array
  index_check(x13, x11); // prefer index in x11
  __ add(x11, x11, arrayOopDesc::base_offset_in_bytes(T_DOUBLE) >> 3);
  __ shadd(t0, x11, x13, t0, 3);
  __ access_store_at(T_DOUBLE, IN_HEAP | IS_ARRAY, Address(t0, 0), noreg /* dtos */, noreg, noreg, noreg);
}

void TemplateTable::aastore() {
  Label is_null, ok_is_subtype, done;
  transition(vtos, vtos);
  // stack: ..., array, index, value
  __ ld(x10, at_tos());    // value
  __ ld(x12, at_tos_p1()); // index
  __ ld(x13, at_tos_p2()); // array

  index_check(x13, x12);     // kills x11
  __ add(x14, x12, arrayOopDesc::base_offset_in_bytes(T_OBJECT) >> LogBytesPerHeapOop);
  __ shadd(x14, x14, x13, x14, LogBytesPerHeapOop);

  Address element_address(x14, 0);

  // do array store check - check for null value first
  __ beqz(x10, is_null);

  // Move subklass into x11
  __ load_klass(x11, x10);
  // Move superklass into x10
  __ load_klass(x10, x13);
  __ ld(x10, Address(x10,
                     ObjArrayKlass::element_klass_offset()));
  // Compress array + index * oopSize + 12 into a single register.  Frees x12.

  // Generate subtype check.  Blows x12, x15
  // Superklass in x10.  Subklass in x11.
  __ gen_subtype_check(x11, ok_is_subtype);

  // Come here on failure
  // object is at TOS
  __ j(Interpreter::_throw_ArrayStoreException_entry);

  // Come here on success
  __ bind(ok_is_subtype);

  // Get the value we will store
  __ ld(x10, at_tos());
  // Now store using the appropriate barrier
  do_oop_store(_masm, element_address, x10, IS_ARRAY);
  __ j(done);

  // Have a null in x10, x13=array, x12=index.  Store null at ary[idx]
  __ bind(is_null);
  __ profile_null_seen(x12);

  // Store a null
  do_oop_store(_masm, element_address, noreg, IS_ARRAY);

  // Pop stack arguments
  __ bind(done);
  __ add(esp, esp, 3 * Interpreter::stackElementSize);
}

void TemplateTable::bastore() {
  transition(itos, vtos);
  __ pop_i(x11);
  __ pop_ptr(x13);
  // x10: value
  // x11: index
  // x13: array
  index_check(x13, x11); // prefer index in x11

  // Need to check whether array is boolean or byte
  // since both types share the bastore bytecode.
  __ load_klass(x12, x13);
  __ lwu(x12, Address(x12, Klass::layout_helper_offset()));
  Label L_skip;
  __ test_bit(t0, x12, exact_log2(Klass::layout_helper_boolean_diffbit()));
  __ beqz(t0, L_skip);
  __ andi(x10, x10, 1);  // if it is a T_BOOLEAN array, mask the stored value to 0/1
  __ bind(L_skip);

  __ add(x11, x11, arrayOopDesc::base_offset_in_bytes(T_BYTE) >> 0);

  __ add(x11, x13, x11);
  __ access_store_at(T_BYTE, IN_HEAP | IS_ARRAY, Address(x11, 0), x10, noreg, noreg, noreg);
}

void TemplateTable::castore() {
  transition(itos, vtos);
  __ pop_i(x11);
  __ pop_ptr(x13);
  // x10: value
  // x11: index
  // x13: array
  index_check(x13, x11); // prefer index in x11
  __ add(x11, x11, arrayOopDesc::base_offset_in_bytes(T_CHAR) >> 1);
  __ shadd(t0, x11, x13, t0, 1);
  __ access_store_at(T_CHAR, IN_HEAP | IS_ARRAY, Address(t0, 0), x10, noreg, noreg, noreg);
}

void TemplateTable::sastore() {
  castore();
}

void TemplateTable::istore(int n) {
  transition(itos, vtos);
  __ sd(x10, iaddress(n));
}

void TemplateTable::lstore(int n) {
  transition(ltos, vtos);
  __ sd(x10, laddress(n));
}

void TemplateTable::fstore(int n) {
  transition(ftos, vtos);
  __ fsw(f10, faddress(n));
}

void TemplateTable::dstore(int n) {
  transition(dtos, vtos);
  __ fsd(f10, daddress(n));
}

void TemplateTable::astore(int n) {
  transition(vtos, vtos);
  __ pop_ptr(x10);
  __ sd(x10, iaddress(n));
}

void TemplateTable::pop() {
  transition(vtos, vtos);
  __ addi(esp, esp, Interpreter::stackElementSize);
}

void TemplateTable::pop2() {
  transition(vtos, vtos);
  __ addi(esp, esp, 2 * Interpreter::stackElementSize);
}

void TemplateTable::dup() {
  transition(vtos, vtos);
  __ ld(x10, Address(esp, 0));
  __ push_reg(x10);
  // stack: ..., a, a
}

void TemplateTable::dup_x1() {
  transition(vtos, vtos);
  // stack: ..., a, b
  __ ld(x10, at_tos());  // load b
  __ ld(x12, at_tos_p1());  // load a
  __ sd(x10, at_tos_p1());  // store b
  __ sd(x12, at_tos());  // store a
  __ push_reg(x10);                  // push b
  // stack: ..., b, a, b
}

void TemplateTable::dup_x2() {
  transition(vtos, vtos);
  // stack: ..., a, b, c
  __ ld(x10, at_tos());  // load c
  __ ld(x12, at_tos_p2());  // load a
  __ sd(x10, at_tos_p2());  // store c in a
  __ push_reg(x10);      // push c
  // stack: ..., c, b, c, c
  __ ld(x10, at_tos_p2());  // load b
  __ sd(x12, at_tos_p2());  // store a in b
  // stack: ..., c, a, c, c
  __ sd(x10, at_tos_p1());  // store b in c
  // stack: ..., c, a, b, c
}

void TemplateTable::dup2() {
  transition(vtos, vtos);
  // stack: ..., a, b
  __ ld(x10, at_tos_p1());  // load a
  __ push_reg(x10);                  // push a
  __ ld(x10, at_tos_p1());  // load b
  __ push_reg(x10);                  // push b
  // stack: ..., a, b, a, b
}

void TemplateTable::dup2_x1() {
  transition(vtos, vtos);
  // stack: ..., a, b, c
  __ ld(x12, at_tos());     // load c
  __ ld(x10, at_tos_p1());  // load b
  __ push_reg(x10);             // push b
  __ push_reg(x12);             // push c
  // stack: ..., a, b, c, b, c
  __ sd(x12, at_tos_p3());  // store c in b
  // stack: ..., a, c, c, b, c
  __ ld(x12, at_tos_p4());  // load a
  __ sd(x12, at_tos_p2());  // store a in 2nd c
  // stack: ..., a, c, a, b, c
  __ sd(x10, at_tos_p4());  // store b in a
  // stack: ..., b, c, a, b, c
}

void TemplateTable::dup2_x2() {
  transition(vtos, vtos);
  // stack: ..., a, b, c, d
  __ ld(x12, at_tos());     // load d
  __ ld(x10, at_tos_p1());  // load c
  __ push_reg(x10);             // push c
  __ push_reg(x12);             // push d
  // stack: ..., a, b, c, d, c, d
  __ ld(x10, at_tos_p4());  // load b
  __ sd(x10, at_tos_p2());  // store b in d
  __ sd(x12, at_tos_p4());  // store d in b
  // stack: ..., a, d, c, b, c, d
  __ ld(x12, at_tos_p5());  // load a
  __ ld(x10, at_tos_p3());  // load c
  __ sd(x12, at_tos_p3());  // store a in c
  __ sd(x10, at_tos_p5());  // store c in a
  // stack: ..., c, d, a, b, c, d
}

void TemplateTable::swap() {
  transition(vtos, vtos);
  // stack: ..., a, b
  __ ld(x12, at_tos_p1());  // load a
  __ ld(x10, at_tos());     // load b
  __ sd(x12, at_tos());     // store a in b
  __ sd(x10, at_tos_p1());  // store b in a
  // stack: ..., b, a
}

void TemplateTable::iop2(Operation op) {
  transition(itos, itos);
  // x10 <== x11 op x10
  __ pop_i(x11);
  switch (op) {
    case add  : __ addw(x10, x11, x10);  break;
    case sub  : __ subw(x10, x11, x10);  break;
    case mul  : __ mulw(x10, x11, x10);  break;
    case _and : __ andrw(x10, x11, x10); break;
    case _or  : __ orrw(x10, x11, x10);  break;
    case _xor : __ xorrw(x10, x11, x10); break;
    case shl  : __ sllw(x10, x11, x10);  break;
    case shr  : __ sraw(x10, x11, x10);  break;
    case ushr : __ srlw(x10, x11, x10);  break;
    default   : ShouldNotReachHere();
  }
}

void TemplateTable::lop2(Operation op) {
  transition(ltos, ltos);
  // x10 <== x11 op x10
  __ pop_l(x11);
  switch (op) {
    case add  : __ add(x10, x11, x10);  break;
    case sub  : __ sub(x10, x11, x10);  break;
    case mul  : __ mul(x10, x11, x10);  break;
    case _and : __ andr(x10, x11, x10); break;
    case _or  : __ orr(x10, x11, x10);  break;
    case _xor : __ xorr(x10, x11, x10); break;
    default   : ShouldNotReachHere();
  }
}

void TemplateTable::idiv() {
  transition(itos, itos);
  // explicitly check for div0
  Label no_div0;
  __ bnez(x10, no_div0);
  __ mv(t0, Interpreter::_throw_ArithmeticException_entry);
  __ jr(t0);
  __ bind(no_div0);
  __ pop_i(x11);
  // x10 <== x11 idiv x10
  __ corrected_idivl(x10, x11, x10, /* want_remainder */ false, /* is_signed */ true);
}

void TemplateTable::irem() {
  transition(itos, itos);
  // explicitly check for div0
  Label no_div0;
  __ bnez(x10, no_div0);
  __ mv(t0, Interpreter::_throw_ArithmeticException_entry);
  __ jr(t0);
  __ bind(no_div0);
  __ pop_i(x11);
  // x10 <== x11 irem x10
  __ corrected_idivl(x10, x11, x10, /* want_remainder */ true, /* is_signed */ true);
}

void TemplateTable::lmul() {
  transition(ltos, ltos);
  __ pop_l(x11);
  __ mul(x10, x10, x11);
}

void TemplateTable::ldiv() {
  transition(ltos, ltos);
  // explicitly check for div0
  Label no_div0;
  __ bnez(x10, no_div0);
  __ mv(t0, Interpreter::_throw_ArithmeticException_entry);
  __ jr(t0);
  __ bind(no_div0);
  __ pop_l(x11);
  // x10 <== x11 ldiv x10
  __ corrected_idivq(x10, x11, x10, /* want_remainder */ false, /* is_signed */ true);
}

void TemplateTable::lrem() {
  transition(ltos, ltos);
  // explicitly check for div0
  Label no_div0;
  __ bnez(x10, no_div0);
  __ mv(t0, Interpreter::_throw_ArithmeticException_entry);
  __ jr(t0);
  __ bind(no_div0);
  __ pop_l(x11);
  // x10 <== x11 lrem x10
  __ corrected_idivq(x10, x11, x10, /* want_remainder */ true, /* is_signed */ true);
}

void TemplateTable::lshl() {
  transition(itos, ltos);
  // shift count is in x10
  __ pop_l(x11);
  __ sll(x10, x11, x10);
}

void TemplateTable::lshr() {
  transition(itos, ltos);
  // shift count is in x10
  __ pop_l(x11);
  __ sra(x10, x11, x10);
}

void TemplateTable::lushr() {
  transition(itos, ltos);
  // shift count is in x10
  __ pop_l(x11);
  __ srl(x10, x11, x10);
}

void TemplateTable::fop2(Operation op) {
  transition(ftos, ftos);
  switch (op) {
    case add:
      __ pop_f(f11);
      __ fadd_s(f10, f11, f10);
      break;
    case sub:
      __ pop_f(f11);
      __ fsub_s(f10, f11, f10);
      break;
    case mul:
      __ pop_f(f11);
      __ fmul_s(f10, f11, f10);
      break;
    case div:
      __ pop_f(f11);
      __ fdiv_s(f10, f11, f10);
      break;
    case rem:
      __ fmv_s(f11, f10);
      __ pop_f(f10);
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::frem));
      break;
    default:
      ShouldNotReachHere();
  }
}

void TemplateTable::dop2(Operation op) {
  transition(dtos, dtos);
  switch (op) {
    case add:
      __ pop_d(f11);
      __ fadd_d(f10, f11, f10);
      break;
    case sub:
      __ pop_d(f11);
      __ fsub_d(f10, f11, f10);
      break;
    case mul:
      __ pop_d(f11);
      __ fmul_d(f10, f11, f10);
      break;
    case div:
      __ pop_d(f11);
      __ fdiv_d(f10, f11, f10);
      break;
    case rem:
      __ fmv_d(f11, f10);
      __ pop_d(f10);
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::drem));
      break;
    default:
      ShouldNotReachHere();
  }
}

void TemplateTable::ineg() {
  transition(itos, itos);
  __ negw(x10, x10);
}

void TemplateTable::lneg() {
  transition(ltos, ltos);
  __ neg(x10, x10);
}

void TemplateTable::fneg() {
  transition(ftos, ftos);
  __ fneg_s(f10, f10);
}

void TemplateTable::dneg() {
  transition(dtos, dtos);
  __ fneg_d(f10, f10);
}

void TemplateTable::iinc() {
  transition(vtos, vtos);
  __ load_signed_byte(x11, at_bcp(2)); // get constant
  locals_index(x12);
  __ ld(x10, iaddress(x12, x10, _masm));
  __ addw(x10, x10, x11);
  __ sd(x10, iaddress(x12, t0, _masm));
}

void TemplateTable::wide_iinc() {
  transition(vtos, vtos);
  __ lwu(x11, at_bcp(2)); // get constant and index
  __ revb_h_w_u(x11, x11); // reverse bytes in half-word (32bit) and zero-extend
  __ zero_extend(x12, x11, 16);
  __ neg(x12, x12);
  __ slli(x11, x11, 32);
  __ srai(x11, x11, 48);
  __ ld(x10, iaddress(x12, t0, _masm));
  __ addw(x10, x10, x11);
  __ sd(x10, iaddress(x12, t0, _masm));
}

void TemplateTable::convert() {
  // Checking
#ifdef ASSERT
  {
    TosState tos_in  = ilgl;
    TosState tos_out = ilgl;
    switch (bytecode()) {
      case Bytecodes::_i2l: // fall through
      case Bytecodes::_i2f: // fall through
      case Bytecodes::_i2d: // fall through
      case Bytecodes::_i2b: // fall through
      case Bytecodes::_i2c: // fall through
      case Bytecodes::_i2s: tos_in = itos; break;
      case Bytecodes::_l2i: // fall through
      case Bytecodes::_l2f: // fall through
      case Bytecodes::_l2d: tos_in = ltos; break;
      case Bytecodes::_f2i: // fall through
      case Bytecodes::_f2l: // fall through
      case Bytecodes::_f2d: tos_in = ftos; break;
      case Bytecodes::_d2i: // fall through
      case Bytecodes::_d2l: // fall through
      case Bytecodes::_d2f: tos_in = dtos; break;
      default             : ShouldNotReachHere();
    }
    switch (bytecode()) {
      case Bytecodes::_l2i: // fall through
      case Bytecodes::_f2i: // fall through
      case Bytecodes::_d2i: // fall through
      case Bytecodes::_i2b: // fall through
      case Bytecodes::_i2c: // fall through
      case Bytecodes::_i2s: tos_out = itos; break;
      case Bytecodes::_i2l: // fall through
      case Bytecodes::_f2l: // fall through
      case Bytecodes::_d2l: tos_out = ltos; break;
      case Bytecodes::_i2f: // fall through
      case Bytecodes::_l2f: // fall through
      case Bytecodes::_d2f: tos_out = ftos; break;
      case Bytecodes::_i2d: // fall through
      case Bytecodes::_l2d: // fall through
      case Bytecodes::_f2d: tos_out = dtos; break;
      default             : ShouldNotReachHere();
    }
    transition(tos_in, tos_out);
  }
#endif // ASSERT

  // Conversion
  switch (bytecode()) {
    case Bytecodes::_i2l:
      __ sign_extend(x10, x10, 32);
      break;
    case Bytecodes::_i2f:
      __ fcvt_s_w(f10, x10);
      break;
    case Bytecodes::_i2d:
      __ fcvt_d_w(f10, x10);
      break;
    case Bytecodes::_i2b:
      __ sign_extend(x10, x10, 8);
      break;
    case Bytecodes::_i2c:
      __ zero_extend(x10, x10, 16);
      break;
    case Bytecodes::_i2s:
      __ sign_extend(x10, x10, 16);
      break;
    case Bytecodes::_l2i:
      __ sign_extend(x10, x10, 32);
      break;
    case Bytecodes::_l2f:
      __ fcvt_s_l(f10, x10);
      break;
    case Bytecodes::_l2d:
      __ fcvt_d_l(f10, x10);
      break;
    case Bytecodes::_f2i:
      __ fcvt_w_s_safe(x10, f10);
      break;
    case Bytecodes::_f2l:
      __ fcvt_l_s_safe(x10, f10);
      break;
    case Bytecodes::_f2d:
      __ fcvt_d_s(f10, f10);
      break;
    case Bytecodes::_d2i:
      __ fcvt_w_d_safe(x10, f10);
      break;
    case Bytecodes::_d2l:
      __ fcvt_l_d_safe(x10, f10);
      break;
    case Bytecodes::_d2f:
      __ fcvt_s_d(f10, f10);
      break;
    default:
      ShouldNotReachHere();
  }
}

void TemplateTable::lcmp() {
  transition(ltos, itos);
  __ pop_l(x11);
  __ cmp_l2i(t0, x11, x10);
  __ mv(x10, t0);
}

void TemplateTable::float_cmp(bool is_float, int unordered_result) {
  // For instruction feq, flt and fle, the result is 0 if either operand is NaN
  if (is_float) {
    __ pop_f(f11);
    // if unordered_result < 0:
    //   we want -1 for unordered or less than, 0 for equal and 1 for
    //   greater than.
    // else:
    //   we want -1 for less than, 0 for equal and 1 for unordered or
    //   greater than.
    // f11 primary, f10 secondary
    __ float_compare(x10, f11, f10, unordered_result);
  } else {
    __ pop_d(f11);
    // if unordered_result < 0:
    //   we want -1 for unordered or less than, 0 for equal and 1 for
    //   greater than.
    // else:
    //   we want -1 for less than, 0 for equal and 1 for unordered or
    //   greater than.
    // f11 primary, f10 secondary
    __ double_compare(x10, f11, f10, unordered_result);
  }
}

void TemplateTable::branch(bool is_jsr, bool is_wide) {
  __ profile_taken_branch(x10, x11);
  const ByteSize be_offset = MethodCounters::backedge_counter_offset() +
                             InvocationCounter::counter_offset();
  const ByteSize inv_offset = MethodCounters::invocation_counter_offset() +
                              InvocationCounter::counter_offset();

  // load branch displacement
  if (!is_wide) {
    if (AvoidUnalignedAccesses) {
      __ lb(x12, at_bcp(1));
      __ lbu(t1, at_bcp(2));
      __ slli(x12, x12, 8);
      __ add(x12, x12, t1);
    } else {
      __ lhu(x12, at_bcp(1));
      __ revb_h_h(x12, x12); // reverse bytes in half-word and sign-extend
    }
  } else {
    __ lwu(x12, at_bcp(1));
    __ revb_w_w(x12, x12); // reverse bytes in word and sign-extend
  }

  // Handle all the JSR stuff here, then exit.
  // It's much shorter and cleaner than intermingling with the non-JSR
  // normal-branch stuff occurring below.

  if (is_jsr) {
    // compute return address as bci
    __ ld(t1, Address(xmethod, Method::const_offset()));
    __ add(t1, t1,
           in_bytes(ConstMethod::codes_offset()) - (is_wide ? 5 : 3));
    __ sub(x11, xbcp, t1);
    __ push_i(x11);
    // Adjust the bcp by the 16-bit displacement in x12
    __ add(xbcp, xbcp, x12);
    __ load_unsigned_byte(t0, Address(xbcp, 0));
    // load the next target bytecode into t0, it is the argument of dispatch_only
    __ dispatch_only(vtos, /*generate_poll*/true);
    return;
  }

  // Normal (non-jsr) branch handling

  // Adjust the bcp by the displacement in x12
  __ add(xbcp, xbcp, x12);

  assert(UseLoopCounter || !UseOnStackReplacement,
         "on-stack-replacement requires loop counters");
  Label backedge_counter_overflow;
  Label dispatch;
  if (UseLoopCounter) {
    // increment backedge counter for backward branches
    // x10: MDO
    // x11: MDO bumped taken-count
    // x12: target offset
    __ bgtz(x12, dispatch); // count only if backward branch

    // check if MethodCounters exists
    Label has_counters;
    __ ld(t0, Address(xmethod, Method::method_counters_offset()));
    __ bnez(t0, has_counters);
    __ push_reg(x10);
    __ push_reg(x11);
    __ push_reg(x12);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address,
            InterpreterRuntime::build_method_counters), xmethod);
    __ pop_reg(x12);
    __ pop_reg(x11);
    __ pop_reg(x10);
    __ ld(t0, Address(xmethod, Method::method_counters_offset()));
    __ beqz(t0, dispatch); // No MethodCounters allocated, OutOfMemory
    __ bind(has_counters);

    Label no_mdo;
    int increment = InvocationCounter::count_increment;
    if (ProfileInterpreter) {
      // Are we profiling?
      __ ld(x11, Address(xmethod, in_bytes(Method::method_data_offset())));
      __ beqz(x11, no_mdo);
      // Increment the MDO backedge counter
      const Address mdo_backedge_counter(x11, in_bytes(MethodData::backedge_counter_offset()) +
                                         in_bytes(InvocationCounter::counter_offset()));
      const Address mask(x11, in_bytes(MethodData::backedge_mask_offset()));
      __ increment_mask_and_jump(mdo_backedge_counter, increment, mask,
                                 x10, t0, false,
                                 UseOnStackReplacement ? &backedge_counter_overflow : &dispatch);
      __ j(dispatch);
    }
    __ bind(no_mdo);
    // Increment backedge counter in MethodCounters*
    __ ld(t0, Address(xmethod, Method::method_counters_offset()));
    const Address mask(t0, in_bytes(MethodCounters::backedge_mask_offset()));
    __ increment_mask_and_jump(Address(t0, be_offset), increment, mask,
                               x10, t1, false,
                               UseOnStackReplacement ? &backedge_counter_overflow : &dispatch);
    __ bind(dispatch);
  }

  // Pre-load the next target bytecode into t0
  __ load_unsigned_byte(t0, Address(xbcp, 0));

  // continue with the bytecode @ target
  // t0: target bytecode
  // xbcp: target bcp
  __ dispatch_only(vtos, /*generate_poll*/true);

  if (UseLoopCounter && UseOnStackReplacement) {
    // invocation counter overflow
    __ bind(backedge_counter_overflow);
    __ neg(x12, x12);
    __ add(x12, x12, xbcp);     // branch xbcp
    // IcoResult frequency_counter_overflow([JavaThread*], address branch_bcp)
    __ call_VM(noreg,
               CAST_FROM_FN_PTR(address,
                                InterpreterRuntime::frequency_counter_overflow),
               x12);
    __ load_unsigned_byte(x11, Address(xbcp, 0));  // restore target bytecode

    // x10: osr nmethod (osr ok) or null (osr not possible)
    // w11: target bytecode
    // x12: temporary
    __ beqz(x10, dispatch);     // test result -- no osr if null
    // nmethod may have been invalidated (VM may block upon call_VM return)
    __ lbu(x12, Address(x10, nmethod::state_offset()));
    if (nmethod::in_use != 0) {
      __ sub(x12, x12, nmethod::in_use);
    }
    __ bnez(x12, dispatch);

    // We have the address of an on stack replacement routine in x10
    // We need to prepare to execute the OSR method. First we must
    // migrate the locals and monitors off of the stack.

    __ mv(x9, x10);                             // save the nmethod

    call_VM(noreg, CAST_FROM_FN_PTR(address, SharedRuntime::OSR_migration_begin));

    // x10 is OSR buffer, move it to expected parameter location
    __ mv(j_rarg0, x10);

    // remove activation
    // get sender esp
    __ ld(esp,
        Address(fp, frame::interpreter_frame_sender_sp_offset * wordSize));
    // remove frame anchor
    __ leave();
    // Ensure compiled code always sees stack at proper alignment
    __ andi(sp, esp, -16);

    // and begin the OSR nmethod
    __ ld(t0, Address(x9, nmethod::osr_entry_point_offset()));
    __ jr(t0);
  }
}

void TemplateTable::if_0cmp(Condition cc) {
  transition(itos, vtos);
  // assume branch is more often taken than not (loops use backward branches)
  Label not_taken;

  __ sign_extend(x10, x10, 32);
  switch (cc) {
    case equal:
      __ bnez(x10, not_taken);
      break;
    case not_equal:
      __ beqz(x10, not_taken);
      break;
    case less:
      __ bgez(x10, not_taken);
      break;
    case less_equal:
      __ bgtz(x10, not_taken);
      break;
    case greater:
      __ blez(x10, not_taken);
      break;
    case greater_equal:
      __ bltz(x10, not_taken);
      break;
    default:
      break;
  }

  branch(false, false);
  __ bind(not_taken);
  __ profile_not_taken_branch(x10);
}

void TemplateTable::if_icmp(Condition cc) {
  transition(itos, vtos);
  // assume branch is more often taken than not (loops use backward branches)
  Label not_taken;
  __ pop_i(x11);
  __ sign_extend(x10, x10, 32);
  switch (cc) {
    case equal:
      __ bne(x11, x10, not_taken);
      break;
    case not_equal:
      __ beq(x11, x10, not_taken);
      break;
    case less:
      __ bge(x11, x10, not_taken);
      break;
    case less_equal:
      __ bgt(x11, x10, not_taken);
      break;
    case greater:
      __ ble(x11, x10, not_taken);
      break;
    case greater_equal:
      __ blt(x11, x10, not_taken);
      break;
    default:
      break;
  }

  branch(false, false);
  __ bind(not_taken);
  __ profile_not_taken_branch(x10);
}

void TemplateTable::if_nullcmp(Condition cc) {
  transition(atos, vtos);
  // assume branch is more often taken than not (loops use backward branches)
  Label not_taken;
  if (cc == equal) {
    __ bnez(x10, not_taken);
  } else {
    __ beqz(x10, not_taken);
  }
  branch(false, false);
  __ bind(not_taken);
  __ profile_not_taken_branch(x10);
}

void TemplateTable::if_acmp(Condition cc) {
  transition(atos, vtos);
  // assume branch is more often taken than not (loops use backward branches)
  Label not_taken;
  __ pop_ptr(x11);

  if (cc == equal) {
    __ bne(x11, x10, not_taken);
  } else if (cc == not_equal) {
    __ beq(x11, x10, not_taken);
  }
  branch(false, false);
  __ bind(not_taken);
  __ profile_not_taken_branch(x10);
}

void TemplateTable::ret() {
  transition(vtos, vtos);
  locals_index(x11);
  __ ld(x11, aaddress(x11, t1, _masm)); // get return bci, compute return bcp
  __ profile_ret(x11, x12);
  __ ld(xbcp, Address(xmethod, Method::const_offset()));
  __ add(xbcp, xbcp, x11);
  __ addi(xbcp, xbcp, in_bytes(ConstMethod::codes_offset()));
  __ dispatch_next(vtos, 0, /*generate_poll*/true);
}

void TemplateTable::wide_ret() {
  transition(vtos, vtos);
  locals_index_wide(x11);
  __ ld(x11, aaddress(x11, t0, _masm)); // get return bci, compute return bcp
  __ profile_ret(x11, x12);
  __ ld(xbcp, Address(xmethod, Method::const_offset()));
  __ add(xbcp, xbcp, x11);
  __ add(xbcp, xbcp, in_bytes(ConstMethod::codes_offset()));
  __ dispatch_next(vtos, 0, /*generate_poll*/true);
}

void TemplateTable::tableswitch() {
  Label default_case, continue_execution;
  transition(itos, vtos);
  // align xbcp
  __ la(x11, at_bcp(BytesPerInt));
  __ andi(x11, x11, -BytesPerInt);
  // load lo & hi
  __ lwu(x12, Address(x11, BytesPerInt));
  __ lwu(x13, Address(x11, 2 * BytesPerInt));
  __ revb_w_w(x12, x12); // reverse bytes in word (32bit) and sign-extend
  __ revb_w_w(x13, x13); // reverse bytes in word (32bit) and sign-extend
  // check against lo & hi
  __ blt(x10, x12, default_case);
  __ bgt(x10, x13, default_case);
  // lookup dispatch offset
  __ subw(x10, x10, x12);
  __ shadd(x13, x10, x11, t0, 2);
  __ lwu(x13, Address(x13, 3 * BytesPerInt));
  __ profile_switch_case(x10, x11, x12);
  // continue execution
  __ bind(continue_execution);
  __ revb_w_w(x13, x13); // reverse bytes in word (32bit) and sign-extend
  __ add(xbcp, xbcp, x13);
  __ load_unsigned_byte(t0, Address(xbcp));
  __ dispatch_only(vtos, /*generate_poll*/true);
  // handle default
  __ bind(default_case);
  __ profile_switch_default(x10);
  __ lwu(x13, Address(x11, 0));
  __ j(continue_execution);
}

void TemplateTable::lookupswitch() {
  transition(itos, itos);
  __ stop("lookupswitch bytecode should have been rewritten");
}

void TemplateTable::fast_linearswitch() {
  transition(itos, vtos);
  Label loop_entry, loop, found, continue_execution;
  // bswap x10 so we can avoid bswapping the table entries
  __ revb_w_w(x10, x10); // reverse bytes in word (32bit) and sign-extend
  // align xbcp
  __ la(x9, at_bcp(BytesPerInt)); // btw: should be able to get rid of
                                    // this instruction (change offsets
                                    // below)
  __ andi(x9, x9, -BytesPerInt);
  // set counter
  __ lwu(x11, Address(x9, BytesPerInt));
  __ revb_w(x11, x11);
  __ j(loop_entry);
  // table search
  __ bind(loop);
  __ shadd(t0, x11, x9, t0, 3);
  __ lw(t0, Address(t0, 2 * BytesPerInt));
  __ beq(x10, t0, found);
  __ bind(loop_entry);
  __ addi(x11, x11, -1);
  __ bgez(x11, loop);
  // default case
  __ profile_switch_default(x10);
  __ lwu(x13, Address(x9, 0));
  __ j(continue_execution);
  // entry found -> get offset
  __ bind(found);
  __ shadd(t0, x11, x9, t0, 3);
  __ lwu(x13, Address(t0, 3 * BytesPerInt));
  __ profile_switch_case(x11, x10, x9);
  // continue execution
  __ bind(continue_execution);
  __ revb_w_w(x13, x13); // reverse bytes in word (32bit) and sign-extend
  __ add(xbcp, xbcp, x13);
  __ lbu(t0, Address(xbcp, 0));
  __ dispatch_only(vtos, /*generate_poll*/true);
}

void TemplateTable::fast_binaryswitch() {
  transition(itos, vtos);
  // Implementation using the following core algorithm:
  //
  // int binary_search(int key, LookupswitchPair* array, int n)
  //   binary_search start:
  //   #Binary search according to "Methodik des Programmierens" by
  //   # Edsger W. Dijkstra and W.H.J. Feijen, Addison Wesley Germany 1985.
  //   int i = 0;
  //   int j = n;
  //   while (i + 1 < j) do
  //     # invariant P: 0 <= i < j <= n and (a[i] <= key < a[j] or Q)
  //     # with      Q: for all i: 0 <= i < n: key < a[i]
  //     # where a stands for the array and assuming that the (inexisting)
  //     # element a[n] is infinitely big.
  //     int h = (i + j) >> 1
  //     # i < h < j
  //     if (key < array[h].fast_match())
  //     then [j = h]
  //     else [i = h]
  //   end
  //   # R: a[i] <= key < a[i+1] or Q
  //   # (i.e., if key is within array, i is the correct index)
  //   return i
  // binary_search end


  // Register allocation
  const Register key   = x10; // already set (tosca)
  const Register array = x11;
  const Register i     = x12;
  const Register j     = x13;
  const Register h     = x14;
  const Register temp  = x15;

  // Find array start
  __ la(array, at_bcp(3 * BytesPerInt));  // btw: should be able to
                                          // get rid of this
                                          // instruction (change
                                          // offsets below)
  __ andi(array, array, -BytesPerInt);

  // Initialize i & j
  __ mv(i, zr);                            // i = 0
  __ lwu(j, Address(array, -BytesPerInt)); // j = length(array)

  // Convert j into native byteordering
  __ revb_w(j, j);

  // And start
  Label entry;
  __ j(entry);

  // binary search loop
  {
    Label loop;
    __ bind(loop);
    __ addw(h, i, j);                           // h = i + j
    __ srliw(h, h, 1);                          // h = (i + j) >> 1
    // if [key < array[h].fast_match()]
    // then [j = h]
    // else [i = h]
    // Convert array[h].match to native byte-ordering before compare
    __ shadd(temp, h, array, temp, 3);
    __ lwu(temp, Address(temp, 0));
    __ revb_w_w(temp, temp); // reverse bytes in word (32bit) and sign-extend

    Label L_done, L_greater;
    __ bge(key, temp, L_greater);
    // if [key < array[h].fast_match()] then j = h
    __ mv(j, h);
    __ j(L_done);
    __ bind(L_greater);
    // if [key >= array[h].fast_match()] then i = h
    __ mv(i, h);
    __ bind(L_done);

    // while [i + 1 < j]
    __ bind(entry);
    __ addiw(h, i, 1);         // i + 1
    __ blt(h, j, loop);        // i + 1 < j
  }

  // end of binary search, result index is i (must check again!)
  Label default_case;
  // Convert array[i].match to native byte-ordering before compare
  __ shadd(temp, i, array, temp, 3);
  __ lwu(temp, Address(temp, 0));
  __ revb_w_w(temp, temp); // reverse bytes in word (32bit) and sign-extend
  __ bne(key, temp, default_case);

  // entry found -> j = offset
  __ shadd(temp, i, array, temp, 3);
  __ lwu(j, Address(temp, BytesPerInt));
  __ profile_switch_case(i, key, array);
  __ revb_w_w(j, j); // reverse bytes in word (32bit) and sign-extend

  __ add(temp, xbcp, j);
  __ load_unsigned_byte(t0, Address(temp, 0));

  __ add(xbcp, xbcp, j);
  __ la(xbcp, Address(xbcp, 0));
  __ dispatch_only(vtos, /*generate_poll*/true);

  // default case -> j = default offset
  __ bind(default_case);
  __ profile_switch_default(i);
  __ lwu(j, Address(array, -2 * BytesPerInt));
  __ revb_w_w(j, j); // reverse bytes in word (32bit) and sign-extend

  __ add(temp, xbcp, j);
  __ load_unsigned_byte(t0, Address(temp, 0));

  __ add(xbcp, xbcp, j);
  __ la(xbcp, Address(xbcp, 0));
  __ dispatch_only(vtos, /*generate_poll*/true);
}

void TemplateTable::_return(TosState state) {
  transition(state, state);
  assert(_desc->calls_vm(),
         "inconsistent calls_vm information"); // call in remove_activation

  if (_desc->bytecode() == Bytecodes::_return_register_finalizer) {
    assert(state == vtos, "only valid state");

    __ ld(c_rarg1, aaddress(0));
    __ load_klass(x13, c_rarg1);
    __ lwu(x13, Address(x13, Klass::access_flags_offset()));
    Label skip_register_finalizer;
    __ test_bit(t0, x13, exact_log2(JVM_ACC_HAS_FINALIZER));
    __ beqz(t0, skip_register_finalizer);

    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::register_finalizer), c_rarg1);

    __ bind(skip_register_finalizer);
  }

  // Issue a StoreStore barrier after all stores but before return
  // from any constructor for any class with a final field. We don't
  // know if this is a finalizer, so we always do so.
  if (_desc->bytecode() == Bytecodes::_return) {
    __ membar(MacroAssembler::StoreStore);
  }

  if (_desc->bytecode() != Bytecodes::_return_register_finalizer) {
    Label no_safepoint;
    __ ld(t0, Address(xthread, JavaThread::polling_word_offset()));
    __ test_bit(t0, t0, exact_log2(SafepointMechanism::poll_bit()));
    __ beqz(t0, no_safepoint);
    __ push(state);
    __ push_cont_fastpath(xthread);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::at_safepoint));
    __ pop_cont_fastpath(xthread);
    __ pop(state);
    __ bind(no_safepoint);
  }

  // Narrow result if state is itos but result type is smaller.
  // Need to narrow in the return bytecode rather than in generate_return_entry
  // since compiled code callers expect the result to already be narrowed.
  if (state == itos) {
    __ narrow(x10);
  }

  __ remove_activation(state);
  __ ret();
}


// ----------------------------------------------------------------------------
// Volatile variables demand their effects be made known to all CPU's
// in order.  Store buffers on most chips allow reads & writes to
// reorder; the JMM's ReadAfterWrite.java test fails in -Xint mode
// without some kind of memory barrier (i.e., it's not sufficient that
// the interpreter does not reorder volatile references, the hardware
// also must not reorder them).
//
// According to the new Java Memory Model (JMM):
// (1) All volatiles are serialized wrt to each other.  ALSO reads &
//     writes act as acquire & release, so:
// (2) A read cannot let unrelated NON-volatile memory refs that
//     happen after the read float up to before the read.  It's OK for
//     non-volatile memory refs that happen before the volatile read to
//     float down below it.
// (3) Similar a volatile write cannot let unrelated NON-volatile
//     memory refs that happen BEFORE the write float down to after the
//     write.  It's OK for non-volatile memory refs that happen after the
//     volatile write to float up before it.
//
// We only put in barriers around volatile refs (they are expensive),
// not _between_ memory refs (that would require us to track the
// flavor of the previous memory refs).  Requirements (2) and (3)
// require some barriers before volatile stores and after volatile
// loads.  These nearly cover requirement (1) but miss the
// volatile-store-volatile-load case.  This final case is placed after
// volatile-stores although it could just as well go before
// volatile-loads.

void TemplateTable::resolve_cache_and_index_for_method(int byte_no,
                                                       Register Rcache,
                                                       Register index) {
  const Register temp = x9;
  assert_different_registers(Rcache, index, temp);
  assert(byte_no == f1_byte || byte_no == f2_byte, "byte_no out of range");

  Label resolved, clinit_barrier_slow;

  Bytecodes::Code code = bytecode();
  __ load_method_entry(Rcache, index);
  switch(byte_no) {
    case f1_byte:
      __ add(temp, Rcache, in_bytes(ResolvedMethodEntry::bytecode1_offset()));
      break;
    case f2_byte:
      __ add(temp, Rcache, in_bytes(ResolvedMethodEntry::bytecode2_offset()));
      break;
  }
  // Load-acquire the bytecode to match store-release in InterpreterRuntime
  __ membar(MacroAssembler::AnyAny);
  __ lbu(temp, Address(temp, 0));
  __ membar(MacroAssembler::LoadLoad | MacroAssembler::LoadStore);

  __ mv(t0, (int) code);
  __ beq(temp, t0, resolved);  // have we resolved this bytecode?

  // resolve first time through
  // Class initialization barrier slow path lands here as well.
  __ bind(clinit_barrier_slow);

  address entry = CAST_FROM_FN_PTR(address, InterpreterRuntime::resolve_from_cache);
  __ mv(temp, (int) code);
  __ call_VM(noreg, entry, temp);

  // Update registers with resolved info
  __ load_method_entry(Rcache, index);
  // n.b. unlike x86 Rcache is now rcpool plus the indexed offset
  // so all clients ofthis method must be modified accordingly
  __ bind(resolved);

  // Class initialization barrier for static methods
  if (VM_Version::supports_fast_class_init_checks() && bytecode() == Bytecodes::_invokestatic) {
    __ ld(temp, Address(Rcache, in_bytes(ResolvedMethodEntry::method_offset())));
    __ load_method_holder(temp, temp);
    __ clinit_barrier(temp, t0, nullptr, &clinit_barrier_slow);
  }
}

void TemplateTable::resolve_cache_and_index_for_field(int byte_no,
                                            Register Rcache,
                                            Register index) {
  const Register temp = x9;
  assert_different_registers(Rcache, index, temp);

  Label resolved;

  Bytecodes::Code code = bytecode();
  switch (code) {
  case Bytecodes::_nofast_getfield: code = Bytecodes::_getfield; break;
  case Bytecodes::_nofast_putfield: code = Bytecodes::_putfield; break;
  default: break;
  }

  assert(byte_no == f1_byte || byte_no == f2_byte, "byte_no out of range");
  __ load_field_entry(Rcache, index);
  if (byte_no == f1_byte) {
    __ la(temp, Address(Rcache, in_bytes(ResolvedFieldEntry::get_code_offset())));
  } else {
    __ la(temp, Address(Rcache, in_bytes(ResolvedFieldEntry::put_code_offset())));
  }
  // Load-acquire the bytecode to match store-release in ResolvedFieldEntry::fill_in()
  __ membar(MacroAssembler::AnyAny);
  __ lbu(temp, Address(temp, 0));
  __ membar(MacroAssembler::LoadLoad | MacroAssembler::LoadStore);
  __ mv(t0, (int) code);  // have we resolved this bytecode?
  __ beq(temp, t0, resolved);

  // resolve first time through
  address entry = CAST_FROM_FN_PTR(address, InterpreterRuntime::resolve_from_cache);
  __ mv(temp, (int) code);
  __ call_VM(noreg, entry, temp);

  // Update registers with resolved info
  __ load_field_entry(Rcache, index);
  __ bind(resolved);
}

void TemplateTable::load_resolved_field_entry(Register obj,
                                              Register cache,
                                              Register tos_state,
                                              Register offset,
                                              Register flags,
                                              bool is_static = false) {
  assert_different_registers(cache, tos_state, flags, offset);

  // Field offset
  __ load_sized_value(offset, Address(cache, in_bytes(ResolvedFieldEntry::field_offset_offset())), sizeof(int), true /*is_signed*/);

  // Flags
  __ load_unsigned_byte(flags, Address(cache, in_bytes(ResolvedFieldEntry::flags_offset())));

  // TOS state
  __ load_unsigned_byte(tos_state, Address(cache, in_bytes(ResolvedFieldEntry::type_offset())));

  // Klass overwrite register
  if (is_static) {
    __ ld(obj, Address(cache, ResolvedFieldEntry::field_holder_offset()));
    const int mirror_offset = in_bytes(Klass::java_mirror_offset());
    __ ld(obj, Address(obj, mirror_offset));
    __ resolve_oop_handle(obj, x15, t1);
  }
}

void TemplateTable::load_resolved_method_entry_special_or_static(Register cache,
                                                                 Register method,
                                                                 Register flags) {

  // setup registers
  const Register index = flags;
  assert_different_registers(method, cache, flags);

  // determine constant pool cache field offsets
  resolve_cache_and_index_for_method(f1_byte, cache, index);
  __ load_unsigned_byte(flags, Address(cache, in_bytes(ResolvedMethodEntry::flags_offset())));
  __ ld(method, Address(cache, in_bytes(ResolvedMethodEntry::method_offset())));
}

void TemplateTable::load_resolved_method_entry_handle(Register cache,
                                                      Register method,
                                                      Register ref_index,
                                                      Register flags) {
  // setup registers
  const Register index = ref_index;
  assert_different_registers(method, flags);
  assert_different_registers(method, cache, index);

  // determine constant pool cache field offsets
  resolve_cache_and_index_for_method(f1_byte, cache, index);
  __ load_unsigned_byte(flags, Address(cache, in_bytes(ResolvedMethodEntry::flags_offset())));

  // maybe push appendix to arguments (just before return address)
  Label L_no_push;
  __ test_bit(t0, flags, ResolvedMethodEntry::has_appendix_shift);
  __ beqz(t0, L_no_push);
  // invokehandle uses an index into the resolved references array
  __ load_unsigned_short(ref_index, Address(cache, in_bytes(ResolvedMethodEntry::resolved_references_index_offset())));
  // Push the appendix as a trailing parameter.
  // This must be done before we get the receiver,
  // since the parameter_size includes it.
  Register appendix = method;
  __ load_resolved_reference_at_index(appendix, ref_index);
  __ push_reg(appendix); // push appendix (MethodType, CallSite, etc.)
  __ bind(L_no_push);

  __ ld(method, Address(cache, in_bytes(ResolvedMethodEntry::method_offset())));
}

void TemplateTable::load_resolved_method_entry_interface(Register cache,
                                                         Register klass,
                                                         Register method_or_table_index,
                                                         Register flags) {
  // setup registers
  const Register index = method_or_table_index;
  assert_different_registers(method_or_table_index, cache, flags);

  // determine constant pool cache field offsets
  resolve_cache_and_index_for_method(f1_byte, cache, index);
  __ load_unsigned_byte(flags, Address(cache, in_bytes(ResolvedMethodEntry::flags_offset())));

  // Invokeinterface can behave in different ways:
  // If calling a method from java.lang.Object, the forced virtual flag is true so the invocation will
  // behave like an invokevirtual call. The state of the virtual final flag will determine whether a method or
  // vtable index is placed in the register.
  // Otherwise, the registers will be populated with the klass and method.

  Label NotVirtual; Label NotVFinal; Label Done;
  __ test_bit(t0, flags, ResolvedMethodEntry::is_forced_virtual_shift);
  __ beqz(t0, NotVirtual);
  __ test_bit(t0, flags, ResolvedMethodEntry::is_vfinal_shift);
  __ beqz(t0, NotVFinal);
  __ ld(method_or_table_index, Address(cache, in_bytes(ResolvedMethodEntry::method_offset())));
  __ j(Done);

  __ bind(NotVFinal);
  __ load_unsigned_short(method_or_table_index, Address(cache, in_bytes(ResolvedMethodEntry::table_index_offset())));
  __ j(Done);

  __ bind(NotVirtual);
  __ ld(method_or_table_index, Address(cache, in_bytes(ResolvedMethodEntry::method_offset())));
  __ ld(klass, Address(cache, in_bytes(ResolvedMethodEntry::klass_offset())));
  __ bind(Done);
}

void TemplateTable::load_resolved_method_entry_virtual(Register cache,
                                                       Register method_or_table_index,
                                                       Register flags) {
  // setup registers
  const Register index = flags;
  assert_different_registers(method_or_table_index, cache, flags);

  // determine constant pool cache field offsets
  resolve_cache_and_index_for_method(f2_byte, cache, index);
  __ load_unsigned_byte(flags, Address(cache, in_bytes(ResolvedMethodEntry::flags_offset())));

  // method_or_table_index can either be an itable index or a method depending on the virtual final flag
  Label NotVFinal; Label Done;
  __ test_bit(t0, flags, ResolvedMethodEntry::is_vfinal_shift);
  __ beqz(t0, NotVFinal);
  __ ld(method_or_table_index, Address(cache, in_bytes(ResolvedMethodEntry::method_offset())));
  __ j(Done);

  __ bind(NotVFinal);
  __ load_unsigned_short(method_or_table_index, Address(cache, in_bytes(ResolvedMethodEntry::table_index_offset())));
  __ bind(Done);
}

// The xmethod register is input and overwritten to be the adapter method for the
// indy call. Return address (ra) is set to the return address for the adapter and
// an appendix may be pushed to the stack. Registers x10-x13 are clobbered.
void TemplateTable::load_invokedynamic_entry(Register method) {
  // setup registers
  const Register appendix = x10;
  const Register cache = x12;
  const Register index = x13;
  assert_different_registers(method, appendix, cache, index, xcpool);

  __ save_bcp();

  Label resolved;

  __ load_resolved_indy_entry(cache, index);
  __ membar(MacroAssembler::AnyAny);
  __ ld(method, Address(cache, in_bytes(ResolvedIndyEntry::method_offset())));
  __ membar(MacroAssembler::LoadLoad | MacroAssembler::LoadStore);

  // Compare the method to zero
  __ bnez(method, resolved);

  Bytecodes::Code code = bytecode();

  // Call to the interpreter runtime to resolve invokedynamic
  address entry = CAST_FROM_FN_PTR(address, InterpreterRuntime::resolve_from_cache);
  __ mv(method, code); // this is essentially Bytecodes::_invokedynamic
  __ call_VM(noreg, entry, method);
  // Update registers with resolved info
  __ load_resolved_indy_entry(cache, index);
  __ membar(MacroAssembler::AnyAny);
  __ ld(method, Address(cache, in_bytes(ResolvedIndyEntry::method_offset())));
  __ membar(MacroAssembler::LoadLoad | MacroAssembler::LoadStore);

#ifdef ASSERT
  __ bnez(method, resolved);
  __ stop("Should be resolved by now");
#endif // ASSERT
  __ bind(resolved);

  Label L_no_push;
  // Check if there is an appendix
  __ load_unsigned_byte(index, Address(cache, in_bytes(ResolvedIndyEntry::flags_offset())));
  __ test_bit(t0, index, ResolvedIndyEntry::has_appendix_shift);
  __ beqz(t0, L_no_push);

  // Get appendix
  __ load_unsigned_short(index, Address(cache, in_bytes(ResolvedIndyEntry::resolved_references_index_offset())));
  // Push the appendix as a trailing parameter
  // since the parameter_size includes it.
  __ push_reg(method);
  __ mv(method, index);
  __ load_resolved_reference_at_index(appendix, method);
  __ verify_oop(appendix);
  __ pop_reg(method);
  __ push_reg(appendix);  // push appendix (MethodType, CallSite, etc.)
  __ bind(L_no_push);

  // compute return type
  __ load_unsigned_byte(index, Address(cache, in_bytes(ResolvedIndyEntry::result_type_offset())));
  // load return address
  // Return address is loaded into ra and not pushed to the stack like x86
  {
    const address table_addr = (address) Interpreter::invoke_return_entry_table_for(code);
    __ mv(t0, table_addr);
    __ shadd(t0, index, t0, index, 3);
    __ ld(ra, Address(t0, 0));
  }
}

// The registers cache and index expected to be set before call.
// Correct values of the cache and index registers are preserved.
void TemplateTable::jvmti_post_field_access(Register cache, Register index,
                                            bool is_static, bool has_tos) {
  // do the JVMTI work here to avoid disturbing the register state below
  // We use c_rarg registers here because we want to use the register used in
  // the call to the VM
  if (JvmtiExport::can_post_field_access()) {
    // Check to see if a field access watch has been set before we
    // take the time to call into the VM.
    Label L1;
    assert_different_registers(cache, index, x10);
    ExternalAddress target((address) JvmtiExport::get_field_access_count_addr());
    __ relocate(target.rspec(), [&] {
      int32_t offset;
      __ la(t0, target.target(), offset);
      __ lwu(x10, Address(t0, offset));
    });

    __ beqz(x10, L1);

    __ load_field_entry(c_rarg2, index);

    if (is_static) {
      __ mv(c_rarg1, zr); // null object reference
    } else {
      __ ld(c_rarg1, at_tos()); // get object pointer without popping it
      __ verify_oop(c_rarg1);
    }
    // c_rarg1: object pointer or null
    // c_rarg2: cache entry pointer
    __ call_VM(noreg, CAST_FROM_FN_PTR(address,
                                       InterpreterRuntime::post_field_access),
                                       c_rarg1, c_rarg2);
    __ load_field_entry(cache, index);
    __ bind(L1);
  }
}

void TemplateTable::pop_and_check_object(Register r) {
  __ pop_ptr(r);
  __ null_check(r);  // for field access must check obj.
  __ verify_oop(r);
}

void TemplateTable::getfield_or_static(int byte_no, bool is_static, RewriteControl rc) {
  const Register cache     = x14;
  const Register obj       = x14;
  const Register index     = x13;
  const Register tos_state = x13;
  const Register off       = x9;
  const Register flags     = x16;
  const Register bc        = x14; // uses same reg as obj, so don't mix them

  resolve_cache_and_index_for_field(byte_no, cache, index);
  jvmti_post_field_access(cache, index, is_static, false);
  load_resolved_field_entry(obj, cache, tos_state, off, flags, is_static);

  if (!is_static) {
    // obj is on the stack
    pop_and_check_object(obj);
  }

  __ add(off, obj, off);
  const Address field(off);

  Label Done, notByte, notBool, notInt, notShort, notChar,
              notLong, notFloat, notObj, notDouble;

  assert(btos == 0, "change code, btos != 0");
  __ bnez(tos_state, notByte);

  // Don't rewrite getstatic, only getfield
  if (is_static) {
    rc = may_not_rewrite;
  }

  // btos
  __ access_load_at(T_BYTE, IN_HEAP, x10, field, noreg, noreg);
  __ push(btos);
  // Rewrite bytecode to be faster
  if (rc == may_rewrite) {
    patch_bytecode(Bytecodes::_fast_bgetfield, bc, x11);
  }
  __ j(Done);

  __ bind(notByte);
  __ sub(t0, tos_state, (u1)ztos);
  __ bnez(t0, notBool);

  // ztos (same code as btos)
  __ access_load_at(T_BOOLEAN, IN_HEAP, x10, field, noreg, noreg);
  __ push(ztos);
  // Rewrite bytecode to be faster
  if (rc == may_rewrite) {
    // uses btos rewriting, no truncating to t/f bit is needed for getfield
    patch_bytecode(Bytecodes::_fast_bgetfield, bc, x11);
  }
  __ j(Done);

  __ bind(notBool);
  __ sub(t0, tos_state, (u1)atos);
  __ bnez(t0, notObj);
  // atos
  do_oop_load(_masm, field, x10, IN_HEAP);
  __ push(atos);
  if (rc == may_rewrite) {
    patch_bytecode(Bytecodes::_fast_agetfield, bc, x11);
  }
  __ j(Done);

  __ bind(notObj);
  __ sub(t0, tos_state, (u1)itos);
  __ bnez(t0, notInt);
  // itos
  __ access_load_at(T_INT, IN_HEAP, x10, field, noreg, noreg);
  __ sign_extend(x10, x10, 32);
  __ push(itos);
  // Rewrite bytecode to be faster
  if (rc == may_rewrite) {
    patch_bytecode(Bytecodes::_fast_igetfield, bc, x11);
  }
  __ j(Done);

  __ bind(notInt);
  __ sub(t0, tos_state, (u1)ctos);
  __ bnez(t0, notChar);
  // ctos
  __ access_load_at(T_CHAR, IN_HEAP, x10, field, noreg, noreg);
  __ push(ctos);
  // Rewrite bytecode to be faster
  if (rc == may_rewrite) {
    patch_bytecode(Bytecodes::_fast_cgetfield, bc, x11);
  }
  __ j(Done);

  __ bind(notChar);
  __ sub(t0, tos_state, (u1)stos);
  __ bnez(t0, notShort);
  // stos
  __ access_load_at(T_SHORT, IN_HEAP, x10, field, noreg, noreg);
  __ push(stos);
  // Rewrite bytecode to be faster
  if (rc == may_rewrite) {
    patch_bytecode(Bytecodes::_fast_sgetfield, bc, x11);
  }
  __ j(Done);

  __ bind(notShort);
  __ sub(t0, tos_state, (u1)ltos);
  __ bnez(t0, notLong);
  // ltos
  __ access_load_at(T_LONG, IN_HEAP, x10, field, noreg, noreg);
  __ push(ltos);
  // Rewrite bytecode to be faster
  if (rc == may_rewrite) {
    patch_bytecode(Bytecodes::_fast_lgetfield, bc, x11);
  }
  __ j(Done);

  __ bind(notLong);
  __ sub(t0, tos_state, (u1)ftos);
  __ bnez(t0, notFloat);
  // ftos
  __ access_load_at(T_FLOAT, IN_HEAP, noreg /* ftos */, field, noreg, noreg);
  __ push(ftos);
  // Rewrite bytecode to be faster
  if (rc == may_rewrite) {
    patch_bytecode(Bytecodes::_fast_fgetfield, bc, x11);
  }
  __ j(Done);

  __ bind(notFloat);
#ifdef ASSERT
  __ sub(t0, tos_state, (u1)dtos);
  __ bnez(t0, notDouble);
#endif
  // dtos
  __ access_load_at(T_DOUBLE, IN_HEAP, noreg /* ftos */, field, noreg, noreg);
  __ push(dtos);
  // Rewrite bytecode to be faster
  if (rc == may_rewrite) {
    patch_bytecode(Bytecodes::_fast_dgetfield, bc, x11);
  }
#ifdef ASSERT
  __ j(Done);

  __ bind(notDouble);
  __ stop("Bad state");
#endif

  __ bind(Done);

  Label notVolatile;
  __ test_bit(t0, flags, ResolvedFieldEntry::is_volatile_shift);
  __ beqz(t0, notVolatile);
  __ membar(MacroAssembler::LoadLoad | MacroAssembler::LoadStore);
  __ bind(notVolatile);
}

void TemplateTable::getfield(int byte_no) {
  getfield_or_static(byte_no, false);
}

void TemplateTable::nofast_getfield(int byte_no) {
  getfield_or_static(byte_no, false, may_not_rewrite);
}

void TemplateTable::getstatic(int byte_no)
{
  getfield_or_static(byte_no, true);
}

// The registers cache and index expected to be set before call.
// The function may destroy various registers, just not the cache and index registers.
void TemplateTable::jvmti_post_field_mod(Register cache, Register index, bool is_static) {
  transition(vtos, vtos);

  if (JvmtiExport::can_post_field_modification()) {
    // Check to see if a field modification watch has been set before
    // we take the time to call into the VM.
    Label L1;
    assert_different_registers(cache, index, x10);
    ExternalAddress target((address)JvmtiExport::get_field_modification_count_addr());
    __ relocate(target.rspec(), [&] {
      int32_t offset;
      __ la(t0, target.target(), offset);
      __ lwu(x10, Address(t0, offset));
    });
    __ beqz(x10, L1);

    __ mv(c_rarg2, cache);

    if (is_static) {
      // Life is simple. Null out the object pointer.
      __ mv(c_rarg1, zr);
    } else {
      // Life is harder. The stack holds the value on top, followed by
      // the object. We don't know the size of the value, though; it
      // could be one or two words depending on its type. As a result,
      // we must find the type to determine where the object is.
      __ load_unsigned_byte(c_rarg3, Address(c_rarg2, in_bytes(ResolvedFieldEntry::type_offset())));
      Label nope2, done, ok;
      __ ld(c_rarg1, at_tos_p1());   // initially assume a one word jvalue
      __ sub(t0, c_rarg3, ltos);
      __ beqz(t0, ok);
      __ sub(t0, c_rarg3, dtos);
      __ bnez(t0, nope2);
      __ bind(ok);
      __ ld(c_rarg1, at_tos_p2());  // ltos (two word jvalue);
      __ bind(nope2);
    }
    // object (tos)
    __ mv(c_rarg3, esp);
    // c_rarg1: object pointer set up above (null if static)
    // c_rarg2: cache entry pointer
    // c_rarg3: jvalue object on  the stack
    __ call_VM(noreg,
               CAST_FROM_FN_PTR(address,
                                InterpreterRuntime::post_field_modification),
                                c_rarg1, c_rarg2, c_rarg3);
    __ load_field_entry(cache, index);
    __ bind(L1);
  }
}

void TemplateTable::putfield_or_static(int byte_no, bool is_static, RewriteControl rc) {
  transition(vtos, vtos);

  const Register cache     = x12;
  const Register index     = x13;
  const Register tos_state = x13;
  const Register obj       = x12;
  const Register off       = x9;
  const Register flags     = x10;
  const Register bc        = x14;

  resolve_cache_and_index_for_field(byte_no, cache, index);
  jvmti_post_field_mod(cache, index, is_static);
  load_resolved_field_entry(obj, cache, tos_state, off, flags, is_static);

  Label Done;
  __ mv(x15, flags);

  {
    Label notVolatile;
    __ test_bit(t0, x15, ResolvedFieldEntry::is_volatile_shift);
    __ beqz(t0, notVolatile);
    __ membar(MacroAssembler::StoreStore | MacroAssembler::LoadStore);
    __ bind(notVolatile);
  }

  Label notByte, notBool, notInt, notShort, notChar,
        notLong, notFloat, notObj, notDouble;

  assert(btos == 0, "change code, btos != 0");
  __ bnez(tos_state, notByte);

  // Don't rewrite putstatic, only putfield
  if (is_static) {
    rc = may_not_rewrite;
  }

  // btos
  {
    __ pop(btos);
    // field address
    if (!is_static) {
      pop_and_check_object(obj);
    }
    __ add(off, obj, off); // if static, obj from cache, else obj from stack.
    const Address field(off, 0); // off register as temparator register.
    __ access_store_at(T_BYTE, IN_HEAP, field, x10, noreg, noreg, noreg);
    if (rc == may_rewrite) {
      patch_bytecode(Bytecodes::_fast_bputfield, bc, x11, true, byte_no);
    }
    __ j(Done);
  }

  __ bind(notByte);
  __ sub(t0, tos_state, (u1)ztos);
  __ bnez(t0, notBool);

  // ztos
  {
    __ pop(ztos);
    // field address
    if (!is_static) {
      pop_and_check_object(obj);
    }
    __ add(off, obj, off); // if static, obj from cache, else obj from stack.
    const Address field(off, 0);
    __ access_store_at(T_BOOLEAN, IN_HEAP, field, x10, noreg, noreg, noreg);
    if (rc == may_rewrite) {
      patch_bytecode(Bytecodes::_fast_zputfield, bc, x11, true, byte_no);
    }
    __ j(Done);
  }

  __ bind(notBool);
  __ sub(t0, tos_state, (u1)atos);
  __ bnez(t0, notObj);

  // atos
  {
    __ pop(atos);
    // field address
    if (!is_static) {
      pop_and_check_object(obj);
    }
    __ add(off, obj, off); // if static, obj from cache, else obj from stack.
    const Address field(off, 0);
    // Store into the field
    do_oop_store(_masm, field, x10, IN_HEAP);
    if (rc == may_rewrite) {
      patch_bytecode(Bytecodes::_fast_aputfield, bc, x11, true, byte_no);
    }
    __ j(Done);
  }

  __ bind(notObj);
  __ sub(t0, tos_state, (u1)itos);
  __ bnez(t0, notInt);

  // itos
  {
    __ pop(itos);
    // field address
    if (!is_static) {
      pop_and_check_object(obj);
    }
    __ add(off, obj, off); // if static, obj from cache, else obj from stack.
    const Address field(off, 0);
    __ access_store_at(T_INT, IN_HEAP, field, x10, noreg, noreg, noreg);
    if (rc == may_rewrite) {
      patch_bytecode(Bytecodes::_fast_iputfield, bc, x11, true, byte_no);
    }
    __ j(Done);
  }

  __ bind(notInt);
  __ sub(t0, tos_state, (u1)ctos);
  __ bnez(t0, notChar);

  // ctos
  {
    __ pop(ctos);
    // field address
    if (!is_static) {
      pop_and_check_object(obj);
    }
    __ add(off, obj, off); // if static, obj from cache, else obj from stack.
    const Address field(off, 0);
    __ access_store_at(T_CHAR, IN_HEAP, field, x10, noreg, noreg, noreg);
    if (rc == may_rewrite) {
      patch_bytecode(Bytecodes::_fast_cputfield, bc, x11, true, byte_no);
    }
    __ j(Done);
  }

  __ bind(notChar);
  __ sub(t0, tos_state, (u1)stos);
  __ bnez(t0, notShort);

  // stos
  {
    __ pop(stos);
    // field address
    if (!is_static) {
      pop_and_check_object(obj);
    }
    __ add(off, obj, off); // if static, obj from cache, else obj from stack.
    const Address field(off, 0);
    __ access_store_at(T_SHORT, IN_HEAP, field, x10, noreg, noreg, noreg);
    if (rc == may_rewrite) {
      patch_bytecode(Bytecodes::_fast_sputfield, bc, x11, true, byte_no);
    }
    __ j(Done);
  }

  __ bind(notShort);
  __ sub(t0, tos_state, (u1)ltos);
  __ bnez(t0, notLong);

  // ltos
  {
    __ pop(ltos);
    // field address
    if (!is_static) {
      pop_and_check_object(obj);
    }
    __ add(off, obj, off); // if static, obj from cache, else obj from stack.
    const Address field(off, 0);
    __ access_store_at(T_LONG, IN_HEAP, field, x10, noreg, noreg, noreg);
    if (rc == may_rewrite) {
      patch_bytecode(Bytecodes::_fast_lputfield, bc, x11, true, byte_no);
    }
    __ j(Done);
  }

  __ bind(notLong);
  __ sub(t0, tos_state, (u1)ftos);
  __ bnez(t0, notFloat);

  // ftos
  {
    __ pop(ftos);
    // field address
    if (!is_static) {
      pop_and_check_object(obj);
    }
    __ add(off, obj, off); // if static, obj from cache, else obj from stack.
    const Address field(off, 0);
    __ access_store_at(T_FLOAT, IN_HEAP, field, noreg /* ftos */, noreg, noreg, noreg);
    if (rc == may_rewrite) {
      patch_bytecode(Bytecodes::_fast_fputfield, bc, x11, true, byte_no);
    }
    __ j(Done);
  }

  __ bind(notFloat);
#ifdef ASSERT
  __ sub(t0, tos_state, (u1)dtos);
  __ bnez(t0, notDouble);
#endif

  // dtos
  {
    __ pop(dtos);
    // field address
    if (!is_static) {
      pop_and_check_object(obj);
    }
    __ add(off, obj, off); // if static, obj from cache, else obj from stack.
    const Address field(off, 0);
    __ access_store_at(T_DOUBLE, IN_HEAP, field, noreg /* dtos */, noreg, noreg, noreg);
    if (rc == may_rewrite) {
      patch_bytecode(Bytecodes::_fast_dputfield, bc, x11, true, byte_no);
    }
  }

#ifdef ASSERT
  __ j(Done);

  __ bind(notDouble);
  __ stop("Bad state");
#endif

  __ bind(Done);

  {
    Label notVolatile;
    __ test_bit(t0, x15, ResolvedFieldEntry::is_volatile_shift);
    __ beqz(t0, notVolatile);
    __ membar(MacroAssembler::StoreLoad | MacroAssembler::StoreStore);
    __ bind(notVolatile);
  }
}

void TemplateTable::putfield(int byte_no) {
  putfield_or_static(byte_no, false);
}

void TemplateTable::nofast_putfield(int byte_no) {
  putfield_or_static(byte_no, false, may_not_rewrite);
}

void TemplateTable::putstatic(int byte_no) {
  putfield_or_static(byte_no, true);
}

void TemplateTable::jvmti_post_fast_field_mod() {
  if (JvmtiExport::can_post_field_modification()) {
    // Check to see if a field modification watch has been set before
    // we take the time to call into the VM.
    Label L2;
    ExternalAddress target((address)JvmtiExport::get_field_modification_count_addr());
    __ relocate(target.rspec(), [&] {
      int32_t offset;
      __ la(t0, target.target(), offset);
      __ lwu(c_rarg3, Address(t0, offset));
    });
    __ beqz(c_rarg3, L2);
    __ pop_ptr(x9);                  // copy the object pointer from tos
    __ verify_oop(x9);
    __ push_ptr(x9);                 // put the object pointer back on tos
    // Save tos values before call_VM() clobbers them. Since we have
    // to do it for every data type, we use the saved values as the
    // jvalue object.
    switch (bytecode()) {          // load values into the jvalue object
      case Bytecodes::_fast_aputfield: __ push_ptr(x10); break;
      case Bytecodes::_fast_bputfield: // fall through
      case Bytecodes::_fast_zputfield: // fall through
      case Bytecodes::_fast_sputfield: // fall through
      case Bytecodes::_fast_cputfield: // fall through
      case Bytecodes::_fast_iputfield: __ push_i(x10); break;
      case Bytecodes::_fast_dputfield: __ push_d(); break;
      case Bytecodes::_fast_fputfield: __ push_f(); break;
      case Bytecodes::_fast_lputfield: __ push_l(x10); break;

      default:
        ShouldNotReachHere();
    }
    __ mv(c_rarg3, esp);             // points to jvalue on the stack
    // access constant pool cache entry
    __ load_field_entry(c_rarg2, x10);
    __ verify_oop(x9);
    // x9: object pointer copied above
    // c_rarg2: cache entry pointer
    // c_rarg3: jvalue object on the stack
    __ call_VM(noreg,
               CAST_FROM_FN_PTR(address,
                                InterpreterRuntime::post_field_modification),
               x9, c_rarg2, c_rarg3);

    switch (bytecode()) {             // restore tos values
      case Bytecodes::_fast_aputfield: __ pop_ptr(x10); break;
      case Bytecodes::_fast_bputfield: // fall through
      case Bytecodes::_fast_zputfield: // fall through
      case Bytecodes::_fast_sputfield: // fall through
      case Bytecodes::_fast_cputfield: // fall through
      case Bytecodes::_fast_iputfield: __ pop_i(x10); break;
      case Bytecodes::_fast_dputfield: __ pop_d(); break;
      case Bytecodes::_fast_fputfield: __ pop_f(); break;
      case Bytecodes::_fast_lputfield: __ pop_l(x10); break;
      default: break;
    }
    __ bind(L2);
  }
}

void TemplateTable::fast_storefield(TosState state) {
  transition(state, vtos);

  ByteSize base = ConstantPoolCache::base_offset();

  jvmti_post_fast_field_mod();

  // access constant pool cache
  __ load_field_entry(x12, x11);
  __ push_reg(x10);
  // X11: field offset, X12: TOS, X13: flags
  load_resolved_field_entry(x12, x12, x10, x11, x13);
  __ pop_reg(x10);

  // Must prevent reordering of the following cp cache loads with bytecode load
  __ membar(MacroAssembler::LoadLoad);

  {
    Label notVolatile;
    __ test_bit(t0, x13, ResolvedFieldEntry::is_volatile_shift);
    __ beqz(t0, notVolatile);
    __ membar(MacroAssembler::StoreStore | MacroAssembler::LoadStore);
    __ bind(notVolatile);
  }

  // Get object from stack
  pop_and_check_object(x12);

  // field address
  __ add(x11, x12, x11);
  const Address field(x11, 0);

  // access field
  switch (bytecode()) {
    case Bytecodes::_fast_aputfield:
      do_oop_store(_masm, field, x10, IN_HEAP);
      break;
    case Bytecodes::_fast_lputfield:
      __ access_store_at(T_LONG, IN_HEAP, field, x10, noreg, noreg, noreg);
      break;
    case Bytecodes::_fast_iputfield:
      __ access_store_at(T_INT, IN_HEAP, field, x10, noreg, noreg, noreg);
      break;
    case Bytecodes::_fast_zputfield:
      __ access_store_at(T_BOOLEAN, IN_HEAP, field, x10, noreg, noreg, noreg);
      break;
    case Bytecodes::_fast_bputfield:
      __ access_store_at(T_BYTE, IN_HEAP, field, x10, noreg, noreg, noreg);
      break;
    case Bytecodes::_fast_sputfield:
      __ access_store_at(T_SHORT, IN_HEAP, field, x10, noreg, noreg, noreg);
      break;
    case Bytecodes::_fast_cputfield:
      __ access_store_at(T_CHAR, IN_HEAP, field, x10, noreg, noreg, noreg);
      break;
    case Bytecodes::_fast_fputfield:
      __ access_store_at(T_FLOAT, IN_HEAP, field, noreg /* ftos */, noreg, noreg, noreg);
      break;
    case Bytecodes::_fast_dputfield:
      __ access_store_at(T_DOUBLE, IN_HEAP, field, noreg /* dtos */, noreg, noreg, noreg);
      break;
    default:
      ShouldNotReachHere();
  }

  {
    Label notVolatile;
    __ test_bit(t0, x13, ResolvedFieldEntry::is_volatile_shift);
    __ beqz(t0, notVolatile);
    __ membar(MacroAssembler::StoreLoad | MacroAssembler::StoreStore);
    __ bind(notVolatile);
  }
}

void TemplateTable::fast_accessfield(TosState state) {
  transition(atos, state);
  // Do the JVMTI work here to avoid disturbing the register state below
  if (JvmtiExport::can_post_field_access()) {
    // Check to see if a field access watch has been set before we
    // take the time to call into the VM.
    Label L1;
    ExternalAddress target((address)JvmtiExport::get_field_access_count_addr());
    __ relocate(target.rspec(), [&] {
      int32_t offset;
      __ la(t0, target.target(), offset);
      __ lwu(x12, Address(t0, offset));
    });
    __ beqz(x12, L1);
    // access constant pool cache entry
    __ load_field_entry(c_rarg2, t1);
    __ verify_oop(x10);
    __ push_ptr(x10);  // save object pointer before call_VM() clobbers it
    __ mv(c_rarg1, x10);
    // c_rarg1: object pointer copied above
    // c_rarg2: cache entry pointer
    __ call_VM(noreg,
               CAST_FROM_FN_PTR(address,
                                InterpreterRuntime::post_field_access),
               c_rarg1, c_rarg2);
    __ pop_ptr(x10); // restore object pointer
    __ bind(L1);
  }

  // access constant pool cache
  __ load_field_entry(x12, x11);

  // Must prevent reordering of the following cp cache loads with bytecode load
  __ membar(MacroAssembler::LoadLoad);

  __ load_sized_value(x11, Address(x12, in_bytes(ResolvedFieldEntry::field_offset_offset())), sizeof(int), true /*is_signed*/);
  __ load_unsigned_byte(x13, Address(x12, in_bytes(ResolvedFieldEntry::flags_offset())));

  // x10: object
  __ verify_oop(x10);
  __ null_check(x10);
  __ add(x11, x10, x11);
  const Address field(x11, 0);

  // access field
  switch (bytecode()) {
    case Bytecodes::_fast_agetfield:
      do_oop_load(_masm, field, x10, IN_HEAP);
      __ verify_oop(x10);
      break;
    case Bytecodes::_fast_lgetfield:
      __ access_load_at(T_LONG, IN_HEAP, x10, field, noreg, noreg);
      break;
    case Bytecodes::_fast_igetfield:
      __ access_load_at(T_INT, IN_HEAP, x10, field, noreg, noreg);
      __ sign_extend(x10, x10, 32);
      break;
    case Bytecodes::_fast_bgetfield:
      __ access_load_at(T_BYTE, IN_HEAP, x10, field, noreg, noreg);
      break;
    case Bytecodes::_fast_sgetfield:
      __ access_load_at(T_SHORT, IN_HEAP, x10, field, noreg, noreg);
      break;
    case Bytecodes::_fast_cgetfield:
      __ access_load_at(T_CHAR, IN_HEAP, x10, field, noreg, noreg);
      break;
    case Bytecodes::_fast_fgetfield:
      __ access_load_at(T_FLOAT, IN_HEAP, noreg /* ftos */, field, noreg, noreg);
      break;
    case Bytecodes::_fast_dgetfield:
      __ access_load_at(T_DOUBLE, IN_HEAP, noreg /* dtos */, field, noreg, noreg);
      break;
    default:
      ShouldNotReachHere();
  }
  {
    Label notVolatile;
    __ test_bit(t0, x13, ResolvedFieldEntry::is_volatile_shift);
    __ beqz(t0, notVolatile);
    __ membar(MacroAssembler::LoadLoad | MacroAssembler::LoadStore);
    __ bind(notVolatile);
  }
}

void TemplateTable::fast_xaccess(TosState state) {
  transition(vtos, state);

  // get receiver
  __ ld(x10, aaddress(0));
  // access constant pool cache
  __ load_field_entry(x12, x13, 2);
  __ load_sized_value(x11, Address(x12, in_bytes(ResolvedFieldEntry::field_offset_offset())), sizeof(int), true /*is_signed*/);

  // make sure exception is reported in correct bcp range (getfield is
  // next instruction)
  __ addi(xbcp, xbcp, 1);
  __ null_check(x10);
  switch (state) {
    case itos:
      __ add(x10, x10, x11);
      __ access_load_at(T_INT, IN_HEAP, x10, Address(x10, 0), noreg, noreg);
      __ sign_extend(x10, x10, 32);
      break;
    case atos:
      __ add(x10, x10, x11);
      do_oop_load(_masm, Address(x10, 0), x10, IN_HEAP);
      __ verify_oop(x10);
      break;
    case ftos:
      __ add(x10, x10, x11);
      __ access_load_at(T_FLOAT, IN_HEAP, noreg /* ftos */, Address(x10), noreg, noreg);
      break;
    default:
      ShouldNotReachHere();
  }

  {
    Label notVolatile;
    __ load_unsigned_byte(x13, Address(x12, in_bytes(ResolvedFieldEntry::flags_offset())));
    __ test_bit(t0, x13, ResolvedFieldEntry::is_volatile_shift);
    __ beqz(t0, notVolatile);
    __ membar(MacroAssembler::LoadLoad | MacroAssembler::LoadStore);
    __ bind(notVolatile);
  }

  __ sub(xbcp, xbcp, 1);
}

//-----------------------------------------------------------------------------
// Calls

void TemplateTable::prepare_invoke(Register cache, Register recv) {

  Bytecodes::Code code = bytecode();
  const bool load_receiver       = (code != Bytecodes::_invokestatic) && (code != Bytecodes::_invokedynamic);

  // save 'interpreter return address'
  __ save_bcp();

  // Load TOS state for later
  __ load_unsigned_byte(t1, Address(cache, in_bytes(ResolvedMethodEntry::type_offset())));

  // load receiver if needed (note: no return address pushed yet)
  if (load_receiver) {
    __ load_unsigned_short(recv, Address(cache, in_bytes(ResolvedMethodEntry::num_parameters_offset())));
    __ shadd(t0, recv, esp, t0, 3);
    __ ld(recv, Address(t0, -Interpreter::expr_offset_in_bytes(1)));
    __ verify_oop(recv);
  }

  // load return address
  {
    const address table_addr = (address) Interpreter::invoke_return_entry_table_for(code);
    __ mv(t0, table_addr);
    __ shadd(t0, t1, t0, t1, 3);
    __ ld(ra, Address(t0, 0));
  }
}

void TemplateTable::invokevirtual_helper(Register index,
                                         Register recv,
                                         Register flags) {
  // Uses temporary registers x10, x13
  assert_different_registers(index, recv, x10, x13);
  // Test for an invoke of a final method
  Label notFinal;
  __ test_bit(t0, flags, ResolvedMethodEntry::is_vfinal_shift);
  __ beqz(t0, notFinal);

  const Register method = index;  // method must be xmethod
  assert(method == xmethod, "Method must be xmethod for interpreter calling convention");

  // do the call - the index is actually the method to call
  // that is, f2 is a vtable index if !is_vfinal, else f2 is a Method*

  // It's final, need a null check here!
  __ null_check(recv);

  // profile this call
  __ profile_final_call(x10);
  __ profile_arguments_type(x10, method, x14, true);

  __ jump_from_interpreted(method);

  __ bind(notFinal);

  // get receiver klass
  __ load_klass(x10, recv);

  // profile this call
  __ profile_virtual_call(x10, xlocals, x13);

  // get target Method & entry point
  __ lookup_virtual_method(x10, index, method);
  __ profile_arguments_type(x13, method, x14, true);
  __ jump_from_interpreted(method);
}

void TemplateTable::invokevirtual(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f2_byte, "use this argument");

  load_resolved_method_entry_virtual(x12,      // ResolvedMethodEntry*
                                     xmethod, // Method* or itable index
                                     x13);     // flags
  prepare_invoke(x12, x12); // recv

  // xmethod: index (actually a Method*)
  // x12: receiver
  // x13: flags

  invokevirtual_helper(xmethod, x12, x13);
}

void TemplateTable::invokespecial(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f1_byte, "use this argument");

  load_resolved_method_entry_special_or_static(x12,      // ResolvedMethodEntry*
                                               xmethod, // Method*
                                               x13);     // flags
  prepare_invoke(x12, x12);  // get receiver also for null check

  __ verify_oop(x12);
  __ null_check(x12);
  // do the call
  __ profile_call(x10);
  __ profile_arguments_type(x10, xmethod, xbcp, false);
  __ jump_from_interpreted(xmethod);
}

void TemplateTable::invokestatic(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f1_byte, "use this argument");

  load_resolved_method_entry_special_or_static(x12,      // ResolvedMethodEntry*
                                               xmethod, // Method*
                                               x13);     // flags
  prepare_invoke(x12, x12);  // get receiver also for null check

  // do the call
  __ profile_call(x10);
  __ profile_arguments_type(x10, xmethod, x14, false);
  __ jump_from_interpreted(xmethod);
}

void TemplateTable::fast_invokevfinal(int byte_no) {
  __ call_Unimplemented();
}

void TemplateTable::invokeinterface(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f1_byte, "use this argument");

  load_resolved_method_entry_interface(x12,      // ResolvedMethodEntry*
                                       x10,      // Klass*
                                       xmethod, // Method* or itable/vtable index
                                       x13);     // flags
  prepare_invoke(x12, x12); // receiver

  // x10: interface klass (from f1)
  // xmethod: method (from f2)
  // x12: receiver
  // x13: flags

  // First check for Object case, then private interface method,
  // then regular interface method.

  // Special case of invokeinterface called for virtual method of
  // java.lang.Object. See cpCache.cpp for details
  Label notObjectMethod;
  __ test_bit(t0, x13, ResolvedMethodEntry::is_forced_virtual_shift);
  __ beqz(t0, notObjectMethod);

  invokevirtual_helper(xmethod, x12, x13);
  __ bind(notObjectMethod);

  Label no_such_interface;

  // Check for private method invocation - indicated by vfinal
  Label notVFinal;
  __ test_bit(t0, x13, ResolvedMethodEntry::is_vfinal_shift);
  __ beqz(t0, notVFinal);

  // Check receiver klass into x13
  __ load_klass(x13, x12);

  Label subtype;
  __ check_klass_subtype(x13, x10, x14, subtype);
  // If we get here the typecheck failed
  __ j(no_such_interface);
  __ bind(subtype);

  __ profile_final_call(x10);
  __ profile_arguments_type(x10, xmethod, x14, true);
  __ jump_from_interpreted(xmethod);

  __ bind(notVFinal);

  // Get receiver klass into x13
  __ restore_locals();
  __ load_klass(x13, x12);

  Label no_such_method;

  // Preserve method for the throw_AbstractMethodErrorVerbose.
  __ mv(x28, xmethod);
  // Receiver subtype check against REFC.
  // Superklass in x10. Subklass in x13. Blows t1, x30
  __ lookup_interface_method(// inputs: rec. class, interface, itable index
                             x13, x10, noreg,
                             // outputs: scan temp. reg, scan temp. reg
                             t1, x30,
                             no_such_interface,
                             /*return_method=*/false);

  // profile this call
  __ profile_virtual_call(x13, x30, x9);

  // Get declaring interface class from method, and itable index
  __ load_method_holder(x10, xmethod);
  __ lwu(xmethod, Address(xmethod, Method::itable_index_offset()));
  __ subw(xmethod, xmethod, Method::itable_index_max);
  __ negw(xmethod, xmethod);

  // Preserve recvKlass for throw_AbstractMethodErrorVerbose
  __ mv(xlocals, x13);
  __ lookup_interface_method(// inputs: rec. class, interface, itable index
                             xlocals, x10, xmethod,
                             // outputs: method, scan temp. reg
                             xmethod, x30,
                             no_such_interface);

  // xmethod: Method to call
  // x12: receiver
  // Check for abstract method error
  // Note: This should be done more efficiently via a throw_abstract_method_error
  //       interpreter entry point and a conditional jump to it in case of a null
  //       method.
  __ beqz(xmethod, no_such_method);

  __ profile_arguments_type(x13, xmethod, x30, true);

  // do the call
  // x12: receiver
  // xmethod: Method
  __ jump_from_interpreted(xmethod);
  __ should_not_reach_here();

  // exception handling code follows ...
  // note: must restore interpreter registers to canonical
  //       state for exception handling to work correctly!

  __ bind(no_such_method);
  // throw exception
  __ restore_bcp();    // bcp must be correct for exception handler   (was destroyed)
  __ restore_locals(); // make sure locals pointer is correct as well (was destroyed)
  // Pass arguments for generating a verbose error message.
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_AbstractMethodErrorVerbose), x13, x28);
  // the call_VM checks for exception, so we should never return here.
  __ should_not_reach_here();

  __ bind(no_such_interface);
  // throw exceptiong
  __ restore_bcp();    // bcp must be correct for exception handler   (was destroyed)
  __ restore_locals(); // make sure locals pointer is correct as well (was destroyed)
  // Pass arguments for generating a verbose error message.
  __ call_VM(noreg, CAST_FROM_FN_PTR(address,
                                     InterpreterRuntime::throw_IncompatibleClassChangeErrorVerbose), x13, x10);
  // the call_VM checks for exception, so we should never return here.
  __ should_not_reach_here();
  return;
}

void TemplateTable::invokehandle(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f1_byte, "use this argument");

  load_resolved_method_entry_handle(x12,      // ResolvedMethodEntry*
                                    xmethod, // Method*
                                    x10,      // Resolved reference
                                    x13);     // flags
  prepare_invoke(x12, x12);

  __ verify_method_ptr(x12);
  __ verify_oop(x12);
  __ null_check(x12);

  // FIXME: profile the LambdaForm also

  // x30 is safe to use here as a temp reg because it is about to
  // be clobbered by jump_from_interpreted().
  __ profile_final_call(x30);
  __ profile_arguments_type(x30, xmethod, x14, true);

  __ jump_from_interpreted(xmethod);
}

void TemplateTable::invokedynamic(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f1_byte, "use this argument");

  load_invokedynamic_entry(xmethod);

  // x10: CallSite object (from cpool->resolved_references[])
  // xmethod: MH.linkToCallSite method

  // Note: x10_callsite is already pushed

  // %%% should make a type profile for any invokedynamic that takes a ref argument
  // profile this call
  __ profile_call(xbcp);
  __ profile_arguments_type(x13, xmethod, x30, false);

  __ verify_oop(x10);

  __ jump_from_interpreted(xmethod);
}

//-----------------------------------------------------------------------------
// Allocation

void TemplateTable::_new() {
  transition(vtos, atos);

  __ get_unsigned_2_byte_index_at_bcp(x13, 1);
  Label slow_case;
  Label done;
  Label initialize_header;

  __ get_cpool_and_tags(x14, x10);
  // Make sure the class we're about to instantiate has been resolved.
  // This is done before loading InstanceKlass to be consistent with the order
  // how Constant Pool is update (see ConstantPool::klass_at_put)
  const int tags_offset = Array<u1>::base_offset_in_bytes();
  __ add(t0, x10, x13);
  __ la(t0, Address(t0, tags_offset));
  __ membar(MacroAssembler::AnyAny);
  __ lbu(t0, t0);
  __ membar(MacroAssembler::LoadLoad | MacroAssembler::LoadStore);
  __ sub(t1, t0, (u1)JVM_CONSTANT_Class);
  __ bnez(t1, slow_case);

  // get InstanceKlass
  __ load_resolved_klass_at_offset(x14, x13, x14, t0);

  // make sure klass is initialized
  assert(VM_Version::supports_fast_class_init_checks(),
         "Optimization requires support for fast class initialization checks");
  __ clinit_barrier(x14, t0, nullptr /*L_fast_path*/, &slow_case);

  // get instance_size in InstanceKlass (scaled to a count of bytes)
  __ lwu(x13, Address(x14, Klass::layout_helper_offset()));
  // test to see if it has a finalizer or is malformed in some way
  __ test_bit(t0, x13, exact_log2(Klass::_lh_instance_slow_path_bit));
  __ bnez(t0, slow_case);

  // Allocate the instance:
  //  If TLAB is enabled:
  //    Try to allocate in the TLAB.
  //    If fails, go to the slow path.
  //    Initialize the allocation.
  //    Exit.
  //  Go to slow path.

  if (UseTLAB) {
    __ tlab_allocate(x10, x13, 0, noreg, x11, slow_case);

    if (ZeroTLAB) {
      // the fields have been already cleared
      __ j(initialize_header);
    }

    // The object is initialized before the header. If the object size is
    // zero, go directly to the header initialization.
    __ sub(x13, x13, sizeof(oopDesc));
    __ beqz(x13, initialize_header);

    // Initialize object fields
    {
      __ add(x12, x10, sizeof(oopDesc));
      Label loop;
      __ bind(loop);
      __ sd(zr, Address(x12));
      __ add(x12, x12, BytesPerLong);
      __ sub(x13, x13, BytesPerLong);
      __ bnez(x13, loop);
    }

    // initialize object hader only.
    __ bind(initialize_header);
    __ mv(t0, (intptr_t)markWord::prototype().value());
    __ sd(t0, Address(x10, oopDesc::mark_offset_in_bytes()));
    __ store_klass_gap(x10, zr);   // zero klass gap for compressed oops
    __ store_klass(x10, x14);      // store klass last

    {
      SkipIfEqual skip(_masm, &DTraceAllocProbes, false);
      // Trigger dtrace event for fastpath
      __ push(atos); // save the return value
      __ call_VM_leaf(CAST_FROM_FN_PTR(address, static_cast<int (*)(oopDesc*)>(SharedRuntime::dtrace_object_alloc)), x10);
      __ pop(atos); // restore the return value
    }
    __ j(done);
  }

  // slow case
  __ bind(slow_case);
  __ get_constant_pool(c_rarg1);
  __ get_unsigned_2_byte_index_at_bcp(c_rarg2, 1);
  call_VM(x10, CAST_FROM_FN_PTR(address, InterpreterRuntime::_new), c_rarg1, c_rarg2);
  __ verify_oop(x10);

  // continue
  __ bind(done);
  // Must prevent reordering of stores for object initialization with stores that publish the new object.
  __ membar(MacroAssembler::StoreStore);
}

void TemplateTable::newarray() {
  transition(itos, atos);
  __ load_unsigned_byte(c_rarg1, at_bcp(1));
  __ mv(c_rarg2, x10);
  call_VM(x10, CAST_FROM_FN_PTR(address, InterpreterRuntime::newarray),
          c_rarg1, c_rarg2);
  // Must prevent reordering of stores for object initialization with stores that publish the new object.
  __ membar(MacroAssembler::StoreStore);
}

void TemplateTable::anewarray() {
  transition(itos, atos);
  __ get_unsigned_2_byte_index_at_bcp(c_rarg2, 1);
  __ get_constant_pool(c_rarg1);
  __ mv(c_rarg3, x10);
  call_VM(x10, CAST_FROM_FN_PTR(address, InterpreterRuntime::anewarray),
          c_rarg1, c_rarg2, c_rarg3);
  // Must prevent reordering of stores for object initialization with stores that publish the new object.
  __ membar(MacroAssembler::StoreStore);
}

void TemplateTable::arraylength() {
  transition(atos, itos);
  __ lwu(x10, Address(x10, arrayOopDesc::length_offset_in_bytes()));
}

void TemplateTable::checkcast() {
  transition(atos, atos);
  Label done, is_null, ok_is_subtype, quicked, resolved;
  __ beqz(x10, is_null);

  // Get cpool & tags index
  __ get_cpool_and_tags(x12, x13); // x12=cpool, x13=tags array
  __ get_unsigned_2_byte_index_at_bcp(x9, 1); // x9=index
  // See if bytecode has already been quicked
  __ add(t0, x13, Array<u1>::base_offset_in_bytes());
  __ add(x11, t0, x9);
  __ membar(MacroAssembler::AnyAny);
  __ lbu(x11, x11);
  __ membar(MacroAssembler::LoadLoad | MacroAssembler::LoadStore);
  __ sub(t0, x11, (u1)JVM_CONSTANT_Class);
  __ beqz(t0, quicked);

  __ push(atos); // save receiver for result, and for GC
  call_VM(x10, CAST_FROM_FN_PTR(address, InterpreterRuntime::quicken_io_cc));
  // vm_result_2 has metadata result
  __ get_vm_result_2(x10, xthread);
  __ pop_reg(x13); // restore receiver
  __ j(resolved);

  // Get superklass in x10 and subklass in x13
  __ bind(quicked);
  __ mv(x13, x10); // Save object in x13; x10 needed for subtype check
  __ load_resolved_klass_at_offset(x12, x9, x10, t0); // x10 = klass

  __ bind(resolved);
  __ load_klass(x9, x13);

  // Generate subtype check.  Blows x12, x15.  Object in x13.
  // Superklass in x10.  Subklass in x9.
  __ gen_subtype_check(x9, ok_is_subtype);

  // Come here on failure
  __ push_reg(x13);
  // object is at TOS
  __ j(Interpreter::_throw_ClassCastException_entry);

  // Come here on success
  __ bind(ok_is_subtype);
  __ mv(x10, x13); // Restore object in x13

  // Collect counts on whether this test sees nulls a lot or not.
  if (ProfileInterpreter) {
    __ j(done);
    __ bind(is_null);
    __ profile_null_seen(x12);
  } else {
    __ bind(is_null);   // same as 'done'
  }
  __ bind(done);
}

void TemplateTable::instanceof() {
  transition(atos, itos);
  Label done, is_null, ok_is_subtype, quicked, resolved;
  __ beqz(x10, is_null);

  // Get cpool & tags index
  __ get_cpool_and_tags(x12, x13); // x12=cpool, x13=tags array
  __ get_unsigned_2_byte_index_at_bcp(x9, 1); // x9=index
  // See if bytecode has already been quicked
  __ add(t0, x13, Array<u1>::base_offset_in_bytes());
  __ add(x11, t0, x9);
  __ membar(MacroAssembler::AnyAny);
  __ lbu(x11, x11);
  __ membar(MacroAssembler::LoadLoad | MacroAssembler::LoadStore);
  __ sub(t0, x11, (u1)JVM_CONSTANT_Class);
  __ beqz(t0, quicked);

  __ push(atos); // save receiver for result, and for GC
  call_VM(x10, CAST_FROM_FN_PTR(address, InterpreterRuntime::quicken_io_cc));
  // vm_result_2 has metadata result
  __ get_vm_result_2(x10, xthread);
  __ pop_reg(x13); // restore receiver
  __ verify_oop(x13);
  __ load_klass(x13, x13);
  __ j(resolved);

  // Get superklass in x10 and subklass in x13
  __ bind(quicked);
  __ load_klass(x13, x10);
  __ load_resolved_klass_at_offset(x12, x9, x10, t0);

  __ bind(resolved);

  // Generate subtype check.  Blows x12, x15
  // Superklass in x10.  Subklass in x13.
  __ gen_subtype_check(x13, ok_is_subtype);

  // Come here on failure
  __ mv(x10, zr);
  __ j(done);
  // Come here on success
  __ bind(ok_is_subtype);
  __ mv(x10, 1);

  // Collect counts on whether this test sees nulls a lot or not.
  if (ProfileInterpreter) {
    __ j(done);
    __ bind(is_null);
    __ profile_null_seen(x12);
  } else {
    __ bind(is_null);   // same as 'done'
  }
  __ bind(done);
  // x10 = 0: obj is    null or  obj is not an instanceof the specified klass
  // x10 = 1: obj isn't null and obj is     an instanceof the specified klass
}

//-----------------------------------------------------------------------------
// Breakpoints

void TemplateTable::_breakpoint() {
  // Note: We get here even if we are single stepping..
  // jbug inists on setting breakpoints at every bytecode
  // even if we are in single step mode.

  transition(vtos, vtos);

  // get the unpatched byte code
  __ get_method(c_rarg1);
  __ call_VM(noreg,
             CAST_FROM_FN_PTR(address,
                              InterpreterRuntime::get_original_bytecode_at),
             c_rarg1, xbcp);
  __ mv(x9, x10);

  // post the breakpoint event
  __ call_VM(noreg,
             CAST_FROM_FN_PTR(address, InterpreterRuntime::_breakpoint),
             xmethod, xbcp);

  // complete the execution of original bytecode
  __ mv(t0, x9);
  __ dispatch_only_normal(vtos);
}

//-----------------------------------------------------------------------------
// Exceptions

void TemplateTable::athrow() {
  transition(atos, vtos);
  __ null_check(x10);
  __ j(Interpreter::throw_exception_entry());
}

//-----------------------------------------------------------------------------
// Synchronization
//
// Note: monitorenter & exit are symmetric routines; which is reflected
//       in the assembly code structure as well
//
// Stack layout:
//
// [expressions  ] <--- esp               = expression stack top
// ..
// [expressions  ]
// [monitor entry] <--- monitor block top = expression stack bot
// ..
// [monitor entry]
// [frame data   ] <--- monitor block bot
// ...
// [saved fp     ] <--- fp

void TemplateTable::monitorenter() {
  transition(atos, vtos);

   // check for null object
   __ null_check(x10);

   const Address monitor_block_top(
         fp, frame::interpreter_frame_monitor_block_top_offset * wordSize);
   const Address monitor_block_bot(
         fp, frame::interpreter_frame_initial_sp_offset * wordSize);
   const int entry_size = frame::interpreter_frame_monitor_size_in_bytes();

   Label allocated;

   // initialize entry pointer
   __ mv(c_rarg1, zr); // points to free slot or null

   // find a free slot in the monitor block (result in c_rarg1)
   {
     Label entry, loop, exit, notUsed;
     __ ld(c_rarg3, monitor_block_top); // derelativize pointer
     __ shadd(c_rarg3, c_rarg3, fp, c_rarg3, LogBytesPerWord);
     // Now c_rarg3 points to current entry, starting with top-most entry

     __ la(c_rarg2, monitor_block_bot); // points to word before bottom

     __ j(entry);

     __ bind(loop);
     // check if current entry is used
     // if not used then remember entry in c_rarg1
     __ ld(t0, Address(c_rarg3, BasicObjectLock::obj_offset()));
     __ bnez(t0, notUsed);
     __ mv(c_rarg1, c_rarg3);
     __ bind(notUsed);
     // check if current entry is for same object
     // if same object then stop searching
     __ beq(x10, t0, exit);
     // otherwise advance to next entry
     __ add(c_rarg3, c_rarg3, entry_size);
     __ bind(entry);
     // check if bottom reached
     // if not at bottom then check this entry
     __ bne(c_rarg3, c_rarg2, loop);
     __ bind(exit);
   }

   __ bnez(c_rarg1, allocated); // check if a slot has been found and
                             // if found, continue with that on

   // allocate one if there's no free slot
   {
     Label entry, loop;
     // 1. compute new pointers            // esp: old expression stack top

     __ check_extended_sp();
     __ sub(sp, sp, entry_size);           // make room for the monitor
     __ sub(t0, sp, fp);
     __ srai(t0, t0, Interpreter::logStackElementSize);
     __ sd(t0, Address(fp, frame::interpreter_frame_extended_sp_offset * wordSize));

     __ ld(c_rarg1, monitor_block_bot);    // derelativize pointer
     __ shadd(c_rarg1, c_rarg1, fp, c_rarg1, LogBytesPerWord);
     // Now c_rarg1 points to the old expression stack bottom

     __ sub(esp, esp, entry_size);         // move expression stack top
     __ sub(c_rarg1, c_rarg1, entry_size); // move expression stack bottom
     __ mv(c_rarg3, esp);                  // set start value for copy loop
     __ sub(t0, c_rarg1, fp);              // relativize pointer
     __ srai(t0, t0, Interpreter::logStackElementSize);
     __ sd(t0, monitor_block_bot);         // set new monitor block bottom

     __ j(entry);
     // 2. move expression stack contents
     __ bind(loop);
     __ ld(c_rarg2, Address(c_rarg3, entry_size)); // load expression stack
                                                   // word from old location
     __ sd(c_rarg2, Address(c_rarg3, 0));          // and store it at new location
     __ add(c_rarg3, c_rarg3, wordSize);           // advance to next word
     __ bind(entry);
     __ bne(c_rarg3, c_rarg1, loop);    // check if bottom reached.if not at bottom
                                        // then copy next word
   }

   // call run-time routine
   // c_rarg1: points to monitor entry
   __ bind(allocated);

   // Increment bcp to point to the next bytecode, so exception
   // handling for async. exceptions work correctly.
   // The object has already been popped from the stack, so the
   // expression stack looks correct.
   __ addi(xbcp, xbcp, 1);

   // store object
   __ sd(x10, Address(c_rarg1, BasicObjectLock::obj_offset()));
   __ lock_object(c_rarg1);

   // check to make sure this monitor doesn't cause stack overflow after locking
   __ save_bcp();  // in case of exception
   __ generate_stack_overflow_check(0);

   // The bcp has already been incremented. Just need to dispatch to
   // next instruction.
   __ dispatch_next(vtos);
}

void TemplateTable::monitorexit() {
  transition(atos, vtos);

  // check for null object
  __ null_check(x10);

  const Address monitor_block_top(
        fp, frame::interpreter_frame_monitor_block_top_offset * wordSize);
  const Address monitor_block_bot(
        fp, frame::interpreter_frame_initial_sp_offset * wordSize);
  const int entry_size = frame::interpreter_frame_monitor_size_in_bytes();

  Label found;

  // find matching slot
  {
    Label entry, loop;
    __ ld(c_rarg1, monitor_block_top); // derelativize pointer
    __ shadd(c_rarg1, c_rarg1, fp, c_rarg1, LogBytesPerWord);
    // Now c_rarg1 points to current entry, starting with top-most entry

    __ la(c_rarg2, monitor_block_bot); // points to word before bottom
                                        // of monitor block
    __ j(entry);

    __ bind(loop);
    // check if current entry is for same object
    __ ld(t0, Address(c_rarg1, BasicObjectLock::obj_offset()));
    // if same object then stop searching
    __ beq(x10, t0, found);
    // otherwise advance to next entry
    __ add(c_rarg1, c_rarg1, entry_size);
    __ bind(entry);
    // check if bottom reached
    // if not at bottom then check this entry
    __ bne(c_rarg1, c_rarg2, loop);
  }

  // error handling. Unlocking was not block-structured
  __ call_VM(noreg, CAST_FROM_FN_PTR(address,
             InterpreterRuntime::throw_illegal_monitor_state_exception));
  __ should_not_reach_here();

  // call run-time routine
  __ bind(found);
  __ push_ptr(x10); // make sure object is on stack (contract with oopMaps)
  __ unlock_object(c_rarg1);
  __ pop_ptr(x10); // discard object
}

// Wide instructions
void TemplateTable::wide() {
  __ load_unsigned_byte(x9, at_bcp(1));
  __ mv(t0, (address)Interpreter::_wentry_point);
  __ shadd(t0, x9, t0, t1, 3);
  __ ld(t0, Address(t0));
  __ jr(t0);
}

// Multi arrays
void TemplateTable::multianewarray() {
  transition(vtos, atos);
  __ load_unsigned_byte(x10, at_bcp(3)); // get number of dimensions
  // last dim is on top of stack; we want address of first one:
  // first_addr = last_addr + (ndims - 1) * wordSize
  __ shadd(c_rarg1, x10, esp, c_rarg1, 3);
  __ sub(c_rarg1, c_rarg1, wordSize);
  call_VM(x10,
          CAST_FROM_FN_PTR(address, InterpreterRuntime::multianewarray),
          c_rarg1);
  __ load_unsigned_byte(x11, at_bcp(3));
  __ shadd(esp, x11, esp, t0, 3);
}
