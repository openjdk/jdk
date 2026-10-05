/*
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

#include "cds/aotMappedHeap.hpp"

// Anything that goes in the header must be thoroughly purged from uninitialized memory
// as it will be written to disk. Therefore, the constructors memset the memory to 0.
// This is not the prettiest thing, but we need to know every byte is initialized,
// including potential padding between fields.

AOTMappedHeapHeader::AOTMappedHeapHeader(size_t ptrmap_start_pos,
                                         size_t oopmap_start_pos,
                                         HeapRootSegments root_segments) {
  memset((char*)this, 0, sizeof(*this));
  _ptrmap_start_pos = ptrmap_start_pos;
  _oopmap_start_pos = oopmap_start_pos;
  _root_segments = root_segments;
}

AOTMappedHeapHeader::AOTMappedHeapHeader() {
  memset((char*)this, 0, sizeof(*this));
}

AOTMappedHeapHeader AOTMappedHeapInfo::create_header() {
  return AOTMappedHeapHeader{_ptrmap_start_pos,
                             _oopmap_start_pos,
                             _root_segments};
}
