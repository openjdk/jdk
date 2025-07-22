/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotClassLinker.hpp"
#include "cds/aotConstantPoolResolver.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/cdsConfig.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "memory/resourceArea.hpp"
#include "oops/constantPool.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "runtime/handles.inline.hpp"

// Returns true if we CAN PROVE that cp_index will always resolve to
// the same information at both dump time and run time. This is a
// necessary (but not sufficient) condition for pre-resolving cp_index
// during CDS archive assembly.
bool AOTConstantPoolResolver::is_resolution_deterministic(ConstantPool* cp, int cp_index) {
  assert(!is_in_archivebuilder_buffer(cp), "sanity");

  if (cp->tag_at(cp_index).is_klass()) {
    // We require cp_index to be already resolved. This is fine for now, are we
    // currently archive only CP entries that are already resolved.
    Klass* resolved_klass = cp->resolved_klass_at(cp_index);
    return resolved_klass != nullptr && is_class_resolution_deterministic(cp->pool_holder(), resolved_klass);
  } else if (cp->tag_at(cp_index).is_invoke_dynamic()) {
    return is_indy_resolution_deterministic(cp, cp_index);
  } else if (cp->tag_at(cp_index).is_field() ||
             cp->tag_at(cp_index).is_method() ||
             cp->tag_at(cp_index).is_interface_method()) {
    int klass_cp_index = cp->uncached_klass_ref_index_at(cp_index);
    if (!cp->tag_at(klass_cp_index).is_klass()) {
      // Not yet resolved
      return false;
    }
    Klass* k = cp->resolved_klass_at(klass_cp_index);
    if (!is_class_resolution_deterministic(cp->pool_holder(), k)) {
      return false;
    }

    if (!k->is_instance_klass()) {
      // TODO: support non instance klasses as well.
      return false;
    }

    // Here, We don't check if this entry can actually be resolved to a valid Field/Method.
    // This method should be called by the ConstantPool to check Fields/Methods that
    // have already been successfully resolved.
    return true;
  } else {
    return false;
  }
}

bool AOTConstantPoolResolver::is_class_resolution_deterministic(InstanceKlass* cp_holder, Klass* resolved_class) {
  assert(!is_in_archivebuilder_buffer(cp_holder), "sanity");
  assert(!is_in_archivebuilder_buffer(resolved_class), "sanity");

  if (resolved_class->is_instance_klass()) {
    InstanceKlass* ik = InstanceKlass::cast(resolved_class);

    if (!ik->is_shared() && SystemDictionaryShared::is_excluded_class(ik)) {
      return false;
    }

    if (cp_holder->is_subtype_of(ik)) {
      // All super types of ik will be resolved in ik->class_loader() before
      // ik is defined in this loader, so it's safe to archive the resolved klass reference.
      return true;
    }

    if (CDSConfig::is_dumping_aot_linked_classes()) {
      // Need to call try_add_candidate instead of is_candidate, as this may be called
      // before AOTClassLinker::add_candidates().
      if (AOTClassLinker::try_add_candidate(ik)) {
        return true;
      } else {
        return false;
      }
    } else if (AOTClassLinker::is_vm_class(ik)) {
      if (ik->class_loader() != cp_holder->class_loader()) {
        // At runtime, cp_holder() may not be able to resolve to the same
        // ik. For example, a different version of ik may be defined in
        // cp->pool_holder()'s loader using MethodHandles.Lookup.defineClass().
        return false;
      } else {
        return true;
      }
    } else {
      return false;
    }
  } else if (resolved_class->is_objArray_klass()) {
    Klass* elem = ObjArrayKlass::cast(resolved_class)->bottom_klass();
    if (elem->is_instance_klass()) {
      return is_class_resolution_deterministic(cp_holder, InstanceKlass::cast(elem));
    } else if (elem->is_typeArray_klass()) {
      return true;
    } else {
      return false;
    }
  } else if (resolved_class->is_typeArray_klass()) {
    return true;
  } else {
    return false;
  }
}

void AOTConstantPoolResolver::preresolve_string_cp_entries(InstanceKlass* ik, TRAPS) {
  if (!ik->is_linked()) {
    // The cp->resolved_referenced() array is not ready yet, so we can't call resolve_string().
    return;
  }
  constantPoolHandle cp(THREAD, ik->constants());
  for (int cp_index = 1; cp_index < cp->length(); cp_index++) { // Index 0 is unused
    switch (cp->tag_at(cp_index).value()) {
    case JVM_CONSTANT_String:
      resolve_string(cp, cp_index, CHECK); // may throw OOM when interning strings.
      break;
    }
  }
}

// This works only for the boot/platform/app loaders
Klass* AOTConstantPoolResolver::find_loaded_class(Thread* current, oop class_loader, Symbol* name) {
  HandleMark hm(current);
  Handle h_loader(current, class_loader);
  Klass* k = SystemDictionary::find_instance_or_array_klass(current, name, h_loader);
  if (k != nullptr) {
    return k;
  }
  if (h_loader() == SystemDictionary::java_system_loader()) {
    return find_loaded_class(current, SystemDictionary::java_platform_loader(), name);
  } else if (h_loader() == SystemDictionary::java_platform_loader()) {
    return find_loaded_class(current, nullptr, name);
  } else {
    assert(h_loader() == nullptr, "This function only works for boot/platform/app loaders %p %p %p",
           cast_from_oop<address>(h_loader()),
           cast_from_oop<address>(SystemDictionary::java_system_loader()),
           cast_from_oop<address>(SystemDictionary::java_platform_loader()));
  }

  return nullptr;
}

Klass* AOTConstantPoolResolver::find_loaded_class(Thread* current, ConstantPool* cp, int class_cp_index) {
  Symbol* name = cp->klass_name_at(class_cp_index);
  return find_loaded_class(current, cp->pool_holder()->class_loader(), name);
}

#if INCLUDE_CDS_JAVA_HEAP
void AOTConstantPoolResolver::resolve_string(constantPoolHandle cp, int cp_index, TRAPS) {
  if (CDSConfig::is_dumping_heap()) {
    int cache_index = cp->cp_to_object_index(cp_index);
    ConstantPool::string_at_impl(cp, cp_index, cache_index, CHECK);
  }
}
#endif

void AOTConstantPoolResolver::preresolve_class_cp_entries(JavaThread* current, InstanceKlass* ik, GrowableArray<bool>* preresolve_list) {
  if (!SystemDictionaryShared::is_builtin_loader(ik->class_loader_data())) {
    return;
  }

  JavaThread* THREAD = current;
  constantPoolHandle cp(THREAD, ik->constants());
  for (int cp_index = 1; cp_index < cp->length(); cp_index++) {
    if (cp->tag_at(cp_index).value() == JVM_CONSTANT_UnresolvedClass) {
      if (preresolve_list != nullptr && preresolve_list->at(cp_index) == false) {
        // This class was not resolved during trial run. Don't attempt to resolve it. Otherwise
        // the compiler may generate less efficient code.
        continue;
      }
      if (find_loaded_class(current, cp(), cp_index) == nullptr) {
        // Do not resolve any class that has not been loaded yet
        continue;
      }
      Klass* resolved_klass = cp->klass_at(cp_index, THREAD);
      if (HAS_PENDING_EXCEPTION) {
        CLEAR_PENDING_EXCEPTION; // just ignore
      } else {
        log_trace(aot, resolve)("Resolved class  [%3d] %s -> %s", cp_index, ik->external_name(),
                                resolved_klass->external_name());
      }
    }
  }
}

void AOTConstantPoolResolver::preresolve_field_and_method_cp_entries(JavaThread* current, InstanceKlass* ik, GrowableArray<bool>* preresolve_list) {
  JavaThread* THREAD = current;
  constantPoolHandle cp(THREAD, ik->constants());
  if (cp->cache() == nullptr) {
    return;
  }
  for (int i = 0; i < ik->methods()->length(); i++) {
    Method* m = ik->methods()->at(i);
    BytecodeStream bcs(methodHandle(THREAD, m));
    while (!bcs.is_last_bytecode()) {
      bcs.next();
      Bytecodes::Code raw_bc = bcs.raw_code();
      switch (raw_bc) {
      case Bytecodes::_getfield:
      case Bytecodes::_putfield:
        maybe_resolve_fmi_ref(ik, m, raw_bc, bcs.get_index_u2(), preresolve_list, THREAD);
        if (HAS_PENDING_EXCEPTION) {
          CLEAR_PENDING_EXCEPTION; // just ignore
        }
        break;
      case Bytecodes::_invokehandle:
      case Bytecodes::_invokespecial:
      case Bytecodes::_invokevirtual:
      case Bytecodes::_invokeinterface:
        maybe_resolve_fmi_ref(ik, m, raw_bc, bcs.get_index_u2(), preresolve_list, THREAD);
        if (HAS_PENDING_EXCEPTION) {
          CLEAR_PENDING_EXCEPTION; // just ignore
        }
        break;
      default:
        break;
      }
    }
  }
}

void AOTConstantPoolResolver::maybe_resolve_fmi_ref(InstanceKlass* ik, Method* m, Bytecodes::Code bc, int raw_index,
                                           GrowableArray<bool>* preresolve_list, TRAPS) {
  methodHandle mh(THREAD, m);
  constantPoolHandle cp(THREAD, ik->constants());
  HandleMark hm(THREAD);
  int cp_index = cp->to_cp_index(raw_index, bc);

  if (cp->is_resolved(raw_index, bc)) {
    return;
  }

  if (preresolve_list != nullptr && preresolve_list->at(cp_index) == false) {
    // This field wasn't resolved during the trial run. Don't attempt to resolve it. Otherwise
    // the compiler may generate less efficient code.
    return;
  }

  int klass_cp_index = cp->uncached_klass_ref_index_at(cp_index);
  if (find_loaded_class(THREAD, cp(), klass_cp_index) == nullptr) {
    // Do not resolve any field/methods from a class that has not been loaded yet.
    return;
  }

  Klass* resolved_klass = cp->klass_ref_at(raw_index, bc, CHECK);

  switch (bc) {
  case Bytecodes::_getfield:
  case Bytecodes::_putfield:
    InterpreterRuntime::resolve_get_put(bc, raw_index, mh, cp, false /*initialize_holder*/, CHECK);
    break;

  case Bytecodes::_invokevirtual:
  case Bytecodes::_invokespecial:
  case Bytecodes::_invokeinterface:
    InterpreterRuntime::cds_resolve_invoke(bc, raw_index, cp, CHECK);
    break;

  case Bytecodes::_invokehandle:
    InterpreterRuntime::cds_resolve_invokehandle(raw_index, cp, CHECK);
    break;

  default:
    ShouldNotReachHere();
  }

  if (log_is_enabled(Trace, aot, resolve)) {
    ResourceMark rm(THREAD);
    bool resolved = cp->is_resolved(raw_index, bc);
    Symbol* name = cp->name_ref_at(raw_index, bc);
    Symbol* signature = cp->signature_ref_at(raw_index, bc);
    log_trace(aot, resolve)("%s %s [%3d] %s -> %s.%s:%s",
                            (resolved ? "Resolved" : "Failed to resolve"),
                            Bytecodes::name(bc), cp_index, ik->external_name(),
                            resolved_klass->external_name(),
                            name->as_C_string(), signature->as_C_string());
  }
}

void AOTConstantPoolResolver::preresolve_indy_cp_entries(JavaThread* current, InstanceKlass* ik, GrowableArray<bool>* preresolve_list) {
  JavaThread* THREAD = current;
  constantPoolHandle cp(THREAD, ik->constants());
  if (!CDSConfig::is_dumping_invokedynamic() || cp->cache() == nullptr) {
    return;
  }

  assert(preresolve_list != nullptr, "preresolve_indy_cp_entries() should not be called for "
         "regenerated LambdaForm Invoker classes, which should not have indys anyway.");

  Array<ResolvedIndyEntry>* indy_entries = cp->cache()->resolved_indy_entries();
  for (int i = 0; i < indy_entries->length(); i++) {
    ResolvedIndyEntry* rie = indy_entries->adr_at(i);
    int cp_index = rie->constant_pool_index();
    if (preresolve_list->at(cp_index) == true) {
      if (!rie->is_resolved() && is_indy_resolution_deterministic(cp(), cp_index)) {
        InterpreterRuntime::cds_resolve_invokedynamic(i, cp, THREAD);
        if (HAS_PENDING_EXCEPTION) {
          CLEAR_PENDING_EXCEPTION; // just ignore
        }
      }
      if (log_is_enabled(Trace, aot, resolve)) {
        ResourceMark rm(THREAD);
        log_trace(aot, resolve)("%s indy   [%3d] %s",
                                rie->is_resolved() ? "Resolved" : "Failed to resolve",
                                cp_index, ik->external_name());
      }
    }
  }
}

// Check the MethodType signatures used by parameters to the indy BSMs. Make sure we don't
// use types that have been excluded, or else we might end up creating MethodTypes that cannot be stored
// in the AOT cache.
bool AOTConstantPoolResolver::check_methodtype_signature(ConstantPool* cp, Symbol* sig, Klass** return_type_ret) {
  ResourceMark rm;
  for (SignatureStream ss(sig); !ss.is_done(); ss.next()) {
    if (ss.is_reference()) {
      Symbol* type = ss.as_symbol();
      Klass* k = find_loaded_class(Thread::current(), cp->pool_holder()->class_loader(), type);
      if (k == nullptr) {
        return false;
      }

      if (SystemDictionaryShared::should_be_excluded(k)) {
        if (log_is_enabled(Warning, aot, resolve)) {
          ResourceMark rm;
          log_warning(aot, resolve)("Cannot aot-resolve Lambda proxy because %s is excluded", k->external_name());
        }
        return false;
      }

      if (ss.at_return_type() && return_type_ret != nullptr) {
        *return_type_ret = k;
      }
    }
  }
  return true;
}

bool AOTConstantPoolResolver::check_lambda_metafactory_signature(ConstantPool* cp, Symbol* sig) {
  Klass* k;
  if (!check_methodtype_signature(cp, sig, &k)) {
    return false;
  }

  // <k> is the interface type implemented by the lambda proxy
  if (!k->is_interface()) {
    // cp->pool_holder() doesn't look like a valid class generated by javac
    return false;
  }


  // The linked lambda callsite has an instance of the interface implemented by this lambda. If this
  // interface requires its <clinit> to be executed, then we must delay the execution to the production run
  // as <clinit> can have side effects ==> exclude such cases.
  InstanceKlass* intf = InstanceKlass::cast(k);
  bool exclude = intf->interface_needs_clinit_execution_as_super();
  if (log_is_enabled(Debug, aot, resolve)) {
    ResourceMark rm;
    log_debug(aot, resolve)("%s aot-resolve Lambda proxy of interface type %s",
                            exclude ? "Cannot" : "Can", k->external_name());
  }
  return !exclude;
}

bool AOTConstantPoolResolver::check_lambda_metafactory_methodtype_arg(ConstantPool* cp, int bsms_attribute_index, int arg_i) {
  int mt_index = cp->bsm_attribute_entry(bsms_attribute_index)->argument_index(arg_i);
  if (!cp->tag_at(mt_index).is_method_type()) {
    // malformed class?
    return false;
  }

  Symbol* sig = cp->method_type_signature_at(mt_index);
  if (log_is_enabled(Debug, aot, resolve)) {
    ResourceMark rm;
    log_debug(aot, resolve)("Checking MethodType for LambdaMetafactory BSM arg %d: %s", arg_i, sig->as_C_string());
  }

  return check_methodtype_signature(cp, sig);
}

bool AOTConstantPoolResolver::check_lambda_metafactory_methodhandle_arg(ConstantPool* cp, int bsms_attribute_index, int arg_i) {
  int mh_index = cp->bsm_attribute_entry(bsms_attribute_index)->argument_index(arg_i);
  if (!cp->tag_at(mh_index).is_method_handle()) {
    // malformed class?
    return false;
  }

  Symbol* sig = cp->method_handle_signature_ref_at(mh_index);
  if (log_is_enabled(Debug, aot, resolve)) {
    ResourceMark rm;
    log_debug(aot, resolve)("Checking MethodType of MethodHandle for LambdaMetafactory BSM arg %d: %s", arg_i, sig->as_C_string());
  }
  return check_methodtype_signature(cp, sig);
}

bool AOTConstantPoolResolver::is_indy_resolution_deterministic(ConstantPool* cp, int cp_index) {
  assert(cp->tag_at(cp_index).is_invoke_dynamic(), "sanity");
  if (!CDSConfig::is_dumping_invokedynamic()) {
    return false;
  }

  InstanceKlass* pool_holder = cp->pool_holder();
  if (!SystemDictionaryShared::is_builtin(pool_holder)) {
    return false;
  }

  int bsm = cp->bootstrap_method_ref_index_at(cp_index);
  int bsm_ref = cp->method_handle_index_at(bsm);
  Symbol* bsm_name = cp->uncached_name_ref_at(bsm_ref);
  Symbol* bsm_signature = cp->uncached_signature_ref_at(bsm_ref);
  Symbol* bsm_klass = cp->klass_name_at(cp->uncached_klass_ref_index_at(bsm_ref));

  // We currently support only StringConcatFactory::makeConcatWithConstants() and LambdaMetafactory::metafactory()
  // We should mark the allowed BSMs in the JDK code using a private annotation.
  // See notes on RFE JDK-8342481.

  if (bsm_klass->equals("java/lang/invoke/StringConcatFactory") &&
      bsm_name->equals("makeConcatWithConstants") &&
      bsm_signature->equals("(Ljava/lang/invoke/MethodHandles$Lookup;"
                             "Ljava/lang/String;"
                             "Ljava/lang/invoke/MethodType;"
                             "Ljava/lang/String;"
                             "[Ljava/lang/Object;"
                            ")Ljava/lang/invoke/CallSite;")) {
    Symbol* factory_type_sig = cp->uncached_signature_ref_at(cp_index);
    if (log_is_enabled(Debug, aot, resolve)) {
      ResourceMark rm;
      log_debug(aot, resolve)("Checking StringConcatFactory callsite signature [%d]: %s", cp_index, factory_type_sig->as_C_string());
    }

    Klass* k;
    if (!check_methodtype_signature(cp, factory_type_sig, &k)) {
      return false;
    }
    if (k != vmClasses::String_klass()) {
      // bad class file?
      return false;
    }

    return true;
  }

  if (bsm_klass->equals("java/lang/invoke/LambdaMetafactory") &&
      bsm_name->equals("metafactory") &&
      bsm_signature->equals("(Ljava/lang/invoke/MethodHandles$Lookup;"
                             "Ljava/lang/String;"
                             "Ljava/lang/invoke/MethodType;"
                             "Ljava/lang/invoke/MethodType;"
                             "Ljava/lang/invoke/MethodHandle;"
                             "Ljava/lang/invoke/MethodType;"
                            ")Ljava/lang/invoke/CallSite;")) {
    /*
     * An indy callsite is associated with the following MethodType and MethodHandles:
     *
     * https://github.com/openjdk/jdk/blob/580eb62dc097efeb51c76b095c1404106859b673/src/java.base/share/classes/java/lang/invoke/LambdaMetafactory.java#L293-L309
     *
     * MethodType factoryType         The expected signature of the {@code CallSite}.  The
     *                                parameter types represent the types of capture variables;
     *                                the return type is the interface to implement.   When
     *                                used with {@code invokedynamic}, this is provided by
     *                                the {@code NameAndType} of the {@code InvokeDynamic}
     *
     * MethodType interfaceMethodType Signature and return type of method to be
     *                                implemented by the function object.
     *
     * MethodHandle implementation    A direct method handle describing the implementation
     *                                method which should be called (with suitable adaptation
     *                                of argument types and return types, and with captured
     *                                arguments prepended to the invocation arguments) at
     *                                invocation time.
     *
     * MethodType dynamicMethodType   The signature and return type that should
     *                                be enforced dynamically at invocation time.
     *                                In simple use cases this is the same as
     *                                {@code interfaceMethodType}.
     */
    Symbol* factory_type_sig = cp->uncached_signature_ref_at(cp_index);
    if (log_is_enabled(Debug, aot, resolve)) {
      ResourceMark rm;
      log_debug(aot, resolve)("Checking lambda callsite signature [%d]: %s", cp_index, factory_type_sig->as_C_string());
    }

    if (!check_lambda_metafactory_signature(cp, factory_type_sig)) {
      return false;
    }

    int bsms_attribute_index = cp->bootstrap_methods_attribute_index(cp_index);
    int arg_count = cp->bsm_attribute_entry(bsms_attribute_index)->argument_count();
    if (arg_count != 3) {
      // Malformed class?
      return false;
    }

    // interfaceMethodType
    if (!check_lambda_metafactory_methodtype_arg(cp, bsms_attribute_index, 0)) {
      return false;
    }

    // implementation
    if (!check_lambda_metafactory_methodhandle_arg(cp, bsms_attribute_index, 1)) {
      return false;
    }

    // dynamicMethodType
    if (!check_lambda_metafactory_methodtype_arg(cp, bsms_attribute_index, 2)) {
      return false;
    }

    return true;
  }

  return false;
}
#ifdef ASSERT
bool AOTConstantPoolResolver::is_in_archivebuilder_buffer(address p) {
  if (!Thread::current()->is_VM_thread() || ArchiveBuilder::current() == nullptr) {
    return false;
  } else {
    return ArchiveBuilder::current()->is_in_buffer_space(p);
  }
}
#endif
