/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jfrEvents.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/arguments.hpp"
#include "runtime/flags/jvmFlag.hpp"
#include "runtime/flags/jvmFlagConstraintList.hpp"
#include "runtime/flags/jvmFlagWriteableList.hpp"
#include "runtime/flags/jvmFlagRangeList.hpp"
#include "runtime/globals_extension.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/stringUtils.hpp"

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

const char* JVMFlag::get_int_default_range_str() {
  return create_range_str("[ " INT32_FORMAT_W(-25) " ... " INT32_FORMAT_W(25) " ]", INT_MIN, INT_MAX);
}

const char* JVMFlag::get_uint_default_range_str() {
  return create_range_str("[ " UINT32_FORMAT_W(-25) " ... " UINT32_FORMAT_W(25) " ]", 0, UINT_MAX);
}

const char* JVMFlag::get_intx_default_range_str() {
  return create_range_str("[ " INTX_FORMAT_W(-25) " ... " INTX_FORMAT_W(25) " ]", min_intx, max_intx);
}

const char* JVMFlag::get_uintx_default_range_str() {
  return create_range_str("[ " UINTX_FORMAT_W(-25) " ... " UINTX_FORMAT_W(25) " ]", 0, max_uintx);
}

const char* JVMFlag::get_uint64_t_default_range_str() {
  return create_range_str("[ " UINT64_FORMAT_W(-25) " ... " UINT64_FORMAT_W(25) " ]", 0, uint64_t(max_juint));
}

const char* JVMFlag::get_size_t_default_range_str() {
  return create_range_str("[ " SIZE_FORMAT_W(-25) " ... " SIZE_FORMAT_W(25) " ]", 0, SIZE_MAX);
}

const char* JVMFlag::get_double_default_range_str() {
  return create_range_str("[ %-25.3f ... %25.3f ]", DBL_MIN, DBL_MAX);
}

static bool is_product_build() {
#ifdef PRODUCT
  return true;
#else
  return false;
#endif
}

JVMFlag::Error JVMFlag::check_writable(bool changed) {
  if (is_constant_in_binary()) {
    fatal("flag is constant: %s", _name);
  }

  JVMFlag::Error error = JVMFlag::SUCCESS;
  if (changed) {
    JVMFlagWriteable* writeable = JVMFlagWriteableList::find(_name);
    if (writeable) {
      if (writeable->is_writeable() == false) {
        switch (writeable->type())
        {
          case JVMFlagWriteable::Once:
            error = JVMFlag::SET_ONLY_ONCE;
            jio_fprintf(defaultStream::error_stream(), "Error: %s may not be set more than once\n", _name);
            break;
          case JVMFlagWriteable::CommandLineOnly:
            error = JVMFlag::COMMAND_LINE_ONLY;
            jio_fprintf(defaultStream::error_stream(), "Error: %s may be modified only from commad line\n", _name);
            break;
          default:
            ShouldNotReachHere();
            break;
        }
      }
      writeable->mark_once();
    }
  }
  return error;
}

bool JVMFlag::is_bool() const {
  return strcmp(_type, "bool") == 0;
}

bool JVMFlag::get_bool() const {
  return *((bool*) _addr);
}

JVMFlag::Error JVMFlag::set_bool(bool value) {
  JVMFlag::Error error = check_writable(value!=get_bool());
  if (error == JVMFlag::SUCCESS) {
    *((bool*) _addr) = value;
  }
  return error;
}

bool JVMFlag::is_int() const {
  return strcmp(_type, "int")  == 0;
}

int JVMFlag::get_int() const {
  return *((int*) _addr);
}

JVMFlag::Error JVMFlag::set_int(int value) {
  JVMFlag::Error error = check_writable(value!=get_int());
  if (error == JVMFlag::SUCCESS) {
    *((int*) _addr) = value;
  }
  return error;
}

bool JVMFlag::is_uint() const {
  return strcmp(_type, "uint")  == 0;
}

uint JVMFlag::get_uint() const {
  return *((uint*) _addr);
}

JVMFlag::Error JVMFlag::set_uint(uint value) {
  JVMFlag::Error error = check_writable(value!=get_uint());
  if (error == JVMFlag::SUCCESS) {
    *((uint*) _addr) = value;
  }
  return error;
}

bool JVMFlag::is_intx() const {
  return strcmp(_type, "intx")  == 0;
}

intx JVMFlag::get_intx() const {
  return *((intx*) _addr);
}

JVMFlag::Error JVMFlag::set_intx(intx value) {
  JVMFlag::Error error = check_writable(value!=get_intx());
  if (error == JVMFlag::SUCCESS) {
    *((intx*) _addr) = value;
  }
  return error;
}

bool JVMFlag::is_uintx() const {
  return strcmp(_type, "uintx") == 0;
}

uintx JVMFlag::get_uintx() const {
  return *((uintx*) _addr);
}

JVMFlag::Error JVMFlag::set_uintx(uintx value) {
  JVMFlag::Error error = check_writable(value!=get_uintx());
  if (error == JVMFlag::SUCCESS) {
    *((uintx*) _addr) = value;
  }
  return error;
}

bool JVMFlag::is_uint64_t() const {
  return strcmp(_type, "uint64_t") == 0;
}

uint64_t JVMFlag::get_uint64_t() const {
  return *((uint64_t*) _addr);
}

JVMFlag::Error JVMFlag::set_uint64_t(uint64_t value) {
  JVMFlag::Error error = check_writable(value!=get_uint64_t());
  if (error == JVMFlag::SUCCESS) {
    *((uint64_t*) _addr) = value;
  }
  return error;
}

bool JVMFlag::is_size_t() const {
  return strcmp(_type, "size_t") == 0;
}

size_t JVMFlag::get_size_t() const {
  return *((size_t*) _addr);
}

JVMFlag::Error JVMFlag::set_size_t(size_t value) {
  JVMFlag::Error error = check_writable(value!=get_size_t());
  if (error == JVMFlag::SUCCESS) {
    *((size_t*) _addr) = value;
  }
  return error;
}

bool JVMFlag::is_double() const {
  return strcmp(_type, "double") == 0;
}

double JVMFlag::get_double() const {
  return *((double*) _addr);
}

JVMFlag::Error JVMFlag::set_double(double value) {
  JVMFlag::Error error = check_writable(value!=get_double());
  if (error == JVMFlag::SUCCESS) {
    *((double*) _addr) = value;
  }
  return error;
}

bool JVMFlag::is_ccstr() const {
  return strcmp(_type, "ccstr") == 0 || strcmp(_type, "ccstrlist") == 0;
}

bool JVMFlag::ccstr_accumulates() const {
  return strcmp(_type, "ccstrlist") == 0;
}

ccstr JVMFlag::get_ccstr() const {
  return *((ccstr*) _addr);
}

JVMFlag::Error JVMFlag::set_ccstr(ccstr value) {
  JVMFlag::Error error = check_writable(value!=get_ccstr());
  if (error == JVMFlag::SUCCESS) {
    *((ccstr*) _addr) = value;
  }
  return error;
}


JVMFlag::Flags JVMFlag::get_origin() {
  return Flags(_flags & VALUE_ORIGIN_MASK);
}

void JVMFlag::set_origin(Flags origin) {
  assert((origin & VALUE_ORIGIN_MASK) == origin, "sanity");
  Flags new_origin = Flags((origin == COMMAND_LINE) ? Flags(origin | ORIG_COMMAND_LINE) : origin);
  _flags = Flags((_flags & ~VALUE_ORIGIN_MASK) | new_origin);
}

bool JVMFlag::is_default() {
  return (get_origin() == DEFAULT);
}

bool JVMFlag::is_ergonomic() {
  return (get_origin() == ERGONOMIC);
}

bool JVMFlag::is_command_line() {
  return (_flags & ORIG_COMMAND_LINE) != 0;
}

void JVMFlag::set_command_line() {
  _flags = Flags(_flags | ORIG_COMMAND_LINE);
}

bool JVMFlag::is_product() const {
  return (_flags & KIND_PRODUCT) != 0;
}

bool JVMFlag::is_manageable() const {
  return (_flags & KIND_MANAGEABLE) != 0;
}

bool JVMFlag::is_diagnostic() const {
  return (_flags & KIND_DIAGNOSTIC) != 0;
}

bool JVMFlag::is_experimental() const {
  return (_flags & KIND_EXPERIMENTAL) != 0;
}

bool JVMFlag::is_notproduct() const {
  return (_flags & KIND_NOT_PRODUCT) != 0;
}

bool JVMFlag::is_develop() const {
  return (_flags & KIND_DEVELOP) != 0;
}

bool JVMFlag::is_read_write() const {
  return (_flags & KIND_READ_WRITE) != 0;
}

/**
 * Returns if this flag is a constant in the binary.  Right now this is
 * true for notproduct and develop flags in product builds.
 */
bool JVMFlag::is_constant_in_binary() const {
#ifdef PRODUCT
  return is_notproduct() || is_develop();
#else
  return false;
#endif
}

bool JVMFlag::is_unlocker() const {
  return strcmp(_name, "UnlockDiagnosticVMOptions") == 0     ||
  strcmp(_name, "UnlockExperimentalVMOptions") == 0   ||
  is_unlocker_ext();
}

bool JVMFlag::is_unlocked() const {
  if (is_diagnostic()) {
    return UnlockDiagnosticVMOptions;
  }
  if (is_experimental()) {
    return UnlockExperimentalVMOptions;
  }
  return is_unlocked_ext();
}

void JVMFlag::clear_diagnostic() {
  assert(is_diagnostic(), "sanity");
  _flags = Flags(_flags & ~KIND_DIAGNOSTIC);
  assert(!is_diagnostic(), "sanity");
}

// Get custom message for this locked flag, or NULL if
// none is available. Returns message type produced.
JVMFlag::MsgType JVMFlag::get_locked_message(char* buf, int buflen) const {
  buf[0] = '\0';
  if (is_diagnostic() && !is_unlocked()) {
    jio_snprintf(buf, buflen,
                 "Error: VM option '%s' is diagnostic and must be enabled via -XX:+UnlockDiagnosticVMOptions.\n"
                 "Error: The unlock option must precede '%s'.\n",
                 _name, _name);
    return JVMFlag::DIAGNOSTIC_FLAG_BUT_LOCKED;
  }
  if (is_experimental() && !is_unlocked()) {
    jio_snprintf(buf, buflen,
                 "Error: VM option '%s' is experimental and must be enabled via -XX:+UnlockExperimentalVMOptions.\n"
                 "Error: The unlock option must precede '%s'.\n",
                 _name, _name);
    return JVMFlag::EXPERIMENTAL_FLAG_BUT_LOCKED;
  }
  if (is_develop() && is_product_build()) {
    jio_snprintf(buf, buflen, "Error: VM option '%s' is develop and is available only in debug version of VM.\n",
                 _name);
    return JVMFlag::DEVELOPER_FLAG_BUT_PRODUCT_BUILD;
  }
  if (is_notproduct() && is_product_build()) {
    jio_snprintf(buf, buflen, "Error: VM option '%s' is notproduct and is available only in debug version of VM.\n",
                 _name);
    return JVMFlag::NOTPRODUCT_FLAG_BUT_PRODUCT_BUILD;
  }
  return get_locked_message_ext(buf, buflen);
}

bool JVMFlag::is_writeable() const {
  return is_manageable() || (is_product() && is_read_write()) || is_writeable_ext();
}

// All flags except "manageable" are assumed to be internal flags.
// Long term, we need to define a mechanism to specify which flags
// are external/stable and change this function accordingly.
bool JVMFlag::is_external() const {
  return is_manageable() || is_external_ext();
}

// Helper function for JVMFlag::print_on().
// Fills current line up to requested position.
// Should the current position already be past the requested position,
// one separator blank is enforced.
void fill_to_pos(outputStream* st, unsigned int req_pos) {
  if ((unsigned int)st->position() < req_pos) {
    st->fill_to(req_pos);  // need to fill with blanks to reach req_pos
  } else {
    st->print(" ");        // enforce blank separation. Previous field too long.
  }
}

void JVMFlag::print_on(outputStream* st, bool withComments, bool printRanges) {
  // Don't print notproduct and develop flags in a product build.
  if (is_constant_in_binary()) {
    return;
  }

  if (!printRanges) {
    // The command line options -XX:+PrintFlags* cause this function to be called
    // for each existing flag to print information pertinent to this flag. The data
    // is displayed in columnar form, with the following layout:
    //  col1 - data type, right-justified
    //  col2 - name,      left-justified
    //  col3 - ' ='       double-char, leading space to align with possible '+='
    //  col4 - value      left-justified
    //  col5 - kind       right-justified
    //  col6 - origin     left-justified
    //  col7 - comments   left-justified
    //
    //  The column widths are fixed. They are defined such that, for most cases,
    //  an eye-pleasing tabular output is created.
    //
    //  Sample output:
    //       bool CMSScavengeBeforeRemark                  = false                                     {product} {default}
    //      uintx CMSScheduleRemarkEdenPenetration         = 50                                        {product} {default}
    //     size_t CMSScheduleRemarkEdenSizeThreshold       = 2097152                                   {product} {default}
    //      uintx CMSScheduleRemarkSamplingRatio           = 5                                         {product} {default}
    //     double CMSSmallCoalSurplusPercent               = 1.050000                                  {product} {default}
    //      ccstr CompileCommandFile                       = MyFile.cmd                                {product} {command line}
    //  ccstrlist CompileOnly                              = Method1
    //            CompileOnly                             += Method2                                   {product} {command line}
    //  |         |                                       |  |                              |                    |               |
    //  |         |                                       |  |                              |                    |               +-- col7
    //  |         |                                       |  |                              |                    +-- col6
    //  |         |                                       |  |                              +-- col5
    //  |         |                                       |  +-- col4
    //  |         |                                       +-- col3
    //  |         +-- col2
    //  +-- col1

    const unsigned int col_spacing = 1;
    const unsigned int col1_pos    = 0;
    const unsigned int col1_width  = 9;
    const unsigned int col2_pos    = col1_pos + col1_width + col_spacing;
    const unsigned int col2_width  = 39;
    const unsigned int col3_pos    = col2_pos + col2_width + col_spacing;
    const unsigned int col3_width  = 2;
    const unsigned int col4_pos    = col3_pos + col3_width + col_spacing;
    const unsigned int col4_width  = 30;
    const unsigned int col5_pos    = col4_pos + col4_width + col_spacing;
    const unsigned int col5_width  = 20;
    const unsigned int col6_pos    = col5_pos + col5_width + col_spacing;
    const unsigned int col6_width  = 15;
    const unsigned int col7_pos    = col6_pos + col6_width + col_spacing;
    const unsigned int col7_width  = 1;

    st->fill_to(col1_pos);
    st->print("%*s", col1_width, _type);  // right-justified, therefore width is required.

    fill_to_pos(st, col2_pos);
    st->print("%s", _name);

    fill_to_pos(st, col3_pos);
    st->print(" =");  // use " =" for proper alignment with multiline ccstr output.

    fill_to_pos(st, col4_pos);
    if (is_bool()) {
      st->print("%s", get_bool() ? "true" : "false");
    } else if (is_int()) {
      st->print("%d", get_int());
    } else if (is_uint()) {
      st->print("%u", get_uint());
    } else if (is_intx()) {
      st->print(INTX_FORMAT, get_intx());
    } else if (is_uintx()) {
      st->print(UINTX_FORMAT, get_uintx());
    } else if (is_uint64_t()) {
      st->print(UINT64_FORMAT, get_uint64_t());
    } else if (is_size_t()) {
      st->print(SIZE_FORMAT, get_size_t());
    } else if (is_double()) {
      st->print("%f", get_double());
    } else if (is_ccstr()) {
      // Honor <newline> characters in ccstr: print multiple lines.
      const char* cp = get_ccstr();
      if (cp != NULL) {
        const char* eol;
        while ((eol = strchr(cp, '\n')) != NULL) {
          size_t llen = pointer_delta(eol, cp, sizeof(char));
          st->print("%.*s", (int)llen, cp);
          st->cr();
          cp = eol+1;
          fill_to_pos(st, col2_pos);
          st->print("%s", _name);
          fill_to_pos(st, col3_pos);
          st->print("+=");
          fill_to_pos(st, col4_pos);
        }
        st->print("%s", cp);
      }
    } else {
      st->print("unhandled  type %s", _type);
      st->cr();
      return;
    }

    fill_to_pos(st, col5_pos);
    print_kind(st, col5_width);

    fill_to_pos(st, col6_pos);
    print_origin(st, col6_width);

#ifndef PRODUCT
    if (withComments) {
      fill_to_pos(st, col7_pos);
      st->print("%s", _doc);
    }
#endif
    st->cr();
  } else if (!is_bool() && !is_ccstr()) {
    // The command line options -XX:+PrintFlags* cause this function to be called
    // for each existing flag to print information pertinent to this flag. The data
    // is displayed in columnar form, with the following layout:
    //  col1 - data type, right-justified
    //  col2 - name,      left-justified
    //  col4 - range      [ min ... max]
    //  col5 - kind       right-justified
    //  col6 - origin     left-justified
    //  col7 - comments   left-justified
    //
    //  The column widths are fixed. They are defined such that, for most cases,
    //  an eye-pleasing tabular output is created.
    //
    //  Sample output:
    //       intx MinPassesBeforeFlush                               [ 0                         ...       9223372036854775807 ]                         {diagnostic} {default}
    //      uintx MinRAMFraction                                     [ 1                         ...      18446744073709551615 ]                            {product} {default}
    //     double MinRAMPercentage                                   [ 0.000                     ...                   100.000 ]                            {product} {default}
    //      uintx MinSurvivorRatio                                   [ 3                         ...      18446744073709551615 ]                            {product} {default}
    //     size_t MinTLABSize                                        [ 1                         ...       9223372036854775807 ]                            {product} {default}
    //       intx MonitorBound                                       [ 0                         ...                2147483647 ]                            {product} {default}
    //  |         |                                                  |                                                           |                                    |               |
    //  |         |                                                  |                                                           |                                    |               +-- col7
    //  |         |                                                  |                                                           |                                    +-- col6
    //  |         |                                                  |                                                           +-- col5
    //  |         |                                                  +-- col4
    //  |         +-- col2
    //  +-- col1

    const unsigned int col_spacing = 1;
    const unsigned int col1_pos    = 0;
    const unsigned int col1_width  = 9;
    const unsigned int col2_pos    = col1_pos + col1_width + col_spacing;
    const unsigned int col2_width  = 49;
    const unsigned int col3_pos    = col2_pos + col2_width + col_spacing;
    const unsigned int col3_width  = 0;
    const unsigned int col4_pos    = col3_pos + col3_width + col_spacing;
    const unsigned int col4_width  = 60;
    const unsigned int col5_pos    = col4_pos + col4_width + col_spacing;
    const unsigned int col5_width  = 35;
    const unsigned int col6_pos    = col5_pos + col5_width + col_spacing;
    const unsigned int col6_width  = 15;
    const unsigned int col7_pos    = col6_pos + col6_width + col_spacing;
    const unsigned int col7_width  = 1;

    st->fill_to(col1_pos);
    st->print("%*s", col1_width, _type);  // right-justified, therefore width is required.

    fill_to_pos(st, col2_pos);
    st->print("%s", _name);

    fill_to_pos(st, col4_pos);
    RangeStrFunc func = NULL;
    if (is_int()) {
      func = JVMFlag::get_int_default_range_str;
    } else if (is_uint()) {
      func = JVMFlag::get_uint_default_range_str;
    } else if (is_intx()) {
      func = JVMFlag::get_intx_default_range_str;
    } else if (is_uintx()) {
      func = JVMFlag::get_uintx_default_range_str;
    } else if (is_uint64_t()) {
      func = JVMFlag::get_uint64_t_default_range_str;
    } else if (is_size_t()) {
      func = JVMFlag::get_size_t_default_range_str;
    } else if (is_double()) {
      func = JVMFlag::get_double_default_range_str;
    } else {
      st->print("unhandled  type %s", _type);
      st->cr();
      return;
    }
    JVMFlagRangeList::print(st, _name, func);

    fill_to_pos(st, col5_pos);
    print_kind(st, col5_width);

    fill_to_pos(st, col6_pos);
    print_origin(st, col6_width);

#ifndef PRODUCT
    if (withComments) {
      fill_to_pos(st, col7_pos);
      st->print("%s", _doc);
    }
#endif
    st->cr();
  }
}

void JVMFlag::print_kind(outputStream* st, unsigned int width) {
  struct Data {
    int flag;
    const char* name;
  };

  Data data[] = {
    { KIND_JVMCI, "JVMCI" },
    { KIND_C1, "C1" },
    { KIND_C2, "C2" },
    { KIND_ARCH, "ARCH" },
    { KIND_PLATFORM_DEPENDENT, "pd" },
    { KIND_PRODUCT, "product" },
    { KIND_MANAGEABLE, "manageable" },
    { KIND_DIAGNOSTIC, "diagnostic" },
    { KIND_EXPERIMENTAL, "experimental" },
    { KIND_NOT_PRODUCT, "notproduct" },
    { KIND_DEVELOP, "develop" },
    { KIND_LP64_PRODUCT, "lp64_product" },
    { KIND_READ_WRITE, "rw" },
    { -1, "" }
  };

  if ((_flags & KIND_MASK) != 0) {
    bool is_first = true;
    const size_t buffer_size = 64;
    size_t buffer_used = 0;
    char kind[buffer_size];

    jio_snprintf(kind, buffer_size, "{");
    buffer_used++;
    for (int i = 0; data[i].flag != -1; i++) {
      Data d = data[i];
      if ((_flags & d.flag) != 0) {
        if (is_first) {
          is_first = false;
        } else {
          assert(buffer_used + 1 < buffer_size, "Too small buffer");
          jio_snprintf(kind + buffer_used, buffer_size - buffer_used, " ");
          buffer_used++;
        }
        size_t length = strlen(d.name);
        assert(buffer_used + length < buffer_size, "Too small buffer");
        jio_snprintf(kind + buffer_used, buffer_size - buffer_used, "%s", d.name);
        buffer_used += length;
      }
    }
    assert(buffer_used + 2 <= buffer_size, "Too small buffer");
    jio_snprintf(kind + buffer_used, buffer_size - buffer_used, "}");
    st->print("%*s", width, kind);
  }
}

void JVMFlag::print_origin(outputStream* st, unsigned int width) {
  int origin = _flags & VALUE_ORIGIN_MASK;
  st->print("{");
  switch(origin) {
    case DEFAULT:
      st->print("default"); break;
    case COMMAND_LINE:
      st->print("command line"); break;
    case ENVIRON_VAR:
      st->print("environment"); break;
    case CONFIG_FILE:
      st->print("config file"); break;
    case MANAGEMENT:
      st->print("management"); break;
    case ERGONOMIC:
      if (_flags & ORIG_COMMAND_LINE) {
        st->print("command line, ");
      }
      st->print("ergonomic"); break;
    case ATTACH_ON_DEMAND:
      st->print("attach"); break;
    case INTERNAL:
      st->print("internal"); break;
  }
  st->print("}");
}

void JVMFlag::print_as_flag(outputStream* st) {
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

const char* JVMFlag::flag_error_str(JVMFlag::Error error) {
  switch (error) {
    case JVMFlag::MISSING_NAME: return "MISSING_NAME";
    case JVMFlag::MISSING_VALUE: return "MISSING_VALUE";
    case JVMFlag::NON_WRITABLE: return "NON_WRITABLE";
    case JVMFlag::OUT_OF_BOUNDS: return "OUT_OF_BOUNDS";
    case JVMFlag::VIOLATES_CONSTRAINT: return "VIOLATES_CONSTRAINT";
    case JVMFlag::INVALID_FLAG: return "INVALID_FLAG";
    case JVMFlag::ERR_OTHER: return "ERR_OTHER";
    case JVMFlag::SUCCESS: return "SUCCESS";
    default: ShouldNotReachHere(); return "NULL";
  }
}

// 4991491 do not "optimize out" the was_set false values: omitting them
// tickles a Microsoft compiler bug causing flagTable to be malformed

#define RUNTIME_PRODUCT_FLAG_STRUCT(     type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_PRODUCT) },
#define RUNTIME_PD_PRODUCT_FLAG_STRUCT(  type, name,        doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_PRODUCT | JVMFlag::KIND_PLATFORM_DEPENDENT) },
#define RUNTIME_DIAGNOSTIC_FLAG_STRUCT(  type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_DIAGNOSTIC) },
#define RUNTIME_PD_DIAGNOSTIC_FLAG_STRUCT(type, name,       doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_DIAGNOSTIC | JVMFlag::KIND_PLATFORM_DEPENDENT) },
#define RUNTIME_EXPERIMENTAL_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_EXPERIMENTAL) },
#define RUNTIME_MANAGEABLE_FLAG_STRUCT(  type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_MANAGEABLE) },
#define RUNTIME_PRODUCT_RW_FLAG_STRUCT(  type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_PRODUCT | JVMFlag::KIND_READ_WRITE) },
#define RUNTIME_DEVELOP_FLAG_STRUCT(     type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_DEVELOP) },
#define RUNTIME_PD_DEVELOP_FLAG_STRUCT(  type, name,        doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_DEVELOP | JVMFlag::KIND_PLATFORM_DEPENDENT) },
#define RUNTIME_NOTPRODUCT_FLAG_STRUCT(  type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_NOT_PRODUCT) },

#define JVMCI_PRODUCT_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_JVMCI | JVMFlag::KIND_PRODUCT) },
#define JVMCI_PD_PRODUCT_FLAG_STRUCT(    type, name,        doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_JVMCI | JVMFlag::KIND_PRODUCT | JVMFlag::KIND_PLATFORM_DEPENDENT) },
#define JVMCI_DIAGNOSTIC_FLAG_STRUCT(    type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_JVMCI | JVMFlag::KIND_DIAGNOSTIC) },
#define JVMCI_PD_DIAGNOSTIC_FLAG_STRUCT( type, name,        doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_JVMCI | JVMFlag::KIND_DIAGNOSTIC | JVMFlag::KIND_PLATFORM_DEPENDENT) },
#define JVMCI_EXPERIMENTAL_FLAG_STRUCT(  type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_JVMCI | JVMFlag::KIND_EXPERIMENTAL) },
#define JVMCI_DEVELOP_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_JVMCI | JVMFlag::KIND_DEVELOP) },
#define JVMCI_PD_DEVELOP_FLAG_STRUCT(    type, name,        doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_JVMCI | JVMFlag::KIND_DEVELOP | JVMFlag::KIND_PLATFORM_DEPENDENT) },
#define JVMCI_NOTPRODUCT_FLAG_STRUCT(    type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_JVMCI | JVMFlag::KIND_NOT_PRODUCT) },

#ifdef _LP64
#define RUNTIME_LP64_PRODUCT_FLAG_STRUCT(type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_LP64_PRODUCT) },
#else
#define RUNTIME_LP64_PRODUCT_FLAG_STRUCT(type, name, value, doc) /* flag is constant */
#endif // _LP64

#define C1_PRODUCT_FLAG_STRUCT(          type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_C1 | JVMFlag::KIND_PRODUCT) },
#define C1_PD_PRODUCT_FLAG_STRUCT(       type, name,        doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_C1 | JVMFlag::KIND_PRODUCT | JVMFlag::KIND_PLATFORM_DEPENDENT) },
#define C1_DIAGNOSTIC_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_C1 | JVMFlag::KIND_DIAGNOSTIC) },
#define C1_PD_DIAGNOSTIC_FLAG_STRUCT(    type, name,        doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_C1 | JVMFlag::KIND_DIAGNOSTIC | JVMFlag::KIND_PLATFORM_DEPENDENT) },
#define C1_DEVELOP_FLAG_STRUCT(          type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_C1 | JVMFlag::KIND_DEVELOP) },
#define C1_PD_DEVELOP_FLAG_STRUCT(       type, name,        doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_C1 | JVMFlag::KIND_DEVELOP | JVMFlag::KIND_PLATFORM_DEPENDENT) },
#define C1_NOTPRODUCT_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_C1 | JVMFlag::KIND_NOT_PRODUCT) },

#define C2_PRODUCT_FLAG_STRUCT(          type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_C2 | JVMFlag::KIND_PRODUCT) },
#define C2_PD_PRODUCT_FLAG_STRUCT(       type, name,        doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_C2 | JVMFlag::KIND_PRODUCT | JVMFlag::KIND_PLATFORM_DEPENDENT) },
#define C2_DIAGNOSTIC_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_C2 | JVMFlag::KIND_DIAGNOSTIC) },
#define C2_PD_DIAGNOSTIC_FLAG_STRUCT(    type, name,        doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_C2 | JVMFlag::KIND_DIAGNOSTIC | JVMFlag::KIND_PLATFORM_DEPENDENT) },
#define C2_EXPERIMENTAL_FLAG_STRUCT(     type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_C2 | JVMFlag::KIND_EXPERIMENTAL) },
#define C2_DEVELOP_FLAG_STRUCT(          type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_C2 | JVMFlag::KIND_DEVELOP) },
#define C2_PD_DEVELOP_FLAG_STRUCT(       type, name,        doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_C2 | JVMFlag::KIND_DEVELOP | JVMFlag::KIND_PLATFORM_DEPENDENT) },
#define C2_NOTPRODUCT_FLAG_STRUCT(       type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_C2 | JVMFlag::KIND_NOT_PRODUCT) },

#define ARCH_PRODUCT_FLAG_STRUCT(        type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_ARCH | JVMFlag::KIND_PRODUCT) },
#define ARCH_DIAGNOSTIC_FLAG_STRUCT(     type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_ARCH | JVMFlag::KIND_DIAGNOSTIC) },
#define ARCH_EXPERIMENTAL_FLAG_STRUCT(   type, name, value, doc) { #type, XSTR(name), &name,         NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_ARCH | JVMFlag::KIND_EXPERIMENTAL) },
#define ARCH_DEVELOP_FLAG_STRUCT(        type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_ARCH | JVMFlag::KIND_DEVELOP) },
#define ARCH_NOTPRODUCT_FLAG_STRUCT(     type, name, value, doc) { #type, XSTR(name), (void*) &name, NOT_PRODUCT_ARG(doc) JVMFlag::Flags(JVMFlag::DEFAULT | JVMFlag::KIND_ARCH | JVMFlag::KIND_NOT_PRODUCT) },

static JVMFlag flagTable[] = {
  VM_FLAGS(RUNTIME_DEVELOP_FLAG_STRUCT, \
           RUNTIME_PD_DEVELOP_FLAG_STRUCT, \
           RUNTIME_PRODUCT_FLAG_STRUCT, \
           RUNTIME_PD_PRODUCT_FLAG_STRUCT, \
           RUNTIME_DIAGNOSTIC_FLAG_STRUCT, \
           RUNTIME_PD_DIAGNOSTIC_FLAG_STRUCT, \
           RUNTIME_EXPERIMENTAL_FLAG_STRUCT, \
           RUNTIME_NOTPRODUCT_FLAG_STRUCT, \
           RUNTIME_MANAGEABLE_FLAG_STRUCT, \
           RUNTIME_PRODUCT_RW_FLAG_STRUCT, \
           RUNTIME_LP64_PRODUCT_FLAG_STRUCT, \
           IGNORE_RANGE, \
           IGNORE_CONSTRAINT, \
           IGNORE_WRITEABLE)

  RUNTIME_OS_FLAGS(RUNTIME_DEVELOP_FLAG_STRUCT, \
                   RUNTIME_PD_DEVELOP_FLAG_STRUCT, \
                   RUNTIME_PRODUCT_FLAG_STRUCT, \
                   RUNTIME_PD_PRODUCT_FLAG_STRUCT, \
                   RUNTIME_DIAGNOSTIC_FLAG_STRUCT, \
                   RUNTIME_PD_DIAGNOSTIC_FLAG_STRUCT, \
                   RUNTIME_NOTPRODUCT_FLAG_STRUCT, \
                   IGNORE_RANGE, \
                   IGNORE_CONSTRAINT, \
                   IGNORE_WRITEABLE)
#if INCLUDE_JVMCI
  JVMCI_FLAGS(JVMCI_DEVELOP_FLAG_STRUCT, \
              JVMCI_PD_DEVELOP_FLAG_STRUCT, \
              JVMCI_PRODUCT_FLAG_STRUCT, \
              JVMCI_PD_PRODUCT_FLAG_STRUCT, \
              JVMCI_DIAGNOSTIC_FLAG_STRUCT, \
              JVMCI_PD_DIAGNOSTIC_FLAG_STRUCT, \
              JVMCI_EXPERIMENTAL_FLAG_STRUCT, \
              JVMCI_NOTPRODUCT_FLAG_STRUCT, \
              IGNORE_RANGE, \
              IGNORE_CONSTRAINT, \
              IGNORE_WRITEABLE)
#endif // INCLUDE_JVMCI
#ifdef COMPILER1
  C1_FLAGS(C1_DEVELOP_FLAG_STRUCT, \
           C1_PD_DEVELOP_FLAG_STRUCT, \
           C1_PRODUCT_FLAG_STRUCT, \
           C1_PD_PRODUCT_FLAG_STRUCT, \
           C1_DIAGNOSTIC_FLAG_STRUCT, \
           C1_PD_DIAGNOSTIC_FLAG_STRUCT, \
           C1_NOTPRODUCT_FLAG_STRUCT, \
           IGNORE_RANGE, \
           IGNORE_CONSTRAINT, \
           IGNORE_WRITEABLE)
#endif // COMPILER1
#ifdef COMPILER2
  C2_FLAGS(C2_DEVELOP_FLAG_STRUCT, \
           C2_PD_DEVELOP_FLAG_STRUCT, \
           C2_PRODUCT_FLAG_STRUCT, \
           C2_PD_PRODUCT_FLAG_STRUCT, \
           C2_DIAGNOSTIC_FLAG_STRUCT, \
           C2_PD_DIAGNOSTIC_FLAG_STRUCT, \
           C2_EXPERIMENTAL_FLAG_STRUCT, \
           C2_NOTPRODUCT_FLAG_STRUCT, \
           IGNORE_RANGE, \
           IGNORE_CONSTRAINT, \
           IGNORE_WRITEABLE)
#endif // COMPILER2
  ARCH_FLAGS(ARCH_DEVELOP_FLAG_STRUCT, \
             ARCH_PRODUCT_FLAG_STRUCT, \
             ARCH_DIAGNOSTIC_FLAG_STRUCT, \
             ARCH_EXPERIMENTAL_FLAG_STRUCT, \
             ARCH_NOTPRODUCT_FLAG_STRUCT, \
             IGNORE_RANGE, \
             IGNORE_CONSTRAINT, \
             IGNORE_WRITEABLE)
  FLAGTABLE_EXT
  {0, NULL, NULL}
};

JVMFlag* JVMFlag::flags = flagTable;
size_t JVMFlag::numFlags = (sizeof(flagTable) / sizeof(JVMFlag));

inline bool str_equal(const char* s, size_t s_len, const char* q, size_t q_len) {
  if (s_len != q_len) return false;
  return memcmp(s, q, q_len) == 0;
}

// Search the flag table for a named flag
JVMFlag* JVMFlag::find_flag(const char* name, size_t length, bool allow_locked, bool return_flag) {
  for (JVMFlag* current = &flagTable[0]; current->_name != NULL; current++) {
    if (str_equal(current->_name, current->get_name_length(), name, length)) {
      // Found a matching entry.
      // Don't report notproduct and develop flags in product builds.
      if (current->is_constant_in_binary()) {
        return (return_flag ? current : NULL);
      }
      // Report locked flags only if allowed.
      if (!(current->is_unlocked() || current->is_unlocker())) {
        if (!allow_locked) {
          // disable use of locked flags, e.g. diagnostic, experimental,
          // etc. until they are explicitly unlocked
          return NULL;
        }
      }
      return current;
    }
  }
  // JVMFlag name is not in the flag table
  return NULL;
}

// Get or compute the flag name length
size_t JVMFlag::get_name_length() {
  if (_name_len == 0) {
    _name_len = strlen(_name);
  }
  return _name_len;
}

JVMFlag* JVMFlag::fuzzy_match(const char* name, size_t length, bool allow_locked) {
  float VMOptionsFuzzyMatchSimilarity = 0.7f;
  JVMFlag* match = NULL;
  float score;
  float max_score = -1;

  for (JVMFlag* current = &flagTable[0]; current->_name != NULL; current++) {
    score = StringUtils::similarity(current->_name, strlen(current->_name), name, length);
    if (score > max_score) {
      max_score = score;
      match = current;
    }
  }

  if (match == NULL) {
    return NULL;
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
static JVMFlag* address_of_flag(JVMFlagsWithType flag) {
  assert((size_t)flag < JVMFlag::numFlags, "bad command line flag index");
  return &JVMFlag::flags[flag];
}

bool JVMFlagEx::is_default(JVMFlags flag) {
  assert((size_t)flag < JVMFlag::numFlags, "bad command line flag index");
  JVMFlag* f = &JVMFlag::flags[flag];
  return f->is_default();
}

bool JVMFlagEx::is_ergo(JVMFlags flag) {
  assert((size_t)flag < JVMFlag::numFlags, "bad command line flag index");
  JVMFlag* f = &JVMFlag::flags[flag];
  return f->is_ergonomic();
}

bool JVMFlagEx::is_cmdline(JVMFlags flag) {
  assert((size_t)flag < JVMFlag::numFlags, "bad command line flag index");
  JVMFlag* f = &JVMFlag::flags[flag];
  return f->is_command_line();
}

bool JVMFlag::wasSetOnCmdline(const char* name, bool* value) {
  JVMFlag* result = JVMFlag::find_flag((char*)name, strlen(name));
  if (result == NULL) return false;
  *value = result->is_command_line();
  return true;
}

void JVMFlagEx::setOnCmdLine(JVMFlagsWithType flag) {
  JVMFlag* faddr = address_of_flag(flag);
  assert(faddr != NULL, "Unknown flag");
  faddr->set_command_line();
}

template<class E, class T>
static void trace_flag_changed(const char* name, const T old_value, const T new_value, const JVMFlag::Flags origin) {
  E e;
  e.set_name(name);
  e.set_oldValue(old_value);
  e.set_newValue(new_value);
  e.set_origin(origin);
  e.commit();
}

static JVMFlag::Error apply_constraint_and_check_range_bool(const char* name, bool new_value, bool verbose) {
  JVMFlag::Error status = JVMFlag::SUCCESS;
  JVMFlagConstraint* constraint = JVMFlagConstraintList::find_if_needs_check(name);
  if (constraint != NULL) {
    status = constraint->apply_bool(new_value, verbose);
  }
  return status;
}

JVMFlag::Error JVMFlag::boolAt(const char* name, size_t len, bool* value, bool allow_locked, bool return_flag) {
  JVMFlag* result = JVMFlag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return JVMFlag::INVALID_FLAG;
  if (!result->is_bool()) return JVMFlag::WRONG_FORMAT;
  *value = result->get_bool();
  return JVMFlag::SUCCESS;
}

JVMFlag::Error JVMFlag::boolAtPut(JVMFlag* flag, bool* value, JVMFlag::Flags origin) {
  const char* name;
  if (flag == NULL) return JVMFlag::INVALID_FLAG;
  if (!flag->is_bool()) return JVMFlag::WRONG_FORMAT;
  name = flag->_name;
  JVMFlag::Error check = apply_constraint_and_check_range_bool(name, *value, !JVMFlagConstraintList::validated_after_ergo());
  if (check != JVMFlag::SUCCESS) return check;
  bool old_value = flag->get_bool();
  trace_flag_changed<EventBooleanFlagChanged, bool>(name, old_value, *value, origin);
  check = flag->set_bool(*value);
  *value = old_value;
  flag->set_origin(origin);
  return check;
}

JVMFlag::Error JVMFlag::boolAtPut(const char* name, size_t len, bool* value, JVMFlag::Flags origin) {
  JVMFlag* result = JVMFlag::find_flag(name, len);
  return boolAtPut(result, value, origin);
}

JVMFlag::Error JVMFlagEx::boolAtPut(JVMFlagsWithType flag, bool value, JVMFlag::Flags origin) {
  JVMFlag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_bool(), "wrong flag type");
  return JVMFlag::boolAtPut(faddr, &value, origin);
}

static JVMFlag::Error apply_constraint_and_check_range_int(const char* name, int new_value, bool verbose) {
  JVMFlag::Error status = JVMFlag::SUCCESS;
  JVMFlagRange* range = JVMFlagRangeList::find(name);
  if (range != NULL) {
    status = range->check_int(new_value, verbose);
  }
  if (status == JVMFlag::SUCCESS) {
    JVMFlagConstraint* constraint = JVMFlagConstraintList::find_if_needs_check(name);
    if (constraint != NULL) {
      status = constraint->apply_int(new_value, verbose);
    }
  }
  return status;
}

JVMFlag::Error JVMFlag::intAt(const char* name, size_t len, int* value, bool allow_locked, bool return_flag) {
  JVMFlag* result = JVMFlag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return JVMFlag::INVALID_FLAG;
  if (!result->is_int()) return JVMFlag::WRONG_FORMAT;
  *value = result->get_int();
  return JVMFlag::SUCCESS;
}

JVMFlag::Error JVMFlag::intAtPut(JVMFlag* flag, int* value, JVMFlag::Flags origin) {
  const char* name;
  if (flag == NULL) return JVMFlag::INVALID_FLAG;
  if (!flag->is_int()) return JVMFlag::WRONG_FORMAT;
  name = flag->_name;
  JVMFlag::Error check = apply_constraint_and_check_range_int(name, *value, !JVMFlagConstraintList::validated_after_ergo());
  if (check != JVMFlag::SUCCESS) return check;
  int old_value = flag->get_int();
  trace_flag_changed<EventIntFlagChanged, s4>(name, old_value, *value, origin);
  check = flag->set_int(*value);
  *value = old_value;
  flag->set_origin(origin);
  return check;
}

JVMFlag::Error JVMFlag::intAtPut(const char* name, size_t len, int* value, JVMFlag::Flags origin) {
  JVMFlag* result = JVMFlag::find_flag(name, len);
  return intAtPut(result, value, origin);
}

JVMFlag::Error JVMFlagEx::intAtPut(JVMFlagsWithType flag, int value, JVMFlag::Flags origin) {
  JVMFlag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_int(), "wrong flag type");
  return JVMFlag::intAtPut(faddr, &value, origin);
}

static JVMFlag::Error apply_constraint_and_check_range_uint(const char* name, uint new_value, bool verbose) {
  JVMFlag::Error status = JVMFlag::SUCCESS;
  JVMFlagRange* range = JVMFlagRangeList::find(name);
  if (range != NULL) {
    status = range->check_uint(new_value, verbose);
  }
  if (status == JVMFlag::SUCCESS) {
    JVMFlagConstraint* constraint = JVMFlagConstraintList::find_if_needs_check(name);
    if (constraint != NULL) {
      status = constraint->apply_uint(new_value, verbose);
    }
  }
  return status;
}

JVMFlag::Error JVMFlag::uintAt(const char* name, size_t len, uint* value, bool allow_locked, bool return_flag) {
  JVMFlag* result = JVMFlag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return JVMFlag::INVALID_FLAG;
  if (!result->is_uint()) return JVMFlag::WRONG_FORMAT;
  *value = result->get_uint();
  return JVMFlag::SUCCESS;
}

JVMFlag::Error JVMFlag::uintAtPut(JVMFlag* flag, uint* value, JVMFlag::Flags origin) {
  const char* name;
  if (flag == NULL) return JVMFlag::INVALID_FLAG;
  if (!flag->is_uint()) return JVMFlag::WRONG_FORMAT;
  name = flag->_name;
  JVMFlag::Error check = apply_constraint_and_check_range_uint(name, *value, !JVMFlagConstraintList::validated_after_ergo());
  if (check != JVMFlag::SUCCESS) return check;
  uint old_value = flag->get_uint();
  trace_flag_changed<EventUnsignedIntFlagChanged, u4>(name, old_value, *value, origin);
  check = flag->set_uint(*value);
  *value = old_value;
  flag->set_origin(origin);
  return check;
}

JVMFlag::Error JVMFlag::uintAtPut(const char* name, size_t len, uint* value, JVMFlag::Flags origin) {
  JVMFlag* result = JVMFlag::find_flag(name, len);
  return uintAtPut(result, value, origin);
}

JVMFlag::Error JVMFlagEx::uintAtPut(JVMFlagsWithType flag, uint value, JVMFlag::Flags origin) {
  JVMFlag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_uint(), "wrong flag type");
  return JVMFlag::uintAtPut(faddr, &value, origin);
}

JVMFlag::Error JVMFlag::intxAt(const char* name, size_t len, intx* value, bool allow_locked, bool return_flag) {
  JVMFlag* result = JVMFlag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return JVMFlag::INVALID_FLAG;
  if (!result->is_intx()) return JVMFlag::WRONG_FORMAT;
  *value = result->get_intx();
  return JVMFlag::SUCCESS;
}

static JVMFlag::Error apply_constraint_and_check_range_intx(const char* name, intx new_value, bool verbose) {
  JVMFlag::Error status = JVMFlag::SUCCESS;
  JVMFlagRange* range = JVMFlagRangeList::find(name);
  if (range != NULL) {
    status = range->check_intx(new_value, verbose);
  }
  if (status == JVMFlag::SUCCESS) {
    JVMFlagConstraint* constraint = JVMFlagConstraintList::find_if_needs_check(name);
    if (constraint != NULL) {
      status = constraint->apply_intx(new_value, verbose);
    }
  }
  return status;
}

JVMFlag::Error JVMFlag::intxAtPut(JVMFlag* flag, intx* value, JVMFlag::Flags origin) {
  const char* name;
  if (flag == NULL) return JVMFlag::INVALID_FLAG;
  if (!flag->is_intx()) return JVMFlag::WRONG_FORMAT;
  name = flag->_name;
  JVMFlag::Error check = apply_constraint_and_check_range_intx(name, *value, !JVMFlagConstraintList::validated_after_ergo());
  if (check != JVMFlag::SUCCESS) return check;
  intx old_value = flag->get_intx();
  trace_flag_changed<EventLongFlagChanged, intx>(name, old_value, *value, origin);
  check = flag->set_intx(*value);
  *value = old_value;
  flag->set_origin(origin);
  return check;
}

JVMFlag::Error JVMFlag::intxAtPut(const char* name, size_t len, intx* value, JVMFlag::Flags origin) {
  JVMFlag* result = JVMFlag::find_flag(name, len);
  return intxAtPut(result, value, origin);
}

JVMFlag::Error JVMFlagEx::intxAtPut(JVMFlagsWithType flag, intx value, JVMFlag::Flags origin) {
  JVMFlag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_intx(), "wrong flag type");
  return JVMFlag::intxAtPut(faddr, &value, origin);
}

JVMFlag::Error JVMFlag::uintxAt(const char* name, size_t len, uintx* value, bool allow_locked, bool return_flag) {
  JVMFlag* result = JVMFlag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return JVMFlag::INVALID_FLAG;
  if (!result->is_uintx()) return JVMFlag::WRONG_FORMAT;
  *value = result->get_uintx();
  return JVMFlag::SUCCESS;
}

static JVMFlag::Error apply_constraint_and_check_range_uintx(const char* name, uintx new_value, bool verbose) {
  JVMFlag::Error status = JVMFlag::SUCCESS;
  JVMFlagRange* range = JVMFlagRangeList::find(name);
  if (range != NULL) {
    status = range->check_uintx(new_value, verbose);
  }
  if (status == JVMFlag::SUCCESS) {
    JVMFlagConstraint* constraint = JVMFlagConstraintList::find_if_needs_check(name);
    if (constraint != NULL) {
      status = constraint->apply_uintx(new_value, verbose);
    }
  }
  return status;
}

JVMFlag::Error JVMFlag::uintxAtPut(JVMFlag* flag, uintx* value, JVMFlag::Flags origin) {
  const char* name;
  if (flag == NULL) return JVMFlag::INVALID_FLAG;
  if (!flag->is_uintx()) return JVMFlag::WRONG_FORMAT;
  name = flag->_name;
  JVMFlag::Error check = apply_constraint_and_check_range_uintx(name, *value, !JVMFlagConstraintList::validated_after_ergo());
  if (check != JVMFlag::SUCCESS) return check;
  uintx old_value = flag->get_uintx();
  trace_flag_changed<EventUnsignedLongFlagChanged, u8>(name, old_value, *value, origin);
  check = flag->set_uintx(*value);
  *value = old_value;
  flag->set_origin(origin);
  return check;
}

JVMFlag::Error JVMFlag::uintxAtPut(const char* name, size_t len, uintx* value, JVMFlag::Flags origin) {
  JVMFlag* result = JVMFlag::find_flag(name, len);
  return uintxAtPut(result, value, origin);
}

JVMFlag::Error JVMFlagEx::uintxAtPut(JVMFlagsWithType flag, uintx value, JVMFlag::Flags origin) {
  JVMFlag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_uintx(), "wrong flag type");
  return JVMFlag::uintxAtPut(faddr, &value, origin);
}

JVMFlag::Error JVMFlag::uint64_tAt(const char* name, size_t len, uint64_t* value, bool allow_locked, bool return_flag) {
  JVMFlag* result = JVMFlag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return JVMFlag::INVALID_FLAG;
  if (!result->is_uint64_t()) return JVMFlag::WRONG_FORMAT;
  *value = result->get_uint64_t();
  return JVMFlag::SUCCESS;
}

static JVMFlag::Error apply_constraint_and_check_range_uint64_t(const char* name, uint64_t new_value, bool verbose) {
  JVMFlag::Error status = JVMFlag::SUCCESS;
  JVMFlagRange* range = JVMFlagRangeList::find(name);
  if (range != NULL) {
    status = range->check_uint64_t(new_value, verbose);
  }
  if (status == JVMFlag::SUCCESS) {
    JVMFlagConstraint* constraint = JVMFlagConstraintList::find_if_needs_check(name);
    if (constraint != NULL) {
      status = constraint->apply_uint64_t(new_value, verbose);
    }
  }
  return status;
}

JVMFlag::Error JVMFlag::uint64_tAtPut(JVMFlag* flag, uint64_t* value, JVMFlag::Flags origin) {
  const char* name;
  if (flag == NULL) return JVMFlag::INVALID_FLAG;
  if (!flag->is_uint64_t()) return JVMFlag::WRONG_FORMAT;
  name = flag->_name;
  JVMFlag::Error check = apply_constraint_and_check_range_uint64_t(name, *value, !JVMFlagConstraintList::validated_after_ergo());
  if (check != JVMFlag::SUCCESS) return check;
  uint64_t old_value = flag->get_uint64_t();
  trace_flag_changed<EventUnsignedLongFlagChanged, u8>(name, old_value, *value, origin);
  check = flag->set_uint64_t(*value);
  *value = old_value;
  flag->set_origin(origin);
  return check;
}

JVMFlag::Error JVMFlag::uint64_tAtPut(const char* name, size_t len, uint64_t* value, JVMFlag::Flags origin) {
  JVMFlag* result = JVMFlag::find_flag(name, len);
  return uint64_tAtPut(result, value, origin);
}

JVMFlag::Error JVMFlagEx::uint64_tAtPut(JVMFlagsWithType flag, uint64_t value, JVMFlag::Flags origin) {
  JVMFlag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_uint64_t(), "wrong flag type");
  return JVMFlag::uint64_tAtPut(faddr, &value, origin);
}

JVMFlag::Error JVMFlag::size_tAt(const char* name, size_t len, size_t* value, bool allow_locked, bool return_flag) {
  JVMFlag* result = JVMFlag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return JVMFlag::INVALID_FLAG;
  if (!result->is_size_t()) return JVMFlag::WRONG_FORMAT;
  *value = result->get_size_t();
  return JVMFlag::SUCCESS;
}

static JVMFlag::Error apply_constraint_and_check_range_size_t(const char* name, size_t new_value, bool verbose) {
  JVMFlag::Error status = JVMFlag::SUCCESS;
  JVMFlagRange* range = JVMFlagRangeList::find(name);
  if (range != NULL) {
    status = range->check_size_t(new_value, verbose);
  }
  if (status == JVMFlag::SUCCESS) {
    JVMFlagConstraint* constraint = JVMFlagConstraintList::find_if_needs_check(name);
    if (constraint != NULL) {
      status = constraint->apply_size_t(new_value, verbose);
    }
  }
  return status;
}


JVMFlag::Error JVMFlag::size_tAtPut(JVMFlag* flag, size_t* value, JVMFlag::Flags origin) {
  const char* name;
  if (flag == NULL) return JVMFlag::INVALID_FLAG;
  if (!flag->is_size_t()) return JVMFlag::WRONG_FORMAT;
  name = flag->_name;
  JVMFlag::Error check = apply_constraint_and_check_range_size_t(name, *value, !JVMFlagConstraintList::validated_after_ergo());
  if (check != JVMFlag::SUCCESS) return check;
  size_t old_value = flag->get_size_t();
  trace_flag_changed<EventUnsignedLongFlagChanged, u8>(name, old_value, *value, origin);
  check = flag->set_size_t(*value);
  *value = old_value;
  flag->set_origin(origin);
  return check;
}

JVMFlag::Error JVMFlag::size_tAtPut(const char* name, size_t len, size_t* value, JVMFlag::Flags origin) {
  JVMFlag* result = JVMFlag::find_flag(name, len);
  return size_tAtPut(result, value, origin);
}

JVMFlag::Error JVMFlagEx::size_tAtPut(JVMFlagsWithType flag, size_t value, JVMFlag::Flags origin) {
  JVMFlag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_size_t(), "wrong flag type");
  return JVMFlag::size_tAtPut(faddr, &value, origin);
}

JVMFlag::Error JVMFlag::doubleAt(const char* name, size_t len, double* value, bool allow_locked, bool return_flag) {
  JVMFlag* result = JVMFlag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return JVMFlag::INVALID_FLAG;
  if (!result->is_double()) return JVMFlag::WRONG_FORMAT;
  *value = result->get_double();
  return JVMFlag::SUCCESS;
}

static JVMFlag::Error apply_constraint_and_check_range_double(const char* name, double new_value, bool verbose) {
  JVMFlag::Error status = JVMFlag::SUCCESS;
  JVMFlagRange* range = JVMFlagRangeList::find(name);
  if (range != NULL) {
    status = range->check_double(new_value, verbose);
  }
  if (status == JVMFlag::SUCCESS) {
    JVMFlagConstraint* constraint = JVMFlagConstraintList::find_if_needs_check(name);
    if (constraint != NULL) {
      status = constraint->apply_double(new_value, verbose);
    }
  }
  return status;
}

JVMFlag::Error JVMFlag::doubleAtPut(JVMFlag* flag, double* value, JVMFlag::Flags origin) {
  const char* name;
  if (flag == NULL) return JVMFlag::INVALID_FLAG;
  if (!flag->is_double()) return JVMFlag::WRONG_FORMAT;
  name = flag->_name;
  JVMFlag::Error check = apply_constraint_and_check_range_double(name, *value, !JVMFlagConstraintList::validated_after_ergo());
  if (check != JVMFlag::SUCCESS) return check;
  double old_value = flag->get_double();
  trace_flag_changed<EventDoubleFlagChanged, double>(name, old_value, *value, origin);
  check = flag->set_double(*value);
  *value = old_value;
  flag->set_origin(origin);
  return check;
}

JVMFlag::Error JVMFlag::doubleAtPut(const char* name, size_t len, double* value, JVMFlag::Flags origin) {
  JVMFlag* result = JVMFlag::find_flag(name, len);
  return doubleAtPut(result, value, origin);
}

JVMFlag::Error JVMFlagEx::doubleAtPut(JVMFlagsWithType flag, double value, JVMFlag::Flags origin) {
  JVMFlag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_double(), "wrong flag type");
  return JVMFlag::doubleAtPut(faddr, &value, origin);
}

JVMFlag::Error JVMFlag::ccstrAt(const char* name, size_t len, ccstr* value, bool allow_locked, bool return_flag) {
  JVMFlag* result = JVMFlag::find_flag(name, len, allow_locked, return_flag);
  if (result == NULL) return JVMFlag::INVALID_FLAG;
  if (!result->is_ccstr()) return JVMFlag::WRONG_FORMAT;
  *value = result->get_ccstr();
  return JVMFlag::SUCCESS;
}

JVMFlag::Error JVMFlag::ccstrAtPut(const char* name, size_t len, ccstr* value, JVMFlag::Flags origin) {
  JVMFlag* result = JVMFlag::find_flag(name, len);
  if (result == NULL) return JVMFlag::INVALID_FLAG;
  if (!result->is_ccstr()) return JVMFlag::WRONG_FORMAT;
  ccstr old_value = result->get_ccstr();
  trace_flag_changed<EventStringFlagChanged, const char*>(name, old_value, *value, origin);
  char* new_value = NULL;
  if (*value != NULL) {
    new_value = os::strdup_check_oom(*value);
  }
  JVMFlag::Error check = result->set_ccstr(new_value);
  if (result->is_default() && old_value != NULL) {
    // Prior value is NOT heap allocated, but was a literal constant.
    old_value = os::strdup_check_oom(old_value);
  }
  *value = old_value;
  result->set_origin(origin);
  return check;
}

JVMFlag::Error JVMFlagEx::ccstrAtPut(JVMFlagsWithType flag, ccstr value, JVMFlag::Flags origin) {
  JVMFlag* faddr = address_of_flag(flag);
  guarantee(faddr != NULL && faddr->is_ccstr(), "wrong flag type");
  ccstr old_value = faddr->get_ccstr();
  trace_flag_changed<EventStringFlagChanged, const char*>(faddr->_name, old_value, value, origin);
  char* new_value = os::strdup_check_oom(value);
  JVMFlag::Error check = faddr->set_ccstr(new_value);
  if (!faddr->is_default() && old_value != NULL) {
    // Prior value is heap allocated so free it.
    FREE_C_HEAP_ARRAY(char, old_value);
  }
  faddr->set_origin(origin);
  return check;
}

extern "C" {
  static int compare_flags(const void* void_a, const void* void_b) {
    return strcmp((*((JVMFlag**) void_a))->_name, (*((JVMFlag**) void_b))->_name);
  }
}

void JVMFlag::printSetFlags(outputStream* out) {
  // Print which flags were set on the command line
  // note: this method is called before the thread structure is in place
  //       which means resource allocation cannot be used.

  // The last entry is the null entry.
  const size_t length = JVMFlag::numFlags - 1;

  // Sort
  JVMFlag** array = NEW_C_HEAP_ARRAY(JVMFlag*, length, mtArguments);
  for (size_t i = 0; i < length; i++) {
    array[i] = &flagTable[i];
  }
  qsort(array, length, sizeof(JVMFlag*), compare_flags);

  // Print
  for (size_t i = 0; i < length; i++) {
    if (array[i]->get_origin() /* naked field! */) {
      array[i]->print_as_flag(out);
      out->print(" ");
    }
  }
  out->cr();
  FREE_C_HEAP_ARRAY(JVMFlag*, array);
}

#ifndef PRODUCT

void JVMFlag::verify() {
  assert(Arguments::check_vm_args_consistency(), "Some flag settings conflict");
}

#endif // PRODUCT

void JVMFlag::printFlags(outputStream* out, bool withComments, bool printRanges, bool skipDefaults) {
  // Print the flags sorted by name
  // note: this method is called before the thread structure is in place
  //       which means resource allocation cannot be used.

  // The last entry is the null entry.
  const size_t length = JVMFlag::numFlags - 1;

  // Sort
  JVMFlag** array = NEW_C_HEAP_ARRAY(JVMFlag*, length, mtArguments);
  for (size_t i = 0; i < length; i++) {
    array[i] = &flagTable[i];
  }
  qsort(array, length, sizeof(JVMFlag*), compare_flags);

  // Print
  if (!printRanges) {
    out->print_cr("[Global flags]");
  } else {
    out->print_cr("[Global flags ranges]");
  }

  for (size_t i = 0; i < length; i++) {
    if (array[i]->is_unlocked() && !(skipDefaults && array[i]->is_default())) {
      array[i]->print_on(out, withComments, printRanges);
    }
  }
  FREE_C_HEAP_ARRAY(JVMFlag*, array);
}

void JVMFlag::printError(bool verbose, const char* msg, ...) {
  if (verbose) {
    va_list listPointer;
    va_start(listPointer, msg);
    jio_vfprintf(defaultStream::error_stream(), msg, listPointer);
    va_end(listPointer);
  }
}

