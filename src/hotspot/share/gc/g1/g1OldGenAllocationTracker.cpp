/*
 * Copyright (c) 2020, Amazon.com, Inc. or its affiliates. All rights reserved.
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
#include "gc/g1/g1OldGenAllocationTracker.hpp"

G1OldGenAllocationTracker::G1OldGenAllocationTracker() :
  _last_cycle_old_bytes(0),
  _last_cycle_duration(0.0),
  _allocated_bytes_since_last_gc(0) {
}

void G1OldGenAllocationTracker::reset_after_full_gc() {
  _last_cycle_duration = 0;
  reset_cycle_after_gc();
}

void G1OldGenAllocationTracker::reset_after_young_gc(double allocation_duration_s) {
  _last_cycle_duration = allocation_duration_s;
  reset_cycle_after_gc();
}