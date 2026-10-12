/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_RUNTIME_STRIPED_MUTEX_HPP
#define SHARE_RUNTIME_STRIPED_MUTEX_HPP

#include "runtime/mutex.hpp"

class StripedMutex : public CHeapObj<mtSynchronizer> {
private:
  const uint32_t _num_stripes;
  const uint32_t _stripe_mask; // _num_stripes - 1
  char*          _stripes_base;
  PaddedMutex*   _stripes;

  inline static uintptr_t hash(const uintptr_t key);

public:
  StripedMutex();
  ~StripedMutex();

  template <typename T>
  Mutex* get(T* key) {
    const uintptr_t h = StripedMutex::hash((uintptr_t) key);
    const uint32_t index = static_cast<uint32_t>(h) & _stripe_mask;
    return &_stripes[index];
  }
};


#endif // SHARE_RUNTIME_STRIPED_MUTEX_HPP
