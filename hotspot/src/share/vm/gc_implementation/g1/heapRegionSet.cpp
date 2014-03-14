/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#include "precompiled.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#include "gc_implementation/g1/heapRegionSet.inline.hpp"

uint FreeRegionList::_unrealistically_long_length = 0;

void HeapRegionSetBase::fill_in_ext_msg(hrs_ext_msg* msg, const char* message) {
  msg->append("[%s] %s ln: %u cy: "SIZE_FORMAT,
              name(), message, length(), total_capacity_bytes());
  fill_in_ext_msg_extra(msg);
}

#ifndef PRODUCT
void HeapRegionSetBase::verify_region(HeapRegion* hr) {
  assert(hr->containing_set() == this, err_msg("Inconsistent containing set for %u", hr->hrs_index()));
  assert(!hr->is_young(), err_msg("Adding young region %u", hr->hrs_index())); // currently we don't use these sets for young regions
  assert(hr->isHumongous() == regions_humongous(), err_msg("Wrong humongous state for region %u and set %s", hr->hrs_index(), name()));
  assert(hr->is_empty() == regions_empty(), err_msg("Wrong empty state for region %u and set %s", hr->hrs_index(), name()));
  assert(hr->rem_set()->verify_ready_for_par_iteration(), err_msg("Wrong iteration state %u", hr->hrs_index()));
}
#endif

void HeapRegionSetBase::verify() {
  // It's important that we also observe the MT safety protocol even
  // for the verification calls. If we do verification without the
  // appropriate locks and the set changes underneath our feet
  // verification might fail and send us on a wild goose chase.
  check_mt_safety();

  guarantee(( is_empty() && length() == 0 && total_capacity_bytes() == 0) ||
            (!is_empty() && length() >= 0 && total_capacity_bytes() >= 0),
            hrs_ext_msg(this, "invariant"));
}

void HeapRegionSetBase::verify_start() {
  // See comment in verify() about MT safety and verification.
  check_mt_safety();
  assert(!_verify_in_progress,
         hrs_ext_msg(this, "verification should not be in progress"));

  // Do the basic verification first before we do the checks over the regions.
  HeapRegionSetBase::verify();

  _verify_in_progress        = true;
}

void HeapRegionSetBase::verify_end() {
  // See comment in verify() about MT safety and verification.
  check_mt_safety();
  assert(_verify_in_progress,
         hrs_ext_msg(this, "verification should be in progress"));

  _verify_in_progress = false;
}

void HeapRegionSetBase::print_on(outputStream* out, bool print_contents) {
  out->cr();
  out->print_cr("Set: %s ("PTR_FORMAT")", name(), this);
  out->print_cr("  Region Assumptions");
  out->print_cr("    humongous         : %s", BOOL_TO_STR(regions_humongous()));
  out->print_cr("    empty             : %s", BOOL_TO_STR(regions_empty()));
  out->print_cr("  Attributes");
  out->print_cr("    length            : %14u", length());
  out->print_cr("    total capacity    : "SIZE_FORMAT_W(14)" bytes",
                total_capacity_bytes());
}

HeapRegionSetBase::HeapRegionSetBase(const char* name, bool humongous, bool empty, HRSMtSafeChecker* mt_safety_checker)
  : _name(name), _verify_in_progress(false),
    _is_humongous(humongous), _is_empty(empty), _mt_safety_checker(mt_safety_checker),
    _count()
{ }

void FreeRegionList::set_unrealistically_long_length(uint len) {
  guarantee(_unrealistically_long_length == 0, "should only be set once");
  _unrealistically_long_length = len;
}

void FreeRegionList::fill_in_ext_msg_extra(hrs_ext_msg* msg) {
  msg->append(" hd: "PTR_FORMAT" tl: "PTR_FORMAT, head(), tail());
}

void FreeRegionList::add_as_head_or_tail(FreeRegionList* from_list, bool as_head) {
  check_mt_safety();
  from_list->check_mt_safety();

  verify_optional();
  from_list->verify_optional();

  if (from_list->is_empty()) {
    return;
  }

#ifdef ASSERT
  FreeRegionListIterator iter(from_list);
  while (iter.more_available()) {
    HeapRegion* hr = iter.get_next();
    // In set_containing_set() we check that we either set the value
    // from NULL to non-NULL or vice versa to catch bugs. So, we have
    // to NULL it first before setting it to the value.
    hr->set_containing_set(NULL);
    hr->set_containing_set(this);
  }
#endif // ASSERT

  if (_head == NULL) {
    assert(length() == 0 && _tail == NULL, hrs_ext_msg(this, "invariant"));
    _head = from_list->_head;
    _tail = from_list->_tail;
  } else {
    assert(length() > 0 && _tail != NULL, hrs_ext_msg(this, "invariant"));
    if (as_head) {
      from_list->_tail->set_next(_head);
      _head = from_list->_head;
    } else {
      _tail->set_next(from_list->_head);
      _tail = from_list->_tail;
    }
  }

  _count.increment(from_list->length(), from_list->total_capacity_bytes());
  from_list->clear();

  verify_optional();
  from_list->verify_optional();
}

void FreeRegionList::add_as_head(FreeRegionList* from_list) {
  add_as_head_or_tail(from_list, true /* as_head */);
}

void FreeRegionList::add_as_tail(FreeRegionList* from_list) {
  add_as_head_or_tail(from_list, false /* as_head */);
}

void FreeRegionList::remove_all() {
  check_mt_safety();
  verify_optional();

  HeapRegion* curr = _head;
  while (curr != NULL) {
    verify_region(curr);

    HeapRegion* next = curr->next();
    curr->set_next(NULL);
    curr->set_containing_set(NULL);
    curr = next;
  }
  clear();

  verify_optional();
}

void FreeRegionList::remove_all_pending(uint target_count) {
  check_mt_safety();
  assert(target_count > 1, hrs_ext_msg(this, "pre-condition"));
  assert(!is_empty(), hrs_ext_msg(this, "pre-condition"));

  verify_optional();
  DEBUG_ONLY(uint old_length = length();)

  HeapRegion* curr = _head;
  HeapRegion* prev = NULL;
  uint count = 0;
  while (curr != NULL) {
    verify_region(curr);
    HeapRegion* next = curr->next();

    if (curr->pending_removal()) {
      assert(count < target_count,
             hrs_err_msg("[%s] should not come across more regions "
                         "pending for removal than target_count: %u",
                         name(), target_count));

      if (prev == NULL) {
        assert(_head == curr, hrs_ext_msg(this, "invariant"));
        _head = next;
      } else {
        assert(_head != curr, hrs_ext_msg(this, "invariant"));
        prev->set_next(next);
      }
      if (next == NULL) {
        assert(_tail == curr, hrs_ext_msg(this, "invariant"));
        _tail = prev;
      } else {
        assert(_tail != curr, hrs_ext_msg(this, "invariant"));
      }

      curr->set_next(NULL);
      remove(curr);
      curr->set_pending_removal(false);

      count += 1;

      // If we have come across the target number of regions we can
      // just bail out. However, for debugging purposes, we can just
      // carry on iterating to make sure there are not more regions
      // tagged with pending removal.
      DEBUG_ONLY(if (count == target_count) break;)
    } else {
      prev = curr;
    }
    curr = next;
  }

  assert(count == target_count,
         hrs_err_msg("[%s] count: %u should be == target_count: %u",
                     name(), count, target_count));
  assert(length() + target_count == old_length,
         hrs_err_msg("[%s] new length should be consistent "
                     "new length: %u old length: %u target_count: %u",
                     name(), length(), old_length, target_count));

  verify_optional();
}

void FreeRegionList::verify() {
  // See comment in HeapRegionSetBase::verify() about MT safety and
  // verification.
  check_mt_safety();

  // This will also do the basic verification too.
  verify_start();

  verify_list();

  verify_end();
}

void FreeRegionList::clear() {
  _count = HeapRegionSetCount();
  _head = NULL;
  _tail = NULL;
}

void FreeRegionList::print_on(outputStream* out, bool print_contents) {
  HeapRegionSetBase::print_on(out, print_contents);
  out->print_cr("  Linking");
  out->print_cr("    head              : "PTR_FORMAT, _head);
  out->print_cr("    tail              : "PTR_FORMAT, _tail);

  if (print_contents) {
    out->print_cr("  Contents");
    FreeRegionListIterator iter(this);
    while (iter.more_available()) {
      HeapRegion* hr = iter.get_next();
      hr->print_on(out);
    }
  }
}
