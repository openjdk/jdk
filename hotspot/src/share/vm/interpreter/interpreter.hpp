/*
 * Copyright (c) 1997, 2007, Oracle and/or its affiliates. All rights reserved.
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

// This file contains the platform-independent parts
// of the interpreter and the interpreter generator.

//------------------------------------------------------------------------------------------------------------------------
// An InterpreterCodelet is a piece of interpreter code. All
// interpreter code is generated into little codelets which
// contain extra information for debugging and printing purposes.

class InterpreterCodelet: public Stub {
  friend class VMStructs;
 private:
  int         _size;                             // the size in bytes
  const char* _description;                      // a description of the codelet, for debugging & printing
  Bytecodes::Code _bytecode;                     // associated bytecode if any

 public:
  // Initialization/finalization
  void    initialize(int size)                   { _size = size; }
  void    finalize()                             { ShouldNotCallThis(); }

  // General info/converters
  int     size() const                           { return _size; }
  static  int code_size_to_size(int code_size)   { return round_to(sizeof(InterpreterCodelet), CodeEntryAlignment) + code_size; }

  // Code info
  address code_begin() const                     { return (address)this + round_to(sizeof(InterpreterCodelet), CodeEntryAlignment); }
  address code_end() const                       { return (address)this + size(); }

  // Debugging
  void    verify();
  void    print_on(outputStream* st) const;
  void    print() const { print_on(tty); }

  // Interpreter-specific initialization
  void    initialize(const char* description, Bytecodes::Code bytecode);

  // Interpreter-specific attributes
  int         code_size() const                  { return code_end() - code_begin(); }
  const char* description() const                { return _description; }
  Bytecodes::Code bytecode() const               { return _bytecode; }
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
    int codelet_size = AbstractInterpreter::code()->available_space() - 2*K;

    // Guarantee there's a little bit of code space left.
    guarantee (codelet_size > 0 && (size_t)codelet_size >  2*K,
               "not enough space for interpreter generation");

    return codelet_size;
  }

 public:
  CodeletMark(
    InterpreterMacroAssembler*& masm,
    const char* description,
    Bytecodes::Code bytecode = Bytecodes::_illegal):
    _clet((InterpreterCodelet*)AbstractInterpreter::code()->request(codelet_size())),
    _cb(_clet->code_begin(), _clet->code_size())

  { // request all space (add some slack for Codelet data)
    assert (_clet != NULL, "we checked not enough space already");

    // initialize Codelet attributes
    _clet->initialize(description, bytecode);
    // create assembler for code generation
    masm  = new InterpreterMacroAssembler(&_cb);
    _masm = &masm;
  }

  ~CodeletMark() {
    // align so printing shows nop's instead of random code at the end (Codelets are aligned)
    (*_masm)->align(wordSize);
    // make sure all code is in code buffer
    (*_masm)->flush();


    // commit Codelet
    AbstractInterpreter::code()->commit((*_masm)->code()->pure_code_size());
    // make sure nobody can use _masm outside a CodeletMark lifespan
    *_masm = NULL;
  }
};

// Wrapper classes to produce Interpreter/InterpreterGenerator from either
// the c++ interpreter or the template interpreter.

class Interpreter: public CC_INTERP_ONLY(CppInterpreter) NOT_CC_INTERP(TemplateInterpreter) {

  public:
  // Debugging/printing
  static InterpreterCodelet* codelet_containing(address pc)     { return (InterpreterCodelet*)_code->stub_containing(pc); }
#include "incls/_interpreter_pd.hpp.incl"
};
