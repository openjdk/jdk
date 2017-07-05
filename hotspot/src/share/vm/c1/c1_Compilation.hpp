/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

class BlockBegin;
class CompilationResourceObj;
class XHandlers;
class ExceptionInfo;
class DebugInformationRecorder;
class FrameMap;
class IR;
class IRScope;
class Instruction;
class LinearScan;
class OopMap;
class LIR_Emitter;
class LIR_Assembler;
class CodeEmitInfo;
class ciEnv;
class ciMethod;
class ValueStack;
class LIR_OprDesc;
class C1_MacroAssembler;
class CFGPrinter;
typedef LIR_OprDesc* LIR_Opr;


define_array(BasicTypeArray, BasicType)
define_stack(BasicTypeList, BasicTypeArray)

define_array(ExceptionInfoArray, ExceptionInfo*)
define_stack(ExceptionInfoList,  ExceptionInfoArray)

class Compilation: public StackObj {
  friend class CompilationResourceObj;
 private:
  // compilation specifics
  Arena* _arena;
  int _next_id;
  int _next_block_id;
  AbstractCompiler*  _compiler;
  ciEnv*             _env;
  ciMethod*          _method;
  int                _osr_bci;
  IR*                _hir;
  int                _max_spills;
  FrameMap*          _frame_map;
  C1_MacroAssembler* _masm;
  bool               _has_exception_handlers;
  bool               _has_fpu_code;
  bool               _has_unsafe_access;
  const char*        _bailout_msg;
  ExceptionInfoList* _exception_info_list;
  ExceptionHandlerTable _exception_handler_table;
  ImplicitExceptionTable _implicit_exception_table;
  LinearScan*        _allocator;
  CodeOffsets        _offsets;
  CodeBuffer         _code;

  // compilation helpers
  void initialize();
  void build_hir();
  void emit_lir();

  void emit_code_epilog(LIR_Assembler* assembler);
  int  emit_code_body();

  int  compile_java_method();
  void install_code(int frame_size);
  void compile_method();

  void generate_exception_handler_table();

  ExceptionInfoList* exception_info_list() const { return _exception_info_list; }
  ExceptionHandlerTable* exception_handler_table() { return &_exception_handler_table; }

  LinearScan* allocator()                          { return _allocator;      }
  void        set_allocator(LinearScan* allocator) { _allocator = allocator; }

  Instruction*       _current_instruction;       // the instruction currently being processed
#ifndef PRODUCT
  Instruction*       _last_instruction_printed;  // the last instruction printed during traversal
#endif // PRODUCT

 public:
  // creation
  Compilation(AbstractCompiler* compiler, ciEnv* env, ciMethod* method,
              int osr_bci, BufferBlob* buffer_blob);
  ~Compilation();


  static Compilation* current() {
    return (Compilation*) ciEnv::current()->compiler_data();
  }

  // accessors
  ciEnv* env() const                             { return _env; }
  AbstractCompiler* compiler() const             { return _compiler; }
  bool has_exception_handlers() const            { return _has_exception_handlers; }
  bool has_fpu_code() const                      { return _has_fpu_code; }
  bool has_unsafe_access() const                 { return _has_unsafe_access; }
  ciMethod* method() const                       { return _method; }
  int osr_bci() const                            { return _osr_bci; }
  bool is_osr_compile() const                    { return osr_bci() >= 0; }
  IR* hir() const                                { return _hir; }
  int max_spills() const                         { return _max_spills; }
  FrameMap* frame_map() const                    { return _frame_map; }
  CodeBuffer* code()                             { return &_code; }
  C1_MacroAssembler* masm() const                { return _masm; }
  CodeOffsets* offsets()                         { return &_offsets; }
  Arena* arena()                                 { return _arena; }

  // Instruction ids
  int get_next_id()                              { return _next_id++; }
  int number_of_instructions() const             { return _next_id; }

  // BlockBegin ids
  int get_next_block_id()                        { return _next_block_id++; }
  int number_of_blocks() const                   { return _next_block_id; }

  // setters
  void set_has_exception_handlers(bool f)        { _has_exception_handlers = f; }
  void set_has_fpu_code(bool f)                  { _has_fpu_code = f; }
  void set_has_unsafe_access(bool f)             { _has_unsafe_access = f; }
  // Add a set of exception handlers covering the given PC offset
  void add_exception_handlers_for_pco(int pco, XHandlers* exception_handlers);
  // Statistics gathering
  void notice_inlined_method(ciMethod* method);

  DebugInformationRecorder* debug_info_recorder() const; // = _env->debug_info();
  Dependencies* dependency_recorder() const; // = _env->dependencies()
  ImplicitExceptionTable* implicit_exception_table()     { return &_implicit_exception_table; }

  Instruction* current_instruction() const       { return _current_instruction; }
  Instruction* set_current_instruction(Instruction* instr) {
    Instruction* previous = _current_instruction;
    _current_instruction = instr;
    return previous;
  }

#ifndef PRODUCT
  void maybe_print_current_instruction();
#endif // PRODUCT

  // error handling
  void bailout(const char* msg);
  bool bailed_out() const                        { return _bailout_msg != NULL; }
  const char* bailout_msg() const                { return _bailout_msg; }

  static int desired_max_code_buffer_size() {
    return (int) NMethodSizeLimit;  // default 256K or 512K
  }
  static int desired_max_constant_size() {
    return (int) NMethodSizeLimit / 10;  // about 25K
  }

  static void setup_code_buffer(CodeBuffer* cb, int call_stub_estimate);

  // timers
  static void print_timers();

#ifndef PRODUCT
  // debugging support.
  // produces a file named c1compileonly in the current directory with
  // directives to compile only the current method and it's inlines.
  // The file can be passed to the command line option -XX:Flags=<filename>
  void compile_only_this_method();
  void compile_only_this_scope(outputStream* st, IRScope* scope);
  void exclude_this_method();
#endif // PRODUCT
};


// Macro definitions for unified bailout-support
// The methods bailout() and bailed_out() are present in all classes
// that might bailout, but forward all calls to Compilation
#define BAILOUT(msg)               { bailout(msg); return;              }
#define BAILOUT_(msg, res)         { bailout(msg); return res;          }

#define CHECK_BAILOUT()            { if (bailed_out()) return;          }
#define CHECK_BAILOUT_(res)        { if (bailed_out()) return res;      }


class InstructionMark: public StackObj {
 private:
  Compilation* _compilation;
  Instruction*  _previous;

 public:
  InstructionMark(Compilation* compilation, Instruction* instr) {
    _compilation = compilation;
    _previous = _compilation->set_current_instruction(instr);
  }
  ~InstructionMark() {
    _compilation->set_current_instruction(_previous);
  }
};


//----------------------------------------------------------------------
// Base class for objects allocated by the compiler in the compilation arena
class CompilationResourceObj ALLOCATION_SUPER_CLASS_SPEC {
 public:
  void* operator new(size_t size) { return Compilation::current()->arena()->Amalloc(size); }
  void* operator new(size_t size, Arena* arena) {
    return arena->Amalloc(size);
  }
  void  operator delete(void* p) {} // nothing to do
};


//----------------------------------------------------------------------
// Class for aggregating exception handler information.

// Effectively extends XHandlers class with PC offset of
// potentially exception-throwing instruction.
// This class is used at the end of the compilation to build the
// ExceptionHandlerTable.
class ExceptionInfo: public CompilationResourceObj {
 private:
  int             _pco;                // PC of potentially exception-throwing instruction
  XHandlers*      _exception_handlers; // flat list of exception handlers covering this PC

 public:
  ExceptionInfo(int pco, XHandlers* exception_handlers)
    : _pco(pco)
    , _exception_handlers(exception_handlers)
  { }

  int pco()                                      { return _pco; }
  XHandlers* exception_handlers()                { return _exception_handlers; }
};
