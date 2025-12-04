/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHALLOCREQUEST_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHALLOCREQUEST_HPP

#include "gc/shenandoah/shenandoahAffiliation.hpp"
#include "memory/allocation.hpp"

class ShenandoahAllocRequest : StackObj {
public:
  // Alloc type is an int value with encoded bits in scheme as:
  // [x|xx|xx|xx]
  //          ^---- Requester:
  //                  00 -- mutator
  //                  10 -- mutator (CDS)
  //                  01 -- GC
  //       ^------- Purpose:
  //                  00 -- shared
  //                  01 -- TLAB/GCLAB
  //                  11 -- PLAB
  //    ^---------- Affiliation:
  //                  00 -- YOUNG
  //                  01 -- OLD
  //                  11 -- OLD, promotion
  typedef int Type;

  static constexpr int bit_gc_alloc         = 1 << 0;
  static constexpr int bit_cds_alloc        = 1 << 1;
  static constexpr int bit_lab_alloc        = 1 << 2;
  static constexpr int bit_plab_alloc       = 1 << 3;
  static constexpr int bit_old_alloc        = 1 << 4;
  static constexpr int bit_promotion_alloc  = 1 << 5;

  static constexpr Type _alloc_shared              = 0;
  static constexpr Type _alloc_tlab                = bit_lab_alloc;
  static constexpr Type _alloc_cds                 = bit_cds_alloc;
  static constexpr Type _alloc_shared_gc           = bit_gc_alloc;
  static constexpr Type _alloc_shared_gc_old       = bit_gc_alloc | bit_old_alloc;
  static constexpr Type _alloc_shared_gc_promotion = bit_gc_alloc | bit_old_alloc | bit_promotion_alloc;
  static constexpr Type _alloc_gclab               = bit_gc_alloc | bit_lab_alloc;
  static constexpr Type _alloc_plab                = bit_gc_alloc | bit_lab_alloc | bit_plab_alloc | bit_old_alloc;

  static const char* alloc_type_to_string(Type type) {
    switch (type) {
      case _alloc_shared:
        return "Shared";
      case _alloc_shared_gc:
        return "Shared GC";
      case _alloc_shared_gc_old:
        return "Shared GC Old";
      case _alloc_shared_gc_promotion:
        return "Shared GC Promotion";
      case _alloc_cds:
        return "CDS";
      case _alloc_tlab:
        return "TLAB";
      case _alloc_gclab:
        return "GCLAB";
      case _alloc_plab:
        return "PLAB";
      default:
        ShouldNotReachHere();
    }
  }

private:
  // When ShenandoahElasticTLAB is enabled, the request cannot be made smaller than _min_size.
  size_t const _min_size;

  // The size of the request in words.
  size_t const _requested_size;

  // The allocation may be increased for padding or decreased to fit in the remaining space of a region.
  size_t _actual_size;

  // For a humongous object, the _waste is the amount of free memory in the last region.
  // For other requests, the _waste will be non-zero if the request enountered one or more regions
  // with less memory than _min_size. This waste does not contribute to the used memory for
  // the heap, but it does contribute to the allocation rate for heuristics.
  size_t _waste;

  // This is the type of the request.
  Type const _alloc_type;

#ifdef ASSERT
  // Check that this is set before being read.
  bool _actual_size_set;
#endif

  ShenandoahAllocRequest(size_t _min_size, size_t _requested_size, Type _alloc_type) :
          _min_size(_min_size), _requested_size(_requested_size),
          _actual_size(0), _waste(0), _alloc_type(_alloc_type)
#ifdef ASSERT
          , _actual_size_set(false)
#endif
  {}

public:
  static inline ShenandoahAllocRequest for_tlab(size_t min_size, size_t requested_size) {
    return ShenandoahAllocRequest(min_size, requested_size, _alloc_tlab);
  }

  static inline ShenandoahAllocRequest for_gclab(size_t min_size, size_t requested_size) {
    return ShenandoahAllocRequest(min_size, requested_size, _alloc_gclab);
  }

  static inline ShenandoahAllocRequest for_plab(size_t min_size, size_t requested_size) {
    return ShenandoahAllocRequest(min_size, requested_size, _alloc_plab);
  }

  static inline ShenandoahAllocRequest for_shared_gc(size_t requested_size, ShenandoahAffiliation affiliation, bool is_promotion = false) {
    if (is_promotion) {
      assert(affiliation == OLD_GENERATION, "Should only promote to old generation");
      return ShenandoahAllocRequest(0, requested_size, _alloc_shared_gc_promotion);
    }
    if (affiliation == OLD_GENERATION) {
      return ShenandoahAllocRequest(0, requested_size, _alloc_shared_gc_old);
    }
    return ShenandoahAllocRequest(0, requested_size, _alloc_shared_gc);
  }

  static inline ShenandoahAllocRequest for_shared(size_t requested_size) {
    return ShenandoahAllocRequest(0, requested_size, _alloc_shared);
  }

  static inline ShenandoahAllocRequest for_cds(size_t requested_size) {
    return ShenandoahAllocRequest(0, requested_size, _alloc_cds);
  }

  inline size_t size() const {
    return _requested_size;
  }

  inline Type type() const {
    return _alloc_type;
  }

  inline const char* type_string() const {
    return alloc_type_to_string(_alloc_type);
  }

  inline size_t min_size() const {
    assert (is_lab_alloc(), "Only access for LAB allocs");
    return _min_size;
  }

  inline size_t actual_size() const {
    assert (_actual_size_set, "Should be set");
    return _actual_size;
  }

  inline void set_actual_size(size_t v) {
#ifdef ASSERT
    assert (!_actual_size_set, "Should not be set");
    _actual_size_set = true;
#endif
    _actual_size = v;
  }

  inline size_t waste() const {
    return _waste;
  }

  inline void set_waste(size_t v) {
    _waste = v;
  }

  inline bool is_mutator_alloc() const {
    return (_alloc_type & bit_gc_alloc) == 0;
  }

  inline bool is_gc_alloc() const {
    return (_alloc_type & bit_gc_alloc) != 0;
  }

  inline bool is_lab_alloc() const {
    return (_alloc_type & bit_lab_alloc) != 0;
  }

  inline bool is_old() const {
    return (_alloc_type & bit_old_alloc) != 0;
  }

  inline bool is_young() const {
    return (_alloc_type & bit_old_alloc) == 0;
  }

  inline bool is_cds() const {
    return _alloc_type == _alloc_cds;
  }

  inline ShenandoahAffiliation affiliation() const {
    return (_alloc_type & bit_old_alloc) == 0 ? YOUNG_GENERATION : OLD_GENERATION ;
  }

  const char* affiliation_name() const {
    return shenandoah_affiliation_name(affiliation());
  }

  inline bool is_promotion() const {
    return (_alloc_type & bit_promotion_alloc) != 0;
  }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHALLOCREQUEST_HPP
