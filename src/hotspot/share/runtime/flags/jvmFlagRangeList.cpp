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
#include "jvm.h"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "oops/markWord.hpp"
#include "runtime/arguments.hpp"
#include "runtime/flags/jvmFlag.hpp"
#include "runtime/flags/jvmFlagConstraintList.hpp"
#include "runtime/flags/jvmFlagRangeList.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/os.hpp"
#include "runtime/task.hpp"
#include "utilities/macros.hpp"

class JVMFlagRange_int : public JVMFlagRange {
  int _min;
  int _max;

public:
  JVMFlagRange_int(const JVMFlag* flag, int min, int max)
    : JVMFlagRange(flag), _min(min), _max(max) {}

  JVMFlag::Error check(bool verbose = true) {
    return check_int(_flag->get_int(), verbose);
  }

  JVMFlag::Error check_int(int value, bool verbose = true) {
    if ((value < _min) || (value > _max)) {
      JVMFlag::printError(verbose,
                          "int %s=%d is outside the allowed range "
                          "[ %d ... %d ]\n",
                          name(), value, _min, _max);
      return JVMFlag::OUT_OF_BOUNDS;
    } else {
      return JVMFlag::SUCCESS;
    }
  }

  void print(outputStream* st) {
    st->print("[ %-25d ... %25d ]", _min, _max);
  }
};

class JVMFlagRange_intx : public JVMFlagRange {
  intx _min;
  intx _max;

public:
  JVMFlagRange_intx(const JVMFlag* flag, intx min, intx max)
    : JVMFlagRange(flag), _min(min), _max(max) {}

  JVMFlag::Error check(bool verbose = true) {
    return check_intx(_flag->get_intx(), verbose);
  }

  JVMFlag::Error check_intx(intx value, bool verbose = true) {
    if ((value < _min) || (value > _max)) {
      JVMFlag::printError(verbose,
                          "intx %s=" INTX_FORMAT " is outside the allowed range "
                          "[ " INTX_FORMAT " ... " INTX_FORMAT " ]\n",
                          name(), value, _min, _max);
      return JVMFlag::OUT_OF_BOUNDS;
    } else {
      return JVMFlag::SUCCESS;
    }
  }

  void print(outputStream* st) {
    st->print("[ " INTX_FORMAT_W(-25) " ... " INTX_FORMAT_W(25) " ]", _min, _max);
  }
};

class JVMFlagRange_uint : public JVMFlagRange {
  uint _min;
  uint _max;

public:
  JVMFlagRange_uint(const JVMFlag* flag, uint min, uint max)
    : JVMFlagRange(flag), _min(min), _max(max) {}

  JVMFlag::Error check(bool verbose = true) {
    return check_uint(_flag->get_uint(), verbose);
  }

  JVMFlag::Error check_uint(uint value, bool verbose = true) {
    if ((value < _min) || (value > _max)) {
      JVMFlag::printError(verbose,
                          "uint %s=%u is outside the allowed range "
                          "[ %u ... %u ]\n",
                          name(), value, _min, _max);
      return JVMFlag::OUT_OF_BOUNDS;
    } else {
      return JVMFlag::SUCCESS;
    }
  }

  void print(outputStream* st) {
    st->print("[ %-25u ... %25u ]", _min, _max);
  }
};

class JVMFlagRange_uintx : public JVMFlagRange {
  uintx _min;
  uintx _max;

public:
  JVMFlagRange_uintx(const JVMFlag* flag, uintx min, uintx max)
    : JVMFlagRange(flag), _min(min), _max(max) {}

  JVMFlag::Error check(bool verbose = true) {
    return check_uintx(_flag->get_uintx(), verbose);
  }

  JVMFlag::Error check_uintx(uintx value, bool verbose = true) {
    if ((value < _min) || (value > _max)) {
      JVMFlag::printError(verbose,
                          "uintx %s=" UINTX_FORMAT " is outside the allowed range "
                          "[ " UINTX_FORMAT " ... " UINTX_FORMAT " ]\n",
                          name(), value, _min, _max);
      return JVMFlag::OUT_OF_BOUNDS;
    } else {
      return JVMFlag::SUCCESS;
    }
  }

  void print(outputStream* st) {
    st->print("[ " UINTX_FORMAT_W(-25) " ... " UINTX_FORMAT_W(25) " ]", _min, _max);
  }
};

class JVMFlagRange_uint64_t : public JVMFlagRange {
  uint64_t _min;
  uint64_t _max;

public:
  JVMFlagRange_uint64_t(const JVMFlag* flag, uint64_t min, uint64_t max)
    : JVMFlagRange(flag), _min(min), _max(max) {}

  JVMFlag::Error check(bool verbose = true) {
    return check_uint64_t(_flag->get_uintx(), verbose);
  }

  JVMFlag::Error check_uint64_t(uint64_t value, bool verbose = true) {
    if ((value < _min) || (value > _max)) {
      JVMFlag::printError(verbose,
                          "uint64_t %s=" UINT64_FORMAT " is outside the allowed range "
                          "[ " UINT64_FORMAT " ... " UINT64_FORMAT " ]\n",
                          name(), value, _min, _max);
      return JVMFlag::OUT_OF_BOUNDS;
    } else {
      return JVMFlag::SUCCESS;
    }
  }

  void print(outputStream* st) {
    st->print("[ " UINT64_FORMAT_W(-25) " ... " UINT64_FORMAT_W(25) " ]", _min, _max);
  }
};

class JVMFlagRange_size_t : public JVMFlagRange {
  size_t _min;
  size_t _max;

public:
  JVMFlagRange_size_t(const JVMFlag* flag, size_t min, size_t max)
    : JVMFlagRange(flag), _min(min), _max(max) {}

  JVMFlag::Error check(bool verbose = true) {
    return check_size_t(_flag->get_size_t(), verbose);
  }

  JVMFlag::Error check_size_t(size_t value, bool verbose = true) {
    if ((value < _min) || (value > _max)) {
      JVMFlag::printError(verbose,
                          "size_t %s=" SIZE_FORMAT " is outside the allowed range "
                          "[ " SIZE_FORMAT " ... " SIZE_FORMAT " ]\n",
                          name(), value, _min, _max);
      return JVMFlag::OUT_OF_BOUNDS;
    } else {
      return JVMFlag::SUCCESS;
    }
  }

  void print(outputStream* st) {
    st->print("[ " SIZE_FORMAT_W(-25) " ... " SIZE_FORMAT_W(25) " ]", _min, _max);
  }
};

class JVMFlagRange_double : public JVMFlagRange {
  double _min;
  double _max;

public:
  JVMFlagRange_double(const JVMFlag* flag, double min, double max)
    : JVMFlagRange(flag), _min(min), _max(max) {}

  JVMFlag::Error check(bool verbose = true) {
    return check_double(_flag->get_double(), verbose);
  }

  JVMFlag::Error check_double(double value, bool verbose = true) {
    if ((value < _min) || (value > _max)) {
      JVMFlag::printError(verbose,
                          "double %s=%f is outside the allowed range "
                          "[ %f ... %f ]\n",
                          name(), value, _min, _max);
      return JVMFlag::OUT_OF_BOUNDS;
    } else {
      return JVMFlag::SUCCESS;
    }
  }

  void print(outputStream* st) {
    st->print("[ %-25.3f ... %25.3f ]", _min, _max);
  }
};

// No constraint emitting
void emit_range_no(...)                         { /* NOP */ }

// No constraint emitting if function argument is NOT provided
void emit_range_bool(const JVMFlag* /*flag*/)      { /* NOP */ }
void emit_range_ccstr(const JVMFlag* /*flag*/)     { /* NOP */ }
void emit_range_ccstrlist(const JVMFlag* /*flag*/) { /* NOP */ }
void emit_range_int(const JVMFlag* /*flag*/)       { /* NOP */ }
void emit_range_intx(const JVMFlag* /*flag*/)      { /* NOP */ }
void emit_range_uint(const JVMFlag* /*flag*/)      { /* NOP */ }
void emit_range_uintx(const JVMFlag* /*flag*/)     { /* NOP */ }
void emit_range_uint64_t(const JVMFlag* /*flag*/)  { /* NOP */ }
void emit_range_size_t(const JVMFlag* /*flag*/)    { /* NOP */ }
void emit_range_double(const JVMFlag* /*flag*/)    { /* NOP */ }

// JVMFlagRange emitting code functions if range arguments are provided
void emit_range_int(const JVMFlag* flag, int min, int max)       {
  JVMFlagRangeList::add(new JVMFlagRange_int(flag, min, max));
}
void emit_range_intx(const JVMFlag* flag, intx min, intx max) {
  JVMFlagRangeList::add(new JVMFlagRange_intx(flag, min, max));
}
void emit_range_uint(const JVMFlag* flag, uint min, uint max) {
  JVMFlagRangeList::add(new JVMFlagRange_uint(flag, min, max));
}
void emit_range_uintx(const JVMFlag* flag, uintx min, uintx max) {
  JVMFlagRangeList::add(new JVMFlagRange_uintx(flag, min, max));
}
void emit_range_uint64_t(const JVMFlag* flag, uint64_t min, uint64_t max) {
  JVMFlagRangeList::add(new JVMFlagRange_uint64_t(flag, min, max));
}
void emit_range_size_t(const JVMFlag* flag, size_t min, size_t max) {
  JVMFlagRangeList::add(new JVMFlagRange_size_t(flag, min, max));
}
void emit_range_double(const JVMFlag* flag, double min, double max) {
  JVMFlagRangeList::add(new JVMFlagRange_double(flag, min, max));
}

// Generate code to call emit_range_xxx function
#define EMIT_RANGE_START       (void)(0
#define EMIT_RANGE(type, name) ); emit_range_##type(JVMFlagEx::flag_from_enum(FLAG_MEMBER_ENUM(name))
#define EMIT_RANGE_NO          ); emit_range_no(0
#define EMIT_RANGE_PRODUCT_FLAG(type, name, value, doc)      EMIT_RANGE(type, name)
#define EMIT_RANGE_DIAGNOSTIC_FLAG(type, name, value, doc)   EMIT_RANGE(type, name)
#define EMIT_RANGE_EXPERIMENTAL_FLAG(type, name, value, doc) EMIT_RANGE(type, name)
#define EMIT_RANGE_MANAGEABLE_FLAG(type, name, value, doc)   EMIT_RANGE(type, name)
#define EMIT_RANGE_PRODUCT_RW_FLAG(type, name, value, doc)   EMIT_RANGE(type, name)
#define EMIT_RANGE_PD_PRODUCT_FLAG(type, name, doc)          EMIT_RANGE(type, name)
#define EMIT_RANGE_PD_DIAGNOSTIC_FLAG(type, name, doc)       EMIT_RANGE(type, name)
#ifndef PRODUCT
#define EMIT_RANGE_DEVELOPER_FLAG(type, name, value, doc)    EMIT_RANGE(type, name)
#define EMIT_RANGE_PD_DEVELOPER_FLAG(type, name, doc)        EMIT_RANGE(type, name)
#define EMIT_RANGE_NOTPRODUCT_FLAG(type, name, value, doc)   EMIT_RANGE(type, name)
#else
#define EMIT_RANGE_DEVELOPER_FLAG(type, name, value, doc)    EMIT_RANGE_NO
#define EMIT_RANGE_PD_DEVELOPER_FLAG(type, name, doc)        EMIT_RANGE_NO
#define EMIT_RANGE_NOTPRODUCT_FLAG(type, name, value, doc)   EMIT_RANGE_NO
#endif
#ifdef _LP64
#define EMIT_RANGE_LP64_PRODUCT_FLAG(type, name, value, doc) EMIT_RANGE(type, name)
#else
#define EMIT_RANGE_LP64_PRODUCT_FLAG(type, name, value, doc) EMIT_RANGE_NO
#endif
#define EMIT_RANGE_END         );

// Generate func argument to pass into emit_range_xxx functions
#define EMIT_RANGE_CHECK(a, b)                               , a, b

#define INITIAL_RANGES_SIZE 379
GrowableArray<JVMFlagRange*>* JVMFlagRangeList::_ranges = NULL;

// Check the ranges of all flags that have them
void JVMFlagRangeList::init(void) {

  _ranges = new (ResourceObj::C_HEAP, mtArguments) GrowableArray<JVMFlagRange*>(INITIAL_RANGES_SIZE, true);

  EMIT_RANGE_START

  ALL_FLAGS(EMIT_RANGE_DEVELOPER_FLAG,
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
            IGNORE_CONSTRAINT)

  EMIT_RANGE_END
}

JVMFlagRange* JVMFlagRangeList::find(const JVMFlag* flag) {
  JVMFlagRange* found = NULL;
  for (int i=0; i<length(); i++) {
    JVMFlagRange* range = at(i);
    if (range->flag() == flag) {
      found = range;
      break;
    }
  }
  return found;
}

void JVMFlagRangeList::print(outputStream* st, const JVMFlag* flag, RangeStrFunc default_range_str_func) {
  JVMFlagRange* range = JVMFlagRangeList::find(flag);
  if (range != NULL) {
    range->print(st);
  } else {
    JVMFlagConstraint* constraint = JVMFlagConstraintList::find(flag);
    if (constraint != NULL) {
      assert(default_range_str_func!=NULL, "default_range_str_func must be provided");
      st->print("%s", default_range_str_func());
    } else {
      st->print("[                           ...                           ]");
    }
  }
}

bool JVMFlagRangeList::check_ranges() {
  bool status = true;
  for (int i=0; i<length(); i++) {
    JVMFlagRange* range = at(i);
    if (range->check(true) != JVMFlag::SUCCESS) status = false;
  }
  return status;
}
