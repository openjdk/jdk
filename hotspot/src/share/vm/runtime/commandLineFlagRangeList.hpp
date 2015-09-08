/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_COMMANDLINEFLAGRANGELIST_HPP
#define SHARE_VM_RUNTIME_COMMANDLINEFLAGRANGELIST_HPP

#include "runtime/globals.hpp"
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

class CommandLineError : public AllStatic {
public:
  static void print(bool verbose, const char* msg, ...);
};

class CommandLineFlagRange : public CHeapObj<mtInternal> {
private:
  const char* _name;
public:
  // the "name" argument must be a string literal
  CommandLineFlagRange(const char* name) { _name=name; }
  ~CommandLineFlagRange() {}
  const char* name() { return _name; }
  virtual Flag::Error check_int(int value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; }
  virtual Flag::Error check_intx(intx value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; }
  virtual Flag::Error check_uint(uint value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; }
  virtual Flag::Error check_uintx(uintx value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; }
  virtual Flag::Error check_uint64_t(uint64_t value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; }
  virtual Flag::Error check_size_t(size_t value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; }
  virtual Flag::Error check_double(double value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; }
  virtual void print(outputStream* st) { ; }
};

class CommandLineFlagRangeList : public AllStatic {
  static GrowableArray<CommandLineFlagRange*>* _ranges;
public:
  static void init();
  static void add_globals_ext();
  static int length() { return (_ranges != NULL) ? _ranges->length() : 0; }
  static CommandLineFlagRange* at(int i) { return (_ranges != NULL) ? _ranges->at(i) : NULL; }
  static CommandLineFlagRange* find(const char* name);
  static void add(CommandLineFlagRange* range) { _ranges->append(range); }
  static void print(const char* name, outputStream* st, bool unspecified = false);
  // Check the final values of all flags for ranges.
  static bool check_ranges();
};

#endif // SHARE_VM_RUNTIME_COMMANDLINEFLAGRANGELIST_HPP
