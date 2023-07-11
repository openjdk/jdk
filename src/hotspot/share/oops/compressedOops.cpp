/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/memRegion.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "memory/virtualspace.hpp"
#include "oops/compressedOops.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals.hpp"

// For UseCompressedOops.
NarrowPtrStruct CompressedOops::_narrow_oop = { nullptr, 0, true };
MemRegion       CompressedOops::_heap_address_range;

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
    set_base(0);
  } else {
    set_base((address)heap_space.compressed_oop_base());
  }

  _heap_address_range = heap_space.region();

  LogTarget(Debug, gc, heap, coops) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
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
  _narrow_oop._base    = base;
}

void CompressedOops::set_shift(int shift) {
  _narrow_oop._shift   = shift;
}

void CompressedOops::set_use_implicit_null_checks(bool use) {
  assert(UseCompressedOops, "no compressed ptrs?");
  _narrow_oop._use_implicit_null_checks   = use;
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

  if (base() != 0) {
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
  return _narrow_oop._base != nullptr && is_disjoint_heap_base_address(_narrow_oop._base);
}

// Check for real heapbased compressed oops.
// We must subtract the base as the bits overlap.
// If we negate above function, we also get unscaled and zerobased.
bool CompressedOops::base_overlaps() {
  return _narrow_oop._base != nullptr && !is_disjoint_heap_base_address(_narrow_oop._base);
}

void CompressedOops::print_mode(outputStream* st) {
  st->print("Heap address: " PTR_FORMAT ", size: " SIZE_FORMAT " MB",
            p2i(_heap_address_range.start()), _heap_address_range.byte_size()/M);

  st->print(", Compressed Oops mode: %s", mode_to_string(mode()));

  if (base() != 0) {
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

// For UseCompressedClassPointers.
NarrowPtrStruct CompressedKlassPointers::_narrow_klass = { nullptr, 0, true };

// CompressedClassSpaceSize set to 1GB, but appear 3GB away from _narrow_ptrs_base during CDS dump.
// (Todo: we should #ifdef out CompressedKlassPointers for 32bit completely and fix all call sites which
//  are compiled for 32bit to LP64_ONLY).
size_t CompressedKlassPointers::_range = 0;

#ifdef _LP64

// Given a klass range [addr, addr+len) and a given encoding scheme, assert that this scheme covers the range, then
// set this encoding scheme. Used by CDS at runtime to re-instate the scheme used to pre-compute klass ids for
// archived heap objects.
void CompressedKlassPointers::initialize_for_given_encoding(address addr, size_t len, address requested_base, int requested_shift) {
  assert(is_valid_base(requested_base), "Address must be a valid encoding base");
  address const end = addr + len;

  const int narrow_klasspointer_bits = sizeof(narrowKlass) * 8;
  const size_t encoding_range_size = nth_bit(narrow_klasspointer_bits + requested_shift);
  address encoding_range_end = requested_base + encoding_range_size;

  // Note: it would be technically valid for the encoding base to precede the start of the Klass range. But we only call
  // this function from CDS, and therefore know this to be true.
  assert(requested_base == addr, "Invalid requested base");
  assert(encoding_range_end >= end, "Encoding does not cover the full Klass range");

  set_base(requested_base);
  set_shift(requested_shift);
  set_range(encoding_range_size);
}

// Given an address range [addr, addr+len) which the encoding is supposed to
//  cover, choose base, shift and range.
//  The address range is the expected range of uncompressed Klass pointers we
//  will encounter (and the implicit promise that there will be no Klass
//  structures outside this range).
void CompressedKlassPointers::initialize(address addr, size_t len) {
  assert(is_valid_base(addr), "Address must be a valid encoding base");
  address const end = addr + len;

  address base;
  int shift;
  size_t range;

  // Attempt to run with encoding base == zero
  if (end <= (address)KlassEncodingMetaspaceMax) {
    base = 0;
  } else {
    base = addr;
  }

  // Highest offset a Klass* can ever have in relation to base.
  range = end - base;

  // We may not even need a shift if the range fits into 32bit:
  const uint64_t UnscaledClassSpaceMax = (uint64_t(max_juint) + 1);
  if (range < UnscaledClassSpaceMax) {
    shift = 0;
  } else {
    shift = LogKlassAlignmentInBytes;
  }

  set_base(base);
  set_shift(shift);
  set_range(range);
}

// Given an address p, return true if p can be used as an encoding base.
//  (Some platforms have restrictions of what constitutes a valid base address).
bool CompressedKlassPointers::is_valid_base(address p) {
#ifdef AARCH64
  // Below 32G, base must be aligned to 4G.
  // Above that point, base must be aligned to 32G
  if (p < (address)(32 * G)) {
    return is_aligned(p, 4 * G);
  }
  return is_aligned(p, (4 << LogKlassAlignmentInBytes) * G);
#else
  return true;
#endif
}

void CompressedKlassPointers::print_mode(outputStream* st) {
  st->print_cr("Narrow klass base: " PTR_FORMAT ", Narrow klass shift: %d, "
               "Narrow klass range: " SIZE_FORMAT_X, p2i(base()), shift(),
               range());
}

void CompressedKlassPointers::set_base(address base) {
  assert(UseCompressedClassPointers, "no compressed klass ptrs?");
  _narrow_klass._base   = base;
}

void CompressedKlassPointers::set_shift(int shift)       {
  assert(shift == 0 || shift == LogKlassAlignmentInBytes, "invalid shift for klass ptrs");
  _narrow_klass._shift   = shift;
}

void CompressedKlassPointers::set_range(size_t range) {
  assert(UseCompressedClassPointers, "no compressed klass ptrs?");
  _range = range;
}

#endif // _LP64
