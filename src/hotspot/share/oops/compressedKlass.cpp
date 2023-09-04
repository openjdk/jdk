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
#include "oops/compressedKlass.hpp"
#include "runtime/globals.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

address CompressedKlassPointers::_base = nullptr;
int CompressedKlassPointers::_shift = 0;
size_t CompressedKlassPointers::_range = 0;

#ifdef _LP64

// Given a klass range [addr, addr+len) and a given encoding scheme, assert that this scheme covers the range, then
// set this encoding scheme. Used by CDS at runtime to re-instate the scheme used to pre-compute klass ids for
// archived heap objects.
void CompressedKlassPointers::initialize_for_given_encoding(address addr, size_t len, address requested_base, int requested_shift) {
  assert(is_valid_base(requested_base), "Address must be a valid encoding base");
  address const end = addr + len;

  const int narrow_klasspointer_bits = sizeof(narrowKlass) * 8;
  const size_t encoding_range_size = nth_bit(narrow_klasspointer_bits + requested_shift);
  address encoding_range_end = requested_base + encoding_range_size;

  // Note: it would be technically valid for the encoding base to precede the start of the Klass range. But we only call
  // this function from CDS, and therefore know this to be true.
  assert(requested_base == addr, "Invalid requested base");
  assert(encoding_range_end >= end, "Encoding does not cover the full Klass range");

  set_base(requested_base);
  set_shift(requested_shift);
  set_range(encoding_range_size);
}

// Given an address range [addr, addr+len) which the encoding is supposed to
//  cover, choose base, shift and range.
//  The address range is the expected range of uncompressed Klass pointers we
//  will encounter (and the implicit promise that there will be no Klass
//  structures outside this range).
void CompressedKlassPointers::initialize(address addr, size_t len) {
  address const end = addr + len;

  address base;
  int shift;
  size_t range;

  // Attempt to run with encoding base == zero
  if (end <= (address)KlassEncodingMetaspaceMax) {
    base = 0;
  } else {
    base = addr;
  }

  // Highest offset a Klass* can ever have in relation to base.
  range = end - base;

  // We may not even need a shift if the range fits into 32bit:
  const uint64_t UnscaledClassSpaceMax = (uint64_t(max_juint) + 1);
  if (range < UnscaledClassSpaceMax) {
    shift = 0;
  } else {
    shift = LogKlassAlignmentInBytes;
  }

  set_base(base);
  set_shift(shift);
  set_range(range);

  assert(is_valid_base(_base), "Address must be a valid encoding base");
}

// Given an address p, return true if p can be used as an encoding base.
//  (Some platforms have restrictions of what constitutes a valid base address).
bool CompressedKlassPointers::is_valid_base(address p) {
#ifdef AARCH64
  // Below 32G, base must be aligned to 4G.
  // Above that point, base must be aligned to 32G
  if (p < (address)(32 * G)) {
    return is_aligned(p, 4 * G);
  }
  return is_aligned(p, (4 << LogKlassAlignmentInBytes) * G);
#else
  return true;
#endif
}

void CompressedKlassPointers::print_mode(outputStream* st) {
  st->print_cr("Narrow klass base: " PTR_FORMAT ", Narrow klass shift: %d, "
               "Narrow klass range: " SIZE_FORMAT_X, p2i(base()), shift(),
               range());
}

void CompressedKlassPointers::set_base(address base) {
  assert(UseCompressedClassPointers, "no compressed klass ptrs?");
  _base   = base;
}

void CompressedKlassPointers::set_shift(int shift)       {
  assert(shift == 0 || shift == LogKlassAlignmentInBytes, "invalid shift for klass ptrs");
  _shift   = shift;
}

void CompressedKlassPointers::set_range(size_t range) {
  assert(UseCompressedClassPointers, "no compressed klass ptrs?");
  _range = range;
}

#endif // _LP64
