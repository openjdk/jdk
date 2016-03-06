/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "jvmci/jvmci_globals.hpp"
#include "utilities/defaultStream.hpp"
#include "runtime/globals_extension.hpp"

JVMCI_FLAGS(MATERIALIZE_DEVELOPER_FLAG, \
            MATERIALIZE_PD_DEVELOPER_FLAG, \
            MATERIALIZE_PRODUCT_FLAG, \
            MATERIALIZE_PD_PRODUCT_FLAG, \
            MATERIALIZE_DIAGNOSTIC_FLAG, \
            MATERIALIZE_EXPERIMENTAL_FLAG, \
            MATERIALIZE_NOTPRODUCT_FLAG,
            IGNORE_RANGE, \
            IGNORE_CONSTRAINT)

#define JVMCI_IGNORE_FLAG_FOUR_PARAM(type, name, value, doc)
#define JVMCI_IGNORE_FLAG_THREE_PARAM(type, name, doc)

// Return true if jvmci flags are consistent.
bool JVMCIGlobals::check_jvmci_flags_are_consistent() {
  if (EnableJVMCI) {
    return true;
  }

  // "FLAG_IS_DEFAULT" fail count.
  int fail_count = 0;
  // Number of "FLAG_IS_DEFAULT" fails that should be skipped before code
  // detect real consistency failure.
  int skip_fail_count;

  // EnableJVMCI flag is false here.
  // If any other flag is changed, consistency check should fail.
  // JVMCI_FLAGS macros added below can handle all JVMCI flags automatically.
  // But it contains check for EnableJVMCI flag too, which is required to be
  // skipped. This can't be handled easily!
  // So the code looks for at-least two flag changes to detect consistency
  // failure when EnableJVMCI flag is changed.
  // Otherwise one flag change is sufficient to detect consistency failure.
  // Set skip_fail_count to 0 if EnableJVMCI flag is default.
  // Set skip_fail_count to 1 if EnableJVMCI flag is changed.
  // This value will be used to skip fails in macro expanded code later.
  if (!FLAG_IS_DEFAULT(EnableJVMCI)) {
    skip_fail_count = 1;
  } else {
    skip_fail_count = 0;
  }

#define EMIT_FLAG_VALUE_CHANGED_CHECK_CODE(FLAG)  \
  if (!FLAG_IS_DEFAULT(FLAG)) {                   \
    fail_count++;                                 \
    if (fail_count > skip_fail_count) {           \
      return false;                               \
    }                                             \
  }

#define JVMCI_DIAGNOSTIC_FLAG_VALUE_CHANGED_CHECK_CODE(type, name, value, doc)     EMIT_FLAG_VALUE_CHANGED_CHECK_CODE(name)
#define JVMCI_EXPERIMENTAL_FLAG_VALUE_CHANGED_CHECK_CODE(type, name, value, doc)   EMIT_FLAG_VALUE_CHANGED_CHECK_CODE(name)

  // Check consistency of diagnostic flags if UnlockDiagnosticVMOptions is true
  // or not default. UnlockDiagnosticVMOptions is default true in debug builds.
  if (UnlockDiagnosticVMOptions || !FLAG_IS_DEFAULT(UnlockDiagnosticVMOptions)) {
    JVMCI_FLAGS(JVMCI_IGNORE_FLAG_FOUR_PARAM, \
                JVMCI_IGNORE_FLAG_THREE_PARAM, \
                JVMCI_IGNORE_FLAG_FOUR_PARAM, \
                JVMCI_IGNORE_FLAG_THREE_PARAM, \
                JVMCI_DIAGNOSTIC_FLAG_VALUE_CHANGED_CHECK_CODE, \
                JVMCI_IGNORE_FLAG_FOUR_PARAM, \
                JVMCI_IGNORE_FLAG_FOUR_PARAM, \
                IGNORE_RANGE, \
                IGNORE_CONSTRAINT)
  }

  // Check consistency of experimental flags if UnlockExperimentalVMOptions is
  // true or not default.
  if (UnlockExperimentalVMOptions || !FLAG_IS_DEFAULT(UnlockExperimentalVMOptions)) {
    JVMCI_FLAGS(JVMCI_IGNORE_FLAG_FOUR_PARAM, \
                JVMCI_IGNORE_FLAG_THREE_PARAM, \
                JVMCI_IGNORE_FLAG_FOUR_PARAM, \
                JVMCI_IGNORE_FLAG_THREE_PARAM, \
                JVMCI_IGNORE_FLAG_FOUR_PARAM, \
                JVMCI_EXPERIMENTAL_FLAG_VALUE_CHANGED_CHECK_CODE, \
                JVMCI_IGNORE_FLAG_FOUR_PARAM, \
                IGNORE_RANGE, \
                IGNORE_CONSTRAINT)
  }

#ifndef PRODUCT
#define JVMCI_DEVELOP_FLAG_VALUE_CHANGED_CHECK_CODE(type, name, value, doc)        EMIT_FLAG_VALUE_CHANGED_CHECK_CODE(name)
#define JVMCI_PD_DEVELOP_FLAG_VALUE_CHANGED_CHECK_CODE(type, name, doc)            EMIT_FLAG_VALUE_CHANGED_CHECK_CODE(name)
#define JVMCI_NOTPRODUCT_FLAG_VALUE_CHANGED_CHECK_CODE(type, name, value, doc)     EMIT_FLAG_VALUE_CHANGED_CHECK_CODE(name)
#else
#define JVMCI_DEVELOP_FLAG_VALUE_CHANGED_CHECK_CODE(type, name, value, doc)
#define JVMCI_PD_DEVELOP_FLAG_VALUE_CHANGED_CHECK_CODE(type, name, doc)
#define JVMCI_NOTPRODUCT_FLAG_VALUE_CHANGED_CHECK_CODE(type, name, value, doc)
#endif

#define JVMCI_PD_PRODUCT_FLAG_VALUE_CHANGED_CHECK_CODE(type, name, doc)            EMIT_FLAG_VALUE_CHANGED_CHECK_CODE(name)
#define JVMCI_PRODUCT_FLAG_VALUE_CHANGED_CHECK_CODE(type, name, value, doc)        EMIT_FLAG_VALUE_CHANGED_CHECK_CODE(name)

  JVMCI_FLAGS(JVMCI_DEVELOP_FLAG_VALUE_CHANGED_CHECK_CODE, \
              JVMCI_PD_DEVELOP_FLAG_VALUE_CHANGED_CHECK_CODE, \
              JVMCI_PRODUCT_FLAG_VALUE_CHANGED_CHECK_CODE, \
              JVMCI_PD_PRODUCT_FLAG_VALUE_CHANGED_CHECK_CODE, \
              JVMCI_IGNORE_FLAG_FOUR_PARAM, \
              JVMCI_IGNORE_FLAG_FOUR_PARAM, \
              JVMCI_NOTPRODUCT_FLAG_VALUE_CHANGED_CHECK_CODE, \
              IGNORE_RANGE, \
              IGNORE_CONSTRAINT)

#undef EMIT_FLAG_VALUE_CHANGED_CHECK_CODE
#undef JVMCI_DEVELOP_FLAG_VALUE_CHANGED_CHECK_CODE
#undef JVMCI_PD_DEVELOP_FLAG_VALUE_CHANGED_CHECK_CODE
#undef JVMCI_NOTPRODUCT_FLAG_VALUE_CHANGED_CHECK_CODE
#undef JVMCI_DIAGNOSTIC_FLAG_VALUE_CHANGED_CHECK_CODE
#undef JVMCI_PD_PRODUCT_FLAG_VALUE_CHANGED_CHECK_CODE
#undef JVMCI_PRODUCT_FLAG_VALUE_CHANGED_CHECK_CODE
#undef JVMCI_EXPERIMENTAL_FLAG_VALUE_CHANGED_CHECK_CODE

  return true;
}

// Print jvmci arguments inconsistency error message.
void JVMCIGlobals::print_jvmci_args_inconsistency_error_message() {
  const char* error_msg = "Improperly specified VM option '%s'\n";
  jio_fprintf(defaultStream::error_stream(), "EnableJVMCI must be enabled\n");

#define EMIT_CHECK_PRINT_ERR_MSG_CODE(FLAG)                         \
  if (!FLAG_IS_DEFAULT(FLAG)) {                                     \
    if (strcmp(#FLAG, "EnableJVMCI")) {                             \
      jio_fprintf(defaultStream::error_stream(), error_msg, #FLAG); \
    }                                                               \
  }

#define JVMCI_DIAGNOSTIC_FLAG_CHECK_PRINT_ERR_MSG_CODE(type, name, value, doc)     EMIT_CHECK_PRINT_ERR_MSG_CODE(name)
#define JVMCI_EXPERIMENTAL_FLAG_CHECK_PRINT_ERR_MSG_CODE(type, name, value, doc)   EMIT_CHECK_PRINT_ERR_MSG_CODE(name)

  if (UnlockDiagnosticVMOptions || !FLAG_IS_DEFAULT(UnlockDiagnosticVMOptions)) {
    JVMCI_FLAGS(JVMCI_IGNORE_FLAG_FOUR_PARAM, \
                JVMCI_IGNORE_FLAG_THREE_PARAM, \
                JVMCI_IGNORE_FLAG_FOUR_PARAM, \
                JVMCI_IGNORE_FLAG_THREE_PARAM, \
                JVMCI_DIAGNOSTIC_FLAG_CHECK_PRINT_ERR_MSG_CODE, \
                JVMCI_IGNORE_FLAG_FOUR_PARAM, \
                JVMCI_IGNORE_FLAG_FOUR_PARAM, \
                IGNORE_RANGE, \
                IGNORE_CONSTRAINT)
  }

  if (UnlockExperimentalVMOptions || !FLAG_IS_DEFAULT(UnlockExperimentalVMOptions)) {
    JVMCI_FLAGS(JVMCI_IGNORE_FLAG_FOUR_PARAM, \
                JVMCI_IGNORE_FLAG_THREE_PARAM, \
                JVMCI_IGNORE_FLAG_FOUR_PARAM, \
                JVMCI_IGNORE_FLAG_THREE_PARAM, \
                JVMCI_IGNORE_FLAG_FOUR_PARAM, \
                JVMCI_EXPERIMENTAL_FLAG_CHECK_PRINT_ERR_MSG_CODE, \
                JVMCI_IGNORE_FLAG_FOUR_PARAM, \
                IGNORE_RANGE, \
                IGNORE_CONSTRAINT)
  }

#ifndef PRODUCT
#define JVMCI_DEVELOP_FLAG_CHECK_PRINT_ERR_MSG_CODE(type, name, value, doc)        EMIT_CHECK_PRINT_ERR_MSG_CODE(name)
#define JVMCI_PD_DEVELOP_FLAG_CHECK_PRINT_ERR_MSG_CODE(type, name, doc)            EMIT_CHECK_PRINT_ERR_MSG_CODE(name)
#define JVMCI_NOTPRODUCT_FLAG_CHECK_PRINT_ERR_MSG_CODE(type, name, value, doc)     EMIT_CHECK_PRINT_ERR_MSG_CODE(name)
#else
#define JVMCI_DEVELOP_FLAG_CHECK_PRINT_ERR_MSG_CODE(type, name, value, doc)
#define JVMCI_PD_DEVELOP_FLAG_CHECK_PRINT_ERR_MSG_CODE(type, name, doc)
#define JVMCI_NOTPRODUCT_FLAG_CHECK_PRINT_ERR_MSG_CODE(type, name, value, doc)
#endif

#define JVMCI_PD_PRODUCT_FLAG_CHECK_PRINT_ERR_MSG_CODE(type, name, doc)            EMIT_CHECK_PRINT_ERR_MSG_CODE(name)
#define JVMCI_PRODUCT_FLAG_CHECK_PRINT_ERR_MSG_CODE(type, name, value, doc)        EMIT_CHECK_PRINT_ERR_MSG_CODE(name)

  JVMCI_FLAGS(JVMCI_DEVELOP_FLAG_CHECK_PRINT_ERR_MSG_CODE, \
              JVMCI_PD_DEVELOP_FLAG_CHECK_PRINT_ERR_MSG_CODE, \
              JVMCI_PRODUCT_FLAG_CHECK_PRINT_ERR_MSG_CODE, \
              JVMCI_PD_PRODUCT_FLAG_CHECK_PRINT_ERR_MSG_CODE, \
              JVMCI_IGNORE_FLAG_FOUR_PARAM, \
              JVMCI_IGNORE_FLAG_FOUR_PARAM, \
              JVMCI_NOTPRODUCT_FLAG_CHECK_PRINT_ERR_MSG_CODE, \
              IGNORE_RANGE, \
              IGNORE_CONSTRAINT)

#undef EMIT_CHECK_PRINT_ERR_MSG_CODE
#undef JVMCI_DEVELOP_FLAG_CHECK_PRINT_ERR_MSG_CODE
#undef JVMCI_PD_DEVELOP_FLAG_CHECK_PRINT_ERR_MSG_CODE
#undef JVMCI_NOTPRODUCT_FLAG_CHECK_PRINT_ERR_MSG_CODE
#undef JVMCI_PD_PRODUCT_FLAG_CHECK_PRINT_ERR_MSG_CODE
#undef JVMCI_PRODUCT_FLAG_CHECK_PRINT_ERR_MSG_CODE
#undef JVMCI_DIAGNOSTIC_FLAG_CHECK_PRINT_ERR_MSG_CODE
#undef JVMCI_EXPERIMENTAL_FLAG_CHECK_PRINT_ERR_MSG_CODE

}

#undef JVMCI_IGNORE_FLAG_FOUR_PARAM
#undef JVMCI_IGNORE_FLAG_THREE_PARAM
