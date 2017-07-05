/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/top.hpp"

// Construct enum of Flag_<cmdline-arg> constants.

// Parens left off in the following for the enum decl below.
#define FLAG_MEMBER(flag) Flag_##flag

#define RUNTIME_PRODUCT_FLAG_MEMBER(type, name, value, doc)    FLAG_MEMBER(name),
#define RUNTIME_PD_PRODUCT_FLAG_MEMBER(type, name, doc)        FLAG_MEMBER(name),
#define RUNTIME_DIAGNOSTIC_FLAG_MEMBER(type, name, value, doc) FLAG_MEMBER(name),
#define RUNTIME_EXPERIMENTAL_FLAG_MEMBER(type, name, value, doc) FLAG_MEMBER(name),
#define RUNTIME_MANAGEABLE_FLAG_MEMBER(type, name, value, doc) FLAG_MEMBER(name),
#define RUNTIME_PRODUCT_RW_FLAG_MEMBER(type, name, value, doc) FLAG_MEMBER(name),
#ifdef PRODUCT
  #define RUNTIME_DEVELOP_FLAG_MEMBER(type, name, value, doc)  /* flag is constant */
  #define RUNTIME_PD_DEVELOP_FLAG_MEMBER(type, name, doc)      /* flag is constant */
  #define RUNTIME_NOTPRODUCT_FLAG_MEMBER(type, name, value, doc)
#else
  #define RUNTIME_DEVELOP_FLAG_MEMBER(type, name, value, doc)  FLAG_MEMBER(name),
  #define RUNTIME_PD_DEVELOP_FLAG_MEMBER(type, name, doc)      FLAG_MEMBER(name),
  #define RUNTIME_NOTPRODUCT_FLAG_MEMBER(type, name, value, doc) FLAG_MEMBER(name),
#endif
#ifdef _LP64
#define RUNTIME_LP64_PRODUCT_FLAG_MEMBER(type, name, value, doc)    FLAG_MEMBER(name),
#else
#define RUNTIME_LP64_PRODUCT_FLAG_MEMBER(type, name, value, doc)    /* flag is constant */
#endif // _LP64

#define C1_PRODUCT_FLAG_MEMBER(type, name, value, doc)         FLAG_MEMBER(name),
#define C1_PD_PRODUCT_FLAG_MEMBER(type, name, doc)             FLAG_MEMBER(name),
#ifdef PRODUCT
  #define C1_DEVELOP_FLAG_MEMBER(type, name, value, doc)       /* flag is constant */
  #define C1_PD_DEVELOP_FLAG_MEMBER(type, name, doc)           /* flag is constant */
  #define C1_NOTPRODUCT_FLAG_MEMBER(type, name, value, doc)
#else
  #define C1_DEVELOP_FLAG_MEMBER(type, name, value, doc)       FLAG_MEMBER(name),
  #define C1_PD_DEVELOP_FLAG_MEMBER(type, name, doc)           FLAG_MEMBER(name),
  #define C1_NOTPRODUCT_FLAG_MEMBER(type, name, value, doc)    FLAG_MEMBER(name),
#endif


#define C2_PRODUCT_FLAG_MEMBER(type, name, value, doc)         FLAG_MEMBER(name),
#define C2_PD_PRODUCT_FLAG_MEMBER(type, name, doc)             FLAG_MEMBER(name),
#define C2_DIAGNOSTIC_FLAG_MEMBER(type, name, value, doc)      FLAG_MEMBER(name),
#define C2_EXPERIMENTAL_FLAG_MEMBER(type, name, value, doc)    FLAG_MEMBER(name),
#ifdef PRODUCT
  #define C2_DEVELOP_FLAG_MEMBER(type, name, value, doc)       /* flag is constant */
  #define C2_PD_DEVELOP_FLAG_MEMBER(type, name, doc)           /* flag is constant */
  #define C2_NOTPRODUCT_FLAG_MEMBER(type, name, value, doc)
#else
  #define C2_DEVELOP_FLAG_MEMBER(type, name, value, doc)       FLAG_MEMBER(name),
  #define C2_PD_DEVELOP_FLAG_MEMBER(type, name, doc)           FLAG_MEMBER(name),
  #define C2_NOTPRODUCT_FLAG_MEMBER(type, name, value, doc)    FLAG_MEMBER(name),
#endif

typedef enum {
 RUNTIME_FLAGS(RUNTIME_DEVELOP_FLAG_MEMBER, RUNTIME_PD_DEVELOP_FLAG_MEMBER, RUNTIME_PRODUCT_FLAG_MEMBER, RUNTIME_PD_PRODUCT_FLAG_MEMBER, RUNTIME_DIAGNOSTIC_FLAG_MEMBER, RUNTIME_EXPERIMENTAL_FLAG_MEMBER, RUNTIME_NOTPRODUCT_FLAG_MEMBER, RUNTIME_MANAGEABLE_FLAG_MEMBER, RUNTIME_PRODUCT_RW_FLAG_MEMBER, RUNTIME_LP64_PRODUCT_FLAG_MEMBER)
 RUNTIME_OS_FLAGS(RUNTIME_DEVELOP_FLAG_MEMBER, RUNTIME_PD_DEVELOP_FLAG_MEMBER, RUNTIME_PRODUCT_FLAG_MEMBER, RUNTIME_PD_PRODUCT_FLAG_MEMBER, RUNTIME_DIAGNOSTIC_FLAG_MEMBER, RUNTIME_NOTPRODUCT_FLAG_MEMBER)
#ifndef KERNEL
 G1_FLAGS(RUNTIME_DEVELOP_FLAG_MEMBER, RUNTIME_PD_DEVELOP_FLAG_MEMBER, RUNTIME_PRODUCT_FLAG_MEMBER, RUNTIME_PD_PRODUCT_FLAG_MEMBER, RUNTIME_DIAGNOSTIC_FLAG_MEMBER, RUNTIME_EXPERIMENTAL_FLAG_MEMBER, RUNTIME_NOTPRODUCT_FLAG_MEMBER, RUNTIME_MANAGEABLE_FLAG_MEMBER, RUNTIME_PRODUCT_RW_FLAG_MEMBER)
#endif
#ifdef COMPILER1
 C1_FLAGS(C1_DEVELOP_FLAG_MEMBER, C1_PD_DEVELOP_FLAG_MEMBER, C1_PRODUCT_FLAG_MEMBER, C1_PD_PRODUCT_FLAG_MEMBER, C1_NOTPRODUCT_FLAG_MEMBER)
#endif
#ifdef COMPILER2
 C2_FLAGS(C2_DEVELOP_FLAG_MEMBER, C2_PD_DEVELOP_FLAG_MEMBER, C2_PRODUCT_FLAG_MEMBER, C2_PD_PRODUCT_FLAG_MEMBER, C2_DIAGNOSTIC_FLAG_MEMBER, C2_EXPERIMENTAL_FLAG_MEMBER, C2_NOTPRODUCT_FLAG_MEMBER)
#endif
 NUM_CommandLineFlag
} CommandLineFlag;

// Construct enum of Flag_<cmdline-arg>_<type> constants.

#define FLAG_MEMBER_WITH_TYPE(flag,type) Flag_##flag##_##type

#define RUNTIME_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)    FLAG_MEMBER_WITH_TYPE(name,type),
#define RUNTIME_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, doc)        FLAG_MEMBER_WITH_TYPE(name,type),
#define RUNTIME_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE(type, name, value, doc) FLAG_MEMBER_WITH_TYPE(name,type),
#define RUNTIME_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE(type, name, value, doc) FLAG_MEMBER_WITH_TYPE(name,type),
#define RUNTIME_MANAGEABLE_FLAG_MEMBER_WITH_TYPE(type, name, value, doc) FLAG_MEMBER_WITH_TYPE(name,type),
#define RUNTIME_PRODUCT_RW_FLAG_MEMBER_WITH_TYPE(type, name, value, doc) FLAG_MEMBER_WITH_TYPE(name,type),
#ifdef PRODUCT
  #define RUNTIME_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)     /* flag is constant */
  #define RUNTIME_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, doc)         /* flag is constant */
  #define RUNTIME_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)
#else
  #define RUNTIME_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)     FLAG_MEMBER_WITH_TYPE(name,type),
  #define RUNTIME_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, doc)         FLAG_MEMBER_WITH_TYPE(name,type),
  #define RUNTIME_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)  FLAG_MEMBER_WITH_TYPE(name,type),
#endif

#define C1_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)         FLAG_MEMBER_WITH_TYPE(name,type),
#define C1_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, doc)             FLAG_MEMBER_WITH_TYPE(name,type),
#ifdef PRODUCT
  #define C1_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)       /* flag is constant */
  #define C1_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, doc)           /* flag is constant */
  #define C1_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)
#else
  #define C1_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)       FLAG_MEMBER_WITH_TYPE(name,type),
  #define C1_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, doc)           FLAG_MEMBER_WITH_TYPE(name,type),
  #define C1_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)    FLAG_MEMBER_WITH_TYPE(name,type),
#endif
#ifdef _LP64
#define RUNTIME_LP64_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)    FLAG_MEMBER_WITH_TYPE(name,type),
#else
#define RUNTIME_LP64_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)    /* flag is constant */
#endif // _LP64


#define C2_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)         FLAG_MEMBER_WITH_TYPE(name,type),
#define C2_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, doc)             FLAG_MEMBER_WITH_TYPE(name,type),
#define C2_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)      FLAG_MEMBER_WITH_TYPE(name,type),
#define C2_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)      FLAG_MEMBER_WITH_TYPE(name,type),
#ifdef PRODUCT
  #define C2_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)       /* flag is constant */
  #define C2_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, doc)           /* flag is constant */
  #define C2_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)
#else
  #define C2_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)       FLAG_MEMBER_WITH_TYPE(name,type),
  #define C2_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE(type, name, doc)           FLAG_MEMBER_WITH_TYPE(name,type),
  #define C2_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE(type, name, value, doc)    FLAG_MEMBER_WITH_TYPE(name,type),
#endif

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
               RUNTIME_LP64_PRODUCT_FLAG_MEMBER_WITH_TYPE)
 RUNTIME_OS_FLAGS(RUNTIME_DEVELOP_FLAG_MEMBER_WITH_TYPE,
                  RUNTIME_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE,
                  RUNTIME_PRODUCT_FLAG_MEMBER_WITH_TYPE,
                  RUNTIME_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE,
                  RUNTIME_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
                  RUNTIME_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE)
#ifndef KERNEL
 G1_FLAGS(RUNTIME_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          RUNTIME_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          RUNTIME_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          RUNTIME_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          RUNTIME_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
          RUNTIME_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE,
          RUNTIME_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE,
          RUNTIME_MANAGEABLE_FLAG_MEMBER_WITH_TYPE,
          RUNTIME_PRODUCT_RW_FLAG_MEMBER_WITH_TYPE)
#endif // KERNEL
#ifdef COMPILER1
 C1_FLAGS(C1_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          C1_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          C1_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          C1_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          C1_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE)
#endif
#ifdef COMPILER2
 C2_FLAGS(C2_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          C2_PD_DEVELOP_FLAG_MEMBER_WITH_TYPE,
          C2_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          C2_PD_PRODUCT_FLAG_MEMBER_WITH_TYPE,
          C2_DIAGNOSTIC_FLAG_MEMBER_WITH_TYPE,
          C2_EXPERIMENTAL_FLAG_MEMBER_WITH_TYPE,
          C2_NOTPRODUCT_FLAG_MEMBER_WITH_TYPE)
#endif
 NUM_CommandLineFlagWithType
} CommandLineFlagWithType;

#define FLAG_IS_DEFAULT(name)         (CommandLineFlagsEx::is_default(FLAG_MEMBER(name)))
#define FLAG_IS_ERGO(name)            (CommandLineFlagsEx::is_ergo(FLAG_MEMBER(name)))
#define FLAG_IS_CMDLINE(name)         (CommandLineFlagsEx::is_cmdline(FLAG_MEMBER(name)))

#define FLAG_SET_DEFAULT(name, value) ((name) = (value))

#define FLAG_SET_CMDLINE(type, name, value) (CommandLineFlagsEx::type##AtPut(FLAG_MEMBER_WITH_TYPE(name,type), (type)(value), COMMAND_LINE))
#define FLAG_SET_ERGO(type, name, value)    (CommandLineFlagsEx::type##AtPut(FLAG_MEMBER_WITH_TYPE(name,type), (type)(value), ERGONOMIC))

// Can't put the following in CommandLineFlags because
// of a circular dependency on the enum definition.
class CommandLineFlagsEx : CommandLineFlags {
 public:
  static void boolAtPut(CommandLineFlagWithType flag, bool value, FlagValueOrigin origin);
  static void intxAtPut(CommandLineFlagWithType flag, intx value, FlagValueOrigin origin);
  static void uintxAtPut(CommandLineFlagWithType flag, uintx value, FlagValueOrigin origin);
  static void uint64_tAtPut(CommandLineFlagWithType flag, uint64_t value, FlagValueOrigin origin);
  static void doubleAtPut(CommandLineFlagWithType flag, double value, FlagValueOrigin origin);
  static void ccstrAtPut(CommandLineFlagWithType flag, ccstr value, FlagValueOrigin origin);

  static bool is_default(CommandLineFlag flag);
  static bool is_ergo(CommandLineFlag flag);
  static bool is_cmdline(CommandLineFlag flag);
};

#endif // SHARE_VM_RUNTIME_GLOBALS_EXTENSION_HPP
