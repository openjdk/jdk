/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_FENCE_HPP
#define SHARE_MEMORY_METASPACE_FENCE_HPP
#ifdef ASSERT

#include "utilities/globalDefinitions.hpp"

namespace metaspace {

class Fence {
  static constexpr uintx EyeCatcher =
    NOT_LP64(0x77698465) LP64_ONLY(0x7769846577698465ULL); // "META" resp "METAMETA"
  // Two eyecatchers to easily spot a corrupted _next pointer
  const uintx _eye1;
  const Fence* const _next;
  NOT_LP64(uintx _dummy;)
  const uintx _eye2;
public:
  Fence(const Fence* next) : _eye1(EyeCatcher), _next(next), _eye2(EyeCatcher) {}
  const Fence* next() const { return _next; }
  void verify() const;
};

} // namespace metaspace

#endif // ASSERT
#endif // SHARE_MEMORY_METASPACE_FENCE_HPP
