/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/z/zList.inline.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageAllocator.hpp"
#include "gc/z/zUnmapper.hpp"
#include "jfr/jfrEvents.hpp"
#include "runtime/globals.hpp"

ZUnmapper::ZUnmapper(ZPageAllocator* page_allocator)
  : _page_allocator(page_allocator),
    _lock(),
    _queue(),
    _enqueued_bytes(0),
    _warned_sync_unmapping(false),
    _stop(false) {
  set_name("ZUnmapper");
  create_and_start();
}

ZPage* ZUnmapper::dequeue() {
  ZLocker<ZConditionLock> locker(&_lock);

  for (;;) {
    if (_stop) {
      return nullptr;
    }

    ZPage* const page = _queue.remove_first();
    if (page != nullptr) {
      _enqueued_bytes -= page->size();
      return page;
    }

    _lock.wait();
  }
}

bool ZUnmapper::try_enqueue(ZPage* page) {
  // Enqueue for asynchronous unmap and destroy
  ZLocker<ZConditionLock> locker(&_lock);
  if (is_saturated()) {
    // The unmapper thread is lagging behind and is unable to unmap memory fast enough
    if (!_warned_sync_unmapping) {
      _warned_sync_unmapping = true;
      log_warning_p(gc)("WARNING: Encountered synchronous unmapping because asynchronous unmapping could not keep up");
    }
    log_debug(gc, unmap)("Synchronous unmapping " SIZE_FORMAT "M page", page->size() / M);
    return false;
  }

  log_trace(gc, unmap)("Asynchronous unmapping " SIZE_FORMAT "M page (" SIZE_FORMAT "M / " SIZE_FORMAT "M enqueued)",
                       page->size() / M, _enqueued_bytes / M, queue_capacity() / M);

  _queue.insert_last(page);
  _enqueued_bytes += page->size();
  _lock.notify_all();

  return true;
}

size_t ZUnmapper::queue_capacity() const {
  return align_up((size_t)(_page_allocator->max_capacity() * ZAsyncUnmappingLimit / 100.0), ZGranuleSize);
}

bool ZUnmapper::is_saturated() const {
  return _enqueued_bytes >= queue_capacity();
}

void ZUnmapper::do_unmap_and_destroy_page(ZPage* page) const {
  EventZUnmap event;
  const size_t unmapped = page->size();

  // Unmap and destroy
  _page_allocator->unmap_page(page);
  _page_allocator->destroy_page(page);

  // Send event
  event.commit(unmapped);
}

void ZUnmapper::unmap_and_destroy_page(ZPage* page) {
  if (!try_enqueue(page)) {
    // Synchronously unmap and destroy
    do_unmap_and_destroy_page(page);
  }
}

void ZUnmapper::run_thread() {
  for (;;) {
    ZPage* const page = dequeue();
    if (page == nullptr) {
      // Stop
      return;
    }

    do_unmap_and_destroy_page(page);
  }
}

void ZUnmapper::terminate() {
  ZLocker<ZConditionLock> locker(&_lock);
  _stop = true;
  _lock.notify_all();
}
