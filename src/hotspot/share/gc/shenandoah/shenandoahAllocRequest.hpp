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
  enum Type {
    _alloc_shared,      // Allocate common, outside of TLAB
    _alloc_shared_gc,   // Allocate common, outside of GCLAB/PLAB
    _alloc_tlab,        // Allocate TLAB
    _alloc_gclab,       // Allocate GCLAB
    _alloc_plab,        // Allocate PLAB
    _ALLOC_LIMIT
  };

  static const char* alloc_type_to_string(Type type) {
    switch (type) {
      case _alloc_shared:
        return "Shared";
      case _alloc_shared_gc:
        return "Shared GC";
      case _alloc_tlab:
        return "TLAB";
      case _alloc_gclab:
        return "GCLAB";
      case _alloc_plab:
        return "PLAB";
      default:
        ShouldNotReachHere();
        return "";
    }
  }

private:
  // When ShenandoahElasticTLAB is enabled, the request cannot be made smaller than _min_size.
  size_t _min_size;

  // The size of the request in words.
  size_t _requested_size;

  // The allocation may be increased for padding or decreased to fit in the remaining space of a region.
  size_t _actual_size;

  // For a humongous object, the _waste is the amount of free memory in the last region.
  // For other requests, the _waste will be non-zero if the request enountered one or more regions
  // with less memory than _min_size. This waste does not contribute to the used memory for
  // the heap, but it does contribute to the allocation rate for heuristics.
  size_t _waste;

  // This is the type of the request.
  Type _alloc_type;

  // This is the generation which the request is targeting.
  ShenandoahAffiliation const _affiliation;

#ifdef ASSERT
  // Check that this is set before being read.
  bool _actual_size_set;
#endif

  ShenandoahAllocRequest(size_t _min_size, size_t _requested_size, Type _alloc_type, ShenandoahAffiliation affiliation) :
          _min_size(_min_size), _requested_size(_requested_size),
          _actual_size(0), _waste(0), _alloc_type(_alloc_type), _affiliation(affiliation)
#ifdef ASSERT
          , _actual_size_set(false)
#endif
  {}

public:
  static inline ShenandoahAllocRequest for_tlab(size_t min_size, size_t requested_size) {
    return ShenandoahAllocRequest(min_size, requested_size, _alloc_tlab, ShenandoahAffiliation::YOUNG_GENERATION);
  }

  static inline ShenandoahAllocRequest for_gclab(size_t min_size, size_t requested_size) {
    return ShenandoahAllocRequest(min_size, requested_size, _alloc_gclab, ShenandoahAffiliation::YOUNG_GENERATION);
  }

  static inline ShenandoahAllocRequest for_plab(size_t min_size, size_t requested_size) {
    return ShenandoahAllocRequest(min_size, requested_size, _alloc_plab, ShenandoahAffiliation::OLD_GENERATION);
  }

  static inline ShenandoahAllocRequest for_shared_gc(size_t requested_size, ShenandoahAffiliation affiliation) {
    return ShenandoahAllocRequest(0, requested_size, _alloc_shared_gc, affiliation);
  }

  static inline ShenandoahAllocRequest for_shared(size_t requested_size) {
    return ShenandoahAllocRequest(0, requested_size, _alloc_shared, ShenandoahAffiliation::YOUNG_GENERATION);
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
    switch (_alloc_type) {
      case _alloc_tlab:
      case _alloc_shared:
        return true;
      case _alloc_gclab:
      case _alloc_plab:
      case _alloc_shared_gc:
        return false;
      default:
        ShouldNotReachHere();
        return false;
    }
  }

  inline bool is_gc_alloc() const {
    switch (_alloc_type) {
      case _alloc_tlab:
      case _alloc_shared:
        return false;
      case _alloc_gclab:
      case _alloc_plab:
      case _alloc_shared_gc:
        return true;
      default:
        ShouldNotReachHere();
        return false;
    }
  }

  inline bool is_lab_alloc() const {
    switch (_alloc_type) {
      case _alloc_tlab:
      case _alloc_gclab:
      case _alloc_plab:
        return true;
      case _alloc_shared:
      case _alloc_shared_gc:
        return false;
      default:
        ShouldNotReachHere();
        return false;
    }
  }

  bool is_old() const {
    return _affiliation == OLD_GENERATION;
  }

  bool is_young() const {
    return _affiliation == YOUNG_GENERATION;
  }

  ShenandoahAffiliation affiliation() const {
    return _affiliation;
  }

  const char* affiliation_name() const {
    return shenandoah_affiliation_name(_affiliation);
  }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHALLOCREQUEST_HPP
