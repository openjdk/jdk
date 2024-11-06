/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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

#ifndef CPU_AARCH64_STUBROUTINES_AARCH64_HPP
#define CPU_AARCH64_STUBROUTINES_AARCH64_HPP

// This file holds the platform specific parts of the StubRoutines
// definition. See stubRoutines.hpp for a description on how to
// extend it.

static bool    returns_to_call_stub(address return_pc)   {
  return return_pc == _call_stub_return_address;
}

// emit enum used to size per-blob code buffers

#define DEFINE_BLOB_SIZE(blob_name, size) \
  _ ## blob_name ## _code_size = size,

enum platform_dependent_constants {
  STUBGEN_ARCH_BLOBS_DO(DEFINE_BLOB_SIZE)
};

#undef DEFINE_BLOB_SIZE

class aarch64 {
 friend class StubGenerator;
#if INCLUDE_JVMCI
  friend class JVMCIVMStructs;
#endif

  // declare fields for arch-specific entries -- getters are not needed

#define DECLARE_ARCH_ENTRY(arch, blob_name, stub_name, field_name, getter_name) \
  static address STUB_FIELD_NAME(field_name) ;

#define DECLARE_ARCH_ENTRY_INIT(arch, blob_name, stub_name, field_name, getter_name, init_function) \
  DECLARE_ARCH_ENTRY(arch, blob_name, stub_name, field_name, getter_name)

private:
  STUBGEN_ARCH_ENTRIES_DO(DECLARE_ARCH_ENTRY, DECLARE_ARCH_ENTRY_INIT)

#undef DECLARE_ARCH_ENTRY_INIT
#undef DECLARE_ARCH_ENTRY

  static bool _completed;

 public:

  static address vector_iota_indices() {
    return _vector_iota_indices;
  }

  static address zero_blocks() {
    return _zero_blocks;
  }

  static address count_positives() {
    return _count_positives;
  }

  static address count_positives_long() {
      return _count_positives_long;
  }

  static address large_array_equals() {
      return _large_array_equals;
  }

  static address large_arrays_hashcode(BasicType eltype) {
    switch (eltype) {
    case T_BOOLEAN:
      return _large_arrays_hashcode_boolean;
    case T_BYTE:
      return _large_arrays_hashcode_byte;
    case T_CHAR:
      return _large_arrays_hashcode_char;
    case T_SHORT:
      return _large_arrays_hashcode_short;
    case T_INT:
      return _large_arrays_hashcode_int;
    default:
      ShouldNotReachHere();
    }

    return nullptr;
  }

  static address compare_long_string_LL() {
      return _compare_long_string_LL;
  }

  static address compare_long_string_LU() {
      return _compare_long_string_LU;
  }

  static address compare_long_string_UL() {
      return _compare_long_string_UL;
  }

  static address compare_long_string_UU() {
      return _compare_long_string_UU;
  }

  static address string_indexof_linear_ul() {
      return _string_indexof_linear_ul;
  }

  static address string_indexof_linear_ll() {
      return _string_indexof_linear_ll;
  }

  static address string_indexof_linear_uu() {
      return _string_indexof_linear_uu;
  }

  static address large_byte_array_inflate() {
      return _large_byte_array_inflate;
  }

  static address spin_wait() {
    return _spin_wait;
  }

  static bool complete() {
    return _completed;
  }

  static void set_completed() {
    _completed = true;
  }

private:
  static juint    _crc_table[];
  static jubyte   _adler_table[];
  // begin trigonometric tables block. See comments in .cpp file
  static juint    _npio2_hw[];
  static jdouble   _two_over_pi[];
  static jdouble   _pio2[];
  static jdouble   _dsin_coef[];
  static jdouble  _dcos_coef[];
  // end trigonometric tables block
};

#endif // CPU_AARCH64_STUBROUTINES_AARCH64_HPP
