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
#if INCLUDE_JVMCI
#include "jvmci/jvmci_globals.hpp"
#endif
#ifdef COMPILER1
#include "c1/c1_globals.hpp"
#endif
#ifdef COMPILER2
#include "opto/c2_globals.hpp"
#endif

// Construct enum of Flag_<cmdline-arg> constants.

// Parenthesis left off in the following for the enum decl below.
#define FLAG_MEMBER(flag) Flag_##flag

#define RUNTIME_PRODUCT_FLAG_MEMBER(type, name, value, doc)      FLAG_MEMBER(name),
#define RUNTIME_PD_PRODUCT_FLAG_MEMBER(type, name, doc)          FLAG_MEMBER(name),
#define RUNTIME_DIAGNOSTIC_FLAG_MEMBER(type, name, value, doc)   FLAG_MEMBER(name),
#define RUNTIME_PD_DIAGNOSTIC_FLAG_MEMBER(type, name, doc)       FLAG_MEMBER(name),
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
#define JVMCI_PD_DIAGNOSTIC_FLAG_MEMBER(type, name, doc)         FLAG_MEMBER(name),
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
#define C1_PD_DIAGNOSTIC_FLAG_MEMBER(type, name, doc)            FLAG_MEMBER(name),
#define C1_DEVELOP_FLAG_MEMBER(type, name, value, doc)           FLAG_MEMBER(name),
#define C1_PD_DEVELOP_FLAG_MEMBER(type, name, doc)               FLAG_MEMBER(name),
#define C1_NOTPRODUCT_FLAG_MEMBER(type, name, value, doc)        FLAG_MEMBER(name),

#define C2_PRODUCT_FLAG_MEMBER(type, name, value, doc)           FLAG_MEMBER(name),
#define C2_PD_PRODUCT_FLAG_MEMBER(type, name, doc)               FLAG_MEMBER(name),
#define C2_DIAGNOSTIC_FLAG_MEMBER(type, name, value, doc)        FLAG_MEMBER(name),
#define C2_PD_DIAGNOSTIC_FLAG_MEMBER(type, name, doc)            FLAG_MEMBER(name),
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
 VM_FLAGS(RUNTIME_DEVELOP_FLAG_MEMBER, \
          RUNTIME_PD_DEVELOP_FLAG_MEMBER, \
          RUNTIME_PRODUCT_FLAG_MEMBER, \
          RUNTIME_PD_PRODUCT_FLAG_MEMBER, \
          RUNTIME_DIAGNOSTIC_FLAG_MEMBER, \
          RUNTIME_PD_DIAGNOSTIC_FLAG_MEMBER, \
          RUNTIME_EXPERIMENTAL_FLAG_MEMBER, \
          RUNTIME_NOTPRODUCT_FLAG_MEMBER, \
          RUNTIME_MANAGEABLE_FLAG_MEMBER, \
          RUNTIME_PRODUCT_RW_FLAG_MEMBER, \
          RUNTIME_LP64_PRODUCT_FLAG_MEMBER, \
          IGNORE_RANGE, \
          IGNORE_CONSTRAINT, \
          IGNORE_WRITEABLE)

 RUNTIME_OS_FLAGS(RUNTIME_DEVELOP_FLAG_MEMBER,    \
                  RUNTIME_PD_DEVELOP_FLAG_MEMBER, \
                  RUNTIME_PRODUCT_FLAG_MEMBER, \
                  RUNTIME_PD_PRODUCT_FLAG_MEMBER, \
                  RUNTIME_DIAGNOSTIC_FLAG_MEMBER, \
                  RUNTIME_PD_DIAGNOSTIC_FLAG_MEMBER, \
                  RUNTIME_NOTPRODUCT_FLAG_MEMBER, \
                  IGNORE_RANGE, \
                  IGNORE_CONSTRAINT, \
                  IGNORE_WRITEABLE)
#if INCLUDE_JVMCI
 JVMCI_FLAGS(JVMCI_DEVELOP_FLAG_MEMBER, \
             JVMCI_PD_DEVELOP_FLAG_MEMBER, \
             JVMCI_PRODUCT_FLAG_MEMBER, \
             JVMCI_PD_PRODUCT_FLAG_MEMBER, \
             JVMCI_DIAGNOSTIC_FLAG_MEMBER, \
             JVMCI_PD_DIAGNOSTIC_FLAG_MEMBER, \
             JVMCI_EXPERIMENTAL_FLAG_MEMBER, \
             JVMCI_NOTPRODUCT_FLAG_MEMBER, \
             IGNORE_RANGE, \
             IGNORE_CONSTRAINT, \
             IGNORE_WRITEABLE)
#endif // INCLUDE_JVMCI
#ifdef COMPILER1
 C1_FLAGS(C1_DEVELOP_FLAG_MEMBER, \
          C1_PD_DEVELOP_FLAG_MEMBER, \
          C1_PRODUCT_FLAG_MEMBER, \
          C1_PD_PRODUCT_FLAG_MEMBER, \
          C1_DIAGNOSTIC_FLAG_MEMBER, \
          C1_PD_DIAGNOSTIC_FLAG_MEMBER, \
          C1_NOTPRODUCT_FLAG_MEMBER, \
          IGNORE_RANGE, \
          IGNORE_CONSTRAINT, \
          IGNORE_WRITEABLE)
#endif
#ifdef COMPILER2
 C2_FLAGS(C2_DEVELOP_FLAG_MEMBER, \
          C2_PD_DEVELOP_FLAG_MEMBER, \
          C2_PRODUCT_FLAG_MEMBER, \
          C2_PD_PRODUCT_FLAG_MEMBER, \
          C2_DIAGNOSTIC_FLAG_MEMBER, \
          C2_PD_DIAGNOSTIC_FLAG_MEMBER, \
          C2_EXPERIMENTAL_FLAG_MEMBER, \
          C2_NOTPRODUCT_FLAG_MEMBER, \
          IGNORE_RANGE, \
          IGNORE_CONSTRAINT, \
          IGNORE_WRITEABLE)
#endif
 ARCH_FLAGS(ARCH_DEVELOP_FLAG_MEMBER, \
            ARCH_PRODUCT_FLAG_MEMBER, \
            ARCH_DIAGNOSTIC_FLAG_MEMBER, \
            ARCH_EXPERIMENTAL_FLAG_MEMBER, \
            ARCH_NOTPRODUCT_FLAG_MEMBER, \
            IGNORE_RANGE, \
            IGNORE_CONSTRAINT, \
            IGNORE_WRITEABLE)
 JVMFLAGS_EXT
 NUM_JVMFlags
} JVMFlags;

// Construct enum of Flag_<cmdline-arg>_<type> constants.

#define FLAG_MEMBER_WITH_TYPE(flag,type) Flag_##flag##_##type

#define RUNTIME_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)      FLAG_MEMBER_WITH_TYPE(name,type),
#define RUNTIME_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, doc)          FLAG_MEMBER_WITH_TYPE(name,type),
#define RUNTIME_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)   FLAG_MEMBER_WITH_TYPE(name,type),
#define RUNTIME_PD_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE(type, name, doc)       FLAG_MEMBER_WITH_TYPE(name,type),
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
#define JVMCI_PD_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE(type, name, doc)         FLAG_MEMBER_WITH_TYPE(name,type),
#define JVMCI_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)   FLAG_MEMBER_WITH_TYPE(name,type),
#define JVMCI_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)     FLAG_MEMBER_WITH_TYPE(name,type),

#define C1_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)           FLAG_MEMBER_WITH_TYPE(name,type),
#define C1_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, doc)               FLAG_MEMBER_WITH_TYPE(name,type),
#define C1_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)        FLAG_MEMBER_WITH_TYPE(name,type),
#define C1_PD_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE(type, name, doc)            FLAG_MEMBER_WITH_TYPE(name,type),
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
#define C2_PD_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE(type, name, doc)            FLAG_MEMBER_WITH_TYPE(name,type),
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
  VM_FLAGS(RUNTIME_DEVELOP_FLAG_MEMBER_WITH_TYPE,
           RUNTIME_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE,
           RUNTIME_PRODUCT_FLAG_MEMBER_WITH_TYPE,
           RUNTIME_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE,
           RUNTIME_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
           RUNTIME_PD_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
           RUNTIME_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE,
           RUNTIME_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE,
           RUNTIME_MANAGEABLE_FLAG_MEMBER_WITH_TYPE,
           RUNTIME_PRODUCT_RW_FLAG_MEMBER_WITH_TYPE,
           RUNTIME_LP64_PRODUCT_FLAG_MEMBER_WITH_TYPE,
           IGNORE_RANGE,
           IGNORE_CONSTRAINT,
           IGNORE_WRITEABLE)

 RUNTIME_OS_FLAGS(RUNTIME_DEVELOP_FLAG_MEMBER_WITH_TYPE,
                  RUNTIME_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE,
                  RUNTIME_PRODUCT_FLAG_MEMBER_WITH_TYPE,
                  RUNTIME_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE,
                  RUNTIME_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
                  RUNTIME_PD_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
                  RUNTIME_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE,
                  IGNORE_RANGE,
                  IGNORE_CONSTRAINT,
                  IGNORE_WRITEABLE)
#if INCLUDE_JVMCI
 JVMCI_FLAGS(JVMCI_DEVELOP_FLAG_MEMBER_WITH_TYPE,
             JVMCI_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE,
             JVMCI_PRODUCT_FLAG_MEMBER_WITH_TYPE,
             JVMCI_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE,
             JVMCI_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
             JVMCI_PD_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
             JVMCI_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE,
             JVMCI_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE,
             IGNORE_RANGE,
             IGNORE_CONSTRAINT,
             IGNORE_WRITEABLE)
#endif // INCLUDE_JVMCI
#ifdef COMPILER1
 C1_FLAGS(C1_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          C1_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          C1_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          C1_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          C1_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
          C1_PD_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
          C1_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE,
          IGNORE_RANGE,
          IGNORE_CONSTRAINT,
          IGNORE_WRITEABLE)
#endif
#ifdef COMPILER2
 C2_FLAGS(C2_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          C2_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          C2_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          C2_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          C2_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
          C2_PD_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
          C2_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE,
          C2_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE,
          IGNORE_RANGE,
          IGNORE_CONSTRAINT,
          IGNORE_WRITEABLE)
#endif
 ARCH_FLAGS(ARCH_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          ARCH_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          ARCH_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
          ARCH_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE,
          ARCH_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE,
          IGNORE_RANGE,
          IGNORE_CONSTRAINT,
          IGNORE_WRITEABLE)
  JVMFLAGSWITHTYPE_EXT
  NUM_JVMFlagsWithType
} JVMFlagsWithType;

#define FLAG_IS_DEFAULT(name)         (JVMFlagEx::is_default(FLAG_MEMBER(name)))
#define FLAG_IS_ERGO(name)            (JVMFlagEx::is_ergo(FLAG_MEMBER(name)))
#define FLAG_IS_CMDLINE(name)         (JVMFlagEx::is_cmdline(FLAG_MEMBER(name)))

#define FLAG_SET_DEFAULT(name, value) ((name) = (value))

#define FLAG_SET_CMDLINE(type, name, value) (JVMFlagEx::setOnCmdLine(FLAG_MEMBER_WITH_TYPE(name, type)), \
                                             JVMFlagEx::type##AtPut(FLAG_MEMBER_WITH_TYPE(name, type), (type)(value), JVMFlag::COMMAND_LINE))
#define FLAG_SET_ERGO(type, name, value)    (JVMFlagEx::type##AtPut(FLAG_MEMBER_WITH_TYPE(name, type), (type)(value), JVMFlag::ERGONOMIC))
#define FLAG_SET_ERGO_IF_DEFAULT(type, name, value) \
  do {                                              \
    if (FLAG_IS_DEFAULT(name)) {                    \
      FLAG_SET_ERGO(type, name, value);             \
    }                                               \
  } while (0)

// Can't put the following in JVMFlags because
// of a circular dependency on the enum definition.
class JVMFlagEx : JVMFlag {
 public:
  static JVMFlag::Error boolAtPut(JVMFlagsWithType flag, bool value, JVMFlag::Flags origin);
  static JVMFlag::Error intAtPut(JVMFlagsWithType flag, int value, JVMFlag::Flags origin);
  static JVMFlag::Error uintAtPut(JVMFlagsWithType flag, uint value, JVMFlag::Flags origin);
  static JVMFlag::Error intxAtPut(JVMFlagsWithType flag, intx value, JVMFlag::Flags origin);
  static JVMFlag::Error uintxAtPut(JVMFlagsWithType flag, uintx value, JVMFlag::Flags origin);
  static JVMFlag::Error uint64_tAtPut(JVMFlagsWithType flag, uint64_t value, JVMFlag::Flags origin);
  static JVMFlag::Error size_tAtPut(JVMFlagsWithType flag, size_t value, JVMFlag::Flags origin);
  static JVMFlag::Error doubleAtPut(JVMFlagsWithType flag, double value, JVMFlag::Flags origin);
  // Contract:  Flag will make private copy of the incoming value
  static JVMFlag::Error ccstrAtPut(JVMFlagsWithType flag, ccstr value, JVMFlag::Flags origin);

  static bool is_default(JVMFlags flag);
  static bool is_ergo(JVMFlags flag);
  static bool is_cmdline(JVMFlags flag);

  static void setOnCmdLine(JVMFlagsWithType flag);
};

#endif // SHARE_VM_RUNTIME_GLOBALS_EXTENSION_HPP
