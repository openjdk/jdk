/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 SAP SE. All rights reserved.
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

#include "nmt/mallocHeader.inline.hpp"
#include "nmt/mallocSiteTable.hpp"
#include "nmt/memflags.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/nativeCallStack.hpp"
#include "utilities/ostream.hpp"

// The malloc header, as well as the coming VMATree implementation, rely on MEMFLAGS
// fitting into eight bits.
STATIC_ASSERT(sizeof(MEMFLAGS) == sizeof(uint8_t));

void MallocHeader::print_block_on_error(outputStream* st, address bad_address) const {
  assert(bad_address >= (address)this, "sanity");

  // This function prints block information, including hex dump, in case of a detected
  // corruption. The hex dump should show both block header and corruption site
  // (which may or may not be close together or identical). Plus some surrounding area.
  //
  // Note that we use os::print_hex_dump(), which is able to cope with unmapped
  // memory (it uses SafeFetch).

  st->print_cr("NMT Block at " PTR_FORMAT ", corruption at: " PTR_FORMAT ": ",
               p2i(this), p2i(bad_address));
  static const size_t min_dump_length = 256;
  address from1 = align_down((address)this, sizeof(void*)) - (min_dump_length / 2);
  address to1 = from1 + min_dump_length;
  address from2 = align_down(bad_address, sizeof(void*)) - (min_dump_length / 2);
  address to2 = from2 + min_dump_length;
  if (from2 > to1) {
    // Dump gets too large, split up in two sections.
    os::print_hex_dump(st, from1, to1, 1);
    st->print_cr("...");
    os::print_hex_dump(st, from2, to2, 1);
  } else {
    // print one hex dump
    os::print_hex_dump(st, from1, to2, 1);
  }
}

bool MallocHeader::get_stack(NativeCallStack& stack) const {
  return MallocSiteTable::access_stack(stack, _mst_marker);
}
