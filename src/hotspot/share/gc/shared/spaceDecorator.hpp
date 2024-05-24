/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_SPACEDECORATOR_HPP
#define SHARE_GC_SHARED_SPACEDECORATOR_HPP

#include "memory/allStatic.hpp"
#include "memory/memRegion.hpp"
#include "utilities/globalDefinitions.hpp"

class SpaceDecorator : AllStatic {
 public:
  // Initialization flags.
  static const bool Clear               = true;
  static const bool DontClear           = false;
  static const bool Mangle              = true;
  static const bool DontMangle          = false;
};

struct SpaceMangler : AllStatic {
  static void mangle_region(MemRegion mr) NOT_DEBUG_RETURN;
};

#endif // SHARE_GC_SHARED_SPACEDECORATOR_HPP
