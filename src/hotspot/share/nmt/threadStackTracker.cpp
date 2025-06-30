/*
 * Copyright (c) 2019, 2024, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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


#include "nmt/memTracker.hpp"
#include "nmt/threadStackTracker.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

volatile size_t ThreadStackTracker::_thread_count = 0;

static void align_thread_stack_boundaries_inward(void*& base, size_t& size) {
  // Thread stack boundaries don't have to be aligned to page boundaries. For cases where they
  // are not aligned (e.g. AIX, Alpine), this function corrects boundaries inward to the next
  // page boundaries. This ensures that we can track thread stacks piggybacking on the virtual
  // memory tracker.
  void* const base_aligned = align_up(base, os::vm_page_size());
  const size_t size_aligned = align_down(size, os::vm_page_size());
  assert(size_aligned > 0, "stack size less than a page?");
  base = base_aligned;
  size = size_aligned;
}

void ThreadStackTracker::new_thread_stack(void* base, size_t size, const NativeCallStack& stack) {
  assert(MemTracker::enabled(), "Must be");
  assert(base != nullptr, "Should have been filtered");
  align_thread_stack_boundaries_inward(base, size);

  MemTracker::NmtVirtualMemoryLocker nvml;
  VirtualMemoryTracker::Instance::add_reserved_region((address)base, size, stack, mtThreadStack);
  _thread_count++;
}

void ThreadStackTracker::delete_thread_stack(void* base, size_t size) {
  assert(MemTracker::enabled(), "Must be");
  assert(base != nullptr, "Should have been filtered");
  align_thread_stack_boundaries_inward(base, size);

  MemTracker::NmtVirtualMemoryLocker nvml;
  MemTracker::record_virtual_memory_release((address)base, size);
  _thread_count--;
}

