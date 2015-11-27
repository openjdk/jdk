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

public:
  // the "name" argument must be a string literal
  CommandLineFlagRange_int(const char* name, int min, int max) : CommandLineFlagRange(name) {
    _min=min, _max=max;
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

public:
  // the "name" argument must be a string literal
  CommandLineFlagRange_intx(const char* name, intx min, intx max) : CommandLineFlagRange(name) {
    _min=min, _max=max;
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

public:
  // the "name" argument must be a string literal
  CommandLineFlagRange_uint(const char* name, uint min, uint max) : CommandLineFlagRange(name) {
    _min=min, _max=max;
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

public:
  // the "name" argument must be a string literal
  CommandLineFlagRange_uintx(const char* name, uintx min, uintx max) : CommandLineFlagRange(name) {
    _min=min, _max=max;
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

public:
  // the "name" argument must be a string literal
  CommandLineFlagRange_uint64_t(const char* name, uint64_t min, uint64_t max) : CommandLineFlagRange(name) {
    _min=min, _max=max;
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

public:
  // the "name" argument must be a string literal
  CommandLineFlagRange_size_t(const char* name, size_t min, size_t max) : CommandLineFlagRange(name) {
    _min=min, _max=max;
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

public:
  // the "name" argument must be a string literal
  CommandLineFlagRange_double(const char* name, double min, double max) : CommandLineFlagRange(name) {
    _min=min, _max=max;
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
void emit_range_bool(const char* /*name*/)      { /* NOP */ }
void emit_range_ccstr(const char* /*name*/)     { /* NOP */ }
void emit_range_ccstrlist(const char* /*name*/) { /* NOP */ }
void emit_range_int(const char* /*name*/)       { /* NOP */ }
void emit_range_intx(const char* /*name*/)      { /* NOP */ }
void emit_range_uint(const char* /*name*/)      { /* NOP */ }
void emit_range_uintx(const char* /*name*/)     { /* NOP */ }
void emit_range_uint64_t(const char* /*name*/)  { /* NOP */ }
void emit_range_size_t(const char* /*name*/)    { /* NOP */ }
void emit_range_double(const char* /*name*/)    { /* NOP */ }

// CommandLineFlagRange emitting code functions if range arguments are provided
void emit_range_intx(const char* name, intx min, intx max) {
  CommandLineFlagRangeList::add(new CommandLineFlagRange_intx(name, min, max));
}
void emit_range_uintx(const char* name, uintx min, uintx max) {
  CommandLineFlagRangeList::add(new CommandLineFlagRange_uintx(name, min, max));
}
void emit_range_uint64_t(const char* name, uint64_t min, uint64_t max) {
  CommandLineFlagRangeList::add(new CommandLineFlagRange_uint64_t(name, min, max));
}
void emit_range_size_t(const char* name, size_t min, size_t max) {
  CommandLineFlagRangeList::add(new CommandLineFlagRange_size_t(name, min, max));
}
void emit_range_double(const char* name, double min, double max) {
  CommandLineFlagRangeList::add(new CommandLineFlagRange_double(name, min, max));
}

// Generate code to call emit_range_xxx function
#define EMIT_RANGE_PRODUCT_FLAG(type, name, value, doc)      ); emit_range_##type(#name
#define EMIT_RANGE_COMMERCIAL_FLAG(type, name, value, doc)   ); emit_range_##type(#name
#define EMIT_RANGE_DIAGNOSTIC_FLAG(type, name, value, doc)   ); emit_range_##type(#name
#define EMIT_RANGE_EXPERIMENTAL_FLAG(type, name, value, doc) ); emit_range_##type(#name
#define EMIT_RANGE_MANAGEABLE_FLAG(type, name, value, doc)   ); emit_range_##type(#name
#define EMIT_RANGE_PRODUCT_RW_FLAG(type, name, value, doc)   ); emit_range_##type(#name
#define EMIT_RANGE_PD_PRODUCT_FLAG(type, name, doc)          ); emit_range_##type(#name
#define EMIT_RANGE_DEVELOPER_FLAG(type, name, value, doc)    ); emit_range_##type(#name
#define EMIT_RANGE_PD_DEVELOPER_FLAG(type, name, doc)        ); emit_range_##type(#name
#define EMIT_RANGE_NOTPRODUCT_FLAG(type, name, value, doc)   ); emit_range_##type(#name
#define EMIT_RANGE_LP64_PRODUCT_FLAG(type, name, value, doc) ); emit_range_##type(#name

// Generate func argument to pass into emit_range_xxx functions
#define EMIT_RANGE_CHECK(a, b)                               , a, b

#define INITIAL_RANGES_SIZE 205
GrowableArray<CommandLineFlagRange*>* CommandLineFlagRangeList::_ranges = NULL;

// Check the ranges of all flags that have them
void CommandLineFlagRangeList::init(void) {

  _ranges = new (ResourceObj::C_HEAP, mtInternal) GrowableArray<CommandLineFlagRange*>(INITIAL_RANGES_SIZE, true);

  emit_range_no(NULL RUNTIME_FLAGS(EMIT_RANGE_DEVELOPER_FLAG,
                                   EMIT_RANGE_PD_DEVELOPER_FLAG,
                                   EMIT_RANGE_PRODUCT_FLAG,
                                   EMIT_RANGE_PD_PRODUCT_FLAG,
                                   EMIT_RANGE_DIAGNOSTIC_FLAG,
                                   EMIT_RANGE_EXPERIMENTAL_FLAG,
                                   EMIT_RANGE_NOTPRODUCT_FLAG,
                                   EMIT_RANGE_MANAGEABLE_FLAG,
                                   EMIT_RANGE_PRODUCT_RW_FLAG,
                                   EMIT_RANGE_LP64_PRODUCT_FLAG,
                                   EMIT_RANGE_CHECK,
                                   IGNORE_CONSTRAINT) );

  EMIT_RANGES_FOR_GLOBALS_EXT

  emit_range_no(NULL ARCH_FLAGS(EMIT_RANGE_DEVELOPER_FLAG,
                                EMIT_RANGE_PRODUCT_FLAG,
                                EMIT_RANGE_DIAGNOSTIC_FLAG,
                                EMIT_RANGE_EXPERIMENTAL_FLAG,
                                EMIT_RANGE_NOTPRODUCT_FLAG,
                                EMIT_RANGE_CHECK,
                                IGNORE_CONSTRAINT));

#if INCLUDE_JVMCI
  emit_range_no(NULL JVMCI_FLAGS(EMIT_RANGE_DEVELOPER_FLAG,
                                 EMIT_RANGE_PD_DEVELOPER_FLAG,
                                 EMIT_RANGE_PRODUCT_FLAG,
                                 EMIT_RANGE_PD_PRODUCT_FLAG,
                                 EMIT_RANGE_DIAGNOSTIC_FLAG,
                                 EMIT_RANGE_EXPERIMENTAL_FLAG,
                                 EMIT_RANGE_NOTPRODUCT_FLAG,
                                 EMIT_RANGE_CHECK,
                                 IGNORE_CONSTRAINT));
#endif // INCLUDE_JVMCI

#ifdef COMPILER1
  emit_range_no(NULL C1_FLAGS(EMIT_RANGE_DEVELOPER_FLAG,
                              EMIT_RANGE_PD_DEVELOPER_FLAG,
                              EMIT_RANGE_PRODUCT_FLAG,
                              EMIT_RANGE_PD_PRODUCT_FLAG,
                              EMIT_RANGE_DIAGNOSTIC_FLAG,
                              EMIT_RANGE_NOTPRODUCT_FLAG,
                              EMIT_RANGE_CHECK,
                              IGNORE_CONSTRAINT));
#endif // COMPILER1

#ifdef COMPILER2
  emit_range_no(NULL C2_FLAGS(EMIT_RANGE_DEVELOPER_FLAG,
                              EMIT_RANGE_PD_DEVELOPER_FLAG,
                              EMIT_RANGE_PRODUCT_FLAG,
                              EMIT_RANGE_PD_PRODUCT_FLAG,
                              EMIT_RANGE_DIAGNOSTIC_FLAG,
                              EMIT_RANGE_EXPERIMENTAL_FLAG,
                              EMIT_RANGE_NOTPRODUCT_FLAG,
                              EMIT_RANGE_CHECK,
                              IGNORE_CONSTRAINT));
#endif // COMPILER2

#if INCLUDE_ALL_GCS
  emit_range_no(NULL G1_FLAGS(EMIT_RANGE_DEVELOPER_FLAG,
                              EMIT_RANGE_PD_DEVELOPER_FLAG,
                              EMIT_RANGE_PRODUCT_FLAG,
                              EMIT_RANGE_PD_PRODUCT_FLAG,
                              EMIT_RANGE_DIAGNOSTIC_FLAG,
                              EMIT_RANGE_EXPERIMENTAL_FLAG,
                              EMIT_RANGE_NOTPRODUCT_FLAG,
                              EMIT_RANGE_MANAGEABLE_FLAG,
                              EMIT_RANGE_PRODUCT_RW_FLAG,
                              EMIT_RANGE_CHECK,
                              IGNORE_CONSTRAINT));
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

void CommandLineFlagRangeList::print(const char* name, outputStream* st, bool unspecified) {
  CommandLineFlagRange* range = CommandLineFlagRangeList::find(name);
  if (range != NULL) {
    range->print(st);
  } else if (unspecified == true) {
    st->print("[                           ...                           ]");
  }
}

bool CommandLineFlagRangeList::check_ranges() {
  // Check ranges.
  bool status = true;
  for (int i=0; i<length(); i++) {
    CommandLineFlagRange* range = at(i);
    const char* name = range->name();
    Flag* flag = Flag::find_flag(name, strlen(name), true, true);
    // We must check for NULL here as lp64_product flags on 32 bit architecture
    // can generate range check (despite that they are declared as constants),
    // but they will not be returned by Flag::find_flag()
    if (flag != NULL) {
      if (flag->is_int()) {
        int value = flag->get_int();
        if (range->check_int(value, true) != Flag::SUCCESS) status = false;
      } else if (flag->is_uint()) {
        uint value = flag->get_uint();
        if (range->check_uint(value, true) != Flag::SUCCESS) status = false;
      } else if (flag->is_intx()) {
        intx value = flag->get_intx();
        if (range->check_intx(value, true) != Flag::SUCCESS) status = false;
      } else if (flag->is_uintx()) {
        uintx value = flag->get_uintx();
        if (range->check_uintx(value, true) != Flag::SUCCESS) status = false;
      } else if (flag->is_uint64_t()) {
        uint64_t value = flag->get_uint64_t();
        if (range->check_uint64_t(value, true) != Flag::SUCCESS) status = false;
      } else if (flag->is_size_t()) {
        size_t value = flag->get_size_t();
        if (range->check_size_t(value, true) != Flag::SUCCESS) status = false;
      } else if (flag->is_double()) {
        double value = flag->get_double();
        if (range->check_double(value, true) != Flag::SUCCESS) status = false;
      }
    }
  }
  return status;
}
