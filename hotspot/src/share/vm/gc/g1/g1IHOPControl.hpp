/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1IHOPCONTROL_HPP
#define SHARE_VM_GC_G1_G1IHOPCONTROL_HPP

#include "memory/allocation.hpp"

// Base class for algorithms that calculate the heap occupancy at which
// concurrent marking should start. This heap usage threshold should be relative
// to old gen size.
class G1IHOPControl : public CHeapObj<mtGC> {
 protected:
  // The initial IHOP value relative to the target occupancy.
  double _initial_ihop_percent;
  // The target maximum occupancy of the heap.
  size_t _target_occupancy;

  // Most recent complete mutator allocation period in seconds.
  double _last_allocation_time_s;
  // Amount of bytes allocated during _last_allocation_time_s.
  size_t _last_allocated_bytes;

  // Initialize an instance with the initial IHOP value in percent and the target
  // occupancy. The target occupancy is the number of bytes when marking should
  // be finished and reclaim started.
  G1IHOPControl(double initial_ihop_percent, size_t target_occupancy);

  // Most recent time from the end of the initial mark to the start of the first
  // mixed gc.
  virtual double last_marking_length_s() const = 0;
 public:
  virtual ~G1IHOPControl() { }

  // Get the current non-young occupancy at which concurrent marking should start.
  virtual size_t get_conc_mark_start_threshold() = 0;

  // Update information about time during which allocations in the Java heap occurred,
  // how large these allocations were in bytes, and an additional buffer.
  // The allocations should contain any amount of space made unusable for further
  // allocation, e.g. any waste caused by TLAB allocation, space at the end of
  // humongous objects that can not be used for allocation, etc.
  // Together with the target occupancy, this additional buffer should contain the
  // difference between old gen size and total heap size at the start of reclamation,
  // and space required for that reclamation.
  virtual void update_allocation_info(double allocation_time_s, size_t allocated_bytes, size_t additional_buffer_size);
  // Update the time spent in the mutator beginning from the end of initial mark to
  // the first mixed gc.
  virtual void update_marking_length(double marking_length_s) = 0;

  virtual void print();
};

// The returned concurrent mark starting occupancy threshold is a fixed value
// relative to the maximum heap size.
class G1StaticIHOPControl : public G1IHOPControl {
  // Most recent mutator time between the end of initial mark to the start of the
  // first mixed gc.
  double _last_marking_length_s;
 protected:
  double last_marking_length_s() const { return _last_marking_length_s; }
 public:
  G1StaticIHOPControl(double ihop_percent, size_t target_occupancy);

  size_t get_conc_mark_start_threshold() { return (size_t) (_initial_ihop_percent * _target_occupancy / 100.0); }

  virtual void update_marking_length(double marking_length_s) {
   assert(marking_length_s > 0.0, "Marking length must be larger than zero but is %.3f", marking_length_s);
    _last_marking_length_s = marking_length_s;
  }

#ifndef PRODUCT
  static void test();
#endif
};

#endif // SHARE_VM_GC_G1_G1IHOPCONTROL_HPP
