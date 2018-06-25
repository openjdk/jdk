/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "interpreter/interp_masm.hpp"
#include "interpreter/templateTable.hpp"
#include "memory/universe.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/synchronizer.hpp"
#include "utilities/macros.hpp"

#define __ _masm->

// Misc helpers

// Do an oop store like *(base + index + offset) = val
// index can be noreg,
static void do_oop_store(InterpreterMacroAssembler* _masm,
                         Register base,
                         Register index,
                         int offset,
                         Register val,
                         Register tmp,
                         DecoratorSet decorators = 0) {
  assert(tmp != val && tmp != base && tmp != index, "register collision");
  assert(index == noreg || offset == 0, "only one offset");
  if (index == noreg) {
    __ store_heap_oop(val, base, offset, tmp, decorators);
  } else {
    __ store_heap_oop(val, base, index, tmp, decorators);
  }
}

// Do an oop load like val = *(base + index + offset)
// index can be noreg.
static void do_oop_load(InterpreterMacroAssembler* _masm,
                        Register base,
                        Register index,
                        int offset,
                        Register dst,
                        Register tmp,
                        DecoratorSet decorators = 0) {
  assert(tmp != dst && tmp != base && tmp != index, "register collision");
  assert(index == noreg || offset == 0, "only one offset");
  if (index == noreg) {
    __ load_heap_oop(base, offset, dst, tmp, decorators);
  } else {
    __ load_heap_oop(base, index, dst, tmp, decorators);
  }
}


//----------------------------------------------------------------------------------------------------
// Platform-dependent initialization

void TemplateTable::pd_initialize() {
  // (none)
}


//----------------------------------------------------------------------------------------------------
// Condition conversion
Assembler::Condition ccNot(TemplateTable::Condition cc) {
  switch (cc) {
    case TemplateTable::equal        : return Assembler::notEqual;
    case TemplateTable::not_equal    : return Assembler::equal;
    case TemplateTable::less         : return Assembler::greaterEqual;
    case TemplateTable::less_equal   : return Assembler::greater;
    case TemplateTable::greater      : return Assembler::lessEqual;
    case TemplateTable::greater_equal: return Assembler::less;
  }
  ShouldNotReachHere();
  return Assembler::zero;
}

//----------------------------------------------------------------------------------------------------
// Miscelaneous helper routines


Address TemplateTable::at_bcp(int offset) {
  assert(_desc->uses_bcp(), "inconsistent uses_bcp information");
  return Address(Lbcp, offset);
}


void TemplateTable::patch_bytecode(Bytecodes::Code bc, Register bc_reg,
                                   Register temp_reg, bool load_bc_into_bc_reg/*=true*/,
                                   int byte_no) {
  // With sharing on, may need to test Method* flag.
  if (!RewriteBytecodes)  return;
  Label L_patch_done;

  switch (bc) {
  case Bytecodes::_fast_aputfield:
  case Bytecodes::_fast_bputfield:
  case Bytecodes::_fast_zputfield:
  case Bytecodes::_fast_cputfield:
  case Bytecodes::_fast_dputfield:
  case Bytecodes::_fast_fputfield:
  case Bytecodes::_fast_iputfield:
  case Bytecodes::_fast_lputfield:
  case Bytecodes::_fast_sputfield:
    {
      // We skip bytecode quickening for putfield instructions when
      // the put_code written to the constant pool cache is zero.
      // This is required so that every execution of this instruction
      // calls out to InterpreterRuntime::resolve_get_put to do
      // additional, required work.
      assert(byte_no == f1_byte || byte_no == f2_byte, "byte_no out of range");
      assert(load_bc_into_bc_reg, "we use bc_reg as temp");
      __ get_cache_and_index_and_bytecode_at_bcp(bc_reg, temp_reg, temp_reg, byte_no, 1);
      __ set(bc, bc_reg);
      __ cmp_and_br_short(temp_reg, 0, Assembler::equal, Assembler::pn, L_patch_done);  // don't patch
    }
    break;
  default:
    assert(byte_no == -1, "sanity");
    if (load_bc_into_bc_reg) {
      __ set(bc, bc_reg);
    }
  }

  if (JvmtiExport::can_post_breakpoint()) {
    Label L_fast_patch;
    __ ldub(at_bcp(0), temp_reg);
    __ cmp_and_br_short(temp_reg, Bytecodes::_breakpoint, Assembler::notEqual, Assembler::pt, L_fast_patch);
    // perform the quickening, slowly, in the bowels of the breakpoint table
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::set_original_bytecode_at), Lmethod, Lbcp, bc_reg);
    __ ba_short(L_patch_done);
    __ bind(L_fast_patch);
  }

#ifdef ASSERT
  Bytecodes::Code orig_bytecode =  Bytecodes::java_code(bc);
  Label L_okay;
  __ ldub(at_bcp(0), temp_reg);
  __ cmp(temp_reg, orig_bytecode);
  __ br(Assembler::equal, false, Assembler::pt, L_okay);
  __ delayed()->cmp(temp_reg, bc_reg);
  __ br(Assembler::equal, false, Assembler::pt, L_okay);
  __ delayed()->nop();
  __ stop("patching the wrong bytecode");
  __ bind(L_okay);
#endif

  // patch bytecode
  __ stb(bc_reg, at_bcp(0));
  __ bind(L_patch_done);
}

//----------------------------------------------------------------------------------------------------
// Individual instructions

void TemplateTable::nop() {
  transition(vtos, vtos);
  // nothing to do
}

void TemplateTable::shouldnotreachhere() {
  transition(vtos, vtos);
  __ stop("shouldnotreachhere bytecode");
}

void TemplateTable::aconst_null() {
  transition(vtos, atos);
  __ clr(Otos_i);
}


void TemplateTable::iconst(int value) {
  transition(vtos, itos);
  __ set(value, Otos_i);
}


void TemplateTable::lconst(int value) {
  transition(vtos, ltos);
  assert(value >= 0, "check this code");
  __ set(value, Otos_l);
}


void TemplateTable::fconst(int value) {
  transition(vtos, ftos);
  static float zero = 0.0, one = 1.0, two = 2.0;
  float* p;
  switch( value ) {
   default: ShouldNotReachHere();
   case 0:  p = &zero;  break;
   case 1:  p = &one;   break;
   case 2:  p = &two;   break;
  }
  AddressLiteral a(p);
  __ sethi(a, G3_scratch);
  __ ldf(FloatRegisterImpl::S, G3_scratch, a.low10(), Ftos_f);
}


void TemplateTable::dconst(int value) {
  transition(vtos, dtos);
  static double zero = 0.0, one = 1.0;
  double* p;
  switch( value ) {
   default: ShouldNotReachHere();
   case 0:  p = &zero;  break;
   case 1:  p = &one;   break;
  }
  AddressLiteral a(p);
  __ sethi(a, G3_scratch);
  __ ldf(FloatRegisterImpl::D, G3_scratch, a.low10(), Ftos_d);
}


// %%%%% Should factore most snippet templates across platforms

void TemplateTable::bipush() {
  transition(vtos, itos);
  __ ldsb( at_bcp(1), Otos_i );
}

void TemplateTable::sipush() {
  transition(vtos, itos);
  __ get_2_byte_integer_at_bcp(1, G3_scratch, Otos_i, InterpreterMacroAssembler::Signed);
}

void TemplateTable::ldc(bool wide) {
  transition(vtos, vtos);
  Label call_ldc, notInt, isString, notString, notClass, notFloat, exit;

  if (wide) {
    __ get_2_byte_integer_at_bcp(1, G3_scratch, O1, InterpreterMacroAssembler::Unsigned);
  } else {
    __ ldub(Lbcp, 1, O1);
  }
  __ get_cpool_and_tags(O0, O2);

  const int base_offset = ConstantPool::header_size() * wordSize;
  const int tags_offset = Array<u1>::base_offset_in_bytes();

  // get type from tags
  __ add(O2, tags_offset, O2);
  __ ldub(O2, O1, O2);

  // unresolved class? If so, must resolve
  __ cmp_and_brx_short(O2, JVM_CONSTANT_UnresolvedClass, Assembler::equal, Assembler::pt, call_ldc);

  // unresolved class in error state
  __ cmp_and_brx_short(O2, JVM_CONSTANT_UnresolvedClassInError, Assembler::equal, Assembler::pn, call_ldc);

  __ cmp(O2, JVM_CONSTANT_Class);      // need to call vm to get java mirror of the class
  __ brx(Assembler::notEqual, true, Assembler::pt, notClass);
  __ delayed()->add(O0, base_offset, O0);

  __ bind(call_ldc);
  __ set(wide, O1);
  call_VM(Otos_i, CAST_FROM_FN_PTR(address, InterpreterRuntime::ldc), O1);
  __ push(atos);
  __ ba(exit);
  __ delayed()->nop();

  __ bind(notClass);
 // __ add(O0, base_offset, O0);
  __ sll(O1, LogBytesPerWord, O1);
  __ cmp(O2, JVM_CONSTANT_Integer);
  __ brx(Assembler::notEqual, true, Assembler::pt, notInt);
  __ delayed()->cmp(O2, JVM_CONSTANT_String);
  __ ld(O0, O1, Otos_i);
  __ push(itos);
  __ ba(exit);
  __ delayed()->nop();

  __ bind(notInt);
 // __ cmp(O2, JVM_CONSTANT_String);
  __ brx(Assembler::notEqual, true, Assembler::pt, notString);
  __ delayed()->cmp(O2, JVM_CONSTANT_Float);
  __ bind(isString);
  __ stop("string should be rewritten to fast_aldc");
  __ ba(exit);
  __ delayed()->nop();

  __ bind(notString);
 //__ cmp(O2, JVM_CONSTANT_Float);
  __ brx(Assembler::notEqual, true, Assembler::pt, notFloat);
  __ delayed()->nop();
  __ ldf(FloatRegisterImpl::S, O0, O1, Ftos_f);
  __ push(ftos);
  __ ba(exit);
  __ delayed()->nop();

  // assume the tag is for condy; if not, the VM runtime will tell us
  __ bind(notFloat);
  condy_helper(exit);

  __ bind(exit);
}

// Fast path for caching oop constants.
// %%% We should use this to handle Class and String constants also.
// %%% It will simplify the ldc/primitive path considerably.
void TemplateTable::fast_aldc(bool wide) {
  transition(vtos, atos);

  int index_size = wide ? sizeof(u2) : sizeof(u1);
  Label resolved;

  // We are resolved if the resolved reference cache entry contains a
  // non-null object (CallSite, etc.)
  assert_different_registers(Otos_i, G3_scratch);
  __ get_cache_index_at_bcp(Otos_i, G3_scratch, 1, index_size);  // load index => G3_scratch
  __ load_resolved_reference_at_index(Otos_i, G3_scratch, Lscratch);
  __ tst(Otos_i);
  __ br(Assembler::notEqual, false, Assembler::pt, resolved);
  __ delayed()->set((int)bytecode(), O1);

  address entry = CAST_FROM_FN_PTR(address, InterpreterRuntime::resolve_ldc);

  // first time invocation - must resolve first
  __ call_VM(Otos_i, entry, O1);
  __ bind(resolved);

  { // Check for the null sentinel.
    // If we just called the VM, it already did the mapping for us,
    // but it's harmless to retry.
    Label notNull;
    __ set(ExternalAddress((address)Universe::the_null_sentinel_addr()), G3_scratch);
    __ ld_ptr(G3_scratch, 0, G3_scratch);
    __ cmp(G3_scratch, Otos_i);
    __ br(Assembler::notEqual, true, Assembler::pt, notNull);
    __ delayed()->nop();
    __ clr(Otos_i);  // NULL object reference
    __ bind(notNull);
  }

  // Safe to call with 0 result
  __ verify_oop(Otos_i);
}

void TemplateTable::ldc2_w() {
  transition(vtos, vtos);
  Label notDouble, notLong, exit;

  __ get_2_byte_integer_at_bcp(1, G3_scratch, O1, InterpreterMacroAssembler::Unsigned);
  __ get_cpool_and_tags(O0, O2);

  const int base_offset = ConstantPool::header_size() * wordSize;
  const int tags_offset = Array<u1>::base_offset_in_bytes();
  // get type from tags
  __ add(O2, tags_offset, O2);
  __ ldub(O2, O1, O2);

  __ sll(O1, LogBytesPerWord, O1);
  __ add(O0, O1, G3_scratch);

  __ cmp_and_brx_short(O2, JVM_CONSTANT_Double, Assembler::notEqual, Assembler::pt, notDouble);
  // A double can be placed at word-aligned locations in the constant pool.
  // Check out Conversions.java for an example.
  // Also ConstantPool::header_size() is 20, which makes it very difficult
  // to double-align double on the constant pool.  SG, 11/7/97
  __ ldf(FloatRegisterImpl::D, G3_scratch, base_offset, Ftos_d);
  __ push(dtos);
  __ ba_short(exit);

  __ bind(notDouble);
  __ cmp_and_brx_short(O2, JVM_CONSTANT_Long, Assembler::notEqual, Assembler::pt, notLong);
  __ ldx(G3_scratch, base_offset, Otos_l);
  __ push(ltos);
  __ ba_short(exit);

  __ bind(notLong);
  condy_helper(exit);

  __ bind(exit);
}

void TemplateTable::condy_helper(Label& exit) {
  Register Robj = Otos_i;
  Register Roffset = G4_scratch;
  Register Rflags = G1_scratch;

  address entry = CAST_FROM_FN_PTR(address, InterpreterRuntime::resolve_ldc);

  __ set((int)bytecode(), O1);
  __ call_VM(Robj, entry, O1);

  // Get vm_result_2 has flags = (tos, off) using format CPCE::_flags
  __ get_vm_result_2(G3_scratch);

  // Get offset
  __ set((int)ConstantPoolCacheEntry::field_index_mask, Roffset);
  __ and3(G3_scratch, Roffset, Roffset);

  // compute type
  __ srl(G3_scratch, ConstantPoolCacheEntry::tos_state_shift, Rflags);
  // Make sure we don't need to mask Rflags after the above shift
  ConstantPoolCacheEntry::verify_tos_state_shift();

  switch (bytecode()) {
  case Bytecodes::_ldc:
  case Bytecodes::_ldc_w:
    {
      // tos in (itos, ftos, stos, btos, ctos, ztos)
      Label notInt, notFloat, notShort, notByte, notChar, notBool;
      __ cmp(Rflags, itos);
      __ br(Assembler::notEqual, false, Assembler::pt, notInt);
      __ delayed()->cmp(Rflags, ftos);
      // itos
      __ ld(Robj, Roffset, Otos_i);
      __ push(itos);
      __ ba_short(exit);

      __ bind(notInt);
      __ br(Assembler::notEqual, false, Assembler::pt, notFloat);
      __ delayed()->cmp(Rflags, stos);
      // ftos
      __ ldf(FloatRegisterImpl::S, Robj, Roffset, Ftos_f);
      __ push(ftos);
      __ ba_short(exit);

      __ bind(notFloat);
      __ br(Assembler::notEqual, false, Assembler::pt, notShort);
      __ delayed()->cmp(Rflags, btos);
      // stos
      __ ldsh(Robj, Roffset, Otos_i);
      __ push(itos);
      __ ba_short(exit);

      __ bind(notShort);
      __ br(Assembler::notEqual, false, Assembler::pt, notByte);
      __ delayed()->cmp(Rflags, ctos);
      // btos
      __ ldsb(Robj, Roffset, Otos_i);
      __ push(itos);
      __ ba_short(exit);

      __ bind(notByte);
      __ br(Assembler::notEqual, false, Assembler::pt, notChar);
      __ delayed()->cmp(Rflags, ztos);
      // ctos
      __ lduh(Robj, Roffset, Otos_i);
      __ push(itos);
      __ ba_short(exit);

      __ bind(notChar);
      __ br(Assembler::notEqual, false, Assembler::pt, notBool);
      __ delayed()->nop();
      // ztos
      __ ldsb(Robj, Roffset, Otos_i);
      __ push(itos);
      __ ba_short(exit);

      __ bind(notBool);
      break;
    }

  case Bytecodes::_ldc2_w:
    {
      Label notLong, notDouble;
      __ cmp(Rflags, ltos);
      __ br(Assembler::notEqual, false, Assembler::pt, notLong);
      __ delayed()->cmp(Rflags, dtos);
      // ltos
      // load must be atomic
      __ ld_long(Robj, Roffset, Otos_l);
      __ push(ltos);
      __ ba_short(exit);

      __ bind(notLong);
      __ br(Assembler::notEqual, false, Assembler::pt, notDouble);
      __ delayed()->nop();
      // dtos
      __ ldf(FloatRegisterImpl::D, Robj, Roffset, Ftos_d);
      __ push(dtos);
      __ ba_short(exit);

      __ bind(notDouble);
      break;
    }

  default:
    ShouldNotReachHere();
  }

  __ stop("bad ldc/condy");

  __ bind(exit);
}

void TemplateTable::locals_index(Register reg, int offset) {
  __ ldub( at_bcp(offset), reg );
}

void TemplateTable::locals_index_wide(Register reg) {
  // offset is 2, not 1, because Lbcp points to wide prefix code
  __ get_2_byte_integer_at_bcp(2, G4_scratch, reg, InterpreterMacroAssembler::Unsigned);
}

void TemplateTable::iload() {
  iload_internal();
}

void TemplateTable::nofast_iload() {
  iload_internal(may_not_rewrite);
}

void TemplateTable::iload_internal(RewriteControl rc) {
  transition(vtos, itos);
  // Rewrite iload,iload  pair into fast_iload2
  //         iload,caload pair into fast_icaload
  if (RewriteFrequentPairs && rc == may_rewrite) {
    Label rewrite, done;

    // get next byte
    __ ldub(at_bcp(Bytecodes::length_for(Bytecodes::_iload)), G3_scratch);

    // if _iload, wait to rewrite to iload2.  We only want to rewrite the
    // last two iloads in a pair.  Comparing against fast_iload means that
    // the next bytecode is neither an iload or a caload, and therefore
    // an iload pair.
    __ cmp_and_br_short(G3_scratch, (int)Bytecodes::_iload, Assembler::equal, Assembler::pn, done);

    __ cmp(G3_scratch, (int)Bytecodes::_fast_iload);
    __ br(Assembler::equal, false, Assembler::pn, rewrite);
    __ delayed()->set(Bytecodes::_fast_iload2, G4_scratch);

    __ cmp(G3_scratch, (int)Bytecodes::_caload);
    __ br(Assembler::equal, false, Assembler::pn, rewrite);
    __ delayed()->set(Bytecodes::_fast_icaload, G4_scratch);

    __ set(Bytecodes::_fast_iload, G4_scratch);  // don't check again
    // rewrite
    // G4_scratch: fast bytecode
    __ bind(rewrite);
    patch_bytecode(Bytecodes::_iload, G4_scratch, G3_scratch, false);
    __ bind(done);
  }

  // Get the local value into tos
  locals_index(G3_scratch);
  __ access_local_int( G3_scratch, Otos_i );
}

void TemplateTable::fast_iload2() {
  transition(vtos, itos);
  locals_index(G3_scratch);
  __ access_local_int( G3_scratch, Otos_i );
  __ push_i();
  locals_index(G3_scratch, 3);  // get next bytecode's local index.
  __ access_local_int( G3_scratch, Otos_i );
}

void TemplateTable::fast_iload() {
  transition(vtos, itos);
  locals_index(G3_scratch);
  __ access_local_int( G3_scratch, Otos_i );
}

void TemplateTable::lload() {
  transition(vtos, ltos);
  locals_index(G3_scratch);
  __ access_local_long( G3_scratch, Otos_l );
}


void TemplateTable::fload() {
  transition(vtos, ftos);
  locals_index(G3_scratch);
  __ access_local_float( G3_scratch, Ftos_f );
}


void TemplateTable::dload() {
  transition(vtos, dtos);
  locals_index(G3_scratch);
  __ access_local_double( G3_scratch, Ftos_d );
}


void TemplateTable::aload() {
  transition(vtos, atos);
  locals_index(G3_scratch);
  __ access_local_ptr( G3_scratch, Otos_i);
}


void TemplateTable::wide_iload() {
  transition(vtos, itos);
  locals_index_wide(G3_scratch);
  __ access_local_int( G3_scratch, Otos_i );
}


void TemplateTable::wide_lload() {
  transition(vtos, ltos);
  locals_index_wide(G3_scratch);
  __ access_local_long( G3_scratch, Otos_l );
}


void TemplateTable::wide_fload() {
  transition(vtos, ftos);
  locals_index_wide(G3_scratch);
  __ access_local_float( G3_scratch, Ftos_f );
}


void TemplateTable::wide_dload() {
  transition(vtos, dtos);
  locals_index_wide(G3_scratch);
  __ access_local_double( G3_scratch, Ftos_d );
}


void TemplateTable::wide_aload() {
  transition(vtos, atos);
  locals_index_wide(G3_scratch);
  __ access_local_ptr( G3_scratch, Otos_i );
  __ verify_oop(Otos_i);
}


void TemplateTable::iaload() {
  transition(itos, itos);
  // Otos_i: index
  // tos: array
  __ index_check(O2, Otos_i, LogBytesPerInt, G3_scratch, O3);
  __ ld(O3, arrayOopDesc::base_offset_in_bytes(T_INT), Otos_i);
}


void TemplateTable::laload() {
  transition(itos, ltos);
  // Otos_i: index
  // O2: array
  __ index_check(O2, Otos_i, LogBytesPerLong, G3_scratch, O3);
  __ ld_long(O3, arrayOopDesc::base_offset_in_bytes(T_LONG), Otos_l);
}


void TemplateTable::faload() {
  transition(itos, ftos);
  // Otos_i: index
  // O2: array
  __ index_check(O2, Otos_i, LogBytesPerInt, G3_scratch, O3);
  __ ldf(FloatRegisterImpl::S, O3, arrayOopDesc::base_offset_in_bytes(T_FLOAT), Ftos_f);
}


void TemplateTable::daload() {
  transition(itos, dtos);
  // Otos_i: index
  // O2: array
  __ index_check(O2, Otos_i, LogBytesPerLong, G3_scratch, O3);
  __ ldf(FloatRegisterImpl::D, O3, arrayOopDesc::base_offset_in_bytes(T_DOUBLE), Ftos_d);
}


void TemplateTable::aaload() {
  transition(itos, atos);
  // Otos_i: index
  // tos: array
  __ index_check(O2, Otos_i, UseCompressedOops ? 2 : LogBytesPerWord, G3_scratch, O3);
  do_oop_load(_masm,
              O3,
              noreg,
              arrayOopDesc::base_offset_in_bytes(T_OBJECT),
              Otos_i,
              G3_scratch,
              IS_ARRAY);
  __ verify_oop(Otos_i);
}


void TemplateTable::baload() {
  transition(itos, itos);
  // Otos_i: index
  // tos: array
  __ index_check(O2, Otos_i, 0, G3_scratch, O3);
  __ ldsb(O3, arrayOopDesc::base_offset_in_bytes(T_BYTE), Otos_i);
}


void TemplateTable::caload() {
  transition(itos, itos);
  // Otos_i: index
  // tos: array
  __ index_check(O2, Otos_i, LogBytesPerShort, G3_scratch, O3);
  __ lduh(O3, arrayOopDesc::base_offset_in_bytes(T_CHAR), Otos_i);
}

void TemplateTable::fast_icaload() {
  transition(vtos, itos);
  // Otos_i: index
  // tos: array
  locals_index(G3_scratch);
  __ access_local_int( G3_scratch, Otos_i );
  __ index_check(O2, Otos_i, LogBytesPerShort, G3_scratch, O3);
  __ lduh(O3, arrayOopDesc::base_offset_in_bytes(T_CHAR), Otos_i);
}


void TemplateTable::saload() {
  transition(itos, itos);
  // Otos_i: index
  // tos: array
  __ index_check(O2, Otos_i, LogBytesPerShort, G3_scratch, O3);
  __ ldsh(O3, arrayOopDesc::base_offset_in_bytes(T_SHORT), Otos_i);
}


void TemplateTable::iload(int n) {
  transition(vtos, itos);
  __ ld( Llocals, Interpreter::local_offset_in_bytes(n), Otos_i );
}


void TemplateTable::lload(int n) {
  transition(vtos, ltos);
  assert(n+1 < Argument::n_register_parameters, "would need more code");
  __ load_unaligned_long(Llocals, Interpreter::local_offset_in_bytes(n+1), Otos_l);
}


void TemplateTable::fload(int n) {
  transition(vtos, ftos);
  assert(n < Argument::n_register_parameters, "would need more code");
  __ ldf( FloatRegisterImpl::S, Llocals, Interpreter::local_offset_in_bytes(n),     Ftos_f );
}


void TemplateTable::dload(int n) {
  transition(vtos, dtos);
  FloatRegister dst = Ftos_d;
  __ load_unaligned_double(Llocals, Interpreter::local_offset_in_bytes(n+1), dst);
}


void TemplateTable::aload(int n) {
  transition(vtos, atos);
  __ ld_ptr( Llocals, Interpreter::local_offset_in_bytes(n), Otos_i );
}

void TemplateTable::aload_0() {
  aload_0_internal();
}

void TemplateTable::nofast_aload_0() {
  aload_0_internal(may_not_rewrite);
}

void TemplateTable::aload_0_internal(RewriteControl rc) {
  transition(vtos, atos);

  // According to bytecode histograms, the pairs:
  //
  // _aload_0, _fast_igetfield (itos)
  // _aload_0, _fast_agetfield (atos)
  // _aload_0, _fast_fgetfield (ftos)
  //
  // occur frequently. If RewriteFrequentPairs is set, the (slow) _aload_0
  // bytecode checks the next bytecode and then rewrites the current
  // bytecode into a pair bytecode; otherwise it rewrites the current
  // bytecode into _fast_aload_0 that doesn't do the pair check anymore.
  //
  if (RewriteFrequentPairs && rc == may_rewrite) {
    Label rewrite, done;

    // get next byte
    __ ldub(at_bcp(Bytecodes::length_for(Bytecodes::_aload_0)), G3_scratch);

    // if _getfield then wait with rewrite
    __ cmp_and_br_short(G3_scratch, (int)Bytecodes::_getfield, Assembler::equal, Assembler::pn, done);

    // if _igetfield then rewrite to _fast_iaccess_0
    assert(Bytecodes::java_code(Bytecodes::_fast_iaccess_0) == Bytecodes::_aload_0, "adjust fast bytecode def");
    __ cmp(G3_scratch, (int)Bytecodes::_fast_igetfield);
    __ br(Assembler::equal, false, Assembler::pn, rewrite);
    __ delayed()->set(Bytecodes::_fast_iaccess_0, G4_scratch);

    // if _agetfield then rewrite to _fast_aaccess_0
    assert(Bytecodes::java_code(Bytecodes::_fast_aaccess_0) == Bytecodes::_aload_0, "adjust fast bytecode def");
    __ cmp(G3_scratch, (int)Bytecodes::_fast_agetfield);
    __ br(Assembler::equal, false, Assembler::pn, rewrite);
    __ delayed()->set(Bytecodes::_fast_aaccess_0, G4_scratch);

    // if _fgetfield then rewrite to _fast_faccess_0
    assert(Bytecodes::java_code(Bytecodes::_fast_faccess_0) == Bytecodes::_aload_0, "adjust fast bytecode def");
    __ cmp(G3_scratch, (int)Bytecodes::_fast_fgetfield);
    __ br(Assembler::equal, false, Assembler::pn, rewrite);
    __ delayed()->set(Bytecodes::_fast_faccess_0, G4_scratch);

    // else rewrite to _fast_aload0
    assert(Bytecodes::java_code(Bytecodes::_fast_aload_0) == Bytecodes::_aload_0, "adjust fast bytecode def");
    __ set(Bytecodes::_fast_aload_0, G4_scratch);

    // rewrite
    // G4_scratch: fast bytecode
    __ bind(rewrite);
    patch_bytecode(Bytecodes::_aload_0, G4_scratch, G3_scratch, false);
    __ bind(done);
  }

  // Do actual aload_0 (must do this after patch_bytecode which might call VM and GC might change oop).
  aload(0);
}

void TemplateTable::istore() {
  transition(itos, vtos);
  locals_index(G3_scratch);
  __ store_local_int( G3_scratch, Otos_i );
}


void TemplateTable::lstore() {
  transition(ltos, vtos);
  locals_index(G3_scratch);
  __ store_local_long( G3_scratch, Otos_l );
}


void TemplateTable::fstore() {
  transition(ftos, vtos);
  locals_index(G3_scratch);
  __ store_local_float( G3_scratch, Ftos_f );
}


void TemplateTable::dstore() {
  transition(dtos, vtos);
  locals_index(G3_scratch);
  __ store_local_double( G3_scratch, Ftos_d );
}


void TemplateTable::astore() {
  transition(vtos, vtos);
  __ load_ptr(0, Otos_i);
  __ inc(Lesp, Interpreter::stackElementSize);
  __ verify_oop_or_return_address(Otos_i, G3_scratch);
  locals_index(G3_scratch);
  __ store_local_ptr(G3_scratch, Otos_i);
}


void TemplateTable::wide_istore() {
  transition(vtos, vtos);
  __ pop_i();
  locals_index_wide(G3_scratch);
  __ store_local_int( G3_scratch, Otos_i );
}


void TemplateTable::wide_lstore() {
  transition(vtos, vtos);
  __ pop_l();
  locals_index_wide(G3_scratch);
  __ store_local_long( G3_scratch, Otos_l );
}


void TemplateTable::wide_fstore() {
  transition(vtos, vtos);
  __ pop_f();
  locals_index_wide(G3_scratch);
  __ store_local_float( G3_scratch, Ftos_f );
}


void TemplateTable::wide_dstore() {
  transition(vtos, vtos);
  __ pop_d();
  locals_index_wide(G3_scratch);
  __ store_local_double( G3_scratch, Ftos_d );
}


void TemplateTable::wide_astore() {
  transition(vtos, vtos);
  __ load_ptr(0, Otos_i);
  __ inc(Lesp, Interpreter::stackElementSize);
  __ verify_oop_or_return_address(Otos_i, G3_scratch);
  locals_index_wide(G3_scratch);
  __ store_local_ptr(G3_scratch, Otos_i);
}


void TemplateTable::iastore() {
  transition(itos, vtos);
  __ pop_i(O2); // index
  // Otos_i: val
  // O3: array
  __ index_check(O3, O2, LogBytesPerInt, G3_scratch, O2);
  __ st(Otos_i, O2, arrayOopDesc::base_offset_in_bytes(T_INT));
}


void TemplateTable::lastore() {
  transition(ltos, vtos);
  __ pop_i(O2); // index
  // Otos_l: val
  // O3: array
  __ index_check(O3, O2, LogBytesPerLong, G3_scratch, O2);
  __ st_long(Otos_l, O2, arrayOopDesc::base_offset_in_bytes(T_LONG));
}


void TemplateTable::fastore() {
  transition(ftos, vtos);
  __ pop_i(O2); // index
  // Ftos_f: val
  // O3: array
  __ index_check(O3, O2, LogBytesPerInt, G3_scratch, O2);
  __ stf(FloatRegisterImpl::S, Ftos_f, O2, arrayOopDesc::base_offset_in_bytes(T_FLOAT));
}


void TemplateTable::dastore() {
  transition(dtos, vtos);
  __ pop_i(O2); // index
  // Fos_d: val
  // O3: array
  __ index_check(O3, O2, LogBytesPerLong, G3_scratch, O2);
  __ stf(FloatRegisterImpl::D, Ftos_d, O2, arrayOopDesc::base_offset_in_bytes(T_DOUBLE));
}


void TemplateTable::aastore() {
  Label store_ok, is_null, done;
  transition(vtos, vtos);
  __ ld_ptr(Lesp, Interpreter::expr_offset_in_bytes(0), Otos_i);
  __ ld(Lesp, Interpreter::expr_offset_in_bytes(1), O2);         // get index
  __ ld_ptr(Lesp, Interpreter::expr_offset_in_bytes(2), O3);     // get array
  // Otos_i: val
  // O2: index
  // O3: array
  __ verify_oop(Otos_i);
  __ index_check_without_pop(O3, O2, UseCompressedOops ? 2 : LogBytesPerWord, G3_scratch, O1);

  // do array store check - check for NULL value first
  __ br_null_short( Otos_i, Assembler::pn, is_null );

  __ load_klass(O3, O4); // get array klass
  __ load_klass(Otos_i, O5); // get value klass

  // do fast instanceof cache test

  __ ld_ptr(O4,     in_bytes(ObjArrayKlass::element_klass_offset()),  O4);

  assert(Otos_i == O0, "just checking");

  // Otos_i:    value
  // O1:        addr - offset
  // O2:        index
  // O3:        array
  // O4:        array element klass
  // O5:        value klass

  // Address element(O1, 0, arrayOopDesc::base_offset_in_bytes(T_OBJECT));

  // Generate a fast subtype check.  Branch to store_ok if no
  // failure.  Throw if failure.
  __ gen_subtype_check( O5, O4, G3_scratch, G4_scratch, G1_scratch, store_ok );

  // Not a subtype; so must throw exception
  __ throw_if_not_x( Assembler::never, Interpreter::_throw_ArrayStoreException_entry, G3_scratch );

  // Store is OK.
  __ bind(store_ok);
  do_oop_store(_masm, O1, noreg, arrayOopDesc::base_offset_in_bytes(T_OBJECT), Otos_i, G3_scratch, IS_ARRAY);

  __ ba(done);
  __ delayed()->inc(Lesp, 3* Interpreter::stackElementSize); // adj sp (pops array, index and value)

  __ bind(is_null);
  do_oop_store(_masm, O1, noreg, arrayOopDesc::base_offset_in_bytes(T_OBJECT), G0, G4_scratch, IS_ARRAY);

  __ profile_null_seen(G3_scratch);
  __ inc(Lesp, 3* Interpreter::stackElementSize);     // adj sp (pops array, index and value)
  __ bind(done);
}


void TemplateTable::bastore() {
  transition(itos, vtos);
  __ pop_i(O2); // index
  // Otos_i: val
  // O2: index
  // O3: array
  __ index_check(O3, O2, 0, G3_scratch, O2);
  // Need to check whether array is boolean or byte
  // since both types share the bastore bytecode.
  __ load_klass(O3, G4_scratch);
  __ ld(G4_scratch, in_bytes(Klass::layout_helper_offset()), G4_scratch);
  __ set(Klass::layout_helper_boolean_diffbit(), G3_scratch);
  __ andcc(G3_scratch, G4_scratch, G0);
  Label L_skip;
  __ br(Assembler::zero, false, Assembler::pn, L_skip);
  __ delayed()->nop();
  __ and3(Otos_i, 1, Otos_i);  // if it is a T_BOOLEAN array, mask the stored value to 0/1
  __ bind(L_skip);
  __ stb(Otos_i, O2, arrayOopDesc::base_offset_in_bytes(T_BYTE));
}


void TemplateTable::castore() {
  transition(itos, vtos);
  __ pop_i(O2); // index
  // Otos_i: val
  // O3: array
  __ index_check(O3, O2, LogBytesPerShort, G3_scratch, O2);
  __ sth(Otos_i, O2, arrayOopDesc::base_offset_in_bytes(T_CHAR));
}


void TemplateTable::sastore() {
  // %%%%% Factor across platform
  castore();
}


void TemplateTable::istore(int n) {
  transition(itos, vtos);
  __ st(Otos_i, Llocals, Interpreter::local_offset_in_bytes(n));
}


void TemplateTable::lstore(int n) {
  transition(ltos, vtos);
  assert(n+1 < Argument::n_register_parameters, "only handle register cases");
  __ store_unaligned_long(Otos_l, Llocals, Interpreter::local_offset_in_bytes(n+1));

}


void TemplateTable::fstore(int n) {
  transition(ftos, vtos);
  assert(n < Argument::n_register_parameters, "only handle register cases");
  __ stf(FloatRegisterImpl::S, Ftos_f, Llocals, Interpreter::local_offset_in_bytes(n));
}


void TemplateTable::dstore(int n) {
  transition(dtos, vtos);
  FloatRegister src = Ftos_d;
  __ store_unaligned_double(src, Llocals, Interpreter::local_offset_in_bytes(n+1));
}


void TemplateTable::astore(int n) {
  transition(vtos, vtos);
  __ load_ptr(0, Otos_i);
  __ inc(Lesp, Interpreter::stackElementSize);
  __ verify_oop_or_return_address(Otos_i, G3_scratch);
  __ store_local_ptr(n, Otos_i);
}


void TemplateTable::pop() {
  transition(vtos, vtos);
  __ inc(Lesp, Interpreter::stackElementSize);
}


void TemplateTable::pop2() {
  transition(vtos, vtos);
  __ inc(Lesp, 2 * Interpreter::stackElementSize);
}


void TemplateTable::dup() {
  transition(vtos, vtos);
  // stack: ..., a
  // load a and tag
  __ load_ptr(0, Otos_i);
  __ push_ptr(Otos_i);
  // stack: ..., a, a
}


void TemplateTable::dup_x1() {
  transition(vtos, vtos);
  // stack: ..., a, b
  __ load_ptr( 1, G3_scratch);  // get a
  __ load_ptr( 0, Otos_l1);     // get b
  __ store_ptr(1, Otos_l1);     // put b
  __ store_ptr(0, G3_scratch);  // put a - like swap
  __ push_ptr(Otos_l1);         // push b
  // stack: ..., b, a, b
}


void TemplateTable::dup_x2() {
  transition(vtos, vtos);
  // stack: ..., a, b, c
  // get c and push on stack, reuse registers
  __ load_ptr( 0, G3_scratch);  // get c
  __ push_ptr(G3_scratch);      // push c with tag
  // stack: ..., a, b, c, c  (c in reg)  (Lesp - 4)
  // (stack offsets n+1 now)
  __ load_ptr( 3, Otos_l1);     // get a
  __ store_ptr(3, G3_scratch);  // put c at 3
  // stack: ..., c, b, c, c  (a in reg)
  __ load_ptr( 2, G3_scratch);  // get b
  __ store_ptr(2, Otos_l1);     // put a at 2
  // stack: ..., c, a, c, c  (b in reg)
  __ store_ptr(1, G3_scratch);  // put b at 1
  // stack: ..., c, a, b, c
}


void TemplateTable::dup2() {
  transition(vtos, vtos);
  __ load_ptr(1, G3_scratch);  // get a
  __ load_ptr(0, Otos_l1);     // get b
  __ push_ptr(G3_scratch);     // push a
  __ push_ptr(Otos_l1);        // push b
  // stack: ..., a, b, a, b
}


void TemplateTable::dup2_x1() {
  transition(vtos, vtos);
  // stack: ..., a, b, c
  __ load_ptr( 1, Lscratch);    // get b
  __ load_ptr( 2, Otos_l1);     // get a
  __ store_ptr(2, Lscratch);    // put b at a
  // stack: ..., b, b, c
  __ load_ptr( 0, G3_scratch);  // get c
  __ store_ptr(1, G3_scratch);  // put c at b
  // stack: ..., b, c, c
  __ store_ptr(0, Otos_l1);     // put a at c
  // stack: ..., b, c, a
  __ push_ptr(Lscratch);        // push b
  __ push_ptr(G3_scratch);      // push c
  // stack: ..., b, c, a, b, c
}


// The spec says that these types can be a mixture of category 1 (1 word)
// types and/or category 2 types (long and doubles)
void TemplateTable::dup2_x2() {
  transition(vtos, vtos);
  // stack: ..., a, b, c, d
  __ load_ptr( 1, Lscratch);    // get c
  __ load_ptr( 3, Otos_l1);     // get a
  __ store_ptr(3, Lscratch);    // put c at 3
  __ store_ptr(1, Otos_l1);     // put a at 1
  // stack: ..., c, b, a, d
  __ load_ptr( 2, G3_scratch);  // get b
  __ load_ptr( 0, Otos_l1);     // get d
  __ store_ptr(0, G3_scratch);  // put b at 0
  __ store_ptr(2, Otos_l1);     // put d at 2
  // stack: ..., c, d, a, b
  __ push_ptr(Lscratch);        // push c
  __ push_ptr(Otos_l1);         // push d
  // stack: ..., c, d, a, b, c, d
}


void TemplateTable::swap() {
  transition(vtos, vtos);
  // stack: ..., a, b
  __ load_ptr( 1, G3_scratch);  // get a
  __ load_ptr( 0, Otos_l1);     // get b
  __ store_ptr(0, G3_scratch);  // put b
  __ store_ptr(1, Otos_l1);     // put a
  // stack: ..., b, a
}


void TemplateTable::iop2(Operation op) {
  transition(itos, itos);
  __ pop_i(O1);
  switch (op) {
   case  add:  __  add(O1, Otos_i, Otos_i);  break;
   case  sub:  __  sub(O1, Otos_i, Otos_i);  break;
     // %%%%% Mul may not exist: better to call .mul?
   case  mul:  __ smul(O1, Otos_i, Otos_i);  break;
   case _and:  __ and3(O1, Otos_i, Otos_i);  break;
   case  _or:  __  or3(O1, Otos_i, Otos_i);  break;
   case _xor:  __ xor3(O1, Otos_i, Otos_i);  break;
   case  shl:  __  sll(O1, Otos_i, Otos_i);  break;
   case  shr:  __  sra(O1, Otos_i, Otos_i);  break;
   case ushr:  __  srl(O1, Otos_i, Otos_i);  break;
   default: ShouldNotReachHere();
  }
}


void TemplateTable::lop2(Operation op) {
  transition(ltos, ltos);
  __ pop_l(O2);
  switch (op) {
   case  add:  __  add(O2, Otos_l, Otos_l);  break;
   case  sub:  __  sub(O2, Otos_l, Otos_l);  break;
   case _and:  __ and3(O2, Otos_l, Otos_l);  break;
   case  _or:  __  or3(O2, Otos_l, Otos_l);  break;
   case _xor:  __ xor3(O2, Otos_l, Otos_l);  break;
   default: ShouldNotReachHere();
  }
}


void TemplateTable::idiv() {
  // %%%%% Later: ForSPARC/V7 call .sdiv library routine,
  // %%%%% Use ldsw...sdivx on pure V9 ABI. 64 bit safe.

  transition(itos, itos);
  __ pop_i(O1); // get 1st op

  // Y contains upper 32 bits of result, set it to 0 or all ones
  __ wry(G0);
  __ mov(~0, G3_scratch);

  __ tst(O1);
     Label neg;
  __ br(Assembler::negative, true, Assembler::pn, neg);
  __ delayed()->wry(G3_scratch);
  __ bind(neg);

     Label ok;
  __ tst(Otos_i);
  __ throw_if_not_icc( Assembler::notZero, Interpreter::_throw_ArithmeticException_entry, G3_scratch );

  const int min_int = 0x80000000;
  Label regular;
  __ cmp(Otos_i, -1);
  __ br(Assembler::notEqual, false, Assembler::pt, regular);
  // Don't put set in delay slot
  // Set will turn into multiple instructions in 64 bit mode
  __ delayed()->nop();
  __ set(min_int, G4_scratch);
  Label done;
  __ cmp(O1, G4_scratch);
  __ br(Assembler::equal, true, Assembler::pt, done);
  __ delayed()->mov(O1, Otos_i);   // (mov only executed if branch taken)

  __ bind(regular);
  __ sdiv(O1, Otos_i, Otos_i); // note: irem uses O1 after this instruction!
  __ bind(done);
}


void TemplateTable::irem() {
  transition(itos, itos);
  __ mov(Otos_i, O2); // save divisor
  idiv();                               // %%%% Hack: exploits fact that idiv leaves dividend in O1
  __ smul(Otos_i, O2, Otos_i);
  __ sub(O1, Otos_i, Otos_i);
}


void TemplateTable::lmul() {
  transition(ltos, ltos);
  __ pop_l(O2);
  __ mulx(Otos_l, O2, Otos_l);

}


void TemplateTable::ldiv() {
  transition(ltos, ltos);

  // check for zero
  __ pop_l(O2);
  __ tst(Otos_l);
  __ throw_if_not_xcc( Assembler::notZero, Interpreter::_throw_ArithmeticException_entry, G3_scratch);
  __ sdivx(O2, Otos_l, Otos_l);
}


void TemplateTable::lrem() {
  transition(ltos, ltos);

  // check for zero
  __ pop_l(O2);
  __ tst(Otos_l);
  __ throw_if_not_xcc( Assembler::notZero, Interpreter::_throw_ArithmeticException_entry, G3_scratch);
  __ sdivx(O2, Otos_l, Otos_l2);
  __ mulx (Otos_l2, Otos_l, Otos_l2);
  __ sub  (O2, Otos_l2, Otos_l);
}


void TemplateTable::lshl() {
  transition(itos, ltos); // %%%% could optimize, fill delay slot or opt for ultra

  __ pop_l(O2);                          // shift value in O2, O3
  __ sllx(O2, Otos_i, Otos_l);
}


void TemplateTable::lshr() {
  transition(itos, ltos); // %%%% see lshl comment

  __ pop_l(O2);                          // shift value in O2, O3
  __ srax(O2, Otos_i, Otos_l);
}



void TemplateTable::lushr() {
  transition(itos, ltos); // %%%% see lshl comment

  __ pop_l(O2);                          // shift value in O2, O3
  __ srlx(O2, Otos_i, Otos_l);
}


void TemplateTable::fop2(Operation op) {
  transition(ftos, ftos);
  switch (op) {
   case  add:  __  pop_f(F4); __ fadd(FloatRegisterImpl::S, F4, Ftos_f, Ftos_f);  break;
   case  sub:  __  pop_f(F4); __ fsub(FloatRegisterImpl::S, F4, Ftos_f, Ftos_f);  break;
   case  mul:  __  pop_f(F4); __ fmul(FloatRegisterImpl::S, F4, Ftos_f, Ftos_f);  break;
   case  div:  __  pop_f(F4); __ fdiv(FloatRegisterImpl::S, F4, Ftos_f, Ftos_f);  break;
   case  rem:
     assert(Ftos_f == F0, "just checking");
     // LP64 calling conventions use F1, F3 for passing 2 floats
     __ pop_f(F1);
     __ fmov(FloatRegisterImpl::S, Ftos_f, F3);
     __ call_VM_leaf(Lscratch, CAST_FROM_FN_PTR(address, SharedRuntime::frem));
     assert( Ftos_f == F0, "fix this code" );
     break;

   default: ShouldNotReachHere();
  }
}


void TemplateTable::dop2(Operation op) {
  transition(dtos, dtos);
  switch (op) {
   case  add:  __  pop_d(F4); __ fadd(FloatRegisterImpl::D, F4, Ftos_d, Ftos_d);  break;
   case  sub:  __  pop_d(F4); __ fsub(FloatRegisterImpl::D, F4, Ftos_d, Ftos_d);  break;
   case  mul:  __  pop_d(F4); __ fmul(FloatRegisterImpl::D, F4, Ftos_d, Ftos_d);  break;
   case  div:  __  pop_d(F4); __ fdiv(FloatRegisterImpl::D, F4, Ftos_d, Ftos_d);  break;
   case  rem:
     // Pass arguments in D0, D2
     __ fmov(FloatRegisterImpl::D, Ftos_f, F2 );
     __ pop_d( F0 );
     __ call_VM_leaf(Lscratch, CAST_FROM_FN_PTR(address, SharedRuntime::drem));
     assert( Ftos_d == F0, "fix this code" );
     break;

   default: ShouldNotReachHere();
  }
}


void TemplateTable::ineg() {
  transition(itos, itos);
  __ neg(Otos_i);
}


void TemplateTable::lneg() {
  transition(ltos, ltos);
  __ sub(G0, Otos_l, Otos_l);
}


void TemplateTable::fneg() {
  transition(ftos, ftos);
  __ fneg(FloatRegisterImpl::S, Ftos_f, Ftos_f);
}


void TemplateTable::dneg() {
  transition(dtos, dtos);
  __ fneg(FloatRegisterImpl::D, Ftos_f, Ftos_f);
}


void TemplateTable::iinc() {
  transition(vtos, vtos);
  locals_index(G3_scratch);
  __ ldsb(Lbcp, 2, O2);  // load constant
  __ access_local_int(G3_scratch, Otos_i);
  __ add(Otos_i, O2, Otos_i);
  __ st(Otos_i, G3_scratch, 0);    // access_local_int puts E.A. in G3_scratch
}


void TemplateTable::wide_iinc() {
  transition(vtos, vtos);
  locals_index_wide(G3_scratch);
  __ get_2_byte_integer_at_bcp( 4,  O2, O3, InterpreterMacroAssembler::Signed);
  __ access_local_int(G3_scratch, Otos_i);
  __ add(Otos_i, O3, Otos_i);
  __ st(Otos_i, G3_scratch, 0);    // access_local_int puts E.A. in G3_scratch
}


void TemplateTable::convert() {
// %%%%% Factor this first part accross platforms
  #ifdef ASSERT
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
  #endif


  // Conversion
  Label done;
  switch (bytecode()) {
   case Bytecodes::_i2l:
    // Sign extend the 32 bits
    __ sra ( Otos_i, 0, Otos_l );
    break;

   case Bytecodes::_i2f:
    __ st(Otos_i, __ d_tmp );
    __ ldf(FloatRegisterImpl::S,  __ d_tmp, F0);
    __ fitof(FloatRegisterImpl::S, F0, Ftos_f);
    break;

   case Bytecodes::_i2d:
    __ st(Otos_i, __ d_tmp);
    __ ldf(FloatRegisterImpl::S,  __ d_tmp, F0);
    __ fitof(FloatRegisterImpl::D, F0, Ftos_f);
    break;

   case Bytecodes::_i2b:
    __ sll(Otos_i, 24, Otos_i);
    __ sra(Otos_i, 24, Otos_i);
    break;

   case Bytecodes::_i2c:
    __ sll(Otos_i, 16, Otos_i);
    __ srl(Otos_i, 16, Otos_i);
    break;

   case Bytecodes::_i2s:
    __ sll(Otos_i, 16, Otos_i);
    __ sra(Otos_i, 16, Otos_i);
    break;

   case Bytecodes::_l2i:
    // Sign-extend into the high 32 bits
    __ sra(Otos_l, 0, Otos_i);
    break;

   case Bytecodes::_l2f:
   case Bytecodes::_l2d:
    __ st_long(Otos_l, __ d_tmp);
    __ ldf(FloatRegisterImpl::D, __ d_tmp, Ftos_d);

    if (bytecode() == Bytecodes::_l2f) {
      __ fxtof(FloatRegisterImpl::S, Ftos_d, Ftos_f);
    } else {
      __ fxtof(FloatRegisterImpl::D, Ftos_d, Ftos_d);
    }
    break;

  case Bytecodes::_f2i:  {
      Label isNaN;
      // result must be 0 if value is NaN; test by comparing value to itself
      __ fcmp(FloatRegisterImpl::S, Assembler::fcc0, Ftos_f, Ftos_f);
      __ fb(Assembler::f_unordered, true, Assembler::pn, isNaN);
      __ delayed()->clr(Otos_i);                                     // NaN
      __ ftoi(FloatRegisterImpl::S, Ftos_f, F30);
      __ stf(FloatRegisterImpl::S, F30, __ d_tmp);
      __ ld(__ d_tmp, Otos_i);
      __ bind(isNaN);
    }
    break;

   case Bytecodes::_f2l:
    // must uncache tos
    __ push_f();
    __ pop_f(F1);
    __ call_VM_leaf(Lscratch, CAST_FROM_FN_PTR(address, SharedRuntime::f2l));
    break;

   case Bytecodes::_f2d:
    __ ftof( FloatRegisterImpl::S, FloatRegisterImpl::D, Ftos_f, Ftos_f);
    break;

   case Bytecodes::_d2i:
   case Bytecodes::_d2l:
    // must uncache tos
    __ push_d();
    // LP64 calling conventions pass first double arg in D0
    __ pop_d( Ftos_d );
    __ call_VM_leaf(Lscratch,
        bytecode() == Bytecodes::_d2i
          ? CAST_FROM_FN_PTR(address, SharedRuntime::d2i)
          : CAST_FROM_FN_PTR(address, SharedRuntime::d2l));
    break;

    case Bytecodes::_d2f:
      __ ftof( FloatRegisterImpl::D, FloatRegisterImpl::S, Ftos_d, Ftos_f);
    break;

    default: ShouldNotReachHere();
  }
  __ bind(done);
}


void TemplateTable::lcmp() {
  transition(ltos, itos);

  __ pop_l(O1); // pop off value 1, value 2 is in O0
  __ lcmp( O1, Otos_l, Otos_i );
}


void TemplateTable::float_cmp(bool is_float, int unordered_result) {

  if (is_float) __ pop_f(F2);
  else          __ pop_d(F2);

  assert(Ftos_f == F0  &&  Ftos_d == F0,  "alias checking:");

  __ float_cmp( is_float, unordered_result, F2, F0, Otos_i );
}

void TemplateTable::branch(bool is_jsr, bool is_wide) {
  // Note: on SPARC, we use InterpreterMacroAssembler::if_cmp also.
  __ verify_thread();

  const Register O2_bumped_count = O2;
  __ profile_taken_branch(G3_scratch, O2_bumped_count);

  // get (wide) offset to O1_disp
  const Register O1_disp = O1;
  if (is_wide)  __ get_4_byte_integer_at_bcp( 1,  G4_scratch, O1_disp,                                    InterpreterMacroAssembler::set_CC);
  else          __ get_2_byte_integer_at_bcp( 1,  G4_scratch, O1_disp, InterpreterMacroAssembler::Signed, InterpreterMacroAssembler::set_CC);

  // Handle all the JSR stuff here, then exit.
  // It's much shorter and cleaner than intermingling with the
  // non-JSR normal-branch stuff occurring below.
  if( is_jsr ) {
    // compute return address as bci in Otos_i
    __ ld_ptr(Lmethod, Method::const_offset(), G3_scratch);
    __ sub(Lbcp, G3_scratch, G3_scratch);
    __ sub(G3_scratch, in_bytes(ConstMethod::codes_offset()) - (is_wide ? 5 : 3), Otos_i);

    // Bump Lbcp to target of JSR
    __ add(Lbcp, O1_disp, Lbcp);
    // Push returnAddress for "ret" on stack
    __ push_ptr(Otos_i);
    // And away we go!
    __ dispatch_next(vtos, 0, true);
    return;
  }

  // Normal (non-jsr) branch handling

  // Save the current Lbcp
  const Register l_cur_bcp = Lscratch;
  __ mov( Lbcp, l_cur_bcp );

  bool increment_invocation_counter_for_backward_branches = UseCompiler && UseLoopCounter;
  if ( increment_invocation_counter_for_backward_branches ) {
    Label Lforward;
    // check branch direction
    __ br( Assembler::positive, false,  Assembler::pn, Lforward );
    // Bump bytecode pointer by displacement (take the branch)
    __ delayed()->add( O1_disp, Lbcp, Lbcp );     // add to bc addr

    const Register G3_method_counters = G3_scratch;
    __ get_method_counters(Lmethod, G3_method_counters, Lforward);

    if (TieredCompilation) {
      Label Lno_mdo, Loverflow;
      int increment = InvocationCounter::count_increment;
      if (ProfileInterpreter) {
        // If no method data exists, go to profile_continue.
        __ ld_ptr(Lmethod, Method::method_data_offset(), G4_scratch);
        __ br_null_short(G4_scratch, Assembler::pn, Lno_mdo);

        // Increment backedge counter in the MDO
        Address mdo_backedge_counter(G4_scratch, in_bytes(MethodData::backedge_counter_offset()) +
                                                 in_bytes(InvocationCounter::counter_offset()));
        Address mask(G4_scratch, in_bytes(MethodData::backedge_mask_offset()));
        __ increment_mask_and_jump(mdo_backedge_counter, increment, mask, G3_scratch, O0,
                                   (UseOnStackReplacement ? Assembler::notZero : Assembler::always), &Lforward);
        __ ba_short(Loverflow);
      }

      // If there's no MDO, increment counter in MethodCounters*
      __ bind(Lno_mdo);
      Address backedge_counter(G3_method_counters,
              in_bytes(MethodCounters::backedge_counter_offset()) +
              in_bytes(InvocationCounter::counter_offset()));
      Address mask(G3_method_counters, in_bytes(MethodCounters::backedge_mask_offset()));
      __ increment_mask_and_jump(backedge_counter, increment, mask, G4_scratch, O0,
                                 (UseOnStackReplacement ? Assembler::notZero : Assembler::always), &Lforward);
      __ bind(Loverflow);

      // notify point for loop, pass branch bytecode
      __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::frequency_counter_overflow), l_cur_bcp);

      // Was an OSR adapter generated?
      // O0 = osr nmethod
      __ br_null_short(O0, Assembler::pn, Lforward);

      // Has the nmethod been invalidated already?
      __ ldub(O0, nmethod::state_offset(), O2);
      __ cmp_and_br_short(O2, nmethod::in_use, Assembler::notEqual, Assembler::pn, Lforward);

      // migrate the interpreter frame off of the stack

      __ mov(G2_thread, L7);
      // save nmethod
      __ mov(O0, L6);
      __ set_last_Java_frame(SP, noreg);
      __ call_VM_leaf(noreg, CAST_FROM_FN_PTR(address, SharedRuntime::OSR_migration_begin), L7);
      __ reset_last_Java_frame();
      __ mov(L7, G2_thread);

      // move OSR nmethod to I1
      __ mov(L6, I1);

      // OSR buffer to I0
      __ mov(O0, I0);

      // remove the interpreter frame
      __ restore(I5_savedSP, 0, SP);

      // Jump to the osr code.
      __ ld_ptr(O1, nmethod::osr_entry_point_offset(), O2);
      __ jmp(O2, G0);
      __ delayed()->nop();

    } else { // not TieredCompilation
      // Update Backedge branch separately from invocations
      const Register G4_invoke_ctr = G4;
      __ increment_backedge_counter(G3_method_counters, G4_invoke_ctr, G1_scratch);
      if (ProfileInterpreter) {
        __ test_invocation_counter_for_mdp(G4_invoke_ctr, G3_method_counters, G1_scratch, Lforward);
        if (UseOnStackReplacement) {

          __ test_backedge_count_for_osr(O2_bumped_count, G3_method_counters, l_cur_bcp, G1_scratch);
        }
      } else {
        if (UseOnStackReplacement) {
          __ test_backedge_count_for_osr(G4_invoke_ctr, G3_method_counters, l_cur_bcp, G1_scratch);
        }
      }
    }

    __ bind(Lforward);
  } else
    // Bump bytecode pointer by displacement (take the branch)
    __ add( O1_disp, Lbcp, Lbcp );// add to bc addr

  // continue with bytecode @ target
  // %%%%% Like Intel, could speed things up by moving bytecode fetch to code above,
  // %%%%% and changing dispatch_next to dispatch_only
  __ dispatch_next(vtos, 0, true);
}


// Note Condition in argument is TemplateTable::Condition
// arg scope is within class scope

void TemplateTable::if_0cmp(Condition cc) {
  // no pointers, integer only!
  transition(itos, vtos);
  // assume branch is more often taken than not (loops use backward branches)
  __ cmp( Otos_i, 0);
  __ if_cmp(ccNot(cc), false);
}


void TemplateTable::if_icmp(Condition cc) {
  transition(itos, vtos);
  __ pop_i(O1);
  __ cmp(O1, Otos_i);
  __ if_cmp(ccNot(cc), false);
}


void TemplateTable::if_nullcmp(Condition cc) {
  transition(atos, vtos);
  __ tst(Otos_i);
  __ if_cmp(ccNot(cc), true);
}


void TemplateTable::if_acmp(Condition cc) {
  transition(atos, vtos);
  __ pop_ptr(O1);
  __ verify_oop(O1);
  __ verify_oop(Otos_i);
  __ cmp(O1, Otos_i);
  __ if_cmp(ccNot(cc), true);
}



void TemplateTable::ret() {
  transition(vtos, vtos);
  locals_index(G3_scratch);
  __ access_local_returnAddress(G3_scratch, Otos_i);
  // Otos_i contains the bci, compute the bcp from that

#ifdef ASSERT
  // jsr result was labeled as an 'itos' not an 'atos' because we cannot GC
  // the result.  The return address (really a BCI) was stored with an
  // 'astore' because JVM specs claim it's a pointer-sized thing.  Hence in
  // the 64-bit build the 32-bit BCI is actually in the low bits of a 64-bit
  // loaded value.
  { Label zzz ;
     __ set (65536, G3_scratch) ;
     __ cmp (Otos_i, G3_scratch) ;
     __ bp( Assembler::lessEqualUnsigned, false, Assembler::xcc, Assembler::pn, zzz);
     __ delayed()->nop();
     __ stop("BCI is in the wrong register half?");
     __ bind (zzz) ;
  }
#endif

  __ profile_ret(vtos, Otos_i, G4_scratch);

  __ ld_ptr(Lmethod, Method::const_offset(), G3_scratch);
  __ add(G3_scratch, Otos_i, G3_scratch);
  __ add(G3_scratch, in_bytes(ConstMethod::codes_offset()), Lbcp);
  __ dispatch_next(vtos, 0, true);
}


void TemplateTable::wide_ret() {
  transition(vtos, vtos);
  locals_index_wide(G3_scratch);
  __ access_local_returnAddress(G3_scratch, Otos_i);
  // Otos_i contains the bci, compute the bcp from that

  __ profile_ret(vtos, Otos_i, G4_scratch);

  __ ld_ptr(Lmethod, Method::const_offset(), G3_scratch);
  __ add(G3_scratch, Otos_i, G3_scratch);
  __ add(G3_scratch, in_bytes(ConstMethod::codes_offset()), Lbcp);
  __ dispatch_next(vtos, 0, true);
}


void TemplateTable::tableswitch() {
  transition(itos, vtos);
  Label default_case, continue_execution;

  // align bcp
  __ add(Lbcp, BytesPerInt, O1);
  __ and3(O1, -BytesPerInt, O1);
  // load lo, hi
  __ ld(O1, 1 * BytesPerInt, O2);       // Low Byte
  __ ld(O1, 2 * BytesPerInt, O3);       // High Byte
  // Sign extend the 32 bits
  __ sra ( Otos_i, 0, Otos_i );

  // check against lo & hi
  __ cmp( Otos_i, O2);
  __ br( Assembler::less, false, Assembler::pn, default_case);
  __ delayed()->cmp( Otos_i, O3 );
  __ br( Assembler::greater, false, Assembler::pn, default_case);
  // lookup dispatch offset
  __ delayed()->sub(Otos_i, O2, O2);
  __ profile_switch_case(O2, O3, G3_scratch, G4_scratch);
  __ sll(O2, LogBytesPerInt, O2);
  __ add(O2, 3 * BytesPerInt, O2);
  __ ba(continue_execution);
  __ delayed()->ld(O1, O2, O2);
  // handle default
  __ bind(default_case);
  __ profile_switch_default(O3);
  __ ld(O1, 0, O2); // get default offset
  // continue execution
  __ bind(continue_execution);
  __ add(Lbcp, O2, Lbcp);
  __ dispatch_next(vtos, 0, true);
}


void TemplateTable::lookupswitch() {
  transition(itos, itos);
  __ stop("lookupswitch bytecode should have been rewritten");
}

void TemplateTable::fast_linearswitch() {
  transition(itos, vtos);
    Label loop_entry, loop, found, continue_execution;
  // align bcp
  __ add(Lbcp, BytesPerInt, O1);
  __ and3(O1, -BytesPerInt, O1);
 // set counter
  __ ld(O1, BytesPerInt, O2);
  __ sll(O2, LogBytesPerInt + 1, O2); // in word-pairs
  __ add(O1, 2 * BytesPerInt, O3); // set first pair addr
  __ ba(loop_entry);
  __ delayed()->add(O3, O2, O2); // counter now points past last pair

  // table search
  __ bind(loop);
  __ cmp(O4, Otos_i);
  __ br(Assembler::equal, true, Assembler::pn, found);
  __ delayed()->ld(O3, BytesPerInt, O4); // offset -> O4
  __ inc(O3, 2 * BytesPerInt);

  __ bind(loop_entry);
  __ cmp(O2, O3);
  __ brx(Assembler::greaterUnsigned, true, Assembler::pt, loop);
  __ delayed()->ld(O3, 0, O4);

  // default case
  __ ld(O1, 0, O4); // get default offset
  if (ProfileInterpreter) {
    __ profile_switch_default(O3);
    __ ba_short(continue_execution);
  }

  // entry found -> get offset
  __ bind(found);
  if (ProfileInterpreter) {
    __ sub(O3, O1, O3);
    __ sub(O3, 2*BytesPerInt, O3);
    __ srl(O3, LogBytesPerInt + 1, O3); // in word-pairs
    __ profile_switch_case(O3, O1, O2, G3_scratch);

    __ bind(continue_execution);
  }
  __ add(Lbcp, O4, Lbcp);
  __ dispatch_next(vtos, 0, true);
}


void TemplateTable::fast_binaryswitch() {
  transition(itos, vtos);
  // Implementation using the following core algorithm: (copied from Intel)
  //
  // int binary_search(int key, LookupswitchPair* array, int n) {
  //   // Binary search according to "Methodik des Programmierens" by
  //   // Edsger W. Dijkstra and W.H.J. Feijen, Addison Wesley Germany 1985.
  //   int i = 0;
  //   int j = n;
  //   while (i+1 < j) {
  //     // invariant P: 0 <= i < j <= n and (a[i] <= key < a[j] or Q)
  //     // with      Q: for all i: 0 <= i < n: key < a[i]
  //     // where a stands for the array and assuming that the (inexisting)
  //     // element a[n] is infinitely big.
  //     int h = (i + j) >> 1;
  //     // i < h < j
  //     if (key < array[h].fast_match()) {
  //       j = h;
  //     } else {
  //       i = h;
  //     }
  //   }
  //   // R: a[i] <= key < a[i+1] or Q
  //   // (i.e., if key is within array, i is the correct index)
  //   return i;
  // }

  // register allocation
  assert(Otos_i == O0, "alias checking");
  const Register Rkey     = Otos_i;                    // already set (tosca)
  const Register Rarray   = O1;
  const Register Ri       = O2;
  const Register Rj       = O3;
  const Register Rh       = O4;
  const Register Rscratch = O5;

  const int log_entry_size = 3;
  const int entry_size = 1 << log_entry_size;

  Label found;
  // Find Array start
  __ add(Lbcp, 3 * BytesPerInt, Rarray);
  __ and3(Rarray, -BytesPerInt, Rarray);
  // initialize i & j (in delay slot)
  __ clr( Ri );

  // and start
  Label entry;
  __ ba(entry);
  __ delayed()->ld( Rarray, -BytesPerInt, Rj);
  // (Rj is already in the native byte-ordering.)

  // binary search loop
  { Label loop;
    __ bind( loop );
    // int h = (i + j) >> 1;
    __ sra( Rh, 1, Rh );
    // if (key < array[h].fast_match()) {
    //   j = h;
    // } else {
    //   i = h;
    // }
    __ sll( Rh, log_entry_size, Rscratch );
    __ ld( Rarray, Rscratch, Rscratch );
    // (Rscratch is already in the native byte-ordering.)
    __ cmp( Rkey, Rscratch );
    __ movcc( Assembler::less,         false, Assembler::icc, Rh, Rj );  // j = h if (key <  array[h].fast_match())
    __ movcc( Assembler::greaterEqual, false, Assembler::icc, Rh, Ri );  // i = h if (key >= array[h].fast_match())

    // while (i+1 < j)
    __ bind( entry );
    __ add( Ri, 1, Rscratch );
    __ cmp(Rscratch, Rj);
    __ br( Assembler::less, true, Assembler::pt, loop );
    __ delayed()->add( Ri, Rj, Rh ); // start h = i + j  >> 1;
  }

  // end of binary search, result index is i (must check again!)
  Label default_case;
  Label continue_execution;
  if (ProfileInterpreter) {
    __ mov( Ri, Rh );              // Save index in i for profiling
  }
  __ sll( Ri, log_entry_size, Ri );
  __ ld( Rarray, Ri, Rscratch );
  // (Rscratch is already in the native byte-ordering.)
  __ cmp( Rkey, Rscratch );
  __ br( Assembler::notEqual, true, Assembler::pn, default_case );
  __ delayed()->ld( Rarray, -2 * BytesPerInt, Rj ); // load default offset -> j

  // entry found -> j = offset
  __ inc( Ri, BytesPerInt );
  __ profile_switch_case(Rh, Rj, Rscratch, Rkey);
  __ ld( Rarray, Ri, Rj );
  // (Rj is already in the native byte-ordering.)

  if (ProfileInterpreter) {
    __ ba_short(continue_execution);
  }

  __ bind(default_case); // fall through (if not profiling)
  __ profile_switch_default(Ri);

  __ bind(continue_execution);
  __ add( Lbcp, Rj, Lbcp );
  __ dispatch_next(vtos, 0, true);
}


void TemplateTable::_return(TosState state) {
  transition(state, state);
  assert(_desc->calls_vm(), "inconsistent calls_vm information");

  if (_desc->bytecode() == Bytecodes::_return_register_finalizer) {
    assert(state == vtos, "only valid state");
    __ mov(G0, G3_scratch);
    __ access_local_ptr(G3_scratch, Otos_i);
    __ load_klass(Otos_i, O2);
    __ set(JVM_ACC_HAS_FINALIZER, G3);
    __ ld(O2, in_bytes(Klass::access_flags_offset()), O2);
    __ andcc(G3, O2, G0);
    Label skip_register_finalizer;
    __ br(Assembler::zero, false, Assembler::pn, skip_register_finalizer);
    __ delayed()->nop();

    // Call out to do finalizer registration
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::register_finalizer), Otos_i);

    __ bind(skip_register_finalizer);
  }

  if (SafepointMechanism::uses_thread_local_poll() && _desc->bytecode() != Bytecodes::_return_register_finalizer) {
    Label no_safepoint;
    __ ldx(Address(G2_thread, Thread::polling_page_offset()), G3_scratch, 0);
    __ btst(SafepointMechanism::poll_bit(), G3_scratch);
    __ br(Assembler::zero, false, Assembler::pt, no_safepoint);
    __ delayed()->nop();
    __ push(state);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::at_safepoint));
    __ pop(state);
    __ bind(no_safepoint);
  }

  // Narrow result if state is itos but result type is smaller.
  // Need to narrow in the return bytecode rather than in generate_return_entry
  // since compiled code callers expect the result to already be narrowed.
  if (state == itos) {
    __ narrow(Otos_i);
  }
  __ remove_activation(state, /* throw_monitor_exception */ true);

  // The caller's SP was adjusted upon method entry to accomodate
  // the callee's non-argument locals. Undo that adjustment.
  __ ret();                             // return to caller
  __ delayed()->restore(I5_savedSP, G0, SP);
}


// ----------------------------------------------------------------------------
// Volatile variables demand their effects be made known to all CPU's in
// order.  Store buffers on most chips allow reads & writes to reorder; the
// JMM's ReadAfterWrite.java test fails in -Xint mode without some kind of
// memory barrier (i.e., it's not sufficient that the interpreter does not
// reorder volatile references, the hardware also must not reorder them).
//
// According to the new Java Memory Model (JMM):
// (1) All volatiles are serialized wrt to each other.
// ALSO reads & writes act as aquire & release, so:
// (2) A read cannot let unrelated NON-volatile memory refs that happen after
// the read float up to before the read.  It's OK for non-volatile memory refs
// that happen before the volatile read to float down below it.
// (3) Similar a volatile write cannot let unrelated NON-volatile memory refs
// that happen BEFORE the write float down to after the write.  It's OK for
// non-volatile memory refs that happen after the volatile write to float up
// before it.
//
// We only put in barriers around volatile refs (they are expensive), not
// _between_ memory refs (that would require us to track the flavor of the
// previous memory refs).  Requirements (2) and (3) require some barriers
// before volatile stores and after volatile loads.  These nearly cover
// requirement (1) but miss the volatile-store-volatile-load case.  This final
// case is placed after volatile-stores although it could just as well go
// before volatile-loads.
void TemplateTable::volatile_barrier(Assembler::Membar_mask_bits order_constraint) {
  // Helper function to insert a is-volatile test and memory barrier
  // All current sparc implementations run in TSO, needing only StoreLoad
  if ((order_constraint & Assembler::StoreLoad) == 0) return;
  __ membar( order_constraint );
}

// ----------------------------------------------------------------------------
void TemplateTable::resolve_cache_and_index(int byte_no,
                                            Register Rcache,
                                            Register index,
                                            size_t index_size) {
  // Depends on cpCacheOop layout!

  Label resolved;
  Bytecodes::Code code = bytecode();
  switch (code) {
  case Bytecodes::_nofast_getfield: code = Bytecodes::_getfield; break;
  case Bytecodes::_nofast_putfield: code = Bytecodes::_putfield; break;
  }

  assert(byte_no == f1_byte || byte_no == f2_byte, "byte_no out of range");
  __ get_cache_and_index_and_bytecode_at_bcp(Rcache, index, Lbyte_code, byte_no, 1, index_size);
  __ cmp(Lbyte_code, code);  // have we resolved this bytecode?
  __ br(Assembler::equal, false, Assembler::pt, resolved);
  __ delayed()->set(code, O1);

  address entry = CAST_FROM_FN_PTR(address, InterpreterRuntime::resolve_from_cache);
  // first time invocation - must resolve first
  __ call_VM(noreg, entry, O1);
  // Update registers with resolved info
  __ get_cache_and_index_at_bcp(Rcache, index, 1, index_size);
  __ bind(resolved);
}

void TemplateTable::load_invoke_cp_cache_entry(int byte_no,
                                               Register method,
                                               Register itable_index,
                                               Register flags,
                                               bool is_invokevirtual,
                                               bool is_invokevfinal,
                                               bool is_invokedynamic) {
  // Uses both G3_scratch and G4_scratch
  Register cache = G3_scratch;
  Register index = G4_scratch;
  assert_different_registers(cache, method, itable_index);

  // determine constant pool cache field offsets
  assert(is_invokevirtual == (byte_no == f2_byte), "is_invokevirtual flag redundant");
  const int method_offset = in_bytes(
      ConstantPoolCache::base_offset() +
      ((byte_no == f2_byte)
       ? ConstantPoolCacheEntry::f2_offset()
       : ConstantPoolCacheEntry::f1_offset()
      )
    );
  const int flags_offset = in_bytes(ConstantPoolCache::base_offset() +
                                    ConstantPoolCacheEntry::flags_offset());
  // access constant pool cache fields
  const int index_offset = in_bytes(ConstantPoolCache::base_offset() +
                                    ConstantPoolCacheEntry::f2_offset());

  if (is_invokevfinal) {
    __ get_cache_and_index_at_bcp(cache, index, 1);
    __ ld_ptr(Address(cache, method_offset), method);
  } else {
    size_t index_size = (is_invokedynamic ? sizeof(u4) : sizeof(u2));
    resolve_cache_and_index(byte_no, cache, index, index_size);
    __ ld_ptr(Address(cache, method_offset), method);
  }

  if (itable_index != noreg) {
    // pick up itable or appendix index from f2 also:
    __ ld_ptr(Address(cache, index_offset), itable_index);
  }
  __ ld_ptr(Address(cache, flags_offset), flags);
}

// The Rcache register must be set before call
void TemplateTable::load_field_cp_cache_entry(Register Robj,
                                              Register Rcache,
                                              Register index,
                                              Register Roffset,
                                              Register Rflags,
                                              bool is_static) {
  assert_different_registers(Rcache, Rflags, Roffset, Lscratch);

  ByteSize cp_base_offset = ConstantPoolCache::base_offset();

  __ ld_ptr(Rcache, cp_base_offset + ConstantPoolCacheEntry::flags_offset(), Rflags);
  __ ld_ptr(Rcache, cp_base_offset + ConstantPoolCacheEntry::f2_offset(), Roffset);
  if (is_static) {
    __ ld_ptr(Rcache, cp_base_offset + ConstantPoolCacheEntry::f1_offset(), Robj);
    const int mirror_offset = in_bytes(Klass::java_mirror_offset());
    __ ld_ptr( Robj, mirror_offset, Robj);
    __ resolve_oop_handle(Robj, Lscratch);
  }
}

// The registers Rcache and index expected to be set before call.
// Correct values of the Rcache and index registers are preserved.
void TemplateTable::jvmti_post_field_access(Register Rcache,
                                            Register index,
                                            bool is_static,
                                            bool has_tos) {
  ByteSize cp_base_offset = ConstantPoolCache::base_offset();

  if (JvmtiExport::can_post_field_access()) {
    // Check to see if a field access watch has been set before we take
    // the time to call into the VM.
    Label Label1;
    assert_different_registers(Rcache, index, G1_scratch);
    AddressLiteral get_field_access_count_addr(JvmtiExport::get_field_access_count_addr());
    __ load_contents(get_field_access_count_addr, G1_scratch);
    __ cmp_and_br_short(G1_scratch, 0, Assembler::equal, Assembler::pt, Label1);

    __ add(Rcache, in_bytes(cp_base_offset), Rcache);

    if (is_static) {
      __ clr(Otos_i);
    } else {
      if (has_tos) {
      // save object pointer before call_VM() clobbers it
        __ push_ptr(Otos_i);  // put object on tos where GC wants it.
      } else {
        // Load top of stack (do not pop the value off the stack);
        __ ld_ptr(Lesp, Interpreter::expr_offset_in_bytes(0), Otos_i);
      }
      __ verify_oop(Otos_i);
    }
    // Otos_i: object pointer or NULL if static
    // Rcache: cache entry pointer
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::post_field_access),
               Otos_i, Rcache);
    if (!is_static && has_tos) {
      __ pop_ptr(Otos_i);  // restore object pointer
      __ verify_oop(Otos_i);
    }
    __ get_cache_and_index_at_bcp(Rcache, index, 1);
    __ bind(Label1);
  }
}

void TemplateTable::getfield_or_static(int byte_no, bool is_static, RewriteControl rc) {
  transition(vtos, vtos);

  Register Rcache = G3_scratch;
  Register index  = G4_scratch;
  Register Rclass = Rcache;
  Register Roffset= G4_scratch;
  Register Rflags = G1_scratch;
  ByteSize cp_base_offset = ConstantPoolCache::base_offset();

  resolve_cache_and_index(byte_no, Rcache, index, sizeof(u2));
  jvmti_post_field_access(Rcache, index, is_static, false);
  load_field_cp_cache_entry(Rclass, Rcache, index, Roffset, Rflags, is_static);

  if (!is_static) {
    pop_and_check_object(Rclass);
  } else {
    __ verify_oop(Rclass);
  }

  Label exit;

  Assembler::Membar_mask_bits membar_bits =
    Assembler::Membar_mask_bits(Assembler::LoadLoad | Assembler::LoadStore);

  if (__ membar_has_effect(membar_bits)) {
    // Get volatile flag
    __ set((1 << ConstantPoolCacheEntry::is_volatile_shift), Lscratch);
    __ and3(Rflags, Lscratch, Lscratch);
  }

  Label checkVolatile;

  // compute field type
  Label notByte, notBool, notInt, notShort, notChar, notLong, notFloat, notObj;
  __ srl(Rflags, ConstantPoolCacheEntry::tos_state_shift, Rflags);
  // Make sure we don't need to mask Rflags after the above shift
  ConstantPoolCacheEntry::verify_tos_state_shift();

  // Check atos before itos for getstatic, more likely (in Queens at least)
  __ cmp(Rflags, atos);
  __ br(Assembler::notEqual, false, Assembler::pt, notObj);
  __ delayed() ->cmp(Rflags, itos);

  // atos
  do_oop_load(_masm, Rclass, Roffset, 0, Otos_i, noreg);
  __ verify_oop(Otos_i);
  __ push(atos);
  if (!is_static && rc == may_rewrite) {
    patch_bytecode(Bytecodes::_fast_agetfield, G3_scratch, G4_scratch);
  }
  __ ba(checkVolatile);
  __ delayed()->tst(Lscratch);

  __ bind(notObj);

  // cmp(Rflags, itos);
  __ br(Assembler::notEqual, false, Assembler::pt, notInt);
  __ delayed() ->cmp(Rflags, ltos);

  // itos
  __ ld(Rclass, Roffset, Otos_i);
  __ push(itos);
  if (!is_static && rc == may_rewrite) {
    patch_bytecode(Bytecodes::_fast_igetfield, G3_scratch, G4_scratch);
  }
  __ ba(checkVolatile);
  __ delayed()->tst(Lscratch);

  __ bind(notInt);

  // cmp(Rflags, ltos);
  __ br(Assembler::notEqual, false, Assembler::pt, notLong);
  __ delayed() ->cmp(Rflags, btos);

  // ltos
  // load must be atomic
  __ ld_long(Rclass, Roffset, Otos_l);
  __ push(ltos);
  if (!is_static && rc == may_rewrite) {
    patch_bytecode(Bytecodes::_fast_lgetfield, G3_scratch, G4_scratch);
  }
  __ ba(checkVolatile);
  __ delayed()->tst(Lscratch);

  __ bind(notLong);

  // cmp(Rflags, btos);
  __ br(Assembler::notEqual, false, Assembler::pt, notByte);
  __ delayed() ->cmp(Rflags, ztos);

  // btos
  __ ldsb(Rclass, Roffset, Otos_i);
  __ push(itos);
  if (!is_static && rc == may_rewrite) {
    patch_bytecode(Bytecodes::_fast_bgetfield, G3_scratch, G4_scratch);
  }
  __ ba(checkVolatile);
  __ delayed()->tst(Lscratch);

  __ bind(notByte);

  // cmp(Rflags, ztos);
  __ br(Assembler::notEqual, false, Assembler::pt, notBool);
  __ delayed() ->cmp(Rflags, ctos);

  // ztos
  __ ldsb(Rclass, Roffset, Otos_i);
  __ push(itos);
  if (!is_static && rc == may_rewrite) {
    // use btos rewriting, no truncating to t/f bit is needed for getfield.
    patch_bytecode(Bytecodes::_fast_bgetfield, G3_scratch, G4_scratch);
  }
  __ ba(checkVolatile);
  __ delayed()->tst(Lscratch);

  __ bind(notBool);

  // cmp(Rflags, ctos);
  __ br(Assembler::notEqual, false, Assembler::pt, notChar);
  __ delayed() ->cmp(Rflags, stos);

  // ctos
  __ lduh(Rclass, Roffset, Otos_i);
  __ push(itos);
  if (!is_static && rc == may_rewrite) {
    patch_bytecode(Bytecodes::_fast_cgetfield, G3_scratch, G4_scratch);
  }
  __ ba(checkVolatile);
  __ delayed()->tst(Lscratch);

  __ bind(notChar);

  // cmp(Rflags, stos);
  __ br(Assembler::notEqual, false, Assembler::pt, notShort);
  __ delayed() ->cmp(Rflags, ftos);

  // stos
  __ ldsh(Rclass, Roffset, Otos_i);
  __ push(itos);
  if (!is_static && rc == may_rewrite) {
    patch_bytecode(Bytecodes::_fast_sgetfield, G3_scratch, G4_scratch);
  }
  __ ba(checkVolatile);
  __ delayed()->tst(Lscratch);

  __ bind(notShort);


  // cmp(Rflags, ftos);
  __ br(Assembler::notEqual, false, Assembler::pt, notFloat);
  __ delayed() ->tst(Lscratch);

  // ftos
  __ ldf(FloatRegisterImpl::S, Rclass, Roffset, Ftos_f);
  __ push(ftos);
  if (!is_static && rc == may_rewrite) {
    patch_bytecode(Bytecodes::_fast_fgetfield, G3_scratch, G4_scratch);
  }
  __ ba(checkVolatile);
  __ delayed()->tst(Lscratch);

  __ bind(notFloat);


  // dtos
  __ ldf(FloatRegisterImpl::D, Rclass, Roffset, Ftos_d);
  __ push(dtos);
  if (!is_static && rc == may_rewrite) {
    patch_bytecode(Bytecodes::_fast_dgetfield, G3_scratch, G4_scratch);
  }

  __ bind(checkVolatile);
  if (__ membar_has_effect(membar_bits)) {
    // __ tst(Lscratch); executed in delay slot
    __ br(Assembler::zero, false, Assembler::pt, exit);
    __ delayed()->nop();
    volatile_barrier(membar_bits);
  }

  __ bind(exit);
}

void TemplateTable::getfield(int byte_no) {
  getfield_or_static(byte_no, false);
}

void TemplateTable::nofast_getfield(int byte_no) {
  getfield_or_static(byte_no, false, may_not_rewrite);
}

void TemplateTable::getstatic(int byte_no) {
  getfield_or_static(byte_no, true);
}

void TemplateTable::fast_accessfield(TosState state) {
  transition(atos, state);
  Register Rcache  = G3_scratch;
  Register index   = G4_scratch;
  Register Roffset = G4_scratch;
  Register Rflags  = Rcache;
  ByteSize cp_base_offset = ConstantPoolCache::base_offset();

  __ get_cache_and_index_at_bcp(Rcache, index, 1);
  jvmti_post_field_access(Rcache, index, /*is_static*/false, /*has_tos*/true);

  __ ld_ptr(Rcache, cp_base_offset + ConstantPoolCacheEntry::f2_offset(), Roffset);

  __ null_check(Otos_i);
  __ verify_oop(Otos_i);

  Label exit;

  Assembler::Membar_mask_bits membar_bits =
    Assembler::Membar_mask_bits(Assembler::LoadLoad | Assembler::LoadStore);
  if (__ membar_has_effect(membar_bits)) {
    // Get volatile flag
    __ ld_ptr(Rcache, cp_base_offset + ConstantPoolCacheEntry::f2_offset(), Rflags);
    __ set((1 << ConstantPoolCacheEntry::is_volatile_shift), Lscratch);
  }

  switch (bytecode()) {
    case Bytecodes::_fast_bgetfield:
      __ ldsb(Otos_i, Roffset, Otos_i);
      break;
    case Bytecodes::_fast_cgetfield:
      __ lduh(Otos_i, Roffset, Otos_i);
      break;
    case Bytecodes::_fast_sgetfield:
      __ ldsh(Otos_i, Roffset, Otos_i);
      break;
    case Bytecodes::_fast_igetfield:
      __ ld(Otos_i, Roffset, Otos_i);
      break;
    case Bytecodes::_fast_lgetfield:
      __ ld_long(Otos_i, Roffset, Otos_l);
      break;
    case Bytecodes::_fast_fgetfield:
      __ ldf(FloatRegisterImpl::S, Otos_i, Roffset, Ftos_f);
      break;
    case Bytecodes::_fast_dgetfield:
      __ ldf(FloatRegisterImpl::D, Otos_i, Roffset, Ftos_d);
      break;
    case Bytecodes::_fast_agetfield:
      do_oop_load(_masm, Otos_i, Roffset, 0, Otos_i, noreg);
      break;
    default:
      ShouldNotReachHere();
  }

  if (__ membar_has_effect(membar_bits)) {
    __ btst(Lscratch, Rflags);
    __ br(Assembler::zero, false, Assembler::pt, exit);
    __ delayed()->nop();
    volatile_barrier(membar_bits);
    __ bind(exit);
  }

  if (state == atos) {
    __ verify_oop(Otos_i);    // does not blow flags!
  }
}

void TemplateTable::jvmti_post_fast_field_mod() {
  if (JvmtiExport::can_post_field_modification()) {
    // Check to see if a field modification watch has been set before we take
    // the time to call into the VM.
    Label done;
    AddressLiteral get_field_modification_count_addr(JvmtiExport::get_field_modification_count_addr());
    __ load_contents(get_field_modification_count_addr, G4_scratch);
    __ cmp_and_br_short(G4_scratch, 0, Assembler::equal, Assembler::pt, done);
    __ pop_ptr(G4_scratch);     // copy the object pointer from tos
    __ verify_oop(G4_scratch);
    __ push_ptr(G4_scratch);    // put the object pointer back on tos
    __ get_cache_entry_pointer_at_bcp(G1_scratch, G3_scratch, 1);
    // Save tos values before call_VM() clobbers them. Since we have
    // to do it for every data type, we use the saved values as the
    // jvalue object.
    switch (bytecode()) {  // save tos values before call_VM() clobbers them
    case Bytecodes::_fast_aputfield: __ push_ptr(Otos_i); break;
    case Bytecodes::_fast_bputfield: // fall through
    case Bytecodes::_fast_zputfield: // fall through
    case Bytecodes::_fast_sputfield: // fall through
    case Bytecodes::_fast_cputfield: // fall through
    case Bytecodes::_fast_iputfield: __ push_i(Otos_i); break;
    case Bytecodes::_fast_dputfield: __ push_d(Ftos_d); break;
    case Bytecodes::_fast_fputfield: __ push_f(Ftos_f); break;
    // get words in right order for use as jvalue object
    case Bytecodes::_fast_lputfield: __ push_l(Otos_l); break;
    }
    // setup pointer to jvalue object
    __ mov(Lesp, G3_scratch);  __ inc(G3_scratch, wordSize);
    // G4_scratch:  object pointer
    // G1_scratch: cache entry pointer
    // G3_scratch: jvalue object on the stack
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::post_field_modification), G4_scratch, G1_scratch, G3_scratch);
    switch (bytecode()) {             // restore tos values
    case Bytecodes::_fast_aputfield: __ pop_ptr(Otos_i); break;
    case Bytecodes::_fast_bputfield: // fall through
    case Bytecodes::_fast_zputfield: // fall through
    case Bytecodes::_fast_sputfield: // fall through
    case Bytecodes::_fast_cputfield: // fall through
    case Bytecodes::_fast_iputfield: __ pop_i(Otos_i); break;
    case Bytecodes::_fast_dputfield: __ pop_d(Ftos_d); break;
    case Bytecodes::_fast_fputfield: __ pop_f(Ftos_f); break;
    case Bytecodes::_fast_lputfield: __ pop_l(Otos_l); break;
    }
    __ bind(done);
  }
}

// The registers Rcache and index expected to be set before call.
// The function may destroy various registers, just not the Rcache and index registers.
void TemplateTable::jvmti_post_field_mod(Register Rcache, Register index, bool is_static) {
  ByteSize cp_base_offset = ConstantPoolCache::base_offset();

  if (JvmtiExport::can_post_field_modification()) {
    // Check to see if a field modification watch has been set before we take
    // the time to call into the VM.
    Label Label1;
    assert_different_registers(Rcache, index, G1_scratch);
    AddressLiteral get_field_modification_count_addr(JvmtiExport::get_field_modification_count_addr());
    __ load_contents(get_field_modification_count_addr, G1_scratch);
    __ cmp_and_br_short(G1_scratch, 0, Assembler::zero, Assembler::pt, Label1);

    // The Rcache and index registers have been already set.
    // This allows to eliminate this call but the Rcache and index
    // registers must be correspondingly used after this line.
    __ get_cache_and_index_at_bcp(G1_scratch, G4_scratch, 1);

    __ add(G1_scratch, in_bytes(cp_base_offset), G3_scratch);
    if (is_static) {
      // Life is simple.  Null out the object pointer.
      __ clr(G4_scratch);
    } else {
      Register Rflags = G1_scratch;
      // Life is harder. The stack holds the value on top, followed by the
      // object.  We don't know the size of the value, though; it could be
      // one or two words depending on its type. As a result, we must find
      // the type to determine where the object is.

      Label two_word, valsizeknown;
      __ ld_ptr(G1_scratch, cp_base_offset + ConstantPoolCacheEntry::flags_offset(), Rflags);
      __ mov(Lesp, G4_scratch);
      __ srl(Rflags, ConstantPoolCacheEntry::tos_state_shift, Rflags);
      // Make sure we don't need to mask Rflags after the above shift
      ConstantPoolCacheEntry::verify_tos_state_shift();
      __ cmp(Rflags, ltos);
      __ br(Assembler::equal, false, Assembler::pt, two_word);
      __ delayed()->cmp(Rflags, dtos);
      __ br(Assembler::equal, false, Assembler::pt, two_word);
      __ delayed()->nop();
      __ inc(G4_scratch, Interpreter::expr_offset_in_bytes(1));
      __ ba_short(valsizeknown);
      __ bind(two_word);

      __ inc(G4_scratch, Interpreter::expr_offset_in_bytes(2));

      __ bind(valsizeknown);
      // setup object pointer
      __ ld_ptr(G4_scratch, 0, G4_scratch);
      __ verify_oop(G4_scratch);
    }
    // setup pointer to jvalue object
    __ mov(Lesp, G1_scratch);  __ inc(G1_scratch, wordSize);
    // G4_scratch:  object pointer or NULL if static
    // G3_scratch: cache entry pointer
    // G1_scratch: jvalue object on the stack
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::post_field_modification),
               G4_scratch, G3_scratch, G1_scratch);
    __ get_cache_and_index_at_bcp(Rcache, index, 1);
    __ bind(Label1);
  }
}

void TemplateTable::pop_and_check_object(Register r) {
  __ pop_ptr(r);
  __ null_check(r);  // for field access must check obj.
  __ verify_oop(r);
}

void TemplateTable::putfield_or_static(int byte_no, bool is_static, RewriteControl rc) {
  transition(vtos, vtos);
  Register Rcache = G3_scratch;
  Register index  = G4_scratch;
  Register Rclass = Rcache;
  Register Roffset= G4_scratch;
  Register Rflags = G1_scratch;
  ByteSize cp_base_offset = ConstantPoolCache::base_offset();

  resolve_cache_and_index(byte_no, Rcache, index, sizeof(u2));
  jvmti_post_field_mod(Rcache, index, is_static);
  load_field_cp_cache_entry(Rclass, Rcache, index, Roffset, Rflags, is_static);

  Assembler::Membar_mask_bits read_bits =
    Assembler::Membar_mask_bits(Assembler::LoadStore | Assembler::StoreStore);
  Assembler::Membar_mask_bits write_bits = Assembler::StoreLoad;

  Label notVolatile, checkVolatile, exit;
  if (__ membar_has_effect(read_bits) || __ membar_has_effect(write_bits)) {
    __ set((1 << ConstantPoolCacheEntry::is_volatile_shift), Lscratch);
    __ and3(Rflags, Lscratch, Lscratch);

    if (__ membar_has_effect(read_bits)) {
      __ cmp_and_br_short(Lscratch, 0, Assembler::equal, Assembler::pt, notVolatile);
      volatile_barrier(read_bits);
      __ bind(notVolatile);
    }
  }

  __ srl(Rflags, ConstantPoolCacheEntry::tos_state_shift, Rflags);
  // Make sure we don't need to mask Rflags after the above shift
  ConstantPoolCacheEntry::verify_tos_state_shift();

  // compute field type
  Label notInt, notShort, notChar, notObj, notByte, notBool, notLong, notFloat;

  if (is_static) {
    // putstatic with object type most likely, check that first
    __ cmp(Rflags, atos);
    __ br(Assembler::notEqual, false, Assembler::pt, notObj);
    __ delayed()->cmp(Rflags, itos);

    // atos
    {
      __ pop_ptr();
      __ verify_oop(Otos_i);
      do_oop_store(_masm, Rclass, Roffset, 0, Otos_i, G1_scratch);
      __ ba(checkVolatile);
      __ delayed()->tst(Lscratch);
    }

    __ bind(notObj);
    // cmp(Rflags, itos);
    __ br(Assembler::notEqual, false, Assembler::pt, notInt);
    __ delayed()->cmp(Rflags, btos);

    // itos
    {
      __ pop_i();
      __ st(Otos_i, Rclass, Roffset);
      __ ba(checkVolatile);
      __ delayed()->tst(Lscratch);
    }

    __ bind(notInt);
  } else {
    // putfield with int type most likely, check that first
    __ cmp(Rflags, itos);
    __ br(Assembler::notEqual, false, Assembler::pt, notInt);
    __ delayed()->cmp(Rflags, atos);

    // itos
    {
      __ pop_i();
      pop_and_check_object(Rclass);
      __ st(Otos_i, Rclass, Roffset);
      if (rc == may_rewrite) patch_bytecode(Bytecodes::_fast_iputfield, G3_scratch, G4_scratch, true, byte_no);
      __ ba(checkVolatile);
      __ delayed()->tst(Lscratch);
    }

    __ bind(notInt);
    // cmp(Rflags, atos);
    __ br(Assembler::notEqual, false, Assembler::pt, notObj);
    __ delayed()->cmp(Rflags, btos);

    // atos
    {
      __ pop_ptr();
      pop_and_check_object(Rclass);
      __ verify_oop(Otos_i);
      do_oop_store(_masm, Rclass, Roffset, 0, Otos_i, G1_scratch);
      if (rc == may_rewrite) patch_bytecode(Bytecodes::_fast_aputfield, G3_scratch, G4_scratch, true, byte_no);
      __ ba(checkVolatile);
      __ delayed()->tst(Lscratch);
    }

    __ bind(notObj);
  }

  // cmp(Rflags, btos);
  __ br(Assembler::notEqual, false, Assembler::pt, notByte);
  __ delayed()->cmp(Rflags, ztos);

  // btos
  {
    __ pop_i();
    if (!is_static) pop_and_check_object(Rclass);
    __ stb(Otos_i, Rclass, Roffset);
    if (!is_static && rc == may_rewrite) {
      patch_bytecode(Bytecodes::_fast_bputfield, G3_scratch, G4_scratch, true, byte_no);
    }
    __ ba(checkVolatile);
    __ delayed()->tst(Lscratch);
  }

  __ bind(notByte);

  // cmp(Rflags, btos);
  __ br(Assembler::notEqual, false, Assembler::pt, notBool);
  __ delayed()->cmp(Rflags, ltos);

  // ztos
  {
    __ pop_i();
    if (!is_static) pop_and_check_object(Rclass);
    __ and3(Otos_i, 1, Otos_i);
    __ stb(Otos_i, Rclass, Roffset);
    if (!is_static && rc == may_rewrite) {
      patch_bytecode(Bytecodes::_fast_zputfield, G3_scratch, G4_scratch, true, byte_no);
    }
    __ ba(checkVolatile);
    __ delayed()->tst(Lscratch);
  }

  __ bind(notBool);
  // cmp(Rflags, ltos);
  __ br(Assembler::notEqual, false, Assembler::pt, notLong);
  __ delayed()->cmp(Rflags, ctos);

  // ltos
  {
    __ pop_l();
    if (!is_static) pop_and_check_object(Rclass);
    __ st_long(Otos_l, Rclass, Roffset);
    if (!is_static && rc == may_rewrite) {
      patch_bytecode(Bytecodes::_fast_lputfield, G3_scratch, G4_scratch, true, byte_no);
    }
    __ ba(checkVolatile);
    __ delayed()->tst(Lscratch);
  }

  __ bind(notLong);
  // cmp(Rflags, ctos);
  __ br(Assembler::notEqual, false, Assembler::pt, notChar);
  __ delayed()->cmp(Rflags, stos);

  // ctos (char)
  {
    __ pop_i();
    if (!is_static) pop_and_check_object(Rclass);
    __ sth(Otos_i, Rclass, Roffset);
    if (!is_static && rc == may_rewrite) {
      patch_bytecode(Bytecodes::_fast_cputfield, G3_scratch, G4_scratch, true, byte_no);
    }
    __ ba(checkVolatile);
    __ delayed()->tst(Lscratch);
  }

  __ bind(notChar);
  // cmp(Rflags, stos);
  __ br(Assembler::notEqual, false, Assembler::pt, notShort);
  __ delayed()->cmp(Rflags, ftos);

  // stos (short)
  {
    __ pop_i();
    if (!is_static) pop_and_check_object(Rclass);
    __ sth(Otos_i, Rclass, Roffset);
    if (!is_static && rc == may_rewrite) {
      patch_bytecode(Bytecodes::_fast_sputfield, G3_scratch, G4_scratch, true, byte_no);
    }
    __ ba(checkVolatile);
    __ delayed()->tst(Lscratch);
  }

  __ bind(notShort);
  // cmp(Rflags, ftos);
  __ br(Assembler::notZero, false, Assembler::pt, notFloat);
  __ delayed()->nop();

  // ftos
  {
    __ pop_f();
    if (!is_static) pop_and_check_object(Rclass);
    __ stf(FloatRegisterImpl::S, Ftos_f, Rclass, Roffset);
    if (!is_static && rc == may_rewrite) {
      patch_bytecode(Bytecodes::_fast_fputfield, G3_scratch, G4_scratch, true, byte_no);
    }
    __ ba(checkVolatile);
    __ delayed()->tst(Lscratch);
  }

  __ bind(notFloat);

  // dtos
  {
    __ pop_d();
    if (!is_static) pop_and_check_object(Rclass);
    __ stf(FloatRegisterImpl::D, Ftos_d, Rclass, Roffset);
    if (!is_static && rc == may_rewrite) {
      patch_bytecode(Bytecodes::_fast_dputfield, G3_scratch, G4_scratch, true, byte_no);
    }
  }

  __ bind(checkVolatile);
  __ tst(Lscratch);

  if (__ membar_has_effect(write_bits)) {
    // __ tst(Lscratch); in delay slot
    __ br(Assembler::zero, false, Assembler::pt, exit);
    __ delayed()->nop();
    volatile_barrier(Assembler::StoreLoad);
    __ bind(exit);
  }
}

void TemplateTable::fast_storefield(TosState state) {
  transition(state, vtos);
  Register Rcache = G3_scratch;
  Register Rclass = Rcache;
  Register Roffset= G4_scratch;
  Register Rflags = G1_scratch;
  ByteSize cp_base_offset = ConstantPoolCache::base_offset();

  jvmti_post_fast_field_mod();

  __ get_cache_and_index_at_bcp(Rcache, G4_scratch, 1);

  Assembler::Membar_mask_bits read_bits =
    Assembler::Membar_mask_bits(Assembler::LoadStore | Assembler::StoreStore);
  Assembler::Membar_mask_bits write_bits = Assembler::StoreLoad;

  Label notVolatile, checkVolatile, exit;
  if (__ membar_has_effect(read_bits) || __ membar_has_effect(write_bits)) {
    __ ld_ptr(Rcache, cp_base_offset + ConstantPoolCacheEntry::flags_offset(), Rflags);
    __ set((1 << ConstantPoolCacheEntry::is_volatile_shift), Lscratch);
    __ and3(Rflags, Lscratch, Lscratch);
    if (__ membar_has_effect(read_bits)) {
      __ cmp_and_br_short(Lscratch, 0, Assembler::equal, Assembler::pt, notVolatile);
      volatile_barrier(read_bits);
      __ bind(notVolatile);
    }
  }

  __ ld_ptr(Rcache, cp_base_offset + ConstantPoolCacheEntry::f2_offset(), Roffset);
  pop_and_check_object(Rclass);

  switch (bytecode()) {
    case Bytecodes::_fast_zputfield: __ and3(Otos_i, 1, Otos_i);  // fall through to bputfield
    case Bytecodes::_fast_bputfield: __ stb(Otos_i, Rclass, Roffset); break;
    case Bytecodes::_fast_cputfield: /* fall through */
    case Bytecodes::_fast_sputfield: __ sth(Otos_i, Rclass, Roffset); break;
    case Bytecodes::_fast_iputfield: __ st(Otos_i, Rclass, Roffset);  break;
    case Bytecodes::_fast_lputfield: __ st_long(Otos_l, Rclass, Roffset); break;
    case Bytecodes::_fast_fputfield:
      __ stf(FloatRegisterImpl::S, Ftos_f, Rclass, Roffset);
      break;
    case Bytecodes::_fast_dputfield:
      __ stf(FloatRegisterImpl::D, Ftos_d, Rclass, Roffset);
      break;
    case Bytecodes::_fast_aputfield:
      do_oop_store(_masm, Rclass, Roffset, 0, Otos_i, G1_scratch);
      break;
    default:
      ShouldNotReachHere();
  }

  if (__ membar_has_effect(write_bits)) {
    __ cmp_and_br_short(Lscratch, 0, Assembler::equal, Assembler::pt, exit);
    volatile_barrier(Assembler::StoreLoad);
    __ bind(exit);
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

void TemplateTable::fast_xaccess(TosState state) {
  transition(vtos, state);
  Register Rcache = G3_scratch;
  Register Roffset = G4_scratch;
  Register Rflags  = G4_scratch;
  Register Rreceiver = Lscratch;

  __ ld_ptr(Llocals, 0, Rreceiver);

  // access constant pool cache  (is resolved)
  __ get_cache_and_index_at_bcp(Rcache, G4_scratch, 2);
  __ ld_ptr(Rcache, ConstantPoolCache::base_offset() + ConstantPoolCacheEntry::f2_offset(), Roffset);
  __ add(Lbcp, 1, Lbcp);       // needed to report exception at the correct bcp

  __ verify_oop(Rreceiver);
  __ null_check(Rreceiver);
  if (state == atos) {
    do_oop_load(_masm, Rreceiver, Roffset, 0, Otos_i, noreg);
  } else if (state == itos) {
    __ ld (Rreceiver, Roffset, Otos_i) ;
  } else if (state == ftos) {
    __ ldf(FloatRegisterImpl::S, Rreceiver, Roffset, Ftos_f);
  } else {
    ShouldNotReachHere();
  }

  Assembler::Membar_mask_bits membar_bits =
    Assembler::Membar_mask_bits(Assembler::LoadLoad | Assembler::LoadStore);
  if (__ membar_has_effect(membar_bits)) {

    // Get is_volatile value in Rflags and check if membar is needed
    __ ld_ptr(Rcache, ConstantPoolCache::base_offset() + ConstantPoolCacheEntry::flags_offset(), Rflags);

    // Test volatile
    Label notVolatile;
    __ set((1 << ConstantPoolCacheEntry::is_volatile_shift), Lscratch);
    __ btst(Rflags, Lscratch);
    __ br(Assembler::zero, false, Assembler::pt, notVolatile);
    __ delayed()->nop();
    volatile_barrier(membar_bits);
    __ bind(notVolatile);
  }

  __ interp_verify_oop(Otos_i, state, __FILE__, __LINE__);
  __ sub(Lbcp, 1, Lbcp);
}

//----------------------------------------------------------------------------------------------------
// Calls

void TemplateTable::count_calls(Register method, Register temp) {
  // implemented elsewhere
  ShouldNotReachHere();
}

void TemplateTable::prepare_invoke(int byte_no,
                                   Register method,  // linked method (or i-klass)
                                   Register ra,      // return address
                                   Register index,   // itable index, MethodType, etc.
                                   Register recv,    // if caller wants to see it
                                   Register flags    // if caller wants to test it
                                   ) {
  // determine flags
  const Bytecodes::Code code = bytecode();
  const bool is_invokeinterface  = code == Bytecodes::_invokeinterface;
  const bool is_invokedynamic    = code == Bytecodes::_invokedynamic;
  const bool is_invokehandle     = code == Bytecodes::_invokehandle;
  const bool is_invokevirtual    = code == Bytecodes::_invokevirtual;
  const bool is_invokespecial    = code == Bytecodes::_invokespecial;
  const bool load_receiver       = (recv != noreg);
  assert(load_receiver == (code != Bytecodes::_invokestatic && code != Bytecodes::_invokedynamic), "");
  assert(recv  == noreg || recv  == O0, "");
  assert(flags == noreg || flags == O1, "");

  // setup registers & access constant pool cache
  if (recv  == noreg)  recv  = O0;
  if (flags == noreg)  flags = O1;
  const Register temp = O2;
  assert_different_registers(method, ra, index, recv, flags, temp);

  load_invoke_cp_cache_entry(byte_no, method, index, flags, is_invokevirtual, false, is_invokedynamic);

  __ mov(SP, O5_savedSP);  // record SP that we wanted the callee to restore

  // maybe push appendix to arguments
  if (is_invokedynamic || is_invokehandle) {
    Label L_no_push;
    __ set((1 << ConstantPoolCacheEntry::has_appendix_shift), temp);
    __ btst(flags, temp);
    __ br(Assembler::zero, false, Assembler::pt, L_no_push);
    __ delayed()->nop();
    // Push the appendix as a trailing parameter.
    // This must be done before we get the receiver,
    // since the parameter_size includes it.
    assert(ConstantPoolCacheEntry::_indy_resolved_references_appendix_offset == 0, "appendix expected at index+0");
    __ load_resolved_reference_at_index(temp, index, /*tmp*/recv);
    __ verify_oop(temp);
    __ push_ptr(temp);  // push appendix (MethodType, CallSite, etc.)
    __ bind(L_no_push);
  }

  // load receiver if needed (after appendix is pushed so parameter size is correct)
  if (load_receiver) {
    __ and3(flags, ConstantPoolCacheEntry::parameter_size_mask, temp);  // get parameter size
    __ load_receiver(temp, recv);  //  __ argument_address uses Gargs but we need Lesp
    __ verify_oop(recv);
  }

  // compute return type
  __ srl(flags, ConstantPoolCacheEntry::tos_state_shift, ra);
  // Make sure we don't need to mask flags after the above shift
  ConstantPoolCacheEntry::verify_tos_state_shift();
  // load return address
  {
    const address table_addr = (address) Interpreter::invoke_return_entry_table_for(code);
    AddressLiteral table(table_addr);
    __ set(table, temp);
    __ sll(ra, LogBytesPerWord, ra);
    __ ld_ptr(Address(temp, ra), ra);
  }
}


void TemplateTable::generate_vtable_call(Register Rrecv, Register Rindex, Register Rret) {
  Register Rtemp = G4_scratch;
  Register Rcall = Rindex;
  assert_different_registers(Rcall, G5_method, Gargs, Rret);

  // get target Method* & entry point
  __ lookup_virtual_method(Rrecv, Rindex, G5_method);
  __ profile_arguments_type(G5_method, Rcall, Gargs, true);
  __ profile_called_method(G5_method, Rtemp);
  __ call_from_interpreter(Rcall, Gargs, Rret);
}

void TemplateTable::invokevirtual(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f2_byte, "use this argument");

  Register Rscratch = G3_scratch;
  Register Rtemp    = G4_scratch;
  Register Rret     = Lscratch;
  Register O0_recv  = O0;
  Label notFinal;

  load_invoke_cp_cache_entry(byte_no, G5_method, noreg, Rret, true, false, false);
  __ mov(SP, O5_savedSP); // record SP that we wanted the callee to restore

  // Check for vfinal
  __ set((1 << ConstantPoolCacheEntry::is_vfinal_shift), G4_scratch);
  __ btst(Rret, G4_scratch);
  __ br(Assembler::zero, false, Assembler::pt, notFinal);
  __ delayed()->and3(Rret, 0xFF, G4_scratch);      // gets number of parameters

  if (RewriteBytecodes && !UseSharedSpaces && !DumpSharedSpaces) {
    patch_bytecode(Bytecodes::_fast_invokevfinal, Rscratch, Rtemp);
  }

  invokevfinal_helper(Rscratch, Rret);

  __ bind(notFinal);

  __ mov(G5_method, Rscratch);  // better scratch register
  __ load_receiver(G4_scratch, O0_recv);  // gets receiverOop
  // receiver is in O0_recv
  __ verify_oop(O0_recv);

  // get return address
  AddressLiteral table(Interpreter::invoke_return_entry_table());
  __ set(table, Rtemp);
  __ srl(Rret, ConstantPoolCacheEntry::tos_state_shift, Rret);          // get return type
  // Make sure we don't need to mask Rret after the above shift
  ConstantPoolCacheEntry::verify_tos_state_shift();
  __ sll(Rret,  LogBytesPerWord, Rret);
  __ ld_ptr(Rtemp, Rret, Rret);         // get return address

  // get receiver klass
  __ null_check(O0_recv, oopDesc::klass_offset_in_bytes());
  __ load_klass(O0_recv, O0_recv);
  __ verify_klass_ptr(O0_recv);

  __ profile_virtual_call(O0_recv, O4);

  generate_vtable_call(O0_recv, Rscratch, Rret);
}

void TemplateTable::fast_invokevfinal(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f2_byte, "use this argument");

  load_invoke_cp_cache_entry(byte_no, G5_method, noreg, Lscratch, true,
                             /*is_invokevfinal*/true, false);
  __ mov(SP, O5_savedSP); // record SP that we wanted the callee to restore
  invokevfinal_helper(G3_scratch, Lscratch);
}

void TemplateTable::invokevfinal_helper(Register Rscratch, Register Rret) {
  Register Rtemp = G4_scratch;

  // Load receiver from stack slot
  __ ld_ptr(G5_method, in_bytes(Method::const_offset()), G4_scratch);
  __ lduh(G4_scratch, in_bytes(ConstMethod::size_of_parameters_offset()), G4_scratch);
  __ load_receiver(G4_scratch, O0);

  // receiver NULL check
  __ null_check(O0);

  __ profile_final_call(O4);
  __ profile_arguments_type(G5_method, Rscratch, Gargs, true);

  // get return address
  AddressLiteral table(Interpreter::invoke_return_entry_table());
  __ set(table, Rtemp);
  __ srl(Rret, ConstantPoolCacheEntry::tos_state_shift, Rret);          // get return type
  // Make sure we don't need to mask Rret after the above shift
  ConstantPoolCacheEntry::verify_tos_state_shift();
  __ sll(Rret,  LogBytesPerWord, Rret);
  __ ld_ptr(Rtemp, Rret, Rret);         // get return address


  // do the call
  __ call_from_interpreter(Rscratch, Gargs, Rret);
}


void TemplateTable::invokespecial(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f1_byte, "use this argument");

  const Register Rret     = Lscratch;
  const Register O0_recv  = O0;
  const Register Rscratch = G3_scratch;

  prepare_invoke(byte_no, G5_method, Rret, noreg, O0_recv);  // get receiver also for null check
  __ null_check(O0_recv);

  // do the call
  __ profile_call(O4);
  __ profile_arguments_type(G5_method, Rscratch, Gargs, false);
  __ call_from_interpreter(Rscratch, Gargs, Rret);
}


void TemplateTable::invokestatic(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f1_byte, "use this argument");

  const Register Rret     = Lscratch;
  const Register Rscratch = G3_scratch;

  prepare_invoke(byte_no, G5_method, Rret);  // get f1 Method*

  // do the call
  __ profile_call(O4);
  __ profile_arguments_type(G5_method, Rscratch, Gargs, false);
  __ call_from_interpreter(Rscratch, Gargs, Rret);
}

void TemplateTable::invokeinterface_object_method(Register RKlass,
                                                  Register Rcall,
                                                  Register Rret,
                                                  Register Rflags) {
  Register Rscratch = G4_scratch;
  Register Rindex = Lscratch;

  assert_different_registers(Rscratch, Rindex, Rret);

  Label notFinal;

  // Check for vfinal
  __ set((1 << ConstantPoolCacheEntry::is_vfinal_shift), Rscratch);
  __ btst(Rflags, Rscratch);
  __ br(Assembler::zero, false, Assembler::pt, notFinal);
  __ delayed()->nop();

  __ profile_final_call(O4);

  // do the call - the index (f2) contains the Method*
  assert_different_registers(G5_method, Gargs, Rcall);
  __ mov(Rindex, G5_method);
  __ profile_arguments_type(G5_method, Rcall, Gargs, true);
  __ call_from_interpreter(Rcall, Gargs, Rret);
  __ bind(notFinal);

  __ profile_virtual_call(RKlass, O4);
  generate_vtable_call(RKlass, Rindex, Rret);
}


void TemplateTable::invokeinterface(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f1_byte, "use this argument");

  const Register Rinterface  = G1_scratch;
  const Register Rmethod     = Lscratch;
  const Register Rret        = G3_scratch;
  const Register O0_recv     = O0;
  const Register O1_flags    = O1;
  const Register O2_Klass    = O2;
  const Register Rscratch    = G4_scratch;
  assert_different_registers(Rscratch, G5_method);

  prepare_invoke(byte_no, Rinterface, Rret, Rmethod, O0_recv, O1_flags);

  // First check for Object case, then private interface method,
  // then regular interface method.

  // get receiver klass - this is also a null check
  __ null_check(O0_recv, oopDesc::klass_offset_in_bytes());
  __ load_klass(O0_recv, O2_Klass);

  // Special case of invokeinterface called for virtual method of
  // java.lang.Object.  See cpCache.cpp for details.
  Label notObjectMethod;
  __ set((1 << ConstantPoolCacheEntry::is_forced_virtual_shift), Rscratch);
  __ btst(O1_flags, Rscratch);
  __ br(Assembler::zero, false, Assembler::pt, notObjectMethod);
  __ delayed()->nop();

  invokeinterface_object_method(O2_Klass, Rinterface, Rret, O1_flags);

  __ bind(notObjectMethod);

  Label L_no_such_interface;

  // Check for private method invocation - indicated by vfinal
  Label notVFinal;
  {
    __ set((1 << ConstantPoolCacheEntry::is_vfinal_shift), Rscratch);
    __ btst(O1_flags, Rscratch);
    __ br(Assembler::zero, false, Assembler::pt, notVFinal);
    __ delayed()->nop();

    Label subtype;
    Register Rtemp = O1_flags;
    __ check_klass_subtype(O2_Klass, Rinterface, Rscratch, Rtemp, subtype);
    // If we get here the typecheck failed
    __ ba(L_no_such_interface);
    __ delayed()->nop();
    __ bind(subtype);

    // do the call
    Register Rcall = Rinterface;
    __ mov(Rmethod, G5_method);
    assert_different_registers(Rcall, G5_method, Gargs, Rret);

    __ profile_arguments_type(G5_method, Rcall, Gargs, true);
    __ profile_final_call(Rscratch);
    __ call_from_interpreter(Rcall, Gargs, Rret);
  }
  __ bind(notVFinal);

  Register Rtemp = O1_flags;

  // Receiver subtype check against REFC.
  __ lookup_interface_method(// inputs: rec. class, interface, itable index
                             O2_Klass, Rinterface, noreg,
                             // outputs: temp reg1, temp reg2, temp reg3
                             G5_method, Rscratch, Rtemp,
                             L_no_such_interface,
                             /*return_method=*/false);

  __ profile_virtual_call(O2_Klass, O4);

  //
  // find entry point to call
  //

  // Get declaring interface class from method
  __ ld_ptr(Rmethod, Method::const_offset(), Rinterface);
  __ ld_ptr(Rinterface, ConstMethod::constants_offset(), Rinterface);
  __ ld_ptr(Rinterface, ConstantPool::pool_holder_offset_in_bytes(), Rinterface);

  // Get itable index from method
  const Register Rindex = G5_method;
  __ ld(Rmethod, Method::itable_index_offset(), Rindex);
  __ sub(Rindex, Method::itable_index_max, Rindex);
  __ neg(Rindex);

  // Preserve O2_Klass for throw_AbstractMethodErrorVerbose
  __ mov(O2_Klass, O4);
  __ lookup_interface_method(// inputs: rec. class, interface, itable index
                             O4, Rinterface, Rindex,
                             // outputs: method, scan temp reg, temp reg
                             G5_method, Rscratch, Rtemp,
                             L_no_such_interface);

  // Check for abstract method error.
  {
    Label ok;
    __ br_notnull_short(G5_method, Assembler::pt, ok);
    // Pass arguments for generating a verbose error message.
    call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_AbstractMethodErrorVerbose),
            O2_Klass, Rmethod);
    __ should_not_reach_here();
    __ bind(ok);
  }

  Register Rcall = Rinterface;
  assert_different_registers(Rcall, G5_method, Gargs, Rret);

  __ profile_arguments_type(G5_method, Rcall, Gargs, true);
  __ profile_called_method(G5_method, Rscratch);
  __ call_from_interpreter(Rcall, Gargs, Rret);

  __ bind(L_no_such_interface);
  // Pass arguments for generating a verbose error message.
  call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_IncompatibleClassChangeErrorVerbose),
          O2_Klass, Rinterface);
  __ should_not_reach_here();
}

void TemplateTable::invokehandle(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f1_byte, "use this argument");

  const Register Rret       = Lscratch;
  const Register G4_mtype   = G4_scratch;
  const Register O0_recv    = O0;
  const Register Rscratch   = G3_scratch;

  prepare_invoke(byte_no, G5_method, Rret, G4_mtype, O0_recv);
  __ null_check(O0_recv);

  // G4: MethodType object (from cpool->resolved_references[f1], if necessary)
  // G5: MH.invokeExact_MT method (from f2)

  // Note:  G4_mtype is already pushed (if necessary) by prepare_invoke

  // do the call
  __ verify_oop(G4_mtype);
  __ profile_final_call(O4);  // FIXME: profile the LambdaForm also
  __ profile_arguments_type(G5_method, Rscratch, Gargs, true);
  __ call_from_interpreter(Rscratch, Gargs, Rret);
}


void TemplateTable::invokedynamic(int byte_no) {
  transition(vtos, vtos);
  assert(byte_no == f1_byte, "use this argument");

  const Register Rret        = Lscratch;
  const Register G4_callsite = G4_scratch;
  const Register Rscratch    = G3_scratch;

  prepare_invoke(byte_no, G5_method, Rret, G4_callsite);

  // G4: CallSite object (from cpool->resolved_references[f1])
  // G5: MH.linkToCallSite method (from f2)

  // Note:  G4_callsite is already pushed by prepare_invoke

  // %%% should make a type profile for any invokedynamic that takes a ref argument
  // profile this call
  __ profile_call(O4);

  // do the call
  __ verify_oop(G4_callsite);
  __ profile_arguments_type(G5_method, Rscratch, Gargs, false);
  __ call_from_interpreter(Rscratch, Gargs, Rret);
}


//----------------------------------------------------------------------------------------------------
// Allocation

void TemplateTable::_new() {
  transition(vtos, atos);

  Label slow_case;
  Label done;
  Label initialize_header;
  Label initialize_object;  // including clearing the fields

  Register RallocatedObject = Otos_i;
  Register RinstanceKlass = O1;
  Register Roffset = O3;
  Register Rscratch = O4;

  __ get_2_byte_integer_at_bcp(1, Rscratch, Roffset, InterpreterMacroAssembler::Unsigned);
  __ get_cpool_and_tags(Rscratch, G3_scratch);
  // make sure the class we're about to instantiate has been resolved
  // This is done before loading InstanceKlass to be consistent with the order
  // how Constant Pool is updated (see ConstantPool::klass_at_put)
  __ add(G3_scratch, Array<u1>::base_offset_in_bytes(), G3_scratch);
  __ ldub(G3_scratch, Roffset, G3_scratch);
  __ cmp(G3_scratch, JVM_CONSTANT_Class);
  __ br(Assembler::notEqual, false, Assembler::pn, slow_case);
  __ delayed()->sll(Roffset, LogBytesPerWord, Roffset);
  // get InstanceKlass
  __ load_resolved_klass_at_offset(Rscratch, Roffset, RinstanceKlass);

  // make sure klass is fully initialized:
  __ ldub(RinstanceKlass, in_bytes(InstanceKlass::init_state_offset()), G3_scratch);
  __ cmp(G3_scratch, InstanceKlass::fully_initialized);
  __ br(Assembler::notEqual, false, Assembler::pn, slow_case);
  __ delayed()->ld(RinstanceKlass, in_bytes(Klass::layout_helper_offset()), Roffset);

  // get instance_size in InstanceKlass (already aligned)
  //__ ld(RinstanceKlass, in_bytes(Klass::layout_helper_offset()), Roffset);

  // make sure klass does not have has_finalizer, or is abstract, or interface or java/lang/Class
  __ btst(Klass::_lh_instance_slow_path_bit, Roffset);
  __ br(Assembler::notZero, false, Assembler::pn, slow_case);
  __ delayed()->nop();

  // Allocate the instance:
  //  If TLAB is enabled:
  //    Try to allocate in the TLAB.
  //    If fails, go to the slow path.
  //  Else If inline contiguous allocations are enabled:
  //    Try to allocate in eden.
  //    If fails due to heap end, go to slow path.
  //
  //  If TLAB is enabled OR inline contiguous is enabled:
  //    Initialize the allocation.
  //    Exit.
  //
  //  Go to slow path.

  const bool allow_shared_alloc =
    Universe::heap()->supports_inline_contig_alloc();

  if(UseTLAB) {
    Register RoldTopValue = RallocatedObject;
    Register RtlabWasteLimitValue = G3_scratch;
    Register RnewTopValue = G1_scratch;
    Register RendValue = Rscratch;
    Register RfreeValue = RnewTopValue;

    // check if we can allocate in the TLAB
    __ ld_ptr(G2_thread, in_bytes(JavaThread::tlab_top_offset()), RoldTopValue); // sets up RalocatedObject
    __ ld_ptr(G2_thread, in_bytes(JavaThread::tlab_end_offset()), RendValue);
    __ add(RoldTopValue, Roffset, RnewTopValue);

    // if there is enough space, we do not CAS and do not clear
    __ cmp(RnewTopValue, RendValue);
    if(ZeroTLAB) {
      // the fields have already been cleared
      __ brx(Assembler::lessEqualUnsigned, true, Assembler::pt, initialize_header);
    } else {
      // initialize both the header and fields
      __ brx(Assembler::lessEqualUnsigned, true, Assembler::pt, initialize_object);
    }
    __ delayed()->st_ptr(RnewTopValue, G2_thread, in_bytes(JavaThread::tlab_top_offset()));

    // Allocation does not fit in the TLAB.
    __ ba_short(slow_case);
  } else {
    // Allocation in the shared Eden
    if (allow_shared_alloc) {
      Register RoldTopValue = G1_scratch;
      Register RtopAddr = G3_scratch;
      Register RnewTopValue = RallocatedObject;
      Register RendValue = Rscratch;

      __ set((intptr_t)Universe::heap()->top_addr(), RtopAddr);

      Label retry;
      __ bind(retry);
      __ set((intptr_t)Universe::heap()->end_addr(), RendValue);
      __ ld_ptr(RendValue, 0, RendValue);
      __ ld_ptr(RtopAddr, 0, RoldTopValue);
      __ add(RoldTopValue, Roffset, RnewTopValue);

      // RnewTopValue contains the top address after the new object
      // has been allocated.
      __ cmp_and_brx_short(RnewTopValue, RendValue, Assembler::greaterUnsigned, Assembler::pn, slow_case);

      __ cas_ptr(RtopAddr, RoldTopValue, RnewTopValue);

      // if someone beat us on the allocation, try again, otherwise continue
      __ cmp_and_brx_short(RoldTopValue, RnewTopValue, Assembler::notEqual, Assembler::pn, retry);

      // bump total bytes allocated by this thread
      // RoldTopValue and RtopAddr are dead, so can use G1 and G3
      __ incr_allocated_bytes(Roffset, G1_scratch, G3_scratch);
    }
  }

  // If UseTLAB or allow_shared_alloc are true, the object is created above and
  // there is an initialize need. Otherwise, skip and go to the slow path.
  if (UseTLAB || allow_shared_alloc) {
    // clear object fields
    __ bind(initialize_object);
    __ deccc(Roffset, sizeof(oopDesc));
    __ br(Assembler::zero, false, Assembler::pt, initialize_header);
    __ delayed()->add(RallocatedObject, sizeof(oopDesc), G3_scratch);

    // initialize remaining object fields
    if (UseBlockZeroing) {
      // Use BIS for zeroing
      __ bis_zeroing(G3_scratch, Roffset, G1_scratch, initialize_header);
    } else {
      Label loop;
      __ subcc(Roffset, wordSize, Roffset);
      __ bind(loop);
      //__ subcc(Roffset, wordSize, Roffset);      // executed above loop or in delay slot
      __ st_ptr(G0, G3_scratch, Roffset);
      __ br(Assembler::notEqual, false, Assembler::pt, loop);
      __ delayed()->subcc(Roffset, wordSize, Roffset);
    }
    __ ba_short(initialize_header);
  }

  // slow case
  __ bind(slow_case);
  __ get_2_byte_integer_at_bcp(1, G3_scratch, O2, InterpreterMacroAssembler::Unsigned);
  __ get_constant_pool(O1);

  call_VM(Otos_i, CAST_FROM_FN_PTR(address, InterpreterRuntime::_new), O1, O2);

  __ ba_short(done);

  // Initialize the header: mark, klass
  __ bind(initialize_header);

  if (UseBiasedLocking) {
    __ ld_ptr(RinstanceKlass, in_bytes(Klass::prototype_header_offset()), G4_scratch);
  } else {
    __ set((intptr_t)markOopDesc::prototype(), G4_scratch);
  }
  __ st_ptr(G4_scratch, RallocatedObject, oopDesc::mark_offset_in_bytes());       // mark
  __ store_klass_gap(G0, RallocatedObject);         // klass gap if compressed
  __ store_klass(RinstanceKlass, RallocatedObject); // klass (last for cms)

  {
    SkipIfEqual skip_if(
      _masm, G4_scratch, &DTraceAllocProbes, Assembler::zero);
    // Trigger dtrace event
    __ push(atos);
    __ call_VM_leaf(noreg,
       CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_object_alloc), O0);
    __ pop(atos);
  }

  // continue
  __ bind(done);
}



void TemplateTable::newarray() {
  transition(itos, atos);
  __ ldub(Lbcp, 1, O1);
     call_VM(Otos_i, CAST_FROM_FN_PTR(address, InterpreterRuntime::newarray), O1, Otos_i);
}


void TemplateTable::anewarray() {
  transition(itos, atos);
  __ get_constant_pool(O1);
  __ get_2_byte_integer_at_bcp(1, G4_scratch, O2, InterpreterMacroAssembler::Unsigned);
     call_VM(Otos_i, CAST_FROM_FN_PTR(address, InterpreterRuntime::anewarray), O1, O2, Otos_i);
}


void TemplateTable::arraylength() {
  transition(atos, itos);
  Label ok;
  __ verify_oop(Otos_i);
  __ tst(Otos_i);
  __ throw_if_not_1_x( Assembler::notZero, ok );
  __ delayed()->ld(Otos_i, arrayOopDesc::length_offset_in_bytes(), Otos_i);
  __ throw_if_not_2( Interpreter::_throw_NullPointerException_entry, G3_scratch, ok);
}


void TemplateTable::checkcast() {
  transition(atos, atos);
  Label done, is_null, quicked, cast_ok, resolved;
  Register Roffset = G1_scratch;
  Register RobjKlass = O5;
  Register RspecifiedKlass = O4;

  // Check for casting a NULL
  __ br_null(Otos_i, false, Assembler::pn, is_null);
  __ delayed()->nop();

  // Get value klass in RobjKlass
  __ load_klass(Otos_i, RobjKlass); // get value klass

  // Get constant pool tag
  __ get_2_byte_integer_at_bcp(1, Lscratch, Roffset, InterpreterMacroAssembler::Unsigned);

  // See if the checkcast has been quickened
  __ get_cpool_and_tags(Lscratch, G3_scratch);
  __ add(G3_scratch, Array<u1>::base_offset_in_bytes(), G3_scratch);
  __ ldub(G3_scratch, Roffset, G3_scratch);
  __ cmp(G3_scratch, JVM_CONSTANT_Class);
  __ br(Assembler::equal, true, Assembler::pt, quicked);
  __ delayed()->sll(Roffset, LogBytesPerWord, Roffset);

  __ push_ptr(); // save receiver for result, and for GC
  call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::quicken_io_cc) );
  __ get_vm_result_2(RspecifiedKlass);
  __ pop_ptr(Otos_i, G3_scratch); // restore receiver

  __ ba_short(resolved);

  // Extract target class from constant pool
  __ bind(quicked);
  __ load_resolved_klass_at_offset(Lscratch, Roffset, RspecifiedKlass);


  __ bind(resolved);
  __ load_klass(Otos_i, RobjKlass); // get value klass

  // Generate a fast subtype check.  Branch to cast_ok if no
  // failure.  Throw exception if failure.
  __ gen_subtype_check( RobjKlass, RspecifiedKlass, G3_scratch, G4_scratch, G1_scratch, cast_ok );

  // Not a subtype; so must throw exception
  __ throw_if_not_x( Assembler::never, Interpreter::_throw_ClassCastException_entry, G3_scratch );

  __ bind(cast_ok);

  if (ProfileInterpreter) {
    __ ba_short(done);
  }
  __ bind(is_null);
  __ profile_null_seen(G3_scratch);
  __ bind(done);
}


void TemplateTable::instanceof() {
  Label done, is_null, quicked, resolved;
  transition(atos, itos);
  Register Roffset = G1_scratch;
  Register RobjKlass = O5;
  Register RspecifiedKlass = O4;

  // Check for casting a NULL
  __ br_null(Otos_i, false, Assembler::pt, is_null);
  __ delayed()->nop();

  // Get value klass in RobjKlass
  __ load_klass(Otos_i, RobjKlass); // get value klass

  // Get constant pool tag
  __ get_2_byte_integer_at_bcp(1, Lscratch, Roffset, InterpreterMacroAssembler::Unsigned);

  // See if the checkcast has been quickened
  __ get_cpool_and_tags(Lscratch, G3_scratch);
  __ add(G3_scratch, Array<u1>::base_offset_in_bytes(), G3_scratch);
  __ ldub(G3_scratch, Roffset, G3_scratch);
  __ cmp(G3_scratch, JVM_CONSTANT_Class);
  __ br(Assembler::equal, true, Assembler::pt, quicked);
  __ delayed()->sll(Roffset, LogBytesPerWord, Roffset);

  __ push_ptr(); // save receiver for result, and for GC
  call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::quicken_io_cc) );
  __ get_vm_result_2(RspecifiedKlass);
  __ pop_ptr(Otos_i, G3_scratch); // restore receiver

  __ ba_short(resolved);

  // Extract target class from constant pool
  __ bind(quicked);
  __ get_constant_pool(Lscratch);
  __ load_resolved_klass_at_offset(Lscratch, Roffset, RspecifiedKlass);

  __ bind(resolved);
  __ load_klass(Otos_i, RobjKlass); // get value klass

  // Generate a fast subtype check.  Branch to cast_ok if no
  // failure.  Return 0 if failure.
  __ or3(G0, 1, Otos_i);      // set result assuming quick tests succeed
  __ gen_subtype_check( RobjKlass, RspecifiedKlass, G3_scratch, G4_scratch, G1_scratch, done );
  // Not a subtype; return 0;
  __ clr( Otos_i );

  if (ProfileInterpreter) {
    __ ba_short(done);
  }
  __ bind(is_null);
  __ profile_null_seen(G3_scratch);
  __ bind(done);
}

void TemplateTable::_breakpoint() {

   // Note: We get here even if we are single stepping..
   // jbug insists on setting breakpoints at every bytecode
   // even if we are in single step mode.

   transition(vtos, vtos);
   // get the unpatched byte code
   __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::get_original_bytecode_at), Lmethod, Lbcp);
   __ mov(O0, Lbyte_code);

   // post the breakpoint event
   __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::_breakpoint), Lmethod, Lbcp);

   // complete the execution of original bytecode
   __ dispatch_normal(vtos);
}


//----------------------------------------------------------------------------------------------------
// Exceptions

void TemplateTable::athrow() {
  transition(atos, vtos);

  // This works because exception is cached in Otos_i which is same as O0,
  // which is same as what throw_exception_entry_expects
  assert(Otos_i == Oexception, "see explanation above");

  __ verify_oop(Otos_i);
  __ null_check(Otos_i);
  __ throw_if_not_x(Assembler::never, Interpreter::throw_exception_entry(), G3_scratch);
}


//----------------------------------------------------------------------------------------------------
// Synchronization


// See frame_sparc.hpp for monitor block layout.
// Monitor elements are dynamically allocated by growing stack as needed.

void TemplateTable::monitorenter() {
  transition(atos, vtos);
  __ verify_oop(Otos_i);
  // Try to acquire a lock on the object
  // Repeat until succeeded (i.e., until
  // monitorenter returns true).

  {   Label ok;
    __ tst(Otos_i);
    __ throw_if_not_1_x( Assembler::notZero,  ok);
    __ delayed()->mov(Otos_i, Lscratch); // save obj
    __ throw_if_not_2( Interpreter::_throw_NullPointerException_entry, G3_scratch, ok);
  }

  assert(O0 == Otos_i, "Be sure where the object to lock is");

  // find a free slot in the monitor block


  // initialize entry pointer
  __ clr(O1); // points to free slot or NULL

  {
    Label entry, loop, exit;
    __ add( __ top_most_monitor(), O2 ); // last one to check
    __ ba( entry );
    __ delayed()->mov( Lmonitors, O3 ); // first one to check


    __ bind( loop );

    __ verify_oop(O4);          // verify each monitor's oop
    __ tst(O4); // is this entry unused?
    __ movcc( Assembler::zero, false, Assembler::ptr_cc, O3, O1);

    __ cmp(O4, O0); // check if current entry is for same object
    __ brx( Assembler::equal, false, Assembler::pn, exit );
    __ delayed()->inc( O3, frame::interpreter_frame_monitor_size() * wordSize ); // check next one

    __ bind( entry );

    __ cmp( O3, O2 );
    __ brx( Assembler::lessEqualUnsigned, true, Assembler::pt, loop );
    __ delayed()->ld_ptr(O3, BasicObjectLock::obj_offset_in_bytes(), O4);

    __ bind( exit );
  }

  { Label allocated;

    // found free slot?
    __ br_notnull_short(O1, Assembler::pn, allocated);

    __ add_monitor_to_stack( false, O2, O3 );
    __ mov(Lmonitors, O1);

    __ bind(allocated);
  }

  // Increment bcp to point to the next bytecode, so exception handling for async. exceptions work correctly.
  // The object has already been poped from the stack, so the expression stack looks correct.
  __ inc(Lbcp);

  __ st_ptr(O0, O1, BasicObjectLock::obj_offset_in_bytes()); // store object
  __ lock_object(O1, O0);

  // check if there's enough space on the stack for the monitors after locking
  __ generate_stack_overflow_check(0);

  // The bcp has already been incremented. Just need to dispatch to next instruction.
  __ dispatch_next(vtos);
}


void TemplateTable::monitorexit() {
  transition(atos, vtos);
  __ verify_oop(Otos_i);
  __ tst(Otos_i);
  __ throw_if_not_x( Assembler::notZero, Interpreter::_throw_NullPointerException_entry, G3_scratch );

  assert(O0 == Otos_i, "just checking");

  { Label entry, loop, found;
    __ add( __ top_most_monitor(), O2 ); // last one to check
    __ ba(entry);
    // use Lscratch to hold monitor elem to check, start with most recent monitor,
    // By using a local it survives the call to the C routine.
    __ delayed()->mov( Lmonitors, Lscratch );

    __ bind( loop );

    __ verify_oop(O4);          // verify each monitor's oop
    __ cmp(O4, O0); // check if current entry is for desired object
    __ brx( Assembler::equal, true, Assembler::pt, found );
    __ delayed()->mov(Lscratch, O1); // pass found entry as argument to monitorexit

    __ inc( Lscratch, frame::interpreter_frame_monitor_size() * wordSize ); // advance to next

    __ bind( entry );

    __ cmp( Lscratch, O2 );
    __ brx( Assembler::lessEqualUnsigned, true, Assembler::pt, loop );
    __ delayed()->ld_ptr(Lscratch, BasicObjectLock::obj_offset_in_bytes(), O4);

    call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_illegal_monitor_state_exception));
    __ should_not_reach_here();

    __ bind(found);
  }
  __ unlock_object(O1);
}


//----------------------------------------------------------------------------------------------------
// Wide instructions

void TemplateTable::wide() {
  transition(vtos, vtos);
  __ ldub(Lbcp, 1, G3_scratch);// get next bc
  __ sll(G3_scratch, LogBytesPerWord, G3_scratch);
  AddressLiteral ep(Interpreter::_wentry_point);
  __ set(ep, G4_scratch);
  __ ld_ptr(G4_scratch, G3_scratch, G3_scratch);
  __ jmp(G3_scratch, G0);
  __ delayed()->nop();
  // Note: the Lbcp increment step is part of the individual wide bytecode implementations
}


//----------------------------------------------------------------------------------------------------
// Multi arrays

void TemplateTable::multianewarray() {
  transition(vtos, atos);
     // put ndims * wordSize into Lscratch
  __ ldub( Lbcp,     3,               Lscratch);
  __ sll(  Lscratch, Interpreter::logStackElementSize, Lscratch);
     // Lesp points past last_dim, so set to O1 to first_dim address
  __ add(  Lesp,     Lscratch,        O1);
     call_VM(Otos_i, CAST_FROM_FN_PTR(address, InterpreterRuntime::multianewarray), O1);
  __ add(  Lesp,     Lscratch,        Lesp); // pop all dimensions off the stack
}
