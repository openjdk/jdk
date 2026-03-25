/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_INTERPRETER_INTERPRETER_HPP
#define SHARE_INTERPRETER_INTERPRETER_HPP

#include "code/stubs.hpp"
#include "interpreter/interp_masm.hpp"
#include "interpreter/templateInterpreter.hpp"
#include "interpreter/zero/zeroInterpreter.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/align.hpp"

// This file contains the platform-independent parts
// of the interpreter and the interpreter generator.

class InterpreterMacroAssembler;

//------------------------------------------------------------------------------------------------------------------------
// An InterpreterCodelet is a piece of interpreter code. All
// interpreter code is generated into little codelets which
// contain extra information for debugging and printing purposes.

class InterpreterCodelet: public Stub {
  friend class VMStructs;
 private:
  NOT_PRODUCT(AsmRemarks _asm_remarks;)   // Comments for annotating assembler output.
  NOT_PRODUCT(DbgStrings _dbg_strings;)   // Debug strings used in generated code.
  const char*     _description;           // A description of the codelet, for debugging & printing
  int             _size;                  // The codelet size in bytes
  Bytecodes::Code _bytecode;              // Associated bytecode, if any

 public:
  // Initialization/finalization
  void    initialize(int size)                   { _size = size; }
  void    finalize()                             { ShouldNotCallThis(); }

  // General info/converters
  int     size() const                           { return _size; }
  static  int alignment()                        { return HeapWordSize; }
  static uint code_alignment()                   { return CodeEntryAlignment; }

  // Code info
  address code_begin() const                     { return align_up((address)this + sizeof(InterpreterCodelet), code_alignment()); }
  address code_end() const                       { return (address)this + size(); }

  // Debugging
  void    verify();
  void    print_on(outputStream* st) const;
  void    print() const;

  // Interpreter-specific initialization
  void    initialize(const char* description, Bytecodes::Code bytecode);

  // Interpreter-specific attributes
  int         code_size() const                  { return (int)(code_end() - code_begin()); }
  const char* description() const                { return _description; }
  Bytecodes::Code bytecode() const               { return _bytecode; }
#ifndef PRODUCT
 ~InterpreterCodelet() {
    // InterpreterCodelets reside in the StubQueue and should not be deleted,
    // nor are they ever finalized (see above).
    ShouldNotCallThis();
  }
  void use_remarks(AsmRemarks &remarks) { _asm_remarks.share(remarks); }
  void use_strings(DbgStrings &strings) { _dbg_strings.share(strings); }

  void clear_remarks() { _asm_remarks.clear(); }
  void clear_strings() { _dbg_strings.clear(); }
#endif
};

// Define a prototype interface
DEF_STUB_INTERFACE(InterpreterCodelet);


//------------------------------------------------------------------------------------------------------------------------
// A CodeletMark serves as an automatic creator/initializer for Codelets
// (As a subclass of ResourceMark it automatically GC's the allocated
// code buffer and assemblers).

class CodeletMark: ResourceMark {
 private:
  InterpreterCodelet*         _clet;
  InterpreterMacroAssembler** _masm;
  CodeBuffer                  _cb;

  int codelet_size() {
    // Request the whole code buffer (minus a little for alignment).
    // The commit call below trims it back for each codelet.
    int codelet_size = AbstractInterpreter::code()->available_space() - (int)(2*K);

    // Guarantee there's a little bit of code space left.
    guarantee(codelet_size > 0 && (size_t)codelet_size > 2*K,
              "not enough space for interpreter generation");

    return codelet_size;
  }

 public:
  CodeletMark(InterpreterMacroAssembler*& masm,
              const char* description,
              Bytecodes::Code bytecode = Bytecodes::_illegal);
  ~CodeletMark();
};

// Wrapper typedef to use the name Interpreter to mean either
// the Zero interpreter or the template interpreter.

typedef ZERO_ONLY(ZeroInterpreter) NOT_ZERO(TemplateInterpreter) Interpreter;

#endif // SHARE_INTERPRETER_INTERPRETER_HPP
