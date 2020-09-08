/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_FLAGS_JVMFLAGLOOKUP_HPP
#define SHARE_RUNTIME_FLAGS_JVMFLAGLOOKUP_HPP

#include "runtime/globals_extension.hpp"
#include "runtime/flags/jvmFlag.hpp"

class JVMFlagLookup {
  static const int NUM_BUCKETS = 277;
  short _buckets[NUM_BUCKETS];
  short _table[NUM_JVMFlagsEnum];
  u2    _hashes[NUM_JVMFlagsEnum];

  static constexpr unsigned int hash_code(const char* s) {
    unsigned int h = 0;
    while (*s != 0) {
      h = 31*h + (unsigned int) *s;
      s++;
    }
    return h;
  }

  static constexpr unsigned int hash_code(const char* s, size_t len) {
    unsigned int h = 0;
    while (len -- > 0) {
      h = 31*h + (unsigned int) *s;
      s++;
    }
    return h;
  }

  JVMFlag* find_impl(const char* flag_name, size_t length) const;

public:
  constexpr JVMFlagLookup();
  static JVMFlag* find(const char* flag_name, size_t length);
};

#endif // SHARE_RUNTIME_FLAGS_JVMFLAGLOOKUP_HPP
