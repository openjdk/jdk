/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/utilities/jfrTypes.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"

#define USED_BIT                             1
#define METHOD_USED_BIT                      (USED_BIT << 2)
#define EPOCH_1_SHIFT                        0
#define EPOCH_2_SHIFT                        1
#define USED_EPOCH_1_BIT                     (USED_BIT << EPOCH_1_SHIFT)
#define USED_EPOCH_2_BIT                     (USED_BIT << EPOCH_2_SHIFT)
#define METHOD_USED_EPOCH_1_BIT              (METHOD_USED_BIT << EPOCH_1_SHIFT)
#define METHOD_USED_EPOCH_2_BIT              (METHOD_USED_BIT << EPOCH_2_SHIFT)
#define METHOD_AND_CLASS_IN_USE_BITS         (METHOD_USED_BIT | USED_BIT)
#define METHOD_AND_CLASS_IN_USE_EPOCH_1_BITS (METHOD_AND_CLASS_IN_USE_BITS << EPOCH_1_SHIFT)
#define METHOD_AND_CLASS_IN_USE_EPOCH_2_BITS (METHOD_AND_CLASS_IN_USE_BITS << EPOCH_2_SHIFT)

 // Epoch alternation on each rotation allow for concurrent tagging.
 // The epoch shift happens only during a safepoint.
 //
 // _synchronizing is a transition state, the purpose of which is to
 // have JavaThreads that run _thread_in_native (i.e. Compiler threads)
 // respect the current epoch shift in-progress during a safepoint.
 //
 // _changed_tag_state == true signals an incremental modification to artifact tagging
 // (klasses, methods, CLDs, etc), used to request collection of artifacts.
 //
class JfrTraceIdEpoch : AllStatic {
  friend class JfrCheckpointManager;
 private:
  static bool _epoch_state;
  static bool _synchronizing;
  static volatile bool _changed_tag_state;

  static void begin_epoch_shift();
  static void end_epoch_shift();

  static bool changed_tag_state() {
    return Atomic::load_acquire(&_changed_tag_state);
  }

  static void set_tag_state(bool value) {
    Atomic::release_store(&_changed_tag_state, value);
  }

 public:
  static bool epoch() {
    return _epoch_state;
  }

  static jlong epoch_address() {
    return (jlong)&_epoch_state;
  }

  static u1 current() {
    return _epoch_state ? (u1)1 : (u1)0;
  }

  static u1 previous() {
    return _epoch_state ? (u1)0 : (u1)1;
  }

  static bool is_synchronizing() {
    return Atomic::load_acquire(&_synchronizing);
  }

  static traceid in_use_this_epoch_bit() {
    return _epoch_state ? USED_EPOCH_2_BIT : USED_EPOCH_1_BIT;
  }

  static traceid in_use_prev_epoch_bit() {
    return _epoch_state ? USED_EPOCH_1_BIT : USED_EPOCH_2_BIT;
  }

  static traceid method_in_use_this_epoch_bit() {
    return _epoch_state ? METHOD_USED_EPOCH_2_BIT : METHOD_USED_EPOCH_1_BIT;
  }

  static traceid method_in_use_prev_epoch_bit() {
    return _epoch_state ? METHOD_USED_EPOCH_1_BIT : METHOD_USED_EPOCH_2_BIT;
  }

  static traceid method_and_class_in_use_this_epoch_bits() {
    return _epoch_state ? METHOD_AND_CLASS_IN_USE_EPOCH_2_BITS : METHOD_AND_CLASS_IN_USE_EPOCH_1_BITS;
  }

  static traceid method_and_class_in_use_prev_epoch_bits() {
    return _epoch_state ? METHOD_AND_CLASS_IN_USE_EPOCH_1_BITS :  METHOD_AND_CLASS_IN_USE_EPOCH_2_BITS;
  }

  static bool has_changed_tag_state() {
    if (changed_tag_state()) {
      set_tag_state(false);
      return true;
    }
    return false;
  }

  static void set_changed_tag_state() {
    if (!changed_tag_state()) {
      set_tag_state(true);
    }
  }
};

#endif // SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDEPOCH_HPP
