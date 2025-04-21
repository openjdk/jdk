/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_MATCHER_X86_HPP
#define CPU_X86_MATCHER_X86_HPP

  // Defined within class Matcher

  // The ecx parameter to rep stosq for the ClearArray node is in words.
  static const bool init_array_count_is_in_bytes = false;

  // Whether this platform implements the scalable vector feature
  static const bool implements_scalable_vector = false;

  static constexpr bool supports_scalable_vector() {
    return false;
  }

  // x86 supports misaligned vectors store/load.
  static constexpr bool misaligned_vectors_ok() {
    return true;
  }

  // Whether code generation need accurate ConvI2L types.
  static const bool convi2l_type_required = true;

  // Do the processor's shift instructions only use the low 5/6 bits
  // of the count for 32/64 bit ints? If not we need to do the masking
  // ourselves.
  static const bool need_masked_shift_count = false;

  // Does the CPU require late expand (see block.cpp for description of late expand)?
  static const bool require_postalloc_expand = false;

  // x86 supports generic vector operands: vec and legVec.
  static const bool supports_generic_vector_operands = true;

  static constexpr bool isSimpleConstant64(jlong value) {
    // Will one (StoreL ConL) be cheaper than two (StoreI ConI)?.
    //return value == (int) value;  // Cf. storeImmL and immL32.
    // Probably always true, even if a temp register is required.
    return true;
  }

  // No additional cost for CMOVL.
  static constexpr int long_cmove_cost() { return 0; }

  // No CMOVF/CMOVD with SSE2
  static int float_cmove_cost() { return ConditionalMoveLimit; }

  static bool narrow_oop_use_complex_address() {
    assert(UseCompressedOops, "only for compressed oops code");
    return (LogMinObjAlignmentInBytes <= 3);
  }

  static bool narrow_klass_use_complex_address() {
    assert(UseCompressedClassPointers, "only for compressed klass code");
    return (CompressedKlassPointers::shift() <= 3);
  }

  // Prefer ConN+DecodeN over ConP.
  static bool const_oop_prefer_decode() {
    // Prefer ConN+DecodeN over ConP.
    return true;
  }

  // Prefer ConP over ConNKlass+DecodeNKlass.
  static bool const_klass_prefer_decode() {
    return false;
  }

  // Is it better to copy float constants, or load them directly from memory?
  // Intel can load a float constant from a direct address, requiring no
  // extra registers.  Most RISCs will have to materialize an address into a
  // register first, so they would do better to copy the constant from stack.
  static const bool rematerialize_float_constants = true;

  // If CPU can load and store mis-aligned doubles directly then no fixup is
  // needed.  Else we split the double into 2 integer pieces and move it
  // piece-by-piece.  Only happens when passing doubles into C code as the
  // Java calling convention forces doubles to be aligned.
  static const bool misaligned_doubles_ok = true;

  // Are floats converted to double when stored to stack during deoptimization?
  // On x64 it is stored without conversion so we can use normal access.
  static constexpr bool float_in_double() {
    return false;
  }

  // Do ints take an entire long register or just half?
  static const bool int_in_long = true;

  // Does the CPU supports vector variable shift instructions?
  static bool supports_vector_variable_shifts(void) {
    return (UseAVX >= 2);
  }

  // Does target support predicated operation emulation.
  static bool supports_vector_predicate_op_emulation(int vopc, int vlen, BasicType bt) {
    switch(vopc) {
      case Op_LoadVectorGatherMasked:
        return is_subword_type(bt) && VM_Version::supports_avx2();
      default:
        return false;
    }
  }

  // Does the CPU supports vector variable rotate instructions?
  static constexpr bool supports_vector_variable_rotates(void) {
    return true;
  }

  // Does the CPU supports vector constant rotate instructions?
  static constexpr bool supports_vector_constant_rotates(int shift) {
    return -0x80 <= shift && shift < 0x80;
  }

  // Does the CPU supports vector unsigned comparison instructions?
  static constexpr bool supports_vector_comparison_unsigned(int vlen, BasicType bt) {
    return true;
  }

  // Some microarchitectures have mask registers used on vectors
  static bool has_predicated_vectors(void) {
    return VM_Version::supports_evex();
  }

  // true means we have fast l2f conversion
  // false means that conversion is done by runtime call
  static constexpr bool convL2FSupported(void) {
      return true;
  }

  // Implements a variant of EncodeISOArrayNode that encode ASCII only
  static const bool supports_encode_ascii_array = true;

  // Without predicated input, an all-one vector is needed for the alltrue vector test
  static constexpr bool vectortest_needs_second_argument(bool is_alltrue, bool is_predicate) {
    return is_alltrue && !is_predicate;
  }

  // BoolTest mask for vector test intrinsics
  static constexpr BoolTest::mask vectortest_mask(bool is_alltrue, bool is_predicate, int vlen) {
    if (!is_alltrue) {
      return BoolTest::ne;
    }
    if (!is_predicate) {
      return BoolTest::lt;
    }
    if ((vlen == 8 && !VM_Version::supports_avx512dq()) || vlen < 8) {
      return BoolTest::eq;
    }
    return BoolTest::lt;
  }

  // Returns pre-selection estimated size of a vector operation.
  // Currently, it's a rudimentary heuristic based on emitted code size for complex
  // IR nodes used by unroll policy. Idea is to constrain unrolling factor and prevent
  // generating bloated loop bodies.
  static int vector_op_pre_select_sz_estimate(int vopc, BasicType ety, int vlen) {
    switch(vopc) {
      default:
        return 0;
      case Op_MulVB:
        return 7;
      case Op_MulVL:
        return VM_Version::supports_avx512vldq() ? 0 : 6;
      case Op_LoadVectorGather:
      case Op_LoadVectorGatherMasked:
        return is_subword_type(ety) ? 50 : 0;
      case Op_VectorCastF2X: // fall through
      case Op_VectorCastD2X:
        return is_floating_point_type(ety) ? 0 : (is_subword_type(ety) ? 35 : 30);
      case Op_CountTrailingZerosV:
      case Op_CountLeadingZerosV:
        return VM_Version::supports_avx512cd() && (ety == T_INT || ety == T_LONG) ? 0 : 40;
      case Op_PopCountVI:
        if (is_subword_type(ety)) {
          return VM_Version::supports_avx512_bitalg() ? 0 : 50;
        } else {
          assert(ety == T_INT, "sanity"); // for documentation purposes
          return VM_Version::supports_avx512_vpopcntdq() ? 0 : 50;
        }
      case Op_PopCountVL:
        return VM_Version::supports_avx512_vpopcntdq() ? 0 : 40;
      case Op_ReverseV:
        return VM_Version::supports_gfni() ? 0 : 30;
      case Op_RoundVF: // fall through
      case Op_RoundVD:
        return 30;
    }
  }

  // Returns pre-selection estimated size of a scalar operation.
  static int scalar_op_pre_select_sz_estimate(int vopc, BasicType ety) {
    switch(vopc) {
      default: return 0;
      case Op_RoundF: // fall through
      case Op_RoundD: {
        return 30;
      }
    }
  }

  // Is SIMD sort supported for this CPU?
  static bool supports_simd_sort(BasicType bt) {
    if (VM_Version::supports_avx512_simd_sort()) {
      return true;
    }
    else if (VM_Version::supports_avx2() && !is_double_word_type(bt)) {
      return true;
    }
    else {
      return false;
    }
  }

#endif // CPU_X86_MATCHER_X86_HPP
