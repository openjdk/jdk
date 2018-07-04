/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZPAGEALLOCATOR_HPP
#define SHARE_GC_Z_ZPAGEALLOCATOR_HPP

#include "gc/z/zAllocationFlags.hpp"
#include "gc/z/zList.hpp"
#include "gc/z/zLock.hpp"
#include "gc/z/zPageCache.hpp"
#include "gc/z/zPhysicalMemory.hpp"
#include "gc/z/zPreMappedMemory.hpp"
#include "gc/z/zVirtualMemory.hpp"
#include "memory/allocation.hpp"

class ZPageAllocRequest;

class ZPageAllocator {
  friend class VMStructs;

private:
  ZLock                    _lock;
  ZVirtualMemoryManager    _virtual;
  ZPhysicalMemoryManager   _physical;
  ZPageCache               _cache;
  const size_t             _max_reserve;
  ZPreMappedMemory         _pre_mapped;
  size_t                   _used_high;
  size_t                   _used_low;
  size_t                   _used;
  size_t                   _allocated;
  ssize_t                  _reclaimed;
  ZList<ZPageAllocRequest> _queue;
  ZList<ZPage>             _detached;

  static ZPage* const      gc_marker;

  void increase_used(size_t size, bool relocation);
  void decrease_used(size_t size, bool reclaimed);

  size_t max_available(bool no_reserve) const;
  size_t try_ensure_unused(size_t size, bool no_reserve);
  size_t try_ensure_unused_for_pre_mapped(size_t size);

  ZPage* create_page(uint8_t type, size_t size);
  void map_page(ZPage* page);
  void detach_page(ZPage* page);
  void flush_pre_mapped();
  void flush_cache(size_t size);

  void check_out_of_memory_during_initialization();

  ZPage* alloc_page_common_inner(uint8_t type, size_t size, ZAllocationFlags flags);
  ZPage* alloc_page_common(uint8_t type, size_t size, ZAllocationFlags flags);
  ZPage* alloc_page_blocking(uint8_t type, size_t size, ZAllocationFlags flags);
  ZPage* alloc_page_nonblocking(uint8_t type, size_t size, ZAllocationFlags flags);

  void satisfy_alloc_queue();

  void detach_memory(const ZVirtualMemory& vmem, ZPhysicalMemory& pmem);

public:
  ZPageAllocator(size_t min_capacity, size_t max_capacity, size_t max_reserve);

  bool is_initialized() const;

  size_t max_capacity() const;
  size_t current_max_capacity() const;
  size_t capacity() const;
  size_t max_reserve() const;
  size_t used_high() const;
  size_t used_low() const;
  size_t used() const;
  size_t allocated() const;
  size_t reclaimed() const;

  void reset_statistics();

  ZPage* alloc_page(uint8_t type, size_t size, ZAllocationFlags flags);
  void flip_page(ZPage* page);
  void free_page(ZPage* page, bool reclaimed);
  void destroy_page(ZPage* page);

  void flush_detached_pages(ZList<ZPage>* list);

  void flip_pre_mapped();

  bool is_alloc_stalled() const;
  void check_out_of_memory();
};

#endif // SHARE_GC_Z_ZPAGEALLOCATOR_HPP
