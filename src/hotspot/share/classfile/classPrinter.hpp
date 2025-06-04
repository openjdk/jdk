/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

class InstanceKlass;
class Method;
class outputStream;
class Symbol;

// ClassPrinter is intended to be called from findclass() and findmethod()
// in debug.cpp (inside a debugger, such as gdb).
//
// The ClassPrinter::print_xxx() functions hold the ClassLoaderDataGraph_lock
// (and the ttyLocker if ClassPrinter::PRINT_BYTECODE is selected). A deadlock
// may happen if these functions are called in a context where these locks
// are already held. Use with caution.

class ClassPrinter : public AllStatic {
  class KlassPrintClosure;

public:

  enum Mode : int {
    PRINT_METHOD_NAME       = 1 << 0,
    PRINT_BYTECODE          = 1 << 1,
    PRINT_BYTECODE_ADDR     = 1 << 2,
    PRINT_DYNAMIC           = 1 << 3, // extra information for invokedynamic (and dynamic constant ...)
    PRINT_METHOD_HANDLE     = 1 << 4, // extra information for invokehandle
  };
  static bool has_mode(int flags, Mode mode) {
    return (flags & static_cast<int>(mode)) != 0;
  }

  static void print_flags_help(outputStream* os);

  // Parameters for print_classes() and print_methods():
  //
  // - The patterns are matched by StringUtils::is_star_match()
  // - class_name_pattern matches Klass::external_name(). E.g., "java/lang/Object" or "*ang/Object"
  // - method_pattern may optionally include the signature. E.g., "wait", "wait:()V" or "*ai*t:(*)V"
  // - flags must be OR'ed from ClassPrinter::Mode
  //
  //   print_classes("java/lang/Object", 0x3, os)            -> find j.l.Object and disasm all of its methods
  //   print_methods("*ang/Object*", "wait", 0xff, os)       -> detailed disasm of all "wait" methods in j.l.Object
  //   print_methods("*ang/Object*", "wait:(*J*)V", 0x1, os) -> list all "wait" methods in j.l.Object that have a long parameter
  static void print_classes(const char* class_name_pattern, int flags, outputStream* os);
  static void print_methods(const char* class_name_pattern,
                            const char* method_pattern, int flags, outputStream* os);
};

#endif // SHARE_CLASSFILE_CLASSPRINTER_HPP
