/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allStatic.hpp"
#include "memory/memRegion.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/globalDefinitions.hpp"

#include <type_traits>

class outputStream;
class ReservedHeapSpace;

class CompressedOops : public AllStatic {
  friend class VMStructs;

  // Base address for oop-within-java-object materialization.
  // null if using wide oops or zero based narrow oops.
  static address _base;
  // Number of shift bits for encoding/decoding narrow ptrs.
  // 0 if using wide oops or zero based unscaled narrow oops,
  // LogMinObjAlignmentInBytes otherwise.
  static int _shift;
  // Generate code with implicit null checks for narrow oops.
  static bool _use_implicit_null_checks;

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
    HeapBasedNarrowOop = 3
  };

  // The representation type for narrowOop is assumed to be uint32_t.
  static_assert(std::is_same<uint32_t, std::underlying_type_t<narrowOop>>::value,
                "narrowOop has unexpected representation type");

  static void initialize(const ReservedHeapSpace& heap_space);

  static void set_base(address base);
  static void set_shift(int shift);
  static void set_use_implicit_null_checks(bool use);

  static address  base()                     { return _base; }
  static address  base_addr()                { return (address)&_base; }
  static address  begin()                    { return (address)_heap_address_range.start(); }
  static address  end()                      { return (address)_heap_address_range.end(); }
  static bool     is_base(void* addr)        { return (base() == (address)addr); }
  static int      shift()                    { return _shift; }
  static bool     use_implicit_null_checks() { return _use_implicit_null_checks; }

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

  static bool is_null(oop v)       { return v == nullptr; }
  static bool is_null(narrowOop v) { return v == narrowOop::null; }

  static inline oop decode_raw_not_null(narrowOop v);
  static inline oop decode_raw(narrowOop v);
  static inline oop decode_not_null(narrowOop v);
  static inline oop decode(narrowOop v);
  static inline narrowOop encode_not_null(oop v);
  static inline narrowOop encode(oop v);

  // No conversions needed for these overloads
  static inline oop decode_raw_not_null(oop v);
  static inline oop decode_not_null(oop v);
  static inline oop decode(oop v);
  static inline narrowOop encode_not_null(narrowOop v);
  static inline narrowOop encode(narrowOop v);

  static inline uint32_t narrow_oop_value(oop o);
  static inline uint32_t narrow_oop_value(narrowOop o);

  template<typename T>
  static inline narrowOop narrow_oop_cast(T i);
};

#endif // SHARE_OOPS_COMPRESSEDOOPS_HPP
