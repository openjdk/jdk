/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/x/xLock.inline.hpp"
#include "gc/x/xMarkStack.inline.hpp"
#include "gc/x/xMarkStackAllocator.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"

uintptr_t XMarkStackSpaceStart;

XMarkStackSpace::XMarkStackSpace() :
    _expand_lock(),
    _start(0),
    _top(0),
    _end(0) {
  assert(ZMarkStackSpaceLimit >= XMarkStackSpaceExpandSize, "ZMarkStackSpaceLimit too small");

  // Reserve address space
  const size_t size = ZMarkStackSpaceLimit;
  const uintptr_t addr = (uintptr_t)os::reserve_memory(size, !ExecMem, mtGC);
  if (addr == 0) {
    log_error_pd(gc, marking)("Failed to reserve address space for mark stacks");
    return;
  }

  // Successfully initialized
  _start = _top = _end = addr;

  // Register mark stack space start
  XMarkStackSpaceStart = _start;

  // Prime space
  _end += expand_space();
}

bool XMarkStackSpace::is_initialized() const {
  return _start != 0;
}

size_t XMarkStackSpace::size() const {
  return _end - _start;
}

size_t XMarkStackSpace::used() const {
  return _top - _start;
}

size_t XMarkStackSpace::expand_space() {
  const size_t expand_size = XMarkStackSpaceExpandSize;
  const size_t old_size = size();
  const size_t new_size = old_size + expand_size;

  if (new_size > ZMarkStackSpaceLimit) {
    // Expansion limit reached. This is a fatal error since we
    // currently can't recover from running out of mark stack space.
    fatal("Mark stack space exhausted. Use -XX:ZMarkStackSpaceLimit=<size> to increase the "
          "maximum number of bytes allocated for mark stacks. Current limit is " SIZE_FORMAT "M.",
          ZMarkStackSpaceLimit / M);
  }

  log_debug(gc, marking)("Expanding mark stack space: " SIZE_FORMAT "M->" SIZE_FORMAT "M",
                         old_size / M, new_size / M);

  // Expand
  os::commit_memory_or_exit((char*)_end, expand_size, !ExecMem, mtGC, "Mark stack space");

  return expand_size;
}

size_t XMarkStackSpace::shrink_space() {
  // Shrink to what is currently used
  const size_t old_size = size();
  const size_t new_size = align_up(used(), XMarkStackSpaceExpandSize);
  const size_t shrink_size = old_size - new_size;

  if (shrink_size > 0) {
    // Shrink
    log_debug(gc, marking)("Shrinking mark stack space: " SIZE_FORMAT "M->" SIZE_FORMAT "M",
                           old_size / M, new_size / M);

    const uintptr_t shrink_start = _end - shrink_size;
    os::uncommit_memory((char*)shrink_start, shrink_size, !ExecMem, mtGC);
  }

  return shrink_size;
}

uintptr_t XMarkStackSpace::alloc_space(size_t size) {
  uintptr_t top = Atomic::load(&_top);

  for (;;) {
    const uintptr_t end = Atomic::load(&_end);
    const uintptr_t new_top = top + size;
    if (new_top > end) {
      // Not enough space left
      return 0;
    }

    const uintptr_t prev_top = Atomic::cmpxchg(&_top, top, new_top);
    if (prev_top == top) {
      // Success
      return top;
    }

    // Retry
    top = prev_top;
  }
}

uintptr_t XMarkStackSpace::expand_and_alloc_space(size_t size) {
  XLocker<XLock> locker(&_expand_lock);

  // Retry allocation before expanding
  uintptr_t addr = alloc_space(size);
  if (addr != 0) {
    return addr;
  }

  // Expand
  const size_t expand_size = expand_space();

  // Increment top before end to make sure another
  // thread can't steal out newly expanded space.
  addr = Atomic::fetch_then_add(&_top, size);
  Atomic::add(&_end, expand_size);

  return addr;
}

uintptr_t XMarkStackSpace::alloc(size_t size) {
  assert(size <= XMarkStackSpaceExpandSize, "Invalid size");

  const uintptr_t addr = alloc_space(size);
  if (addr != 0) {
    return addr;
  }

  return expand_and_alloc_space(size);
}

void XMarkStackSpace::free() {
  _end -= shrink_space();
  _top = _start;
}

XMarkStackAllocator::XMarkStackAllocator() :
    _freelist(),
    _space() {}

bool XMarkStackAllocator::is_initialized() const {
  return _space.is_initialized();
}

size_t XMarkStackAllocator::size() const {
  return _space.size();
}

XMarkStackMagazine* XMarkStackAllocator::create_magazine_from_space(uintptr_t addr, size_t size) {
  assert(is_aligned(size, XMarkStackSize), "Invalid size");

  // Use first stack as magazine
  XMarkStackMagazine* const magazine = new ((void*)addr) XMarkStackMagazine();
  for (size_t i = XMarkStackSize; i < size; i += XMarkStackSize) {
    XMarkStack* const stack = new ((void*)(addr + i)) XMarkStack();
    const bool success = magazine->push(stack);
    assert(success, "Magazine should never get full");
  }

  return magazine;
}

XMarkStackMagazine* XMarkStackAllocator::alloc_magazine() {
  // Try allocating from the free list first
  XMarkStackMagazine* const magazine = _freelist.pop();
  if (magazine != nullptr) {
    return magazine;
  }

  // Allocate new magazine
  const uintptr_t addr = _space.alloc(XMarkStackMagazineSize);
  if (addr == 0) {
    return nullptr;
  }

  return create_magazine_from_space(addr, XMarkStackMagazineSize);
}

void XMarkStackAllocator::free_magazine(XMarkStackMagazine* magazine) {
  _freelist.push(magazine);
}

void XMarkStackAllocator::free() {
  _freelist.clear();
  _space.free();
}
