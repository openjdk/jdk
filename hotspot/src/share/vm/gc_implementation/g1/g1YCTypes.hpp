/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1YCTYPES_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1YCTYPES_HPP

#include "utilities/debug.hpp"

enum G1YCType {
  Normal,
  InitialMark,
  DuringMark,
  Mixed,
  G1YCTypeEndSentinel
};

class G1YCTypeHelper {
 public:
  static const char* to_string(G1YCType type) {
    switch(type) {
      case Normal: return "Normal";
      case InitialMark: return "Initial Mark";
      case DuringMark: return "During Mark";
      case Mixed: return "Mixed";
      default: ShouldNotReachHere(); return NULL;
    }
  }
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1YCTYPES_HPP
