/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZOBJECTALLOCATOR_HPP
#define SHARE_GC_Z_ZOBJECTALLOCATOR_HPP

#include "gc/z/zAddress.hpp"
#include "gc/z/zAllocationFlags.hpp"
#include "gc/z/zLock.hpp"
#include "gc/z/zPageAge.hpp"
#include "gc/z/zPageType.hpp"
#include "gc/z/zValue.hpp"

class ZPage;
class ZPageTable;

class ZObjectAllocator {
private:
  ZPageAge           _age;
  const bool         _use_per_cpu_shared_small_pages;
  ZPerCPU<size_t>    _used;
  ZPerCPU<size_t>    _undone;
  ZPerCPU<ZPage*>    _shared_small_page;
  ZContended<ZPage*> _shared_medium_page;
  ZLock              _medium_page_alloc_lock;

  ZPage** shared_small_page_addr();
  ZPage* const* shared_small_page_addr() const;

  ZPage* alloc_page(ZPageType type, size_t size, ZAllocationFlags flags);
  void undo_alloc_page(ZPage* page);

  // Allocate an object in a shared page. Allocate and
  // atomically install a new page if necessary.
  zaddress alloc_object_in_shared_page(ZPage** shared_page,
                                       ZPageType page_type,
                                       size_t page_size,
                                       size_t size,
                                       ZAllocationFlags flags);

  zaddress alloc_object_in_medium_page(size_t size,
                                       ZAllocationFlags flags);

  zaddress alloc_large_object(size_t size, ZAllocationFlags flags);
  zaddress alloc_medium_object(size_t size, ZAllocationFlags flags);
  zaddress alloc_small_object(size_t size, ZAllocationFlags flags);
  zaddress alloc_object(size_t size, ZAllocationFlags flags);

public:
  ZObjectAllocator(ZPageAge age);

  // Mutator allocation
  zaddress alloc_object(size_t size);

  // Relocation
  zaddress alloc_object_for_relocation(size_t size);
  void undo_alloc_object_for_relocation(zaddress addr, size_t size);

  ZPage* alloc_page_for_relocation(ZPageType type, size_t size, ZAllocationFlags flags);

  ZPageAge age() const;

  size_t used() const;
  size_t remaining() const;

  void retire_pages();
};

#endif // SHARE_GC_Z_ZOBJECTALLOCATOR_HPP
