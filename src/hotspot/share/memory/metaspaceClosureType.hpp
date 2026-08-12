/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACECLOSURETYPE_HPP
#define SHARE_MEMORY_METASPACECLOSURETYPE_HPP

#include "memory/allocation.hpp"
#include "metaprogramming/enableIf.hpp"

// MetaspaceClosure is able to iterate on MetaspaceObjs, plus the following classes
#define METASPACE_CLOSURE_TYPES_DO(f) \
  METASPACE_OBJ_TYPES_DO(f) \
  f(CArray) \
  f(GrowableArray) \
  f(ModuleEntry) \
  f(PackageEntry) \

#define METASPACE_CLOSURE_TYPE_DECLARE(name) name ## Type,

enum class MetaspaceClosureType : int {
  METASPACE_CLOSURE_TYPES_DO(METASPACE_CLOSURE_TYPE_DECLARE)
  _number_of_types
};

inline MetaspaceClosureType as_type(MetaspaceClosureType t) {
  return t;
}

inline MetaspaceClosureType as_type(MetaspaceObj::Type msotype) {
  precond(msotype < MetaspaceObj::_number_of_types);
  return (MetaspaceClosureType)msotype;
}

// This macro checks for the existence of a member with the name metaspace_pointers_do
#define HAS_METASPACE_POINTERS_DO(T) HasMetaspacePointersDo<T>::value

template<typename T>
class HasMetaspacePointersDo {
  template<typename U> static void* test(decltype(&U::metaspace_pointers_do));
  template<typename> static int test(...);

  // - If the first template matches, test_type will be void*
  // - If the first template doesn't match, test<T> will match second template,
  //   and test_type will be int
  using test_type = decltype(test<T>(nullptr));
public:
  static constexpr bool value = std::is_pointer_v<test_type>;
};

#endif // SHARE_MEMORY_METASPACECLOSURETYPE_HPP
