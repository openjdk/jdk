/*
 * Copyright (c) 2017, Red Hat, Inc. and/or its affiliates.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/serial/serialArguments.hpp"
#include "gc/serial/serialHeap.hpp"
#include "gc/shared/fullGCForwarding.hpp"
#include "gc/shared/gcArguments.hpp"

static size_t compute_heap_alignment() {
  // The card marking array and the offset arrays for old generations are
  // committed in os pages as well. Make sure they are entirely full (to
  // avoid partial page problems), e.g. if 512 bytes heap corresponds to 1
  // byte entry and the os page size is 4096, the maximum heap size should
  // be 512*4096 = 2MB aligned.

  size_t alignment = CardTable::ct_max_alignment_constraint();

  if (UseLargePages) {
      // In presence of large pages we have to make sure that our
      // alignment is large page aware.
      alignment = lcm(os::large_page_size(), alignment);
  }

  return alignment;
}

void SerialArguments::initialize_alignments() {
  // Initialize card size before initializing alignments
  CardTable::initialize_card_size();
  SpaceAlignment = (size_t)Generation::GenGrain;
  HeapAlignment = compute_heap_alignment();
}

void SerialArguments::initialize() {
  GCArguments::initialize();
  FullGCForwarding::initialize_flags(MaxHeapSize);
}

size_t SerialArguments::conservative_max_heap_alignment() {
  return MAX2((size_t)Generation::GenGrain, compute_heap_alignment());
}

CollectedHeap* SerialArguments::create_heap() {
  return new SerialHeap();
}

size_t SerialArguments::young_gen_size_lower_bound() {
  // The young generation must be aligned and have room for eden + two survivors
  return 3 * SpaceAlignment;
}

size_t SerialArguments::old_gen_size_lower_bound() {
  return SpaceAlignment;
}
