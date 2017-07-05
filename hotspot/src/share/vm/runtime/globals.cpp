/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "trace/tracing.hpp"
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

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

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


static bool is_product_build() {
#ifdef PRODUCT
  return true;
#else
  return false;
#endif
}

void Flag::check_writable() {
  if (is_constant_in_binary()) {
    fatal(err_msg("flag is constant: %s", _name));
  }
}

bool Flag::is_bool() const {
  return strcmp(_type, "bool") == 0;
}

bool Flag::get_bool() const {
  return *((bool*) _addr);
}

void Flag::set_bool(bool value) {
  check_writable();
  *((bool*) _addr) = value;
}

bool Flag::is_intx() const {
  return strcmp(_type, "intx")  == 0;
}

intx Flag::get_intx() const {
  return *((intx*) _addr);
}

void Flag::set_intx(intx value) {
  check_writable();
  *((intx*) _addr) = value;
}

bool Flag::is_uintx() const {
  return strcmp(_type, "uintx") == 0;
}

uintx Flag::get_uintx() const {
  return *((uintx*) _addr);
}

void Flag::set_uintx(uintx value) {
  check_writable();
  *((uintx*) _addr) = value;
}

bool Flag::is_uint64_t() const {
  return strcmp(_type, "uint64_t") == 0;
}

uint64_t Flag::get_uint64_t() const {
  return *((uint64_t*) _addr);
}

void Flag::set_uint64_t(uint64_t value) {
  check_writable();
  *((uint64_t*) _addr) = value;
}

bool Flag::is_double() const {
  return strcmp(_type, "double") == 0;
}

double Flag::get_double() const {
  return *((double*) _addr);
}

void Flag::set_double(double value) {
  check_writable();
  *((double*) _addr) = value;
}

bool Flag::is_ccstr() const {
  return strcmp(_type, "ccstr") == 0 || strcmp(_type, "ccstrlist") == 0;
}

bool Flag::ccstr_accumulates() const {
  return strcmp(_type, "ccstrlist") == 0;
}

ccstr Flag::get_ccstr() const {
  return *((ccstr*) _addr);
}

void Flag::set_ccstr(ccstr value) {
  check_writable();
  *((ccstr*) _addr) = value;
}


Flag::Flags Flag::get_origin() {
  return Flags(_flags & VALUE_ORIGIN_MASK);
}

void Flag::set_origin(Flags origin) {
  assert((origin & VALUE_ORIGIN_MASK) == origin, "sanity");
  _flags = Flags((_flags & ~VALUE_ORIGIN_MASK) | origin);
}

bool Flag::is_default() {
  return (get_origin() == DEFAULT);
}

bool Flag::is_ergonomic() {
  return (get_origin() == ERGONOMIC);
}

bool Flag::is_command_line() {
  return (get_origin() == COMMAND_LINE);
}

bool Flag::is_product() const {
  return (_flags & KIND_PRODUCT) != 0;
}

bool Flag::is_manageable() const {
  return (_flags & KIND_MANAGEABLE) != 0;
}

bool Flag::is_diagnostic() const {
  return (_flags & KIND_DIAGNOSTIC) != 0;
}

bool Flag::is_experimental() const {
  return (_flags & KIND_EXPERIMENTAL) != 0;
}

bool Flag::is_notproduct() const {
  return (_flags & KIND_NOT_PRODUCT) != 0;
}

bool Flag::is_develop() const {
  return (_flags & KIND_DEVELOP) != 0;
}

bool Flag::is_read_write() const {
  return (_flags & KIND_READ_WRITE) != 0;
}

bool Flag::is_commercial() const {
  return (_flags & KIND_COMMERCIAL) != 0;
}

/**
 * Returns if this flag is a constant in the binary.  Right now this is
 * true for notproduct and develop flags in product builds.
 */
bool Flag::is_constant_in_binary() const {
#ifdef PRODUCT
    return is_notproduct() || is_develop();
#else
    return false;
#endif
}

bool Flag::is_unlocker() const {
  return strcmp(_name, "UnlockDiagnosticVMOptions") == 0     ||
         strcmp(_name, "UnlockExperimentalVMOptions") == 0   ||
         is_unlocker_ext();
}

bool Flag::is_unlocked() const {
  if (is_diagnostic()) {
    return UnlockDiagnosticVMOptions;
  }
  if (is_experimental()) {
    return UnlockExperimentalVMOptions;
  }
  return is_unlocked_ext();
}

// Get custom message for this locked flag, or return NULL if
// none is available.
void Flag::get_locked_message(char* buf, int buflen) const {
  buf[0] = '\0';
  if (is_diagnostic() && !is_unlocked()) {
    jio_snprintf(buf, buflen, "Error: VM option '%s' is diagnostic and must be enabled via -XX:+UnlockDiagnosticVMOptions.\n",
                 _name);
    return;
  }
  if (is_experimental() && !is_unlocked()) {
    jio_snprintf(buf, buflen, "Error: VM option '%s' is experimental and must be enabled via -XX:+UnlockExperimentalVMOptions.\n",
                 _name);
    return;
  }
  if (is_develop() && is_product_build()) {
    jio_snprintf(buf, buflen, "Error: VM option '%s' is develop and is available only in debug version of VM.\n",
                 _name);
    return;
  }
  if (is_notproduct() && is_product_build()) {
    jio_snprintf(buf, buflen, "Error: VM option '%s' is notproduct and is available only in debug version of VM.\n",
                 _name);
    return;
  }
  get_locked_message_ext(buf, buflen);
}

bool Flag::is_writeable() const {
  return is_manageable() || (is_product() && is_read_write()) || is_writeable_ext();
}

// All flags except "manageable" are assumed to be internal flags.
// Long term, we need to define a mechanism to specify which flags
// are external/stable and change this function accordingly.
bool Flag::is_external() const {
  return is_manageable() || is_external_ext();
}


// Length of format string (e.g. "%.1234s") for printing ccstr below
#define FORMAT_BUFFER_LEN 16

PRAGMA_FORMAT_NONLITERAL_IGNORED_EXTERNAL
void Flag::print_on(outputStream* st, bool withComments) {
  // Don't print notproduct and develop flags in a product build.
  if (is_constant_in_binary()) {
    return;
  }

  st->print("%9s %-40s %c= ", _type, _name, (!is_default() ? ':' : ' '));

  if (is_bool()) {
    st->print("%-16s", get_bool() ? "true" : "false");
  }
  if (is_intx()) {
    st->print("%-16ld", get_intx());
  }
  if (is_uintx()) {
    st->print("%-16lu", get_uintx());
  }
  if (is_uint64_t()) {
    st->print("%-16lu", get_uint64_t());
  }
  if (is_double()) {
    st->print("%-16f", get_double());
  }
  if (is_ccstr()) {
    const char* cp = get_ccstr();
    if (cp != NULL) {
      const char* eol;
      while ((eol = strchr(cp, '\n')) != NULL) {
        char format_buffer[FORMAT_BUFFER_LEN];
        size_t llen = pointer_delta(eol, cp, sizeof(char));
        jio_snprintf(format_buffer, FORMAT_BUFFER_LEN,
            "%%." SIZE_FORMAT "s", llen);
PRAGMA_DIAG_PUSH
PRAGMA_FORMAT_NONLITERAL_IGNORED_INTERNAL
        st->print(format_buffer, cp);
PRAGMA_DIAG_POP
        st->cr();
        cp = eol+1;
        st->print("%5s %-35s += ", "", _name);
      }
      st->print("%-16s", cp);
    }
    else st->print("%-16s", "");
  }

  st->print("%-20s", " ");
  print_kind(st);

  if (withComments) {
#ifndef PRODUCT
    st->print("%s", _doc);
#endif
  }
  st->cr();
}

void Flag::print_kind(outputStream* st) {
  struct Data {
    int flag;
    const char* name;
  };

  Data data[] = {
      { KIND_C1, "C1" },
      { KIND_C2, "C2" },
      { KIND_ARCH, "ARCH" },
      { KIND_SHARK, "SHARK" },
      { KIND_PLATFORM_DEPENDENT, "pd" },
      { KIND_PRODUCT, "product" },
      { KIND_MANAGEABLE, "manageable" },
      { KIND_DIAGNOSTIC, "diagnostic" },
      { KIND_EXPERIMENTAL, "experimental" },
      { KIND_COMMERCIAL, "commercial" },
      { KIND_NOT_PRODUCT, "notproduct" },
      { KIND_DEVELOP, "develop" },
      { KIND_LP64_PRODUCT, "lp64_product" },
      { KIND_READ_WRITE, "rw" },
      { -1, "" }
  };

  if ((_flags & KIND_MASK) != 0) {
    st->print("{");
    bool is_first = true;

    for (int i = 0; data[i].flag != -1; i++) {
      Data d = data[i];
      if ((_flags & d.flag) != 0) {
        if (is_first) {
          is_first = false;
        } else {
          st->print(" ");
        }
        st->print("%s", d.name);
      }
    }

    st->print("}");
  }
}

void Flag::print_as_flag(outputStream* st) {
  if (is_bool()) {
    st->print("-XX:%s%s", get_bool() ? "+" : "-", _name);
  } else if (is_intx()) {
    st->print("-XX:%s=" INTX_FORMAT, _name, get_intx());
  } else if (is_uintx()) {
    st->print("-XX:%s=" UINTX_FORMAT, _name, get_uintx());
  } else if (is_uint64_t()) {
    st->print("-XX:%s=" UINT64_FORMAT, _name, get_uint64_t());
  } else if (is_double()) {
    st->print("-XX:%s=%f", _name, get_double());
  } else if (is_ccstr()) {
    st->print("-XX:%s=", _name);
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
            st->print(" -XX:%s=", _name);
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

#define NAME(name) NOT_PRODUCT(&name) PRODUCT_ONLY(&CONST_##name)

#define RUNTIME_PRODUCT_FLAG_STRUCT(     type, name, value, doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_PRODUCT) },
#define RUNTIME_PD_PRODUCT_FLAG_STRUCT(  type, name,        doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_PRODUCT | Flag::KIND_PLATFORM_DEPENDENT) },
#define RUNTIME_DIAGNOSTIC_FLAG_STRUCT(  type, name, value, doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_DIAGNOSTIC) },
#define RUNTIME_EXPERIMENTAL_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_EXPERIMENTAL) },
#define RUNTIME_MANAGEABLE_FLAG_STRUCT(  type, name, value, doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_MANAGEABLE) },
#define RUNTIME_PRODUCT_RW_FLAG_STRUCT(  type, name, value, doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_PRODUCT | Flag::KIND_READ_WRITE) },
#define RUNTIME_DEVELOP_FLAG_STRUCT(     type, name, value, doc) { #type, XSTR(name), NAME(name), NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_DEVELOP) },
#define RUNTIME_PD_DEVELOP_FLAG_STRUCT(  type, name,        doc) { #type, XSTR(name), NAME(name), NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_DEVELOP | Flag::KIND_PLATFORM_DEPENDENT) },
#define RUNTIME_NOTPRODUCT_FLAG_STRUCT(  type, name, value, doc) { #type, XSTR(name), NAME(name), NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_NOT_PRODUCT) },

#ifdef _LP64
#define RUNTIME_LP64_PRODUCT_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_LP64_PRODUCT) },
#else
#define RUNTIME_LP64_PRODUCT_FLAG_STRUCT(type, name, value, doc) /* flag is constant */
#endif // _LP64

#define C1_PRODUCT_FLAG_STRUCT(          type, name, value, doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C1 | Flag::KIND_PRODUCT) },
#define C1_PD_PRODUCT_FLAG_STRUCT(       type, name,        doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C1 | Flag::KIND_PRODUCT | Flag::KIND_PLATFORM_DEPENDENT) },
#define C1_DIAGNOSTIC_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C1 | Flag::KIND_DIAGNOSTIC) },
#define C1_DEVELOP_FLAG_STRUCT(          type, name, value, doc) { #type, XSTR(name), NAME(name), NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C1 | Flag::KIND_DEVELOP) },
#define C1_PD_DEVELOP_FLAG_STRUCT(       type, name,        doc) { #type, XSTR(name), NAME(name), NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C1 | Flag::KIND_DEVELOP | Flag::KIND_PLATFORM_DEPENDENT) },
#define C1_NOTPRODUCT_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), NAME(name), NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C1 | Flag::KIND_NOT_PRODUCT) },

#define C2_PRODUCT_FLAG_STRUCT(          type, name, value, doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C2 | Flag::KIND_PRODUCT) },
#define C2_PD_PRODUCT_FLAG_STRUCT(       type, name,        doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C2 | Flag::KIND_PRODUCT | Flag::KIND_PLATFORM_DEPENDENT) },
#define C2_DIAGNOSTIC_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C2 | Flag::KIND_DIAGNOSTIC) },
#define C2_EXPERIMENTAL_FLAG_STRUCT(     type, name, value, doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C2 | Flag::KIND_EXPERIMENTAL) },
#define C2_DEVELOP_FLAG_STRUCT(          type, name, value, doc) { #type, XSTR(name), NAME(name), NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C2 | Flag::KIND_DEVELOP) },
#define C2_PD_DEVELOP_FLAG_STRUCT(       type, name,        doc) { #type, XSTR(name), NAME(name), NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C2 | Flag::KIND_DEVELOP | Flag::KIND_PLATFORM_DEPENDENT) },
#define C2_NOTPRODUCT_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), NAME(name), NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C2 | Flag::KIND_NOT_PRODUCT) },

#define ARCH_PRODUCT_FLAG_STRUCT(        type, name, value, doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_ARCH | Flag::KIND_PRODUCT) },
#define ARCH_DIAGNOSTIC_FLAG_STRUCT(     type, name, value, doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_ARCH | Flag::KIND_DIAGNOSTIC) },
#define ARCH_EXPERIMENTAL_FLAG_STRUCT(   type, name, value, doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_ARCH | Flag::KIND_EXPERIMENTAL) },
#define ARCH_DEVELOP_FLAG_STRUCT(        type, name, value, doc) { #type, XSTR(name), NAME(name), NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_ARCH | Flag::KIND_DEVELOP) },
#define ARCH_NOTPRODUCT_FLAG_STRUCT(     type, name, value, doc) { #type, XSTR(name), NAME(name), NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_ARCH | Flag::KIND_NOT_PRODUCT) },

#define SHARK_PRODUCT_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_SHARK | Flag::KIND_PRODUCT) },
#define SHARK_PD_PRODUCT_FLAG_STRUCT(    type, name,        doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_SHARK | Flag::KIND_PRODUCT | Flag::KIND_PLATFORM_DEPENDENT) },
#define SHARK_DIAGNOSTIC_FLAG_STRUCT(    type, name, value, doc) { #type, XSTR(name), &name,      NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_SHARK | Flag::KIND_DIAGNOSTIC) },
#define SHARK_DEVELOP_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), NAME(name), NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_SHARK | Flag::KIND_DEVELOP) },
#define SHARK_PD_DEVELOP_FLAG_STRUCT(    type, name,        doc) { #type, XSTR(name), NAME(name), NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_SHARK | Flag::KIND_DEVELOP | Flag::KIND_PLATFORM_DEPENDENT) },
#define SHARK_NOTPRODUCT_FLAG_STRUCT(    type, name, value, doc) { #type, XSTR(name), NAME(name), NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_SHARK | Flag::KIND_NOT_PRODUCT) },

static Flag flagTable[] = {
 RUNTIME_FLAGS(RUNTIME_DEVELOP_FLAG_STRUCT, RUNTIME_PD_DEVELOP_FLAG_STRUCT, RUNTIME_PRODUCT_FLAG_STRUCT, RUNTIME_PD_PRODUCT_FLAG_STRUCT, RUNTIME_DIAGNOSTIC_FLAG_STRUCT, RUNTIME_EXPERIMENTAL_FLAG_STRUCT, RUNTIME_NOTPRODUCT_FLAG_STRUCT, RUNTIME_MANAGEABLE_FLAG_STRUCT, RUNTIME_PRODUCT_RW_FLAG_STRUCT, RUNTIME_LP64_PRODUCT_FLAG_STRUCT)
 RUNTIME_OS_FLAGS(RUNTIME_DEVELOP_FLAG_STRUCT, RUNTIME_PD_DEVELOP_FLAG_STRUCT, RUNTIME_PRODUCT_FLAG_STRUCT, RUNTIME_PD_PRODUCT_FLAG_STRUCT, RUNTIME_DIAGNOSTIC_FLAG_STRUCT, RUNTIME_NOTPRODUCT_FLAG_STRUCT)
#if INCLUDE_ALL_GCS
 G1_FLAGS(RUNTIME_DEVELOP_FLAG_STRUCT, RUNTIME_PD_DEVELOP_FLAG_STRUCT, RUNTIME_PRODUCT_FLAG_STRUCT, RUNTIME_PD_PRODUCT_FLAG_STRUCT, RUNTIME_DIAGNOSTIC_FLAG_STRUCT, RUNTIME_EXPERIMENTAL_FLAG_STRUCT, RUNTIME_NOTPRODUCT_FLAG_STRUCT, RUNTIME_MANAGEABLE_FLAG_STRUCT, RUNTIME_PRODUCT_RW_FLAG_STRUCT)
#endif // INCLUDE_ALL_GCS
#ifdef COMPILER1
 C1_FLAGS(C1_DEVELOP_FLAG_STRUCT, C1_PD_DEVELOP_FLAG_STRUCT, C1_PRODUCT_FLAG_STRUCT, C1_PD_PRODUCT_FLAG_STRUCT, C1_DIAGNOSTIC_FLAG_STRUCT, C1_NOTPRODUCT_FLAG_STRUCT)
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

inline bool str_equal(const char* s, const char* q, size_t len) {
  // s is null terminated, q is not!
  if (strlen(s) != (unsigned int) len) return false;
  return strncmp(s, q, len) == 0;
}

// Search the flag table for a named flag
Flag* Flag::find_flag(const char* name, size_t length, bool allow_locked, bool return_flag) {
  for (Flag* current = &flagTable[0]; current->_name != NULL; current++) {
    if (str_equal(current->_name, name, length)) {
      // Found a matching entry.
      // Don't report notproduct and develop flags in product builds.
      if (current->is_constant_in_binary()) {
        return (return_flag == true ? current : NULL);
      }
      // Report locked flags only if allowed.
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

// Compute string similarity based on Dice's coefficient
static float str_similar(const char* str1, const char* str2, size_t len2) {
  int len1 = (int) strlen(str1);
  int total = len1 + (int) len2;

  int hit = 0;

  for (int i = 0; i < len1 -1; ++i) {
    for (int j = 0; j < (int) len2 -1; ++j) {
      if ((str1[i] == str2[j]) && (str1[i+1] == str2[j+1])) {
        ++hit;
        break;
      }
    }
  }

  return 2.0f * (float) hit / (float) total;
}

Flag* Flag::fuzzy_match(const char* name, size_t length, bool allow_locked) {
  float VMOptionsFuzzyMatchSimilarity = 0.7f;
  Flag* match = NULL;
  float score;
  float max_score = -1;

  for (Flag* current = &flagTable[0]; current->_name != NULL; current++) {
    score = str_similar(current->_name, name, length);
    if (score > max_score) {
      max_score = score;
      match = current;
    }
  }

  if (!(match->is_unlocked() || match->is_unlocker())) {
    if (!allow_locked) {
      return NULL;
    }
  }

  if (max_score < VMOptionsFuzzyMatchSimilarity) {
    return NULL;
  }

  return match;
}

// Returns the address of the index'th element
static Flag* address_of_flag(CommandLineFlagWithType flag) {
  assert((size_t)flag < Flag::numFlags, "bad command line flag index");
  return &Flag::flags[flag];
}

bool CommandLineFlagsEx::is_default(CommandLineFlag flag) {
  assert((size_t)flag < Flag::numFlags, "bad command line flag index");
  Flag* f = &Flag::flags[flag];
  return f->is_default();
}

bool CommandLineFlagsEx::is_ergo(CommandLineFlag flag) {
  assert((size_t)flag < Flag::numFlags, "bad command line flag index");
  Flag* f = &Flag::flags[flag];
  return f->is_ergonomic();
}

bool CommandLineFlagsEx::is_cmdline(CommandLineFlag flag) {
  assert((size_t)flag < Flag::numFlags, "bad command line flag index");
  Flag* f = &Flag::flags[flag];
  return f->is_command_line();
}

bool CommandLineFlags::wasSetOnCmdline(const char* name, bool* value) {
  Flag* result = Flag::find_flag((char*)name, strlen(name));
  if (result == NULL) return false;
  *value = result->is_command_line();
  return true;
}

template<class E, class T>
static void trace_flag_changed(const char* name, const T old_value, const T new_value, const Flag::Flags origin)
{
  E e;
  e.set_name(name);
  e.set_old_value(old_value);
  e.set_new_value(new_value);
  e.set_origin(origin);
  e.commit();
}

bool CommandLineFlags::boolAt(const char* name, size_t len, bool* value) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_bool()) return false;
  *value = result->get_bool();
  return true;
}

bool CommandLineFlags::boolAtPut(const char* name, size_t len, bool* value, Flag::Flags origin) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_bool()) return false;
  bool old_value = result->get_bool();
  trace_flag_changed<EventBooleanFlagChanged, bool>(name, old_value, *value, origin);
  result->set_bool(*value);
  *value = old_value;
  result->set_origin(origin);
  return true;
}

void CommandLineFlagsEx::boolAtPut(CommandLineFlagWithType flag, bool value, Flag::Flags origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_bool(), "wrong flag type");
  trace_flag_changed<EventBooleanFlagChanged, bool>(faddr->_name, faddr->get_bool(), value, origin);
  faddr->set_bool(value);
  faddr->set_origin(origin);
}

bool CommandLineFlags::intxAt(const char* name, size_t len, intx* value) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_intx()) return false;
  *value = result->get_intx();
  return true;
}

bool CommandLineFlags::intxAtPut(const char* name, size_t len, intx* value, Flag::Flags origin) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_intx()) return false;
  intx old_value = result->get_intx();
  trace_flag_changed<EventLongFlagChanged, s8>(name, old_value, *value, origin);
  result->set_intx(*value);
  *value = old_value;
  result->set_origin(origin);
  return true;
}

void CommandLineFlagsEx::intxAtPut(CommandLineFlagWithType flag, intx value, Flag::Flags origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_intx(), "wrong flag type");
  trace_flag_changed<EventLongFlagChanged, s8>(faddr->_name, faddr->get_intx(), value, origin);
  faddr->set_intx(value);
  faddr->set_origin(origin);
}

bool CommandLineFlags::uintxAt(const char* name, size_t len, uintx* value) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_uintx()) return false;
  *value = result->get_uintx();
  return true;
}

bool CommandLineFlags::uintxAtPut(const char* name, size_t len, uintx* value, Flag::Flags origin) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_uintx()) return false;
  uintx old_value = result->get_uintx();
  trace_flag_changed<EventUnsignedLongFlagChanged, u8>(name, old_value, *value, origin);
  result->set_uintx(*value);
  *value = old_value;
  result->set_origin(origin);
  return true;
}

void CommandLineFlagsEx::uintxAtPut(CommandLineFlagWithType flag, uintx value, Flag::Flags origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_uintx(), "wrong flag type");
  trace_flag_changed<EventUnsignedLongFlagChanged, u8>(faddr->_name, faddr->get_uintx(), value, origin);
  faddr->set_uintx(value);
  faddr->set_origin(origin);
}

bool CommandLineFlags::uint64_tAt(const char* name, size_t len, uint64_t* value) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_uint64_t()) return false;
  *value = result->get_uint64_t();
  return true;
}

bool CommandLineFlags::uint64_tAtPut(const char* name, size_t len, uint64_t* value, Flag::Flags origin) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_uint64_t()) return false;
  uint64_t old_value = result->get_uint64_t();
  trace_flag_changed<EventUnsignedLongFlagChanged, u8>(name, old_value, *value, origin);
  result->set_uint64_t(*value);
  *value = old_value;
  result->set_origin(origin);
  return true;
}

void CommandLineFlagsEx::uint64_tAtPut(CommandLineFlagWithType flag, uint64_t value, Flag::Flags origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_uint64_t(), "wrong flag type");
  trace_flag_changed<EventUnsignedLongFlagChanged, u8>(faddr->_name, faddr->get_uint64_t(), value, origin);
  faddr->set_uint64_t(value);
  faddr->set_origin(origin);
}

bool CommandLineFlags::doubleAt(const char* name, size_t len, double* value) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_double()) return false;
  *value = result->get_double();
  return true;
}

bool CommandLineFlags::doubleAtPut(const char* name, size_t len, double* value, Flag::Flags origin) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_double()) return false;
  double old_value = result->get_double();
  trace_flag_changed<EventDoubleFlagChanged, double>(name, old_value, *value, origin);
  result->set_double(*value);
  *value = old_value;
  result->set_origin(origin);
  return true;
}

void CommandLineFlagsEx::doubleAtPut(CommandLineFlagWithType flag, double value, Flag::Flags origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_double(), "wrong flag type");
  trace_flag_changed<EventDoubleFlagChanged, double>(faddr->_name, faddr->get_double(), value, origin);
  faddr->set_double(value);
  faddr->set_origin(origin);
}

bool CommandLineFlags::ccstrAt(const char* name, size_t len, ccstr* value) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_ccstr()) return false;
  *value = result->get_ccstr();
  return true;
}

bool CommandLineFlags::ccstrAtPut(const char* name, size_t len, ccstr* value, Flag::Flags origin) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return false;
  if (!result->is_ccstr()) return false;
  ccstr old_value = result->get_ccstr();
  trace_flag_changed<EventStringFlagChanged, const char*>(name, old_value, *value, origin);
  char* new_value = NULL;
  if (*value != NULL) {
    new_value = NEW_C_HEAP_ARRAY(char, strlen(*value)+1, mtInternal);
    strcpy(new_value, *value);
  }
  result->set_ccstr(new_value);
  if (result->is_default() && old_value != NULL) {
    // Prior value is NOT heap allocated, but was a literal constant.
    char* old_value_to_free = NEW_C_HEAP_ARRAY(char, strlen(old_value)+1, mtInternal);
    strcpy(old_value_to_free, old_value);
    old_value = old_value_to_free;
  }
  *value = old_value;
  result->set_origin(origin);
  return true;
}

void CommandLineFlagsEx::ccstrAtPut(CommandLineFlagWithType flag, ccstr value, Flag::Flags origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_ccstr(), "wrong flag type");
  ccstr old_value = faddr->get_ccstr();
  trace_flag_changed<EventStringFlagChanged, const char*>(faddr->_name, old_value, value, origin);
  char* new_value = NEW_C_HEAP_ARRAY(char, strlen(value)+1, mtInternal);
  strcpy(new_value, value);
  faddr->set_ccstr(new_value);
  if (!faddr->is_default() && old_value != NULL) {
    // Prior value is heap allocated so free it.
    FREE_C_HEAP_ARRAY(char, old_value, mtInternal);
  }
  faddr->set_origin(origin);
}

extern "C" {
  static int compare_flags(const void* void_a, const void* void_b) {
    return strcmp((*((Flag**) void_a))->_name, (*((Flag**) void_b))->_name);
  }
}

void CommandLineFlags::printSetFlags(outputStream* out) {
  // Print which flags were set on the command line
  // note: this method is called before the thread structure is in place
  //       which means resource allocation cannot be used.

  // The last entry is the null entry.
  const size_t length = Flag::numFlags - 1;

  // Sort
  Flag** array = NEW_C_HEAP_ARRAY(Flag*, length, mtInternal);
  for (size_t i = 0; i < length; i++) {
    array[i] = &flagTable[i];
  }
  qsort(array, length, sizeof(Flag*), compare_flags);

  // Print
  for (size_t i = 0; i < length; i++) {
    if (array[i]->get_origin() /* naked field! */) {
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

  // The last entry is the null entry.
  const size_t length = Flag::numFlags - 1;

  // Sort
  Flag** array = NEW_C_HEAP_ARRAY(Flag*, length, mtInternal);
  for (size_t i = 0; i < length; i++) {
    array[i] = &flagTable[i];
  }
  qsort(array, length, sizeof(Flag*), compare_flags);

  // Print
  out->print_cr("[Global flags]");
  for (size_t i = 0; i < length; i++) {
    if (array[i]->is_unlocked()) {
      array[i]->print_on(out, withComments);
    }
  }
  FREE_C_HEAP_ARRAY(Flag*, array, mtInternal);
}
