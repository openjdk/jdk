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

#ifndef SHARE_VM_RUNTIME_COMMANDLINEFLAGCONSTRAINTLIST_HPP
#define SHARE_VM_RUNTIME_COMMANDLINEFLAGCONSTRAINTLIST_HPP

#include "runtime/globals.hpp"
#include "utilities/growableArray.hpp"

/*
 * Here we have a mechanism for extracting constraints (as custom functions) for flags,
 * which otherwise can not be expressed via simple range check, specified in flag macro tables.
 *
 * An example of a constraint is "flag1 < flag2" where both flag1 and flag2 can change.
 *
 * See runtime "runtime/commandLineFlagConstraintsCompiler.hpp",
 * "runtime/commandLineFlagConstraintsGC.hpp" and
 * "runtime/commandLineFlagConstraintsRuntime.hpp" for the functions themselves.
 */

typedef Flag::Error (*CommandLineFlagConstraintFunc_bool)(bool verbose, bool* value);
typedef Flag::Error (*CommandLineFlagConstraintFunc_int)(bool verbose, int* value);
typedef Flag::Error (*CommandLineFlagConstraintFunc_intx)(bool verbose, intx* value);
typedef Flag::Error (*CommandLineFlagConstraintFunc_uint)(bool verbose, uint* value);
typedef Flag::Error (*CommandLineFlagConstraintFunc_uintx)(bool verbose, uintx* value);
typedef Flag::Error (*CommandLineFlagConstraintFunc_uint64_t)(bool verbose, uint64_t* value);
typedef Flag::Error (*CommandLineFlagConstraintFunc_size_t)(bool verbose, size_t* value);
typedef Flag::Error (*CommandLineFlagConstraintFunc_double)(bool verbose, double* value);

class CommandLineFlagConstraint : public CHeapObj<mtInternal> {
private:
  const char* _name;
public:
  // the "name" argument must be a string literal
  CommandLineFlagConstraint(const char* name) { _name=name; };
  ~CommandLineFlagConstraint() {};
  const char* name() { return _name; }
  virtual Flag::Error apply_bool(bool* value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; };
  virtual Flag::Error apply_int(int* value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; };
  virtual Flag::Error apply_intx(intx* value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; };
  virtual Flag::Error apply_uint(uint* value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; };
  virtual Flag::Error apply_uintx(uintx* value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; };
  virtual Flag::Error apply_uint64_t(uint64_t* value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; };
  virtual Flag::Error apply_size_t(size_t* value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; };
  virtual Flag::Error apply_double(double* value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; };
};

class CommandLineFlagConstraintList : public AllStatic {
private:
  static GrowableArray<CommandLineFlagConstraint*>* _constraints;
public:
  static void init();
  static int length() { return (_constraints != NULL) ? _constraints->length() : 0; }
  static CommandLineFlagConstraint* at(int i) { return (_constraints != NULL) ? _constraints->at(i) : NULL; }
  static CommandLineFlagConstraint* find(const char* name);
  static void add(CommandLineFlagConstraint* constraint) { _constraints->append(constraint); }
};

#endif /* SHARE_VM_RUNTIME_COMMANDLINEFLAGCONSTRAINTLIST_HPP */
