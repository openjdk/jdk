/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/globalDefinitions.hpp"

char* CompressedKlassPointers::reserve_address_space_for_compressed_classes(size_t size, bool aslr, bool optimize_for_zero_base) {

  char* result = nullptr;

  uintptr_t tried_below = 0;

  // First, attempt to allocate < 4GB. We do this unconditionally:
  // - if optimize_for_zero_base, a <4GB mapping start allows us to use base=0 shift=0
  // - if !optimize_for_zero_base, a <4GB mapping start allows us to use algfi
  result = reserve_address_space_for_unscaled_encoding(size, aslr);

  // Failing that, try optimized for base=0 shift>0
  if (result == nullptr && optimize_for_zero_base) {
    result = reserve_address_space_for_zerobased_encoding(size, aslr);
  }

  // Failing that, aim for a base that is 4G-aligned; such a base can be set with aih.
  if (result == nullptr) {
    result = reserve_address_space_for_16bit_move(size, aslr);
  }

  return result;
}
