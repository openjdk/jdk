/*
 * Copyright 2003-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_templateTable_x86_64.cpp.incl"

#ifndef CC_INTERP

#define __ _masm->

// Platform-dependent initialization

void TemplateTable::pd_initialize() {
  // No amd64 specific initialization
}

// Address computation: local variables

static inline Address iaddress(int n) {
  return Address(r14, Interpreter::local_offset_in_bytes(n));
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

static inline Address iaddress(Register r) {
  return Address(r14, r, Address::times_8);
}

static inline Address laddress(Register r) {
  return Address(r14, r, Address::times_8, Interpreter::local_offset_in_bytes(1));
}

static inline Address faddress(Register r) {
  return iaddress(r);
}

static inline Address daddress(Register r) {
  return laddress(r);
}

static inline Address aaddress(Register r) {
  return iaddress(r);
}

static inline Address at_rsp() {
  return Address(rsp, 0);
}

// At top of Java expression stack which may be different than esp().  It
// isn't for category 1 objects.
static inline Address at_tos   () {
  return Address(rsp,  Interpreter::expr_offset_in_bytes(0));
}

static inline Address at_tos_p1() {
  return Address(rsp,  Interpreter::expr_offset_in_bytes(1));
}

static inline Address at_tos_p2() {
  return Address(rsp,  Interpreter::expr_offset_in_bytes(2));
}

static inline Address at_tos_p3() {
  return Address(rsp,  Interpreter::expr_offset_in_bytes(3));
}

// Condition conversion
static Assembler::Condition j_not(TemplateTable::Condition cc) {
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


// Miscelaneous helper routines
// Store an oop (or NULL) at the address described by obj.
// If val == noreg this means store a NULL

static void do_oop_store(InterpreterMacroAssembler* _masm,
                         Address obj,
                         Register val,
                         BarrierSet::Name barrier,
                         bool precise) {
  assert(val == noreg || val == rax, "parameter is just for looks");
  switch (barrier) {
#ifndef SERIALGC
    case BarrierSet::G1SATBCT:
    case BarrierSet::G1SATBCTLogging:
      {
        // flatten object address if needed
        if (obj.index() == noreg && obj.disp() == 0) {
          if (obj.base() != rdx) {
            __ movq(rdx, obj.base());
          }
        } else {
          __ leaq(rdx, obj);
        }
        __ g1_write_barrier_pre(rdx, r8, rbx, val != noreg);
        if (val == noreg) {
          __ store_heap_oop_null(Address(rdx, 0));
        } else {
          __ store_heap_oop(Address(rdx, 0), val);
          __ g1_write_barrier_post(rdx, val, r8, rbx);
        }

      }
      break;
#endif // SERIALGC
    case BarrierSet::CardTableModRef:
    case BarrierSet::CardTableExtension:
      {
        if (val == noreg) {
          __ store_heap_oop_null(obj);
        } else {
          __ store_heap_oop(obj, val);
          // flatten object address if needed
          if (!precise || (obj.index() == noreg && obj.disp() == 0)) {
            __ store_check(obj.base());
          } else {
            __ leaq(rdx, obj);
            __ store_check(rdx);
          }
        }
      }
      break;
    case BarrierSet::ModRef:
    case BarrierSet::Other:
      if (val == noreg) {
        __ store_heap_oop_null(obj);
      } else {
        __ store_heap_oop(obj, val);
      }
      break;
    default      :
      ShouldNotReachHere();

  }
}

Address TemplateTable::at_bcp(int offset) {
  assert(_desc->uses_bcp(), "inconsistent uses_bcp information");
  return Address(r13, offset);
}

void TemplateTable::patch_bytecode(Bytecodes::Code bytecode, Register bc,
                                   Register scratch,
                                   bool load_bc_into_scratch/*=true*/) {
  if (!RewriteBytecodes) {
    return;
  }
  // the pair bytecodes have already done the load.
  if (load_bc_into_scratch) {
    __ movl(bc, bytecode);
  }
  Label patch_done;
  if (JvmtiExport::can_post_breakpoint()) {
    Label fast_patch;
    // if a breakpoint is present we can't rewrite the stream directly
    __ movzbl(scratch, at_bcp(0));
    __ cmpl(scratch, Bytecodes::_breakpoint);
    __ jcc(Assembler::notEqual, fast_patch);
    __ get_method(scratch);
    // Let breakpoint table handling rewrite to quicker bytecode
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::set_original_bytecode_at), scratch, r13, bc);
#ifndef ASSERT
    __ jmpb(patch_done);
#else
    __ jmp(patch_done);
#endif
    __ bind(fast_patch);
  }
#ifdef ASSERT
  Label okay;
  __ load_unsigned_byte(scratch, at_bcp(0));
  __ cmpl(scratch, (int) Bytecodes::java_code(bytecode));
  __ jcc(Assembler::equal, okay);
  __ cmpl(scratch, bc);
  __ jcc(Assembler::equal, okay);
  __ stop("patching the wrong bytecode");
  __ bind(okay);
#endif
  // patch bytecode
  __ movb(at_bcp(0), bc);
  __ bind(patch_done);
}


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
  __ xorl(rax, rax);
}

void TemplateTable::iconst(int value) {
  transition(vtos, itos);
  if (value == 0) {
    __ xorl(rax, rax);
  } else {
    __ movl(rax, value);
  }
}

void TemplateTable::lconst(int value) {
  transition(vtos, ltos);
  if (value == 0) {
    __ xorl(rax, rax);
  } else {
    __ movl(rax, value);
  }
}

void TemplateTable::fconst(int value) {
  transition(vtos, ftos);
  static float one = 1.0f, two = 2.0f;
  switch (value) {
  case 0:
    __ xorps(xmm0, xmm0);
    break;
  case 1:
    __ movflt(xmm0, ExternalAddress((address) &one));
    break;
  case 2:
    __ movflt(xmm0, ExternalAddress((address) &two));
    break;
  default:
    ShouldNotReachHere();
    break;
  }
}

void TemplateTable::dconst(int value) {
  transition(vtos, dtos);
  static double one = 1.0;
  switch (value) {
  case 0:
    __ xorpd(xmm0, xmm0);
    break;
  case 1:
    __ movdbl(xmm0, ExternalAddress((address) &one));
    break;
  default:
    ShouldNotReachHere();
    break;
  }
}

void TemplateTable::bipush() {
  transition(vtos, itos);
  __ load_signed_byte(rax, at_bcp(1));
}

void TemplateTable::sipush() {
  transition(vtos, itos);
  __ load_unsigned_short(rax, at_bcp(1));
  __ bswapl(rax);
  __ sarl(rax, 16);
}

void TemplateTable::ldc(bool wide) {
  transition(vtos, vtos);
  Label call_ldc, notFloat, notClass, Done;

  if (wide) {
    __ get_unsigned_2_byte_index_at_bcp(rbx, 1);
  } else {
    __ load_unsigned_byte(rbx, at_bcp(1));
  }

  __ get_cpool_and_tags(rcx, rax);
  const int base_offset = constantPoolOopDesc::header_size() * wordSize;
  const int tags_offset = typeArrayOopDesc::header_size(T_BYTE) * wordSize;

  // get type
  __ movzbl(rdx, Address(rax, rbx, Address::times_1, tags_offset));

  // unresolved string - get the resolved string
  __ cmpl(rdx, JVM_CONSTANT_UnresolvedString);
  __ jccb(Assembler::equal, call_ldc);

  // unresolved class - get the resolved class
  __ cmpl(rdx, JVM_CONSTANT_UnresolvedClass);
  __ jccb(Assembler::equal, call_ldc);

  // unresolved class in error state - call into runtime to throw the error
  // from the first resolution attempt
  __ cmpl(rdx, JVM_CONSTANT_UnresolvedClassInError);
  __ jccb(Assembler::equal, call_ldc);

  // resolved class - need to call vm to get java mirror of the class
  __ cmpl(rdx, JVM_CONSTANT_Class);
  __ jcc(Assembler::notEqual, notClass);

  __ bind(call_ldc);
  __ movl(c_rarg1, wide);
  call_VM(rax, CAST_FROM_FN_PTR(address, InterpreterRuntime::ldc), c_rarg1);
  __ push_ptr(rax);
  __ verify_oop(rax);
  __ jmp(Done);

  __ bind(notClass);
  __ cmpl(rdx, JVM_CONSTANT_Float);
  __ jccb(Assembler::notEqual, notFloat);
  // ftos
  __ movflt(xmm0, Address(rcx, rbx, Address::times_8, base_offset));
  __ push_f();
  __ jmp(Done);

  __ bind(notFloat);
#ifdef ASSERT
  {
    Label L;
    __ cmpl(rdx, JVM_CONSTANT_Integer);
    __ jcc(Assembler::equal, L);
    __ cmpl(rdx, JVM_CONSTANT_String);
    __ jcc(Assembler::equal, L);
    __ stop("unexpected tag type in ldc");
    __ bind(L);
  }
#endif
  // atos and itos
  Label isOop;
  __ cmpl(rdx, JVM_CONSTANT_Integer);
  __ jcc(Assembler::notEqual, isOop);
  __ movl(rax, Address(rcx, rbx, Address::times_8, base_offset));
  __ push_i(rax);
  __ jmp(Done);

  __ bind(isOop);
  __ movptr(rax, Address(rcx, rbx, Address::times_8, base_offset));
  __ push_ptr(rax);

  if (VerifyOops) {
    __ verify_oop(rax);
  }

  __ bind(Done);
}

void TemplateTable::ldc2_w() {
  transition(vtos, vtos);
  Label Long, Done;
  __ get_unsigned_2_byte_index_at_bcp(rbx, 1);

  __ get_cpool_and_tags(rcx, rax);
  const int base_offset = constantPoolOopDesc::header_size() * wordSize;
  const int tags_offset = typeArrayOopDesc::header_size(T_BYTE) * wordSize;

  // get type
  __ cmpb(Address(rax, rbx, Address::times_1, tags_offset),
          JVM_CONSTANT_Double);
  __ jccb(Assembler::notEqual, Long);
  // dtos
  __ movdbl(xmm0, Address(rcx, rbx, Address::times_8, base_offset));
  __ push_d();
  __ jmpb(Done);

  __ bind(Long);
  // ltos
  __ movq(rax, Address(rcx, rbx, Address::times_8, base_offset));
  __ push_l();

  __ bind(Done);
}

void TemplateTable::locals_index(Register reg, int offset) {
  __ load_unsigned_byte(reg, at_bcp(offset));
  __ negptr(reg);
}

void TemplateTable::iload() {
  transition(vtos, itos);
  if (RewriteFrequentPairs) {
    Label rewrite, done;
    const Register bc = c_rarg3;
    assert(rbx != bc, "register damaged");

    // get next byte
    __ load_unsigned_byte(rbx,
                          at_bcp(Bytecodes::length_for(Bytecodes::_iload)));
    // if _iload, wait to rewrite to iload2.  We only want to rewrite the
    // last two iloads in a pair.  Comparing against fast_iload means that
    // the next bytecode is neither an iload or a caload, and therefore
    // an iload pair.
    __ cmpl(rbx, Bytecodes::_iload);
    __ jcc(Assembler::equal, done);

    __ cmpl(rbx, Bytecodes::_fast_iload);
    __ movl(bc, Bytecodes::_fast_iload2);
    __ jccb(Assembler::equal, rewrite);

    // if _caload, rewrite to fast_icaload
    __ cmpl(rbx, Bytecodes::_caload);
    __ movl(bc, Bytecodes::_fast_icaload);
    __ jccb(Assembler::equal, rewrite);

    // rewrite so iload doesn't check again.
    __ movl(bc, Bytecodes::_fast_iload);

    // rewrite
    // bc: fast bytecode
    __ bind(rewrite);
    patch_bytecode(Bytecodes::_iload, bc, rbx, false);
    __ bind(done);
  }

  // Get the local value into tos
  locals_index(rbx);
  __ movl(rax, iaddress(rbx));
}

void TemplateTable::fast_iload2() {
  transition(vtos, itos);
  locals_index(rbx);
  __ movl(rax, iaddress(rbx));
  __ push(itos);
  locals_index(rbx, 3);
  __ movl(rax, iaddress(rbx));
}

void TemplateTable::fast_iload() {
  transition(vtos, itos);
  locals_index(rbx);
  __ movl(rax, iaddress(rbx));
}

void TemplateTable::lload() {
  transition(vtos, ltos);
  locals_index(rbx);
  __ movq(rax, laddress(rbx));
}

void TemplateTable::fload() {
  transition(vtos, ftos);
  locals_index(rbx);
  __ movflt(xmm0, faddress(rbx));
}

void TemplateTable::dload() {
  transition(vtos, dtos);
  locals_index(rbx);
  __ movdbl(xmm0, daddress(rbx));
}

void TemplateTable::aload() {
  transition(vtos, atos);
  locals_index(rbx);
  __ movptr(rax, aaddress(rbx));
}

void TemplateTable::locals_index_wide(Register reg) {
  __ movl(reg, at_bcp(2));
  __ bswapl(reg);
  __ shrl(reg, 16);
  __ negptr(reg);
}

void TemplateTable::wide_iload() {
  transition(vtos, itos);
  locals_index_wide(rbx);
  __ movl(rax, iaddress(rbx));
}

void TemplateTable::wide_lload() {
  transition(vtos, ltos);
  locals_index_wide(rbx);
  __ movq(rax, laddress(rbx));
}

void TemplateTable::wide_fload() {
  transition(vtos, ftos);
  locals_index_wide(rbx);
  __ movflt(xmm0, faddress(rbx));
}

void TemplateTable::wide_dload() {
  transition(vtos, dtos);
  locals_index_wide(rbx);
  __ movdbl(xmm0, daddress(rbx));
}

void TemplateTable::wide_aload() {
  transition(vtos, atos);
  locals_index_wide(rbx);
  __ movptr(rax, aaddress(rbx));
}

void TemplateTable::index_check(Register array, Register index) {
  // destroys rbx
  // check array
  __ null_check(array, arrayOopDesc::length_offset_in_bytes());
  // sign extend index for use by indexed load
  __ movl2ptr(index, index);
  // check index
  __ cmpl(index, Address(array, arrayOopDesc::length_offset_in_bytes()));
  if (index != rbx) {
    // ??? convention: move aberrant index into ebx for exception message
    assert(rbx != array, "different registers");
    __ movl(rbx, index);
  }
  __ jump_cc(Assembler::aboveEqual,
             ExternalAddress(Interpreter::_throw_ArrayIndexOutOfBoundsException_entry));
}

void TemplateTable::iaload() {
  transition(itos, itos);
  __ pop_ptr(rdx);
  // eax: index
  // rdx: array
  index_check(rdx, rax); // kills rbx
  __ movl(rax, Address(rdx, rax,
                       Address::times_4,
                       arrayOopDesc::base_offset_in_bytes(T_INT)));
}

void TemplateTable::laload() {
  transition(itos, ltos);
  __ pop_ptr(rdx);
  // eax: index
  // rdx: array
  index_check(rdx, rax); // kills rbx
  __ movq(rax, Address(rdx, rbx,
                       Address::times_8,
                       arrayOopDesc::base_offset_in_bytes(T_LONG)));
}

void TemplateTable::faload() {
  transition(itos, ftos);
  __ pop_ptr(rdx);
  // eax: index
  // rdx: array
  index_check(rdx, rax); // kills rbx
  __ movflt(xmm0, Address(rdx, rax,
                         Address::times_4,
                         arrayOopDesc::base_offset_in_bytes(T_FLOAT)));
}

void TemplateTable::daload() {
  transition(itos, dtos);
  __ pop_ptr(rdx);
  // eax: index
  // rdx: array
  index_check(rdx, rax); // kills rbx
  __ movdbl(xmm0, Address(rdx, rax,
                          Address::times_8,
                          arrayOopDesc::base_offset_in_bytes(T_DOUBLE)));
}

void TemplateTable::aaload() {
  transition(itos, atos);
  __ pop_ptr(rdx);
  // eax: index
  // rdx: array
  index_check(rdx, rax); // kills rbx
  __ load_heap_oop(rax, Address(rdx, rax,
                                UseCompressedOops ? Address::times_4 : Address::times_8,
                                arrayOopDesc::base_offset_in_bytes(T_OBJECT)));
}

void TemplateTable::baload() {
  transition(itos, itos);
  __ pop_ptr(rdx);
  // eax: index
  // rdx: array
  index_check(rdx, rax); // kills rbx
  __ load_signed_byte(rax,
                      Address(rdx, rax,
                              Address::times_1,
                              arrayOopDesc::base_offset_in_bytes(T_BYTE)));
}

void TemplateTable::caload() {
  transition(itos, itos);
  __ pop_ptr(rdx);
  // eax: index
  // rdx: array
  index_check(rdx, rax); // kills rbx
  __ load_unsigned_short(rax,
                         Address(rdx, rax,
                                 Address::times_2,
                                 arrayOopDesc::base_offset_in_bytes(T_CHAR)));
}

// iload followed by caload frequent pair
void TemplateTable::fast_icaload() {
  transition(vtos, itos);
  // load index out of locals
  locals_index(rbx);
  __ movl(rax, iaddress(rbx));

  // eax: index
  // rdx: array
  __ pop_ptr(rdx);
  index_check(rdx, rax); // kills rbx
  __ load_unsigned_short(rax,
                         Address(rdx, rax,
                                 Address::times_2,
                                 arrayOopDesc::base_offset_in_bytes(T_CHAR)));
}

void TemplateTable::saload() {
  transition(itos, itos);
  __ pop_ptr(rdx);
  // eax: index
  // rdx: array
  index_check(rdx, rax); // kills rbx
  __ load_signed_short(rax,
                       Address(rdx, rax,
                               Address::times_2,
                               arrayOopDesc::base_offset_in_bytes(T_SHORT)));
}

void TemplateTable::iload(int n) {
  transition(vtos, itos);
  __ movl(rax, iaddress(n));
}

void TemplateTable::lload(int n) {
  transition(vtos, ltos);
  __ movq(rax, laddress(n));
}

void TemplateTable::fload(int n) {
  transition(vtos, ftos);
  __ movflt(xmm0, faddress(n));
}

void TemplateTable::dload(int n) {
  transition(vtos, dtos);
  __ movdbl(xmm0, daddress(n));
}

void TemplateTable::aload(int n) {
  transition(vtos, atos);
  __ movptr(rax, aaddress(n));
}

void TemplateTable::aload_0() {
  transition(vtos, atos);
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
  if (RewriteFrequentPairs) {
    Label rewrite, done;
    const Register bc = c_rarg3;
    assert(rbx != bc, "register damaged");
    // get next byte
    __ load_unsigned_byte(rbx,
                          at_bcp(Bytecodes::length_for(Bytecodes::_aload_0)));

    // do actual aload_0
    aload(0);

    // if _getfield then wait with rewrite
    __ cmpl(rbx, Bytecodes::_getfield);
    __ jcc(Assembler::equal, done);

    // if _igetfield then reqrite to _fast_iaccess_0
    assert(Bytecodes::java_code(Bytecodes::_fast_iaccess_0) ==
           Bytecodes::_aload_0,
           "fix bytecode definition");
    __ cmpl(rbx, Bytecodes::_fast_igetfield);
    __ movl(bc, Bytecodes::_fast_iaccess_0);
    __ jccb(Assembler::equal, rewrite);

    // if _agetfield then reqrite to _fast_aaccess_0
    assert(Bytecodes::java_code(Bytecodes::_fast_aaccess_0) ==
           Bytecodes::_aload_0,
           "fix bytecode definition");
    __ cmpl(rbx, Bytecodes::_fast_agetfield);
    __ movl(bc, Bytecodes::_fast_aaccess_0);
    __ jccb(Assembler::equal, rewrite);

    // if _fgetfield then reqrite to _fast_faccess_0
    assert(Bytecodes::java_code(Bytecodes::_fast_faccess_0) ==
           Bytecodes::_aload_0,
           "fix bytecode definition");
    __ cmpl(rbx, Bytecodes::_fast_fgetfield);
    __ movl(bc, Bytecodes::_fast_faccess_0);
    __ jccb(Assembler::equal, rewrite);

    // else rewrite to _fast_aload0
    assert(Bytecodes::java_code(Bytecodes::_fast_aload_0) ==
           Bytecodes::_aload_0,
           "fix bytecode definition");
    __ movl(bc, Bytecodes::_fast_aload_0);

    // rewrite
    // bc: fast bytecode
    __ bind(rewrite);
    patch_bytecode(Bytecodes::_aload_0, bc, rbx, false);

    __ bind(done);
  } else {
    aload(0);
  }
}

void TemplateTable::istore() {
  transition(itos, vtos);
  locals_index(rbx);
  __ movl(iaddress(rbx), rax);
}

void TemplateTable::lstore() {
  transition(ltos, vtos);
  locals_index(rbx);
  __ movq(laddress(rbx), rax);
}

void TemplateTable::fstore() {
  transition(ftos, vtos);
  locals_index(rbx);
  __ movflt(faddress(rbx), xmm0);
}

void TemplateTable::dstore() {
  transition(dtos, vtos);
  locals_index(rbx);
  __ movdbl(daddress(rbx), xmm0);
}

void TemplateTable::astore() {
  transition(vtos, vtos);
  __ pop_ptr(rax);
  locals_index(rbx);
  __ movptr(aaddress(rbx), rax);
}

void TemplateTable::wide_istore() {
  transition(vtos, vtos);
  __ pop_i();
  locals_index_wide(rbx);
  __ movl(iaddress(rbx), rax);
}

void TemplateTable::wide_lstore() {
  transition(vtos, vtos);
  __ pop_l();
  locals_index_wide(rbx);
  __ movq(laddress(rbx), rax);
}

void TemplateTable::wide_fstore() {
  transition(vtos, vtos);
  __ pop_f();
  locals_index_wide(rbx);
  __ movflt(faddress(rbx), xmm0);
}

void TemplateTable::wide_dstore() {
  transition(vtos, vtos);
  __ pop_d();
  locals_index_wide(rbx);
  __ movdbl(daddress(rbx), xmm0);
}

void TemplateTable::wide_astore() {
  transition(vtos, vtos);
  __ pop_ptr(rax);
  locals_index_wide(rbx);
  __ movptr(aaddress(rbx), rax);
}

void TemplateTable::iastore() {
  transition(itos, vtos);
  __ pop_i(rbx);
  __ pop_ptr(rdx);
  // eax: value
  // ebx: index
  // rdx: array
  index_check(rdx, rbx); // prefer index in ebx
  __ movl(Address(rdx, rbx,
                  Address::times_4,
                  arrayOopDesc::base_offset_in_bytes(T_INT)),
          rax);
}

void TemplateTable::lastore() {
  transition(ltos, vtos);
  __ pop_i(rbx);
  __ pop_ptr(rdx);
  // rax: value
  // ebx: index
  // rdx: array
  index_check(rdx, rbx); // prefer index in ebx
  __ movq(Address(rdx, rbx,
                  Address::times_8,
                  arrayOopDesc::base_offset_in_bytes(T_LONG)),
          rax);
}

void TemplateTable::fastore() {
  transition(ftos, vtos);
  __ pop_i(rbx);
  __ pop_ptr(rdx);
  // xmm0: value
  // ebx:  index
  // rdx:  array
  index_check(rdx, rbx); // prefer index in ebx
  __ movflt(Address(rdx, rbx,
                   Address::times_4,
                   arrayOopDesc::base_offset_in_bytes(T_FLOAT)),
           xmm0);
}

void TemplateTable::dastore() {
  transition(dtos, vtos);
  __ pop_i(rbx);
  __ pop_ptr(rdx);
  // xmm0: value
  // ebx:  index
  // rdx:  array
  index_check(rdx, rbx); // prefer index in ebx
  __ movdbl(Address(rdx, rbx,
                   Address::times_8,
                   arrayOopDesc::base_offset_in_bytes(T_DOUBLE)),
           xmm0);
}

void TemplateTable::aastore() {
  Label is_null, ok_is_subtype, done;
  transition(vtos, vtos);
  // stack: ..., array, index, value
  __ movptr(rax, at_tos());    // value
  __ movl(rcx, at_tos_p1()); // index
  __ movptr(rdx, at_tos_p2()); // array

  Address element_address(rdx, rcx,
                          UseCompressedOops? Address::times_4 : Address::times_8,
                          arrayOopDesc::base_offset_in_bytes(T_OBJECT));

  index_check(rdx, rcx);     // kills rbx
  // do array store check - check for NULL value first
  __ testptr(rax, rax);
  __ jcc(Assembler::zero, is_null);

  // Move subklass into rbx
  __ load_klass(rbx, rax);
  // Move superklass into rax
  __ load_klass(rax, rdx);
  __ movptr(rax, Address(rax,
                         sizeof(oopDesc) +
                         objArrayKlass::element_klass_offset_in_bytes()));
  // Compress array + index*oopSize + 12 into a single register.  Frees rcx.
  __ lea(rdx, element_address);

  // Generate subtype check.  Blows rcx, rdi
  // Superklass in rax.  Subklass in rbx.
  __ gen_subtype_check(rbx, ok_is_subtype);

  // Come here on failure
  // object is at TOS
  __ jump(ExternalAddress(Interpreter::_throw_ArrayStoreException_entry));

  // Come here on success
  __ bind(ok_is_subtype);

  // Get the value we will store
  __ movptr(rax, at_tos());
  // Now store using the appropriate barrier
  do_oop_store(_masm, Address(rdx, 0), rax, _bs->kind(), true);
  __ jmp(done);

  // Have a NULL in rax, rdx=array, ecx=index.  Store NULL at ary[idx]
  __ bind(is_null);
  __ profile_null_seen(rbx);

  // Store a NULL
  do_oop_store(_masm, element_address, noreg, _bs->kind(), true);

  // Pop stack arguments
  __ bind(done);
  __ addptr(rsp, 3 * Interpreter::stackElementSize);
}

void TemplateTable::bastore() {
  transition(itos, vtos);
  __ pop_i(rbx);
  __ pop_ptr(rdx);
  // eax: value
  // ebx: index
  // rdx: array
  index_check(rdx, rbx); // prefer index in ebx
  __ movb(Address(rdx, rbx,
                  Address::times_1,
                  arrayOopDesc::base_offset_in_bytes(T_BYTE)),
          rax);
}

void TemplateTable::castore() {
  transition(itos, vtos);
  __ pop_i(rbx);
  __ pop_ptr(rdx);
  // eax: value
  // ebx: index
  // rdx: array
  index_check(rdx, rbx);  // prefer index in ebx
  __ movw(Address(rdx, rbx,
                  Address::times_2,
                  arrayOopDesc::base_offset_in_bytes(T_CHAR)),
          rax);
}

void TemplateTable::sastore() {
  castore();
}

void TemplateTable::istore(int n) {
  transition(itos, vtos);
  __ movl(iaddress(n), rax);
}

void TemplateTable::lstore(int n) {
  transition(ltos, vtos);
  __ movq(laddress(n), rax);
}

void TemplateTable::fstore(int n) {
  transition(ftos, vtos);
  __ movflt(faddress(n), xmm0);
}

void TemplateTable::dstore(int n) {
  transition(dtos, vtos);
  __ movdbl(daddress(n), xmm0);
}

void TemplateTable::astore(int n) {
  transition(vtos, vtos);
  __ pop_ptr(rax);
  __ movptr(aaddress(n), rax);
}

void TemplateTable::pop() {
  transition(vtos, vtos);
  __ addptr(rsp, Interpreter::stackElementSize);
}

void TemplateTable::pop2() {
  transition(vtos, vtos);
  __ addptr(rsp, 2 * Interpreter::stackElementSize);
}

void TemplateTable::dup() {
  transition(vtos, vtos);
  __ load_ptr(0, rax);
  __ push_ptr(rax);
  // stack: ..., a, a
}

void TemplateTable::dup_x1() {
  transition(vtos, vtos);
  // stack: ..., a, b
  __ load_ptr( 0, rax);  // load b
  __ load_ptr( 1, rcx);  // load a
  __ store_ptr(1, rax);  // store b
  __ store_ptr(0, rcx);  // store a
  __ push_ptr(rax);      // push b
  // stack: ..., b, a, b
}

void TemplateTable::dup_x2() {
  transition(vtos, vtos);
  // stack: ..., a, b, c
  __ load_ptr( 0, rax);  // load c
  __ load_ptr( 2, rcx);  // load a
  __ store_ptr(2, rax);  // store c in a
  __ push_ptr(rax);      // push c
  // stack: ..., c, b, c, c
  __ load_ptr( 2, rax);  // load b
  __ store_ptr(2, rcx);  // store a in b
  // stack: ..., c, a, c, c
  __ store_ptr(1, rax);  // store b in c
  // stack: ..., c, a, b, c
}

void TemplateTable::dup2() {
  transition(vtos, vtos);
  // stack: ..., a, b
  __ load_ptr(1, rax);  // load a
  __ push_ptr(rax);     // push a
  __ load_ptr(1, rax);  // load b
  __ push_ptr(rax);     // push b
  // stack: ..., a, b, a, b
}

void TemplateTable::dup2_x1() {
  transition(vtos, vtos);
  // stack: ..., a, b, c
  __ load_ptr( 0, rcx);  // load c
  __ load_ptr( 1, rax);  // load b
  __ push_ptr(rax);      // push b
  __ push_ptr(rcx);      // push c
  // stack: ..., a, b, c, b, c
  __ store_ptr(3, rcx);  // store c in b
  // stack: ..., a, c, c, b, c
  __ load_ptr( 4, rcx);  // load a
  __ store_ptr(2, rcx);  // store a in 2nd c
  // stack: ..., a, c, a, b, c
  __ store_ptr(4, rax);  // store b in a
  // stack: ..., b, c, a, b, c
}

void TemplateTable::dup2_x2() {
  transition(vtos, vtos);
  // stack: ..., a, b, c, d
  __ load_ptr( 0, rcx);  // load d
  __ load_ptr( 1, rax);  // load c
  __ push_ptr(rax);      // push c
  __ push_ptr(rcx);      // push d
  // stack: ..., a, b, c, d, c, d
  __ load_ptr( 4, rax);  // load b
  __ store_ptr(2, rax);  // store b in d
  __ store_ptr(4, rcx);  // store d in b
  // stack: ..., a, d, c, b, c, d
  __ load_ptr( 5, rcx);  // load a
  __ load_ptr( 3, rax);  // load c
  __ store_ptr(3, rcx);  // store a in c
  __ store_ptr(5, rax);  // store c in a
  // stack: ..., c, d, a, b, c, d
}

void TemplateTable::swap() {
  transition(vtos, vtos);
  // stack: ..., a, b
  __ load_ptr( 1, rcx);  // load a
  __ load_ptr( 0, rax);  // load b
  __ store_ptr(0, rcx);  // store a in b
  __ store_ptr(1, rax);  // store b in a
  // stack: ..., b, a
}

void TemplateTable::iop2(Operation op) {
  transition(itos, itos);
  switch (op) {
  case add  :                    __ pop_i(rdx); __ addl (rax, rdx); break;
  case sub  : __ movl(rdx, rax); __ pop_i(rax); __ subl (rax, rdx); break;
  case mul  :                    __ pop_i(rdx); __ imull(rax, rdx); break;
  case _and :                    __ pop_i(rdx); __ andl (rax, rdx); break;
  case _or  :                    __ pop_i(rdx); __ orl  (rax, rdx); break;
  case _xor :                    __ pop_i(rdx); __ xorl (rax, rdx); break;
  case shl  : __ movl(rcx, rax); __ pop_i(rax); __ shll (rax);      break;
  case shr  : __ movl(rcx, rax); __ pop_i(rax); __ sarl (rax);      break;
  case ushr : __ movl(rcx, rax); __ pop_i(rax); __ shrl (rax);      break;
  default   : ShouldNotReachHere();
  }
}

void TemplateTable::lop2(Operation op) {
  transition(ltos, ltos);
  switch (op) {
  case add  :                    __ pop_l(rdx); __ addptr(rax, rdx); break;
  case sub  : __ mov(rdx, rax);  __ pop_l(rax); __ subptr(rax, rdx); break;
  case _and :                    __ pop_l(rdx); __ andptr(rax, rdx); break;
  case _or  :                    __ pop_l(rdx); __ orptr (rax, rdx); break;
  case _xor :                    __ pop_l(rdx); __ xorptr(rax, rdx); break;
  default   : ShouldNotReachHere();
  }
}

void TemplateTable::idiv() {
  transition(itos, itos);
  __ movl(rcx, rax);
  __ pop_i(rax);
  // Note: could xor eax and ecx and compare with (-1 ^ min_int). If
  //       they are not equal, one could do a normal division (no correction
  //       needed), which may speed up this implementation for the common case.
  //       (see also JVM spec., p.243 & p.271)
  __ corrected_idivl(rcx);
}

void TemplateTable::irem() {
  transition(itos, itos);
  __ movl(rcx, rax);
  __ pop_i(rax);
  // Note: could xor eax and ecx and compare with (-1 ^ min_int). If
  //       they are not equal, one could do a normal division (no correction
  //       needed), which may speed up this implementation for the common case.
  //       (see also JVM spec., p.243 & p.271)
  __ corrected_idivl(rcx);
  __ movl(rax, rdx);
}

void TemplateTable::lmul() {
  transition(ltos, ltos);
  __ pop_l(rdx);
  __ imulq(rax, rdx);
}

void TemplateTable::ldiv() {
  transition(ltos, ltos);
  __ mov(rcx, rax);
  __ pop_l(rax);
  // generate explicit div0 check
  __ testq(rcx, rcx);
  __ jump_cc(Assembler::zero,
             ExternalAddress(Interpreter::_throw_ArithmeticException_entry));
  // Note: could xor rax and rcx and compare with (-1 ^ min_int). If
  //       they are not equal, one could do a normal division (no correction
  //       needed), which may speed up this implementation for the common case.
  //       (see also JVM spec., p.243 & p.271)
  __ corrected_idivq(rcx); // kills rbx
}

void TemplateTable::lrem() {
  transition(ltos, ltos);
  __ mov(rcx, rax);
  __ pop_l(rax);
  __ testq(rcx, rcx);
  __ jump_cc(Assembler::zero,
             ExternalAddress(Interpreter::_throw_ArithmeticException_entry));
  // Note: could xor rax and rcx and compare with (-1 ^ min_int). If
  //       they are not equal, one could do a normal division (no correction
  //       needed), which may speed up this implementation for the common case.
  //       (see also JVM spec., p.243 & p.271)
  __ corrected_idivq(rcx); // kills rbx
  __ mov(rax, rdx);
}

void TemplateTable::lshl() {
  transition(itos, ltos);
  __ movl(rcx, rax);                             // get shift count
  __ pop_l(rax);                                 // get shift value
  __ shlq(rax);
}

void TemplateTable::lshr() {
  transition(itos, ltos);
  __ movl(rcx, rax);                             // get shift count
  __ pop_l(rax);                                 // get shift value
  __ sarq(rax);
}

void TemplateTable::lushr() {
  transition(itos, ltos);
  __ movl(rcx, rax);                             // get shift count
  __ pop_l(rax);                                 // get shift value
  __ shrq(rax);
}

void TemplateTable::fop2(Operation op) {
  transition(ftos, ftos);
  switch (op) {
  case add:
    __ addss(xmm0, at_rsp());
    __ addptr(rsp, Interpreter::stackElementSize);
    break;
  case sub:
    __ movflt(xmm1, xmm0);
    __ pop_f(xmm0);
    __ subss(xmm0, xmm1);
    break;
  case mul:
    __ mulss(xmm0, at_rsp());
    __ addptr(rsp, Interpreter::stackElementSize);
    break;
  case div:
    __ movflt(xmm1, xmm0);
    __ pop_f(xmm0);
    __ divss(xmm0, xmm1);
    break;
  case rem:
    __ movflt(xmm1, xmm0);
    __ pop_f(xmm0);
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::frem), 2);
    break;
  default:
    ShouldNotReachHere();
    break;
  }
}

void TemplateTable::dop2(Operation op) {
  transition(dtos, dtos);
  switch (op) {
  case add:
    __ addsd(xmm0, at_rsp());
    __ addptr(rsp, 2 * Interpreter::stackElementSize);
    break;
  case sub:
    __ movdbl(xmm1, xmm0);
    __ pop_d(xmm0);
    __ subsd(xmm0, xmm1);
    break;
  case mul:
    __ mulsd(xmm0, at_rsp());
    __ addptr(rsp, 2 * Interpreter::stackElementSize);
    break;
  case div:
    __ movdbl(xmm1, xmm0);
    __ pop_d(xmm0);
    __ divsd(xmm0, xmm1);
    break;
  case rem:
    __ movdbl(xmm1, xmm0);
    __ pop_d(xmm0);
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::drem), 2);
    break;
  default:
    ShouldNotReachHere();
    break;
  }
}

void TemplateTable::ineg() {
  transition(itos, itos);
  __ negl(rax);
}

void TemplateTable::lneg() {
  transition(ltos, ltos);
  __ negq(rax);
}

// Note: 'double' and 'long long' have 32-bits alignment on x86.
static jlong* double_quadword(jlong *adr, jlong lo, jlong hi) {
  // Use the expression (adr)&(~0xF) to provide 128-bits aligned address
  // of 128-bits operands for SSE instructions.
  jlong *operand = (jlong*)(((intptr_t)adr)&((intptr_t)(~0xF)));
  // Store the value to a 128-bits operand.
  operand[0] = lo;
  operand[1] = hi;
  return operand;
}

// Buffer for 128-bits masks used by SSE instructions.
static jlong float_signflip_pool[2*2];
static jlong double_signflip_pool[2*2];

void TemplateTable::fneg() {
  transition(ftos, ftos);
  static jlong *float_signflip  = double_quadword(&float_signflip_pool[1], 0x8000000080000000, 0x8000000080000000);
  __ xorps(xmm0, ExternalAddress((address) float_signflip));
}

void TemplateTable::dneg() {
  transition(dtos, dtos);
  static jlong *double_signflip  = double_quadword(&double_signflip_pool[1], 0x8000000000000000, 0x8000000000000000);
  __ xorpd(xmm0, ExternalAddress((address) double_signflip));
}

void TemplateTable::iinc() {
  transition(vtos, vtos);
  __ load_signed_byte(rdx, at_bcp(2)); // get constant
  locals_index(rbx);
  __ addl(iaddress(rbx), rdx);
}

void TemplateTable::wide_iinc() {
  transition(vtos, vtos);
  __ movl(rdx, at_bcp(4)); // get constant
  locals_index_wide(rbx);
  __ bswapl(rdx); // swap bytes & sign-extend constant
  __ sarl(rdx, 16);
  __ addl(iaddress(rbx), rdx);
  // Note: should probably use only one movl to get both
  //       the index and the constant -> fix this
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

  static const int64_t is_nan = 0x8000000000000000L;

  // Conversion
  switch (bytecode()) {
  case Bytecodes::_i2l:
    __ movslq(rax, rax);
    break;
  case Bytecodes::_i2f:
    __ cvtsi2ssl(xmm0, rax);
    break;
  case Bytecodes::_i2d:
    __ cvtsi2sdl(xmm0, rax);
    break;
  case Bytecodes::_i2b:
    __ movsbl(rax, rax);
    break;
  case Bytecodes::_i2c:
    __ movzwl(rax, rax);
    break;
  case Bytecodes::_i2s:
    __ movswl(rax, rax);
    break;
  case Bytecodes::_l2i:
    __ movl(rax, rax);
    break;
  case Bytecodes::_l2f:
    __ cvtsi2ssq(xmm0, rax);
    break;
  case Bytecodes::_l2d:
    __ cvtsi2sdq(xmm0, rax);
    break;
  case Bytecodes::_f2i:
  {
    Label L;
    __ cvttss2sil(rax, xmm0);
    __ cmpl(rax, 0x80000000); // NaN or overflow/underflow?
    __ jcc(Assembler::notEqual, L);
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::f2i), 1);
    __ bind(L);
  }
    break;
  case Bytecodes::_f2l:
  {
    Label L;
    __ cvttss2siq(rax, xmm0);
    // NaN or overflow/underflow?
    __ cmp64(rax, ExternalAddress((address) &is_nan));
    __ jcc(Assembler::notEqual, L);
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::f2l), 1);
    __ bind(L);
  }
    break;
  case Bytecodes::_f2d:
    __ cvtss2sd(xmm0, xmm0);
    break;
  case Bytecodes::_d2i:
  {
    Label L;
    __ cvttsd2sil(rax, xmm0);
    __ cmpl(rax, 0x80000000); // NaN or overflow/underflow?
    __ jcc(Assembler::notEqual, L);
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::d2i), 1);
    __ bind(L);
  }
    break;
  case Bytecodes::_d2l:
  {
    Label L;
    __ cvttsd2siq(rax, xmm0);
    // NaN or overflow/underflow?
    __ cmp64(rax, ExternalAddress((address) &is_nan));
    __ jcc(Assembler::notEqual, L);
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::d2l), 1);
    __ bind(L);
  }
    break;
  case Bytecodes::_d2f:
    __ cvtsd2ss(xmm0, xmm0);
    break;
  default:
    ShouldNotReachHere();
  }
}

void TemplateTable::lcmp() {
  transition(ltos, itos);
  Label done;
  __ pop_l(rdx);
  __ cmpq(rdx, rax);
  __ movl(rax, -1);
  __ jccb(Assembler::less, done);
  __ setb(Assembler::notEqual, rax);
  __ movzbl(rax, rax);
  __ bind(done);
}

void TemplateTable::float_cmp(bool is_float, int unordered_result) {
  Label done;
  if (is_float) {
    // XXX get rid of pop here, use ... reg, mem32
    __ pop_f(xmm1);
    __ ucomiss(xmm1, xmm0);
  } else {
    // XXX get rid of pop here, use ... reg, mem64
    __ pop_d(xmm1);
    __ ucomisd(xmm1, xmm0);
  }
  if (unordered_result < 0) {
    __ movl(rax, -1);
    __ jccb(Assembler::parity, done);
    __ jccb(Assembler::below, done);
    __ setb(Assembler::notEqual, rdx);
    __ movzbl(rax, rdx);
  } else {
    __ movl(rax, 1);
    __ jccb(Assembler::parity, done);
    __ jccb(Assembler::above, done);
    __ movl(rax, 0);
    __ jccb(Assembler::equal, done);
    __ decrementl(rax);
  }
  __ bind(done);
}

void TemplateTable::branch(bool is_jsr, bool is_wide) {
  __ get_method(rcx); // rcx holds method
  __ profile_taken_branch(rax, rbx); // rax holds updated MDP, rbx
                                     // holds bumped taken count

  const ByteSize be_offset = methodOopDesc::backedge_counter_offset() +
                             InvocationCounter::counter_offset();
  const ByteSize inv_offset = methodOopDesc::invocation_counter_offset() +
                              InvocationCounter::counter_offset();
  const int method_offset = frame::interpreter_frame_method_offset * wordSize;

  // Load up edx with the branch displacement
  __ movl(rdx, at_bcp(1));
  __ bswapl(rdx);

  if (!is_wide) {
    __ sarl(rdx, 16);
  }
  __ movl2ptr(rdx, rdx);

  // Handle all the JSR stuff here, then exit.
  // It's much shorter and cleaner than intermingling with the non-JSR
  // normal-branch stuff occurring below.
  if (is_jsr) {
    // Pre-load the next target bytecode into rbx
    __ load_unsigned_byte(rbx, Address(r13, rdx, Address::times_1, 0));

    // compute return address as bci in rax
    __ lea(rax, at_bcp((is_wide ? 5 : 3) -
                        in_bytes(constMethodOopDesc::codes_offset())));
    __ subptr(rax, Address(rcx, methodOopDesc::const_offset()));
    // Adjust the bcp in r13 by the displacement in rdx
    __ addptr(r13, rdx);
    // jsr returns atos that is not an oop
    __ push_i(rax);
    __ dispatch_only(vtos);
    return;
  }

  // Normal (non-jsr) branch handling

  // Adjust the bcp in r13 by the displacement in rdx
  __ addptr(r13, rdx);

  assert(UseLoopCounter || !UseOnStackReplacement,
         "on-stack-replacement requires loop counters");
  Label backedge_counter_overflow;
  Label profile_method;
  Label dispatch;
  if (UseLoopCounter) {
    // increment backedge counter for backward branches
    // rax: MDO
    // ebx: MDO bumped taken-count
    // rcx: method
    // rdx: target offset
    // r13: target bcp
    // r14: locals pointer
    __ testl(rdx, rdx);             // check if forward or backward branch
    __ jcc(Assembler::positive, dispatch); // count only if backward branch

    // increment counter
    __ movl(rax, Address(rcx, be_offset));        // load backedge counter
    __ incrementl(rax, InvocationCounter::count_increment); // increment
                                                            // counter
    __ movl(Address(rcx, be_offset), rax);        // store counter

    __ movl(rax, Address(rcx, inv_offset));    // load invocation counter
    __ andl(rax, InvocationCounter::count_mask_value); // and the status bits
    __ addl(rax, Address(rcx, be_offset));        // add both counters

    if (ProfileInterpreter) {
      // Test to see if we should create a method data oop
      __ cmp32(rax,
               ExternalAddress((address) &InvocationCounter::InterpreterProfileLimit));
      __ jcc(Assembler::less, dispatch);

      // if no method data exists, go to profile method
      __ test_method_data_pointer(rax, profile_method);

      if (UseOnStackReplacement) {
        // check for overflow against ebx which is the MDO taken count
        __ cmp32(rbx,
                 ExternalAddress((address) &InvocationCounter::InterpreterBackwardBranchLimit));
        __ jcc(Assembler::below, dispatch);

        // When ProfileInterpreter is on, the backedge_count comes
        // from the methodDataOop, which value does not get reset on
        // the call to frequency_counter_overflow().  To avoid
        // excessive calls to the overflow routine while the method is
        // being compiled, add a second test to make sure the overflow
        // function is called only once every overflow_frequency.
        const int overflow_frequency = 1024;
        __ andl(rbx, overflow_frequency - 1);
        __ jcc(Assembler::zero, backedge_counter_overflow);

      }
    } else {
      if (UseOnStackReplacement) {
        // check for overflow against eax, which is the sum of the
        // counters
        __ cmp32(rax,
                 ExternalAddress((address) &InvocationCounter::InterpreterBackwardBranchLimit));
        __ jcc(Assembler::aboveEqual, backedge_counter_overflow);

      }
    }
    __ bind(dispatch);
  }

  // Pre-load the next target bytecode into rbx
  __ load_unsigned_byte(rbx, Address(r13, 0));

  // continue with the bytecode @ target
  // eax: return bci for jsr's, unused otherwise
  // ebx: target bytecode
  // r13: target bcp
  __ dispatch_only(vtos);

  if (UseLoopCounter) {
    if (ProfileInterpreter) {
      // Out-of-line code to allocate method data oop.
      __ bind(profile_method);
      __ call_VM(noreg,
                 CAST_FROM_FN_PTR(address,
                                  InterpreterRuntime::profile_method), r13);
      __ load_unsigned_byte(rbx, Address(r13, 0));  // restore target bytecode
      __ movptr(rcx, Address(rbp, method_offset));
      __ movptr(rcx, Address(rcx,
                             in_bytes(methodOopDesc::method_data_offset())));
      __ movptr(Address(rbp, frame::interpreter_frame_mdx_offset * wordSize),
                rcx);
      __ test_method_data_pointer(rcx, dispatch);
      // offset non-null mdp by MDO::data_offset() + IR::profile_method()
      __ addptr(rcx, in_bytes(methodDataOopDesc::data_offset()));
      __ addptr(rcx, rax);
      __ movptr(Address(rbp, frame::interpreter_frame_mdx_offset * wordSize),
                rcx);
      __ jmp(dispatch);
    }

    if (UseOnStackReplacement) {
      // invocation counter overflow
      __ bind(backedge_counter_overflow);
      __ negptr(rdx);
      __ addptr(rdx, r13); // branch bcp
      // IcoResult frequency_counter_overflow([JavaThread*], address branch_bcp)
      __ call_VM(noreg,
                 CAST_FROM_FN_PTR(address,
                                  InterpreterRuntime::frequency_counter_overflow),
                 rdx);
      __ load_unsigned_byte(rbx, Address(r13, 0));  // restore target bytecode

      // rax: osr nmethod (osr ok) or NULL (osr not possible)
      // ebx: target bytecode
      // rdx: scratch
      // r14: locals pointer
      // r13: bcp
      __ testptr(rax, rax);                        // test result
      __ jcc(Assembler::zero, dispatch);         // no osr if null
      // nmethod may have been invalidated (VM may block upon call_VM return)
      __ movl(rcx, Address(rax, nmethod::entry_bci_offset()));
      __ cmpl(rcx, InvalidOSREntryBci);
      __ jcc(Assembler::equal, dispatch);

      // We have the address of an on stack replacement routine in eax
      // We need to prepare to execute the OSR method. First we must
      // migrate the locals and monitors off of the stack.

      __ mov(r13, rax);                             // save the nmethod

      call_VM(noreg, CAST_FROM_FN_PTR(address, SharedRuntime::OSR_migration_begin));

      // eax is OSR buffer, move it to expected parameter location
      __ mov(j_rarg0, rax);

      // We use j_rarg definitions here so that registers don't conflict as parameter
      // registers change across platforms as we are in the midst of a calling
      // sequence to the OSR nmethod and we don't want collision. These are NOT parameters.

      const Register retaddr = j_rarg2;
      const Register sender_sp = j_rarg1;

      // pop the interpreter frame
      __ movptr(sender_sp, Address(rbp, frame::interpreter_frame_sender_sp_offset * wordSize)); // get sender sp
      __ leave();                                // remove frame anchor
      __ pop(retaddr);                           // get return address
      __ mov(rsp, sender_sp);                   // set sp to sender sp
      // Ensure compiled code always sees stack at proper alignment
      __ andptr(rsp, -(StackAlignmentInBytes));

      // unlike x86 we need no specialized return from compiled code
      // to the interpreter or the call stub.

      // push the return address
      __ push(retaddr);

      // and begin the OSR nmethod
      __ jmp(Address(r13, nmethod::osr_entry_point_offset()));
    }
  }
}


void TemplateTable::if_0cmp(Condition cc) {
  transition(itos, vtos);
  // assume branch is more often taken than not (loops use backward branches)
  Label not_taken;
  __ testl(rax, rax);
  __ jcc(j_not(cc), not_taken);
  branch(false, false);
  __ bind(not_taken);
  __ profile_not_taken_branch(rax);
}

void TemplateTable::if_icmp(Condition cc) {
  transition(itos, vtos);
  // assume branch is more often taken than not (loops use backward branches)
  Label not_taken;
  __ pop_i(rdx);
  __ cmpl(rdx, rax);
  __ jcc(j_not(cc), not_taken);
  branch(false, false);
  __ bind(not_taken);
  __ profile_not_taken_branch(rax);
}

void TemplateTable::if_nullcmp(Condition cc) {
  transition(atos, vtos);
  // assume branch is more often taken than not (loops use backward branches)
  Label not_taken;
  __ testptr(rax, rax);
  __ jcc(j_not(cc), not_taken);
  branch(false, false);
  __ bind(not_taken);
  __ profile_not_taken_branch(rax);
}

void TemplateTable::if_acmp(Condition cc) {
  transition(atos, vtos);
  // assume branch is more often taken than not (loops use backward branches)
  Label not_taken;
  __ pop_ptr(rdx);
  __ cmpptr(rdx, rax);
  __ jcc(j_not(cc), not_taken);
  branch(false, false);
  __ bind(not_taken);
  __ profile_not_taken_branch(rax);
}

void TemplateTable::ret() {
  transition(vtos, vtos);
  locals_index(rbx);
  __ movslq(rbx, iaddress(rbx)); // get return bci, compute return bcp
  __ profile_ret(rbx, rcx);
  __ get_method(rax);
  __ movptr(r13, Address(rax, methodOopDesc::const_offset()));
  __ lea(r13, Address(r13, rbx, Address::times_1,
                      constMethodOopDesc::codes_offset()));
  __ dispatch_next(vtos);
}

void TemplateTable::wide_ret() {
  transition(vtos, vtos);
  locals_index_wide(rbx);
  __ movptr(rbx, aaddress(rbx)); // get return bci, compute return bcp
  __ profile_ret(rbx, rcx);
  __ get_method(rax);
  __ movptr(r13, Address(rax, methodOopDesc::const_offset()));
  __ lea(r13, Address(r13, rbx, Address::times_1, constMethodOopDesc::codes_offset()));
  __ dispatch_next(vtos);
}

void TemplateTable::tableswitch() {
  Label default_case, continue_execution;
  transition(itos, vtos);
  // align r13
  __ lea(rbx, at_bcp(BytesPerInt));
  __ andptr(rbx, -BytesPerInt);
  // load lo & hi
  __ movl(rcx, Address(rbx, BytesPerInt));
  __ movl(rdx, Address(rbx, 2 * BytesPerInt));
  __ bswapl(rcx);
  __ bswapl(rdx);
  // check against lo & hi
  __ cmpl(rax, rcx);
  __ jcc(Assembler::less, default_case);
  __ cmpl(rax, rdx);
  __ jcc(Assembler::greater, default_case);
  // lookup dispatch offset
  __ subl(rax, rcx);
  __ movl(rdx, Address(rbx, rax, Address::times_4, 3 * BytesPerInt));
  __ profile_switch_case(rax, rbx, rcx);
  // continue execution
  __ bind(continue_execution);
  __ bswapl(rdx);
  __ movl2ptr(rdx, rdx);
  __ load_unsigned_byte(rbx, Address(r13, rdx, Address::times_1));
  __ addptr(r13, rdx);
  __ dispatch_only(vtos);
  // handle default
  __ bind(default_case);
  __ profile_switch_default(rax);
  __ movl(rdx, Address(rbx, 0));
  __ jmp(continue_execution);
}

void TemplateTable::lookupswitch() {
  transition(itos, itos);
  __ stop("lookupswitch bytecode should have been rewritten");
}

void TemplateTable::fast_linearswitch() {
  transition(itos, vtos);
  Label loop_entry, loop, found, continue_execution;
  // bswap rax so we can avoid bswapping the table entries
  __ bswapl(rax);
  // align r13
  __ lea(rbx, at_bcp(BytesPerInt)); // btw: should be able to get rid of
                                    // this instruction (change offsets
                                    // below)
  __ andptr(rbx, -BytesPerInt);
  // set counter
  __ movl(rcx, Address(rbx, BytesPerInt));
  __ bswapl(rcx);
  __ jmpb(loop_entry);
  // table search
  __ bind(loop);
  __ cmpl(rax, Address(rbx, rcx, Address::times_8, 2 * BytesPerInt));
  __ jcc(Assembler::equal, found);
  __ bind(loop_entry);
  __ decrementl(rcx);
  __ jcc(Assembler::greaterEqual, loop);
  // default case
  __ profile_switch_default(rax);
  __ movl(rdx, Address(rbx, 0));
  __ jmp(continue_execution);
  // entry found -> get offset
  __ bind(found);
  __ movl(rdx, Address(rbx, rcx, Address::times_8, 3 * BytesPerInt));
  __ profile_switch_case(rcx, rax, rbx);
  // continue execution
  __ bind(continue_execution);
  __ bswapl(rdx);
  __ movl2ptr(rdx, rdx);
  __ load_unsigned_byte(rbx, Address(r13, rdx, Address::times_1));
  __ addptr(r13, rdx);
  __ dispatch_only(vtos);
}

void TemplateTable::fast_binaryswitch() {
  transition(itos, vtos);
  // Implementation using the following core algorithm:
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

  // Register allocation
  const Register key   = rax; // already set (tosca)
  const Register array = rbx;
  const Register i     = rcx;
  const Register j     = rdx;
  const Register h     = rdi;
  const Register temp  = rsi;

  // Find array start
  __ lea(array, at_bcp(3 * BytesPerInt)); // btw: should be able to
                                          // get rid of this
                                          // instruction (change
                                          // offsets below)
  __ andptr(array, -BytesPerInt);

  // Initialize i & j
  __ xorl(i, i);                            // i = 0;
  __ movl(j, Address(array, -BytesPerInt)); // j = length(array);

  // Convert j into native byteordering
  __ bswapl(j);

  // And start
  Label entry;
  __ jmp(entry);

  // binary search loop
  {
    Label loop;
    __ bind(loop);
    // int h = (i + j) >> 1;
    __ leal(h, Address(i, j, Address::times_1)); // h = i + j;
    __ sarl(h, 1);                               // h = (i + j) >> 1;
    // if (key < array[h].fast_match()) {
    //   j = h;
    // } else {
    //   i = h;
    // }
    // Convert array[h].match to native byte-ordering before compare
    __ movl(temp, Address(array, h, Address::times_8));
    __ bswapl(temp);
    __ cmpl(key, temp);
    // j = h if (key <  array[h].fast_match())
    __ cmovl(Assembler::less, j, h);
    // i = h if (key >= array[h].fast_match())
    __ cmovl(Assembler::greaterEqual, i, h);
    // while (i+1 < j)
    __ bind(entry);
    __ leal(h, Address(i, 1)); // i+1
    __ cmpl(h, j);             // i+1 < j
    __ jcc(Assembler::less, loop);
  }

  // end of binary search, result index is i (must check again!)
  Label default_case;
  // Convert array[i].match to native byte-ordering before compare
  __ movl(temp, Address(array, i, Address::times_8));
  __ bswapl(temp);
  __ cmpl(key, temp);
  __ jcc(Assembler::notEqual, default_case);

  // entry found -> j = offset
  __ movl(j , Address(array, i, Address::times_8, BytesPerInt));
  __ profile_switch_case(i, key, array);
  __ bswapl(j);
  __ movl2ptr(j, j);
  __ load_unsigned_byte(rbx, Address(r13, j, Address::times_1));
  __ addptr(r13, j);
  __ dispatch_only(vtos);

  // default case -> j = default offset
  __ bind(default_case);
  __ profile_switch_default(i);
  __ movl(j, Address(array, -2 * BytesPerInt));
  __ bswapl(j);
  __ movl2ptr(j, j);
  __ load_unsigned_byte(rbx, Address(r13, j, Address::times_1));
  __ addptr(r13, j);
  __ dispatch_only(vtos);
}


void TemplateTable::_return(TosState state) {
  transition(state, state);
  assert(_desc->calls_vm(),
         "inconsistent calls_vm information"); // call in remove_activation

  if (_desc->bytecode() == Bytecodes::_return_register_finalizer) {
    assert(state == vtos, "only valid state");
    __ movptr(c_rarg1, aaddress(0));
    __ load_klass(rdi, c_rarg1);
    __ movl(rdi, Address(rdi, Klass::access_flags_offset_in_bytes() + sizeof(oopDesc)));
    __ testl(rdi, JVM_ACC_HAS_FINALIZER);
    Label skip_register_finalizer;
    __ jcc(Assembler::zero, skip_register_finalizer);

    __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::register_finalizer), c_rarg1);

    __ bind(skip_register_finalizer);
  }

  __ remove_activation(state, r13);
  __ jmp(r13);
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
//     writes act as aquire & release, so:
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
void TemplateTable::volatile_barrier(Assembler::Membar_mask_bits
                                     order_constraint) {
  // Helper function to insert a is-volatile test and memory barrier
  if (os::is_MP()) { // Not needed on single CPU
    __ membar(order_constraint);
  }
}

void TemplateTable::resolve_cache_and_index(int byte_no, Register Rcache, Register index) {
  assert(byte_no == 1 || byte_no == 2, "byte_no out of range");
  bool is_invokedynamic = (bytecode() == Bytecodes::_invokedynamic);

  const Register temp = rbx;
  assert_different_registers(Rcache, index, temp);

  const int shift_count = (1 + byte_no) * BitsPerByte;
  Label resolved;
  __ get_cache_and_index_at_bcp(Rcache, index, 1, is_invokedynamic);
  if (is_invokedynamic) {
    // we are resolved if the f1 field contains a non-null CallSite object
    __ cmpptr(Address(Rcache, index, Address::times_ptr, constantPoolCacheOopDesc::base_offset() + ConstantPoolCacheEntry::f1_offset()), (int32_t) NULL_WORD);
    __ jcc(Assembler::notEqual, resolved);
  } else {
    __ movl(temp, Address(Rcache, index, Address::times_ptr, constantPoolCacheOopDesc::base_offset() + ConstantPoolCacheEntry::indices_offset()));
    __ shrl(temp, shift_count);
    // have we resolved this bytecode?
    __ andl(temp, 0xFF);
    __ cmpl(temp, (int) bytecode());
    __ jcc(Assembler::equal, resolved);
  }

  // resolve first time through
  address entry;
  switch (bytecode()) {
  case Bytecodes::_getstatic:
  case Bytecodes::_putstatic:
  case Bytecodes::_getfield:
  case Bytecodes::_putfield:
    entry = CAST_FROM_FN_PTR(address, InterpreterRuntime::resolve_get_put);
    break;
  case Bytecodes::_invokevirtual:
  case Bytecodes::_invokespecial:
  case Bytecodes::_invokestatic:
  case Bytecodes::_invokeinterface:
    entry = CAST_FROM_FN_PTR(address, InterpreterRuntime::resolve_invoke);
    break;
  case Bytecodes::_invokedynamic:
    entry = CAST_FROM_FN_PTR(address, InterpreterRuntime::resolve_invokedynamic);
    break;
  default:
    ShouldNotReachHere();
    break;
  }
  __ movl(temp, (int) bytecode());
  __ call_VM(noreg, entry, temp);

  // Update registers with resolved info
  __ get_cache_and_index_at_bcp(Rcache, index, 1, is_invokedynamic);
  __ bind(resolved);
}

// The Rcache and index registers must be set before call
void TemplateTable::load_field_cp_cache_entry(Register obj,
                                              Register cache,
                                              Register index,
                                              Register off,
                                              Register flags,
                                              bool is_static = false) {
  assert_different_registers(cache, index, flags, off);

  ByteSize cp_base_offset = constantPoolCacheOopDesc::base_offset();
  // Field offset
  __ movptr(off, Address(cache, index, Address::times_8,
                         in_bytes(cp_base_offset +
                                  ConstantPoolCacheEntry::f2_offset())));
  // Flags
  __ movl(flags, Address(cache, index, Address::times_8,
                         in_bytes(cp_base_offset +
                                  ConstantPoolCacheEntry::flags_offset())));

  // klass overwrite register
  if (is_static) {
    __ movptr(obj, Address(cache, index, Address::times_8,
                           in_bytes(cp_base_offset +
                                    ConstantPoolCacheEntry::f1_offset())));
  }
}

void TemplateTable::load_invoke_cp_cache_entry(int byte_no,
                                               Register method,
                                               Register itable_index,
                                               Register flags,
                                               bool is_invokevirtual,
                                               bool is_invokevfinal /*unused*/) {
  // setup registers
  const Register cache = rcx;
  const Register index = rdx;
  assert_different_registers(method, flags);
  assert_different_registers(method, cache, index);
  assert_different_registers(itable_index, flags);
  assert_different_registers(itable_index, cache, index);
  // determine constant pool cache field offsets
  const int method_offset = in_bytes(
    constantPoolCacheOopDesc::base_offset() +
      (is_invokevirtual
       ? ConstantPoolCacheEntry::f2_offset()
       : ConstantPoolCacheEntry::f1_offset()));
  const int flags_offset = in_bytes(constantPoolCacheOopDesc::base_offset() +
                                    ConstantPoolCacheEntry::flags_offset());
  // access constant pool cache fields
  const int index_offset = in_bytes(constantPoolCacheOopDesc::base_offset() +
                                    ConstantPoolCacheEntry::f2_offset());

  resolve_cache_and_index(byte_no, cache, index);

  assert(wordSize == 8, "adjust code below");
  __ movptr(method, Address(cache, index, Address::times_8, method_offset));
  if (itable_index != noreg) {
    __ movptr(itable_index,
            Address(cache, index, Address::times_8, index_offset));
  }
  __ movl(flags , Address(cache, index, Address::times_8, flags_offset));
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
    assert_different_registers(cache, index, rax);
    __ mov32(rax, ExternalAddress((address) JvmtiExport::get_field_access_count_addr()));
    __ testl(rax, rax);
    __ jcc(Assembler::zero, L1);

    __ get_cache_and_index_at_bcp(c_rarg2, c_rarg3, 1);

    // cache entry pointer
    __ addptr(c_rarg2, in_bytes(constantPoolCacheOopDesc::base_offset()));
    __ shll(c_rarg3, LogBytesPerWord);
    __ addptr(c_rarg2, c_rarg3);
    if (is_static) {
      __ xorl(c_rarg1, c_rarg1); // NULL object reference
    } else {
      __ movptr(c_rarg1, at_tos()); // get object pointer without popping it
      __ verify_oop(c_rarg1);
    }
    // c_rarg1: object pointer or NULL
    // c_rarg2: cache entry pointer
    // c_rarg3: jvalue object on the stack
    __ call_VM(noreg, CAST_FROM_FN_PTR(address,
                                       InterpreterRuntime::post_field_access),
               c_rarg1, c_rarg2, c_rarg3);
    __ get_cache_and_index_at_bcp(cache, index, 1);
    __ bind(L1);
  }
}

void TemplateTable::pop_and_check_object(Register r) {
  __ pop_ptr(r);
  __ null_check(r);  // for field access must check obj.
  __ verify_oop(r);
}

void TemplateTable::getfield_or_static(int byte_no, bool is_static) {
  transition(vtos, vtos);

  const Register cache = rcx;
  const Register index = rdx;
  const Register obj   = c_rarg3;
  const Register off   = rbx;
  const Register flags = rax;
  const Register bc = c_rarg3; // uses same reg as obj, so don't mix them

  resolve_cache_and_index(byte_no, cache, index);
  jvmti_post_field_access(cache, index, is_static, false);
  load_field_cp_cache_entry(obj, cache, index, off, flags, is_static);

  if (!is_static) {
    // obj is on the stack
    pop_and_check_object(obj);
  }

  const Address field(obj, off, Address::times_1);

  Label Done, notByte, notInt, notShort, notChar,
              notLong, notFloat, notObj, notDouble;

  __ shrl(flags, ConstantPoolCacheEntry::tosBits);
  assert(btos == 0, "change code, btos != 0");

  __ andl(flags, 0x0F);
  __ jcc(Assembler::notZero, notByte);
  // btos
  __ load_signed_byte(rax, field);
  __ push(btos);
  // Rewrite bytecode to be faster
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_bgetfield, bc, rbx);
  }
  __ jmp(Done);

  __ bind(notByte);
  __ cmpl(flags, atos);
  __ jcc(Assembler::notEqual, notObj);
  // atos
  __ load_heap_oop(rax, field);
  __ push(atos);
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_agetfield, bc, rbx);
  }
  __ jmp(Done);

  __ bind(notObj);
  __ cmpl(flags, itos);
  __ jcc(Assembler::notEqual, notInt);
  // itos
  __ movl(rax, field);
  __ push(itos);
  // Rewrite bytecode to be faster
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_igetfield, bc, rbx);
  }
  __ jmp(Done);

  __ bind(notInt);
  __ cmpl(flags, ctos);
  __ jcc(Assembler::notEqual, notChar);
  // ctos
  __ load_unsigned_short(rax, field);
  __ push(ctos);
  // Rewrite bytecode to be faster
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_cgetfield, bc, rbx);
  }
  __ jmp(Done);

  __ bind(notChar);
  __ cmpl(flags, stos);
  __ jcc(Assembler::notEqual, notShort);
  // stos
  __ load_signed_short(rax, field);
  __ push(stos);
  // Rewrite bytecode to be faster
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_sgetfield, bc, rbx);
  }
  __ jmp(Done);

  __ bind(notShort);
  __ cmpl(flags, ltos);
  __ jcc(Assembler::notEqual, notLong);
  // ltos
  __ movq(rax, field);
  __ push(ltos);
  // Rewrite bytecode to be faster
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_lgetfield, bc, rbx);
  }
  __ jmp(Done);

  __ bind(notLong);
  __ cmpl(flags, ftos);
  __ jcc(Assembler::notEqual, notFloat);
  // ftos
  __ movflt(xmm0, field);
  __ push(ftos);
  // Rewrite bytecode to be faster
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_fgetfield, bc, rbx);
  }
  __ jmp(Done);

  __ bind(notFloat);
#ifdef ASSERT
  __ cmpl(flags, dtos);
  __ jcc(Assembler::notEqual, notDouble);
#endif
  // dtos
  __ movdbl(xmm0, field);
  __ push(dtos);
  // Rewrite bytecode to be faster
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_dgetfield, bc, rbx);
  }
#ifdef ASSERT
  __ jmp(Done);

  __ bind(notDouble);
  __ stop("Bad state");
#endif

  __ bind(Done);
  // [jk] not needed currently
  // volatile_barrier(Assembler::Membar_mask_bits(Assembler::LoadLoad |
  //                                              Assembler::LoadStore));
}


void TemplateTable::getfield(int byte_no) {
  getfield_or_static(byte_no, false);
}

void TemplateTable::getstatic(int byte_no) {
  getfield_or_static(byte_no, true);
}

// The registers cache and index expected to be set before call.
// The function may destroy various registers, just not the cache and index registers.
void TemplateTable::jvmti_post_field_mod(Register cache, Register index, bool is_static) {
  transition(vtos, vtos);

  ByteSize cp_base_offset = constantPoolCacheOopDesc::base_offset();

  if (JvmtiExport::can_post_field_modification()) {
    // Check to see if a field modification watch has been set before
    // we take the time to call into the VM.
    Label L1;
    assert_different_registers(cache, index, rax);
    __ mov32(rax, ExternalAddress((address)JvmtiExport::get_field_modification_count_addr()));
    __ testl(rax, rax);
    __ jcc(Assembler::zero, L1);

    __ get_cache_and_index_at_bcp(c_rarg2, rscratch1, 1);

    if (is_static) {
      // Life is simple.  Null out the object pointer.
      __ xorl(c_rarg1, c_rarg1);
    } else {
      // Life is harder. The stack holds the value on top, followed by
      // the object.  We don't know the size of the value, though; it
      // could be one or two words depending on its type. As a result,
      // we must find the type to determine where the object is.
      __ movl(c_rarg3, Address(c_rarg2, rscratch1,
                           Address::times_8,
                           in_bytes(cp_base_offset +
                                     ConstantPoolCacheEntry::flags_offset())));
      __ shrl(c_rarg3, ConstantPoolCacheEntry::tosBits);
      // Make sure we don't need to mask rcx for tosBits after the
      // above shift
      ConstantPoolCacheEntry::verify_tosBits();
      __ movptr(c_rarg1, at_tos_p1());  // initially assume a one word jvalue
      __ cmpl(c_rarg3, ltos);
      __ cmovptr(Assembler::equal,
                 c_rarg1, at_tos_p2()); // ltos (two word jvalue)
      __ cmpl(c_rarg3, dtos);
      __ cmovptr(Assembler::equal,
                 c_rarg1, at_tos_p2()); // dtos (two word jvalue)
    }
    // cache entry pointer
    __ addptr(c_rarg2, in_bytes(cp_base_offset));
    __ shll(rscratch1, LogBytesPerWord);
    __ addptr(c_rarg2, rscratch1);
    // object (tos)
    __ mov(c_rarg3, rsp);
    // c_rarg1: object pointer set up above (NULL if static)
    // c_rarg2: cache entry pointer
    // c_rarg3: jvalue object on the stack
    __ call_VM(noreg,
               CAST_FROM_FN_PTR(address,
                                InterpreterRuntime::post_field_modification),
               c_rarg1, c_rarg2, c_rarg3);
    __ get_cache_and_index_at_bcp(cache, index, 1);
    __ bind(L1);
  }
}

void TemplateTable::putfield_or_static(int byte_no, bool is_static) {
  transition(vtos, vtos);

  const Register cache = rcx;
  const Register index = rdx;
  const Register obj   = rcx;
  const Register off   = rbx;
  const Register flags = rax;
  const Register bc    = c_rarg3;

  resolve_cache_and_index(byte_no, cache, index);
  jvmti_post_field_mod(cache, index, is_static);
  load_field_cp_cache_entry(obj, cache, index, off, flags, is_static);

  // [jk] not needed currently
  // volatile_barrier(Assembler::Membar_mask_bits(Assembler::LoadStore |
  //                                              Assembler::StoreStore));

  Label notVolatile, Done;
  __ movl(rdx, flags);
  __ shrl(rdx, ConstantPoolCacheEntry::volatileField);
  __ andl(rdx, 0x1);

  // field address
  const Address field(obj, off, Address::times_1);

  Label notByte, notInt, notShort, notChar,
        notLong, notFloat, notObj, notDouble;

  __ shrl(flags, ConstantPoolCacheEntry::tosBits);

  assert(btos == 0, "change code, btos != 0");
  __ andl(flags, 0x0f);
  __ jcc(Assembler::notZero, notByte);
  // btos
  __ pop(btos);
  if (!is_static) pop_and_check_object(obj);
  __ movb(field, rax);
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_bputfield, bc, rbx);
  }
  __ jmp(Done);

  __ bind(notByte);
  __ cmpl(flags, atos);
  __ jcc(Assembler::notEqual, notObj);
  // atos
  __ pop(atos);
  if (!is_static) pop_and_check_object(obj);

  // Store into the field
  do_oop_store(_masm, field, rax, _bs->kind(), false);

  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_aputfield, bc, rbx);
  }
  __ jmp(Done);

  __ bind(notObj);
  __ cmpl(flags, itos);
  __ jcc(Assembler::notEqual, notInt);
  // itos
  __ pop(itos);
  if (!is_static) pop_and_check_object(obj);
  __ movl(field, rax);
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_iputfield, bc, rbx);
  }
  __ jmp(Done);

  __ bind(notInt);
  __ cmpl(flags, ctos);
  __ jcc(Assembler::notEqual, notChar);
  // ctos
  __ pop(ctos);
  if (!is_static) pop_and_check_object(obj);
  __ movw(field, rax);
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_cputfield, bc, rbx);
  }
  __ jmp(Done);

  __ bind(notChar);
  __ cmpl(flags, stos);
  __ jcc(Assembler::notEqual, notShort);
  // stos
  __ pop(stos);
  if (!is_static) pop_and_check_object(obj);
  __ movw(field, rax);
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_sputfield, bc, rbx);
  }
  __ jmp(Done);

  __ bind(notShort);
  __ cmpl(flags, ltos);
  __ jcc(Assembler::notEqual, notLong);
  // ltos
  __ pop(ltos);
  if (!is_static) pop_and_check_object(obj);
  __ movq(field, rax);
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_lputfield, bc, rbx);
  }
  __ jmp(Done);

  __ bind(notLong);
  __ cmpl(flags, ftos);
  __ jcc(Assembler::notEqual, notFloat);
  // ftos
  __ pop(ftos);
  if (!is_static) pop_and_check_object(obj);
  __ movflt(field, xmm0);
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_fputfield, bc, rbx);
  }
  __ jmp(Done);

  __ bind(notFloat);
#ifdef ASSERT
  __ cmpl(flags, dtos);
  __ jcc(Assembler::notEqual, notDouble);
#endif
  // dtos
  __ pop(dtos);
  if (!is_static) pop_and_check_object(obj);
  __ movdbl(field, xmm0);
  if (!is_static) {
    patch_bytecode(Bytecodes::_fast_dputfield, bc, rbx);
  }

#ifdef ASSERT
  __ jmp(Done);

  __ bind(notDouble);
  __ stop("Bad state");
#endif

  __ bind(Done);
  // Check for volatile store
  __ testl(rdx, rdx);
  __ jcc(Assembler::zero, notVolatile);
  volatile_barrier(Assembler::Membar_mask_bits(Assembler::StoreLoad |
                                               Assembler::StoreStore));

  __ bind(notVolatile);
}

void TemplateTable::putfield(int byte_no) {
  putfield_or_static(byte_no, false);
}

void TemplateTable::putstatic(int byte_no) {
  putfield_or_static(byte_no, true);
}

void TemplateTable::jvmti_post_fast_field_mod() {
  if (JvmtiExport::can_post_field_modification()) {
    // Check to see if a field modification watch has been set before
    // we take the time to call into the VM.
    Label L2;
    __ mov32(c_rarg3, ExternalAddress((address)JvmtiExport::get_field_modification_count_addr()));
    __ testl(c_rarg3, c_rarg3);
    __ jcc(Assembler::zero, L2);
    __ pop_ptr(rbx);                  // copy the object pointer from tos
    __ verify_oop(rbx);
    __ push_ptr(rbx);                 // put the object pointer back on tos
    __ subptr(rsp, sizeof(jvalue));  // add space for a jvalue object
    __ mov(c_rarg3, rsp);
    const Address field(c_rarg3, 0);

    switch (bytecode()) {          // load values into the jvalue object
    case Bytecodes::_fast_aputfield: __ movq(field, rax); break;
    case Bytecodes::_fast_lputfield: __ movq(field, rax); break;
    case Bytecodes::_fast_iputfield: __ movl(field, rax); break;
    case Bytecodes::_fast_bputfield: __ movb(field, rax); break;
    case Bytecodes::_fast_sputfield: // fall through
    case Bytecodes::_fast_cputfield: __ movw(field, rax); break;
    case Bytecodes::_fast_fputfield: __ movflt(field, xmm0); break;
    case Bytecodes::_fast_dputfield: __ movdbl(field, xmm0); break;
    default:
      ShouldNotReachHere();
    }

    // Save rax because call_VM() will clobber it, then use it for
    // JVMTI purposes
    __ push(rax);
    // access constant pool cache entry
    __ get_cache_entry_pointer_at_bcp(c_rarg2, rax, 1);
    __ verify_oop(rbx);
    // rbx: object pointer copied above
    // c_rarg2: cache entry pointer
    // c_rarg3: jvalue object on the stack
    __ call_VM(noreg,
               CAST_FROM_FN_PTR(address,
                                InterpreterRuntime::post_field_modification),
               rbx, c_rarg2, c_rarg3);
    __ pop(rax);     // restore lower value
    __ addptr(rsp, sizeof(jvalue));  // release jvalue object space
    __ bind(L2);
  }
}

void TemplateTable::fast_storefield(TosState state) {
  transition(state, vtos);

  ByteSize base = constantPoolCacheOopDesc::base_offset();

  jvmti_post_fast_field_mod();

  // access constant pool cache
  __ get_cache_and_index_at_bcp(rcx, rbx, 1);

  // test for volatile with rdx
  __ movl(rdx, Address(rcx, rbx, Address::times_8,
                       in_bytes(base +
                                ConstantPoolCacheEntry::flags_offset())));

  // replace index with field offset from cache entry
  __ movptr(rbx, Address(rcx, rbx, Address::times_8,
                         in_bytes(base + ConstantPoolCacheEntry::f2_offset())));

  // [jk] not needed currently
  // volatile_barrier(Assembler::Membar_mask_bits(Assembler::LoadStore |
  //                                              Assembler::StoreStore));

  Label notVolatile;
  __ shrl(rdx, ConstantPoolCacheEntry::volatileField);
  __ andl(rdx, 0x1);

  // Get object from stack
  pop_and_check_object(rcx);

  // field address
  const Address field(rcx, rbx, Address::times_1);

  // access field
  switch (bytecode()) {
  case Bytecodes::_fast_aputfield:
    do_oop_store(_masm, field, rax, _bs->kind(), false);
    break;
  case Bytecodes::_fast_lputfield:
    __ movq(field, rax);
    break;
  case Bytecodes::_fast_iputfield:
    __ movl(field, rax);
    break;
  case Bytecodes::_fast_bputfield:
    __ movb(field, rax);
    break;
  case Bytecodes::_fast_sputfield:
    // fall through
  case Bytecodes::_fast_cputfield:
    __ movw(field, rax);
    break;
  case Bytecodes::_fast_fputfield:
    __ movflt(field, xmm0);
    break;
  case Bytecodes::_fast_dputfield:
    __ movdbl(field, xmm0);
    break;
  default:
    ShouldNotReachHere();
  }

  // Check for volatile store
  __ testl(rdx, rdx);
  __ jcc(Assembler::zero, notVolatile);
  volatile_barrier(Assembler::Membar_mask_bits(Assembler::StoreLoad |
                                               Assembler::StoreStore));
  __ bind(notVolatile);
}


void TemplateTable::fast_accessfield(TosState state) {
  transition(atos, state);

  // Do the JVMTI work here to avoid disturbing the register state below
  if (JvmtiExport::can_post_field_access()) {
    // Check to see if a field access watch has been set before we
    // take the time to call into the VM.
    Label L1;
    __ mov32(rcx, ExternalAddress((address) JvmtiExport::get_field_access_count_addr()));
    __ testl(rcx, rcx);
    __ jcc(Assembler::zero, L1);
    // access constant pool cache entry
    __ get_cache_entry_pointer_at_bcp(c_rarg2, rcx, 1);
    __ verify_oop(rax);
    __ mov(r12, rax);  // save object pointer before call_VM() clobbers it
    __ mov(c_rarg1, rax);
    // c_rarg1: object pointer copied above
    // c_rarg2: cache entry pointer
    __ call_VM(noreg,
               CAST_FROM_FN_PTR(address,
                                InterpreterRuntime::post_field_access),
               c_rarg1, c_rarg2);
    __ mov(rax, r12); // restore object pointer
    __ reinit_heapbase();
    __ bind(L1);
  }

  // access constant pool cache
  __ get_cache_and_index_at_bcp(rcx, rbx, 1);
  // replace index with field offset from cache entry
  // [jk] not needed currently
  // if (os::is_MP()) {
  //   __ movl(rdx, Address(rcx, rbx, Address::times_8,
  //                        in_bytes(constantPoolCacheOopDesc::base_offset() +
  //                                 ConstantPoolCacheEntry::flags_offset())));
  //   __ shrl(rdx, ConstantPoolCacheEntry::volatileField);
  //   __ andl(rdx, 0x1);
  // }
  __ movptr(rbx, Address(rcx, rbx, Address::times_8,
                         in_bytes(constantPoolCacheOopDesc::base_offset() +
                                  ConstantPoolCacheEntry::f2_offset())));

  // rax: object
  __ verify_oop(rax);
  __ null_check(rax);
  Address field(rax, rbx, Address::times_1);

  // access field
  switch (bytecode()) {
  case Bytecodes::_fast_agetfield:
    __ load_heap_oop(rax, field);
    __ verify_oop(rax);
    break;
  case Bytecodes::_fast_lgetfield:
    __ movq(rax, field);
    break;
  case Bytecodes::_fast_igetfield:
    __ movl(rax, field);
    break;
  case Bytecodes::_fast_bgetfield:
    __ movsbl(rax, field);
    break;
  case Bytecodes::_fast_sgetfield:
    __ load_signed_short(rax, field);
    break;
  case Bytecodes::_fast_cgetfield:
    __ load_unsigned_short(rax, field);
    break;
  case Bytecodes::_fast_fgetfield:
    __ movflt(xmm0, field);
    break;
  case Bytecodes::_fast_dgetfield:
    __ movdbl(xmm0, field);
    break;
  default:
    ShouldNotReachHere();
  }
  // [jk] not needed currently
  // if (os::is_MP()) {
  //   Label notVolatile;
  //   __ testl(rdx, rdx);
  //   __ jcc(Assembler::zero, notVolatile);
  //   __ membar(Assembler::LoadLoad);
  //   __ bind(notVolatile);
  //};
}

void TemplateTable::fast_xaccess(TosState state) {
  transition(vtos, state);

  // get receiver
  __ movptr(rax, aaddress(0));
  // access constant pool cache
  __ get_cache_and_index_at_bcp(rcx, rdx, 2);
  __ movptr(rbx,
            Address(rcx, rdx, Address::times_8,
                    in_bytes(constantPoolCacheOopDesc::base_offset() +
                             ConstantPoolCacheEntry::f2_offset())));
  // make sure exception is reported in correct bcp range (getfield is
  // next instruction)
  __ increment(r13);
  __ null_check(rax);
  switch (state) {
  case itos:
    __ movl(rax, Address(rax, rbx, Address::times_1));
    break;
  case atos:
    __ load_heap_oop(rax, Address(rax, rbx, Address::times_1));
    __ verify_oop(rax);
    break;
  case ftos:
    __ movflt(xmm0, Address(rax, rbx, Address::times_1));
    break;
  default:
    ShouldNotReachHere();
  }

  // [jk] not needed currently
  // if (os::is_MP()) {
  //   Label notVolatile;
  //   __ movl(rdx, Address(rcx, rdx, Address::times_8,
  //                        in_bytes(constantPoolCacheOopDesc::base_offset() +
  //                                 ConstantPoolCacheEntry::flags_offset())));
  //   __ shrl(rdx, ConstantPoolCacheEntry::volatileField);
  //   __ testl(rdx, 0x1);
  //   __ jcc(Assembler::zero, notVolatile);
  //   __ membar(Assembler::LoadLoad);
  //   __ bind(notVolatile);
  // }

  __ decrement(r13);
}



//-----------------------------------------------------------------------------
// Calls

void TemplateTable::count_calls(Register method, Register temp) {
  // implemented elsewhere
  ShouldNotReachHere();
}

void TemplateTable::prepare_invoke(Register method, Register index, int byte_no) {
  // determine flags
  Bytecodes::Code code = bytecode();
  const bool is_invokeinterface  = code == Bytecodes::_invokeinterface;
  const bool is_invokedynamic    = code == Bytecodes::_invokedynamic;
  const bool is_invokevirtual    = code == Bytecodes::_invokevirtual;
  const bool is_invokespecial    = code == Bytecodes::_invokespecial;
  const bool load_receiver      = (code != Bytecodes::_invokestatic && code != Bytecodes::_invokedynamic);
  const bool receiver_null_check = is_invokespecial;
  const bool save_flags = is_invokeinterface || is_invokevirtual;
  // setup registers & access constant pool cache
  const Register recv   = rcx;
  const Register flags  = rdx;
  assert_different_registers(method, index, recv, flags);

  // save 'interpreter return address'
  __ save_bcp();

  load_invoke_cp_cache_entry(byte_no, method, index, flags, is_invokevirtual);

  // load receiver if needed (note: no return address pushed yet)
  if (load_receiver) {
    __ movl(recv, flags);
    __ andl(recv, 0xFF);
    Address recv_addr(rsp, recv, Address::times_8, -Interpreter::expr_offset_in_bytes(1));
    __ movptr(recv, recv_addr);
    __ verify_oop(recv);
  }

  // do null check if needed
  if (receiver_null_check) {
    __ null_check(recv);
  }

  if (save_flags) {
    __ movl(r13, flags);
  }

  // compute return type
  __ shrl(flags, ConstantPoolCacheEntry::tosBits);
  // Make sure we don't need to mask flags for tosBits after the above shift
  ConstantPoolCacheEntry::verify_tosBits();
  // load return address
  {
    address table_addr;
    if (is_invokeinterface || is_invokedynamic)
      table_addr = (address)Interpreter::return_5_addrs_by_index_table();
    else
      table_addr = (address)Interpreter::return_3_addrs_by_index_table();
    ExternalAddress table(table_addr);
    __ lea(rscratch1, table);
    __ movptr(flags, Address(rscratch1, flags, Address::times_ptr));
  }

  // push return address
  __ push(flags);

  // Restore flag field from the constant pool cache, and restore esi
  // for later null checks.  r13 is the bytecode pointer
  if (save_flags) {
    __ movl(flags, r13);
    __ restore_bcp();
  }
}


void TemplateTable::invokevirtual_helper(Register index,
                                         Register recv,
                                         Register flags) {
  // Uses temporary registers rax, rdx  assert_different_registers(index, recv, rax, rdx);

  // Test for an invoke of a final method
  Label notFinal;
  __ movl(rax, flags);
  __ andl(rax, (1 << ConstantPoolCacheEntry::vfinalMethod));
  __ jcc(Assembler::zero, notFinal);

  const Register method = index;  // method must be rbx
  assert(method == rbx,
         "methodOop must be rbx for interpreter calling convention");

  // do the call - the index is actually the method to call
  __ verify_oop(method);

  // It's final, need a null check here!
  __ null_check(recv);

  // profile this call
  __ profile_final_call(rax);

  __ jump_from_interpreted(method, rax);

  __ bind(notFinal);

  // get receiver klass
  __ null_check(recv, oopDesc::klass_offset_in_bytes());
  __ load_klass(rax, recv);

  __ verify_oop(rax);

  // profile this call
  __ profile_virtual_call(rax, r14, rdx);

  // get target methodOop & entry point
  const int base = instanceKlass::vtable_start_offset() * wordSize;
  assert(vtableEntry::size() * wordSize == 8,
         "adjust the scaling in the code below");
  __ movptr(method, Address(rax, index,
                                 Address::times_8,
                                 base + vtableEntry::method_offset_in_bytes()));
  __ movptr(rdx, Address(method, methodOopDesc::interpreter_entry_offset()));
  __ jump_from_interpreted(method, rdx);
}


void TemplateTable::invokevirtual(int byte_no) {
  transition(vtos, vtos);
  prepare_invoke(rbx, noreg, byte_no);

  // rbx: index
  // rcx: receiver
  // rdx: flags

  invokevirtual_helper(rbx, rcx, rdx);
}


void TemplateTable::invokespecial(int byte_no) {
  transition(vtos, vtos);
  prepare_invoke(rbx, noreg, byte_no);
  // do the call
  __ verify_oop(rbx);
  __ profile_call(rax);
  __ jump_from_interpreted(rbx, rax);
}


void TemplateTable::invokestatic(int byte_no) {
  transition(vtos, vtos);
  prepare_invoke(rbx, noreg, byte_no);
  // do the call
  __ verify_oop(rbx);
  __ profile_call(rax);
  __ jump_from_interpreted(rbx, rax);
}

void TemplateTable::fast_invokevfinal(int byte_no) {
  transition(vtos, vtos);
  __ stop("fast_invokevfinal not used on amd64");
}

void TemplateTable::invokeinterface(int byte_no) {
  transition(vtos, vtos);
  prepare_invoke(rax, rbx, byte_no);

  // rax: Interface
  // rbx: index
  // rcx: receiver
  // rdx: flags

  // Special case of invokeinterface called for virtual method of
  // java.lang.Object.  See cpCacheOop.cpp for details.
  // This code isn't produced by javac, but could be produced by
  // another compliant java compiler.
  Label notMethod;
  __ movl(r14, rdx);
  __ andl(r14, (1 << ConstantPoolCacheEntry::methodInterface));
  __ jcc(Assembler::zero, notMethod);

  invokevirtual_helper(rbx, rcx, rdx);
  __ bind(notMethod);

  // Get receiver klass into rdx - also a null check
  __ restore_locals(); // restore r14
  __ load_klass(rdx, rcx);
  __ verify_oop(rdx);

  // profile this call
  __ profile_virtual_call(rdx, r13, r14);

  Label no_such_interface, no_such_method;

  __ lookup_interface_method(// inputs: rec. class, interface, itable index
                             rdx, rax, rbx,
                             // outputs: method, scan temp. reg
                             rbx, r13,
                             no_such_interface);

  // rbx,: methodOop to call
  // rcx: receiver
  // Check for abstract method error
  // Note: This should be done more efficiently via a throw_abstract_method_error
  //       interpreter entry point and a conditional jump to it in case of a null
  //       method.
  __ testptr(rbx, rbx);
  __ jcc(Assembler::zero, no_such_method);

  // do the call
  // rcx: receiver
  // rbx,: methodOop
  __ jump_from_interpreted(rbx, rdx);
  __ should_not_reach_here();

  // exception handling code follows...
  // note: must restore interpreter registers to canonical
  //       state for exception handling to work correctly!

  __ bind(no_such_method);
  // throw exception
  __ pop(rbx);           // pop return address (pushed by prepare_invoke)
  __ restore_bcp();      // r13 must be correct for exception handler   (was destroyed)
  __ restore_locals();   // make sure locals pointer is correct as well (was destroyed)
  __ call_VM(noreg, CAST_FROM_FN_PTR(address, InterpreterRuntime::throw_AbstractMethodError));
  // the call_VM checks for exception, so we should never return here.
  __ should_not_reach_here();

  __ bind(no_such_interface);
  // throw exception
  __ pop(rbx);           // pop return address (pushed by prepare_invoke)
  __ restore_bcp();      // r13 must be correct for exception handler   (was destroyed)
  __ restore_locals();   // make sure locals pointer is correct as well (was destroyed)
  __ call_VM(noreg, CAST_FROM_FN_PTR(address,
                   InterpreterRuntime::throw_IncompatibleClassChangeError));
  // the call_VM checks for exception, so we should never return here.
  __ should_not_reach_here();
  return;
}

void TemplateTable::invokedynamic(int byte_no) {
  transition(vtos, vtos);

  if (!EnableInvokeDynamic) {
    // We should not encounter this bytecode if !EnableInvokeDynamic.
    // The verifier will stop it.  However, if we get past the verifier,
    // this will stop the thread in a reasonable way, without crashing the JVM.
    __ call_VM(noreg, CAST_FROM_FN_PTR(address,
                     InterpreterRuntime::throw_IncompatibleClassChangeError));
    // the call_VM checks for exception, so we should never return here.
    __ should_not_reach_here();
    return;
  }

  prepare_invoke(rax, rbx, byte_no);

  // rax: CallSite object (f1)
  // rbx: unused (f2)
  // rcx: receiver address
  // rdx: flags (unused)

  if (ProfileInterpreter) {
    Label L;
    // %%% should make a type profile for any invokedynamic that takes a ref argument
    // profile this call
    __ profile_call(r13);
  }

  __ movptr(rcx, Address(rax, __ delayed_value(java_dyn_CallSite::target_offset_in_bytes, rcx)));
  __ null_check(rcx);
  __ prepare_to_jump_from_interpreted();
  __ jump_to_method_handle_entry(rcx, rdx);
}


//-----------------------------------------------------------------------------
// Allocation

void TemplateTable::_new() {
  transition(vtos, atos);
  __ get_unsigned_2_byte_index_at_bcp(rdx, 1);
  Label slow_case;
  Label done;
  Label initialize_header;
  Label initialize_object; // including clearing the fields
  Label allocate_shared;

  __ get_cpool_and_tags(rsi, rax);
  // get instanceKlass
  __ movptr(rsi, Address(rsi, rdx,
                         Address::times_8, sizeof(constantPoolOopDesc)));

  // make sure the class we're about to instantiate has been
  // resolved. Note: slow_case does a pop of stack, which is why we
  // loaded class/pushed above
  const int tags_offset = typeArrayOopDesc::header_size(T_BYTE) * wordSize;
  __ cmpb(Address(rax, rdx, Address::times_1, tags_offset),
          JVM_CONSTANT_Class);
  __ jcc(Assembler::notEqual, slow_case);

  // make sure klass is initialized & doesn't have finalizer
  // make sure klass is fully initialized
  __ cmpl(Address(rsi,
                  instanceKlass::init_state_offset_in_bytes() +
                  sizeof(oopDesc)),
          instanceKlass::fully_initialized);
  __ jcc(Assembler::notEqual, slow_case);

  // get instance_size in instanceKlass (scaled to a count of bytes)
  __ movl(rdx,
          Address(rsi,
                  Klass::layout_helper_offset_in_bytes() + sizeof(oopDesc)));
  // test to see if it has a finalizer or is malformed in some way
  __ testl(rdx, Klass::_lh_instance_slow_path_bit);
  __ jcc(Assembler::notZero, slow_case);

  // Allocate the instance
  // 1) Try to allocate in the TLAB
  // 2) if fail and the object is large allocate in the shared Eden
  // 3) if the above fails (or is not applicable), go to a slow case
  // (creates a new TLAB, etc.)

  const bool allow_shared_alloc =
    Universe::heap()->supports_inline_contig_alloc() && !CMSIncrementalMode;

  if (UseTLAB) {
    __ movptr(rax, Address(r15_thread, in_bytes(JavaThread::tlab_top_offset())));
    __ lea(rbx, Address(rax, rdx, Address::times_1));
    __ cmpptr(rbx, Address(r15_thread, in_bytes(JavaThread::tlab_end_offset())));
    __ jcc(Assembler::above, allow_shared_alloc ? allocate_shared : slow_case);
    __ movptr(Address(r15_thread, in_bytes(JavaThread::tlab_top_offset())), rbx);
    if (ZeroTLAB) {
      // the fields have been already cleared
      __ jmp(initialize_header);
    } else {
      // initialize both the header and fields
      __ jmp(initialize_object);
    }
  }

  // Allocation in the shared Eden, if allowed.
  //
  // rdx: instance size in bytes
  if (allow_shared_alloc) {
    __ bind(allocate_shared);

    ExternalAddress top((address)Universe::heap()->top_addr());
    ExternalAddress end((address)Universe::heap()->end_addr());

    const Register RtopAddr = rscratch1;
    const Register RendAddr = rscratch2;

    __ lea(RtopAddr, top);
    __ lea(RendAddr, end);
    __ movptr(rax, Address(RtopAddr, 0));

    // For retries rax gets set by cmpxchgq
    Label retry;
    __ bind(retry);
    __ lea(rbx, Address(rax, rdx, Address::times_1));
    __ cmpptr(rbx, Address(RendAddr, 0));
    __ jcc(Assembler::above, slow_case);

    // Compare rax with the top addr, and if still equal, store the new
    // top addr in rbx at the address of the top addr pointer. Sets ZF if was
    // equal, and clears it otherwise. Use lock prefix for atomicity on MPs.
    //
    // rax: object begin
    // rbx: object end
    // rdx: instance size in bytes
    if (os::is_MP()) {
      __ lock();
    }
    __ cmpxchgptr(rbx, Address(RtopAddr, 0));

    // if someone beat us on the allocation, try again, otherwise continue
    __ jcc(Assembler::notEqual, retry);
  }

  if (UseTLAB || Universe::heap()->supports_inline_contig_alloc()) {
    // The object is initialized before the header.  If the object size is
    // zero, go directly to the header initialization.
    __ bind(initialize_object);
    __ decrementl(rdx, sizeof(oopDesc));
    __ jcc(Assembler::zero, initialize_header);

    // Initialize object fields
    __ xorl(rcx, rcx); // use zero reg to clear memory (shorter code)
    __ shrl(rdx, LogBytesPerLong);  // divide by oopSize to simplify the loop
    {
      Label loop;
      __ bind(loop);
      __ movq(Address(rax, rdx, Address::times_8,
                      sizeof(oopDesc) - oopSize),
              rcx);
      __ decrementl(rdx);
      __ jcc(Assembler::notZero, loop);
    }

    // initialize object header only.
    __ bind(initialize_header);
    if (UseBiasedLocking) {
      __ movptr(rscratch1, Address(rsi, Klass::prototype_header_offset_in_bytes() + klassOopDesc::klass_part_offset_in_bytes()));
      __ movptr(Address(rax, oopDesc::mark_offset_in_bytes()), rscratch1);
    } else {
      __ movptr(Address(rax, oopDesc::mark_offset_in_bytes()),
               (intptr_t) markOopDesc::prototype()); // header (address 0x1)
    }
    __ xorl(rcx, rcx); // use zero reg to clear memory (shorter code)
    __ store_klass_gap(rax, rcx);  // zero klass gap for compressed oops
    __ store_klass(rax, rsi);      // store klass last

    {
      SkipIfEqual skip(_masm, &DTraceAllocProbes, false);
      // Trigger dtrace event for fastpath
      __ push(atos); // save the return value
      __ call_VM_leaf(
           CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_object_alloc), rax);
      __ pop(atos); // restore the return value

    }
    __ jmp(done);
  }


  // slow case
  __ bind(slow_case);
  __ get_constant_pool(c_rarg1);
  __ get_unsigned_2_byte_index_at_bcp(c_rarg2, 1);
  call_VM(rax, CAST_FROM_FN_PTR(address, InterpreterRuntime::_new), c_rarg1, c_rarg2);
  __ verify_oop(rax);

  // continue
  __ bind(done);
}

void TemplateTable::newarray() {
  transition(itos, atos);
  __ load_unsigned_byte(c_rarg1, at_bcp(1));
  __ movl(c_rarg2, rax);
  call_VM(rax, CAST_FROM_FN_PTR(address, InterpreterRuntime::newarray),
          c_rarg1, c_rarg2);
}

void TemplateTable::anewarray() {
  transition(itos, atos);
  __ get_unsigned_2_byte_index_at_bcp(c_rarg2, 1);
  __ get_constant_pool(c_rarg1);
  __ movl(c_rarg3, rax);
  call_VM(rax, CAST_FROM_FN_PTR(address, InterpreterRuntime::anewarray),
          c_rarg1, c_rarg2, c_rarg3);
}

void TemplateTable::arraylength() {
  transition(atos, itos);
  __ null_check(rax, arrayOopDesc::length_offset_in_bytes());
  __ movl(rax, Address(rax, arrayOopDesc::length_offset_in_bytes()));
}

void TemplateTable::checkcast() {
  transition(atos, atos);
  Label done, is_null, ok_is_subtype, quicked, resolved;
  __ testptr(rax, rax); // object is in rax
  __ jcc(Assembler::zero, is_null);

  // Get cpool & tags index
  __ get_cpool_and_tags(rcx, rdx); // rcx=cpool, rdx=tags array
  __ get_unsigned_2_byte_index_at_bcp(rbx, 1); // rbx=index
  // See if bytecode has already been quicked
  __ cmpb(Address(rdx, rbx,
                  Address::times_1,
                  typeArrayOopDesc::header_size(T_BYTE) * wordSize),
          JVM_CONSTANT_Class);
  __ jcc(Assembler::equal, quicked);
  __ push(atos); // save receiver for result, and for GC
  __ mov(r12, rcx); // save rcx XXX
  call_VM(rax, CAST_FROM_FN_PTR(address, InterpreterRuntime::quicken_io_cc));
  __ movq(rcx, r12); // restore rcx XXX
  __ reinit_heapbase();
  __ pop_ptr(rdx); // restore receiver
  __ jmpb(resolved);

  // Get superklass in rax and subklass in rbx
  __ bind(quicked);
  __ mov(rdx, rax); // Save object in rdx; rax needed for subtype check
  __ movptr(rax, Address(rcx, rbx,
                       Address::times_8, sizeof(constantPoolOopDesc)));

  __ bind(resolved);
  __ load_klass(rbx, rdx);

  // Generate subtype check.  Blows rcx, rdi.  Object in rdx.
  // Superklass in rax.  Subklass in rbx.
  __ gen_subtype_check(rbx, ok_is_subtype);

  // Come here on failure
  __ push_ptr(rdx);
  // object is at TOS
  __ jump(ExternalAddress(Interpreter::_throw_ClassCastException_entry));

  // Come here on success
  __ bind(ok_is_subtype);
  __ mov(rax, rdx); // Restore object in rdx

  // Collect counts on whether this check-cast sees NULLs a lot or not.
  if (ProfileInterpreter) {
    __ jmp(done);
    __ bind(is_null);
    __ profile_null_seen(rcx);
  } else {
    __ bind(is_null);   // same as 'done'
  }
  __ bind(done);
}

void TemplateTable::instanceof() {
  transition(atos, itos);
  Label done, is_null, ok_is_subtype, quicked, resolved;
  __ testptr(rax, rax);
  __ jcc(Assembler::zero, is_null);

  // Get cpool & tags index
  __ get_cpool_and_tags(rcx, rdx); // rcx=cpool, rdx=tags array
  __ get_unsigned_2_byte_index_at_bcp(rbx, 1); // rbx=index
  // See if bytecode has already been quicked
  __ cmpb(Address(rdx, rbx,
                  Address::times_1,
                  typeArrayOopDesc::header_size(T_BYTE) * wordSize),
          JVM_CONSTANT_Class);
  __ jcc(Assembler::equal, quicked);

  __ push(atos); // save receiver for result, and for GC
  __ mov(r12, rcx); // save rcx
  call_VM(rax, CAST_FROM_FN_PTR(address, InterpreterRuntime::quicken_io_cc));
  __ movq(rcx, r12); // restore rcx
  __ reinit_heapbase();
  __ pop_ptr(rdx); // restore receiver
  __ load_klass(rdx, rdx);
  __ jmpb(resolved);

  // Get superklass in rax and subklass in rdx
  __ bind(quicked);
  __ load_klass(rdx, rax);
  __ movptr(rax, Address(rcx, rbx,
                         Address::times_8, sizeof(constantPoolOopDesc)));

  __ bind(resolved);

  // Generate subtype check.  Blows rcx, rdi
  // Superklass in rax.  Subklass in rdx.
  __ gen_subtype_check(rdx, ok_is_subtype);

  // Come here on failure
  __ xorl(rax, rax);
  __ jmpb(done);
  // Come here on success
  __ bind(ok_is_subtype);
  __ movl(rax, 1);

  // Collect counts on whether this test sees NULLs a lot or not.
  if (ProfileInterpreter) {
    __ jmp(done);
    __ bind(is_null);
    __ profile_null_seen(rcx);
  } else {
    __ bind(is_null);   // same as 'done'
  }
  __ bind(done);
  // rax = 0: obj == NULL or  obj is not an instanceof the specified klass
  // rax = 1: obj != NULL and obj is     an instanceof the specified klass
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
             c_rarg1, r13);
  __ mov(rbx, rax);

  // post the breakpoint event
  __ get_method(c_rarg1);
  __ call_VM(noreg,
             CAST_FROM_FN_PTR(address, InterpreterRuntime::_breakpoint),
             c_rarg1, r13);

  // complete the execution of original bytecode
  __ dispatch_only_normal(vtos);
}

//-----------------------------------------------------------------------------
// Exceptions

void TemplateTable::athrow() {
  transition(atos, vtos);
  __ null_check(rax);
  __ jump(ExternalAddress(Interpreter::throw_exception_entry()));
}

//-----------------------------------------------------------------------------
// Synchronization
//
// Note: monitorenter & exit are symmetric routines; which is reflected
//       in the assembly code structure as well
//
// Stack layout:
//
// [expressions  ] <--- rsp               = expression stack top
// ..
// [expressions  ]
// [monitor entry] <--- monitor block top = expression stack bot
// ..
// [monitor entry]
// [frame data   ] <--- monitor block bot
// ...
// [saved rbp    ] <--- rbp
void TemplateTable::monitorenter() {
  transition(atos, vtos);

  // check for NULL object
  __ null_check(rax);

  const Address monitor_block_top(
        rbp, frame::interpreter_frame_monitor_block_top_offset * wordSize);
  const Address monitor_block_bot(
        rbp, frame::interpreter_frame_initial_sp_offset * wordSize);
  const int entry_size = frame::interpreter_frame_monitor_size() * wordSize;

  Label allocated;

  // initialize entry pointer
  __ xorl(c_rarg1, c_rarg1); // points to free slot or NULL

  // find a free slot in the monitor block (result in c_rarg1)
  {
    Label entry, loop, exit;
    __ movptr(c_rarg3, monitor_block_top); // points to current entry,
                                     // starting with top-most entry
    __ lea(c_rarg2, monitor_block_bot); // points to word before bottom
                                     // of monitor block
    __ jmpb(entry);

    __ bind(loop);
    // check if current entry is used
    __ cmpptr(Address(c_rarg3, BasicObjectLock::obj_offset_in_bytes()), (int32_t) NULL_WORD);
    // if not used then remember entry in c_rarg1
    __ cmov(Assembler::equal, c_rarg1, c_rarg3);
    // check if current entry is for same object
    __ cmpptr(rax, Address(c_rarg3, BasicObjectLock::obj_offset_in_bytes()));
    // if same object then stop searching
    __ jccb(Assembler::equal, exit);
    // otherwise advance to next entry
    __ addptr(c_rarg3, entry_size);
    __ bind(entry);
    // check if bottom reached
    __ cmpptr(c_rarg3, c_rarg2);
    // if not at bottom then check this entry
    __ jcc(Assembler::notEqual, loop);
    __ bind(exit);
  }

  __ testptr(c_rarg1, c_rarg1); // check if a slot has been found
  __ jcc(Assembler::notZero, allocated); // if found, continue with that one

  // allocate one if there's no free slot
  {
    Label entry, loop;
    // 1. compute new pointers             // rsp: old expression stack top
    __ movptr(c_rarg1, monitor_block_bot); // c_rarg1: old expression stack bottom
    __ subptr(rsp, entry_size);            // move expression stack top
    __ subptr(c_rarg1, entry_size);        // move expression stack bottom
    __ mov(c_rarg3, rsp);                  // set start value for copy loop
    __ movptr(monitor_block_bot, c_rarg1); // set new monitor block bottom
    __ jmp(entry);
    // 2. move expression stack contents
    __ bind(loop);
    __ movptr(c_rarg2, Address(c_rarg3, entry_size)); // load expression stack
                                                      // word from old location
    __ movptr(Address(c_rarg3, 0), c_rarg2);          // and store it at new location
    __ addptr(c_rarg3, wordSize);                     // advance to next word
    __ bind(entry);
    __ cmpptr(c_rarg3, c_rarg1);            // check if bottom reached
    __ jcc(Assembler::notEqual, loop);      // if not at bottom then
                                            // copy next word
  }

  // call run-time routine
  // c_rarg1: points to monitor entry
  __ bind(allocated);

  // Increment bcp to point to the next bytecode, so exception
  // handling for async. exceptions work correctly.
  // The object has already been poped from the stack, so the
  // expression stack looks correct.
  __ increment(r13);

  // store object
  __ movptr(Address(c_rarg1, BasicObjectLock::obj_offset_in_bytes()), rax);
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

  // check for NULL object
  __ null_check(rax);

  const Address monitor_block_top(
        rbp, frame::interpreter_frame_monitor_block_top_offset * wordSize);
  const Address monitor_block_bot(
        rbp, frame::interpreter_frame_initial_sp_offset * wordSize);
  const int entry_size = frame::interpreter_frame_monitor_size() * wordSize;

  Label found;

  // find matching slot
  {
    Label entry, loop;
    __ movptr(c_rarg1, monitor_block_top); // points to current entry,
                                     // starting with top-most entry
    __ lea(c_rarg2, monitor_block_bot); // points to word before bottom
                                     // of monitor block
    __ jmpb(entry);

    __ bind(loop);
    // check if current entry is for same object
    __ cmpptr(rax, Address(c_rarg1, BasicObjectLock::obj_offset_in_bytes()));
    // if same object then stop searching
    __ jcc(Assembler::equal, found);
    // otherwise advance to next entry
    __ addptr(c_rarg1, entry_size);
    __ bind(entry);
    // check if bottom reached
    __ cmpptr(c_rarg1, c_rarg2);
    // if not at bottom then check this entry
    __ jcc(Assembler::notEqual, loop);
  }

  // error handling. Unlocking was not block-structured
  __ call_VM(noreg, CAST_FROM_FN_PTR(address,
                   InterpreterRuntime::throw_illegal_monitor_state_exception));
  __ should_not_reach_here();

  // call run-time routine
  // rsi: points to monitor entry
  __ bind(found);
  __ push_ptr(rax); // make sure object is on stack (contract with oopMaps)
  __ unlock_object(c_rarg1);
  __ pop_ptr(rax); // discard object
}


// Wide instructions
void TemplateTable::wide() {
  transition(vtos, vtos);
  __ load_unsigned_byte(rbx, at_bcp(1));
  __ lea(rscratch1, ExternalAddress((address)Interpreter::_wentry_point));
  __ jmp(Address(rscratch1, rbx, Address::times_8));
  // Note: the r13 increment step is part of the individual wide
  // bytecode implementations
}


// Multi arrays
void TemplateTable::multianewarray() {
  transition(vtos, atos);
  __ load_unsigned_byte(rax, at_bcp(3)); // get number of dimensions
  // last dim is on top of stack; we want address of first one:
  // first_addr = last_addr + (ndims - 1) * wordSize
  __ lea(c_rarg1, Address(rsp, rax, Address::times_8, -wordSize));
  call_VM(rax,
          CAST_FROM_FN_PTR(address, InterpreterRuntime::multianewarray),
          c_rarg1);
  __ load_unsigned_byte(rbx, at_bcp(3));
  __ lea(rsp, Address(rsp, rbx, Address::times_8));
}
#endif // !CC_INTERP
