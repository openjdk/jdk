/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

// A methodOop represents a Java method.
//
// Memory layout (each line represents a word). Note that most applications load thousands of methods,
// so keeping the size of this structure small has a big impact on footprint.
//
// We put all oops and method_size first for better gc cache locality.
//
// The actual bytecodes are inlined after the end of the methodOopDesc struct.
//
// There are bits in the access_flags telling whether inlined tables are present.
// Note that accessing the line number and local variable tables is not performance critical at all.
// Accessing the checked exceptions table is used by reflection, so we put that last to make access
// to it fast.
//
// The line number table is compressed and inlined following the byte codes. It is found as the first
// byte following the byte codes. The checked exceptions table and the local variable table are inlined
// after the line number table, and indexed from the end of the method. We do not compress the checked
// exceptions table since the average length is less than 2, and do not bother to compress the local
// variable table either since it is mostly absent.
//
// Note that native_function and signature_handler has to be at fixed offsets (required by the interpreter)
//
// |------------------------------------------------------|
// | header                                               |
// | klass                                                |
// |------------------------------------------------------|
// | constMethodOop                 (oop)                 |
// | constants                      (oop)                 |
// |------------------------------------------------------|
// | methodData                     (oop)                 |
// | interp_invocation_count                              |
// |------------------------------------------------------|
// | access_flags                                         |
// | vtable_index                                         |
// |------------------------------------------------------|
// | result_index (C++ interpreter only)                  |
// |------------------------------------------------------|
// | method_size             | max_stack                  |
// | max_locals              | size_of_parameters         |
// |------------------------------------------------------|
// | intrinsic_id, (unused)  |  throwout_count            |
// |------------------------------------------------------|
// | num_breakpoints         |  (unused)                  |
// |------------------------------------------------------|
// | invocation_counter                                   |
// | backedge_counter                                     |
// |------------------------------------------------------|
// | code                           (pointer)             |
// | i2i                            (pointer)             |
// | adapter                        (pointer)             |
// | from_compiled_entry            (pointer)             |
// | from_interpreted_entry         (pointer)             |
// |------------------------------------------------------|
// | native_function       (present only if native)       |
// | signature_handler     (present only if native)       |
// |------------------------------------------------------|


class CheckedExceptionElement;
class LocalVariableTableElement;
class AdapterHandlerEntry;
class methodDataOopDesc;

class methodOopDesc : public oopDesc {
 friend class methodKlass;
 friend class VMStructs;
 private:
  constMethodOop    _constMethod;                // Method read-only data.
  constantPoolOop   _constants;                  // Constant pool
  methodDataOop     _method_data;
  int               _interpreter_invocation_count; // Count of times invoked (reused as prev_event_count in tiered)
  AccessFlags       _access_flags;               // Access flags
  int               _vtable_index;               // vtable index of this method (see VtableIndexFlag)
                                                 // note: can have vtables with >2**16 elements (because of inheritance)
#ifdef CC_INTERP
  int               _result_index;               // C++ interpreter needs for converting results to/from stack
#endif
  u2                _method_size;                // size of this object
  u2                _max_stack;                  // Maximum number of entries on the expression stack
  u2                _max_locals;                 // Number of local variables used by this method
  u2                _size_of_parameters;         // size of the parameter block (receiver + arguments) in words
  u1                _intrinsic_id;               // vmSymbols::intrinsic_id (0 == _none)
  u2                _interpreter_throwout_count; // Count of times method was exited via exception while interpreting
  u2                _number_of_breakpoints;      // fullspeed debugging support
  InvocationCounter _invocation_counter;         // Incremented before each activation of the method - used to trigger frequency-based optimizations
  InvocationCounter _backedge_counter;           // Incremented before each backedge taken - used to trigger frequencey-based optimizations

#ifndef PRODUCT
  int               _compiled_invocation_count;  // Number of nmethod invocations so far (for perf. debugging)
#endif
  // Entry point for calling both from and to the interpreter.
  address _i2i_entry;           // All-args-on-stack calling convention
  // Adapter blob (i2c/c2i) for this methodOop. Set once when method is linked.
  AdapterHandlerEntry* _adapter;
  // Entry point for calling from compiled code, to compiled code if it exists
  // or else the interpreter.
  volatile address _from_compiled_entry;        // Cache of: _code ? _code->entry_point() : _adapter->c2i_entry()
  // The entry point for calling both from and to compiled code is
  // "_code->entry_point()".  Because of tiered compilation and de-opt, this
  // field can come and go.  It can transition from NULL to not-null at any
  // time (whenever a compile completes).  It can transition from not-null to
  // NULL only at safepoints (because of a de-opt).
  nmethod* volatile _code;                       // Points to the corresponding piece of native code
  volatile address           _from_interpreted_entry; // Cache of _code ? _adapter->i2c_entry() : _i2i_entry

 public:

  static const bool IsUnsafeConc         = false;
  static const bool IsSafeConc           = true;

  // accessors for instance variables
  constMethodOop constMethod() const             { return _constMethod; }
  void set_constMethod(constMethodOop xconst)    { oop_store_without_check((oop*)&_constMethod, (oop)xconst); }


  static address make_adapters(methodHandle mh, TRAPS);
  volatile address from_compiled_entry() const   { return (address)OrderAccess::load_ptr_acquire(&_from_compiled_entry); }
  volatile address from_interpreted_entry() const{ return (address)OrderAccess::load_ptr_acquire(&_from_interpreted_entry); }

  // access flag
  AccessFlags access_flags() const               { return _access_flags;  }
  void set_access_flags(AccessFlags flags)       { _access_flags = flags; }

  // name
  symbolOop name() const                         { return _constants->symbol_at(name_index()); }
  int name_index() const                         { return constMethod()->name_index();         }
  void set_name_index(int index)                 { constMethod()->set_name_index(index);       }

  // signature
  symbolOop signature() const                    { return _constants->symbol_at(signature_index()); }
  int signature_index() const                    { return constMethod()->signature_index();         }
  void set_signature_index(int index)            { constMethod()->set_signature_index(index);       }

  // generics support
  symbolOop generic_signature() const            { int idx = generic_signature_index(); return ((idx != 0) ? _constants->symbol_at(idx) : (symbolOop)NULL); }
  int generic_signature_index() const            { return constMethod()->generic_signature_index(); }
  void set_generic_signature_index(int index)    { constMethod()->set_generic_signature_index(index); }

  // annotations support
  typeArrayOop annotations() const               { return instanceKlass::cast(method_holder())->get_method_annotations_of(method_idnum()); }
  typeArrayOop parameter_annotations() const     { return instanceKlass::cast(method_holder())->get_method_parameter_annotations_of(method_idnum()); }
  typeArrayOop annotation_default() const        { return instanceKlass::cast(method_holder())->get_method_default_annotations_of(method_idnum()); }

#ifdef CC_INTERP
  void set_result_index(BasicType type);
  int  result_index()                            { return _result_index; }
#endif

  // Helper routine: get klass name + "." + method name + signature as
  // C string, for the purpose of providing more useful NoSuchMethodErrors
  // and fatal error handling. The string is allocated in resource
  // area if a buffer is not provided by the caller.
  char* name_and_sig_as_C_string();
  char* name_and_sig_as_C_string(char* buf, int size);

  // Static routine in the situations we don't have a methodOop
  static char* name_and_sig_as_C_string(Klass* klass, symbolOop method_name, symbolOop signature);
  static char* name_and_sig_as_C_string(Klass* klass, symbolOop method_name, symbolOop signature, char* buf, int size);

  // JVMTI breakpoints
  Bytecodes::Code orig_bytecode_at(int bci);
  void        set_orig_bytecode_at(int bci, Bytecodes::Code code);
  void set_breakpoint(int bci);
  void clear_breakpoint(int bci);
  void clear_all_breakpoints();
  // Tracking number of breakpoints, for fullspeed debugging.
  // Only mutated by VM thread.
  u2   number_of_breakpoints() const             { return _number_of_breakpoints; }
  void incr_number_of_breakpoints()              { ++_number_of_breakpoints; }
  void decr_number_of_breakpoints()              { --_number_of_breakpoints; }
  // Initialization only
  void clear_number_of_breakpoints()             { _number_of_breakpoints = 0; }

  // index into instanceKlass methods() array
  u2 method_idnum() const           { return constMethod()->method_idnum(); }
  void set_method_idnum(u2 idnum)   { constMethod()->set_method_idnum(idnum); }

  // code size
  int code_size() const                  { return constMethod()->code_size(); }

  // method size
  int method_size() const                        { return _method_size; }
  void set_method_size(int size) {
    assert(0 <= size && size < (1 << 16), "invalid method size");
    _method_size = size;
  }

  // constant pool for klassOop holding this method
  constantPoolOop constants() const              { return _constants; }
  void set_constants(constantPoolOop c)          { oop_store_without_check((oop*)&_constants, c); }

  // max stack
  int  max_stack() const                         { return _max_stack; }
  void set_max_stack(int size)                   { _max_stack = size; }

  // max locals
  int  max_locals() const                        { return _max_locals; }
  void set_max_locals(int size)                  { _max_locals = size; }

  int highest_comp_level() const;
  void set_highest_comp_level(int level);
  int highest_osr_comp_level() const;
  void set_highest_osr_comp_level(int level);

  // Count of times method was exited via exception while interpreting
  void interpreter_throwout_increment() {
    if (_interpreter_throwout_count < 65534) {
      _interpreter_throwout_count++;
    }
  }

  int  interpreter_throwout_count() const        { return _interpreter_throwout_count; }
  void set_interpreter_throwout_count(int count) { _interpreter_throwout_count = count; }

  // size of parameters
  int  size_of_parameters() const                { return _size_of_parameters; }

  bool has_stackmap_table() const {
    return constMethod()->has_stackmap_table();
  }

  typeArrayOop stackmap_data() const {
    return constMethod()->stackmap_data();
  }

  // exception handler table
  typeArrayOop exception_table() const
                                   { return constMethod()->exception_table(); }
  void set_exception_table(typeArrayOop e)
                                     { constMethod()->set_exception_table(e); }
  bool has_exception_handler() const
                             { return constMethod()->has_exception_handler(); }

  // Finds the first entry point bci of an exception handler for an
  // exception of klass ex_klass thrown at throw_bci. A value of NULL
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
  int fast_exception_handler_bci_for(KlassHandle ex_klass, int throw_bci, TRAPS);

  // method data access
  methodDataOop method_data() const              {
    return _method_data;
  }
  void set_method_data(methodDataOop data)       {
    oop_store_without_check((oop*)&_method_data, (oop)data);
  }

  // invocation counter
  InvocationCounter* invocation_counter() { return &_invocation_counter; }
  InvocationCounter* backedge_counter()   { return &_backedge_counter; }

  int invocation_count();
  int backedge_count();

  bool was_executed_more_than(int n);
  bool was_never_executed()                      { return !was_executed_more_than(0); }

  static void build_interpreter_method_data(methodHandle method, TRAPS);

  int interpreter_invocation_count() {
    if (TieredCompilation) return invocation_count();
    else return _interpreter_invocation_count;
  }
  void set_interpreter_invocation_count(int count) { _interpreter_invocation_count = count; }
  int increment_interpreter_invocation_count() {
    if (TieredCompilation) ShouldNotReachHere();
    return ++_interpreter_invocation_count;
  }

#ifndef PRODUCT
  int  compiled_invocation_count() const         { return _compiled_invocation_count;  }
  void set_compiled_invocation_count(int count)  { _compiled_invocation_count = count; }
#endif // not PRODUCT

  // Clear (non-shared space) pointers which could not be relevant
  // if this (shared) method were mapped into another JVM.
  void remove_unshareable_info();

  // nmethod/verified compiler entry
  address verified_code_entry();
  bool check_code() const;      // Not inline to avoid circular ref
  nmethod* volatile code() const                 { assert( check_code(), "" ); return (nmethod *)OrderAccess::load_ptr_acquire(&_code); }
  void clear_code();            // Clear out any compiled code
  static void set_code(methodHandle mh, nmethod* code);
  void set_adapter_entry(AdapterHandlerEntry* adapter) {  _adapter = adapter; }
  address get_i2c_entry();
  address get_c2i_entry();
  address get_c2i_unverified_entry();
  AdapterHandlerEntry* adapter() {  return _adapter; }
  // setup entry points
  void link_method(methodHandle method, TRAPS);
  // clear entry points. Used by sharing code
  void unlink_method();

  // vtable index
  enum VtableIndexFlag {
    // Valid vtable indexes are non-negative (>= 0).
    // These few negative values are used as sentinels.
    highest_unused_vtable_index_value = -5,
    invalid_vtable_index    = -4,  // distinct from any valid vtable index
    garbage_vtable_index    = -3,  // not yet linked; no vtable layout yet
    nonvirtual_vtable_index = -2   // there is no need for vtable dispatch
    // 6330203 Note:  Do not use -1, which was overloaded with many meanings.
  };
  DEBUG_ONLY(bool valid_vtable_index() const     { return _vtable_index >= nonvirtual_vtable_index; })
  int  vtable_index() const                      { assert(valid_vtable_index(), "");
                                                   return _vtable_index; }
  void set_vtable_index(int index)               { _vtable_index = index; }

  // interpreter entry
  address interpreter_entry() const              { return _i2i_entry; }
  // Only used when first initialize so we can set _i2i_entry and _from_interpreted_entry
  void set_interpreter_entry(address entry)      { _i2i_entry = entry;  _from_interpreted_entry = entry; }
  int  interpreter_kind(void) {
     return constMethod()->interpreter_kind();
  }
  void set_interpreter_kind();
  void set_interpreter_kind(int kind) {
    constMethod()->set_interpreter_kind(kind);
  }

  // native function (used for native methods only)
  enum {
    native_bind_event_is_interesting = true
  };
  address native_function() const                { return *(native_function_addr()); }
  // Must specify a real function (not NULL).
  // Use clear_native_function() to unregister.
  void set_native_function(address function, bool post_event_flag);
  bool has_native_function() const;
  void clear_native_function();

  // signature handler (used for native methods only)
  address signature_handler() const              { return *(signature_handler_addr()); }
  void set_signature_handler(address handler);

  // Interpreter oopmap support
  void mask_for(int bci, InterpreterOopMap* mask);

#ifndef PRODUCT
  // operations on invocation counter
  void print_invocation_count();
#endif

  // byte codes
  void    set_code(address code)      { return constMethod()->set_code(code); }
  address code_base() const           { return constMethod()->code_base(); }
  bool    contains(address bcp) const { return constMethod()->contains(bcp); }

  // prints byte codes
  void print_codes() const            { print_codes_on(tty); }
  void print_codes_on(outputStream* st) const                      PRODUCT_RETURN;
  void print_codes_on(int from, int to, outputStream* st) const    PRODUCT_RETURN;

  // checked exceptions
  int checked_exceptions_length() const
                         { return constMethod()->checked_exceptions_length(); }
  CheckedExceptionElement* checked_exceptions_start() const
                          { return constMethod()->checked_exceptions_start(); }

  // localvariable table
  bool has_localvariable_table() const
                          { return constMethod()->has_localvariable_table(); }
  int localvariable_table_length() const
                        { return constMethod()->localvariable_table_length(); }
  LocalVariableTableElement* localvariable_table_start() const
                         { return constMethod()->localvariable_table_start(); }

  bool has_linenumber_table() const
                              { return constMethod()->has_linenumber_table(); }
  u_char* compressed_linenumber_table() const
                       { return constMethod()->compressed_linenumber_table(); }

  // method holder (the klassOop holding this method)
  klassOop method_holder() const                 { return _constants->pool_holder(); }

  void compute_size_of_parameters(Thread *thread); // word size of parameters (receiver if any + arguments)
  symbolOop klass_name() const;                  // returns the name of the method holder
  BasicType result_type() const;                 // type of the method result
  int result_type_index() const;                 // type index of the method result
  bool is_returning_oop() const                  { BasicType r = result_type(); return (r == T_OBJECT || r == T_ARRAY); }
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
  bool is_strict() const                         { return access_flags().is_strict();      }
  bool is_synthetic() const                      { return access_flags().is_synthetic();   }

  // returns true if contains only return operation
  bool is_empty_method() const;

  // returns true if this is a vanilla constructor
  bool is_vanilla_constructor() const;

  // checks method and its method holder
  bool is_final_method() const;
  bool is_strict_method() const;

  // true if method needs no dynamic dispatch (final and/or no vtable entry)
  bool can_be_statically_bound() const;

  // returns true if the method has any backward branches.
  bool has_loops() {
    return access_flags().loops_flag_init() ? access_flags().has_loops() : compute_has_loops_flag();
  };

  bool compute_has_loops_flag();

  bool has_jsrs() {
    return access_flags().has_jsrs();
  };
  void set_has_jsrs() {
    _access_flags.set_has_jsrs();
  }

  // returns true if the method has any monitors.
  bool has_monitors() const                      { return is_synchronized() || access_flags().has_monitor_bytecodes(); }
  bool has_monitor_bytecodes() const             { return access_flags().has_monitor_bytecodes(); }

  void set_has_monitor_bytecodes()               { _access_flags.set_has_monitor_bytecodes(); }

  // monitor matching. This returns a conservative estimate of whether the monitorenter/monitorexit bytecodes
  // propererly nest in the method. It might return false, even though they actually nest properly, since the info.
  // has not been computed yet.
  bool guaranteed_monitor_matching() const       { return access_flags().is_monitor_matching(); }
  void set_guaranteed_monitor_matching()         { _access_flags.set_monitor_matching(); }

  // returns true if the method is an accessor function (setter/getter).
  bool is_accessor() const;

  // returns true if the method is an initializer (<init> or <clinit>).
  bool is_initializer() const;

  // compiled code support
  // NOTE: code() is inherently racy as deopt can be clearing code
  // simultaneously. Use with caution.
  bool has_compiled_code() const                 { return code() != NULL; }

  // sizing
  static int object_size(bool is_native);
  static int header_size()                       { return sizeof(methodOopDesc)/HeapWordSize; }
  int object_size() const                        { return method_size(); }

  bool object_is_parsable() const                { return method_size() > 0; }

  // interpreter support
  static ByteSize const_offset()                 { return byte_offset_of(methodOopDesc, _constMethod       ); }
  static ByteSize constants_offset()             { return byte_offset_of(methodOopDesc, _constants         ); }
  static ByteSize access_flags_offset()          { return byte_offset_of(methodOopDesc, _access_flags      ); }
#ifdef CC_INTERP
  static ByteSize result_index_offset()          { return byte_offset_of(methodOopDesc, _result_index ); }
#endif /* CC_INTERP */
  static ByteSize size_of_locals_offset()        { return byte_offset_of(methodOopDesc, _max_locals        ); }
  static ByteSize size_of_parameters_offset()    { return byte_offset_of(methodOopDesc, _size_of_parameters); }
  static ByteSize from_compiled_offset()         { return byte_offset_of(methodOopDesc, _from_compiled_entry); }
  static ByteSize code_offset()                  { return byte_offset_of(methodOopDesc, _code); }
  static ByteSize invocation_counter_offset()    { return byte_offset_of(methodOopDesc, _invocation_counter); }
  static ByteSize backedge_counter_offset()      { return byte_offset_of(methodOopDesc, _backedge_counter); }
  static ByteSize method_data_offset()           {
    return byte_offset_of(methodOopDesc, _method_data);
  }
  static ByteSize interpreter_invocation_counter_offset() { return byte_offset_of(methodOopDesc, _interpreter_invocation_count); }
#ifndef PRODUCT
  static ByteSize compiled_invocation_counter_offset() { return byte_offset_of(methodOopDesc, _compiled_invocation_count); }
#endif // not PRODUCT
  static ByteSize native_function_offset()       { return in_ByteSize(sizeof(methodOopDesc));                 }
  static ByteSize from_interpreted_offset()      { return byte_offset_of(methodOopDesc, _from_interpreted_entry ); }
  static ByteSize interpreter_entry_offset()     { return byte_offset_of(methodOopDesc, _i2i_entry ); }
  static ByteSize signature_handler_offset()     { return in_ByteSize(sizeof(methodOopDesc) + wordSize);      }
  static ByteSize max_stack_offset()             { return byte_offset_of(methodOopDesc, _max_stack         ); }

  // for code generation
  static int method_data_offset_in_bytes()       { return offset_of(methodOopDesc, _method_data); }
  static int interpreter_invocation_counter_offset_in_bytes()
                                                 { return offset_of(methodOopDesc, _interpreter_invocation_count); }

  // Static methods that are used to implement member methods where an exposed this pointer
  // is needed due to possible GCs
  static objArrayHandle resolved_checked_exceptions_impl(methodOop this_oop, TRAPS);

  // Returns the byte code index from the byte code pointer
  int     bci_from(address bcp) const;
  address bcp_from(int     bci) const;
  int validate_bci_from_bcx(intptr_t bcx) const;

  // Returns the line number for a bci if debugging information for the method is prowided,
  // -1 is returned otherwise.
  int line_number_from_bci(int bci) const;

  // Reflection support
  bool is_overridden_in(klassOop k) const;

  // JSR 292 support
  bool is_method_handle_invoke() const              { return access_flags().is_method_handle_invoke(); }
  static bool is_method_handle_invoke_name(vmSymbols::SID name_sid);
  static bool is_method_handle_invoke_name(symbolOop name) {
    return is_method_handle_invoke_name(vmSymbols::find_sid(name));
  }
  // Tests if this method is an internal adapter frame from the
  // MethodHandleCompiler.
  bool is_method_handle_adapter() const;
  static methodHandle make_invoke_method(KlassHandle holder,
                                         symbolHandle name, //invokeExact or invokeGeneric
                                         symbolHandle signature, //anything at all
                                         Handle method_type,
                                         TRAPS);
  // these operate only on invoke methods:
  oop method_handle_type() const;
  static jint* method_type_offsets_chain();  // series of pointer-offsets, terminated by -1
  // presize interpreter frames for extra interpreter stack entries, if needed
  // method handles want to be able to push a few extra values (e.g., a bound receiver), and
  // invokedynamic sometimes needs to push a bootstrap method, call site, and arglist,
  // all without checking for a stack overflow
  static int extra_stack_entries() { return (EnableMethodHandles ? (int)MethodHandlePushLimit : 0) + (EnableInvokeDynamic ? 3 : 0); }
  static int extra_stack_words();  // = extra_stack_entries() * Interpreter::stackElementSize()

  // RedefineClasses() support:
  bool is_old() const                               { return access_flags().is_old(); }
  void set_is_old()                                 { _access_flags.set_is_old(); }
  bool is_obsolete() const                          { return access_flags().is_obsolete(); }
  void set_is_obsolete()                            { _access_flags.set_is_obsolete(); }
  // see the definition in methodOop.cpp for the gory details
  bool should_not_be_cached() const;

  // JVMTI Native method prefixing support:
  bool is_prefixed_native() const                   { return access_flags().is_prefixed_native(); }
  void set_is_prefixed_native()                     { _access_flags.set_is_prefixed_native(); }

  // Rewriting support
  static methodHandle clone_with_new_data(methodHandle m, u_char* new_code, int new_code_length,
                                          u_char* new_compressed_linenumber_table, int new_compressed_linenumber_size, TRAPS);

  // Get this method's jmethodID -- allocate if it doesn't exist
  jmethodID jmethod_id()                            { methodHandle this_h(this);
                                                      return instanceKlass::get_jmethod_id(method_holder(), this_h); }

  // Lookup the jmethodID for this method.  Return NULL if not found.
  // NOTE that this function can be called from a signal handler
  // (see AsyncGetCallTrace support for Forte Analyzer) and this
  // needs to be async-safe. No allocation should be done and
  // so handles are not used to avoid deadlock.
  jmethodID find_jmethod_id_or_null()               { return instanceKlass::cast(method_holder())->jmethod_id_or_null(this); }

  // JNI static invoke cached itable index accessors
  int cached_itable_index()                         { return instanceKlass::cast(method_holder())->cached_itable_index(method_idnum()); }
  void set_cached_itable_index(int index)           { instanceKlass::cast(method_holder())->set_cached_itable_index(method_idnum(), index); }

  // Support for inlining of intrinsic methods
  vmIntrinsics::ID intrinsic_id() const          { return (vmIntrinsics::ID) _intrinsic_id;           }
  void     set_intrinsic_id(vmIntrinsics::ID id) {                           _intrinsic_id = (u1) id; }

  // Helper routines for intrinsic_id() and vmIntrinsics::method().
  void init_intrinsic_id();     // updates from _none if a match
  static vmSymbols::SID klass_id_for_intrinsics(klassOop holder);

  // On-stack replacement support
  bool has_osr_nmethod(int level, bool match_level) {
   return instanceKlass::cast(method_holder())->lookup_osr_nmethod(this, InvocationEntryBci, level, match_level) != NULL;
  }

  nmethod* lookup_osr_nmethod_for(int bci, int level, bool match_level) {
    return instanceKlass::cast(method_holder())->lookup_osr_nmethod(this, bci, level, match_level);
  }

  // Inline cache support
  void cleanup_inline_caches();

  // Find if klass for method is loaded
  bool is_klass_loaded_by_klass_index(int klass_index) const;
  bool is_klass_loaded(int refinfo_index, bool must_be_resolved = false) const;

  // Indicates whether compilation failed earlier for this method, or
  // whether it is not compilable for another reason like having a
  // breakpoint set in it.
  bool is_not_compilable(int comp_level = CompLevel_any) const;
  void set_not_compilable(int comp_level = CompLevel_all, bool report = true);
  void set_not_compilable_quietly(int comp_level = CompLevel_all) {
    set_not_compilable(comp_level, false);
  }
  bool is_not_osr_compilable(int comp_level = CompLevel_any) const {
    return is_not_compilable(comp_level) || access_flags().is_not_osr_compilable();
  }
  void set_not_osr_compilable()               { _access_flags.set_not_osr_compilable();       }
  bool is_not_c1_compilable() const           { return access_flags().is_not_c1_compilable(); }
  void set_not_c1_compilable()                { _access_flags.set_not_c1_compilable();        }
  bool is_not_c2_compilable() const           { return access_flags().is_not_c2_compilable(); }
  void set_not_c2_compilable()                { _access_flags.set_not_c2_compilable();        }

  // Background compilation support
  bool queued_for_compilation() const  { return access_flags().queued_for_compilation(); }
  void set_queued_for_compilation()    { _access_flags.set_queued_for_compilation();     }
  void clear_queued_for_compilation()  { _access_flags.clear_queued_for_compilation();   }

  static methodOop method_from_bcp(address bcp);

  // Resolve all classes in signature, return 'true' if successful
  static bool load_signature_classes(methodHandle m, TRAPS);

  // Return if true if not all classes references in signature, including return type, has been loaded
  static bool has_unloaded_classes_in_signature(methodHandle m, TRAPS);

  // Printing
  void print_short_name(outputStream* st)        /*PRODUCT_RETURN*/; // prints as klassname::methodname; Exposed so field engineers can debug VM
  void print_name(outputStream* st)              PRODUCT_RETURN; // prints as "virtual void foo(int)"

  // Helper routine used for method sorting
  static void sort_methods(objArrayOop methods,
                           objArrayOop methods_annotations,
                           objArrayOop methods_parameter_annotations,
                           objArrayOop methods_default_annotations,
                           bool idempotent = false);

  // size of parameters
  void set_size_of_parameters(int size)          { _size_of_parameters = size; }
 private:

  // Inlined elements
  address* native_function_addr() const          { assert(is_native(), "must be native"); return (address*) (this+1); }
  address* signature_handler_addr() const        { return native_function_addr() + 1; }

  // Garbage collection support
  oop*  adr_constMethod() const                  { return (oop*)&_constMethod;     }
  oop*  adr_constants() const                    { return (oop*)&_constants;       }
  oop*  adr_method_data() const                  { return (oop*)&_method_data;     }
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

  inline void write_pair_inline(int bci, int line) {
    int bci_delta = bci - _bci;
    int line_delta = line - _line;
    _bci = bci;
    _line = line;
    // Skip (0,0) deltas - they do not add information and conflict with terminator.
    if (bci_delta == 0 && line_delta == 0) return;
    // Check if bci is 5-bit and line number 3-bit unsigned.
    if (((bci_delta & ~0x1F) == 0) && ((line_delta & ~0x7) == 0)) {
      // Compress into single byte.
      jubyte value = ((jubyte) bci_delta << 3) | (jubyte) line_delta;
      // Check that value doesn't match escape character.
      if (value != 0xFF) {
        write_byte(value);
        return;
      }
    }
    write_pair_regular(bci_delta, line_delta);
  }

// Windows AMD64 + Apr 2005 PSDK with /O2 generates bad code for write_pair.
// Disabling optimization doesn't work for methods in header files
// so we force it to call through the non-optimized version in the .cpp.
// It's gross, but it's the only way we can ensure that all callers are
// fixed.  MSC_VER is defined in build/windows/makefiles/compile.make.
#if defined(_M_AMD64) && MSC_VER >= 1400
  void write_pair(int bci, int line);
#else
  void write_pair(int bci, int line) { write_pair_inline(bci, line); }
#endif

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


/// Fast Breakpoints.

// If this structure gets more complicated (because bpts get numerous),
// move it into its own header.

// There is presently no provision for concurrent access
// to breakpoint lists, which is only OK for JVMTI because
// breakpoints are written only at safepoints, and are read
// concurrently only outside of safepoints.

class BreakpointInfo : public CHeapObj {
  friend class VMStructs;
 private:
  Bytecodes::Code  _orig_bytecode;
  int              _bci;
  u2               _name_index;       // of method
  u2               _signature_index;  // of method
  BreakpointInfo*  _next;             // simple storage allocation

 public:
  BreakpointInfo(methodOop m, int bci);

  // accessors
  Bytecodes::Code orig_bytecode()                     { return _orig_bytecode; }
  void        set_orig_bytecode(Bytecodes::Code code) { _orig_bytecode = code; }
  int         bci()                                   { return _bci; }

  BreakpointInfo*          next() const               { return _next; }
  void                 set_next(BreakpointInfo* n)    { _next = n; }

  // helps for searchers
  bool match(methodOop m, int bci) {
    return bci == _bci && match(m);
  }

  bool match(methodOop m) {
    return _name_index == m->name_index() &&
      _signature_index == m->signature_index();
  }

  void set(methodOop method);
  void clear(methodOop method);
};
