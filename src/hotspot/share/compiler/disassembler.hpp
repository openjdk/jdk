/*
 * Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_COMPILER_DISASSEMBLER_HPP
#define SHARE_VM_COMPILER_DISASSEMBLER_HPP

#include "asm/codeBuffer.hpp"
#include "runtime/globals.hpp"
#include "utilities/macros.hpp"

class decode_env;

// The disassembler prints out assembly code annotated
// with Java specific information.

class Disassembler {
  friend class decode_env;
 private:
  // this is the type of the dll entry point:
  typedef void* (*decode_func_virtual)(uintptr_t start_va, uintptr_t end_va,
                               unsigned char* buffer, uintptr_t length,
                               void* (*event_callback)(void*, const char*, void*),
                               void* event_stream,
                               int (*printf_callback)(void*, const char*, ...),
                               void* printf_stream,
                               const char* options,
                               int newline);
  // this is the type of the dll entry point for old version:
  typedef void* (*decode_func)(void* start_va, void* end_va,
                               void* (*event_callback)(void*, const char*, void*),
                               void* event_stream,
                               int (*printf_callback)(void*, const char*, ...),
                               void* printf_stream,
                               const char* options);
  // points to the library.
  static void*    _library;
  // bailout
  static bool     _tried_to_load_library;
  // points to the decode function.
  static decode_func_virtual _decode_instructions_virtual;
  static decode_func _decode_instructions;
  // tries to load library and return whether it succedded.
  static bool load_library();

  // Machine dependent stuff
#include CPU_HEADER(disassembler)

 public:
  static bool can_decode() {
    ttyLocker tl;
    return (_decode_instructions_virtual != NULL) ||
           (_decode_instructions != NULL) ||
           load_library();
  }
  static void decode(CodeBlob *cb,               outputStream* st = NULL);
  static void decode(nmethod* nm,                outputStream* st = NULL);
  static void decode(address begin, address end, outputStream* st = NULL,
                     CodeStrings c = CodeStrings(), ptrdiff_t offset = 0);
  static void _hook(const char* file, int line, class MacroAssembler* masm);

  // This functions makes it easy to generate comments in the generated
  // interpreter code, by riding on the customary __ macro in the interpreter generator.
  // See templateTable_x86.cpp for an example.
  template<class T> inline static T* hook(const char* file, int line, T* masm) {
    if (PrintInterpreter) {
      _hook(file, line, masm);
    }
    return masm;
  }
};

#endif // SHARE_VM_COMPILER_DISASSEMBLER_HPP
