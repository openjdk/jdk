/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zNMT.hpp"
#include "gc/z/zVirtualMemory.hpp"
#include "nmt/memTag.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/memoryFileTracker.hpp"
#include "utilities/nativeCallStack.hpp"

MemoryFileTracker::MemoryFile* ZNMT::_device = nullptr;

void ZNMT::initialize() {
  _device = MemTracker::register_file("ZGC heap backing file");
}

void ZNMT::reserve(zaddress_unsafe start, size_t size) {
  MemTracker::record_virtual_memory_reserve((address)untype(start), size, CALLER_PC, mtJavaHeap);
}

void ZNMT::commit(zoffset offset, size_t size) {
  MemTracker::allocate_memory_in(ZNMT::_device, untype(offset), size, CALLER_PC, mtJavaHeap);
}

void ZNMT::uncommit(zoffset offset, size_t size) {
  MemTracker::free_memory_in(ZNMT::_device, untype(offset), size);
}

void ZNMT::map(zaddress_unsafe addr, size_t size, zoffset offset) {
  // NMT doesn't track mappings at the moment.
}

void ZNMT::unmap(zaddress_unsafe addr, size_t size) {
  // NMT doesn't track mappings at the moment.
}
