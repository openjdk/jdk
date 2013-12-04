/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.hpp"
#include "utilities/ticks.inline.hpp"

#ifdef ASSERT
 const jlong Ticks::invalid_time_stamp = -2; // 0xFFFF FFFF`FFFF FFFE
#endif

void Ticks::stamp() {
  _stamp_ticks = os::elapsed_counter();
}

const Ticks Ticks::now() {
  Ticks t;
  t.stamp();
  return t;
}

Tickspan::Tickspan(const Ticks& end, const Ticks& start) {
  assert(end.value() != Ticks::invalid_time_stamp, "end is unstamped!");
  assert(start.value() != Ticks::invalid_time_stamp, "start is unstamped!");

  assert(end >= start, "negative time!");

  _span_ticks = end.value() - start.value();
}

template <typename ReturnType>
static ReturnType time_conversion(const Tickspan& span, TicksToTimeHelper::Unit unit) {
  assert(TicksToTimeHelper::SECONDS == unit ||
         TicksToTimeHelper::MILLISECONDS == unit, "invalid unit!");

  ReturnType frequency_per_unit = (ReturnType)os::elapsed_frequency() / (ReturnType)unit;

  return (ReturnType) ((ReturnType)span.value() / frequency_per_unit);
}

double TicksToTimeHelper::seconds(const Tickspan& span) {
  return time_conversion<double>(span, SECONDS);
}

jlong TicksToTimeHelper::milliseconds(const Tickspan& span) {
  return time_conversion<jlong>(span, MILLISECONDS);
}
