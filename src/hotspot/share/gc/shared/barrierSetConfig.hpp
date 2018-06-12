/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_BARRIERSETCONFIG_HPP
#define SHARE_VM_GC_SHARED_BARRIERSETCONFIG_HPP

#include "utilities/macros.hpp"

// Do something for each concrete barrier set part of the build.
#define FOR_EACH_CONCRETE_BARRIER_SET_DO(f)          \
  f(CardTableBarrierSet)                             \
  EPSILONGC_ONLY(f(EpsilonBarrierSet))               \
  G1GC_ONLY(f(G1BarrierSet))                         \
  ZGC_ONLY(f(ZBarrierSet))

#define FOR_EACH_ABSTRACT_BARRIER_SET_DO(f)          \
  f(ModRef)

// Do something for each known barrier set.
#define FOR_EACH_BARRIER_SET_DO(f)    \
  FOR_EACH_ABSTRACT_BARRIER_SET_DO(f) \
  FOR_EACH_CONCRETE_BARRIER_SET_DO(f)

// To enable runtime-resolution of GC barriers on primitives, please
// define SUPPORT_BARRIER_ON_PRIMITIVES.
#ifdef SUPPORT_BARRIER_ON_PRIMITIVES
#define ACCESS_PRIMITIVE_SUPPORT INTERNAL_BT_BARRIER_ON_PRIMITIVES
#else
#define ACCESS_PRIMITIVE_SUPPORT INTERNAL_EMPTY
#endif

#ifdef SUPPORT_NOT_TO_SPACE_INVARIANT
#define ACCESS_TO_SPACE_INVARIANT_SUPPORT INTERNAL_EMPTY
#else
#define ACCESS_TO_SPACE_INVARIANT_SUPPORT INTERNAL_BT_TO_SPACE_INVARIANT
#endif

#define BT_BUILDTIME_DECORATORS (ACCESS_PRIMITIVE_SUPPORT | ACCESS_TO_SPACE_INVARIANT_SUPPORT)

#endif // SHARE_VM_GC_SHARED_BARRIERSETCONFIG_HPP
