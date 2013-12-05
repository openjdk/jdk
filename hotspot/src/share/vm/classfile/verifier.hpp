/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_VERIFIER_HPP
#define SHARE_VM_CLASSFILE_VERIFIER_HPP

#include "classfile/verificationType.hpp"
#include "memory/gcLocker.hpp"
#include "oops/klass.hpp"
#include "oops/method.hpp"
#include "runtime/handles.hpp"
#include "utilities/exceptions.hpp"

// The verifier class
class Verifier : AllStatic {
 public:
  enum {
    STRICTER_ACCESS_CTRL_CHECK_VERSION  = 49,
    STACKMAP_ATTRIBUTE_MAJOR_VERSION    = 50,
    INVOKEDYNAMIC_MAJOR_VERSION         = 51,
    NO_RELAX_ACCESS_CTRL_CHECK_VERSION  = 52
  };
  typedef enum { ThrowException, NoException } Mode;

  /**
   * Verify the bytecodes for a class.  If 'throw_exception' is true
   * then the appropriate VerifyError or ClassFormatError will be thrown.
   * Otherwise, no exception is thrown and the return indicates the
   * error.
   */
  static bool verify(instanceKlassHandle klass, Mode mode, bool should_verify_class, TRAPS);

  // Return false if the class is loaded by the bootstrap loader,
  // or if defineClass was called requesting skipping verification
  // -Xverify:all/none override this value
  static bool should_verify_for(oop class_loader, bool should_verify_class);

  // Relax certain verifier checks to enable some broken 1.1 apps to run on 1.2.
  static bool relax_verify_for(oop class_loader);

 private:
  static bool is_eligible_for_verification(instanceKlassHandle klass, bool should_verify_class);
  static Symbol* inference_verify(
    instanceKlassHandle klass, char* msg, size_t msg_len, TRAPS);
};

class RawBytecodeStream;
class StackMapFrame;
class StackMapTable;

// Summary of verifier's memory usage:
// StackMapTable is stack allocated.
// StackMapFrame are resource allocated. There is only one ResourceMark
// for each class verification, which is created at the top level.
// There is one mutable StackMapFrame (current_frame) which is updated
// by abstract bytecode interpretation. frame_in_exception_handler() returns
// a frame that has a mutable one-item stack (ready for pushing the
// catch type exception object). All the other StackMapFrame's
// are immutable (including their locals and stack arrays) after
// their constructions.
// locals/stack arrays in StackMapFrame are resource allocated.
// locals/stack arrays can be shared between StackMapFrame's, except
// the mutable StackMapFrame (current_frame).

// These macros are used similarly to CHECK macros but also check
// the status of the verifier and return if that has an error.
#define CHECK_VERIFY(verifier) \
  CHECK); if ((verifier)->has_error()) return; ((void)0
#define CHECK_VERIFY_(verifier, result) \
  CHECK_(result)); if ((verifier)->has_error()) return (result); ((void)0

class TypeOrigin VALUE_OBJ_CLASS_SPEC {
 private:
  typedef enum {
    CF_LOCALS,  // Comes from the current frame locals
    CF_STACK,   // Comes from the current frame expression stack
    SM_LOCALS,  // Comes from stackmap locals
    SM_STACK,   // Comes from stackmap expression stack
    CONST_POOL, // Comes from the constant pool
    SIG,        // Comes from method signature
    IMPLICIT,   // Comes implicitly from code or context
    BAD_INDEX,  // No type, but the index is bad
    FRAME_ONLY, // No type, context just contains the frame
    NONE
  } Origin;

  Origin _origin;
  u2 _index;              // local, stack, or constant pool index
  StackMapFrame* _frame;  // source frame if CF or SM
  VerificationType _type; // The actual type

  TypeOrigin(
      Origin origin, u2 index, StackMapFrame* frame, VerificationType type)
      : _origin(origin), _index(index), _frame(frame), _type(type) {}

 public:
  TypeOrigin() : _origin(NONE), _index(0), _frame(NULL) {}

  static TypeOrigin null();
  static TypeOrigin local(u2 index, StackMapFrame* frame);
  static TypeOrigin stack(u2 index, StackMapFrame* frame);
  static TypeOrigin sm_local(u2 index, StackMapFrame* frame);
  static TypeOrigin sm_stack(u2 index, StackMapFrame* frame);
  static TypeOrigin cp(u2 index, VerificationType vt);
  static TypeOrigin signature(VerificationType vt);
  static TypeOrigin bad_index(u2 index);
  static TypeOrigin implicit(VerificationType t);
  static TypeOrigin frame(StackMapFrame* frame);

  void reset_frame();
  void details(outputStream* ss) const;
  void print_frame(outputStream* ss) const;
  const StackMapFrame* frame() const { return _frame; }
  bool is_valid() const { return _origin != NONE; }
  u2 index() const { return _index; }

#ifdef ASSERT
  void print_on(outputStream* str) const;
#endif
};

class ErrorContext VALUE_OBJ_CLASS_SPEC {
 private:
  typedef enum {
    INVALID_BYTECODE,     // There was a problem with the bytecode
    WRONG_TYPE,           // Type value was not as expected
    FLAGS_MISMATCH,       // Frame flags are not assignable
    BAD_CP_INDEX,         // Invalid constant pool index
    BAD_LOCAL_INDEX,      // Invalid local index
    LOCALS_SIZE_MISMATCH, // Frames have differing local counts
    STACK_SIZE_MISMATCH,  // Frames have different stack sizes
    STACK_OVERFLOW,       // Attempt to push onto a full expression stack
    STACK_UNDERFLOW,      // Attempt to pop and empty expression stack
    MISSING_STACKMAP,     // No stackmap for this location and there should be
    BAD_STACKMAP,         // Format error in stackmap
    NO_FAULT,             // No error
    UNKNOWN
  } FaultType;

  int _bci;
  FaultType _fault;
  TypeOrigin _type;
  TypeOrigin _expected;

  ErrorContext(int bci, FaultType fault) :
      _bci(bci), _fault(fault)  {}
  ErrorContext(int bci, FaultType fault, TypeOrigin type) :
      _bci(bci), _fault(fault), _type(type)  {}
  ErrorContext(int bci, FaultType fault, TypeOrigin type, TypeOrigin exp) :
      _bci(bci), _fault(fault), _type(type), _expected(exp)  {}

 public:
  ErrorContext() : _bci(-1), _fault(NO_FAULT) {}

  static ErrorContext bad_code(u2 bci) {
    return ErrorContext(bci, INVALID_BYTECODE);
  }
  static ErrorContext bad_type(u2 bci, TypeOrigin type) {
    return ErrorContext(bci, WRONG_TYPE, type);
  }
  static ErrorContext bad_type(u2 bci, TypeOrigin type, TypeOrigin exp) {
    return ErrorContext(bci, WRONG_TYPE, type, exp);
  }
  static ErrorContext bad_flags(u2 bci, StackMapFrame* frame) {
    return ErrorContext(bci, FLAGS_MISMATCH, TypeOrigin::frame(frame));
  }
  static ErrorContext bad_flags(u2 bci, StackMapFrame* cur, StackMapFrame* sm) {
    return ErrorContext(bci, FLAGS_MISMATCH,
                        TypeOrigin::frame(cur), TypeOrigin::frame(sm));
  }
  static ErrorContext bad_cp_index(u2 bci, u2 index) {
    return ErrorContext(bci, BAD_CP_INDEX, TypeOrigin::bad_index(index));
  }
  static ErrorContext bad_local_index(u2 bci, u2 index) {
    return ErrorContext(bci, BAD_LOCAL_INDEX, TypeOrigin::bad_index(index));
  }
  static ErrorContext locals_size_mismatch(
      u2 bci, StackMapFrame* frame0, StackMapFrame* frame1) {
    return ErrorContext(bci, LOCALS_SIZE_MISMATCH,
        TypeOrigin::frame(frame0), TypeOrigin::frame(frame1));
  }
  static ErrorContext stack_size_mismatch(
      u2 bci, StackMapFrame* frame0, StackMapFrame* frame1) {
    return ErrorContext(bci, STACK_SIZE_MISMATCH,
        TypeOrigin::frame(frame0), TypeOrigin::frame(frame1));
  }
  static ErrorContext stack_overflow(u2 bci, StackMapFrame* frame) {
    return ErrorContext(bci, STACK_OVERFLOW, TypeOrigin::frame(frame));
  }
  static ErrorContext stack_underflow(u2 bci, StackMapFrame* frame) {
    return ErrorContext(bci, STACK_UNDERFLOW, TypeOrigin::frame(frame));
  }
  static ErrorContext missing_stackmap(u2 bci) {
    return ErrorContext(bci, MISSING_STACKMAP);
  }
  static ErrorContext bad_stackmap(int index, StackMapFrame* frame) {
    return ErrorContext(0, BAD_STACKMAP, TypeOrigin::frame(frame));
  }

  bool is_valid() const { return _fault != NO_FAULT; }
  int bci() const { return _bci; }

  void reset_frames() {
    _type.reset_frame();
    _expected.reset_frame();
  }

  void details(outputStream* ss, const Method* method) const;

#ifdef ASSERT
  void print_on(outputStream* str) const {
    str->print("error_context(%d, %d,", _bci, _fault);
    _type.print_on(str);
    str->print(",");
    _expected.print_on(str);
    str->print(")");
  }
#endif

 private:
  void location_details(outputStream* ss, const Method* method) const;
  void reason_details(outputStream* ss) const;
  void frame_details(outputStream* ss) const;
  void bytecode_details(outputStream* ss, const Method* method) const;
  void handler_details(outputStream* ss, const Method* method) const;
  void stackmap_details(outputStream* ss, const Method* method) const;
};

// A new instance of this class is created for each class being verified
class ClassVerifier : public StackObj {
 private:
  Thread* _thread;
  GrowableArray<Symbol*>* _symbols;  // keep a list of symbols created

  Symbol* _exception_type;
  char* _message;

  ErrorContext _error_context;  // contains information about an error

  void verify_method(methodHandle method, TRAPS);
  char* generate_code_data(methodHandle m, u4 code_length, TRAPS);
  void verify_exception_handler_table(u4 code_length, char* code_data,
                                      int& min, int& max, TRAPS);
  void verify_local_variable_table(u4 code_length, char* code_data, TRAPS);

  VerificationType cp_ref_index_to_type(
      int index, constantPoolHandle cp, TRAPS) {
    return cp_index_to_type(cp->klass_ref_index_at(index), cp, THREAD);
  }

  bool is_protected_access(
    instanceKlassHandle this_class, Klass* target_class,
    Symbol* field_name, Symbol* field_sig, bool is_method);

  void verify_cp_index(u2 bci, constantPoolHandle cp, int index, TRAPS);
  void verify_cp_type(u2 bci, int index, constantPoolHandle cp,
      unsigned int types, TRAPS);
  void verify_cp_class_type(u2 bci, int index, constantPoolHandle cp, TRAPS);

  u2 verify_stackmap_table(
    u2 stackmap_index, u2 bci, StackMapFrame* current_frame,
    StackMapTable* stackmap_table, bool no_control_flow, TRAPS);

  void verify_exception_handler_targets(
    u2 bci, bool this_uninit, StackMapFrame* current_frame,
    StackMapTable* stackmap_table, TRAPS);

  void verify_ldc(
    int opcode, u2 index, StackMapFrame *current_frame,
    constantPoolHandle cp, u2 bci, TRAPS);

  void verify_switch(
    RawBytecodeStream* bcs, u4 code_length, char* code_data,
    StackMapFrame* current_frame, StackMapTable* stackmap_table, TRAPS);

  void verify_field_instructions(
    RawBytecodeStream* bcs, StackMapFrame* current_frame,
    constantPoolHandle cp, TRAPS);

  void verify_invoke_init(
    RawBytecodeStream* bcs, u2 ref_index, VerificationType ref_class_type,
    StackMapFrame* current_frame, u4 code_length, bool* this_uninit,
    constantPoolHandle cp, TRAPS);

  void verify_invoke_instructions(
    RawBytecodeStream* bcs, u4 code_length, StackMapFrame* current_frame,
    bool* this_uninit, VerificationType return_type,
    constantPoolHandle cp, TRAPS);

  VerificationType get_newarray_type(u2 index, u2 bci, TRAPS);
  void verify_anewarray(u2 bci, u2 index, constantPoolHandle cp,
      StackMapFrame* current_frame, TRAPS);
  void verify_return_value(
      VerificationType return_type, VerificationType type, u2 offset,
      StackMapFrame* current_frame, TRAPS);

  void verify_iload (u2 index, StackMapFrame* current_frame, TRAPS);
  void verify_lload (u2 index, StackMapFrame* current_frame, TRAPS);
  void verify_fload (u2 index, StackMapFrame* current_frame, TRAPS);
  void verify_dload (u2 index, StackMapFrame* current_frame, TRAPS);
  void verify_aload (u2 index, StackMapFrame* current_frame, TRAPS);
  void verify_istore(u2 index, StackMapFrame* current_frame, TRAPS);
  void verify_lstore(u2 index, StackMapFrame* current_frame, TRAPS);
  void verify_fstore(u2 index, StackMapFrame* current_frame, TRAPS);
  void verify_dstore(u2 index, StackMapFrame* current_frame, TRAPS);
  void verify_astore(u2 index, StackMapFrame* current_frame, TRAPS);
  void verify_iinc  (u2 index, StackMapFrame* current_frame, TRAPS);

  bool name_in_supers(Symbol* ref_name, instanceKlassHandle current);

  VerificationType object_type() const;

  instanceKlassHandle _klass;  // the class being verified
  methodHandle        _method; // current method being verified
  VerificationType    _this_type; // the verification type of the current class

  // Some recursive calls from the verifier to the name resolver
  // can cause the current class to be re-verified and rewritten.
  // If this happens, the original verification should not continue,
  // because constant pool indexes will have changed.
  // The rewriter is preceded by the verifier.  If the verifier throws
  // an error, rewriting is prevented.  Also, rewriting always precedes
  // bytecode execution or compilation.  Thus, is_rewritten implies
  // that a class has been verified and prepared for execution.
  bool was_recursively_verified() { return _klass->is_rewritten(); }

  bool is_same_or_direct_interface(instanceKlassHandle klass,
    VerificationType klass_type, VerificationType ref_class_type);

 public:
  enum {
    BYTECODE_OFFSET = 1,
    NEW_OFFSET = 2
  };

  // constructor
  ClassVerifier(instanceKlassHandle klass, TRAPS);

  // destructor
  ~ClassVerifier();

  Thread* thread()             { return _thread; }
  methodHandle method()        { return _method; }
  instanceKlassHandle current_class() const { return _klass; }
  VerificationType current_type() const { return _this_type; }

  // Verifies the class.  If a verify or class file format error occurs,
  // the '_exception_name' symbols will set to the exception name and
  // the message_buffer will be filled in with the exception message.
  void verify_class(TRAPS);

  // Return status modes
  Symbol* result() const { return _exception_type; }
  bool has_error() const { return result() != NULL; }
  char* exception_message() {
    stringStream ss;
    ss.print(_message);
    _error_context.details(&ss, _method());
    return ss.as_string();
  }

  // Called when verify or class format errors are encountered.
  // May throw an exception based upon the mode.
  void verify_error(ErrorContext ctx, const char* fmt, ...);
  void class_format_error(const char* fmt, ...);

  Klass* load_class(Symbol* name, TRAPS);

  int change_sig_to_verificationType(
    SignatureStream* sig_type, VerificationType* inference_type, TRAPS);

  VerificationType cp_index_to_type(int index, constantPoolHandle cp, TRAPS) {
    return VerificationType::reference_type(cp->klass_name_at(index));
  }

  // Keep a list of temporary symbols created during verification because
  // their reference counts need to be decrememented when the verifier object
  // goes out of scope.  Since these symbols escape the scope in which they're
  // created, we can't use a TempNewSymbol.
  Symbol* create_temporary_symbol(
      const Symbol* s, int begin, int end, TRAPS);
  Symbol* create_temporary_symbol(const char *s, int length, TRAPS);

  TypeOrigin ref_ctx(const char* str, TRAPS);
};

inline int ClassVerifier::change_sig_to_verificationType(
    SignatureStream* sig_type, VerificationType* inference_type, TRAPS) {
  BasicType bt = sig_type->type();
  switch (bt) {
    case T_OBJECT:
    case T_ARRAY:
      {
        Symbol* name = sig_type->as_symbol(CHECK_0);
        // Create another symbol to save as signature stream unreferences
        // this symbol.
        Symbol* name_copy =
          create_temporary_symbol(name, 0, name->utf8_length(), CHECK_0);
        assert(name_copy == name, "symbols don't match");
        *inference_type =
          VerificationType::reference_type(name_copy);
        return 1;
      }
    case T_LONG:
      *inference_type = VerificationType::long_type();
      *++inference_type = VerificationType::long2_type();
      return 2;
    case T_DOUBLE:
      *inference_type = VerificationType::double_type();
      *++inference_type = VerificationType::double2_type();
      return 2;
    case T_INT:
    case T_BOOLEAN:
    case T_BYTE:
    case T_CHAR:
    case T_SHORT:
      *inference_type = VerificationType::integer_type();
      return 1;
    case T_FLOAT:
      *inference_type = VerificationType::float_type();
      return 1;
    default:
      ShouldNotReachHere();
      return 1;
  }
}

#endif // SHARE_VM_CLASSFILE_VERIFIER_HPP
