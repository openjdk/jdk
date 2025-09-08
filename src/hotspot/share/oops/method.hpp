/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_METHOD_HPP
#define SHARE_OOPS_METHOD_HPP

#include "code/compressedStream.hpp"
#include "compiler/compilerDefinitions.hpp"
#include "oops/annotations.hpp"
#include "oops/constantPool.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/methodFlags.hpp"
#include "oops/oop.hpp"
#include "utilities/accessFlags.hpp"
#include "utilities/align.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"
#include "utilities/vmEnums.hpp"
#if INCLUDE_JFR
#include "jfr/support/jfrTraceIdExtension.hpp"
#endif


// A Method represents a Java method.
//
// Note that most applications load thousands of methods, so keeping the size of this
// class small has a big impact on footprint.
//
// Note that native_function and signature_handler have to be at fixed offsets
// (required by the interpreter)
//
//  Method embedded field layout (after declared fields):
//   [EMBEDDED native_function       (present only if native) ]
//   [EMBEDDED signature_handler     (present only if native) ]

class CheckedExceptionElement;
class LocalVariableTableElement;
class AdapterHandlerEntry;
class MethodData;
class MethodCounters;
class MethodTrainingData;
class ConstMethod;
class InlineTableSizes;
class nmethod;
class InterpreterOopMap;

class Method : public Metadata {
 friend class VMStructs;
 friend class JVMCIVMStructs;
 friend class MethodTest;
 private:
  // If you add a new field that points to any metaspace object, you
  // must add this field to Method::metaspace_pointers_do().
  ConstMethod*      _constMethod;                // Method read-only data.
  MethodData*       _method_data;
  MethodCounters*   _method_counters;
  AdapterHandlerEntry* _adapter;
  int               _vtable_index;               // vtable index of this method (see VtableIndexFlag)
  AccessFlags       _access_flags;               // Access flags
  MethodFlags       _flags;

  u2                _intrinsic_id;               // vmSymbols::intrinsic_id (0 == _none)

  JFR_ONLY(DEFINE_TRACE_FLAG;)

#ifndef PRODUCT
  int64_t _compiled_invocation_count;

  Symbol* _name;
#endif
  // Entry point for calling both from and to the interpreter.
  address _i2i_entry;           // All-args-on-stack calling convention
  // Entry point for calling from compiled code, to compiled code if it exists
  // or else the interpreter.
  volatile address _from_compiled_entry;     // Cache of: _code ? _code->entry_point() : _adapter->c2i_entry()
  // The entry point for calling both from and to compiled code is
  // "_code->entry_point()".  Because of tiered compilation and de-opt, this
  // field can come and go.  It can transition from null to not-null at any
  // time (whenever a compile completes).  It can transition from not-null to
  // null only at safepoints (because of a de-opt).
  nmethod* volatile _code;                   // Points to the corresponding piece of native code
  volatile address  _from_interpreted_entry; // Cache of _code ? _adapter->i2c_entry() : _i2i_entry

  // Constructor
  Method(ConstMethod* xconst, AccessFlags access_flags, Symbol* name);
 public:

  static Method* allocate(ClassLoaderData* loader_data,
                          int byte_code_size,
                          AccessFlags access_flags,
                          InlineTableSizes* sizes,
                          ConstMethod::MethodType method_type,
                          Symbol* name,
                          TRAPS);

  // CDS and vtbl checking can create an empty Method to get vtbl pointer.
  Method(){}

  virtual bool is_method() const { return true; }

#if INCLUDE_CDS
  void remove_unshareable_info();
  void restore_unshareable_info(TRAPS);
  static void restore_archived_method_handle_intrinsic(methodHandle m, TRAPS);
#endif

  // accessors for instance variables

  ConstMethod* constMethod() const             { return _constMethod; }
  void set_constMethod(ConstMethod* xconst)    { _constMethod = xconst; }


  static address make_adapters(const methodHandle& mh, TRAPS);
  address from_compiled_entry() const;
  address from_interpreted_entry() const;

  // access flag
  AccessFlags access_flags() const               { return _access_flags;  }
  void set_access_flags(AccessFlags flags)       { _access_flags = flags; }

  // name
  Symbol* name() const                           { return constants()->symbol_at(name_index()); }
  u2 name_index() const                          { return constMethod()->name_index();         }
  void set_name_index(int index)                 { constMethod()->set_name_index(index);       }

  // signature
  Symbol* signature() const                      { return constants()->symbol_at(signature_index()); }
  u2 signature_index() const                     { return constMethod()->signature_index();         }
  void set_signature_index(int index)            { constMethod()->set_signature_index(index);       }

  // generics support
  Symbol* generic_signature() const              { int idx = generic_signature_index(); return ((idx != 0) ? constants()->symbol_at(idx) : nullptr); }
  u2 generic_signature_index() const             { return constMethod()->generic_signature_index(); }

  // annotations support
  AnnotationArray* annotations() const           {
    return constMethod()->method_annotations();
  }
  AnnotationArray* parameter_annotations() const {
    return constMethod()->parameter_annotations();
  }
  AnnotationArray* annotation_default() const    {
    return constMethod()->default_annotations();
  }
  AnnotationArray* type_annotations() const      {
    return constMethod()->type_annotations();
  }

  // Helper routine: get klass name + "." + method name + signature as
  // C string, for the purpose of providing more useful
  // fatal error handling. The string is allocated in resource
  // area if a buffer is not provided by the caller.
  char* name_and_sig_as_C_string() const;
  char* name_and_sig_as_C_string(char* buf, int size) const;

  // Static routine in the situations we don't have a Method*
  static char* name_and_sig_as_C_string(Klass* klass, Symbol* method_name, Symbol* signature);
  static char* name_and_sig_as_C_string(Klass* klass, Symbol* method_name, Symbol* signature, char* buf, int size);

  // Get return type + klass name + "." + method name + ( parameters types )
  // as a C string or print it to an outputStream.
  // This is to be used to assemble strings passed to Java, so that
  // the text more resembles Java code. Used in exception messages.
  // Memory is allocated in the resource area; the caller needs
  // a ResourceMark.
  const char* external_name() const;
  void  print_external_name(outputStream *os) const;

  static const char* external_name(                  Klass* klass, Symbol* method_name, Symbol* signature);
  static void  print_external_name(outputStream *os, Klass* klass, Symbol* method_name, Symbol* signature);

  Bytecodes::Code java_code_at(int bci) const {
    return Bytecodes::java_code_at(this, bcp_from(bci));
  }
  Bytecodes::Code code_at(int bci) const {
    return Bytecodes::code_at(this, bcp_from(bci));
  }

  // JVMTI breakpoints
#if !INCLUDE_JVMTI
  Bytecodes::Code orig_bytecode_at(int bci) const {
    ShouldNotReachHere();
    return Bytecodes::_shouldnotreachhere;
  }
  void set_orig_bytecode_at(int bci, Bytecodes::Code code) {
    ShouldNotReachHere();
  };
  u2   number_of_breakpoints() const {return 0;}
#else // !INCLUDE_JVMTI
  Bytecodes::Code orig_bytecode_at(int bci) const;
  void set_orig_bytecode_at(int bci, Bytecodes::Code code);
  void set_breakpoint(int bci);
  void clear_breakpoint(int bci);
  void clear_all_breakpoints();
  // Tracking number of breakpoints, for fullspeed debugging.
  // Only mutated by VM thread.
  inline u2 number_of_breakpoints() const;
  inline void incr_number_of_breakpoints(Thread* current);
  inline void decr_number_of_breakpoints(Thread* current);
  // Initialization only
  inline void clear_number_of_breakpoints();
#endif // !INCLUDE_JVMTI

  // index into InstanceKlass methods() array
  // note: also used by jfr
  u2 method_idnum() const           { return constMethod()->method_idnum(); }
  void set_method_idnum(u2 idnum)   { constMethod()->set_method_idnum(idnum); }

  u2 orig_method_idnum() const           { return constMethod()->orig_method_idnum(); }
  void set_orig_method_idnum(u2 idnum)   { constMethod()->set_orig_method_idnum(idnum); }

  // code size
  u2 code_size() const                   { return constMethod()->code_size(); }

  // method size in words
  int method_size() const                { return sizeof(Method)/wordSize + ( is_native() ? 2 : 0 ); }

  // constant pool for Klass* holding this method
  ConstantPool* constants() const              { return constMethod()->constants(); }
  void set_constants(ConstantPool* c)          { constMethod()->set_constants(c); }

  // max stack
  // return original max stack size for method verification
  u2  verifier_max_stack() const               { return constMethod()->max_stack(); }
  int          max_stack() const               { return constMethod()->max_stack() + extra_stack_entries(); }
  void      set_max_stack(int size)            {        constMethod()->set_max_stack(size); }

  // max locals
  u2  max_locals() const                       { return constMethod()->max_locals(); }
  void set_max_locals(int size)                { constMethod()->set_max_locals(size); }

  void set_deprecated() { constMethod()->set_deprecated(); }
  bool deprecated() const { return constMethod()->deprecated(); }

  void set_deprecated_for_removal() { constMethod()->set_deprecated_for_removal(); }
  bool deprecated_for_removal() const { return constMethod()->deprecated_for_removal(); }

  inline int highest_comp_level() const;
  void set_highest_comp_level(int level);
  int highest_osr_comp_level() const;
  void set_highest_osr_comp_level(int level);

#if COMPILER2_OR_JVMCI
  // Count of times method was exited via exception while interpreting
  inline void interpreter_throwout_increment(Thread* current);
#endif

  inline int interpreter_throwout_count() const;

  u2 size_of_parameters() const { return constMethod()->size_of_parameters(); }

  bool has_stackmap_table() const {
    return constMethod()->has_stackmap_table();
  }

  Array<u1>* stackmap_data() const {
    return constMethod()->stackmap_data();
  }

  void set_stackmap_data(Array<u1>* sd) {
    constMethod()->set_stackmap_data(sd);
  }

  // exception handler table
  bool has_exception_handler() const
                             { return constMethod()->has_exception_table(); }
  u2 exception_table_length() const
                             { return constMethod()->exception_table_length(); }
  ExceptionTableElement* exception_table_start() const
                             { return constMethod()->exception_table_start(); }

  // Finds the first entry point bci of an exception handler for an
  // exception of klass ex_klass thrown at throw_bci. A value of null
  // for ex_klass indicates that the exception klass is not known; in
  // this case it matches any constraint class. Returns -1 if the
  // exception cannot be handled in this method. The handler
  // constraint classes are loaded if necessary. Note that this may
  // throw an exception if loading of the constraint classes causes
  // an IllegalAccessError (bugid 4307310) or an OutOfMemoryError.
  // If an exception is thrown, returns the bci of the
  // exception handler which caused the exception to be thrown, which
  // is needed for proper retries. See, for example,
  // InterpreterRuntime::exception_handler_for_exception.
  static int fast_exception_handler_bci_for(const methodHandle& mh, Klass* ex_klass, int throw_bci, TRAPS);

  static bool register_native(Klass* k,
                              Symbol* name,
                              Symbol* signature,
                              address entry,
                              TRAPS);

  // method data access
  MethodData* method_data() const {
    return _method_data;
  }
  void set_method_data(MethodData* data);

  MethodTrainingData* training_data_or_null() const;
  bool init_training_data(MethodTrainingData* td);

  // mark an exception handler as entered (used to prune dead catch blocks in C2)
  void set_exception_handler_entered(int handler_bci);

  MethodCounters* method_counters() const {
    return _method_counters;
  }

  void clear_method_counters() {
    _method_counters = nullptr;
  }

  bool init_method_counters(MethodCounters* counters);

  inline int prev_event_count() const;
  inline void set_prev_event_count(int count);
  inline jlong prev_time() const;
  inline void set_prev_time(jlong time);
  inline float rate() const;
  inline void set_rate(float rate);

  inline int invocation_count() const;
  inline int backedge_count() const;

  bool was_executed_more_than(int n);
  bool was_never_executed()                     { return !was_executed_more_than(0);  }

  static void build_profiling_method_data(const methodHandle& method, TRAPS);
  static bool install_training_method_data(const methodHandle& method);
  static MethodCounters* build_method_counters(Thread* current, Method* m);

  inline int interpreter_invocation_count() const;

#ifndef PRODUCT
  int64_t  compiled_invocation_count() const    { return _compiled_invocation_count;}
  void set_compiled_invocation_count(int count) { _compiled_invocation_count = (int64_t)count; }
#else
  // for PrintMethodData in a product build
  int64_t  compiled_invocation_count() const    { return 0; }
#endif // not PRODUCT

  // nmethod/verified compiler entry
  address verified_code_entry();
  bool check_code() const;      // Not inline to avoid circular ref
  nmethod* code() const;

  // Locks NMethodState_lock if not held.
  void unlink_code(nmethod *compare);
  // Locks NMethodState_lock if not held.
  void unlink_code();

private:
  // Either called with NMethodState_lock held or from constructor.
  void clear_code();

  void clear_method_data() {
    _method_data = nullptr;
  }

public:
  static void set_code(const methodHandle& mh, nmethod* code);
  void set_adapter_entry(AdapterHandlerEntry* adapter) {
    _adapter = adapter;
  }
  void set_from_compiled_entry(address entry) {
    _from_compiled_entry =  entry;
  }

  address get_i2c_entry();
  address get_c2i_entry();
  address get_c2i_unverified_entry();
  address get_c2i_no_clinit_check_entry();
  AdapterHandlerEntry* adapter() const {
    return _adapter;
  }
  // setup entry points
  void link_method(const methodHandle& method, TRAPS);
  // clear entry points. Used by sharing code during dump time
  void unlink_method() NOT_CDS_RETURN;
  void remove_unshareable_flags() NOT_CDS_RETURN;

  virtual void metaspace_pointers_do(MetaspaceClosure* iter);
  virtual MetaspaceObj::Type type() const { return MethodType; }

  // vtable index
  enum VtableIndexFlag {
    // Valid vtable indexes are non-negative (>= 0).
    // These few negative values are used as sentinels.
    itable_index_max        = -10, // first itable index, growing downward
    pending_itable_index    = -9,  // itable index will be assigned
    invalid_vtable_index    = -4,  // distinct from any valid vtable index
    garbage_vtable_index    = -3,  // not yet linked; no vtable layout yet
    nonvirtual_vtable_index = -2   // there is no need for vtable dispatch
    // 6330203 Note:  Do not use -1, which was overloaded with many meanings.
  };
  DEBUG_ONLY(bool valid_vtable_index() const     { return _vtable_index >= nonvirtual_vtable_index; })
  bool has_vtable_index() const                  { return _vtable_index >= 0; }
  int  vtable_index() const                      { return _vtable_index; }
  void set_vtable_index(int index);
  DEBUG_ONLY(bool valid_itable_index() const     { return _vtable_index <= pending_itable_index; })
  bool has_itable_index() const                  { return _vtable_index <= itable_index_max; }
  int  itable_index() const                      { assert(valid_itable_index(), "");
                                                   return itable_index_max - _vtable_index; }
  void set_itable_index(int index);

  // interpreter entry
  address interpreter_entry() const              { return _i2i_entry; }
  // Only used when first initialize so we can set _i2i_entry and _from_interpreted_entry
  void set_interpreter_entry(address entry) {
    if (_i2i_entry != entry) {
      _i2i_entry = entry;
    }
    if (_from_interpreted_entry != entry) {
      _from_interpreted_entry = entry;
    }
  }

  // native function (used for native methods only)
  enum {
    native_bind_event_is_interesting = true
  };
  address native_function() const                { return *(native_function_addr()); }

  // Must specify a real function (not null).
  // Use clear_native_function() to unregister.
  void set_native_function(address function, bool post_event_flag);
  bool has_native_function() const;
  void clear_native_function();

  // signature handler (used for native methods only)
  address signature_handler() const              { return *(signature_handler_addr()); }
  void set_signature_handler(address handler);

  // Interpreter oopmap support.
  // If handle is already available, call with it for better performance.
  void mask_for(int bci, InterpreterOopMap* mask);
  void mask_for(const methodHandle& this_mh, int bci, InterpreterOopMap* mask);

  // operations on invocation counter
  void print_invocation_count(outputStream* st);

  // byte codes
  void    set_code(address code)      { return constMethod()->set_code(code); }
  address code_base() const           { return constMethod()->code_base(); }
  bool    contains(address bcp) const { return constMethod()->contains(bcp); }

  // prints byte codes
  void print_codes(int flags = 0) const { print_codes_on(tty, flags); }
  void print_codes_on(outputStream* st, int flags = 0) const;
  void print_codes_on(int from, int to, outputStream* st, int flags = 0) const;

  // method parameters
  bool has_method_parameters() const
                         { return constMethod()->has_method_parameters(); }
  int method_parameters_length() const
                         { return constMethod()->method_parameters_length(); }
  MethodParametersElement* method_parameters_start() const
                         { return constMethod()->method_parameters_start(); }

  // checked exceptions
  u2 checked_exceptions_length() const
                         { return constMethod()->checked_exceptions_length(); }
  CheckedExceptionElement* checked_exceptions_start() const
                         { return constMethod()->checked_exceptions_start(); }

  // localvariable table
  bool has_localvariable_table() const
                          { return constMethod()->has_localvariable_table(); }
  u2 localvariable_table_length() const
                        { return constMethod()->localvariable_table_length(); }
  LocalVariableTableElement* localvariable_table_start() const
                         { return constMethod()->localvariable_table_start(); }

  bool has_linenumber_table() const
                              { return constMethod()->has_linenumber_table(); }
  u_char* compressed_linenumber_table() const
                       { return constMethod()->compressed_linenumber_table(); }

  // method holder (the Klass* holding this method)
  InstanceKlass* method_holder() const         { return constants()->pool_holder(); }

  Symbol* klass_name() const;                    // returns the name of the method holder
  BasicType result_type() const                  { return constMethod()->result_type(); }
  bool is_returning_oop() const                  { BasicType r = result_type(); return is_reference_type(r); }
  bool is_returning_fp() const                   { BasicType r = result_type(); return (r == T_FLOAT || r == T_DOUBLE); }

  // Checked exceptions thrown by this method (resolved to mirrors)
  objArrayHandle resolved_checked_exceptions(TRAPS) { return resolved_checked_exceptions_impl(this, THREAD); }

  // Access flags
  bool is_public() const                         { return access_flags().is_public();      }
  bool is_private() const                        { return access_flags().is_private();     }
  bool is_protected() const                      { return access_flags().is_protected();   }
  bool is_package_private() const                { return !is_public() && !is_private() && !is_protected(); }
  bool is_static() const                         { return access_flags().is_static();      }
  bool is_final() const                          { return access_flags().is_final();       }
  bool is_synchronized() const                   { return access_flags().is_synchronized();}
  bool is_native() const                         { return access_flags().is_native();      }
  bool is_abstract() const                       { return access_flags().is_abstract();    }
  bool is_synthetic() const                      { return access_flags().is_synthetic();   }

  // returns true if contains only return operation
  bool is_empty_method() const;

  // returns true if this is a vanilla constructor
  bool is_vanilla_constructor() const;

  // checks method and its method holder
  bool is_final_method() const;
  bool is_final_method(AccessFlags class_access_flags) const;
  // interface method declared with 'default' - excludes private interface methods
  bool is_default_method() const;

  // true if method needs no dynamic dispatch (final and/or no vtable entry)
  bool can_be_statically_bound() const;
  bool can_be_statically_bound(InstanceKlass* context) const;
  bool can_be_statically_bound(AccessFlags class_access_flags) const;

  // true if method can omit stack trace in throw in compiled code.
  bool can_omit_stack_trace();

  // Flags getting and setting.
#define M_STATUS_GET_SET(name, ignore)          \
  bool name() const { return _flags.name(); }   \
  void set_##name(bool x) { _flags.set_##name(x); } \
  void set_##name() { _flags.set_##name(true); }
  M_STATUS_DO(M_STATUS_GET_SET)
#undef M_STATUS_GET_SET

  // returns true if the method has any backward branches.
  bool has_loops() {
    return has_loops_flag_init() ? has_loops_flag() : compute_has_loops_flag();
  };

  bool compute_has_loops_flag();
  bool set_has_loops() {
    // set both the flags and that it's been initialized.
    set_has_loops_flag();
    set_has_loops_flag_init();
    return true;
  }

  // returns true if the method has any monitors.
  bool has_monitors() const                      { return is_synchronized() || has_monitor_bytecodes(); }

  // monitor matching. This returns a conservative estimate of whether the monitorenter/monitorexit bytecodes
  // properly nest in the method. It might return false, even though they actually nest properly, since the info.
  // has not been computed yet.
  bool guaranteed_monitor_matching() const       { return monitor_matching(); }
  void set_guaranteed_monitor_matching()         { set_monitor_matching(); }

  // returns true if the method is an accessor function (setter/getter).
  bool is_accessor() const;

  // returns true if the method is a getter
  bool is_getter() const;

  // returns true if the method is a setter
  bool is_setter() const;

  // returns true if the method does nothing but return a constant of primitive type
  bool is_constant_getter() const;

  // returns true if the method is static OR if the classfile version < 51
  bool has_valid_initializer_flags() const;

  // returns true if the method name is <clinit> and the method has
  // valid static initializer flags.
  bool is_static_initializer() const;

  // returns true if the method name is <init>
  bool is_object_initializer() const;

  // returns true if the method name is wait0
  bool is_object_wait0() const;

  // compiled code support
  // NOTE: code() is inherently racy as deopt can be clearing code
  // simultaneously. Use with caution.
  bool has_compiled_code() const;

  bool needs_clinit_barrier() const;

  // sizing
  static int header_size()                       {
    return align_up((int)sizeof(Method), wordSize) / wordSize;
  }
  static int size(bool is_native);
  int size() const                               { return method_size(); }
  void log_touched(Thread* current);
  static void print_touched_methods(outputStream* out);

  // interpreter support
  static ByteSize const_offset()                 { return byte_offset_of(Method, _constMethod       ); }
  static ByteSize access_flags_offset()          { return byte_offset_of(Method, _access_flags      ); }
  static ByteSize from_compiled_offset()         { return byte_offset_of(Method, _from_compiled_entry); }
  static ByteSize code_offset()                  { return byte_offset_of(Method, _code); }

  static ByteSize method_counters_offset()       {
    return byte_offset_of(Method, _method_counters);
  }
#ifndef PRODUCT
  static ByteSize compiled_invocation_counter_offset() { return byte_offset_of(Method, _compiled_invocation_count); }
#endif // not PRODUCT
  static ByteSize native_function_offset()       { return in_ByteSize(sizeof(Method));                 }
  static ByteSize from_interpreted_offset()      { return byte_offset_of(Method, _from_interpreted_entry ); }
  static ByteSize interpreter_entry_offset()     { return byte_offset_of(Method, _i2i_entry ); }
  static ByteSize signature_handler_offset()     { return in_ByteSize(sizeof(Method) + wordSize);      }
  static ByteSize itable_index_offset()          { return byte_offset_of(Method, _vtable_index ); }

  // for code generation
  static ByteSize method_data_offset()  { return byte_offset_of(Method, _method_data); }
  static ByteSize intrinsic_id_offset() { return byte_offset_of(Method, _intrinsic_id); }
  static int intrinsic_id_size_in_bytes()        { return sizeof(u2); }

  // Static methods that are used to implement member methods where an exposed this pointer
  // is needed due to possible GCs
  static objArrayHandle resolved_checked_exceptions_impl(Method* method, TRAPS);

  // Returns the byte code index from the byte code pointer
  int     bci_from(address bcp) const;
  address bcp_from(int bci) const;
  address bcp_from(address bcp) const;
  int validate_bci_from_bcp(address bcp) const;
  int validate_bci(int bci) const;

  // Returns the line number for a bci if debugging information for the method is prowided,
  // -1 is returned otherwise.
  int line_number_from_bci(int bci) const;

  // Reflection support
  bool is_overridden_in(Klass* k) const;

  // Stack walking support
  bool is_ignored_by_security_stack_walk() const;

  // JSR 292 support
  bool is_method_handle_intrinsic() const;          // MethodHandles::is_signature_polymorphic_intrinsic(intrinsic_id)
  bool is_compiled_lambda_form() const;             // intrinsic_id() == vmIntrinsics::_compiledLambdaForm
  bool has_member_arg() const;                      // intrinsic_id() == vmIntrinsics::_linkToSpecial, etc.
  static methodHandle make_method_handle_intrinsic(vmIntrinsicID iid, // _invokeBasic, _linkToVirtual
                                                   Symbol* signature, //anything at all
                                                   TRAPS);
  // Some special methods don't need to be findable by nmethod iterators and are permanent.
  bool can_be_allocated_in_NonNMethod_space() const { return is_method_handle_intrinsic(); }

  // Continuation
  inline bool is_continuation_enter_intrinsic() const;
  inline bool is_continuation_yield_intrinsic() const;
  inline bool is_continuation_native_intrinsic() const;
  inline bool is_special_native_intrinsic() const;

  static Klass* check_non_bcp_klass(Klass* klass);

  enum {
    // How many extra stack entries for invokedynamic
    extra_stack_entries_for_jsr292 = 1
  };

  // this operates only on invoke methods:
  // presize interpreter frames for extra interpreter stack entries, if needed
  // Account for the extra appendix argument for invokehandle/invokedynamic
  static int extra_stack_entries() { return extra_stack_entries_for_jsr292; }
  static int extra_stack_words();  // = extra_stack_entries() * Interpreter::stackElementSize

  // RedefineClasses() support:
  bool on_stack() const                             { return on_stack_flag(); }
  void set_on_stack(const bool value);

  void record_gc_epoch();

  // see the definition in Method*.cpp for the gory details
  bool should_not_be_cached() const;

  // Rewriting support
  static methodHandle clone_with_new_data(const methodHandle& m, u_char* new_code, int new_code_length,
                                          u_char* new_compressed_linenumber_table, int new_compressed_linenumber_size, TRAPS);

  // jmethodID handling
  // Because the useful life-span of a jmethodID cannot be determined,
  // once created they are never reclaimed.  The methods to which they refer,
  // however, can be GC'ed away if the class is unloaded or if the method is
  // made obsolete or deleted -- in these cases, the jmethodID
  // refers to null (as is the case for any weak reference).
  static jmethodID make_jmethod_id(ClassLoaderData* cld, Method* mh);

  // Use resolve_jmethod_id() in situations where the caller is expected
  // to provide a valid jmethodID; the only sanity checks are in asserts;
  // result guaranteed not to be null.
  static Method* resolve_jmethod_id(jmethodID mid);

  // Use checked_resolve_jmethod_id() in situations where the caller
  // should provide a valid jmethodID, but might not. Null is returned
  // when the jmethodID does not refer to a valid method.
  static Method* checked_resolve_jmethod_id(jmethodID mid);

  static void change_method_associated_with_jmethod_id(jmethodID old_jmid_ptr, Method* new_method);
  static bool validate_jmethod_id(jmethodID mid);

  // Clear jmethodID
  void clear_jmethod_id();
  static void print_jmethod_ids_count(const ClassLoaderData* loader_data, outputStream* out) PRODUCT_RETURN;

  // Get this method's jmethodID -- allocate if it doesn't exist
  jmethodID jmethod_id();

  // Lookup the jmethodID for this method.  Return null if not found.
  // NOTE that this function can be called from a signal handler
  // (see AsyncGetCallTrace support for Forte Analyzer) and this
  // needs to be async-safe. No allocation should be done and
  // so handles are not used to avoid deadlock.
  jmethodID find_jmethod_id_or_null()               { return method_holder()->jmethod_id_or_null(this); }

  // Support for inlining of intrinsic methods
  vmIntrinsicID intrinsic_id() const          { return (vmIntrinsicID) _intrinsic_id;           }
  void     set_intrinsic_id(vmIntrinsicID id) {                           _intrinsic_id = (u2) id; }

  // Helper routines for intrinsic_id() and vmIntrinsics::method().
  void init_intrinsic_id(vmSymbolID klass_id);     // updates from _none if a match
  static vmSymbolID klass_id_for_intrinsics(const Klass* holder);

  bool caller_sensitive() const     { return constMethod()->caller_sensitive(); }
  void set_caller_sensitive() { constMethod()->set_caller_sensitive(); }

  bool changes_current_thread() const { return constMethod()->changes_current_thread(); }
  void set_changes_current_thread() { constMethod()->set_changes_current_thread(); }

  bool jvmti_hide_events() const { return constMethod()->jvmti_hide_events(); }
  void set_jvmti_hide_events() { constMethod()->set_jvmti_hide_events(); }

  bool jvmti_mount_transition() const { return constMethod()->jvmti_mount_transition(); }
  void set_jvmti_mount_transition() { constMethod()->set_jvmti_mount_transition(); }

  bool is_hidden() const { return constMethod()->is_hidden(); }
  void set_is_hidden() { constMethod()->set_is_hidden(); }

  bool is_scoped() const { return constMethod()->is_scoped(); }
  void set_scoped() { constMethod()->set_is_scoped(); }

  bool intrinsic_candidate() const { return constMethod()->intrinsic_candidate(); }
  void set_intrinsic_candidate() { constMethod()->set_intrinsic_candidate(); }

  bool has_injected_profile() const { return constMethod()->has_injected_profile(); }
  void set_has_injected_profile() { constMethod()->set_has_injected_profile(); }

  bool has_reserved_stack_access() const { return constMethod()->reserved_stack_access(); }
  void set_has_reserved_stack_access() { constMethod()->set_reserved_stack_access(); }

  JFR_ONLY(DEFINE_TRACE_FLAG_ACCESSOR;)

  ConstMethod::MethodType method_type() const {
      return _constMethod->method_type();
  }
  bool is_overpass() const { return method_type() == ConstMethod::OVERPASS; }

  // On-stack replacement support
  bool has_osr_nmethod(int level, bool match_level) {
   return method_holder()->lookup_osr_nmethod(this, InvocationEntryBci, level, match_level) != nullptr;
  }

  nmethod* lookup_osr_nmethod_for(int bci, int level, bool match_level) {
    return method_holder()->lookup_osr_nmethod(this, bci, level, match_level);
  }

  // Find if klass for method is loaded
  bool is_klass_loaded_by_klass_index(int klass_index) const;
  bool is_klass_loaded(int refinfo_index, Bytecodes::Code bc, bool must_be_resolved = false) const;

  // Indicates whether compilation failed earlier for this method, or
  // whether it is not compilable for another reason like having a
  // breakpoint set in it.
  bool  is_not_compilable(int comp_level = CompLevel_any) const;
  void set_not_compilable(const char* reason, int comp_level = CompLevel_all, bool report = true);
  void set_not_compilable_quietly(const char* reason, int comp_level = CompLevel_all) {
    set_not_compilable(reason, comp_level, false);
  }
  bool  is_not_osr_compilable(int comp_level = CompLevel_any) const;
  void set_not_osr_compilable(const char* reason, int comp_level = CompLevel_all, bool report = true);
  void set_not_osr_compilable_quietly(const char* reason, int comp_level = CompLevel_all) {
    set_not_osr_compilable(reason, comp_level, false);
  }
  bool is_always_compilable() const;

 private:
  void print_made_not_compilable(int comp_level, bool is_osr, bool report, const char* reason);

 public:
  MethodCounters* get_method_counters(Thread* current) {
    if (_method_counters == nullptr) {
      build_method_counters(current, this);
    }
    return _method_counters;
  }

  void clear_is_not_c1_compilable()           { set_is_not_c1_compilable(false); }
  void clear_is_not_c2_compilable()           { set_is_not_c2_compilable(false); }
  void clear_is_not_c2_osr_compilable()       { set_is_not_c2_osr_compilable(false); }

  // not_c1_osr_compilable == not_c1_compilable
  bool is_not_c1_osr_compilable() const       { return is_not_c1_compilable(); }
  void set_is_not_c1_osr_compilable()         { set_is_not_c1_compilable(); }
  void clear_is_not_c1_osr_compilable()       { clear_is_not_c1_compilable(); }

  // Background compilation support
  void clear_queued_for_compilation()  { set_queued_for_compilation(false);   }

  // Resolve all classes in signature, return 'true' if successful
  static bool load_signature_classes(const methodHandle& m, TRAPS);

  // Printing
  void print_short_name(outputStream* st = tty) const; // prints as klassname::methodname; Exposed so field engineers can debug VM
#if INCLUDE_JVMTI
  void print_name(outputStream* st = tty) const; // prints as "virtual void foo(int)"; exposed for -Xlog:redefine+class
#else
  void print_name(outputStream* st = tty) const  PRODUCT_RETURN; // prints as "virtual void foo(int)"
#endif

  typedef int (*method_comparator_func)(Method* a, Method* b);

  // Helper routine used for method sorting
  static void sort_methods(Array<Method*>* methods, bool set_idnums = true, method_comparator_func func = nullptr);

  // Deallocation function for redefine classes or if an error occurs
  void deallocate_contents(ClassLoaderData* loader_data);

  void release_C_heap_structures();

  Method* get_new_method() const {
    InstanceKlass* holder = method_holder();
    Method* new_method = holder->method_with_idnum(orig_method_idnum());

    assert(new_method != nullptr, "method_with_idnum() should not be null");
    assert(this != new_method, "sanity check");
    return new_method;
  }

  // Printing
#ifndef PRODUCT
  void print_on(outputStream* st) const;
#endif
  void print_value_on(outputStream* st) const;
  void print_linkage_flags(outputStream* st) PRODUCT_RETURN;

  const char* internal_name() const { return "{method}"; }

  // Check for valid method pointer
  static bool has_method_vptr(const void* ptr);
  static bool is_valid_method(const Method* m);

  // Verify
  void verify() { verify_on(tty); }
  void verify_on(outputStream* st);

 private:

  // Inlined elements
  address* native_function_addr() const          { assert(is_native(), "must be native"); return (address*) (this+1); }
  address* signature_handler_addr() const        { return native_function_addr() + 1; }
};


// Utility class for compressing line number tables

class CompressedLineNumberWriteStream: public CompressedWriteStream {
 private:
  int _bci;
  int _line;
 public:
  // Constructor
  CompressedLineNumberWriteStream(int initial_size) : CompressedWriteStream(initial_size), _bci(0), _line(0) {}
  CompressedLineNumberWriteStream(u_char* buffer, int initial_size) : CompressedWriteStream(buffer, initial_size), _bci(0), _line(0) {}

  // Write (bci, line number) pair to stream
  void write_pair_regular(int bci_delta, int line_delta);

  // If (bci delta, line delta) fits in (5-bit unsigned, 3-bit unsigned)
  // we save it as one byte, otherwise we write a 0xFF escape character
  // and use regular compression. 0x0 is used as end-of-stream terminator.
  void write_pair_inline(int bci, int line);

  void write_pair(int bci, int line);

  // Write end-of-stream marker
  void write_terminator()                        { write_byte(0); }
};


// Utility class for decompressing line number tables

class CompressedLineNumberReadStream: public CompressedReadStream {
 private:
  int _bci;
  int _line;
 public:
  // Constructor
  CompressedLineNumberReadStream(u_char* buffer);
  // Read (bci, line number) pair from stream. Returns false at end-of-stream.
  bool read_pair();
  // Accessing bci and line number (after calling read_pair)
  int bci() const                               { return _bci; }
  int line() const                              { return _line; }
};


#if INCLUDE_JVMTI

/// Fast Breakpoints.

// If this structure gets more complicated (because bpts get numerous),
// move it into its own header.

// There is presently no provision for concurrent access
// to breakpoint lists, which is only OK for JVMTI because
// breakpoints are written only at safepoints, and are read
// concurrently only outside of safepoints.

class BreakpointInfo : public CHeapObj<mtClass> {
  friend class VMStructs;
 private:
  Bytecodes::Code  _orig_bytecode;
  int              _bci;
  u2               _name_index;       // of method
  u2               _signature_index;  // of method
  BreakpointInfo*  _next;             // simple storage allocation

 public:
  BreakpointInfo(Method* m, int bci);

  // accessors
  Bytecodes::Code orig_bytecode()                     { return _orig_bytecode; }
  void        set_orig_bytecode(Bytecodes::Code code) { _orig_bytecode = code; }
  int         bci()                                   { return _bci; }

  BreakpointInfo*          next() const               { return _next; }
  void                 set_next(BreakpointInfo* n)    { _next = n; }

  // helps for searchers
  bool match(const Method* m, int bci) {
    return bci == _bci && match(m);
  }

  bool match(const Method* m) {
    return _name_index == m->name_index() &&
      _signature_index == m->signature_index();
  }

  void set(Method* method);
  void clear(Method* method);
};

#endif // INCLUDE_JVMTI

// Utility class for access exception handlers
class ExceptionTable : public StackObj {
 private:
  ExceptionTableElement* _table;
  u2  _length;

 public:
  ExceptionTable(const Method* m) {
    if (m->has_exception_handler()) {
      _table = m->exception_table_start();
      _length = m->exception_table_length();
    } else {
      _table = nullptr;
      _length = 0;
    }
  }

  u2 length() const {
    return _length;
  }

  u2 start_pc(int idx) const {
    assert(idx < _length, "out of bounds");
    return _table[idx].start_pc;
  }

  void set_start_pc(int idx, u2 value) {
    assert(idx < _length, "out of bounds");
    _table[idx].start_pc = value;
  }

  u2 end_pc(int idx) const {
    assert(idx < _length, "out of bounds");
    return _table[idx].end_pc;
  }

  void set_end_pc(int idx, u2 value) {
    assert(idx < _length, "out of bounds");
    _table[idx].end_pc = value;
  }

  u2 handler_pc(int idx) const {
    assert(idx < _length, "out of bounds");
    return _table[idx].handler_pc;
  }

  void set_handler_pc(int idx, u2 value) {
    assert(idx < _length, "out of bounds");
    _table[idx].handler_pc = value;
  }

  u2 catch_type_index(int idx) const {
    assert(idx < _length, "out of bounds");
    return _table[idx].catch_type_index;
  }

  void set_catch_type_index(int idx, u2 value) {
    assert(idx < _length, "out of bounds");
    _table[idx].catch_type_index = value;
  }
};

#endif // SHARE_OOPS_METHOD_HPP
