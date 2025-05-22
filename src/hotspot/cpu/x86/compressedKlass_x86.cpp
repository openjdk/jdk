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

  // We always attempt to reserve < 4GB:
  // - without CDS, this means we can use zero-based encoding
  // - even with CDS (which disallows zero-based encoding), this allows us to use shorter imm32 movs when loading the base
  result = reserve_address_space_X(0, nth_bit(32), size, Metaspace::reserve_alignment(), aslr);

  if (result == 0 && optimize_for_zero_base) {
    // Failing that, if we are running without CDS, attempt to allocate below 32G. This allows us to use zero-based encoding
    // with a non-zero shift.
    result = reserve_address_space_for_zerobased_encoding(size, aslr);
  }

  return result;
}

#endif // _LP64
