/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotReferenceObjSupport.hpp"
#include "cds/heapShared.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "runtime/fieldDescriptor.inline.hpp"

// Handling of java.lang.ref.Reference objects in the AOT cache
// ============================================================
//
// When AOTArtifactFinder finds an oop which is a instance of java.lang.ref.Reference:
//
// - We check if the oop is eligible to be stored in the AOT cache. If not, the AOT cache
//   creation fails -- see AOTReferenceObjSupport::check_if_ref_obj()
//
// - Otherwise, we store the oop into the AOT cache, but we unconditionally reset its
//   "next" and "discovered" fields to null. Otherwise, if AOTArtifactFinder follows these
//   fields, it may found unrelated objects that we don't intent to cache.
//
// Eligibility
// ===========
//
// [1] A reference that does not require special clean up (i.e., Reference::queue == ReferenceQueue::NULL)
//     is eligible.
//
// [2] A reference that REQUIRE specials clean up (i.e., Reference::queue != ReferenceQueue::NULL)
//     is eligible ONLY if it has not been put into the "pending" state by the GC (See Reference.java).
//
// AOTReferenceObjSupport::check_if_ref_obj() detects the "pending" state by checking the "next" and
// "discovered" fields of the oop.
//
// As of this version, the only oops in group [2] that can be found by AOTArtifactFinder are
// the keys used by ReferencedKeyMap in the implementation of MethodType::internTable.
// ReferencedKeyMap::prepareForAOTCache ensures that all keys found by AOTArtifactFinder are eligible.
//
// The purpose of the error check in check_if_ref_obj() is to guard against changes in the JDK core
// libs that might introduce new types of oops in group [2] into the AOT cache.
//
//
// Reasons for the eligibility restrictions
// ========================================
//
// Reference handling is complex. In this version, we implement only enough functionality to support
// the use of Weak/Soft references used by java.lang.invoke.
//
// We intent to evolve the implementation in the future by
// -- implementing more prepareForAOTCache() operations for other use cases, and/or
// -- relaxing the eligibility restrictions.

static OopHandle _null_queue;

void AOTReferenceObjSupport::initialize(TRAPS) {
  TempNewSymbol class_name = SymbolTable::new_symbol("java/lang/ref/ReferenceQueue");
  Klass* k = SystemDictionary::resolve_or_fail(class_name, true, CHECK);
  InstanceKlass* ik = InstanceKlass::cast(k);
  ik->initialize(CHECK);

  TempNewSymbol field_name = SymbolTable::new_symbol("NULL");
  fieldDescriptor fd;
  bool found = ik->find_local_field(field_name, vmSymbols::referencequeue_signature(), &fd);
  precond(found);
  precond(fd.is_static());

  _null_queue = OopHandle(Universe::vm_global(), ik->java_mirror()->obj_field(fd.offset()));
}

bool AOTReferenceObjSupport::check_if_ref_obj(oop obj) {
  // We have a single Java thread. This means java.lang.ref.Reference$ReferenceHandler thread
  // is not running. Otherwise the checks for next/discovered may not work.
  precond(CDSConfig::allow_only_single_java_thread());

  if (obj->klass()->is_subclass_of(vmClasses::Reference_klass())) {
    oop referent = obj->obj_field(java_lang_ref_Reference::referent_offset());
    oop queue = obj->obj_field(java_lang_ref_Reference::queue_offset());
    oop next = java_lang_ref_Reference::next(obj);
    oop discovered = java_lang_ref_Reference::discovered(obj);

    if (next != nullptr || discovered != nullptr) {
      if (queue != _null_queue.resolve()) {
        ResourceMark rm;
        log_error(cds, heap)("Cannot archive reference object " PTR_FORMAT " of class %s",
                             p2i(obj), obj->klass()->external_name());
        log_error(cds, heap)("referent = " PTR_FORMAT
                             ", queue = " PTR_FORMAT
                             ", next = " PTR_FORMAT
                             ", discovered = " PTR_FORMAT,
                             p2i(referent), p2i(queue), p2i(next), p2i(discovered));
        log_error(cds, heap)("This object requires special clean up as its queue is not ReferenceQueue::NULL ("
                             PTR_FORMAT ")", p2i(_null_queue.resolve()));
        HeapShared::debug_trace();
        MetaspaceShared::unrecoverable_writing_error();
      }
    }

    if (log_is_enabled(Info, cds, ref)) {
      ResourceMark rm;
      log_info(cds, ref)("Reference obj:"
                         " r=" PTR_FORMAT
                         " q=" PTR_FORMAT
                         " n=" PTR_FORMAT
                         " d=" PTR_FORMAT
                         " %s",
                         p2i(referent),
                         p2i(queue),
                         p2i(next),
                         p2i(discovered),
                         obj->klass()->external_name());
    }
    return true;
  } else {
    return false;
  }
}

bool AOTReferenceObjSupport::skip_field(int field_offset) {
  return (field_offset == java_lang_ref_Reference::next_offset() ||
          field_offset == java_lang_ref_Reference::discovered_offset());
}
