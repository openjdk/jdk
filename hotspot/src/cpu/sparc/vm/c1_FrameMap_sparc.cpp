/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_c1_FrameMap_sparc.cpp.incl"


const int FrameMap::pd_c_runtime_reserved_arg_size = 7;


LIR_Opr FrameMap::map_to_opr(BasicType type, VMRegPair* reg, bool outgoing) {
  LIR_Opr opr = LIR_OprFact::illegalOpr;
  VMReg r_1 = reg->first();
  VMReg r_2 = reg->second();
  if (r_1->is_stack()) {
    // Convert stack slot to an SP offset
    // The calling convention does not count the SharedRuntime::out_preserve_stack_slots() value
    // so we must add it in here.
    int st_off = (r_1->reg2stack() + SharedRuntime::out_preserve_stack_slots()) * VMRegImpl::stack_slot_size;
    opr = LIR_OprFact::address(new LIR_Address(SP_opr, st_off + STACK_BIAS, type));
  } else if (r_1->is_Register()) {
    Register reg = r_1->as_Register();
    if (outgoing) {
      assert(!reg->is_in(), "should be using I regs");
    } else {
      assert(!reg->is_out(), "should be using O regs");
    }
    if (r_2->is_Register() && (type == T_LONG || type == T_DOUBLE)) {
      opr = as_long_opr(reg);
    } else if (type == T_OBJECT || type == T_ARRAY) {
      opr = as_oop_opr(reg);
    } else {
      opr = as_opr(reg);
    }
  } else if (r_1->is_FloatRegister()) {
    assert(type == T_DOUBLE || type == T_FLOAT, "wrong type");
    FloatRegister f = r_1->as_FloatRegister();
    if (type == T_DOUBLE) {
      opr = as_double_opr(f);
    } else {
      opr = as_float_opr(f);
    }
  }
  return opr;
}

//               FrameMap
//--------------------------------------------------------

FloatRegister FrameMap::_fpu_regs [FrameMap::nof_fpu_regs];

// some useful constant RInfo's:
LIR_Opr FrameMap::in_long_opr;
LIR_Opr FrameMap::out_long_opr;

LIR_Opr FrameMap::F0_opr;
LIR_Opr FrameMap::F0_double_opr;

LIR_Opr FrameMap::G0_opr;
LIR_Opr FrameMap::G1_opr;
LIR_Opr FrameMap::G2_opr;
LIR_Opr FrameMap::G3_opr;
LIR_Opr FrameMap::G4_opr;
LIR_Opr FrameMap::G5_opr;
LIR_Opr FrameMap::G6_opr;
LIR_Opr FrameMap::G7_opr;
LIR_Opr FrameMap::O0_opr;
LIR_Opr FrameMap::O1_opr;
LIR_Opr FrameMap::O2_opr;
LIR_Opr FrameMap::O3_opr;
LIR_Opr FrameMap::O4_opr;
LIR_Opr FrameMap::O5_opr;
LIR_Opr FrameMap::O6_opr;
LIR_Opr FrameMap::O7_opr;
LIR_Opr FrameMap::L0_opr;
LIR_Opr FrameMap::L1_opr;
LIR_Opr FrameMap::L2_opr;
LIR_Opr FrameMap::L3_opr;
LIR_Opr FrameMap::L4_opr;
LIR_Opr FrameMap::L5_opr;
LIR_Opr FrameMap::L6_opr;
LIR_Opr FrameMap::L7_opr;
LIR_Opr FrameMap::I0_opr;
LIR_Opr FrameMap::I1_opr;
LIR_Opr FrameMap::I2_opr;
LIR_Opr FrameMap::I3_opr;
LIR_Opr FrameMap::I4_opr;
LIR_Opr FrameMap::I5_opr;
LIR_Opr FrameMap::I6_opr;
LIR_Opr FrameMap::I7_opr;

LIR_Opr FrameMap::G0_oop_opr;
LIR_Opr FrameMap::G1_oop_opr;
LIR_Opr FrameMap::G2_oop_opr;
LIR_Opr FrameMap::G3_oop_opr;
LIR_Opr FrameMap::G4_oop_opr;
LIR_Opr FrameMap::G5_oop_opr;
LIR_Opr FrameMap::G6_oop_opr;
LIR_Opr FrameMap::G7_oop_opr;
LIR_Opr FrameMap::O0_oop_opr;
LIR_Opr FrameMap::O1_oop_opr;
LIR_Opr FrameMap::O2_oop_opr;
LIR_Opr FrameMap::O3_oop_opr;
LIR_Opr FrameMap::O4_oop_opr;
LIR_Opr FrameMap::O5_oop_opr;
LIR_Opr FrameMap::O6_oop_opr;
LIR_Opr FrameMap::O7_oop_opr;
LIR_Opr FrameMap::L0_oop_opr;
LIR_Opr FrameMap::L1_oop_opr;
LIR_Opr FrameMap::L2_oop_opr;
LIR_Opr FrameMap::L3_oop_opr;
LIR_Opr FrameMap::L4_oop_opr;
LIR_Opr FrameMap::L5_oop_opr;
LIR_Opr FrameMap::L6_oop_opr;
LIR_Opr FrameMap::L7_oop_opr;
LIR_Opr FrameMap::I0_oop_opr;
LIR_Opr FrameMap::I1_oop_opr;
LIR_Opr FrameMap::I2_oop_opr;
LIR_Opr FrameMap::I3_oop_opr;
LIR_Opr FrameMap::I4_oop_opr;
LIR_Opr FrameMap::I5_oop_opr;
LIR_Opr FrameMap::I6_oop_opr;
LIR_Opr FrameMap::I7_oop_opr;

LIR_Opr FrameMap::SP_opr;
LIR_Opr FrameMap::FP_opr;

LIR_Opr FrameMap::Oexception_opr;
LIR_Opr FrameMap::Oissuing_pc_opr;

LIR_Opr FrameMap::_caller_save_cpu_regs[] = { 0, };
LIR_Opr FrameMap::_caller_save_fpu_regs[] = { 0, };


FloatRegister FrameMap::nr2floatreg (int rnr) {
  assert(_init_done, "tables not initialized");
  debug_only(fpu_range_check(rnr);)
  return _fpu_regs[rnr];
}


// returns true if reg could be smashed by a callee.
bool FrameMap::is_caller_save_register (LIR_Opr reg) {
  if (reg->is_single_fpu() || reg->is_double_fpu()) { return true; }
  if (reg->is_double_cpu()) {
    return is_caller_save_register(reg->as_register_lo()) ||
           is_caller_save_register(reg->as_register_hi());
  }
  return is_caller_save_register(reg->as_register());
}


NEEDS_CLEANUP   // once the new calling convention is enabled, we no
                // longer need to treat I5, I4 and L0 specially
// Because the interpreter destroys caller's I5, I4 and L0,
// we must spill them before doing a Java call as we may land in
// interpreter.
bool FrameMap::is_caller_save_register (Register r) {
  return (r->is_global() && (r != G0)) || r->is_out();
}


void FrameMap::initialize() {
  assert(!_init_done, "once");

  int i=0;
  // Register usage:
  //  O6: sp
  //  I6: fp
  //  I7: return address
  //  G0: zero
  //  G2: thread
  //  G7: not available
  //  G6: not available
  /*  0 */ map_register(i++, L0);
  /*  1 */ map_register(i++, L1);
  /*  2 */ map_register(i++, L2);
  /*  3 */ map_register(i++, L3);
  /*  4 */ map_register(i++, L4);
  /*  5 */ map_register(i++, L5);
  /*  6 */ map_register(i++, L6);
  /*  7 */ map_register(i++, L7);

  /*  8 */ map_register(i++, I0);
  /*  9 */ map_register(i++, I1);
  /* 10 */ map_register(i++, I2);
  /* 11 */ map_register(i++, I3);
  /* 12 */ map_register(i++, I4);
  /* 13 */ map_register(i++, I5);
  /* 14 */ map_register(i++, O0);
  /* 15 */ map_register(i++, O1);
  /* 16 */ map_register(i++, O2);
  /* 17 */ map_register(i++, O3);
  /* 18 */ map_register(i++, O4);
  /* 19 */ map_register(i++, O5); // <- last register visible in RegAlloc (RegAlloc::nof+cpu_regs)
  /* 20 */ map_register(i++, G1);
  /* 21 */ map_register(i++, G3);
  /* 22 */ map_register(i++, G4);
  /* 23 */ map_register(i++, G5);
  /* 24 */ map_register(i++, G0);

  // the following registers are not normally available
  /* 25 */ map_register(i++, O7);
  /* 26 */ map_register(i++, G2);
  /* 27 */ map_register(i++, O6);
  /* 28 */ map_register(i++, I6);
  /* 29 */ map_register(i++, I7);
  /* 30 */ map_register(i++, G6);
  /* 31 */ map_register(i++, G7);
  assert(i == nof_cpu_regs, "number of CPU registers");

  for (i = 0; i < nof_fpu_regs; i++) {
    _fpu_regs[i] = as_FloatRegister(i);
  }

  _init_done = true;

  in_long_opr    = as_long_opr(I0);
  out_long_opr   = as_long_opr(O0);

  G0_opr = as_opr(G0);
  G1_opr = as_opr(G1);
  G2_opr = as_opr(G2);
  G3_opr = as_opr(G3);
  G4_opr = as_opr(G4);
  G5_opr = as_opr(G5);
  G6_opr = as_opr(G6);
  G7_opr = as_opr(G7);
  O0_opr = as_opr(O0);
  O1_opr = as_opr(O1);
  O2_opr = as_opr(O2);
  O3_opr = as_opr(O3);
  O4_opr = as_opr(O4);
  O5_opr = as_opr(O5);
  O6_opr = as_opr(O6);
  O7_opr = as_opr(O7);
  L0_opr = as_opr(L0);
  L1_opr = as_opr(L1);
  L2_opr = as_opr(L2);
  L3_opr = as_opr(L3);
  L4_opr = as_opr(L4);
  L5_opr = as_opr(L5);
  L6_opr = as_opr(L6);
  L7_opr = as_opr(L7);
  I0_opr = as_opr(I0);
  I1_opr = as_opr(I1);
  I2_opr = as_opr(I2);
  I3_opr = as_opr(I3);
  I4_opr = as_opr(I4);
  I5_opr = as_opr(I5);
  I6_opr = as_opr(I6);
  I7_opr = as_opr(I7);

  G0_oop_opr = as_oop_opr(G0);
  G1_oop_opr = as_oop_opr(G1);
  G2_oop_opr = as_oop_opr(G2);
  G3_oop_opr = as_oop_opr(G3);
  G4_oop_opr = as_oop_opr(G4);
  G5_oop_opr = as_oop_opr(G5);
  G6_oop_opr = as_oop_opr(G6);
  G7_oop_opr = as_oop_opr(G7);
  O0_oop_opr = as_oop_opr(O0);
  O1_oop_opr = as_oop_opr(O1);
  O2_oop_opr = as_oop_opr(O2);
  O3_oop_opr = as_oop_opr(O3);
  O4_oop_opr = as_oop_opr(O4);
  O5_oop_opr = as_oop_opr(O5);
  O6_oop_opr = as_oop_opr(O6);
  O7_oop_opr = as_oop_opr(O7);
  L0_oop_opr = as_oop_opr(L0);
  L1_oop_opr = as_oop_opr(L1);
  L2_oop_opr = as_oop_opr(L2);
  L3_oop_opr = as_oop_opr(L3);
  L4_oop_opr = as_oop_opr(L4);
  L5_oop_opr = as_oop_opr(L5);
  L6_oop_opr = as_oop_opr(L6);
  L7_oop_opr = as_oop_opr(L7);
  I0_oop_opr = as_oop_opr(I0);
  I1_oop_opr = as_oop_opr(I1);
  I2_oop_opr = as_oop_opr(I2);
  I3_oop_opr = as_oop_opr(I3);
  I4_oop_opr = as_oop_opr(I4);
  I5_oop_opr = as_oop_opr(I5);
  I6_oop_opr = as_oop_opr(I6);
  I7_oop_opr = as_oop_opr(I7);

  FP_opr = as_pointer_opr(FP);
  SP_opr = as_pointer_opr(SP);

  F0_opr = as_float_opr(F0);
  F0_double_opr = as_double_opr(F0);

  Oexception_opr = as_oop_opr(Oexception);
  Oissuing_pc_opr = as_opr(Oissuing_pc);

  _caller_save_cpu_regs[0] = FrameMap::O0_opr;
  _caller_save_cpu_regs[1] = FrameMap::O1_opr;
  _caller_save_cpu_regs[2] = FrameMap::O2_opr;
  _caller_save_cpu_regs[3] = FrameMap::O3_opr;
  _caller_save_cpu_regs[4] = FrameMap::O4_opr;
  _caller_save_cpu_regs[5] = FrameMap::O5_opr;
  _caller_save_cpu_regs[6] = FrameMap::G1_opr;
  _caller_save_cpu_regs[7] = FrameMap::G3_opr;
  _caller_save_cpu_regs[8] = FrameMap::G4_opr;
  _caller_save_cpu_regs[9] = FrameMap::G5_opr;
  for (int i = 0; i < nof_caller_save_fpu_regs; i++) {
    _caller_save_fpu_regs[i] = LIR_OprFact::single_fpu(i);
  }
}


Address FrameMap::make_new_address(ByteSize sp_offset) const {
  return Address(SP, STACK_BIAS + in_bytes(sp_offset));
}


VMReg FrameMap::fpu_regname (int n) {
  return as_FloatRegister(n)->as_VMReg();
}


LIR_Opr FrameMap::stack_pointer() {
  return SP_opr;
}


// JSR 292
LIR_Opr FrameMap::method_handle_invoke_SP_save_opr() {
  assert(L7 == L7_mh_SP_save, "must be same register");
  return L7_opr;
}


bool FrameMap::validate_frame() {
  int max_offset = in_bytes(framesize_in_bytes());
  int java_index = 0;
  for (int i = 0; i < _incoming_arguments->length(); i++) {
    LIR_Opr opr = _incoming_arguments->at(i);
    if (opr->is_stack()) {
      max_offset = MAX2(_argument_locations->at(java_index), max_offset);
    }
    java_index += type2size[opr->type()];
  }
  return Assembler::is_simm13(max_offset + STACK_BIAS);
}
