/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPhysicalMemory.inline.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

ZPage::ZPage(uint8_t type, ZVirtualMemory vmem, ZPhysicalMemory pmem) :
    _type(type),
    _pinned(0),
    _numa_id((uint8_t)-1),
    _seqnum(0),
    _virtual(vmem),
    _top(start()),
    _livemap(object_max_count()),
    _refcount(0),
    _forwarding(),
    _physical(pmem) {
  assert(!_physical.is_null(), "Should not be null");
  assert(!_virtual.is_null(), "Should not be null");
  assert((type == ZPageTypeSmall && size() == ZPageSizeSmall) ||
         (type == ZPageTypeMedium && size() == ZPageSizeMedium) ||
         (type == ZPageTypeLarge && is_aligned(size(), ZGranuleSize)),
         "Page type/size mismatch");
}

ZPage::~ZPage() {
  assert(!is_active(), "Should not be active");
  assert(is_detached(), "Should be detached");
}

void ZPage::reset() {
  assert(!is_active(), "Should not be active");
  assert(!is_pinned(), "Should not be pinned");
  assert(!is_detached(), "Should not be detached");

  _seqnum = ZGlobalSeqNum;
  _top = start();
  _livemap.reset();

  // Make sure we don't make the page active before
  // the reset of the above fields are visible.
  OrderAccess::storestore();

  _refcount = 1;
}

void ZPage::print_on(outputStream* out) const {
  out->print_cr(" %-6s  " PTR_FORMAT " " PTR_FORMAT " " PTR_FORMAT " %s%s%s%s%s%s",
                type_to_string(), start(), top(), end(),
                is_allocating()  ? " Allocating"  : "",
                is_relocatable() ? " Relocatable" : "",
                is_forwarding()  ? " Forwarding"  : "",
                is_pinned()      ? " Pinned"      : "",
                is_detached()    ? " Detached"    : "",
                !is_active()     ? " Inactive"    : "");
}

void ZPage::print() const {
  print_on(tty);
}
