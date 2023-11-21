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
 */

#include "precompiled.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zNMT.hpp"
#include "gc/z/zVirtualMemory.hpp"
#include "memory/allocation.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/virtualMemoryView.hpp"
#include "utilities/nativeCallStack.hpp"

VirtualMemoryView::PhysicalMemorySpace ZNMT::_space{};

void ZNMT::reserve(zaddress_unsafe start, size_t size) {
  log_info(zgc, nmt)("Reserving " PTR_FORMAT " of size " SIZE_FORMAT, p2i((address)start), size);
  MemTracker::reserve_memory((address)start, size, mtJavaHeap, CALLER_PC);
}
void ZNMT::commit(zoffset offset, size_t size) {
  log_info(zgc, nmt)("Committing " PTR_FORMAT " of size " SIZE_FORMAT, p2i((address)offset), size);
  MemTracker::commit_memory_into_space(ZNMT::_space, (address)offset, size, CALLER_PC);
}
void ZNMT::uncommit(zoffset offset, size_t size) {
  log_info(zgc, nmt)("Uncommitting " PTR_FORMAT " of size " SIZE_FORMAT, p2i((address)untype(offset)), size);
  MemTracker::uncommit_memory_into_space(ZNMT::_space, (address)offset, size);
}
void ZNMT::map(zaddress_unsafe addr, size_t size, zoffset offset) {
  log_info(zgc, nmt)("Mapping " PTR_FORMAT " of size " SIZE_FORMAT " to offset " PTR_FORMAT,
                     p2i((address)addr), size, p2i((address)offset));
  MemTracker::add_view_into_space(ZNMT::_space, (address)addr, size, (address)offset, mtJavaHeap, CALLER_PC);
}
void ZNMT::unmap(zaddress_unsafe addr, size_t size) {
  log_info(zgc, nmt)("Unmapping " PTR_FORMAT " of size " SIZE_FORMAT, p2i((address)addr), size);
  MemTracker::remove_view_into_space(ZNMT::_space, (address)addr, size);
}
void ZNMT::init() {
  _space = MemTracker::register_space("ZGC heap mapping");
}
