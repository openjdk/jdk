/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZALLOCATOR_HPP
#define SHARE_GC_Z_ZALLOCATOR_HPP

#include "gc/z/zAllocationFlags.hpp"
#include "gc/z/zObjectAllocator.hpp"
#include "gc/z/zPageAge.hpp"
#include "gc/z/zPageType.hpp"

class ZAllocatorEden;
class ZAllocatorForRelocation;
class ZPage;

class ZAllocator {
public:
  static constexpr uint _relocation_allocators = static_cast<uint>(ZPageAge::old);

protected:
  ZObjectAllocator _object_allocator;

  static ZAllocatorEden*          _eden;
  static ZAllocatorForRelocation* _relocation[ZAllocator::_relocation_allocators];

public:
  static ZAllocatorEden* eden();
  static ZAllocatorForRelocation* relocation(ZPageAge page_age);
  static ZAllocatorForRelocation* old();

  ZAllocator(ZPageAge age);

  void retire_pages();
};

class ZAllocatorEden : public ZAllocator {
public:
  ZAllocatorEden();

  // Mutator allocation
  zaddress alloc_tlab(size_t size);
  zaddress alloc_object(size_t size);

  // Statistics
  size_t tlab_used() const;
  size_t remaining() const;
};

class ZAllocatorForRelocation : public ZAllocator {
private:
  ZPageAge install();

public:
  ZAllocatorForRelocation();

  // Relocation
  zaddress alloc_object(size_t size);
  void undo_alloc_object(zaddress addr, size_t size);

  ZPage* alloc_page_for_relocation(ZPageType type, size_t size, ZAllocationFlags flags);
};

#endif // SHARE_GC_Z_ZALLOCATOR_HPP
