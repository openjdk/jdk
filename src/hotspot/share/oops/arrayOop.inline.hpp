/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_ARRAYOOP_INLINE_HPP
#define SHARE_OOPS_ARRAYOOP_INLINE_HPP

#include "oops/access.inline.hpp"
#include "oops/arrayOop.hpp"

void* arrayOopDesc::base(BasicType type) const {
  oop resolved_obj = Access<>::resolve(as_oop());
  return arrayOop(resolved_obj)->base_raw(type);
}

void* arrayOopDesc::base_raw(BasicType type) const {
  return reinterpret_cast<void*>(cast_from_oop<intptr_t>(as_oop()) + base_offset_in_bytes(type));
}

#endif // SHARE_OOPS_ARRAYOOP_INLINE_HPP
