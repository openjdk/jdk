/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_PRIMS_METHODHANDLES_HPP
#define SHARE_VM_PRIMS_METHODHANDLES_HPP

#include "classfile/javaClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/interfaceSupport.hpp"

class MacroAssembler;
class Label;

class MethodHandles: AllStatic {
  // JVM support for MethodHandle, MethodType, and related types
  // in java.lang.invoke and sun.invoke.
  // See also  javaClasses for layouts java_lang_invoke_Method{Handle,Type,Type::Form}.
 public:
 public:
  static bool enabled()                         { return _enabled; }
  static void set_enabled(bool z);

 private:
  static bool _enabled;

  // Adapters.
  static MethodHandlesAdapterBlob* _adapter_code;

  // utility functions for reifying names and types
  static oop field_name_or_null(Symbol* s);
  static oop field_signature_type_or_null(Symbol* s);

 public:
  // working with member names
  static Handle resolve_MemberName(Handle mname, TRAPS); // compute vmtarget/vmindex from name/type
  static void expand_MemberName(Handle mname, int suppress, TRAPS);  // expand defc/name/type if missing
  static Handle new_MemberName(TRAPS);  // must be followed by init_MemberName
  static oop init_MemberName(Handle mname_h, Handle target_h); // compute vmtarget/vmindex from target
  static oop init_field_MemberName(Handle mname_h, fieldDescriptor& fd, bool is_setter = false);
  static oop init_method_MemberName(Handle mname_h, CallInfo& info);
  static int method_ref_kind(Method* m, bool do_dispatch_if_possible = true);
  static int find_MemberNames(KlassHandle k, Symbol* name, Symbol* sig,
                              int mflags, KlassHandle caller,
                              int skip, objArrayHandle results);
  // bit values for suppress argument to expand_MemberName:
  enum { _suppress_defc = 1, _suppress_name = 2, _suppress_type = 4 };

  // Generate MethodHandles adapters.
                              static void generate_adapters();

  // Called from MethodHandlesAdapterGenerator.
  static address generate_method_handle_interpreter_entry(MacroAssembler* _masm, vmIntrinsics::ID iid);
  static void generate_method_handle_dispatch(MacroAssembler* _masm,
                                              vmIntrinsics::ID iid,
                                              Register receiver_reg,
                                              Register member_reg,
                                              bool for_compiler_entry);

  // Queries
  static bool is_signature_polymorphic(vmIntrinsics::ID iid) {
    return (iid >= vmIntrinsics::FIRST_MH_SIG_POLY &&
            iid <= vmIntrinsics::LAST_MH_SIG_POLY);
  }

  static bool is_signature_polymorphic_intrinsic(vmIntrinsics::ID iid) {
    assert(is_signature_polymorphic(iid), "");
    // Most sig-poly methods are intrinsics which do not require an
    // appeal to Java for adapter code.
    return (iid != vmIntrinsics::_invokeGeneric);
  }

  static bool is_signature_polymorphic_static(vmIntrinsics::ID iid) {
    assert(is_signature_polymorphic(iid), "");
    return (iid >= vmIntrinsics::FIRST_MH_STATIC &&
            iid <= vmIntrinsics::LAST_MH_SIG_POLY);
  }

  static bool has_member_arg(vmIntrinsics::ID iid) {
    assert(is_signature_polymorphic(iid), "");
    return (iid >= vmIntrinsics::_linkToVirtual &&
            iid <= vmIntrinsics::_linkToInterface);
  }
  static bool has_member_arg(Symbol* klass, Symbol* name) {
    if ((klass == vmSymbols::java_lang_invoke_MethodHandle()) &&
        is_signature_polymorphic_name(name)) {
      vmIntrinsics::ID iid = signature_polymorphic_name_id(name);
      return has_member_arg(iid);
    }
    return false;
  }

  static Symbol* signature_polymorphic_intrinsic_name(vmIntrinsics::ID iid);
  static int signature_polymorphic_intrinsic_ref_kind(vmIntrinsics::ID iid);

  static vmIntrinsics::ID signature_polymorphic_name_id(Klass* klass, Symbol* name);
  static vmIntrinsics::ID signature_polymorphic_name_id(Symbol* name);
  static bool is_signature_polymorphic_name(Symbol* name) {
    return signature_polymorphic_name_id(name) != vmIntrinsics::_none;
  }
  static bool is_method_handle_invoke_name(Klass* klass, Symbol* name);
  static bool is_signature_polymorphic_name(Klass* klass, Symbol* name) {
    return signature_polymorphic_name_id(klass, name) != vmIntrinsics::_none;
  }

  enum {
    // format of query to getConstant:
    GC_COUNT_GWT = 4,
    GC_LAMBDA_SUPPORT = 5
  };
  static int get_named_constant(int which, Handle name_box, TRAPS);

public:
  static Symbol* lookup_signature(oop type_str, bool polymorphic, TRAPS);  // use TempNewSymbol
  static Symbol* lookup_basic_type_signature(Symbol* sig, bool keep_last_arg, TRAPS);  // use TempNewSymbol
  static Symbol* lookup_basic_type_signature(Symbol* sig, TRAPS) {
    return lookup_basic_type_signature(sig, false, THREAD);
  }
  static bool is_basic_type_signature(Symbol* sig);

  static Symbol* lookup_method_type(Symbol* msig, Handle mtype, TRAPS);

  static void print_as_method_type_on(outputStream* st, Symbol* sig) {
    print_as_basic_type_signature_on(st, sig, true, true);
  }
  static void print_as_basic_type_signature_on(outputStream* st, Symbol* sig, bool keep_arrays = false, bool keep_basic_names = false);

  // decoding CONSTANT_MethodHandle constants
  enum { JVM_REF_MIN = JVM_REF_getField, JVM_REF_MAX = JVM_REF_invokeInterface };
  static bool ref_kind_is_valid(int ref_kind) {
    return (ref_kind >= JVM_REF_MIN && ref_kind <= JVM_REF_MAX);
  }
  static bool ref_kind_is_field(int ref_kind) {
    assert(ref_kind_is_valid(ref_kind), "");
    return (ref_kind <= JVM_REF_putStatic);
  }
  static bool ref_kind_is_getter(int ref_kind) {
    assert(ref_kind_is_valid(ref_kind), "");
    return (ref_kind <= JVM_REF_getStatic);
  }
  static bool ref_kind_is_setter(int ref_kind) {
    return ref_kind_is_field(ref_kind) && !ref_kind_is_getter(ref_kind);
  }
  static bool ref_kind_is_method(int ref_kind) {
    return !ref_kind_is_field(ref_kind) && (ref_kind != JVM_REF_newInvokeSpecial);
  }
  static bool ref_kind_has_receiver(int ref_kind) {
    assert(ref_kind_is_valid(ref_kind), "");
    return (ref_kind & 1) != 0;
  }
  static bool ref_kind_is_static(int ref_kind) {
    return !ref_kind_has_receiver(ref_kind) && (ref_kind != JVM_REF_newInvokeSpecial);
  }
  static bool ref_kind_does_dispatch(int ref_kind) {
    return (ref_kind == JVM_REF_invokeVirtual ||
            ref_kind == JVM_REF_invokeInterface);
  }


#ifdef TARGET_ARCH_x86
# include "methodHandles_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "methodHandles_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "methodHandles_zero.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "methodHandles_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "methodHandles_ppc.hpp"
#endif

  // Tracing
  static void trace_method_handle(MacroAssembler* _masm, const char* adaptername) PRODUCT_RETURN;
  static void trace_method_handle_interpreter_entry(MacroAssembler* _masm, vmIntrinsics::ID iid) {
    if (TraceMethodHandles) {
      const char* name = vmIntrinsics::name_at(iid);
      if (*name == '_')  name += 1;
      const size_t len = strlen(name) + 50;
      char* qname = NEW_C_HEAP_ARRAY(char, len, mtInternal);
      const char* suffix = "";
      if (is_signature_polymorphic(iid)) {
        if (is_signature_polymorphic_static(iid))
          suffix = "/static";
        else
          suffix = "/private";
      }
      jio_snprintf(qname, len, "MethodHandle::interpreter_entry::%s%s", name, suffix);
      trace_method_handle(_masm, qname);
      // Note:  Don't free the allocated char array because it's used
      // during runtime.
    }
  }
};

//------------------------------------------------------------------------------
// MethodHandlesAdapterGenerator
//
class MethodHandlesAdapterGenerator : public StubCodeGenerator {
public:
  MethodHandlesAdapterGenerator(CodeBuffer* code) : StubCodeGenerator(code, PrintMethodHandleStubs) {}

  void generate();
};

//------------------------------------------------------------------------------
// MemberNameTable
//

class MemberNameTable : public GrowableArray<jweak> {
 public:
  MemberNameTable(int methods_cnt);
  ~MemberNameTable();
  void add_member_name(int index, jweak mem_name_ref);
  oop  get_member_name(int index);

#if INCLUDE_JVMTI
 public:
  // RedefineClasses() API support:
  // If a MemberName refers to old_method then update it
  // to refer to new_method.
  void adjust_method_entries(Method** old_methods, Method** new_methods,
                             int methods_length, bool *trace_name_printed);
 private:
  oop find_member_name_by_method(Method* old_method);
#endif // INCLUDE_JVMTI
};

#endif // SHARE_VM_PRIMS_METHODHANDLES_HPP
