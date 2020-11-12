/*
 * Copyright (c) 1998, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_COMPILER_COMPILERORACLE_HPP
#define SHARE_COMPILER_COMPILERORACLE_HPP

#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"

class methodHandle;


// CompilerOracle is an interface for turning on and off compilation
// for some methods

//       OPTION_TYPES: type, store type, name
#define OPTION_TYPES(type) \
  type(Intx, Intx, "intx") \
  type(Uintx, Uintx, "uintx") \
  type(Bool, Bool, "bool") \
  type(Ccstr, Ccstr, "ccstr") \
  type(Ccstrlist, Ccstr, "ccstrlist") \
  type(Double, Double, "double")

// Basic option:    break,<method pattern>
// Trivial option:  quiet
// Legacy option:   option,<method pattern>,type,<option name>,<value>
// Standard option: <option name>,<method pattern>,<value>

//       COMPILECOMMAND_OPTIONS: option, name, variant, type
#define COMPILECOMMAND_OPTIONS(option) \
  option(Help,  "help", Trivial, Unknown) \
  option(Quiet, "quiet", Trivial, Unknown) \
  option(Break, "break", Basic, Bool) \
  option(Print, "print", Basic, Bool) \
  option(Exclude, "exclude", Basic, Bool) \
  option(Inline,  "inline", Basic, Bool) \
  option(DontInline,  "dontinline", Basic, Bool) \
  option(CompileOnly, "compileonly", Basic, Bool) \
  option(Log, "log", Basic, Bool) \
  option(CompileThresholdScaling, "CompileThresholdScaling", Standard, Double) \
  option(ControlIntrinsic,  "ControlIntrinsic",  Standard, Ccstrlist) \
  option(DisableIntrinsic,  "DisableIntrinsic",  Standard, Ccstrlist) \
  option(NoRTMLockEliding,  "NoRTMLockEliding",  Standard, Bool) \
  option(UseRTMLockEliding, "UseRTMLockEliding", Standard, Bool) \
  option(PrintDebugInfo,    "PrintDebugInfo",    Standard, Bool) \
  option(PrintRelocations,  "PrintRelocations",  Standard, Bool) \
  option(PrintDependencies, "PrintDependencies", Standard, Bool) \
NOT_PRODUCT(option(TestOptionInt,    "TestOptionInt",    Standard, Intx)) \
NOT_PRODUCT(option(TestOptionUint,   "TestOptionUint",   Standard, Uintx)) \
NOT_PRODUCT(option(TestOptionBool,   "TestOptionBool",   Standard, Bool)) \
NOT_PRODUCT(option(TestOptionBool2,  "TestOptionBool2",   Standard, Bool)) \
NOT_PRODUCT(option(TestOptionStr,    "TestOptionStr",    Standard, Ccstr)) \
NOT_PRODUCT(option(TestOptionList,   "TestOptionList",   Standard, Ccstrlist)) \
NOT_PRODUCT(option(TestOptionDouble, "TestOptionDouble", Standard, Double)) \
  option(Option, "option", Legacy, Unknown)

enum class CompileCommand {
  Unknown = -1,
  #define enum_of_options(option, name, cvariant, ctype) option,
    COMPILECOMMAND_OPTIONS(enum_of_options)
  #undef enum_of_options
  Count
};

static const char * command_names[] = {
  #define enum_of_options(option, name, cvariant, ctype) name,
    COMPILECOMMAND_OPTIONS(enum_of_options)
  #undef enum_of_options
};

enum class CompileCommandVariant {
  Trivial,
  Basic,
  Legacy,
  Standard
};

static enum CompileCommandVariant command2variant[] = {
#define enum_of_options(option, name, cvariant, ctype) CompileCommandVariant::cvariant,
        COMPILECOMMAND_OPTIONS(enum_of_options)
#undef enum_of_options
};

class CompilerOracle : AllStatic {
 private:
  static bool _quiet;
  static void print_tip();
  static void print_commands();
  static void print_parse_error(const char*&  error_msg, char* original_line);

 public:

  // True if the command file has been specified or is implicit
  static bool has_command_file();

  // Reads from file and adds to lists
  static void parse_from_file();

  // Tells whether we to exclude compilation of method
  static bool should_exclude(const methodHandle& method);
  static bool should_exclude_quietly() { return _quiet; }

  // Tells whether we want to inline this method
  static bool should_inline(const methodHandle& method);

  // Tells whether we want to disallow inlining of this method
  static bool should_not_inline(const methodHandle& method);

  // Tells whether we should print the assembly for this method
  static bool should_print(const methodHandle& method);

  // Tells whether we should log the compilation data for this method
  static bool should_log(const methodHandle& method);

  // Tells whether to break when compiling method
  static bool should_break_at(const methodHandle& method);

  // A wrapper for checking bool options
  static bool has_option(const methodHandle& method, enum CompileCommand option);

  // Check if method has option and value set. If yes, overwrite value and return true,
  // otherwise leave value unchanged and return false.
  template<typename T>
  static bool has_option_value(const methodHandle& method, enum CompileCommand option, T& value, bool verfiy_type = false);

  // Fast check if there is any option available that compile control needs to know about
  static bool has_any_option();

  // Reads from string instead of file
  static void parse_from_string(const char* command_string, void (*parser)(char*));

  static void parse_from_line(char* line);
  static void parse_from_line_impl(char* line, const char* original_line);
  static void parse_compile_only(char * line);

  // Tells whether there are any methods to print for print_method_statistics()
  static bool should_print_methods();

  // convert a string to a proper option - used from whitebox.
  // returns CompileCommand::Unknown on names not matching an option.
  static enum CompileCommand string_to_option(const char *name);
};

#endif // SHARE_COMPILER_COMPILERORACLE_HPP