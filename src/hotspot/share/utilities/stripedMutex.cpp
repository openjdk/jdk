/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"
#include "memory/padded.hpp"
#include "runtime/os.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/stripedMutex.inline.hpp"


StripedMutex::StripedMutex()
  : _num_stripes(round_down_power_of_2((uint32_t) MAX2(os::processor_count(), 1))),
    _stripe_mask(_num_stripes - 1),
    _stripes_base(AllocateHeap((_num_stripes * sizeof(PaddedMutex)) + DEFAULT_PADDING_SIZE, mtSynchronizer)),
    _stripes(reinterpret_cast<PaddedMutex*> (_stripes_base)) {
  // To create an array of cache aligned PaddedMutexes, we allocate one more cache line than needed and align the base address.
  if (!is_aligned(_stripes, DEFAULT_PADDING_SIZE)) {
    _stripes = align_up(_stripes, DEFAULT_PADDING_SIZE);
  }
  assert(is_aligned(_stripes, DEFAULT_PADDING_SIZE), "unaligned");

  // Use placement new to construct our Mutexes.
  for (size_t i = 0; i < _num_stripes; i++) {
    // These locks could be acquired while we are holding DumpTimeTable_lock/nosafepoint
    ::new(&_stripes[i]) PaddedMutex(Mutex::nosafepoint-1, "MDOExtraData_lock");
  }
}

StripedMutex::~StripedMutex() {
  for (size_t i = 0; i < _num_stripes; i++) {
    _stripes[i].~PaddedMutex();
  }
  FreeHeap(_stripes_base);
}
