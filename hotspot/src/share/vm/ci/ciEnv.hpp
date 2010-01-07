/*
 * Copyright 1999-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

class CompileTask;

// ciEnv
//
// This class is the top level broker for requests from the compiler
// to the VM.
class ciEnv : StackObj {
  CI_PACKAGE_ACCESS_TO

  friend class CompileBroker;
  friend class Dependencies;  // for get_object, during logging

private:
  Arena*           _arena;       // Alias for _ciEnv_arena except in init_shared_objects()
  Arena            _ciEnv_arena;
  int              _system_dictionary_modification_counter;
  ciObjectFactory* _factory;
  OopRecorder*     _oop_recorder;
  DebugInformationRecorder* _debug_info;
  Dependencies*    _dependencies;
  const char*      _failure_reason;
  int              _compilable;
  bool             _break_at_compile;
  int              _num_inlined_bytecodes;
  CompileTask*     _task;           // faster access to CompilerThread::task
  CompileLog*      _log;            // faster access to CompilerThread::log
  void*            _compiler_data;  // compiler-specific stuff, if any

  char* _name_buffer;
  int   _name_buffer_len;

  // Cache Jvmti state
  bool  _jvmti_can_hotswap_or_post_breakpoint;
  bool  _jvmti_can_examine_or_deopt_anywhere;
  bool  _jvmti_can_access_local_variables;
  bool  _jvmti_can_post_exceptions;

  // Cache DTrace flags
  bool  _dtrace_extended_probes;
  bool  _dtrace_monitor_probes;
  bool  _dtrace_method_probes;
  bool  _dtrace_alloc_probes;

  // Distinguished instances of certain ciObjects..
  static ciObject*              _null_object_instance;
  static ciMethodKlass*         _method_klass_instance;
  static ciSymbolKlass*         _symbol_klass_instance;
  static ciKlassKlass*          _klass_klass_instance;
  static ciInstanceKlassKlass*  _instance_klass_klass_instance;
  static ciTypeArrayKlassKlass* _type_array_klass_klass_instance;
  static ciObjArrayKlassKlass*  _obj_array_klass_klass_instance;

#define WK_KLASS_DECL(name, ignore_s, ignore_o) static ciInstanceKlass* _##name;
  WK_KLASSES_DO(WK_KLASS_DECL)
#undef WK_KLASS_DECL

  static ciSymbol*        _unloaded_cisymbol;
  static ciInstanceKlass* _unloaded_ciinstance_klass;
  static ciObjArrayKlass* _unloaded_ciobjarrayklass;

  static jobject _ArrayIndexOutOfBoundsException_handle;
  static jobject _ArrayStoreException_handle;
  static jobject _ClassCastException_handle;

  ciInstance* _NullPointerException_instance;
  ciInstance* _ArithmeticException_instance;
  ciInstance* _ArrayIndexOutOfBoundsException_instance;
  ciInstance* _ArrayStoreException_instance;
  ciInstance* _ClassCastException_instance;

  ciInstance* _the_null_string;      // The Java string "null"
  ciInstance* _the_min_jint_string; // The Java string "-2147483648"

  // Look up a klass by name from a particular class loader (the accessor's).
  // If require_local, result must be defined in that class loader, or NULL.
  // If !require_local, a result from remote class loader may be reported,
  // if sufficient class loader constraints exist such that initiating
  // a class loading request from the given loader is bound to return
  // the class defined in the remote loader (or throw an error).
  //
  // Return an unloaded klass if !require_local and no class at all is found.
  //
  // The CI treats a klass as loaded if it is consistently defined in
  // another loader, even if it hasn't yet been loaded in all loaders
  // that could potentially see it via delegation.
  ciKlass* get_klass_by_name(ciKlass* accessing_klass,
                             ciSymbol* klass_name,
                             bool require_local);

  // Constant pool access.
  ciKlass*   get_klass_by_index(constantPoolHandle cpool,
                                int klass_index,
                                bool& is_accessible,
                                ciInstanceKlass* loading_klass);
  ciConstant get_constant_by_index(constantPoolHandle cpool,
                                   int constant_index,
                                   ciInstanceKlass* accessor);
  bool       is_unresolved_string(ciInstanceKlass* loading_klass,
                                   int constant_index) const;
  bool       is_unresolved_klass(ciInstanceKlass* loading_klass,
                                   int constant_index) const;
  ciField*   get_field_by_index(ciInstanceKlass* loading_klass,
                                int field_index);
  ciMethod*  get_method_by_index(constantPoolHandle cpool,
                                 int method_index, Bytecodes::Code bc,
                                 ciInstanceKlass* loading_klass);

  // Implementation methods for loading and constant pool access.
  ciKlass* get_klass_by_name_impl(ciKlass* accessing_klass,
                                  ciSymbol* klass_name,
                                  bool require_local);
  ciKlass*   get_klass_by_index_impl(constantPoolHandle cpool,
                                     int klass_index,
                                     bool& is_accessible,
                                     ciInstanceKlass* loading_klass);
  ciConstant get_constant_by_index_impl(constantPoolHandle cpool,
                                        int constant_index,
                                        ciInstanceKlass* loading_klass);
  bool       is_unresolved_string_impl (instanceKlass* loading_klass,
                                        int constant_index) const;
  bool       is_unresolved_klass_impl (instanceKlass* loading_klass,
                                        int constant_index) const;
  ciField*   get_field_by_index_impl(ciInstanceKlass* loading_klass,
                                     int field_index);
  ciMethod*  get_method_by_index_impl(constantPoolHandle cpool,
                                      int method_index, Bytecodes::Code bc,
                                      ciInstanceKlass* loading_klass);
  ciMethod*  get_fake_invokedynamic_method_impl(constantPoolHandle cpool,
                                                int index, Bytecodes::Code bc);

  // Helper methods
  bool       check_klass_accessibility(ciKlass* accessing_klass,
                                      klassOop resolved_klassOop);
  methodOop  lookup_method(instanceKlass*  accessor,
                           instanceKlass*  holder,
                           symbolOop       name,
                           symbolOop       sig,
                           Bytecodes::Code bc);

  // Get a ciObject from the object factory.  Ensures uniqueness
  // of ciObjects.
  ciObject* get_object(oop o) {
    if (o == NULL) {
      return _null_object_instance;
    } else {
      return _factory->get(o);
    }
  }

  ciMethod* get_method_from_handle(jobject method);

  ciInstance* get_or_create_exception(jobject& handle, symbolHandle name);

  // Get a ciMethod representing either an unfound method or
  // a method with an unloaded holder.  Ensures uniqueness of
  // the result.
  ciMethod* get_unloaded_method(ciInstanceKlass* holder,
                                ciSymbol*        name,
                                ciSymbol*        signature) {
    return _factory->get_unloaded_method(holder, name, signature);
  }

  // Get a ciKlass representing an unloaded klass.
  // Ensures uniqueness of the result.
  ciKlass* get_unloaded_klass(ciKlass* accessing_klass,
                              ciSymbol* name) {
    return _factory->get_unloaded_klass(accessing_klass, name, true);
  }

  // See if we already have an unloaded klass for the given name
  // or return NULL if not.
  ciKlass *check_get_unloaded_klass(ciKlass* accessing_klass, ciSymbol* name) {
    return _factory->get_unloaded_klass(accessing_klass, name, false);
  }

  // Get a ciReturnAddress corresponding to the given bci.
  // Ensures uniqueness of the result.
  ciReturnAddress* get_return_address(int bci) {
    return _factory->get_return_address(bci);
  }

  // Get a ciMethodData representing the methodData for a method
  // with none.
  ciMethodData* get_empty_methodData() {
    return _factory->get_empty_methodData();
  }

  // General utility : get a buffer of some required length.
  // Used in symbol creation.
  char* name_buffer(int req_len);

  // Is this thread currently in the VM state?
  static bool is_in_vm();

  // Helper routine for determining the validity of a compilation
  // with respect to concurrent class loading.
  void check_for_system_dictionary_modification(ciMethod* target);

public:
  enum {
    MethodCompilable,
    MethodCompilable_not_at_tier,
    MethodCompilable_never
  };

  ciEnv(CompileTask* task, int system_dictionary_modification_counter);
  // Used only during initialization of the ci
  ciEnv(Arena* arena);
  ~ciEnv();

  OopRecorder* oop_recorder() { return _oop_recorder; }
  void set_oop_recorder(OopRecorder* r) { _oop_recorder = r; }

  DebugInformationRecorder* debug_info() { return _debug_info; }
  void set_debug_info(DebugInformationRecorder* i) { _debug_info = i; }

  Dependencies* dependencies() { return _dependencies; }
  void set_dependencies(Dependencies* d) { _dependencies = d; }

  // This is true if the compilation is not going to produce code.
  // (It is reasonable to retry failed compilations.)
  bool failing() { return _failure_reason != NULL; }

  // Reason this compilation is failing, such as "too many basic blocks".
  const char* failure_reason() { return _failure_reason; }

  // Return state of appropriate compilability
  int compilable() { return _compilable; }

  bool break_at_compile() { return _break_at_compile; }
  void set_break_at_compile(bool z) { _break_at_compile = z; }

  // Cache Jvmti state
  void  cache_jvmti_state();
  bool  jvmti_can_hotswap_or_post_breakpoint() const { return _jvmti_can_hotswap_or_post_breakpoint; }
  bool  jvmti_can_examine_or_deopt_anywhere()  const { return _jvmti_can_examine_or_deopt_anywhere; }
  bool  jvmti_can_access_local_variables()     const { return _jvmti_can_access_local_variables; }
  bool  jvmti_can_post_exceptions()            const { return _jvmti_can_post_exceptions; }

  // Cache DTrace flags
  void  cache_dtrace_flags();
  bool  dtrace_extended_probes() const { return _dtrace_extended_probes; }
  bool  dtrace_monitor_probes()  const { return _dtrace_monitor_probes; }
  bool  dtrace_method_probes()   const { return _dtrace_method_probes; }
  bool  dtrace_alloc_probes()    const { return _dtrace_alloc_probes; }

  // The compiler task which has created this env.
  // May be useful to find out compile_id, comp_level, etc.
  CompileTask* task() { return _task; }
  // Handy forwards to the task:
  int comp_level();   // task()->comp_level()
  uint compile_id();  // task()->compile_id()

  // Register the result of a compilation.
  void register_method(ciMethod*                 target,
                       int                       entry_bci,
                       CodeOffsets*              offsets,
                       int                       orig_pc_offset,
                       CodeBuffer*               code_buffer,
                       int                       frame_words,
                       OopMapSet*                oop_map_set,
                       ExceptionHandlerTable*    handler_table,
                       ImplicitExceptionTable*   inc_table,
                       AbstractCompiler*         compiler,
                       int                       comp_level,
                       bool                      has_debug_info = true,
                       bool                      has_unsafe_access = false);


  // Access to certain well known ciObjects.
#define WK_KLASS_FUNC(name, ignore_s, ignore_o) \
  ciInstanceKlass* name() { \
    return _##name;\
  }
  WK_KLASSES_DO(WK_KLASS_FUNC)
#undef WK_KLASS_FUNC

  ciInstance* NullPointerException_instance() {
    assert(_NullPointerException_instance != NULL, "initialization problem");
    return _NullPointerException_instance;
  }
  ciInstance* ArithmeticException_instance() {
    assert(_ArithmeticException_instance != NULL, "initialization problem");
    return _ArithmeticException_instance;
  }

  // Lazy constructors:
  ciInstance* ArrayIndexOutOfBoundsException_instance();
  ciInstance* ArrayStoreException_instance();
  ciInstance* ClassCastException_instance();

  ciInstance* the_null_string();
  ciInstance* the_min_jint_string();

  static ciSymbol* unloaded_cisymbol() {
    return _unloaded_cisymbol;
  }
  static ciObjArrayKlass* unloaded_ciobjarrayklass() {
    return _unloaded_ciobjarrayklass;
  }
  static ciInstanceKlass* unloaded_ciinstance_klass() {
    return _unloaded_ciinstance_klass;
  }

  ciKlass*  find_system_klass(ciSymbol* klass_name);
  // Note:  To find a class from its name string, use ciSymbol::make,
  // but consider adding to vmSymbols.hpp instead.

  // Use this to make a holder for non-perm compile time constants.
  // The resulting array is guaranteed to satisfy "can_be_constant".
  ciArray*  make_system_array(GrowableArray<ciObject*>* objects);

  // converts the ciKlass* representing the holder of a method into a
  // ciInstanceKlass*.  This is needed since the holder of a method in
  // the bytecodes could be an array type.  Basically this converts
  // array types into java/lang/Object and other types stay as they are.
  static ciInstanceKlass* get_instance_klass_for_declared_method_holder(ciKlass* klass);

  // Return the machine-level offset of o, which must be an element of a.
  // This may be used to form constant-loading expressions in lieu of simpler encodings.
  int       array_element_offset_in_bytes(ciArray* a, ciObject* o);

  // Access to the compile-lifetime allocation arena.
  Arena*    arena() { return _arena; }

  // What is the current compilation environment?
  static ciEnv* current() { return CompilerThread::current()->env(); }

  // Overload with current thread argument
  static ciEnv* current(CompilerThread *thread) { return thread->env(); }

  // Per-compiler data.  (Used by C2 to publish the Compile* pointer.)
  void* compiler_data() { return _compiler_data; }
  void set_compiler_data(void* x) { _compiler_data = x; }

  // Notice that a method has been inlined in the current compile;
  // used only for statistics.
  void notice_inlined_method(ciMethod* method);

  // Total number of bytecodes in inlined methods in this compile
  int num_inlined_bytecodes() const;

  // Output stream for logging compilation info.
  CompileLog* log() { return _log; }
  void set_log(CompileLog* log) { _log = log; }

  // Check for changes to the system dictionary during compilation
  bool system_dictionary_modification_counter_changed();

  void record_failure(const char* reason);
  void record_method_not_compilable(const char* reason, bool all_tiers = true);
  void record_out_of_memory_failure();
};
