/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/collectedHeap.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/memRegion.hpp"
#include "memory/reservedSpace.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/compressedOops.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals.hpp"

// For UseCompressedOops.
address CompressedOops::_base = nullptr;
int CompressedOops::_shift = 0;
bool CompressedOops::_use_implicit_null_checks = true;
MemRegion CompressedOops::_heap_address_range;

// Choose the heap base address and oop encoding mode
// when compressed oops are used:
// Unscaled  - Use 32-bits oops without encoding when
//     NarrowOopHeapBaseMin + heap_size < 4Gb
// ZeroBased - Use zero based compressed oops with encoding when
//     NarrowOopHeapBaseMin + heap_size < 32Gb
// HeapBased - Use compressed oops with heap base + encoding.
void CompressedOops::initialize(const ReservedHeapSpace& heap_space) {
#ifdef _LP64
  // Subtract a page because something can get allocated at heap base.
  // This also makes implicit null checking work, because the
  // memory+1 page below heap_base needs to cause a signal.
  // See needs_explicit_null_check.
  // Only set the heap base for compressed oops because it indicates
  // compressed oops for pstack code.
  if ((uint64_t)heap_space.end() > UnscaledOopHeapMax) {
    // Didn't reserve heap below 4Gb.  Must shift.
    set_shift(LogMinObjAlignmentInBytes);
  }
  if ((uint64_t)heap_space.end() <= OopEncodingHeapMax) {
    // Did reserve heap below 32Gb. Can use base == 0;
    set_base(nullptr);
  } else {
    set_base((address)heap_space.compressed_oop_base());
  }

  _heap_address_range = MemRegion((HeapWord*)heap_space.base(), (HeapWord*)heap_space.end());

  LogTarget(Debug, gc, heap, coops) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    print_mode(&ls);
  }

  // Tell tests in which mode we run.
  Arguments::PropertyList_add(new SystemProperty("java.vm.compressedOopsMode",
                                                 mode_to_string(mode()),
                                                 false));

  // base() is one page below the heap.
  assert((intptr_t)base() <= ((intptr_t)_heap_address_range.start() - (intptr_t)os::vm_page_size()) ||
         base() == nullptr, "invalid value");
  assert(shift() == LogMinObjAlignmentInBytes ||
         shift() == 0, "invalid value");
#endif
}

void CompressedOops::set_base(address base) {
  assert(UseCompressedOops, "no compressed oops?");
  _base = base;
}

void CompressedOops::set_shift(int shift) {
  _shift = shift;
}

void CompressedOops::set_use_implicit_null_checks(bool use) {
  assert(UseCompressedOops, "no compressed ptrs?");
  _use_implicit_null_checks = use;
}

bool CompressedOops::is_in(void* addr) {
  return _heap_address_range.contains(addr);
}

bool CompressedOops::is_in(MemRegion mr) {
  return _heap_address_range.contains(mr);
}

CompressedOops::Mode CompressedOops::mode() {
  if (base_disjoint()) {
    return DisjointBaseNarrowOop;
  }

  if (base() != nullptr) {
    return HeapBasedNarrowOop;
  }

  if (shift() != 0) {
    return ZeroBasedNarrowOop;
  }

  return UnscaledNarrowOop;
}

const char* CompressedOops::mode_to_string(Mode mode) {
  switch (mode) {
    case UnscaledNarrowOop:
      return "32-bit";
    case ZeroBasedNarrowOop:
      return "Zero based";
    case DisjointBaseNarrowOop:
      return "Non-zero disjoint base";
    case HeapBasedNarrowOop:
      return "Non-zero based";
    default:
      ShouldNotReachHere();
      return "";
  }
}

// Test whether bits of addr and possible offsets into the heap overlap.
bool CompressedOops::is_disjoint_heap_base_address(address addr) {
  return (((uint64_t)(intptr_t)addr) &
          (((uint64_t)UCONST64(0xFFFFffffFFFFffff)) >> (32-LogMinObjAlignmentInBytes))) == 0;
}

// Check for disjoint base compressed oops.
bool CompressedOops::base_disjoint() {
  return _base != nullptr && is_disjoint_heap_base_address(_base);
}

// Check for real heapbased compressed oops.
// We must subtract the base as the bits overlap.
// If we negate above function, we also get unscaled and zerobased.
bool CompressedOops::base_overlaps() {
  return _base != nullptr && !is_disjoint_heap_base_address(_base);
}

void CompressedOops::print_mode(outputStream* st) {
  st->print("Heap address: " PTR_FORMAT ", size: %zu MB",
            p2i(_heap_address_range.start()), _heap_address_range.byte_size()/M);

  st->print(", Compressed Oops mode: %s", mode_to_string(mode()));

  if (base() != nullptr) {
    st->print(": " PTR_FORMAT, p2i(base()));
  }

  if (shift() != 0) {
    st->print(", Oop shift amount: %d", shift());
  }

  if (!use_implicit_null_checks()) {
    st->print(", no protected page in front of the heap");
  }
  st->cr();
}
