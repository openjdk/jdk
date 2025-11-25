/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZNMT_HPP
#define SHARE_GC_Z_ZNMT_HPP

#include "gc/z/zAddress.hpp"
#include "gc/z/zGlobals.hpp"
#include "memory/allStatic.hpp"
#include "nmt/memoryFileTracker.hpp"
#include "nmt/memTracker.hpp"
#include "utilities/globalDefinitions.hpp"

class ZNMT : public AllStatic {
private:
  static MemoryFileTracker::MemoryFile* _device;

public:
  static void initialize();

  static void reserve(zaddress_unsafe start, size_t size);
  static void unreserve(zaddress_unsafe start, size_t size);

  static void commit(zbacking_offset offset, size_t size);
  static void uncommit(zbacking_offset offset, size_t size);

  static void map(zaddress_unsafe addr, size_t size, zbacking_offset offset);
  static void unmap(zaddress_unsafe addr, size_t size);
};

#endif // SHARE_GC_Z_ZNMT_HPP
