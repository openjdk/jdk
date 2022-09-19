/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/classPrelinker.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmClasses.hpp"
#include "memory/resourceArea.hpp"
#include "oops/constantPool.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "runtime/handles.inline.hpp"

ClassPrelinker* ClassPrelinker::_singleton = NULL;

bool ClassPrelinker::is_vm_class(InstanceKlass* ik) {
  return (_vm_classes.get(ik) != NULL);
}

void ClassPrelinker::add_one_vm_class(InstanceKlass* ik) {
  bool created;
  _vm_classes.put_if_absent(ik, &created);
  if (created) {
    InstanceKlass* super = ik->java_super();
    if (super != NULL) {
      add_one_vm_class(super);
    }
    Array<InstanceKlass*>* ifs = ik->local_interfaces();
    for (int i = 0; i < ifs->length(); i++) {
      add_one_vm_class(ifs->at(i));
    }
  }
}

ClassPrelinker::ClassPrelinker() {
  assert(_singleton == NULL, "must be");
  _singleton = this;
  for (auto id : EnumRange<vmClassID>{}) {
    add_one_vm_class(vmClasses::klass_at(id));
  }
}

ClassPrelinker::~ClassPrelinker() {
  assert(_singleton == this, "must be");
  _singleton = NULL;
}

bool ClassPrelinker::can_archive_resolved_vm_class(InstanceKlass* cp_holder, InstanceKlass* resolved_klass) {
  if (!is_vm_class(resolved_klass)) {
    return false;
  }
  if (!cp_holder->is_shared_boot_class() &&
      !cp_holder->is_shared_platform_class() &&
      !cp_holder->is_shared_app_class()) {
    // Custom loaders are not guaranteed to resolve the vmClasses to the
    // ones resolved by the boot loader.
    return false;
  }
  if (cp_holder->class_loader_data() != resolved_klass->class_loader_data()) {
    // If they are defined by different loaders, it's possible for resolved_klass
    // to be already defined, but is not yet resolved in cp_holder->class_loader().

    // TODO: this check can be removed if we preload the vmClasses into
    // platform and app loaders during VM bootstrap.
    return false;
  }

  return true;
}

bool ClassPrelinker::can_archive_resolved_klass(InstanceKlass* cp_holder, Klass* resolved_klass) {
  assert(!is_in_archivebuilder_buffer(cp_holder), "sanity");
  assert(!is_in_archivebuilder_buffer(resolved_klass), "sanity");

  if (resolved_klass->is_instance_klass()) {
    InstanceKlass* ik = InstanceKlass::cast(resolved_klass);
    if (can_archive_resolved_vm_class(cp_holder, ik)) {
      return true;
    }
    if (cp_holder->is_subtype_of(ik)) {
      // All super types of ik will be resolved in ik->class_loader() before
      // ik is defined in this loader, so it's safe to archive the resolved klass reference.
      return true;
    }

    // TODO -- allow objArray classes, too
  }

  return false;
}

Klass* ClassPrelinker::get_resolved_klass_or_null(ConstantPool* cp, int cp_index) {
  if (cp->tag_at(cp_index).is_klass()) {
    CPKlassSlot kslot = cp->klass_slot_at(cp_index);
    int resolved_klass_index = kslot.resolved_klass_index();
    return cp->resolved_klasses()->at(resolved_klass_index);
  } else {
    // klass is not resolved yet
    assert(cp->tag_at(cp_index).is_unresolved_klass() ||
           cp->tag_at(cp_index).is_unresolved_klass_in_error(), "must be");
    return NULL;
  }
}

bool ClassPrelinker::can_archive_resolved_klass(ConstantPool* cp, int cp_index) {
  assert(!is_in_archivebuilder_buffer(cp), "sanity");
  assert(cp->tag_at(cp_index).is_klass(), "must be resolved");

  Klass* resolved_klass = get_resolved_klass_or_null(cp, cp_index);
  assert(resolved_klass != NULL, "must be");

  return can_archive_resolved_klass(cp->pool_holder(), resolved_klass);
}

void ClassPrelinker::dumptime_resolve_constants(InstanceKlass* ik, TRAPS) {
  constantPoolHandle cp(THREAD, ik->constants());
  if (cp->cache() == NULL || cp->reference_map() == NULL) {
    // The cache may be NULL if the pool_holder klass fails verification
    // at dump time due to missing dependencies.
    return;
  }

  bool first_time;
  _processed_classes.put_if_absent(ik, &first_time);
  if (!first_time) {
    return;
  }

  for (int cp_index = 1; cp_index < cp->length(); cp_index++) { // Index 0 is unused
    switch (cp->tag_at(cp_index).value()) {
    case JVM_CONSTANT_UnresolvedClass:
      maybe_resolve_class(cp, cp_index, CHECK);
      break;

    case JVM_CONSTANT_String:
      resolve_string(cp, cp_index, CHECK); // may throw OOM when interning strings.
      break;
    }
  }
}

Klass* ClassPrelinker::find_loaded_class(JavaThread* THREAD, oop class_loader, Symbol* name) {
  Handle h_loader(THREAD, class_loader);
  Klass* k = SystemDictionary::find_instance_or_array_klass(THREAD, name,
                                                            h_loader,
                                                            Handle());
  if (k != NULL) {
    return k;
  }
  if (class_loader == SystemDictionary::java_system_loader()) {
    return find_loaded_class(THREAD, SystemDictionary::java_platform_loader(), name);
  } else if (class_loader == SystemDictionary::java_platform_loader()) {
    return find_loaded_class(THREAD, NULL, name);
  }

  return NULL;
}

Klass* ClassPrelinker::maybe_resolve_class(constantPoolHandle cp, int cp_index, TRAPS) {
  assert(!is_in_archivebuilder_buffer(cp()), "sanity");
  InstanceKlass* cp_holder = cp->pool_holder();
  if (!cp_holder->is_shared_boot_class() &&
      !cp_holder->is_shared_platform_class() &&
      !cp_holder->is_shared_app_class()) {
    // Don't trust custom loaders, as they may not be well-behaved
    // when resolving classes.
    return NULL;
  }

  CPKlassSlot kslot = cp->klass_slot_at(cp_index);
  int name_index = kslot.name_index();
  Symbol* name = cp->symbol_at(name_index);
  Klass* resolved_klass = find_loaded_class(THREAD, cp_holder->class_loader(), name);
  if (resolved_klass != NULL && can_archive_resolved_klass(cp_holder, resolved_klass)) {
    Klass* k = ConstantPool::klass_at_impl(cp, cp_index, CHECK_NULL); // Should fail only with OOM
    assert(k == resolved_klass, "must be");
  }

  return resolved_klass;
}

#if INCLUDE_CDS_JAVA_HEAP
void ClassPrelinker::resolve_string(constantPoolHandle cp, int cp_index, TRAPS) {
  if (!DumpSharedSpaces) {
    // The archive heap is not supported for the dynamic archive.
    return;
  }

  int cache_index = cp->cp_to_object_index(cp_index);
  ConstantPool::string_at_impl(cp, cp_index, cache_index, CHECK);
}
#endif

#ifdef ASSERT
bool ClassPrelinker::is_in_archivebuilder_buffer(address p) {
  if (!Thread::current()->is_VM_thread() || ArchiveBuilder::current() == NULL) {
    return false;
  } else {
    return ArchiveBuilder::current()->is_in_buffer_space(p);
  }
}
#endif
