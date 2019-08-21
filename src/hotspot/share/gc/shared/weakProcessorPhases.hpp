/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_WEAKPROCESSORPHASES_HPP
#define SHARE_GC_SHARED_WEAKPROCESSORPHASES_HPP

#include "gc/shared/oopStorageSet.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class BoolObjectClosure;
class OopClosure;
class OopStorage;

class WeakProcessorPhases : AllStatic {
public:
  class Iterator;

  typedef void (*Processor)(BoolObjectClosure*, OopClosure*);

  enum Phase {
    // Serial phases.
    JVMTI_ONLY(jvmti JFR_ONLY(COMMA))
    JFR_ONLY(jfr)

    // Additional implicit phase values follow for oopstorages.
  };

  static const uint serial_phase_start = 0;
  static const uint serial_phase_count = 0 JVMTI_ONLY(+ 1) JFR_ONLY(+ 1);
  static const uint oopstorage_phase_start = serial_phase_count;
  static const uint oopstorage_phase_count = OopStorageSet::weak_count;
  static const uint phase_count = serial_phase_count + oopstorage_phase_count;

  // Precondition: value < serial_phase_count
  static Phase serial_phase(uint value);

  // Precondition: value < oopstorage_phase_count
  static Phase oopstorage_phase(uint value);

  // Indexes relative to the corresponding phase_start constant.
  // Precondition: is_serial(phase) or is_oopstorage(phase) accordingly
  static uint serial_index(Phase phase);
  static uint oopstorage_index(Phase phase);

  static bool is_serial(Phase phase);
  static bool is_oopstorage(Phase phase);

  static Iterator serial_iterator();
  static Iterator oopstorage_iterator();

  // Precondition: is_serial(phase)
  static const char* description(Phase phase);

  // Precondition: is_serial(phase)
  static Processor processor(Phase phase);
};

typedef WeakProcessorPhases::Phase WeakProcessorPhase;

class WeakProcessorPhases::Iterator {
  friend class WeakProcessorPhases;

  uint _index;
  uint _limit;

  Iterator(uint index, uint limit) : _index(index), _limit(limit) {}

  static const uint singular_value = UINT_MAX;
  void verify_nonsingular() const NOT_DEBUG_RETURN;
  void verify_category_match(const Iterator& other) const NOT_DEBUG_RETURN;
  void verify_dereferenceable() const NOT_DEBUG_RETURN;

public:
  // Construct a singular iterator for later assignment.  The only valid
  // operations are destruction and assignment.
  Iterator() : _index(singular_value), _limit(singular_value) {}

  bool is_end() const {
    verify_nonsingular();
    return _index == _limit;
  }

  bool operator==(const Iterator& other) const {
    verify_category_match(other);
    return _index == other._index;
  }

  bool operator!=(const Iterator& other) const {
    return !operator==(other);
  }

  Phase operator*() const {
    verify_dereferenceable();
    return static_cast<Phase>(_index);
  }

  // Phase doesn't have members, so no operator->().

  Iterator& operator++() {
    verify_dereferenceable();
    ++_index;
    return *this;
  }

  Iterator operator++(int) {
    verify_dereferenceable();
    return Iterator(_index++, _limit);
  }

  Iterator begin() const {
    verify_nonsingular();
    return *this;
  }

  Iterator end() const {
    verify_nonsingular();
    return Iterator(_limit, _limit);
  }
};

inline WeakProcessorPhases::Iterator WeakProcessorPhases::serial_iterator() {
  return Iterator(serial_phase_start, serial_phase_start + serial_phase_count);
}

inline WeakProcessorPhases::Iterator WeakProcessorPhases::oopstorage_iterator() {
  return Iterator(oopstorage_phase_start, oopstorage_phase_start + oopstorage_phase_count);
}

#endif // SHARE_GC_SHARED_WEAKPROCESSORPHASES_HPP
