/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XFORWARDINGTABLE_INLINE_HPP
#define SHARE_GC_X_XFORWARDINGTABLE_INLINE_HPP

#include "gc/x/xForwardingTable.hpp"

#include "gc/x/xAddress.inline.hpp"
#include "gc/x/xForwarding.inline.hpp"
#include "gc/x/xGlobals.hpp"
#include "gc/x/xGranuleMap.inline.hpp"
#include "utilities/debug.hpp"

inline XForwardingTable::XForwardingTable() :
    _map(XAddressOffsetMax) {}

inline XForwarding* XForwardingTable::get(uintptr_t addr) const {
  assert(!XAddress::is_null(addr), "Invalid address");
  return _map.get(XAddress::offset(addr));
}

inline void XForwardingTable::insert(XForwarding* forwarding) {
  const uintptr_t offset = forwarding->start();
  const size_t size = forwarding->size();

  assert(_map.get(offset) == NULL, "Invalid entry");
  _map.put(offset, size, forwarding);
}

inline void XForwardingTable::remove(XForwarding* forwarding) {
  const uintptr_t offset = forwarding->start();
  const size_t size = forwarding->size();

  assert(_map.get(offset) == forwarding, "Invalid entry");
  _map.put(offset, size, NULL);
}

#endif // SHARE_GC_X_XFORWARDINGTABLE_INLINE_HPP
