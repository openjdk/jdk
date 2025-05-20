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
#include "runtime/javaCalls.hpp"
#include "utilities/resourceHash.hpp"

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
//   fields, it may found unrelated objects that we don't intend to cache.
//
// Eligibility
// ===========
//
// [1] A reference that does not require special clean up (i.e., Reference::queue == ReferenceQueue.NULL_QUEUE)
//     is eligible.
//
// [2] A reference that REQUIRE specials clean up (i.e., Reference::queue != ReferenceQueue.NULL_QUEUE)
//     is eligible ONLY if its referent is not null.
//
// As of this version, the only oops in group [2] that can be found by AOTArtifactFinder are
// the keys used by ReferencedKeyMap in the implementation of MethodType::internTable.
// stabilize_cached_reference_objects() ensures that all keys found by AOTArtifactFinder are eligible.
//
// The purpose of the error check in check_if_ref_obj() is to guard against changes in the JDK core
// libs that might introduce new types of oops in group [2] into the AOT cache.
//
// Reasons for the eligibility restrictions
// ========================================
//
// Reference handling is complex. In this version, we implement only enough functionality to support
// the use of Weak/Soft references used by java.lang.invoke.
//
// We intend to evolve the implementation in the future by
// -- implementing more assemblySetup() operations for other use cases, and/or
// -- relaxing the eligibility restrictions.
//
//
// null referents for group [1]
// ============================
//
// Any cached reference R1 of group [1] is allowed to have a null referent.
// This can happen in the following situations:
//    (a) R1.clear() was called by Java code during the assembly phase.
//    (b) The referent has been collected, and R1 is in the "pending" state.
// In case (b), the "next" and "discovered" fields of the cached copy of R1 will
// be set to null. During the production run:
//    - It would appear to the Java program as if immediately during VM start-up, the referent
//      was collected and ReferenceThread completed processing of R1.
//    - It would appear to the GC as if immediately during VM start-up, the Java program called
//      R1.clear().

#if INCLUDE_CDS_JAVA_HEAP

class KeepAliveObjectsTable : public ResourceHashtable<oop, bool,
    36137, // prime number
    AnyObj::C_HEAP,
    mtClassShared,
    HeapShared::oop_hash> {};

static KeepAliveObjectsTable* _keep_alive_objs_table;
static OopHandle _keep_alive_objs_array;
static OopHandle _null_queue;

bool AOTReferenceObjSupport::is_enabled() {
  // For simplicity, AOTReferenceObjSupport is enabled only when dumping method handles.
  // Otherwise we won't see Reference objects in the AOT cache. Let's be conservative now.
  return CDSConfig::is_dumping_method_handles();
}

void AOTReferenceObjSupport::initialize(TRAPS) {
  if (!AOTReferenceObjSupport::is_enabled()) {
    return;
  }

  TempNewSymbol class_name = SymbolTable::new_symbol("java/lang/ref/ReferenceQueue");
  Klass* k = SystemDictionary::resolve_or_fail(class_name, true, CHECK);
  InstanceKlass* ik = InstanceKlass::cast(k);
  ik->initialize(CHECK);

  TempNewSymbol field_name = SymbolTable::new_symbol("NULL_QUEUE");
  fieldDescriptor fd;
  bool found = ik->find_local_field(field_name, vmSymbols::referencequeue_signature(), &fd);
  precond(found);
  precond(fd.is_static());

  _null_queue = OopHandle(Universe::vm_global(), ik->java_mirror()->obj_field(fd.offset()));
}

// Ensure that all group [2] references found by AOTArtifactFinder are eligible.
void AOTReferenceObjSupport::stabilize_cached_reference_objects(TRAPS) {
  if (AOTReferenceObjSupport::is_enabled()) {
    // This assert means that the MethodType and MethodTypeForm tables won't be
    // updated concurrently, so we can remove GC'ed entries ...
    assert(CDSConfig::allow_only_single_java_thread(), "Required");

    {
      TempNewSymbol method_name = SymbolTable::new_symbol("assemblySetup");
      JavaValue result(T_VOID);
      JavaCalls::call_static(&result, vmClasses::MethodType_klass(),
                           method_name,
                           vmSymbols::void_method_signature(),
                           CHECK);
    }

    {
      Symbol* cds_name  = vmSymbols::jdk_internal_misc_CDS();
      Klass* cds_klass = SystemDictionary::resolve_or_fail(cds_name, true /*throw error*/,  CHECK);
      TempNewSymbol method_name = SymbolTable::new_symbol("getKeepAliveObjects");
      TempNewSymbol method_sig = SymbolTable::new_symbol("()[Ljava/lang/Object;");
      JavaValue result(T_OBJECT);
      JavaCalls::call_static(&result, cds_klass, method_name, method_sig, CHECK);

      _keep_alive_objs_array = OopHandle(Universe::vm_global(), result.get_oop());
    }
  }
}

void AOTReferenceObjSupport::init_keep_alive_objs_table() {
  assert_at_safepoint(); // _keep_alive_objs_table uses raw oops
  oop a = _keep_alive_objs_array.resolve();
  if (a != nullptr) {
    precond(a->is_objArray());
    precond(AOTReferenceObjSupport::is_enabled());
    objArrayOop array = objArrayOop(a);

    _keep_alive_objs_table = new (mtClass)KeepAliveObjectsTable();
    for (int i = 0; i < array->length(); i++) {
      oop obj = array->obj_at(i);
      _keep_alive_objs_table->put(obj, true); // The array may have duplicated entries but that's OK.
    }
  }
}

// Returns true IFF obj is an instance of java.lang.ref.Reference. If so, perform extra eligibility checks.
bool AOTReferenceObjSupport::check_if_ref_obj(oop obj) {
  // We have a single Java thread. This means java.lang.ref.Reference$ReferenceHandler thread
  // is not running. Otherwise the checks for next/discovered may not work.
  precond(CDSConfig::allow_only_single_java_thread());
  assert_at_safepoint(); // _keep_alive_objs_table uses raw oops

  if (obj->klass()->is_subclass_of(vmClasses::Reference_klass())) {
    precond(AOTReferenceObjSupport::is_enabled());
    precond(JavaClasses::is_supported_for_archiving(obj));
    precond(_keep_alive_objs_table != nullptr);

    // GC needs to know about this load, It will keep referent alive until the current safepoint ends.
    oop referent = HeapAccess<ON_UNKNOWN_OOP_REF>::oop_load_at(obj, java_lang_ref_Reference::referent_offset());

    oop queue = obj->obj_field(java_lang_ref_Reference::queue_offset());
    oop next = java_lang_ref_Reference::next(obj);
    oop discovered = java_lang_ref_Reference::discovered(obj);
    bool needs_special_cleanup = (queue != _null_queue.resolve());

    // If you see the errors below, you probably modified the implementation of java.lang.invoke.
    // Please check the comments at the top of this file.
    if (needs_special_cleanup && (referent == nullptr || !_keep_alive_objs_table->contains(referent))) {
      ResourceMark rm;

      log_error(aot, heap)("Cannot archive reference object " PTR_FORMAT " of class %s",
                           p2i(obj), obj->klass()->external_name());
      log_error(aot, heap)("referent = " PTR_FORMAT
                           ", queue = " PTR_FORMAT
                           ", next = " PTR_FORMAT
                           ", discovered = " PTR_FORMAT,
                           p2i(referent), p2i(queue), p2i(next), p2i(discovered));
      log_error(aot, heap)("This object requires special clean up as its queue is not ReferenceQueue::N" "ULL ("
                           PTR_FORMAT ")", p2i(_null_queue.resolve()));
      log_error(aot, heap)("%s", (referent == nullptr) ?
                           "referent cannot be null" : "referent is not registered with CDS.keepAlive()");
      HeapShared::debug_trace();
      MetaspaceShared::unrecoverable_writing_error();
    }

    if (log_is_enabled(Info, aot, ref)) {
      ResourceMark rm;
      log_info(aot, ref)("Reference obj:"
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

#endif // INCLUDE_CDS_JAVA_HEAP
