/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * DO NOT ALTER OR REMOVE THIS COPYRIGHT NOTICE OR THIS FILE HEADER.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHALLOCATOR_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHALLOCATOR_HPP

#include "memory/allocation.hpp"

class ShenandoahAllocRequest;
class ShenandoahFreeSet;

// ShenandoahAllocator defines the allocation interface for the Shenandoah GC.
// Subclasses implement different allocation strategies (e.g. serial under heap lock, CAS-based).
// Partition accounting is delegated back to ShenandoahFreeSet.
class ShenandoahAllocator : public CHeapObj<mtGC> {
protected:
  ShenandoahFreeSet* const _free_set;

public:
  ShenandoahAllocator(ShenandoahFreeSet* free_set) : _free_set(free_set) {}
  virtual ~ShenandoahAllocator() = default;

  // Allocate memory for the given request. Returns nullptr on failure.
  // Sets in_new_region to true if allocation consumes a previously empty region.
  virtual HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region) = 0;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHALLOCATOR_HPP
