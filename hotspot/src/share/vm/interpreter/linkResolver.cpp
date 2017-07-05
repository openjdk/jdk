/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/defaultMethods.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "compiler/compileBroker.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "interpreter/bytecode.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "interpreter/linkResolver.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/method.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "prims/methodHandles.hpp"
#include "prims/nativeLookup.hpp"
#include "runtime/compilationPolicy.hpp"
#include "runtime/fieldDescriptor.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/reflection.hpp"
#include "runtime/signature.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vmThread.hpp"


//------------------------------------------------------------------------------------------------------------------------
// Implementation of CallInfo


void CallInfo::set_static(KlassHandle resolved_klass, const methodHandle& resolved_method, TRAPS) {
  int vtable_index = Method::nonvirtual_vtable_index;
  set_common(resolved_klass, resolved_klass, resolved_method, resolved_method, CallInfo::direct_call, vtable_index, CHECK);
}


void CallInfo::set_interface(KlassHandle resolved_klass,
                             KlassHandle selected_klass,
                             const methodHandle& resolved_method,
                             const methodHandle& selected_method,
                             int itable_index, TRAPS) {
  // This is only called for interface methods. If the resolved_method
  // comes from java/lang/Object, it can be the subject of a virtual call, so
  // we should pick the vtable index from the resolved method.
  // In that case, the caller must call set_virtual instead of set_interface.
  assert(resolved_method->method_holder()->is_interface(), "");
  assert(itable_index == resolved_method()->itable_index(), "");
  set_common(resolved_klass, selected_klass, resolved_method, selected_method, CallInfo::itable_call, itable_index, CHECK);
}

void CallInfo::set_virtual(KlassHandle resolved_klass,
                           KlassHandle selected_klass,
                           const methodHandle& resolved_method,
                           const methodHandle& selected_method,
                           int vtable_index, TRAPS) {
  assert(vtable_index >= 0 || vtable_index == Method::nonvirtual_vtable_index, "valid index");
  assert(vtable_index < 0 || !resolved_method->has_vtable_index() || vtable_index == resolved_method->vtable_index(), "");
  CallKind kind = (vtable_index >= 0 && !resolved_method->can_be_statically_bound() ? CallInfo::vtable_call : CallInfo::direct_call);
  set_common(resolved_klass, selected_klass, resolved_method, selected_method, kind, vtable_index, CHECK);
  assert(!resolved_method->is_compiled_lambda_form(), "these must be handled via an invokehandle call");
}

void CallInfo::set_handle(const methodHandle& resolved_method,
                          Handle resolved_appendix,
                          Handle resolved_method_type, TRAPS) {
  set_handle(SystemDictionary::MethodHandle_klass(), resolved_method, resolved_appendix, resolved_method_type, CHECK);
}

void CallInfo::set_handle(KlassHandle resolved_klass,
                          const methodHandle& resolved_method,
                          Handle resolved_appendix,
                          Handle resolved_method_type, TRAPS) {
  if (resolved_method.is_null()) {
    THROW_MSG(vmSymbols::java_lang_InternalError(), "resolved method is null");
  }
  assert(resolved_method->intrinsic_id() == vmIntrinsics::_invokeBasic ||
         resolved_method->is_compiled_lambda_form(),
         "linkMethod must return one of these");
  int vtable_index = Method::nonvirtual_vtable_index;
  assert(!resolved_method->has_vtable_index(), "");
  set_common(resolved_klass, resolved_klass, resolved_method, resolved_method, CallInfo::direct_call, vtable_index, CHECK);
  _resolved_appendix    = resolved_appendix;
  _resolved_method_type = resolved_method_type;
}

void CallInfo::set_common(KlassHandle resolved_klass,
                          KlassHandle selected_klass,
                          const methodHandle& resolved_method,
                          const methodHandle& selected_method,
                          CallKind kind,
                          int index,
                          TRAPS) {
  assert(resolved_method->signature() == selected_method->signature(), "signatures must correspond");
  _resolved_klass  = resolved_klass;
  _selected_klass  = selected_klass;
  _resolved_method = resolved_method;
  _selected_method = selected_method;
  _call_kind       = kind;
  _call_index      = index;
  _resolved_appendix = Handle();
  DEBUG_ONLY(verify());  // verify before making side effects

  if (CompilationPolicy::must_be_compiled(selected_method)) {
    // This path is unusual, mostly used by the '-Xcomp' stress test mode.

    // Note: with several active threads, the must_be_compiled may be true
    //       while can_be_compiled is false; remove assert
    // assert(CompilationPolicy::can_be_compiled(selected_method), "cannot compile");
    if (!THREAD->can_call_java()) {
      // don't force compilation, resolve was on behalf of compiler
      return;
    }
    if (selected_method->method_holder()->is_not_initialized()) {
      // 'is_not_initialized' means not only '!is_initialized', but also that
      // initialization has not been started yet ('!being_initialized')
      // Do not force compilation of methods in uninitialized classes.
      // Note that doing this would throw an assert later,
      // in CompileBroker::compile_method.
      // We sometimes use the link resolver to do reflective lookups
      // even before classes are initialized.
      return;
    }
    CompileBroker::compile_method(selected_method, InvocationEntryBci,
                                  CompilationPolicy::policy()->initial_compile_level(),
                                  methodHandle(), 0, "must_be_compiled", CHECK);
  }
}

// utility query for unreflecting a method
CallInfo::CallInfo(Method* resolved_method, Klass* resolved_klass) {
  Klass* resolved_method_holder = resolved_method->method_holder();
  if (resolved_klass == NULL) { // 2nd argument defaults to holder of 1st
    resolved_klass = resolved_method_holder;
  }
  _resolved_klass  = resolved_klass;
  _selected_klass  = resolved_klass;
  _resolved_method = resolved_method;
  _selected_method = resolved_method;
  // classify:
  CallKind kind = CallInfo::unknown_kind;
  int index = resolved_method->vtable_index();
  if (resolved_method->can_be_statically_bound()) {
    kind = CallInfo::direct_call;
  } else if (!resolved_method_holder->is_interface()) {
    // Could be an Object method inherited into an interface, but still a vtable call.
    kind = CallInfo::vtable_call;
  } else if (!resolved_klass->is_interface()) {
    // A default or miranda method.  Compute the vtable index.
    ResourceMark rm;
    klassVtable* vt = resolved_klass->vtable();
    index = LinkResolver::vtable_index_of_interface_method(resolved_klass,
                           resolved_method);
    assert(index >= 0 , "we should have valid vtable index at this point");

    kind = CallInfo::vtable_call;
  } else if (resolved_method->has_vtable_index()) {
    // Can occur if an interface redeclares a method of Object.

#ifdef ASSERT
    // Ensure that this is really the case.
    KlassHandle object_klass = SystemDictionary::Object_klass();
    Method * object_resolved_method = object_klass()->vtable()->method_at(index);
    assert(object_resolved_method->name() == resolved_method->name(),
      "Object and interface method names should match at vtable index %d, %s != %s",
      index, object_resolved_method->name()->as_C_string(), resolved_method->name()->as_C_string());
    assert(object_resolved_method->signature() == resolved_method->signature(),
      "Object and interface method signatures should match at vtable index %d, %s != %s",
      index, object_resolved_method->signature()->as_C_string(), resolved_method->signature()->as_C_string());
#endif // ASSERT

    kind = CallInfo::vtable_call;
  } else {
    // A regular interface call.
    kind = CallInfo::itable_call;
    index = resolved_method->itable_index();
  }
  assert(index == Method::nonvirtual_vtable_index || index >= 0, "bad index %d", index);
  _call_kind  = kind;
  _call_index = index;
  _resolved_appendix = Handle();
  DEBUG_ONLY(verify());
}

#ifdef ASSERT
void CallInfo::verify() {
  switch (call_kind()) {  // the meaning and allowed value of index depends on kind
  case CallInfo::direct_call:
    if (_call_index == Method::nonvirtual_vtable_index)  break;
    // else fall through to check vtable index:
  case CallInfo::vtable_call:
    assert(resolved_klass()->verify_vtable_index(_call_index), "");
    break;
  case CallInfo::itable_call:
    assert(resolved_method()->method_holder()->verify_itable_index(_call_index), "");
    break;
  case CallInfo::unknown_kind:
    assert(call_kind() != CallInfo::unknown_kind, "CallInfo must be set");
    break;
  default:
    fatal("Unexpected call kind %d", call_kind());
  }
}
#endif //ASSERT

#ifndef PRODUCT
void CallInfo::print() {
  ResourceMark rm;
  const char* kindstr = "unknown";
  switch (_call_kind) {
  case direct_call: kindstr = "direct"; break;
  case vtable_call: kindstr = "vtable"; break;
  case itable_call: kindstr = "itable"; break;
  }
  tty->print_cr("Call %s@%d %s", kindstr, _call_index,
                _resolved_method.is_null() ? "(none)" : _resolved_method->name_and_sig_as_C_string());
}
#endif

//------------------------------------------------------------------------------------------------------------------------
// Implementation of LinkInfo

LinkInfo::LinkInfo(const constantPoolHandle& pool, int index, TRAPS) {
   // resolve klass
  Klass* result = pool->klass_ref_at(index, CHECK);
  _resolved_klass = KlassHandle(THREAD, result);

  // Get name, signature, and static klass
  _name          = pool->name_ref_at(index);
  _signature     = pool->signature_ref_at(index);
  _current_klass = KlassHandle(THREAD, pool->pool_holder());

  // Coming from the constant pool always checks access
  _check_access  = true;
}

char* LinkInfo::method_string() const {
  return Method::name_and_sig_as_C_string(_resolved_klass(), _name, _signature);
}

#ifndef PRODUCT
void LinkInfo::print() {
  ResourceMark rm;
  tty->print_cr("Link resolved_klass=%s name=%s signature=%s current_klass=%s check_access=%s",
                _resolved_klass->name()->as_C_string(),
                _name->as_C_string(),
                _signature->as_C_string(),
                _current_klass.is_null() ? "(none)" : _current_klass->name()->as_C_string(),
                _check_access ? "true" : "false");
}
#endif // PRODUCT
//------------------------------------------------------------------------------------------------------------------------
// Klass resolution

void LinkResolver::check_klass_accessability(KlassHandle ref_klass, KlassHandle sel_klass, TRAPS) {
  Reflection::VerifyClassAccessResults vca_result =
    Reflection::verify_class_access(ref_klass(), sel_klass(), true);
  if (vca_result != Reflection::ACCESS_OK) {
    ResourceMark rm(THREAD);
    char* msg = Reflection::verify_class_access_msg(ref_klass(), sel_klass(), vca_result);
    if (msg == NULL) {
      Exceptions::fthrow(
        THREAD_AND_LOCATION,
        vmSymbols::java_lang_IllegalAccessError(),
        "failed to access class %s from class %s",
        sel_klass->external_name(),
        ref_klass->external_name());
    } else {
      // Use module specific message returned by verify_class_access_msg().
      Exceptions::fthrow(
        THREAD_AND_LOCATION,
        vmSymbols::java_lang_IllegalAccessError(),
        "%s", msg);
    }
  }
}

//------------------------------------------------------------------------------------------------------------------------
// Method resolution
//
// According to JVM spec. $5.4.3c & $5.4.3d

// Look up method in klasses, including static methods
// Then look up local default methods
methodHandle LinkResolver::lookup_method_in_klasses(const LinkInfo& link_info,
                                                    bool checkpolymorphism,
                                                    bool in_imethod_resolve, TRAPS) {
  KlassHandle klass = link_info.resolved_klass();
  Symbol* name = link_info.name();
  Symbol* signature = link_info.signature();

  // Ignore overpasses so statics can be found during resolution
  Method* result = klass->uncached_lookup_method(name, signature, Klass::skip_overpass);

  if (klass->is_array_klass()) {
    // Only consider klass and super klass for arrays
    return methodHandle(THREAD, result);
  }

  InstanceKlass* ik = InstanceKlass::cast(klass());

  // JDK 8, JVMS 5.4.3.4: Interface method resolution should
  // ignore static and non-public methods of java.lang.Object,
  // like clone, finalize, registerNatives.
  if (in_imethod_resolve &&
      result != NULL &&
      ik->is_interface() &&
      (result->is_static() || !result->is_public()) &&
      result->method_holder() == SystemDictionary::Object_klass()) {
    result = NULL;
  }

  // Before considering default methods, check for an overpass in the
  // current class if a method has not been found.
  if (result == NULL) {
    result = ik->find_method(name, signature);
  }

  if (result == NULL) {
    Array<Method*>* default_methods = ik->default_methods();
    if (default_methods != NULL) {
      result = InstanceKlass::find_method(default_methods, name, signature);
    }
  }

  if (checkpolymorphism && result != NULL) {
    vmIntrinsics::ID iid = result->intrinsic_id();
    if (MethodHandles::is_signature_polymorphic(iid)) {
      // Do not link directly to these.  The VM must produce a synthetic one using lookup_polymorphic_method.
      return NULL;
    }
  }
  return methodHandle(THREAD, result);
}

// returns first instance method
// Looks up method in classes, then looks up local default methods
methodHandle LinkResolver::lookup_instance_method_in_klasses(KlassHandle klass,
                                                             Symbol* name,
                                                             Symbol* signature, TRAPS) {
  Method* result = klass->uncached_lookup_method(name, signature, Klass::find_overpass);

  while (result != NULL && result->is_static() && result->method_holder()->super() != NULL) {
    Klass* super_klass = result->method_holder()->super();
    result = super_klass->uncached_lookup_method(name, signature, Klass::find_overpass);
  }

  if (klass->is_array_klass()) {
    // Only consider klass and super klass for arrays
    return methodHandle(THREAD, result);
  }

  if (result == NULL) {
    Array<Method*>* default_methods = InstanceKlass::cast(klass())->default_methods();
    if (default_methods != NULL) {
      result = InstanceKlass::find_method(default_methods, name, signature);
      assert(result == NULL || !result->is_static(), "static defaults not allowed");
    }
  }
  return methodHandle(THREAD, result);
}

int LinkResolver::vtable_index_of_interface_method(KlassHandle klass,
                                                   const methodHandle& resolved_method) {

  int vtable_index = Method::invalid_vtable_index;
  Symbol* name = resolved_method->name();
  Symbol* signature = resolved_method->signature();
  InstanceKlass* ik = InstanceKlass::cast(klass());

  // First check in default method array
  if (!resolved_method->is_abstract() && ik->default_methods() != NULL) {
    int index = InstanceKlass::find_method_index(ik->default_methods(),
                                                 name, signature, Klass::find_overpass,
                                                 Klass::find_static, Klass::find_private);
    if (index >= 0 ) {
      vtable_index = ik->default_vtable_indices()->at(index);
    }
  }
  if (vtable_index == Method::invalid_vtable_index) {
    // get vtable_index for miranda methods
    ResourceMark rm;
    klassVtable *vt = ik->vtable();
    vtable_index = vt->index_of_miranda(name, signature);
  }
  return vtable_index;
}

methodHandle LinkResolver::lookup_method_in_interfaces(const LinkInfo& cp_info, TRAPS) {
  InstanceKlass *ik = InstanceKlass::cast(cp_info.resolved_klass()());

  // Specify 'true' in order to skip default methods when searching the
  // interfaces.  Function lookup_method_in_klasses() already looked for
  // the method in the default methods table.
  return methodHandle(THREAD,
    ik->lookup_method_in_all_interfaces(cp_info.name(), cp_info.signature(),
    Klass::skip_defaults));
}

methodHandle LinkResolver::lookup_polymorphic_method(
                                             const LinkInfo& link_info,
                                             Handle *appendix_result_or_null,
                                             Handle *method_type_result,
                                             TRAPS) {
  KlassHandle klass = link_info.resolved_klass();
  Symbol* name = link_info.name();
  Symbol* full_signature = link_info.signature();

  vmIntrinsics::ID iid = MethodHandles::signature_polymorphic_name_id(name);
  if (TraceMethodHandles) {
    ResourceMark rm(THREAD);
    tty->print_cr("lookup_polymorphic_method iid=%s %s.%s%s",
                  vmIntrinsics::name_at(iid), klass->external_name(),
                  name->as_C_string(), full_signature->as_C_string());
  }
  if ((klass() == SystemDictionary::MethodHandle_klass() ||
       klass() == SystemDictionary::VarHandle_klass()) &&
      iid != vmIntrinsics::_none) {
    if (MethodHandles::is_signature_polymorphic_intrinsic(iid)) {
      // Most of these do not need an up-call to Java to resolve, so can be done anywhere.
      // Do not erase last argument type (MemberName) if it is a static linkTo method.
      bool keep_last_arg = MethodHandles::is_signature_polymorphic_static(iid);
      TempNewSymbol basic_signature =
        MethodHandles::lookup_basic_type_signature(full_signature, keep_last_arg, CHECK_NULL);
      if (TraceMethodHandles) {
        ResourceMark rm(THREAD);
        tty->print_cr("lookup_polymorphic_method %s %s => basic %s",
                      name->as_C_string(),
                      full_signature->as_C_string(),
                      basic_signature->as_C_string());
      }
      methodHandle result = SystemDictionary::find_method_handle_intrinsic(iid,
                                                              basic_signature,
                                                              CHECK_NULL);
      if (result.not_null()) {
        assert(result->is_method_handle_intrinsic(), "MH.invokeBasic or MH.linkTo* intrinsic");
        assert(result->intrinsic_id() != vmIntrinsics::_invokeGeneric, "wrong place to find this");
        assert(basic_signature == result->signature(), "predict the result signature");
        if (TraceMethodHandles) {
          ttyLocker ttyl;
          tty->print("lookup_polymorphic_method => intrinsic ");
          result->print_on(tty);
        }
      }
      return result;
    } else if (iid == vmIntrinsics::_invokeGeneric
               && THREAD->can_call_java()
               && appendix_result_or_null != NULL) {
      // This is a method with type-checking semantics.
      // We will ask Java code to spin an adapter method for it.
      if (!MethodHandles::enabled()) {
        // Make sure the Java part of the runtime has been booted up.
        Klass* natives = SystemDictionary::MethodHandleNatives_klass();
        if (natives == NULL || InstanceKlass::cast(natives)->is_not_initialized()) {
          SystemDictionary::resolve_or_fail(vmSymbols::java_lang_invoke_MethodHandleNatives(),
                                            Handle(),
                                            Handle(),
                                            true,
                                            CHECK_NULL);
        }
      }

      Handle appendix;
      Handle method_type;
      methodHandle result = SystemDictionary::find_method_handle_invoker(
                                                            klass,
                                                            name,
                                                            full_signature,
                                                            link_info.current_klass(),
                                                            &appendix,
                                                            &method_type,
                                                            CHECK_NULL);
      if (TraceMethodHandles) {
        ttyLocker ttyl;
        tty->print("lookup_polymorphic_method => (via Java) ");
        result->print_on(tty);
        tty->print("  lookup_polymorphic_method => appendix = ");
        if (appendix.is_null())  tty->print_cr("(none)");
        else                     appendix->print_on(tty);
      }
      if (result.not_null()) {
#ifdef ASSERT
        ResourceMark rm(THREAD);

        TempNewSymbol basic_signature =
          MethodHandles::lookup_basic_type_signature(full_signature, CHECK_NULL);
        int actual_size_of_params = result->size_of_parameters();
        int expected_size_of_params = ArgumentSizeComputer(basic_signature).size();
        // +1 for MethodHandle.this, +1 for trailing MethodType
        if (!MethodHandles::is_signature_polymorphic_static(iid))  expected_size_of_params += 1;
        if (appendix.not_null())                                   expected_size_of_params += 1;
        if (actual_size_of_params != expected_size_of_params) {
          tty->print_cr("*** basic_signature=%s", basic_signature->as_C_string());
          tty->print_cr("*** result for %s: ", vmIntrinsics::name_at(iid));
          result->print();
        }
        assert(actual_size_of_params == expected_size_of_params,
               "%d != %d", actual_size_of_params, expected_size_of_params);
#endif //ASSERT

        assert(appendix_result_or_null != NULL, "");
        (*appendix_result_or_null) = appendix;
        (*method_type_result)      = method_type;
      }
      return result;
    }
  }
  return NULL;
}

void LinkResolver::check_method_accessability(KlassHandle ref_klass,
                                              KlassHandle resolved_klass,
                                              KlassHandle sel_klass,
                                              const methodHandle& sel_method,
                                              TRAPS) {

  AccessFlags flags = sel_method->access_flags();

  // Special case:  arrays always override "clone". JVMS 2.15.
  // If the resolved klass is an array class, and the declaring class
  // is java.lang.Object and the method is "clone", set the flags
  // to public.
  //
  // We'll check for the method name first, as that's most likely
  // to be false (so we'll short-circuit out of these tests).
  if (sel_method->name() == vmSymbols::clone_name() &&
      sel_klass() == SystemDictionary::Object_klass() &&
      resolved_klass->is_array_klass()) {
    // We need to change "protected" to "public".
    assert(flags.is_protected(), "clone not protected?");
    jint new_flags = flags.as_int();
    new_flags = new_flags & (~JVM_ACC_PROTECTED);
    new_flags = new_flags | JVM_ACC_PUBLIC;
    flags.set_flags(new_flags);
  }
//  assert(extra_arg_result_or_null != NULL, "must be able to return extra argument");

  if (!Reflection::verify_field_access(ref_klass(),
                                       resolved_klass(),
                                       sel_klass(),
                                       flags,
                                       true)) {
    ResourceMark rm(THREAD);
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbols::java_lang_IllegalAccessError(),
      "tried to access method %s.%s%s from class %s",
      sel_klass->external_name(),
      sel_method->name()->as_C_string(),
      sel_method->signature()->as_C_string(),
      ref_klass->external_name()
    );
    return;
  }
}

methodHandle LinkResolver::resolve_method_statically(Bytecodes::Code code,
                                                     const constantPoolHandle& pool, int index, TRAPS) {
  // This method is used only
  // (1) in C2 from InlineTree::ok_to_inline (via ciMethod::check_call),
  // and
  // (2) in Bytecode_invoke::static_target
  // It appears to fail when applied to an invokeinterface call site.
  // FIXME: Remove this method and ciMethod::check_call; refactor to use the other LinkResolver entry points.
  // resolve klass
  KlassHandle resolved_klass;
  if (code == Bytecodes::_invokedynamic) {
    resolved_klass = SystemDictionary::MethodHandle_klass();
    Symbol* method_name = vmSymbols::invoke_name();
    Symbol* method_signature = pool->signature_ref_at(index);
    KlassHandle  current_klass(THREAD, pool->pool_holder());
    LinkInfo link_info(resolved_klass, method_name, method_signature, current_klass);
    return resolve_method(link_info, /*require_methodref*/false, THREAD);
  }

  LinkInfo link_info(pool, index, CHECK_NULL);
  resolved_klass = link_info.resolved_klass();

  if (pool->has_preresolution()
      || (resolved_klass() == SystemDictionary::MethodHandle_klass() &&
          MethodHandles::is_signature_polymorphic_name(resolved_klass(), link_info.name()))) {
    Method* result = ConstantPool::method_at_if_loaded(pool, index);
    if (result != NULL) {
      return methodHandle(THREAD, result);
    }
  }

  if (code == Bytecodes::_invokeinterface) {
    return resolve_interface_method(link_info, true, THREAD);
  } else if (code == Bytecodes::_invokevirtual) {
    return resolve_method(link_info, /*require_methodref*/true, THREAD);
  } else if (!resolved_klass->is_interface()) {
    return resolve_method(link_info, /*require_methodref*/false, THREAD);
  } else {
    bool nostatics = (code == Bytecodes::_invokestatic) ? false : true;
    return resolve_interface_method(link_info, nostatics, THREAD);
  }
}

// Check and print a loader constraint violation message for method or interface method
void LinkResolver::check_method_loader_constraints(const LinkInfo& link_info,
                                                   const methodHandle& resolved_method,
                                                   const char* method_type, TRAPS) {
  Handle current_loader(THREAD, link_info.current_klass()->class_loader());
  Handle resolved_loader(THREAD, resolved_method->method_holder()->class_loader());

  ResourceMark rm(THREAD);
  Symbol* failed_type_symbol =
    SystemDictionary::check_signature_loaders(link_info.signature(), current_loader,
                                              resolved_loader, true, CHECK);
  if (failed_type_symbol != NULL) {
    const char* msg = "loader constraint violation: when resolving %s"
      " \"%s\" the class loader (instance of %s) of the current class, %s,"
      " and the class loader (instance of %s) for the method's defining class, %s, have"
      " different Class objects for the type %s used in the signature";
    char* sig = link_info.method_string();
    const char* loader1_name = SystemDictionary::loader_name(current_loader());
    char* current = link_info.current_klass()->name()->as_C_string();
    const char* loader2_name = SystemDictionary::loader_name(resolved_loader());
    char* target = resolved_method->method_holder()->name()->as_C_string();
    char* failed_type_name = failed_type_symbol->as_C_string();
    size_t buflen = strlen(msg) + strlen(sig) + strlen(loader1_name) +
      strlen(current) + strlen(loader2_name) + strlen(target) +
      strlen(failed_type_name) + strlen(method_type) + 1;
    char* buf = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, buflen);
    jio_snprintf(buf, buflen, msg, method_type, sig, loader1_name, current, loader2_name,
                 target, failed_type_name);
    THROW_MSG(vmSymbols::java_lang_LinkageError(), buf);
  }
}

void LinkResolver::check_field_loader_constraints(Symbol* field, Symbol* sig,
                                                  KlassHandle current_klass,
                                                  KlassHandle sel_klass, TRAPS) {
  Handle ref_loader(THREAD, current_klass->class_loader());
  Handle sel_loader(THREAD, sel_klass->class_loader());

  ResourceMark rm(THREAD);  // needed for check_signature_loaders
  Symbol* failed_type_symbol =
    SystemDictionary::check_signature_loaders(sig,
                                              ref_loader, sel_loader,
                                              false,
                                              CHECK);
  if (failed_type_symbol != NULL) {
    const char* msg = "loader constraint violation: when resolving field"
      " \"%s\" the class loader (instance of %s) of the referring class, "
      "%s, and the class loader (instance of %s) for the field's resolved "
      "type, %s, have different Class objects for that type";
    char* field_name = field->as_C_string();
    const char* loader1_name = SystemDictionary::loader_name(ref_loader());
    char* sel = sel_klass->name()->as_C_string();
    const char* loader2_name = SystemDictionary::loader_name(sel_loader());
    char* failed_type_name = failed_type_symbol->as_C_string();
    size_t buflen = strlen(msg) + strlen(field_name) + strlen(loader1_name) +
                    strlen(sel) + strlen(loader2_name) + strlen(failed_type_name) + 1;
    char* buf = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, buflen);
    jio_snprintf(buf, buflen, msg, field_name, loader1_name, sel, loader2_name,
                     failed_type_name);
    THROW_MSG(vmSymbols::java_lang_LinkageError(), buf);
  }
}

methodHandle LinkResolver::resolve_method(const LinkInfo& link_info,
                                          bool require_methodref, TRAPS) {

  Handle nested_exception;
  KlassHandle resolved_klass = link_info.resolved_klass();

  // 1. check if methodref required, that resolved_klass is not interfacemethodref
  if (require_methodref && resolved_klass->is_interface()) {
    ResourceMark rm(THREAD);
    char buf[200];
    jio_snprintf(buf, sizeof(buf), "Found interface %s, but class was expected",
        resolved_klass()->external_name());
    THROW_MSG_NULL(vmSymbols::java_lang_IncompatibleClassChangeError(), buf);
  }

  // 2. lookup method in resolved klass and its super klasses
  methodHandle resolved_method = lookup_method_in_klasses(link_info, true, false, CHECK_NULL);

  if (resolved_method.is_null() && !resolved_klass->is_array_klass()) { // not found in the class hierarchy
    // 3. lookup method in all the interfaces implemented by the resolved klass
    resolved_method = lookup_method_in_interfaces(link_info, CHECK_NULL);

    if (resolved_method.is_null()) {
      // JSR 292:  see if this is an implicitly generated method MethodHandle.linkToVirtual(*...), etc
      resolved_method = lookup_polymorphic_method(link_info, (Handle*)NULL, (Handle*)NULL, THREAD);
      if (HAS_PENDING_EXCEPTION) {
        nested_exception = Handle(THREAD, PENDING_EXCEPTION);
        CLEAR_PENDING_EXCEPTION;
      }
    }
  }

  if (resolved_method.is_null()) {
    // 4. method lookup failed
    ResourceMark rm(THREAD);
    THROW_MSG_CAUSE_(vmSymbols::java_lang_NoSuchMethodError(),
                    Method::name_and_sig_as_C_string(resolved_klass(),
                                                     link_info.name(),
                                                     link_info.signature()),
                    nested_exception, NULL);
  }

  // 5. access checks, access checking may be turned off when calling from within the VM.
  KlassHandle current_klass = link_info.current_klass();
  if (link_info.check_access()) {
    assert(current_klass.not_null() , "current_klass should not be null");

    // check if method can be accessed by the referring class
    check_method_accessability(current_klass,
                               resolved_klass,
                               KlassHandle(THREAD, resolved_method->method_holder()),
                               resolved_method,
                               CHECK_NULL);

    // check loader constraints
    check_method_loader_constraints(link_info, resolved_method, "method", CHECK_NULL);
  }

  return resolved_method;
}

static void trace_method_resolution(const char* prefix,
                                    KlassHandle klass,
                                    KlassHandle resolved_klass,
                                    const methodHandle& method,
                                    bool logitables,
                                    int index = -1) {
#ifndef PRODUCT
  ResourceMark rm;
  outputStream* st;
  if (logitables) {
    st = LogHandle(itables)::trace_stream();
  } else {
    st = LogHandle(vtables)::trace_stream();
  }
  st->print("%s%s, compile-time-class:%s, method:%s, method_holder:%s, access_flags: ",
            prefix,
            (klass.is_null() ? "<NULL>" : klass->internal_name()),
            (resolved_klass.is_null() ? "<NULL>" : resolved_klass->internal_name()),
            Method::name_and_sig_as_C_string(resolved_klass(),
                                             method->name(),
                                             method->signature()),
            method->method_holder()->internal_name());
  method->print_linkage_flags(st);
  if (index != -1) {
    st->print("vtable_index:%d", index);
  }
  st->cr();
#endif // PRODUCT
}

methodHandle LinkResolver::resolve_interface_method(const LinkInfo& link_info,
                                                    bool nostatics, TRAPS) {

  KlassHandle resolved_klass = link_info.resolved_klass();

  // check if klass is interface
  if (!resolved_klass->is_interface()) {
    ResourceMark rm(THREAD);
    char buf[200];
    jio_snprintf(buf, sizeof(buf), "Found class %s, but interface was expected", resolved_klass()->external_name());
    THROW_MSG_NULL(vmSymbols::java_lang_IncompatibleClassChangeError(), buf);
  }

  // lookup method in this interface or its super, java.lang.Object
  // JDK8: also look for static methods
  methodHandle resolved_method = lookup_method_in_klasses(link_info, false, true, CHECK_NULL);

  if (resolved_method.is_null() && !resolved_klass->is_array_klass()) {
    // lookup method in all the super-interfaces
    resolved_method = lookup_method_in_interfaces(link_info, CHECK_NULL);
  }

  if (resolved_method.is_null()) {
    // no method found
    ResourceMark rm(THREAD);
    THROW_MSG_NULL(vmSymbols::java_lang_NoSuchMethodError(),
                   Method::name_and_sig_as_C_string(resolved_klass(),
                                                    link_info.name(),
                                                    link_info.signature()));
  }

  if (link_info.check_access()) {
    // JDK8 adds non-public interface methods, and accessability check requirement
    KlassHandle current_klass = link_info.current_klass();

    assert(current_klass.not_null() , "current_klass should not be null");

    // check if method can be accessed by the referring class
    check_method_accessability(current_klass,
                               resolved_klass,
                               KlassHandle(THREAD, resolved_method->method_holder()),
                               resolved_method,
                               CHECK_NULL);

    check_method_loader_constraints(link_info, resolved_method, "interface method", CHECK_NULL);
  }

  if (nostatics && resolved_method->is_static()) {
    ResourceMark rm(THREAD);
    char buf[200];
    jio_snprintf(buf, sizeof(buf), "Expected instance not static method %s",
                 Method::name_and_sig_as_C_string(resolved_klass(),
                 resolved_method->name(), resolved_method->signature()));
    THROW_MSG_NULL(vmSymbols::java_lang_IncompatibleClassChangeError(), buf);
  }

  if (log_develop_is_enabled(Trace, itables)) {
    trace_method_resolution("invokeinterface resolved method: caller-class",
                            link_info.current_klass(), resolved_klass,
                            resolved_method, true);
  }

  return resolved_method;
}

//------------------------------------------------------------------------------------------------------------------------
// Field resolution

void LinkResolver::check_field_accessability(KlassHandle ref_klass,
                                             KlassHandle resolved_klass,
                                             KlassHandle sel_klass,
                                             const fieldDescriptor& fd,
                                             TRAPS) {
  if (!Reflection::verify_field_access(ref_klass(),
                                       resolved_klass(),
                                       sel_klass(),
                                       fd.access_flags(),
                                       true)) {
    ResourceMark rm(THREAD);
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbols::java_lang_IllegalAccessError(),
      "tried to access field %s.%s from class %s",
      sel_klass->external_name(),
      fd.name()->as_C_string(),
      ref_klass->external_name()
    );
    return;
  }
}

void LinkResolver::resolve_field_access(fieldDescriptor& fd, const constantPoolHandle& pool, int index, Bytecodes::Code byte, TRAPS) {
  LinkInfo link_info(pool, index, CHECK);
  resolve_field(fd, link_info, byte, true, CHECK);
}

void LinkResolver::resolve_field(fieldDescriptor& fd,
                                 const LinkInfo& link_info,
                                 Bytecodes::Code byte, bool initialize_class,
                                 TRAPS) {
  assert(byte == Bytecodes::_getstatic || byte == Bytecodes::_putstatic ||
         byte == Bytecodes::_getfield  || byte == Bytecodes::_putfield  ||
         byte == Bytecodes::_nofast_getfield  || byte == Bytecodes::_nofast_putfield  ||
         (byte == Bytecodes::_nop && !link_info.check_access()), "bad field access bytecode");

  bool is_static = (byte == Bytecodes::_getstatic || byte == Bytecodes::_putstatic);
  bool is_put    = (byte == Bytecodes::_putfield  || byte == Bytecodes::_putstatic || byte == Bytecodes::_nofast_putfield);
  // Check if there's a resolved klass containing the field
  KlassHandle resolved_klass = link_info.resolved_klass();
  Symbol* field = link_info.name();
  Symbol* sig = link_info.signature();

  if (resolved_klass.is_null()) {
    ResourceMark rm(THREAD);
    THROW_MSG(vmSymbols::java_lang_NoSuchFieldError(), field->as_C_string());
  }

  // Resolve instance field
  KlassHandle sel_klass(THREAD, resolved_klass->find_field(field, sig, &fd));
  // check if field exists; i.e., if a klass containing the field def has been selected
  if (sel_klass.is_null()) {
    ResourceMark rm(THREAD);
    THROW_MSG(vmSymbols::java_lang_NoSuchFieldError(), field->as_C_string());
  }

  if (!link_info.check_access())
    // Access checking may be turned off when calling from within the VM.
    return;

  // check access
  KlassHandle current_klass = link_info.current_klass();
  check_field_accessability(current_klass, resolved_klass, sel_klass, fd, CHECK);

  // check for errors
  if (is_static != fd.is_static()) {
    ResourceMark rm(THREAD);
    char msg[200];
    jio_snprintf(msg, sizeof(msg), "Expected %s field %s.%s", is_static ? "static" : "non-static", resolved_klass()->external_name(), fd.name()->as_C_string());
    THROW_MSG(vmSymbols::java_lang_IncompatibleClassChangeError(), msg);
  }

  // Final fields can only be accessed from its own class.
  if (is_put && fd.access_flags().is_final() && sel_klass() != current_klass()) {
    THROW(vmSymbols::java_lang_IllegalAccessError());
  }

  // initialize resolved_klass if necessary
  // note 1: the klass which declared the field must be initialized (i.e, sel_klass)
  //         according to the newest JVM spec (5.5, p.170) - was bug (gri 7/28/99)
  //
  // note 2: we don't want to force initialization if we are just checking
  //         if the field access is legal; e.g., during compilation
  if (is_static && initialize_class) {
    sel_klass->initialize(CHECK);
  }

  if (sel_klass() != current_klass()) {
    check_field_loader_constraints(field, sig, current_klass, sel_klass, CHECK);
  }

  // return information. note that the klass is set to the actual klass containing the
  // field, otherwise access of static fields in superclasses will not work.
}


//------------------------------------------------------------------------------------------------------------------------
// Invoke resolution
//
// Naming conventions:
//
// resolved_method    the specified method (i.e., static receiver specified via constant pool index)
// sel_method         the selected method  (selected via run-time lookup; e.g., based on dynamic receiver class)
// resolved_klass     the specified klass  (i.e., specified via constant pool index)
// recv_klass         the receiver klass


void LinkResolver::resolve_static_call(CallInfo& result,
                                       const LinkInfo& link_info,
                                       bool initialize_class, TRAPS) {
  methodHandle resolved_method = linktime_resolve_static_method(link_info, CHECK);

  // The resolved class can change as a result of this resolution.
  KlassHandle resolved_klass = KlassHandle(THREAD, resolved_method->method_holder());

  Method* save_resolved_method = resolved_method();
  // Initialize klass (this should only happen if everything is ok)
  if (initialize_class && resolved_klass->should_be_initialized()) {
    resolved_klass->initialize(CHECK);
    // Use updated LinkInfo (to reresolve with resolved_klass as method_holder?)
    LinkInfo new_info(resolved_klass, link_info.name(), link_info.signature(),
                      link_info.current_klass(), link_info.check_access());
    resolved_method = linktime_resolve_static_method(new_info, CHECK);
  }

  assert(save_resolved_method == resolved_method(), "does this change?");
  // setup result
  result.set_static(resolved_klass, resolved_method, CHECK);
}

// throws linktime exceptions
methodHandle LinkResolver::linktime_resolve_static_method(const LinkInfo& link_info, TRAPS) {

  KlassHandle resolved_klass = link_info.resolved_klass();
  methodHandle resolved_method;
  if (!resolved_klass->is_interface()) {
    resolved_method = resolve_method(link_info, /*require_methodref*/false, CHECK_NULL);
  } else {
    resolved_method = resolve_interface_method(link_info, /*nostatics*/false, CHECK_NULL);
  }
  assert(resolved_method->name() != vmSymbols::class_initializer_name(), "should have been checked in verifier");

  // check if static
  if (!resolved_method->is_static()) {
    ResourceMark rm(THREAD);
    char buf[200];
    jio_snprintf(buf, sizeof(buf), "Expected static method %s", Method::name_and_sig_as_C_string(resolved_klass(),
                                                      resolved_method->name(),
                                                      resolved_method->signature()));
    THROW_MSG_NULL(vmSymbols::java_lang_IncompatibleClassChangeError(), buf);
  }
  return resolved_method;
}


void LinkResolver::resolve_special_call(CallInfo& result,
                                        const LinkInfo& link_info,
                                        TRAPS) {
  methodHandle resolved_method = linktime_resolve_special_method(link_info, CHECK);
  runtime_resolve_special_method(result, resolved_method,
                                 link_info.resolved_klass(),
                                 link_info.current_klass(),
                                 link_info.check_access(), CHECK);
}

// throws linktime exceptions
methodHandle LinkResolver::linktime_resolve_special_method(const LinkInfo& link_info,
                                                           TRAPS) {

  // Invokespecial is called for multiple special reasons:
  // <init>
  // local private method invocation, for classes and interfaces
  // superclass.method, which can also resolve to a default method
  // and the selected method is recalculated relative to the direct superclass
  // superinterface.method, which explicitly does not check shadowing
  KlassHandle resolved_klass = link_info.resolved_klass();
  methodHandle resolved_method;

  if (!resolved_klass->is_interface()) {
    resolved_method = resolve_method(link_info, /*require_methodref*/false, CHECK_NULL);
  } else {
    resolved_method = resolve_interface_method(link_info, /*nostatics*/true, CHECK_NULL);
  }

  // check if method name is <init>, that it is found in same klass as static type
  if (resolved_method->name() == vmSymbols::object_initializer_name() &&
      resolved_method->method_holder() != resolved_klass()) {
    ResourceMark rm(THREAD);
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbols::java_lang_NoSuchMethodError(),
      "%s: method %s%s not found",
      resolved_klass->external_name(),
      resolved_method->name()->as_C_string(),
      resolved_method->signature()->as_C_string()
    );
    return NULL;
  }

  // check if invokespecial's interface method reference is in an indirect superinterface
  KlassHandle current_klass = link_info.current_klass();
  if (!current_klass.is_null() && resolved_klass->is_interface()) {
    Klass *klass_to_check = !InstanceKlass::cast(current_klass())->is_anonymous() ?
                                  current_klass() :
                                  InstanceKlass::cast(current_klass())->host_klass();
    // Disable verification for the dynamically-generated reflection bytecodes.
    bool is_reflect = klass_to_check->is_subclass_of(
                        SystemDictionary::reflect_MagicAccessorImpl_klass());

    if (!is_reflect &&
        !InstanceKlass::cast(klass_to_check)->is_same_or_direct_interface(resolved_klass())) {
      ResourceMark rm(THREAD);
      char buf[200];
      jio_snprintf(buf, sizeof(buf),
                   "Interface method reference: %s, is in an indirect superinterface of %s",
                   Method::name_and_sig_as_C_string(resolved_klass(),
                                                         resolved_method->name(),
                                                         resolved_method->signature()),
                   current_klass->external_name());
      THROW_MSG_NULL(vmSymbols::java_lang_IncompatibleClassChangeError(), buf);
    }
  }

  // check if not static
  if (resolved_method->is_static()) {
    ResourceMark rm(THREAD);
    char buf[200];
    jio_snprintf(buf, sizeof(buf),
                 "Expecting non-static method %s",
                 Method::name_and_sig_as_C_string(resolved_klass(),
                                                  resolved_method->name(),
                                                  resolved_method->signature()));
    THROW_MSG_NULL(vmSymbols::java_lang_IncompatibleClassChangeError(), buf);
  }

  if (log_develop_is_enabled(Trace, itables)) {
    trace_method_resolution("invokespecial resolved method: caller-class:",
                            current_klass, resolved_klass, resolved_method, true);
  }

  return resolved_method;
}

// throws runtime exceptions
void LinkResolver::runtime_resolve_special_method(CallInfo& result,
                                                  const methodHandle& resolved_method,
                                                  KlassHandle resolved_klass,
                                                  KlassHandle current_klass,
                                                  bool check_access, TRAPS) {

  // resolved method is selected method unless we have an old-style lookup
  // for a superclass method
  // Invokespecial for a superinterface, resolved method is selected method,
  // no checks for shadowing
  methodHandle sel_method(THREAD, resolved_method());

  // check if this is an old-style super call and do a new lookup if so
  { KlassHandle method_klass  = KlassHandle(THREAD,
                                            resolved_method->method_holder());

    if (check_access &&
        // a) check if ACC_SUPER flag is set for the current class
        (current_klass->is_super() || !AllowNonVirtualCalls) &&
        // b) check if the class of the resolved_klass is a superclass
        // (not supertype in order to exclude interface classes) of the current class.
        // This check is not performed for super.invoke for interface methods
        // in super interfaces.
        current_klass->is_subclass_of(resolved_klass()) &&
        current_klass() != resolved_klass() &&
        // c) check if the method is not <init>
        resolved_method->name() != vmSymbols::object_initializer_name()) {
      // Lookup super method
      KlassHandle super_klass(THREAD, current_klass->super());
      sel_method = lookup_instance_method_in_klasses(super_klass,
                           resolved_method->name(),
                           resolved_method->signature(), CHECK);
      // check if found
      if (sel_method.is_null()) {
        ResourceMark rm(THREAD);
        THROW_MSG(vmSymbols::java_lang_AbstractMethodError(),
                  Method::name_and_sig_as_C_string(resolved_klass(),
                                            resolved_method->name(),
                                            resolved_method->signature()));
      }
    }
  }

  // check if not static
  if (sel_method->is_static()) {
    ResourceMark rm(THREAD);
    char buf[200];
    jio_snprintf(buf, sizeof(buf), "Expecting non-static method %s", Method::name_and_sig_as_C_string(resolved_klass(),
                                                                                                             resolved_method->name(),
                                                                                                             resolved_method->signature()));
    THROW_MSG(vmSymbols::java_lang_IncompatibleClassChangeError(), buf);
  }

  // check if abstract
  if (sel_method->is_abstract()) {
    ResourceMark rm(THREAD);
    THROW_MSG(vmSymbols::java_lang_AbstractMethodError(),
              Method::name_and_sig_as_C_string(resolved_klass(),
                                               sel_method->name(),
                                               sel_method->signature()));
  }

  if (log_develop_is_enabled(Trace, itables)) {
    trace_method_resolution("invokespecial selected method: resolved-class:",
                            resolved_klass, resolved_klass, sel_method, true);
  }

  // setup result
  result.set_static(resolved_klass, sel_method, CHECK);
}

void LinkResolver::resolve_virtual_call(CallInfo& result, Handle recv, KlassHandle receiver_klass,
                                        const LinkInfo& link_info,
                                        bool check_null_and_abstract, TRAPS) {
  methodHandle resolved_method = linktime_resolve_virtual_method(link_info, CHECK);
  runtime_resolve_virtual_method(result, resolved_method,
                                 link_info.resolved_klass(),
                                 recv, receiver_klass,
                                 check_null_and_abstract, CHECK);
}

// throws linktime exceptions
methodHandle LinkResolver::linktime_resolve_virtual_method(const LinkInfo& link_info,
                                                           TRAPS) {
  // normal method resolution
  methodHandle resolved_method = resolve_method(link_info, /*require_methodref*/true, CHECK_NULL);

  assert(resolved_method->name() != vmSymbols::object_initializer_name(), "should have been checked in verifier");
  assert(resolved_method->name() != vmSymbols::class_initializer_name (), "should have been checked in verifier");

  // check if private interface method
  KlassHandle resolved_klass = link_info.resolved_klass();
  KlassHandle current_klass = link_info.current_klass();

  if (resolved_klass->is_interface() && resolved_method->is_private()) {
    ResourceMark rm(THREAD);
    char buf[200];
    jio_snprintf(buf, sizeof(buf), "private interface method requires invokespecial, not invokevirtual: method %s, caller-class:%s",
                 Method::name_and_sig_as_C_string(resolved_klass(),
                                                  resolved_method->name(),
                                                  resolved_method->signature()),
                   (current_klass.is_null() ? "<NULL>" : current_klass->internal_name()));
    THROW_MSG_NULL(vmSymbols::java_lang_IncompatibleClassChangeError(), buf);
  }

  // check if not static
  if (resolved_method->is_static()) {
    ResourceMark rm(THREAD);
    char buf[200];
    jio_snprintf(buf, sizeof(buf), "Expecting non-static method %s", Method::name_and_sig_as_C_string(resolved_klass(),
                                                                                                             resolved_method->name(),
                                                                                                             resolved_method->signature()));
    THROW_MSG_NULL(vmSymbols::java_lang_IncompatibleClassChangeError(), buf);
  }

  if (log_develop_is_enabled(Trace, vtables)) {
    trace_method_resolution("invokevirtual resolved method: caller-class:",
                            current_klass, resolved_klass, resolved_method, false);
  }

  return resolved_method;
}

// throws runtime exceptions
void LinkResolver::runtime_resolve_virtual_method(CallInfo& result,
                                                  const methodHandle& resolved_method,
                                                  KlassHandle resolved_klass,
                                                  Handle recv,
                                                  KlassHandle recv_klass,
                                                  bool check_null_and_abstract,
                                                  TRAPS) {

  // setup default return values
  int vtable_index = Method::invalid_vtable_index;
  methodHandle selected_method;

  assert(recv.is_null() || recv->is_oop(), "receiver is not an oop");

  // runtime method resolution
  if (check_null_and_abstract && recv.is_null()) { // check if receiver exists
    THROW(vmSymbols::java_lang_NullPointerException());
  }

  // Virtual methods cannot be resolved before its klass has been linked, for otherwise the Method*'s
  // has not been rewritten, and the vtable initialized. Make sure to do this after the nullcheck, since
  // a missing receiver might result in a bogus lookup.
  assert(resolved_method->method_holder()->is_linked(), "must be linked");

  // do lookup based on receiver klass using the vtable index
  if (resolved_method->method_holder()->is_interface()) { // default or miranda method
    vtable_index = vtable_index_of_interface_method(resolved_klass,
                           resolved_method);
    assert(vtable_index >= 0 , "we should have valid vtable index at this point");

    selected_method = methodHandle(THREAD, recv_klass->method_at_vtable(vtable_index));
  } else {
    // at this point we are sure that resolved_method is virtual and not
    // a default or miranda method; therefore, it must have a valid vtable index.
    assert(!resolved_method->has_itable_index(), "");
    vtable_index = resolved_method->vtable_index();
    // We could get a negative vtable_index for final methods,
    // because as an optimization they are they are never put in the vtable,
    // unless they override an existing method.
    // If we do get a negative, it means the resolved method is the the selected
    // method, and it can never be changed by an override.
    if (vtable_index == Method::nonvirtual_vtable_index) {
      assert(resolved_method->can_be_statically_bound(), "cannot override this method");
      selected_method = resolved_method;
    } else {
      selected_method = methodHandle(THREAD, recv_klass->method_at_vtable(vtable_index));
    }
  }

  // check if method exists
  if (selected_method.is_null()) {
    ResourceMark rm(THREAD);
    THROW_MSG(vmSymbols::java_lang_AbstractMethodError(),
              Method::name_and_sig_as_C_string(resolved_klass(),
                                                      resolved_method->name(),
                                                      resolved_method->signature()));
  }

  // check if abstract
  if (check_null_and_abstract && selected_method->is_abstract()) {
    ResourceMark rm(THREAD);
    THROW_MSG(vmSymbols::java_lang_AbstractMethodError(),
              Method::name_and_sig_as_C_string(resolved_klass(),
                                                      selected_method->name(),
                                                      selected_method->signature()));
  }

  if (log_develop_is_enabled(Trace, vtables)) {
    trace_method_resolution("invokevirtual selected method: receiver-class:",
                            recv_klass, resolved_klass, selected_method,
                            false, vtable_index);
  }
  // setup result
  result.set_virtual(resolved_klass, recv_klass, resolved_method, selected_method, vtable_index, CHECK);
}

void LinkResolver::resolve_interface_call(CallInfo& result, Handle recv, KlassHandle recv_klass,
                                          const LinkInfo& link_info,
                                          bool check_null_and_abstract, TRAPS) {
  // throws linktime exceptions
  methodHandle resolved_method = linktime_resolve_interface_method(link_info, CHECK);
  runtime_resolve_interface_method(result, resolved_method,link_info.resolved_klass(),
                                   recv, recv_klass, check_null_and_abstract, CHECK);
}

methodHandle LinkResolver::linktime_resolve_interface_method(const LinkInfo& link_info,
                                                             TRAPS) {
  // normal interface method resolution
  methodHandle resolved_method = resolve_interface_method(link_info, true, CHECK_NULL);
  assert(resolved_method->name() != vmSymbols::object_initializer_name(), "should have been checked in verifier");
  assert(resolved_method->name() != vmSymbols::class_initializer_name (), "should have been checked in verifier");

  return resolved_method;
}

// throws runtime exceptions
void LinkResolver::runtime_resolve_interface_method(CallInfo& result,
                                                    const methodHandle& resolved_method,
                                                    KlassHandle resolved_klass,
                                                    Handle recv,
                                                    KlassHandle recv_klass,
                                                    bool check_null_and_abstract, TRAPS) {
  // check if receiver exists
  if (check_null_and_abstract && recv.is_null()) {
    THROW(vmSymbols::java_lang_NullPointerException());
  }

  // check if private interface method
  if (resolved_klass->is_interface() && resolved_method->is_private()) {
    ResourceMark rm(THREAD);
    char buf[200];
    jio_snprintf(buf, sizeof(buf), "private interface method requires invokespecial, not invokeinterface: method %s",
                 Method::name_and_sig_as_C_string(resolved_klass(),
                                                  resolved_method->name(),
                                                  resolved_method->signature()));
    THROW_MSG(vmSymbols::java_lang_IncompatibleClassChangeError(), buf);
  }

  // check if receiver klass implements the resolved interface
  if (!recv_klass->is_subtype_of(resolved_klass())) {
    ResourceMark rm(THREAD);
    char buf[200];
    jio_snprintf(buf, sizeof(buf), "Class %s does not implement the requested interface %s",
                 recv_klass()->external_name(),
                 resolved_klass()->external_name());
    THROW_MSG(vmSymbols::java_lang_IncompatibleClassChangeError(), buf);
  }

  // do lookup based on receiver klass
  // This search must match the linktime preparation search for itable initialization
  // to correctly enforce loader constraints for interface method inheritance
  methodHandle sel_method = lookup_instance_method_in_klasses(recv_klass,
                                                  resolved_method->name(),
                                                  resolved_method->signature(), CHECK);
  if (sel_method.is_null() && !check_null_and_abstract) {
    // In theory this is a harmless placeholder value, but
    // in practice leaving in null affects the nsk default method tests.
    // This needs further study.
    sel_method = resolved_method;
  }
  // check if method exists
  if (sel_method.is_null()) {
    ResourceMark rm(THREAD);
    THROW_MSG(vmSymbols::java_lang_AbstractMethodError(),
                   Method::name_and_sig_as_C_string(recv_klass(),
                                                    resolved_method->name(),
                                                    resolved_method->signature()));
  }
  // check access
  // Throw Illegal Access Error if sel_method is not public.
  if (!sel_method->is_public()) {
    ResourceMark rm(THREAD);
    THROW_MSG(vmSymbols::java_lang_IllegalAccessError(),
              Method::name_and_sig_as_C_string(recv_klass(),
                                               sel_method->name(),
                                               sel_method->signature()));
  }
  // check if abstract
  if (check_null_and_abstract && sel_method->is_abstract()) {
    ResourceMark rm(THREAD);
    THROW_MSG(vmSymbols::java_lang_AbstractMethodError(),
              Method::name_and_sig_as_C_string(recv_klass(),
                                                      sel_method->name(),
                                                      sel_method->signature()));
  }

  if (log_develop_is_enabled(Trace, itables)) {
    trace_method_resolution("invokeinterface selected method: receiver-class",
                            recv_klass, resolved_klass, sel_method, true);
  }
  // setup result
  if (!resolved_method->has_itable_index()) {
    int vtable_index = resolved_method->vtable_index();
    assert(vtable_index == sel_method->vtable_index(), "sanity check");
    result.set_virtual(resolved_klass, recv_klass, resolved_method, sel_method, vtable_index, CHECK);
  } else {
    int itable_index = resolved_method()->itable_index();
    result.set_interface(resolved_klass, recv_klass, resolved_method, sel_method, itable_index, CHECK);
  }
}


methodHandle LinkResolver::linktime_resolve_interface_method_or_null(
                                                 const LinkInfo& link_info) {
  EXCEPTION_MARK;
  methodHandle method_result = linktime_resolve_interface_method(link_info, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    CLEAR_PENDING_EXCEPTION;
    return methodHandle();
  } else {
    return method_result;
  }
}

methodHandle LinkResolver::linktime_resolve_virtual_method_or_null(
                                                 const LinkInfo& link_info) {
  EXCEPTION_MARK;
  methodHandle method_result = linktime_resolve_virtual_method(link_info, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    CLEAR_PENDING_EXCEPTION;
    return methodHandle();
  } else {
    return method_result;
  }
}

methodHandle LinkResolver::resolve_virtual_call_or_null(
                                                 KlassHandle receiver_klass,
                                                 const LinkInfo& link_info) {
  EXCEPTION_MARK;
  CallInfo info;
  resolve_virtual_call(info, Handle(), receiver_klass, link_info, false, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    CLEAR_PENDING_EXCEPTION;
    return methodHandle();
  }
  return info.selected_method();
}

methodHandle LinkResolver::resolve_interface_call_or_null(
                                                 KlassHandle receiver_klass,
                                                 const LinkInfo& link_info) {
  EXCEPTION_MARK;
  CallInfo info;
  resolve_interface_call(info, Handle(), receiver_klass, link_info, false, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    CLEAR_PENDING_EXCEPTION;
    return methodHandle();
  }
  return info.selected_method();
}

int LinkResolver::resolve_virtual_vtable_index(KlassHandle receiver_klass,
                                               const LinkInfo& link_info) {
  EXCEPTION_MARK;
  CallInfo info;
  resolve_virtual_call(info, Handle(), receiver_klass, link_info,
                       /*check_null_or_abstract*/false, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    CLEAR_PENDING_EXCEPTION;
    return Method::invalid_vtable_index;
  }
  return info.vtable_index();
}

methodHandle LinkResolver::resolve_static_call_or_null(const LinkInfo& link_info) {
  EXCEPTION_MARK;
  CallInfo info;
  resolve_static_call(info, link_info, /*initialize_class*/false, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    CLEAR_PENDING_EXCEPTION;
    return methodHandle();
  }
  return info.selected_method();
}

methodHandle LinkResolver::resolve_special_call_or_null(const LinkInfo& link_info) {
  EXCEPTION_MARK;
  CallInfo info;
  resolve_special_call(info, link_info, THREAD);
  if (HAS_PENDING_EXCEPTION) {
    CLEAR_PENDING_EXCEPTION;
    return methodHandle();
  }
  return info.selected_method();
}



//------------------------------------------------------------------------------------------------------------------------
// ConstantPool entries

void LinkResolver::resolve_invoke(CallInfo& result, Handle recv, const constantPoolHandle& pool, int index, Bytecodes::Code byte, TRAPS) {
  switch (byte) {
    case Bytecodes::_invokestatic   : resolve_invokestatic   (result,       pool, index, CHECK); break;
    case Bytecodes::_invokespecial  : resolve_invokespecial  (result,       pool, index, CHECK); break;
    case Bytecodes::_invokevirtual  : resolve_invokevirtual  (result, recv, pool, index, CHECK); break;
    case Bytecodes::_invokehandle   : resolve_invokehandle   (result,       pool, index, CHECK); break;
    case Bytecodes::_invokedynamic  : resolve_invokedynamic  (result,       pool, index, CHECK); break;
    case Bytecodes::_invokeinterface: resolve_invokeinterface(result, recv, pool, index, CHECK); break;
  }
  return;
}

void LinkResolver::resolve_invoke(CallInfo& result, Handle& recv,
                             const methodHandle& attached_method,
                             Bytecodes::Code byte, TRAPS) {
  KlassHandle defc = attached_method->method_holder();
  Symbol* name = attached_method->name();
  Symbol* type = attached_method->signature();
  LinkInfo link_info(defc, name, type, KlassHandle(), /*check_access=*/false);
  switch(byte) {
    case Bytecodes::_invokevirtual:
      resolve_virtual_call(result, recv, recv->klass(), link_info,
                           /*check_null_and_abstract=*/true, CHECK);
      break;
    case Bytecodes::_invokeinterface:
      resolve_interface_call(result, recv, recv->klass(), link_info,
                             /*check_null_and_abstract=*/true, CHECK);
      break;
    case Bytecodes::_invokestatic:
      resolve_static_call(result, link_info, /*initialize_class=*/false, CHECK);
      break;
    case Bytecodes::_invokespecial:
      resolve_special_call(result, link_info, CHECK);
      break;
    default:
      fatal("bad call: %s", Bytecodes::name(byte));
  }
}

void LinkResolver::resolve_invokestatic(CallInfo& result, const constantPoolHandle& pool, int index, TRAPS) {
  LinkInfo link_info(pool, index, CHECK);
  resolve_static_call(result, link_info, /*initialize_class*/true, CHECK);
}


void LinkResolver::resolve_invokespecial(CallInfo& result, const constantPoolHandle& pool, int index, TRAPS) {
  LinkInfo link_info(pool, index, CHECK);
  resolve_special_call(result, link_info, CHECK);
}


void LinkResolver::resolve_invokevirtual(CallInfo& result, Handle recv,
                                          const constantPoolHandle& pool, int index,
                                          TRAPS) {

  LinkInfo link_info(pool, index, CHECK);
  KlassHandle recvrKlass (THREAD, recv.is_null() ? (Klass*)NULL : recv->klass());
  resolve_virtual_call(result, recv, recvrKlass, link_info, /*check_null_or_abstract*/true, CHECK);
}


void LinkResolver::resolve_invokeinterface(CallInfo& result, Handle recv, const constantPoolHandle& pool, int index, TRAPS) {
  LinkInfo link_info(pool, index, CHECK);
  KlassHandle recvrKlass (THREAD, recv.is_null() ? (Klass*)NULL : recv->klass());
  resolve_interface_call(result, recv, recvrKlass, link_info, true, CHECK);
}


void LinkResolver::resolve_invokehandle(CallInfo& result, const constantPoolHandle& pool, int index, TRAPS) {
  // This guy is reached from InterpreterRuntime::resolve_invokehandle.
  LinkInfo link_info(pool, index, CHECK);
  if (TraceMethodHandles) {
    ResourceMark rm(THREAD);
    tty->print_cr("resolve_invokehandle %s %s", link_info.name()->as_C_string(),
                  link_info.signature()->as_C_string());
  }
  resolve_handle_call(result, link_info, CHECK);
}

void LinkResolver::resolve_handle_call(CallInfo& result,
                                       const LinkInfo& link_info,
                                       TRAPS) {
  // JSR 292:  this must be an implicitly generated method MethodHandle.invokeExact(*...) or similar
  KlassHandle resolved_klass = link_info.resolved_klass();
  assert(resolved_klass() == SystemDictionary::MethodHandle_klass() ||
         resolved_klass() == SystemDictionary::VarHandle_klass(), "");
  assert(MethodHandles::is_signature_polymorphic_name(link_info.name()), "");
  Handle       resolved_appendix;
  Handle       resolved_method_type;
  methodHandle resolved_method = lookup_polymorphic_method(link_info,
                                       &resolved_appendix, &resolved_method_type, CHECK);
  result.set_handle(resolved_klass, resolved_method, resolved_appendix, resolved_method_type, CHECK);
}

static void wrap_invokedynamic_exception(TRAPS) {
  if (HAS_PENDING_EXCEPTION) {
    if (TraceMethodHandles) {
      tty->print_cr("invokedynamic throws BSME for " INTPTR_FORMAT, p2i((void *)PENDING_EXCEPTION));
      PENDING_EXCEPTION->print();
    }
    if (PENDING_EXCEPTION->is_a(SystemDictionary::BootstrapMethodError_klass())) {
      // throw these guys, since they are already wrapped
      return;
    }
    if (!PENDING_EXCEPTION->is_a(SystemDictionary::LinkageError_klass())) {
      // intercept only LinkageErrors which might have failed to wrap
      return;
    }
    // See the "Linking Exceptions" section for the invokedynamic instruction in the JVMS.
    Handle nested_exception(THREAD, PENDING_EXCEPTION);
    CLEAR_PENDING_EXCEPTION;
    THROW_CAUSE(vmSymbols::java_lang_BootstrapMethodError(), nested_exception)
  }
}

void LinkResolver::resolve_invokedynamic(CallInfo& result, const constantPoolHandle& pool, int index, TRAPS) {
  Symbol* method_name       = pool->name_ref_at(index);
  Symbol* method_signature  = pool->signature_ref_at(index);
  KlassHandle current_klass = KlassHandle(THREAD, pool->pool_holder());

  // Resolve the bootstrap specifier (BSM + optional arguments).
  Handle bootstrap_specifier;
  // Check if CallSite has been bound already:
  ConstantPoolCacheEntry* cpce = pool->invokedynamic_cp_cache_entry_at(index);
  if (cpce->is_f1_null()) {
    int pool_index = cpce->constant_pool_index();
    oop bsm_info = pool->resolve_bootstrap_specifier_at(pool_index, THREAD);
    wrap_invokedynamic_exception(CHECK);
    assert(bsm_info != NULL, "");
    // FIXME: Cache this once per BootstrapMethods entry, not once per CONSTANT_InvokeDynamic.
    bootstrap_specifier = Handle(THREAD, bsm_info);
  }
  if (!cpce->is_f1_null()) {
    methodHandle method(     THREAD, cpce->f1_as_method());
    Handle       appendix(   THREAD, cpce->appendix_if_resolved(pool));
    Handle       method_type(THREAD, cpce->method_type_if_resolved(pool));
    result.set_handle(method, appendix, method_type, THREAD);
    wrap_invokedynamic_exception(CHECK);
    return;
  }

  if (TraceMethodHandles) {
    ResourceMark rm(THREAD);
    tty->print_cr("resolve_invokedynamic #%d %s %s in %s",
                  ConstantPool::decode_invokedynamic_index(index),
                  method_name->as_C_string(), method_signature->as_C_string(),
                  current_klass->name()->as_C_string());
    tty->print("  BSM info: "); bootstrap_specifier->print();
  }

  resolve_dynamic_call(result, bootstrap_specifier, method_name, method_signature, current_klass, CHECK);
}

void LinkResolver::resolve_dynamic_call(CallInfo& result,
                                        Handle bootstrap_specifier,
                                        Symbol* method_name, Symbol* method_signature,
                                        KlassHandle current_klass,
                                        TRAPS) {
  // JSR 292:  this must resolve to an implicitly generated method MH.linkToCallSite(*...)
  // The appendix argument is likely to be a freshly-created CallSite.
  Handle       resolved_appendix;
  Handle       resolved_method_type;
  methodHandle resolved_method =
    SystemDictionary::find_dynamic_call_site_invoker(current_klass,
                                                     bootstrap_specifier,
                                                     method_name, method_signature,
                                                     &resolved_appendix,
                                                     &resolved_method_type,
                                                     THREAD);
  wrap_invokedynamic_exception(CHECK);
  result.set_handle(resolved_method, resolved_appendix, resolved_method_type, THREAD);
  wrap_invokedynamic_exception(CHECK);
}
