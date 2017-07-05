/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/threadLocalStorage.hpp"
#include "thread_linux.inline.hpp"

// Map stack pointer (%esp) to thread pointer for faster TLS access
//
// Here we use a flat table for better performance. Getting current thread
// is down to one memory access (read _sp_map[%esp>>12]) in generated code
// and two in runtime code (-fPIC code needs an extra load for _sp_map).
//
// This code assumes stack page is not shared by different threads. It works
// in 32-bit VM when page size is 4K (or a multiple of 4K, if that matters).
//
// Notice that _sp_map is allocated in the bss segment, which is ZFOD
// (zero-fill-on-demand). While it reserves 4M address space upfront,
// actual memory pages are committed on demand.
//
// If an application creates and destroys a lot of threads, usually the
// stack space freed by a thread will soon get reused by new thread
// (this is especially true in NPTL or LinuxThreads in fixed-stack mode).
// No memory page in _sp_map is wasted.
//
// However, it's still possible that we might end up populating &
// committing a large fraction of the 4M table over time, but the actual
// amount of live data in the table could be quite small. The max wastage
// is less than 4M bytes. If it becomes an issue, we could use madvise()
// with MADV_DONTNEED to reclaim unused (i.e. all-zero) pages in _sp_map.
// MADV_DONTNEED on Linux keeps the virtual memory mapping, but zaps the
// physical memory page (i.e. similar to MADV_FREE on Solaris).

#ifndef AMD64
Thread* ThreadLocalStorage::_sp_map[1UL << (SP_BITLENGTH - PAGE_SHIFT)];
#endif // !AMD64

void ThreadLocalStorage::generate_code_for_get_thread() {
    // nothing we can do here for user-level thread
}

void ThreadLocalStorage::pd_init() {
#ifndef AMD64
  assert(align_size_down(os::vm_page_size(), PAGE_SIZE) == os::vm_page_size(),
         "page size must be multiple of PAGE_SIZE");
#endif // !AMD64
}

void ThreadLocalStorage::pd_set_thread(Thread* thread) {
  os::thread_local_storage_at_put(ThreadLocalStorage::thread_index(), thread);

#ifndef AMD64
  address stack_top = os::current_stack_base();
  size_t stack_size = os::current_stack_size();

  for (address p = stack_top - stack_size; p < stack_top; p += PAGE_SIZE) {
    // pd_set_thread() is called with non-NULL value when a new thread is
    // created/attached, or with NULL value when a thread is about to exit.
    // If both "thread" and the corresponding _sp_map[] entry are non-NULL,
    // they should have the same value. Otherwise it might indicate that the
    // stack page is shared by multiple threads. However, a more likely cause
    // for this assertion to fail is that an attached thread exited without
    // detaching itself from VM, which is a program error and could cause VM
    // to crash.
    assert(thread == NULL || _sp_map[(uintptr_t)p >> PAGE_SHIFT] == NULL ||
           thread == _sp_map[(uintptr_t)p >> PAGE_SHIFT],
           "thread exited without detaching from VM??");
    _sp_map[(uintptr_t)p >> PAGE_SHIFT] = thread;
  }
#endif // !AMD64
}
