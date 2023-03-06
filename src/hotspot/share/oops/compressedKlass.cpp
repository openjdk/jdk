/*
 * Copyright (c) 2021 SAP SE. All rights reserved.
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 *
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
#include "utilities/ostream.hpp"
#include "utilities/debug.hpp"
#include "runtime/globals.hpp"

address CompressedKlassPointers::_base = nullptr;
int CompressedKlassPointers::_shift_copy = 0;

// Given an address range [addr, addr+len) which the encoding is supposed to
//  cover, choose base, shift and range.
//  The address range is the expected range of uncompressed Klass pointers we
//  will encounter (and the implicit promise that there will be no Klass
//  structures outside this range).
void CompressedKlassPointers::initialize(address addr, size_t len) {
#ifdef _LP64
  assert(UseCompressedClassPointers, "Sanity");

  assert(len <= (size_t)KlassEncodingMetaspaceMax, "Range " SIZE_FORMAT " too large "
         "- cannot be contained fully in narrow Klass pointer encoding range.", len);

  if (UseSharedSpaces || DumpSharedSpaces) {

    // Special requirements if CDS is active:
    // Encoding base and shift must be the same between dump and run time.
    //   CDS takes care that the SharedBaseAddress and CompressedClassSpaceSize
    //   are the same. Archive size will be probably different at runtime, but
    //   it can only be smaller than at, never larger, since archives get
    //   shrunk at the end of the dump process.
    //   From that it follows that the range [addr, len) we are handed in at
    //   runtime will start at the same address then at dumptime, and its len
    //   may be smaller at runtime then it was at dump time.
    //
    // To be very careful here, we avoid any optimizations and just keep using
    //  the same address and shift value. Specifically we avoid using zero-based
    //  encoding. We also set the expected value range to 4G (encoding range
    //  cannot be larger than that).

    _base = addr;

  } else {

    // (Note that this case is almost not worth optimizing for. CDS is typically on.)
    if ((addr + len) <= (address)KlassEncodingMetaspaceMax) {
      _base = 0;
    } else {
      _base = addr;
    }
  }

  assert(is_valid_base(_base), "Address " PTR_FORMAT " was chosen as encoding base for range ["
                               PTR_FORMAT ", " PTR_FORMAT ") but is not a valid encoding base",
                               p2i(_base), p2i(addr), p2i(addr + len));

  // For SA
  _shift_copy = LogKlassAlignmentInBytes;

#else
  ShouldNotReachHere(); // 64-bit only
#endif
}

// 64-bit platforms define these functions on a per-platform base. They are not needed for
//  32-bit (in fact, the whole setup is not needed and could be excluded from compilation,
//  but that is a question for another RFE).
#ifndef _LP64
// Given an address p, return true if p can be used as an encoding base.
//  (Some platforms have restrictions of what constitutes a valid base address).
bool CompressedKlassPointers::is_valid_base(address p) {
  ShouldNotReachHere(); // 64-bit only
  return false;
}

void CompressedKlassPointers::print_mode(outputStream* st) {
}
#endif

