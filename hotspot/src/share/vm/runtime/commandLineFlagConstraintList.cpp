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

#include "precompiled.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "runtime/arguments.hpp"
#include "runtime/commandLineFlagConstraintList.hpp"
#include "runtime/commandLineFlagConstraintsCompiler.hpp"
#include "runtime/commandLineFlagConstraintsGC.hpp"
#include "runtime/commandLineFlagConstraintsRuntime.hpp"
#include "runtime/os.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_JVMCI
#include "jvmci/commandLineFlagConstraintsJVMCI.hpp"
#endif

class CommandLineFlagConstraint_bool : public CommandLineFlagConstraint {
  CommandLineFlagConstraintFunc_bool _constraint;

public:
  // the "name" argument must be a string literal
  CommandLineFlagConstraint_bool(const char* name,
                                 CommandLineFlagConstraintFunc_bool func,
                                 ConstraintType type) : CommandLineFlagConstraint(name, type) {
    _constraint=func;
  }

  Flag::Error apply_bool(bool value, bool verbose) {
    return _constraint(value, verbose);
  }
};

class CommandLineFlagConstraint_int : public CommandLineFlagConstraint {
  CommandLineFlagConstraintFunc_int _constraint;

public:
  // the "name" argument must be a string literal
  CommandLineFlagConstraint_int(const char* name,
                                CommandLineFlagConstraintFunc_int func,
                                ConstraintType type) : CommandLineFlagConstraint(name, type) {
    _constraint=func;
  }

  Flag::Error apply_int(int value, bool verbose) {
    return _constraint(value, verbose);
  }
};

class CommandLineFlagConstraint_intx : public CommandLineFlagConstraint {
  CommandLineFlagConstraintFunc_intx _constraint;

public:
  // the "name" argument must be a string literal
  CommandLineFlagConstraint_intx(const char* name,
                                 CommandLineFlagConstraintFunc_intx func,
                                 ConstraintType type) : CommandLineFlagConstraint(name, type) {
    _constraint=func;
  }

  Flag::Error apply_intx(intx value, bool verbose) {
    return _constraint(value, verbose);
  }
};

class CommandLineFlagConstraint_uint : public CommandLineFlagConstraint {
  CommandLineFlagConstraintFunc_uint _constraint;

public:
  // the "name" argument must be a string literal
  CommandLineFlagConstraint_uint(const char* name,
                                 CommandLineFlagConstraintFunc_uint func,
                                 ConstraintType type) : CommandLineFlagConstraint(name, type) {
    _constraint=func;
  }

  Flag::Error apply_uint(uint value, bool verbose) {
    return _constraint(value, verbose);
  }
};

class CommandLineFlagConstraint_uintx : public CommandLineFlagConstraint {
  CommandLineFlagConstraintFunc_uintx _constraint;

public:
  // the "name" argument must be a string literal
  CommandLineFlagConstraint_uintx(const char* name,
                                  CommandLineFlagConstraintFunc_uintx func,
                                  ConstraintType type) : CommandLineFlagConstraint(name, type) {
    _constraint=func;
  }

  Flag::Error apply_uintx(uintx value, bool verbose) {
    return _constraint(value, verbose);
  }
};

class CommandLineFlagConstraint_uint64_t : public CommandLineFlagConstraint {
  CommandLineFlagConstraintFunc_uint64_t _constraint;

public:
  // the "name" argument must be a string literal
  CommandLineFlagConstraint_uint64_t(const char* name,
                                     CommandLineFlagConstraintFunc_uint64_t func,
                                     ConstraintType type) : CommandLineFlagConstraint(name, type) {
    _constraint=func;
  }

  Flag::Error apply_uint64_t(uint64_t value, bool verbose) {
    return _constraint(value, verbose);
  }
};

class CommandLineFlagConstraint_size_t : public CommandLineFlagConstraint {
  CommandLineFlagConstraintFunc_size_t _constraint;

public:
  // the "name" argument must be a string literal
  CommandLineFlagConstraint_size_t(const char* name,
                                   CommandLineFlagConstraintFunc_size_t func,
                                   ConstraintType type) : CommandLineFlagConstraint(name, type) {
    _constraint=func;
  }

  Flag::Error apply_size_t(size_t value, bool verbose) {
    return _constraint(value, verbose);
  }
};

class CommandLineFlagConstraint_double : public CommandLineFlagConstraint {
  CommandLineFlagConstraintFunc_double _constraint;

public:
  // the "name" argument must be a string literal
  CommandLineFlagConstraint_double(const char* name,
                                   CommandLineFlagConstraintFunc_double func,
                                   ConstraintType type) : CommandLineFlagConstraint(name, type) {
    _constraint=func;
  }

  Flag::Error apply_double(double value, bool verbose) {
    return _constraint(value, verbose);
  }
};

// No constraint emitting
void emit_constraint_no(...)                          { /* NOP */ }

// No constraint emitting if function argument is NOT provided
void emit_constraint_bool(const char* /*name*/)       { /* NOP */ }
void emit_constraint_ccstr(const char* /*name*/)      { /* NOP */ }
void emit_constraint_ccstrlist(const char* /*name*/)  { /* NOP */ }
void emit_constraint_int(const char* /*name*/)        { /* NOP */ }
void emit_constraint_intx(const char* /*name*/)       { /* NOP */ }
void emit_constraint_uint(const char* /*name*/)       { /* NOP */ }
void emit_constraint_uintx(const char* /*name*/)      { /* NOP */ }
void emit_constraint_uint64_t(const char* /*name*/)   { /* NOP */ }
void emit_constraint_size_t(const char* /*name*/)     { /* NOP */ }
void emit_constraint_double(const char* /*name*/)     { /* NOP */ }

// CommandLineFlagConstraint emitting code functions if function argument is provided
void emit_constraint_bool(const char* name, CommandLineFlagConstraintFunc_bool func, CommandLineFlagConstraint::ConstraintType type) {
  CommandLineFlagConstraintList::add(new CommandLineFlagConstraint_bool(name, func, type));
}
void emit_constraint_int(const char* name, CommandLineFlagConstraintFunc_int func, CommandLineFlagConstraint::ConstraintType type) {
  CommandLineFlagConstraintList::add(new CommandLineFlagConstraint_int(name, func, type));
}
void emit_constraint_intx(const char* name, CommandLineFlagConstraintFunc_intx func, CommandLineFlagConstraint::ConstraintType type) {
  CommandLineFlagConstraintList::add(new CommandLineFlagConstraint_intx(name, func, type));
}
void emit_constraint_uint(const char* name, CommandLineFlagConstraintFunc_uint func, CommandLineFlagConstraint::ConstraintType type) {
  CommandLineFlagConstraintList::add(new CommandLineFlagConstraint_uint(name, func, type));
}
void emit_constraint_uintx(const char* name, CommandLineFlagConstraintFunc_uintx func, CommandLineFlagConstraint::ConstraintType type) {
  CommandLineFlagConstraintList::add(new CommandLineFlagConstraint_uintx(name, func, type));
}
void emit_constraint_uint64_t(const char* name, CommandLineFlagConstraintFunc_uint64_t func, CommandLineFlagConstraint::ConstraintType type) {
  CommandLineFlagConstraintList::add(new CommandLineFlagConstraint_uint64_t(name, func, type));
}
void emit_constraint_size_t(const char* name, CommandLineFlagConstraintFunc_size_t func, CommandLineFlagConstraint::ConstraintType type) {
  CommandLineFlagConstraintList::add(new CommandLineFlagConstraint_size_t(name, func, type));
}
void emit_constraint_double(const char* name, CommandLineFlagConstraintFunc_double func, CommandLineFlagConstraint::ConstraintType type) {
  CommandLineFlagConstraintList::add(new CommandLineFlagConstraint_double(name, func, type));
}

// Generate code to call emit_constraint_xxx function
#define EMIT_CONSTRAINT_PRODUCT_FLAG(type, name, value, doc)      ); emit_constraint_##type(#name
#define EMIT_CONSTRAINT_COMMERCIAL_FLAG(type, name, value, doc)   ); emit_constraint_##type(#name
#define EMIT_CONSTRAINT_DIAGNOSTIC_FLAG(type, name, value, doc)   ); emit_constraint_##type(#name
#define EMIT_CONSTRAINT_EXPERIMENTAL_FLAG(type, name, value, doc) ); emit_constraint_##type(#name
#define EMIT_CONSTRAINT_MANAGEABLE_FLAG(type, name, value, doc)   ); emit_constraint_##type(#name
#define EMIT_CONSTRAINT_PRODUCT_RW_FLAG(type, name, value, doc)   ); emit_constraint_##type(#name
#define EMIT_CONSTRAINT_PD_PRODUCT_FLAG(type, name, doc)          ); emit_constraint_##type(#name
#define EMIT_CONSTRAINT_DEVELOPER_FLAG(type, name, value, doc)    ); emit_constraint_##type(#name
#define EMIT_CONSTRAINT_PD_DEVELOPER_FLAG(type, name, doc)        ); emit_constraint_##type(#name
#define EMIT_CONSTRAINT_NOTPRODUCT_FLAG(type, name, value, doc)   ); emit_constraint_##type(#name
#define EMIT_CONSTRAINT_LP64_PRODUCT_FLAG(type, name, value, doc) ); emit_constraint_##type(#name

// Generate func argument to pass into emit_constraint_xxx functions
#define EMIT_CONSTRAINT_CHECK(func, type)                               , func, CommandLineFlagConstraint::type

// the "name" argument must be a string literal
#define INITIAL_CONSTRAINTS_SIZE 72
GrowableArray<CommandLineFlagConstraint*>* CommandLineFlagConstraintList::_constraints = NULL;
CommandLineFlagConstraint::ConstraintType CommandLineFlagConstraintList::_validating_type = CommandLineFlagConstraint::AtParse;

// Check the ranges of all flags that have them or print them out and exit if requested
void CommandLineFlagConstraintList::init(void) {
  _constraints = new (ResourceObj::C_HEAP, mtInternal) GrowableArray<CommandLineFlagConstraint*>(INITIAL_CONSTRAINTS_SIZE, true);

  emit_constraint_no(NULL RUNTIME_FLAGS(EMIT_CONSTRAINT_DEVELOPER_FLAG,
                                        EMIT_CONSTRAINT_PD_DEVELOPER_FLAG,
                                        EMIT_CONSTRAINT_PRODUCT_FLAG,
                                        EMIT_CONSTRAINT_PD_PRODUCT_FLAG,
                                        EMIT_CONSTRAINT_DIAGNOSTIC_FLAG,
                                        EMIT_CONSTRAINT_EXPERIMENTAL_FLAG,
                                        EMIT_CONSTRAINT_NOTPRODUCT_FLAG,
                                        EMIT_CONSTRAINT_MANAGEABLE_FLAG,
                                        EMIT_CONSTRAINT_PRODUCT_RW_FLAG,
                                        EMIT_CONSTRAINT_LP64_PRODUCT_FLAG,
                                        IGNORE_RANGE,
                                        EMIT_CONSTRAINT_CHECK));

  EMIT_CONSTRAINTS_FOR_GLOBALS_EXT

  emit_constraint_no(NULL ARCH_FLAGS(EMIT_CONSTRAINT_DEVELOPER_FLAG,
                                     EMIT_CONSTRAINT_PRODUCT_FLAG,
                                     EMIT_CONSTRAINT_DIAGNOSTIC_FLAG,
                                     EMIT_CONSTRAINT_EXPERIMENTAL_FLAG,
                                     EMIT_CONSTRAINT_NOTPRODUCT_FLAG,
                                     IGNORE_RANGE,
                                     EMIT_CONSTRAINT_CHECK));

#if INCLUDE_JVMCI
  emit_constraint_no(NULL JVMCI_FLAGS(EMIT_CONSTRAINT_DEVELOPER_FLAG,
                                      EMIT_CONSTRAINT_PD_DEVELOPER_FLAG,
                                      EMIT_CONSTRAINT_PRODUCT_FLAG,
                                      EMIT_CONSTRAINT_PD_PRODUCT_FLAG,
                                      EMIT_CONSTRAINT_DIAGNOSTIC_FLAG,
                                      EMIT_CONSTRAINT_EXPERIMENTAL_FLAG,
                                      EMIT_CONSTRAINT_NOTPRODUCT_FLAG,
                                      IGNORE_RANGE,
                                      EMIT_CONSTRAINT_CHECK));
#endif // INCLUDE_JVMCI

#ifdef COMPILER1
  emit_constraint_no(NULL C1_FLAGS(EMIT_CONSTRAINT_DEVELOPER_FLAG,
                                   EMIT_CONSTRAINT_PD_DEVELOPER_FLAG,
                                   EMIT_CONSTRAINT_PRODUCT_FLAG,
                                   EMIT_CONSTRAINT_PD_PRODUCT_FLAG,
                                   EMIT_CONSTRAINT_DIAGNOSTIC_FLAG,
                                   EMIT_CONSTRAINT_NOTPRODUCT_FLAG,
                                   IGNORE_RANGE,
                                   EMIT_CONSTRAINT_CHECK));
#endif // COMPILER1

#ifdef COMPILER2
  emit_constraint_no(NULL C2_FLAGS(EMIT_CONSTRAINT_DEVELOPER_FLAG,
                                   EMIT_CONSTRAINT_PD_DEVELOPER_FLAG,
                                   EMIT_CONSTRAINT_PRODUCT_FLAG,
                                   EMIT_CONSTRAINT_PD_PRODUCT_FLAG,
                                   EMIT_CONSTRAINT_DIAGNOSTIC_FLAG,
                                   EMIT_CONSTRAINT_EXPERIMENTAL_FLAG,
                                   EMIT_CONSTRAINT_NOTPRODUCT_FLAG,
                                   IGNORE_RANGE,
                                   EMIT_CONSTRAINT_CHECK));
#endif // COMPILER2

#if INCLUDE_ALL_GCS
  emit_constraint_no(NULL G1_FLAGS(EMIT_CONSTRAINT_DEVELOPER_FLAG,
                                   EMIT_CONSTRAINT_PD_DEVELOPER_FLAG,
                                   EMIT_CONSTRAINT_PRODUCT_FLAG,
                                   EMIT_CONSTRAINT_PD_PRODUCT_FLAG,
                                   EMIT_CONSTRAINT_DIAGNOSTIC_FLAG,
                                   EMIT_CONSTRAINT_EXPERIMENTAL_FLAG,
                                   EMIT_CONSTRAINT_NOTPRODUCT_FLAG,
                                   EMIT_CONSTRAINT_MANAGEABLE_FLAG,
                                   EMIT_CONSTRAINT_PRODUCT_RW_FLAG,
                                   IGNORE_RANGE,
                                   EMIT_CONSTRAINT_CHECK));
#endif // INCLUDE_ALL_GCS
}

// Find constraints by name and return only if found constraint's type is equal or lower than current validating type.
CommandLineFlagConstraint* CommandLineFlagConstraintList::find_if_needs_check(const char* name) {
  CommandLineFlagConstraint* found = NULL;
  for (int i=0; i<length(); i++) {
    CommandLineFlagConstraint* constraint = at(i);
    if ((strcmp(constraint->name(), name) == 0) &&
        (constraint->type() <= _validating_type)) {
      found = constraint;
      break;
    }
  }
  return found;
}

// Check constraints for specific constraint type.
bool CommandLineFlagConstraintList::check_constraints(CommandLineFlagConstraint::ConstraintType type) {
  guarantee(type > _validating_type, "Constraint check is out of order.");
  _validating_type = type;

  bool status = true;
  for (int i=0; i<length(); i++) {
    CommandLineFlagConstraint* constraint = at(i);
    if (type != constraint->type()) continue;
    const char* name = constraint->name();
    Flag* flag = Flag::find_flag(name, strlen(name), true, true);
    // We must check for NULL here as lp64_product flags on 32 bit architecture
    // can generate constraint check (despite that they are declared as constants),
    // but they will not be returned by Flag::find_flag()
    if (flag != NULL) {
      if (flag->is_bool()) {
        bool value = flag->get_bool();
        if (constraint->apply_bool(value, true) != Flag::SUCCESS) status = false;
      } else if (flag->is_int()) {
        int value = flag->get_int();
        if (constraint->apply_int(value, true) != Flag::SUCCESS) status = false;
      } else if (flag->is_uint()) {
        uint value = flag->get_uint();
        if (constraint->apply_uint(value, true) != Flag::SUCCESS) status = false;
      } else if (flag->is_intx()) {
        intx value = flag->get_intx();
        if (constraint->apply_intx(value, true) != Flag::SUCCESS) status = false;
      } else if (flag->is_uintx()) {
        uintx value = flag->get_uintx();
        if (constraint->apply_uintx(value, true) != Flag::SUCCESS) status = false;
      } else if (flag->is_uint64_t()) {
        uint64_t value = flag->get_uint64_t();
        if (constraint->apply_uint64_t(value, true) != Flag::SUCCESS) status = false;
      } else if (flag->is_size_t()) {
        size_t value = flag->get_size_t();
        if (constraint->apply_size_t(value, true) != Flag::SUCCESS) status = false;
      } else if (flag->is_double()) {
        double value = flag->get_double();
        if (constraint->apply_double(value, true) != Flag::SUCCESS) status = false;
      }
    }
  }
  return status;
}
