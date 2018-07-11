/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/arguments.hpp"
#include "runtime/flags/jvmFlag.hpp"
#include "runtime/flags/jvmFlagRangeList.hpp"
#include "runtime/java.hpp"
#include "runtime/jniHandles.hpp"
#include "services/writeableFlags.hpp"

#define TEMP_BUF_SIZE 80

static void buffer_concat(char* buffer, const char* src) {
  strncat(buffer, src, TEMP_BUF_SIZE - 1 - strlen(buffer));
}

static void print_flag_error_message_bounds(const char* name, char* buffer) {
  JVMFlagRange* range = JVMFlagRangeList::find(name);
  if (range != NULL) {
    buffer_concat(buffer, "must have value in range ");

    stringStream stream;
    range->print(&stream);
    const char* range_string = stream.as_string();
    size_t j = strlen(buffer);
    for (size_t i=0; j<TEMP_BUF_SIZE-1; i++) {
      if (range_string[i] == '\0') {
        break;
      } else if (range_string[i] != ' ') {
        buffer[j] = range_string[i];
        j++;
      }
    }
    buffer[j] = '\0';
  }
}

static void print_flag_error_message_if_needed(JVMFlag::Error error, const char* name, FormatBuffer<80>& err_msg) {
  if (error == JVMFlag::SUCCESS) {
    return;
  }

  char buffer[TEMP_BUF_SIZE] = {'\0'};
  if ((error != JVMFlag::MISSING_NAME) && (name != NULL)) {
    buffer_concat(buffer, name);
    buffer_concat(buffer, " error: ");
  } else {
    buffer_concat(buffer, "Error: ");
  }
  switch (error) {
    case JVMFlag::MISSING_NAME:
      buffer_concat(buffer, "flag name is missing."); break;
    case JVMFlag::MISSING_VALUE:
      buffer_concat(buffer, "parsing the textual form of the value."); break;
    case JVMFlag::NON_WRITABLE:
      buffer_concat(buffer, "flag is not writeable."); break;
    case JVMFlag::OUT_OF_BOUNDS:
      if (name != NULL) { print_flag_error_message_bounds(name, buffer); } break;
    case JVMFlag::VIOLATES_CONSTRAINT:
      buffer_concat(buffer, "value violates its flag's constraint."); break;
    case JVMFlag::INVALID_FLAG:
      buffer_concat(buffer, "there is no flag with the given name."); break;
    case JVMFlag::ERR_OTHER:
      buffer_concat(buffer, "other, unspecified error related to setting the flag."); break;
    case JVMFlag::SUCCESS:
      break;
    default:
      break;
  }

  err_msg.print("%s", buffer);
}

// set a boolean global flag
JVMFlag::Error WriteableFlags::set_bool_flag(const char* name, const char* arg, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  if ((strcasecmp(arg, "true") == 0) || (*arg == '1' && *(arg + 1) == 0)) {
    return set_bool_flag(name, true, origin, err_msg);
  } else if ((strcasecmp(arg, "false") == 0) || (*arg == '0' && *(arg + 1) == 0)) {
    return set_bool_flag(name, false, origin, err_msg);
  }
  err_msg.print("flag value must be a boolean (1/0 or true/false)");
  return JVMFlag::WRONG_FORMAT;
}

JVMFlag::Error WriteableFlags::set_bool_flag(const char* name, bool value, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  JVMFlag::Error err = JVMFlag::boolAtPut(name, &value, origin);
  print_flag_error_message_if_needed(err, name, err_msg);
  return err;
}

// set a int global flag
JVMFlag::Error WriteableFlags::set_int_flag(const char* name, const char* arg, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  int value;

  if (sscanf(arg, "%d", &value)) {
    return set_int_flag(name, value, origin, err_msg);
  }
  err_msg.print("flag value must be an integer");
  return JVMFlag::WRONG_FORMAT;
}

JVMFlag::Error WriteableFlags::set_int_flag(const char* name, int value, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  JVMFlag::Error err = JVMFlag::intAtPut(name, &value, origin);
  print_flag_error_message_if_needed(err, name, err_msg);
  return err;
}

// set a uint global flag
JVMFlag::Error WriteableFlags::set_uint_flag(const char* name, const char* arg, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  uint value;

  if (sscanf(arg, "%u", &value)) {
    return set_uint_flag(name, value, origin, err_msg);
  }
  err_msg.print("flag value must be an unsigned integer");
  return JVMFlag::WRONG_FORMAT;
}

JVMFlag::Error WriteableFlags::set_uint_flag(const char* name, uint value, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  JVMFlag::Error err = JVMFlag::uintAtPut(name, &value, origin);
  print_flag_error_message_if_needed(err, name, err_msg);
  return err;
}

// set a intx global flag
JVMFlag::Error WriteableFlags::set_intx_flag(const char* name, const char* arg, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  intx value;

  if (sscanf(arg, INTX_FORMAT, &value)) {
    return set_intx_flag(name, value, origin, err_msg);
  }
  err_msg.print("flag value must be an integer");
  return JVMFlag::WRONG_FORMAT;
}

JVMFlag::Error WriteableFlags::set_intx_flag(const char* name, intx value, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  JVMFlag::Error err = JVMFlag::intxAtPut(name, &value, origin);
  print_flag_error_message_if_needed(err, name, err_msg);
  return err;
}

// set a uintx global flag
JVMFlag::Error WriteableFlags::set_uintx_flag(const char* name, const char* arg, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  uintx value;

  if (sscanf(arg, UINTX_FORMAT, &value)) {
    return set_uintx_flag(name, value, origin, err_msg);
  }
  err_msg.print("flag value must be an unsigned integer");
  return JVMFlag::WRONG_FORMAT;
}

JVMFlag::Error WriteableFlags::set_uintx_flag(const char* name, uintx value, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  JVMFlag::Error err = JVMFlag::uintxAtPut(name, &value, origin);
  print_flag_error_message_if_needed(err, name, err_msg);
  return err;
}

// set a uint64_t global flag
JVMFlag::Error WriteableFlags::set_uint64_t_flag(const char* name, const char* arg, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  uint64_t value;

  if (sscanf(arg, UINT64_FORMAT, &value)) {
    return set_uint64_t_flag(name, value, origin, err_msg);
  }
  err_msg.print("flag value must be an unsigned 64-bit integer");
  return JVMFlag::WRONG_FORMAT;
}

JVMFlag::Error WriteableFlags::set_uint64_t_flag(const char* name, uint64_t value, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  JVMFlag::Error err = JVMFlag::uint64_tAtPut(name, &value, origin);
  print_flag_error_message_if_needed(err, name, err_msg);
  return err;
}

// set a size_t global flag
JVMFlag::Error WriteableFlags::set_size_t_flag(const char* name, const char* arg, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  size_t value;

  if (sscanf(arg, SIZE_FORMAT, &value)) {
    return set_size_t_flag(name, value, origin, err_msg);
  }
  err_msg.print("flag value must be an unsigned integer");
  return JVMFlag::WRONG_FORMAT;
}

JVMFlag::Error WriteableFlags::set_size_t_flag(const char* name, size_t value, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  JVMFlag::Error err = JVMFlag::size_tAtPut(name, &value, origin);
  print_flag_error_message_if_needed(err, name, err_msg);
  return err;
}

// set a string global flag using value from AttachOperation
JVMFlag::Error WriteableFlags::set_ccstr_flag(const char* name, const char* value, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  JVMFlag::Error err = JVMFlag::ccstrAtPut((char*)name, &value, origin);
  print_flag_error_message_if_needed(err, name, err_msg);
  return err;
}

/* sets a writeable flag to the provided value
 *
 * - return status is one of the WriteableFlags::err enum values
 * - an eventual error message will be generated to the provided err_msg buffer
 */
JVMFlag::Error WriteableFlags::set_flag(const char* flag_name, const char* flag_value, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  return set_flag(flag_name, &flag_value, set_flag_from_char, origin, err_msg);
}

/* sets a writeable flag to the provided value
 *
 * - return status is one of the WriteableFlags::err enum values
 * - an eventual error message will be generated to the provided err_msg buffer
 */
JVMFlag::Error WriteableFlags::set_flag(const char* flag_name, jvalue flag_value, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  return set_flag(flag_name, &flag_value, set_flag_from_jvalue, origin, err_msg);
}

// a writeable flag setter accepting either 'jvalue' or 'char *' values
JVMFlag::Error WriteableFlags::set_flag(const char* name, const void* value, JVMFlag::Error(*setter)(JVMFlag*,const void*,JVMFlag::Flags,FormatBuffer<80>&), JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  if (name == NULL) {
    err_msg.print("flag name is missing");
    return JVMFlag::MISSING_NAME;
  }
  if (value == NULL) {
    err_msg.print("flag value is missing");
    return JVMFlag::MISSING_VALUE;
  }

  JVMFlag* f = JVMFlag::find_flag((char*)name, strlen(name));
  if (f) {
    // only writeable flags are allowed to be set
    if (f->is_writeable()) {
      return setter(f, value, origin, err_msg);
    } else {
      err_msg.print("only 'writeable' flags can be set");
      return JVMFlag::NON_WRITABLE;
    }
  }

  err_msg.print("flag %s does not exist", name);
  return JVMFlag::INVALID_FLAG;
}

// a writeable flag setter accepting 'char *' values
JVMFlag::Error WriteableFlags::set_flag_from_char(JVMFlag* f, const void* value, JVMFlag::Flags origin, FormatBuffer<80>& err_msg) {
  char* flag_value = *(char**)value;
  if (flag_value == NULL) {
    err_msg.print("flag value is missing");
    return JVMFlag::MISSING_VALUE;
  }
  if (f->is_bool()) {
    return set_bool_flag(f->_name, flag_value, origin, err_msg);
  } else if (f->is_int()) {
    return set_int_flag(f->_name, flag_value, origin, err_msg);
  } else if (f->is_uint()) {
    return set_uint_flag(f->_name, flag_value, origin, err_msg);
  } else if (f->is_intx()) {
    return set_intx_flag(f->_name, flag_value, origin, err_msg);
  } else if (f->is_uintx()) {
    return set_uintx_flag(f->_name, flag_value, origin, err_msg);
  } else if (f->is_uint64_t()) {
    return set_uint64_t_flag(f->_name, flag_value, origin, err_msg);
  } else if (f->is_size_t()) {
    return set_size_t_flag(f->_name, flag_value, origin, err_msg);
  } else if (f->is_ccstr()) {
    return set_ccstr_flag(f->_name, flag_value, origin, err_msg);
  } else {
    ShouldNotReachHere();
  }
  return JVMFlag::ERR_OTHER;
}

// a writeable flag setter accepting 'jvalue' values
JVMFlag::Error WriteableFlags::set_flag_from_jvalue(JVMFlag* f, const void* value, JVMFlag::Flags origin,
                                                 FormatBuffer<80>& err_msg) {
  jvalue new_value = *(jvalue*)value;
  if (f->is_bool()) {
    bool bvalue = (new_value.z == JNI_TRUE ? true : false);
    return set_bool_flag(f->_name, bvalue, origin, err_msg);
  } else if (f->is_int()) {
    int ivalue = (int)new_value.j;
    return set_int_flag(f->_name, ivalue, origin, err_msg);
  } else if (f->is_uint()) {
    uint uvalue = (uint)new_value.j;
    return set_uint_flag(f->_name, uvalue, origin, err_msg);
  } else if (f->is_intx()) {
    intx ivalue = (intx)new_value.j;
    return set_intx_flag(f->_name, ivalue, origin, err_msg);
  } else if (f->is_uintx()) {
    uintx uvalue = (uintx)new_value.j;
    return set_uintx_flag(f->_name, uvalue, origin, err_msg);
  } else if (f->is_uint64_t()) {
    uint64_t uvalue = (uint64_t)new_value.j;
    return set_uint64_t_flag(f->_name, uvalue, origin, err_msg);
  } else if (f->is_size_t()) {
    size_t svalue = (size_t)new_value.j;
    return set_size_t_flag(f->_name, svalue, origin, err_msg);
  } else if (f->is_ccstr()) {
    oop str = JNIHandles::resolve_external_guard(new_value.l);
    if (str == NULL) {
      err_msg.print("flag value is missing");
      return JVMFlag::MISSING_VALUE;
    }
    ccstr svalue = java_lang_String::as_utf8_string(str);
    JVMFlag::Error ret = WriteableFlags::set_ccstr_flag(f->_name, svalue, origin, err_msg);
    if (ret != JVMFlag::SUCCESS) {
      FREE_C_HEAP_ARRAY(char, svalue);
    }
    return ret;
  } else {
    ShouldNotReachHere();
  }
  return JVMFlag::ERR_OTHER;
}
