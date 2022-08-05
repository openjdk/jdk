/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_THREADJAVACLASSES_INLINE_HPP
#define SHARE_RUNTIME_THREADJAVACLASSES_INLINE_HPP

#include "runtime/threadJavaClasses.hpp"

#include "oops/instanceKlass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopsHierarchy.hpp"


inline oop java_lang_Thread::continuation(oop java_thread) {
  return java_thread->obj_field(_continuation_offset);
}

inline int64_t java_lang_Thread::thread_id(oop java_thread) {
  return java_thread->long_field(_tid_offset);
}

inline oop java_lang_VirtualThread::vthread_scope() {
  oop base = vmClasses::VirtualThread_klass()->static_field_base_raw();
  return base->obj_field(static_vthread_scope_offset);
}

#if INCLUDE_JFR
inline u2 java_lang_Thread::jfr_epoch(oop ref) {
  return ref->short_field(_jfr_epoch_offset);
}

inline void java_lang_Thread::set_jfr_epoch(oop ref, u2 epoch) {
  ref->short_field_put(_jfr_epoch_offset, epoch);
}
#endif // INCLUDE_JFR



#endif // SHARE_RUNTIME_THREADJAVACLASSES_INLINE_HPP
