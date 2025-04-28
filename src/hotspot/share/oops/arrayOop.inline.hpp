/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "oops/arrayOop.hpp"
#include "oops/objLayout.inline.hpp"

template <HeaderMode mode>
inline constexpr int arrayOopDesc::length_offset_in_bytes_nobranches() {
  return ObjLayoutHelpers::markword_plus_klass_in_bytes<mode>();
}

template <HeaderMode mode>
inline int* arrayOopDesc::length_addr_nobranches() const {
  return field_addr<int>(length_offset_in_bytes_nobranches<mode>());
}

template <HeaderMode mode>
inline int arrayOopDesc::length_nobranches() const {
  const int len = *length_addr_nobranches<mode>();
  return len;
}

#endif // SHARE_OOPS_ARRAYOOP_INLINE_HPP
