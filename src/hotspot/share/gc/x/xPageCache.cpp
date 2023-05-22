/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/x/xGlobals.hpp"
#include "gc/x/xList.inline.hpp"
#include "gc/x/xNUMA.hpp"
#include "gc/x/xPage.inline.hpp"
#include "gc/x/xPageCache.hpp"
#include "gc/x/xStat.hpp"
#include "gc/x/xValue.inline.hpp"
#include "memory/allocation.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"

static const XStatCounter XCounterPageCacheHitL1("Memory", "Page Cache Hit L1", XStatUnitOpsPerSecond);
static const XStatCounter XCounterPageCacheHitL2("Memory", "Page Cache Hit L2", XStatUnitOpsPerSecond);
static const XStatCounter XCounterPageCacheHitL3("Memory", "Page Cache Hit L3", XStatUnitOpsPerSecond);
static const XStatCounter XCounterPageCacheMiss("Memory", "Page Cache Miss", XStatUnitOpsPerSecond);

class XPageCacheFlushClosure : public StackObj {
  friend class XPageCache;

protected:
  const size_t _requested;
  size_t       _flushed;

public:
  XPageCacheFlushClosure(size_t requested);
  virtual bool do_page(const XPage* page) = 0;
};

XPageCacheFlushClosure::XPageCacheFlushClosure(size_t requested) :
    _requested(requested),
    _flushed(0) {}

XPageCache::XPageCache() :
    _small(),
    _medium(),
    _large(),
    _last_commit(0) {}

XPage* XPageCache::alloc_small_page() {
  const uint32_t numa_id = XNUMA::id();
  const uint32_t numa_count = XNUMA::count();

  // Try NUMA local page cache
  XPage* const l1_page = _small.get(numa_id).remove_first();
  if (l1_page != nullptr) {
    XStatInc(XCounterPageCacheHitL1);
    return l1_page;
  }

  // Try NUMA remote page cache(s)
  uint32_t remote_numa_id = numa_id + 1;
  const uint32_t remote_numa_count = numa_count - 1;
  for (uint32_t i = 0; i < remote_numa_count; i++) {
    if (remote_numa_id == numa_count) {
      remote_numa_id = 0;
    }

    XPage* const l2_page = _small.get(remote_numa_id).remove_first();
    if (l2_page != nullptr) {
      XStatInc(XCounterPageCacheHitL2);
      return l2_page;
    }

    remote_numa_id++;
  }

  return nullptr;
}

XPage* XPageCache::alloc_medium_page() {
  XPage* const page = _medium.remove_first();
  if (page != nullptr) {
    XStatInc(XCounterPageCacheHitL1);
    return page;
  }

  return nullptr;
}

XPage* XPageCache::alloc_large_page(size_t size) {
  // Find a page with the right size
  XListIterator<XPage> iter(&_large);
  for (XPage* page; iter.next(&page);) {
    if (size == page->size()) {
      // Page found
      _large.remove(page);
      XStatInc(XCounterPageCacheHitL1);
      return page;
    }
  }

  return nullptr;
}

XPage* XPageCache::alloc_oversized_medium_page(size_t size) {
  if (size <= XPageSizeMedium) {
    return _medium.remove_first();
  }

  return nullptr;
}

XPage* XPageCache::alloc_oversized_large_page(size_t size) {
  // Find a page that is large enough
  XListIterator<XPage> iter(&_large);
  for (XPage* page; iter.next(&page);) {
    if (size <= page->size()) {
      // Page found
      _large.remove(page);
      return page;
    }
  }

  return nullptr;
}

XPage* XPageCache::alloc_oversized_page(size_t size) {
  XPage* page = alloc_oversized_large_page(size);
  if (page == nullptr) {
    page = alloc_oversized_medium_page(size);
  }

  if (page != nullptr) {
    XStatInc(XCounterPageCacheHitL3);
  }

  return page;
}

XPage* XPageCache::alloc_page(uint8_t type, size_t size) {
  XPage* page;

  // Try allocate exact page
  if (type == XPageTypeSmall) {
    page = alloc_small_page();
  } else if (type == XPageTypeMedium) {
    page = alloc_medium_page();
  } else {
    page = alloc_large_page(size);
  }

  if (page == nullptr) {
    // Try allocate potentially oversized page
    XPage* const oversized = alloc_oversized_page(size);
    if (oversized != nullptr) {
      if (size < oversized->size()) {
        // Split oversized page
        page = oversized->split(type, size);

        // Cache remainder
        free_page(oversized);
      } else {
        // Re-type correctly sized page
        page = oversized->retype(type);
      }
    }
  }

  if (page == nullptr) {
    XStatInc(XCounterPageCacheMiss);
  }

  return page;
}

void XPageCache::free_page(XPage* page) {
  const uint8_t type = page->type();
  if (type == XPageTypeSmall) {
    _small.get(page->numa_id()).insert_first(page);
  } else if (type == XPageTypeMedium) {
    _medium.insert_first(page);
  } else {
    _large.insert_first(page);
  }
}

bool XPageCache::flush_list_inner(XPageCacheFlushClosure* cl, XList<XPage>* from, XList<XPage>* to) {
  XPage* const page = from->last();
  if (page == nullptr || !cl->do_page(page)) {
    // Don't flush page
    return false;
  }

  // Flush page
  from->remove(page);
  to->insert_last(page);
  return true;
}

void XPageCache::flush_list(XPageCacheFlushClosure* cl, XList<XPage>* from, XList<XPage>* to) {
  while (flush_list_inner(cl, from, to));
}

void XPageCache::flush_per_numa_lists(XPageCacheFlushClosure* cl, XPerNUMA<XList<XPage> >* from, XList<XPage>* to) {
  const uint32_t numa_count = XNUMA::count();
  uint32_t numa_done = 0;
  uint32_t numa_next = 0;

  // Flush lists round-robin
  while (numa_done < numa_count) {
    XList<XPage>* numa_list = from->addr(numa_next);
    if (++numa_next == numa_count) {
      numa_next = 0;
    }

    if (flush_list_inner(cl, numa_list, to)) {
      // Not done
      numa_done = 0;
    } else {
      // Done
      numa_done++;
    }
  }
}

void XPageCache::flush(XPageCacheFlushClosure* cl, XList<XPage>* to) {
  // Prefer flushing large, then medium and last small pages
  flush_list(cl, &_large, to);
  flush_list(cl, &_medium, to);
  flush_per_numa_lists(cl, &_small, to);

  if (cl->_flushed > cl->_requested) {
    // Overflushed, re-insert part of last page into the cache
    const size_t overflushed = cl->_flushed - cl->_requested;
    XPage* const reinsert = to->last()->split(overflushed);
    free_page(reinsert);
    cl->_flushed -= overflushed;
  }
}

class XPageCacheFlushForAllocationClosure : public XPageCacheFlushClosure {
public:
  XPageCacheFlushForAllocationClosure(size_t requested) :
      XPageCacheFlushClosure(requested) {}

  virtual bool do_page(const XPage* page) {
    if (_flushed < _requested) {
      // Flush page
      _flushed += page->size();
      return true;
    }

    // Don't flush page
    return false;
  }
};

void XPageCache::flush_for_allocation(size_t requested, XList<XPage>* to) {
  XPageCacheFlushForAllocationClosure cl(requested);
  flush(&cl, to);
}

class XPageCacheFlushForUncommitClosure : public XPageCacheFlushClosure {
private:
  const uint64_t _now;
  uint64_t*      _timeout;

public:
  XPageCacheFlushForUncommitClosure(size_t requested, uint64_t now, uint64_t* timeout) :
      XPageCacheFlushClosure(requested),
      _now(now),
      _timeout(timeout) {
    // Set initial timeout
    *_timeout = ZUncommitDelay;
  }

  virtual bool do_page(const XPage* page) {
    const uint64_t expires = page->last_used() + ZUncommitDelay;
    if (expires > _now) {
      // Don't flush page, record shortest non-expired timeout
      *_timeout = MIN2(*_timeout, expires - _now);
      return false;
    }

    if (_flushed >= _requested) {
      // Don't flush page, requested amount flushed
      return false;
    }

    // Flush page
    _flushed += page->size();
    return true;
  }
};

size_t XPageCache::flush_for_uncommit(size_t requested, XList<XPage>* to, uint64_t* timeout) {
  const uint64_t now = os::elapsedTime();
  const uint64_t expires = _last_commit + ZUncommitDelay;
  if (expires > now) {
    // Delay uncommit, set next timeout
    *timeout = expires - now;
    return 0;
  }

  if (requested == 0) {
    // Nothing to flush, set next timeout
    *timeout = ZUncommitDelay;
    return 0;
  }

  XPageCacheFlushForUncommitClosure cl(requested, now, timeout);
  flush(&cl, to);

  return cl._flushed;
}

void XPageCache::set_last_commit() {
  _last_commit = ceil(os::elapsedTime());
}

void XPageCache::pages_do(XPageClosure* cl) const {
  // Small
  XPerNUMAConstIterator<XList<XPage> > iter_numa(&_small);
  for (const XList<XPage>* list; iter_numa.next(&list);) {
    XListIterator<XPage> iter_small(list);
    for (XPage* page; iter_small.next(&page);) {
      cl->do_page(page);
    }
  }

  // Medium
  XListIterator<XPage> iter_medium(&_medium);
  for (XPage* page; iter_medium.next(&page);) {
    cl->do_page(page);
  }

  // Large
  XListIterator<XPage> iter_large(&_large);
  for (XPage* page; iter_large.next(&page);) {
    cl->do_page(page);
  }
}
