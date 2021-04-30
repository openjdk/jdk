/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XPAGEALLOCATOR_HPP
#define SHARE_GC_X_XPAGEALLOCATOR_HPP

#include "gc/x/xAllocationFlags.hpp"
#include "gc/x/xArray.hpp"
#include "gc/x/xList.hpp"
#include "gc/x/xLock.hpp"
#include "gc/x/xPageCache.hpp"
#include "gc/x/xPhysicalMemory.hpp"
#include "gc/x/xSafeDelete.hpp"
#include "gc/x/xVirtualMemory.hpp"

class ThreadClosure;
class VMStructs;
class XPageAllocation;
class XPageAllocatorStats;
class XWorkers;
class XUncommitter;
class XUnmapper;

class XPageAllocator {
  friend class ::VMStructs;
  friend class XUnmapper;
  friend class XUncommitter;

private:
  mutable XLock              _lock;
  XPageCache                 _cache;
  XVirtualMemoryManager      _virtual;
  XPhysicalMemoryManager     _physical;
  const size_t               _min_capacity;
  const size_t               _max_capacity;
  volatile size_t            _current_max_capacity;
  volatile size_t            _capacity;
  volatile size_t            _claimed;
  volatile size_t            _used;
  size_t                     _used_high;
  size_t                     _used_low;
  ssize_t                    _reclaimed;
  XList<XPageAllocation>     _stalled;
  volatile uint64_t          _nstalled;
  XList<XPageAllocation>     _satisfied;
  XUnmapper*                 _unmapper;
  XUncommitter*              _uncommitter;
  mutable XSafeDelete<XPage> _safe_delete;
  bool                       _initialized;

  bool prime_cache(XWorkers* workers, size_t size);

  size_t increase_capacity(size_t size);
  void decrease_capacity(size_t size, bool set_max_capacity);

  void increase_used(size_t size, bool relocation);
  void decrease_used(size_t size, bool reclaimed);

  bool commit_page(XPage* page);
  void uncommit_page(XPage* page);

  void map_page(const XPage* page) const;
  void unmap_page(const XPage* page) const;

  void destroy_page(XPage* page);

  bool is_alloc_allowed(size_t size) const;

  bool alloc_page_common_inner(uint8_t type, size_t size, XList<XPage>* pages);
  bool alloc_page_common(XPageAllocation* allocation);
  bool alloc_page_stall(XPageAllocation* allocation);
  bool alloc_page_or_stall(XPageAllocation* allocation);
  bool should_defragment(const XPage* page) const;
  bool is_alloc_satisfied(XPageAllocation* allocation) const;
  XPage* alloc_page_create(XPageAllocation* allocation);
  XPage* alloc_page_finalize(XPageAllocation* allocation);
  void alloc_page_failed(XPageAllocation* allocation);

  void satisfy_stalled();

  void free_page_inner(XPage* page, bool reclaimed);

  size_t uncommit(uint64_t* timeout);

public:
  XPageAllocator(XWorkers* workers,
                 size_t min_capacity,
                 size_t initial_capacity,
                 size_t max_capacity);

  bool is_initialized() const;

  size_t min_capacity() const;
  size_t max_capacity() const;
  size_t soft_max_capacity() const;
  size_t capacity() const;
  size_t used() const;
  size_t unused() const;

  XPageAllocatorStats stats() const;

  void reset_statistics();

  XPage* alloc_page(uint8_t type, size_t size, XAllocationFlags flags);
  void free_page(XPage* page, bool reclaimed);
  void free_pages(const XArray<XPage*>* pages, bool reclaimed);

  void enable_deferred_delete() const;
  void disable_deferred_delete() const;

  void debug_map_page(const XPage* page) const;
  void debug_unmap_page(const XPage* page) const;

  bool has_alloc_stalled() const;
  void check_out_of_memory();

  void pages_do(XPageClosure* cl) const;

  void threads_do(ThreadClosure* tc) const;
};

class XPageAllocatorStats {
private:
  size_t _min_capacity;
  size_t _max_capacity;
  size_t _soft_max_capacity;
  size_t _current_max_capacity;
  size_t _capacity;
  size_t _used;
  size_t _used_high;
  size_t _used_low;
  size_t _reclaimed;

public:
  XPageAllocatorStats(size_t min_capacity,
                      size_t max_capacity,
                      size_t soft_max_capacity,
                      size_t capacity,
                      size_t used,
                      size_t used_high,
                      size_t used_low,
                      size_t reclaimed);

  size_t min_capacity() const;
  size_t max_capacity() const;
  size_t soft_max_capacity() const;
  size_t capacity() const;
  size_t used() const;
  size_t used_high() const;
  size_t used_low() const;
  size_t reclaimed() const;
};

#endif // SHARE_GC_X_XPAGEALLOCATOR_HPP
