/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_interface/collectedHeap.hpp"
#include "memory/barrierSet.inline.hpp"
#include "memory/universe.hpp"

// count is number of array elements being written
void BarrierSet::static_write_ref_array_pre(HeapWord* start, size_t count) {
  assert(count <= (size_t)max_intx, "count too large");
#if 0
  warning("Pre: \t" INTPTR_FORMAT "[" SIZE_FORMAT "]\t",
                   start,            count);
#endif
  if (UseCompressedOops) {
    Universe::heap()->barrier_set()->write_ref_array_pre((narrowOop*)start, (int)count);
  } else {
    Universe::heap()->barrier_set()->write_ref_array_pre(      (oop*)start, (int)count);
  }
}

// count is number of array elements being written
void BarrierSet::static_write_ref_array_post(HeapWord* start, size_t count) {
  // simply delegate to instance method
  Universe::heap()->barrier_set()->write_ref_array(start, count);
}
