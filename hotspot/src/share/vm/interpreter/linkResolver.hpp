/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_INTERPRETER_LINKRESOLVER_HPP
#define SHARE_VM_INTERPRETER_LINKRESOLVER_HPP

#include "oops/method.hpp"
#include "utilities/top.hpp"

// All the necessary definitions for run-time link resolution.

// CallInfo provides all the information gathered for a particular
// linked call site after resolving it. A link is any reference
// made from within the bytecodes of a method to an object outside of
// that method. If the info is invalid, the link has not been resolved
// successfully.

class CallInfo : public StackObj {
 public:
  // Ways that a method call might be selected (or not) based on receiver type.
  // Note that an invokevirtual instruction might be linked with no_dispatch,
  // and an invokeinterface instruction might be linked with any of the three options
  enum CallKind {
    direct_call,                        // jump into resolved_method (must be concrete)
    vtable_call,                        // select recv.klass.method_at_vtable(index)
    itable_call,                        // select recv.klass.method_at_itable(resolved_method.holder, index)
    unknown_kind = -1
  };
 private:
  KlassHandle  _resolved_klass;         // static receiver klass, resolved from a symbolic reference
  KlassHandle  _selected_klass;         // dynamic receiver class (same as static, or subklass)
  methodHandle _resolved_method;        // static target method
  methodHandle _selected_method;        // dynamic (actual) target method
  CallKind     _call_kind;              // kind of call (static(=bytecode static/special +
                                        //               others inferred), vtable, itable)
  int          _call_index;             // vtable or itable index of selected class method (if any)
  Handle       _resolved_appendix;      // extra argument in constant pool (if CPCE::has_appendix)
  Handle       _resolved_method_type;   // MethodType (for invokedynamic and invokehandle call sites)

  void set_static(KlassHandle resolved_klass, const methodHandle& resolved_method, TRAPS);
  void set_interface(KlassHandle resolved_klass, KlassHandle selected_klass,
                     const methodHandle& resolved_method,
                     const methodHandle& selected_method,
                     int itable_index, TRAPS);
  void set_virtual(KlassHandle resolved_klass, KlassHandle selected_klass,
                   const methodHandle& resolved_method,
                   const methodHandle& selected_method,
                   int vtable_index, TRAPS);
  void set_handle(const methodHandle& resolved_method,
                  Handle resolved_appendix, Handle resolved_method_type, TRAPS);
  void set_common(KlassHandle resolved_klass, KlassHandle selected_klass,
                  const methodHandle& resolved_method,
                  const methodHandle& selected_method,
                  CallKind kind,
                  int index, TRAPS);

  friend class LinkResolver;

 public:
  CallInfo() {
#ifndef PRODUCT
    _call_kind  = CallInfo::unknown_kind;
    _call_index = Method::garbage_vtable_index;
#endif //PRODUCT
  }

  // utility to extract an effective CallInfo from a method and an optional receiver limit
  // does not queue the method for compilation
  CallInfo(Method* resolved_method, Klass* resolved_klass = NULL);

  KlassHandle  resolved_klass() const            { return _resolved_klass; }
  KlassHandle  selected_klass() const            { return _selected_klass; }
  methodHandle resolved_method() const           { return _resolved_method; }
  methodHandle selected_method() const           { return _selected_method; }
  Handle       resolved_appendix() const         { return _resolved_appendix; }
  Handle       resolved_method_type() const      { return _resolved_method_type; }

  BasicType    result_type() const               { return selected_method()->result_type(); }
  CallKind     call_kind() const                 { return _call_kind; }
  int          call_index() const                { return _call_index; }
  int          vtable_index() const {
    // Even for interface calls the vtable index could be non-negative.
    // See CallInfo::set_interface.
    assert(has_vtable_index() || is_statically_bound(), "");
    assert(call_kind() == vtable_call || call_kind() == direct_call, "");
    // The returned value is < 0 if the call is statically bound.
    // But, the returned value may be >= 0 even if the kind is direct_call.
    // It is up to the caller to decide which way to go.
    return _call_index;
  }
  int          itable_index() const {
    assert(call_kind() == itable_call, "");
    // The returned value is always >= 0, a valid itable index.
    return _call_index;
  }

  // debugging
#ifdef ASSERT
  bool         has_vtable_index() const          { return _call_index >= 0 && _call_kind != CallInfo::itable_call; }
  bool         is_statically_bound() const       { return _call_index == Method::nonvirtual_vtable_index; }
#endif //ASSERT
  void         verify() PRODUCT_RETURN;
  void         print()  PRODUCT_RETURN;
};


// Condensed information from constant pool to use to resolve the method or field.
//   resolved_klass = specified class (i.e., static receiver class)
//   current_klass  = sending method holder (i.e., class containing the method
//                    containing the call being resolved)
class LinkInfo : public StackObj {
  Symbol*     _name;            // extracted from JVM_CONSTANT_NameAndType
  Symbol*     _signature;
  KlassHandle _resolved_klass;  // class that the constant pool entry points to
  KlassHandle _current_klass;   // class that owns the constant pool
  bool        _check_access;
 public:
  LinkInfo(constantPoolHandle pool, int index, TRAPS);
  // Condensed information from other call sites within the vm.
  LinkInfo(KlassHandle resolved_klass, Symbol* name, Symbol* signature,
           KlassHandle current_klass, bool check_access = true) :
    _resolved_klass(resolved_klass),
    _name(name), _signature(signature), _current_klass(current_klass),
    _check_access(check_access) {}

  // accessors
  Symbol* name() const               { return _name; }
  Symbol* signature() const          { return _signature; }
  KlassHandle resolved_klass() const { return _resolved_klass; }
  KlassHandle current_klass() const  { return _current_klass; }
  bool check_access() const          { return _check_access; }
  char* method_string() const;

  void         print()  PRODUCT_RETURN;
};

// Link information for getfield/putfield & getstatic/putstatic bytecodes
// is represented using a fieldDescriptor.

// The LinkResolver is used to resolve constant-pool references at run-time.
// It does all necessary link-time checks & throws exceptions if necessary.

class LinkResolver: AllStatic {
  friend class klassVtable;
  friend class klassItable;

 private:

  static methodHandle lookup_method_in_klasses(const LinkInfo& link_info,
                                               bool checkpolymorphism,
                                               bool in_imethod_resolve, TRAPS);
  static methodHandle lookup_method_in_interfaces(const LinkInfo& link_info, TRAPS);
  static methodHandle lookup_polymorphic_method(const LinkInfo& link_info,
                                                Handle *appendix_result_or_null,
                                                Handle *method_type_result, TRAPS);
 JVMCI_ONLY(public:) // Needed for CompilerToVM.resolveMethod()
  // Not Linktime so doesn't take LinkInfo
  static methodHandle lookup_instance_method_in_klasses (
                                       KlassHandle klass, Symbol* name, Symbol* signature, TRAPS);
 JVMCI_ONLY(private:)

  // Similar loader constraint checking functions that throw
  // LinkageError with descriptive message.
  static void check_method_loader_constraints(const LinkInfo& link_info,
                                              const methodHandle& resolved_method,
                                              const char* method_type, TRAPS);
  static void check_field_loader_constraints(Symbol* field, Symbol* sig,
                                             KlassHandle current_klass,
                                             KlassHandle sel_klass, TRAPS);

  static methodHandle resolve_interface_method(const LinkInfo& link_info, bool nostatics, TRAPS);
  static methodHandle resolve_method          (const LinkInfo& link_info, bool require_methodref, TRAPS);

  static methodHandle linktime_resolve_static_method    (const LinkInfo& link_info, TRAPS);
  static methodHandle linktime_resolve_special_method   (const LinkInfo& link_info, TRAPS);
  static methodHandle linktime_resolve_virtual_method   (const LinkInfo& link_info, TRAPS);
  static methodHandle linktime_resolve_interface_method (const LinkInfo& link_info, TRAPS);

  static void runtime_resolve_special_method    (CallInfo& result,
                                                 const methodHandle& resolved_method,
                                                 KlassHandle resolved_klass,
                                                 KlassHandle current_klass,
                                                 bool check_access, TRAPS);
  static void runtime_resolve_virtual_method    (CallInfo& result,
                                                 const methodHandle& resolved_method,
                                                 KlassHandle resolved_klass,
                                                 Handle recv,
                                                 KlassHandle recv_klass,
                                                 bool check_null_and_abstract, TRAPS);
  static void runtime_resolve_interface_method  (CallInfo& result,
                                                 const methodHandle& resolved_method,
                                                 KlassHandle resolved_klass,
                                                 Handle recv,
                                                 KlassHandle recv_klass,
                                                 bool check_null_and_abstract, TRAPS);

  static void check_field_accessability(KlassHandle ref_klass,
                                        KlassHandle resolved_klass,
                                        KlassHandle sel_klass,
                                        const fieldDescriptor& fd, TRAPS);
  static void check_method_accessability(KlassHandle ref_klass,
                                         KlassHandle resolved_klass,
                                         KlassHandle sel_klass,
                                         const methodHandle& sel_method, TRAPS);

  // runtime resolving from constant pool
  static void resolve_invokestatic   (CallInfo& result,
                                      constantPoolHandle pool, int index, TRAPS);
  static void resolve_invokespecial  (CallInfo& result,
                                      constantPoolHandle pool, int index, TRAPS);
  static void resolve_invokevirtual  (CallInfo& result, Handle recv,
                                      constantPoolHandle pool, int index, TRAPS);
  static void resolve_invokeinterface(CallInfo& result, Handle recv,
                                      constantPoolHandle pool, int index, TRAPS);
  static void resolve_invokedynamic  (CallInfo& result,
                                      constantPoolHandle pool, int index, TRAPS);
  static void resolve_invokehandle   (CallInfo& result,
                                      constantPoolHandle pool, int index, TRAPS);
 public:
  // constant pool resolving
  static void check_klass_accessability(KlassHandle ref_klass, KlassHandle sel_klass, TRAPS);

  // static resolving calls (will not run any Java code);
  // used only from Bytecode_invoke::static_target
  static methodHandle resolve_method_statically(Bytecodes::Code code,
                                                constantPoolHandle pool,
                                                int index, TRAPS);

  static void resolve_field_access(fieldDescriptor& result,
                                   constantPoolHandle pool,
                                   int index, Bytecodes::Code byte, TRAPS);
  static void resolve_field(fieldDescriptor& result, const LinkInfo& link_info,
                            Bytecodes::Code access_kind,
                            bool initialize_class, TRAPS);

  static void resolve_static_call   (CallInfo& result,
                                     const LinkInfo& link_info,
                                     bool initialize_klass, TRAPS);
  static void resolve_special_call  (CallInfo& result,
                                     const LinkInfo& link_info,
                                     TRAPS);
  static void resolve_virtual_call  (CallInfo& result, Handle recv, KlassHandle recv_klass,
                                     const LinkInfo& link_info,
                                     bool check_null_and_abstract, TRAPS);
  static void resolve_interface_call(CallInfo& result, Handle recv, KlassHandle recv_klass,
                                     const LinkInfo& link_info,
                                     bool check_null_and_abstract, TRAPS);
  static void resolve_handle_call   (CallInfo& result,
                                     const LinkInfo& link_info, TRAPS);
  static void resolve_dynamic_call  (CallInfo& result, Handle bootstrap_specifier,
                                     Symbol* method_name, Symbol* method_signature,
                                     KlassHandle current_klass, TRAPS);

  // same as above for compile-time resolution; but returns null handle instead of throwing
  // an exception on error also, does not initialize klass (i.e., no side effects)
  static methodHandle resolve_virtual_call_or_null  (KlassHandle receiver_klass,
                                                     const LinkInfo& link_info);
  static methodHandle resolve_interface_call_or_null(KlassHandle receiver_klass,
                                                     const LinkInfo& link_info);
  static methodHandle resolve_static_call_or_null   (const LinkInfo& link_info);
  static methodHandle resolve_special_call_or_null  (const LinkInfo& link_info);

  static int vtable_index_of_interface_method(KlassHandle klass, const methodHandle& resolved_method);

  // same as above for compile-time resolution; returns vtable_index if current_klass if linked
  static int resolve_virtual_vtable_index  (KlassHandle receiver_klass,
                                            const LinkInfo& link_info);

  // static resolving for compiler (does not throw exceptions, returns null handle if unsuccessful)
  static methodHandle linktime_resolve_virtual_method_or_null  (const LinkInfo& link_info);
  static methodHandle linktime_resolve_interface_method_or_null(const LinkInfo& link_info);

  // runtime resolving from constant pool
  static void resolve_invoke(CallInfo& result, Handle recv,
                             constantPoolHandle pool, int index,
                             Bytecodes::Code byte, TRAPS);
 private:
  static void trace_method_resolution(const char* prefix, KlassHandle klass,
                                      KlassHandle resolved_klass,
                                      const methodHandle& method) PRODUCT_RETURN;
};
#endif // SHARE_VM_INTERPRETER_LINKRESOLVER_HPP
