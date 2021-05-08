/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_AARCH64_MATCHER_AARCH64_HPP
#define CPU_AARCH64_MATCHER_AARCH64_HPP

  // Defined within class Matcher

  // false => size gets scaled to BytesPerLong, ok.
  static const bool init_array_count_is_in_bytes = false;

  // Whether this platform implements the scalable vector feature
  static const bool implements_scalable_vector = true;

  // Whether code generation need accurate ConvI2L types.
  static const bool convi2l_type_required = false;

  // Does the CPU require late expand (see block.cpp for description of late expand)?
  static const bool require_postalloc_expand = false;

  // Do we need to mask the count passed to shift instructions or does
  // the cpu only look at the lower 5/6 bits anyway?
  static const bool need_masked_shift_count = false;

  // No support for generic vector operands.
  static const bool supports_generic_vector_operands = false;

  // Is it better to copy float constants, or load them directly from
  // memory?  Intel can load a float constant from a direct address,
  // requiring no extra registers.  Most RISCs will have to materialize
  // an address into a register first, so they would do better to copy
  // the constant from stack.
  static const bool rematerialize_float_constants = false;

  // If CPU can load and store mis-aligned doubles directly then no
  // fixup is needed.  Else we split the double into 2 integer pieces
  // and move it piece-by-piece.  Only happens when passing doubles into
  // C code as the Java calling convention forces doubles to be aligned.
  static const bool misaligned_doubles_ok = true;

  // Advertise here if the CPU requires explicit rounding operations to implement strictfp mode.
  static const bool strict_fp_requires_explicit_rounding = false;

  // Do ints take an entire long register or just half?
  // The relevant question is how the int is callee-saved:
  // the whole long is written but de-opt'ing will have to extract
  // the relevant 32 bits.
  static const bool int_in_long = true;

#endif // CPU_AARCH64_MATCHER_AARCH64_HPP
