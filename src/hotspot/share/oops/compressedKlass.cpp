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

#include "precompiled.hpp"
#include "logging/log.hpp"
#include "memory/metaspace.hpp"
#include "oops/compressedKlass.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

address CompressedKlassPointers::_base = nullptr;
int CompressedKlassPointers::_shift = 0;
size_t CompressedKlassPointers::_range = 0;

#ifdef _LP64

#ifdef ASSERT
void CompressedKlassPointers::assert_is_valid_encoding(address addr, size_t len, address base, int shift) {
  assert(base + nth_bit(32 + shift) >= addr + len, "Encoding (base=" PTR_FORMAT ", shift=%d) does not "
         "fully cover the class range " PTR_FORMAT "-" PTR_FORMAT, p2i(base), shift, p2i(addr), p2i(addr + len));
}
#endif

// Given a klass range [addr, addr+len) and a given encoding scheme, assert that this scheme covers the range, then
// set this encoding scheme. Used by CDS at runtime to re-instate the scheme used to pre-compute klass ids for
// archived heap objects.
void CompressedKlassPointers::initialize_for_given_encoding(address addr, size_t len, address requested_base, int requested_shift) {
  address const end = addr + len;

  const int narrow_klasspointer_bits = sizeof(narrowKlass) * 8;
  const size_t encoding_range_size = nth_bit(narrow_klasspointer_bits + requested_shift);
  address encoding_range_end = requested_base + encoding_range_size;

  // Note: it would be technically valid for the encoding base to precede the start of the Klass range. But we only call
  // this function from CDS, and therefore know this to be true.
  assert(requested_base == addr, "Invalid requested base");
  assert(encoding_range_end >= end, "Encoding does not cover the full Klass range");

  _base = requested_base;
  _shift = requested_shift;
  _range = encoding_range_size;

  DEBUG_ONLY(assert_is_valid_encoding(addr, len, _base, _shift);)
}

char* CompressedKlassPointers::reserve_address_space_X(uintptr_t from, uintptr_t to, size_t size, size_t alignment, bool aslr) {
  alignment = MAX2(Metaspace::reserve_alignment(), alignment);
  return os::attempt_reserve_memory_between((char*)from, (char*)to, size, alignment, aslr);
}

char* CompressedKlassPointers::reserve_address_space_for_unscaled_encoding(size_t size, bool aslr) {
  return reserve_address_space_X(0, nth_bit(32), size, Metaspace::reserve_alignment(), aslr);
}

char* CompressedKlassPointers::reserve_address_space_for_zerobased_encoding(size_t size, bool aslr) {
  return reserve_address_space_X(nth_bit(32), nth_bit(32 + LogKlassAlignmentInBytes), size, Metaspace::reserve_alignment(), aslr);
}

char* CompressedKlassPointers::reserve_address_space_for_16bit_move(size_t size, bool aslr) {
  return reserve_address_space_X(nth_bit(32), nth_bit(48), size, nth_bit(32), aslr);
}

#if !defined(AARCH64) || defined(ZERO)
// On aarch64 we have an own version; all other platforms use the default version
void CompressedKlassPointers::initialize(address addr, size_t len) {
  // The default version of this code tries, in order of preference:
  // -unscaled    (base=0 shift=0)
  // -zero-based  (base=0 shift>0)
  // -nonzero-base (base>0 shift=0)
  // Note that base>0 shift>0 should never be needed, since the klass range will
  // never exceed 4GB.
  constexpr uintptr_t unscaled_max = nth_bit(32);
  assert(len <= unscaled_max, "Klass range larger than 32 bits?");

  constexpr uintptr_t zerobased_max = nth_bit(32 + LogKlassAlignmentInBytes);

  address const end = addr + len;
  if (end <= (address)unscaled_max) {
    _base = nullptr;
    _shift = 0;
  } else {
    if (end <= (address)zerobased_max) {
      _base = nullptr;
      _shift = LogKlassAlignmentInBytes;
    } else {
      _base = addr;
      _shift = 0;
    }
  }
  _range = end - _base;

  DEBUG_ONLY(assert_is_valid_encoding(addr, len, _base, _shift);)
}
#endif // !AARCH64 || ZERO

void CompressedKlassPointers::print_mode(outputStream* st) {
  st->print_cr("Narrow klass base: " PTR_FORMAT ", Narrow klass shift: %d, "
               "Narrow klass range: " SIZE_FORMAT_X, p2i(base()), shift(),
               range());
}

#endif // _LP64
