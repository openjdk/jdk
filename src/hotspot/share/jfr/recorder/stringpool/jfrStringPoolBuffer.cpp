/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/recorder/stringpool/jfrStringPoolBuffer.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/thread.inline.hpp"

JfrStringPoolBuffer::JfrStringPoolBuffer() : JfrBuffer(), _string_count_pos(0), _string_count_top(0) {}

void JfrStringPoolBuffer::reinitialize() {
  concurrent_top();
  set_pos((start()));
  set_string_pos(0);
  set_string_top(0);
  set_concurrent_top(start());
}

uint64_t JfrStringPoolBuffer::string_pos() const {
  return OrderAccess::load_acquire(&_string_count_pos);
}

uint64_t JfrStringPoolBuffer::string_top() const {
  return OrderAccess::load_acquire(&_string_count_top);
}

uint64_t JfrStringPoolBuffer::string_count() const {
  return string_pos() - string_top();
}

void JfrStringPoolBuffer::set_string_pos(uint64_t value) {
  Atomic::store(value, &_string_count_pos);
}

void JfrStringPoolBuffer::increment(uint64_t value) {
#if !(defined(ARM) || defined(IA32))
  Atomic::add(value, &_string_count_pos);
#else
  // TODO: This should be fixed in Atomic::add handling for 32-bit platforms,
  // see JDK-8203283. We workaround the absence of support right here.
  uint64_t cur, val;
  do {
     cur = Atomic::load(&_string_count_top);
     val = cur + value;
  } while (Atomic::cmpxchg(val, &_string_count_pos, cur) != cur);
#endif
}

void JfrStringPoolBuffer::set_string_top(uint64_t value) {
  Atomic::store(value, &_string_count_top);
}
