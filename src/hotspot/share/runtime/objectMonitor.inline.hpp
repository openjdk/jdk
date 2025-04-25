/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_OBJECTMONITOR_INLINE_HPP
#define SHARE_RUNTIME_OBJECTMONITOR_INLINE_HPP

#include "runtime/objectMonitor.hpp"

#include "classfile/vmSymbols.hpp"
#include "logging/log.hpp"
#include "oops/access.inline.hpp"
#include "oops/markWord.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/lockStack.inline.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/threadIdentifier.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/globalDefinitions.hpp"

inline int64_t ObjectMonitor::owner_id_from(JavaThread* thread) {
  return thread->monitor_owner_id();
}

inline int64_t ObjectMonitor::owner_id_from(oop vthread) {
  int64_t id = java_lang_Thread::thread_id(vthread);
  ThreadIdentifier::verify_id(id);
  return id;
}

inline bool ObjectMonitor::is_entered(JavaThread* current) const {
  if (has_anonymous_owner()) {
    if (LockingMode == LM_LIGHTWEIGHT) {
      return current->lock_stack().contains(object());
    } else {
      return current->is_lock_owned((address)stack_locker());
    }
  } else {
    return has_owner(current);
  }
  return false;
}

inline uintptr_t ObjectMonitor::metadata() const {
  return Atomic::load(&_metadata);
}

inline void ObjectMonitor::set_metadata(uintptr_t value) {
  Atomic::store(&_metadata, value);
}

inline volatile uintptr_t* ObjectMonitor::metadata_addr() {
  STATIC_ASSERT(std::is_standard_layout<ObjectMonitor>::value);
  STATIC_ASSERT(offsetof(ObjectMonitor, _metadata) == 0);
  return &_metadata;
}

inline markWord ObjectMonitor::header() const {
  assert(!UseObjectMonitorTable, "Lightweight locking with OM table does not use header");
  return markWord(metadata());
}

inline void ObjectMonitor::set_header(markWord hdr) {
  assert(!UseObjectMonitorTable, "Lightweight locking with OM table does not use header");
  set_metadata(hdr.value());
}

inline intptr_t ObjectMonitor::hash() const {
  assert(UseObjectMonitorTable, "Only used by lightweight locking with OM table");
  return metadata();
}

inline void ObjectMonitor::set_hash(intptr_t hash) {
  assert(UseObjectMonitorTable, "Only used by lightweight locking with OM table");
  set_metadata(hash);
}

inline int ObjectMonitor::waiters() const {
  return _waiters;
}

inline bool ObjectMonitor::has_owner() const {
  int64_t owner = owner_raw();
  return owner != NO_OWNER && owner != DEFLATER_MARKER;
}

// Returns NO_OWNER if DEFLATER_MARKER is observed.
inline int64_t ObjectMonitor::owner() const {
  int64_t owner = owner_raw();
  return owner != DEFLATER_MARKER ? owner : NO_OWNER;
}

inline int64_t ObjectMonitor::owner_raw() const {
  return Atomic::load(&_owner);
}

inline BasicLock* ObjectMonitor::stack_locker() const {
  return Atomic::load(&_stack_locker);
}

inline void ObjectMonitor::set_stack_locker(BasicLock* locker) {
  Atomic::store(&_stack_locker, locker);
}

// Returns true if owner field == DEFLATER_MARKER and false otherwise.
inline bool ObjectMonitor::owner_is_DEFLATER_MARKER() const {
  return owner_raw() == DEFLATER_MARKER;
}

// Returns true if 'this' is being async deflated and false otherwise.
inline bool ObjectMonitor::is_being_async_deflated() {
  return contentions() < 0;
}

// Return number of threads contending for this monitor.
inline int ObjectMonitor::contentions() const {
  return Atomic::load(&_contentions);
}

// Add value to the contentions field.
inline void ObjectMonitor::add_to_contentions(int value) {
  Atomic::add(&_contentions, value);
}

inline void ObjectMonitor::set_recursions(size_t recursions) {
  assert(_recursions == 0, "must be");
  assert(has_owner(), "must be owned");
  _recursions = checked_cast<intx>(recursions);
}

inline void ObjectMonitor::increment_recursions(JavaThread* current) {
  assert(has_owner(current), "must be the owner");
  _recursions++;
}

// Clear _owner field; current value must match old_value.
inline void ObjectMonitor::release_clear_owner(JavaThread* old_owner) {
  int64_t old_value = owner_id_from(old_owner);
#ifdef ASSERT
  int64_t prev = Atomic::load(&_owner);
  assert(prev == old_value, "unexpected prev owner=" INT64_FORMAT
         ", expected=" INT64_FORMAT, prev, old_value);
#endif
  Atomic::release_store(&_owner, NO_OWNER);
  log_trace(monitorinflation, owner)("release_clear_owner(): mid="
                                     INTPTR_FORMAT ", old_value=" INT64_FORMAT,
                                     p2i(this), old_value);
}

// Simply set _owner field to new_value; current value must match old_value.
// (Simple means no memory sync needed.)
inline void ObjectMonitor::set_owner_from_raw(int64_t old_value, int64_t new_value) {
#ifdef ASSERT
  int64_t prev = Atomic::load(&_owner);
  assert((int64_t)prev < ThreadIdentifier::current(), "must be reasonable");
  assert(prev == old_value, "unexpected prev owner=" INT64_FORMAT
         ", expected=" INT64_FORMAT, prev, old_value);
#endif
  Atomic::store(&_owner, new_value);
  log_trace(monitorinflation, owner)("set_owner_from(): mid="
                                     INTPTR_FORMAT ", old_value=" INT64_FORMAT
                                     ", new_value=" INT64_FORMAT, p2i(this),
                                     old_value, new_value);
}

inline void ObjectMonitor::set_owner_from(int64_t old_value, JavaThread* current) {
  set_owner_from_raw(old_value, owner_id_from(current));
}

// Try to set _owner field to new_value if the current value matches
// old_value. Otherwise, does not change the _owner field. Returns
// the prior value of the _owner field.
inline int64_t ObjectMonitor::try_set_owner_from_raw(int64_t old_value, int64_t new_value) {
  assert((int64_t)new_value < ThreadIdentifier::current(), "must be reasonable");
  int64_t prev = Atomic::cmpxchg(&_owner, old_value, new_value);
  if (prev == old_value) {
    log_trace(monitorinflation, owner)("try_set_owner_from(): mid="
                                       INTPTR_FORMAT ", prev=" INT64_FORMAT
                                       ", new=" INT64_FORMAT, p2i(this),
                                       prev, new_value);
  }
  return prev;
}

inline int64_t ObjectMonitor::try_set_owner_from(int64_t old_value, JavaThread* current) {
  return try_set_owner_from_raw(old_value, owner_id_from(current));
}

inline bool ObjectMonitor::has_successor() const {
  return Atomic::load(&_succ) != NO_OWNER;
}

inline bool ObjectMonitor::has_successor(JavaThread* thread) const {
  return owner_id_from(thread) == Atomic::load(&_succ);
}

inline void ObjectMonitor::set_successor(JavaThread* thread) {
  Atomic::store(&_succ, owner_id_from(thread));
}

inline void ObjectMonitor::set_successor(oop vthread) {
  Atomic::store(&_succ, java_lang_Thread::thread_id(vthread));
}

inline void ObjectMonitor::clear_successor() {
  Atomic::store(&_succ, NO_OWNER);
}

inline int64_t ObjectMonitor::successor() const {
  return Atomic::load(&_succ);
}

// The _next_om field can be concurrently read and modified so we
// use Atomic operations to disable compiler optimizations that
// might try to elide loading and/or storing this field.

// Simply get _next_om field.
inline ObjectMonitor* ObjectMonitor::next_om() const {
  return Atomic::load(&_next_om);
}

// Simply set _next_om field to new_value.
inline void ObjectMonitor::set_next_om(ObjectMonitor* new_value) {
  Atomic::store(&_next_om, new_value);
}

// Block out deflation.
inline ObjectMonitorContentionMark::ObjectMonitorContentionMark(ObjectMonitor* monitor)
  : _monitor(monitor), _extended(false) {
  // Contentions is incremented to a positive value as part of the
  // contended enter protocol, which prevents the deflater thread from
  // winning the last part of the 2-part async deflation
  // protocol. See: ObjectMonitor::deflate_monitor() and
  // ObjectMonitor::try_lock_with_contention_mark().
  _monitor->add_to_contentions(1);
}

inline ObjectMonitorContentionMark::~ObjectMonitorContentionMark() {
  // Decrement contentions when the contention mark goes out of
  // scope. This opens up for deflation, if the contention mark
  // hasn't been extended.
  _monitor->add_to_contentions(-1);
}

inline void ObjectMonitorContentionMark::extend() {
  // Used by ObjectMonitor::try_lock_with_contention_mark() to "extend the
  // lifetime" of the contention mark.
  assert(!_extended, "extending twice is probably a bad design");
  _monitor->add_to_contentions(1);
  _extended = true;
}

inline oop ObjectMonitor::object_peek() const {
  if (_object.is_null()) {
    return nullptr;
  }
  return _object.peek();
}

inline bool ObjectMonitor::object_is_dead() const {
  return object_peek() == nullptr;
}

inline bool ObjectMonitor::object_refers_to(oop obj) const {
  if (_object.is_null()) {
    return false;
  }
  return _object.peek() == obj;
}

inline bool ObjectMonitor::is_jfr_excluded(const Klass* monitor_klass) {
  assert(monitor_klass != nullptr, "invariant");
  NOT_JFR_RETURN_(false);
  JFR_ONLY(return vmSymbols::jdk_jfr_internal_management_HiddenWait() == monitor_klass->name();)
}

#endif // SHARE_RUNTIME_OBJECTMONITOR_INLINE_HPP
