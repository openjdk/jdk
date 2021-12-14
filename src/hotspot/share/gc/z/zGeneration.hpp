/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZGENERATION_HPP
#define SHARE_GC_Z_ZGENERATION_HPP

#include "gc/z/zGenerationId.hpp"
#include "gc/z/zObjectAllocator.hpp"
#include "gc/z/zPageAge.hpp"
#include "gc/z/zPageAllocator.hpp"

class ZOldGeneration;
class ZYoungGeneration;

class ZGeneration {
protected:
  static ZYoungGeneration* _young;
  static ZOldGeneration*   _old;

  const ZGenerationId _id;
  size_t              _used;

public:
  ZGeneration(ZGenerationId id);

  ZGenerationId id() const;

  void increase_used(size_t size);
  void decrease_used(size_t size);

  size_t used() const;

  bool is_young() const;
  bool is_old() const;

  static ZYoungGeneration* young();
  static ZOldGeneration* old();
  static ZGeneration* generation(ZGenerationId id);

  // Allocation
  virtual zaddress alloc_object_for_relocation(size_t size, bool promotion) = 0;
  virtual void undo_alloc_object_for_relocation(zaddress addr, size_t size, bool promotion) = 0;
};

class ZYoungGeneration : public ZGeneration {
private:
  ZObjectAllocator _eden_allocator;
  ZObjectAllocator _survivor_allocator;

public:
  ZYoungGeneration();

  // Allocation
  zaddress alloc_tlab(size_t size);
  zaddress alloc_object(size_t size);
  virtual zaddress alloc_object_for_relocation(size_t size, bool promotion);
  virtual void undo_alloc_object_for_relocation(zaddress addr, size_t size, bool promotion);
  void retire_pages();

  // Statistics
  size_t tlab_used() const;
  size_t remaining() const;
};

class ZOldGeneration : public ZGeneration {
private:
  ZObjectAllocator _old_allocator;

public:
  ZOldGeneration();

  // Allocation
  virtual zaddress alloc_object_for_relocation(size_t size, bool promotion);
  virtual void undo_alloc_object_for_relocation(zaddress addr, size_t size, bool promotion);
  void retire_pages();
};

#endif // SHARE_GC_Z_ZGENERATION_HPP
