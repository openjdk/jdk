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

// For UseCompressedClassPointers.
class CompressedKlassPointers : public AllStatic {
  friend class VMStructs;
  friend class ArchiveBuilder;

  // Tiny-class-pointer mode
  static int _tiny_cp; // -1, 0=true, 1=false

  // We use a different narrow Klass pointer geometry depending on
  // whether we run in standard mode or in compact-object-header-mode (Lilliput):
  // In Lilliput, we use smaller-than-32-bit class pointers ("tiny classpointer mode")

  // Narrow klass pointer bits for an unshifted narrow Klass pointer.
  static constexpr int narrow_klass_pointer_bits_legacy = 32;
  static constexpr int narrow_klass_pointer_bits_tinycp = 22;

  static int _narrow_klass_pointer_bits;

  // The maximum shift we can use for standard mode and for TinyCP mode
  static constexpr int max_shift_legacy = 3;
  static constexpr int max_shift_tinycp = 10;

  static int _max_shift;

  static address _base;
  static int _shift;

  // Together with base, this defines the address range within which Klass
  //  structures will be located: [base, base+range). While the maximal
  //  possible encoding range is 4|32G for shift 0|3, if we know beforehand
  //  the expected range of Klass* pointers will be smaller, a platform
  //  could use this info to optimize encoding.
  static size_t _range;

  // Helper function for common cases.
  static char* reserve_address_space_X(uintptr_t from, uintptr_t to, size_t size, size_t alignment, bool aslr);
  static char* reserve_address_space_for_unscaled_encoding(size_t size, bool aslr);
  static char* reserve_address_space_for_zerobased_encoding(size_t size, bool aslr);
  static char* reserve_address_space_for_16bit_move(size_t size, bool aslr);

  // Returns the highest address expressable with an unshifted narrow Klass pointer
  inline static uintptr_t highest_unscaled_address();

  static bool pd_initialize(address addr, size_t len);

#ifdef ASSERT
  // For sanity checks: Klass range
  static address _klass_range_start;
  static address _klass_range_end;
  // For sanity checks: lowest, highest valid narrow klass ids != null
  static narrowKlass _lowest_valid_narrow_klass_id;
  static narrowKlass _highest_valid_narrow_klass_id;
  static void calc_lowest_highest_narrow_klass_id();
  static void sanity_check_after_initialization();
#endif // ASSERT

  template <typename T>
  static inline void check_init(T var) {
    assert(var != (T)-1, "Not yet initialized");
  }

  static inline Klass* decode_not_null_without_asserts(narrowKlass v, address base, int shift);
  static inline Klass* decode_not_null(narrowKlass v, address base, int shift);

  static inline narrowKlass encode_not_null(Klass* v, address base, int shift);

public:

  // Initialization sequence:
  // 1) Parse arguments. The following arguments take a role:
  //      - UseCompressedClassPointers
  //      - UseCompactObjectHeaders
  //      - Xshare on off dump
  //      - CompressedClassSpaceSize
  // 2) call pre_initialize(): depending on UseCompactObjectHeaders, defines the limits of narrow Klass pointer
  //    geometry (how many bits, the max. possible shift)
  // 3) .. from here on, narrow_klass_pointer_bits() and max_shift() can be used
  // 4) call reserve_address_space_for_compressed_classes() either from CDS initialization or, if CDS is off,
  //    from metaspace initialization. Reserves space for class space + CDS, attempts to reserve such that
  //    we later can use a "good" encoding scheme. Reservation is highly CPU-specific.
  // 5) Initialize the narrow Klass encoding scheme by determining encoding base and shift:
  //   5a) if CDS=on: Calls initialize_for_given_encoding() with the reservation base from step (4) and the
  //       CDS-intrinsic setting for shift; here, we don't have any freedom to deviate from the base.
  //   5b) if CDS=off: Calls initialize() - here, we have more freedom and, if we want, can choose an encoding
  //       base that differs from the reservation base from step (4). That allows us, e.g., to later use
  //       zero-based encoding.
  // 6) ... from now on, we can use base() and shift().

  // Called right after argument parsing; defines narrow klass pointer geometry limits
  static void pre_initialize();

  static bool tiny_classpointer_mode()   { check_init(_tiny_cp); return (_tiny_cp == 1); }

  // The number of bits a narrow Klass pointer has;
  static int narrow_klass_pointer_bits() { check_init(_narrow_klass_pointer_bits); return _narrow_klass_pointer_bits; }

  // The maximum possible shift; the actual shift employed later can be smaller (see initialize())
  static int max_shift()                 { check_init(_max_shift); return _max_shift; }

  // Returns the maximum encoding range that can be covered with the currently
  // choosen nKlassID geometry (nKlass bit size, max shift)
  static size_t max_encoding_range_size();

  // Reserve a range of memory that is to contain Klass strucutures which are referenced by narrow Klass IDs.
  // If optimize_for_zero_base is true, the implementation will attempt to reserve optimized for zero-based encoding.
  static char* reserve_address_space_for_compressed_classes(size_t size, bool aslr, bool optimize_for_zero_base);

  // Given a klass range [addr, addr+len) and a given encoding scheme, assert that this scheme covers the range, then
  // set this encoding scheme. Used by CDS at runtime to re-instate the scheme used to pre-compute klass ids for
  // archived heap objects. In this case, we don't have the freedom to choose base and shift; they are handed to
  // us from CDS.
  static void initialize_for_given_encoding(address addr, size_t len, address requested_base, int requested_shift);

  // Given an address range [addr, addr+len) which the encoding is supposed to
  //  cover, choose base, shift and range.
  //  The address range is the expected range of uncompressed Klass pointers we
  //  will encounter (and the implicit promise that there will be no Klass
  //  structures outside this range).
  static void initialize(address addr, size_t len);

  static void     print_mode(outputStream* st);

  // Can only be used after initialization
  static address  base()             { check_init(_base); return  _base; }
  static size_t   range()            { check_init(_range); return  _range; }
  static int      shift()            { check_init(_shift); return  _shift; }

  // Returns the alignment a Klass* is guaranteed to have.
  // Note: *Not* the same as 1 << shift ! Klass are always guaranteed to be at least 64-bit aligned,
  // so this will return 8 even if shift is 0.
  static int klass_alignment_in_bytes() { return nth_bit(MAX2(3, _shift)); }
  static int klass_alignment_in_words() { return klass_alignment_in_bytes() / BytesPerWord; }

  static bool is_null(Klass* v)      { return v == nullptr; }
  static bool is_null(narrowKlass v) { return v == 0; }

  // Versions without asserts
  static inline Klass* decode_not_null_without_asserts(narrowKlass v);
  static inline Klass* decode_without_asserts(narrowKlass v);

  static inline Klass* decode_not_null(narrowKlass v);
  static inline Klass* decode(narrowKlass v);

  static inline narrowKlass encode_not_null_without_asserts(Klass* k, address narrow_base, int shift);
  static inline narrowKlass encode_not_null(Klass* v);
  static inline narrowKlass encode(Klass* v);

#ifdef ASSERT
  // Given a Klass* k and an encoding (base, shift), check that k can be encoded
  inline static void check_valid_klass(const Klass* k, address base, int shift);
  // Given a Klass* k, check that k can be encoded with the current encoding
  inline static void check_valid_klass(const Klass* k);
  // Given a narrow Klass ID, check that it is valid according to current encoding
  inline static void check_valid_narrow_klass_id(narrowKlass nk);
#endif

};

#endif // SHARE_OOPS_COMPRESSEDKLASS_HPP
