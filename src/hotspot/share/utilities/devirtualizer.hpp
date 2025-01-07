/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_DEVIRTUALIZER_HPP
#define SHARE_UTILITIES_DEVIRTUALIZER_HPP

#include "oops/klassInfoLUTEntry.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/bitMap.hpp"

class ClassLoaderData;

// Dispatches to the non-virtual functions if OopClosureType has
// a concrete implementation, otherwise a virtual call is taken.
class Devirtualizer {
 public:
  template <typename OopClosureType, typename T> static void do_oop(OopClosureType* closure, T* p);
  template <typename OopClosureType>             static void do_klass(OopClosureType* closure, Klass* k);
  template <typename OopClosureType>             static void do_cld(OopClosureType* closure, ClassLoaderData* cld);
  template <typename OopClosureType>             static bool do_metadata(OopClosureType* closure);
  template <typename DerivedOopClosureType>      static void do_derived_oop(DerivedOopClosureType* closure, derived_base* base, derived_pointer* derived);
  template <typename BitMapClosureType>          static bool do_bit(BitMapClosureType* closure, BitMap::idx_t index);
};

#endif // SHARE_UTILITIES_DEVIRTUALIZER_HPP
