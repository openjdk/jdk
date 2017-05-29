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

#include "precompiled.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "runtime/arguments.hpp"
#include "runtime/commandLineFlagConstraintList.hpp"
#include "runtime/commandLineFlagRangeList.hpp"
#include "runtime/os.hpp"
#include "runtime/task.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/macros.hpp"

void CommandLineError::print(bool verbose, const char* msg, ...) {
  if (verbose) {
    va_list listPointer;
    va_start(listPointer, msg);
    jio_vfprintf(defaultStream::error_stream(), msg, listPointer);
    va_end(listPointer);
  }
}

class CommandLineFlagRange_int : public CommandLineFlagRange {
  int _min;
  int _max;
  const int* _ptr;

public:
  // the "name" argument must be a string literal
  CommandLineFlagRange_int(const char* name, const int* ptr, int min, int max)
    : CommandLineFlagRange(name), _min(min), _max(max), _ptr(ptr) {}

  Flag::Error check(bool verbose = true) {
    return check_int(*_ptr, verbose);
  }

  Flag::Error check_int(int value, bool verbose = true) {
    if ((value < _min) || (value > _max)) {
      CommandLineError::print(verbose,
                              "int %s=%d is outside the allowed range "
                              "[ %d ... %d ]\n",
                              name(), value, _min, _max);
      return Flag::OUT_OF_BOUNDS;
    } else {
      return Flag::SUCCESS;
    }
  }

  void print(outputStream* st) {
    st->print("[ %-25d ... %25d ]", _min, _max);
  }
};

class CommandLineFlagRange_intx : public CommandLineFlagRange {
  intx _min;
  intx _max;
  const intx* _ptr;
public:
  // the "name" argument must be a string literal
  CommandLineFlagRange_intx(const char* name, const intx* ptr, intx min, intx max)
    : CommandLineFlagRange(name), _min(min), _max(max), _ptr(ptr) {}

  Flag::Error check(bool verbose = true) {
    return check_intx(*_ptr, verbose);
  }

  Flag::Error check_intx(intx value, bool verbose = true) {
    if ((value < _min) || (value > _max)) {
      CommandLineError::print(verbose,
                              "intx %s=" INTX_FORMAT " is outside the allowed range "
                              "[ " INTX_FORMAT " ... " INTX_FORMAT " ]\n",
                              name(), value, _min, _max);
      return Flag::OUT_OF_BOUNDS;
    } else {
      return Flag::SUCCESS;
    }
  }

  void print(outputStream* st) {
    st->print("[ " INTX_FORMAT_W(-25) " ... " INTX_FORMAT_W(25) " ]", _min, _max);
  }
};

class CommandLineFlagRange_uint : public CommandLineFlagRange {
  uint _min;
  uint _max;
  const uint* _ptr;

public:
  // the "name" argument must be a string literal
  CommandLineFlagRange_uint(const char* name, const uint* ptr, uint min, uint max)
    : CommandLineFlagRange(name), _min(min), _max(max), _ptr(ptr) {}

  Flag::Error check(bool verbose = true) {
    return check_uint(*_ptr, verbose);
  }

  Flag::Error check_uint(uint value, bool verbose = true) {
    if ((value < _min) || (value > _max)) {
      CommandLineError::print(verbose,
                              "uint %s=%u is outside the allowed range "
                              "[ %u ... %u ]\n",
                              name(), value, _min, _max);
      return Flag::OUT_OF_BOUNDS;
    } else {
      return Flag::SUCCESS;
    }
  }

  void print(outputStream* st) {
    st->print("[ %-25u ... %25u ]", _min, _max);
  }
};

class CommandLineFlagRange_uintx : public CommandLineFlagRange {
  uintx _min;
  uintx _max;
  const uintx* _ptr;

public:
  // the "name" argument must be a string literal
  CommandLineFlagRange_uintx(const char* name, const uintx* ptr, uintx min, uintx max)
    : CommandLineFlagRange(name), _min(min), _max(max), _ptr(ptr) {}

  Flag::Error check(bool verbose = true) {
    return check_uintx(*_ptr, verbose);
  }

  Flag::Error check_uintx(uintx value, bool verbose = true) {
    if ((value < _min) || (value > _max)) {
      CommandLineError::print(verbose,
                              "uintx %s=" UINTX_FORMAT " is outside the allowed range "
                              "[ " UINTX_FORMAT " ... " UINTX_FORMAT " ]\n",
                              name(), value, _min, _max);
      return Flag::OUT_OF_BOUNDS;
    } else {
      return Flag::SUCCESS;
    }
  }

  void print(outputStream* st) {
    st->print("[ " UINTX_FORMAT_W(-25) " ... " UINTX_FORMAT_W(25) " ]", _min, _max);
  }
};

class CommandLineFlagRange_uint64_t : public CommandLineFlagRange {
  uint64_t _min;
  uint64_t _max;
  const uint64_t* _ptr;

public:
  // the "name" argument must be a string literal
  CommandLineFlagRange_uint64_t(const char* name, const uint64_t* ptr, uint64_t min, uint64_t max)
    : CommandLineFlagRange(name), _min(min), _max(max), _ptr(ptr) {}

  Flag::Error check(bool verbose = true) {
    return check_uint64_t(*_ptr, verbose);
  }

  Flag::Error check_uint64_t(uint64_t value, bool verbose = true) {
    if ((value < _min) || (value > _max)) {
      CommandLineError::print(verbose,
                              "uint64_t %s=" UINT64_FORMAT " is outside the allowed range "
                              "[ " UINT64_FORMAT " ... " UINT64_FORMAT " ]\n",
                              name(), value, _min, _max);
      return Flag::OUT_OF_BOUNDS;
    } else {
      return Flag::SUCCESS;
    }
  }

  void print(outputStream* st) {
    st->print("[ " UINT64_FORMAT_W(-25) " ... " UINT64_FORMAT_W(25) " ]", _min, _max);
  }
};

class CommandLineFlagRange_size_t : public CommandLineFlagRange {
  size_t _min;
  size_t _max;
  const size_t* _ptr;

public:
  // the "name" argument must be a string literal
  CommandLineFlagRange_size_t(const char* name, const size_t* ptr, size_t min, size_t max)
    : CommandLineFlagRange(name), _min(min), _max(max), _ptr(ptr) {}

  Flag::Error check(bool verbose = true) {
    return check_size_t(*_ptr, verbose);
  }

  Flag::Error check_size_t(size_t value, bool verbose = true) {
    if ((value < _min) || (value > _max)) {
      CommandLineError::print(verbose,
                              "size_t %s=" SIZE_FORMAT " is outside the allowed range "
                              "[ " SIZE_FORMAT " ... " SIZE_FORMAT " ]\n",
                              name(), value, _min, _max);
      return Flag::OUT_OF_BOUNDS;
    } else {
      return Flag::SUCCESS;
    }
  }

  void print(outputStream* st) {
    st->print("[ " SIZE_FORMAT_W(-25) " ... " SIZE_FORMAT_W(25) " ]", _min, _max);
  }
};

class CommandLineFlagRange_double : public CommandLineFlagRange {
  double _min;
  double _max;
  const double* _ptr;

public:
  // the "name" argument must be a string literal
  CommandLineFlagRange_double(const char* name, const double* ptr, double min, double max)
    : CommandLineFlagRange(name), _min(min), _max(max), _ptr(ptr) {}

  Flag::Error check(bool verbose = true) {
    return check_double(*_ptr, verbose);
  }

  Flag::Error check_double(double value, bool verbose = true) {
    if ((value < _min) || (value > _max)) {
      CommandLineError::print(verbose,
                              "double %s=%f is outside the allowed range "
                              "[ %f ... %f ]\n",
                              name(), value, _min, _max);
      return Flag::OUT_OF_BOUNDS;
    } else {
      return Flag::SUCCESS;
    }
  }

  void print(outputStream* st) {
    st->print("[ %-25.3f ... %25.3f ]", _min, _max);
  }
};

// No constraint emitting
void emit_range_no(...)                         { /* NOP */ }

// No constraint emitting if function argument is NOT provided
void emit_range_bool(const char* /*name*/, const bool* /*value*/)            { /* NOP */ }
void emit_range_ccstr(const char* /*name*/, const ccstr* /*value*/)          { /* NOP */ }
void emit_range_ccstrlist(const char* /*name*/, const ccstrlist* /*value*/)  { /* NOP */ }
void emit_range_int(const char* /*name*/, const int* /*value*/)              { /* NOP */ }
void emit_range_intx(const char* /*name*/, const intx* /*value*/)            { /* NOP */ }
void emit_range_uint(const char* /*name*/, const uint* /*value*/)            { /* NOP */ }
void emit_range_uintx(const char* /*name*/, const uintx* /*value*/)          { /* NOP */ }
void emit_range_uint64_t(const char* /*name*/, const uint64_t* /*value*/)    { /* NOP */ }
void emit_range_size_t(const char* /*name*/, const size_t* /*value*/)        { /* NOP */ }
void emit_range_double(const char* /*name*/, const double* /*value*/)        { /* NOP */ }

// CommandLineFlagRange emitting code functions if range arguments are provided
void emit_range_int(const char* name, const int* ptr, int min, int max)       {
  CommandLineFlagRangeList::add(new CommandLineFlagRange_int(name, ptr, min, max));
}
void emit_range_intx(const char* name, const intx* ptr, intx min, intx max) {
  CommandLineFlagRangeList::add(new CommandLineFlagRange_intx(name, ptr, min, max));
}
void emit_range_uint(const char* name, const uint* ptr, uint min, uint max) {
  CommandLineFlagRangeList::add(new CommandLineFlagRange_uint(name, ptr, min, max));
}
void emit_range_uintx(const char* name, const uintx* ptr, uintx min, uintx max) {
  CommandLineFlagRangeList::add(new CommandLineFlagRange_uintx(name, ptr, min, max));
}
void emit_range_uint64_t(const char* name, const uint64_t* ptr, uint64_t min, uint64_t max) {
  CommandLineFlagRangeList::add(new CommandLineFlagRange_uint64_t(name, ptr, min, max));
}
void emit_range_size_t(const char* name, const size_t* ptr, size_t min, size_t max) {
  CommandLineFlagRangeList::add(new CommandLineFlagRange_size_t(name, ptr, min, max));
}
void emit_range_double(const char* name, const double* ptr, double min, double max) {
  CommandLineFlagRangeList::add(new CommandLineFlagRange_double(name, ptr, min, max));
}

// Generate code to call emit_range_xxx function
#define EMIT_RANGE_PRODUCT_FLAG(type, name, value, doc)      ); emit_range_##type(#name,&name
#define EMIT_RANGE_COMMERCIAL_FLAG(type, name, value, doc)   ); emit_range_##type(#name,&name
#define EMIT_RANGE_DIAGNOSTIC_FLAG(type, name, value, doc)   ); emit_range_##type(#name,&name
#define EMIT_RANGE_EXPERIMENTAL_FLAG(type, name, value, doc) ); emit_range_##type(#name,&name
#define EMIT_RANGE_MANAGEABLE_FLAG(type, name, value, doc)   ); emit_range_##type(#name,&name
#define EMIT_RANGE_PRODUCT_RW_FLAG(type, name, value, doc)   ); emit_range_##type(#name,&name
#define EMIT_RANGE_PD_PRODUCT_FLAG(type, name, doc)          ); emit_range_##type(#name,&name
#define EMIT_RANGE_PD_DIAGNOSTIC_FLAG(type, name, doc)       ); emit_range_##type(#name,&name
#ifndef PRODUCT
#define EMIT_RANGE_DEVELOPER_FLAG(type, name, value, doc)    ); emit_range_##type(#name,&name
#define EMIT_RANGE_PD_DEVELOPER_FLAG(type, name, doc)        ); emit_range_##type(#name,&name
#define EMIT_RANGE_NOTPRODUCT_FLAG(type, name, value, doc)   ); emit_range_##type(#name,&name
#else
#define EMIT_RANGE_DEVELOPER_FLAG(type, name, value, doc)    ); emit_range_no(#name,&name
#define EMIT_RANGE_PD_DEVELOPER_FLAG(type, name, doc)        ); emit_range_no(#name,&name
#define EMIT_RANGE_NOTPRODUCT_FLAG(type, name, value, doc)   ); emit_range_no(#name,&name
#endif
#ifdef _LP64
#define EMIT_RANGE_LP64_PRODUCT_FLAG(type, name, value, doc) ); emit_range_##type(#name,&name
#else
#define EMIT_RANGE_LP64_PRODUCT_FLAG(type, name, value, doc) ); emit_range_no(#name,&name
#endif

// Generate func argument to pass into emit_range_xxx functions
#define EMIT_RANGE_CHECK(a, b)                               , a, b

#define INITIAL_RANGES_SIZE 379
GrowableArray<CommandLineFlagRange*>* CommandLineFlagRangeList::_ranges = NULL;

// Check the ranges of all flags that have them
void CommandLineFlagRangeList::init(void) {

  _ranges = new (ResourceObj::C_HEAP, mtArguments) GrowableArray<CommandLineFlagRange*>(INITIAL_RANGES_SIZE, true);

  emit_range_no(NULL RUNTIME_FLAGS(EMIT_RANGE_DEVELOPER_FLAG,
                                   EMIT_RANGE_PD_DEVELOPER_FLAG,
                                   EMIT_RANGE_PRODUCT_FLAG,
                                   EMIT_RANGE_PD_PRODUCT_FLAG,
                                   EMIT_RANGE_DIAGNOSTIC_FLAG,
                                   EMIT_RANGE_PD_DIAGNOSTIC_FLAG,
                                   EMIT_RANGE_EXPERIMENTAL_FLAG,
                                   EMIT_RANGE_NOTPRODUCT_FLAG,
                                   EMIT_RANGE_MANAGEABLE_FLAG,
                                   EMIT_RANGE_PRODUCT_RW_FLAG,
                                   EMIT_RANGE_LP64_PRODUCT_FLAG,
                                   EMIT_RANGE_CHECK,
                                   IGNORE_CONSTRAINT,
                                   IGNORE_WRITEABLE));

  EMIT_RANGES_FOR_GLOBALS_EXT

  emit_range_no(NULL ARCH_FLAGS(EMIT_RANGE_DEVELOPER_FLAG,
                                EMIT_RANGE_PRODUCT_FLAG,
                                EMIT_RANGE_DIAGNOSTIC_FLAG,
                                EMIT_RANGE_EXPERIMENTAL_FLAG,
                                EMIT_RANGE_NOTPRODUCT_FLAG,
                                EMIT_RANGE_CHECK,
                                IGNORE_CONSTRAINT,
                                IGNORE_WRITEABLE));

#if INCLUDE_JVMCI
  emit_range_no(NULL JVMCI_FLAGS(EMIT_RANGE_DEVELOPER_FLAG,
                                 EMIT_RANGE_PD_DEVELOPER_FLAG,
                                 EMIT_RANGE_PRODUCT_FLAG,
                                 EMIT_RANGE_PD_PRODUCT_FLAG,
                                 EMIT_RANGE_DIAGNOSTIC_FLAG,
                                 EMIT_RANGE_PD_DIAGNOSTIC_FLAG,
                                 EMIT_RANGE_EXPERIMENTAL_FLAG,
                                 EMIT_RANGE_NOTPRODUCT_FLAG,
                                 EMIT_RANGE_CHECK,
                                 IGNORE_CONSTRAINT,
                                 IGNORE_WRITEABLE));
#endif // INCLUDE_JVMCI

#ifdef COMPILER1
  emit_range_no(NULL C1_FLAGS(EMIT_RANGE_DEVELOPER_FLAG,
                              EMIT_RANGE_PD_DEVELOPER_FLAG,
                              EMIT_RANGE_PRODUCT_FLAG,
                              EMIT_RANGE_PD_PRODUCT_FLAG,
                              EMIT_RANGE_DIAGNOSTIC_FLAG,
                              EMIT_RANGE_PD_DIAGNOSTIC_FLAG,
                              EMIT_RANGE_NOTPRODUCT_FLAG,
                              EMIT_RANGE_CHECK,
                              IGNORE_CONSTRAINT,
                              IGNORE_WRITEABLE));
#endif // COMPILER1

#ifdef COMPILER2
  emit_range_no(NULL C2_FLAGS(EMIT_RANGE_DEVELOPER_FLAG,
                              EMIT_RANGE_PD_DEVELOPER_FLAG,
                              EMIT_RANGE_PRODUCT_FLAG,
                              EMIT_RANGE_PD_PRODUCT_FLAG,
                              EMIT_RANGE_DIAGNOSTIC_FLAG,
                              EMIT_RANGE_PD_DIAGNOSTIC_FLAG,
                              EMIT_RANGE_EXPERIMENTAL_FLAG,
                              EMIT_RANGE_NOTPRODUCT_FLAG,
                              EMIT_RANGE_CHECK,
                              IGNORE_CONSTRAINT,
                              IGNORE_WRITEABLE));
#endif // COMPILER2

#if INCLUDE_ALL_GCS
  emit_range_no(NULL G1_FLAGS(EMIT_RANGE_DEVELOPER_FLAG,
                              EMIT_RANGE_PD_DEVELOPER_FLAG,
                              EMIT_RANGE_PRODUCT_FLAG,
                              EMIT_RANGE_PD_PRODUCT_FLAG,
                              EMIT_RANGE_DIAGNOSTIC_FLAG,
                              EMIT_RANGE_PD_DIAGNOSTIC_FLAG,
                              EMIT_RANGE_EXPERIMENTAL_FLAG,
                              EMIT_RANGE_NOTPRODUCT_FLAG,
                              EMIT_RANGE_MANAGEABLE_FLAG,
                              EMIT_RANGE_PRODUCT_RW_FLAG,
                              EMIT_RANGE_CHECK,
                              IGNORE_CONSTRAINT,
                              IGNORE_WRITEABLE));
#endif // INCLUDE_ALL_GCS
}

CommandLineFlagRange* CommandLineFlagRangeList::find(const char* name) {
  CommandLineFlagRange* found = NULL;
  for (int i=0; i<length(); i++) {
    CommandLineFlagRange* range = at(i);
    if (strcmp(range->name(), name) == 0) {
      found = range;
      break;
    }
  }
  return found;
}

void CommandLineFlagRangeList::print(outputStream* st, const char* name, RangeStrFunc default_range_str_func) {
  CommandLineFlagRange* range = CommandLineFlagRangeList::find(name);
  if (range != NULL) {
    range->print(st);
  } else {
    CommandLineFlagConstraint* constraint = CommandLineFlagConstraintList::find(name);
    if (constraint != NULL) {
      assert(default_range_str_func!=NULL, "default_range_str_func must be provided");
      st->print("%s", default_range_str_func());
    } else {
      st->print("[                           ...                           ]");
    }
  }
}

bool CommandLineFlagRangeList::check_ranges() {
  // Check ranges.
  bool status = true;
  for (int i=0; i<length(); i++) {
    CommandLineFlagRange* range = at(i);
    if (range->check(true) != Flag::SUCCESS) status = false;
  }
  return status;
}
