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

#ifndef SHARE_VM_RUNTIME_FLAGS_JVMFLAG_HPP
#define SHARE_VM_RUNTIME_FLAGS_JVMFLAG_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// function type that will construct default range string
typedef const char* (*RangeStrFunc)(void);

struct JVMFlag {
  enum Flags {
    // latest value origin
    DEFAULT          = 0,
    COMMAND_LINE     = 1,
    ENVIRON_VAR      = 2,
    CONFIG_FILE      = 3,
    MANAGEMENT       = 4,
    ERGONOMIC        = 5,
    ATTACH_ON_DEMAND = 6,
    INTERNAL         = 7,

    LAST_VALUE_ORIGIN = INTERNAL,
    VALUE_ORIGIN_BITS = 4,
    VALUE_ORIGIN_MASK = right_n_bits(VALUE_ORIGIN_BITS),

    // flag kind
    KIND_PRODUCT            = 1 << 4,
    KIND_MANAGEABLE         = 1 << 5,
    KIND_DIAGNOSTIC         = 1 << 6,
    KIND_EXPERIMENTAL       = 1 << 7,
    KIND_NOT_PRODUCT        = 1 << 8,
    KIND_DEVELOP            = 1 << 9,
    KIND_PLATFORM_DEPENDENT = 1 << 10,
    KIND_READ_WRITE         = 1 << 11,
    KIND_C1                 = 1 << 12,
    KIND_C2                 = 1 << 13,
    KIND_ARCH               = 1 << 14,
    KIND_LP64_PRODUCT       = 1 << 15,
    KIND_JVMCI              = 1 << 16,

    // set this bit if the flag was set on the command line
    ORIG_COMMAND_LINE       = 1 << 17,

    KIND_MASK = ~(VALUE_ORIGIN_MASK | ORIG_COMMAND_LINE)
  };

  enum Error {
    // no error
    SUCCESS = 0,
    // flag name is missing
    MISSING_NAME,
    // flag value is missing
    MISSING_VALUE,
    // error parsing the textual form of the value
    WRONG_FORMAT,
    // flag is not writable
    NON_WRITABLE,
    // flag value is outside of its bounds
    OUT_OF_BOUNDS,
    // flag value violates its constraint
    VIOLATES_CONSTRAINT,
    // there is no flag with the given name
    INVALID_FLAG,
    // the flag can only be set only on command line during invocation of the VM
    COMMAND_LINE_ONLY,
    // the flag may only be set once
    SET_ONLY_ONCE,
    // the flag is not writable in this combination of product/debug build
    CONSTANT,
    // other, unspecified error related to setting the flag
    ERR_OTHER
  };

  enum MsgType {
    NONE = 0,
    DIAGNOSTIC_FLAG_BUT_LOCKED,
    EXPERIMENTAL_FLAG_BUT_LOCKED,
    DEVELOPER_FLAG_BUT_PRODUCT_BUILD,
    NOTPRODUCT_FLAG_BUT_PRODUCT_BUILD
  };

  const char* _type;
  const char* _name;
  void* _addr;
  NOT_PRODUCT(const char* _doc;)
  Flags _flags;
  size_t _name_len;

  // points to all Flags static array
  static JVMFlag* flags;

  // number of flags
  static size_t numFlags;

  static JVMFlag* find_flag(const char* name) { return find_flag(name, strlen(name), true, true); };
  static JVMFlag* find_flag(const char* name, size_t length, bool allow_locked = false, bool return_flag = false);
  static JVMFlag* fuzzy_match(const char* name, size_t length, bool allow_locked = false);

  static const char* get_int_default_range_str();
  static const char* get_uint_default_range_str();
  static const char* get_intx_default_range_str();
  static const char* get_uintx_default_range_str();
  static const char* get_uint64_t_default_range_str();
  static const char* get_size_t_default_range_str();
  static const char* get_double_default_range_str();

  JVMFlag::Error check_writable(bool changed);

  bool is_bool() const;
  bool get_bool() const;
  JVMFlag::Error set_bool(bool value);

  bool is_int() const;
  int get_int() const;
  JVMFlag::Error set_int(int value);

  bool is_uint() const;
  uint get_uint() const;
  JVMFlag::Error set_uint(uint value);

  bool is_intx() const;
  intx get_intx() const;
  JVMFlag::Error set_intx(intx value);

  bool is_uintx() const;
  uintx get_uintx() const;
  JVMFlag::Error set_uintx(uintx value);

  bool is_uint64_t() const;
  uint64_t get_uint64_t() const;
  JVMFlag::Error set_uint64_t(uint64_t value);

  bool is_size_t() const;
  size_t get_size_t() const;
  JVMFlag::Error set_size_t(size_t value);

  bool is_double() const;
  double get_double() const;
  JVMFlag::Error set_double(double value);

  bool is_ccstr() const;
  bool ccstr_accumulates() const;
  ccstr get_ccstr() const;
  JVMFlag::Error set_ccstr(ccstr value);

  Flags get_origin();
  void set_origin(Flags origin);

  size_t get_name_length();

  bool is_default();
  bool is_ergonomic();
  bool is_command_line();
  void set_command_line();

  bool is_product() const;
  bool is_manageable() const;
  bool is_diagnostic() const;
  bool is_experimental() const;
  bool is_notproduct() const;
  bool is_develop() const;
  bool is_read_write() const;

  bool is_constant_in_binary() const;

  bool is_unlocker() const;
  bool is_unlocked() const;
  bool is_writeable() const;
  bool is_external() const;

  bool is_unlocker_ext() const;
  bool is_unlocked_ext() const;
  bool is_writeable_ext() const;
  bool is_external_ext() const;

  void clear_diagnostic();

  JVMFlag::MsgType get_locked_message(char*, int) const;
  JVMFlag::MsgType get_locked_message_ext(char*, int) const;

  // printRanges will print out flags type, name and range values as expected by -XX:+PrintFlagsRanges
  void print_on(outputStream* st, bool withComments = false, bool printRanges = false);
  void print_kind(outputStream* st, unsigned int width);
  void print_origin(outputStream* st, unsigned int width);
  void print_as_flag(outputStream* st);

  static const char* flag_error_str(JVMFlag::Error error);

public:
  static JVMFlag::Error boolAt(const char* name, size_t len, bool* value, bool allow_locked = false, bool return_flag = false);
  static JVMFlag::Error boolAt(const char* name, bool* value, bool allow_locked = false, bool return_flag = false)      { return boolAt(name, strlen(name), value, allow_locked, return_flag); }
  static JVMFlag::Error boolAtPut(JVMFlag* flag, bool* value, JVMFlag::Flags origin);
  static JVMFlag::Error boolAtPut(const char* name, size_t len, bool* value, JVMFlag::Flags origin);
  static JVMFlag::Error boolAtPut(const char* name, bool* value, JVMFlag::Flags origin)   { return boolAtPut(name, strlen(name), value, origin); }

  static JVMFlag::Error intAt(const char* name, size_t len, int* value, bool allow_locked = false, bool return_flag = false);
  static JVMFlag::Error intAt(const char* name, int* value, bool allow_locked = false, bool return_flag = false)      { return intAt(name, strlen(name), value, allow_locked, return_flag); }
  static JVMFlag::Error intAtPut(JVMFlag* flag, int* value, JVMFlag::Flags origin);
  static JVMFlag::Error intAtPut(const char* name, size_t len, int* value, JVMFlag::Flags origin);
  static JVMFlag::Error intAtPut(const char* name, int* value, JVMFlag::Flags origin)   { return intAtPut(name, strlen(name), value, origin); }

  static JVMFlag::Error uintAt(const char* name, size_t len, uint* value, bool allow_locked = false, bool return_flag = false);
  static JVMFlag::Error uintAt(const char* name, uint* value, bool allow_locked = false, bool return_flag = false)      { return uintAt(name, strlen(name), value, allow_locked, return_flag); }
  static JVMFlag::Error uintAtPut(JVMFlag* flag, uint* value, JVMFlag::Flags origin);
  static JVMFlag::Error uintAtPut(const char* name, size_t len, uint* value, JVMFlag::Flags origin);
  static JVMFlag::Error uintAtPut(const char* name, uint* value, JVMFlag::Flags origin)   { return uintAtPut(name, strlen(name), value, origin); }

  static JVMFlag::Error intxAt(const char* name, size_t len, intx* value, bool allow_locked = false, bool return_flag = false);
  static JVMFlag::Error intxAt(const char* name, intx* value, bool allow_locked = false, bool return_flag = false)      { return intxAt(name, strlen(name), value, allow_locked, return_flag); }
  static JVMFlag::Error intxAtPut(JVMFlag* flag, intx* value, JVMFlag::Flags origin);
  static JVMFlag::Error intxAtPut(const char* name, size_t len, intx* value, JVMFlag::Flags origin);
  static JVMFlag::Error intxAtPut(const char* name, intx* value, JVMFlag::Flags origin)   { return intxAtPut(name, strlen(name), value, origin); }

  static JVMFlag::Error uintxAt(const char* name, size_t len, uintx* value, bool allow_locked = false, bool return_flag = false);
  static JVMFlag::Error uintxAt(const char* name, uintx* value, bool allow_locked = false, bool return_flag = false)    { return uintxAt(name, strlen(name), value, allow_locked, return_flag); }
  static JVMFlag::Error uintxAtPut(JVMFlag* flag, uintx* value, JVMFlag::Flags origin);
  static JVMFlag::Error uintxAtPut(const char* name, size_t len, uintx* value, JVMFlag::Flags origin);
  static JVMFlag::Error uintxAtPut(const char* name, uintx* value, JVMFlag::Flags origin) { return uintxAtPut(name, strlen(name), value, origin); }

  static JVMFlag::Error size_tAt(const char* name, size_t len, size_t* value, bool allow_locked = false, bool return_flag = false);
  static JVMFlag::Error size_tAt(const char* name, size_t* value, bool allow_locked = false, bool return_flag = false)    { return size_tAt(name, strlen(name), value, allow_locked, return_flag); }
  static JVMFlag::Error size_tAtPut(JVMFlag* flag, size_t* value, JVMFlag::Flags origin);
  static JVMFlag::Error size_tAtPut(const char* name, size_t len, size_t* value, JVMFlag::Flags origin);
  static JVMFlag::Error size_tAtPut(const char* name, size_t* value, JVMFlag::Flags origin) { return size_tAtPut(name, strlen(name), value, origin); }

  static JVMFlag::Error uint64_tAt(const char* name, size_t len, uint64_t* value, bool allow_locked = false, bool return_flag = false);
  static JVMFlag::Error uint64_tAt(const char* name, uint64_t* value, bool allow_locked = false, bool return_flag = false) { return uint64_tAt(name, strlen(name), value, allow_locked, return_flag); }
  static JVMFlag::Error uint64_tAtPut(JVMFlag* flag, uint64_t* value, JVMFlag::Flags origin);
  static JVMFlag::Error uint64_tAtPut(const char* name, size_t len, uint64_t* value, JVMFlag::Flags origin);
  static JVMFlag::Error uint64_tAtPut(const char* name, uint64_t* value, JVMFlag::Flags origin) { return uint64_tAtPut(name, strlen(name), value, origin); }

  static JVMFlag::Error doubleAt(const char* name, size_t len, double* value, bool allow_locked = false, bool return_flag = false);
  static JVMFlag::Error doubleAt(const char* name, double* value, bool allow_locked = false, bool return_flag = false)    { return doubleAt(name, strlen(name), value, allow_locked, return_flag); }
  static JVMFlag::Error doubleAtPut(JVMFlag* flag, double* value, JVMFlag::Flags origin);
  static JVMFlag::Error doubleAtPut(const char* name, size_t len, double* value, JVMFlag::Flags origin);
  static JVMFlag::Error doubleAtPut(const char* name, double* value, JVMFlag::Flags origin) { return doubleAtPut(name, strlen(name), value, origin); }

  static JVMFlag::Error ccstrAt(const char* name, size_t len, ccstr* value, bool allow_locked = false, bool return_flag = false);
  static JVMFlag::Error ccstrAt(const char* name, ccstr* value, bool allow_locked = false, bool return_flag = false)    { return ccstrAt(name, strlen(name), value, allow_locked, return_flag); }
  // Contract:  JVMFlag will make private copy of the incoming value.
  // Outgoing value is always malloc-ed, and caller MUST call free.
  static JVMFlag::Error ccstrAtPut(const char* name, size_t len, ccstr* value, JVMFlag::Flags origin);
  static JVMFlag::Error ccstrAtPut(const char* name, ccstr* value, JVMFlag::Flags origin) { return ccstrAtPut(name, strlen(name), value, origin); }

  // Returns false if name is not a command line flag.
  static bool wasSetOnCmdline(const char* name, bool* value);
  static void printSetFlags(outputStream* out);

  // printRanges will print out flags type, name and range values as expected by -XX:+PrintFlagsRanges
  static void printFlags(outputStream* out, bool withComments, bool printRanges = false, bool skipDefaults = false);
  static void printError(bool verbose, const char* msg, ...);

  static void verify() PRODUCT_RETURN;
};

#endif // SHARE_VM_RUNTIME_FLAGS_JVMFLAG_HPP
