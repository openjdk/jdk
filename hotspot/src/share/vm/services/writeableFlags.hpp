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

#ifndef SHARE_VM_SERVICES_WRITEABLEFLAG_HPP
#define SHARE_VM_SERVICES_WRITEABLEFLAG_HPP

class WriteableFlags : AllStatic {
public:
  enum error {
    // no error
    SUCCESS,
    // flag name is missing
    MISSING_NAME,
    // flag value is missing
    MISSING_VALUE,
    // error parsing the textual form of the value
    WRONG_FORMAT,
    // flag is not writeable
    NON_WRITABLE,
    // flag value is outside of its bounds
    OUT_OF_BOUNDS,
    // there is no flag with the given name
    INVALID_FLAG,
    // other, unspecified error related to setting the flag
    ERR_OTHER
  } err;

private:
  // a writeable flag setter accepting either 'jvalue' or 'char *' values
  static int set_flag(const char* name, const void* value, int(*setter)(Flag*, const void*, Flag::Flags, FormatBuffer<80>&), Flag::Flags origin, FormatBuffer<80>& err_msg);
  // a writeable flag setter accepting 'char *' values
  static int set_flag_from_char(Flag* f, const void* value, Flag::Flags origin, FormatBuffer<80>& err_msg);
  // a writeable flag setter accepting 'jvalue' values
  static int set_flag_from_jvalue(Flag* f, const void* value, Flag::Flags origin, FormatBuffer<80>& err_msg);

  // set a boolean global flag
  static int set_bool_flag(const char* name, const char* value, Flag::Flags origin, FormatBuffer<80>& err_msg);
  // set a intx global flag
  static int set_intx_flag(const char* name, const char* value, Flag::Flags origin, FormatBuffer<80>& err_msg);
  // set a uintx global flag
  static int set_uintx_flag(const char* name, const char* value, Flag::Flags origin, FormatBuffer<80>& err_msg);
  // set a uint64_t global flag
  static int set_uint64_t_flag(const char* name, const char* value, Flag::Flags origin, FormatBuffer<80>& err_msg);
  // set a size_t global flag using value from AttachOperation
  static int set_size_t_flag(const char* name, const char* value, Flag::Flags origin, FormatBuffer<80>& err_msg);
  // set a boolean global flag
  static int set_bool_flag(const char* name, bool value, Flag::Flags origin, FormatBuffer<80>& err_msg);
  // set a intx global flag
  static int set_intx_flag(const char* name, intx value, Flag::Flags origin, FormatBuffer<80>& err_msg);
  // set a uintx global flag
  static int set_uintx_flag(const char* name, uintx value, Flag::Flags origin, FormatBuffer<80>& err_msg);
  // set a uint64_t global flag
  static int set_uint64_t_flag(const char* name, uint64_t value, Flag::Flags origin, FormatBuffer<80>& err_msg);
  // set a size_t global flag using value from AttachOperation
  static int set_size_t_flag(const char* name, size_t value, Flag::Flags origin, FormatBuffer<80>& err_msg);
  // set a string global flag
  static int set_ccstr_flag(const char* name, const char* value, Flag::Flags origin, FormatBuffer<80>& err_msg);

public:
  /* sets a writeable flag to the provided value
   *
   * - return status is one of the WriteableFlags::err enum values
   * - an eventual error message will be generated to the provided err_msg buffer
   */
  static int set_flag(const char* flag_name, const char* flag_value, Flag::Flags origin, FormatBuffer<80>& err_msg);

  /* sets a writeable flag to the provided value
   *
   * - return status is one of the WriteableFlags::err enum values
   * - an eventual error message will be generated to the provided err_msg buffer
   */
  static int set_flag(const char* flag_name, jvalue flag_value, Flag::Flags origin, FormatBuffer<80>& err_msg);
};

#endif /* SHARE_VM_SERVICES_WRITEABLEFLAG_HPP */

