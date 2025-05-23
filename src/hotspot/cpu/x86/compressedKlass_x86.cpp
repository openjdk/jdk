/*
 * Copyright (c) 2023, 2025, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifdef _LP64

#include "oops/compressedKlass.hpp"
#include "utilities/globalDefinitions.hpp"
#include "memory/metaspace.hpp"

char* CompressedKlassPointers::reserve_address_space_for_compressed_classes(size_t size, bool aslr, bool optimize_for_zero_base) {

  char* result = nullptr;

  bool did_look_in_lower_4G = false;
  if (optimize_for_zero_base && CompressedKlassPointers::narrow_klass_pointer_bits() == 32) {
    result = reserve_address_space_for_unscaled_encoding(size, aslr);
    did_look_in_lower_4G = true;
  }

  if (result == nullptr && !did_look_in_lower_4G) {
    // Even without aiming for zero-based encoding, allocating below 4G gives us
    // a low base address that can be encoded with imm32
    result = reserve_address_space_below_4G(size, aslr);
  }

  if (result == nullptr && optimize_for_zero_base) {
    // Failing that, if we are running without CDS, attempt to allocate below 32G.
    // This allows us to use zero-based encoding with a non-zero shift.
    result = reserve_address_space_for_zerobased_encoding(size, aslr);
  }

  return result;
}

#endif // _LP64
