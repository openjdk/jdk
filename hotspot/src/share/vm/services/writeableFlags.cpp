/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/arguments.hpp"
#include "runtime/java.hpp"
#include "runtime/jniHandles.hpp"
#include "services/writeableFlags.hpp"

// set a boolean global flag
int WriteableFlags::set_bool_flag(const char* name, const char* arg, Flag::Flags origin, FormatBuffer<80>& err_msg) {
  int value = true;

  if (sscanf(arg, "%d", &value)) {
    return set_bool_flag(name, value != 0, origin, err_msg);
  }
  err_msg.print("flag value must be a boolean (1 or 0)");
  return WRONG_FORMAT;
}

int WriteableFlags::set_bool_flag(const char* name, bool value, Flag::Flags origin, FormatBuffer<80>& err_msg) {
  return CommandLineFlags::boolAtPut((char*)name, &value, origin) ? SUCCESS : ERR_OTHER;
}

// set a intx global flag
int WriteableFlags::set_intx_flag(const char* name, const char* arg, Flag::Flags origin, FormatBuffer<80>& err_msg) {
  intx value;

  if (sscanf(arg, INTX_FORMAT, &value)) {
    return set_intx_flag(name, value, origin, err_msg);
  }
  err_msg.print("flag value must be an integer");
  return WRONG_FORMAT;
}

int WriteableFlags::set_intx_flag(const char* name, intx value, Flag::Flags origin, FormatBuffer<80>& err_msg) {
  return CommandLineFlags::intxAtPut((char*)name, &value, origin) ? SUCCESS : ERR_OTHER;
}

// set a uintx global flag
int WriteableFlags::set_uintx_flag(const char* name, const char* arg, Flag::Flags origin, FormatBuffer<80>& err_msg) {
  uintx value;

  if (sscanf(arg, UINTX_FORMAT, &value)) {
    return set_uintx_flag(name, value, origin, err_msg);
  }
  err_msg.print("flag value must be an unsigned integer");
  return WRONG_FORMAT;
}

int WriteableFlags::set_uintx_flag(const char* name, uintx value, Flag::Flags origin, FormatBuffer<80>& err_msg) {
  if (strncmp(name, "MaxHeapFreeRatio", 17) == 0) {
      if (!Arguments::verify_MaxHeapFreeRatio(err_msg, value)) {
        return OUT_OF_BOUNDS;
      }
    } else if (strncmp(name, "MinHeapFreeRatio", 17) == 0) {
      if (!Arguments::verify_MinHeapFreeRatio(err_msg, value)) {
        return OUT_OF_BOUNDS;
      }
    }
    return CommandLineFlags::uintxAtPut((char*)name, &value, origin) ? SUCCESS : ERR_OTHER;
}

// set a uint64_t global flag
int WriteableFlags::set_uint64_t_flag(const char* name, const char* arg, Flag::Flags origin, FormatBuffer<80>& err_msg) {
  uint64_t value;

  if (sscanf(arg, UINT64_FORMAT, &value)) {
    return set_uint64_t_flag(name, value, origin, err_msg);
  }
  err_msg.print("flag value must be an unsigned 64-bit integer");
  return WRONG_FORMAT;
}

int WriteableFlags::set_uint64_t_flag(const char* name, uint64_t value, Flag::Flags origin, FormatBuffer<80>& err_msg) {
  return CommandLineFlags::uint64_tAtPut((char*)name, &value, origin) ? SUCCESS : ERR_OTHER;
}

// set a size_t global flag
int WriteableFlags::set_size_t_flag(const char* name, const char* arg, Flag::Flags origin, FormatBuffer<80>& err_msg) {
  size_t value;

  if (sscanf(arg, SIZE_FORMAT, &value)) {
    return set_size_t_flag(name, value, origin, err_msg);
  }
  err_msg.print("flag value must be an unsigned integer");
  return WRONG_FORMAT;
}

int WriteableFlags::set_size_t_flag(const char* name, size_t value, Flag::Flags origin, FormatBuffer<80>& err_msg) {
  return CommandLineFlags::size_tAtPut((char*)name, &value, origin) ? SUCCESS : ERR_OTHER;
}

// set a string global flag using value from AttachOperation
int WriteableFlags::set_ccstr_flag(const char* name, const char* arg, Flag::Flags origin, FormatBuffer<80>& err_msg) {
  bool res = CommandLineFlags::ccstrAtPut((char*)name, &arg, origin);

  return res? SUCCESS : ERR_OTHER;
}

/* sets a writeable flag to the provided value
 *
 * - return status is one of the WriteableFlags::err enum values
 * - an eventual error message will be generated to the provided err_msg buffer
 */
int WriteableFlags::set_flag(const char* flag_name, const char* flag_value, Flag::Flags origin, FormatBuffer<80>& err_msg) {
  return set_flag(flag_name, &flag_value, set_flag_from_char, origin, err_msg);
}

/* sets a writeable flag to the provided value
 *
 * - return status is one of the WriteableFlags::err enum values
 * - an eventual error message will be generated to the provided err_msg buffer
 */
int WriteableFlags::set_flag(const char* flag_name, jvalue flag_value, Flag::Flags origin, FormatBuffer<80>& err_msg) {
  return set_flag(flag_name, &flag_value, set_flag_from_jvalue, origin, err_msg);
}

// a writeable flag setter accepting either 'jvalue' or 'char *' values
int WriteableFlags::set_flag(const char* name, const void* value, int(*setter)(Flag*,const void*,Flag::Flags,FormatBuffer<80>&), Flag::Flags origin, FormatBuffer<80>& err_msg) {
  if (name == NULL) {
    err_msg.print("flag name is missing");
    return MISSING_NAME;
  }
  if (value == NULL) {
    err_msg.print("flag value is missing");
    return MISSING_VALUE;
  }

  Flag* f = Flag::find_flag((char*)name, strlen(name));
  if (f) {
    // only writeable flags are allowed to be set
    if (f->is_writeable()) {
      return setter(f, value, origin, err_msg);
    } else {
      err_msg.print("only 'writeable' flags can be set");
      return NON_WRITABLE;
    }
  }

  err_msg.print("flag %s does not exist", name);
  return INVALID_FLAG;
}

// a writeable flag setter accepting 'char *' values
int WriteableFlags::set_flag_from_char(Flag* f, const void* value, Flag::Flags origin, FormatBuffer<80>& err_msg) {
  char* flag_value = *(char**)value;
  if (flag_value == NULL) {
    err_msg.print("flag value is missing");
    return MISSING_VALUE;
  }
  if (f->is_bool()) {
    return set_bool_flag(f->_name, flag_value, origin, err_msg);
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
  return ERR_OTHER;
}

// a writeable flag setter accepting 'jvalue' values
int WriteableFlags::set_flag_from_jvalue(Flag* f, const void* value, Flag::Flags origin, FormatBuffer<80>& err_msg) {
  jvalue new_value = *(jvalue*)value;
  if (f->is_bool()) {
    bool bvalue = (new_value.z == JNI_TRUE ? true : false);
    return set_bool_flag(f->_name, bvalue, origin, err_msg);
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
      return MISSING_VALUE;
    }
    ccstr svalue = java_lang_String::as_utf8_string(str);
    int ret = WriteableFlags::set_ccstr_flag(f->_name, svalue, origin, err_msg);
    if (ret != SUCCESS) {
      FREE_C_HEAP_ARRAY(char, svalue);
    }
    return ret;
  } else {
    ShouldNotReachHere();
  }
  return ERR_OTHER;
}

