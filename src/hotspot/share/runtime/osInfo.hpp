/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_OSINFO_HPP
#define SHARE_RUNTIME_OSINFO_HPP

#include "memory/allStatic.hpp"
#include "utilities/debug.hpp"

// Static information about the operating system. Initialized exactly once
// at VM start-up and never changes again.
class OSInfo : AllStatic {
  static size_t    _vm_page_size;
  static size_t    _vm_allocation_granularity;

public:
  // Returns the byte size of a virtual memory page
  static size_t vm_page_size() { return _vm_page_size; }

  // Returns the size, in bytes, of the granularity with which memory can be reserved using os::reserve_memory().
  static size_t vm_allocation_granularity() { return _vm_allocation_granularity; }

  static void set_vm_page_size(size_t n) {
    assert(_vm_page_size == 0, "init only once");
    _vm_page_size = n;
  }

  static void set_vm_allocation_granularity(size_t n) {
    assert(_vm_allocation_granularity == 0, "init only once");
    _vm_allocation_granularity = n;
  }
};

#endif // SHARE_RUNTIME_OSINFO_HPP
