/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CLASSFILE_CLASSPRINTER_HPP
#define SHARE_CLASSFILE_CLASSPRINTER_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

class Symbol;

// ClassPrinter is intended to be called from findclass/findmethod/findmethod2
// in debug.cpp (inside a debugger, such as gdb). To avoid deadlocks (as the JVM
// may be at an arbitrary native breakpoint), ClassPrinter calls
// ClassLoaderDataGraph::classes_do without holding any locks. The down side is
// that the printing may proceed while other threads are running, so race conditions
// are possible. Use with care.
//
// If you want to call these functions programmatically, make sure the caller
// holds the appropriate locks.
class ClassPrinter : public AllStatic {
  class KlassPrintClosure;

  static bool matches(const char *pattern, const char *candidate, int p, int c);
  static bool matches(const char* pattern, Symbol* symbol);

public:

  enum Mode : int {
    PRINT_METHOD_NAME       = 1 << 0,
    PRINT_BYTECODE          = 1 << 1,
    PRINT_BYTECODE_ADDR     = 1 << 2,
  };

  // flags must be OR'ed from ClassPrinter::Mode for the next 3 functions
  static void print_classes_unlocked(const char* class_name_pattern, int flags);
  static void print_methods_unlocked(const char* class_name_pattern,
                                     const char* method_name_pattern, int flags);
  static void print_methods_unlocked(const char* class_name_pattern,
                                     const char* method_name_pattern,
                                     const char* method_signature_pattern, int flags);

  static bool has_mode(int flags, Mode mode) {
    return (flags & static_cast<int>(mode)) != 0;
  }
};

#endif // SHARE_CLASSFILE_CLASSPRINTER_HPP
