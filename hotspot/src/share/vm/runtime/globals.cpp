/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "utilities/ostream.hpp"
#include "utilities/macros.hpp"
#include "utilities/top.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/g1/g1_globals.hpp"
#endif // INCLUDE_ALL_GCS
#ifdef COMPILER1
#include "c1/c1_globals.hpp"
#endif
#ifdef COMPILER2
#include "opto/c2_globals.hpp"
#endif
#ifdef SHARK
#include "shark/shark_globals.hpp"
#endif

RUNTIME_FLAGS(MATERIALIZE_DEVELOPER_FLAG, MATERIALIZE_PD_DEVELOPER_FLAG, \
              MATERIALIZE_PRODUCT_FLAG, MATERIALIZE_PD_PRODUCT_FLAG, \
              MATERIALIZE_DIAGNOSTIC_FLAG, MATERIALIZE_EXPERIMENTAL_FLAG, \
              MATERIALIZE_NOTPRODUCT_FLAG, \
              MATERIALIZE_MANAGEABLE_FLAG, MATERIALIZE_PRODUCT_RW_FLAG, \
              MATERIALIZE_LP64_PRODUCT_FLAG)

RUNTIME_OS_FLAGS(MATERIALIZE_DEVELOPER_FLAG, MATERIALIZE_PD_DEVELOPER_FLAG, \
                 MATERIALIZE_PRODUCT_FLAG, MATERIALIZE_PD_PRODUCT_FLAG, \
                 MATERIALIZE_DIAGNOSTIC_FLAG, MATERIALIZE_NOTPRODUCT_FLAG)

ARCH_FLAGS(MATERIALIZE_DEVELOPER_FLAG, MATERIALIZE_PRODUCT_FLAG, \
           MATERIALIZE_DIAGNOSTIC_FLAG, MATERIALIZE_EXPERIMENTAL_FLAG, \
           MATERIALIZE_NOTPRODUCT_FLAG)

MATERIALIZE_FLAGS_EXT


bool Flag::is_unlocker() const {
  return strcmp(name, "UnlockDiagnosticVMOptions") == 0     ||
         strcmp(name, "UnlockExperimentalVMOptions") == 0   ||
         is_unlocker_ext();
}

bool Flag::is_unlocked() const {
  if (strcmp(kind, "{diagnostic}") == 0 ||
      strcmp(kind, "{C2 diagnostic}") == 0 ||
      strcmp(kind, "{ARCH diagnostic}") == 0 ||
      strcmp(kind, "{Shark diagnostic}") == 0) {
    return UnlockDiagnosticVMOptions;
  } else if (strcmp(kind, "{experimental}") == 0 ||
             strcmp(kind, "{C2 experimental}") == 0 ||
             strcmp(kind, "{ARCH experimental}") == 0 ||
             strcmp(kind, "{Shark experimental}") == 0) {
    return UnlockExperimentalVMOptions;
  } else {
    return is_unlocked_ext();
  }
}

// Get custom message for this locked flag, or return NULL if
// none is available.
void Flag::get_locked_message(char* buf, int buflen) const {
  get_locked_message_ext(buf, buflen);
}

bool Flag::is_writeable() const {
  return strcmp(kind, "{manageable}") == 0 ||
         strcmp(kind, "{product rw}") == 0 ||
         is_writeable_ext();
}

// All flags except "manageable" are assumed to be internal flags.
// Long term, we need to define a mechanism to specify which flags
// are external/stable and change this function accordingly.
bool Flag::is_external() const {
  return strcmp(kind, "{manageable}") == 0 || is_external_ext();
}


// Length of format string (e.g. "%.1234s") for printing ccstr below
#define FORMAT_BUFFER_LEN 16

void Flag::print_on(outputStream* st, bool withComments) {
  st->print("%9s %-40s %c= ", type, name, (origin != DEFAULT ? ':' : ' '));
  if (is_bool())     st->print("%-16s", get_bool() ? "true" : "false");
  if (is_intx())     st->print("%-16ld", get_intx());
  if (is_uintx())    st->print("%-16lu", get_uintx());
  if (is_uint64_t()) st->print("%-16lu", get_uint64_t());
  if (is_double())   st->print("%-16f", get_double());

  if (is_ccstr()) {
     const char* cp = get_ccstr();
     if (cp != NULL) {
       const char* eol;
       while ((eol = strchr(cp, '\n')) != NULL) {
         char format_buffer[FORMAT_BUFFER_LEN];
         size_t llen = pointer_delta(eol, cp, sizeof(char));
         jio_snprintf(format_buffer, FORMAT_BUFFER_LEN,
                     "%%." SIZE_FORMAT "s", llen);
         st->print(format_buffer, cp);
         st->cr();
         cp = eol+1;
         st->print("%5s %-35s += ", "", name);
       }
       st->print("%-16s", cp);
     }
     else st->print("%-16s", "");
  }
  st->print("%-20s", kind);
  if (withComments) {
#ifndef PRODUCT
    st->print("%s", doc );
#endif
  }
  st->cr();
}

void Flag::print_as_flag(outputStream* st) {
  if (is_bool()) {
    st->print("-XX:%s%s", get_bool() ? "+" : "-", name);
  } else if (is_intx()) {
    st->print("-XX:%s=" INTX_FORMAT, name, get_intx());
  } else if (is_uintx()) {
    st->print("-XX:%s=" UINTX_FORMAT, name, get_uintx());
  } else if (is_uint64_t()) {
    st->print("-XX:%s=" UINT64_FORMAT, name, get_uint64_t());
  } else if (is_double()) {
    st->print("-XX:%s=%f", name, get_double());
  } else if (is_ccstr()) {
    st->print("-XX:%s=", name);
    const char* cp = get_ccstr();
    if (cp != NULL) {
      // Need to turn embedded '\n's back into separate arguments
      // Not so efficient to print one character at a time,
      // but the choice is to do the transformation to a buffer
      // and print that.  And this need not be efficient.
      for (; *cp != '\0'; cp += 1) {
        switch (*cp) {
          default:
            st->print("%c", *cp);
            break;
          case '\n':
            st->print(" -XX:%s=", name);
            break;
        }
      }
    }
  } else {
    ShouldNotReachHere();
  }
}

// 4991491 do not "optimize out" the was_set false values: omitting them
// tickles a Microsoft compiler bug causing flagTable to be malformed

#define RUNTIME_PRODUCT_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{product}", DEFAULT },
#define RUNTIME_PD_PRODUCT_FLAG_STRUCT(type, name, doc)     { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{pd product}", DEFAULT },
#define RUNTIME_DIAGNOSTIC_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{diagnostic}", DEFAULT },
#define RUNTIME_EXPERIMENTAL_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{experimental}", DEFAULT },
#define RUNTIME_MANAGEABLE_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{manageable}", DEFAULT },
#define RUNTIME_PRODUCT_RW_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{product rw}", DEFAULT },

#ifdef PRODUCT
  #define RUNTIME_DEVELOP_FLAG_STRUCT(type, name, value, doc) /* flag is constant */
  #define RUNTIME_PD_DEVELOP_FLAG_STRUCT(type, name, doc)     /* flag is constant */
  #define RUNTIME_NOTPRODUCT_FLAG_STRUCT(type, name, value, doc)
#else
  #define RUNTIME_DEVELOP_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, doc, "", DEFAULT },
  #define RUNTIME_PD_DEVELOP_FLAG_STRUCT(type, name, doc)     { #type, XSTR(name), &name, doc, "{pd}", DEFAULT },
  #define RUNTIME_NOTPRODUCT_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, doc, "{notproduct}", DEFAULT },
#endif

#ifdef _LP64
  #define RUNTIME_LP64_PRODUCT_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{lp64_product}", DEFAULT },
#else
  #define RUNTIME_LP64_PRODUCT_FLAG_STRUCT(type, name, value, doc) /* flag is constant */
#endif // _LP64

#define C1_PRODUCT_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{C1 product}", DEFAULT },
#define C1_PD_PRODUCT_FLAG_STRUCT(type, name, doc)     { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{C1 pd product}", DEFAULT },
#ifdef PRODUCT
  #define C1_DEVELOP_FLAG_STRUCT(type, name, value, doc) /* flag is constant */
  #define C1_PD_DEVELOP_FLAG_STRUCT(type, name, doc)     /* flag is constant */
  #define C1_NOTPRODUCT_FLAG_STRUCT(type, name, value, doc)
#else
  #define C1_DEVELOP_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, doc, "{C1}", DEFAULT },
  #define C1_PD_DEVELOP_FLAG_STRUCT(type, name, doc)     { #type, XSTR(name), &name, doc, "{C1 pd}", DEFAULT },
  #define C1_NOTPRODUCT_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, doc, "{C1 notproduct}", DEFAULT },
#endif

#define C2_PRODUCT_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{C2 product}", DEFAULT },
#define C2_PD_PRODUCT_FLAG_STRUCT(type, name, doc)     { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{C2 pd product}", DEFAULT },
#define C2_DIAGNOSTIC_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{C2 diagnostic}", DEFAULT },
#define C2_EXPERIMENTAL_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{C2 experimental}", DEFAULT },
#ifdef PRODUCT
  #define C2_DEVELOP_FLAG_STRUCT(type, name, value, doc) /* flag is constant */
  #define C2_PD_DEVELOP_FLAG_STRUCT(type, name, doc)     /* flag is constant */
  #define C2_NOTPRODUCT_FLAG_STRUCT(type, name, value, doc)
#else
  #define C2_DEVELOP_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, doc, "{C2}", DEFAULT },
  #define C2_PD_DEVELOP_FLAG_STRUCT(type, name, doc)     { #type, XSTR(name), &name, doc, "{C2 pd}", DEFAULT },
  #define C2_NOTPRODUCT_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, doc, "{C2 notproduct}", DEFAULT },
#endif

#define ARCH_PRODUCT_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{ARCH product}", DEFAULT },
#define ARCH_DIAGNOSTIC_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{ARCH diagnostic}", DEFAULT },
#define ARCH_EXPERIMENTAL_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{ARCH experimental}", DEFAULT },
#ifdef PRODUCT
  #define ARCH_DEVELOP_FLAG_STRUCT(type, name, value, doc) /* flag is constant */
  #define ARCH_NOTPRODUCT_FLAG_STRUCT(type, name, value, doc)
#else
  #define ARCH_DEVELOP_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, doc, "{ARCH}", DEFAULT },
  #define ARCH_NOTPRODUCT_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, doc, "{ARCH notproduct}", DEFAULT },
#endif

#define SHARK_PRODUCT_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{Shark product}", DEFAULT },
#define SHARK_PD_PRODUCT_FLAG_STRUCT(type, name, doc)     { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{Shark pd product}", DEFAULT },
#define SHARK_DIAGNOSTIC_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, NOT_PRODUCT_ARG(doc) "{Shark diagnostic}", DEFAULT },
#ifdef PRODUCT
  #define SHARK_DEVELOP_FLAG_STRUCT(type, name, value, doc) /* flag is constant */
  #define SHARK_PD_DEVELOP_FLAG_STRUCT(type, name, doc)     /* flag is constant */
  #define SHARK_NOTPRODUCT_FLAG_STRUCT(type, name, value, doc)
#else
  #define SHARK_DEVELOP_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, doc, "{Shark}", DEFAULT },
  #define SHARK_PD_DEVELOP_FLAG_STRUCT(type, name, doc)     { #type, XSTR(name), &name, doc, "{Shark pd}", DEFAULT },
  #define SHARK_NOTPRODUCT_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name, doc, "{Shark notproduct}", DEFAULT },
#endif

static Flag flagTable[] = {
 RUNTIME_FLAGS(RUNTIME_DEVELOP_FLAG_STRUCT, RUNTIME_PD_DEVELOP_FLAG_STRUCT, RUNTIME_PRODUCT_FLAG_STRUCT, RUNTIME_PD_PRODUCT_FLAG_STRUCT, RUNTIME_DIAGNOSTIC_FLAG_STRUCT, RUNTIME_EXPERIMENTAL_FLAG_STRUCT, RUNTIME_NOTPRODUCT_FLAG_STRUCT, RUNTIME_MANAGEABLE_FLAG_STRUCT, RUNTIME_PRODUCT_RW_FLAG_STRUCT, RUNTIME_LP64_PRODUCT_FLAG_STRUCT)
 RUNTIME_OS_FLAGS(RUNTIME_DEVELOP_FLAG_STRUCT, RUNTIME_PD_DEVELOP_FLAG_STRUCT, RUNTIME_PRODUCT_FLAG_STRUCT, RUNTIME_PD_PRODUCT_FLAG_STRUCT, RUNTIME_DIAGNOSTIC_FLAG_STRUCT, RUNTIME_NOTPRODUCT_FLAG_STRUCT)
#if INCLUDE_ALL_GCS
 G1_FLAGS(RUNTIME_DEVELOP_FLAG_STRUCT, RUNTIME_PD_DEVELOP_FLAG_STRUCT, RUNTIME_PRODUCT_FLAG_STRUCT, RUNTIME_PD_PRODUCT_FLAG_STRUCT, RUNTIME_DIAGNOSTIC_FLAG_STRUCT, RUNTIME_EXPERIMENTAL_FLAG_STRUCT, RUNTIME_NOTPRODUCT_FLAG_STRUCT, RUNTIME_MANAGEABLE_FLAG_STRUCT, RUNTIME_PRODUCT_RW_FLAG_STRUCT)
#endif // INCLUDE_ALL_GCS
#ifdef COMPILER1
 C1_FLAGS(C1_DEVELOP_FLAG_STRUCT, C1_PD_DEVELOP_FLAG_STRUCT, C1_PRODUCT_FLAG_STRUCT, C1_PD_PRODUCT_FLAG_STRUCT, C1_NOTPRODUCT_FLAG_STRUCT)
#endif
#ifdef COMPILER2
 C2_FLAGS(C2_DEVELOP_FLAG_STRUCT, C2_PD_DEVELOP_FLAG_STRUCT, C2_PRODUCT_FLAG_STRUCT, C2_PD_PRODUCT_FLAG_STRUCT, C2_DIAGNOSTIC_FLAG_STRUCT, C2_EXPERIMENTAL_FLAG_STRUCT, C2_NOTPRODUCT_FLAG_STRUCT)
#endif
#ifdef SHARK
 SHARK_FLAGS(SHARK_DEVELOP_FLAG_STRUCT, SHARK_PD_DEVELOP_FLAG_STRUCT, SHARK_PRODUCT_FLAG_STRUCT, SHARK_PD_PRODUCT_FLAG_STRUCT, SHARK_DIAGNOSTIC_FLAG_STRUCT, SHARK_NOTPRODUCT_FLAG_STRUCT)
#endif
 ARCH_FLAGS(ARCH_DEVELOP_FLAG_STRUCT, ARCH_PRODUCT_FLAG_STRUCT, ARCH_DIAGNOSTIC_FLAG_STRUCT, ARCH_EXPERIMENTAL_FLAG_STRUCT, ARCH_NOTPRODUCT_FLAG_STRUCT)
 FLAGTABLE_EXT
 {0, NULL, NULL}
};

Flag* Flag::flags = flagTable;
size_t Flag::numFlags = (sizeof(flagTable) / sizeof(Flag));

inline bool str_equal(const char* s, char* q, size_t len) {
  // s is null terminated, q is not!
  if (strlen(s) != (unsigned int) len) return false;
  return strncmp(s, q, len) == 0;
}

// Search the flag table for a named flag
Flag* Flag::find_flag(char* name, size_t length, bool allow_locked) {
  for (Flag* current = &flagTable[0]; current->name != NULL; current++) {
    if (str_equal(current->name, name, length)) {
      // Found a matching entry.  Report locked flags only if allowed.
      if (!(current->is_unlocked() || current->is_unlocker())) {
        if (!allow_locked) {
          // disable use of locked flags, e.g. diagnostic, experimental,
          // commercial... until they are explicitly unlocked
          return NULL;
        }
      }
      return current;
    }
  }
  // Flag name is not in the flag table
  return NULL;
}

// Returns the address of the index'th element
static Flag* address_of_flag(CommandLineFlagWithType flag) {
  assert((size_t)flag < Flag::numFlags, "bad command line flag index");
  return &Flag::flags[flag];
}

bool CommandLineFlagsEx::is_default(CommandLineFlag flag) {
  assert((size_t)flag < Flag::numFlags, "bad command line flag index");
  Flag* f = &Flag::flags[flag];
  return (f->origin == DEFAULT);
}

bool CommandLineFlagsEx::is_ergo(CommandLineFlag flag) {
  assert((size_t)flag < Flag::numFlags, "bad command line flag index");
  Flag* f = &Flag::flags[flag];
  return (f->origin == ERGONOMIC);
}

bool CommandLineFlagsEx::is_cmdline(CommandLineFlag flag) {
  assert((size_t)flag < Flag::numFlags, "bad command line flag index");
  Flag* f = &Flag::flags[flag];
  return (f->origin == COMMAND_LINE);
}

bool CommandLineFlags::wasSetOnCmdline(const char* name, bool* value) {
  Flag* result = Flag::find_flag((char*)name, strlen(name));
  if (result == NULL) return false;
  *value = (result->origin == COMMAND_LINE);
  return true;
}

bool CommandLineFlags::boolAt(char* name, size_t len, bool* value) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_bool()) return false;
  *value = result->get_bool();
  return true;
}

bool CommandLineFlags::boolAtPut(char* name, size_t len, bool* value, FlagValueOrigin origin) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_bool()) return false;
  bool old_value = result->get_bool();
  result->set_bool(*value);
  *value = old_value;
  result->origin = origin;
  return true;
}

void CommandLineFlagsEx::boolAtPut(CommandLineFlagWithType flag, bool value, FlagValueOrigin origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_bool(), "wrong flag type");
  faddr->set_bool(value);
  faddr->origin = origin;
}

bool CommandLineFlags::intxAt(char* name, size_t len, intx* value) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_intx()) return false;
  *value = result->get_intx();
  return true;
}

bool CommandLineFlags::intxAtPut(char* name, size_t len, intx* value, FlagValueOrigin origin) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_intx()) return false;
  intx old_value = result->get_intx();
  result->set_intx(*value);
  *value = old_value;
  result->origin = origin;
  return true;
}

void CommandLineFlagsEx::intxAtPut(CommandLineFlagWithType flag, intx value, FlagValueOrigin origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_intx(), "wrong flag type");
  faddr->set_intx(value);
  faddr->origin = origin;
}

bool CommandLineFlags::uintxAt(char* name, size_t len, uintx* value) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_uintx()) return false;
  *value = result->get_uintx();
  return true;
}

bool CommandLineFlags::uintxAtPut(char* name, size_t len, uintx* value, FlagValueOrigin origin) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_uintx()) return false;
  uintx old_value = result->get_uintx();
  result->set_uintx(*value);
  *value = old_value;
  result->origin = origin;
  return true;
}

void CommandLineFlagsEx::uintxAtPut(CommandLineFlagWithType flag, uintx value, FlagValueOrigin origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_uintx(), "wrong flag type");
  faddr->set_uintx(value);
  faddr->origin = origin;
}

bool CommandLineFlags::uint64_tAt(char* name, size_t len, uint64_t* value) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_uint64_t()) return false;
  *value = result->get_uint64_t();
  return true;
}

bool CommandLineFlags::uint64_tAtPut(char* name, size_t len, uint64_t* value, FlagValueOrigin origin) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_uint64_t()) return false;
  uint64_t old_value = result->get_uint64_t();
  result->set_uint64_t(*value);
  *value = old_value;
  result->origin = origin;
  return true;
}

void CommandLineFlagsEx::uint64_tAtPut(CommandLineFlagWithType flag, uint64_t value, FlagValueOrigin origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_uint64_t(), "wrong flag type");
  faddr->set_uint64_t(value);
  faddr->origin = origin;
}

bool CommandLineFlags::doubleAt(char* name, size_t len, double* value) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_double()) return false;
  *value = result->get_double();
  return true;
}

bool CommandLineFlags::doubleAtPut(char* name, size_t len, double* value, FlagValueOrigin origin) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_double()) return false;
  double old_value = result->get_double();
  result->set_double(*value);
  *value = old_value;
  result->origin = origin;
  return true;
}

void CommandLineFlagsEx::doubleAtPut(CommandLineFlagWithType flag, double value, FlagValueOrigin origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_double(), "wrong flag type");
  faddr->set_double(value);
  faddr->origin = origin;
}

bool CommandLineFlags::ccstrAt(char* name, size_t len, ccstr* value) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_ccstr()) return false;
  *value = result->get_ccstr();
  return true;
}

// Contract:  Flag will make private copy of the incoming value.
// Outgoing value is always malloc-ed, and caller MUST call free.
bool CommandLineFlags::ccstrAtPut(char* name, size_t len, ccstr* value, FlagValueOrigin origin) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_ccstr()) return false;
  ccstr old_value = result->get_ccstr();
  char* new_value = NULL;
  if (*value != NULL) {
    new_value = NEW_C_HEAP_ARRAY(char, strlen(*value)+1, mtInternal);
    strcpy(new_value, *value);
  }
  result->set_ccstr(new_value);
  if (result->origin == DEFAULT && old_value != NULL) {
    // Prior value is NOT heap allocated, but was a literal constant.
    char* old_value_to_free = NEW_C_HEAP_ARRAY(char, strlen(old_value)+1, mtInternal);
    strcpy(old_value_to_free, old_value);
    old_value = old_value_to_free;
  }
  *value = old_value;
  result->origin = origin;
  return true;
}

// Contract:  Flag will make private copy of the incoming value.
void CommandLineFlagsEx::ccstrAtPut(CommandLineFlagWithType flag, ccstr value, FlagValueOrigin origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_ccstr(), "wrong flag type");
  ccstr old_value = faddr->get_ccstr();
  char* new_value = NEW_C_HEAP_ARRAY(char, strlen(value)+1, mtInternal);
  strcpy(new_value, value);
  faddr->set_ccstr(new_value);
  if (faddr->origin != DEFAULT && old_value != NULL) {
    // Prior value is heap allocated so free it.
    FREE_C_HEAP_ARRAY(char, old_value, mtInternal);
  }
  faddr->origin = origin;
}

extern "C" {
  static int compare_flags(const void* void_a, const void* void_b) {
    return strcmp((*((Flag**) void_a))->name, (*((Flag**) void_b))->name);
  }
}

void CommandLineFlags::printSetFlags(outputStream* out) {
  // Print which flags were set on the command line
  // note: this method is called before the thread structure is in place
  //       which means resource allocation cannot be used.

  // Compute size
  int length= 0;
  while (flagTable[length].name != NULL) length++;

  // Sort
  Flag** array = NEW_C_HEAP_ARRAY(Flag*, length, mtInternal);
  for (int index = 0; index < length; index++) {
    array[index] = &flagTable[index];
  }
  qsort(array, length, sizeof(Flag*), compare_flags);

  // Print
  for (int i = 0; i < length; i++) {
    if (array[i]->origin /* naked field! */) {
      array[i]->print_as_flag(out);
      out->print(" ");
    }
  }
  out->cr();
  FREE_C_HEAP_ARRAY(Flag*, array, mtInternal);
}

#ifndef PRODUCT


void CommandLineFlags::verify() {
  assert(Arguments::check_vm_args_consistency(), "Some flag settings conflict");
}

#endif // PRODUCT

void CommandLineFlags::printFlags(outputStream* out, bool withComments) {
  // Print the flags sorted by name
  // note: this method is called before the thread structure is in place
  //       which means resource allocation cannot be used.

  // Compute size
  int length= 0;
  while (flagTable[length].name != NULL) length++;

  // Sort
  Flag** array = NEW_C_HEAP_ARRAY(Flag*, length, mtInternal);
  for (int index = 0; index < length; index++) {
    array[index] = &flagTable[index];
  }
  qsort(array, length, sizeof(Flag*), compare_flags);

  // Print
  out->print_cr("[Global flags]");
  for (int i = 0; i < length; i++) {
    if (array[i]->is_unlocked()) {
      array[i]->print_on(out, withComments);
    }
  }
  FREE_C_HEAP_ARRAY(Flag*, array, mtInternal);
}
