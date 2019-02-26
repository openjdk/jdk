/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zNMethodAllocator.hpp"
#include "gc/z/zNMethodData.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/growableArray.hpp"

size_t ZNMethodDataOops::header_size() {
  const size_t size = sizeof(ZNMethodDataOops);
  assert(is_aligned(size, sizeof(oop*)), "Header misaligned");
  return size;
}

ZNMethodDataOops* ZNMethodDataOops::create(const GrowableArray<oop*>& immediates, bool has_non_immediates) {
  // Allocate memory for the ZNMethodDataOops object
  // plus the immediate oop* array that follows right after.
  const size_t size = ZNMethodDataOops::header_size() + (sizeof(oop*) * immediates.length());
  void* const mem = ZNMethodAllocator::allocate(size);
  return ::new (mem) ZNMethodDataOops(immediates, has_non_immediates);
}

void ZNMethodDataOops::destroy(ZNMethodDataOops* oops) {
  ZNMethodAllocator::free(oops);
}

ZNMethodDataOops::ZNMethodDataOops(const GrowableArray<oop*>& immediates, bool has_non_immediates) :
    _nimmediates(immediates.length()),
    _has_non_immediates(has_non_immediates) {
  // Save all immediate oops
  for (size_t i = 0; i < _nimmediates; i++) {
    immediates_begin()[i] = immediates.at(i);
  }
}

size_t ZNMethodDataOops::immediates_count() const {
  return _nimmediates;
}

oop** ZNMethodDataOops::immediates_begin() const {
  // The immediate oop* array starts immediately after this object
  return (oop**)((uintptr_t)this + header_size());
}

oop** ZNMethodDataOops::immediates_end() const {
  return immediates_begin() + immediates_count();
}

bool ZNMethodDataOops::has_non_immediates() const {
  return _has_non_immediates;
}

ZNMethodData::ZNMethodData() :
    _lock(),
    _oops(NULL) {}

ZNMethodData::~ZNMethodData() {
  ZNMethodAllocator::free(_oops);
}

ZReentrantLock* ZNMethodData::lock() {
  return &_lock;
}

ZNMethodDataOops* ZNMethodData::oops() const {
  return OrderAccess::load_acquire(&_oops);
}

ZNMethodDataOops* ZNMethodData::swap_oops(ZNMethodDataOops* new_oops) {
  return Atomic::xchg(new_oops, &_oops);
}
