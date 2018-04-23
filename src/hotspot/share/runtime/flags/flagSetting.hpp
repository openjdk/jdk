/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_FLAGS_FLAGSETTING_HPP
#define SHARE_VM_RUNTIME_FLAGS_FLAGSETTING_HPP

#include "memory/allocation.hpp"

// debug flags control various aspects of the VM and are global accessible

// use FlagSetting to temporarily change some debug flag
// e.g. FlagSetting fs(DebugThisAndThat, true);
// restored to previous value upon leaving scope
class FlagSetting : public StackObj {
  bool val;
  bool* flag;
public:
  FlagSetting(bool& fl, bool newValue) { flag = &fl; val = fl; fl = newValue; }
  ~FlagSetting()                       { *flag = val; }
};

class UIntFlagSetting : public StackObj {
  uint val;
  uint* flag;
public:
  UIntFlagSetting(uint& fl, uint newValue) { flag = &fl; val = fl; fl = newValue; }
  ~UIntFlagSetting()                       { *flag = val; }
};

class SizeTFlagSetting : public StackObj {
  size_t val;
  size_t* flag;
public:
  SizeTFlagSetting(size_t& fl, size_t newValue) { flag = &fl; val = fl; fl = newValue; }
  ~SizeTFlagSetting()                           { *flag = val; }
};

// Helper class for temporarily saving the value of a flag during a scope.
template <size_t SIZE>
class FlagGuard {
  unsigned char _value[SIZE];
  void* const _addr;
public:
  FlagGuard(void* flag_addr) : _addr(flag_addr) { memcpy(_value, _addr, SIZE); }
  ~FlagGuard()                                  { memcpy(_addr, _value, SIZE); }
};

#define FLAG_GUARD(f) FlagGuard<sizeof(f)> f ## _guard(&f)

#endif // SHARE_VM_RUNTIME_FLAGS_FLAGSETTING_HPP
