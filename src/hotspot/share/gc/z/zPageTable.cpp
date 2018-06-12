/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zPageTable.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/debug.hpp"

ZPageTable::ZPageTable() :
    _map() {}

ZPageTableEntry ZPageTable::get_entry(ZPage* page) const {
  const uintptr_t addr = ZAddress::good(page->start());
  return _map.get(addr);
}

void ZPageTable::put_entry(ZPage* page, ZPageTableEntry entry) {
  // Make sure a newly created page is globally visible before
  // updating the pagetable.
  OrderAccess::storestore();

  const uintptr_t start = ZAddress::good(page->start());
  const uintptr_t end = start + page->size();
  for (uintptr_t addr = start; addr < end; addr += ZPageSizeMin) {
    _map.put(addr, entry);
  }
}

void ZPageTable::insert(ZPage* page) {
  assert(get_entry(page).page() == NULL ||
         get_entry(page).page() == page, "Invalid entry");

  // Cached pages stays in the pagetable and we must not re-insert
  // those when they get re-allocated because they might also be
  // relocating and we don't want to clear their relocating bit.
  if (get_entry(page).page() == NULL) {
    ZPageTableEntry entry(page, false /* relocating */);
    put_entry(page, entry);
  }

  assert(get_entry(page).page() == page, "Invalid entry");
}

void ZPageTable::remove(ZPage* page) {
  assert(get_entry(page).page() == page, "Invalid entry");

  ZPageTableEntry entry;
  put_entry(page, entry);

  assert(get_entry(page).page() == NULL, "Invalid entry");
}

void ZPageTable::set_relocating(ZPage* page) {
  assert(get_entry(page).page() == page, "Invalid entry");
  assert(!get_entry(page).relocating(), "Invalid entry");

  ZPageTableEntry entry(page, true /* relocating */);
  put_entry(page, entry);

  assert(get_entry(page).page() == page, "Invalid entry");
  assert(get_entry(page).relocating(), "Invalid entry");
}

void ZPageTable::clear_relocating(ZPage* page) {
  assert(get_entry(page).page() == page, "Invalid entry");
  assert(get_entry(page).relocating(), "Invalid entry");

  ZPageTableEntry entry(page, false /* relocating */);
  put_entry(page, entry);

  assert(get_entry(page).page() == page, "Invalid entry");
  assert(!get_entry(page).relocating(), "Invalid entry");
}
