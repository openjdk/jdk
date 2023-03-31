/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_PRIMS_FOREIGN_GLOBALS_INLINE_HPP
#define SHARE_PRIMS_FOREIGN_GLOBALS_INLINE_HPP

#include "prims/foreignGlobals.hpp"

#include "classfile/javaClasses.hpp"
#include "oops/oopsHierarchy.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oopCast.inline.hpp"

template<typename T>
void ForeignGlobals::parse_register_array(objArrayOop jarray, StorageType type_index, GrowableArray<T>& array, T (*converter)(int)) {
  objArrayOop subarray = oop_cast<objArrayOop>(jarray->obj_at((int) type_index));
  int subarray_length = subarray->length();
  for (int i = 0; i < subarray_length; i++) {
    oop storage = subarray->obj_at(i);
    jint index = jdk_internal_foreign_abi_VMStorage::index_or_offset(storage);
    array.push(converter(index));
  }
}

inline const char* null_safe_string(const char* str) {
  return str == nullptr ? "null" : str;
}

#endif // SHARE_PRIMS_FOREIGN_GLOBALS_INLINE_HPP
