/*
* Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_SUPPORT_JFRTHREADID_INLINE_HPP
#define SHARE_JFR_SUPPORT_JFRTHREADID_INLINE_HPP

#include "jfr/support/jfrThreadId.hpp"

#include "classfile/javaClasses.inline.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "memory/allocation.inline.hpp"

static constexpr const u2 excluded_bit = 32768;
static constexpr const u2 epoch_mask = excluded_bit - 1;

class ThreadIdAccess : AllStatic {
 public:
  static traceid id(oop ref) {
    return static_cast<traceid>(java_lang_Thread::thread_id(ref));
  }
  static bool is_excluded(oop ref) {
    return epoch(ref) & excluded_bit;
  }
  static void include(oop ref) {
    assert(is_excluded(ref), "invariant");
    set_epoch(ref, epoch(ref) ^ excluded_bit);
  }
  static void exclude(oop ref) {
    set_epoch(ref, excluded_bit | epoch(ref));
  }
  static u2 epoch(oop ref) {
    return java_lang_Thread::jfr_epoch(ref);
  }
  static void set_epoch(oop ref, u2 epoch) {
    java_lang_Thread::set_jfr_epoch(ref, epoch);
  }
  static u2 current_epoch() {
    return JfrTraceIdEpoch::epoch_generation();
  }
};

#endif // SHARE_JFR_SUPPORT_JFRTHREADID_INLINE_HPP
