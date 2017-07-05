/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
  // byte flip mask for sha256
  static address _pshuffle_byte_flip_mask_addr;

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
  static address pshuffle_byte_flip_mask_addr() { return _pshuffle_byte_flip_mask_addr; }
  static void generate_CRC32C_table(bool is_pclmulqdq_supported);
#endif // CPU_X86_VM_STUBROUTINES_X86_32_HPP
