/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_JVMFLAGRANGELIST_HPP
#define SHARE_VM_RUNTIME_JVMFLAGRANGELIST_HPP

#include "runtime/flags/jvmFlag.hpp"
#include "utilities/growableArray.hpp"

/*
 * Here we have a mechanism for extracting ranges specified in flag macro tables.
 *
 * The specified ranges are used to verify that flags have valid values.
 *
 * An example of a range is "min <= flag <= max". Both "min" and "max" must be
 * constant and can not change. If either "min" or "max" can change,
 * then we need to use constraint instead.
 */

class JVMFlagRange : public CHeapObj<mtArguments> {
private:
  const char* _name;
public:
  // the "name" argument must be a string literal
  JVMFlagRange(const char* name) { _name=name; }
  ~JVMFlagRange() {}
  const char* name() { return _name; }
  virtual JVMFlag::Error check(bool verbose = true) { ShouldNotReachHere(); return JVMFlag::ERR_OTHER; }
  virtual JVMFlag::Error check_int(int value, bool verbose = true) { ShouldNotReachHere(); return JVMFlag::ERR_OTHER; }
  virtual JVMFlag::Error check_intx(intx value, bool verbose = true) { ShouldNotReachHere(); return JVMFlag::ERR_OTHER; }
  virtual JVMFlag::Error check_uint(uint value, bool verbose = true) { ShouldNotReachHere(); return JVMFlag::ERR_OTHER; }
  virtual JVMFlag::Error check_uintx(uintx value, bool verbose = true) { ShouldNotReachHere(); return JVMFlag::ERR_OTHER; }
  virtual JVMFlag::Error check_uint64_t(uint64_t value, bool verbose = true) { ShouldNotReachHere(); return JVMFlag::ERR_OTHER; }
  virtual JVMFlag::Error check_size_t(size_t value, bool verbose = true) { ShouldNotReachHere(); return JVMFlag::ERR_OTHER; }
  virtual JVMFlag::Error check_double(double value, bool verbose = true) { ShouldNotReachHere(); return JVMFlag::ERR_OTHER; }
  virtual void print(outputStream* st) { ; }
};

class JVMFlagRangeList : public AllStatic {
  static GrowableArray<JVMFlagRange*>* _ranges;
public:
  static void init();
  static int length() { return (_ranges != NULL) ? _ranges->length() : 0; }
  static JVMFlagRange* at(int i) { return (_ranges != NULL) ? _ranges->at(i) : NULL; }
  static JVMFlagRange* find(const char* name);
  static void add(JVMFlagRange* range) { _ranges->append(range); }
  static void print(outputStream* st, const char* name, RangeStrFunc default_range_str_func);
  // Check the final values of all flags for ranges.
  static bool check_ranges();
};

#endif // SHARE_VM_RUNTIME_JVMFLAGRANGELIST_HPP
