/*
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

#ifndef SHARE_GC_SERIAL_SERIALGCVIRTUALSPACE_HPP
#define SHARE_GC_SERIAL_SERIALGCVIRTUALSPACE_HPP

#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "memory/reservedSpace.hpp"
#include "memory/virtualspace.hpp"

class SerialGCVirtualSpace: public CHeapObj<mtInternal> {
private:
  VirtualSpace _virtual_space;
  MemRegion _heap_region;

  MemRegion _tenured_region;
  MemRegion _young_region;

  // returns true on success, false otherwise
  bool expand_by(size_t bytes, bool pre_touch = false);
  void shrink_by(size_t bytes);
  bool resize_virtual_space(size_t tenured_gen_size, size_t young_gen_size);

public:
  SerialGCVirtualSpace() {}

  void initialize(ReservedSpace rs, size_t old_size, size_t new_size);

  inline size_t committed_size();

  MemRegion tenured_region() { return _tenured_region; }
  MemRegion young_region() { return _young_region; }

  void set_tenured_region(MemRegion region);
  void set_young_region(MemRegion region);

  bool resize(size_t young_gen_size);
  bool resize(size_t tenured_gen_size, size_t young_gen_size);

  size_t max_new_size() const;
};

#endif // SHARE_GC_SERIAL_SERIALGCVIRTUALSPACE_HPP
