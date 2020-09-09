/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_GLOBALS_EXTENSION_HPP
#define SHARE_RUNTIME_GLOBALS_EXTENSION_HPP

#include "runtime/flags/jvmFlag.hpp"
#include "runtime/globals.hpp"
#include "utilities/macros.hpp"

// Construct enum of Flag_<cmdline-arg> constants.

#define FLAG_MEMBER_ENUM(name) Flag_##name##_enum
#define FLAG_MEMBER_ENUM_(name) FLAG_MEMBER_ENUM(name),

#define FLAG_MEMBER_ENUM_PRODUCT(type, name, value, ...)      FLAG_MEMBER_ENUM_(name)
#define FLAG_MEMBER_ENUM_PD_PRODUCT(type, name, ...)          FLAG_MEMBER_ENUM_(name)
#define FLAG_MEMBER_ENUM_DEVELOP(type, name, value, ...)      FLAG_MEMBER_ENUM_(name)
#define FLAG_MEMBER_ENUM_PD_DEVELOP(type, name, ...)          FLAG_MEMBER_ENUM_(name)
#define FLAG_MEMBER_ENUM_NOTPRODUCT(type, name, value, ...)   FLAG_MEMBER_ENUM_(name)

typedef enum : int {
  ALL_FLAGS(FLAG_MEMBER_ENUM_DEVELOP,
            FLAG_MEMBER_ENUM_PD_DEVELOP,
            FLAG_MEMBER_ENUM_PRODUCT,
            FLAG_MEMBER_ENUM_PD_PRODUCT,
            FLAG_MEMBER_ENUM_NOTPRODUCT,
            IGNORE_RANGE,
            IGNORE_CONSTRAINT)
  NUM_JVMFlagsEnum
} JVMFlagsEnum;

// Can't put the following in JVMFlags because
// of a circular dependency on the enum definition.
class JVMFlagEx : JVMFlag {
 public:
  static JVMFlag::Error boolAtPut(JVMFlagsEnum flag, bool value, JVMFlag::Flags origin);
  static JVMFlag::Error intAtPut(JVMFlagsEnum flag, int value, JVMFlag::Flags origin);
  static JVMFlag::Error uintAtPut(JVMFlagsEnum flag, uint value, JVMFlag::Flags origin);
  static JVMFlag::Error intxAtPut(JVMFlagsEnum flag, intx value, JVMFlag::Flags origin);
  static JVMFlag::Error uintxAtPut(JVMFlagsEnum flag, uintx value, JVMFlag::Flags origin);
  static JVMFlag::Error uint64_tAtPut(JVMFlagsEnum flag, uint64_t value, JVMFlag::Flags origin);
  static JVMFlag::Error size_tAtPut(JVMFlagsEnum flag, size_t value, JVMFlag::Flags origin);
  static JVMFlag::Error doubleAtPut(JVMFlagsEnum flag, double value, JVMFlag::Flags origin);
  // Contract:  Flag will make private copy of the incoming value
  static JVMFlag::Error ccstrAtPut(JVMFlagsEnum flag, ccstr value, JVMFlag::Flags origin);
  static JVMFlag::Error ccstrlistAtPut(JVMFlagsEnum flag, ccstr value, JVMFlag::Flags origin) {
    return ccstrAtPut(flag, value, origin);
  }

  static bool is_default(JVMFlagsEnum flag);
  static bool is_ergo(JVMFlagsEnum flag);
  static bool is_cmdline(JVMFlagsEnum flag);
  static bool is_jimage_resource(JVMFlagsEnum flag);

  static void setOnCmdLine(JVMFlagsEnum flag);

  static JVMFlag* flag_from_enum(JVMFlagsEnum flag);
};

// Construct set functions for all flags

#define FLAG_MEMBER_SET(name) Flag_##name##_set
#define FLAG_MEMBER_SET_(type, name) inline JVMFlag::Error FLAG_MEMBER_SET(name)(type value, JVMFlag::Flags origin) { return JVMFlagEx::type##AtPut(FLAG_MEMBER_ENUM(name), value, origin); }

#define FLAG_MEMBER_SET_PRODUCT(type, name, value, ...)      FLAG_MEMBER_SET_(type, name)
#define FLAG_MEMBER_SET_PD_PRODUCT(type, name, ...)          FLAG_MEMBER_SET_(type, name)
#define FLAG_MEMBER_SET_DEVELOP(type, name, value, ...)      FLAG_MEMBER_SET_(type, name)
#define FLAG_MEMBER_SET_PD_DEVELOP(type, name, ...)          FLAG_MEMBER_SET_(type, name)
#define FLAG_MEMBER_SET_NOTPRODUCT(type, name, value, ...)   FLAG_MEMBER_SET_(type, name)

ALL_FLAGS(FLAG_MEMBER_SET_DEVELOP,
          FLAG_MEMBER_SET_PD_DEVELOP,
          FLAG_MEMBER_SET_PRODUCT,
          FLAG_MEMBER_SET_PD_PRODUCT,
          FLAG_MEMBER_SET_NOTPRODUCT,
          IGNORE_RANGE,
          IGNORE_CONSTRAINT)

#define FLAG_IS_DEFAULT(name)         (JVMFlagEx::is_default(FLAG_MEMBER_ENUM(name)))
#define FLAG_IS_ERGO(name)            (JVMFlagEx::is_ergo(FLAG_MEMBER_ENUM(name)))
#define FLAG_IS_CMDLINE(name)         (JVMFlagEx::is_cmdline(FLAG_MEMBER_ENUM(name)))
#define FLAG_IS_JIMAGE_RESOURCE(name) (JVMFlagEx::is_jimage_resource(FLAG_MEMBER_ENUM(name)))

#define FLAG_SET_DEFAULT(name, value) ((name) = (value))

#define FLAG_SET_CMDLINE(name, value) (JVMFlagEx::setOnCmdLine(FLAG_MEMBER_ENUM(name)), \
                                       FLAG_MEMBER_SET(name)((value), JVMFlag::COMMAND_LINE))
#define FLAG_SET_ERGO(name, value)    (FLAG_MEMBER_SET(name)((value), JVMFlag::ERGONOMIC))
#define FLAG_SET_MGMT(name, value)    (FLAG_MEMBER_SET(name)((value), JVMFlag::MANAGEMENT))

#define FLAG_SET_ERGO_IF_DEFAULT(name, value) \
  do {                                        \
    if (FLAG_IS_DEFAULT(name)) {              \
      FLAG_SET_ERGO(name, value);             \
    }                                         \
  } while (0)

#endif // SHARE_RUNTIME_GLOBALS_EXTENSION_HPP
