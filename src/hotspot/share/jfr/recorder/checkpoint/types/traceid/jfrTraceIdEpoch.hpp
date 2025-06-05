/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDEPOCH_HPP
#define SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDEPOCH_HPP

#include "jfr/utilities/jfrSignal.hpp"
#include "memory/allStatic.hpp"

#define BIT                                  1
#define METHOD_BIT                           (BIT << 2)
#define EPOCH_0_SHIFT                        0
#define EPOCH_1_SHIFT                        1
#define EPOCH_0_BIT                          (BIT << EPOCH_0_SHIFT)
#define EPOCH_1_BIT                          (BIT << EPOCH_1_SHIFT)
#define EPOCH_0_METHOD_BIT                   (METHOD_BIT << EPOCH_0_SHIFT)
#define EPOCH_1_METHOD_BIT                   (METHOD_BIT << EPOCH_1_SHIFT)
#define METHOD_AND_CLASS_BITS                (METHOD_BIT | BIT)
#define EPOCH_0_METHOD_AND_CLASS_BITS        (METHOD_AND_CLASS_BITS << EPOCH_0_SHIFT)
#define EPOCH_1_METHOD_AND_CLASS_BITS        (METHOD_AND_CLASS_BITS << EPOCH_1_SHIFT)

/*
 * An epoch shift or alternation on each rotation enables concurrent tagging.
 * The epoch shift happens only during a safepoint.
 *
 *   _generation - mainly used with virtual threads, but also for the generational string pool in Java.
 *   _tag_state  - signals an incremental modification to artifact tagging (klasses, methods, CLDs, etc)
 *                   purpose of which is to trigger a collection of artifacts.
 *   _method_tracer_state - a special notification state only used with method timing and tracing.
 *   _epoch_state - the fundamental binary epoch state that shifts on each rotation during a safepoint.
 */

class JfrTraceIdEpoch : AllStatic {
  friend class JfrCheckpointManager;
 private:
  static u2 _generation;
  static JfrSignal _tag_state;
  static bool _method_tracer_state;
  static bool _epoch_state;

  static void shift_epoch();

 public:
  static bool epoch() {
    return _epoch_state;
  }

  static address epoch_address() {
    return (address)&_epoch_state;
  }

  static address epoch_generation_address() {
    return (address)&_generation;
  }

  static u1 current() {
    return _epoch_state ? (u1)1 : (u1)0;
  }

  static u2 epoch_generation() {
    return _generation;
  }

  static bool is_current_epoch_generation(u2 generation) {
    return _generation == generation;
  }

  static u1 previous() {
    return _epoch_state ? (u1)0 : (u1)1;
  }

  static bool is_synchronizing();

  static uint8_t this_epoch_bit() {
    return _epoch_state ? EPOCH_1_BIT : EPOCH_0_BIT;
  }

  static uint8_t previous_epoch_bit() {
    return _epoch_state ? EPOCH_0_BIT : EPOCH_1_BIT;
  }

  static uint8_t this_epoch_method_bit() {
    return _epoch_state ? EPOCH_1_METHOD_BIT : EPOCH_0_METHOD_BIT;
  }

  static uint8_t previous_epoch_method_bit() {
    return _epoch_state ? EPOCH_0_METHOD_BIT : EPOCH_1_METHOD_BIT;
  }

  static uint8_t this_epoch_method_and_class_bits() {
    return _epoch_state ? EPOCH_1_METHOD_AND_CLASS_BITS : EPOCH_0_METHOD_AND_CLASS_BITS;
  }

  static uint8_t previous_epoch_method_and_class_bits() {
    return _epoch_state ? EPOCH_0_METHOD_AND_CLASS_BITS : EPOCH_1_METHOD_AND_CLASS_BITS;
  }

  static bool has_changed_tag_state() {
    return _tag_state.is_signaled_with_reset() || has_method_tracer_changed_tag_state();
  }

  static bool has_changed_tag_state_no_reset() {
    return _tag_state.is_signaled();
  }

  static void set_changed_tag_state() {
    _tag_state.signal();
  }

  static address signal_address() {
    return _tag_state.signaled_address();
  }

  static void set_method_tracer_tag_state();
  static void reset_method_tracer_tag_state();
  static bool has_method_tracer_changed_tag_state();
};

#endif // SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDEPOCH_HPP
