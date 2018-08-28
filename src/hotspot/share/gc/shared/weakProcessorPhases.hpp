/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class BoolObjectClosure;
class OopClosure;
class OopStorage;

class WeakProcessorPhases : AllStatic {
public:
  typedef void (*Processor)(BoolObjectClosure*, OopClosure*);

  enum Phase {
    // Serial phases.
    JVMTI_ONLY(jvmti COMMA)
    JFR_ONLY(jfr COMMA)

    // OopStorage phases.
    jni,
    vm
  };

  static const uint serial_phase_start = 0;
  static const uint serial_phase_count = jni;
  static const uint oop_storage_phase_start = serial_phase_count;
  static const uint oop_storage_phase_count = (vm + 1) - oop_storage_phase_start;
  static const uint phase_count = serial_phase_count + oop_storage_phase_count;

  static Phase phase(uint value);
  static uint index(Phase phase);
  // Indexes relative to the corresponding phase_start constant.
  static uint serial_index(Phase phase);
  static uint oop_storage_index(Phase phase);

  static bool is_serial(Phase phase);
  static bool is_oop_storage(Phase phase);

  static const char* description(Phase phase);
  static Processor processor(Phase phase); // Precondition: is_serial(phase)
  static OopStorage* oop_storage(Phase phase); // Precondition: is_oop_storage(phase)
};

typedef WeakProcessorPhases::Phase WeakProcessorPhase;

#define FOR_EACH_WEAK_PROCESSOR_PHASE(P)                                \
  for (WeakProcessorPhase P = static_cast<WeakProcessorPhase>(0);       \
       static_cast<uint>(P) <  WeakProcessorPhases::phase_count;        \
       P = static_cast<WeakProcessorPhase>(static_cast<uint>(P) + 1))

#define FOR_EACH_WEAK_PROCESSOR_OOP_STORAGE_PHASE(P)                    \
  for (WeakProcessorPhase P = static_cast<WeakProcessorPhase>(WeakProcessorPhases::oop_storage_phase_start); \
       static_cast<uint>(P) < (WeakProcessorPhases::oop_storage_phase_start + \
                               WeakProcessorPhases::oop_storage_phase_count); \
       P = static_cast<WeakProcessorPhase>(static_cast<uint>(P) + 1))

#endif // SHARE_GC_SHARED_WEAKPROCESSORPHASES_HPP
