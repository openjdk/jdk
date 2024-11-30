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

#include "precompiled.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdLoadBarrier.inline.hpp"
#include "jfr/support/jfrIntrinsics.hpp"
#include "jfr/support/jfrThreadId.inline.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfr/writers/jfrJavaEventWriter.hpp"
#include "runtime/interfaceSupport.inline.hpp"

#ifdef ASSERT
static void assert_precondition(JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_java(jt);)
  assert(jt->has_last_Java_frame(), "invariant");
}

static void assert_epoch_identity(JavaThread* jt, u2 current_epoch) {
  assert_precondition(jt);
  // Verify the epoch updates got written through also to the vthread object.
  const u2 epoch_raw = ThreadIdAccess::epoch(jt->vthread());
  const bool excluded = epoch_raw & excluded_bit;
  assert(!excluded, "invariant");
  assert(!JfrThreadLocal::is_excluded(jt), "invariant");
  const u2 vthread_epoch = epoch_raw & epoch_mask;
  assert(vthread_epoch == current_epoch, "invariant");
}
#endif // ASSERT

void* JfrIntrinsicSupport::write_checkpoint(JavaThread* jt) {
  DEBUG_ONLY(assert_precondition(jt);)
  assert(JfrThreadLocal::is_vthread(jt), "invariant");
  const u2 vthread_thread_local_epoch = JfrThreadLocal::vthread_epoch(jt);
  const u2 current_epoch = ThreadIdAccess::current_epoch();
  MACOS_AARCH64_ONLY(ThreadWXEnable __wx(WXWrite, jt));
  if (vthread_thread_local_epoch == current_epoch) {
    // After the epoch test in the intrinsic, the thread sampler interleaved
    // and suspended the thread. As part of taking a sample, it updated
    // the vthread object and the thread local "for us". We are good.
    DEBUG_ONLY(assert_epoch_identity(jt, current_epoch);)
    ThreadInVMfromJava transition(jt);
    return JfrJavaEventWriter::event_writer(jt);
  }
  const traceid vthread_tid = JfrThreadLocal::vthread_id(jt);
  // Transition before reading the epoch generation anew, now as _thread_in_vm. Can safepoint here.
  ThreadInVMfromJava transition(jt);
  JfrThreadLocal::set_vthread_epoch(jt, vthread_tid, ThreadIdAccess::current_epoch());
  return JfrJavaEventWriter::event_writer(jt);
}

void* JfrIntrinsicSupport::return_lease(JavaThread* jt) {
  DEBUG_ONLY(assert_precondition(jt);)
  MACOS_AARCH64_ONLY(ThreadWXEnable __wx(WXWrite, jt));
  ThreadStateTransition::transition_from_java(jt, _thread_in_native);
  assert(jt->jfr_thread_local()->has_java_event_writer(), "invariant");
  assert(jt->jfr_thread_local()->shelved_buffer() != nullptr, "invariant");
  JfrJavaEventWriter::flush(jt->jfr_thread_local()->java_event_writer(), 0, 0, jt);
  assert(jt->jfr_thread_local()->shelved_buffer() == nullptr, "invariant");
  ThreadStateTransition::transition_from_native(jt, _thread_in_Java);
  return nullptr;
}

void JfrIntrinsicSupport::load_barrier(const Klass* klass) {
  assert(klass != nullptr, "sanity");
  JfrTraceIdLoadBarrier::load_barrier(klass);
}

address JfrIntrinsicSupport::epoch_address() {
  return JfrTraceIdEpoch::epoch_address();
}

address JfrIntrinsicSupport::epoch_generation_address() {
  return JfrTraceIdEpoch::epoch_generation_address();
}

address JfrIntrinsicSupport::signal_address() {
  return JfrTraceIdEpoch::signal_address();
}
