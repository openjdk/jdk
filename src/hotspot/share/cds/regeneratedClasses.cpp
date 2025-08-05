/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/archiveBuilder.hpp"
#include "cds/regeneratedClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "memory/universe.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/method.hpp"
#include "oops/oopHandle.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/thread.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/resourceHash.hpp"

using RegeneratedObjTable = ResourceHashtable<address, address, 15889, AnyObj::C_HEAP, mtClassShared>;
static RegeneratedObjTable* _regenerated_objs = nullptr; // InstanceKlass* and Method*  orig_obj  -> regen_obj
static RegeneratedObjTable* _original_objs = nullptr;    // InstanceKlass* and Method*  regen_obj -> orig_obj
static GrowableArrayCHeap<OopHandle, mtClassShared>* _regenerated_mirrors = nullptr;

// The regenerated Klass is not added to any class loader, so we need
// to keep its java_mirror alive to avoid class unloading.
void RegeneratedClasses::add_class(InstanceKlass* orig_klass, InstanceKlass* regen_klass) {
  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);
  if (_regenerated_mirrors == nullptr) {
    _regenerated_mirrors = new GrowableArrayCHeap<OopHandle, mtClassShared>(150);
  }
  _regenerated_mirrors->append(OopHandle(Universe::vm_global(), regen_klass->java_mirror()));

  if (_regenerated_objs == nullptr) {
    _regenerated_objs = new (mtClass)RegeneratedObjTable();
  }
  if (_original_objs == nullptr) {
    _original_objs = new (mtClass)RegeneratedObjTable();
  }

  _regenerated_objs->put((address)orig_klass, (address)regen_klass);
  _original_objs->put((address)regen_klass, (address)orig_klass);
  Array<Method*>* methods = orig_klass->methods();
  for (int i = 0; i < methods->length(); i++) {
    Method* orig_m = methods->at(i);
    Method* regen_m = regen_klass->find_method(orig_m->name(), orig_m->signature());
    if (regen_m == nullptr) {
      ResourceMark rm;
      if (orig_m->name() != vmSymbols::object_initializer_name()) {
        // JLI Holder classes are never instantiated, they don't need to have constructors.
        // Not printing the warning if the method is a constructor.
        log_warning(aot)("Method in original class is missing from regenerated class: " INTPTR_FORMAT " %s",
                         p2i(orig_m), orig_m->external_name());
      }
    } else {
      _regenerated_objs->put((address)orig_m, (address)regen_m);
      _original_objs->put((address)regen_m, (address)orig_m);
    }
  }
}

bool RegeneratedClasses::has_been_regenerated(address orig_obj) {
  if (_regenerated_objs == nullptr) {
    return false;
  } else {
    return _regenerated_objs->get(orig_obj) != nullptr;
  }
}

address RegeneratedClasses::get_regenerated_object(address orig_obj) {
  assert(_regenerated_objs != nullptr, "must be");
  address* p =_regenerated_objs->get(orig_obj);
  assert(p != nullptr, "must be");
  return *p;
}

bool RegeneratedClasses::is_regenerated_object(address regen_obj) {
  if (_original_objs == nullptr) {
    return false;
  } else {
    return _original_objs->get(regen_obj) != nullptr;
  }
}

address RegeneratedClasses::get_original_object(address regen_obj) {
  assert(_original_objs != nullptr, "must be");
  address* p =_original_objs->get(regen_obj);
  assert(p != nullptr, "must be");
  return *p;
}

void RegeneratedClasses::record_regenerated_objects() {
  assert_locked_or_safepoint(DumpTimeTable_lock);
  if (_regenerated_objs != nullptr) {
    auto doit = [&] (address orig_obj, address regen_obj) {
      ArchiveBuilder::current()->record_regenerated_object(orig_obj, regen_obj);
    };
    _regenerated_objs->iterate_all(doit);
  }
}

void RegeneratedClasses::cleanup() {
  MutexLocker ml(DumpTimeTable_lock, Mutex::_no_safepoint_check_flag);
  if (_regenerated_mirrors != nullptr) {
    for (int i = 0; i < _regenerated_mirrors->length(); i++) {
      _regenerated_mirrors->at(i).release(Universe::vm_global());
    }
    delete _regenerated_mirrors;
    _regenerated_mirrors = nullptr;
  }
  if (_regenerated_objs != nullptr) {
    delete _regenerated_objs;
  }
}
