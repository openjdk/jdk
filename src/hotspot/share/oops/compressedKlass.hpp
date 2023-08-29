/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_COMPRESSEDKLASS_HPP
#define SHARE_OOPS_COMPRESSEDKLASS_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

class outputStream;
class Klass;

// If compressed klass pointers then use narrowKlass.
typedef juint  narrowKlass;

const int LogKlassAlignmentInBytes = 3;
const int KlassAlignmentInBytes    = 1 << LogKlassAlignmentInBytes;

// Maximal size of compressed class space. Above this limit compression is not possible.
// Also upper bound for placement of zero based class space. (Class space is further limited
// to be < 3G, see arguments.cpp.)
const  uint64_t KlassEncodingMetaspaceMax = (uint64_t(max_juint) + 1) << LogKlassAlignmentInBytes;

// For UseCompressedClassPointers.
class CompressedKlassPointers : public AllStatic {
  friend class VMStructs;

  static address _base;
  static int _shift;

  // Together with base, this defines the address range within which Klass
  //  structures will be located: [base, base+range). While the maximal
  //  possible encoding range is 4|32G for shift 0|3, if we know beforehand
  //  the expected range of Klass* pointers will be smaller, a platform
  //  could use this info to optimize encoding.
  static size_t _range;

  // Reduce the number of loads generated to decode an nKlass by packing all
  // relevant information (flag, base, shift) into a single 64-bit word. The
  // encoding base is aligned to metaspace reserve alignment (16Mb), so enough
  // space to hide additional info away in low bytes. It even allows us the luxury
  // to use 8 bits for shift and flag each, resulting in 8-bit moves used by the
  // compiler.
  // - Bit  [0-7]   shift
  // - Bit  8       UseCompressedOops
  // - Bits [16-64] the base.
  static uint64_t _combo;
  static constexpr int base_alignment = 16;
  static constexpr uint64_t mask_base = ~right_n_bits(base_alignment);
  static constexpr int shift_bitlen = 8; // read with a mov8
  static constexpr int bitpos_useccp = shift_bitlen;

  static void set_base_and_shift(address base, int shift);
  static void set_range(size_t range);

public:

  // Given an address p, return true if p can be used as an encoding base.
  //  (Some platforms have restrictions of what constitutes a valid base
  //   address).
  static bool is_valid_base(address p);

  // Given a klass range [addr, addr+len) and a given encoding scheme, assert that this scheme covers the range, then
  // set this encoding scheme. Used by CDS at runtime to re-instate the scheme used to pre-compute klass ids for
  // archived heap objects.
  static void initialize_for_given_encoding(address addr, size_t len, address requested_base, int requested_shift);

  // Given an address range [addr, addr+len) which the encoding is supposed to
  //  cover, choose base, shift and range.
  //  The address range is the expected range of uncompressed Klass pointers we
  //  will encounter (and the implicit promise that there will be no Klass
  //  structures outside this range).
  static void initialize(address addr, size_t len);

  static void     print_mode(outputStream* st);

  static bool     use_compressed_class_pointers() { return (_combo & nth_bit(bitpos_useccp)); }
  static address  base()             { return  (address)(_combo & mask_base); }
  static int      shift()            { return  (int)(_combo & right_n_bits(shift_bitlen)); }
  static size_t   range()            { return  _range; }

  static bool is_null(Klass* v)      { return v == nullptr; }
  static bool is_null(narrowKlass v) { return v == 0; }

  static inline Klass* decode_raw(narrowKlass v, address base, int shift);
  static inline Klass* decode_raw(narrowKlass v);
  static inline Klass* decode_not_null(narrowKlass v);
  static inline Klass* decode_not_null(narrowKlass v, address base, int shift);
  static inline Klass* decode(narrowKlass v);
  static inline narrowKlass encode_not_null(Klass* v);
  static inline narrowKlass encode_not_null(Klass* v, address base, int shift);
  static inline narrowKlass encode(Klass* v);

};

#endif // SHARE_OOPS_COMPRESSEDKLASS_HPP
