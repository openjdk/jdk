/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

typedef Flag::Error (*CommandLineFlagConstraintFunc_bool)(bool value, bool verbose);
typedef Flag::Error (*CommandLineFlagConstraintFunc_int)(int value, bool verbose);
typedef Flag::Error (*CommandLineFlagConstraintFunc_intx)(intx value, bool verbose);
typedef Flag::Error (*CommandLineFlagConstraintFunc_uint)(uint value, bool verbose);
typedef Flag::Error (*CommandLineFlagConstraintFunc_uintx)(uintx value, bool verbose);
typedef Flag::Error (*CommandLineFlagConstraintFunc_uint64_t)(uint64_t value, bool verbose);
typedef Flag::Error (*CommandLineFlagConstraintFunc_size_t)(size_t value, bool verbose);
typedef Flag::Error (*CommandLineFlagConstraintFunc_double)(double value, bool verbose);

class CommandLineFlagConstraint : public CHeapObj<mtArguments> {
public:
  // During VM initialization, constraint validation will be done order of ConstraintType.
  enum ConstraintType {
    // Will be validated during argument processing (Arguments::parse_argument).
    AtParse         = 0,
    // Will be validated inside Threads::create_vm(), right after Arguments::apply_ergo().
    AfterErgo       = 1,
    // Will be validated inside universe_init(), right after Metaspace::global_initialize().
    AfterMemoryInit = 2
  };

private:
  const char* _name;
  ConstraintType _validate_type;

public:
  // the "name" argument must be a string literal
  CommandLineFlagConstraint(const char* name, ConstraintType type) { _name=name; _validate_type=type; };
  ~CommandLineFlagConstraint() {};
  const char* name() const { return _name; }
  ConstraintType type() const { return _validate_type; }
  virtual Flag::Error apply_bool(bool value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; };
  virtual Flag::Error apply_int(int value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; };
  virtual Flag::Error apply_intx(intx value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; };
  virtual Flag::Error apply_uint(uint value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; };
  virtual Flag::Error apply_uintx(uintx value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; };
  virtual Flag::Error apply_uint64_t(uint64_t value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; };
  virtual Flag::Error apply_size_t(size_t value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; };
  virtual Flag::Error apply_double(double value, bool verbose = true) { ShouldNotReachHere(); return Flag::ERR_OTHER; };
};

class CommandLineFlagConstraintList : public AllStatic {
private:
  static GrowableArray<CommandLineFlagConstraint*>* _constraints;
  // Latest constraint validation type.
  static CommandLineFlagConstraint::ConstraintType _validating_type;
public:
  static void init();
  static int length() { return (_constraints != NULL) ? _constraints->length() : 0; }
  static CommandLineFlagConstraint* at(int i) { return (_constraints != NULL) ? _constraints->at(i) : NULL; }
  static CommandLineFlagConstraint* find(const char* name);
  static CommandLineFlagConstraint* find_if_needs_check(const char* name);
  static void add(CommandLineFlagConstraint* constraint) { _constraints->append(constraint); }
  // True if 'AfterErgo' or later constraint functions are validated.
  static bool validated_after_ergo() { return _validating_type >= CommandLineFlagConstraint::AfterErgo; };
  static bool check_constraints(CommandLineFlagConstraint::ConstraintType type);
};

#endif /* SHARE_VM_RUNTIME_COMMANDLINEFLAGCONSTRAINTLIST_HPP */
