/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZREMEMBER_HPP
#define SHARE_GC_Z_ZREMEMBER_HPP

#include "gc/z/zAddress.hpp"

class OopClosure;
class ZForwarding;
class ZPageTable;
class ZRememberedSetContaining;
template <typename T> class GrowableArrayView;

class ZRemember {
  friend class ZRememberScanForwardingTask;
  friend class ZRememberScanPageTask;

private:
  ZPageTable* const _page_table;
  ZPageAllocator* const _page_allocator;

  template <typename Function>
  void oops_do_forwarded(ZForwarding* forwarding, Function function) const;

  template <typename Function>
  void oops_do_forwarded_via_containing(GrowableArrayView<ZRememberedSetContaining>* array, Function function) const;

  void scan_page(ZPage* page) const;
  void scan_forwarding(ZForwarding* forwarding, void* context) const;

public:
  ZRemember(ZPageTable* page_table, ZPageAllocator* page_allocator);

  void remember(volatile zpointer* p) const;
  void remember_fields(zaddress obj) const;

  // Global flip of the current active set of remset bitmaps
  void flip() const;

  void scan() const;

  void mark_and_remember(volatile zpointer* p) const;

  static bool should_scan(ZPage* page);

  // Verification
  bool is_remembered(volatile zpointer* p) const;
};

#endif // SHARE_GC_Z_ZREMEMBER_HPP
