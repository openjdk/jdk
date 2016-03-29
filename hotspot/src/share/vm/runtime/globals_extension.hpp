/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_GLOBALS_EXTENSION_HPP
#define SHARE_VM_RUNTIME_GLOBALS_EXTENSION_HPP

#include "runtime/globals.hpp"
#include "utilities/macros.hpp"
#include "utilities/top.hpp"

// Construct enum of Flag_<cmdline-arg> constants.

// Parenthesis left off in the following for the enum decl below.
#define FLAG_MEMBER(flag) Flag_##flag

#define RUNTIME_PRODUCT_FLAG_MEMBER(type, name, value, doc)      FLAG_MEMBER(name),
#define RUNTIME_PD_PRODUCT_FLAG_MEMBER(type, name, doc)          FLAG_MEMBER(name),
#define RUNTIME_DIAGNOSTIC_FLAG_MEMBER(type, name, value, doc)   FLAG_MEMBER(name),
#define RUNTIME_EXPERIMENTAL_FLAG_MEMBER(type, name, value, doc) FLAG_MEMBER(name),
#define RUNTIME_MANAGEABLE_FLAG_MEMBER(type, name, value, doc)   FLAG_MEMBER(name),
#define RUNTIME_PRODUCT_RW_FLAG_MEMBER(type, name, value, doc)   FLAG_MEMBER(name),
#define RUNTIME_DEVELOP_FLAG_MEMBER(type, name, value, doc)      FLAG_MEMBER(name),
#define RUNTIME_PD_DEVELOP_FLAG_MEMBER(type, name, doc)          FLAG_MEMBER(name),
#define RUNTIME_NOTPRODUCT_FLAG_MEMBER(type, name, value, doc)   FLAG_MEMBER(name),

#define JVMCI_PRODUCT_FLAG_MEMBER(type, name, value, doc)        FLAG_MEMBER(name),
#define JVMCI_PD_PRODUCT_FLAG_MEMBER(type, name, doc)            FLAG_MEMBER(name),
#define JVMCI_DEVELOP_FLAG_MEMBER(type, name, value, doc)        FLAG_MEMBER(name),
#define JVMCI_PD_DEVELOP_FLAG_MEMBER(type, name, doc)            FLAG_MEMBER(name),
#define JVMCI_DIAGNOSTIC_FLAG_MEMBER(type, name, value, doc)     FLAG_MEMBER(name),
#define JVMCI_EXPERIMENTAL_FLAG_MEMBER(type, name, value, doc)   FLAG_MEMBER(name),
#define JVMCI_NOTPRODUCT_FLAG_MEMBER(type, name, value, doc)     FLAG_MEMBER(name),

#ifdef _LP64
#define RUNTIME_LP64_PRODUCT_FLAG_MEMBER(type, name, value, doc) FLAG_MEMBER(name),
#else
#define RUNTIME_LP64_PRODUCT_FLAG_MEMBER(type, name, value, doc) /* flag is constant */
#endif // _LP64

#define C1_PRODUCT_FLAG_MEMBER(type, name, value, doc)           FLAG_MEMBER(name),
#define C1_PD_PRODUCT_FLAG_MEMBER(type, name, doc)               FLAG_MEMBER(name),
#define C1_DIAGNOSTIC_FLAG_MEMBER(type, name, value, doc)        FLAG_MEMBER(name),
#define C1_DEVELOP_FLAG_MEMBER(type, name, value, doc)           FLAG_MEMBER(name),
#define C1_PD_DEVELOP_FLAG_MEMBER(type, name, doc)               FLAG_MEMBER(name),
#define C1_NOTPRODUCT_FLAG_MEMBER(type, name, value, doc)        FLAG_MEMBER(name),

#define C2_PRODUCT_FLAG_MEMBER(type, name, value, doc)           FLAG_MEMBER(name),
#define C2_PD_PRODUCT_FLAG_MEMBER(type, name, doc)               FLAG_MEMBER(name),
#define C2_DIAGNOSTIC_FLAG_MEMBER(type, name, value, doc)        FLAG_MEMBER(name),
#define C2_EXPERIMENTAL_FLAG_MEMBER(type, name, value, doc)      FLAG_MEMBER(name),
#define C2_DEVELOP_FLAG_MEMBER(type, name, value, doc)           FLAG_MEMBER(name),
#define C2_PD_DEVELOP_FLAG_MEMBER(type, name, doc)               FLAG_MEMBER(name),
#define C2_NOTPRODUCT_FLAG_MEMBER(type, name, value, doc)        FLAG_MEMBER(name),

#define ARCH_PRODUCT_FLAG_MEMBER(type, name, value, doc)         FLAG_MEMBER(name),
#define ARCH_DIAGNOSTIC_FLAG_MEMBER(type, name, value, doc)      FLAG_MEMBER(name),
#define ARCH_EXPERIMENTAL_FLAG_MEMBER(type, name, value, doc)    FLAG_MEMBER(name),
#define ARCH_DEVELOP_FLAG_MEMBER(type, name, value, doc)         FLAG_MEMBER(name),
#define ARCH_NOTPRODUCT_FLAG_MEMBER(type, name, value, doc)      FLAG_MEMBER(name),

typedef enum {
 RUNTIME_FLAGS(RUNTIME_DEVELOP_FLAG_MEMBER, \
               RUNTIME_PD_DEVELOP_FLAG_MEMBER, \
               RUNTIME_PRODUCT_FLAG_MEMBER, \
               RUNTIME_PD_PRODUCT_FLAG_MEMBER, \
               RUNTIME_DIAGNOSTIC_FLAG_MEMBER, \
               RUNTIME_EXPERIMENTAL_FLAG_MEMBER, \
               RUNTIME_NOTPRODUCT_FLAG_MEMBER, \
               RUNTIME_MANAGEABLE_FLAG_MEMBER, \
               RUNTIME_PRODUCT_RW_FLAG_MEMBER, \
               RUNTIME_LP64_PRODUCT_FLAG_MEMBER, \
               IGNORE_RANGE, \
               IGNORE_CONSTRAINT)
 RUNTIME_OS_FLAGS(RUNTIME_DEVELOP_FLAG_MEMBER, \
                  RUNTIME_PD_DEVELOP_FLAG_MEMBER, \
                  RUNTIME_PRODUCT_FLAG_MEMBER, \
                  RUNTIME_PD_PRODUCT_FLAG_MEMBER, \
                  RUNTIME_DIAGNOSTIC_FLAG_MEMBER, \
                  RUNTIME_NOTPRODUCT_FLAG_MEMBER, \
                  IGNORE_RANGE, \
                  IGNORE_CONSTRAINT)
#if INCLUDE_ALL_GCS
 G1_FLAGS(RUNTIME_DEVELOP_FLAG_MEMBER, \
          RUNTIME_PD_DEVELOP_FLAG_MEMBER, \
          RUNTIME_PRODUCT_FLAG_MEMBER, \
          RUNTIME_PD_PRODUCT_FLAG_MEMBER, \
          RUNTIME_DIAGNOSTIC_FLAG_MEMBER, \
          RUNTIME_EXPERIMENTAL_FLAG_MEMBER, \
          RUNTIME_NOTPRODUCT_FLAG_MEMBER, \
          RUNTIME_MANAGEABLE_FLAG_MEMBER, \
          RUNTIME_PRODUCT_RW_FLAG_MEMBER, \
          IGNORE_RANGE, \
          IGNORE_CONSTRAINT)
#endif // INCLUDE_ALL_GCS
#if INCLUDE_JVMCI
 JVMCI_FLAGS(JVMCI_DEVELOP_FLAG_MEMBER, \
             JVMCI_PD_DEVELOP_FLAG_MEMBER, \
             JVMCI_PRODUCT_FLAG_MEMBER, \
             JVMCI_PD_PRODUCT_FLAG_MEMBER, \
             JVMCI_DIAGNOSTIC_FLAG_MEMBER, \
             JVMCI_EXPERIMENTAL_FLAG_MEMBER, \
             JVMCI_NOTPRODUCT_FLAG_MEMBER, \
             IGNORE_RANGE, \
             IGNORE_CONSTRAINT)
#endif // INCLUDE_JVMCI
#ifdef COMPILER1
 C1_FLAGS(C1_DEVELOP_FLAG_MEMBER, \
          C1_PD_DEVELOP_FLAG_MEMBER, \
          C1_PRODUCT_FLAG_MEMBER, \
          C1_PD_PRODUCT_FLAG_MEMBER, \
          C1_DIAGNOSTIC_FLAG_MEMBER, \
          C1_NOTPRODUCT_FLAG_MEMBER, \
          IGNORE_RANGE, \
          IGNORE_CONSTRAINT)
#endif
#ifdef COMPILER2
 C2_FLAGS(C2_DEVELOP_FLAG_MEMBER, \
          C2_PD_DEVELOP_FLAG_MEMBER, \
          C2_PRODUCT_FLAG_MEMBER, \
          C2_PD_PRODUCT_FLAG_MEMBER, \
          C2_DIAGNOSTIC_FLAG_MEMBER, \
          C2_EXPERIMENTAL_FLAG_MEMBER, \
          C2_NOTPRODUCT_FLAG_MEMBER, \
          IGNORE_RANGE, \
          IGNORE_CONSTRAINT)
#endif
 ARCH_FLAGS(ARCH_DEVELOP_FLAG_MEMBER, \
            ARCH_PRODUCT_FLAG_MEMBER, \
            ARCH_DIAGNOSTIC_FLAG_MEMBER, \
            ARCH_EXPERIMENTAL_FLAG_MEMBER, \
            ARCH_NOTPRODUCT_FLAG_MEMBER, \
            IGNORE_RANGE, \
            IGNORE_CONSTRAINT)
 COMMANDLINEFLAG_EXT
 NUM_CommandLineFlag
} CommandLineFlag;

// Construct enum of Flag_<cmdline-arg>_<type> constants.

#define FLAG_MEMBER_WITH_TYPE(flag,type) Flag_##flag##_##type

#define RUNTIME_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)      FLAG_MEMBER_WITH_TYPE(name,type),
#define RUNTIME_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, doc)          FLAG_MEMBER_WITH_TYPE(name,type),
#define RUNTIME_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)   FLAG_MEMBER_WITH_TYPE(name,type),
#define RUNTIME_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE(type, name, value, doc) FLAG_MEMBER_WITH_TYPE(name,type),
#define RUNTIME_MANAGEABLE_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)   FLAG_MEMBER_WITH_TYPE(name,type),
#define RUNTIME_PRODUCT_RW_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)   FLAG_MEMBER_WITH_TYPE(name,type),
#define RUNTIME_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)      FLAG_MEMBER_WITH_TYPE(name,type),
#define RUNTIME_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, doc)          FLAG_MEMBER_WITH_TYPE(name,type),
#define RUNTIME_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)   FLAG_MEMBER_WITH_TYPE(name,type),

#define JVMCI_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)        FLAG_MEMBER_WITH_TYPE(name,type),
#define JVMCI_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, doc)            FLAG_MEMBER_WITH_TYPE(name,type),
#define JVMCI_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)        FLAG_MEMBER_WITH_TYPE(name,type),
#define JVMCI_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, doc)            FLAG_MEMBER_WITH_TYPE(name,type),
#define JVMCI_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)     FLAG_MEMBER_WITH_TYPE(name,type),
#define JVMCI_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)   FLAG_MEMBER_WITH_TYPE(name,type),
#define JVMCI_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)     FLAG_MEMBER_WITH_TYPE(name,type),

#define C1_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)           FLAG_MEMBER_WITH_TYPE(name,type),
#define C1_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, doc)               FLAG_MEMBER_WITH_TYPE(name,type),
#define C1_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)        FLAG_MEMBER_WITH_TYPE(name,type),
#define C1_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)           FLAG_MEMBER_WITH_TYPE(name,type),
#define C1_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, doc)               FLAG_MEMBER_WITH_TYPE(name,type),
#define C1_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)        FLAG_MEMBER_WITH_TYPE(name,type),

#ifdef _LP64
#define RUNTIME_LP64_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc) FLAG_MEMBER_WITH_TYPE(name,type),
#else
#define RUNTIME_LP64_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc) /* flag is constant */
#endif // _LP64

#define C2_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)           FLAG_MEMBER_WITH_TYPE(name,type),
#define C2_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, doc)               FLAG_MEMBER_WITH_TYPE(name,type),
#define C2_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)        FLAG_MEMBER_WITH_TYPE(name,type),
#define C2_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)      FLAG_MEMBER_WITH_TYPE(name,type),
#define C2_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)           FLAG_MEMBER_WITH_TYPE(name,type),
#define C2_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, doc)               FLAG_MEMBER_WITH_TYPE(name,type),
#define C2_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)        FLAG_MEMBER_WITH_TYPE(name,type),

#define ARCH_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)         FLAG_MEMBER_WITH_TYPE(name,type),
#define ARCH_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)      FLAG_MEMBER_WITH_TYPE(name,type),
#define ARCH_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)    FLAG_MEMBER_WITH_TYPE(name,type),
#define ARCH_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)         FLAG_MEMBER_WITH_TYPE(name,type),
#define ARCH_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)      FLAG_MEMBER_WITH_TYPE(name,type),

typedef enum {
 RUNTIME_FLAGS(RUNTIME_DEVELOP_FLAG_MEMBER_WITH_TYPE,
               RUNTIME_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE,
               RUNTIME_PRODUCT_FLAG_MEMBER_WITH_TYPE,
               RUNTIME_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE,
               RUNTIME_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
               RUNTIME_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE,
               RUNTIME_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE,
               RUNTIME_MANAGEABLE_FLAG_MEMBER_WITH_TYPE,
               RUNTIME_PRODUCT_RW_FLAG_MEMBER_WITH_TYPE,
               RUNTIME_LP64_PRODUCT_FLAG_MEMBER_WITH_TYPE,
               IGNORE_RANGE,
               IGNORE_CONSTRAINT)
 RUNTIME_OS_FLAGS(RUNTIME_DEVELOP_FLAG_MEMBER_WITH_TYPE,
                  RUNTIME_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE,
                  RUNTIME_PRODUCT_FLAG_MEMBER_WITH_TYPE,
                  RUNTIME_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE,
                  RUNTIME_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
                  RUNTIME_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE,
                  IGNORE_RANGE,
                  IGNORE_CONSTRAINT)
#if INCLUDE_ALL_GCS
 G1_FLAGS(RUNTIME_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          RUNTIME_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          RUNTIME_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          RUNTIME_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          RUNTIME_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
          RUNTIME_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE,
          RUNTIME_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE,
          RUNTIME_MANAGEABLE_FLAG_MEMBER_WITH_TYPE,
          RUNTIME_PRODUCT_RW_FLAG_MEMBER_WITH_TYPE,
          IGNORE_RANGE,
          IGNORE_CONSTRAINT)
#endif // INCLUDE_ALL_GCS
#if INCLUDE_JVMCI
 JVMCI_FLAGS(JVMCI_DEVELOP_FLAG_MEMBER_WITH_TYPE,
             JVMCI_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE,
             JVMCI_PRODUCT_FLAG_MEMBER_WITH_TYPE,
             JVMCI_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE,
             JVMCI_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
             JVMCI_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE,
             JVMCI_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE,
             IGNORE_RANGE,
             IGNORE_CONSTRAINT)
#endif // INCLUDE_JVMCI
#ifdef COMPILER1
 C1_FLAGS(C1_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          C1_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          C1_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          C1_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          C1_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
          C1_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE,
          IGNORE_RANGE,
          IGNORE_CONSTRAINT)
#endif
#ifdef COMPILER2
 C2_FLAGS(C2_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          C2_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          C2_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          C2_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          C2_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
          C2_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE,
          C2_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE,
          IGNORE_RANGE,
          IGNORE_CONSTRAINT)
#endif
 ARCH_FLAGS(ARCH_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          ARCH_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          ARCH_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
          ARCH_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE,
          ARCH_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE,
          IGNORE_RANGE,
          IGNORE_CONSTRAINT)
 COMMANDLINEFLAGWITHTYPE_EXT
 NUM_CommandLineFlagWithType
} CommandLineFlagWithType;

#define FLAG_IS_DEFAULT(name)         (CommandLineFlagsEx::is_default(FLAG_MEMBER(name)))
#define FLAG_IS_ERGO(name)            (CommandLineFlagsEx::is_ergo(FLAG_MEMBER(name)))
#define FLAG_IS_CMDLINE(name)         (CommandLineFlagsEx::is_cmdline(FLAG_MEMBER(name)))

#define FLAG_SET_DEFAULT(name, value) ((name) = (value))

#define FLAG_SET_CMDLINE(type, name, value) (CommandLineFlagsEx::type##AtPut(FLAG_MEMBER_WITH_TYPE(name,type), (type)(value), Flag::COMMAND_LINE))
#define FLAG_SET_ERGO(type, name, value)    (CommandLineFlagsEx::type##AtPut(FLAG_MEMBER_WITH_TYPE(name,type), (type)(value), Flag::ERGONOMIC))
#define FLAG_SET_ERGO_IF_DEFAULT(type, name, value) \
  do {                                              \
    if (FLAG_IS_DEFAULT(name)) {                    \
      FLAG_SET_ERGO(type, name, value);             \
    }                                               \
  } while (0)

// Can't put the following in CommandLineFlags because
// of a circular dependency on the enum definition.
class CommandLineFlagsEx : CommandLineFlags {
 public:
  static Flag::Error boolAtPut(CommandLineFlagWithType flag, bool value, Flag::Flags origin);
  static Flag::Error intAtPut(CommandLineFlagWithType flag, int value, Flag::Flags origin);
  static Flag::Error uintAtPut(CommandLineFlagWithType flag, uint value, Flag::Flags origin);
  static Flag::Error intxAtPut(CommandLineFlagWithType flag, intx value, Flag::Flags origin);
  static Flag::Error uintxAtPut(CommandLineFlagWithType flag, uintx value, Flag::Flags origin);
  static Flag::Error uint64_tAtPut(CommandLineFlagWithType flag, uint64_t value, Flag::Flags origin);
  static Flag::Error size_tAtPut(CommandLineFlagWithType flag, size_t value, Flag::Flags origin);
  static Flag::Error doubleAtPut(CommandLineFlagWithType flag, double value, Flag::Flags origin);
  // Contract:  Flag will make private copy of the incoming value
  static Flag::Error ccstrAtPut(CommandLineFlagWithType flag, ccstr value, Flag::Flags origin);

  static bool is_default(CommandLineFlag flag);
  static bool is_ergo(CommandLineFlag flag);
  static bool is_cmdline(CommandLineFlag flag);
};

#endif // SHARE_VM_RUNTIME_GLOBALS_EXTENSION_HPP
