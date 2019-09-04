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

// No constraint emitting
void emit_constraint_no(...)                            { /* NOP */ }

// No constraint emitting if function argument is NOT provided
void emit_constraint_bool(const JVMFlag* /*flag*/)      { /* NOP */ }
void emit_constraint_ccstr(const JVMFlag* /*flag*/)     { /* NOP */ }
void emit_constraint_ccstrlist(const JVMFlag* /*flag*/) { /* NOP */ }
void emit_constraint_int(const JVMFlag* /*flag*/)       { /* NOP */ }
void emit_constraint_intx(const JVMFlag* /*flag*/)      { /* NOP */ }
void emit_constraint_uint(const JVMFlag* /*flag*/)      { /* NOP */ }
void emit_constraint_uintx(const JVMFlag* /*flag*/)     { /* NOP */ }
void emit_constraint_uint64_t(const JVMFlag* /*flag*/)  { /* NOP */ }
void emit_constraint_size_t(const JVMFlag* /*flag*/)    { /* NOP */ }
void emit_constraint_double(const JVMFlag* /*flag*/)    { /* NOP */ }

// JVMFlagConstraint emitting code functions if function argument is provided
void emit_constraint_bool(const JVMFlag* flag, JVMFlagConstraintFunc_bool func, JVMFlagConstraint::ConstraintType type) {
  JVMFlagConstraintList::add(new JVMFlagConstraint_bool(flag, func, type));
}
void emit_constraint_int(const JVMFlag* flag, JVMFlagConstraintFunc_int func, JVMFlagConstraint::ConstraintType type) {
  JVMFlagConstraintList::add(new JVMFlagConstraint_int(flag, func, type));
}
void emit_constraint_intx(const JVMFlag* flag, JVMFlagConstraintFunc_intx func, JVMFlagConstraint::ConstraintType type) {
  JVMFlagConstraintList::add(new JVMFlagConstraint_intx(flag, func, type));
}
void emit_constraint_uint(const JVMFlag* flag, JVMFlagConstraintFunc_uint func, JVMFlagConstraint::ConstraintType type) {
  JVMFlagConstraintList::add(new JVMFlagConstraint_uint(flag, func, type));
}
void emit_constraint_uintx(const JVMFlag* flag, JVMFlagConstraintFunc_uintx func, JVMFlagConstraint::ConstraintType type) {
  JVMFlagConstraintList::add(new JVMFlagConstraint_uintx(flag, func, type));
}
void emit_constraint_uint64_t(const JVMFlag* flag, JVMFlagConstraintFunc_uint64_t func, JVMFlagConstraint::ConstraintType type) {
  JVMFlagConstraintList::add(new JVMFlagConstraint_uint64_t(flag, func, type));
}
void emit_constraint_size_t(const JVMFlag* flag, JVMFlagConstraintFunc_size_t func, JVMFlagConstraint::ConstraintType type) {
  JVMFlagConstraintList::add(new JVMFlagConstraint_size_t(flag, func, type));
}
void emit_constraint_double(const JVMFlag* flag, JVMFlagConstraintFunc_double func, JVMFlagConstraint::ConstraintType type) {
  JVMFlagConstraintList::add(new JVMFlagConstraint_double(flag, func, type));
}

// Generate code to call emit_constraint_xxx function
#define EMIT_CONSTRAINT_START       (void)(0
#define EMIT_CONSTRAINT(type, name) ); emit_constraint_##type(JVMFlagEx::flag_from_enum(FLAG_MEMBER_ENUM(name))
#define EMIT_CONSTRAINT_NO          ); emit_constraint_no(0
#define EMIT_CONSTRAINT_PRODUCT_FLAG(type, name, value, doc)      EMIT_CONSTRAINT(type, name)
#define EMIT_CONSTRAINT_DIAGNOSTIC_FLAG(type, name, value, doc)   EMIT_CONSTRAINT(type, name)
#define EMIT_CONSTRAINT_EXPERIMENTAL_FLAG(type, name, value, doc) EMIT_CONSTRAINT(type, name)
#define EMIT_CONSTRAINT_MANAGEABLE_FLAG(type, name, value, doc)   EMIT_CONSTRAINT(type, name)
#define EMIT_CONSTRAINT_PRODUCT_RW_FLAG(type, name, value, doc)   EMIT_CONSTRAINT(type, name)
#define EMIT_CONSTRAINT_PD_PRODUCT_FLAG(type, name, doc)          EMIT_CONSTRAINT(type, name)
#define EMIT_CONSTRAINT_PD_DIAGNOSTIC_FLAG(type, name, doc)       EMIT_CONSTRAINT(type, name)
#ifndef PRODUCT
#define EMIT_CONSTRAINT_DEVELOPER_FLAG(type, name, value, doc)    EMIT_CONSTRAINT(type, name)
#define EMIT_CONSTRAINT_PD_DEVELOPER_FLAG(type, name, doc)        EMIT_CONSTRAINT(type, name)
#define EMIT_CONSTRAINT_NOTPRODUCT_FLAG(type, name, value, doc)   EMIT_CONSTRAINT(type, name)
#else
#define EMIT_CONSTRAINT_DEVELOPER_FLAG(type, name, value, doc)    EMIT_CONSTRAINT_NO
#define EMIT_CONSTRAINT_PD_DEVELOPER_FLAG(type, name, doc)        EMIT_CONSTRAINT_NO
#define EMIT_CONSTRAINT_NOTPRODUCT_FLAG(type, name, value, doc)   EMIT_CONSTRAINT_NO
#endif
#ifdef _LP64
#define EMIT_CONSTRAINT_LP64_PRODUCT_FLAG(type, name, value, doc) EMIT_CONSTRAINT(type, name)
#else
#define EMIT_CONSTRAINT_LP64_PRODUCT_FLAG(type, name, value, doc) EMIT_CONSTRAINT_NO
#endif
#define EMIT_CONSTRAINT_END         );

// Generate func argument to pass into emit_constraint_xxx functions
#define EMIT_CONSTRAINT_CHECK(func, type)                         , func, JVMFlagConstraint::type

// the "name" argument must be a string literal
#define INITIAL_CONSTRAINTS_SIZE 72
GrowableArray<JVMFlagConstraint*>* JVMFlagConstraintList::_constraints = NULL;
JVMFlagConstraint::ConstraintType JVMFlagConstraintList::_validating_type = JVMFlagConstraint::AtParse;

// Check the ranges of all flags that have them or print them out and exit if requested
void JVMFlagConstraintList::init(void) {
  _constraints = new (ResourceObj::C_HEAP, mtArguments) GrowableArray<JVMFlagConstraint*>(INITIAL_CONSTRAINTS_SIZE, true);

  EMIT_CONSTRAINT_START

  ALL_FLAGS(EMIT_CONSTRAINT_DEVELOPER_FLAG,
            EMIT_CONSTRAINT_PD_DEVELOPER_FLAG,
            EMIT_CONSTRAINT_PRODUCT_FLAG,
            EMIT_CONSTRAINT_PD_PRODUCT_FLAG,
            EMIT_CONSTRAINT_DIAGNOSTIC_FLAG,
            EMIT_CONSTRAINT_PD_DIAGNOSTIC_FLAG,
            EMIT_CONSTRAINT_EXPERIMENTAL_FLAG,
            EMIT_CONSTRAINT_NOTPRODUCT_FLAG,
            EMIT_CONSTRAINT_MANAGEABLE_FLAG,
            EMIT_CONSTRAINT_PRODUCT_RW_FLAG,
            EMIT_CONSTRAINT_LP64_PRODUCT_FLAG,
            IGNORE_RANGE,
            EMIT_CONSTRAINT_CHECK,
            IGNORE_WRITEABLE)

  EMIT_CONSTRAINT_END
}

JVMFlagConstraint* JVMFlagConstraintList::find(const JVMFlag* flag) {
  JVMFlagConstraint* found = NULL;
  for (int i=0; i<length(); i++) {
    JVMFlagConstraint* constraint = at(i);
    if (constraint->flag() == flag) {
      found = constraint;
      break;
    }
  }
  return found;
}

// Find constraints and return only if found constraint's type is equal or lower than current validating type.
JVMFlagConstraint* JVMFlagConstraintList::find_if_needs_check(const JVMFlag* flag) {
  JVMFlagConstraint* found = NULL;
  JVMFlagConstraint* constraint = find(flag);
  if (constraint != NULL && (constraint->type() <= _validating_type)) {
    found = constraint;
  }
  return found;
}

// Check constraints for specific constraint type.
bool JVMFlagConstraintList::check_constraints(JVMFlagConstraint::ConstraintType type) {
  guarantee(type > _validating_type, "Constraint check is out of order.");
  _validating_type = type;

  bool status = true;
  for (int i=0; i<length(); i++) {
    JVMFlagConstraint* constraint = at(i);
    if (type != constraint->type()) continue;
    if (constraint->apply(true) != JVMFlag::SUCCESS) status = false;
  }
  return status;
}
