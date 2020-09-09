/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "gc/shared/jvmFlagConstraintsGC.hpp"
#include "runtime/arguments.hpp"
#include "runtime/flags/jvmFlag.hpp"
#include "runtime/flags/jvmFlagConstraintList.hpp"
#include "runtime/flags/jvmFlagConstraintsCompiler.hpp"
#include "runtime/flags/jvmFlagConstraintsRuntime.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/os.hpp"
#include "utilities/macros.hpp"

class JVMFlagConstraint_bool : public JVMFlagConstraint {
  JVMFlagConstraintFunc_bool _constraint;

public:
  JVMFlagConstraint_bool(const JVMFlag* flag,
                         JVMFlagConstraintFunc_bool func,
                         ConstraintType type) : JVMFlagConstraint(flag, type), _constraint(func) {}

  JVMFlag::Error apply(bool verbose) {
    return _constraint(_flag->get_bool(), verbose);
  }

  JVMFlag::Error apply_bool(bool value, bool verbose) {
    return _constraint(value, verbose);
  }
};

class JVMFlagConstraint_int : public JVMFlagConstraint {
  JVMFlagConstraintFunc_int _constraint;

public:
  JVMFlagConstraint_int(const JVMFlag* flag,
                        JVMFlagConstraintFunc_int func,
                        ConstraintType type) : JVMFlagConstraint(flag, type), _constraint(func) {}

  JVMFlag::Error apply(bool verbose) {
    return _constraint(_flag->get_int(), verbose);
  }

  JVMFlag::Error apply_int(int value, bool verbose) {
    return _constraint(value, verbose);
  }
};

class JVMFlagConstraint_intx : public JVMFlagConstraint {
  JVMFlagConstraintFunc_intx _constraint;

public:
  JVMFlagConstraint_intx(const JVMFlag* flag,
                         JVMFlagConstraintFunc_intx func,
                         ConstraintType type) : JVMFlagConstraint(flag, type), _constraint(func) {}

  JVMFlag::Error apply(bool verbose) {
    return _constraint(_flag->get_intx(), verbose);
  }

  JVMFlag::Error apply_intx(intx value, bool verbose) {
    return _constraint(value, verbose);
  }
};

class JVMFlagConstraint_uint : public JVMFlagConstraint {
  JVMFlagConstraintFunc_uint _constraint;

public:
  JVMFlagConstraint_uint(const JVMFlag* flag,
                         JVMFlagConstraintFunc_uint func,
                         ConstraintType type) : JVMFlagConstraint(flag, type), _constraint(func) {}

  JVMFlag::Error apply(bool verbose) {
    return _constraint(_flag->get_uint(), verbose);
  }

  JVMFlag::Error apply_uint(uint value, bool verbose) {
    return _constraint(value, verbose);
  }
};

class JVMFlagConstraint_uintx : public JVMFlagConstraint {
  JVMFlagConstraintFunc_uintx _constraint;

public:
  JVMFlagConstraint_uintx(const JVMFlag* flag,
                          JVMFlagConstraintFunc_uintx func,
                          ConstraintType type) : JVMFlagConstraint(flag, type), _constraint(func) {}

  JVMFlag::Error apply(bool verbose) {
    return _constraint(_flag->get_uintx(), verbose);
  }

  JVMFlag::Error apply_uintx(uintx value, bool verbose) {
    return _constraint(value, verbose);
  }
};

class JVMFlagConstraint_uint64_t : public JVMFlagConstraint {
  JVMFlagConstraintFunc_uint64_t _constraint;

public:
  JVMFlagConstraint_uint64_t(const JVMFlag* flag,
                             JVMFlagConstraintFunc_uint64_t func,
                             ConstraintType type) : JVMFlagConstraint(flag, type), _constraint(func) {}

  JVMFlag::Error apply(bool verbose) {
    return _constraint(_flag->get_uint64_t(), verbose);
  }

  JVMFlag::Error apply_uint64_t(uint64_t value, bool verbose) {
    return _constraint(value, verbose);
  }
};

class JVMFlagConstraint_size_t : public JVMFlagConstraint {
  JVMFlagConstraintFunc_size_t _constraint;

public:
  JVMFlagConstraint_size_t(const JVMFlag* flag,
                           JVMFlagConstraintFunc_size_t func,
                           ConstraintType type) : JVMFlagConstraint(flag, type), _constraint(func) {}

  JVMFlag::Error apply(bool verbose) {
    return _constraint(_flag->get_size_t(), verbose);
  }

  JVMFlag::Error apply_size_t(size_t value, bool verbose) {
    return _constraint(value, verbose);
  }
};

class JVMFlagConstraint_double : public JVMFlagConstraint {
  JVMFlagConstraintFunc_double _constraint;

public:
  JVMFlagConstraint_double(const JVMFlag* flag,
                           JVMFlagConstraintFunc_double func,
                           ConstraintType type) : JVMFlagConstraint(flag, type), _constraint(func) {}

  JVMFlag::Error apply(bool verbose) {
    return _constraint(_flag->get_double(), verbose);
  }

  JVMFlag::Error apply_double(double value, bool verbose) {
    return _constraint(value, verbose);
  }
};

#define DEFINE_CONSTRAINT_APPLY(T) \
JVMFlag::Error JVMFlagConstraintChecker::apply_ ## T(T value, bool verbose) const {           \
  assert(exists(), "must be");                                                                \
  JVMFlagConstraint_ ## T constraint(_flag,                                                   \
                                     (JVMFlagConstraintFunc_ ## T)_limit->constraint_func(),  \
                                     (JVMFlagConstraint::ConstraintType)_limit->phase());     \
  return constraint.apply_ ## T(value, verbose);                                              \
}

ALL_CONSTRAINT_TYPES(DEFINE_CONSTRAINT_APPLY)


JVMFlag::Error JVMFlagConstraintChecker::apply(bool verbose) const {
#define APPLY_CONSTRAINT(T)                                                                     \
  if (_flag->is_ ## T()) {                                                                      \
    JVMFlagConstraint_ ## T constraint(_flag,                                                   \
                                       (JVMFlagConstraintFunc_ ## T)_limit->constraint_func(),  \
                                       (JVMFlagConstraint::ConstraintType)_limit->phase());     \
    return constraint.apply(verbose);                                                           \
  }

  ALL_CONSTRAINT_TYPES(APPLY_CONSTRAINT);

  ShouldNotReachHere();
  return JVMFlag::INVALID_FLAG;
}


JVMFlagConstraint::ConstraintType JVMFlagConstraintList::_validating_type = JVMFlagConstraint::AtParse;

// Find constraints and return only if found constraint's type is equal or lower than current validating type.
JVMFlagConstraintChecker JVMFlagConstraintList::find_if_needs_check(const JVMFlag* flag) {
  JVMFlagConstraintChecker constraint = JVMFlagConstraintList::find(flag);
  if (constraint.exists() && (constraint.type() <= _validating_type)) {
    return constraint;
  }
  return JVMFlagConstraintChecker(flag, NULL);
}

// Check constraints for specific constraint type.
bool JVMFlagConstraintList::check_constraints(JVMFlagConstraint::ConstraintType type) {
  guarantee(type > _validating_type, "Constraint check is out of order.");
  _validating_type = type;

  bool status = true;
  for (int i = 0; i < NUM_JVMFlagsEnum; i++) {
    JVMFlagConstraintChecker constraint(&JVMFlag::flags[i], JVMFlagLimit::get_constraint_at(i));
    if (!constraint.exists()) continue;
    if (type != constraint.type()) continue;
    if (constraint.apply(true) != JVMFlag::SUCCESS) status = false;
  }
  return status;
}
