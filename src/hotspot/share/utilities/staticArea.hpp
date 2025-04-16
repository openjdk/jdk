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

#ifndef SHARE_UTILITIES_STATICAREA_HPP
#define SHARE_UTILITIES_STATICAREA_HPP

#include <stddef.h>
#include <stdint.h>

// A memory area with adequate size and alignment for storage of a T.
template<typename T>
class alignas(alignof(T)) StaticArea {
#ifdef ASSERT
  static const uint32_t death_pattern = 0xBADDCAFE;
#endif
  char _mem[sizeof(T)];

public:
#ifdef ASSERT
  StaticArea() {
    uint32_t* d = reinterpret_cast<uint32_t*>(_mem);
    for (size_t i = 0; i < sizeof(T) / sizeof(uint32_t); i++) {
      d[i] = death_pattern;
    }
  }

  bool is_death_pattern() {
    uint32_t* d = reinterpret_cast<uint32_t*>(_mem);
    for (size_t i = 0; i < sizeof(T) / sizeof(uint32_t); i++) {
      if (d[i] != death_pattern) return false;
    }
    return true;
  }
#endif

  T* as() {
    return reinterpret_cast<T*>(this);
  }
  T* operator->() {
    assert(!is_death_pattern(), "Potential access to uninitialized memory");
    return as();
  }
};

#endif // SHARE_UTILITIES_STATICAREA_HPP
