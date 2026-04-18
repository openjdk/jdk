/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/gcLogPrecious.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zForwarding.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "logging/log.hpp"
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

bool ZForwarding::claim() {
  return _claimed.compare_set(false, true);
}

void ZForwarding::in_place_relocation_start(zoffset relocated_watermark) {
  _page->log_msg(" In-place reloc start  - relocated to: " PTR_FORMAT, untype(relocated_watermark));

  _in_place = true;

  // Support for ZHeap::is_in checks of from-space objects
  // in a page that is in-place relocating
  _in_place_thread.store_relaxed(Thread::current());
  _in_place_top_at_start = _page->top();
}

void ZForwarding::in_place_relocation_finish() {
  assert(_in_place, "Must be an in-place relocated page");

  _page->log_msg(" In-place reloc finish - top at start: " PTR_FORMAT, untype(_in_place_top_at_start));

  if (_from_age == ZPageAge::old || _to_age != ZPageAge::old) {
    // Only do this for non-promoted pages, that still need to reset live map.
    // Done with iterating over the "from-page" view, so can now drop the _livemap.
    _page->reset_livemap();
  }

  // Disable relaxed ZHeap::is_in checks
  _in_place_thread.store_relaxed(nullptr);
}

bool ZForwarding::in_place_relocation_is_below_top_at_start(zoffset offset) const {
  // Only the relocating thread is allowed to know about the old relocation top.
  return _in_place_thread.load_relaxed() == Thread::current() && offset < _in_place_top_at_start;
}

bool ZForwarding::retain_page(ZRelocateQueue* queue) {
  for (;;) {
    const int32_t ref_count = _ref_count.load_acquire();

    if (ref_count == 0) {
      // Released
      return false;
    }

    if (ref_count < 0) {
      // Claimed
      queue->add_and_wait(this);

      // Released
      return false;
    }

    if (_ref_count.compare_set(ref_count, ref_count + 1)) {
      // Retained
      return true;
    }
  }
}

void ZForwarding::in_place_relocation_claim_page() {
  for (;;) {
    const int32_t ref_count = _ref_count.load_relaxed();
    assert(ref_count > 0, "Invalid state");

    // Invert reference count
    if (!_ref_count.compare_set(ref_count, -ref_count)) {
      continue;
    }

    // If the previous reference count was 1, then we just changed it to -1,
    // and we have now claimed the page. Otherwise we wait until it is claimed.
    if (ref_count != 1) {
      ZLocker<ZConditionLock> locker(&_ref_lock);
      while (_ref_count.load_acquire() != -1) {
        _ref_lock.wait();
      }
    }

    // Done
    break;
  }
}

void ZForwarding::release_page() {
  for (;;) {
    const int32_t ref_count = _ref_count.load_relaxed();
    assert(ref_count != 0, "Invalid state");

    if (ref_count > 0) {
      // Decrement reference count
      if (!_ref_count.compare_set(ref_count, ref_count - 1)) {
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
      if (!_ref_count.compare_set(ref_count, ref_count + 1)) {
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

ZPage* ZForwarding::detach_page() {
  // Wait until released
  if (_ref_count.load_acquire() != 0) {
    ZLocker<ZConditionLock> locker(&_ref_lock);
    while (_ref_count.load_acquire() != 0) {
      _ref_lock.wait();
    }
  }

  return _page;
}

ZPage* ZForwarding::page() {
  assert(_ref_count.load_relaxed() != 0, "The page has been released/detached");
  return _page;
}

void ZForwarding::mark_done() {
  _done.store_relaxed(true);
}

bool ZForwarding::is_done() const {
  return _done.load_relaxed();
}

//
// The relocated_remembered_fields are used when the old generation
// collection is relocating objects, concurrently with the young
// generation collection's remembered set scanning for the marking.
//
// When the OC is relocating objects, the old remembered set bits
// for the from-space objects need to be moved over to the to-space
// objects.
//
// The YC doesn't want to wait for the OC, so it eagerly helps relocating
// objects with remembered set bits, so that it can perform marking on the
// to-space copy of the object fields that are associated with the remembered
// set bits.
//
// This requires some synchronization between the OC and YC, and this is
// mainly done via the _relocated_remembered_fields_state in each ZForwarding.
// The values corresponds to:
//
// none:      Starting state - neither OC nor YC has stated their intentions
// published: The OC has completed relocating all objects, and published an array
//            of all to-space fields that should have a remembered set entry.
// reject:    The OC relocation of the page happened concurrently with the YC
//            remset scanning. Two situations:
//            a) The page had not been released yet: The YC eagerly relocated and
//            scanned the to-space objects with remset entries.
//            b) The page had been released: The YC accepts the array published in
//            (published).
// accept:    The YC found that the forwarding/page had already been relocated when
//            the YC started.
//
// Central to this logic is the ZRemembered::scan_forwarding function, where
// the YC tries to "retain" the forwarding/page. If it succeeds it means that
// the OC has not finished (or maybe not even started) the relocation of all objects.
//
// When the YC manages to retaining the page it will bring the state from:
//  none      -> reject - Started collecting remembered set info
//  published -> reject - Rejected the OC's remembered set info
//  reject    -> reject - An earlier YC had already handled the remembered set info
//  accept    ->        - Invalid state - will not happen
//
// When the YC fails to retain the page the state transitions are:
// none      -> x - The page was relocated before the YC started
// published -> x - The OC completed relocation before YC visited this forwarding.
//                  The YC will use the remembered set info collected by the OC.
// reject    -> x - A previous YC has already handled the remembered set info
// accept    -> x - See above
//
// x is:
//  reject        - if the relocation finished while the current YC was running
//  accept        - if the relocation finished before the current YC started
//
// Note the subtlety that even though the relocation could released the page
// and made it non-retainable, the relocation code might not have gotten to
// the point where the page is removed from the page table. It could also be
// the case that the relocated page became in-place relocated, and we therefore
// shouldn't be scanning it this YC.
//
// The (reject) state is the "dangerous" state, where both OC and YC work on
// the same forwarding/page somewhat concurrently. While (accept) denotes that
// that the entire relocation of a page (including freeing/reusing it) was
// completed before the current YC started.
//
// After all remset entries of relocated objects have been scanned, the code
// proceeds to visit all pages in the page table, to scan all pages not part
// of the OC relocation set. Pages with virtual addresses that doesn't match
// any of the once in the OC relocation set will be visited. Pages with
// virtual address that *do* have a corresponding forwarding entry has two
// cases:
//
// a) The forwarding entry is marked with (reject). This means that the
//    corresponding page is guaranteed to be one that has been relocated by the
//    current OC during the active YC. Any remset entry is guaranteed to have
//    already been scanned by the scan_forwarding code.
//
// b) The forwarding entry is marked with (accept). This means that the page was
//    *not* created by the OC relocation during this YC, which means that the
//    page must be scanned.
//

void ZForwarding::relocated_remembered_fields_after_relocate() {
  assert(from_age() == ZPageAge::old, "Only old pages have remsets");

  _relocated_remembered_fields_publish_young_seqnum = ZGeneration::young()->seqnum();

  if (ZGeneration::young()->is_phase_mark()) {
    relocated_remembered_fields_publish();
  }
}

void ZForwarding::relocated_remembered_fields_publish() {
  // The OC has relocated all objects and collected all fields that
  // used to have remembered set entries. Now publish the fields to
  // the YC.

  const ZPublishState res = _relocated_remembered_fields_state.compare_exchange(ZPublishState::none, ZPublishState::published);

  // none:      OK to publish
  // published: Not possible - this operation makes this transition
  // reject:    YC started scanning the "from" page concurrently and rejects the fields
  //            the OC collected.
  // accept:    YC accepted the fields published by this function - not possible
  //            because they weren't published before the CAS above

  if (res == ZPublishState::none) {
    // fields were successfully published
    log_debug(gc, remset)("Forwarding remset published       : " PTR_FORMAT " " PTR_FORMAT, untype(start()), untype(end()));

    return;
  }

  log_debug(gc, remset)("Forwarding remset discarded       : " PTR_FORMAT " " PTR_FORMAT, untype(start()), untype(end()));

  // reject: YC scans the remset concurrently
  // accept: YC accepted published remset - not possible, we just atomically published it
  //         YC failed to retain page - not possible, since the current page is retainable
  assert(res == ZPublishState::reject, "Unexpected value");

  // YC has rejected the stored values and will (or have already) find them them itself
  _relocated_remembered_fields_array.clear_and_deallocate();
}

void ZForwarding::relocated_remembered_fields_notify_concurrent_scan_of() {
  // Invariant: The page is being retained
  assert(ZGeneration::young()->is_phase_mark(), "Only called when");

  const ZPublishState res = _relocated_remembered_fields_state.compare_exchange(ZPublishState::none, ZPublishState::reject);

  // none:      OC has not completed relocation
  // published: OC has completed and published all relocated remembered fields
  // reject:    A previous YC has already handled the field
  // accept:    A previous YC has determined that there's no concurrency between
  //            OC relocation and YC remembered fields scanning - not possible
  //            since the page has been retained (still being relocated) and
  //            we are in the process of scanning fields

  if (res == ZPublishState::none) {
    // Successfully notified and rejected any collected data from the OC
    log_debug(gc, remset)("Forwarding remset eager           : " PTR_FORMAT " " PTR_FORMAT, untype(start()), untype(end()));

    return;
  }

  if (res == ZPublishState::published) {
    // OC relocation already collected and published fields

    // Still notify concurrent scanning and reject the collected data from the OC
    const ZPublishState res2 = _relocated_remembered_fields_state.compare_exchange(ZPublishState::published, ZPublishState::reject);
    assert(res2 == ZPublishState::published, "Should not fail");

    log_debug(gc, remset)("Forwarding remset eager and reject: " PTR_FORMAT " " PTR_FORMAT, untype(start()), untype(end()));

    // The YC rejected the publish fields and is responsible for the array
    // Eagerly deallocate the memory
    _relocated_remembered_fields_array.clear_and_deallocate();
    return;
  }

  log_debug(gc, remset)("Forwarding remset redundant       : " PTR_FORMAT " " PTR_FORMAT, untype(start()), untype(end()));

  // Previous YC already handled the remembered fields
  assert(res == ZPublishState::reject, "Unexpected value");
}

bool ZForwarding::relocated_remembered_fields_published_contains(volatile zpointer* p) {
  for (volatile zpointer* const elem : _relocated_remembered_fields_array) {
    if (elem == p) {
      return true;
    }
  }

  return false;
}

void ZForwarding::verify() const {
  guarantee(_ref_count.load_relaxed() != 0, "Invalid reference count");
  guarantee(_page != nullptr, "Invalid page");

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
