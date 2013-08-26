/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/assembler.hpp"
#include "code/relocInfo.hpp"
#include "nativeInst_sparc.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/safepoint.hpp"

void Relocation::pd_set_data_value(address x, intptr_t o, bool verify_only) {
  NativeInstruction* ip = nativeInstruction_at(addr());
  jint inst = ip->long_at(0);
  assert(inst != NativeInstruction::illegal_instruction(), "no breakpoint");
  switch (Assembler::inv_op(inst)) {

  case Assembler::ldst_op:
    #ifdef ASSERT
      switch (Assembler::inv_op3(inst)) {
        case Assembler::lduw_op3:
        case Assembler::ldub_op3:
        case Assembler::lduh_op3:
        case Assembler::ldd_op3:
        case Assembler::ldsw_op3:
        case Assembler::ldsb_op3:
        case Assembler::ldsh_op3:
        case Assembler::ldx_op3:
        case Assembler::ldf_op3:
        case Assembler::lddf_op3:
        case Assembler::stw_op3:
        case Assembler::stb_op3:
        case Assembler::sth_op3:
        case Assembler::std_op3:
        case Assembler::stx_op3:
        case Assembler::stf_op3:
        case Assembler::stdf_op3:
        case Assembler::casa_op3:
        case Assembler::casxa_op3:
          break;
        default:
          ShouldNotReachHere();
      }
      goto do_non_sethi;
    #endif

  case Assembler::arith_op:
    #ifdef ASSERT
      switch (Assembler::inv_op3(inst)) {
        case Assembler::or_op3:
        case Assembler::add_op3:
        case Assembler::jmpl_op3:
          break;
        default:
          ShouldNotReachHere();
      }
    do_non_sethi:;
    #endif
    {
    guarantee(Assembler::inv_immed(inst), "must have a simm13 field");
    int simm13 = Assembler::low10((intptr_t)x) + o;
    guarantee(Assembler::is_simm13(simm13), "offset can't overflow simm13");
    inst &= ~Assembler::simm(    -1, 13);
    inst |=  Assembler::simm(simm13, 13);
    if (verify_only) {
      assert(ip->long_at(0) == inst, "instructions must match");
    } else {
      ip->set_long_at(0, inst);
    }
    }
    break;

  case Assembler::branch_op:
    {
#ifdef _LP64
    jint inst2;
    guarantee(Assembler::inv_op2(inst)==Assembler::sethi_op2, "must be sethi");
    if (format() != 0) {
      assert(type() == relocInfo::oop_type || type() == relocInfo::metadata_type, "only narrow oops or klasses case");
      jint np = type() == relocInfo::oop_type ? oopDesc::encode_heap_oop((oop)x) : Klass::encode_klass((Klass*)x);
      inst &= ~Assembler::hi22(-1);
      inst |=  Assembler::hi22((intptr_t)np);
      if (verify_only) {
        assert(ip->long_at(0) == inst, "instructions must match");
      } else {
        ip->set_long_at(0, inst);
      }
      inst2 = ip->long_at( NativeInstruction::nop_instruction_size );
      guarantee(Assembler::inv_op(inst2)==Assembler::arith_op, "arith op");
      if (verify_only) {
        assert(ip->long_at(NativeInstruction::nop_instruction_size) == NativeInstruction::set_data32_simm13( inst2, (intptr_t)np),
               "instructions must match");
      } else {
        ip->set_long_at(NativeInstruction::nop_instruction_size, NativeInstruction::set_data32_simm13( inst2, (intptr_t)np));
      }
      break;
    }
    if (verify_only) {
      ip->verify_data64_sethi( ip->addr_at(0), (intptr_t)x );
    } else {
      ip->set_data64_sethi( ip->addr_at(0), (intptr_t)x );
    }
#else
    guarantee(Assembler::inv_op2(inst)==Assembler::sethi_op2, "must be sethi");
    inst &= ~Assembler::hi22(     -1);
    inst |=  Assembler::hi22((intptr_t)x);
    // (ignore offset; it doesn't play into the sethi)
    if (verify_only) {
      assert(ip->long_at(0) == inst, "instructions must match");
    } else {
      ip->set_long_at(0, inst);
    }
#endif
    }
    break;

  default:
    guarantee(false, "instruction must perform arithmetic or memory access");
  }
}


address Relocation::pd_call_destination(address orig_addr) {
  intptr_t adj = 0;
  if (orig_addr != NULL) {
    // We just moved this call instruction from orig_addr to addr().
    // This means its target will appear to have grown by addr() - orig_addr.
    adj = -( addr() - orig_addr );
  }
  if (NativeCall::is_call_at(addr())) {
    NativeCall* call = nativeCall_at(addr());
    return call->destination() + adj;
  }
  if (NativeFarCall::is_call_at(addr())) {
    NativeFarCall* call = nativeFarCall_at(addr());
    return call->destination() + adj;
  }
  // Special case:  Patchable branch local to the code cache.
  // This will break badly if the code cache grows larger than a few Mb.
  NativeGeneralJump* br = nativeGeneralJump_at(addr());
  return br->jump_destination() + adj;
}


void Relocation::pd_set_call_destination(address x) {
  if (NativeCall::is_call_at(addr())) {
    NativeCall* call = nativeCall_at(addr());
    call->set_destination(x);
    return;
  }
  if (NativeFarCall::is_call_at(addr())) {
    NativeFarCall* call = nativeFarCall_at(addr());
    call->set_destination(x);
    return;
  }
  // Special case:  Patchable branch local to the code cache.
  // This will break badly if the code cache grows larger than a few Mb.
  NativeGeneralJump* br = nativeGeneralJump_at(addr());
  br->set_jump_destination(x);
}


address* Relocation::pd_address_in_code() {
  // SPARC never embeds addresses in code, at present.
  //assert(type() == relocInfo::oop_type, "only oops are inlined at present");
  return (address*)addr();
}


address Relocation::pd_get_address_from_code() {
  // SPARC never embeds addresses in code, at present.
  //assert(type() == relocInfo::oop_type, "only oops are inlined at present");
  return *(address*)addr();
}

void poll_Relocation::fix_relocation_after_move(const CodeBuffer* src, CodeBuffer* dest) {
}

void poll_return_Relocation::fix_relocation_after_move(const CodeBuffer* src, CodeBuffer* dest) {
}

void metadata_Relocation::pd_fix_value(address x) {
}
