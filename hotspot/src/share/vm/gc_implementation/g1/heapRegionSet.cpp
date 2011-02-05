/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/heapRegionSet.inline.hpp"

size_t HeapRegionSetBase::_unrealistically_long_length = 0;

//////////////////// HeapRegionSetBase ////////////////////

void HeapRegionSetBase::set_unrealistically_long_length(size_t len) {
  guarantee(_unrealistically_long_length == 0, "should only be set once");
  _unrealistically_long_length = len;
}

size_t HeapRegionSetBase::calculate_region_num(HeapRegion* hr) {
  assert(hr->startsHumongous(), "pre-condition");
  assert(hr->capacity() % HeapRegion::GrainBytes == 0, "invariant");
  size_t region_num = hr->capacity() >> HeapRegion::LogOfHRGrainBytes;
  assert(region_num > 0, "sanity");
  return region_num;
}

void HeapRegionSetBase::fill_in_ext_msg(hrl_ext_msg* msg, const char* message) {
  msg->append("[%s] %s "
              "ln: "SIZE_FORMAT" rn: "SIZE_FORMAT" "
              "cy: "SIZE_FORMAT" ud: "SIZE_FORMAT,
              name(), message, length(), region_num(),
              total_capacity_bytes(), total_used_bytes());
  fill_in_ext_msg_extra(msg);
}

bool HeapRegionSetBase::verify_region(HeapRegion* hr,
                                  HeapRegionSetBase* expected_containing_set) {
  const char* error_message = NULL;

  if (!regions_humongous()) {
    if (hr->isHumongous()) {
      error_message = "the region should not be humongous";
    }
  } else {
    if (!hr->isHumongous() || !hr->startsHumongous()) {
      error_message = "the region should be 'starts humongous'";
    }
  }

  if (!regions_empty()) {
    if (hr->is_empty()) {
      error_message = "the region should not be empty";
    }
  } else {
    if (!hr->is_empty()) {
      error_message = "the region should be empty";
    }
  }

#ifdef ASSERT
  // The _containing_set field is only available when ASSERT is defined.
  if (hr->containing_set() != expected_containing_set) {
    error_message = "inconsistent containing set found";
  }
#endif // ASSERT

  const char* extra_error_message = verify_region_extra(hr);
  if (extra_error_message != NULL) {
    error_message = extra_error_message;
  }

  if (error_message != NULL) {
    outputStream* out = tty;
    out->cr();
    out->print_cr("## [%s] %s", name(), error_message);
    out->print_cr("## Offending Region: "PTR_FORMAT, hr);
    out->print_cr("   "HR_FORMAT, HR_FORMAT_PARAMS(hr));
#ifdef ASSERT
    out->print_cr("   containing set: "PTR_FORMAT, hr->containing_set());
#endif // ASSERT
    out->print_cr("## Offending Region Set: "PTR_FORMAT, this);
    print_on(out);
    return false;
  } else {
    return true;
  }
}

void HeapRegionSetBase::verify() {
  // It's important that we also observe the MT safety protocol even
  // for the verification calls. If we do verification without the
  // appropriate locks and the set changes underneath our feet
  // verification might fail and send us on a wild goose chase.
  hrl_assert_mt_safety_ok(this);

  guarantee(( is_empty() && length() == 0 && region_num() == 0 &&
              total_used_bytes() == 0 && total_capacity_bytes() == 0) ||
            (!is_empty() && length() >= 0 && region_num() >= 0 &&
              total_used_bytes() >= 0 && total_capacity_bytes() >= 0),
            hrl_ext_msg(this, "invariant"));

  guarantee((!regions_humongous() && region_num() == length()) ||
            ( regions_humongous() && region_num() >= length()),
            hrl_ext_msg(this, "invariant"));

  guarantee(!regions_empty() || total_used_bytes() == 0,
            hrl_ext_msg(this, "invariant"));

  guarantee(total_used_bytes() <= total_capacity_bytes(),
            hrl_ext_msg(this, "invariant"));
}

void HeapRegionSetBase::verify_start() {
  // See comment in verify() about MT safety and verification.
  hrl_assert_mt_safety_ok(this);
  assert(!_verify_in_progress,
         hrl_ext_msg(this, "verification should not be in progress"));

  // Do the basic verification first before we do the checks over the regions.
  HeapRegionSetBase::verify();

  _calc_length               = 0;
  _calc_region_num           = 0;
  _calc_total_capacity_bytes = 0;
  _calc_total_used_bytes     = 0;
  _verify_in_progress        = true;
}

void HeapRegionSetBase::verify_next_region(HeapRegion* hr) {
  // See comment in verify() about MT safety and verification.
  hrl_assert_mt_safety_ok(this);
  assert(_verify_in_progress,
         hrl_ext_msg(this, "verification should be in progress"));

  guarantee(verify_region(hr, this), hrl_ext_msg(this, "region verification"));

  _calc_length               += 1;
  if (!hr->isHumongous()) {
    _calc_region_num         += 1;
  } else {
    _calc_region_num         += calculate_region_num(hr);
  }
  _calc_total_capacity_bytes += hr->capacity();
  _calc_total_used_bytes     += hr->used();
}

void HeapRegionSetBase::verify_end() {
  // See comment in verify() about MT safety and verification.
  hrl_assert_mt_safety_ok(this);
  assert(_verify_in_progress,
         hrl_ext_msg(this, "verification should be in progress"));

  guarantee(length() == _calc_length,
            hrl_err_msg("[%s] length: "SIZE_FORMAT" should be == "
                        "calc length: "SIZE_FORMAT,
                        name(), length(), _calc_length));

  guarantee(region_num() == _calc_region_num,
            hrl_err_msg("[%s] region num: "SIZE_FORMAT" should be == "
                        "calc region num: "SIZE_FORMAT,
                        name(), region_num(), _calc_region_num));

  guarantee(total_capacity_bytes() == _calc_total_capacity_bytes,
            hrl_err_msg("[%s] capacity bytes: "SIZE_FORMAT" should be == "
                        "calc capacity bytes: "SIZE_FORMAT,
                        name(),
                        total_capacity_bytes(), _calc_total_capacity_bytes));

  guarantee(total_used_bytes() == _calc_total_used_bytes,
            hrl_err_msg("[%s] used bytes: "SIZE_FORMAT" should be == "
                        "calc used bytes: "SIZE_FORMAT,
                        name(), total_used_bytes(), _calc_total_used_bytes));

  _verify_in_progress = false;
}

void HeapRegionSetBase::print_on(outputStream* out, bool print_contents) {
  out->cr();
  out->print_cr("Set: %s ("PTR_FORMAT")", name(), this);
  out->print_cr("  Region Assumptions");
  out->print_cr("    humongous         : %s", BOOL_TO_STR(regions_humongous()));
  out->print_cr("    empty             : %s", BOOL_TO_STR(regions_empty()));
  out->print_cr("  Attributes");
  out->print_cr("    length            : "SIZE_FORMAT_W(14), length());
  out->print_cr("    region num        : "SIZE_FORMAT_W(14), region_num());
  out->print_cr("    total capacity    : "SIZE_FORMAT_W(14)" bytes",
                total_capacity_bytes());
  out->print_cr("    total used        : "SIZE_FORMAT_W(14)" bytes",
                total_used_bytes());
}

void HeapRegionSetBase::clear() {
  _length           = 0;
  _region_num       = 0;
  _total_used_bytes = 0;
}

HeapRegionSetBase::HeapRegionSetBase(const char* name)
  : _name(name), _verify_in_progress(false),
    _calc_length(0), _calc_region_num(0),
    _calc_total_capacity_bytes(0), _calc_total_used_bytes(0) { }

//////////////////// HeapRegionSet ////////////////////

void HeapRegionSet::update_from_proxy(HeapRegionSet* proxy_set) {
  hrl_assert_mt_safety_ok(this);
  hrl_assert_mt_safety_ok(proxy_set);
  hrl_assert_sets_match(this, proxy_set);

  verify_optional();
  proxy_set->verify_optional();

  if (proxy_set->is_empty()) return;

  assert(proxy_set->length() <= _length,
         hrl_err_msg("[%s] proxy set length: "SIZE_FORMAT" "
                     "should be <= length: "SIZE_FORMAT,
                     name(), proxy_set->length(), _length));
  _length -= proxy_set->length();

  assert(proxy_set->region_num() <= _region_num,
         hrl_err_msg("[%s] proxy set region num: "SIZE_FORMAT" "
                     "should be <= region num: "SIZE_FORMAT,
                     name(), proxy_set->region_num(), _region_num));
  _region_num -= proxy_set->region_num();

  assert(proxy_set->total_used_bytes() <= _total_used_bytes,
         hrl_err_msg("[%s] proxy set used bytes: "SIZE_FORMAT" "
                     "should be <= used bytes: "SIZE_FORMAT,
                     name(), proxy_set->total_used_bytes(),
                     _total_used_bytes));
  _total_used_bytes -= proxy_set->total_used_bytes();

  proxy_set->clear();

  verify_optional();
  proxy_set->verify_optional();
}

//////////////////// HeapRegionLinkedList ////////////////////

void HeapRegionLinkedList::fill_in_ext_msg_extra(hrl_ext_msg* msg) {
  msg->append(" hd: "PTR_FORMAT" tl: "PTR_FORMAT, head(), tail());
}

void HeapRegionLinkedList::add_as_tail(HeapRegionLinkedList* from_list) {
  hrl_assert_mt_safety_ok(this);
  hrl_assert_mt_safety_ok(from_list);

  verify_optional();
  from_list->verify_optional();

  if (from_list->is_empty()) return;

#ifdef ASSERT
  HeapRegionLinkedListIterator iter(from_list);
  while (iter.more_available()) {
    HeapRegion* hr = iter.get_next();
    // In set_containing_set() we check that we either set the value
    // from NULL to non-NULL or vice versa to catch bugs. So, we have
    // to NULL it first before setting it to the value.
    hr->set_containing_set(NULL);
    hr->set_containing_set(this);
  }
#endif // ASSERT

  if (_tail != NULL) {
    assert(length() >  0 && _head != NULL, hrl_ext_msg(this, "invariant"));
    _tail->set_next(from_list->_head);
  } else {
    assert(length() == 0 && _head == NULL, hrl_ext_msg(this, "invariant"));
    _head = from_list->_head;
  }
  _tail = from_list->_tail;

  _length           += from_list->length();
  _region_num       += from_list->region_num();
  _total_used_bytes += from_list->total_used_bytes();
  from_list->clear();

  verify_optional();
  from_list->verify_optional();
}

void HeapRegionLinkedList::remove_all() {
  hrl_assert_mt_safety_ok(this);
  verify_optional();

  HeapRegion* curr = _head;
  while (curr != NULL) {
    hrl_assert_region_ok(this, curr, this);

    HeapRegion* next = curr->next();
    curr->set_next(NULL);
    curr->set_containing_set(NULL);
    curr = next;
  }
  clear();

  verify_optional();
}

void HeapRegionLinkedList::remove_all_pending(size_t target_count) {
  hrl_assert_mt_safety_ok(this);
  assert(target_count > 1, hrl_ext_msg(this, "pre-condition"));
  assert(!is_empty(), hrl_ext_msg(this, "pre-condition"));

  verify_optional();
  DEBUG_ONLY(size_t old_length = length();)

  HeapRegion* curr = _head;
  HeapRegion* prev = NULL;
  size_t count = 0;
  while (curr != NULL) {
    hrl_assert_region_ok(this, curr, this);
    HeapRegion* next = curr->next();

    if (curr->pending_removal()) {
      assert(count < target_count,
             hrl_err_msg("[%s] should not come across more regions "
                         "pending for removal than target_count: "SIZE_FORMAT,
                         name(), target_count));

      if (prev == NULL) {
        assert(_head == curr, hrl_ext_msg(this, "invariant"));
        _head = next;
      } else {
        assert(_head != curr, hrl_ext_msg(this, "invariant"));
        prev->set_next(next);
      }
      if (next == NULL) {
        assert(_tail == curr, hrl_ext_msg(this, "invariant"));
        _tail = prev;
      } else {
        assert(_tail != curr, hrl_ext_msg(this, "invariant"));
      }

      curr->set_next(NULL);
      remove_internal(curr);
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
         hrl_err_msg("[%s] count: "SIZE_FORMAT" should be == "
                     "target_count: "SIZE_FORMAT, name(), count, target_count));
  assert(length() + target_count == old_length,
         hrl_err_msg("[%s] new length should be consistent "
                     "new length: "SIZE_FORMAT" old length: "SIZE_FORMAT" "
                     "target_count: "SIZE_FORMAT,
                     name(), length(), old_length, target_count));

  verify_optional();
}

void HeapRegionLinkedList::verify() {
  // See comment in HeapRegionSetBase::verify() about MT safety and
  // verification.
  hrl_assert_mt_safety_ok(this);

  // This will also do the basic verification too.
  verify_start();

  HeapRegion* curr  = _head;
  HeapRegion* prev1 = NULL;
  HeapRegion* prev0 = NULL;
  size_t      count = 0;
  while (curr != NULL) {
    verify_next_region(curr);

    count += 1;
    guarantee(count < _unrealistically_long_length,
              hrl_err_msg("[%s] the calculated length: "SIZE_FORMAT" "
                          "seems very long, is there maybe a cycle? "
                          "curr: "PTR_FORMAT" prev0: "PTR_FORMAT" "
                          "prev1: "PTR_FORMAT" length: "SIZE_FORMAT,
                          name(), count, curr, prev0, prev1, length()));

    prev1 = prev0;
    prev0 = curr;
    curr  = curr->next();
  }

  guarantee(_tail == prev0, hrl_ext_msg(this, "post-condition"));

  verify_end();
}

void HeapRegionLinkedList::clear() {
  HeapRegionSetBase::clear();
  _head = NULL;
  _tail = NULL;
}

void HeapRegionLinkedList::print_on(outputStream* out, bool print_contents) {
  HeapRegionSetBase::print_on(out, print_contents);
  out->print_cr("  Linking");
  out->print_cr("    head              : "PTR_FORMAT, _head);
  out->print_cr("    tail              : "PTR_FORMAT, _tail);

  if (print_contents) {
    out->print_cr("  Contents");
    HeapRegionLinkedListIterator iter(this);
    while (iter.more_available()) {
      HeapRegion* hr = iter.get_next();
      hr->print_on(out);
    }
  }
}
