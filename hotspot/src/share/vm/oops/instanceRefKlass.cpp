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

#include "precompiled.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/genCollectedHeap.hpp"
#include "gc/shared/specialized_oop_closures.hpp"
#include "oops/instanceRefKlass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/macros.hpp"
#include "utilities/preserveException.hpp"

void InstanceRefKlass::update_nonstatic_oop_maps(Klass* k) {
  // Clear the nonstatic oop-map entries corresponding to referent
  // and nextPending field.  They are treated specially by the
  // garbage collector.
  // The discovered field is used only by the garbage collector
  // and is also treated specially.
  InstanceKlass* ik = InstanceKlass::cast(k);

  // Check that we have the right class
  debug_only(static bool first_time = true);
  assert(k == SystemDictionary::Reference_klass() && first_time,
         "Invalid update of maps");
  debug_only(first_time = false);
  assert(ik->nonstatic_oop_map_count() == 1, "just checking");

  OopMapBlock* map = ik->start_of_nonstatic_oop_maps();

  // Check that the current map is (2,4) - currently points at field with
  // offset 2 (words) and has 4 map entries.
  debug_only(int offset = java_lang_ref_Reference::referent_offset);
  debug_only(unsigned int count = ((java_lang_ref_Reference::discovered_offset -
    java_lang_ref_Reference::referent_offset)/heapOopSize) + 1);

  if (UseSharedSpaces) {
    assert(map->offset() == java_lang_ref_Reference::queue_offset &&
           map->count() == 1, "just checking");
  } else {
    assert(map->offset() == offset && map->count() == count,
           "just checking");

    // Update map to (3,1) - point to offset of 3 (words) with 1 map entry.
    map->set_offset(java_lang_ref_Reference::queue_offset);
    map->set_count(1);
  }
}


// Verification

void InstanceRefKlass::oop_verify_on(oop obj, outputStream* st) {
  InstanceKlass::oop_verify_on(obj, st);
  // Verify referent field
  oop referent = java_lang_ref_Reference::referent(obj);
  if (referent != NULL) {
    guarantee(referent->is_oop(), "referent field heap failed");
  }
  // Verify next field
  oop next = java_lang_ref_Reference::next(obj);
  if (next != NULL) {
    guarantee(next->is_oop(), "next field verify failed");
    guarantee(next->is_instanceRef(), "next field verify failed");
  }
}

bool InstanceRefKlass::owns_pending_list_lock(JavaThread* thread) {
  if (java_lang_ref_Reference::pending_list_lock() == NULL) return false;
  Handle h_lock(thread, java_lang_ref_Reference::pending_list_lock());
  return ObjectSynchronizer::current_thread_holds_lock(thread, h_lock);
}

void InstanceRefKlass::acquire_pending_list_lock(BasicLock *pending_list_basic_lock) {
  // we may enter this with pending exception set
  PRESERVE_EXCEPTION_MARK;  // exceptions are never thrown, needed for TRAPS argument

  // Create a HandleMark in case we retry a GC multiple times.
  // Each time we attempt the GC, we allocate the handle below
  // to hold the pending list lock. We want to free this handle.
  HandleMark hm;

  Handle h_lock(THREAD, java_lang_ref_Reference::pending_list_lock());
  ObjectSynchronizer::fast_enter(h_lock, pending_list_basic_lock, false, THREAD);
  assert(ObjectSynchronizer::current_thread_holds_lock(
           JavaThread::current(), h_lock),
         "Locking should have succeeded");
  if (HAS_PENDING_EXCEPTION) CLEAR_PENDING_EXCEPTION;
}

void InstanceRefKlass::release_and_notify_pending_list_lock(
  BasicLock *pending_list_basic_lock) {
  // we may enter this with pending exception set
  PRESERVE_EXCEPTION_MARK;  // exceptions are never thrown, needed for TRAPS argument

  // Create a HandleMark in case we retry a GC multiple times.
  // Each time we attempt the GC, we allocate the handle below
  // to hold the pending list lock. We want to free this handle.
  HandleMark hm;

  Handle h_lock(THREAD, java_lang_ref_Reference::pending_list_lock());
  assert(ObjectSynchronizer::current_thread_holds_lock(
           JavaThread::current(), h_lock),
         "Lock should be held");
  // Notify waiters on pending lists lock if there is any reference.
  if (java_lang_ref_Reference::pending_list() != NULL) {
    ObjectSynchronizer::notifyall(h_lock, THREAD);
  }
  ObjectSynchronizer::fast_exit(h_lock(), pending_list_basic_lock, THREAD);
  if (HAS_PENDING_EXCEPTION) CLEAR_PENDING_EXCEPTION;
}
