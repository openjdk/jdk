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

#include "memory/metaspace.hpp"
#include "oops/compressedKlass.hpp"
#include "utilities/globalDefinitions.hpp"

char* CompressedKlassPointers::reserve_address_space_for_compressed_classes(size_t size, bool aslr, bool optimize_for_zero_base) {

  char* result = nullptr;

  assert(CompressedKlassPointers::narrow_klass_pointer_bits() == 32 ||
         CompressedKlassPointers::narrow_klass_pointer_bits() == 22, "Rethink if we ever use different nKlass bit sizes");

  // Unconditionally attempting to reserve in lower 4G first makes always sense:
  // -CDS -COH: Try to get unscaled mode (zero base, zero shift)
  // +CDS -COH: No zero base possible (CDS prevents it); but we still benefit from small base pointers (imm32 movabs)
  // -CDS +COH: No zero base possible (22bit nKlass + zero base zero shift = 4MB encoding range, way too small);
  //            but we still benefit from small base pointers (imm32 movabs)
  // +CDS +COH: No zero base possible for multiple reasons (CDS prevents it and encoding range too small);
  //            but we still benefit from small base pointers (imm32 movabs)

  result = reserve_address_space_below_4G(size, aslr);

  if (result == nullptr && optimize_for_zero_base) {
    // Failing that, if we are running without CDS, attempt to allocate below 32G.
    // This allows us to use zero-based encoding with a non-zero shift.
    result = reserve_address_space_for_zerobased_encoding(size, aslr);
  }

  return result;
}

#endif // _LP64
