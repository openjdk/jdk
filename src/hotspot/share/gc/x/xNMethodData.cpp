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
#include "gc/x/xAttachedArray.inline.hpp"
#include "gc/x/xLock.inline.hpp"
#include "gc/x/xNMethodData.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/growableArray.hpp"

XNMethodDataOops* XNMethodDataOops::create(const GrowableArray<oop*>& immediates, bool has_non_immediates) {
  return ::new (AttachedArray::alloc(immediates.length())) XNMethodDataOops(immediates, has_non_immediates);
}

void XNMethodDataOops::destroy(XNMethodDataOops* oops) {
  AttachedArray::free(oops);
}

XNMethodDataOops::XNMethodDataOops(const GrowableArray<oop*>& immediates, bool has_non_immediates) :
    _immediates(immediates.length()),
    _has_non_immediates(has_non_immediates) {
  // Save all immediate oops
  for (size_t i = 0; i < immediates_count(); i++) {
    immediates_begin()[i] = immediates.at(int(i));
  }
}

size_t XNMethodDataOops::immediates_count() const {
  return _immediates.length();
}

oop** XNMethodDataOops::immediates_begin() const {
  return _immediates(this);
}

oop** XNMethodDataOops::immediates_end() const {
  return immediates_begin() + immediates_count();
}

bool XNMethodDataOops::has_non_immediates() const {
  return _has_non_immediates;
}

XNMethodData::XNMethodData() :
    _lock(),
    _ic_lock(),
    _oops(nullptr) {}

XNMethodData::~XNMethodData() {
  XNMethodDataOops::destroy(_oops);
}

XReentrantLock* XNMethodData::lock() {
  return &_lock;
}

XReentrantLock* XNMethodData::ic_lock() {
  return &_ic_lock;
}

XNMethodDataOops* XNMethodData::oops() const {
  return Atomic::load_acquire(&_oops);
}

XNMethodDataOops* XNMethodData::swap_oops(XNMethodDataOops* new_oops) {
  XLocker<XReentrantLock> locker(&_lock);
  XNMethodDataOops* const old_oops = _oops;
  _oops = new_oops;
  return old_oops;
}
