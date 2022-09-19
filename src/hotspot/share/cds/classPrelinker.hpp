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

#ifndef SHARE_CDS_CLASSPRELINKER_HPP
#define SHARE_CDS_CLASSPRELINKER_HPP

#include "oops/oopsHierarchy.hpp"
#include "memory/allocation.hpp"
#include "runtime/handles.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/macros.hpp"
#include "utilities/resourceHash.hpp"

class ConstantPool;
class constantPoolHandle;
class InstanceKlass;
class Klass;

// ClassPrelinker is used to perform ahead-of-time linking of ConstantPool entries
// for archived InstanceKlasses.
//
// At run time, Java classes are loaded dynamically and may be replaced with JVMTI.
// Therefore, we take care to prelink only the ConstantPool entries that are
// guatanteed to resolve to the same results at both dump time and run time.
//
// For example, a JVM_CONSTANT_Class reference to a supertype can be safely resolved
// at dump time, because at run time we will load a class from the CDS archive only
// if all of its supertypes are loaded from the CDS archive.
class ClassPrelinker :  public StackObj {
  typedef ResourceHashtable<InstanceKlass*, bool, 15889, ResourceObj::C_HEAP, mtClassShared> ClassesTable;
  ClassesTable _processed_classes;
  ClassesTable _vm_classes;

  void add_one_vm_class(InstanceKlass* ik);
  bool can_archive_resolved_vm_class(InstanceKlass* cp_holder, InstanceKlass* resolved_klass);

#ifdef ASSERT
  static bool is_in_archivebuilder_buffer(address p);
#endif

  template <typename T>
  static bool is_in_archivebuilder_buffer(T p) {
    return is_in_archivebuilder_buffer((address)(p));
  }
  void resolve_string(constantPoolHandle cp, int cp_index, TRAPS) NOT_CDS_JAVA_HEAP_RETURN;
  Klass* maybe_resolve_class(constantPoolHandle cp, int cp_index, TRAPS);
  bool can_archive_resolved_klass(InstanceKlass* cp_holder, Klass* resolved_klass);
  Klass* find_loaded_class(JavaThread* THREAD, oop class_loader, Symbol* name);
  Klass* get_resolved_klass_or_null(ConstantPool* cp, int cp_index);

  static ClassPrelinker* _singleton;
public:
  ClassPrelinker();
  ~ClassPrelinker();
  static ClassPrelinker* current() {
    assert(_singleton != NULL, "must have one");
    return _singleton;
  }

  // Is this class resolved as part of vmClasses::resolve_all()? If so, these
  // classes are guatanteed to be loaded at runtime (and cannot be replaced by JVMTI)
  // when CDS is enabled. Therefore, we can safely keep a direct reference to these
  // classes.
  bool is_vm_class(InstanceKlass* ik);

  // Resolve all constant pool entries that are safe to be stored in the
  // CDS archive.
  void dumptime_resolve_constants(InstanceKlass* ik, TRAPS);

  // Can we resolve the klass entry at cp_index in this constant pool, and store
  // the result in the CDS archive? Returns true if cp_index is guaranteed to
  // resolve to the same InstanceKlass* at both dump time and run time.
  bool can_archive_resolved_klass(ConstantPool* cp, int cp_index);
};

#endif // SHARE_CDS_CLASSPRELINKER_HPP
