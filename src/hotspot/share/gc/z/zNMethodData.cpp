/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zNMethodData.hpp"
#include "utilities/debug.hpp"

ZNMethodData::ZNMethodData()
  : _lock(),
    _ic_lock(),
    _barriers(),
    _immediate_oops(),
    _has_non_immediate_oops(false) {}

ZReentrantLock* ZNMethodData::lock() {
  return &_lock;
}

ZReentrantLock* ZNMethodData::ic_lock() {
  return &_ic_lock;
}

const ZArray<ZNMethodDataBarrier>* ZNMethodData::barriers() const {
  assert(_lock.is_owned(), "Should be owned");
  return &_barriers;
}

const ZArray<oop*>* ZNMethodData::immediate_oops() const {
  assert(_lock.is_owned(), "Should be owned");
  return &_immediate_oops;
}

bool ZNMethodData::has_non_immediate_oops() const {
  assert(_lock.is_owned(), "Should be owned");
  return _has_non_immediate_oops;
}

void ZNMethodData::swap(ZArray<ZNMethodDataBarrier>* barriers,
                        ZArray<oop*>* immediate_oops,
                        bool has_non_immediate_oops) {
  ZLocker<ZReentrantLock> locker(&_lock);
  _barriers.swap(barriers);
  _immediate_oops.swap(immediate_oops);
  _has_non_immediate_oops = has_non_immediate_oops;
}
