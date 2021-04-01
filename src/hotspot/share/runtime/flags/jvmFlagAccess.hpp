/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_FLAGS_JVMFLAGACCESS_HPP
#define SHARE_RUNTIME_FLAGS_JVMFLAGACCESS_HPP

#include "memory/allStatic.hpp"
#include "runtime/flags/jvmFlag.hpp"
#include "utilities/vmEnums.hpp"

class FlagAccessImpl;
class JVMFlagLimit;
class outputStream;

// Use this macro in combination with JVMFlag::{read, write} and JVMFlagAccess::{get, set}
// to safely access the underlying variable of a JVMFlag:
//
//  JVMFlag* flag = JVMFlag::flag_from_enum(FLAG_MEMBER_ENUM(ObjectAlignmentInBytes));
//
//  /* If you use a wrong type, a run-time assertion will happen */
//  intx v = flag->read<intx>();
//
//  /* If you use a wrong type, or a NULL flag, an error code is returned */
//  JVMFlag::Error err = JVMFlagAccess::get<intx>(flag, &v, origin);

#define JVM_FLAG_TYPE(t) \
  t, JVMFlag::TYPE_ ## t

// This class provides a unified interface for getting/setting the JVM flags, with support
// for (1) type correctness checks, (2) range checks, (3) constraint checks. Two main types
// of setters are provided. See notes below on which one to use.
class JVMFlagAccess : AllStatic {
  inline static const FlagAccessImpl* access_impl(const JVMFlag* flag);
  static JVMFlag::Error set_impl(JVMFlag* flag, void* value, JVMFlagOrigin origin);
  static JVMFlag::Error set_or_assert(JVMFlagsEnum flag_enum, int type_enum, void* value, JVMFlagOrigin origin);

public:
  static JVMFlag::Error check_range(const JVMFlag* flag, bool verbose);
  static JVMFlag::Error check_constraint(const JVMFlag* flag, void * func, bool verbose);
  static void print_range(outputStream* st, const JVMFlag* flag, const JVMFlagLimit* range);
  static void print_range(outputStream* st, const JVMFlag* flag);

  template <typename T>
  static JVMFlag::Error get(const JVMFlag* flag, T* value) {
    if (flag == NULL) {
      return JVMFlag::INVALID_FLAG;
    }
    int type_enum = flag->type();
    if (!JVMFlag::is_compatible_type<T>(type_enum)) {
      return JVMFlag::WRONG_FORMAT;
    }
    *value = flag->read<T>();
    return JVMFlag::SUCCESS;
  }

  // This is a *flag specific* setter. It should be used only via by the
  // FLAG_SET_{DEFAULT, CMDLINE, ERGO, MGMT} macros.
  // It's used to set a specific flag whose type is statically known. A mismatched
  // type_enum will result in an assert.
  template <typename T, int type_enum>
  static JVMFlag::Error set(JVMFlagsEnum flag_enum, T value, JVMFlagOrigin origin) {
    return set_or_assert(flag_enum, type_enum, &value, origin);
  }

  // This is a *generic* setter. It should be used by code that can set a number of different
  // flags, often according to external input that may contain errors.
  // Examples callers are arguments.cpp, writeableFlags.cpp, and WB_SetXxxVMFlag functions.
  // A mismatched type_enum would result in a JVMFlag::WRONG_FORMAT code.
  template <typename T>
  static JVMFlag::Error set(JVMFlag* flag, T* value, JVMFlagOrigin origin) {
    if (flag == NULL) {
      return JVMFlag::INVALID_FLAG;
    }
    if (!JVMFlag::is_compatible_type<T>(flag->type())) {
      return JVMFlag::WRONG_FORMAT;
    }
    return set_impl(flag, (void*)value, origin);
  }

  // Special handling needed for ccstr
  // Contract:  JVMFlag will make private copy of the incoming value.
  // Outgoing value is always malloc-ed, and caller MUST call free.
  static JVMFlag::Error set_ccstr(JVMFlag* flag, ccstr* value, JVMFlagOrigin origin);

  // Handy aliases
  static JVMFlag::Error get_ccstr(const JVMFlag* flag, ccstr* value) {
    return get<ccstr>(flag, value);
  }
};

#endif // SHARE_RUNTIME_FLAGS_JVMFLAGACCESS_HPP
