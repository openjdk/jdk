/*
 * Copyright 1999-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

class ciMethodBlocks;
class MethodLiveness;
class BitMap;
class Arena;
class BCEscapeAnalyzer;


// ciMethod
//
// This class represents a methodOop in the HotSpot virtual
// machine.
class ciMethod : public ciObject {
  friend class CompileBroker;
  CI_PACKAGE_ACCESS
  friend class ciEnv;
  friend class ciExceptionHandlerStream;
  friend class ciBytecodeStream;
  friend class ciMethodHandle;

 private:
  // General method information.
  ciFlags          _flags;
  ciSymbol*        _name;
  ciInstanceKlass* _holder;
  ciSignature*     _signature;
  ciMethodData*    _method_data;
  BCEscapeAnalyzer* _bcea;
  ciMethodBlocks*   _method_blocks;

  // Code attributes.
  int _code_size;
  int _max_stack;
  int _max_locals;
  vmIntrinsics::ID _intrinsic_id;
  int _handler_count;
  int _interpreter_invocation_count;
  int _interpreter_throwout_count;

  bool _uses_monitors;
  bool _balanced_monitors;
  bool _is_compilable;
  bool _can_be_statically_bound;

  // Lazy fields, filled in on demand
  address              _code;
  ciExceptionHandler** _exception_handlers;

  // Optional liveness analyzer.
  MethodLiveness* _liveness;
#ifdef COMPILER2
  ciTypeFlow*     _flow;
#endif

  ciMethod(methodHandle h_m);
  ciMethod(ciInstanceKlass* holder, ciSymbol* name, ciSymbol* signature);

  methodOop get_methodOop() const {
    methodOop m = (methodOop)get_oop();
    assert(m != NULL, "illegal use of unloaded method");
    return m;
  }

  oop loader() const                             { return _holder->loader(); }

  const char* type_string()                      { return "ciMethod"; }

  void print_impl(outputStream* st);

  void load_code();

  void check_is_loaded() const                   { assert(is_loaded(), "not loaded"); }

  void build_method_data(methodHandle h_m);

  void code_at_put(int bci, Bytecodes::Code code) {
    Bytecodes::check(code);
    assert(0 <= bci && bci < code_size(), "valid bci");
    address bcp = _code + bci;
    *bcp = code;
  }

 public:
  // Basic method information.
  ciFlags flags() const                          { check_is_loaded(); return _flags; }
  ciSymbol* name() const                         { return _name; }
  ciInstanceKlass* holder() const                { return _holder; }
  ciMethodData* method_data();

  // Signature information.
  ciSignature* signature() const                 { return _signature; }
  ciType*      return_type() const               { return _signature->return_type(); }
  int          arg_size_no_receiver() const      { return _signature->size(); }
  int          arg_size() const                  { return _signature->size() + (_flags.is_static() ? 0 : 1); }

  // Method code and related information.
  address code()                                 { if (_code == NULL) load_code(); return _code; }
  int code_size() const                          { check_is_loaded(); return _code_size; }
  int max_stack() const                          { check_is_loaded(); return _max_stack; }
  int max_locals() const                         { check_is_loaded(); return _max_locals; }
  vmIntrinsics::ID intrinsic_id() const          { check_is_loaded(); return _intrinsic_id; }
  bool has_exception_handlers() const            { check_is_loaded(); return _handler_count > 0; }
  int exception_table_length() const             { check_is_loaded(); return _handler_count; }
  int interpreter_invocation_count() const       { check_is_loaded(); return _interpreter_invocation_count; }
  int interpreter_throwout_count() const         { check_is_loaded(); return _interpreter_throwout_count; }

  Bytecodes::Code java_code_at_bci(int bci) {
    address bcp = code() + bci;
    return Bytecodes::java_code_at(bcp);
  }
  BCEscapeAnalyzer  *get_bcea();
  ciMethodBlocks    *get_method_blocks();

  bool    has_linenumber_table() const;          // length unknown until decompression
  u_char* compressed_linenumber_table() const;   // not preserved by gc

  int line_number_from_bci(int bci) const;

  // Runtime information.
  int           vtable_index();
  address       native_entry();
  address       interpreter_entry();

  // Analysis and profiling.
  //
  // Usage note: liveness_at_bci and init_vars should be wrapped in ResourceMarks.
  bool          uses_monitors() const            { return _uses_monitors; } // this one should go away, it has a misleading name
  bool          has_monitor_bytecodes() const    { return _uses_monitors; }
  bool          has_balanced_monitors();

  // Returns a bitmap indicating which locals are required to be
  // maintained as live for deopt.  raw_liveness_at_bci is always the
  // direct output of the liveness computation while liveness_at_bci
  // may mark all locals as live to improve support for debugging Java
  // code by maintaining the state of as many locals as possible.
  MethodLivenessResult raw_liveness_at_bci(int bci);
  MethodLivenessResult liveness_at_bci(int bci);

  // Get the interpreters viewpoint on oop liveness.  MethodLiveness is
  // conservative in the sense that it may consider locals to be live which
  // cannot be live, like in the case where a local could contain an oop or
  // a primitive along different paths.  In that case the local must be
  // dead when those paths merge. Since the interpreter's viewpoint is
  // used when gc'ing an interpreter frame we need to use its viewpoint
  // during OSR when loading the locals.

  BitMap  live_local_oops_at_bci(int bci);

#ifdef COMPILER1
  const BitMap  bci_block_start();
#endif

  ciTypeFlow*   get_flow_analysis();
  ciTypeFlow*   get_osr_flow_analysis(int osr_bci);  // alternate entry point
  ciCallProfile call_profile_at_bci(int bci);
  int           interpreter_call_site_count(int bci);

  // Given a certain calling environment, find the monomorphic target
  // for the call.  Return NULL if the call is not monomorphic in
  // its calling environment.
  ciMethod* find_monomorphic_target(ciInstanceKlass* caller,
                                    ciInstanceKlass* callee_holder,
                                    ciInstanceKlass* actual_receiver);

  // Given a known receiver klass, find the target for the call.
  // Return NULL if the call has no target or is abstract.
  ciMethod* resolve_invoke(ciKlass* caller, ciKlass* exact_receiver);

  // Find the proper vtable index to invoke this method.
  int resolve_vtable_index(ciKlass* caller, ciKlass* receiver);

  // Compilation directives
  bool will_link(ciKlass* accessing_klass,
                 ciKlass* declared_method_holder,
                 Bytecodes::Code bc);
  bool should_exclude();
  bool should_inline();
  bool should_not_inline();
  bool should_print_assembly();
  bool break_at_execute();
  bool has_option(const char *option);
  bool can_be_compiled();
  bool can_be_osr_compiled(int entry_bci);
  void set_not_compilable();
  bool has_compiled_code();
  int  instructions_size();
  void log_nmethod_identity(xmlStream* log);
  bool is_not_reached(int bci);
  bool was_executed_more_than(int times);
  bool has_unloaded_classes_in_signature();
  bool is_klass_loaded(int refinfo_index, bool must_be_resolved) const;
  bool check_call(int refinfo_index, bool is_static) const;
  void build_method_data();  // make sure it exists in the VM also
  int scale_count(int count, float prof_factor = 1.);  // make MDO count commensurate with IIC

  // JSR 292 support
  bool is_method_handle_invoke()  const;
  bool is_method_handle_adapter() const;
  ciInstance* method_handle_type();

  // What kind of ciObject is this?
  bool is_method()                               { return true; }

  // Java access flags
  bool is_public      () const                   { return flags().is_public(); }
  bool is_private     () const                   { return flags().is_private(); }
  bool is_protected   () const                   { return flags().is_protected(); }
  bool is_static      () const                   { return flags().is_static(); }
  bool is_final       () const                   { return flags().is_final(); }
  bool is_synchronized() const                   { return flags().is_synchronized(); }
  bool is_native      () const                   { return flags().is_native(); }
  bool is_interface   () const                   { return flags().is_interface(); }
  bool is_abstract    () const                   { return flags().is_abstract(); }
  bool is_strict      () const                   { return flags().is_strict(); }

  // Other flags
  bool is_empty_method() const;
  bool is_vanilla_constructor() const;
  bool is_final_method() const                   { return is_final() || holder()->is_final(); }
  bool has_loops      () const;
  bool has_jsrs       () const;
  bool is_accessor    () const;
  bool is_initializer () const;
  bool can_be_statically_bound() const           { return _can_be_statically_bound; }

  // Print the bytecodes of this method.
  void print_codes_on(outputStream* st);
  void print_codes() {
    print_codes_on(tty);
  }
  void print_codes_on(int from, int to, outputStream* st);

  // Print the name of this method in various incarnations.
  void print_name(outputStream* st = tty);
  void print_short_name(outputStream* st = tty);

  methodOop get_method_handle_target() {
    klassOop receiver_limit_oop = NULL;
    int flags = 0;
    return MethodHandles::decode_method(get_oop(), receiver_limit_oop, flags);
  }
};
