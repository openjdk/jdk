/*
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHPLAB_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHPLAB_HPP

#include "gc/shared/plab.hpp"
#include "memory/allocation.hpp"

class ShenandoahGenerationalHeap;

class ShenandoahPLAB {
private:
  // The actual allocation buffer
  PLAB* _plab;

  // Heuristics will grow the desired size of plabs.
  size_t _desired_size;

  // Once the plab has been allocated, and we know the actual size, we record it here.
  size_t _actual_size;

  // As the plab is used for promotions, this value is incremented. When the plab is
  // retired, the difference between 'actual_size' and 'promoted' will be returned to
  // the old generation's promotion reserve (i.e., it will be 'unexpended').
  size_t _promoted;

  // If false, no more promotion by this thread during this evacuation phase.
  bool _allows_promotion;

  // If true, evacuations may attempt to allocate a smaller plab if the original size fails.
  bool _retries_enabled;

  ShenandoahGenerationalHeap* _heap;

public:
  ShenandoahPLAB();
  ~ShenandoahPLAB();

  PLAB* plab() const { return _plab; }

  size_t desired_size() const { return _desired_size; }
  void set_desired_size(size_t v) { _desired_size = v; }

  void enable_retries() { _retries_enabled = true; }
  void disable_retries() { _retries_enabled = false; }
  bool retries_enabled() const { return _retries_enabled; }

  void enable_promotions() { _allows_promotion = true; }
  void disable_promotions() { _allows_promotion = false; }
  bool allows_promotion() const { return _allows_promotion; }

  void reset_promoted() { _promoted = 0; }
  void add_to_promoted(size_t increment) { _promoted += increment; }
  void subtract_from_promoted(size_t increment);
  size_t get_promoted() const { return _promoted; }

  void set_actual_size(size_t value) { _actual_size = value; }
  size_t get_actual_size() const { return _actual_size; }

  HeapWord* allocate(size_t size, bool is_promotion);
  HeapWord* allocate_slow(size_t size, bool is_promotion);
  HeapWord* allocate_new_plab(size_t min_size, size_t word_size, size_t* actual_size);
  void retire();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHPLAB_HPP
