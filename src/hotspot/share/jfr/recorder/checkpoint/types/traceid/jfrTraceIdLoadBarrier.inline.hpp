/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDBARRIER_INLINE_HPP
#define SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDBARRIER_INLINE_HPP

#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdLoadBarrier.hpp"

#include "classfile/classLoaderData.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/packageEntry.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdBits.inline.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdMacros.hpp"
#include "jfr/support/jfrKlassExtension.hpp"
#include "oops/klass.hpp"
#include "oops/method.hpp"
#include "runtime/javaThread.hpp"
#include "utilities/debug.hpp"

inline bool is_not_tagged(traceid value) {
  const traceid this_epoch_bit = JfrTraceIdEpoch::this_epoch_bit();
  return ((value & ((this_epoch_bit << META_SHIFT) | this_epoch_bit)) != this_epoch_bit);
}

template <typename T>
inline bool should_tag(const T* t) {
  assert(t != nullptr, "invariant");
  return is_not_tagged(TRACE_ID_RAW(t));
}

template <>
inline bool should_tag<Method>(const Method* method) {
  assert(method != nullptr, "invariant");
  return is_not_tagged((traceid)method->trace_flags());
}

template <typename T>
inline traceid set_used_and_get(const T* type) {
  assert(type != nullptr, "invariant");
  if (should_tag(type)) {
    SET_USED_THIS_EPOCH(type);
    JfrTraceIdEpoch::set_changed_tag_state();
  }
  assert(USED_THIS_EPOCH(type), "invariant");
  return TRACE_ID(type);
}

// We set the 'method_and_class' bits to have a consistent
// bit pattern set always. This is because the tag is non-atomic,
// hence, we always need the same bit pattern in an epoch to avoid losing information.
inline void JfrTraceIdLoadBarrier::load_barrier(const Klass* klass) {
  SET_METHOD_AND_CLASS_USED_THIS_EPOCH(klass);
  assert(METHOD_AND_CLASS_USED_THIS_EPOCH(klass), "invariant");
  enqueue(klass);
  JfrTraceIdEpoch::set_changed_tag_state();
}

inline traceid JfrTraceIdLoadBarrier::load(const Klass* klass) {
  assert(klass != nullptr, "invariant");
  if (should_tag(klass)) {
    load_barrier(klass);
  }
  assert(METHOD_AND_CLASS_USED_THIS_EPOCH(klass), "invariant");
  return TRACE_ID(klass);
}

inline traceid JfrTraceIdLoadBarrier::load(const Method* method) {
  return load(method->method_holder(), method);
}

inline traceid JfrTraceIdLoadBarrier::load(const Klass* klass, const Method* method) {
   assert(klass != nullptr, "invariant");
   assert(method != nullptr, "invariant");
   if (should_tag(method)) {
     SET_METHOD_AND_CLASS_USED_THIS_EPOCH(klass);
     SET_METHOD_FLAG_USED_THIS_EPOCH(method);
     assert(METHOD_AND_CLASS_USED_THIS_EPOCH(klass), "invariant");
     assert(METHOD_FLAG_USED_THIS_EPOCH(method), "invariant");
     enqueue(klass);
     JfrTraceIdEpoch::set_changed_tag_state();
   }
   return (METHOD_ID(klass, method));
}

inline traceid JfrTraceIdLoadBarrier::load_no_enqueue(const Method* method) {
  return load_no_enqueue(method->method_holder(), method);
}

inline traceid JfrTraceIdLoadBarrier::load_no_enqueue(const Klass* klass, const Method* method) {
  assert(klass != nullptr, "invariant");
  assert(method != nullptr, "invariant");
  SET_METHOD_AND_CLASS_USED_THIS_EPOCH(klass);
  SET_METHOD_FLAG_USED_THIS_EPOCH(method);
  assert(METHOD_AND_CLASS_USED_THIS_EPOCH(klass), "invariant");
  assert(METHOD_FLAG_USED_THIS_EPOCH(method), "invariant");
  return (METHOD_ID(klass, method));
}

inline traceid JfrTraceIdLoadBarrier::load(const ClassLoaderData* cld) {
  assert(cld != nullptr, "invariant");
  if (cld->has_class_mirror_holder()) {
    return 0;
  }
  const Klass* const class_loader_klass = cld->class_loader_klass();
  if (class_loader_klass != nullptr) {
    load(class_loader_klass);
  }
  return set_used_and_get(cld);
}

inline traceid JfrTraceIdLoadBarrier::load(const ModuleEntry* module) {
  assert(module != nullptr, "invariant");
  const ClassLoaderData* cld = module->loader_data();
  if (cld != nullptr) {
    load(cld);
  }
  return set_used_and_get(module);
}

inline traceid JfrTraceIdLoadBarrier::load(const PackageEntry* package) {
  assert(package != nullptr, "invariant");
  const ModuleEntry* const module_entry = package->module();
  if (module_entry != nullptr) {
    load(module_entry);
  }
  return set_used_and_get(package);
}

inline traceid JfrTraceIdLoadBarrier::load_leakp(const Klass* klass) {
  assert(klass != nullptr, "invariant");
  load(klass); // Ensure tagged and enqueued.
  SET_LEAKP(klass);
  return TRACE_ID(klass);
}

inline traceid JfrTraceIdLoadBarrier::load_leakp(const Klass* klass, const Method* method) {
  assert(klass != nullptr, "invariant");
  assert(method != nullptr, "invariant");
  assert(klass == method->method_holder(), "invariant");
  assert(METHOD_AND_CLASS_USED_THIS_EPOCH(klass), "invariant");
  if (should_tag(method)) {
    // the method is already logically tagged, just like the klass,
    // but because of redefinition, the latest Method*
    // representation might not have a reified tag.
    SET_METHOD_FLAG_USED_THIS_EPOCH(method);
    assert(METHOD_FLAG_USED_THIS_EPOCH(method), "invariant");
  }
  SET_LEAKP(klass);
  SET_METHOD_LEAKP(method);
  return (METHOD_ID(klass, method));
}

inline traceid JfrTraceIdLoadBarrier::load_leakp_previuos_epoch(const Klass* klass, const Method* method) {
  assert(klass != nullptr, "invariant");
  assert(method != nullptr, "invariant");
  assert(klass == method->method_holder(), "invariant");
  assert(METHOD_AND_CLASS_USED_PREVIOUS_EPOCH(klass), "invariant");
  if (METHOD_FLAG_NOT_USED_PREVIOUS_EPOCH(method)) {
    // the method is already logically tagged, just like the klass,
    // but because of redefinition, the latest Method*
    // representation might not have a reified tag.
    SET_TRANSIENT(method);
    assert(METHOD_FLAG_USED_PREVIOUS_EPOCH(method), "invariant");
  }
  SET_LEAKP(klass);
  SET_METHOD_LEAKP(method);
  return (METHOD_ID(klass, method));
}

#endif // SHARE_JFR_RECORDER_CHECKPOINT_TYPES_TRACEID_JFRTRACEIDBARRIER_INLINE_HPP
