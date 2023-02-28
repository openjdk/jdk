/*
* Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFROOPTRACEID_HPP
#define SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFROOPTRACEID_HPP

#include "jfr/utilities/jfrTypes.hpp"
#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"

template <typename T>
class JfrOopTraceId : AllStatic {
 public:
  static traceid id(oop ref);
  static u2 epoch(oop ref);
  static u2 current_epoch();
  static void set_epoch(oop ref);
  static void set_epoch(oop ref, u2 epoch);
  static bool is_excluded(oop ref);
  static void exclude(oop ref);
  static void include(oop ref);
};

#endif // SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFROOPTRACEID_HPP
