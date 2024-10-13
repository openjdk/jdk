/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/archiveBuilder.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/classPrelinker.hpp"
#include "cds/regeneratedClasses.hpp"
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

ClassPrelinker::ClassesTable* ClassPrelinker::_processed_classes = nullptr;
ClassPrelinker::ClassesTable* ClassPrelinker::_vm_classes = nullptr;

bool ClassPrelinker::is_vm_class(InstanceKlass* ik) {
  return (_vm_classes->get(ik) != nullptr);
}

void ClassPrelinker::add_one_vm_class(InstanceKlass* ik) {
  bool created;
  _vm_classes->put_if_absent(ik, &created);
  if (created) {
    InstanceKlass* super = ik->java_super();
    if (super != nullptr) {
      add_one_vm_class(super);
    }
    Array<InstanceKlass*>* ifs = ik->local_interfaces();
    for (int i = 0; i < ifs->length(); i++) {
      add_one_vm_class(ifs->at(i));
    }
  }
}

void ClassPrelinker::initialize() {
  assert(_vm_classes == nullptr, "must be");
  _vm_classes = new (mtClass)ClassesTable();
  _processed_classes = new (mtClass)ClassesTable();
  for (auto id : EnumRange<vmClassID>{}) {
    add_one_vm_class(vmClasses::klass_at(id));
  }
}

void ClassPrelinker::dispose() {
  assert(_vm_classes != nullptr, "must be");
  delete _vm_classes;
  delete _processed_classes;
  _vm_classes = nullptr;
  _processed_classes = nullptr;
}

// Returns true if we CAN PROVE that cp_index will always resolve to
// the same information at both dump time and run time. This is a
// necessary (but not sufficient) condition for pre-resolving cp_index
// during CDS archive assembly.
bool ClassPrelinker::is_resolution_deterministic(ConstantPool* cp, int cp_index) {
  assert(!is_in_archivebuilder_buffer(cp), "sanity");

  if (cp->tag_at(cp_index).is_klass()) {
    // We require cp_index to be already resolved. This is fine for now, are we
    // currently archive only CP entries that are already resolved.
    Klass* resolved_klass = cp->resolved_klass_at(cp_index);
    return resolved_klass != nullptr && is_class_resolution_deterministic(cp->pool_holder(), resolved_klass);
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

bool ClassPrelinker::is_class_resolution_deterministic(InstanceKlass* cp_holder, Klass* resolved_class) {
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

    if (is_vm_class(ik)) {
      if (ik->class_loader() != cp_holder->class_loader()) {
        // At runtime, cp_holder() may not be able to resolve to the same
        // ik. For example, a different version of ik may be defined in
        // cp->pool_holder()'s loader using MethodHandles.Lookup.defineClass().
        return false;
      } else {
        return true;
      }
    }
  } else if (resolved_class->is_objArray_klass()) {
    Klass* elem = ObjArrayKlass::cast(resolved_class)->bottom_klass();
    if (elem->is_instance_klass()) {
      return is_class_resolution_deterministic(cp_holder, InstanceKlass::cast(elem));
    } else if (elem->is_typeArray_klass()) {
      return true;
    }
  } else if (resolved_class->is_typeArray_klass()) {
    return true;
  }

  return false;
}

void ClassPrelinker::dumptime_resolve_constants(InstanceKlass* ik, TRAPS) {
  if (!ik->is_linked()) {
    return;
  }
  bool first_time;
  _processed_classes->put_if_absent(ik, &first_time);
  if (!first_time) {
    // We have already resolved the constants in class, so no need to do it again.
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
Klass* ClassPrelinker::find_loaded_class(Thread* current, oop class_loader, Symbol* name) {
  HandleMark hm(current);
  Handle h_loader(current, class_loader);
  Klass* k = SystemDictionary::find_instance_or_array_klass(current, name,
                                                            h_loader,
                                                            Handle());
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

Klass* ClassPrelinker::find_loaded_class(Thread* current, ConstantPool* cp, int class_cp_index) {
  Symbol* name = cp->klass_name_at(class_cp_index);
  return find_loaded_class(current, cp->pool_holder()->class_loader(), name);
}

#if INCLUDE_CDS_JAVA_HEAP
void ClassPrelinker::resolve_string(constantPoolHandle cp, int cp_index, TRAPS) {
  if (CDSConfig::is_dumping_heap()) {
    int cache_index = cp->cp_to_object_index(cp_index);
    ConstantPool::string_at_impl(cp, cp_index, cache_index, CHECK);
  }
}
#endif

void ClassPrelinker::preresolve_class_cp_entries(JavaThread* current, InstanceKlass* ik, GrowableArray<bool>* preresolve_list) {
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
        log_trace(cds, resolve)("Resolved class  [%3d] %s -> %s", cp_index, ik->external_name(),
                                resolved_klass->external_name());
      }
    }
  }
}

void ClassPrelinker::preresolve_field_and_method_cp_entries(JavaThread* current, InstanceKlass* ik, GrowableArray<bool>* preresolve_list) {
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

void ClassPrelinker::maybe_resolve_fmi_ref(InstanceKlass* ik, Method* m, Bytecodes::Code bc, int raw_index,
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

  default:
    ShouldNotReachHere();
  }

  if (log_is_enabled(Trace, cds, resolve)) {
    ResourceMark rm(THREAD);
    bool resolved = cp->is_resolved(raw_index, bc);
    Symbol* name = cp->name_ref_at(raw_index, bc);
    Symbol* signature = cp->signature_ref_at(raw_index, bc);
    log_trace(cds, resolve)("%s %s [%3d] %s -> %s.%s:%s",
                            (resolved ? "Resolved" : "Failed to resolve"),
                            Bytecodes::name(bc), cp_index, ik->external_name(),
                            resolved_klass->external_name(),
                            name->as_C_string(), signature->as_C_string());
  }
}

#ifdef ASSERT
bool ClassPrelinker::is_in_archivebuilder_buffer(address p) {
  if (!Thread::current()->is_VM_thread() || ArchiveBuilder::current() == nullptr) {
    return false;
  } else {
    return ArchiveBuilder::current()->is_in_buffer_space(p);
  }
}
#endif
