/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

const int LogKlassAlignmentInBytes = 3;
const int KlassAlignmentInBytes    = 1 << LogKlassAlignmentInBytes;

// Maximal size of compressed class space. Above this limit compression is not possible.
// Also upper bound for placement of zero based class space. (Class space is further limited
// to be < 3G, see arguments.cpp.)
const  uint64_t KlassEncodingMetaspaceMax = (uint64_t(max_juint) + 1) << LogKlassAlignmentInBytes;

// For UseCompressedClassPointers.
class CompressedKlassPointers : public AllStatic {
  friend class VMStructs;
  friend class ArchiveBuilder;

  static address _base;
  static int _shift;

  // Start and end of the Klass Range.
  // Note: guaranteed to be aligned to KlassAlignmentInBytes
  static address _klass_range_start;
  static address _klass_range_end;

  // Helper function for common cases.
  static char* reserve_address_space_X(uintptr_t from, uintptr_t to, size_t size, size_t alignment, bool aslr);
  static char* reserve_address_space_for_unscaled_encoding(size_t size, bool aslr);
  static char* reserve_address_space_for_zerobased_encoding(size_t size, bool aslr);
  static char* reserve_address_space_for_16bit_move(size_t size, bool aslr);

  DEBUG_ONLY(static void assert_is_valid_encoding(address addr, size_t len, address base, int shift);)

  static inline Klass* decode_not_null_without_asserts(narrowKlass v, address base, int shift);
  static inline Klass* decode_not_null(narrowKlass v, address base, int shift);

  static inline narrowKlass encode_not_null(Klass* v, address base, int shift);

public:

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

  static address  base()               { return  _base; }
  static int      shift()              { return  _shift; }

  static address  klass_range_start()  { return  _klass_range_start; }
  static address  klass_range_end()    { return  _klass_range_end; }

  static inline address encoding_range_end();

  static bool is_null(Klass* v)      { return v == nullptr; }
  static bool is_null(narrowKlass v) { return v == 0; }

  // Versions without asserts
  static inline Klass* decode_not_null_without_asserts(narrowKlass v);
  static inline Klass* decode_without_asserts(narrowKlass v);

  static inline Klass* decode_not_null(narrowKlass v);
  static inline Klass* decode(narrowKlass v);

  static inline narrowKlass encode_not_null(Klass* v);
  static inline narrowKlass encode(Klass* v);

  // Returns whether the pointer is in the memory region used for encoding compressed
  // class pointers.  This includes CDS.
  static inline bool is_encodable(const void* p) {
    return (address) p >= _klass_range_start &&
           (address) p < _klass_range_end;
  }
};

#endif // SHARE_OOPS_COMPRESSEDKLASS_HPP
