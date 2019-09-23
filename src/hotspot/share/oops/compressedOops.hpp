/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_COMPRESSEDOOPS_HPP
#define SHARE_OOPS_COMPRESSEDOOPS_HPP

#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/globalDefinitions.hpp"

class outputStream;
class ReservedHeapSpace;

struct NarrowPtrStruct {
  // Base address for oop-within-java-object materialization.
  // NULL if using wide oops or zero based narrow oops.
  address _base;
  // Number of shift bits for encoding/decoding narrow ptrs.
  // 0 if using wide ptrs or zero based unscaled narrow ptrs,
  // LogMinObjAlignmentInBytes/LogKlassAlignmentInBytes otherwise.
  int     _shift;
  // Generate code with implicit null checks for narrow ptrs.
  bool    _use_implicit_null_checks;
};

class CompressedOops : public AllStatic {
  friend class VMStructs;

  // For UseCompressedOops.
  static NarrowPtrStruct _narrow_oop;

  // The address range of the heap
  static MemRegion _heap_address_range;

public:
  // For UseCompressedOops
  // Narrow Oop encoding mode:
  // 0 - Use 32-bits oops without encoding when
  //     NarrowOopHeapBaseMin + heap_size < 4Gb
  // 1 - Use zero based compressed oops with encoding when
  //     NarrowOopHeapBaseMin + heap_size < 32Gb
  // 2 - Use compressed oops with disjoint heap base if
  //     base is 32G-aligned and base > 0. This allows certain
  //     optimizations in encoding/decoding.
  //     Disjoint: Bits used in base are disjoint from bits used
  //     for oops ==> oop = (cOop << 3) | base.  One can disjoint
  //     the bits of an oop into base and compressed oop.
  // 3 - Use compressed oops with heap base + encoding.
  enum Mode {
    UnscaledNarrowOop  = 0,
    ZeroBasedNarrowOop = 1,
    DisjointBaseNarrowOop = 2,
    HeapBasedNarrowOop = 3,
    AnyNarrowOopMode = 4
  };

  static void initialize(const ReservedHeapSpace& heap_space);

  static void set_base(address base);
  static void set_shift(int shift);
  static void set_use_implicit_null_checks(bool use);

  static address  base()                     { return _narrow_oop._base; }
  static address  begin()                    { return (address)_heap_address_range.start(); }
  static address  end()                      { return (address)_heap_address_range.end(); }
  static bool     is_base(void* addr)        { return (base() == (address)addr); }
  static int      shift()                    { return _narrow_oop._shift; }
  static bool     use_implicit_null_checks() { return _narrow_oop._use_implicit_null_checks; }

  static address* ptrs_base_addr()           { return &_narrow_oop._base; }
  static address  ptrs_base()                { return _narrow_oop._base; }

  static bool is_in(void* addr);
  static bool is_in(MemRegion mr);

  static Mode mode();
  static const char* mode_to_string(Mode mode);

  // Test whether bits of addr and possible offsets into the heap overlap.
  static bool     is_disjoint_heap_base_address(address addr);

  // Check for disjoint base compressed oops.
  static bool     base_disjoint();

  // Check for real heapbased compressed oops.
  // We must subtract the base as the bits overlap.
  // If we negate above function, we also get unscaled and zerobased.
  static bool     base_overlaps();

  static void     print_mode(outputStream* st);

  static bool is_null(oop v)       { return v == NULL; }
  static bool is_null(narrowOop v) { return v == 0; }

  static inline oop decode_raw(narrowOop v);
  static inline oop decode_not_null(narrowOop v);
  static inline oop decode(narrowOop v);
  static inline narrowOop encode_not_null(oop v);
  static inline narrowOop encode(oop v);

  // No conversions needed for these overloads
  static oop decode_not_null(oop v)             { return v; }
  static oop decode(oop v)                      { return v; }
  static narrowOop encode_not_null(narrowOop v) { return v; }
  static narrowOop encode(narrowOop v)          { return v; }
};

// For UseCompressedClassPointers.
class CompressedKlassPointers : public AllStatic {
  friend class VMStructs;

  static NarrowPtrStruct _narrow_klass;

  // CompressedClassSpaceSize set to 1GB, but appear 3GB away from _narrow_ptrs_base during CDS dump.
  static uint64_t _narrow_klass_range;

public:
  static void set_base(address base);
  static void set_shift(int shift);
  static void set_range(uint64_t range);

  static address  base()               { return  _narrow_klass._base; }
  static uint64_t range()              { return  _narrow_klass_range; }
  static int      shift()              { return  _narrow_klass._shift; }

  static bool is_null(Klass* v)      { return v == NULL; }
  static bool is_null(narrowKlass v) { return v == 0; }

  static inline Klass* decode_raw(narrowKlass v);
  static inline Klass* decode_not_null(narrowKlass v);
  static inline Klass* decode(narrowKlass v);
  static inline narrowKlass encode_not_null(Klass* v);
  static inline narrowKlass encode(Klass* v);
};

#endif // SHARE_OOPS_COMPRESSEDOOPS_HPP
