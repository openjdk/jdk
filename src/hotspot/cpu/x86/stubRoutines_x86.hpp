/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_STUBROUTINES_X86_HPP
#define CPU_X86_STUBROUTINES_X86_HPP

// This file holds the platform specific parts of the StubRoutines
// definition. See stubRoutines.hpp for a description on how to
// extend it.

static bool returns_to_call_stub(address return_pc) { return return_pc == _call_stub_return_address; }

// emit enum used to size per-blob code buffers

#define DEFINE_BLOB_SIZE(blob_name, size) \
  _ ## blob_name ## _code_size = size,

enum platform_dependent_constants {
  STUBGEN_ARCH_BLOBS_DO(DEFINE_BLOB_SIZE)
};

#undef DEFINE_BLOB_SIZE

class x86 {
 friend class StubGenerator;
 friend class StubRoutines;
 friend class VMStructs;

  // declare fields for arch-specific entries

#define DECLARE_ARCH_ENTRY(arch, blob_name, stub_name, field_name, getter_name) \
  static address STUB_FIELD_NAME(field_name) ;

#define DECLARE_ARCH_ENTRY_INIT(arch, blob_name, stub_name, field_name, getter_name, init_function) \
  DECLARE_ARCH_ENTRY(arch, blob_name, stub_name, field_name, getter_name)

private:
  STUBGEN_ARCH_ENTRIES_DO(DECLARE_ARCH_ENTRY, DECLARE_ARCH_ENTRY_INIT)

#undef DECLARE_ARCH_ENTRY_INIT
#undef DECLARE_ARCH_ENTRY


  // define getters for arch-specific entries

#define DEFINE_ARCH_ENTRY_GETTER(arch, blob_name, stub_name, field_name, getter_name) \
  static address getter_name() { return STUB_FIELD_NAME(field_name); }

#define DEFINE_ARCH_ENTRY_GETTER_INIT(arch, blob_name, stub_name, field_name, getter_name, init_function) \
  DEFINE_ARCH_ENTRY_GETTER(arch, blob_name, stub_name, field_name, getter_name)

public:
  STUBGEN_ARCH_ENTRIES_DO(DEFINE_ARCH_ENTRY_GETTER, DEFINE_ARCH_ENTRY_GETTER_INIT)

#undef DEFINE_ARCH_ENTRY_GETTER_INIT
#undef DEFINE_ARCH_GETTER_ENTRY

 private:
  static jint    _mxcsr_std;
  static jint    _mxcsr_rz;
  // masks and table for CRC32
  static const uint64_t _crc_by128_masks[];
  static const juint    _crc_table[];
  static const juint    _crc_by128_masks_avx512[];
  static const juint    _crc_table_avx512[];
  static const juint    _crc32c_table_avx512[];
  static const juint    _shuf_table_crc32_avx512[];
  // table for CRC32C
  static juint* _crc32c_table;
  // table for arrays_hashcode
  static const jint _arrays_hashcode_powers_of_31[];
  //k256 table for sha256
  static const juint _k256[];
  static address _k256_adr;
  static juint _k256_W[];
  static address _k256_W_adr;
  static const julong _k512_W[];
  static address _k512_W_addr;

 public:
  static address addr_mxcsr_std()        { return (address)&_mxcsr_std; }
  static address addr_mxcsr_rz()        { return (address)&_mxcsr_rz; }
  static address crc_by128_masks_addr()  { return (address)_crc_by128_masks; }
  static address crc_by128_masks_avx512_addr()  { return (address)_crc_by128_masks_avx512; }
  static address shuf_table_crc32_avx512_addr()  { return (address)_shuf_table_crc32_avx512; }
  static address crc_table_avx512_addr()  { return (address)_crc_table_avx512; }
  static address crc32c_table_avx512_addr()  { return (address)_crc32c_table_avx512; }
  static address k256_addr()      { return _k256_adr; }
  static address k256_W_addr()    { return _k256_W_adr; }
  static address k512_W_addr()    { return _k512_W_addr; }

  static address arrays_hashcode_powers_of_31() { return (address)_arrays_hashcode_powers_of_31; }
  static void generate_CRC32C_table(bool is_pclmulqdq_supported);
};

#endif // CPU_X86_STUBROUTINES_X86_HPP
