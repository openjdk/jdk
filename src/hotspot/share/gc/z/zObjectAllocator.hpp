/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/deferred.hpp"
#include "utilities/valueObjArray.hpp"

class ZPage;
class ZPageTable;

class ZObjectAllocator {
  template <typename Type, int Count> friend class ValueObjBlock;
  friend class ZObjectAllocators;

private:
  const ZPageAge     _age;
  const bool         _use_per_cpu_shared_small_pages;
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

  void retire_pages();

  ZObjectAllocator(ZPageAge age);
};

class ZObjectAllocators : public AllStatic {
public:
  static constexpr uint NumAllocators = ZPageAgeCount;
  static constexpr uint NumRelocationAllocators = NumAllocators - 1;

private:
  using Allocators = Deferred<ValueObjArray<ZObjectAllocator, NumAllocators>>;

  static Allocators _allocators;
  static ZObjectAllocator* allocator(ZPageAge age);

public:
  static void initialize();

  static void retire_pages(ZPageAgeRange range);

  static size_t remaining_in_eden();

  // Mutator allocation
  static zaddress alloc_object(size_t size, ZPageAge age);
  static void undo_alloc_object(zaddress addr, size_t size, ZPageAge age);
};

#endif // SHARE_GC_Z_ZOBJECTALLOCATOR_HPP
