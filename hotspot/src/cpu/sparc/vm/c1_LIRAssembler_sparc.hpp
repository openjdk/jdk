/*
 * Copyright (c) 2000, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_C1_LIRASSEMBLER_SPARC_HPP
#define CPU_SPARC_VM_C1_LIRASSEMBLER_SPARC_HPP

 private:

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  //
  // Sparc load/store emission
  //
  // The sparc ld/st instructions cannot accomodate displacements > 13 bits long.
  // The following "pseudo" sparc instructions (load/store) make it easier to use the indexed addressing mode
  // by allowing 32 bit displacements:
  //
  //    When disp <= 13 bits long, a single load or store instruction is emitted with (disp + [d]).
  //    When disp >  13 bits long, code is emitted to set the displacement into the O7 register,
  //       and then a load or store is emitted with ([O7] + [d]).
  //

  int store(LIR_Opr from_reg, Register base, int offset, BasicType type, bool wide, bool unaligned);
  int store(LIR_Opr from_reg, Register base, Register disp, BasicType type, bool wide);

  int load(Register base, int offset, LIR_Opr to_reg, BasicType type, bool wide, bool unaligned);
  int load(Register base, Register disp, LIR_Opr to_reg, BasicType type, bool wide);

  void monitorexit(LIR_Opr obj_opr, LIR_Opr lock_opr, Register hdr, int monitor_no);

  int shift_amount(BasicType t);

  static bool is_single_instruction(LIR_Op* op);

  // Record the type of the receiver in ReceiverTypeData
  void type_profile_helper(Register mdo, int mdo_offset_bias,
                           ciMethodData *md, ciProfileData *data,
                           Register recv, Register tmp1, Label* update_done);
  // Setup pointers to MDO, MDO slot, also compute offset bias to access the slot.
  void setup_md_access(ciMethod* method, int bci,
                       ciMethodData*& md, ciProfileData*& data, int& mdo_offset_bias);

  enum {
    _call_stub_size = 68,
    _call_aot_stub_size = 0,
    _exception_handler_size = DEBUG_ONLY(1*K) NOT_DEBUG(128),
    _deopt_handler_size = DEBUG_ONLY(1*K) NOT_DEBUG(64)
  };

 public:
  void   pack64(LIR_Opr src, LIR_Opr dst);
  void unpack64(LIR_Opr src, LIR_Opr dst);

#endif // CPU_SPARC_VM_C1_LIRASSEMBLER_SPARC_HPP
