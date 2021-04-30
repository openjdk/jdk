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
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zForwarding.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zThread.inline.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "utilities/align.hpp"

//
// Reference count states:
//
// * If the reference count is zero, it will never change again.
//
// * If the reference count is positive, it can be both retained
//   (increased) and released (decreased).
//
// * If the reference count is negative, is can only be released
//   (increased). A negative reference count means that one or more
//   threads are waiting for one or more other threads to release
//   their references.
//
// The reference lock is used for waiting until the reference
// count has become zero (released) or negative one (claimed).
//

static const ZStatCriticalPhase ZCriticalPhaseRelocationStall("Relocation Stall");

bool ZForwarding::claim() {
  return Atomic::cmpxchg(&_claimed, false, true) == false;
}

void ZForwarding::in_place_relocation_start() {
  _page->log_msg(" In-place reloc start");

  _in_place = true;

  // Support for ZHeap::is_in checks of from-space objects
  // in a page that is in-place relocating
  Atomic::store(&_in_place_thread, Thread::current());
  _in_place_top_at_start = _page->top();
}

void ZForwarding::in_place_relocation_finish() {
  assert(_in_place, "Must be an in-place relocated page");

  _page->log_msg(err_msg(" In-place reloc finish - top at start: " PTR_FORMAT, untype(_in_place_top_at_start)));

  if (_age_from == ZPageAge::old && _age_to == ZPageAge::old) {
    // The old to old relocation reused the ZPage
    // It took ownership of clearing and updating the remset up to,
    // and including, the last from object. Clear the rest.
    in_place_relocation_clear_remset_up_to(_in_place_top_at_start - _page->start());

  }

  if (_age_from == ZPageAge::old || _age_to != ZPageAge::old) {
    // Only do this for non-promoted pages, that still need to reset live map.
    // Done with iterating over the "from-page" view, so can now drop the _livemap.
    _page->finalize_reset_for_in_place_relocation();
  }

  // Disable relaxed ZHeap::is_in checks
  Atomic::store(&_in_place_thread, (Thread*)nullptr);
}

bool ZForwarding::in_place_relocation_is_below_top_at_start(zoffset offset) const {
  // Only the relocating thread is allowed to know about the old relocation top.
  return Atomic::load(&_in_place_thread) == Thread::current() && offset < _in_place_top_at_start;
}

void ZForwarding::in_place_relocation_clear_remset_up_to(uintptr_t local_offset) const {
  const size_t size = local_offset - _in_place_clear_remset_watermark;
  if (size > 0) {
    _page->log_msg(err_msg(" In-place clear  : " PTR_FORMAT, untype(_page->start() + local_offset)));
    _page->clear_remset_range_non_par(_in_place_clear_remset_watermark, size);
  }
}

void ZForwarding::in_place_relocation_set_clear_remset_watermark(uintptr_t local_offset) {
  _page->log_msg(err_msg(" In-place cleared: " PTR_FORMAT, untype(_page->start() + local_offset)));
  _in_place_clear_remset_watermark = local_offset;
}

bool ZForwarding::retain_page() {
  for (;;) {
    const int32_t ref_count = Atomic::load_acquire(&_ref_count);

    if (ref_count == 0) {
      // Released
      return false;
    }

    if (ref_count < 0) {
      // Claimed
      const bool success = wait_page_released();
      assert(success, "Should always succeed");
      return false;
    }

    if (Atomic::cmpxchg(&_ref_count, ref_count, ref_count + 1) == ref_count) {
      // Retained
      return true;
    }
  }
}

void ZForwarding::in_place_relocation_claim_page() {
  for (;;) {
    const int32_t ref_count = Atomic::load(&_ref_count);
    assert(ref_count > 0, "Invalid state");

    // Invert reference count
    if (Atomic::cmpxchg(&_ref_count, ref_count, -ref_count) != ref_count) {
      continue;
    }

    // If the previous reference count was 1, then we just changed it to -1,
    // and we have now claimed the page. Otherwise we wait until it is claimed.
    if (ref_count != 1) {
      ZLocker<ZConditionLock> locker(&_ref_lock);
      while (Atomic::load_acquire(&_ref_count) != -1) {
        _ref_lock.wait();
      }
    }

    // Done
    break;
  }
}

void ZForwarding::release_page() {
  for (;;) {
    const int32_t ref_count = Atomic::load(&_ref_count);
    assert(ref_count != 0, "Invalid state");

    if (ref_count > 0) {
      // Decrement reference count
      if (Atomic::cmpxchg(&_ref_count, ref_count, ref_count - 1) != ref_count) {
        continue;
      }

      // If the previous reference count was 1, then we just decremented
      // it to 0 and we should signal that the page is now released.
      if (ref_count == 1) {
        // Notify released
        ZLocker<ZConditionLock> locker(&_ref_lock);
        _ref_lock.notify_all();
      }
    } else {
      // Increment reference count
      if (Atomic::cmpxchg(&_ref_count, ref_count, ref_count + 1) != ref_count) {
        continue;
      }

      // If the previous reference count was -2 or -1, then we just incremented it
      // to -1 or 0, and we should signal the that page is now claimed or released.
      if (ref_count == -2 || ref_count == -1) {
        // Notify claimed or released
        ZLocker<ZConditionLock> locker(&_ref_lock);
        _ref_lock.notify_all();
      }
    }

    return;
  }
}

bool ZForwarding::wait_page_released() const {
  if (Atomic::load_acquire(&_ref_count) != 0) {
    ZStatTimerFIXME timer(ZCriticalPhaseRelocationStall);
    ZLocker<ZConditionLock> locker(&_ref_lock);
    while (Atomic::load_acquire(&_ref_count) != 0) {
      if (_ref_abort) {
        return false;
      }

      _ref_lock.wait();
    }
  }

  return true;
}

ZPage* ZForwarding::detach_page() {
  // Wait until released
  if (Atomic::load_acquire(&_ref_count) != 0) {
    ZLocker<ZConditionLock> locker(&_ref_lock);
    while (Atomic::load_acquire(&_ref_count) != 0) {
      _ref_lock.wait();
    }
  }

  return _page;
}

ZPage* ZForwarding::page() {
  assert(Atomic::load(&_ref_count) != 0, "The page has been released/detached");
  return _page;
}

void ZForwarding::abort_page() {
  ZLocker<ZConditionLock> locker(&_ref_lock);
  assert(Atomic::load(&_ref_count) > 0, "Invalid state");
  assert(!_ref_abort, "Invalid state");
  _ref_abort = true;
  _ref_lock.notify_all();
}

void ZForwarding::verify() const {
  guarantee(_ref_count != 0, "Invalid reference count");
  guarantee(_page != NULL, "Invalid page");

  uint32_t live_objects = 0;
  size_t live_bytes = 0;

  for (ZForwardingCursor i = 0; i < _entries.length(); i++) {
    const ZForwardingEntry entry = at(&i);
    if (!entry.populated()) {
      // Skip empty entries
      continue;
    }

    // Check from index
    guarantee(entry.from_index() < _page->object_max_count(), "Invalid from index");

    // Check for duplicates
    for (ZForwardingCursor j = i + 1; j < _entries.length(); j++) {
      const ZForwardingEntry other = at(&j);
      if (!other.populated()) {
        // Skip empty entries
        continue;
      }

      guarantee(entry.from_index() != other.from_index(), "Duplicate from");
      guarantee(entry.to_offset() != other.to_offset(), "Duplicate to");
    }

    const zaddress to_addr = ZOffset::address(to_zoffset(entry.to_offset()));
    const size_t size = ZUtils::object_size(to_addr);
    const size_t aligned_size = align_up(size, _page->object_alignment());
    live_bytes += aligned_size;
    live_objects++;
  }

  // Verify number of live objects and bytes
  _page->verify_live(live_objects, live_bytes, _in_place);
}
