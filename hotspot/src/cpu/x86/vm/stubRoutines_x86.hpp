/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_VM_STUBROUTINES_X86_HPP
#define CPU_X86_VM_STUBROUTINES_X86_HPP

// This file holds the platform specific parts of the StubRoutines
// definition. See stubRoutines.hpp for a description on how to
// extend it.

static bool returns_to_call_stub(address return_pc) { return return_pc == _call_stub_return_address; }

enum platform_dependent_constants {
  code_size1 = 20000 LP64_ONLY(+10000),         // simply increase if too small (assembler will crash if too small)
  code_size2 = 33800 LP64_ONLY(+10000)           // simply increase if too small (assembler will crash if too small)
};

class x86 {
 friend class StubGenerator;
 friend class VMStructs;

#ifdef _LP64
 private:
  static address _get_previous_fp_entry;
  static address _get_previous_sp_entry;

  static address _f2i_fixup;
  static address _f2l_fixup;
  static address _d2i_fixup;
  static address _d2l_fixup;

  static address _float_sign_mask;
  static address _float_sign_flip;
  static address _double_sign_mask;
  static address _double_sign_flip;

 public:

  static address get_previous_fp_entry() {
    return _get_previous_fp_entry;
  }

  static address get_previous_sp_entry() {
    return _get_previous_sp_entry;
  }

  static address f2i_fixup() {
    return _f2i_fixup;
  }

  static address f2l_fixup() {
    return _f2l_fixup;
  }

  static address d2i_fixup() {
    return _d2i_fixup;
  }

  static address d2l_fixup() {
    return _d2l_fixup;
  }

  static address float_sign_mask() {
    return _float_sign_mask;
  }

  static address float_sign_flip() {
    return _float_sign_flip;
  }

  static address double_sign_mask() {
    return _double_sign_mask;
  }

  static address double_sign_flip() {
    return _double_sign_flip;
  }
#else // !LP64

 private:
  static address _verify_fpu_cntrl_wrd_entry;

 public:
  static address verify_fpu_cntrl_wrd_entry() { return _verify_fpu_cntrl_wrd_entry; }

#endif // !LP64

 private:
  static address _verify_mxcsr_entry;
  // shuffle mask for fixing up 128-bit words consisting of big-endian 32-bit integers
  static address _key_shuffle_mask_addr;

  //shuffle mask for big-endian 128-bit integers
  static address _counter_shuffle_mask_addr;

  // masks and table for CRC32
  static uint64_t _crc_by128_masks[];
  static juint    _crc_table[];
  // table for CRC32C
  static juint* _crc32c_table;
  // swap mask for ghash
  static address _ghash_long_swap_mask_addr;
  static address _ghash_byte_swap_mask_addr;

  // upper word mask for sha1
  static address _upper_word_mask_addr;
  // byte flip mask for sha1
  static address _shuffle_byte_flip_mask_addr;

  //k256 table for sha256
  static juint _k256[];
  static address _k256_adr;
#ifdef _LP64
  static juint _k256_W[];
  static address _k256_W_adr;
  static julong _k512_W[];
  static address _k512_W_addr;
  // byte flip mask for sha512
  static address _pshuffle_byte_flip_mask_addr_sha512;
#endif
  // byte flip mask for sha256
  static address _pshuffle_byte_flip_mask_addr;

  //tables common for LIBM sin and cos
  static juint _ONEHALF[];
  static address _ONEHALF_adr;
  static juint _P_2[];
  static address _P_2_adr;
  static juint _SC_4[];
  static address _SC_4_adr;
  static juint _Ctable[];
  static address _Ctable_adr;
  static juint _SC_2[];
  static address _SC_2_adr;
  static juint _SC_3[];
  static address _SC_3_adr;
  static juint _SC_1[];
  static address _SC_1_adr;
  static juint _PI_INV_TABLE[];
  static address _PI_INV_TABLE_adr;
  static juint _PI_4[];
  static address _PI_4_adr;
  static juint _PI32INV[];
  static address _PI32INV_adr;
  static juint _SIGN_MASK[];
  static address _SIGN_MASK_adr;
  static juint _P_1[];
  static address _P_1_adr;
  static juint _P_3[];
  static address _P_3_adr;
  static juint _NEG_ZERO[];
  static address _NEG_ZERO_adr;

  //tables common for LIBM sincos and tancot
  static juint _L_2il0floatpacket_0[];
  static address _L_2il0floatpacket_0_adr;
  static juint _Pi4Inv[];
  static address _Pi4Inv_adr;
  static juint _Pi4x3[];
  static address _Pi4x3_adr;
  static juint _Pi4x4[];
  static address _Pi4x4_adr;
  static juint _ones[];
  static address _ones_adr;

 public:
  static address verify_mxcsr_entry()    { return _verify_mxcsr_entry; }
  static address key_shuffle_mask_addr() { return _key_shuffle_mask_addr; }
  static address counter_shuffle_mask_addr() { return _counter_shuffle_mask_addr; }
  static address crc_by128_masks_addr()  { return (address)_crc_by128_masks; }
  static address ghash_long_swap_mask_addr() { return _ghash_long_swap_mask_addr; }
  static address ghash_byte_swap_mask_addr() { return _ghash_byte_swap_mask_addr; }
  static address upper_word_mask_addr() { return _upper_word_mask_addr; }
  static address shuffle_byte_flip_mask_addr() { return _shuffle_byte_flip_mask_addr; }
  static address k256_addr()      { return _k256_adr; }
#ifdef _LP64
  static address k256_W_addr()    { return _k256_W_adr; }
  static address k512_W_addr()    { return _k512_W_addr; }
  static address pshuffle_byte_flip_mask_addr_sha512() { return _pshuffle_byte_flip_mask_addr_sha512; }
#endif
  static address pshuffle_byte_flip_mask_addr() { return _pshuffle_byte_flip_mask_addr; }
  static void generate_CRC32C_table(bool is_pclmulqdq_supported);
  static address _ONEHALF_addr()      { return _ONEHALF_adr; }
  static address _P_2_addr()      { return _P_2_adr; }
  static address _SC_4_addr()      { return _SC_4_adr; }
  static address _Ctable_addr()      { return _Ctable_adr; }
  static address _SC_2_addr()      { return _SC_2_adr; }
  static address _SC_3_addr()      { return _SC_3_adr; }
  static address _SC_1_addr()      { return _SC_1_adr; }
  static address _PI_INV_TABLE_addr()      { return _PI_INV_TABLE_adr; }
  static address _PI_4_addr()      { return _PI_4_adr; }
  static address _PI32INV_addr()      { return _PI32INV_adr; }
  static address _SIGN_MASK_addr()      { return _SIGN_MASK_adr; }
  static address _P_1_addr()      { return _P_1_adr; }
  static address _P_3_addr()      { return _P_3_adr; }
  static address _NEG_ZERO_addr()      { return _NEG_ZERO_adr; }
  static address _L_2il0floatpacket_0_addr()      { return _L_2il0floatpacket_0_adr; }
  static address _Pi4Inv_addr()      { return _Pi4Inv_adr; }
  static address _Pi4x3_addr()      { return _Pi4x3_adr; }
  static address _Pi4x4_addr()      { return _Pi4x4_adr; }
  static address _ones_addr()      { return _ones_adr; }

};

#endif // CPU_X86_VM_STUBROUTINES_X86_HPP
