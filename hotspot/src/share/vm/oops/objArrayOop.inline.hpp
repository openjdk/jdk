/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_OBJARRAYOOP_INLINE_HPP
#define SHARE_VM_OOPS_OBJARRAYOOP_INLINE_HPP

#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/globals.hpp"

inline oop objArrayOopDesc::obj_at(int index) const {
  // With UseCompressedOops decode the narrow oop in the objArray to an
  // uncompressed oop.  Otherwise this is simply a "*" operator.
  if (UseCompressedOops) {
    return load_decode_heap_oop(obj_at_addr<narrowOop>(index));
  } else {
    return load_decode_heap_oop(obj_at_addr<oop>(index));
  }
}

inline void objArrayOopDesc::obj_at_put(int index, oop value) {
  if (UseCompressedOops) {
    oop_store(obj_at_addr<narrowOop>(index), value);
  } else {
    oop_store(obj_at_addr<oop>(index), value);
  }
}

#endif // SHARE_VM_OOPS_OBJARRAYOOP_INLINE_HPP
