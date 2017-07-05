/*
 * Copyright (c) 1998, 2006, Oracle and/or its affiliates. All rights reserved.
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

// The verifier class
class Verifier : AllStatic {
 public:
  enum { STACKMAP_ATTRIBUTE_MAJOR_VERSION = 50 };
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
  static symbolHandle inference_verify(
    instanceKlassHandle klass, char* msg, size_t msg_len, TRAPS);
};

class RawBytecodeStream;
class StackMapFrame;
class StackMapTable;

// Summary of verifier's memory usage:
// StackMapTable is stack allocated.
// StackMapFrame are resource allocated. There is one ResourceMark
// for each method.
// There is one mutable StackMapFrame (current_frame) which is updated
// by abstract bytecode interpretation. frame_in_exception_handler() returns
// a frame that has a mutable one-item stack (ready for pushing the
// catch type exception object). All the other StackMapFrame's
// are immutable (including their locals and stack arrays) after
// their constructions.
// locals/stack arrays in StackMapFrame are resource allocated.
// locals/stack arrays can be shared between StackMapFrame's, except
// the mutable StackMapFrame (current_frame).
// Care needs to be taken to make sure resource objects don't outlive
// the lifetime of their ResourceMark.

// These macros are used similarly to CHECK macros but also check
// the status of the verifier and return if that has an error.
#define CHECK_VERIFY(verifier) \
  CHECK); if ((verifier)->has_error()) return; (0
#define CHECK_VERIFY_(verifier, result) \
  CHECK_(result)); if ((verifier)->has_error()) return (result); (0

// A new instance of this class is created for each class being verified
class ClassVerifier : public StackObj {
 private:
  Thread* _thread;
  symbolHandle _exception_type;
  char* _message;
  size_t _message_buffer_len;

  void verify_method(methodHandle method, TRAPS);
  char* generate_code_data(methodHandle m, u4 code_length, TRAPS);
  void verify_exception_handler_table(u4 code_length, char* code_data, int& min, int& max, TRAPS);
  void verify_local_variable_table(u4 code_length, char* code_data, TRAPS);

  VerificationType cp_ref_index_to_type(
      int index, constantPoolHandle cp, TRAPS) {
    return cp_index_to_type(cp->klass_ref_index_at(index), cp, THREAD);
  }

  bool is_protected_access(
    instanceKlassHandle this_class, klassOop target_class,
    symbolOop field_name, symbolOop field_sig, bool is_method);

  void verify_cp_index(constantPoolHandle cp, int index, TRAPS);
  void verify_cp_type(
    int index, constantPoolHandle cp, unsigned int types, TRAPS);
  void verify_cp_class_type(int index, constantPoolHandle cp, TRAPS);

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
    RawBytecodeStream* bcs, VerificationType ref_class_type,
    StackMapFrame* current_frame, u4 code_length, bool* this_uninit,
    constantPoolHandle cp, TRAPS);

  void verify_invoke_instructions(
    RawBytecodeStream* bcs, u4 code_length, StackMapFrame* current_frame,
    bool* this_uninit, VerificationType return_type,
    constantPoolHandle cp, TRAPS);

  VerificationType get_newarray_type(u2 index, u2 bci, TRAPS);
  void verify_anewarray(
    u2 index, constantPoolHandle cp, StackMapFrame* current_frame, TRAPS);
  void verify_return_value(
    VerificationType return_type, VerificationType type, u2 offset, TRAPS);

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

  bool name_in_supers(symbolOop ref_name, instanceKlassHandle current);

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

 public:
  enum {
    BYTECODE_OFFSET = 1,
    NEW_OFFSET = 2
  };

  // constructor
  ClassVerifier(instanceKlassHandle klass, char* msg, size_t msg_len, TRAPS);

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
  symbolHandle result() const { return _exception_type; }
  bool has_error() const { return !(result().is_null()); }

  // Called when verify or class format errors are encountered.
  // May throw an exception based upon the mode.
  void verify_error(u2 offset, const char* fmt, ...);
  void verify_error(const char* fmt, ...);
  void class_format_error(const char* fmt, ...);
  void format_error_message(const char* fmt, int offset, va_list args);

  klassOop load_class(symbolHandle name, TRAPS);

  int change_sig_to_verificationType(
    SignatureStream* sig_type, VerificationType* inference_type, TRAPS);

  VerificationType cp_index_to_type(int index, constantPoolHandle cp, TRAPS) {
    return VerificationType::reference_type(
      symbolHandle(THREAD, cp->klass_name_at(index)));
  }

  static bool _verify_verbose;  // for debugging
};

inline int ClassVerifier::change_sig_to_verificationType(
    SignatureStream* sig_type, VerificationType* inference_type, TRAPS) {
  BasicType bt = sig_type->type();
  switch (bt) {
    case T_OBJECT:
    case T_ARRAY:
      {
        symbolOop name = sig_type->as_symbol(CHECK_0);
        *inference_type =
          VerificationType::reference_type(symbolHandle(THREAD, name));
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
