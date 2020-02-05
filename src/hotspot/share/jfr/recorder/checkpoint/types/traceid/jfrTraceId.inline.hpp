/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFRTRACEID_INLINE_HPP
#define SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFRTRACEID_INLINE_HPP

#include "classfile/classLoaderData.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/packageEntry.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdBits.inline.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/support/jfrKlassExtension.hpp"
#include "oops/arrayKlass.hpp"
#include "oops/klass.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/method.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/debug.hpp"

inline bool is_not_tagged(traceid value) {
  const traceid this_epoch_bit = JfrTraceIdEpoch::in_use_this_epoch_bit();
  return (value & ((this_epoch_bit << META_SHIFT) | this_epoch_bit)) != this_epoch_bit;
}

template <typename T>
inline bool should_tag(const T* t) {
  assert(t != NULL, "invariant");
  return is_not_tagged(TRACE_ID_RAW(t));
}

template <>
inline bool should_tag<Method>(const Method* method) {
  assert(method != NULL, "invariant");
  return is_not_tagged((traceid)method->trace_flags());
}

template <typename T>
inline traceid set_used_and_get(const T* type) {
  assert(type != NULL, "invariant");
  if (should_tag(type)) {
    SET_USED_THIS_EPOCH(type);
    JfrTraceIdEpoch::set_changed_tag_state();
  }
  assert(USED_THIS_EPOCH(type), "invariant");
  return TRACE_ID(type);
}

inline traceid JfrTraceId::get(const Klass* klass) {
  assert(klass != NULL, "invariant");
  return TRACE_ID(klass);
}

inline traceid JfrTraceId::get(const Thread* t) {
  assert(t != NULL, "invariant");
  return TRACE_ID_RAW(t->jfr_thread_local());
}

inline traceid JfrTraceId::use(const Klass* klass) {
  assert(klass != NULL, "invariant");
  if (should_tag(klass)) {
    SET_USED_THIS_EPOCH(klass);
    JfrTraceIdEpoch::set_changed_tag_state();
  }
  assert(USED_THIS_EPOCH(klass), "invariant");
  return get(klass);
}

inline traceid JfrTraceId::use(const Method* method) {
  return use(method->method_holder(), method);
}

inline traceid JfrTraceId::use(const Klass* klass, const Method* method) {
  assert(klass != NULL, "invariant");
  assert(method != NULL, "invariant");
  if (METHOD_FLAG_NOT_USED_THIS_EPOCH(method)) {
    SET_METHOD_AND_CLASS_USED_THIS_EPOCH(klass);
    SET_METHOD_FLAG_USED_THIS_EPOCH(method);
    assert(METHOD_AND_CLASS_USED_THIS_EPOCH(klass), "invariant");
    assert(METHOD_FLAG_USED_THIS_EPOCH(method), "invariant");
    JfrTraceIdEpoch::set_changed_tag_state();
  }
  return (METHOD_ID(klass, method));
}

inline traceid JfrTraceId::use(const ModuleEntry* module) {
  return set_used_and_get(module);
}

inline traceid JfrTraceId::use(const PackageEntry* package) {
  return set_used_and_get(package);
}

inline traceid JfrTraceId::use(const ClassLoaderData* cld) {
  assert(cld != NULL, "invariant");
  return cld->is_unsafe_anonymous() ? 0 : set_used_and_get(cld);
}

inline void JfrTraceId::set_leakp(const Klass* klass, const Method* method) {
  assert(klass != NULL, "invariant");
  assert(METHOD_AND_CLASS_USED_THIS_EPOCH(klass), "invariant");
  assert(method != NULL, "invariant");
  assert(klass == method->method_holder(), "invariant");
  if (METHOD_FLAG_NOT_USED_THIS_EPOCH(method)) {
    // the method is already logically tagged, just like the klass,
    // but because of redefinition, the latest Method*
    // representation might not have a reified tag.
    SET_METHOD_FLAG_USED_THIS_EPOCH(method);
    assert(METHOD_FLAG_USED_THIS_EPOCH(method), "invariant");
  }
  SET_LEAKP(klass);
  SET_METHOD_LEAKP(method);
}

inline bool JfrTraceId::in_visible_set(const Klass* klass) {
  assert(klass != NULL, "invariant");
  assert(((JavaThread*)Thread::current())->thread_state() == _thread_in_vm, "invariant");
  return (IS_JDK_JFR_EVENT_SUBKLASS(klass) && !klass->is_abstract()) || IS_EVENT_HOST_KLASS(klass);
}

inline bool JfrTraceId::is_jdk_jfr_event(const Klass* k) {
  assert(k != NULL, "invariant");
  return IS_JDK_JFR_EVENT_KLASS(k);
}

inline void JfrTraceId::tag_as_jdk_jfr_event(const Klass* klass) {
  assert(klass != NULL, "invariant");
  SET_JDK_JFR_EVENT_KLASS(klass);
  assert(IS_JDK_JFR_EVENT_KLASS(klass), "invariant");
}

inline bool JfrTraceId::is_jdk_jfr_event_sub(const Klass* k) {
  assert(k != NULL, "invariant");
  return IS_JDK_JFR_EVENT_SUBKLASS(k);
}

inline void JfrTraceId::tag_as_jdk_jfr_event_sub(const Klass* k) {
  assert(k != NULL, "invariant");
  if (IS_NOT_AN_EVENT_SUB_KLASS(k)) {
    SET_JDK_JFR_EVENT_SUBKLASS(k);
  }
  assert(IS_JDK_JFR_EVENT_SUBKLASS(k), "invariant");
}

inline bool JfrTraceId::in_jdk_jfr_event_hierarchy(const Klass* klass) {
  assert(klass != NULL, "invariant");
  if (is_jdk_jfr_event(klass)) {
    return true;
  }
  const Klass* const super = klass->super();
  return super != NULL ? IS_EVENT_KLASS(super) : false;
}

inline bool JfrTraceId::is_event_host(const Klass* k) {
  assert(k != NULL, "invariant");
  return IS_EVENT_HOST_KLASS(k);
}

inline void JfrTraceId::tag_as_event_host(const Klass* k) {
  assert(k != NULL, "invariant");
  SET_EVENT_HOST_KLASS(k);
  assert(IS_EVENT_HOST_KLASS(k), "invariant");
}

#endif // SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFRTRACEID_INLINE_HPP
