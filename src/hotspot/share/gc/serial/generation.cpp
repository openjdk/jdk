/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/serial/cardTableRS.hpp"
#include "gc/serial/generation.hpp"
#include "gc/serial/serialHeap.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/space.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "utilities/copy.hpp"
#include "utilities/events.hpp"

Generation::Generation(ReservedSpace rs, size_t initial_size) :
  _gc_manager(nullptr) {
  if (!_virtual_space.initialize(rs, initial_size)) {
    vm_exit_during_initialization("Could not reserve enough space for "
                    "object heap");
  }
  // Mangle all of the initial generation.
  if (ZapUnusedHeapArea) {
    MemRegion mangle_region((HeapWord*)_virtual_space.low(),
      (HeapWord*)_virtual_space.high());
    SpaceMangler::mangle_region(mangle_region);
  }
  _reserved = MemRegion((HeapWord*)_virtual_space.low_boundary(),
          (HeapWord*)_virtual_space.high_boundary());
}

size_t Generation::max_capacity() const {
  return reserved().byte_size();
}

void Generation::print() const { print_on(tty); }

void Generation::print_on(outputStream* st)  const {
  st->print(" %-20s", name());
  st->print(" total " SIZE_FORMAT "K, used " SIZE_FORMAT "K",
             capacity()/K, used()/K);
  st->print_cr(" [" PTR_FORMAT ", " PTR_FORMAT ", " PTR_FORMAT ")",
              p2i(_virtual_space.low_boundary()),
              p2i(_virtual_space.high()),
              p2i(_virtual_space.high_boundary()));
}
