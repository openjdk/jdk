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

#include "precompiled.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "gc/shared/weakProcessorPhases.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"

#if INCLUDE_JFR
#include "jfr/jfr.hpp"
#endif // INCLUDE_JFR

#if INCLUDE_JVMTI
#include "prims/jvmtiExport.hpp"
#endif // INCLUDE_JVMTI

// serial_phase_count is 0 if JFR and JVMTI are both not built,
// requiring some code to be careful to avoid tautological checks
// that some compilers warn about.

#define HAVE_SERIAL_PHASES (INCLUDE_JVMTI || INCLUDE_JFR)

WeakProcessorPhases::Phase WeakProcessorPhases::serial_phase(uint value) {
#if HAVE_SERIAL_PHASES
  assert(value < serial_phase_count, "Invalid serial phase value %u", value);
  return static_cast<Phase>(value + serial_phase_start);
#else
  STATIC_ASSERT(serial_phase_count == 0);
  fatal("invalid serial phase value %u", value);
  return static_cast<Phase>(serial_phase_start);
#endif // HAVE_SERIAL_PHASES
}

WeakProcessorPhases::Phase WeakProcessorPhases::oopstorage_phase(uint value) {
  assert(value < oopstorage_phase_count, "Invalid oopstorage phase value %u", value);
  return static_cast<Phase>(value + oopstorage_phase_start);
}

static uint raw_phase_index(WeakProcessorPhases::Phase phase) {
  return static_cast<uint>(phase);
}

uint WeakProcessorPhases::serial_index(Phase phase) {
  assert(is_serial(phase), "not serial phase %u", raw_phase_index(phase));
  return raw_phase_index(phase) - serial_phase_start;
}

uint WeakProcessorPhases::oopstorage_index(Phase phase) {
  assert(is_oopstorage(phase), "not oopstorage phase %u", raw_phase_index(phase));
  return raw_phase_index(phase) - oopstorage_phase_start;
}

static bool is_phase(WeakProcessorPhases::Phase phase, uint start, uint count) {
  return (raw_phase_index(phase) - start) < count;
}

bool WeakProcessorPhases::is_serial(Phase phase) {
#if HAVE_SERIAL_PHASES
  return is_phase(phase, serial_phase_start, serial_phase_count);
#else
  STATIC_ASSERT(serial_phase_count == 0);
  return false;
#endif // HAVE_SERIAL_PHASES
}

bool WeakProcessorPhases::is_oopstorage(Phase phase) {
  return is_phase(phase, oopstorage_phase_start, oopstorage_phase_count);
}

#ifdef ASSERT

void WeakProcessorPhases::Iterator::verify_nonsingular() const {
  assert(_limit != singular_value, "precondition");
}

void WeakProcessorPhases::Iterator::verify_category_match(const Iterator& other) const {
  verify_nonsingular();
  assert(_limit == other._limit, "precondition");
}

void WeakProcessorPhases::Iterator::verify_dereferenceable() const {
  verify_nonsingular();
  assert(_index < _limit, "precondition");
}

#endif // ASSERT

const char* WeakProcessorPhases::description(Phase phase) {
  switch (phase) {
  JVMTI_ONLY(case jvmti: return "JVMTI weak processing";)
  JFR_ONLY(case jfr: return "JFR weak processing";)
  default:
    ShouldNotReachHere();
    return "Invalid serial weak processing phase";
  }
}

WeakProcessorPhases::Processor WeakProcessorPhases::processor(Phase phase) {
  switch (phase) {
  JVMTI_ONLY(case jvmti: return &JvmtiExport::weak_oops_do;)
  JFR_ONLY(case jfr: return &Jfr::weak_oops_do;)
  default:
    ShouldNotReachHere();
    return NULL;
  }
}
