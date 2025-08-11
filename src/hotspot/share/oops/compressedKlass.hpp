/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"

class outputStream;
class Klass;

// Narrow Klass Encoding
//
// Klass Range:
//  a contiguous memory range into which we place Klass that should be encodable. Not every Klass
//  needs to be encodable. There is only one such memory range.
//  If CDS is disabled, this Klass Range is the same as the metaspace class space. If CDS is enabled, the
//  Klass Range contains both CDS and class space adjacent to each other (with a potential small
//  unused alignment gap between them).
//
// Encoding Range:
//  This is the range covered by the current encoding scheme. The encoding scheme is defined by
//  the encoding base, encoding shift and (implicitly) the bit size of the narrowKlass. The
//  Encoding Range is:
//   [ <encoding base> ... <encoding base> + (1 << (<narrowKlass-bitsize> + <shift>) )
//
// Note that while the Klass Range must be contained within the Encoding Range, the Encoding Range
// is typically a lot larger than the Klass Range:
//  - the encoding base can start before the Klass Range start (specifically, it can start at 0 for
//    zero-based encoding)
//  - the end of the Encoding Range usually extends far beyond the end of the Klass Range.
//
//
// Examples:
//
// "unscaled" (zero-based zero-shift) encoding, CDS off, class space of 1G starts at 0x4B00_0000:
// - Encoding Range: [0             .. 0x1_0000_0000 ) (4 GB)
// - Klass Range:    [0x4B00_0000   .. 0x  8B00_0000 ) (1 GB)
//
//
// _base        _klass_range_start              _klass_range_end             encoding end
//   |                |//////////////////////////////|                             |
//   |   ...          |///////1gb class space////////|               ...           |
//   |                |//////////////////////////////|                             |
//  0x0         0x4B00_0000                   0x8B00_0000                    0x1_0000_0000
//
//
//
// "zero-based" (but scaled) encoding, shift=3, CDS off, 1G Class space at 0x7_C000_0000 (31GB):
// - Encoding Range: [0             .. 0x8_0000_0000 ) (32 GB)
// - Klass Range:    [0x7_C000_0000 .. 0x8_0000_0000 ) (1 GB)
//
//                                                                  encoding end
// _base                            _klass_range_start              _klass_range_end
//   |                                   |//////////////////////////////|
//   |   ...                             |///////1gb class space////////|
//   |                                   |//////////////////////////////|
//  0x0                            0x7_C000_0000                  0x8_0000_0000
//
//
// CDS enabled, 128MB CDS region starts 0x8_0000_0000, followed by a 1GB class space. Encoding
// base will point to CDS region start, shift=0:
// - Encoding Range: [0x8_0000_0000 .. 0x9_0000_0000 ) (4 GB)
// - Klass Range:    [0x8_0000_0000 .. 0x8_4800_0000 ) (128 MB + 1 GB)
//
//  _base
// _klass_range_start                   _klass_range_end                        encoding end
//   |//////////|///////////////////////////|                                         |
//   |///CDS////|////1gb class space////////|            ...    ...                   |
//   |//////////|///////////////////////////|                                         |
//   |                                      |                                         |
// 0x8_0000_0000                      0x8_4800_0000                            0x9_0000_0000
//

// If compressed klass pointers then use narrowKlass.
typedef juint  narrowKlass;

// For UseCompressedClassPointers.
class CompressedKlassPointers : public AllStatic {
  friend class VMStructs;
  friend class ArchiveBuilder;

  // We use a different narrow Klass pointer geometry depending on
  // whether we run in standard mode or in compact-object-header-mode.

  // Narrow klass pointer bits for an unshifted narrow Klass pointer.
  static constexpr int narrow_klass_pointer_bits_noncoh = 32;
  static constexpr int narrow_klass_pointer_bits_coh = 22;

  // Bit size of a narrowKlass
  static int _narrow_klass_pointer_bits;

  // The maximum shift values we can use depending on UseCompactObjectHeaders
  static constexpr int max_shift_noncoh = 3;
  static constexpr int max_shift_coh = 10;

  // Maximum shift usable
  static int _max_shift;

  // Encoding Base, Encoding Shift
  static address _base;
  static int _shift;

  // Start and end of the Klass Range. Start includes the protection zone if one exists.
  // Note: guaranteed to be aligned to 1<<shift (klass_alignment_in_bytes)
  static address _klass_range_start;
  static address _klass_range_end;

  // Values for the lowest (inclusive) and highest (inclusive) narrow Klass ID, given the
  // current Klass Range and encoding settings.
  static narrowKlass _lowest_valid_narrow_klass_id;
  static narrowKlass _highest_valid_narrow_klass_id;

  // Protection zone size (0 if not set up)
  static size_t _protection_zone_size;

  // Helper function for common cases.
  static char* reserve_address_space_X(uintptr_t from, uintptr_t to, size_t size, size_t alignment, bool aslr);
  static char* reserve_address_space_below_4G(size_t size, bool aslr);
  static char* reserve_address_space_for_unscaled_encoding(size_t size, bool aslr);
  static char* reserve_address_space_for_zerobased_encoding(size_t size, bool aslr);
  static char* reserve_address_space_for_16bit_move(size_t size, bool aslr);
  static void calc_lowest_highest_narrow_klass_id();

#ifdef ASSERT
  static void sanity_check_after_initialization();
#endif // ASSERT

  template <typename T>
  static inline void check_init(T var) {
    assert(var != (T)-1, "Not yet initialized");
  }

  static inline Klass* decode_not_null_without_asserts(narrowKlass v, address base, int shift);

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

  // The number of bits a narrow Klass pointer has;
  static int narrow_klass_pointer_bits() { check_init(_narrow_klass_pointer_bits); return _narrow_klass_pointer_bits; }

  // The maximum possible shift; the actual shift employed later can be smaller (see initialize())
  static int max_shift()                 { check_init(_max_shift); return _max_shift; }

  // Returns the maximum encoding range, given the current geometry (narrow klass bit size and shift)
  static size_t max_encoding_range_size() { return nth_bit(narrow_klass_pointer_bits() + max_shift()); }

  // Returns the maximum allowed klass range size.
  static size_t max_klass_range_size();

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
  static address  base_addr()        { return (address)&_base; }
  static int      shift()            { check_init(_shift); return  _shift; }

  static address  klass_range_start()  { return  _klass_range_start; }
  static address  klass_range_end()    { return  _klass_range_end; }

  static inline address encoding_range_end();

  // Returns the alignment a Klass* is guaranteed to have.
  // Note: *Not* the same as 1 << shift ! Klass are always guaranteed to be at least 64-bit aligned,
  // so this will return 8 even if shift is 0.
  static int klass_alignment_in_bytes() { return nth_bit(MAX2(3, _shift)); }
  static int klass_alignment_in_words() { return klass_alignment_in_bytes() / BytesPerWord; }

  // Returns the highest possible narrowKlass value given the current Klass range
  static narrowKlass highest_valid_narrow_klass_id() { return _highest_valid_narrow_klass_id; }

  static bool is_null(const Klass* v)  { return v == nullptr; }
  static bool is_null(narrowKlass v)   { return v == 0; }

  // Versions without asserts
  static inline Klass* decode_not_null_without_asserts(narrowKlass v);
  static inline Klass* decode_without_asserts(narrowKlass v);
  static inline Klass* decode_not_null(narrowKlass v);
  static inline Klass* decode(narrowKlass v);

  static inline narrowKlass encode_not_null_without_asserts(const Klass* k, address narrow_base, int shift);
  static inline narrowKlass encode_not_null(const Klass* v);
  static inline narrowKlass encode(const Klass* v);

#ifdef ASSERT
  // Given an address, check that it can be encoded with the current encoding
  inline static void check_encodable(const void* addr);
  // Given a narrow Klass ID, check that it is valid according to current encoding
  inline static void check_valid_narrow_klass_id(narrowKlass nk);
#endif

  // Given a narrow Klass ID, returns true if it appears to be valid
  inline static bool is_valid_narrow_klass_id(narrowKlass nk);

  // Returns whether the pointer is in the memory region used for encoding compressed
  // class pointers.  This includes CDS.
  static inline bool is_encodable(const void* addr) {
    // An address can only be encoded if:
    //
    // 1) the address lies within the klass range.
    // 2) It is suitably aligned to 2^encoding_shift. This only really matters for
    //    +UseCompactObjectHeaders, since the encoding shift can be large (max 10 bits -> 1KB).
    return (address)addr >= _klass_range_start && (address)addr < _klass_range_end &&
        is_aligned(addr, klass_alignment_in_bytes());
  }

  // Protect a zone a the start of the encoding range
  static void establish_protection_zone(address addr, size_t size);

  // Returns true if address points into protection zone (for error reporting)
  static bool is_in_protection_zone(address addr);

#if defined(AARCH64) && !defined(ZERO)
  // Check that with the given base, shift and range, aarch64 code can encode and decode the klass pointer.
  static bool check_klass_decode_mode(address base, int shift, const size_t range);
  // Called after initialization.
  static bool set_klass_decode_mode();
#else
  static bool check_klass_decode_mode(address base, int shift, const size_t range) { return true; }
  static bool set_klass_decode_mode() { return true; }
#endif
};

#endif // SHARE_OOPS_COMPRESSEDKLASS_HPP
