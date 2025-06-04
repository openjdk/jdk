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

#ifndef SHARE_GC_SHARED_CONTINUATIONGCSUPPORT_HPP
#define SHARE_GC_SHARED_CONTINUATIONGCSUPPORT_HPP

#include "memory/allStatic.hpp"
#include "oops/oopsHierarchy.hpp"

class ContinuationGCSupport : public AllStatic {
public:
  // Relativize the given oop if it is a stack chunk.
  static bool relativize_stack_chunk(oop obj);
  // Relativize and transform to use a bitmap for future oop iteration for the
  // given oop if it is a stack chunk.
  static void transform_stack_chunk(oop obj);
};

#endif // SHARE_GC_SHARED_CONTINUATIONGCSUPPORT_HPP
