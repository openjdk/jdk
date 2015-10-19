/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1INCSETSTATE_HPP
#define SHARE_VM_GC_G1_G1INCSETSTATE_HPP

#include "gc/g1/g1BiasedArray.hpp"
#include "gc/g1/heapRegion.hpp"
#include "memory/allocation.hpp"

// Per-region state during garbage collection.
struct InCSetState {
 public:
  // We use different types to represent the state value. Particularly SPARC puts
  // values in structs from "left to right", i.e. MSB to LSB. This results in many
  // unnecessary shift operations when loading and storing values of this type.
  // This degrades performance significantly (>10%) on that platform.
  // Other tested ABIs do not seem to have this problem, and actually tend to
  // favor smaller types, so we use the smallest usable type there.
#ifdef SPARC
  #define CSETSTATE_FORMAT INTPTR_FORMAT
  typedef intptr_t in_cset_state_t;
#else
  #define CSETSTATE_FORMAT "%d"
  typedef int8_t in_cset_state_t;
#endif
 private:
  in_cset_state_t _value;
 public:
  enum {
    // Selection of the values were driven to micro-optimize the encoding and
    // frequency of the checks.
    // The most common check is whether the region is in the collection set or not.
    // This encoding allows us to use an != 0 check which in some architectures
    // (x86*) can be encoded slightly more efficently than a normal comparison
    // against zero.
    // The same situation occurs when checking whether the region is humongous
    // or not, which is encoded by values < 0.
    // The other values are simply encoded in increasing generation order, which
    // makes getting the next generation fast by a simple increment.
    Humongous    = -1,    // The region is humongous - note that actually any value < 0 would be possible here.
    NotInCSet    =  0,    // The region is not in the collection set.
    Young        =  1,    // The region is in the collection set and a young region.
    Old          =  2,    // The region is in the collection set and an old region.
    Num
  };

  InCSetState(in_cset_state_t value = NotInCSet) : _value(value) {
    assert(is_valid(), "Invalid state %d", _value);
  }

  in_cset_state_t value() const        { return _value; }

  void set_old()                       { _value = Old; }

  bool is_in_cset_or_humongous() const { return _value != NotInCSet; }
  bool is_in_cset() const              { return _value > NotInCSet; }
  bool is_humongous() const            { return _value < NotInCSet; }
  bool is_young() const                { return _value == Young; }
  bool is_old() const                  { return _value == Old; }

#ifdef ASSERT
  bool is_default() const              { return !is_in_cset_or_humongous(); }
  bool is_valid() const                { return (_value >= Humongous) && (_value < Num); }
  bool is_valid_gen() const            { return (_value >= Young && _value <= Old); }
#endif
};

// Instances of this class are used for quick tests on whether a reference points
// into the collection set and into which generation or is a humongous object
//
// Each of the array's elements indicates whether the corresponding region is in
// the collection set and if so in which generation, or a humongous region.
//
// We use this to speed up reference processing during young collection and
// quickly reclaim humongous objects. For the latter, by making a humongous region
// succeed this test, we sort-of add it to the collection set. During the reference
// iteration closures, when we see a humongous region, we then simply mark it as
// referenced, i.e. live.
class G1InCSetStateFastTestBiasedMappedArray : public G1BiasedMappedArray<InCSetState> {
 protected:
  InCSetState default_value() const { return InCSetState::NotInCSet; }
 public:
  void set_humongous(uintptr_t index) {
    assert(get_by_index(index).is_default(),
           "State at index " INTPTR_FORMAT " should be default but is " CSETSTATE_FORMAT, index, get_by_index(index).value());
    set_by_index(index, InCSetState::Humongous);
  }

  void clear_humongous(uintptr_t index) {
    set_by_index(index, InCSetState::NotInCSet);
  }

  void set_in_young(uintptr_t index) {
    assert(get_by_index(index).is_default(),
           "State at index " INTPTR_FORMAT " should be default but is " CSETSTATE_FORMAT, index, get_by_index(index).value());
    set_by_index(index, InCSetState::Young);
  }

  void set_in_old(uintptr_t index) {
    assert(get_by_index(index).is_default(),
           "State at index " INTPTR_FORMAT " should be default but is " CSETSTATE_FORMAT, index, get_by_index(index).value());
    set_by_index(index, InCSetState::Old);
  }

  bool is_in_cset_or_humongous(HeapWord* addr) const { return at(addr).is_in_cset_or_humongous(); }
  bool is_in_cset(HeapWord* addr) const { return at(addr).is_in_cset(); }
  bool is_in_cset(const HeapRegion* hr) const { return get_by_index(hr->hrm_index()).is_in_cset(); }
  InCSetState at(HeapWord* addr) const { return get_by_address(addr); }
  void clear() { G1BiasedMappedArray<InCSetState>::clear(); }
  void clear(const HeapRegion* hr) { return set_by_index(hr->hrm_index(), InCSetState::NotInCSet); }
};

#endif // SHARE_VM_GC_G1_G1INCSETSTATE_HPP
