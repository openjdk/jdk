/*
 * Copyright (c) 1999, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_C1_FRAMEMAP_SPARC_HPP
#define CPU_SPARC_VM_C1_FRAMEMAP_SPARC_HPP

 public:

  enum {
    nof_reg_args = 6,   // registers o0-o5 are available for parameter passing
    first_available_sp_in_frame = frame::memory_parameter_word_sp_offset * BytesPerWord,
    frame_pad_in_bytes = 0
  };

  static const int pd_c_runtime_reserved_arg_size;

  static LIR_Opr G0_opr;
  static LIR_Opr G1_opr;
  static LIR_Opr G2_opr;
  static LIR_Opr G3_opr;
  static LIR_Opr G4_opr;
  static LIR_Opr G5_opr;
  static LIR_Opr G6_opr;
  static LIR_Opr G7_opr;
  static LIR_Opr O0_opr;
  static LIR_Opr O1_opr;
  static LIR_Opr O2_opr;
  static LIR_Opr O3_opr;
  static LIR_Opr O4_opr;
  static LIR_Opr O5_opr;
  static LIR_Opr O6_opr;
  static LIR_Opr O7_opr;
  static LIR_Opr L0_opr;
  static LIR_Opr L1_opr;
  static LIR_Opr L2_opr;
  static LIR_Opr L3_opr;
  static LIR_Opr L4_opr;
  static LIR_Opr L5_opr;
  static LIR_Opr L6_opr;
  static LIR_Opr L7_opr;
  static LIR_Opr I0_opr;
  static LIR_Opr I1_opr;
  static LIR_Opr I2_opr;
  static LIR_Opr I3_opr;
  static LIR_Opr I4_opr;
  static LIR_Opr I5_opr;
  static LIR_Opr I6_opr;
  static LIR_Opr I7_opr;

  static LIR_Opr SP_opr;
  static LIR_Opr FP_opr;

  static LIR_Opr G0_oop_opr;
  static LIR_Opr G1_oop_opr;
  static LIR_Opr G2_oop_opr;
  static LIR_Opr G3_oop_opr;
  static LIR_Opr G4_oop_opr;
  static LIR_Opr G5_oop_opr;
  static LIR_Opr G6_oop_opr;
  static LIR_Opr G7_oop_opr;
  static LIR_Opr O0_oop_opr;
  static LIR_Opr O1_oop_opr;
  static LIR_Opr O2_oop_opr;
  static LIR_Opr O3_oop_opr;
  static LIR_Opr O4_oop_opr;
  static LIR_Opr O5_oop_opr;
  static LIR_Opr O6_oop_opr;
  static LIR_Opr O7_oop_opr;
  static LIR_Opr L0_oop_opr;
  static LIR_Opr L1_oop_opr;
  static LIR_Opr L2_oop_opr;
  static LIR_Opr L3_oop_opr;
  static LIR_Opr L4_oop_opr;
  static LIR_Opr L5_oop_opr;
  static LIR_Opr L6_oop_opr;
  static LIR_Opr L7_oop_opr;
  static LIR_Opr I0_oop_opr;
  static LIR_Opr I1_oop_opr;
  static LIR_Opr I2_oop_opr;
  static LIR_Opr I3_oop_opr;
  static LIR_Opr I4_oop_opr;
  static LIR_Opr I5_oop_opr;
  static LIR_Opr I6_oop_opr;
  static LIR_Opr I7_oop_opr;

  static LIR_Opr G0_metadata_opr;
  static LIR_Opr G1_metadata_opr;
  static LIR_Opr G2_metadata_opr;
  static LIR_Opr G3_metadata_opr;
  static LIR_Opr G4_metadata_opr;
  static LIR_Opr G5_metadata_opr;
  static LIR_Opr G6_metadata_opr;
  static LIR_Opr G7_metadata_opr;
  static LIR_Opr O0_metadata_opr;
  static LIR_Opr O1_metadata_opr;
  static LIR_Opr O2_metadata_opr;
  static LIR_Opr O3_metadata_opr;
  static LIR_Opr O4_metadata_opr;
  static LIR_Opr O5_metadata_opr;
  static LIR_Opr O6_metadata_opr;
  static LIR_Opr O7_metadata_opr;
  static LIR_Opr L0_metadata_opr;
  static LIR_Opr L1_metadata_opr;
  static LIR_Opr L2_metadata_opr;
  static LIR_Opr L3_metadata_opr;
  static LIR_Opr L4_metadata_opr;
  static LIR_Opr L5_metadata_opr;
  static LIR_Opr L6_metadata_opr;
  static LIR_Opr L7_metadata_opr;
  static LIR_Opr I0_metadata_opr;
  static LIR_Opr I1_metadata_opr;
  static LIR_Opr I2_metadata_opr;
  static LIR_Opr I3_metadata_opr;
  static LIR_Opr I4_metadata_opr;
  static LIR_Opr I5_metadata_opr;
  static LIR_Opr I6_metadata_opr;
  static LIR_Opr I7_metadata_opr;

  static LIR_Opr in_long_opr;
  static LIR_Opr out_long_opr;
  static LIR_Opr g1_long_single_opr;

  static LIR_Opr F0_opr;
  static LIR_Opr F0_double_opr;

  static LIR_Opr Oexception_opr;
  static LIR_Opr Oissuing_pc_opr;

 private:
  static FloatRegister  _fpu_regs [nof_fpu_regs];

  static LIR_Opr as_long_single_opr(Register r) {
    return LIR_OprFact::double_cpu(cpu_reg2rnr(r), cpu_reg2rnr(r));
  }
  static LIR_Opr as_long_pair_opr(Register r) {
    return LIR_OprFact::double_cpu(cpu_reg2rnr(r->successor()), cpu_reg2rnr(r));
  }

 public:

  static LIR_Opr as_long_opr(Register r) {
    return as_long_single_opr(r);
  }
  static LIR_Opr as_pointer_opr(Register r) {
    return as_long_single_opr(r);
  }
  static LIR_Opr as_float_opr(FloatRegister r) {
    return LIR_OprFact::single_fpu(r->encoding());
  }
  static LIR_Opr as_double_opr(FloatRegister r) {
    return LIR_OprFact::double_fpu(r->successor()->encoding(), r->encoding());
  }

  static FloatRegister nr2floatreg (int rnr);

  static VMReg fpu_regname (int n);

  static bool is_caller_save_register (LIR_Opr  reg);
  static bool is_caller_save_register (Register r);

  static int nof_caller_save_cpu_regs() { return pd_nof_caller_save_cpu_regs_frame_map; }
  static int last_cpu_reg()             { return pd_last_cpu_reg;  }

#endif // CPU_SPARC_VM_C1_FRAMEMAP_SPARC_HPP
