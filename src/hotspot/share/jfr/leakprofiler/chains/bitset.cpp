/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/leakprofiler/chains/bitset.hpp"
#include "jfr/recorder/storage/jfrVirtualMemory.hpp"
#include "memory/memRegion.hpp"

BitSet::BitSet(const MemRegion& covered_region) :
  _vmm(NULL),
  _region_start(covered_region.start()),
  _region_size(covered_region.word_size()) {
}

BitSet::~BitSet() {
  delete _vmm;
}

bool BitSet::initialize() {
  assert(_vmm == NULL, "invariant");
  _vmm = new JfrVirtualMemory();
  if (_vmm == NULL) {
    return false;
  }

  const BitMap::idx_t bits = _region_size >> LogMinObjAlignment;
  const size_t words = bits / BitsPerWord;
  const size_t raw_bytes = words * sizeof(BitMap::idx_t);

  // the virtual memory invocation will reserve and commit the entire space
  BitMap::bm_word_t* map = (BitMap::bm_word_t*)_vmm->initialize(raw_bytes, raw_bytes);
  if (map == NULL) {
    return false;
  }
  _bits = BitMapView(map, bits);
  return true;
}

