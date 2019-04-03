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

class CompilerOracle : AllStatic {
 private:
  static bool _quiet;
  static void print_tip();
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

  // Check to see if this method has option set for it
  static bool has_option_string(const methodHandle& method, const char * option);

  // Check if method has option and value set. If yes, overwrite value and return true,
  // otherwise leave value unchanged and return false.
  template<typename T>
  static bool has_option_value(const methodHandle& method, const char* option, T& value);

  // Fast check if there is any option available that compile control needs to know about
  static bool has_any_option();

  // Reads from string instead of file
  static void parse_from_string(const char* command_string, void (*parser)(char*));

  static void parse_from_line(char* line);
  static void parse_compile_only(char * line);

  // Tells whether there are any methods to print for print_method_statistics()
  static bool should_print_methods();
};

#endif // SHARE_COMPILER_COMPILERORACLE_HPP
