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

#ifndef SHARE_GC_Z_ZREMEMBERED_HPP
#define SHARE_GC_Z_ZREMEMBERED_HPP

#include "gc/z/zAddress.hpp"

class OopClosure;
class ZForwarding;
class ZPage;
class ZPageAllocator;
class ZPageTable;
struct ZRememberedSetContaining;
template <typename T> class GrowableArrayView;

class ZRemembered {
  friend class ZRememberedScanForwardingTask;
  friend class ZRememberedScanPageTask;

private:
  ZPageTable* const _page_table;
  ZPageAllocator* const _page_allocator;

  template <typename Function>
  void oops_do_forwarded_via_containing(GrowableArrayView<ZRememberedSetContaining>* array, Function function) const;

  bool should_scan_page(ZPage* page) const;

  void scan_page(ZPage* page) const;
  void scan_forwarding(ZForwarding* forwarding, void* context) const;

public:
  ZRemembered(ZPageTable* page_table, ZPageAllocator* page_allocator);

  // Add to remembered set
  void remember(volatile zpointer* p) const;

  // Scan all remembered sets
  void scan() const;

  // Save the current remembered sets,
  // and switch over to empty remembered sets.
  void flip() const;

  // Scan a remembered set entry
  void scan_field(volatile zpointer* p) const;

  // Verification
  bool is_remembered(volatile zpointer* p) const;
};

#endif // SHARE_GC_Z_ZREMEMBERED_HPP
