/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

// A Generation that does parallel young-gen collection extended
// for adaptive size policy.

// Division of generation into spaces
// done by DefNewGeneration::compute_space_boundaries()
//      +---------------+
//      | uncommitted   |
//      |---------------|
//      | ss0           |
//      |---------------|
//      | ss1           |
//      |---------------|
//      |               |
//      | eden          |
//      |               |
//      +---------------+       <-- low end of VirtualSpace
//
class ASParNewGeneration: public ParNewGeneration {

  size_t _min_gen_size;

  // Resize the generation based on the desired sizes of
  // the constituent spaces.
  bool resize_generation(size_t eden_size, size_t survivor_size);
  // Resize the spaces based on their desired sizes but
  // respecting the maximum size of the generation.
  void resize_spaces(size_t eden_size, size_t survivor_size);
  // Return the byte size remaining to the minimum generation size.
  size_t available_to_min_gen();
  // Return the byte size remaining to the live data in the generation.
  size_t available_to_live() const;
  // Return the byte size that the generation is allowed to shrink.
  size_t limit_gen_shrink(size_t bytes);
  // Reset the size of the spaces after a shrink of the generation.
  void reset_survivors_after_shrink();

  // Accessor
  VirtualSpace* virtual_space() { return &_virtual_space; }

  virtual void adjust_desired_tenuring_threshold();

 public:

  ASParNewGeneration(ReservedSpace rs,
                     size_t initial_byte_size,
                     size_t min_byte_size,
                     int level);

  virtual const char* short_name() const { return "ASParNew"; }
  virtual const char* name() const;
  virtual Generation::Name kind() { return ASParNew; }

  // Change the sizes of eden and the survivor spaces in
  // the generation.  The parameters are desired sizes
  // and are not guaranteed to be met.  For example, if
  // the total is larger than the generation.
  void resize(size_t eden_size, size_t survivor_size);

  virtual void compute_new_size();

  size_t max_gen_size()                 { return _reserved.byte_size(); }
  size_t min_gen_size() const           { return _min_gen_size; }

  // Space boundary invariant checker
  void space_invariants() PRODUCT_RETURN;
};
