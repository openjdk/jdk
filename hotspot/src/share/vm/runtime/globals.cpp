/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/commandLineFlagConstraintList.hpp"
#include "runtime/commandLineFlagRangeList.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "trace/tracing.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "utilities/top.hpp"
#if INCLUDE_ALL_GCS
#include "gc/g1/g1_globals.hpp"
#endif // INCLUDE_ALL_GCS
#ifdef COMPILER1
#include "c1/c1_globals.hpp"
#endif
#if INCLUDE_JVMCI
#include "jvmci/jvmci_globals.hpp"
#endif
#ifdef COMPILER2
#include "opto/c2_globals.hpp"
#endif
#ifdef SHARK
#include "shark/shark_globals.hpp"
#endif

RUNTIME_FLAGS(MATERIALIZE_DEVELOPER_FLAG, \
              MATERIALIZE_PD_DEVELOPER_FLAG, \
              MATERIALIZE_PRODUCT_FLAG, \
              MATERIALIZE_PD_PRODUCT_FLAG, \
              MATERIALIZE_DIAGNOSTIC_FLAG, \
              MATERIALIZE_EXPERIMENTAL_FLAG, \
              MATERIALIZE_NOTPRODUCT_FLAG, \
              MATERIALIZE_MANAGEABLE_FLAG, \
              MATERIALIZE_PRODUCT_RW_FLAG, \
              MATERIALIZE_LP64_PRODUCT_FLAG, \
              IGNORE_RANGE, \
              IGNORE_CONSTRAINT)

RUNTIME_OS_FLAGS(MATERIALIZE_DEVELOPER_FLAG, \
                 MATERIALIZE_PD_DEVELOPER_FLAG, \
                 MATERIALIZE_PRODUCT_FLAG, \
                 MATERIALIZE_PD_PRODUCT_FLAG, \
                 MATERIALIZE_DIAGNOSTIC_FLAG, \
                 MATERIALIZE_NOTPRODUCT_FLAG, \
                 IGNORE_RANGE, \
                 IGNORE_CONSTRAINT)

ARCH_FLAGS(MATERIALIZE_DEVELOPER_FLAG, \
           MATERIALIZE_PRODUCT_FLAG, \
           MATERIALIZE_DIAGNOSTIC_FLAG, \
           MATERIALIZE_EXPERIMENTAL_FLAG, \
           MATERIALIZE_NOTPRODUCT_FLAG, \
           IGNORE_RANGE, \
           IGNORE_CONSTRAINT)

MATERIALIZE_FLAGS_EXT

#define DEFAULT_RANGE_STR_CHUNK_SIZE 64
static char* create_range_str(const char *fmt, ...) {
  static size_t string_length = DEFAULT_RANGE_STR_CHUNK_SIZE;
  static char* range_string = NEW_C_HEAP_ARRAY(char, string_length, mtLogging);

  int size_needed = 0;
  do {
    va_list args;
    va_start(args, fmt);
    size_needed = jio_vsnprintf(range_string, string_length, fmt, args);
    va_end(args);

    if (size_needed < 0) {
      string_length += DEFAULT_RANGE_STR_CHUNK_SIZE;
      range_string = REALLOC_C_HEAP_ARRAY(char, range_string, string_length, mtLogging);
      guarantee(range_string != NULL, "create_range_str string should not be NULL");
    }
  } while (size_needed < 0);

  return range_string;
}

const char* Flag::get_int_default_range_str() {
  return create_range_str("[ " INT32_FORMAT_W(-25) " ... " INT32_FORMAT_W(25) " ]", INT_MIN, INT_MAX);
}

const char* Flag::get_uint_default_range_str() {
  return create_range_str("[ " UINT32_FORMAT_W(-25) " ... " UINT32_FORMAT_W(25) " ]", 0, UINT_MAX);
}

const char* Flag::get_intx_default_range_str() {
  return create_range_str("[ " INTX_FORMAT_W(-25) " ... " INTX_FORMAT_W(25) " ]", min_intx, max_intx);
}

const char* Flag::get_uintx_default_range_str() {
  return create_range_str("[ " UINTX_FORMAT_W(-25) " ... " UINTX_FORMAT_W(25) " ]", 0, max_uintx);
}

const char* Flag::get_uint64_t_default_range_str() {
  return create_range_str("[ " UINT64_FORMAT_W(-25) " ... " UINT64_FORMAT_W(25) " ]", 0, uint64_t(max_juint));
}

const char* Flag::get_size_t_default_range_str() {
  return create_range_str("[ " SIZE_FORMAT_W(-25) " ... " SIZE_FORMAT_W(25) " ]", 0, SIZE_MAX);
}

const char* Flag::get_double_default_range_str() {
  return create_range_str("[ %-25.3f ... %25.3f ]", DBL_MIN, DBL_MAX);
}

static bool is_product_build() {
#ifdef PRODUCT
  return true;
#else
  return false;
#endif
}

void Flag::check_writable() {
  if (is_constant_in_binary()) {
    fatal("flag is constant: %s", _name);
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

bool Flag::is_int() const {
  return strcmp(_type, "int")  == 0;
}

int Flag::get_int() const {
  return *((int*) _addr);
}

void Flag::set_int(int value) {
  check_writable();
  *((int*) _addr) = value;
}

bool Flag::is_uint() const {
  return strcmp(_type, "uint")  == 0;
}

uint Flag::get_uint() const {
  return *((uint*) _addr);
}

void Flag::set_uint(uint value) {
  check_writable();
  *((uint*) _addr) = value;
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

bool Flag::is_size_t() const {
  return strcmp(_type, "size_t") == 0;
}

size_t Flag::get_size_t() const {
  return *((size_t*) _addr);
}

void Flag::set_size_t(size_t value) {
  check_writable();
  *((size_t*) _addr) = value;
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

void Flag::unlock_diagnostic() {
  assert(is_diagnostic(), "sanity");
  _flags = Flags(_flags & ~KIND_DIAGNOSTIC);
}

// Get custom message for this locked flag, or NULL if
// none is available. Returns message type produced.
Flag::MsgType Flag::get_locked_message(char* buf, int buflen) const {
  buf[0] = '\0';
  if (is_diagnostic() && !is_unlocked()) {
    jio_snprintf(buf, buflen,
                 "Error: VM option '%s' is diagnostic and must be enabled via -XX:+UnlockDiagnosticVMOptions.\n"
                 "Error: The unlock option must precede '%s'.\n",
                 _name, _name);
    return Flag::DIAGNOSTIC_FLAG_BUT_LOCKED;
  }
  if (is_experimental() && !is_unlocked()) {
    jio_snprintf(buf, buflen,
                 "Error: VM option '%s' is experimental and must be enabled via -XX:+UnlockExperimentalVMOptions.\n"
                 "Error: The unlock option must precede '%s'.\n",
                 _name, _name);
    return Flag::EXPERIMENTAL_FLAG_BUT_LOCKED;
  }
  if (is_develop() && is_product_build()) {
    jio_snprintf(buf, buflen, "Error: VM option '%s' is develop and is available only in debug version of VM.\n",
                 _name);
    return Flag::DEVELOPER_FLAG_BUT_PRODUCT_BUILD;
  }
  if (is_notproduct() && is_product_build()) {
    jio_snprintf(buf, buflen, "Error: VM option '%s' is notproduct and is available only in debug version of VM.\n",
                 _name);
    return Flag::NOTPRODUCT_FLAG_BUT_PRODUCT_BUILD;
  }
  get_locked_message_ext(buf, buflen);
  return Flag::NONE;
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

void Flag::print_on(outputStream* st, bool withComments, bool printRanges) {
  // Don't print notproduct and develop flags in a product build.
  if (is_constant_in_binary()) {
    return;
  }

  if (!printRanges) {

    st->print("%9s %-40s %c= ", _type, _name, (!is_default() ? ':' : ' '));

    if (is_bool()) {
      st->print("%-16s", get_bool() ? "true" : "false");
    } else if (is_int()) {
      st->print("%-16d", get_int());
    } else if (is_uint()) {
      st->print("%-16u", get_uint());
    } else if (is_intx()) {
      st->print(INTX_FORMAT_W(-16), get_intx());
    } else if (is_uintx()) {
      st->print(UINTX_FORMAT_W(-16), get_uintx());
    } else if (is_uint64_t()) {
      st->print(UINT64_FORMAT_W(-16), get_uint64_t());
    } else if (is_size_t()) {
      st->print(SIZE_FORMAT_W(-16), get_size_t());
    } else if (is_double()) {
      st->print("%-16f", get_double());
    } else if (is_ccstr()) {
      const char* cp = get_ccstr();
      if (cp != NULL) {
        const char* eol;
        while ((eol = strchr(cp, '\n')) != NULL) {
          size_t llen = pointer_delta(eol, cp, sizeof(char));
          st->print("%.*s", (int)llen, cp);
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

#ifndef PRODUCT
    if (withComments) {
      st->print("%s", _doc);
    }
#endif

    st->cr();

  } else if (!is_bool() && !is_ccstr()) {
    st->print("%9s %-50s ", _type, _name);

    RangeStrFunc func = NULL;
    if (is_int()) {
      func = Flag::get_int_default_range_str;
    } else if (is_uint()) {
      func = Flag::get_uint_default_range_str;
    } else if (is_intx()) {
      func = Flag::get_intx_default_range_str;
    } else if (is_uintx()) {
      func = Flag::get_uintx_default_range_str;
    } else if (is_uint64_t()) {
      func = Flag::get_uint64_t_default_range_str;
    } else if (is_size_t()) {
      func = Flag::get_size_t_default_range_str;
    } else if (is_double()) {
      func = Flag::get_double_default_range_str;
    } else {
      ShouldNotReachHere();
    }
    CommandLineFlagRangeList::print(st, _name, func);

    st->print(" %-20s", " ");
    print_kind(st);

#ifndef PRODUCT
    if (withComments) {
      st->print("%s", _doc);
    }
#endif

    st->cr();
  }
}

void Flag::print_kind(outputStream* st) {
  struct Data {
    int flag;
    const char* name;
  };

  Data data[] = {
      { KIND_JVMCI, "JVMCI" },
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
  } else if (is_int()) {
    st->print("-XX:%s=%d", _name, get_int());
  } else if (is_uint()) {
    st->print("-XX:%s=%u", _name, get_uint());
  } else if (is_intx()) {
    st->print("-XX:%s=" INTX_FORMAT, _name, get_intx());
  } else if (is_uintx()) {
    st->print("-XX:%s=" UINTX_FORMAT, _name, get_uintx());
  } else if (is_uint64_t()) {
    st->print("-XX:%s=" UINT64_FORMAT, _name, get_uint64_t());
  } else if (is_size_t()) {
    st->print("-XX:%s=" SIZE_FORMAT, _name, get_size_t());
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

const char* Flag::flag_error_str(Flag::Error error) {
  switch (error) {
    case Flag::MISSING_NAME: return "MISSING_NAME";
    case Flag::MISSING_VALUE: return "MISSING_VALUE";
    case Flag::NON_WRITABLE: return "NON_WRITABLE";
    case Flag::OUT_OF_BOUNDS: return "OUT_OF_BOUNDS";
    case Flag::VIOLATES_CONSTRAINT: return "VIOLATES_CONSTRAINT";
    case Flag::INVALID_FLAG: return "INVALID_FLAG";
    case Flag::ERR_OTHER: return "ERR_OTHER";
    case Flag::SUCCESS: return "SUCCESS";
    default: ShouldNotReachHere(); return "NULL";
  }
}

// 4991491 do not "optimize out" the was_set false values: omitting them
// tickles a Microsoft compiler bug causing flagTable to be malformed

#define RUNTIME_PRODUCT_FLAG_STRUCT(     type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_PRODUCT) },
#define RUNTIME_PD_PRODUCT_FLAG_STRUCT(  type, name,        doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_PRODUCT | Flag::KIND_PLATFORM_DEPENDENT) },
#define RUNTIME_DIAGNOSTIC_FLAG_STRUCT(  type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_DIAGNOSTIC) },
#define RUNTIME_EXPERIMENTAL_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_EXPERIMENTAL) },
#define RUNTIME_MANAGEABLE_FLAG_STRUCT(  type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_MANAGEABLE) },
#define RUNTIME_PRODUCT_RW_FLAG_STRUCT(  type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_PRODUCT | Flag::KIND_READ_WRITE) },
#define RUNTIME_DEVELOP_FLAG_STRUCT(     type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_DEVELOP) },
#define RUNTIME_PD_DEVELOP_FLAG_STRUCT(  type, name,        doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_DEVELOP | Flag::KIND_PLATFORM_DEPENDENT) },
#define RUNTIME_NOTPRODUCT_FLAG_STRUCT(  type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_NOT_PRODUCT) },

#define JVMCI_PRODUCT_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_JVMCI | Flag::KIND_PRODUCT) },
#define JVMCI_PD_PRODUCT_FLAG_STRUCT(    type, name,        doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_JVMCI | Flag::KIND_PRODUCT | Flag::KIND_PLATFORM_DEPENDENT) },
#define JVMCI_DIAGNOSTIC_FLAG_STRUCT(    type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_JVMCI | Flag::KIND_DIAGNOSTIC) },
#define JVMCI_EXPERIMENTAL_FLAG_STRUCT(  type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_JVMCI | Flag::KIND_EXPERIMENTAL) },
#define JVMCI_DEVELOP_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_JVMCI | Flag::KIND_DEVELOP) },
#define JVMCI_PD_DEVELOP_FLAG_STRUCT(    type, name,        doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_JVMCI | Flag::KIND_DEVELOP | Flag::KIND_PLATFORM_DEPENDENT) },
#define JVMCI_NOTPRODUCT_FLAG_STRUCT(    type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_JVMCI | Flag::KIND_NOT_PRODUCT) },

#ifdef _LP64
#define RUNTIME_LP64_PRODUCT_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_LP64_PRODUCT) },
#else
#define RUNTIME_LP64_PRODUCT_FLAG_STRUCT(type, name, value, doc) /* flag is constant */
#endif // _LP64

#define C1_PRODUCT_FLAG_STRUCT(          type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C1 | Flag::KIND_PRODUCT) },
#define C1_PD_PRODUCT_FLAG_STRUCT(       type, name,        doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C1 | Flag::KIND_PRODUCT | Flag::KIND_PLATFORM_DEPENDENT) },
#define C1_DIAGNOSTIC_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C1 | Flag::KIND_DIAGNOSTIC) },
#define C1_DEVELOP_FLAG_STRUCT(          type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C1 | Flag::KIND_DEVELOP) },
#define C1_PD_DEVELOP_FLAG_STRUCT(       type, name,        doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C1 | Flag::KIND_DEVELOP | Flag::KIND_PLATFORM_DEPENDENT) },
#define C1_NOTPRODUCT_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C1 | Flag::KIND_NOT_PRODUCT) },

#define C2_PRODUCT_FLAG_STRUCT(          type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C2 | Flag::KIND_PRODUCT) },
#define C2_PD_PRODUCT_FLAG_STRUCT(       type, name,        doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C2 | Flag::KIND_PRODUCT | Flag::KIND_PLATFORM_DEPENDENT) },
#define C2_DIAGNOSTIC_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C2 | Flag::KIND_DIAGNOSTIC) },
#define C2_EXPERIMENTAL_FLAG_STRUCT(     type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C2 | Flag::KIND_EXPERIMENTAL) },
#define C2_DEVELOP_FLAG_STRUCT(          type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C2 | Flag::KIND_DEVELOP) },
#define C2_PD_DEVELOP_FLAG_STRUCT(       type, name,        doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C2 | Flag::KIND_DEVELOP | Flag::KIND_PLATFORM_DEPENDENT) },
#define C2_NOTPRODUCT_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_C2 | Flag::KIND_NOT_PRODUCT) },

#define ARCH_PRODUCT_FLAG_STRUCT(        type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_ARCH | Flag::KIND_PRODUCT) },
#define ARCH_DIAGNOSTIC_FLAG_STRUCT(     type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_ARCH | Flag::KIND_DIAGNOSTIC) },
#define ARCH_EXPERIMENTAL_FLAG_STRUCT(   type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_ARCH | Flag::KIND_EXPERIMENTAL) },
#define ARCH_DEVELOP_FLAG_STRUCT(        type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_ARCH | Flag::KIND_DEVELOP) },
#define ARCH_NOTPRODUCT_FLAG_STRUCT(     type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_ARCH | Flag::KIND_NOT_PRODUCT) },

#define SHARK_PRODUCT_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_SHARK | Flag::KIND_PRODUCT) },
#define SHARK_PD_PRODUCT_FLAG_STRUCT(    type, name,        doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_SHARK | Flag::KIND_PRODUCT | Flag::KIND_PLATFORM_DEPENDENT) },
#define SHARK_DIAGNOSTIC_FLAG_STRUCT(    type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_SHARK | Flag::KIND_DIAGNOSTIC) },
#define SHARK_DEVELOP_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_SHARK | Flag::KIND_DEVELOP) },
#define SHARK_PD_DEVELOP_FLAG_STRUCT(    type, name,        doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_SHARK | Flag::KIND_DEVELOP | Flag::KIND_PLATFORM_DEPENDENT) },
#define SHARK_NOTPRODUCT_FLAG_STRUCT(    type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) Flag::Flags(Flag::DEFAULT | Flag::KIND_SHARK | Flag::KIND_NOT_PRODUCT) },

static Flag flagTable[] = {
 RUNTIME_FLAGS(RUNTIME_DEVELOP_FLAG_STRUCT, \
               RUNTIME_PD_DEVELOP_FLAG_STRUCT, \
               RUNTIME_PRODUCT_FLAG_STRUCT, \
               RUNTIME_PD_PRODUCT_FLAG_STRUCT, \
               RUNTIME_DIAGNOSTIC_FLAG_STRUCT, \
               RUNTIME_EXPERIMENTAL_FLAG_STRUCT, \
               RUNTIME_NOTPRODUCT_FLAG_STRUCT, \
               RUNTIME_MANAGEABLE_FLAG_STRUCT, \
               RUNTIME_PRODUCT_RW_FLAG_STRUCT, \
               RUNTIME_LP64_PRODUCT_FLAG_STRUCT, \
               IGNORE_RANGE, \
               IGNORE_CONSTRAINT)
 RUNTIME_OS_FLAGS(RUNTIME_DEVELOP_FLAG_STRUCT, \
                  RUNTIME_PD_DEVELOP_FLAG_STRUCT, \
                  RUNTIME_PRODUCT_FLAG_STRUCT, \
                  RUNTIME_PD_PRODUCT_FLAG_STRUCT, \
                  RUNTIME_DIAGNOSTIC_FLAG_STRUCT, \
                  RUNTIME_NOTPRODUCT_FLAG_STRUCT, \
                  IGNORE_RANGE, \
                  IGNORE_CONSTRAINT)
#if INCLUDE_ALL_GCS
 G1_FLAGS(RUNTIME_DEVELOP_FLAG_STRUCT, \
          RUNTIME_PD_DEVELOP_FLAG_STRUCT, \
          RUNTIME_PRODUCT_FLAG_STRUCT, \
          RUNTIME_PD_PRODUCT_FLAG_STRUCT, \
          RUNTIME_DIAGNOSTIC_FLAG_STRUCT, \
          RUNTIME_EXPERIMENTAL_FLAG_STRUCT, \
          RUNTIME_NOTPRODUCT_FLAG_STRUCT, \
          RUNTIME_MANAGEABLE_FLAG_STRUCT, \
          RUNTIME_PRODUCT_RW_FLAG_STRUCT, \
          IGNORE_RANGE, \
          IGNORE_CONSTRAINT)
#endif // INCLUDE_ALL_GCS
#if INCLUDE_JVMCI
 JVMCI_FLAGS(JVMCI_DEVELOP_FLAG_STRUCT, \
             JVMCI_PD_DEVELOP_FLAG_STRUCT, \
             JVMCI_PRODUCT_FLAG_STRUCT, \
             JVMCI_PD_PRODUCT_FLAG_STRUCT, \
             JVMCI_DIAGNOSTIC_FLAG_STRUCT, \
             JVMCI_EXPERIMENTAL_FLAG_STRUCT, \
             JVMCI_NOTPRODUCT_FLAG_STRUCT, \
             IGNORE_RANGE, \
             IGNORE_CONSTRAINT)
#endif // INCLUDE_JVMCI
#ifdef COMPILER1
 C1_FLAGS(C1_DEVELOP_FLAG_STRUCT, \
          C1_PD_DEVELOP_FLAG_STRUCT, \
          C1_PRODUCT_FLAG_STRUCT, \
          C1_PD_PRODUCT_FLAG_STRUCT, \
          C1_DIAGNOSTIC_FLAG_STRUCT, \
          C1_NOTPRODUCT_FLAG_STRUCT, \
          IGNORE_RANGE, \
          IGNORE_CONSTRAINT)
#endif // COMPILER1
#ifdef COMPILER2
 C2_FLAGS(C2_DEVELOP_FLAG_STRUCT, \
          C2_PD_DEVELOP_FLAG_STRUCT, \
          C2_PRODUCT_FLAG_STRUCT, \
          C2_PD_PRODUCT_FLAG_STRUCT, \
          C2_DIAGNOSTIC_FLAG_STRUCT, \
          C2_EXPERIMENTAL_FLAG_STRUCT, \
          C2_NOTPRODUCT_FLAG_STRUCT, \
          IGNORE_RANGE, \
          IGNORE_CONSTRAINT)
#endif // COMPILER2
#ifdef SHARK
 SHARK_FLAGS(SHARK_DEVELOP_FLAG_STRUCT, \
             SHARK_PD_DEVELOP_FLAG_STRUCT, \
             SHARK_PRODUCT_FLAG_STRUCT, \
             SHARK_PD_PRODUCT_FLAG_STRUCT, \
             SHARK_DIAGNOSTIC_FLAG_STRUCT, \
             SHARK_NOTPRODUCT_FLAG_STRUCT)
#endif // SHARK
 ARCH_FLAGS(ARCH_DEVELOP_FLAG_STRUCT, \
            ARCH_PRODUCT_FLAG_STRUCT, \
            ARCH_DIAGNOSTIC_FLAG_STRUCT, \
            ARCH_EXPERIMENTAL_FLAG_STRUCT, \
            ARCH_NOTPRODUCT_FLAG_STRUCT, \
            IGNORE_RANGE, \
            IGNORE_CONSTRAINT)
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
        return (return_flag ? current : NULL);
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
static void trace_flag_changed(const char* name, const T old_value, const T new_value, const Flag::Flags origin) {
  E e;
  e.set_name(name);
  e.set_old_value(old_value);
  e.set_new_value(new_value);
  e.set_origin(origin);
  e.commit();
}

static Flag::Error apply_constraint_and_check_range_bool(const char* name, bool new_value, bool verbose) {
  Flag::Error status = Flag::SUCCESS;
  CommandLineFlagConstraint* constraint = CommandLineFlagConstraintList::find_if_needs_check(name);
  if (constraint != NULL) {
    status = constraint->apply_bool(new_value, verbose);
  }
  return status;
}

Flag::Error CommandLineFlags::boolAt(const char* name, size_t len, bool* value, bool allow_locked, bool return_flag) {
  Flag* result = Flag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return Flag::INVALID_FLAG;
  if (!result->is_bool()) return Flag::WRONG_FORMAT;
  *value = result->get_bool();
  return Flag::SUCCESS;
}

Flag::Error CommandLineFlags::boolAtPut(Flag* flag, bool* value, Flag::Flags origin) {
  const char* name;
  if (flag == NULL) return Flag::INVALID_FLAG;
  if (!flag->is_bool()) return Flag::WRONG_FORMAT;
  name = flag->_name;
  Flag::Error check = apply_constraint_and_check_range_bool(name, *value, !CommandLineFlagConstraintList::validated_after_ergo());
  if (check != Flag::SUCCESS) return check;
  bool old_value = flag->get_bool();
  trace_flag_changed<EventBooleanFlagChanged, bool>(name, old_value, *value, origin);
  flag->set_bool(*value);
  *value = old_value;
  flag->set_origin(origin);
  return Flag::SUCCESS;
}

Flag::Error CommandLineFlags::boolAtPut(const char* name, size_t len, bool* value, Flag::Flags origin) {
  Flag* result = Flag::find_flag(name, len);
  return boolAtPut(result, value, origin);
}

Flag::Error CommandLineFlagsEx::boolAtPut(CommandLineFlagWithType flag, bool value, Flag::Flags origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_bool(), "wrong flag type");
  return CommandLineFlags::boolAtPut(faddr, &value, origin);
}

static Flag::Error apply_constraint_and_check_range_int(const char* name, int new_value, bool verbose) {
  Flag::Error status = Flag::SUCCESS;
  CommandLineFlagRange* range = CommandLineFlagRangeList::find(name);
  if (range != NULL) {
    status = range->check_int(new_value, verbose);
  }
  if (status == Flag::SUCCESS) {
    CommandLineFlagConstraint* constraint = CommandLineFlagConstraintList::find_if_needs_check(name);
    if (constraint != NULL) {
      status = constraint->apply_int(new_value, verbose);
    }
  }
  return status;
}

Flag::Error CommandLineFlags::intAt(const char* name, size_t len, int* value, bool allow_locked, bool return_flag) {
  Flag* result = Flag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return Flag::INVALID_FLAG;
  if (!result->is_int()) return Flag::WRONG_FORMAT;
  *value = result->get_int();
  return Flag::SUCCESS;
}

Flag::Error CommandLineFlags::intAtPut(Flag* flag, int* value, Flag::Flags origin) {
  const char* name;
  if (flag == NULL) return Flag::INVALID_FLAG;
  if (!flag->is_int()) return Flag::WRONG_FORMAT;
  name = flag->_name;
  Flag::Error check = apply_constraint_and_check_range_int(name, *value, !CommandLineFlagConstraintList::validated_after_ergo());
  if (check != Flag::SUCCESS) return check;
  int old_value = flag->get_int();
  trace_flag_changed<EventIntFlagChanged, s4>(name, old_value, *value, origin);
  flag->set_int(*value);
  *value = old_value;
  flag->set_origin(origin);
  return Flag::SUCCESS;
}

Flag::Error CommandLineFlags::intAtPut(const char* name, size_t len, int* value, Flag::Flags origin) {
  Flag* result = Flag::find_flag(name, len);
  return intAtPut(result, value, origin);
}

Flag::Error CommandLineFlagsEx::intAtPut(CommandLineFlagWithType flag, int value, Flag::Flags origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_int(), "wrong flag type");
  return CommandLineFlags::intAtPut(faddr, &value, origin);
}

static Flag::Error apply_constraint_and_check_range_uint(const char* name, uint new_value, bool verbose) {
  Flag::Error status = Flag::SUCCESS;
  CommandLineFlagRange* range = CommandLineFlagRangeList::find(name);
  if (range != NULL) {
    status = range->check_uint(new_value, verbose);
  }
  if (status == Flag::SUCCESS) {
    CommandLineFlagConstraint* constraint = CommandLineFlagConstraintList::find_if_needs_check(name);
    if (constraint != NULL) {
      status = constraint->apply_uint(new_value, verbose);
    }
  }
  return status;
}

Flag::Error CommandLineFlags::uintAt(const char* name, size_t len, uint* value, bool allow_locked, bool return_flag) {
  Flag* result = Flag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return Flag::INVALID_FLAG;
  if (!result->is_uint()) return Flag::WRONG_FORMAT;
  *value = result->get_uint();
  return Flag::SUCCESS;
}

Flag::Error CommandLineFlags::uintAtPut(Flag* flag, uint* value, Flag::Flags origin) {
  const char* name;
  if (flag == NULL) return Flag::INVALID_FLAG;
  if (!flag->is_uint()) return Flag::WRONG_FORMAT;
  name = flag->_name;
  Flag::Error check = apply_constraint_and_check_range_uint(name, *value, !CommandLineFlagConstraintList::validated_after_ergo());
  if (check != Flag::SUCCESS) return check;
  uint old_value = flag->get_uint();
  trace_flag_changed<EventUnsignedIntFlagChanged, u4>(name, old_value, *value, origin);
  flag->set_uint(*value);
  *value = old_value;
  flag->set_origin(origin);
  return Flag::SUCCESS;
}

Flag::Error CommandLineFlags::uintAtPut(const char* name, size_t len, uint* value, Flag::Flags origin) {
  Flag* result = Flag::find_flag(name, len);
  return uintAtPut(result, value, origin);
}

Flag::Error CommandLineFlagsEx::uintAtPut(CommandLineFlagWithType flag, uint value, Flag::Flags origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_uint(), "wrong flag type");
  return CommandLineFlags::uintAtPut(faddr, &value, origin);
}

Flag::Error CommandLineFlags::intxAt(const char* name, size_t len, intx* value, bool allow_locked, bool return_flag) {
  Flag* result = Flag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return Flag::INVALID_FLAG;
  if (!result->is_intx()) return Flag::WRONG_FORMAT;
  *value = result->get_intx();
  return Flag::SUCCESS;
}

static Flag::Error apply_constraint_and_check_range_intx(const char* name, intx new_value, bool verbose) {
  Flag::Error status = Flag::SUCCESS;
  CommandLineFlagRange* range = CommandLineFlagRangeList::find(name);
  if (range != NULL) {
    status = range->check_intx(new_value, verbose);
  }
  if (status == Flag::SUCCESS) {
    CommandLineFlagConstraint* constraint = CommandLineFlagConstraintList::find_if_needs_check(name);
    if (constraint != NULL) {
      status = constraint->apply_intx(new_value, verbose);
    }
  }
  return status;
}

Flag::Error CommandLineFlags::intxAtPut(Flag* flag, intx* value, Flag::Flags origin) {
  const char* name;
  if (flag == NULL) return Flag::INVALID_FLAG;
  if (!flag->is_intx()) return Flag::WRONG_FORMAT;
  name = flag->_name;
  Flag::Error check = apply_constraint_and_check_range_intx(name, *value, !CommandLineFlagConstraintList::validated_after_ergo());
  if (check != Flag::SUCCESS) return check;
  intx old_value = flag->get_intx();
  trace_flag_changed<EventLongFlagChanged, intx>(name, old_value, *value, origin);
  flag->set_intx(*value);
  *value = old_value;
  flag->set_origin(origin);
  return Flag::SUCCESS;
}

Flag::Error CommandLineFlags::intxAtPut(const char* name, size_t len, intx* value, Flag::Flags origin) {
  Flag* result = Flag::find_flag(name, len);
  return intxAtPut(result, value, origin);
}

Flag::Error CommandLineFlagsEx::intxAtPut(CommandLineFlagWithType flag, intx value, Flag::Flags origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_intx(), "wrong flag type");
  return CommandLineFlags::intxAtPut(faddr, &value, origin);
}

Flag::Error CommandLineFlags::uintxAt(const char* name, size_t len, uintx* value, bool allow_locked, bool return_flag) {
  Flag* result = Flag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return Flag::INVALID_FLAG;
  if (!result->is_uintx()) return Flag::WRONG_FORMAT;
  *value = result->get_uintx();
  return Flag::SUCCESS;
}

static Flag::Error apply_constraint_and_check_range_uintx(const char* name, uintx new_value, bool verbose) {
  Flag::Error status = Flag::SUCCESS;
  CommandLineFlagRange* range = CommandLineFlagRangeList::find(name);
  if (range != NULL) {
    status = range->check_uintx(new_value, verbose);
  }
  if (status == Flag::SUCCESS) {
    CommandLineFlagConstraint* constraint = CommandLineFlagConstraintList::find_if_needs_check(name);
    if (constraint != NULL) {
      status = constraint->apply_uintx(new_value, verbose);
    }
  }
  return status;
}

Flag::Error CommandLineFlags::uintxAtPut(Flag* flag, uintx* value, Flag::Flags origin) {
  const char* name;
  if (flag == NULL) return Flag::INVALID_FLAG;
  if (!flag->is_uintx()) return Flag::WRONG_FORMAT;
  name = flag->_name;
  Flag::Error check = apply_constraint_and_check_range_uintx(name, *value, !CommandLineFlagConstraintList::validated_after_ergo());
  if (check != Flag::SUCCESS) return check;
  uintx old_value = flag->get_uintx();
  trace_flag_changed<EventUnsignedLongFlagChanged, u8>(name, old_value, *value, origin);
  flag->set_uintx(*value);
  *value = old_value;
  flag->set_origin(origin);
  return Flag::SUCCESS;
}

Flag::Error CommandLineFlags::uintxAtPut(const char* name, size_t len, uintx* value, Flag::Flags origin) {
  Flag* result = Flag::find_flag(name, len);
  return uintxAtPut(result, value, origin);
}

Flag::Error CommandLineFlagsEx::uintxAtPut(CommandLineFlagWithType flag, uintx value, Flag::Flags origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_uintx(), "wrong flag type");
  return CommandLineFlags::uintxAtPut(faddr, &value, origin);
}

Flag::Error CommandLineFlags::uint64_tAt(const char* name, size_t len, uint64_t* value, bool allow_locked, bool return_flag) {
  Flag* result = Flag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return Flag::INVALID_FLAG;
  if (!result->is_uint64_t()) return Flag::WRONG_FORMAT;
  *value = result->get_uint64_t();
  return Flag::SUCCESS;
}

static Flag::Error apply_constraint_and_check_range_uint64_t(const char* name, uint64_t new_value, bool verbose) {
  Flag::Error status = Flag::SUCCESS;
  CommandLineFlagRange* range = CommandLineFlagRangeList::find(name);
  if (range != NULL) {
    status = range->check_uint64_t(new_value, verbose);
  }
  if (status == Flag::SUCCESS) {
    CommandLineFlagConstraint* constraint = CommandLineFlagConstraintList::find_if_needs_check(name);
    if (constraint != NULL) {
      status = constraint->apply_uint64_t(new_value, verbose);
    }
  }
  return status;
}

Flag::Error CommandLineFlags::uint64_tAtPut(Flag* flag, uint64_t* value, Flag::Flags origin) {
  const char* name;
  if (flag == NULL) return Flag::INVALID_FLAG;
  if (!flag->is_uint64_t()) return Flag::WRONG_FORMAT;
  name = flag->_name;
  Flag::Error check = apply_constraint_and_check_range_uint64_t(name, *value, !CommandLineFlagConstraintList::validated_after_ergo());
  if (check != Flag::SUCCESS) return check;
  uint64_t old_value = flag->get_uint64_t();
  trace_flag_changed<EventUnsignedLongFlagChanged, u8>(name, old_value, *value, origin);
  flag->set_uint64_t(*value);
  *value = old_value;
  flag->set_origin(origin);
  return Flag::SUCCESS;
}

Flag::Error CommandLineFlags::uint64_tAtPut(const char* name, size_t len, uint64_t* value, Flag::Flags origin) {
  Flag* result = Flag::find_flag(name, len);
  return uint64_tAtPut(result, value, origin);
}

Flag::Error CommandLineFlagsEx::uint64_tAtPut(CommandLineFlagWithType flag, uint64_t value, Flag::Flags origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_uint64_t(), "wrong flag type");
  return CommandLineFlags::uint64_tAtPut(faddr, &value, origin);
}

Flag::Error CommandLineFlags::size_tAt(const char* name, size_t len, size_t* value, bool allow_locked, bool return_flag) {
  Flag* result = Flag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return Flag::INVALID_FLAG;
  if (!result->is_size_t()) return Flag::WRONG_FORMAT;
  *value = result->get_size_t();
  return Flag::SUCCESS;
}

static Flag::Error apply_constraint_and_check_range_size_t(const char* name, size_t new_value, bool verbose) {
  Flag::Error status = Flag::SUCCESS;
  CommandLineFlagRange* range = CommandLineFlagRangeList::find(name);
  if (range != NULL) {
    status = range->check_size_t(new_value, verbose);
  }
  if (status == Flag::SUCCESS) {
    CommandLineFlagConstraint* constraint = CommandLineFlagConstraintList::find_if_needs_check(name);
    if (constraint != NULL) {
      status = constraint->apply_size_t(new_value, verbose);
    }
  }
  return status;
}


Flag::Error CommandLineFlags::size_tAtPut(Flag* flag, size_t* value, Flag::Flags origin) {
  const char* name;
  if (flag == NULL) return Flag::INVALID_FLAG;
  if (!flag->is_size_t()) return Flag::WRONG_FORMAT;
  name = flag->_name;
  Flag::Error check = apply_constraint_and_check_range_size_t(name, *value, !CommandLineFlagConstraintList::validated_after_ergo());
  if (check != Flag::SUCCESS) return check;
  size_t old_value = flag->get_size_t();
  trace_flag_changed<EventUnsignedLongFlagChanged, u8>(name, old_value, *value, origin);
  flag->set_size_t(*value);
  *value = old_value;
  flag->set_origin(origin);
  return Flag::SUCCESS;
}

Flag::Error CommandLineFlags::size_tAtPut(const char* name, size_t len, size_t* value, Flag::Flags origin) {
  Flag* result = Flag::find_flag(name, len);
  return size_tAtPut(result, value, origin);
}

Flag::Error CommandLineFlagsEx::size_tAtPut(CommandLineFlagWithType flag, size_t value, Flag::Flags origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_size_t(), "wrong flag type");
  return CommandLineFlags::size_tAtPut(faddr, &value, origin);
}

Flag::Error CommandLineFlags::doubleAt(const char* name, size_t len, double* value, bool allow_locked, bool return_flag) {
  Flag* result = Flag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return Flag::INVALID_FLAG;
  if (!result->is_double()) return Flag::WRONG_FORMAT;
  *value = result->get_double();
  return Flag::SUCCESS;
}

static Flag::Error apply_constraint_and_check_range_double(const char* name, double new_value, bool verbose) {
  Flag::Error status = Flag::SUCCESS;
  CommandLineFlagRange* range = CommandLineFlagRangeList::find(name);
  if (range != NULL) {
    status = range->check_double(new_value, verbose);
  }
  if (status == Flag::SUCCESS) {
    CommandLineFlagConstraint* constraint = CommandLineFlagConstraintList::find_if_needs_check(name);
    if (constraint != NULL) {
      status = constraint->apply_double(new_value, verbose);
    }
  }
  return status;
}

Flag::Error CommandLineFlags::doubleAtPut(Flag* flag, double* value, Flag::Flags origin) {
  const char* name;
  if (flag == NULL) return Flag::INVALID_FLAG;
  if (!flag->is_double()) return Flag::WRONG_FORMAT;
  name = flag->_name;
  Flag::Error check = apply_constraint_and_check_range_double(name, *value, !CommandLineFlagConstraintList::validated_after_ergo());
  if (check != Flag::SUCCESS) return check;
  double old_value = flag->get_double();
  trace_flag_changed<EventDoubleFlagChanged, double>(name, old_value, *value, origin);
  flag->set_double(*value);
  *value = old_value;
  flag->set_origin(origin);
  return Flag::SUCCESS;
}

Flag::Error CommandLineFlags::doubleAtPut(const char* name, size_t len, double* value, Flag::Flags origin) {
  Flag* result = Flag::find_flag(name, len);
  return doubleAtPut(result, value, origin);
}

Flag::Error CommandLineFlagsEx::doubleAtPut(CommandLineFlagWithType flag, double value, Flag::Flags origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_double(), "wrong flag type");
  return CommandLineFlags::doubleAtPut(faddr, &value, origin);
}

Flag::Error CommandLineFlags::ccstrAt(const char* name, size_t len, ccstr* value, bool allow_locked, bool return_flag) {
  Flag* result = Flag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return Flag::INVALID_FLAG;
  if (!result->is_ccstr()) return Flag::WRONG_FORMAT;
  *value = result->get_ccstr();
  return Flag::SUCCESS;
}

Flag::Error CommandLineFlags::ccstrAtPut(const char* name, size_t len, ccstr* value, Flag::Flags origin) {
  Flag* result = Flag::find_flag(name, len);
  if (result == NULL) return Flag::INVALID_FLAG;
  if (!result->is_ccstr()) return Flag::WRONG_FORMAT;
  ccstr old_value = result->get_ccstr();
  trace_flag_changed<EventStringFlagChanged, const char*>(name, old_value, *value, origin);
  char* new_value = NULL;
  if (*value != NULL) {
    new_value = os::strdup_check_oom(*value);
  }
  result->set_ccstr(new_value);
  if (result->is_default() && old_value != NULL) {
    // Prior value is NOT heap allocated, but was a literal constant.
    old_value = os::strdup_check_oom(old_value);
  }
  *value = old_value;
  result->set_origin(origin);
  return Flag::SUCCESS;
}

Flag::Error CommandLineFlagsEx::ccstrAtPut(CommandLineFlagWithType flag, ccstr value, Flag::Flags origin) {
  Flag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_ccstr(), "wrong flag type");
  ccstr old_value = faddr->get_ccstr();
  trace_flag_changed<EventStringFlagChanged, const char*>(faddr->_name, old_value, value, origin);
  char* new_value = os::strdup_check_oom(value);
  faddr->set_ccstr(new_value);
  if (!faddr->is_default() && old_value != NULL) {
    // Prior value is heap allocated so free it.
    FREE_C_HEAP_ARRAY(char, old_value);
  }
  faddr->set_origin(origin);
  return Flag::SUCCESS;
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
  FREE_C_HEAP_ARRAY(Flag*, array);
}

#ifndef PRODUCT

void CommandLineFlags::verify() {
  assert(Arguments::check_vm_args_consistency(), "Some flag settings conflict");
}

#endif // PRODUCT

void CommandLineFlags::printFlags(outputStream* out, bool withComments, bool printRanges) {
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
  if (!printRanges) {
    out->print_cr("[Global flags]");
  } else {
    out->print_cr("[Global flags ranges]");
  }

  for (size_t i = 0; i < length; i++) {
    if (array[i]->is_unlocked()) {
      array[i]->print_on(out, withComments, printRanges);
    }
  }
  FREE_C_HEAP_ARRAY(Flag*, array);
}
